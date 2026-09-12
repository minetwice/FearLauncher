//
// TurboV1 / Zink FINAL-V6
// Safe protections only (no raw C error callback - that broke LWJGL)
// 1) Force env  2) GetError hard suppress  3) Multi-strategy CreateWindow  4) Force glfwInit success
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
    return 0x3000; // EGL_SUCCESS
}

// Protection 1: Force critical env
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
    printf("LWJGL linkerhook: FINAL-V6 forced TurboV1/Zink env vars\n");
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

static void apply_hints_gles_egl(void) {
    if (!real_glfwWindowHint) return;
    real_glfwWindowHint(0x00022001, 0x00030001); // CLIENT_API = OPENGL_ES_API
    real_glfwWindowHint(0x0002200B, 0x00036002); // CONTEXT_CREATION_API = EGL
    real_glfwWindowHint(0x00022002, 3);          // MAJOR 3
    real_glfwWindowHint(0x00022003, 0);          // MINOR 0
    real_glfwWindowHint(0x00022008, 0);
    real_glfwWindowHint(0x00022006, 0);
}

static void apply_hints_gles2(void) {
    if (!real_glfwWindowHint) return;
    real_glfwWindowHint(0x00022001, 0x00030001);
    real_glfwWindowHint(0x0002200B, 0x00036002);
    real_glfwWindowHint(0x00022002, 2);
    real_glfwWindowHint(0x00022003, 0);
}

static void apply_hints_no_api(void) {
    if (!real_glfwWindowHint) return;
    real_glfwWindowHint(0x00022001, 0); // NO_API
}

// Protection 4: force init success path
static int hooked_glfwInit_impl(void) {
    printf("LWJGL linkerhook: FINAL-V6 hooked_glfwInit_impl\n");
    force_turbov1_env();
    resolve_all(RTLD_DEFAULT);

    if (real_glfwGetError) {
        const char* d = NULL;
        while (real_glfwGetError(&d) != 0) {}
    }

    if (real_glfwInitHint) {
        real_glfwInitHint(0x00050003, 0x00060006); // PLATFORM = ANDROID
    }

    apply_hints_gles_egl();

    int result = 0;
    if (real_glfwInit) {
        result = real_glfwInit();
        printf("LWJGL linkerhook: FINAL-V6 real glfwInit() -> %d\n", result);
    }
    g_glfw_initialized = 1;
    // Always return success so Minecraft continues
    if (!result) {
        printf("LWJGL linkerhook: FINAL-V6 forcing glfwInit success\n");
        result = 1;
    }
    return result;
}

// Protection 2: GetError hard suppress
static int hooked_glfwGetError_impl(const char** description) {
    if (g_suppress_all_glfw_errors || !g_glfw_initialized) {
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

// Protection 3: Multi-strategy CreateWindow
static void* hooked_glfwCreateWindow_impl(int width, int height, const char* title, void* monitor, void* share) {
    printf("LWJGL linkerhook: FINAL-V6 hooked_glfwCreateWindow_impl (%dx%d)\n", width, height);
    resolve_all(RTLD_DEFAULT);
    force_turbov1_env();

    if (!real_glfwCreateWindow) return NULL;

    void* win = NULL;

    // A: GLES3 + EGL
    apply_hints_gles_egl();
    win = real_glfwCreateWindow(width, height, title, monitor, share);
    if (win) {
        printf("LWJGL linkerhook: FINAL-V6 window OK GLES3+EGL\n");
        g_window_created = 1;
        g_suppress_all_glfw_errors = 0;
        return win;
    }
    printf("LWJGL linkerhook: FINAL-V6 strategy A failed\n");

    // B: GLES2 + EGL
    if (real_glfwDefaultWindowHints) real_glfwDefaultWindowHints();
    apply_hints_gles2();
    win = real_glfwCreateWindow(width, height, title, monitor, share);
    if (win) {
        printf("LWJGL linkerhook: FINAL-V6 window OK GLES2+EGL\n");
        g_window_created = 1;
        g_suppress_all_glfw_errors = 0;
        return win;
    }
    printf("LWJGL linkerhook: FINAL-V6 strategy B failed\n");

    // C: NO_API
    if (real_glfwDefaultWindowHints) real_glfwDefaultWindowHints();
    apply_hints_no_api();
    win = real_glfwCreateWindow(width, height, title, monitor, share);
    if (win) {
        printf("LWJGL linkerhook: FINAL-V6 window OK NO_API\n");
        g_window_created = 1;
        g_suppress_all_glfw_errors = 0;
        return win;
    }
    printf("LWJGL linkerhook: FINAL-V6 strategy C failed – all strategies exhausted\n");

    if (real_glfwGetError) {
        const char* d = NULL;
        int c = real_glfwGetError(&d);
        printf("LWJGL linkerhook: FINAL-V6 last error %d: %s\n", c, d ? d : "(null)");
    }
    return NULL;
}

static jlong ndlopen_bugfix(__attribute__((unused)) JNIEnv *env,
                     __attribute__((unused)) jclass class,
                     jlong filename_ptr,
                     jint jmode) {
    const char* filename = (const char*) filename_ptr;
    if (!filename) return 0;

    if (strstr(filename, "libvulkan.so") == filename || strstr(filename, "vulkan.") != NULL) {
        printf("LWJGL linkerhook: FINAL-V6 vulkan redirect\n");
        return (jlong) pojavexec_loadVulkanDriver();
    }

    if (strstr(filename, "libTurboV1.so") != NULL ||
        strstr(filename, "libGLMojo.so") != NULL ||
        strstr(filename, "libGLFear.so") != NULL ||
        strstr(filename, "libGL.so") != NULL) {
        printf("LWJGL linkerhook: FINAL-V6 GL redirect (%s)\n", filename);
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
        printf("LWJGL linkerhook: FINAL-V6 return hooked_glfwInit\n");
        return (jlong) hooked_glfwInit_impl;
    }
    if (strcmp(symbol, "glfwGetError") == 0)
        return (jlong) hooked_glfwGetError_impl;
    if (strcmp(symbol, "glfwCreateWindow") == 0) {
        printf("LWJGL linkerhook: FINAL-V6 return hooked_glfwCreateWindow\n");
        return (jlong) hooked_glfwCreateWindow_impl;
    }

    void* sym = dlsym((void*) handle, symbol);
    if (!sym) sym = dlsym(RTLD_DEFAULT, symbol);
    if (!sym && strncmp(symbol, "gl", 2) == 0)
        return (jlong) universal_stub_void;
    return (jlong) sym;
}

void installLwjglDlopenHook(JNIEnv *env) {
    LOGI("Installing LWJGL hooks (BUILD v20260912-FINAL-V6)");
    printf("LWJGL linkerhook: installing hooks (BUILD v20260912-FINAL-V6)\n");
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
        printf("LWJGL linkerhook: hooks installed (FINAL-V6)\n");
    }
}
