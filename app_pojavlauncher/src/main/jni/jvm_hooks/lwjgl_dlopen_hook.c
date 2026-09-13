//
// FearLauncher — LWJGL dlopen/dlsym hook v2.10 (TURNIP-ZINK)
// Zink detection via GALLIUM_DRIVER/FEAR_RENDERER (POJAV_RENDERER scrubbed for Sodium)
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
#include <stdint.h>

#define TAG __FILE_NAME__
#include <log.h>
#include "../pojavexec.h"
#include "ctxbridges/bridge_environ.h"
#include "ctxbridges/osm_bridge.h"
#include "ctxbridges/osmesa_loader.h"

bridge_environ_t bridge_environ = {0};

static int g_is_zink_cached = -1;

static bool is_zink_renderer() {
    if (g_is_zink_cached >= 0) return g_is_zink_cached == 1;
    const char* fear = getenv("FEAR_RENDERER");
    const char* gallium = getenv("GALLIUM_DRIVER");
    const char* renderer = getenv("POJAV_RENDERER");
    bool z = false;
    if (fear && (strcmp(fear, "turnip_zink") == 0 || strcmp(fear, "vulkan_zink") == 0))
        z = true;
    else if (gallium && strcmp(gallium, "zink") == 0)
        z = true;
    else if (renderer && (strcmp(renderer, "turnip_zink") == 0 || strcmp(renderer, "vulkan_zink") == 0))
        z = true;
    g_is_zink_cached = z ? 1 : 0;
    return z;
}

static void hide_pojav_from_sodium(void) {
    unsetenv("POJAV_RENDERER");
    unsetenv("POJAV_LAUNCHER");
    printf("LWJGL hook v2.10: unset POJAV_RENDERER/POJAV_LAUNCHER (Sodium bypass)\n");
}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_setupBridgeWindow(JNIEnv* env, jclass clazz, jobject surface) {
    if (surface == NULL) return;
    bridge_environ.pojavWindow = ANativeWindow_fromSurface(env, surface);
    bridge_environ.savedWidth = ANativeWindow_getWidth(bridge_environ.pojavWindow);
    bridge_environ.savedHeight = ANativeWindow_getHeight(bridge_environ.pojavWindow);
    LOGI("Bridge window set: %p (%dx%d)", bridge_environ.pojavWindow,
         bridge_environ.savedWidth, bridge_environ.savedHeight);
    if (osmesa_is_loaded()) osm_setup_window();
}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_releaseBridgeWindow(JNIEnv* env, jclass clazz) {
    if (bridge_environ.pojavWindow != NULL) {
        ANativeWindow_release(bridge_environ.pojavWindow);
        bridge_environ.pojavWindow = NULL;
    }
}

static void* g_vulkan_handle = NULL;

static bool ensure_vulkan_ptr(void) {
    if (g_vulkan_handle == NULL) {
        g_vulkan_handle = pojavexec_loadVulkanDriver();
    }
    if (g_vulkan_handle == NULL) {
        printf("LWJGL hook v2.10: Vulkan load FAILED\n");
        return false;
    }
    char hex[32];
    snprintf(hex, sizeof(hex), "%lx", (unsigned long)(uintptr_t)g_vulkan_handle);
    setenv("VULKAN_PTR", hex, 1);
    printf("LWJGL hook v2.10: VULKAN_PTR=%s (handle=%p)\n", hex, g_vulkan_handle);
    return true;
}

