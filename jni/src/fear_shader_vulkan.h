#ifndef FEAR_SHADER_VULKAN_H
#define FEAR_SHADER_VULKAN_H
#include <string>
// Vulkan-to-GLES translation layers and bridge mappings
struct VulkanBridgeState {
    bool enableVulkanBridge = true;
    int maxSPIRVVersion = 100;
};
#endif
