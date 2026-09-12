//
// TurboV1 / Zink FINAL-V9
// Critical: do NOT force EGL_PLATFORM=android — Java side intentionally skips it for Zink.
// Prefer real GL/ES context so LWJGL createCapabilities works.
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
static volatile int g_has_gl_context = 0; // 1 if window has real GL/ES context
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
    // IMPORTANT: Do NOT set EGL_PLATFORM=android here.
    // JREUtils intentionally omits it for turbov1/vulkan_zink so Mesa can pick its path.
    unsetenv("EGL_PLATFORM");

    setenv("MESA_LOADER_DRIVER_OVERRIDE", "zink", 1);
    setenv("GALLIUM_DRIVER", "zink", 1);
    setenv("LIBGL_NOERROR", "1", 1);
    setenv("MESA_GL_VERSION_OVERRIDE", "4.6", 1);
    setenv("MESA_GLSL_VERSION_OVERRIDE", "460", 1);
    setenv("ZINK_DESCRIPTORS", "lazy", 1);
    setenv("mesa_glthread", "false", 1);
    setenv("ZINK_DEBUG", "", 1);
    // Desktop GL path preferred for Zink (matches useGles=false in JREUtils)
    // Do not force LIBGL_ES=2 here.
    printf("LWJGL linkerhook: FINAL-V9 env set (no EGL_PLATFORM force)\n");
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

// Desktop OpenGL via EGL (Zink preferred path)
static void apply_hints_desktop_gl(void) {
    if (!real_glfwWindowHint) return;
    real_glfwWindowHint(0x00022001, 0x00030001); // wait - OPENGL_API is 0x00030001? 
    // GLFW_OPENGL_API = 0x00030001, GLFW_OPENGL_ES_API = 0x00030002
    // Actually: GLFW_NO_API=0, GLFW_OPENGL_API=0x00030001, GLFW_OPENGL_ES_API=0x00030002
    real_glfwWindowHint(0x00022001, 0x00030001); // CLIENT_API = OPENGL_API
    real_glfwWindowHint(0x0002200B, 0x00036002); // CONTEXT_CREATION_API = EGL
    real_glfwWindowHint(0x00022002, 4);          // MAJOR 4
    real_glfwWindowHint(0x00022003, 6);          // MINOR 6
    real_glfwWindowHint(0x00022008, 0);
    real_glfwWindowHint(0x00022006, 0);          // ANY profile
}

static void apply_hints_gles3(void) {
    if (!real_glfwWindowHint) return;
    real_glfwWindowHint(0x00022001, 0x00030002); // OPENGL_ES_API
    real_glfwWindowHint(0x0002200B, 0x00036002); // EGL
    real_glfwWindowHint(0x00022002, 3);
    real_glfwWindowHint(0x00022003, 0);
    real_glfwWindowHint(0x00022008, 0);
    real_glfwWindowHint(0x00022006, 0);
}

static void apply_hints_no_api(void) {
    if (!real_glfwWindowHint) return;
    real_glfwWindowHint(0x00022001, 0);
}

static int hooked_glfwInit_impl(void) {
    printf("LWJGL linkerhook: FINAL-V9 hooked_glfwInit\n");
    force_turbov1_env();
    resolve_all(RTLD_DEFAULT);
    drain_glfw_errors();

    if (real_glfwInitHint)
        real_glfwInitHint(0x00050003, 0x00060006); // ANDROID platform

    int result = 0;
    if (real_glfwInit) result = real_glfwInit();
    g_glfw_initialized = 1;
    drain_glfw_errors();
    printf("LWJGL linkerhook: FINAL-V9 glfwInit -> %d\n", result);
    return 1;
}

static int hooked_glfwGetError_impl(const char** description) {
    if (real_glfwGetError) {
        const char* d = NULL;
        int code = real_glfwGetError(&d);
        if (code == 0 || code == 0x10001 || code == 0x10004 || code == 0x10008 ||
            code == 65542 || code == 65546 || code == 0x10007) {
            if (description) *description = NULL;
            return 0;
        }
        if (!g_window_created) {
            if (description) *description = NULL;
            return 0;
        }
        if (description) *description = d;
        return code;
    }
    if (description) *description = NULL;
    return 0;
}

