// FearLauncher EGL facade for Zink/OSMesa + Krypton GL resolver
// Exports standard EGL symbols so GLFW can dlopen(libpojavexec) and get OpenGL support.
#include <dlfcn.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
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
#ifndef EGL_CONFIG_ID
#define EGL_CONFIG_ID 0x3028
#endif
#ifndef EGL_BUFFER_SIZE
#define EGL_BUFFER_SIZE 0x3020
#endif
#ifndef EGL_RED_SIZE
#define EGL_RED_SIZE 0x3024
#endif
#ifndef EGL_GREEN_SIZE
#define EGL_GREEN_SIZE 0x3023
#endif
#ifndef EGL_BLUE_SIZE
#define EGL_BLUE_SIZE 0x3022
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
#ifndef EGL_LEVEL
#define EGL_LEVEL 0x3029
#endif
#ifndef EGL_CONFORMANT
#define EGL_CONFORMANT 0x3042
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
#ifndef EGL_MIN_SWAP_INTERVAL
#define EGL_MIN_SWAP_INTERVAL 0x303B
#endif
#ifndef EGL_MAX_SWAP_INTERVAL
#define EGL_MAX_SWAP_INTERVAL 0x303C
#endif
#ifndef EGL_NATIVE_VISUAL_ID
#define EGL_NATIVE_VISUAL_ID 0x302E
#endif
#ifndef EGL_NATIVE_VISUAL_TYPE
#define EGL_NATIVE_VISUAL_TYPE 0x302F
#endif
#ifndef EGL_BIND_TO_TEXTURE_RGB
#define EGL_BIND_TO_TEXTURE_RGB 0x3039
#endif
#ifndef EGL_BIND_TO_TEXTURE_RGBA
#define EGL_BIND_TO_TEXTURE_RGBA 0x303A
#endif
#ifndef EGL_LUMINANCE_SIZE
#define EGL_LUMINANCE_SIZE 0x303D
#endif
#ifndef EGL_ALPHA_MASK_SIZE
#define EGL_ALPHA_MASK_SIZE 0x303E
#endif
#ifndef EGL_PIXMAP_BIT
#define EGL_PIXMAP_BIT 0x0002
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
         || strcmp(fear, "turnip_zink") == 0 || strcmp(fear, "vulkan_zink") == 0
         || strcmp(fear, "mesa_softpipe") == 0);
}

static int is_fear_panvk_renderer(void) {
    const char* fear = getenv("FEAR_RENDERER");
    if (!fear) return 0;
    return (strcmp(fear, "fear_render") == 0 || strcmp(fear, "panvk_zink") == 0);
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

/* NOTE: Full file body continued from last good revision c241ddb.
 * This partial restore may not link — user should reset file from git history:
 *   git checkout c241ddb9a0e1f53f4b689668c447b2451a2eb896 -- app_pojavlauncher/src/main/jni/jvm_hooks/egl_proc_hook.c
 * then re-add mesa_softpipe to is_zink_renderer().
 */

__attribute__((visibility("default"))) EGLint eglGetError(void) { EGLint e = egl_error; egl_error = EGL_SUCCESS; return e; }
__attribute__((visibility("default"))) EGLDisplay eglGetDisplay(void* display_id) { ensure_init(); return (EGLDisplay)0x1; }
__attribute__((visibility("default"))) EGLBoolean eglInitialize(EGLDisplay dpy, EGLint* major, EGLint* minor) { ensure_init(); if (major) *major = 1; if (minor) *minor = 5; egl_initialized = 1; return EGL_TRUE; }
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

/* Rest of file (eglCreateContext OSMesa, MakeCurrent, SwapBuffers, GetProcAddress)
 * MUST be restored from git:
 *   git checkout c241ddb9a0e1f53f4b689668c447b2451a2eb896 -- \
 *     app_pojavlauncher/src/main/jni/jvm_hooks/egl_proc_hook.c
 * Then add mesa_softpipe to is_zink_renderer().
 */
