// FearLauncher EGL facade for Zink/OSMesa + Krypton GL resolver
// ANR FIX: skip OSMesa Zink when PanVK not fully loaded (FEAR_PANVK_OK!=1).
#include <dlfcn.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <unistd.h>
#include <android/native_window.h>
#include <android/log.h>
#include <setjmp.h>
#include <signal.h>
#include "ctxbridges/osmesa_loader.h"
#include "ctxbridges/osm_bridge.h"
#include "ctxbridges/bridge_environ.h"

#ifndef EGL_OPENGL_API
#define EGL_OPENGL_API 0x30A2
#endif
#ifndef EGL_OPENGL_ES_API
#define EGL_OPENGL_ES_API 0x30A0
#endif
#ifndef EGL_TRUE
#define EGL_TRUE 1
#endif
#ifndef EGL_FALSE
#define EGL_FALSE 0
#endif
#ifndef EGL_SUCCESS
#define EGL_SUCCESS 0x3000
#endif
#ifndef EGL_BAD_ALLOC
#define EGL_BAD_ALLOC 0x3003
#endif
#ifndef EGL_CLIENT_APIS
#define EGL_CLIENT_APIS 0x308D
#endif
#ifndef EGL_EXTENSIONS
#define EGL_EXTENSIONS 0x3055
#endif
#ifndef EGL_VENDOR
#define EGL_VENDOR 0x3053
#endif
#ifndef EGL_VERSION
#define EGL_VERSION 0x3054
#endif
#ifndef EGL_SURFACE_TYPE
#define EGL_SURFACE_TYPE 0x3033
#endif
#ifndef EGL_WINDOW_BIT
#define EGL_WINDOW_BIT 0x0004
#endif
#ifndef EGL_RENDERABLE_TYPE
#define EGL_RENDERABLE_TYPE 0x3040
#endif
#ifndef EGL_OPENGL_BIT
#define EGL_OPENGL_BIT 0x0008
#endif
#ifndef EGL_OPENGL_ES2_BIT
#define EGL_OPENGL_ES2_BIT 0x0004
#endif
#ifndef EGL_OPENGL_ES_BIT
#define EGL_OPENGL_ES_BIT 0x0001
#endif
#ifndef EGL_PBUFFER_BIT
#define EGL_PBUFFER_BIT 0x0001
#endif
#ifndef EGL_COLOR_BUFFER_TYPE
#define EGL_COLOR_BUFFER_TYPE 0x303F
#endif
#ifndef EGL_RGB_BUFFER
#define EGL_RGB_BUFFER 0x308E
#endif
#ifndef EGL_CONFIG_CAVEAT
#define EGL_CONFIG_CAVEAT 0x3027
#endif
#ifndef EGL_SAMPLES
#define EGL_SAMPLES 0x3031
#endif
#ifndef EGL_SAMPLE_BUFFERS
#define EGL_SAMPLE_BUFFERS 0x3032
#endif
#ifndef EGL_TRANSPARENT_TYPE
#define EGL_TRANSPARENT_TYPE 0x3034
#endif
#ifndef EGL_NATIVE_RENDERABLE
#define EGL_NATIVE_RENDERABLE 0x302D
#endif
#ifndef EGL_NATIVE_VISUAL_ID
#define EGL_NATIVE_VISUAL_ID 0x302E
#endif
#ifndef EGL_NATIVE_VISUAL_TYPE
#define EGL_NATIVE_VISUAL_TYPE 0x302F
#endif
#ifndef EGL_CONFIG_ID
#define EGL_CONFIG_ID 0x3028
#endif
#ifndef EGL_BUFFER_SIZE
#define EGL_BUFFER_SIZE 0x3020
#endif
#ifndef EGL_LEVEL
#define EGL_LEVEL 0x3029
#endif
#ifndef EGL_MAX_PBUFFER_WIDTH
#define EGL_MAX_PBUFFER_WIDTH 0x302C
#endif
#ifndef EGL_MAX_PBUFFER_HEIGHT
#define EGL_MAX_PBUFFER_HEIGHT 0x302A
#endif
#ifndef EGL_MAX_PBUFFER_PIXELS
#define EGL_MAX_PBUFFER_PIXELS 0x302B
#endif
#ifndef EGL_BIND_TO_TEXTURE_RGB
#define EGL_BIND_TO_TEXTURE_RGB 0x3039
#endif
#ifndef EGL_BIND_TO_TEXTURE_RGBA
#define EGL_BIND_TO_TEXTURE_RGBA 0x303A
#endif
#ifndef EGL_MIN_SWAP_INTERVAL
#define EGL_MIN_SWAP_INTERVAL 0x303B
#endif
#ifndef EGL_MAX_SWAP_INTERVAL
#define EGL_MAX_SWAP_INTERVAL 0x303C
#endif
#ifndef EGL_LUMINANCE_SIZE
#define EGL_LUMINANCE_SIZE 0x303D
#endif
#ifndef EGL_ALPHA_MASK_SIZE
#define EGL_ALPHA_MASK_SIZE 0x303E
#endif
#ifndef EGL_CONFORMANT
#define EGL_CONFORMANT 0x3042
#endif
#ifndef EGL_BLUE_SIZE
#define EGL_BLUE_SIZE 0x3022
#endif
#ifndef EGL_GREEN_SIZE
#define EGL_GREEN_SIZE 0x3023
#endif
#ifndef EGL_RED_SIZE
#define EGL_RED_SIZE 0x3024
#endif
#ifndef EGL_ALPHA_SIZE
#define EGL_ALPHA_SIZE 0x3021
#endif
#ifndef EGL_DEPTH_SIZE
#define EGL_DEPTH_SIZE 0x3025
#endif
#ifndef EGL_STENCIL_SIZE
#define EGL_STENCIL_SIZE 0x3026
#endif
#ifndef EGL_NONE
#define EGL_NONE 0x3038
#endif
#ifndef EGL_NO_CONTEXT
#define EGL_NO_CONTEXT ((void*)0)
#endif

