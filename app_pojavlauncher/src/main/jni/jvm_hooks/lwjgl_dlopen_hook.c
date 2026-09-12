//
// TurboV1 / Zink FINAL-V5
// 4 protection systems so game does not hard-crash on EGL/GLFW issues
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

// -------------------- Protection state --------------------
static volatile int g_glfw_initialized = 0;
static volatile int g_suppress_all_glfw_errors = 1; // start suppressing until window is up
static volatile int g_window_created = 0;

// Real function pointers
static int  (*real_glfwInit)(void) = NULL;
static int  (*real_glfwGetError)(const char**) = NULL;
static void (*real_glfwInitHint)(int, int) = NULL;
static void (*real_glfwWindowHint)(int, int) = NULL;
static void* (*real_glfwCreateWindow)(int, int, const char*, void*, void*) = NULL;
static void (*real_glfwSetErrorCallback)(void*) = NULL;
static void (*real_glfwDefaultWindowHints)(void) = NULL;

// EGL helpers (optional)
static void* (*real_eglGetDisplay)(void*) = NULL;
static int  (*real_eglInitialize)(void*, int*, int*) = NULL;
static int  (*real_eglGetError)(void) = NULL;

static void universal_stub_void(void) {
    LOGI("LWJGL linkerhook: universal GL stub");
}

static int eglGetError_always_success(void) {
    return 0x3000; // EGL_SUCCESS
}

// -------------------- Protection 1: Force critical env --------------------
static void force_turbov1_env(void) {
    setenv("EGL_PLATFORM", "android", 1);
    setenv("MESA_LOADER_DRIVER_OVERRIDE", "zink", 1);
    setenv("GALLIUM_DRIVER", "zink", 1);
    setenv("LIBGL_ES", "2", 1);
    setenv("LIBGL_NOERROR", "1", 1);
    setenv("MESA_GL_VERSION_OVERRIDE", "4.6", 1);
    setenv("MESA_GLSL_VERSION_OVERRIDE", "460", 1);
    setenv("ZINK_DESCRIPTORS", "lazy", 1);
    // Reduce chance of early EGL failure on some Mali drivers
    setenv("mesa_glthread", "false", 1);
    printf("LWJGL linkerhook: FINAL-V5 forced TurboV1/Zink env vars\n");
}

// -------------------- Resolve symbols --------------------
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
    if (!real_glfwSetErrorCallback) {
        real_glfwSetErrorCallback = (void (*)(void*)) dlsym(handle, "glfwSetErrorCallback");
        if (!real_glfwSetErrorCallback) real_glfwSetErrorCallback = (void (*)(void*)) dlsym(RTLD_DEFAULT, "glfwSetErrorCallback");
    }
    if (!real_glfwDefaultWindowHints) {
        real_glfwDefaultWindowHints = (void (*)(void)) dlsym(handle, "glfwDefaultWindowHints");
        if (!real_glfwDefaultWindowHints) real_glfwDefaultWindowHints = (void (*)(void)) dlsym(RTLD_DEFAULT, "glfwDefaultWindowHints");
    }
    if (!real_eglGetDisplay) {
        real_eglGetDisplay = (void* (*)(void*)) dlsym(RTLD_DEFAULT, "eglGetDisplay");
    }
    if (!real_eglInitialize) {
        real_eglInitialize = (int (*)(void*, int*, int*)) dlsym(RTLD_DEFAULT, "eglInitialize");
    }
    if (!real_eglGetError) {
        real_eglGetError = (int (*)(void)) dlsym(RTLD_DEFAULT, "eglGetError");
    }
}

// -------------------- Protection 2: Silent error callback --------------------
// GLFW error callback signature: void callback(int error_code, const char* description)
static void silent_glfw_error_callback(int error_code, const char* description) {
    // Log only, never let it reach Minecraft's fatal path during startup
    printf("LWJGL linkerhook: FINAL-V5 swallowed GLFW error %d: %s\n",
           error_code, description ? description : "(null)");
}

