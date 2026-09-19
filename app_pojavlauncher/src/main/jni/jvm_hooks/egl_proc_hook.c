// FearLauncher EGL facade for Zink/OSMesa + Krypton GL resolver
// Exports standard EGL symbols so GLFW/LWJGL can use OSMesa as desktop GL.
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <dlfcn.h>
#include <android/native_window.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>

#include "../ctxbridges/bridge_environ.h"
#include "../ctxbridges/osmesa_loader.h"
#include "../ctxbridges/osm_bridge.h"

/* External OSMesa symbols from osmesa_loader */
extern void* OSMesaCreateContext_p;
extern void* OSMesaCreateContextExt_p;
extern void* OSMesaCreateContextAttribs_p;
extern void* OSMesaMakeCurrent_p;
extern void* OSMesaGetProcAddress_p;
extern void* OSMesaDestroyContext_p;
extern int osmesa_is_loaded(void);
extern void* get_mesa_dl_handle(void);

static EGLenum current_api = EGL_OPENGL_ES_API;
static int g_fake_config = 1;
static EGLContext g_current_ctx = NULL;
static void* g_osmesa_ctx = NULL;
static void* ng_handle = NULL;
static void* (*gl4es_GetProcAddress)(const char*) = NULL;
static void* (*real_eglGetProcAddress)(const char*) = NULL;
static int (*real_eglBindAPI)(int) = NULL;

static int is_zink_renderer(void) {
    const char* fear = getenv("FEAR_RENDERER");
    if (!fear) return 0;
    return (strcmp(fear, "fear_render") == 0 || strcmp(fear, "panvk_zink") == 0
         || strcmp(fear, "turnip_zink") == 0 || strcmp(fear, "vulkan_zink") == 0);
}

static void ensure_init(void) {
    static int once = 0;
    if (once) return;
    once = 1;
    if (is_zink_renderer()) {
        printf("ensure_init: Zink/Fear path, skipping ng_gl4es\n");
        fflush(stdout);
        /* Prefer system eglGetProcAddress only as last-resort fallback */
        void* egl = dlopen("libEGL.so", RTLD_NOW | RTLD_NOLOAD);
        if (!egl) egl = dlopen("libEGL.so", RTLD_NOW | RTLD_LOCAL);
        if (egl) {
            real_eglGetProcAddress = (void*(*)(const char*))dlsym(egl, "eglGetProcAddress");
            real_eglBindAPI = (int(*)(int))dlsym(egl, "eglBindAPI");
        }
        return;
    }
    /* Legacy ng_gl4es path */
    const char* nd = getenv("POJAV_NATIVEDIR");
    if (nd && nd[0]) {
        char path[512];
        snprintf(path, sizeof(path), "%s/libng_gl4es.so", nd);
        ng_handle = dlopen(path, RTLD_NOW | RTLD_GLOBAL);
    }
    if (!ng_handle) ng_handle = dlopen("libng_gl4es.so", RTLD_NOW | RTLD_GLOBAL);
    if (ng_handle) {
        gl4es_GetProcAddress = (void*(*)(const char*))dlsym(ng_handle, "gl4es_GetProcAddress");
        if (!gl4es_GetProcAddress)
            gl4es_GetProcAddress = (void*(*)(const char*))dlsym(ng_handle, "glXGetProcAddress");
        printf("ensure_init: ng_gl4es=%p getproc=%p\n", ng_handle, (void*)gl4es_GetProcAddress);
    }
}

__attribute__((visibility("default")))
EGLDisplay eglGetDisplay(EGLNativeDisplayType display_id) {
    ensure_init();
    if (is_zink_renderer()) {
        printf("eglGetDisplay: Zink facade display\n"); fflush(stdout);
        return (EGLDisplay)0x1;
    }
    return (EGLDisplay)0x1;
}

