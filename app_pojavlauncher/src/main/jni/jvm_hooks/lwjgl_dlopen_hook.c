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

/* ---- FEARFRAMECOUNT / FEARBRAND ------------------------------------------------
   Frame counting and GL branding, kept in one place.

   The lag report's frame rate used to be guessed from the game log, where the
   busiest repeating line was a chat message, so it read a constant ~1 fps and the
   auto-scale tuner believed it. The real number is the count of frames the
   renderer actually presented: the game resolves glfwSwapBuffers / eglSwapBuffers
   through LWJGL's DynamicLinkLoader (ndlsym) and through glfwGetProcAddress, so
   both present entry points are counted here. The monotonic count is handed to
   Java through JREUtils.getPresentedFrameCount(), which the LagWatch sampler
   polls each sample.

   The same layer rewrites GL_VENDOR / GL_RENDERER so the in-game F3 debug screen
   shows the launcher instead of the prebuilt LTW wrapper's name ("MojoLauncher").
   Only those two strings change: GL_VERSION and GL_EXTENSIONS are returned
   untouched, because mods and Sodium-style workarounds parse them. */
#define FEAR_GL_VENDOR   "FearLauncher"
#define FEAR_GL_RENDERER "FearLTW (OpenGL ES 3)"

static uint64_t g_presented_frames = 0;

static unsigned int (*g_real_eglSwapBuffers)(void*, void*) = NULL;
static void (*g_real_glfwSwapBuffers)(void*) = NULL;
static unsigned char* (*g_real_glGetString)(unsigned int) = NULL;
static void* (*g_real_glfwGetProcAddress)(const char*) = NULL;

/* Resolve a real entry point from the handle the game looked it up in, falling
   back to the global namespace. */
static void* fear_resolve(const void* handle, const char* name) {
    void* sym = (handle != NULL) ? dlsym((void*) handle, name) : NULL;
    if (sym == NULL) sym = dlsym(RTLD_DEFAULT, name);
    return sym;
}

/* Present path: count the frame, then hand the call to the real implementation. */
static unsigned int fear_eglSwapBuffers_hook(void* dpy, void* surface) {
    __atomic_fetch_add(&g_presented_frames, 1, __ATOMIC_RELAXED);
    if (g_real_eglSwapBuffers == NULL)
        g_real_eglSwapBuffers = (unsigned int (*)(void*, void*)) fear_resolve(NULL, "eglSwapBuffers");
    if (g_real_eglSwapBuffers == NULL) return 1; /* EGL_TRUE: never break the frame */
    return g_real_eglSwapBuffers(dpy, surface);
}

static void fear_glfwSwapBuffers_hook(void* window) {
    __atomic_fetch_add(&g_presented_frames, 1, __ATOMIC_RELAXED);
    if (g_real_glfwSwapBuffers == NULL)
        g_real_glfwSwapBuffers = (void (*)(void*)) fear_resolve(NULL, "glfwSwapBuffers");
    if (g_real_glfwSwapBuffers != NULL) g_real_glfwSwapBuffers(window);
}

/* Branding: static buffers, so the returned pointer stays stable. */
static unsigned char fear_gl_vendor_str[]   = FEAR_GL_VENDOR;
static unsigned char fear_gl_renderer_str[] = FEAR_GL_RENDERER;

static unsigned char* fear_glGetString_hook(unsigned int name) {
    if (name == 0x1F00u) return fear_gl_vendor_str;   /* GL_VENDOR   */
    if (name == 0x1F01u) return fear_gl_renderer_str; /* GL_RENDERER */
    /* GL_VERSION (0x1F02) and GL_EXTENSIONS (0x1F03) pass through untouched. */
    if (g_real_glGetString == NULL)
        g_real_glGetString = (unsigned char* (*)(unsigned int)) fear_resolve(NULL, "glGetString");
    if (g_real_glGetString == NULL) return NULL;
    return g_real_glGetString(name);
}