static void force_zink_env(void) {
    setenv("GALLIUM_DRIVER", "zink", 1);
    setenv("MESA_LOADER_DRIVER_OVERRIDE", "zink", 1);
    setenv("MESA_GL_VERSION_OVERRIDE", "4.6", 1);
    setenv("MESA_GLSL_VERSION_OVERRIDE", "460", 1);
    setenv("LIBGL_NOERROR", "1", 1);
    setenv("mesa_glthread", "false", 1);
    unsetenv("LIBGL_ES");
    const char* cache = getenv("MESA_GLSL_CACHE_DIR");
    if (cache && cache[0]) {
        setenv("MESA_SHADER_CACHE_DIR", cache, 1);
        setenv("XDG_CACHE_HOME", cache, 0);
        setenv("XDG_CONFIG_HOME", cache, 0);
    }
    if (!getenv("HOME") || !getenv("HOME")[0]) {
        setenv("HOME", cache && cache[0] ? cache : "/data/local/tmp", 1);
    }
    unsetenv("MESA_VK_WSI_PRESENT_MODE");
    unsetenv("MESA_PRESENT_MODE");
    setenv("ZINK_DESCRIPTORS", "lazy", 1);
}

static volatile int g_glfw_initialized = 0;
static void* g_current_window = NULL;
static bool g_use_osmesa = false;
static int g_fake_monitor = 1;
static struct {
    int width, height, redBits, greenBits, blueBits, refreshRate;
} g_fake_vidmode;

static void ensure_vidmode(void) {
    if (g_fake_vidmode.width == 0) {
        g_fake_vidmode.width = bridge_environ.savedWidth > 0 ? bridge_environ.savedWidth : 1920;
        g_fake_vidmode.height = bridge_environ.savedHeight > 0 ? bridge_environ.savedHeight : 1080;
        g_fake_vidmode.redBits = 8;
        g_fake_vidmode.greenBits = 8;
        g_fake_vidmode.blueBits = 8;
        g_fake_vidmode.refreshRate = 60;
    }
}

static void glfw_stub_void(void) {}
static int glfw_stub_int0(void) { return 0; }
static void* glfw_stub_ptr0(void) { return NULL; }

static void* hooked_glfwSetCallback_impl(void* window, void* callback) {
    (void)window; (void)callback; return NULL;
}

static int hooked_glfwInit_impl(void) {
    if (!g_glfw_initialized) {
        force_zink_env();
        bridge_environ.config_renderer = RENDERER_VK_ZINK;
        ensure_vulkan_ptr();
        if (!osmesa_is_loaded()) dlsym_OSMesa();
        if (osmesa_is_loaded() && osm_init()) {
            g_use_osmesa = true;
            printf("LWJGL hook v2.10: OSMesa bridge\n");
        } else {
            g_use_osmesa = false;
            printf("LWJGL hook v2.10: GLFW full stub (no OSMesa)\n");
        }
        (void)is_zink_renderer();
        hide_pojav_from_sodium();
        g_glfw_initialized = 1;
    }
    return 1;
}

static int hooked_glfwGetError_impl(const char** description) {
    if (description) *description = NULL;
    return 0;
}

static void* hooked_glfwGetPrimaryMonitor_impl(void) {
    return (void*)(uintptr_t)&g_fake_monitor;
}

static void* hooked_glfwGetVideoMode_impl(void* monitor) {
    (void)monitor; ensure_vidmode(); return (void*)&g_fake_vidmode;
}

static const void* hooked_glfwGetVideoModes_impl(void* monitor, int* count) {
    (void)monitor; ensure_vidmode(); if (count) *count = 1; return (const void*)&g_fake_vidmode;
}

static void* const* hooked_glfwGetMonitors_impl(int* count) {
    static void* monitors[1];
    monitors[0] = (void*)(uintptr_t)&g_fake_monitor;
    if (count) *count = 1;
    return monitors;
}

static void hooked_glfwGetMonitorPos_impl(void* m, int* x, int* y) {
    (void)m; if (x) *x = 0; if (y) *y = 0;
}

static void hooked_glfwGetMonitorWorkarea_impl(void* m, int* x, int* y, int* w, int* h) {
    (void)m; ensure_vidmode();
    if (x) *x = 0; if (y) *y = 0;
    if (w) *w = g_fake_vidmode.width; if (h) *h = g_fake_vidmode.height;
}

static const char* hooked_glfwGetMonitorName_impl(void* m) {
    (void)m; return "FearLauncher-Display";
}

