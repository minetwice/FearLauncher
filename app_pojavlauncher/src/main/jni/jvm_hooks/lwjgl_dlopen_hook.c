//
// Created by maks on 06.01.2025.
// Cleaned & fixed version for TurboV1 / Zink (v2) - properly suppress pre-init GLFW errors
//

#include "jvm_hooks.h"

#include <android/api-level.h>
#include <dlfcn.h>
#include <string.h>
#include <stdlib.h>
#include <pthread.h>

#define TAG __FILE_NAME__
#include <log.h>
#include "../pojavexec.h"

static void universal_stub_void(void) {
    LOGI("LWJGL linkerhook: universal GL stub executed");
}

static int eglGetError_stub(void) {
    return 0x3000; // EGL_SUCCESS
}

// Track whether real glfwInit has been called
static volatile int g_glfw_initialized = 0;

// Real function pointers (resolved once)
static int (*real_glfwInit)(void) = NULL;
static int (*real_glfwGetError)(const char**) = NULL;
static void (*real_glfwInitHint)(int, int) = NULL;
static void (*real_glfwWindowHint)(int, int) = NULL;
static void* (*real_glfwCreateWindow)(int, int, const char*, void*, void*) = NULL;

static void resolve_glfw_funcs(void* handle) {
    if (!real_glfwInit) {
        real_glfwInit = (int (*)(void)) dlsym(handle, "glfwInit");
        if (!real_glfwInit) real_glfwInit = (int (*)(void)) dlsym(RTLD_DEFAULT, "glfwInit");
    }
    if (!real_glfwGetError) {
        real_glfwGetError = (int (*)(const char**)) dlsym(handle, "glfwGetError");
        if (!real_glfwGetError) real_glfwGetError = (int (*)(const char**)) dlsym(RTLD_DEFAULT, "glfwGetError");
    }
    if (!real_glfwInitHint) {
        real_glfwInitHint = (void (*)(int, int)) dlsym(handle, "glfwInitHint");
        if (!real_glfwInitHint) real_glfwInitHint = (void (*)(int, int)) dlsym(RTLD_DEFAULT, "glfwInitHint");
    }
    if (!real_glfwWindowHint) {
        real_glfwWindowHint = (void (*)(int, int)) dlsym(handle, "glfwWindowHint");
        if (!real_glfwWindowHint) real_glfwWindowHint = (void (*)(int, int)) dlsym(RTLD_DEFAULT, "glfwWindowHint");
    }
    if (!real_glfwCreateWindow) {
        real_glfwCreateWindow = (void* (*)(int, int, const char*, void*, void*)) dlsym(handle, "glfwCreateWindow");
        if (!real_glfwCreateWindow) real_glfwCreateWindow = (void* (*)(int, int, const char*, void*, void*)) dlsym(RTLD_DEFAULT, "glfwCreateWindow");
    }
}

// Our hooked glfwInit - sets correct hints for TurboV1/Zink then calls real
static int hooked_glfwInit_impl(void) {
    printf("LWJGL linkerhook: TurboV1 hooked_glfwInit_impl executing\n");
    resolve_glfw_funcs(RTLD_DEFAULT);

    if (real_glfwInitHint) {
        // Force Android platform
        real_glfwInitHint(0x00050003 /* GLFW_PLATFORM */, 0x00060006 /* GLFW_PLATFORM_ANDROID */);
    }

    if (real_glfwWindowHint) {
        // For Zink / TurboV1 we prefer NO_API so the surface can be Vulkan-based.
        // This matches most working Zink setups on Android.
        real_glfwWindowHint(0x00022001 /* GLFW_CLIENT_API */, 0 /* GLFW_NO_API */);
        // Do NOT force OpenGL ES here - Zink provides the GL layer.
    }

    int result = 0;
    if (real_glfwInit) {
        result = real_glfwInit();
        if (result) {
            g_glfw_initialized = 1;
            printf("LWJGL linkerhook: real glfwInit() succeeded\n");
        } else {
            printf("LWJGL linkerhook: real glfwInit() FAILED\n");
        }
    }
    return result;
}

// Hooked glfwGetError - suppress "not initialized" errors before init
static int hooked_glfwGetError_impl(const char** description) {
    if (!g_glfw_initialized) {
        if (description) *description = NULL;
        return 0; // No error
    }
    if (real_glfwGetError) {
        return real_glfwGetError(description);
    }
    if (description) *description = NULL;
    return 0;
}

