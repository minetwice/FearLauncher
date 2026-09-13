//
// FearLauncher — LWJGL dlopen/dlsym hook v2.0 (TURNIP-ZINK)
// Adapted from ZalithLauncher's OSMesa bridge approach.
//
// For zink renderers (turnip_zink / vulkan_zink):
//   - No EGL. Rendering goes through OSMesa → ANativeWindow buffer.
//   - GLFW calls are routed to the OSMesa bridge.
//   - GL symbols resolve from the Mesa library (Zink = GL→Vulkan).
// For other renderers:
//   - Pass-through to the real GLFW; only Vulkan loading is redirected.
//
#include "jvm_hooks.h"

#include <android/api-level.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <dlfcn.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <pthread.h>
#include <stdbool.h>

#define TAG __FILE_NAME__
#include <log.h>
#include "../pojavexec.h"
#include "ctxbridges/bridge_environ.h"
#include "ctxbridges/osm_bridge.h"
#include "ctxbridges/osmesa_loader.h"

// Global bridge environment
bridge_environ_t bridge_environ = {0};

// ===================== Renderer detection =====================

static bool is_zink_renderer() {
    const char* renderer = getenv("POJAV_RENDERER");
    if (renderer == NULL) return false;
    return strcmp(renderer, "turnip_zink") == 0 ||
           strcmp(renderer, "vulkan_zink") == 0;
}

// ===================== JNI: Surface bridge =====================

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_setupBridgeWindow(JNIEnv* env, jclass clazz, jobject surface) {
    if (surface == NULL) {
        LOGW("setupBridgeWindow: surface is NULL");
        return;
    }
    bridge_environ.pojavWindow = ANativeWindow_fromSurface(env, surface);
    bridge_environ.savedWidth = ANativeWindow_getWidth(bridge_environ.pojavWindow);
    bridge_environ.savedHeight = ANativeWindow_getHeight(bridge_environ.pojavWindow);
    LOGI("Bridge window set: %p (%dx%d)", bridge_environ.pojavWindow,
         bridge_environ.savedWidth, bridge_environ.savedHeight);
    // If the OSMesa bridge already has a main window, update it
    osm_setup_window();
}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_releaseBridgeWindow(JNIEnv* env, jclass clazz) {
    if (bridge_environ.pojavWindow != NULL) {
        ANativeWindow_release(bridge_environ.pojavWindow);
        bridge_environ.pojavWindow = NULL;
        LOGI("Bridge window released");
    }
}

// ===================== Zink env setup =====================

static void force_zink_env(void) {
    // Mesa Zink driver selection
    setenv("GALLIUM_DRIVER", "zink", 1);
    setenv("MESA_LOADER_DRIVER_OVERRIDE", "zink", 1);
    // Desktop GL 4.6 via Zink
    setenv("MESA_GL_VERSION_OVERRIDE", "4.6", 1);
    setenv("MESA_GLSL_VERSION_OVERRIDE", "460", 1);
    // Android WSI: only FIFO is guaranteed
    setenv("MESA_VK_WSI_PRESENT_MODE", "fifo", 1);
    setenv("MESA_PRESENT_MODE", "fifo", 1);
    setenv("LIBGL_NOERROR", "1", 1);
    setenv("ZINK_DESCRIPTORS", "lazy", 1);
    setenv("mesa_glthread", "false", 1);
    printf("LWJGL hook: zink env set (OSMesa bridge, no EGL)\n");
}

// ===================== OSMesa-GLFW bridge =====================

static volatile int g_glfw_initialized = 0;
static volatile int g_window_created = 0;
static void* g_current_window = NULL;
static void* g_mesa_handle = NULL;

static void* get_mesa_handle(void) {
    if (g_mesa_handle != NULL) return g_mesa_handle;
    // Load the Mesa library via LIB_MESA_NAME (same as OSMesa loader)
    const char* mesa_name = getenv("LIB_MESA_NAME");
    const char* native_dir = getenv("POJAV_NATIVEDIR");
    if (mesa_name == NULL || native_dir == NULL) {
        printf("LWJGL hook: LIB_MESA_NAME or POJAV_NATIVEDIR not set!\n");
        return NULL;
    }
    char path[512];
    if (strncmp(mesa_name, "/data", 5) == 0) {
        snprintf(path, sizeof(path), "%s", mesa_name);
    } else {
        snprintf(path, sizeof(path), "%s/%s", native_dir, mesa_name);
    }
    g_mesa_handle = dlopen(path, RTLD_LOCAL | RTLD_NOW);
    printf("LWJGL hook: loaded Mesa (%s) handle=%p\n", path, g_mesa_handle);
    return g_mesa_handle;
}

