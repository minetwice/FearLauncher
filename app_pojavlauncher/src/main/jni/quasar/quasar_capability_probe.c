//
// Quasar Capability Probe — Vulkan device feature detection via dlopen/dlsym
//
// This file probes the device's Vulkan capabilities by dynamically loading
// libvulkan.so and calling vkGetPhysicalDeviceFeatures / vkGetPhysicalDeviceProperties.
//
// Results are returned as a JSON string to Java for parsing.
//

#include <jni.h>
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define TAG "QuasarProbe"
#include "log.h"

// We define minimal Vulkan types here to be completely self-contained.
// This avoids any dependency on <vulkan/vulkan.h> which may not be available
// at all API levels in the NDK.

#include <stdint.h>

#define VK_MAKE_VERSION(major, minor, patch) \
    (((uint32_t)(major) << 22) | ((uint32_t)(minor) << 12) | (uint32_t)(patch))

#define VK_API_VERSION_1_0 VK_MAKE_VERSION(1, 0, 0)
#define VK_API_VERSION_1_1 VK_MAKE_VERSION(1, 1, 0)
#define VK_API_VERSION_1_2 VK_MAKE_VERSION(1, 2, 0)

#define VK_SUCCESS 0
#define VK_ERROR_INITIALIZATION_FAILED -9

#define VK_STRUCTURE_TYPE_APPLICATION_INFO 0
#define VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO 1

#define VK_MAX_PHYSICAL_DEVICE_NAME_SIZE 256
#define VK_MAX_EXTENSION_NAME_SIZE 256

typedef int32_t VkResult;
typedef uint32_t VkFlags;
typedef struct VkInstance_T* VkInstance;
typedef struct VkPhysicalDevice_T* VkPhysicalDevice;

typedef struct {
    uint32_t apiVersion;
} VkApplicationInfo_part;

typedef struct {
    const void* pNext;
    const void* pApplicationInfo;
    const void* ppEnabledLayerNames;
    uint32_t enabledLayerCount;
    const void* ppEnabledExtensionNames;
    uint32_t enabledExtensionCount;
    const void* pNext2;
} VkInstanceCreateInfo_part;

// VkPhysicalDeviceFeatures — each field is VkBool32 (uint32_t)
// We only care about a subset, but the struct has ~55 fields.
// Rather than define them all, we'll read specific offsets.
// The struct layout is: each VkBool32 is 4 bytes, packed in declaration order.
// We define the full struct based on the Vulkan spec.

typedef struct {
    uint32_t robustBufferAccess;                    // 0
    uint32_t fullDrawIndexUint32;                  // 1
    uint32_t imageCubeArray;                       // 2
    uint32_t independentBlend;                     // 3
    uint32_t geometryShader;                       // 4
    uint32_t tessellationShader;                   // 5
    uint32_t sampleRateShading;                    // 6
    uint32_t dualSrcBlend;                         // 7
    uint32_t logicOp;                              // 8
    uint32_t multiDrawIndirect;                    // 9
    uint32_t drawIndirectFirstInstance;            // 10
    uint32_t depthClamp;                           // 11
    uint32_t depthBiasClamp;                       // 12
    uint32_t fillModeNonSolid;                     // 13
    uint32_t depthBounds;                          // 14
    uint32_t wideLines;                            // 15
    uint32_t largePoints;                          // 16
    uint32_t alphaToOne;                           // 17
    uint32_t multiViewport;                        // 18
    uint32_t samplerAnisotropy;                    // 19
    uint32_t textureCompressionETC2;               // 20
    uint32_t textureCompressionASTC_LDR;           // 21
    uint32_t textureCompressionBC;                 // 22
    uint32_t occlusionQueryPrecise;                // 23
    uint32_t pipelineStatisticsQuery;              // 24
    uint32_t vertexPipelineStoresAndAtomics;       // 25
    uint32_t fragmentStoresAndAtomics;             // 26
    uint32_t shaderTessellationAndGeometryPointSize; // 27
    uint32_t shaderImageGatherExtended;            // 28
    uint32_t shaderStorageImageExtendedFormats;    // 29
    uint32_t shaderStorageImageMultisample;        // 30
    uint32_t shaderStorageImageReadWithoutFormat;   // 31
    uint32_t shaderStorageImageWriteWithoutFormat; // 32
    uint32_t shaderUniformBufferArrayDynamicIndexing; // 33
    uint32_t shaderSampledImageArrayDynamicIndexing; // 34
    uint32_t shaderStorageBufferArrayDynamicIndexing; // 35
    uint32_t shaderStorageImageArrayDynamicIndexing; // 36
    uint32_t shaderClipDistance;                   // 37
    uint32_t shaderCullDistance;                   // 38
    uint32_t shaderFloat64;                        // 39
    uint32_t shaderInt64;                          // 40
    uint32_t shaderInt16;                          // 41
    uint32_t shaderResourceResidency;              // 42
    uint32_t shaderResourceMinLod;                  // 43
    uint32_t sparseBinding;                        // 44
    uint32_t sparseResidencyBuffer;                // 45
    uint32_t sparseResidencyImage2D;                // 46
    uint32_t sparseResidencyImage3D;                // 47
    uint32_t sparseResidency2Samples;              // 48
    uint32_t sparseResidency4Samples;              // 49
    uint32_t sparseResidency8Samples;              // 50
    uint32_t sparseResidency16Samples;             // 51
    uint32_t sparseResidencyAliased;               // 52
    uint32_t variableMultisampleRate;              // 53
    uint32_t inheritedQueries;                     // 54
} VkPhysicalDeviceFeatures_min;

