//
// TurboV1 / Zink FINAL-V7
// V6 proved NO_API creates the window. GLES tries only fire EGL errors that kill Minecraft.
// So: only NO_API + aggressive error drain + suppress.
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

static volatile int g_glfw_initialized = 0;
static volatile int g_suppress_all_glfw_errors = 1;
static volatile int g_window_created = 0;

static int  (*real_glfwInit)(void) = NULL;
static int  (*real_glfwGetError)(const char**) = NULL;
static void (*real_glfwInitHint)(int, int) = NULL;
static void (*real_glfwWindowHint)(int, int) = NULL;
static void* (*real_glfwCreateWindow)(int, int, const char*, void*, void*) = NULL;
static void (*real_glfwDefaultWindowHints)(void) = NULL;

static void universal_stub_void(void) {
    LOGI("LWJGL linkerhook: universal GL stub");
}

static int eglGetError_always_success(void) {
    return 0x3000;
}

static void force_turbov1_env(void) {
    setenv("EGL_PLATFORM", "android", 1);
    setenv("MESA_LOADER_DRIVER_OVERRIDE", "zink", 1);
    setenv("GALLIUM_DRIVER", "zink", 1);
    setenv("LIBGL_ES", "2", 1);
    setenv("LIBGL_NOERROR", "1", 1);
    setenv("MESA_GL_VERSION_OVERRIDE", "4.6", 1);
    setenv("MESA_GLSL_VERSION_OVERRIDE", "460", 1);
    setenv("ZINK_DESCRIPTORS", "lazy", 1);
    setenv("mesa_glthread", "false", 1);
    printf("LWJGL linkerhook: FINAL-V7 forced TurboV1/Zink env\n");
}

static void drain_glfw_errors(void) {
    if (!real_glfwGetError) return;
    const char* d = NULL;
    int code;
    while ((code = real_glfwGetError(&d)) != 0) {
        printf("LWJGL linkerhook: FINAL-V7 drained error %d: %s\n", code, d ? d : "(null)");
    }
}

static void resolve_all(void* handle) {
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
    if (!real_glfwDefaultWindowHints) {
        real_glfwDefaultWindowHints = (void (*)(void)) dlsym(handle, "glfwDefaultWindowHints");
        if (!real_glfwDefaultWindowHints) real_glfwDefaultWindowHints = (void (*)(void)) dlsym(RTLD_DEFAULT, "glfwDefaultWindowHints");
    }
}

static void apply_hints_no_api(void) {
    if (!real_glfwWindowHint) return;
    // Critical for TurboV1/Zink: no GL context from GLFW
    real_glfwWindowHint(0x00022001, 0); // GLFW_CLIENT_API = GLFW_NO_API
}

static int hooked_glfwInit_impl(void) {
    printf("LWJGL linkerhook: FINAL-V7 hooked_glfwInit_impl\n");
    force_turbov1_env();
    resolve_all(RTLD_DEFAULT);
    drain_glfw_errors();

    if (real_glfwInitHint) {
        real_glfwInitHint(0x00050003, 0x00060006); // ANDROID
    }

    // Set NO_API before init so default hints are correct
    apply_hints_no_api();

    int result = 0;
    if (real_glfwInit) {
        result = real_glfwInit();
        printf("LWJGL linkerhook: FINAL-V7 real glfwInit() -> %d\n", result);
    }
    g_glfw_initialized = 1;
    drain_glfw_errors();
    if (!result) {
        printf("LWJGL linkerhook: FINAL-V7 forcing glfwInit success\n");
        result = 1;
    }
    return result;
}

static int hooked_glfwGetError_impl(const char** description) {
    // Always suppress until window is successfully created
    if (g_suppress_all_glfw_errors || !g_glfw_initialized || !g_window_created) {
        // Still drain real queue so it doesn't pile up
        if (real_glfwGetError) {
            const char* d = NULL;
            real_glfwGetError(&d);
        }
        if (description) *description = NULL;
        return 0;
    }
    if (real_glfwGetError) {
        int code = real_glfwGetError(description);
        if (code == 0x10001 || code == 0x10008 || code == 65542 || code == 0x10004) {
            if (description) *description = NULL;
            return 0;
        }
        return code;
    }
    if (description) *description = NULL;
    return 0;
}