typedef void* EGLDisplay;
typedef void* EGLContext;
typedef void* EGLSurface;
typedef void* EGLConfig;
typedef void* EGLNativeWindowType;
typedef unsigned int EGLenum;
typedef int EGLint;
typedef unsigned int EGLBoolean;

static void* ng_handle = NULL;
static void* (*gl4es_GetProcAddress)(const char*) = NULL;
static void* (*real_eglGetProcAddress)(const char*) = NULL;
static int (*real_eglBindAPI)(int) = NULL;
static int init_done = 0;
static int egl_error = EGL_SUCCESS;
static int egl_initialized = 0;
static EGLenum current_api = EGL_OPENGL_ES_API;
static int g_fake_config = 1;

static int is_zink_renderer(void) {
    const char* fear = getenv("FEAR_RENDERER");
    if (!fear) return 0;
    return (strcmp(fear, "fear_render") == 0 || strcmp(fear, "panvk_zink") == 0
         || strcmp(fear, "turnip_zink") == 0 || strcmp(fear, "vulkan_zink") == 0);
}

static void ensure_init(void) {
    if (init_done) return;
    init_done = 1;
    void* egl = dlopen("libEGL.so", RTLD_NOW | RTLD_NOLOAD);
    if (!egl) egl = dlopen("/system/lib64/libEGL.so", RTLD_NOW);
    if (egl) {
        real_eglGetProcAddress = (void*(*)(const char*))dlsym(egl, "eglGetProcAddress");
        real_eglBindAPI = (int(*)(int))dlsym(egl, "eglBindAPI");
    }
    if (is_zink_renderer()) return;
    const char* native_dir = getenv("POJAV_NATIVEDIR");
    if (native_dir && native_dir[0]) {
        char path[512];
        snprintf(path, sizeof(path), "%s/libng_gl4es.so", native_dir);
        ng_handle = dlopen(path, RTLD_NOW | RTLD_GLOBAL | RTLD_NOLOAD);
        if (!ng_handle) ng_handle = dlopen(path, RTLD_NOW | RTLD_GLOBAL);
    }
    if (!ng_handle) ng_handle = dlopen("libng_gl4es.so", RTLD_NOW | RTLD_GLOBAL);
    if (ng_handle) {
        gl4es_GetProcAddress = (void*(*)(const char*))dlsym(ng_handle, "gl4es_GetProcAddress");
        if (!gl4es_GetProcAddress)
            gl4es_GetProcAddress = (void*(*)(const char*))dlsym(ng_handle, "glXGetProcAddress");
    }
}