typedef struct {
    uint32_t sType;
    const void* pNext;
    uint32_t apiVersion;
    uint32_t driverVersion;
    uint32_t vendorID;
    uint32_t deviceID;
    uint32_t deviceType;
    char deviceName[VK_MAX_PHYSICAL_DEVICE_NAME_SIZE];
    uint8_t pipelineCacheUUID[16];
    uint32_t limits_pad[64]; // VkPhysicalDeviceLimits is large, we skip it
    uint32_t sparse_pad[2]; // VkPhysicalDeviceSparseProperties
} VkPhysicalDeviceProperties_min;

typedef struct {
    char extensionName[VK_MAX_EXTENSION_NAME_SIZE];
    uint32_t specVersion;
} VkExtensionProperties_min;

// Function pointer typedefs
typedef VkResult (*PFN_vkCreateInstance_ptr)(const void*, const void*, VkInstance*);
typedef void (*PFN_vkDestroyInstance_ptr)(VkInstance, const void*);
typedef VkResult (*PFN_vkEnumeratePhysicalDevices_ptr)(VkInstance, uint32_t*, VkPhysicalDevice*);
typedef void (*PFN_vkGetPhysicalDeviceFeatures_ptr)(VkPhysicalDevice, VkPhysicalDeviceFeatures_min*);
typedef void (*PFN_vkGetPhysicalDeviceProperties_ptr)(VkPhysicalDevice, VkPhysicalDeviceProperties_min*);
typedef VkResult (*PFN_vkEnumerateDeviceExtensionProperties_ptr)(VkPhysicalDevice, const char*, uint32_t*, VkExtensionProperties_min*);

// vkGetInstanceProcAddr returns void* (PFN_vkVoidFunction)
typedef void* (*PFN_vkGetInstanceProcAddr_ptr)(VkInstance, const char*);

// Helper: append a boolean field to JSON string
static void append_bool(char* json, int* pos, int max, const char* key, uint32_t value, int* first) {
    if (*pos < max) {
        *pos += snprintf(json + *pos, max - *pos, "%s\"%s\":%s",
                         *first ? "" : ",", key, value ? "true" : "false");
        *first = 0;
    }
}

// Helper: append a string field to JSON
static void append_string(char* json, int* pos, int max, const char* key, const char* value, int* first) {
    if (*pos < max) {
        // Escape quotes in value (simple approach — device names rarely have quotes)
        *pos += snprintf(json + *pos, max - *pos, "%s\"%s\":\"%s\"",
                         *first ? "" : ",", key, value);
        *first = 0;
    }
}

// Helper: append an int field to JSON
static void append_int(char* json, int* pos, int max, const char* key, uint32_t value, int* first) {
    if (*pos < max) {
        *pos += snprintf(json + *pos, max - *pos, "%s\"%s\":%u",
                         *first ? "" : ",", key, value);
        *first = 0;
    }
}

