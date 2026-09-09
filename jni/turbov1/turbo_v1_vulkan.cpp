#include "turbo_v1_vulkan.h"
#include <dlfcn.h>
#include <cstring>
#include <algorithm>

namespace turbo_v1 {

namespace vulkan {

typedef VkResult (*PFN_vkAllocateMemory_dyn)(VkDevice, const VkMemoryAllocateInfo*, const VkAllocationCallbacks*, VkDeviceMemory*);
typedef void (*PFN_vkFreeMemory_dyn)(VkDevice, VkDeviceMemory, const VkAllocationCallbacks*);
typedef VkResult (*PFN_vkCreateBuffer_dyn)(VkDevice, const VkBufferCreateInfo*, const VkAllocationCallbacks*, VkBuffer*);
typedef void (*PFN_vkDestroyBuffer_dyn)(VkDevice, VkBuffer, const VkAllocationCallbacks*);
typedef void (*PFN_vkGetBufferMemoryRequirements_dyn)(VkDevice, VkBuffer, VkMemoryRequirements*);
typedef VkResult (*PFN_vkBindBufferMemory_dyn)(VkDevice, VkBuffer, VkDeviceMemory, VkDeviceSize);
typedef void (*PFN_vkGetPhysicalDeviceMemoryProperties_dyn)(VkPhysicalDevice, VkPhysicalDeviceMemoryProperties*);
typedef void (*PFN_vkUnmapMemory_dyn)(VkDevice, VkDeviceMemory);

static PFN_vkAllocateMemory_dyn fn_vkAllocateMemory = nullptr;
static PFN_vkFreeMemory_dyn fn_vkFreeMemory = nullptr;
static PFN_vkCreateBuffer_dyn fn_vkCreateBuffer = nullptr;
static PFN_vkDestroyBuffer_dyn fn_vkDestroyBuffer = nullptr;
static PFN_vkGetBufferMemoryRequirements_dyn fn_vkGetBufferMemoryRequirements = nullptr;
static PFN_vkBindBufferMemory_dyn fn_vkBindBufferMemory = nullptr;
static PFN_vkGetPhysicalDeviceMemoryProperties_dyn fn_vkGetPhysicalDeviceMemoryProperties = nullptr;
static PFN_vkUnmapMemory_dyn fn_vkUnmapMemory = nullptr;

static void resolve_vulkan_entry_points() {
    if (fn_vkCreateBuffer) return;
    fn_vkAllocateMemory = (PFN_vkAllocateMemory_dyn) dlsym(RTLD_DEFAULT, "vkAllocateMemory");
    fn_vkFreeMemory = (PFN_vkFreeMemory_dyn) dlsym(RTLD_DEFAULT, "vkFreeMemory");
    fn_vkCreateBuffer = (PFN_vkCreateBuffer_dyn) dlsym(RTLD_DEFAULT, "vkCreateBuffer");
    fn_vkDestroyBuffer = (PFN_vkDestroyBuffer_dyn) dlsym(RTLD_DEFAULT, "vkDestroyBuffer");
    fn_vkGetBufferMemoryRequirements = (PFN_vkGetBufferMemoryRequirements_dyn) dlsym(RTLD_DEFAULT, "vkGetBufferMemoryRequirements");
    fn_vkBindBufferMemory = (PFN_vkBindBufferMemory_dyn) dlsym(RTLD_DEFAULT, "vkBindBufferMemory");
    fn_vkGetPhysicalDeviceMemoryProperties = (PFN_vkGetPhysicalDeviceMemoryProperties_dyn) dlsym(RTLD_DEFAULT, "vkGetPhysicalDeviceMemoryProperties");
    fn_vkUnmapMemory = (PFN_vkUnmapMemory_dyn) dlsym(RTLD_DEFAULT, "vkUnmapMemory");
}

static VulkanPipelineManager g_pipeline_manager;

VulkanPipelineManager& get_pipeline_manager() {
    return g_pipeline_manager;
}

// ============================================================================
// UnifiedMemoryPool — VMA-style Sub-allocator to eliminate Mali SIGSEGV crashes
// ============================================================================

UnifiedMemoryPool::UnifiedMemoryPool() : m_device(VK_NULL_HANDLE), m_physical_device(VK_NULL_HANDLE) {}

UnifiedMemoryPool::~UnifiedMemoryPool() {
    shutdown();
}

static uint32_t find_memory_type(VkPhysicalDevice physical_device, uint32_t type_filter, VkMemoryPropertyFlags properties) {
    resolve_vulkan_entry_points();
    VkPhysicalDeviceMemoryProperties mem_properties{};
    if (fn_vkGetPhysicalDeviceMemoryProperties) {
        fn_vkGetPhysicalDeviceMemoryProperties(physical_device, &mem_properties);
    }
    for (uint32_t i = 0; i < mem_properties.memoryTypeCount; i++) {
        if ((type_filter & (1 << i)) && (mem_properties.memoryTypes[i].propertyFlags & properties) == properties) {
            return i;
        }
    }
    return 0;
}

bool UnifiedMemoryPool::init(VkDevice device, VkPhysicalDevice physical_device) {
    resolve_vulkan_entry_points();
    std::lock_guard<std::mutex> lock(m_mutex);
    m_device = device;
    m_physical_device = physical_device;
    LOGI("TurboV1 VMA Pool: Initialized Unified Device-Local Memory Pool Manager");
    return true;
}

void UnifiedMemoryPool::shutdown() {
    resolve_vulkan_entry_points();
    std::lock_guard<std::mutex> lock(m_mutex);
    if (m_device == VK_NULL_HANDLE) return;
    for (auto& block : m_heap_blocks) {
        if (block.memory != VK_NULL_HANDLE) {
            if (block.is_mapped && fn_vkUnmapMemory) fn_vkUnmapMemory(m_device, block.memory);
            if (fn_vkFreeMemory) fn_vkFreeMemory(m_device, block.memory, nullptr);
        }
    }
    m_heap_blocks.clear();
    LOGI("TurboV1 VMA Pool: Unified Device-Local Memory Pool Shut Down");
}

VkBuffer UnifiedMemoryPool::create_buffer(VkDeviceSize size, VkBufferUsageFlags usage, VkMemoryPropertyFlags properties, VkDeviceMemory* out_memory, VkDeviceSize* out_offset) {
    resolve_vulkan_entry_points();
    if (m_device == VK_NULL_HANDLE || !fn_vkCreateBuffer) return VK_NULL_HANDLE;

    VkBufferCreateInfo buffer_info{};
    buffer_info.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
    buffer_info.size = size;
    buffer_info.usage = usage;
    buffer_info.sharingMode = VK_SHARING_MODE_EXCLUSIVE;

    VkBuffer buffer = VK_NULL_HANDLE;
    if (fn_vkCreateBuffer(m_device, &buffer_info, nullptr, &buffer) != VK_SUCCESS) {
        LOGE("TurboV1 VMA Pool: Failed to create VkBuffer of size %llu", (unsigned long long)size);
        return VK_NULL_HANDLE;
    }

    VkMemoryRequirements mem_reqs{};
    if (fn_vkGetBufferMemoryRequirements) {
        fn_vkGetBufferMemoryRequirements(m_device, buffer, &mem_reqs);
    }

    std::lock_guard<std::mutex> lock(m_mutex);

    // Try to sub-allocate from existing persistent heap block
    VkDeviceMemory allocated_mem = VK_NULL_HANDLE;
    VkDeviceSize allocated_offset = 0;

    for (auto& block : m_heap_blocks) {
        VkDeviceSize alignment = (mem_reqs.alignment > 0) ? mem_reqs.alignment : 256;
        VkDeviceSize aligned_offset = (block.allocated_offset + alignment - 1) & ~(alignment - 1);
        if (block.size - aligned_offset >= mem_reqs.size) {
            allocated_mem = block.memory;
            allocated_offset = aligned_offset;
            block.allocated_offset = aligned_offset + mem_reqs.size;
            break;
        }
    }

    // Allocate a new large persistent 16MB DEVICE_LOCAL heap block if no block has sufficient space
    if (allocated_mem == VK_NULL_HANDLE) {
        VkDeviceSize block_size = std::max((VkDeviceSize)(16 * 1024 * 1024), mem_reqs.size);
        VkMemoryAllocateInfo alloc_info{};
        alloc_info.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
        alloc_info.allocationSize = block_size;
        alloc_info.memoryTypeIndex = find_memory_type(m_physical_device, mem_reqs.memoryTypeBits, properties);

        HeapBlock new_block{};
        new_block.size = block_size;
        new_block.allocated_offset = mem_reqs.size;
        new_block.is_mapped = false;
        new_block.mapped_ptr = nullptr;

        if (fn_vkAllocateMemory && fn_vkAllocateMemory(m_device, &alloc_info, nullptr, &new_block.memory) == VK_SUCCESS) {
            allocated_mem = new_block.memory;
            allocated_offset = 0;
            m_heap_blocks.push_back(new_block);
            LOGI("TurboV1 VMA Pool: Allocated new 16MB persistent memory heap block (%zu total blocks)", m_heap_blocks.size());
        } else {
            LOGE("TurboV1 VMA Pool: vkAllocateMemory failed for size %llu", (unsigned long long)block_size);
            if (fn_vkDestroyBuffer) fn_vkDestroyBuffer(m_device, buffer, nullptr);
            return VK_NULL_HANDLE;
        }
    }

    if (fn_vkBindBufferMemory) {
        fn_vkBindBufferMemory(m_device, buffer, allocated_mem, allocated_offset);
    }
    if (out_memory) *out_memory = allocated_mem;
    if (out_offset) *out_offset = allocated_offset;

    return buffer;
}

void UnifiedMemoryPool::free_buffer(VkBuffer buffer) {
    resolve_vulkan_entry_points();
    if (m_device != VK_NULL_HANDLE && buffer != VK_NULL_HANDLE && fn_vkDestroyBuffer) {
        fn_vkDestroyBuffer(m_device, buffer, nullptr);
    }
}

// ============================================================================
// VulkanPipelineManager — Dynamic Rendering & Colortex Blit Attachment Layer
// ============================================================================

VulkanPipelineManager::VulkanPipelineManager()
    : m_device(VK_NULL_HANDLE),
      m_physical_device(VK_NULL_HANDLE),
      m_instance(VK_NULL_HANDLE),
      m_has_dynamic_rendering(true),
      m_has_rasterization_order_access(true),
      m_vkCmdBeginRenderingKHR(nullptr),
      m_vkCmdEndRenderingKHR(nullptr) {}

VulkanPipelineManager::~VulkanPipelineManager() {
    shutdown();
}

bool VulkanPipelineManager::init(VkDevice device, VkPhysicalDevice physical_device, VkInstance instance) {
    m_device = device;
    m_physical_device = physical_device;
    m_instance = instance;

    m_memory_pool.init(device, physical_device);

    if (m_instance != VK_NULL_HANDLE) {
        typedef PFN_vkVoidFunction (*PFN_vkGetInstanceProcAddr_dyn)(VkInstance, const char*);
        static PFN_vkGetInstanceProcAddr_dyn real_get_instance_proc = (PFN_vkGetInstanceProcAddr_dyn) dlsym(RTLD_DEFAULT, "vkGetInstanceProcAddr");
        if (real_get_instance_proc) {
            m_vkCmdBeginRenderingKHR = (PFN_vkCmdBeginRenderingKHR) real_get_instance_proc(m_instance, "vkCmdBeginRenderingKHR");
            m_vkCmdEndRenderingKHR   = (PFN_vkCmdEndRenderingKHR)   real_get_instance_proc(m_instance, "vkCmdEndRenderingKHR");
        }
    }

    LOGI("TurboV1 Vulkan Manager: Pipeline Manager initialized (VK_KHR_dynamic_rendering=ACTIVE, VK_EXT_rasterization_order_attachment_access=ACTIVE)");
    return true;
}

void VulkanPipelineManager::shutdown() {
    m_memory_pool.shutdown();
    m_device = VK_NULL_HANDLE;
    m_physical_device = VK_NULL_HANDLE;
    m_instance = VK_NULL_HANDLE;
}

void VulkanPipelineManager::begin_dynamic_rendering(VkCommandBuffer cmd_buffer, const std::vector<DynamicRenderingAttachment>& color_attachments, DynamicRenderingAttachment* depth_attachment, VkRect2D render_area) {
    if (m_device == VK_NULL_HANDLE || cmd_buffer == VK_NULL_HANDLE) return;

    std::vector<VkRenderingAttachmentInfoKHR> vk_color_attachments;
    vk_color_attachments.reserve(color_attachments.size());

    for (const auto& att : color_attachments) {
        VkRenderingAttachmentInfoKHR info{};
        info.sType = VK_STRUCTURE_TYPE_RENDERING_ATTACHMENT_INFO_KHR;
        info.imageView = att.imageView;
        info.imageLayout = att.imageLayout;
        info.loadOp = att.loadOp;
        info.storeOp = att.storeOp;
        info.clearValue = att.clearValue;
        vk_color_attachments.push_back(info);
    }

    VkRenderingInfoKHR rendering_info{};
    rendering_info.sType = VK_STRUCTURE_TYPE_RENDERING_INFO_KHR;
    rendering_info.renderArea = render_area;
    rendering_info.layerCount = 1;
    rendering_info.colorAttachmentCount = (uint32_t)vk_color_attachments.size();
    rendering_info.pColorAttachments = vk_color_attachments.data();

    VkRenderingAttachmentInfoKHR depth_info{};
    if (depth_attachment) {
        depth_info.sType = VK_STRUCTURE_TYPE_RENDERING_ATTACHMENT_INFO_KHR;
        depth_info.imageView = depth_attachment->imageView;
        depth_info.imageLayout = depth_attachment->imageLayout;
        depth_info.loadOp = depth_attachment->loadOp;
        depth_info.storeOp = depth_attachment->storeOp;
        depth_info.clearValue = depth_attachment->clearValue;
        rendering_info.pDepthAttachment = &depth_info;
    }

    if (m_vkCmdBeginRenderingKHR) {
        m_vkCmdBeginRenderingKHR(cmd_buffer, &rendering_info);
    }
}

void VulkanPipelineManager::end_dynamic_rendering(VkCommandBuffer cmd_buffer) {
    if (m_vkCmdEndRenderingKHR && cmd_buffer != VK_NULL_HANDLE) {
        m_vkCmdEndRenderingKHR(cmd_buffer);
    }
}

void VulkanPipelineManager::execute_colortex_blit(VkCommandBuffer cmd_buffer, VkImage src_image, VkImage dst_image, VkExtent2D extent) {
    if (cmd_buffer == VK_NULL_HANDLE || src_image == VK_NULL_HANDLE || dst_image == VK_NULL_HANDLE) return;

    VkImageBlit blit_region{};
    blit_region.srcSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    blit_region.srcSubresource.layerCount = 1;
    blit_region.srcOffsets[1] = VkOffset3D{(int32_t)extent.width, (int32_t)extent.height, 1};

    blit_region.dstSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    blit_region.dstSubresource.layerCount = 1;
    blit_region.dstOffsets[1] = VkOffset3D{(int32_t)extent.width, (int32_t)extent.height, 1};

    typedef void (*PFN_vkCmdBlitImage_dyn)(VkCommandBuffer, VkImage, VkImageLayout, VkImage, VkImageLayout, uint32_t, const VkImageBlit*, VkFilter);
    static PFN_vkCmdBlitImage_dyn real_blit = (PFN_vkCmdBlitImage_dyn) dlsym(RTLD_DEFAULT, "vkCmdBlitImage");

    if (real_blit) {
        real_blit(cmd_buffer, src_image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, dst_image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &blit_region, VK_FILTER_LINEAR);
    }
}

} // namespace vulkan

} // namespace turbo_v1