__attribute__((visibility("default"))) EGLint eglGetError(void) { EGLint e = egl_error; egl_error = EGL_SUCCESS; return e; }
__attribute__((visibility("default"))) EGLDisplay eglGetDisplay(void* display_id) { ensure_init(); return (EGLDisplay)0x1; }
__attribute__((visibility("default"))) EGLBoolean eglInitialize(EGLDisplay dpy, EGLint* major, EGLint* minor) {
    ensure_init(); if (major) *major = 1; if (minor) *minor = 5; egl_initialized = 1; return EGL_TRUE;
}
__attribute__((visibility("default"))) EGLBoolean eglTerminate(EGLDisplay dpy) { egl_initialized = 0; return EGL_TRUE; }
__attribute__((visibility("default"))) const char* eglQueryString(EGLDisplay dpy, EGLint name) {
    ensure_init();
    if (name == EGL_CLIENT_APIS) return "OpenGL OpenGL_ES";
    if (name == EGL_VENDOR) return "FearLauncher";
    if (name == EGL_VERSION) return "1.5 FearLauncher-OSMesa";
    if (name == EGL_EXTENSIONS) return "EGL_KHR_create_context EGL_KHR_surfaceless_context";
    return "";
}
__attribute__((visibility("default"))) EGLBoolean eglBindAPI(EGLenum api) {
    ensure_init();
    if (is_zink_renderer() && (api == EGL_OPENGL_API || api == EGL_OPENGL_ES_API)) { current_api = api; return EGL_TRUE; }
    if (real_eglBindAPI) return (EGLBoolean)real_eglBindAPI((int)api);
    current_api = api; return EGL_TRUE;
}
__attribute__((visibility("default"))) EGLenum eglQueryAPI(void) { return current_api; }
__attribute__((visibility("default"))) EGLBoolean eglGetConfigs(EGLDisplay dpy, EGLConfig* configs, EGLint config_size, EGLint* num_config) {
    if (num_config) *num_config = 1;
    if (configs && config_size > 0) configs[0] = (EGLConfig)&g_fake_config;
    return EGL_TRUE;
}
__attribute__((visibility("default"))) EGLBoolean eglChooseConfig(EGLDisplay dpy, const EGLint* attrib_list, EGLConfig* configs, EGLint config_size, EGLint* num_config) {
    if (num_config) *num_config = 1;
    if (configs && config_size > 0) configs[0] = (EGLConfig)&g_fake_config;
    return EGL_TRUE;
}
__attribute__((visibility("default"))) EGLBoolean eglGetConfigAttrib(EGLDisplay dpy, EGLConfig config, EGLint attribute, EGLint* value) {
    if (!value) return EGL_FALSE;
    switch (attribute) {
        case EGL_BUFFER_SIZE: *value = 32; break;
        case EGL_RED_SIZE: case EGL_GREEN_SIZE: case EGL_BLUE_SIZE: case EGL_ALPHA_SIZE: *value = 8; break;
        case EGL_DEPTH_SIZE: *value = 24; break;
        case EGL_STENCIL_SIZE: *value = 8; break;
        case EGL_SURFACE_TYPE: *value = EGL_WINDOW_BIT | EGL_PBUFFER_BIT; break;
        case EGL_RENDERABLE_TYPE: *value = EGL_OPENGL_BIT | EGL_OPENGL_ES2_BIT | EGL_OPENGL_ES_BIT; break;
        case EGL_COLOR_BUFFER_TYPE: *value = EGL_RGB_BUFFER; break;
        case EGL_CONFIG_CAVEAT: *value = EGL_NONE; break;
        case EGL_CONFIG_ID: *value = 1; break;
        default: *value = 0; break;
    }
    return EGL_TRUE;
}
__attribute__((visibility("default"))) EGLSurface eglCreateWindowSurface(EGLDisplay dpy, EGLConfig config, EGLNativeWindowType win, const EGLint* attrib_list) {
    if (win && is_zink_renderer()) {
        bridge_environ.pojavWindow = (ANativeWindow*)win;
        bridge_environ.savedWidth = ANativeWindow_getWidth((ANativeWindow*)win);
        bridge_environ.savedHeight = ANativeWindow_getHeight((ANativeWindow*)win);
    }
    return (EGLSurface)(win ? win : (void*)0x2);
}
__attribute__((visibility("default"))) EGLSurface eglCreatePbufferSurface(EGLDisplay dpy, EGLConfig config, const EGLint* attrib_list) { return (EGLSurface)0x3; }
__attribute__((visibility("default"))) EGLBoolean eglDestroySurface(EGLDisplay dpy, EGLSurface surface) { return EGL_TRUE; }