static int hooked_glfwInit_impl(void) {
    printf("LWJGL hook: glfwInit (OSMesa)\n");
    if (!g_glfw_initialized) {
        force_zink_env();
        bridge_environ.config_renderer = RENDERER_VK_ZINK;
        if (!osm_init()) {
            printf("LWJGL hook: osm_init FAILED\n");
            return 0;
        }
        g_glfw_initialized = 1;
    }
    return 1; // GLFW_TRUE
}

static int hooked_glfwGetError_impl(const char** description) {
    if (description) *description = NULL;
    return 0; // GLFW_NO_ERROR — OSMesa bridge never produces GLFW errors
}

static void* hooked_glfwCreateWindow_impl(int width, int height, const char* title, void* monitor, void* share) {
    printf("LWJGL hook: glfwCreateWindow %dx%d (OSMesa)\n", width, height);
    (void)title; (void)monitor;

    // Create an OSMesa render window (GL context via Zink)
    osm_render_window_t* share_bundle = (share != NULL) ? (osm_render_window_t*) share : NULL;
    osm_render_window_t* bundle = osm_init_context(share_bundle);
    if (bundle == NULL) {
        printf("LWJGL hook: osm_init_context FAILED\n");
        return NULL;
    }
    bundle->state = STATE_RENDERER_ALIVE;

    // If this is the first window, bind it to the Android native window
    if (bridge_environ.mainWindowBundle == NULL) {
        bridge_environ.mainWindowBundle = (basic_render_window_t*) bundle;
        bundle->newNativeSurface = bridge_environ.pojavWindow;
    }

    // Make it current immediately
    osm_make_current(bundle);

    g_window_created = 1;
    g_current_window = (void*) bundle;
    printf("LWJGL hook: window OK (OSMesa, context=%p)\n", (void*)bundle->context);
    return g_current_window;
}

static void hooked_glfwMakeContextCurrent_impl(void* window) {
    printf("LWJGL hook: glfwMakeContextCurrent %p\n", window);
    if (window == NULL) {
        osm_make_current(NULL);
        g_current_window = NULL;
        return;
    }
    osm_make_current((osm_render_window_t*) window);
    g_current_window = window;
}

static void* hooked_glfwGetCurrentContext_impl(void) {
    return g_current_window;
}

static void hooked_glfwSwapBuffers_impl(void* window) {
    (void)window;
    osm_swap_buffers();
}

static void hooked_glfwSwapInterval_impl(int interval) {
    osm_swap_interval(interval);
}

static void hooked_glfwDestroyWindow_impl(void* window) {
    printf("LWJGL hook: glfwDestroyWindow %p\n", window);
    if (window != NULL) {
        osm_render_window_t* bundle = (osm_render_window_t*) window;
        if (bundle->context != NULL) {
            OSMesaDestroyContext_p(bundle->context);
            bundle->context = NULL;
        }
        if (bundle->nativeSurface != NULL) {
            ANativeWindow_release(bundle->nativeSurface);
            bundle->nativeSurface = NULL;
        }
        if (g_current_window == window) g_current_window = NULL;
        free(bundle);
    }
}

static void* hooked_glfwGetProcAddress_impl(const char* procname) {
    // Resolve GL extension functions from the Mesa library
    void* mesa = get_mesa_handle();
    if (mesa == NULL) return NULL;
    void* sym = dlsym(mesa, procname);
    if (sym == NULL) {
        // Try OSMesaGetProcAddress
        void* (*osm_get_proc)(const char*) = dlsym(mesa, "OSMesaGetProcAddress");
        if (osm_get_proc != NULL) sym = osm_get_proc(procname);
    }
    return sym;
}

static void hooked_glfwWindowHint_impl(int hint, int value) {
    (void)hint; (void)value; // no-op: OSMesa doesn't use GLFW hints
}

static void hooked_glfwDefaultWindowHints_impl(void) {
    // no-op
}

static void hooked_glfwGetFramebufferSize_impl(void* window, int* width, int* height) {
    if (width) *width = bridge_environ.savedWidth;
    if (height) *height = bridge_environ.savedHeight;
    (void)window;
}

static void hooked_glfwGetWindowSize_impl(void* window, int* width, int* height) {
    hooked_glfwGetFramebufferSize_impl(window, width, height);
}

static int hooked_glfwWindowShouldClose_impl(void* window) {
    (void)window;
    return 0; // window never closes from the native side
}

static void hooked_glfwSetWindowTitle_impl(void* window, const char* title) {
    (void)window; (void)title; // no-op
}

static void hooked_glfwShowWindow_impl(void* window) {
    (void)window; // no-op
}

// ===================== Pass-through GLFW (non-zink renderers) =====================

static int  (*real_glfwInit)(void) = NULL;
static void (*real_glfwWindowHint)(int, int) = NULL;