// Simple passthrough for glfwCreateWindow (can be extended later for Vulkan surface)
static void* hooked_glfwCreateWindow_impl(int width, int height, const char* title, void* monitor, void* share) {
    printf("LWJGL linkerhook: TurboV1 hooked_glfwCreateWindow_impl (%dx%d)\n", width, height);
    resolve_glfw_funcs(RTLD_DEFAULT);
    if (real_glfwCreateWindow) {
        return real_glfwCreateWindow(width, height, title, monitor, share);
    }
    return NULL;
}

/**
 * ndlopen hook
 */
static jlong ndlopen_bugfix(__attribute__((unused)) JNIEnv *env,
                     __attribute__((unused)) jclass class,
                     jlong filename_ptr,
                     jint jmode) {
    const char* filename = (const char*) filename_ptr;
    if (!filename) return 0;

    if (strstr(filename, "libvulkan.so") == filename || strstr(filename, "vulkan.") != NULL) {
        printf("LWJGL linkerhook: replacing load for libvulkan.so with custom driver\n");
        return (jlong) pojavexec_loadVulkanDriver();
    }

    if (strstr(filename, "libTurboV1.so") != NULL ||
        strstr(filename, "libGLMojo.so") != NULL ||
        strstr(filename, "libGLFear.so") != NULL) {
        printf("LWJGL linkerhook: replacing OpenGL with renderspec / TurboV1 driver\n");
        const pojavexec_renderspec_t *rspec = pojavexec_getRenderSpec();
        if (rspec && rspec->egl_acquire) {
            return (jlong) rspec->egl_acquire(rspec->egl_path);
        }
    }

    return (jlong) dlopen(filename, (int)jmode);
}

/**
 * ndlsym hook
 */
static jlong ndlsym_hook(__attribute__((unused)) JNIEnv *env,
                  __attribute__((unused)) jclass class,
                  jlong handle,
                  jlong symbol_ptr) {
    const char* symbol = (const char*) symbol_ptr;
    if (!symbol) return 0;

    resolve_glfw_funcs((void*)handle);

    if (strcmp(symbol, "eglGetError") == 0) {
        return (jlong) eglGetError_stub;
    }

    if (strcmp(symbol, "glfwInit") == 0) {
        printf("LWJGL linkerhook: returning TurboV1 hooked_glfwInit_impl\n");
        return (jlong) hooked_glfwInit_impl;
    }

    if (strcmp(symbol, "glfwGetError") == 0) {
        printf("LWJGL linkerhook: returning TurboV1 hooked_glfwGetError_impl (suppress pre-init)\n");
        return (jlong) hooked_glfwGetError_impl;
    }

    if (strcmp(symbol, "glfwCreateWindow") == 0) {
        printf("LWJGL linkerhook: returning TurboV1 hooked_glfwCreateWindow_impl\n");
        return (jlong) hooked_glfwCreateWindow_impl;
    }

    void* sym = dlsym((void*) handle, symbol);
    if (!sym) {
        sym = dlsym(RTLD_DEFAULT, symbol);
    }
    if (!sym && strncmp(symbol, "gl", 2) == 0) {
        return (jlong) universal_stub_void;
    }
    return (jlong) sym;
}

void installLwjglDlopenHook(JNIEnv *env) {
    LOGI("Installing LWJGL dlopen/dlsym hooks (BUILD v20260912-CLEAN-V2)");
    printf("LWJGL linkerhook: installing dlopen/dlsym hooks (BUILD v20260912-CLEAN-V2)\n");
    jclass dynamicLinkLoader = (*env)->FindClass(env, "org/lwjgl/system/linux/DynamicLinkLoader");
    if (dynamicLinkLoader == NULL) {
        LOGE("Failed to find DynamicLinkLoader class");
        (*env)->ExceptionClear(env);
        return;
    }
    JNINativeMethod hooks[] = {
            {"ndlopen", "(JI)J", &ndlopen_bugfix},
            {"ndlsym",  "(JJ)J", &ndlsym_hook}
    };
    if ((*env)->RegisterNatives(env, dynamicLinkLoader, hooks, 2) != 0) {
        LOGE("Failed to register hooked methods");
        (*env)->ExceptionClear(env);
    } else {
        printf("LWJGL linkerhook: dlopen/dlsym hooks installed successfully (V2)\n");
    }
}