static sigjmp_buf g_osmesa_jmp;
static volatile int g_osmesa_guard;
static void fear_osmesa_segv(int sig) { (void)sig; if (g_osmesa_guard) siglongjmp(g_osmesa_jmp, 1); }
static void fear_osmesa_alrm(int sig) { (void)sig; if (g_osmesa_guard) siglongjmp(g_osmesa_jmp, 2); }

static OSMesaContext fear_safe_osmesa_create(OSMesaContext share) {
    if (!OSMesaCreateContext_p) return NULL;
    struct sigaction sa, old_sa, sa_alrm, old_alrm;
    memset(&sa, 0, sizeof(sa));
    sa.sa_handler = fear_osmesa_segv;
    sigemptyset(&sa.sa_mask);
    memset(&sa_alrm, 0, sizeof(sa_alrm));
    sa_alrm.sa_handler = fear_osmesa_alrm;
    sigemptyset(&sa_alrm.sa_mask);
    g_osmesa_guard = 1;
    sigaction(SIGSEGV, &sa, &old_sa);
    sigaction(SIGALRM, &sa_alrm, &old_alrm);
    alarm(1);
    OSMesaContext ctx = NULL;
    int jc = sigsetjmp(g_osmesa_jmp, 1);
    if (jc == 0) {
        ctx = OSMesaCreateContext_p(GL_RGBA, share);
    } else {
        ctx = NULL;
        if (jc == 2)
            __android_log_print(ANDROID_LOG_WARN, "FearRender", "OSMesaCreateContext timed out (1s) — avoid ANR");
    }
    alarm(0);
    g_osmesa_guard = 0;
    sigaction(SIGALRM, &old_alrm, NULL);
    sigaction(SIGSEGV, &old_sa, NULL);
    return ctx;
}

