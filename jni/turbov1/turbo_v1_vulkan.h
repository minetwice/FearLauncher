#ifndef TURBO_V1_VULKAN_H
#define TURBO_V1_VULKAN_H

#include "turbo_v1_core.h"
#include <vulkan/vulkan.h>
#include <vector>
#include <string>
#include <mutex>
#include <memory>

namespace turbo_v1 {

namespace vulkan {

struct HeapBlock {
    VkDeviceMemory memory;
    VkDeviceSize   size;
    VkDeviceSize   allocated_offset;
    void*          mapped_ptr;
    bool           is_mapped;
};

class UnifiedMemoryPool {
public:
    UnifiedMemoryPool();
    ~UnifiedMemoryPool();

    bool init(VkDevice device, VkPhysicalDevice physical_device);
    void shutdown();

    VkBuffer create_buffer(VkDeviceSize size, VkBufferUsageFlags usage, VkMemoryPropertyFlags properties, VkDeviceMemory* out_memory, VkDeviceSize* out_offset);
    void free_buffer(VkBuffer buffer);

private:
    VkDevice m_device;
    VkPhysicalDevice m_physical_device;
    std::vector<HeapBlock> m_heap_blocks;
    std::mutex m_mutex;
};

struct DynamicRenderingAttachment {
    VkImageView imageView;
    VkImageLayout imageLayout;
    VkAttachmentLoadOp loadOp;
    VkAttachmentStoreOp storeOp;
    VkClearValue clearValue;
};

class VulkanPipelineManager {
public:
    VulkanPipelineManager();
    ~VulkanPipelineManager();

    bool init(VkDevice device, VkPhysicalDevice physical_device, VkInstance instance);
    void shutdown();

    // Pipeline Warmup & Sandboxed Creation Engine (VkPipelineCache)
    VkPipeline create_graphics_pipeline_sandboxed(const VkGraphicsPipelineCreateInfo* create_info, uint32_t layout_index);

    // Dynamic Rendering Enforcement (VK_KHR_dynamic_rendering)
    void begin_dynamic_rendering(VkCommandBuffer cmd_buffer, const std::vector<DynamicRenderingAttachment>& color_attachments, DynamicRenderingAttachment* depth_attachment, VkRect2D render_area);
    void end_dynamic_rendering(VkCommandBuffer cmd_buffer);

    // Framebuffer Fetch & Colortex Blit Simulation Layer (sampler2D colortex0)
    void execute_colortex_blit(VkCommandBuffer cmd_buffer, VkImage src_image, VkImage dst_image, VkExtent2D extent);

    // Native Android Surface Allocator
    VkSurfaceKHR create_android_surface(void* window_handle);

    UnifiedMemoryPool& get_memory_pool() { return m_memory_pool; }

private:
    VkDevice m_device;
    VkPhysicalDevice m_physical_device;
    VkInstance m_instance;
    VkPipelineCache m_pipeline_cache;
    UnifiedMemoryPool m_memory_pool;
    bool m_has_dynamic_rendering;
    bool m_has_rasterization_order_access;
    PFN_vkCmdBeginRenderingKHR m_vkCmdBeginRenderingKHR;
    PFN_vkCmdEndRenderingKHR   m_vkCmdEndRenderingKHR;
    std::mutex m_pipeline_mutex;
};

VulkanPipelineManager& get_pipeline_manager();

} // namespace vulkan

} // namespace turbo_v1

#endif // TURBO_V1_VULKAN_H
