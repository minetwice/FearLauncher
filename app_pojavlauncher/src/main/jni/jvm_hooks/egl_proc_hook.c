// FearLauncher EGL facade for Zink/OSMesa + Krypton GL resolver
// Exports standard EGL symbols so GLFW can dlopen(libpojavexec) and get OpenGL support.
#include <dlfcn.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <android/native_window.h>
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
#ifndef EGL_BAD_CONTEXT
#define EGL_BAD_CONTEXT 0x3006
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
#ifndef EGL_NO_SURFACE
#define EGL_NO_SURFACE ((void*)0)
#endif
#ifndef EGL_NO_DISPLAY
#define EGL_NO_DISPLAY ((void*)0)
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
    /* Never load ng_gl4es for Zink/Fear — it steals EGL and crashes OSMesaCreateContext */
    if (is_zink_renderer()) {
        printf("ensure_init: Zink/Fear path, skipping ng_gl4es\n");
        fflush(stdout);
        return;
    }
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

__attribute__((visibility("default")))
EGLint eglGetError(void) {
    EGLint e = egl_error;
    egl_error = EGL_SUCCESS;
    return e;
}

__attribute__((visibility("default")))
EGLDisplay eglGetDisplay(void* display_id) {
    ensure_init();
    printf("eglGetDisplay: Zink facade display\n");
    return (EGLDisplay)0x1;
}

__attribute__((visibility("default")))
EGLBoolean eglInitialize(EGLDisplay dpy, EGLint* major, EGLint* minor) {
    ensure_init();
    if (major) *major = 1;
    if (minor) *minor = 5;
    egl_initialized = 1;
    printf("eglInitialize: Zink facade OK\n");
    return EGL_TRUE;
}

__attribute__((visibility("default")))
EGLBoolean eglTerminate(EGLDisplay dpy) {
    egl_initialized = 0;
    return EGL_TRUE;
}

__attribute__((visibility("default")))
const char* eglQueryString(EGLDisplay dpy, EGLint name) {
    ensure_init();
    if (name == EGL_CLIENT_APIS) return "OpenGL OpenGL_ES";
    if (name == EGL_VENDOR) return "FearLauncher";
    if (name == EGL_VERSION) return "1.5 FearLauncher-OSMesa";
    if (name == EGL_EXTENSIONS)
        return "EGL_KHR_create_context EGL_KHR_create_context_no_error EGL_KHR_surfaceless_context EGL_KHR_get_all_proc_addresses";
    return "";
}

__attribute__((visibility("default")))
EGLBoolean eglBindAPI(EGLenum api) {
    ensure_init();
    printf("eglBindAPI: api=0x%x zink=%d\n", (unsigned)api, is_zink_renderer());
    if (is_zink_renderer() && (api == EGL_OPENGL_API || api == EGL_OPENGL_ES_API)) {
        current_api = api;
        printf("eglBindAPI: ALLOW 0x%x for Zink/OSMesa\n", (unsigned)api);
        return EGL_TRUE;
    }
    if (real_eglBindAPI) return (EGLBoolean)real_eglBindAPI((int)api);
    current_api = api;
    return EGL_TRUE;
}

__attribute__((visibility("default")))
EGLenum eglQueryAPI(void) { return current_api; }

__attribute__((visibility("default")))
EGLBoolean eglGetConfigs(EGLDisplay dpy, EGLConfig* configs, EGLint config_size, EGLint* num_config) {
    if (num_config) *num_config = 1;
    if (configs && config_size > 0) configs[0] = (EGLConfig)&g_fake_config;
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
        case EGL_SURFACE_TYPE: *value = EGL_WINDOW_BIT; break;
        case EGL_RENDERABLE_TYPE: *value = EGL_OPENGL_BIT | EGL_OPENGL_ES2_BIT; break;
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
EGLContext eglCreateContext(EGLDisplay dpy, EGLConfig config,
                            EGLContext share_context, const EGLint* attrib_list) {
    ensure_init();
    printf("eglCreateContext: zink=%d osmesa=%d share=%p\n",
           is_zink_renderer(), osmesa_is_loaded(), share_context);
    fflush(stdout);
    if (is_zink_renderer()) {
        if (!osmesa_is_loaded()) dlsym_OSMesa();
        if (!osmesa_is_loaded() || !OSMesaCreateContext_p) {
            printf("eglCreateContext: OSMesa not available\n");
            fflush(stdout);
            egl_error = EGL_BAD_ALLOC;
            return EGL_NO_CONTEXT;
        }
        printf("eglCreateContext: calling OSMesaCreateContext(RGBA, NULL)...\n");
        fflush(stdout);
        void* ctx = OSMesaCreateContext_p(0x1908 /* OSMESA_RGBA */, NULL);
        printf("eglCreateContext: OSMesa ctx=%p\n", ctx);
        fflush(stdout);
        if (!ctx) { egl_error = EGL_BAD_ALLOC; return EGL_NO_CONTEXT; }
        return (EGLContext)ctx;
    }
    egl_error = EGL_BAD_ALLOC;
    return EGL_NO_CONTEXT;
}

__attribute__((visibility("default")))
EGLBoolean eglDestroyContext(EGLDisplay dpy, EGLContext ctx) {
    if (is_zink_renderer() && ctx && OSMesaDestroyContext_p)
        OSMesaDestroyContext_p(ctx);
    return EGL_TRUE;
}

__attribute__((visibility("default")))
EGLBoolean eglMakeCurrent(EGLDisplay dpy, EGLSurface draw, EGLSurface read, EGLContext ctx) {
    ensure_init();
    if (!is_zink_renderer()) return EGL_FALSE;
    if (!ctx) {
        if (OSMesaMakeCurrent_p) OSMesaMakeCurrent_p(NULL, NULL, 0, 0, 0);
        return EGL_TRUE;
    }
    if (!osmesa_is_loaded()) dlsym_OSMesa();
    int w = bridge_environ.savedWidth > 0 ? bridge_environ.savedWidth : 1280;
    int h = bridge_environ.savedHeight > 0 ? bridge_environ.savedHeight : 720;
    if (bridge_environ.pojavWindow) {
        int nw = ANativeWindow_getWidth(bridge_environ.pojavWindow);
        int nh = ANativeWindow_getHeight(bridge_environ.pojavWindow);
        if (nw > 0) w = nw;
        if (nh > 0) h = nh;
    }
    static void* tmp_buf = NULL;
    static int tmp_w = 0, tmp_h = 0;
    if (!tmp_buf || tmp_w != w || tmp_h != h) {
        free(tmp_buf);
        tmp_buf = malloc((size_t)w * (size_t)h * 4);
        tmp_w = w; tmp_h = h;
    }
    if (!tmp_buf || !OSMesaMakeCurrent_p) {
        egl_error = EGL_BAD_CONTEXT;
        return EGL_FALSE;
    }
    GLboolean ok = OSMesaMakeCurrent_p(ctx, tmp_buf, 0x1401 /* GL_UNSIGNED_BYTE */, w, h);
    printf("eglMakeCurrent: OSMesa -> %d (%dx%d)\n", (int)ok, w, h);
    return ok ? EGL_TRUE : EGL_FALSE;
}

__attribute__((visibility("default")))
EGLBoolean eglSwapBuffers(EGLDisplay dpy, EGLSurface surface) {
    if (is_zink_renderer() && glFinish_p) glFinish_p();
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

__attribute__((visibility("default")))
void fear_install_zink_egl_hooks(void) {
    printf("fear_install_zink_egl_hooks: using libpojavexec EGL facade\n");
}