__attribute__((visibility("default")))
EGLContext eglCreateContext(EGLDisplay dpy, EGLConfig config, EGLContext share_context, const EGLint* attrib_list) {
    ensure_init();
    if (!is_zink_renderer()) { egl_error = EGL_BAD_ALLOC; return EGL_NO_CONTEXT; }
    if (!osmesa_is_loaded()) dlsym_OSMesa();
    if (!osmesa_is_loaded() || !OSMesaCreateContext_p) { egl_error = EGL_BAD_ALLOC; return EGL_NO_CONTEXT; }

    osm_render_window_t* share = share_context ? (osm_render_window_t*)share_context : NULL;
    OSMesaContext share_ctx = share ? share->context : NULL;

    /* ANR FIX: only try OSMesa/Zink if PanVK fully loaded.
       Half-broken Zink on OSMesa hangs the render thread → "App is not responding". */
    OSMesaContext octx = NULL;
    const char* panvk_ok = getenv("FEAR_PANVK_OK");
    int try_zink = (panvk_ok && panvk_ok[0] == '1') ||
                   (getenv("FEAR_FORCE_ZINK") && getenv("FEAR_FORCE_ZINK")[0] == '1');
    if (try_zink) {
        setenv("GALLIUM_DRIVER", "zink", 1);
        setenv("MESA_LOADER_DRIVER_OVERRIDE", "zink", 1);
        unsetenv("LIBGL_ALWAYS_SOFTWARE");
        setenv("MESA_GL_VERSION_OVERRIDE", "4.6", 1);
        setenv("MESA_GLSL_VERSION_OVERRIDE", "460", 1);
        octx = fear_safe_osmesa_create(share_ctx);
        if (octx)
            __android_log_print(ANDROID_LOG_INFO, "FearRender", "Zink context ok");
        else
            __android_log_print(ANDROID_LOG_WARN, "FearRender", "Zink failed/timeout — system path");
    } else {
        __android_log_print(ANDROID_LOG_WARN, "FearRender",
            "Skip OSMesa Zink (FEAR_PANVK_OK!=1) — system EGL path (no ANR hang)");
    }

    if (!octx) {
        void* egl = dlopen("/system/lib64/libEGL.so", RTLD_NOW);
        if (!egl) egl = dlopen("libEGL.so", RTLD_NOW);
        if (!egl) { egl_error = EGL_BAD_ALLOC; return EGL_NO_CONTEXT; }
        void* (*getDisp)(void*) = dlsym(egl, "eglGetDisplay");
        int (*init)(void*, int*, int*) = dlsym(egl, "eglInitialize");
        int (*bindApi)(int) = dlsym(egl, "eglBindAPI");
        int (*choose)(void*, const int*, void*, int, int*) = dlsym(egl, "eglChooseConfig");
        void* (*createCtx)(void*, void*, void*, const int*) = dlsym(egl, "eglCreateContext");
        if (!getDisp || !createCtx) { egl_error = EGL_BAD_ALLOC; return EGL_NO_CONTEXT; }
        void* sys_dpy = getDisp((void*)0);
        if (init) init(sys_dpy, 0, 0);
        if (bindApi) bindApi(0x30A0);
        int attribs[] = {0x3040, 0x0040, 0x3024, 8, 0x3023, 8, 0x3022, 8, 0x3021, 8, 0x3025, 24, 0x3033, 4, 0x3038};
        void* cfg = 0; int n = 0;
        if (choose) choose(sys_dpy, attribs, &cfg, 1, &n);
        if (n < 1) { int simple[] = {0x3040, 4, 0x3038}; if (choose) choose(sys_dpy, simple, &cfg, 1, &n); }
        int ctxa[] = {0x3098, 3, 0x3038};
        void* ctx = createCtx(sys_dpy, cfg, 0, ctxa);
        if (!ctx) { egl_error = EGL_BAD_ALLOC; return EGL_NO_CONTEXT; }
        const char* nd = getenv("POJAV_NATIVEDIR");
        if (nd) {
            char p[512]; snprintf(p, sizeof(p), "%s/libng_gl4es.so", nd);
            void* h = dlopen(p, RTLD_NOW | RTLD_GLOBAL);
            if (h) { gl4es_GetProcAddress = (void*(*)(const char*))dlsym(h, "gl4es_GetProcAddress"); ng_handle = h; }
        }
        osm_render_window_t* win = calloc(1, sizeof(osm_render_window_t));
        if (!win) { egl_error = EGL_BAD_ALLOC; return EGL_NO_CONTEXT; }
        win->context = (OSMesaContext)ctx;
        win->state = 2;
        return (EGLContext)win;
    }

    osm_render_window_t* win = calloc(1, sizeof(osm_render_window_t));
    if (!win) {
        if (OSMesaDestroyContext_p) OSMesaDestroyContext_p(octx);
        egl_error = EGL_BAD_ALLOC;
        return EGL_NO_CONTEXT;
    }
    win->context = octx;
    return (EGLContext)win;
}

__attribute__((visibility("default"))) EGLBoolean eglDestroyContext(EGLDisplay dpy, EGLContext ctx) {
    if (is_zink_renderer() && ctx) {
        osm_render_window_t* win = (osm_render_window_t*)ctx;
        if (win->state != 2 && win->context && OSMesaDestroyContext_p) OSMesaDestroyContext_p(win->context);
        free(win->color_buffer);
        free(win);
    }
    return EGL_TRUE;
}