__attribute__((visibility("default")))
EGLBoolean eglInitialize(EGLDisplay dpy, EGLint* major, EGLint* minor) {
    if (major) *major = 1;
    if (minor) *minor = 5;
    if (is_zink_renderer()) {
        printf("eglInitialize: Zink facade OK\n"); fflush(stdout);
    }
    return EGL_TRUE;
}

__attribute__((visibility("default")))
EGLBoolean eglTerminate(EGLDisplay dpy) {
    return EGL_TRUE;
}

__attribute__((visibility("default")))
EGLint eglGetError(void) { return EGL_SUCCESS; }

__attribute__((visibility("default")))
const char* eglQueryString(EGLDisplay dpy, EGLint name) {
    switch (name) {
        case EGL_VENDOR: return "FearLauncher Zink/OSMesa";
        case EGL_VERSION: return "1.5 Fear Zink";
        case EGL_EXTENSIONS: return "EGL_KHR_create_context EGL_KHR_surfaceless_context";
        case EGL_CLIENT_APIS: return "OpenGL OpenGL_ES";
        default: return "";
    }
}

__attribute__((visibility("default")))
EGLBoolean eglBindAPI(EGLenum api) {
    current_api = api;
    printf("eglBindAPI: api=0x%x zink=%d\n", (unsigned)api, is_zink_renderer());
    fflush(stdout);
    if (is_zink_renderer()) {
        printf("eglBindAPI: ALLOW 0x%x for Zink/OSMesa\n", (unsigned)api);
        return EGL_TRUE;
    }
    if (real_eglBindAPI) return (EGLBoolean)real_eglBindAPI((int)api);
    return EGL_TRUE;
}

__attribute__((visibility("default")))
EGLenum eglQueryAPI(void) { return current_api; }

__attribute__((visibility("default")))
EGLBoolean eglGetConfigs(EGLDisplay dpy, EGLConfig* configs, EGLint config_size, EGLint* num_config) {
    if (num_config) *num_config = 1;
    if (configs && config_size > 0) configs[0] = (EGLConfig)&g_fake_config;
    printf("eglGetConfigs: 1 fake config\n"); fflush(stdout);
    return EGL_TRUE;
}

__attribute__((visibility("default")))
EGLBoolean eglChooseConfig(EGLDisplay dpy, const EGLint* attrib_list,
                           EGLConfig* configs, EGLint config_size, EGLint* num_config) {
    if (num_config) *num_config = 1;
    if (configs && config_size > 0) configs[0] = (EGLConfig)&g_fake_config;
    printf("eglChooseConfig: 1 fake config\n"); fflush(stdout);
    return EGL_TRUE;
}

__attribute__((visibility("default")))
EGLBoolean eglGetConfigAttrib(EGLDisplay dpy, EGLConfig config, EGLint attribute, EGLint* value) {
    if (!value) return EGL_FALSE;
    switch (attribute) {
        case EGL_RED_SIZE: case EGL_GREEN_SIZE: case EGL_BLUE_SIZE: case EGL_ALPHA_SIZE: *value = 8; break;
        case EGL_DEPTH_SIZE: *value = 24; break;
        case EGL_STENCIL_SIZE: *value = 8; break;
        case EGL_SURFACE_TYPE: *value = EGL_WINDOW_BIT | EGL_PBUFFER_BIT; break;
        case EGL_RENDERABLE_TYPE: *value = EGL_OPENGL_BIT | EGL_OPENGL_ES2_BIT | EGL_OPENGL_ES_BIT; break;
        case EGL_CONFIG_ID: *value = 1; break;
        case EGL_BUFFER_SIZE: *value = 32; break;
        case EGL_NATIVE_RENDERABLE: *value = EGL_TRUE; break;
        case EGL_CONFORMANT: *value = EGL_OPENGL_BIT | EGL_OPENGL_ES2_BIT; break;
        default: *value = 0; break;
    }
    return EGL_TRUE;
}

