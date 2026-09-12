//
// Created by maks on 06.01.2025.
// TurboV1 / Zink FINAL-V4 - OpenGL ES + EGL for Zink, fix EGL init failure
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

static void apply_turbov1_window_hints(void) {
    if (!real_glfwWindowHint) return;

    // Zink on Android works best when GLFW creates an OpenGL ES context via EGL.
    // Mesa/Zink then provides the GLES implementation on top of Vulkan.
    real_glfwWindowHint(0x00022001 /* GLFW_CLIENT_API */, 0x00030001 /* GLFW_OPENGL_ES_API */);
    real_glfwWindowHint(0x0002200B /* GLFW_CONTEXT_CREATION_API */, 0x00036002 /* GLFW_EGL_CONTEXT_API */);
    real_glfwWindowHint(0x00022002 /* GLFW_CONTEXT_VERSION_MAJOR */, 3);
    real_glfwWindowHint(0x00022003 /* GLFW_CONTEXT_VERSION_MINOR */, 0);
    // Don't require a forward-compatible / core profile that may break on GLES
    real_glfwWindowHint(0x00022008 /* GLFW_OPENGL_FORWARD_COMPAT */, 0);
    real_glfwWindowHint(0x00022006 /* GLFW_OPENGL_PROFILE */, 0 /* GLFW_OPENGL_ANY_PROFILE */);
}

// Our hooked glfwInit - sets correct hints for TurboV1/Zink then calls real
static int hooked_glfwInit_impl(void) {
    printf("LWJGL linkerhook: FINAL-V4 TurboV1 hooked_glfwInit_impl executing\n");
    resolve_glfw_funcs(RTLD_DEFAULT);

    // Drain any previous error state
    if (real_glfwGetError) {
        const char* desc = NULL;
        while (real_glfwGetError(&desc) != 0) {
            // drain
        }
    }

    if (real_glfwInitHint) {
        real_glfwInitHint(0x00050003 /* GLFW_PLATFORM */, 0x00060006 /* GLFW_PLATFORM_ANDROID */);
    }

    apply_turbov1_window_hints();

    int result = 0;
    if (real_glfwInit) {
        result = real_glfwInit();
        g_glfw_initialized = 1; // mark attempted either way
        if (result) {
            printf("LWJGL linkerhook: FINAL-V4 real glfwInit() succeeded\n");
        } else {
            printf("LWJGL linkerhook: FINAL-V4 real glfwInit() FAILED\n");
        }
    }
    return result;
}

// Hooked glfwGetError - suppress pre-init and known noisy errors
static int hooked_glfwGetError_impl(const char** description) {
    if (!g_glfw_initialized) {
        if (description) *description = NULL;
        return 0;
    }
    if (real_glfwGetError) {
        int code = real_glfwGetError(description);
        // Suppress "not initialized" even after we marked init (race / double-check paths)
        if (code == 0x10001 /* GLFW_NOT_INITIALIZED */) {
            if (description) *description = NULL;
            return 0;
        }
        return code;
    }
    if (description) *description = NULL;
    return 0;
}

// glfwCreateWindow - re-apply hints then create
static void* hooked_glfwCreateWindow_impl(int width, int height, const char* title, void* monitor, void* share) {
    printf("LWJGL linkerhook: FINAL-V4 TurboV1 hooked_glfwCreateWindow_impl (%dx%d)\n", width, height);
    resolve_glfw_funcs(RTLD_DEFAULT);

    // Re-apply hints right before window creation (Minecraft may override earlier)
    apply_turbov1_window_hints();

    if (real_glfwCreateWindow) {
        void* win = real_glfwCreateWindow(width, height, title, monitor, share);
        if (!win) {
            printf("LWJGL linkerhook: FINAL-V4 glfwCreateWindow returned NULL\n");
            // Drain error for logging
            if (real_glfwGetError) {
                const char* desc = NULL;
                int code = real_glfwGetError(&desc);
                printf("LWJGL linkerhook: FINAL-V4 glfwCreateWindow error %d: %s\n",
                       code, desc ? desc : "(null)");
            }
        } else {
            printf("LWJGL linkerhook: FINAL-V4 glfwCreateWindow succeeded\n");
        }
        return win;
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
        printf("LWJGL linkerhook: FINAL-V4 replacing load for libvulkan.so with custom driver\n");
        return (jlong) pojavexec_loadVulkanDriver();
    }

    // Also catch generic libGL.so loads and redirect to TurboV1 / renderspec when possible
    if (strstr(filename, "libTurboV1.so") != NULL ||
        strstr(filename, "libGLMojo.so") != NULL ||
        strstr(filename, "libGLFear.so") != NULL ||
        strstr(filename, "libGL.so") != NULL) {
        printf("LWJGL linkerhook: FINAL-V4 replacing OpenGL load (%s) with renderspec / TurboV1\n", filename);
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
        printf("LWJGL linkerhook: FINAL-V4 returning hooked_glfwInit_impl\n");
        return (jlong) hooked_glfwInit_impl;
    }

    if (strcmp(symbol, "glfwGetError") == 0) {
        printf("LWJGL linkerhook: FINAL-V4 returning hooked_glfwGetError_impl\n");
        return (jlong) hooked_glfwGetError_impl;
    }

    if (strcmp(symbol, "glfwCreateWindow") == 0) {
        printf("LWJGL linkerhook: FINAL-V4 returning hooked_glfwCreateWindow_impl\n");
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
    LOGI("Installing LWJGL dlopen/dlsym hooks (BUILD v20260912-FINAL-V4)");
    printf("LWJGL linkerhook: installing dlopen/dlsym hooks (BUILD v20260912-FINAL-V4)\n");
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
        printf("LWJGL linkerhook: dlopen/dlsym hooks installed successfully (FINAL-V4)\n");
    }
}
