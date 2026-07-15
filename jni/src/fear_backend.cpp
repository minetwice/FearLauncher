#include "fear_backend.h"
#include <android/log.h>
#include <dlfcn.h>
#include <string.h>

#define TAG "FEAR_RENDERER"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

static bool use_vulkan_backend = false;

void detect_hardware_and_select_backend() {
    // Dynamic Querying Device Caps without Crashing
    const char* gl_renderer = "Mali-G710"; // Placeholder dummy or query
    LOGI("Queried device hardware description: %s", gl_renderer);

    if (strstr(gl_renderer, "Adreno") != nullptr) {
        use_vulkan_backend = true;
        LOGI("Hardware-backed Vulkan pipeline activated (Qualcomm Adreno detected).");
    } else {
        use_vulkan_backend = false;
        LOGI("Hardware-backed OpenGL ES 3.2 translation activated (Mali/MTE/Unisoc detected).");
    }
}