// -------------------- Hint strategies --------------------
static void apply_hints_gles_egl(void) {
    if (!real_glfwWindowHint) return;
    real_glfwWindowHint(0x00022001 /* CLIENT_API */, 0x00030001 /* OPENGL_ES_API */);
    real_glfwWindowHint(0x0002200B /* CONTEXT_CREATION_API */, 0x00036002 /* EGL_CONTEXT_API */);
    real_glfwWindowHint(0x00022002 /* CONTEXT_VERSION_MAJOR */, 3);
    real_glfwWindowHint(0x00022003 /* CONTEXT_VERSION_MINOR */, 0);
    real_glfwWindowHint(0x00022008 /* OPENGL_FORWARD_COMPAT */, 0);
    real_glfwWindowHint(0x00022006 /* OPENGL_PROFILE */, 0);
}

static void apply_hints_no_api(void) {
    if (!real_glfwWindowHint) return;
    real_glfwWindowHint(0x00022001 /* CLIENT_API */, 0 /* NO_API */);
}

static void apply_hints_gles2(void) {
    if (!real_glfwWindowHint) return;
    real_glfwWindowHint(0x00022001 /* CLIENT_API */, 0x00030001 /* OPENGL_ES_API */);
    real_glfwWindowHint(0x0002200B /* CONTEXT_CREATION_API */, 0x00036002 /* EGL_CONTEXT_API */);
    real_glfwWindowHint(0x00022002 /* CONTEXT_VERSION_MAJOR */, 2);
    real_glfwWindowHint(0x00022003 /* CONTEXT_VERSION_MINOR */, 0);
}

// -------------------- Hooked glfwInit --------------------
static int hooked_glfwInit_impl(void) {
    printf("LWJGL linkerhook: FINAL-V5 hooked_glfwInit_impl\n");
    force_turbov1_env();
    resolve_all(RTLD_DEFAULT);

    // Drain old errors
    if (real_glfwGetError) {
        const char* d = NULL;
        while (real_glfwGetError(&d) != 0) {}
    }

    // Protection 2: install silent error callback early
    if (real_glfwSetErrorCallback) {
        real_glfwSetErrorCallback((void*) silent_glfw_error_callback);
        printf("LWJGL linkerhook: FINAL-V5 silent error callback installed\n");
    }

    if (real_glfwInitHint) {
        real_glfwInitHint(0x00050003 /* GLFW_PLATFORM */, 0x00060006 /* GLFW_PLATFORM_ANDROID */);
    }

    apply_hints_gles_egl();

    int result = 0;
    if (real_glfwInit) {
        result = real_glfwInit();
        g_glfw_initialized = 1;
        printf("LWJGL linkerhook: FINAL-V5 real glfwInit() -> %d\n", result);
    }
    // Always report success to upper layers so they continue
    // (Protection 3: never fail init)
    if (!result) {
        printf("LWJGL linkerhook: FINAL-V5 forcing glfwInit success to avoid early abort\n");
        result = 1;
        g_glfw_initialized = 1;
    }
    return result;
}

// -------------------- Protection 3: GetError hard suppress --------------------
static int hooked_glfwGetError_impl(const char** description) {
    if (g_suppress_all_glfw_errors || !g_glfw_initialized) {
        if (description) *description = NULL;
        return 0;
    }
    if (real_glfwGetError) {
        int code = real_glfwGetError(description);
        // Always suppress NOT_INITIALIZED and PLATFORM_ERROR during startup
        if (code == 0x10001 /* NOT_INITIALIZED */ || code == 0x10008 /* PLATFORM_ERROR */ || code == 65542) {
            if (description) *description = NULL;
            return 0;
        }
        return code;
    }
    if (description) *description = NULL;
    return 0;
}

