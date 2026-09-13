//
// FearLauncher — LWJGL dlopen/dlsym hook v2.3 (TURNIP-ZINK)
// Adds glfwGetVideoModes so Monitor init does not NPE on null Buffer.
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

static bool is_zink_renderer() {
    const char* renderer = getenv("POJAV_RENDERER");
    if (renderer == NULL) return false;
    return strcmp(renderer, "turnip_zink") == 0 ||
           strcmp(renderer, "vulkan_zink") == 0;
}

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

static void force_zink_env(void) {
    setenv("GALLIUM_DRIVER", "zink", 1);
    setenv("MESA_LOADER_DRIVER_OVERRIDE", "zink", 1);
    setenv("MESA_GL_VERSION_OVERRIDE", "4.6", 1);
    setenv("MESA_GLSL_VERSION_OVERRIDE", "460", 1);
    setenv("MESA_VK_WSI_PRESENT_MODE", "fifo", 1);
    setenv("MESA_PRESENT_MODE", "fifo", 1);
    setenv("LIBGL_NOERROR", "1", 1);
    setenv("ZINK_DESCRIPTORS", "lazy", 1);
    setenv("mesa_glthread", "false", 1);
}

static volatile int g_glfw_initialized = 0;
static volatile int g_window_created = 0;
static void* g_current_window = NULL;
static bool g_use_osmesa = false;

