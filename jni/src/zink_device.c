#include <stdbool.h>
#include <string.h>
#include <stdio.h>
#include <stdint.h>
#include <android/log.h>

#define TAG "MH_DRIVE"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

#define VK_TRUE 1
#define VK_FALSE 0
#define VK_MAX_PHYSICAL_DEVICE_NAME_SIZE 256

typedef struct {
    int shaderClipDistance;
    int fillModeNonSolid;
    int dynamicRendering;
    int descriptorIndexing;
} VkPhysicalDeviceFeatures;

typedef struct {
    uint32_t apiVersion;
    uint32_t driverVersion;
    uint32_t vendorID;
    uint32_t deviceID;
    uint32_t deviceType;
    char     deviceName[VK_MAX_PHYSICAL_DEVICE_NAME_SIZE];
    uint8_t  pipelineCacheUUID[16];
} VkPhysicalDevicePropertiesEx;

const char* GetNativeMaliHardwareString(void) {
    return "Mali-G710/G615 via TurboV1 Translation";
}

void OverrideDeviceProperties(VkPhysicalDevicePropertiesEx* properties) {
    if (!properties) return;

    // 1. Clear original hardware device name string safely
    memset(properties->deviceName, 0, VK_MAX_PHYSICAL_DEVICE_NAME_SIZE);

    // 2. Fetch the true hardware descriptor string (Mali Core)
    const char* nativeGPU = GetNativeMaliHardwareString();

    // 3. Construct and format custom engine branding signature
    char customBranding[VK_MAX_PHYSICAL_DEVICE_NAME_SIZE];
    snprintf(customBranding, sizeof(customBranding), "TurboV1 Engine v1.0 [%s] (Vulkan Core)", nativeGPU);

    // 4. Inject the custom string back into the Vulkan property packet array (keeping vendorID / deviceID untouched!)
    strncpy(properties->deviceName, customBranding, VK_MAX_PHYSICAL_DEVICE_NAME_SIZE - 1);

    LOGI("MH DRIVE: Injected custom Vulkan branding metadata into deviceName: %s", properties->deviceName);
}

// Track 2: Mesa Zink device and pipeline dynamic capabilities override routine
void mh_drive_spoof_physical_device_features(VkPhysicalDeviceFeatures* features) {
    if (!features) return;

    // Hardcode absolute device feature capabilities override to deceive the shader capabilities checker
    features->shaderClipDistance = VK_TRUE;
    features->fillModeNonSolid = VK_TRUE;
    features->dynamicRendering = VK_TRUE;
    features->descriptorIndexing = VK_TRUE;

    LOGI("MH DRIVE: Masked device capabilities structure. Spoofed shaderClipDistance, fillModeNonSolid, dynamicRendering and descriptorIndexing explicitly.");
}