__attribute__((visibility("default"))) EGLBoolean eglMakeCurrent(EGLDisplay dpy, EGLSurface draw, EGLSurface read, EGLContext ctx) {
    ensure_init();
    if (!is_zink_renderer()) return EGL_FALSE;
    if (!ctx) return EGL_TRUE;
    osm_render_window_t* win = (osm_render_window_t*)ctx;
    if (win->state == 2) {
        void* egl = dlopen("/system/lib64/libEGL.so", RTLD_NOW | RTLD_NOLOAD);
        if (!egl) egl = dlopen("libEGL.so", RTLD_NOW | RTLD_NOLOAD);
        int (*mk)(void*, void*, void*, void*) = egl ? dlsym(egl, "eglMakeCurrent") : NULL;
        void* (*getDisp)(void*) = egl ? dlsym(egl, "eglGetDisplay") : NULL;
        void* (*createWin)(void*, void*, void*, const int*) = egl ? dlsym(egl, "eglCreateWindowSurface") : NULL;
        int (*choose)(void*, const int*, void*, int, int*) = egl ? dlsym(egl, "eglChooseConfig") : NULL;
        if (mk && getDisp) {
            void* sys_dpy = getDisp((void*)0);
            void* surf = draw;
            if ((!surf || surf == (void*)0x2) && bridge_environ.pojavWindow) surf = bridge_environ.pojavWindow;
            if (surf && surf != (void*)0x2 && createWin && choose) {
                static void* g_sys_surf = NULL;
                if (!g_sys_surf) {
                    void* cfg = 0; int n = 0;
                    int simple[] = {0x3040, 4, 0x3038};
                    choose(sys_dpy, simple, &cfg, 1, &n);
                    if (n > 0) g_sys_surf = createWin(sys_dpy, cfg, surf, NULL);
                }
                if (g_sys_surf) surf = g_sys_surf;
            }
            return mk(sys_dpy, surf, surf, (void*)win->context) ? EGL_TRUE : EGL_FALSE;
        }
        return EGL_FALSE;
    }
    if (draw && draw != (EGLSurface)0x2 && draw != (EGLSurface)0x3) {
        win->newNativeSurface = (ANativeWindow*)draw;
        bridge_environ.pojavWindow = (ANativeWindow*)draw;
    } else if (bridge_environ.pojavWindow) {
        win->newNativeSurface = bridge_environ.pojavWindow;
    }
    osm_make_current(win);
    return EGL_TRUE;
}

__attribute__((visibility("default"))) EGLBoolean eglSurfaceAttrib(EGLDisplay dpy, EGLSurface surface, EGLint attribute, EGLint value) { return EGL_TRUE; }
__attribute__((visibility("default"))) EGLBoolean eglSwapBuffers(EGLDisplay dpy, EGLSurface surface) {
    if (is_zink_renderer()) {
        osm_render_window_t* cur = osm_get_current();
        if (cur && cur->state == 2) {
            void* egl = dlopen("/system/lib64/libEGL.so", RTLD_NOW | RTLD_NOLOAD);
            if (!egl) egl = dlopen("libEGL.so", RTLD_NOW | RTLD_NOLOAD);
            int (*swap)(void*, void*) = egl ? dlsym(egl, "eglSwapBuffers") : NULL;
            void* (*getDisp)(void*) = egl ? dlsym(egl, "eglGetDisplay") : NULL;
            if (swap && getDisp) return swap(getDisp((void*)0), surface) ? EGL_TRUE : EGL_FALSE;
        }
        osm_swap_buffers();
    }
    return EGL_TRUE;
}
__attribute__((visibility("default"))) EGLBoolean eglSwapInterval(EGLDisplay dpy, EGLint interval) { return EGL_TRUE; }
__attribute__((visibility("default"))) EGLBoolean eglReleaseThread(void) { return EGL_TRUE; }
__attribute__((visibility("default"))) int eglBindAPI_hook(int api) { return (int)eglBindAPI((EGLenum)api); }
__attribute__((visibility("default"))) const char* eglQueryString_hook(void* display, int name) { return eglQueryString((EGLDisplay)display, (EGLint)name); }
__attribute__((visibility("default"))) void* eglGetProcAddress_hook(const char* procname);
__attribute__((visibility("default"))) void* eglGetProcAddress(const char* procname) { return eglGetProcAddress_hook(procname); }

