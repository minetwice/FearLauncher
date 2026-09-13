//
// FearLauncher — LWJGL dlopen/dlsym hook v2.1 (TURNIP-ZINK with EGL fallback)
//
// For zink renderers (turnip_zink / vulkan_zink):
//   - Try OSMesa bridge first (no EGL, best for Mali)
//   - If OSMesa symbols not found in the Mesa library, fall back to EGL
//     with proper Android fixes (EGL_PLATFORM=android, WSI=fifo)
// For other renderers: pass-through (only Vulkan redirect kept)
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
    // Only call osm_setup_window if OSMesa is actually loaded
    if (osmesa_is_loaded()) {
        osm_setup_window();
    }
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
    setenv("GALLIUM_DRIVER", "zink", 1);
    setenv("MESA_LOADER_DRIVER_OVERRIDE", "zink", 1);
    setenv("MESA_GL_VERSION_OVERRIDE", "4.6", 1);
    setenv("MESA_GLSL_VERSION_OVERRIDE", "460", 1);
    // Android WSI: only FIFO is guaranteed
    setenv("MESA_VK_WSI_PRESENT_MODE", "fifo", 1);
    setenv("MESA_PRESENT_MODE", "fifo", 1);
    // EGL platform: must be android (not surfaceless) for Mali
    setenv("EGL_PLATFORM", "android", 1);
    setenv("LIBGL_NOERROR", "1", 1);
    setenv("ZINK_DESCRIPTORS", "lazy", 1);
    setenv("mesa_glthread", "false", 1);
}

// ===================== OSMesa-GLFW bridge (only if OSMesa available) =====================

static volatile int g_glfw_initialized = 0;
static volatile int g_window_created = 0;
static void* g_current_window = NULL;
static bool g_use_osmesa = false; // true if OSMesa bridge is active

static int hooked_glfwInit_impl(void) {
    if (!g_glfw_initialized) {
        force_zink_env();
        bridge_environ.config_renderer = RENDERER_VK_ZINK;

        // Try OSMesa first
        if (!osmesa_is_loaded()) {
            dlsym_OSMesa();
        }

        if (osmesa_is_loaded() && osm_init()) {
            g_use_osmesa = true;
            printf("LWJGL hook: using OSMesa bridge (no EGL)\n");
        } else {
            g_use_osmesa = false;
            printf("LWJGL hook: OSMesa not available, using EGL fallback (with fifo+android fixes)\n");
        }
        g_glfw_initialized = 1;
    }
    return 1; // GLFW_TRUE
}

static int hooked_glfwGetError_impl(const char** description) {
    if (description) *description = NULL;
    return 0; // GLFW_NO_ERROR
}

static void* hooked_glfwCreateWindow_impl(int width, int height, const char* title, void* monitor, void* share) {
    printf("LWJGL hook: glfwCreateWindow %dx%d (mode=%s)\n", width, height,
           g_use_osmesa ? "OSMesa" : "EGL");
    (void)title; (void)monitor;

    if (g_use_osmesa) {
        // OSMesa path: create context via OSMesa
        osm_render_window_t* share_bundle = (share != NULL) ? (osm_render_window_t*) share : NULL;
        osm_render_window_t* bundle = osm_init_context(share_bundle);
        if (bundle == NULL) {
            printf("LWJGL hook: osm_init_context FAILED, falling back to EGL\n");
            g_use_osmesa = false;
        } else {
            bundle->state = STATE_RENDERER_ALIVE;
            if (bridge_environ.mainWindowBundle == NULL) {
                bridge_environ.mainWindowBundle = (basic_render_window_t*) bundle;
                bundle->newNativeSurface = bridge_environ.pojavWindow;
            }
            osm_make_current(bundle);
            g_window_created = 1;
            g_current_window = (void*) bundle;
            printf("LWJGL hook: window OK (OSMesa, context=%p)\n", (void*)bundle->context);
            return g_current_window;
        }
    }

    // EGL fallback: return a dummy non-NULL pointer so GLFW thinks the window was created.
    // The actual EGL context is managed by pojavexec's renderspec/minibridge path.
    g_window_created = 1;
    g_current_window = (void*) 0xDEADBEEF; // sentinel
    printf("LWJGL hook: window OK (EGL fallback — context managed by pojavexec)\n");
    return g_current_window;
}

static void hooked_glfwMakeContextCurrent_impl(void* window) {
    if (g_use_osmesa) {
        if (window == NULL) {
            osm_make_current(NULL);
            g_current_window = NULL;
            return;
        }
        osm_make_current((osm_render_window_t*) window);
    }
    // EGL fallback: no-op (pojavexec handles context)
    g_current_window = window;
}

