#include "fear_backend.h"
#include <android/log.h>
#include <dlfcn.h>
#include <string.h>
#include <cstring>
#include <cstdlib>

#define TAG "FEAR_RENDERER"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

static bool use_vulkan_backend = false;

bool isUsingZink() {
    const char* galliumDriver = getenv("GALLIUM_DRIVER");
    return galliumDriver != nullptr && strcmp(galliumDriver, "zink") == 0;
}

void detect_hardware_and_select_backend() {
    if (isUsingZink()) {
        LOGI("[FearRender] backend=ZINK (Vulkan: 1.3)");
    } else {
        LOGI("[FearRender] backend=GLES (direct, no Zink)");
    }
}

// Module 4 Implementation: Adreno & Mali GPU Specific Workarounds
FearGPUWorkarounds fear_get_gpu_workarounds() {
    FearGPUWorkarounds wa = {};

    typedef const unsigned char* (*glGetString_pfn)(unsigned int);
    static glGetString_pfn real_glGetString = (glGetString_pfn)dlsym(RTLD_NEXT, "glGetString");

    const char* renderer = nullptr;
    if (real_glGetString) {
        renderer = (const char*)real_glGetString(0x1F01 /* GL_RENDERER */);
    }
    if (!renderer) {
        renderer = "Generic GPU";
    }

    LOGI("[FearBackend Workarounds] Detecting GPU via glGetString: %s", renderer);

    if (strstr(renderer, "Adreno") || strstr(renderer, "adreno") || strstr(renderer, "Qualcomm")) {
        wa.is_adreno = true;
        wa.bypass_spirv_validation_fail = true; // Qualcomm Adreno driver SPIR-V validation workaround
        wa.min_uniform_buffer_offset_alignment = 64;
        LOGI("[FearBackend Workarounds] Qualcomm Adreno detected: SPIR-V driver validation bypass enabled.");
    } else if (strstr(renderer, "Mali") || strstr(renderer, "mali") || strstr(renderer, "ARM")) {
        wa.is_mali = true;
        wa.min_uniform_buffer_offset_alignment = 256; // ARM Mali UBO alignment quirk workaround
        wa.bypass_spirv_validation_fail = false;
        LOGI("[FearBackend Workarounds] ARM Mali detected: Uniform buffer alignment forced to 256 bytes.");
    } else {
        wa.min_uniform_buffer_offset_alignment = 16;
        LOGI("[FearBackend Workarounds] Generic GPU architecture initialized.");
    }

    return wa;
}