static void* hooked_glfwGetWindowMonitor_impl(void* window) {
    (void)window; return NULL;
}

static void hooked_glfwSetWindowMonitor_impl(void* window, void* monitor,
        int xpos, int ypos, int width, int height, int refreshRate) {
    (void)window; (void)monitor; (void)xpos; (void)ypos; (void)refreshRate;
    if (width > 0) bridge_environ.savedWidth = width;
    if (height > 0) bridge_environ.savedHeight = height;
    ensure_vidmode();
    g_fake_vidmode.width = bridge_environ.savedWidth;
    g_fake_vidmode.height = bridge_environ.savedHeight;
}

static void* hooked_glfwCreateWindow_impl(int width, int height, const char* title, void* monitor, void* share) {
    printf("LWJGL hook v2.10: glfwCreateWindow %dx%d\n", width, height);
    (void)title; (void)monitor;
    if (bridge_environ.savedWidth <= 0) bridge_environ.savedWidth = width;
    if (bridge_environ.savedHeight <= 0) bridge_environ.savedHeight = height;
    ensure_vidmode();
    ensure_vulkan_ptr();

    if (g_use_osmesa) {
        printf("LWJGL hook v2.10: calling OSMesaCreateContext...\n");
        osm_render_window_t* share_bundle = (share != NULL) ? (osm_render_window_t*) share : NULL;
        osm_render_window_t* bundle = osm_init_context(share_bundle);
        if (bundle != NULL) {
            bundle->state = STATE_RENDERER_ALIVE;
            if (bridge_environ.mainWindowBundle == NULL) {
                bridge_environ.mainWindowBundle = (basic_render_window_t*) bundle;
                bundle->newNativeSurface = bridge_environ.pojavWindow;
            }
            osm_make_current(bundle);
            g_current_window = (void*) bundle;
            if (glGetString_p) {
                const char* vendor = (const char*)glGetString_p(0x1F00);
                const char* renderer = (const char*)glGetString_p(0x1F01);
                const char* version = (const char*)glGetString_p(0x1F02);
                printf("LWJGL hook v2.10: GL_VENDOR=%s\n", vendor ? vendor : "(null)");
                printf("LWJGL hook v2.10: GL_RENDERER=%s\n", renderer ? renderer : "(null)");
                printf("LWJGL hook v2.10: GL_VERSION=%s\n", version ? version : "(null)");
            }
            printf("LWJGL hook v2.10: window OK (OSMesa)\n");
            return g_current_window;
        }
        printf("LWJGL hook v2.10: OSMesaCreateContext failed, falling back to stub\n");
        g_use_osmesa = false;
    }

    g_current_window = (void*) 0xDEADBEEF;
    printf("LWJGL hook v2.10: window OK (stub)\n");
    return g_current_window;
}

static void hooked_glfwMakeContextCurrent_impl(void* window) {
    if (g_use_osmesa) {
        if (window == NULL) { osm_make_current(NULL); g_current_window = NULL; return; }
        osm_make_current((osm_render_window_t*) window);
    }
    g_current_window = window;
}

static void* hooked_glfwGetCurrentContext_impl(void) { return g_current_window; }

static void hooked_glfwSwapBuffers_impl(void* window) {
    if (g_use_osmesa) osm_swap_buffers();
    (void)window;
}

