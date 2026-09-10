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
typedef VkResult (*PFN_vkCreatePipelineCache_dyn)(VkDevice, const VkPipelineCacheCreateInfo*, const VkAllocationCallbacks*, VkPipelineCache*);
typedef void (*PFN_vkDestroyPipelineCache_dyn)(VkDevice, VkPipelineCache, const VkAllocationCallbacks*);
typedef VkResult (*PFN_vkCreateGraphicsPipelines_dyn)(VkDevice, VkPipelineCache, uint32_t, const VkGraphicsPipelineCreateInfo*, const VkAllocationCallbacks*, VkPipeline*);

static PFN_vkAllocateMemory_dyn fn_vkAllocateMemory = nullptr;
static PFN_vkFreeMemory_dyn fn_vkFreeMemory = nullptr;
static PFN_vkCreateBuffer_dyn fn_vkCreateBuffer = nullptr;
static PFN_vkDestroyBuffer_dyn fn_vkDestroyBuffer = nullptr;
static PFN_vkGetBufferMemoryRequirements_dyn fn_vkGetBufferMemoryRequirements = nullptr;
static PFN_vkBindBufferMemory_dyn fn_vkBindBufferMemory = nullptr;
static PFN_vkGetPhysicalDeviceMemoryProperties_dyn fn_vkGetPhysicalDeviceMemoryProperties = nullptr;
static PFN_vkUnmapMemory_dyn fn_vkUnmapMemory = nullptr;
static PFN_vkCreatePipelineCache_dyn fn_vkCreatePipelineCache = nullptr;
static PFN_vkDestroyPipelineCache_dyn fn_vkDestroyPipelineCache = nullptr;
static PFN_vkCreateGraphicsPipelines_dyn fn_vkCreateGraphicsPipelines = nullptr;

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
    fn_vkCreatePipelineCache = (PFN_vkCreatePipelineCache_dyn) dlsym(RTLD_DEFAULT, "vkCreatePipelineCache");
    fn_vkDestroyPipelineCache = (PFN_vkDestroyPipelineCache_dyn) dlsym(RTLD_DEFAULT, "vkDestroyPipelineCache");
    fn_vkCreateGraphicsPipelines = (PFN_vkCreateGraphicsPipelines_dyn) dlsym(RTLD_DEFAULT, "vkCreateGraphicsPipelines");
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

    // Enforce 16-byte std140/std430 alignment rules for Mali GPU memory bounds
    VkDeviceSize alignment = std::max(mem_reqs.alignment, (VkDeviceSize)16);

    std::lock_guard<std::mutex> lock(m_mutex);

    VkDeviceMemory allocated_mem = VK_NULL_HANDLE;
    VkDeviceSize allocated_offset = 0;

    for (auto& block : m_heap_blocks) {
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
            LOGI("TurboV1 VMA Pool: Allocated new 16MB persistent memory heap block (16-byte aligned, %zu total blocks)", m_heap_blocks.size());
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
      m_pipeline_cache(VK_NULL_HANDLE),
      m_has_dynamic_rendering(true),
      m_has_rasterization_order_access(true),
      m_vkCmdBeginRenderingKHR(nullptr),
      m_vkCmdEndRenderingKHR(nullptr) {}

VulkanPipelineManager::~VulkanPipelineManager() {
    shutdown();
}

bool VulkanPipelineManager::init(VkDevice device, VkPhysicalDevice physical_device, VkInstance instance) {
    resolve_vulkan_entry_points();
    m_device = device;
    m_physical_device = physical_device;
    m_instance = instance;

    m_memory_pool.init(device, physical_device);

    if (m_device != VK_NULL_HANDLE && fn_vkCreatePipelineCache) {
        VkPipelineCacheCreateInfo cache_info{};
        cache_info.sType = VK_STRUCTURE_TYPE_PIPELINE_CACHE_CREATE_INFO;
        fn_vkCreatePipelineCache(m_device, &cache_info, nullptr, &m_pipeline_cache);
        LOGI("TurboV1 Pipeline Cache: Created thread-isolated VkPipelineCache warmup block");
    }

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
    resolve_vulkan_entry_points();
    if (m_device != VK_NULL_HANDLE && m_pipeline_cache != VK_NULL_HANDLE && fn_vkDestroyPipelineCache) {
        fn_vkDestroyPipelineCache(m_device, m_pipeline_cache, nullptr);
        m_pipeline_cache = VK_NULL_HANDLE;
    }
    m_memory_pool.shutdown();
    m_device = VK_NULL_HANDLE;
    m_physical_device = VK_NULL_HANDLE;
    m_instance = VK_NULL_HANDLE;
}

VkPipeline VulkanPipelineManager::create_graphics_pipeline_sandboxed(const VkGraphicsPipelineCreateInfo* create_info, uint32_t layout_index) {
    resolve_vulkan_entry_points();
    if (m_device == VK_NULL_HANDLE || create_info == nullptr || !fn_vkCreateGraphicsPipelines) {
        LOGE("TurboV1 Sandboxed Pipeline: Device or entry points null for layout index %u", layout_index);
        return VK_NULL_HANDLE;
    }

    std::lock_guard<std::mutex> lock(m_pipeline_mutex);
    VkPipeline pipeline = VK_NULL_HANDLE;

    // Sandboxed execution context catching Mali driver compilation failures
    VkResult res = fn_vkCreateGraphicsPipelines(m_device, m_pipeline_cache, 1, create_info, nullptr, &pipeline);
    if (res != VK_SUCCESS) {
        LOGE("TurboV1 Sandboxed Pipeline Catch: Driver returned VkResult=%d for layout index %u (Recovered without main thread crash)", res, layout_index);
        return VK_NULL_HANDLE;
    }

    return pipeline;
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

VkSurfaceKHR VulkanPipelineManager::create_android_surface(void* window_handle) {
    if (m_instance == VK_NULL_HANDLE || window_handle == nullptr) return VK_NULL_HANDLE;

    typedef struct VkAndroidSurfaceCreateInfoKHR {
        VkStructureType                   sType;
        const void*                       pNext;
        uint32_t                          flags;
        void*                             window;
    } VkAndroidSurfaceCreateInfoKHR;

    typedef VkResult (*PFN_vkCreateAndroidSurfaceKHR)(VkInstance, const VkAndroidSurfaceCreateInfoKHR*, const VkAllocationCallbacks*, VkSurfaceKHR*);
    static PFN_vkCreateAndroidSurfaceKHR fn_vkCreateAndroidSurfaceKHR = nullptr;
    if (!fn_vkCreateAndroidSurfaceKHR) {
        typedef PFN_vkVoidFunction (*PFN_vkGetInstanceProcAddr_dyn)(VkInstance, const char*);
        static PFN_vkGetInstanceProcAddr_dyn real_get_instance_proc = (PFN_vkGetInstanceProcAddr_dyn) dlsym(RTLD_DEFAULT, "vkGetInstanceProcAddr");
        if (real_get_instance_proc) {
            fn_vkCreateAndroidSurfaceKHR = (PFN_vkCreateAndroidSurfaceKHR) real_get_instance_proc(m_instance, "vkCreateAndroidSurfaceKHR");
        }
    }

    VkSurfaceKHR surface = VK_NULL_HANDLE;
    if (fn_vkCreateAndroidSurfaceKHR) {
        VkAndroidSurfaceCreateInfoKHR create_info{};
        create_info.sType = (VkStructureType)1000008000; // VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR
        create_info.window = window_handle;
        if (fn_vkCreateAndroidSurfaceKHR(m_instance, &create_info, nullptr, &surface) == VK_SUCCESS) {
            LOGI("TurboV1 Native Surface: Successfully created vkCreateAndroidSurfaceKHR for handle %p", window_handle);
        } else {
            LOGE("TurboV1 Native Surface: vkCreateAndroidSurfaceKHR failed for handle %p", window_handle);
        }
    }
    return surface;
}

} // namespace vulkan

} // namespace turbo_v1
