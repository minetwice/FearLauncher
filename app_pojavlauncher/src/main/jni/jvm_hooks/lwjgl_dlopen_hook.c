//
// TurboV1 / Zink FINAL-V8 — strong final mechanism
// NO_API window + full context API bridge so Minecraft does not crash on
// glfwMakeContextCurrent / SwapBuffers / etc.
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
static volatile int g_window_created = 0;
static volatile int g_context_current = 0;
static void* g_current_window = NULL;

static int  (*real_glfwInit)(void) = NULL;
static int  (*real_glfwGetError)(const char**) = NULL;
static void (*real_glfwInitHint)(int, int) = NULL;
static void (*real_glfwWindowHint)(int, int) = NULL;
static void* (*real_glfwCreateWindow)(int, int, const char*, void*, void*) = NULL;
static void (*real_glfwDefaultWindowHints)(void) = NULL;
static void (*real_glfwMakeContextCurrent)(void*) = NULL;
static void (*real_glfwSwapBuffers)(void*) = NULL;
static void (*real_glfwSwapInterval)(int) = NULL;
static void* (*real_glfwGetCurrentContext)(void) = NULL;

static void universal_stub_void(void) {}
static int eglGetError_always_success(void) { return 0x3000; }

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
}

static void drain_glfw_errors(void) {
    if (!real_glfwGetError) return;
    const char* d = NULL;
    while (real_glfwGetError(&d) != 0) {}
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
    if (!real_glfwMakeContextCurrent) {
        real_glfwMakeContextCurrent = (void (*)(void*)) dlsym(handle, "glfwMakeContextCurrent");
        if (!real_glfwMakeContextCurrent) real_glfwMakeContextCurrent = (void (*)(void*)) dlsym(RTLD_DEFAULT, "glfwMakeContextCurrent");
    }
    if (!real_glfwSwapBuffers) {
        real_glfwSwapBuffers = (void (*)(void*)) dlsym(handle, "glfwSwapBuffers");
        if (!real_glfwSwapBuffers) real_glfwSwapBuffers = (void (*)(void*)) dlsym(RTLD_DEFAULT, "glfwSwapBuffers");
    }
    if (!real_glfwSwapInterval) {
        real_glfwSwapInterval = (void (*)(int)) dlsym(handle, "glfwSwapInterval");
        if (!real_glfwSwapInterval) real_glfwSwapInterval = (void (*)(int)) dlsym(RTLD_DEFAULT, "glfwSwapInterval");
    }
    if (!real_glfwGetCurrentContext) {
        real_glfwGetCurrentContext = (void* (*)(void)) dlsym(handle, "glfwGetCurrentContext");
        if (!real_glfwGetCurrentContext) real_glfwGetCurrentContext = (void* (*)(void)) dlsym(RTLD_DEFAULT, "glfwGetCurrentContext");
    }
}

static void apply_hints_no_api(void) {
    if (!real_glfwWindowHint) return;
    real_glfwWindowHint(0x00022001, 0); // GLFW_NO_API
}

// ---- glfwInit ----
static int hooked_glfwInit_impl(void) {
    printf("LWJGL linkerhook: FINAL-V8 hooked_glfwInit\n");
    force_turbov1_env();
    resolve_all(RTLD_DEFAULT);
    drain_glfw_errors();

    if (real_glfwInitHint)
        real_glfwInitHint(0x00050003, 0x00060006); // ANDROID
    apply_hints_no_api();

    int result = 0;
    if (real_glfwInit) result = real_glfwInit();
    g_glfw_initialized = 1;
    drain_glfw_errors();
    printf("LWJGL linkerhook: FINAL-V8 glfwInit -> %d (forced ok)\n", result);
    return 1; // always success
}

// ---- glfwGetError: suppress everything dangerous ----
static int hooked_glfwGetError_impl(const char** description) {
    if (real_glfwGetError) {
        const char* d = NULL;
        int code = real_glfwGetError(&d);
        // Suppress all known startup / NO_API related codes
        if (code == 0 || code == 0x10001 || code == 0x10004 || code == 0x10008 ||
            code == 65542 || code == 65546 || code == 0x10007) {
            if (description) *description = NULL;
            return 0;
        }
        // During early phase suppress everything
        if (!g_window_created || !g_context_current) {
            if (description) *description = NULL;
            return 0;
        }
        if (description) *description = d;
        return code;
    }
    if (description) *description = NULL;
    return 0;
}