/* LWJGL's GLFW binding resolves GL entry points through glfwGetProcAddress, so for
   the LTW path (which gets the real libglfw loader) the branding and the present
   count have to be applied here too. Everything other than the three names below is
   forwarded to the real loader unchanged, so no other GL resolution changes. */
static void* fear_glfwGetProcAddress_hook(const char* procname) {
    if (g_real_glfwGetProcAddress == NULL) return NULL;
    if (procname == NULL) return g_real_glfwGetProcAddress(NULL);
    if (strcmp(procname, "glGetString") == 0) {
        if (g_real_glGetString == NULL)
            g_real_glGetString = (unsigned char* (*)(unsigned int)) g_real_glfwGetProcAddress(procname);
        return (void*) fear_glGetString_hook;
    }
    if (strcmp(procname, "eglSwapBuffers") == 0) {
        if (g_real_eglSwapBuffers == NULL)
            g_real_eglSwapBuffers = (unsigned int (*)(void*, void*)) g_real_glfwGetProcAddress(procname);
        return (void*) fear_eglSwapBuffers_hook;
    }
    if (strcmp(procname, "glfwSwapBuffers") == 0) return (void*) fear_glfwSwapBuffers_hook;
    return g_real_glfwGetProcAddress(procname);
}

JNIEXPORT jlong JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_getPresentedFrameCount(JNIEnv* env, jclass clazz) {
    (void) env;
    (void) clazz;
    return (jlong) __atomic_load_n(&g_presented_frames, __ATOMIC_RELAXED);
}

static int g_is_zink_cached = -1;