static void hooked_glfwSwapInterval_impl(int interval) {
    if (g_use_osmesa) osm_swap_interval(interval);
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
    if (!procname) return NULL;
    void* mesa = get_mesa_dl_handle();
    void* sym = NULL;
    if (mesa != NULL) {
        void* (*osm_get_proc)(const char*) = dlsym(mesa, "OSMesaGetProcAddress");
        if (osm_get_proc != NULL) {
            sym = osm_get_proc(procname);
            if (sym != NULL) return sym;
        }
        sym = dlsym(mesa, procname);
        if (sym != NULL) return sym;
    }
    if (strcmp(procname, "glGetString") == 0 && glGetString_p) return (void*)glGetString_p;
    if (strcmp(procname, "glFinish") == 0 && glFinish_p) return (void*)glFinish_p;
    if (strcmp(procname, "glClear") == 0 && glClear_p) return (void*)glClear_p;
    if (strcmp(procname, "glClearColor") == 0 && glClearColor_p) return (void*)glClearColor_p;
    if (strcmp(procname, "glReadPixels") == 0 && glReadPixels_p) return (void*)glReadPixels_p;
    if (strcmp(procname, "glReadBuffer") == 0 && glReadBuffer_p) return (void*)glReadBuffer_p;
    sym = dlsym(RTLD_DEFAULT, procname);
    if (sym == NULL && strncmp(procname, "gl", 2) == 0) {
        printf("LWJGL hook v2.10: GetProcAddress MISS %s\n", procname);
    }
    return sym;
}

static void hooked_glfwWindowHint_impl(int h, int v) { (void)h; (void)v; }
static void hooked_glfwDefaultWindowHints_impl(void) {}
static void hooked_glfwGetFramebufferSize_impl(void* w, int* width, int* height) {
    ensure_vidmode();
    if (width) *width = bridge_environ.savedWidth > 0 ? bridge_environ.savedWidth : g_fake_vidmode.width;
    if (height) *height = bridge_environ.savedHeight > 0 ? bridge_environ.savedHeight : g_fake_vidmode.height;
    (void)w;
}
static void hooked_glfwGetWindowSize_impl(void* w, int* width, int* height) {
    hooked_glfwGetFramebufferSize_impl(w, width, height);
}
static void hooked_glfwGetWindowPos_impl(void* w, int* x, int* y) {
    (void)w; if (x) *x = 0; if (y) *y = 0;
}
static void hooked_glfwSetWindowPos_impl(void* w, int x, int y) { (void)w; (void)x; (void)y; }
static void hooked_glfwSetWindowSize_impl(void* w, int width, int height) {
    (void)w;
    if (width > 0) bridge_environ.savedWidth = width;
    if (height > 0) bridge_environ.savedHeight = height;
}
static int hooked_glfwWindowShouldClose_impl(void* w) { (void)w; return 0; }
static void hooked_glfwSetWindowShouldClose_impl(void* w, int v) { (void)w; (void)v; }
static void hooked_glfwSetWindowTitle_impl(void* w, const char* t) { (void)w; (void)t; }
static void hooked_glfwShowWindow_impl(void* w) { (void)w; }
static void hooked_glfwHideWindow_impl(void* w) { (void)w; }
static void hooked_glfwFocusWindow_impl(void* w) { (void)w; }
static void hooked_glfwIconifyWindow_impl(void* w) { (void)w; }
static void hooked_glfwRestoreWindow_impl(void* w) { (void)w; }
static void hooked_glfwMaximizeWindow_impl(void* w) { (void)w; }
static int hooked_glfwGetWindowAttrib_impl(void* w, int attrib) {
    (void)w;
    if (attrib == 0x00020001) return 1;
    if (attrib == 0x00020004) return 1;
    return 0;
}
static void hooked_glfwSetWindowAttrib_impl(void* w, int a, int v) { (void)w; (void)a; (void)v; }
static void hooked_glfwSetInputMode_impl(void* w, int m, int v) { (void)w; (void)m; (void)v; }
static int hooked_glfwGetInputMode_impl(void* w, int m) { (void)w; (void)m; return 0; }
static void hooked_glfwPollEvents_impl(void) {}
static void hooked_glfwWaitEvents_impl(void) {}
static void hooked_glfwWaitEventsTimeout_impl(double t) { (void)t; }
static void hooked_glfwPostEmptyEvent_impl(void) {}
static void hooked_glfwTerminate_impl(void) {}
static int hooked_glfwVulkanSupported_impl(void) { return 1; }