static void* hooked_glfwCreateWindow_impl(int width, int height, const char* title, void* monitor, void* share) {
    printf("LWJGL linkerhook: FINAL-V9 CreateWindow %dx%d\n", width, height);
    resolve_all(RTLD_DEFAULT);
    force_turbov1_env();
    drain_glfw_errors();

    if (!real_glfwCreateWindow) return NULL;

    void* win = NULL;

    // Strategy 1: Desktop OpenGL 4.6 + EGL (Zink desktop path)
    if (real_glfwDefaultWindowHints) real_glfwDefaultWindowHints();
    apply_hints_desktop_gl();
    win = real_glfwCreateWindow(width, height, title, monitor, share);
    drain_glfw_errors();
    if (win) {
        g_window_created = 1;
        g_has_gl_context = 1;
        g_current_window = win;
        printf("LWJGL linkerhook: FINAL-V9 window OK desktop GL+EGL\n");
        return win;
    }
    printf("LWJGL linkerhook: FINAL-V9 desktop GL failed\n");

    // Strategy 2: GLES3 + EGL
    if (real_glfwDefaultWindowHints) real_glfwDefaultWindowHints();
    apply_hints_gles3();
    win = real_glfwCreateWindow(width, height, title, monitor, share);
    drain_glfw_errors();
    if (win) {
        g_window_created = 1;
        g_has_gl_context = 1;
        g_current_window = win;
        printf("LWJGL linkerhook: FINAL-V9 window OK GLES3+EGL\n");
        return win;
    }
    printf("LWJGL linkerhook: FINAL-V9 GLES3 failed\n");

    // Strategy 3: NO_API last resort (will need more work for createCapabilities)
    if (real_glfwDefaultWindowHints) real_glfwDefaultWindowHints();
    apply_hints_no_api();
    win = real_glfwCreateWindow(width, height, title, monitor, share);
    drain_glfw_errors();
    if (win) {
        g_window_created = 1;
        g_has_gl_context = 0;
        g_current_window = win;
        printf("LWJGL linkerhook: FINAL-V9 window OK NO_API (no GL context)\n");
        return win;
    }

    printf("LWJGL linkerhook: FINAL-V9 all CreateWindow strategies failed\n");
    return NULL;
}

static void hooked_glfwMakeContextCurrent_impl(void* window) {
    printf("LWJGL linkerhook: FINAL-V9 MakeContextCurrent %p (has_gl=%d)\n", window, g_has_gl_context);
    if (g_has_gl_context && real_glfwMakeContextCurrent) {
        real_glfwMakeContextCurrent(window);
        drain_glfw_errors();
        g_current_window = window;
        g_context_current = window != NULL;
        return;
    }
    // NO_API path — do not call real (65546)
    g_current_window = window;
    g_context_current = window != NULL;
    drain_glfw_errors();
}

static void* hooked_glfwGetCurrentContext_impl(void) {
    if (g_context_current) return g_current_window;
    return NULL;
}

static void hooked_glfwSwapBuffers_impl(void* window) {
    if (real_glfwSwapBuffers && window && g_has_gl_context) {
        real_glfwSwapBuffers(window);
        drain_glfw_errors();
    }
}

static void hooked_glfwSwapInterval_impl(int interval) {
    if (real_glfwSwapInterval && g_has_gl_context)
        real_glfwSwapInterval(interval);
}

static jlong ndlopen_bugfix(__attribute__((unused)) JNIEnv *env,
                     __attribute__((unused)) jclass class,
                     jlong filename_ptr,
                     jint jmode) {
    const char* filename = (const char*) filename_ptr;
    if (!filename) return 0;

    if (strstr(filename, "libvulkan.so") == filename || strstr(filename, "vulkan.") != NULL) {
        printf("LWJGL linkerhook: FINAL-V9 vulkan redirect\n");
        return (jlong) pojavexec_loadVulkanDriver();
    }
    if (strstr(filename, "libTurboV1.so") || strstr(filename, "libGLMojo.so") ||
        strstr(filename, "libGLFear.so") || strstr(filename, "libGL.so")) {
        printf("LWJGL linkerhook: FINAL-V9 GL redirect (%s)\n", filename);
        const pojavexec_renderspec_t *rspec = pojavexec_getRenderSpec();
        if (rspec && rspec->egl_acquire)
            return (jlong) rspec->egl_acquire(rspec->egl_path);
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
    LOGI("Installing LWJGL hooks (BUILD v20260912-FINAL-V9)");
    printf("LWJGL linkerhook: installing hooks (BUILD v20260912-FINAL-V9)\n");
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
        printf("LWJGL linkerhook: FINAL-V9 hooks installed\n");
    }
}
