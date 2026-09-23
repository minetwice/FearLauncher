//
// FearLauncher — LWJGL dlopen/dlsym hook v2.12 (TURNIP-ZINK)
// Hybrid: real libglfw for window/input/pollEvents; OSMesa for GL context
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
    if (fear && (strcmp(fear, "turnip_zink") == 0 || strcmp(fear, "vulkan_zink") == 0 || strcmp(fear, "panvk_zink") == 0 || strcmp(fear, "panfork") == 0))
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
    printf("LWJGL hook v2.12: unset POJAV_RENDERER/POJAV_LAUNCHER (Sodium bypass)\n");
}

/* MC20: Panfork = Gallium panfrost on the ARM kbase kernel driver, via OSMesa.
   No Vulkan loader is needed at all. GL 3.3 unlocked via PAN_MESA_DEBUG=gl3,
   AFBC off (Minecraft block-texture glitches on Mali). */
static bool is_panfork_renderer(void) {
    const char* fear = getenv("FEAR_RENDERER");
    return fear && strcmp(fear, "panfork") == 0;
}

static void force_panfork_env(void) {
    setenv("GALLIUM_DRIVER", "panfrost", 1);
    setenv("MESA_LOADER_DRIVER_OVERRIDE", "panfrost", 1);
    setenv("PAN_MESA_DEBUG", "gl3,noafbc", 1);
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
    printf("LWJGL hook v2.12: PANFORK env active (GALLIUM_DRIVER=panfrost, PAN_MESA_DEBUG=gl3,noafbc)\n");
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
static void* g_libglfw = NULL;
static void* g_libglfw_window = NULL;

static void* load_libglfw(void) {
    if (g_libglfw) return g_libglfw;
    g_libglfw = dlopen("libglfw.so", RTLD_NOW | RTLD_GLOBAL);
    if (!g_libglfw) {
        const char* nd = getenv("POJAV_NATIVEDIR");
        if (nd && nd[0]) {
            char path[512];
            snprintf(path, sizeof(path), "%s/libglfw.so", nd);
            g_libglfw = dlopen(path, RTLD_NOW | RTLD_GLOBAL);
        }
    }
    if (!g_libglfw)
        printf("LWJGL hook v2.12: libglfw.so NOT found (input will be broken)\n");
    else
        printf("LWJGL hook v2.12: libglfw.so loaded %p\n", g_libglfw);
    return g_libglfw;
}

static void* glfw_real(const char* name) {
    void* lib = load_libglfw();
    if (!lib) return NULL;
    return dlsym(lib, name);
}

static bool ensure_vulkan_ptr(void) {
    if (g_vulkan_handle == NULL) {
        g_vulkan_handle = pojavexec_loadVulkanDriver();
    }
    if (g_vulkan_handle == NULL) {
        printf("LWJGL hook v2.12: Vulkan load FAILED\n");
        return false;
    }
    char hex[32];
    snprintf(hex, sizeof(hex), "%lx", (unsigned long)(uintptr_t)g_vulkan_handle);
    setenv("VULKAN_PTR", hex, 1);
    printf("LWJGL hook v2.12: VULKAN_PTR=%s (handle=%p)\n", hex, g_vulkan_handle);
    return true;
}

static void force_zink_env(void) {
    setenv("GALLIUM_DRIVER", "zink", 1);
    setenv("MESA_LOADER_DRIVER_OVERRIDE", "zink", 1);
    setenv("MESA_GL_VERSION_OVERRIDE", "4.6", 1);
    setenv("MESA_GLSL_VERSION_OVERRIDE", "460", 1);
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
    /* MC16: do NOT force ZINK_DESCRIPTORS/lazy here - it overwrites the
       ZINK_DEBUG=noreorder / conservative env applied from Java
       (JREUtils.setupRendererEnv) for Mali / system-Vulkan devices. */
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

/* Forward all callbacks to real libglfw so mouse/key/cursor events work */
static void* hooked_glfwSetCallback_impl(void* window, void* callback) {
    /* This is only used as last-resort stub when real symbol is missing.
       Prefer real libglfw for input callbacks. */
    (void)window; (void)callback;
    return NULL;
}

static int hooked_glfwInit_impl(void) {
    if (!g_glfw_initialized) {
        if (is_panfork_renderer()) {
            force_panfork_env();
            /* Same OSMesa present path as zink; no Vulkan driver is loaded. */
            bridge_environ.config_renderer = RENDERER_VK_ZINK;
        } else {
            force_zink_env();
            bridge_environ.config_renderer = RENDERER_VK_ZINK;
            ensure_vulkan_ptr();
        }
        load_libglfw();
        /* Also init real libglfw so input queue/surfaceOwner path works */
        int (*real_init)(void) = (int (*)(void)) glfw_real("glfwInit");
        if (real_init) {
            int r = real_init();
            printf("LWJGL hook v2.12: real glfwInit -> %d\n", r);
        }
        if (!osmesa_is_loaded()) dlsym_OSMesa();
        if (osmesa_is_loaded() && osm_init()) {
            g_use_osmesa = true;
            printf("LWJGL hook v2.12: OSMesa bridge\n");
        } else {
            g_use_osmesa = false;
            printf("LWJGL hook v2.12: GLFW full stub (no OSMesa)\n");
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
    printf("LWJGL hook v2.12: glfwCreateWindow %dx%d\n", width, height);
    (void)title; (void)monitor;
    if (bridge_environ.savedWidth <= 0) bridge_environ.savedWidth = width;
    if (bridge_environ.savedHeight <= 0) bridge_environ.savedHeight = height;
    if (bridge_environ.pojavWindow != NULL) {
        int sw = ANativeWindow_getWidth(bridge_environ.pojavWindow);
        int sh = ANativeWindow_getHeight(bridge_environ.pojavWindow);
        if (sw > 0 && sh > 0) {
            bridge_environ.savedWidth = sw;
            bridge_environ.savedHeight = sh;
            width = sw; height = sh;
            printf("LWJGL hook v2.12: using surface size %dx%d\n", sw, sh);
        }
    }
    ensure_vidmode();
    g_fake_vidmode.width = bridge_environ.savedWidth;
    g_fake_vidmode.height = bridge_environ.savedHeight;
    ensure_vulkan_ptr();

    typedef void* (*create_fn)(int, int, const char*, void*, void*);
    typedef void (*hint_fn)(int, int);
    create_fn real_create = (create_fn) glfw_real("glfwCreateWindow");
    hint_fn real_hint = (hint_fn) glfw_real("glfwWindowHint");
    if (real_hint) {
        real_hint(0x00022001, 0); /* GLFW_CLIENT_API = GLFW_NO_API */
    }
    if (real_create) {
        g_libglfw_window = real_create(width, height, title ? title : "FearLauncher", monitor, share);
        printf("LWJGL hook v2.12: real glfwCreateWindow -> %p\n", g_libglfw_window);
        /* Force focus + visible cursor so title-screen clicks work */
        if (g_libglfw_window) {
            void (*real_focus)(void*) = (void (*)(void*)) glfw_real("glfwFocusWindow");
            void (*real_show)(void*) = (void (*)(void*)) glfw_real("glfwShowWindow");
            void (*real_input)(void*, int, int) = (void (*)(void*, int, int)) glfw_real("glfwSetInputMode");
            if (real_show) real_show(g_libglfw_window);
            if (real_focus) real_focus(g_libglfw_window);
            /* GLFW_CURSOR = 0x00033001, GLFW_CURSOR_NORMAL = 0x00034001 */
            if (real_input) real_input(g_libglfw_window, 0x00033001, 0x00034001);
            printf("LWJGL hook v2.12: focused window + normal cursor\n");
        }
    } else {
        printf("LWJGL hook v2.12: real glfwCreateWindow missing\n");
    }

    if (g_use_osmesa) {
        printf("LWJGL hook v2.12: calling OSMesaCreateContext...\n");
        osm_render_window_t* share_bundle = (share != NULL) ? (osm_render_window_t*) share : NULL;
        osm_render_window_t* bundle = osm_init_context(share_bundle);
        if (bundle != NULL) {
            bundle->state = STATE_RENDERER_ALIVE;
            if (bridge_environ.mainWindowBundle == NULL) {
                bridge_environ.mainWindowBundle = (basic_render_window_t*) bundle;
                bundle->newNativeSurface = bridge_environ.pojavWindow;
            }
            osm_make_current(bundle);
            g_current_window = g_libglfw_window ? g_libglfw_window : (void*) bundle;
            if (glGetString_p) {
                const char* vendor = (const char*)glGetString_p(0x1F00);
                const char* renderer = (const char*)glGetString_p(0x1F01);
                const char* version = (const char*)glGetString_p(0x1F02);
                printf("LWJGL hook v2.12: GL_VENDOR=%s\n", vendor ? vendor : "(null)");
                printf("LWJGL hook v2.12: GL_RENDERER=%s\n", renderer ? renderer : "(null)");
                printf("LWJGL hook v2.12: GL_VERSION=%s\n", version ? version : "(null)");
            }
            printf("LWJGL hook v2.12: window OK (OSMesa+libglfw)\n");
            return g_current_window;
        }
        printf("LWJGL hook v2.12: OSMesaCreateContext failed\n");
        g_use_osmesa = false;
    }

    if (g_libglfw_window) {
        g_current_window = g_libglfw_window;
        return g_libglfw_window;
    }
    g_current_window = (void*) 0xDEADBEEF;
    printf("LWJGL hook v2.12: window OK (stub)\n");
    return g_current_window;
}

static void hooked_glfwMakeContextCurrent_impl(void* window) {
    if (g_use_osmesa) {
        if (window == NULL) { osm_make_current(NULL); g_current_window = NULL; return; }
        osm_render_window_t* bundle = (osm_render_window_t*) bridge_environ.mainWindowBundle;
        if (bundle) osm_make_current(bundle);
        else osm_make_current((osm_render_window_t*) window);
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
    if (g_use_osmesa && bridge_environ.mainWindowBundle != NULL) {
        osm_render_window_t* bundle = (osm_render_window_t*) bridge_environ.mainWindowBundle;
        if (bundle->context != NULL) {
            OSMesaDestroyContext_p(bundle->context);
            bundle->context = NULL;
        }
        if (bundle->nativeSurface != NULL) {
            ANativeWindow_release(bundle->nativeSurface);
            bundle->nativeSurface = NULL;
        }
        free(bundle);
        bridge_environ.mainWindowBundle = NULL;
    }
    void (*real_destroy)(void*) = (void (*)(void*)) glfw_real("glfwDestroyWindow");
    if (real_destroy && g_libglfw_window)
        real_destroy(g_libglfw_window);
    g_libglfw_window = NULL;
    if (g_current_window == window) g_current_window = NULL;
}

/* ---- MC20 panfork: glBlitFramebuffer CPU fallback (GL blit broken on Valhall v11) ---- */
static void* g_blit_real = NULL;
static void* g_blit_fn_ptr = NULL;

static void hooked_glBlitFramebuffer_impl(int srcX0, int srcY0, int srcX1, int srcY1,
                                          int dstX0, int dstY0, int dstX1, int dstY1,
                                          unsigned int mask, unsigned int filter) {
    void* h;
    void* sym;
    void (*pReadPixels)(int, int, int, int, unsigned, unsigned, void*);
    void (*pDrawPixels)(int, int, unsigned, unsigned, const void*);
    void (*pWindowPos2i)(int, int);
    void (*pPixelZoom)(float, float);
    void (*pEnable)(unsigned);
    void (*pDisable)(unsigned);
    int (*pIsEnabled)(unsigned);
    int sw, sh, dw, dh;
    int depth_on, blend_on, scissor_on, stencil_on;
    unsigned char* buf;
    float zoomx, zoomy;

    if ((mask & ~0x4000u) != 0u) {
        void (*real)(int, int, int, int, int, int, int, int, unsigned, unsigned);
        if (g_blit_real != NULL) {
            memcpy(&real, &g_blit_real, sizeof(real));
            real(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
        }
        return;
    }
    if ((mask & 0x4000u) == 0u) return;
    sw = srcX1 - srcX0;
    sh = srcY1 - srcY0;
    dw = dstX1 - dstX0;
    dh = dstY1 - dstY0;
    if (sw <= 0 || sh <= 0 || dw <= 0 || dh <= 0) return;
    h = get_mesa_dl_handle();
    if (h == NULL) return;
    if (g_blit_real == NULL) g_blit_real = dlsym(h, "glBlitFramebuffer");
    sym = dlsym(h, "glReadPixels"); memcpy(&pReadPixels, &sym, sizeof(sym));
    sym = dlsym(h, "glDrawPixels"); memcpy(&pDrawPixels, &sym, sizeof(sym));
    sym = dlsym(h, "glWindowPos2i"); memcpy(&pWindowPos2i, &sym, sizeof(sym));
    sym = dlsym(h, "glPixelZoom"); memcpy(&pPixelZoom, &sym, sizeof(sym));
    sym = dlsym(h, "glEnable"); memcpy(&pEnable, &sym, sizeof(sym));
    sym = dlsym(h, "glDisable"); memcpy(&pDisable, &sym, sizeof(sym));
    sym = dlsym(h, "glIsEnabled"); memcpy(&pIsEnabled, &sym, sizeof(sym));
    if (pReadPixels == NULL || pDrawPixels == NULL || pWindowPos2i == NULL ||
        pPixelZoom == NULL || pEnable == NULL || pDisable == NULL || pIsEnabled == NULL) {
        fprintf(stderr, "LWJGL hook: BLITFALLBACK symbols missing\n");
        return;
    }
    buf = malloc((size_t) sw * sh * 4);
    if (buf == NULL) return;
    pReadPixels(srcX0, srcY0, sw, sh, 0x1908u, 0x1401u, buf);
    depth_on = pIsEnabled(0x0B71u);
    blend_on = pIsEnabled(0x0BE2u);
    scissor_on = pIsEnabled(0x0C11u);
    stencil_on = pIsEnabled(0x0B90u);
    if (depth_on) pDisable(0x0B71u);
    if (blend_on) pDisable(0x0BE2u);
    if (scissor_on) pDisable(0x0C11u);
    if (stencil_on) pDisable(0x0B90u);
    zoomx = (float) dw / (float) sw;
    zoomy = (float) dh / (float) sh;
    pPixelZoom(zoomx, zoomy);
    pWindowPos2i(dstX0, dstY0);
    pDrawPixels(sw, sh, 0x1908u, 0x1401u, buf);
    pPixelZoom(1.0f, 1.0f);
    if (depth_on) pEnable(0x0B71u);
    if (blend_on) pEnable(0x0BE2u);
    if (scissor_on) pEnable(0x0C11u);
    if (stencil_on) pEnable(0x0B90u);
    free(buf);
}

static void* hooked_glfwGetProcAddress_impl(const char* procname) {
    if (!procname) return NULL;
    if (strcmp(procname, "glBlitFramebuffer") == 0 && is_panfork_renderer()) {
        printf("LWJGL hook: glBlitFramebuffer -> CPU fallback (panfork)\n");
        return (void*) hooked_glBlitFramebuffer_impl;
    }
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
        printf("LWJGL hook v2.12: GetProcAddress MISS %s\n", procname);
    }
    return sym;
}

static void hooked_glfwWindowHint_impl(int h, int v) {
    void (*real)(int,int) = (void (*)(int,int)) glfw_real("glfwWindowHint");
    if (real) real(h, v);
}
static void hooked_glfwDefaultWindowHints_impl(void) {
    void (*real)(void) = (void (*)(void)) glfw_real("glfwDefaultWindowHints");
    if (real) real();
}
static void hooked_glfwGetFramebufferSize_impl(void* w, int* width, int* height) {
    if (bridge_environ.pojavWindow != NULL) {
        int sw = ANativeWindow_getWidth(bridge_environ.pojavWindow);
        int sh = ANativeWindow_getHeight(bridge_environ.pojavWindow);
        if (sw > 0) bridge_environ.savedWidth = sw;
        if (sh > 0) bridge_environ.savedHeight = sh;
    }
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
    if (bridge_environ.pojavWindow != NULL) {
        int sw = ANativeWindow_getWidth(bridge_environ.pojavWindow);
        int sh = ANativeWindow_getHeight(bridge_environ.pojavWindow);
        if (sw > 0 && sh > 0) {
            bridge_environ.savedWidth = sw;
            bridge_environ.savedHeight = sh;
            return;
        }
    }
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
static void hooked_glfwPollEvents_impl(void) {
    void (*real)(void) = (void (*)(void)) glfw_real("glfwPollEvents");
    if (real) real();
}
static void hooked_glfwWaitEvents_impl(void) {
    void (*real)(void) = (void (*)(void)) glfw_real("glfwWaitEvents");
    if (real) real();
}
static void hooked_glfwWaitEventsTimeout_impl(double t) {
    void (*real)(double) = (void (*)(double)) glfw_real("glfwWaitEventsTimeout");
    if (real) real(t);
}
static void hooked_glfwPostEmptyEvent_impl(void) {
    void (*real)(void) = (void (*)(void)) glfw_real("glfwPostEmptyEvent");
    if (real) real();
}
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
    int mode = (int) jmode;
    void* handle = dlopen(filename, mode);
    return (jlong) handle;
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
        if (strcmp(symbol, "glfwPollEvents") == 0) return (jlong) hooked_glfwPollEvents_impl;
        if (strcmp(symbol, "glfwWaitEvents") == 0) return (jlong) hooked_glfwWaitEvents_impl;
        if (strcmp(symbol, "glfwWaitEventsTimeout") == 0) return (jlong) hooked_glfwWaitEventsTimeout_impl;
        if (strcmp(symbol, "glfwPostEmptyEvent") == 0) return (jlong) hooked_glfwPostEmptyEvent_impl;
        if (strcmp(symbol, "glfwTerminate") == 0) return (jlong) hooked_glfwTerminate_impl;
        if (strcmp(symbol, "glfwVulkanSupported") == 0) return (jlong) hooked_glfwVulkanSupported_impl;

        /* Input path: always prefer real Android GLFW (dnbglfw) so touch/mouse
           injection from CallbackBridge/GLFW.sendMouseEvent reaches the game.
           Never swallow mouse-button / cursor / key callbacks with a no-op. */
        if (strstr(symbol, "Callback") != NULL ||
            strcmp(symbol, "glfwGetCursorPos") == 0 ||
            strcmp(symbol, "glfwSetCursorPos") == 0 ||
            strcmp(symbol, "glfwGetKey") == 0 ||
            strcmp(symbol, "glfwGetMouseButton") == 0 ||
            strcmp(symbol, "glfwSetInputMode") == 0 ||
            strcmp(symbol, "glfwGetInputMode") == 0 ||
            strcmp(symbol, "glfwRawMouseMotionSupported") == 0 ||
            strcmp(symbol, "glfwCreateCursor") == 0 ||
            strcmp(symbol, "glfwCreateStandardCursor") == 0 ||
            strcmp(symbol, "glfwDestroyCursor") == 0 ||
            strcmp(symbol, "glfwSetCursor") == 0 ||
            strcmp(symbol, "glfwGetKeyName") == 0 ||
            strcmp(symbol, "glfwGetKeyScancode") == 0 ||
            strcmp(symbol, "glfwSetClipboardString") == 0 ||
            strcmp(symbol, "glfwGetClipboardString") == 0 ||
            strcmp(symbol, "glfwJoystickPresent") == 0 ||
            strncmp(symbol, "glfwGetJoystick", 15) == 0 ||
            strncmp(symbol, "glfwJoystick", 12) == 0 ||
            strncmp(symbol, "glfwGetGamepad", 14) == 0 ||
            strcmp(symbol, "glfwUpdateGamepadMappings") == 0 ||
            strcmp(symbol, "glfwFocusWindow") == 0 ||
            strcmp(symbol, "glfwShowWindow") == 0) {
            void* real = glfw_real(symbol);
            if (real == NULL) real = dlsym(RTLD_DEFAULT, symbol);
            if (real != NULL) {
                return (jlong) real;
            }
            /* Only use no-op stub if symbol truly missing — log it */
            if (strstr(symbol, "Callback") != NULL) {
                printf("LWJGL hook v2.12: WARNING missing Callback symbol %s\n", symbol);
                return (jlong) hooked_glfwSetCallback_impl;
            }
            if (strncmp(symbol, "glfwGet", 7) == 0) return (jlong) glfw_stub_ptr0;
            if (strncmp(symbol, "glfwSet", 7) == 0 || strncmp(symbol, "glfwDestroy", 11) == 0)
                return (jlong) glfw_stub_void;
            return (jlong) glfw_stub_int0;
        }

        if (strncmp(symbol, "glfw", 4) == 0) {
            void* real = glfw_real(symbol);
            if (real == NULL) real = dlsym(RTLD_DEFAULT, symbol);
            if (real != NULL) {
                printf("LWJGL hook v2.12: unlisted %s -> real libglfw\n", symbol);
                return (jlong) real;
            }
            printf("LWJGL hook v2.12: unlisted %s -> safe stub\n", symbol);
            if (strncmp(symbol, "glfwGet", 7) == 0) return (jlong) glfw_stub_ptr0;
            if (strncmp(symbol, "glfwSet", 7) == 0 || strncmp(symbol, "glfwDestroy", 11) == 0)
                return (jlong) glfw_stub_void;
            return (jlong) glfw_stub_int0;
        }

        if (strncmp(symbol, "gl", 2) == 0) {
            if (strcmp(symbol, "glBlitFramebuffer") == 0 && is_panfork_renderer()) {
                if (g_blit_fn_ptr == NULL) {
                    void* tmp = NULL;
                    memcpy(&tmp, &hooked_glBlitFramebuffer_impl, sizeof(tmp));
                    g_blit_fn_ptr = tmp;
                }
                printf("LWJGL hook: ndlsym glBlitFramebuffer -> CPU fallback (panfork)\n");
                return (jlong) g_blit_fn_ptr;
            }
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
    LOGI("Installing LWJGL hooks (TURNIP-ZINK v2.12)");
    printf("LWJGL hook: installing hooks (TURNIP-ZINK v2.12)\n");

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
        printf("LWJGL hook: hooks installed (TURNIP-ZINK v2.12)\n");
    }
}