static int g_fake_monitor = 1;
/* Must match GLFWvidmode layout exactly for LWJGL GLFWVidMode.Buffer */
static struct {
    int width;
    int height;
    int redBits;
    int greenBits;
    int blueBits;
    int refreshRate;
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

static int hooked_glfwInit_impl(void) {
    if (!g_glfw_initialized) {
        force_zink_env();
        bridge_environ.config_renderer = RENDERER_VK_ZINK;
        if (!osmesa_is_loaded()) dlsym_OSMesa();
        if (osmesa_is_loaded() && osm_init()) {
            g_use_osmesa = true;
            printf("LWJGL hook v2.3: using OSMesa bridge\n");
        } else {
            g_use_osmesa = false;
            printf("LWJGL hook v2.3: OSMesa unavailable — GLFW full stub\n");
        }
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
    (void)monitor;
    ensure_vidmode();
    return (void*)&g_fake_vidmode;
}

/* Minecraft Monitor uses this — must not return NULL */
static const void* hooked_glfwGetVideoModes_impl(void* monitor, int* count) {
    (void)monitor;
    ensure_vidmode();
    if (count) *count = 1;
    printf("LWJGL hook v2.3: glfwGetVideoModes -> 1 mode %dx%d@%d\n",
           g_fake_vidmode.width, g_fake_vidmode.height, g_fake_vidmode.refreshRate);
    return (const void*)&g_fake_vidmode;
}

static void* const* hooked_glfwGetMonitors_impl(int* count) {
    static void* monitors[1];
    monitors[0] = (void*)(uintptr_t)&g_fake_monitor;
    if (count) *count = 1;
    return monitors;
}

static void hooked_glfwGetMonitorPos_impl(void* monitor, int* xpos, int* ypos) {
    (void)monitor;
    if (xpos) *xpos = 0;
    if (ypos) *ypos = 0;
}

static void hooked_glfwGetMonitorWorkarea_impl(void* monitor, int* xpos, int* ypos, int* width, int* height) {
    (void)monitor;
    ensure_vidmode();
    if (xpos) *xpos = 0;
    if (ypos) *ypos = 0;
    if (width) *width = g_fake_vidmode.width;
    if (height) *height = g_fake_vidmode.height;
}

static const char* hooked_glfwGetMonitorName_impl(void* monitor) {
    (void)monitor;
    return "FearLauncher-Display";
}

static void* hooked_glfwCreateWindow_impl(int width, int height, const char* title, void* monitor, void* share) {
    printf("LWJGL hook v2.3: glfwCreateWindow %dx%d\n", width, height);
    (void)title; (void)monitor;
    if (bridge_environ.savedWidth <= 0) bridge_environ.savedWidth = width;
    if (bridge_environ.savedHeight <= 0) bridge_environ.savedHeight = height;
    ensure_vidmode();

    if (g_use_osmesa) {
        osm_render_window_t* share_bundle = (share != NULL) ? (osm_render_window_t*) share : NULL;
        osm_render_window_t* bundle = osm_init_context(share_bundle);
        if (bundle != NULL) {
            bundle->state = STATE_RENDERER_ALIVE;
            if (bridge_environ.mainWindowBundle == NULL) {
                bridge_environ.mainWindowBundle = (basic_render_window_t*) bundle;
                bundle->newNativeSurface = bridge_environ.pojavWindow;
            }
            osm_make_current(bundle);
            g_window_created = 1;
            g_current_window = (void*) bundle;
            printf("LWJGL hook v2.3: window OK (OSMesa)\n");
            return g_current_window;
        }
        g_use_osmesa = false;
    }

    g_window_created = 1;
    g_current_window = (void*) 0xDEADBEEF;
    printf("LWJGL hook v2.3: window OK (stub)\n");
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
    void* mesa = get_mesa_dl_handle();
    if (mesa != NULL) {
        void* sym = dlsym(mesa, procname);
        if (sym != NULL) return sym;
        void* (*osm_get_proc)(const char*) = dlsym(mesa, "OSMesaGetProcAddress");
        if (osm_get_proc != NULL) {
            sym = osm_get_proc(procname);
            if (sym != NULL) return sym;
        }
    }
    return dlsym(RTLD_DEFAULT, procname);
}

static void hooked_glfwWindowHint_impl(int hint, int value) { (void)hint; (void)value; }
static void hooked_glfwDefaultWindowHints_impl(void) {}
static void hooked_glfwGetFramebufferSize_impl(void* window, int* width, int* height) {
    ensure_vidmode();
    if (width) *width = bridge_environ.savedWidth > 0 ? bridge_environ.savedWidth : g_fake_vidmode.width;
    if (height) *height = bridge_environ.savedHeight > 0 ? bridge_environ.savedHeight : g_fake_vidmode.height;
    (void)window;
}
static void hooked_glfwGetWindowSize_impl(void* window, int* width, int* height) {
    hooked_glfwGetFramebufferSize_impl(window, width, height);
}
static int hooked_glfwWindowShouldClose_impl(void* window) { (void)window; return 0; }
static void hooked_glfwSetWindowTitle_impl(void* window, const char* title) { (void)window; (void)title; }
static void hooked_glfwShowWindow_impl(void* window) { (void)window; }
static void hooked_glfwHideWindow_impl(void* window) { (void)window; }
static void hooked_glfwFocusWindow_impl(void* window) { (void)window; }
static int hooked_glfwGetWindowAttrib_impl(void* window, int attrib) {
    (void)window;
    if (attrib == 0x00020001) return 1;
    if (attrib == 0x00020004) return 1;
    return 0;
}
static void hooked_glfwPollEvents_impl(void) {}
static void hooked_glfwWaitEvents_impl(void) {}
static void hooked_glfwTerminate_impl(void) {}

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
        if (strcmp(symbol, "glfwWindowShouldClose") == 0) return (jlong) hooked_glfwWindowShouldClose_impl;
        if (strcmp(symbol, "glfwSetWindowTitle") == 0) return (jlong) hooked_glfwSetWindowTitle_impl;
        if (strcmp(symbol, "glfwShowWindow") == 0) return (jlong) hooked_glfwShowWindow_impl;
        if (strcmp(symbol, "glfwHideWindow") == 0) return (jlong) hooked_glfwHideWindow_impl;
        if (strcmp(symbol, "glfwFocusWindow") == 0) return (jlong) hooked_glfwFocusWindow_impl;
        if (strcmp(symbol, "glfwGetWindowAttrib") == 0) return (jlong) hooked_glfwGetWindowAttrib_impl;
        if (strcmp(symbol, "glfwPollEvents") == 0) return (jlong) hooked_glfwPollEvents_impl;
        if (strcmp(symbol, "glfwWaitEvents") == 0) return (jlong) hooked_glfwWaitEvents_impl;
        if (strcmp(symbol, "glfwTerminate") == 0) return (jlong) hooked_glfwTerminate_impl;

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
    LOGI("Installing LWJGL hooks (TURNIP-ZINK v2.3)");
    printf("LWJGL hook: installing hooks (TURNIP-ZINK v2.3)\n");

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
        printf("LWJGL hook: hooks installed (TURNIP-ZINK v2.3)\n");
    }
}