__attribute__((visibility("default")))
EGLSurface eglCreateWindowSurface(EGLDisplay dpy, EGLConfig config,
                                  EGLNativeWindowType win, const EGLint* attrib_list) {
    printf("eglCreateWindowSurface: win=%p\n", win);
    if (win && is_zink_renderer()) {
        bridge_environ.pojavWindow = (ANativeWindow*)win;
        bridge_environ.savedWidth = ANativeWindow_getWidth((ANativeWindow*)win);
        bridge_environ.savedHeight = ANativeWindow_getHeight((ANativeWindow*)win);
    }
    return (EGLSurface)(win ? win : (void*)0x2);
}

__attribute__((visibility("default")))
EGLSurface eglCreatePbufferSurface(EGLDisplay dpy, EGLConfig config, const EGLint* attrib_list) {
    return (EGLSurface)0x3;
}

__attribute__((visibility("default")))
EGLBoolean eglDestroySurface(EGLDisplay dpy, EGLSurface surface) { return EGL_TRUE; }

__attribute__((visibility("default")))
EGLContext eglCreateContext(EGLDisplay dpy, EGLConfig config, EGLContext share_context,
                            const EGLint* attrib_list) {
    printf("eglCreateContext: share=%p\n", share_context);
    fflush(stdout);
    if (!is_zink_renderer()) {
        printf("eglCreateContext: non-zink, return dummy\n");
        return (EGLContext)0x10;
    }
    /* Force NULL sharelist — sharing OSMesa contexts is unsupported */
    share_context = NULL;
    void* ctx = NULL;
    if (OSMesaCreateContextAttribs_p && attrib_list) {
        /* OSMesaCreateContextAttribs signature differs; use Ext/simple */
    }
    if (!ctx && OSMesaCreateContextExt_p) {
        typedef void* (*OSMesaCreateContextExt_fn)(unsigned int, int, int, int, void*);
        OSMesaCreateContextExt_fn fn = (OSMesaCreateContextExt_fn)OSMesaCreateContextExt_p;
        printf("eglCreateContext: calling OSMesaCreateContextExt(RGBA,16,0,0,NULL)...\n");
        fflush(stdout);
        ctx = fn(0x1908 /* GL_RGBA */, 16, 0, 0, NULL);
    }
    if (!ctx && OSMesaCreateContext_p) {
        typedef void* (*OSMesaCreateContext_fn)(unsigned int, void*);
        OSMesaCreateContext_fn fn = (OSMesaCreateContext_fn)OSMesaCreateContext_p;
        printf("eglCreateContext: calling OSMesaCreateContext(RGBA, NULL)...\n");
        fflush(stdout);
        ctx = fn(0x1908 /* GL_RGBA */, NULL);
    }
    printf("eglCreateContext: OSMesa ctx=%p\n", ctx);
    fflush(stdout);
    if (!ctx) return EGL_NO_CONTEXT;
    g_osmesa_ctx = ctx;
    g_current_ctx = (EGLContext)ctx;
    return (EGLContext)ctx;
}

__attribute__((visibility("default")))
EGLBoolean eglDestroyContext(EGLDisplay dpy, EGLContext ctx) {
    if (ctx && OSMesaDestroyContext_p) {
        typedef void (*fn_t)(void*);
        ((fn_t)OSMesaDestroyContext_p)((void*)ctx);
    }
    if (ctx == g_current_ctx) g_current_ctx = NULL;
    return EGL_TRUE;
}