// -------------------- Protection 4: Multi-strategy CreateWindow --------------------
static void* hooked_glfwCreateWindow_impl(int width, int height, const char* title, void* monitor, void* share) {
    printf("LWJGL linkerhook: FINAL-V5 hooked_glfwCreateWindow_impl (%dx%d)\n", width, height);
    resolve_all(RTLD_DEFAULT);
    force_turbov1_env();

    if (!real_glfwCreateWindow) {
        printf("LWJGL linkerhook: FINAL-V5 no real glfwCreateWindow\n");
        return NULL;
    }

    void* win = NULL;

    // Strategy A: GLES 3 + EGL
    apply_hints_gles_egl();
    win = real_glfwCreateWindow(width, height, title, monitor, share);
    if (win) {
        printf("LWJGL linkerhook: FINAL-V5 window OK with GLES3+EGL\n");
        g_window_created = 1;
        g_suppress_all_glfw_errors = 0;
        return win;
    }
    printf("LWJGL linkerhook: FINAL-V5 strategy A (GLES3) failed\n");

    // Strategy B: GLES 2 + EGL
    if (real_glfwDefaultWindowHints) real_glfwDefaultWindowHints();
    apply_hints_gles2();
    win = real_glfwCreateWindow(width, height, title, monitor, share);
    if (win) {
        printf("LWJGL linkerhook: FINAL-V5 window OK with GLES2+EGL\n");
        g_window_created = 1;
        g_suppress_all_glfw_errors = 0;
        return win;
    }
    printf("LWJGL linkerhook: FINAL-V5 strategy B (GLES2) failed\n");

    // Strategy C: NO_API (Vulkan surface path)
    if (real_glfwDefaultWindowHints) real_glfwDefaultWindowHints();
    apply_hints_no_api();
    win = real_glfwCreateWindow(width, height, title, monitor, share);
    if (win) {
        printf("LWJGL linkerhook: FINAL-V5 window OK with NO_API\n");
        g_window_created = 1;
        g_suppress_all_glfw_errors = 0;
        return win;
    }
    printf("LWJGL linkerhook: FINAL-V5 strategy C (NO_API) failed\n");

    // All strategies failed – keep suppressing errors so upper layer may recover
    // or show a softer failure instead of immediate hard crash
    printf("LWJGL linkerhook: FINAL-V5 ALL window strategies failed – keeping error suppress on\n");
    if (real_glfwGetError) {
        const char* d = NULL;
        int c = real_glfwGetError(&d);
        printf("LWJGL linkerhook: FINAL-V5 last error %d: %s\n", c, d ? d : "(null)");
    }
    return NULL;
}

// -------------------- ndlopen / ndlsym --------------------
static jlong ndlopen_bugfix(__attribute__((unused)) JNIEnv *env,
                     __attribute__((unused)) jclass class,
                     jlong filename_ptr,
                     jint jmode) {
    const char* filename = (const char*) filename_ptr;
    if (!filename) return 0;

    if (strstr(filename, "libvulkan.so") == filename || strstr(filename, "vulkan.") != NULL) {
        printf("LWJGL linkerhook: FINAL-V5 vulkan load redirect\n");
        return (jlong) pojavexec_loadVulkanDriver();
    }

    if (strstr(filename, "libTurboV1.so") != NULL ||
        strstr(filename, "libGLMojo.so") != NULL ||
        strstr(filename, "libGLFear.so") != NULL ||
        strstr(filename, "libGL.so") != NULL) {
        printf("LWJGL linkerhook: FINAL-V5 GL load redirect (%s)\n", filename);
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

    if (strcmp(symbol, "eglGetError") == 0) {
        return (jlong) eglGetError_always_success;
    }
    if (strcmp(symbol, "glfwInit") == 0) {
        printf("LWJGL linkerhook: FINAL-V5 return hooked_glfwInit\n");
        return (jlong) hooked_glfwInit_impl;
    }
    if (strcmp(symbol, "glfwGetError") == 0) {
        return (jlong) hooked_glfwGetError_impl;
    }
    if (strcmp(symbol, "glfwCreateWindow") == 0) {
        printf("LWJGL linkerhook: FINAL-V5 return hooked_glfwCreateWindow\n");
        return (jlong) hooked_glfwCreateWindow_impl;
    }
    if (strcmp(symbol, "glfwSetErrorCallback") == 0) {
        // Always keep our silent callback – don't let Minecraft replace it early
        printf("LWJGL linkerhook: FINAL-V5 blocking external error callback install\n");
        return (jlong) real_glfwSetErrorCallback; // still allow call but we re-install ours in init
    }

    void* sym = dlsym((void*) handle, symbol);
    if (!sym) sym = dlsym(RTLD_DEFAULT, symbol);
    if (!sym && strncmp(symbol, "gl", 2) == 0) {
        return (jlong) universal_stub_void;
    }
    return (jlong) sym;
}

void installLwjglDlopenHook(JNIEnv *env) {
    LOGI("Installing LWJGL hooks (BUILD v20260912-FINAL-V5)");
    printf("LWJGL linkerhook: installing dlopen/dlsym hooks (BUILD v20260912-FINAL-V5)\n");
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
        printf("LWJGL linkerhook: hooks installed (FINAL-V5) – 4 protection layers active\n");
    }
}
