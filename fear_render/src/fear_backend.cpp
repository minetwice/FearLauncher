#include "fear_backend.h"
#include <dlfcn.h>
#include <android/log.h>
#include <vector>
#include <vulkan/vulkan.h>

#define LOG_TAG "FEAR_RENDERER"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

void FearBackendManager::detectAndInitializeBackend() {
    LOGI("Auto-Detecting GPU hardware features & Vulkan driver support...");

    void* vulkan_lib = dlopen("libvulkan.so", RTLD_NOW | RTLD_GLOBAL);
    if (!vulkan_lib) {
        LOGI("Vulkan driver not found. Falling back to GLES 3.2 Emulation Mode.");
        m_active_backend = RenderBackendType::GLES_32_EMULATION;
        m_capabilities = {false, false, true, false}; // GLES 3.2 defaults
        return;
    }

    PFN_vkCreateInstance vkCreateInstance = (PFN_vkCreateInstance)dlsym(vulkan_lib, "vkCreateInstance");
    PFN_vkEnumeratePhysicalDevices vkEnumeratePhysicalDevices = (PFN_vkEnumeratePhysicalDevices)dlsym(vulkan_lib, "vkEnumeratePhysicalDevices");
    PFN_vkGetPhysicalDeviceFeatures vkGetPhysicalDeviceFeatures = (PFN_vkGetPhysicalDeviceFeatures)dlsym(vulkan_lib, "vkGetPhysicalDeviceFeatures");

    if (!vkCreateInstance || !vkEnumeratePhysicalDevices || !vkGetPhysicalDeviceFeatures) {
        LOGE("Vulkan symbols could not be fully loaded. Forcing GLES 3.2 Emulation Mode.");
        m_active_backend = RenderBackendType::GLES_32_EMULATION;
        dlclose(vulkan_lib);
        return;
    }

    // Safely configure and create standard VkInstance using precise NDK structures
    VkApplicationInfo app_info{};
    app_info.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    app_info.apiVersion = VK_API_VERSION_1_0;

    VkInstanceCreateInfo create_info{};
    create_info.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    create_info.pApplicationInfo = &app_info;

    VkInstance instance = nullptr;
    VkResult res = vkCreateInstance(&create_info, nullptr, &instance);
    if (res != VK_SUCCESS || !instance) {
        LOGW("Failed to create dummy Vulkan instance. Fallback to GLES 3.2 Emulation.");
        m_active_backend = RenderBackendType::GLES_32_EMULATION;
        dlclose(vulkan_lib);
        return;
    }

    uint32_t gpu_count = 0;
    res = vkEnumeratePhysicalDevices(instance, &gpu_count, nullptr);
    if (res == VK_SUCCESS && gpu_count > 0) {
        std::vector<VkPhysicalDevice> gpus(gpu_count);
        vkEnumeratePhysicalDevices(instance, &gpu_count, gpus.data());

        VkPhysicalDeviceFeatures features;
        vkGetPhysicalDeviceFeatures(gpus[0], &features);

        m_capabilities.has_fill_mode_non_solid = features.fillModeNonSolid;
        m_capabilities.has_shader_clip_distance = features.shaderClipDistance;
        m_capabilities.has_geometry_shader = features.geometryShader;

        LOGI("Vulkan GPU Features: fillModeNonSolid=%d, shaderClipDistance=%d, geometryShader=%d",
             m_capabilities.has_fill_mode_non_solid, m_capabilities.has_shader_clip_distance, m_capabilities.has_geometry_shader);

        // If critical features like fillModeNonSolid or shaderClipDistance are missing,
        // we use our software emulator fallback layers instead of risking GPU crashes.
        if (!m_capabilities.has_fill_mode_non_solid || !m_capabilities.has_shader_clip_distance) {
            LOGW("Missing hardware capabilities for stable Vulkan-Zink translation. Triggering GLES 3.2 Emulation Fallback.");
            m_active_backend = RenderBackendType::GLES_32_EMULATION;
        } else {
            LOGI("All hardware capabilities met. Initializing High-Performance Vulkan backend.");
            m_active_backend = RenderBackendType::VULKAN_HARDWARE;
        }
    } else {
        LOGW("No physical GPUs detected under Vulkan. Switching to OpenGL ES 3.2 Emulation.");
        m_active_backend = RenderBackendType::GLES_32_EMULATION;
    }

    dlclose(vulkan_lib);
}