__attribute__((visibility("default")))
EGLBoolean eglMakeCurrent(EGLDisplay dpy, EGLSurface draw, EGLSurface read, EGLContext ctx) {
    printf("eglMakeCurrent: ctx=%p draw=%p\n", ctx, draw);
    fflush(stdout);
    if (!ctx) {
        g_current_ctx = NULL;
        return EGL_TRUE;
    }
    if (is_zink_renderer() && OSMesaMakeCurrent_p) {
        int w = bridge_environ.savedWidth > 0 ? bridge_environ.savedWidth : 1;
        int h = bridge_environ.savedHeight > 0 ? bridge_environ.savedHeight : 1;
        /* Allocate a small buffer if needed — OSMesa needs a color buffer */
        static void* buf = NULL;
        static int bw = 0, bh = 0;
        if (!buf || bw != w || bh != h) {
            free(buf);
            buf = malloc((size_t)w * h * 4);
            bw = w; bh = h;
        }
        typedef int (*fn_t)(void*, void*, int, int, int);
        int ok = ((fn_t)OSMesaMakeCurrent_p)((void*)ctx, buf, 0x1401 /* GL_UNSIGNED_BYTE */, w, h);
        printf("eglMakeCurrent: OSMesaMakeCurrent -> %d (%dx%d)\n", ok, w, h);
        fflush(stdout);
        if (!ok) return EGL_FALSE;
    }
    g_current_ctx = ctx;
    return EGL_TRUE;
}

__attribute__((visibility("default")))
EGLBoolean eglSwapBuffers(EGLDisplay dpy, EGLSurface surface) {
    /* OSMesa renders into memory; actual presentation is handled by the launcher bridge */
    return EGL_TRUE;
}

__attribute__((visibility("default")))
EGLBoolean eglSwapInterval(EGLDisplay dpy, EGLint interval) { return EGL_TRUE; }

__attribute__((visibility("default")))
EGLBoolean eglReleaseThread(void) { return EGL_TRUE; }

__attribute__((visibility("default")))
int eglBindAPI_hook(int api) { return (int)eglBindAPI((EGLenum)api); }

__attribute__((visibility("default")))
const char* eglQueryString_hook(void* display, int name) {
    return eglQueryString((EGLDisplay)display, (EGLint)name);
}

/* Forward */
__attribute__((visibility("default")))
void* eglGetProcAddress_hook(const char* procname);

__attribute__((visibility("default")))
void* eglGetProcAddress(const char* procname) {
    return eglGetProcAddress_hook(procname);
}

__attribute__((visibility("default")))
void* eglGetProcAddress_hook(const char* procname) {
    if (!procname) return NULL;
    ensure_init();
    if (strncmp(procname, "egl", 3) == 0) {
        printf("eglGetProcAddress: %s\n", procname);
        fflush(stdout);
    }
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
    if (real_eglGetProcAddress) {
        void* sym = real_eglGetProcAddress(procname);
        if (sym) return sym;
    }
    return dlsym(RTLD_DEFAULT, procname);
}

/* Early install — runs from configureRenderspec, before GLFW binds EGL symbols */
static void* (*g_real_dlsym_early)(void*, const char*) = NULL;
static int g_hooks_installed = 0;

static void* dlsym_egl_redirect_early(void* handle, const char* symbol) {
    if (symbol && is_zink_renderer()) {
        if (strcmp(symbol, "eglChooseConfig") == 0) {
            printf("dlsym_early: eglChooseConfig -> facade\n"); fflush(stdout);
            return (void*)eglChooseConfig;
        }
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
        if (strcmp(symbol, "eglTerminate") == 0) return (void*)eglTerminate;
        if (strcmp(symbol, "eglGetError") == 0) return (void*)eglGetError;
        if (strcmp(symbol, "eglDestroyContext") == 0) return (void*)eglDestroyContext;
        if (strcmp(symbol, "eglDestroySurface") == 0) return (void*)eglDestroySurface;
        if (strcmp(symbol, "eglSwapInterval") == 0) return (void*)eglSwapInterval;
        if (strcmp(symbol, "eglCreatePbufferSurface") == 0) return (void*)eglCreatePbufferSurface;
        if (strcmp(symbol, "eglQueryAPI") == 0) return (void*)eglQueryAPI;
        if (strcmp(symbol, "eglReleaseThread") == 0) return (void*)eglReleaseThread;
    }
    if (g_real_dlsym_early) return g_real_dlsym_early(handle, symbol);
    return NULL;
}