static void resolve_real_glfw(void* handle) {
    if (!real_glfwInit) {
        real_glfwInit = (int (*)(void)) dlsym(handle, "glfwInit");
        if (!real_glfwInit) real_glfwInit = (int (*)(void)) dlsym(RTLD_DEFAULT, "glfwInit");
    }
    if (!real_glfwWindowHint) {
        real_glfwWindowHint = (void (*)(int, int)) dlsym(handle, "glfwWindowHint");
        if (!real_glfwWindowHint) real_glfwWindowHint = (void (*)(int, int)) dlsym(RTLD_DEFAULT, "glfwWindowHint");
    }
}

// ===================== ndlopen / ndlsym hooks =====================

static jlong ndlopen_bugfix(__attribute__((unused)) JNIEnv *env,
                     __attribute__((unused)) jclass class,
                     jlong filename_ptr,
                     jint jmode) {
    const char* filename = (const char*) filename_ptr;
    if (!filename) return 0;

    // Always redirect Vulkan loads to our loader (handles Turnip on Adreno,
    // system driver on Mali)
    if (strstr(filename, "libvulkan.so") == filename || strstr(filename, "vulkan.") != NULL) {
        printf("LWJGL hook: vulkan redirect\n");
        return (jlong) pojavexec_loadVulkanDriver();
    }

    if (is_zink_renderer()) {
        // For zink: redirect GL library to the Mesa (OSMesa) library
        if (strstr(filename, "libGL.so") != NULL || strstr(filename, "libOSMesa") != NULL) {
            printf("LWJGL hook: GL redirect to Mesa (%s)\n", filename);
            void* mesa = get_mesa_handle();
            if (mesa != NULL) return (jlong) mesa;
        }
        // Also redirect legacy renderer lib names to Mesa
        if (strstr(filename, "libTurboV1.so") || strstr(filename, "libGLMojo.so") ||
            strstr(filename, "libGLFear.so")) {
            printf("LWJGL hook: legacy GL redirect to Mesa (%s)\n", filename);
            void* mesa = get_mesa_handle();
            if (mesa != NULL) return (jlong) mesa;
        }
    } else {
        // Non-zink renderers: use the renderspec EGL redirect (existing behavior)
        if (strstr(filename, "libGL.so") != NULL) {
            const pojavexec_renderspec_t *rspec = pojavexec_getRenderSpec();
            if (rspec && rspec->egl_acquire)
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

    if (is_zink_renderer()) {
        // Route GLFW calls to the OSMesa bridge
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
        if (strcmp(symbol, "glfwDestroyWindow") == 0)
            return (jlong) hooked_glfwDestroyWindow_impl;
        if (strcmp(symbol, "glfwGetProcAddress") == 0 ||
            strcmp(symbol, "glfwGetProcessAddress") == 0)
            return (jlong) hooked_glfwGetProcAddress_impl;
        if (strcmp(symbol, "glfwWindowHint") == 0)
            return (jlong) hooked_glfwWindowHint_impl;
        if (strcmp(symbol, "glfwDefaultWindowHints") == 0)
            return (jlong) hooked_glfwDefaultWindowHints_impl;
        if (strcmp(symbol, "glfwGetFramebufferSize") == 0)
            return (jlong) hooked_glfwGetFramebufferSize_impl;
        if (strcmp(symbol, "glfwGetWindowSize") == 0)
            return (jlong) hooked_glfwGetWindowSize_impl;
        if (strcmp(symbol, "glfwWindowShouldClose") == 0)
            return (jlong) hooked_glfwWindowShouldClose_impl;
        if (strcmp(symbol, "glfwSetWindowTitle") == 0)
            return (jlong) hooked_glfwSetWindowTitle_impl;
        if (strcmp(symbol, "glfwShowWindow") == 0)
            return (jlong) hooked_glfwShowWindow_impl;

        // GL functions: resolve from the Mesa library handle
        if (strncmp(symbol, "gl", 2) == 0) {
            void* mesa = get_mesa_handle();
            if (mesa != NULL) {
                void* sym = dlsym(mesa, symbol);
                if (sym != NULL) return (jlong) sym;
            }
        }
    } else {
        // Non-zink renderers: minimal hooking
        resolve_real_glfw((void*)handle);
        if (strcmp(symbol, "eglGetError") == 0) {
            // stub for legacy renderers
            return (jlong) 0;
        }
    }

    void* sym = dlsym((void*) handle, symbol);
    if (!sym) sym = dlsym(RTLD_DEFAULT, symbol);
    return (jlong) sym;
}

void installLwjglDlopenHook(JNIEnv *env) {
    LOGI("Installing LWJGL hooks (TURNIP-ZINK v2.0, OSMesa bridge)");
    printf("LWJGL hook: installing hooks (TURNIP-ZINK v2.0, OSMesa bridge)\n");

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
        printf("LWJGL hook: hooks installed (TURNIP-ZINK v2.0)\n");
    }
}