static void* hooked_glfwCreateWindow_impl(int width, int height, const char* title, void* monitor, void* share) {
    printf("LWJGL linkerhook: FINAL-V7 hooked_glfwCreateWindow_impl (%dx%d) — NO_API only\n", width, height);
    resolve_all(RTLD_DEFAULT);
    force_turbov1_env();
    drain_glfw_errors();

    if (!real_glfwCreateWindow) return NULL;

    if (real_glfwDefaultWindowHints) real_glfwDefaultWindowHints();
    apply_hints_no_api();

    void* win = real_glfwCreateWindow(width, height, title, monitor, share);

    // Drain any errors produced during create so Java side does not see them
    drain_glfw_errors();

    if (win) {
        printf("LWJGL linkerhook: FINAL-V7 window OK with NO_API\n");
        g_window_created = 1;
        // Keep suppress on a bit longer – Minecraft may still poll GetError
        // g_suppress_all_glfw_errors stays 1 until we are more confident
        return win;
    }

    printf("LWJGL linkerhook: FINAL-V7 NO_API CreateWindow failed\n");
    drain_glfw_errors();
    return NULL;
}

static jlong ndlopen_bugfix(__attribute__((unused)) JNIEnv *env,
                     __attribute__((unused)) jclass class,
                     jlong filename_ptr,
                     jint jmode) {
    const char* filename = (const char*) filename_ptr;
    if (!filename) return 0;

    if (strstr(filename, "libvulkan.so") == filename || strstr(filename, "vulkan.") != NULL) {
        printf("LWJGL linkerhook: FINAL-V7 vulkan redirect\n");
        return (jlong) pojavexec_loadVulkanDriver();
    }

    if (strstr(filename, "libTurboV1.so") != NULL ||
        strstr(filename, "libGLMojo.so") != NULL ||
        strstr(filename, "libGLFear.so") != NULL ||
        strstr(filename, "libGL.so") != NULL) {
        printf("LWJGL linkerhook: FINAL-V7 GL redirect (%s)\n", filename);
        const pojavexec_renderspec_t *rspec = pojavexec_getRenderSpec();
        if (rspec && rspec->egl_acquire) {
            return (jlong) rspec->egl_acquire(rspec->egl_path);
        }
    }

    return (jlong) dlopen(filename, (int)jmode);
}

static jlong ndlsym_hook(__attribute__((unused)) JNIEnv *env,
                  __attribute__((unused)) jclass class,
                  jlong handle,
                  jlong symbol_ptr) {
    const char* symbol = (const char*) symbol_ptr;
    if (!symbol) return 0;

    resolve_all((void*)handle);

    if (strcmp(symbol, "eglGetError") == 0)
        return (jlong) eglGetError_always_success;
    if (strcmp(symbol, "glfwInit") == 0) {
        printf("LWJGL linkerhook: FINAL-V7 return hooked_glfwInit\n");
        return (jlong) hooked_glfwInit_impl;
    }
    if (strcmp(symbol, "glfwGetError") == 0)
        return (jlong) hooked_glfwGetError_impl;
    if (strcmp(symbol, "glfwCreateWindow") == 0) {
        printf("LWJGL linkerhook: FINAL-V7 return hooked_glfwCreateWindow\n");
        return (jlong) hooked_glfwCreateWindow_impl;
    }

    void* sym = dlsym((void*) handle, symbol);
    if (!sym) sym = dlsym(RTLD_DEFAULT, symbol);
    if (!sym && strncmp(symbol, "gl", 2) == 0)
        return (jlong) universal_stub_void;
    return (jlong) sym;
}

void installLwjglDlopenHook(JNIEnv *env) {
    LOGI("Installing LWJGL hooks (BUILD v20260912-FINAL-V7)");
    printf("LWJGL linkerhook: installing hooks (BUILD v20260912-FINAL-V7)\n");
    force_turbov1_env();

    jclass dynamicLinkLoader = (*env)->FindClass(env, "org/lwjgl/system/linux/DynamicLinkLoader");
    if (dynamicLinkLoader == NULL) {
        LOGE("Failed to find DynamicLinkLoader");
        (*env)->ExceptionClear(env);
        return;
    }
    JNINativeMethod hooks[] = {
            {"ndlopen", "(JI)J", &ndlopen_bugfix},
            {"ndlsym",  "(JJ)J", &ndlsym_hook}
    };
    if ((*env)->RegisterNatives(env, dynamicLinkLoader, hooks, 2) != 0) {
        LOGE("Failed to register hooks");
        (*env)->ExceptionClear(env);
    } else {
        printf("LWJGL linkerhook: hooks installed (FINAL-V7)\n");
    }
}