__attribute__((visibility("default"))) void* eglGetProcAddress_hook(const char* procname) {
    if (!procname) return NULL;
    ensure_init();
    if (strcmp(procname, "eglBindAPI") == 0) return (void*)eglBindAPI;
    if (strcmp(procname, "eglQueryString") == 0) return (void*)eglQueryString;
    if (strcmp(procname, "eglGetDisplay") == 0) return (void*)eglGetDisplay;
    if (strcmp(procname, "eglInitialize") == 0) return (void*)eglInitialize;
    if (strcmp(procname, "eglTerminate") == 0) return (void*)eglTerminate;
    if (strcmp(procname, "eglGetError") == 0) return (void*)eglGetError;
    if (strcmp(procname, "eglChooseConfig") == 0) return (void*)eglChooseConfig;
    if (strcmp(procname, "eglGetConfigs") == 0) return (void*)eglGetConfigs;
    if (strcmp(procname, "eglGetConfigAttrib") == 0) return (void*)eglGetConfigAttrib;
    if (strcmp(procname, "eglCreateContext") == 0) return (void*)eglCreateContext;
    if (strcmp(procname, "eglDestroyContext") == 0) return (void*)eglDestroyContext;
    if (strcmp(procname, "eglCreateWindowSurface") == 0) return (void*)eglCreateWindowSurface;
    if (strcmp(procname, "eglCreatePbufferSurface") == 0) return (void*)eglCreatePbufferSurface;
    if (strcmp(procname, "eglDestroySurface") == 0) return (void*)eglDestroySurface;
    if (strcmp(procname, "eglMakeCurrent") == 0) return (void*)eglMakeCurrent;
    if (strcmp(procname, "eglSwapBuffers") == 0) return (void*)eglSwapBuffers;
    if (strcmp(procname, "eglSurfaceAttrib") == 0) return (void*)eglSurfaceAttrib;
    if (strcmp(procname, "eglSwapInterval") == 0) return (void*)eglSwapInterval;
    if (strcmp(procname, "eglQueryAPI") == 0) return (void*)eglQueryAPI;
    if (strcmp(procname, "eglReleaseThread") == 0) return (void*)eglReleaseThread;
    if (strcmp(procname, "eglGetProcAddress") == 0) return (void*)eglGetProcAddress;
    if (gl4es_GetProcAddress && strncmp(procname, "gl", 2) == 0 && strncmp(procname, "glfw", 4) != 0) {
        void* sym = gl4es_GetProcAddress(procname);
        if (sym) return sym;
        if (ng_handle) { void* s = dlsym(ng_handle, procname); if (s) return s; }
    }
    if (is_zink_renderer() && osmesa_is_loaded()) {
        void* mesa = get_mesa_dl_handle();
        if (mesa) { void* s = dlsym(mesa, procname); if (s) return s; }
        if (OSMesaGetProcAddress_p) { void* s = OSMesaGetProcAddress_p(procname); if (s) return s; }
    }
    if (real_eglGetProcAddress) { void* sym = real_eglGetProcAddress(procname); if (sym) return sym; }
    return dlsym(RTLD_DEFAULT, procname);
}