__attribute__((visibility("default")))
void fear_install_zink_egl_hooks(void) {
    printf("fear_install_zink_egl_hooks: using libpojavexec EGL facade\n");
    if (g_hooks_installed) {
        printf("fear_install_zink_egl_hooks: already installed\n");
        return;
    }
    if (!is_zink_renderer()) {
        printf("fear_install_zink_egl_hooks: not zink, skip\n");
        return;
    }
    void* bh = dlopen("libbytehook.so", RTLD_NOW);
    if (!bh) {
        printf("fear_install_zink_egl_hooks: libbytehook.so not found\n");
        return;
    }
    int (*bytehook_init)(int, int) = dlsym(bh, "bytehook_init");
    void* (*bytehook_hook_all)(const char*, const char*, void*, void*, void*) =
        dlsym(bh, "bytehook_hook_all");
    if (!bytehook_hook_all) {
        printf("fear_install_zink_egl_hooks: bytehook_hook_all missing\n");
        return;
    }
    if (bytehook_init) {
        int st = bytehook_init(0, 0);
        printf("fear_install_zink_egl_hooks: bytehook_init -> %d\n", st);
    }
    if (!g_real_dlsym_early) {
        void* libc = dlopen("libc.so", RTLD_NOW | RTLD_NOLOAD);
        if (!libc) libc = dlopen("libc.so.6", RTLD_NOW | RTLD_NOLOAD);
        if (libc) g_real_dlsym_early = (void*(*)(void*, const char*))dlsym(libc, "dlsym");
        if (!g_real_dlsym_early) g_real_dlsym_early = (void*(*)(void*, const char*))dlsym(RTLD_NEXT, "dlsym");
        printf("fear_install_zink_egl_hooks: real_dlsym=%p\n", (void*)g_real_dlsym_early);
    }
    void* h1 = bytehook_hook_all(NULL, "eglChooseConfig", (void*)eglChooseConfig, NULL, NULL);
    void* h2 = bytehook_hook_all(NULL, "eglGetConfigs", (void*)eglGetConfigs, NULL, NULL);
    void* h3 = bytehook_hook_all(NULL, "eglGetConfigAttrib", (void*)eglGetConfigAttrib, NULL, NULL);
    void* h4 = bytehook_hook_all(NULL, "eglGetDisplay", (void*)eglGetDisplay, NULL, NULL);
    void* h5 = bytehook_hook_all(NULL, "eglInitialize", (void*)eglInitialize, NULL, NULL);
    void* h6 = bytehook_hook_all(NULL, "eglCreateContext", (void*)eglCreateContext, NULL, NULL);
    void* h7 = bytehook_hook_all(NULL, "eglMakeCurrent", (void*)eglMakeCurrent, NULL, NULL);
    void* h8 = bytehook_hook_all(NULL, "eglCreateWindowSurface", (void*)eglCreateWindowSurface, NULL, NULL);
    void* h9 = bytehook_hook_all(NULL, "eglBindAPI", (void*)eglBindAPI, NULL, NULL);
    void* h10 = bytehook_hook_all(NULL, "eglGetProcAddress", (void*)eglGetProcAddress, NULL, NULL);
    void* h11 = bytehook_hook_all(NULL, "eglQueryString", (void*)eglQueryString, NULL, NULL);
    void* h12 = bytehook_hook_all(NULL, "dlsym", (void*)dlsym_egl_redirect_early, NULL, NULL);
    printf("fear_install_zink_egl_hooks: Choose=%p GetConfigs=%p GetAttrib=%p GetDisplay=%p Init=%p CreateCtx=%p MakeCurrent=%p CreateWin=%p BindAPI=%p GetProc=%p Query=%p dlsym=%p\n",
           h1, h2, h3, h4, h5, h6, h7, h8, h9, h10, h11, h12);
    fflush(stdout);
    g_hooks_installed = 1;
}