static void* hooked_glfwGetCurrentContext_impl(void) {
    return g_current_window;
}

static void hooked_glfwSwapBuffers_impl(void* window) {
    if (g_use_osmesa) {
        osm_swap_buffers();
    }
    // EGL fallback: pojavexec handles swap via its own bridge
    (void)window;
}

static void hooked_glfwSwapInterval_impl(int interval) {
    if (g_use_osmesa) {
        osm_swap_interval(interval);
    }
    // EGL fallback: no-op
}

static void hooked_glfwDestroyWindow_impl(void* window) {
    if (g_use_osmesa && window != NULL && window != (void*)0xDEADBEEF) {
        osm_render_window_t* bundle = (osm_render_window_t*) window;
        if (bundle->context != NULL) {
            OSMesaDestroyContext_p(bundle->context);
            bundle->context = NULL;
        }
        if (bundle->nativeSurface != NULL) {
            ANativeWindow_release(bundle->nativeSurface);
            bundle->nativeSurface = NULL;
        }
        free(bundle);
    }
    if (g_current_window == window) g_current_window = NULL;
}

static void* hooked_glfwGetProcAddress_impl(const char* procname) {
    // Try Mesa library first (works for both OSMesa and EGL modes)
    void* mesa = get_mesa_dl_handle();
    if (mesa != NULL) {
        void* sym = dlsym(mesa, procname);
        if (sym != NULL) return sym;
        // Try OSMesaGetProcAddress
        void* (*osm_get_proc)(const char*) = dlsym(mesa, "OSMesaGetProcAddress");
        if (osm_get_proc != NULL) {
            sym = osm_get_proc(procname);
            if (sym != NULL) return sym;
        }
    }
    // Fall back to RTLD_DEFAULT
    return dlsym(RTLD_DEFAULT, procname);
}

static void hooked_glfwWindowHint_impl(int hint, int value) {
    (void)hint; (void)value;
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
    return 0;
}

static void hooked_glfwSetWindowTitle_impl(void* window, const char* title) {
    (void)window; (void)title;
}

static void hooked_glfwShowWindow_impl(void* window) {
    (void)window;
}

// ===================== ndlopen / ndlsym hooks =====================

static jlong ndlopen_bugfix(__attribute__((unused)) JNIEnv *env,
                     __attribute__((unused)) jclass class,
                     jlong filename_ptr,
                     jint jmode) {
    const char* filename = (const char*) filename_ptr;
    if (!filename) return 0;

    // Always redirect Vulkan loads to our loader
    if (strstr(filename, "libvulkan.so") == filename || strstr(filename, "vulkan.") != NULL) {
        printf("LWJGL hook: vulkan redirect\n");
        return (jlong) pojavexec_loadVulkanDriver();
    }

    if (is_zink_renderer()) {
        // For zink: redirect GL library to the Mesa library
        if (strstr(filename, "libGL.so") != NULL || strstr(filename, "libOSMesa") != NULL) {
            void* mesa = get_mesa_dl_handle();
            if (mesa != NULL) return (jlong) mesa;
            // Fall through to renderspec if Mesa not loaded yet
        }
        // Redirect legacy renderer lib names to renderspec (existing behavior)
        if (strstr(filename, "libTurboV1.so") || strstr(filename, "libGLMojo.so") ||
            strstr(filename, "libGLFear.so") || strstr(filename, "libGL.so")) {
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
        // Route GLFW calls to our hooks (works for both OSMesa and EGL modes)
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

        // GL functions: try Mesa library first, then default
        if (strncmp(symbol, "gl", 2) == 0) {
            void* mesa = get_mesa_dl_handle();
            if (mesa != NULL) {
                void* sym = dlsym(mesa, symbol);
                if (sym != NULL) return (jlong) sym;
            }
        }
    }

    void* sym = dlsym((void*) handle, symbol);
    if (!sym) sym = dlsym(RTLD_DEFAULT, symbol);
    return (jlong) sym;
}

void installLwjglDlopenHook(JNIEnv *env) {
    LOGI("Installing LWJGL hooks (TURNIP-ZINK v2.1, OSMesa+EGL fallback)");
    printf("LWJGL hook: installing hooks (TURNIP-ZINK v2.1, OSMesa+EGL fallback)\n");

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
        printf("LWJGL hook: hooks installed (TURNIP-ZINK v2.1)\n");
    }
}