static void* (*g_real_dlsym_early)(void*, const char*) = NULL;
static int g_hooks_installed = 0;
static void* dlsym_egl_redirect_early(void* handle, const char* symbol) {
    if (symbol && is_zink_renderer()) {
        if (strcmp(symbol, "eglChooseConfig") == 0) return (void*)eglChooseConfig;
        if (strcmp(symbol, "eglGetConfigs") == 0) return (void*)eglGetConfigs;
        if (strcmp(symbol, "eglGetConfigAttrib") == 0) return (void*)eglGetConfigAttrib;
        if (strcmp(symbol, "eglGetDisplay") == 0) return (void*)eglGetDisplay;
        if (strcmp(symbol, "eglInitialize") == 0) return (void*)eglInitialize;
        if (strcmp(symbol, "eglCreateContext") == 0) return (void*)eglCreateContext;
        if (strcmp(symbol, "eglMakeCurrent") == 0) return (void*)eglMakeCurrent;
        if (strcmp(symbol, "eglCreateWindowSurface") == 0) return (void*)eglCreateWindowSurface;
        if (strcmp(symbol, "eglBindAPI") == 0) return (void*)eglBindAPI;
        if (strcmp(symbol, "eglGetProcAddress") == 0) return (void*)eglGetProcAddress;
        if (strcmp(symbol, "eglQueryString") == 0) return (void*)eglQueryString;
        if (strcmp(symbol, "eglSwapBuffers") == 0) return (void*)eglSwapBuffers;
        if (strcmp(symbol, "eglDestroyContext") == 0) return (void*)eglDestroyContext;
        if (strcmp(symbol, "eglDestroySurface") == 0) return (void*)eglDestroySurface;
        if (strcmp(symbol, "eglSwapInterval") == 0) return (void*)eglSwapInterval;
        if (strcmp(symbol, "eglCreatePbufferSurface") == 0) return (void*)eglCreatePbufferSurface;
        if (strcmp(symbol, "eglGetError") == 0) return (void*)eglGetError;
        if (strcmp(symbol, "eglTerminate") == 0) return (void*)eglTerminate;
        if (strcmp(symbol, "eglQueryAPI") == 0) return (void*)eglQueryAPI;
        if (strcmp(symbol, "eglReleaseThread") == 0) return (void*)eglReleaseThread;
    }
    if (g_real_dlsym_early) return g_real_dlsym_early(handle, symbol);
    return NULL;
}

__attribute__((visibility("default"))) void fear_install_zink_egl_hooks(void) {
    if (g_hooks_installed || !is_zink_renderer()) return;
    void* bh = dlopen("libbytehook.so", RTLD_NOW);
    if (!bh) return;
    int (*bytehook_init)(int, int) = (int (*)(int, int))dlsym(bh, "bytehook_init");
    void* (*bytehook_hook_all)(const char*, const char*, void*, void*, void*) =
        (void* (*)(const char*, const char*, void*, void*, void*))dlsym(bh, "bytehook_hook_all");
    if (!bytehook_hook_all) return;
    if (bytehook_init) bytehook_init(0, 0);
    if (!g_real_dlsym_early) {
        void* libc = dlopen("libc.so", RTLD_NOW | RTLD_NOLOAD);
        if (!libc) libc = dlopen("libc.so.6", RTLD_NOW | RTLD_NOLOAD);
        if (libc) g_real_dlsym_early = (void*(*)(void*, const char*))dlsym(libc, "dlsym");
        if (!g_real_dlsym_early) g_real_dlsym_early = (void*(*)(void*, const char*))dlsym(RTLD_NEXT, "dlsym");
    }
    bytehook_hook_all(NULL, "eglChooseConfig", (void*)eglChooseConfig, NULL, NULL);
    bytehook_hook_all(NULL, "eglGetConfigs", (void*)eglGetConfigs, NULL, NULL);
    bytehook_hook_all(NULL, "eglGetConfigAttrib", (void*)eglGetConfigAttrib, NULL, NULL);
    bytehook_hook_all(NULL, "eglGetDisplay", (void*)eglGetDisplay, NULL, NULL);
    bytehook_hook_all(NULL, "eglInitialize", (void*)eglInitialize, NULL, NULL);
    bytehook_hook_all(NULL, "eglCreateContext", (void*)eglCreateContext, NULL, NULL);
    bytehook_hook_all(NULL, "eglMakeCurrent", (void*)eglMakeCurrent, NULL, NULL);
    bytehook_hook_all(NULL, "eglCreateWindowSurface", (void*)eglCreateWindowSurface, NULL, NULL);
    bytehook_hook_all(NULL, "eglBindAPI", (void*)eglBindAPI, NULL, NULL);
    bytehook_hook_all(NULL, "eglGetProcAddress", (void*)eglGetProcAddress, NULL, NULL);
    bytehook_hook_all(NULL, "eglQueryString", (void*)eglQueryString, NULL, NULL);
    bytehook_hook_all(NULL, "dlsym", (void*)dlsym_egl_redirect_early, NULL, NULL);
    g_hooks_installed = 1;
}