// ---- glfwCreateWindow: NO_API only ----
static void* hooked_glfwCreateWindow_impl(int width, int height, const char* title, void* monitor, void* share) {
    printf("LWJGL linkerhook: FINAL-V8 CreateWindow %dx%d NO_API\n", width, height);
    resolve_all(RTLD_DEFAULT);
    force_turbov1_env();
    drain_glfw_errors();

    if (!real_glfwCreateWindow) return NULL;

    if (real_glfwDefaultWindowHints) real_glfwDefaultWindowHints();
    apply_hints_no_api();

    void* win = real_glfwCreateWindow(width, height, title, monitor, share);
    drain_glfw_errors();

    if (win) {
        g_window_created = 1;
        g_current_window = win;
        printf("LWJGL linkerhook: FINAL-V8 window OK\n");
    } else {
        printf("LWJGL linkerhook: FINAL-V8 window FAILED\n");
    }
    return win;
}

// ---- glfwMakeContextCurrent: NO-OP success for NO_API ----
static void hooked_glfwMakeContextCurrent_impl(void* window) {
    printf("LWJGL linkerhook: FINAL-V8 MakeContextCurrent %p (no-op safe)\n", window);
    // Do NOT call real — it throws 65546 on NO_API windows
    if (window) {
        g_current_window = window;
        g_context_current = 1;
    } else {
        g_context_current = 0;
    }
    drain_glfw_errors();
}

// ---- glfwGetCurrentContext ----
static void* hooked_glfwGetCurrentContext_impl(void) {
    if (g_context_current) return g_current_window;
    return NULL;
}

// ---- glfwSwapBuffers: try real, never crash ----
static void hooked_glfwSwapBuffers_impl(void* window) {
    if (real_glfwSwapBuffers && window) {
        // On NO_API this may error — drain after
        real_glfwSwapBuffers(window);
        drain_glfw_errors();
    }
}

// ---- glfwSwapInterval: no-op ----
static void hooked_glfwSwapInterval_impl(int interval) {
    (void)interval;
    // no-op — vsync controlled by env / Zink
}

// ---- ndlopen ----
static jlong ndlopen_bugfix(__attribute__((unused)) JNIEnv *env,
                     __attribute__((unused)) jclass class,
                     jlong filename_ptr,
                     jint jmode) {
    const char* filename = (const char*) filename_ptr;
    if (!filename) return 0;

    if (strstr(filename, "libvulkan.so") == filename || strstr(filename, "vulkan.") != NULL) {
        printf("LWJGL linkerhook: FINAL-V8 vulkan redirect\n");
        return (jlong) pojavexec_loadVulkanDriver();
    }
    if (strstr(filename, "libTurboV1.so") || strstr(filename, "libGLMojo.so") ||
        strstr(filename, "libGLFear.so") || strstr(filename, "libGL.so")) {
        printf("LWJGL linkerhook: FINAL-V8 GL redirect (%s)\n", filename);
        const pojavexec_renderspec_t *rspec = pojavexec_getRenderSpec();
        if (rspec && rspec->egl_acquire)
            return (jlong) rspec->egl_acquire(rspec->egl_path);
    }
    return (jlong) dlopen(filename, (int)jmode);
}

// ---- ndlsym ----
static jlong ndlsym_hook(__attribute__((unused)) JNIEnv *env,
                  __attribute__((unused)) jclass class,
                  jlong handle,
                  jlong symbol_ptr) {
    const char* symbol = (const char*) symbol_ptr;
    if (!symbol) return 0;

    resolve_all((void*)handle);

    if (strcmp(symbol, "eglGetError") == 0)
        return (jlong) eglGetError_always_success;
    if (strcmp(symbol, "glfwInit") == 0)
        return (jlong) hooked_glfwInit_impl;
    if (strcmp(symbol, "glfwGetError") == 0)
        return (jlong) hooked_glfwGetError_impl;
    if (strcmp(symbol, "glfwCreateWindow") == 0)
        return (jlong) hooked_glfwCreateWindow_impl;
    if (strcmp(symbol, "glfwMakeContextCurrent") == 0)
        return (jlong) hooked_glfwMakeContextCurrent_impl;
    if (strcmp(symbol, "glfwGetCurrentContext") == 0)
        return (jlong) hooked_glfwGetCurrentContext_impl;
    if (strcmp(symbol, "glfwSwapBuffers") == 0)
        return (jlong) hooked_glfwSwapBuffers_impl;
    if (strcmp(symbol, "glfwSwapInterval") == 0)
        return (jlong) hooked_glfwSwapInterval_impl;

    void* sym = dlsym((void*) handle, symbol);
    if (!sym) sym = dlsym(RTLD_DEFAULT, symbol);
    if (!sym && strncmp(symbol, "gl", 2) == 0)
        return (jlong) universal_stub_void;
    return (jlong) sym;
}

void installLwjglDlopenHook(JNIEnv *env) {
    LOGI("Installing LWJGL hooks (BUILD v20260912-FINAL-V8)");
    printf("LWJGL linkerhook: installing hooks (BUILD v20260912-FINAL-V8) — full context bridge\n");
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
        printf("LWJGL linkerhook: FINAL-V8 hooks installed\n");
    }
}