static jlong ndlopen_bugfix(__attribute__((unused)) JNIEnv *env,
                     __attribute__((unused)) jclass class,
                     jlong filename_ptr,
                     jint jmode) {
    const char* filename = (const char*) filename_ptr;
    if (!filename) return 0;
    if (strstr(filename, "libvulkan.so") == filename || strstr(filename, "vulkan.") != NULL)
        return (jlong) pojavexec_loadVulkanDriver();
    if (is_zink_renderer()) {
        if (strstr(filename, "libGL.so") != NULL || strstr(filename, "libOSMesa") != NULL) {
            void* mesa = get_mesa_dl_handle();
            if (mesa != NULL) return (jlong) mesa;
        }
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
        if (strcmp(symbol, "glfwInit") == 0) return (jlong) hooked_glfwInit_impl;
        if (strcmp(symbol, "glfwGetError") == 0) return (jlong) hooked_glfwGetError_impl;
        if (strcmp(symbol, "glfwGetPrimaryMonitor") == 0) return (jlong) hooked_glfwGetPrimaryMonitor_impl;
        if (strcmp(symbol, "glfwGetVideoMode") == 0) return (jlong) hooked_glfwGetVideoMode_impl;
        if (strcmp(symbol, "glfwGetVideoModes") == 0) return (jlong) hooked_glfwGetVideoModes_impl;
        if (strcmp(symbol, "glfwGetMonitors") == 0) return (jlong) hooked_glfwGetMonitors_impl;
        if (strcmp(symbol, "glfwGetMonitorPos") == 0) return (jlong) hooked_glfwGetMonitorPos_impl;
        if (strcmp(symbol, "glfwGetMonitorWorkarea") == 0) return (jlong) hooked_glfwGetMonitorWorkarea_impl;
        if (strcmp(symbol, "glfwGetMonitorName") == 0) return (jlong) hooked_glfwGetMonitorName_impl;
        if (strcmp(symbol, "glfwGetWindowMonitor") == 0) return (jlong) hooked_glfwGetWindowMonitor_impl;
        if (strcmp(symbol, "glfwSetWindowMonitor") == 0) return (jlong) hooked_glfwSetWindowMonitor_impl;
        if (strcmp(symbol, "glfwCreateWindow") == 0) return (jlong) hooked_glfwCreateWindow_impl;
        if (strcmp(symbol, "glfwMakeContextCurrent") == 0) return (jlong) hooked_glfwMakeContextCurrent_impl;
        if (strcmp(symbol, "glfwGetCurrentContext") == 0) return (jlong) hooked_glfwGetCurrentContext_impl;
        if (strcmp(symbol, "glfwSwapBuffers") == 0) return (jlong) hooked_glfwSwapBuffers_impl;
        if (strcmp(symbol, "glfwSwapInterval") == 0) return (jlong) hooked_glfwSwapInterval_impl;
        if (strcmp(symbol, "glfwDestroyWindow") == 0) return (jlong) hooked_glfwDestroyWindow_impl;
        if (strcmp(symbol, "glfwGetProcAddress") == 0 || strcmp(symbol, "glfwGetProcessAddress") == 0)
            return (jlong) hooked_glfwGetProcAddress_impl;
        if (strcmp(symbol, "glfwWindowHint") == 0) return (jlong) hooked_glfwWindowHint_impl;
        if (strcmp(symbol, "glfwDefaultWindowHints") == 0) return (jlong) hooked_glfwDefaultWindowHints_impl;
        if (strcmp(symbol, "glfwGetFramebufferSize") == 0) return (jlong) hooked_glfwGetFramebufferSize_impl;
        if (strcmp(symbol, "glfwGetWindowSize") == 0) return (jlong) hooked_glfwGetWindowSize_impl;
        if (strcmp(symbol, "glfwGetWindowPos") == 0) return (jlong) hooked_glfwGetWindowPos_impl;
        if (strcmp(symbol, "glfwSetWindowPos") == 0) return (jlong) hooked_glfwSetWindowPos_impl;
        if (strcmp(symbol, "glfwSetWindowSize") == 0) return (jlong) hooked_glfwSetWindowSize_impl;
        if (strcmp(symbol, "glfwWindowShouldClose") == 0) return (jlong) hooked_glfwWindowShouldClose_impl;
        if (strcmp(symbol, "glfwSetWindowShouldClose") == 0) return (jlong) hooked_glfwSetWindowShouldClose_impl;
        if (strcmp(symbol, "glfwSetWindowTitle") == 0) return (jlong) hooked_glfwSetWindowTitle_impl;
        if (strcmp(symbol, "glfwShowWindow") == 0) return (jlong) hooked_glfwShowWindow_impl;
        if (strcmp(symbol, "glfwHideWindow") == 0) return (jlong) hooked_glfwHideWindow_impl;
        if (strcmp(symbol, "glfwFocusWindow") == 0) return (jlong) hooked_glfwFocusWindow_impl;
        if (strcmp(symbol, "glfwIconifyWindow") == 0) return (jlong) hooked_glfwIconifyWindow_impl;
        if (strcmp(symbol, "glfwRestoreWindow") == 0) return (jlong) hooked_glfwRestoreWindow_impl;
        if (strcmp(symbol, "glfwMaximizeWindow") == 0) return (jlong) hooked_glfwMaximizeWindow_impl;
        if (strcmp(symbol, "glfwGetWindowAttrib") == 0) return (jlong) hooked_glfwGetWindowAttrib_impl;
        if (strcmp(symbol, "glfwSetWindowAttrib") == 0) return (jlong) hooked_glfwSetWindowAttrib_impl;
        if (strcmp(symbol, "glfwSetInputMode") == 0) return (jlong) hooked_glfwSetInputMode_impl;
        if (strcmp(symbol, "glfwGetInputMode") == 0) return (jlong) hooked_glfwGetInputMode_impl;
        if (strcmp(symbol, "glfwPollEvents") == 0) return (jlong) hooked_glfwPollEvents_impl;
        if (strcmp(symbol, "glfwWaitEvents") == 0) return (jlong) hooked_glfwWaitEvents_impl;
        if (strcmp(symbol, "glfwWaitEventsTimeout") == 0) return (jlong) hooked_glfwWaitEventsTimeout_impl;
        if (strcmp(symbol, "glfwPostEmptyEvent") == 0) return (jlong) hooked_glfwPostEmptyEvent_impl;
        if (strcmp(symbol, "glfwTerminate") == 0) return (jlong) hooked_glfwTerminate_impl;
        if (strcmp(symbol, "glfwVulkanSupported") == 0) return (jlong) hooked_glfwVulkanSupported_impl;

        if (strstr(symbol, "Callback") != NULL)
            return (jlong) hooked_glfwSetCallback_impl;

        if (strncmp(symbol, "glfw", 4) == 0) {
            printf("LWJGL hook v2.10: unlisted %s -> safe stub\n", symbol);
            if (strncmp(symbol, "glfwGet", 7) == 0) return (jlong) glfw_stub_ptr0;
            if (strncmp(symbol, "glfwSet", 7) == 0 || strncmp(symbol, "glfwDestroy", 11) == 0)
                return (jlong) glfw_stub_void;
            return (jlong) glfw_stub_int0;
        }

        if (strncmp(symbol, "gl", 2) == 0) {
            void* mesa = get_mesa_dl_handle();
            if (mesa != NULL) {
                void* (*osm_get_proc)(const char*) = dlsym(mesa, "OSMesaGetProcAddress");
                if (osm_get_proc) {
                    void* sym = osm_get_proc(symbol);
                    if (sym) return (jlong) sym;
                }
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
    LOGI("Installing LWJGL hooks (TURNIP-ZINK v2.10)");
    printf("LWJGL hook: installing hooks (TURNIP-ZINK v2.10)\n");

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
        printf("LWJGL hook: hooks installed (TURNIP-ZINK v2.10)\n");
    }
}