static bool is_zink_renderer() {
    if (g_is_zink_cached >= 0) return g_is_zink_cached == 1;
    const char* fear = getenv("FEAR_RENDERER");
    const char* gallium = getenv("GALLIUM_DRIVER");
    const char* renderer = getenv("POJAV_RENDERER");
    bool z = false;
    if (fear && (strcmp(fear, "turnip_zink") == 0 || strcmp(fear, "vulkan_zink") == 0
                 || strcmp(fear, "holy_zink_kopper") == 0)) /* FEARWIRE-HOLYZINK-OSMESA (v10.13): route holy through the hooked-glfw + OSMesa bridge path */
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

/* Legacy panfork detection - kept for the blit CPU fallback below; can never
   trigger anymore since the panfork renderer was removed (always returns false). */
static bool is_panfork_renderer(void) {
    const char* fear = getenv("FEAR_RENDERER");
    return fear && strcmp(fear, "panfork") == 0;
}

#include <unistd.h>
/* FEARWIRE-HOLYZINK-ROTATE8b: ANativeWindow_setBuffersTransform is not in the
   NDK public symbol list, so it cannot be linked directly - resolve it at
   runtime (RTLD_DEFAULT, then explicit dlopen; the log-65 dlopen path
   resolved fine). */
static int32_t (*fear_setBuffersTransform_p)(struct ANativeWindow*, int32_t);
static int32_t fear_applyRotateT(int rotateT) {
    if (fear_setBuffersTransform_p == NULL) {
        fear_setBuffersTransform_p = (int32_t (*)(struct ANativeWindow*, int32_t)) dlsym(RTLD_DEFAULT, "ANativeWindow_setBuffersTransform");
        if (fear_setBuffersTransform_p == NULL) {
            void* fearH = dlopen("libnativewindow.so", RTLD_NOW);
            if (fearH == NULL) fearH = dlopen("libandroid.so", RTLD_NOW);
            if (fearH != NULL)
                fear_setBuffersTransform_p = (int32_t (*)(struct ANativeWindow*, int32_t)) dlsym(fearH, "ANativeWindow_setBuffersTransform");
        }
        printf("FEARWIRE-ROTATE8: setBuffersTransform resolved=%p\n", (void*) fear_setBuffersTransform_p);
    }
    if (fear_setBuffersTransform_p == NULL || bridge_environ.pojavWindow == NULL) return -1;
    return fear_setBuffersTransform_p(bridge_environ.pojavWindow, rotateT);
}

/* FEARWIRE-HOLYZINK-ROTATE7: waits (up to 60s) for FEAR_RENDERER - the env is
   set at game launch, after the surface already exists - then applies the
   portrait buffer geometry for holy zink before the game creates its window. */
static volatile int fear_rotate_started = 0;
static void* fear_rotate_env_wait(void* arg) {
    (void) arg;
    for (int i = 0; i < 300; i++) {
        const char* r = getenv("FEAR_RENDERER");
        if (r != NULL) {
            if (strcmp(r, "holy_zink_kopper") != 0) {
                printf("FEARWIRE-ROTATE7: renderer=%s, no rotation needed\n", r);
                return NULL;
            }
            if (bridge_environ.pojavWindow != NULL
                && bridge_environ.savedWidth > 0 && bridge_environ.savedHeight > 0) {
                const char* fearT = getenv("FEAR_ROTATE_T");
                int rotateT = fearT != NULL ? atoi(fearT) : 1;
                if (rotateT != 0 && rotateT != 1 && rotateT != 3 && rotateT != 4) rotateT = 1;
                if (rotateT != 0)
                    ANativeWindow_setBuffersGeometry(bridge_environ.pojavWindow,
                            bridge_environ.savedHeight, bridge_environ.savedWidth,
                            ANativeWindow_getFormat(bridge_environ.pojavWindow));
                fear_applyRotateT(rotateT);
                printf("FEARWIRE-ROTATE9: late renderer pick-up, geometry+transform(%d) applied\n", rotateT);
            }
            return NULL;
        }
        usleep(200 * 1000);
    }
    printf("FEARWIRE-ROTATE7: renderer env never appeared\n");
    return NULL;
}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_setupBridgeWindow(JNIEnv* env, jclass clazz, jobject surface) {
    if (surface == NULL) return;
    bridge_environ.pojavWindow = ANativeWindow_fromSurface(env, surface);
    bridge_environ.savedWidth = ANativeWindow_getWidth(bridge_environ.pojavWindow);
    bridge_environ.savedHeight = ANativeWindow_getHeight(bridge_environ.pojavWindow);
    LOGI("Bridge window set: %p (%dx%d)", bridge_environ.pojavWindow,
         bridge_environ.savedWidth, bridge_environ.savedHeight);
    {
        /* FEARWIRE-HOLYZINK-ROTATE6: unconditional marker - proves which
           build is running and whether the renderer env is visible here. */
        const char* fearRendererDbg = getenv("FEAR_RENDERER");
        printf("FEARWIRE v10.9: setupBridgeWindow FEAR_RENDERER=%s surface=%dx%d\n",
               fearRendererDbg ? fearRendererDbg : "(null)",
               bridge_environ.savedWidth, bridge_environ.savedHeight);
    }
    /* FEARWIRE-DISPSPEC: publish the real surface size as the display mode
       so the prebuilt libglfw.so reports a sane landscape monitor to the game */
    /* FEARWIRE-HOLYZINK-ROTATE8: the Android Vulkan WSI queues buffers with
       preTransform from the window transform hint (the display rotation); MC
       does not pre-rotate, so content shows +90 rotated (sky on the right).
       Set the producer transform to ROTATE_90 so the hint becomes IDENTITY
       and the swapchain presents unrotated - turnip-like: landscape window,
       no geometry swap, no input remap. FEAR_ROTATE_T env (0/1/3/4, from
       holy_rotate.txt in the game dir) overrides without a rebuild. */
    {
        const char* fearRenderer = getenv("FEAR_RENDERER");
        if (fearRenderer && strcmp(fearRenderer, "holy_zink_kopper") == 0
            && getenv("FEAR_HOLY_KOPPER") != NULL /* FEARWIRE-HOLYZINK-OSMESA (v10.13): kopper-only rotation experiments, OFF for the OSMesa bridge */
            && bridge_environ.pojavWindow != NULL
            && bridge_environ.savedWidth > 0 && bridge_environ.savedHeight > 0) {
            /* FEARWIRE-HOLYZINK-ROTATE9: the Android pre-rotation contract -
               swapped-dimension (portrait) buffers PLUS the ROTATE_90 buffer
               transform. log-66 proved ROTATE_90 is the correct compensating
               direction; pairing it with swapped geometry fills the screen.
               holy_rotate.txt (0/1/3/4) overrides without a rebuild. */
            const char* fearT = getenv("FEAR_ROTATE_T");
            int rotateT = fearT != NULL ? atoi(fearT) : 1;
            if (rotateT != 0 && rotateT != 1 && rotateT != 3 && rotateT != 4) rotateT = 1;
            if (rotateT != 0) {
                ANativeWindow_setBuffersGeometry(bridge_environ.pojavWindow,
                                bridge_environ.savedHeight, /* portrait width */
                                bridge_environ.savedWidth,  /* portrait height */
                                ANativeWindow_getFormat(bridge_environ.pojavWindow));
                printf("FEARWIRE-ROTATE9: portrait buffer geometry %dx%d APPLIED\n",
                       bridge_environ.savedHeight, bridge_environ.savedWidth);
            }
            if (fear_applyRotateT(rotateT) == 0)
                printf("FEARWIRE-ROTATE9: setBuffersTransform(%d) APPLIED (pre-rotation contract)\n", rotateT);
            else
                printf("FEARWIRE-ROTATE9: setBuffersTransform(%d) FAILED to resolve/apply\n", rotateT);
            pojavexec_setDisplayParams(bridge_environ.savedHeight, bridge_environ.savedWidth, 60);
        } else {
            pojavexec_setDisplayParams(bridge_environ.savedWidth, bridge_environ.savedHeight, 60);
        }
    }
    /* FEARWIRE-HOLYZINK-ROTATE7: the renderer env usually appears only at
       game launch (after the surface exists) - watch for it in the background
       and apply the portrait geometry for holy zink as soon as it shows up. */
    {
        const char* fearRendererNow = getenv("FEAR_RENDERER");
        if ((fearRendererNow == NULL || strcmp(fearRendererNow, "holy_zink_kopper") != 0)
            && getenv("FEAR_HOLY_KOPPER") != NULL /* FEARWIRE-HOLYZINK-OSMESA (v10.13) */
            && !fear_rotate_started) {
            fear_rotate_started = 1;
            pthread_t fearRotateThread;
            if (pthread_create(&fearRotateThread, NULL, fear_rotate_env_wait, NULL) == 0)
                pthread_detach(fearRotateThread);
            printf("FEARWIRE-ROTATE7: renderer env watcher thread started\n");
        }
    }
    /* FEARWIRE-HOLYZINK-ROTATE8: superseded - do NOT clear the transform;
       the ROTATE_90 producer transform above is what neutralizes the WSI's
       pre-rotation. */
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
        force_zink_env();
        bridge_environ.config_renderer = RENDERER_VK_ZINK;
        ensure_vulkan_ptr();
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
    /* Count the presented frame (zink / OSMesa path); the LTW path is counted by
       fear_glfwSwapBuffers_hook below. */
    __atomic_fetch_add(&g_presented_frames, 1, __ATOMIC_RELAXED);
    if (g_use_osmesa) osm_swap_buffers();
    (void)window;
}

/* FEAR-FPSUNLOCK: the game asks for swap interval 1 (Minecraft's VSync option),
   which pins presentation to the display's refresh rate - on a 60Hz surface that
   is the "60 fps lock" the user sees even on a 120Hz panel. Force the interval
   to 0 so the frame rate is bounded only by the GPU, following the technique
   already used in tools/fearrender/fearpatch.py. Escape hatch: MG_FORCE_VSYNC=1
   in the environment restores the requested interval. */
void setNativeWindowSwapInterval(struct ANativeWindow* nativeWindow, int swapInterval);
static void hooked_glfwSwapInterval_impl(int interval) {
    if (g_use_osmesa) {
        /* zink / OSMesa path - unchanged. */
        osm_swap_interval(interval);
        return;
    }
    /* LTW and the other GL renderers: the launcher owns the game's Surface, so
       apply the interval straight to the ANativeWindow (no EGL needed), exactly
       like the zink bridge does. */
    if (interval != 0) {
        static int fear_vsync_pref = -1;
        if (fear_vsync_pref == -1) {
            const char* fear_env = getenv("MG_FORCE_VSYNC");
            fear_vsync_pref = (fear_env != NULL && strcmp(fear_env, "1") == 0) ? 1 : 0;
        }
        if (!fear_vsync_pref) interval = 0;
    }
    if (bridge_environ.pojavWindow != NULL) {
        setNativeWindowSwapInterval(bridge_environ.pojavWindow, interval);
    } else {
        void (*fear_real)(int) = (void (*)(int)) glfw_real("glfwSwapInterval");
        if (fear_real) fear_real(interval);
    }
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

/* ---- legacy panfork blit CPU fallback (GL blit broken on Valhall v11).
   v2.7: glBlitNamedFramebuffer (DSA, MC 1.21.6+ present path) + request logging.
   Unreachable now that the panfork renderer is gone; kept for reference. ---- */
static void* g_blit_real = NULL;
static unsigned char* g_blit_buf = NULL;
static size_t g_blit_buf_size = 0;
static int g_req_log_count = 0;

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
    if (g_blit_buf == NULL || g_blit_buf_size < (size_t) sw * sh * 4) {
        free(g_blit_buf);
        g_blit_buf_size = (size_t) sw * sh * 4;
        g_blit_buf = malloc(g_blit_buf_size);
    }
    buf = g_blit_buf;
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
}

static void hooked_glBlitNamedFramebuffer_impl(unsigned int readFbo, unsigned int drawFbo,
                                                int srcX0, int srcY0, int srcX1, int srcY1,
                                                int dstX0, int dstY0, int dstX1, int dstY1,
                                                unsigned int mask, unsigned int filter) {
    void* h;
    void* sym;
    void (*pBindFb)(unsigned, unsigned);
    int (*pGetInt)(unsigned, int*);
    int saveRead = 0, saveDraw = 0;

    if ((mask & 0x4000u) == 0u) return;
    h = get_mesa_dl_handle();
    if (h == NULL) return;
    sym = dlsym(h, "glBindFramebuffer"); memcpy(&pBindFb, &sym, sizeof(sym));
    sym = dlsym(h, "glGetIntegerv"); memcpy(&pGetInt, &sym, sizeof(sym));
    if (pBindFb == NULL || pGetInt == NULL) {
        fprintf(stderr, "LWJGL hook: BLITFALLBACK named symbols missing\n");
        return;
    }
    pGetInt(0x8CAAu, &saveRead);
    pGetInt(0x8CA9u, &saveDraw);
    pBindFb(0x8CAAu, readFbo);
    pBindFb(0x8CA9u, drawFbo);
    hooked_glBlitFramebuffer_impl(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    pBindFb(0x8CAAu, (unsigned) saveRead);
    pBindFb(0x8CA9u, (unsigned) saveDraw);
}

static void* hooked_glfwGetProcAddress_impl(const char* procname) {
    if (!procname) return NULL;
    if (strncmp(procname, "gl", 2) == 0 && g_req_log_count < 1500) {
        g_req_log_count++;
        printf("LWJGL hook: REQ %s\n", procname);
    }
    /* FEARBRAND / FEARFRAMECOUNT: brand the GL vendor/renderer and count presented
       frames, whichever way the game resolves these entry points. */
    if (strcmp(procname, "glGetString") == 0) {
        if (g_real_glGetString == NULL) {
            if (glGetString_p) g_real_glGetString = (unsigned char* (*)(unsigned int)) glGetString_p;
            else g_real_glGetString = (unsigned char* (*)(unsigned int)) fear_resolve(NULL, "glGetString");
        }
        return (void*) fear_glGetString_hook;
    }
    if (strcmp(procname, "eglSwapBuffers") == 0) return (void*) fear_eglSwapBuffers_hook;
    if (strcmp(procname, "glfwSwapBuffers") == 0) return (void*) fear_glfwSwapBuffers_hook;
    if (strcmp(procname, "glBlitFramebuffer") == 0 && is_panfork_renderer()) {
        printf("LWJGL hook: glBlitFramebuffer -> CPU fallback (panfork)\n");
        return (void*) hooked_glBlitFramebuffer_impl;
    }
    if (strcmp(procname, "glBlitNamedFramebuffer") == 0 && is_panfork_renderer()) {
        printf("LWJGL hook: glBlitNamedFramebuffer -> CPU fallback (panfork)\n");
        return (void*) hooked_glBlitNamedFramebuffer_impl;
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
            if (g_req_log_count < 1500) {
                g_req_log_count++;
                printf("LWJGL hook: NREQ %s\n", symbol);
            }
            if (strcmp(symbol, "glBlitFramebuffer") == 0 && is_panfork_renderer()) {
                printf("LWJGL hook: ndlsym glBlitFramebuffer -> CPU fallback (panfork)\n");
                return (jlong) hooked_glBlitFramebuffer_impl;
            }
            if (strcmp(symbol, "glBlitNamedFramebuffer") == 0 && is_panfork_renderer()) {
                printf("LWJGL hook: ndlsym glBlitNamedFramebuffer -> CPU fallback (panfork)\n");
                return (jlong) hooked_glBlitNamedFramebuffer_impl;
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

    /* FEAR-FPSUNLOCK: route the game's swap-interval lookups through the launcher
       hook for every renderer, not just zink, so LTW's frame rate is not pinned
       to the display refresh either. */
    if (strcmp(symbol, "glfwSwapInterval") == 0 || strcmp(symbol, "pojavSwapInterval") == 0)
        return (jlong) hooked_glfwSwapInterval_impl;

    /* FEARFRAMECOUNT: count the real present path for every renderer, so the lag
       report measures frames the renderer actually presented. The real entry
       point is resolved from the handle the game asked for the symbol in. */
    if (strcmp(symbol, "eglSwapBuffers") == 0) {
        if (g_real_eglSwapBuffers == NULL)
            g_real_eglSwapBuffers = (unsigned int (*)(void*, void*)) fear_resolve((void*) handle, symbol);
        return (jlong) fear_eglSwapBuffers_hook;
    }
    if (strcmp(symbol, "glfwSwapBuffers") == 0) {
        if (g_real_glfwSwapBuffers == NULL)
            g_real_glfwSwapBuffers = (void (*)(void*)) fear_resolve((void*) handle, symbol);
        return (jlong) fear_glfwSwapBuffers_hook;
    }
    /* FEARBRAND: report the launcher, not the LTW wrapper, as the GL vendor. */
    if (strcmp(symbol, "glGetString") == 0) {
        if (g_real_glGetString == NULL)
            g_real_glGetString = (unsigned char* (*)(unsigned int)) fear_resolve((void*) handle, symbol);
        return (jlong) fear_glGetString_hook;
    }
    /* FEARBRAND on the glfwGetProcAddress path: wrap the real loader so GL_VENDOR /
       GL_RENDERER are branded and the present path is counted, without changing how
       any other GL function is resolved. */
    if (strcmp(symbol, "glfwGetProcAddress") == 0 || strcmp(symbol, "glfwGetProcessAddress") == 0) {
        if (g_real_glfwGetProcAddress == NULL)
            g_real_glfwGetProcAddress = (void* (*)(const char*)) fear_resolve((void*) handle, symbol);
        if (g_real_glfwGetProcAddress != NULL) return (jlong) fear_glfwGetProcAddress_hook;
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