JNIEXPORT jstring JNICALL
Java_net_kdt_pojavlaunch_quasar_capability_DeviceCapabilityProbe_nativeProbeVulkan(JNIEnv *env, jclass clazz) {
    LOGI("QuasarProbe: Starting Vulkan capability probe...");

    // Step 1: dlopen libvulkan.so
    void* libvulkan = dlopen("libvulkan.so", RTLD_LAZY | RTLD_LOCAL);
    if (!libvulkan) {
        LOGW("QuasarProbe: Failed to load libvulkan.so — Vulkan not available");
        return (*env)->NewStringUTF(env, "{\"available\":false,\"error\":\"dlopen_failed\"}");
    }
    LOGI("QuasarProbe: libvulkan.so loaded successfully");

    // Step 2: Get vkGetInstanceProcAddr
    PFN_vkGetInstanceProcAddr_ptr vkGetInstanceProcAddr =
        (PFN_vkGetInstanceProcAddr_ptr) dlsym(libvulkan, "vkGetInstanceProcAddr");
    if (!vkGetInstanceProcAddr) {
        LOGE("QuasarProbe: vkGetInstanceProcAddr not found");
        dlclose(libvulkan);
        return (*env)->NewStringUTF(env, "{\"available\":false,\"error\":\"no_get_proc_addr\"}");
    }

    // Step 3: Get vkCreateInstance
    PFN_vkCreateInstance_ptr vkCreateInstance =
        (PFN_vkCreateInstance_ptr) vkGetInstanceProcAddr(NULL, "vkCreateInstance");
    if (!vkCreateInstance) {
        LOGE("QuasarProbe: vkCreateInstance not found");
        dlclose(libvulkan);
        return (*env)->NewStringUTF(env, "{\"available\":false,\"error\":\"no_create_instance\"}");
    }

    // Step 4: Create a VkInstance
    // Use VkApplicationInfo with apiVersion = VK_API_VERSION_1_0 for maximum compatibility
    struct {
        uint32_t sType;
        const void* pNext;
        const char* pApplicationName;
        uint32_t applicationVersion;
        const char* pEngineName;
        uint32_t engineVersion;
        uint32_t apiVersion;
    } appInfo = {
        .sType = VK_STRUCTURE_TYPE_APPLICATION_INFO,
        .pNext = NULL,
        .pApplicationName = "QuasarProbe",
        .applicationVersion = 1,
        .pEngineName = "Quasar",
        .engineVersion = 1,
        .apiVersion = VK_API_VERSION_1_0
    };

    struct {
        uint32_t sType;
        const void* pNext;
        const void* pApplicationInfo;
        const void* ppEnabledLayerNames;
        uint32_t enabledLayerCount;
        const void* ppEnabledExtensionNames;
        uint32_t enabledExtensionCount;
    } createInfo = {
        .sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO,
        .pNext = NULL,
        .pApplicationInfo = &appInfo,
        .ppEnabledLayerNames = NULL,
        .enabledLayerCount = 0,
        .ppEnabledExtensionNames = NULL,
        .enabledExtensionCount = 0
    };

    VkInstance instance = NULL;
    VkResult result = vkCreateInstance(&createInfo, NULL, &instance);
    if (result != VK_SUCCESS || !instance) {
        LOGE("QuasarProbe: vkCreateInstance failed with result %d", result);
        dlclose(libvulkan);
        char errJson[128];
        snprintf(errJson, sizeof(errJson), "{\"available\":false,\"error\":\"create_instance_failed\",\"result\":%d}", result);
        return (*env)->NewStringUTF(env, errJson);
    }
    LOGI("QuasarProbe: VkInstance created successfully");

    // Step 5: Get instance-level function pointers
    PFN_vkEnumeratePhysicalDevices_ptr vkEnumeratePhysicalDevices =
        (PFN_vkEnumeratePhysicalDevices_ptr) vkGetInstanceProcAddr(instance, "vkEnumeratePhysicalDevices");
    PFN_vkGetPhysicalDeviceFeatures_ptr vkGetPhysicalDeviceFeatures =
        (PFN_vkGetPhysicalDeviceFeatures_ptr) vkGetInstanceProcAddr(instance, "vkGetPhysicalDeviceFeatures");
    PFN_vkGetPhysicalDeviceProperties_ptr vkGetPhysicalDeviceProperties =
        (PFN_vkGetPhysicalDeviceProperties_ptr) vkGetInstanceProcAddr(instance, "vkGetPhysicalDeviceProperties");
    PFN_vkEnumerateDeviceExtensionProperties_ptr vkEnumerateDeviceExtensionProperties =
        (PFN_vkEnumerateDeviceExtensionProperties_ptr) vkGetInstanceProcAddr(instance, "vkEnumerateDeviceExtensionProperties");
    PFN_vkDestroyInstance_ptr vkDestroyInstance =
        (PFN_vkDestroyInstance_ptr) vkGetInstanceProcAddr(instance, "vkDestroyInstance");

    if (!vkEnumeratePhysicalDevices || !vkGetPhysicalDeviceFeatures || !vkGetPhysicalDeviceProperties || !vkDestroyInstance) {
        LOGE("QuasarProbe: Failed to get instance-level function pointers");
        if (vkDestroyInstance) vkDestroyInstance(instance, NULL);
        dlclose(libvulkan);
        return (*env)->NewStringUTF(env, "{\"available\":false,\"error\":\"missing_functions\"}");
    }

    // Step 6: Enumerate physical devices
    uint32_t deviceCount = 0;
    result = vkEnumeratePhysicalDevices(instance, &deviceCount, NULL);
    if (result != VK_SUCCESS || deviceCount == 0) {
        LOGE("QuasarProbe: No Vulkan physical devices found (result=%d, count=%d)", result, deviceCount);
        vkDestroyInstance(instance, NULL);
        dlclose(libvulkan);
        return (*env)->NewStringUTF(env, "{\"available\":false,\"error\":\"no_devices\"}");
    }
    LOGI("QuasarProbe: Found %d physical device(s)", deviceCount);

    // Get the first physical device
    VkPhysicalDevice physicalDevice = NULL;
    result = vkEnumeratePhysicalDevices(instance, &deviceCount, &physicalDevice);
    if (result != VK_SUCCESS || !physicalDevice) {
        LOGE("QuasarProbe: Failed to get physical device handle");
        vkDestroyInstance(instance, NULL);
        dlclose(libvulkan);
        return (*env)->NewStringUTF(env, "{\"available\":false,\"error\":\"device_get_failed\"}");
    }

    // Step 7: Get physical device features
    VkPhysicalDeviceFeatures_min features;
    memset(&features, 0, sizeof(features));
    vkGetPhysicalDeviceFeatures(physicalDevice, &features);

    // Step 8: Get physical device properties
    VkPhysicalDeviceProperties_min properties;
    memset(&properties, 0, sizeof(properties));
    vkGetPhysicalDeviceProperties(physicalDevice, &properties);
    LOGI("QuasarProbe: Device: %s (vendor=0x%x, api=0x%x)", properties.deviceName, properties.vendorID, properties.apiVersion);

    // Step 9: Detect GPU vendor from vendorID
    const char* gpuVendor = "unknown";
    switch (properties.vendorID) {
        case 0x1002: gpuVendor = "amd"; break;
        case 0x10DE: gpuVendor = "nvidia"; break;
        case 0x8086: gpuVendor = "intel"; break;
        case 0x5143: gpuVendor = "adreno"; break;  // Qualcomm (Adreno)
        case 0x13B5: gpuVendor = "arm"; break;      // ARM (Mali)
        case 0x1010: gpuVendor = "mali"; break;      // ARM (Mali, alternate)
        case 0x10005: gpuVendor = "powervr"; break; // Imagination (PowerVR)
        default:
            // Try to detect from device name
            if (strstr(properties.deviceName, "Adreno") || strstr(properties.deviceName, "adreno")) {
                gpuVendor = "adreno";
            } else if (strstr(properties.deviceName, "Mali") || strstr(properties.deviceName, "mali")) {
                gpuVendor = "mali";
            } else if (strstr(properties.deviceName, "PowerVR") || strstr(properties.deviceName, "powervr")) {
                gpuVendor = "powervr";
            } else if (strstr(properties.deviceName, "Turnip") || strstr(properties.deviceName, "turnip")) {
                gpuVendor = "adreno"; // Turnip is the open-source Adreno driver
            } else if (strstr(properties.deviceName, "llvmpipe") || strstr(properties.deviceName, "swiftshader")) {
                gpuVendor = "software";
            }
            break;
    }
    LOGI("QuasarProbe: Detected GPU vendor: %s", gpuVendor);

    // Step 10: Enumerate device extensions
    char extJson[2048];
    int extPos = 0;
    extPos += snprintf(extJson, sizeof(extJson), "[");

    if (vkEnumerateDeviceExtensionProperties) {
        uint32_t extCount = 0;
        result = vkEnumerateDeviceExtensionProperties(physicalDevice, NULL, &extCount, NULL);
        if (result == VK_SUCCESS && extCount > 0) {
            VkExtensionProperties_min* exts = (VkExtensionProperties_min*)malloc(extCount * sizeof(VkExtensionProperties_min));
            if (exts) {
                result = vkEnumerateDeviceExtensionProperties(physicalDevice, NULL, &extCount, exts);
                if (result == VK_SUCCESS) {
                    LOGI("QuasarProbe: Found %d device extensions", extCount);
                    for (uint32_t i = 0; i < extCount && extPos < (int)sizeof(extJson) - 256; i++) {
                        extPos += snprintf(extJson + extPos, sizeof(extJson) - extPos,
                                          "%s\"%s\"", i > 0 ? "," : "", exts[i].extensionName);
                    }
                }
                free(exts);
            }
        }
    }
    extPos += snprintf(extJson + extPos, sizeof(extJson) - extPos, "]");

    // Step 11: Build JSON result
    char json[8192];
    int pos = 0;
    int first = 1;

    pos += snprintf(json, sizeof(json), "{");

    append_string(json, &pos, sizeof(json), "available", "true", &first);
    append_string(json, &pos, sizeof(json), "deviceName", properties.deviceName, &first);
    append_string(json, &pos, sizeof(json), "gpuVendor", gpuVendor, &first);
    append_int(json, &pos, sizeof(json), "vendorID", properties.vendorID, &first);
    append_int(json, &pos, sizeof(json), "deviceID", properties.deviceID, &first);
    append_int(json, &pos, sizeof(json), "apiVersion", properties.apiVersion, &first);
    append_int(json, &pos, sizeof(json), "driverVersion", properties.driverVersion, &first);
    append_int(json, &pos, sizeof(json), "deviceType", properties.deviceType, &first);

    // Feature flags
    append_bool(json, &pos, sizeof(json), "geometryShader", features.geometryShader, &first);
    append_bool(json, &pos, sizeof(json), "tessellationShader", features.tessellationShader, &first);
    append_bool(json, &pos, sizeof(json), "multiDrawIndirect", features.multiDrawIndirect, &first);
    append_bool(json, &pos, sizeof(json), "shaderStorageImageExtendedFormats", features.shaderStorageImageExtendedFormats, &first);
    append_bool(json, &pos, sizeof(json), "shaderStorageImageWriteWithoutFormat", features.shaderStorageImageWriteWithoutFormat, &first);
    append_bool(json, &pos, sizeof(json), "shaderStorageImageReadWithoutFormat", features.shaderStorageImageReadWithoutFormat, &first);
    append_bool(json, &pos, sizeof(json), "shaderImageGatherExtended", features.shaderImageGatherExtended, &first);
    append_bool(json, &pos, sizeof(json), "vertexPipelineStoresAndAtomics", features.vertexPipelineStoresAndAtomics, &first);
    append_bool(json, &pos, sizeof(json), "fragmentStoresAndAtomics", features.fragmentStoresAndAtomics, &first);
    append_bool(json, &pos, sizeof(json), "shaderInt64", features.shaderInt64, &first);
    append_bool(json, &pos, sizeof(json), "shaderFloat64", features.shaderFloat64, &first);
    append_bool(json, &pos, sizeof(json), "shaderInt16", features.shaderInt16, &first);
    append_bool(json, &pos, sizeof(json), "shaderClipDistance", features.shaderClipDistance, &first);
    append_bool(json, &pos, sizeof(json), "shaderCullDistance", features.shaderCullDistance, &first);
    append_bool(json, &pos, sizeof(json), "sparseBinding", features.sparseBinding, &first);
    append_bool(json, &pos, sizeof(json), "pipelineStatisticsQuery", features.pipelineStatisticsQuery, &first);
    append_bool(json, &pos, sizeof(json), "occlusionQueryPrecise", features.occlusionQueryPrecise, &first);

    // Extensions array
    if (pos < (int)sizeof(json) - 100) {
        pos += snprintf(json + pos, sizeof(json) - pos, ",\"extensions\":%s", extJson);
    }

    pos += snprintf(json + pos, sizeof(json) - pos, "}");

    // Step 12: Cleanup
    vkDestroyInstance(instance, NULL);
    dlclose(libvulkan);

    LOGI("QuasarProbe: Probe complete, JSON length: %d", pos);
    LOGD("QuasarProbe: JSON: %s", json);

    return (*env)->NewStringUTF(env, json);
}
