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
#ifndef EGL_OPENGL_ES_BIT
#define EGL_OPENGL_ES_BIT 0x0001
#endif
#ifndef EGL_PBUFFER_BIT
#define EGL_PBUFFER_BIT 0x0001
#endif
#ifndef EGL_PIXMAP_BIT
#define EGL_PIXMAP_BIT 0x0002
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
    printf("eglGetConfigs: 1 fake config\n"); fflush(stdout);
    return EGL_TRUE;
}

__attribute__((visibility("default")))
EGLBoolean eglChooseConfig(EGLDisplay dpy, const EGLint* attrib_list,
                           EGLConfig* configs, EGLint config_size, EGLint* num_config) {
    if (num_config) *num_config = 1;
    if (configs && config_size > 0) configs[0] = (EGLConfig)&g_fake_config;
    printf("eglChooseConfig: 1 fake config (size=%d)\n", config_size);
    if (attrib_list) {
        for (int i = 0; attrib_list[i] != EGL_NONE && i < 64; i += 2)
            printf("  attrib 0x%x = %d\n", (unsigned)attrib_list[i], (int)attrib_list[i+1]);
    }
    fflush(stdout);
    return EGL_TRUE;
}

__attribute__((visibility("default")))
EGLBoolean eglGetConfigAttrib(EGLDisplay dpy, EGLConfig config, EGLint attribute, EGLint* value) {
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
        case EGL_SAMPLES: *value = 0; break;
        case EGL_SAMPLE_BUFFERS: *value = 0; break;
        case EGL_TRANSPARENT_TYPE: *value = EGL_NONE; break;
        case EGL_NATIVE_RENDERABLE: *value = EGL_TRUE; break;
        case EGL_NATIVE_VISUAL_ID: *value = 0; break;
        case EGL_NATIVE_VISUAL_TYPE: *value = EGL_NONE; break;
        case EGL_CONFIG_ID: *value = 1; break;
        case EGL_LEVEL: *value = 0; break;
        case EGL_MAX_PBUFFER_WIDTH: *value = 4096; break;
        case EGL_MAX_PBUFFER_HEIGHT: *value = 4096; break;
        case EGL_MAX_PBUFFER_PIXELS: *value = 4096 * 4096; break;
        case EGL_BIND_TO_TEXTURE_RGB: *value = EGL_FALSE; break;
        case EGL_BIND_TO_TEXTURE_RGBA: *value = EGL_FALSE; break;
        case EGL_MIN_SWAP_INTERVAL: *value = 0; break;
        case EGL_MAX_SWAP_INTERVAL: *value = 1; break;
        case EGL_LUMINANCE_SIZE: *value = 0; break;
        case EGL_ALPHA_MASK_SIZE: *value = 0; break;
        case EGL_CONFORMANT: *value = EGL_OPENGL_BIT | EGL_OPENGL_ES2_BIT; break;
        default:
            printf("eglGetConfigAttrib: unknown attrib 0x%x -> 0\n", (unsigned)attribute);
            fflush(stdout);
            *value = 0;
            break;
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
    int (*bytehook_init)(int, int) = (int (*)(int, int))dlsym(bh, "bytehook_init");
    void* (*bytehook_hook_all)(const char*, const char*, void*, void*, void*) =
        (void* (*)(const char*, const char*, void*, void*, void*))dlsym(bh, "bytehook_hook_all");
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
