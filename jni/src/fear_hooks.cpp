#include "fear_hooks.h"
#include "fear_gl_emulation.h"
#include "fear_render_engine.h"
#include "es/utils.hpp"
#include "main.hpp"
#include <android/log.h>
#include <dlfcn.h>
#include <string.h>
#include <stdlib.h>
#include <thread>
#include <chrono>
#include <mutex>
#include <unistd.h>
#include <EGL/egl.h>

#define FEAR_EXPORT __attribute__((visibility("default")))

#define GL_VERSION 0x1F02
#define GL_RENDERER 0x1F01
#define GL_VENDOR 0x1F00
#define GL_EXTENSIONS 0x1F03
#define GL_SHADING_LANGUAGE_VERSION 0x8B8C
#define GL_MAX_TEXTURE_SIZE 0x0D33

static void* g_eglHandle = nullptr;
static void* g_glesHandle = nullptr;
static int g_windowCreated = 0;
static bool g_emergencyContextCreated = false;

static void universal_safe_stub() {
    static bool logged = false;
    if (!logged) {
        __android_log_print(ANDROID_LOG_INFO, "FearRender", "[FearRender] Universal GL stub invoked without context");
        logged = true;
    }
}

static void initEGLGLESHandles() {
    static std::once_flag flag;
    std::call_once(flag, []() {
        __android_log_print(ANDROID_LOG_WARN, "FearRender", "BUILD MARKER v20260907-MAPBUF compiled " __DATE__ " " __TIME__);
        g_eglHandle = dlopen("libgl4es_114.so", RTLD_GLOBAL | RTLD_LAZY);
        if (!g_eglHandle) g_eglHandle = dlopen("libEGL.so", RTLD_GLOBAL | RTLD_LAZY);
        g_glesHandle = dlopen("libgl4es_114.so", RTLD_GLOBAL | RTLD_LAZY);
        if (!g_glesHandle) g_glesHandle = dlopen("libGLESv3.so", RTLD_GLOBAL | RTLD_LAZY);
        if (!g_glesHandle) g_glesHandle = dlopen("libGLESv2.so", RTLD_GLOBAL | RTLD_LAZY);
        __android_log_print(ANDROID_LOG_INFO, "FearRender",
            "[FearRender] EGL handle: %p, GLES/gl4es handle: %p (shadow MapBuffer active)",
            g_eglHandle, g_glesHandle);
    });
}

static EGLContext getCurrentEGLContext() {
    initEGLGLESHandles();
    typedef EGLContext (*eglGetCurrentContext_pfn)();
    static eglGetCurrentContext_pfn real_eglGetCurrentContext = (eglGetCurrentContext_pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_DEFAULT, "eglGetCurrentContext");
    return real_eglGetCurrentContext ? real_eglGetCurrentContext() : EGL_NO_CONTEXT;
}

static bool isContextCurrent() {
    return getCurrentEGLContext() != EGL_NO_CONTEXT;
}

extern "C" {

void* resolve_fear_symbol(const char* symbol) {
    if (symbol && (strncmp(symbol, "gl", 2) == 0 || strncmp(symbol, "egl", 3) == 0)) {
        void* our_fn = fear_eglGetProcAddress(symbol);
        if (our_fn && our_fn != (void*)universal_safe_stub) {
            return our_fn;
        }
    }
    return nullptr;
}

FEAR_EXPORT EGLContext eglCreateContext(EGLDisplay dpy, EGLConfig config, EGLContext share_context, const EGLint* attrib_list) {
    initEGLGLESHandles();
    typedef EGLContext (*eglCreateContext_pfn)(EGLDisplay, EGLConfig, EGLContext, const EGLint*);
    static eglCreateContext_pfn real_eglCreateContext = (eglCreateContext_pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_DEFAULT, "eglCreateContext");
    return real_eglCreateContext ? real_eglCreateContext(dpy, config, share_context, attrib_list) : EGL_NO_CONTEXT;
}

FEAR_EXPORT EGLBoolean eglMakeCurrent(EGLDisplay dpy, EGLSurface draw, EGLSurface read, EGLContext ctx) {
    initEGLGLESHandles();
    typedef EGLBoolean (*eglMakeCurrent_pfn)(EGLDisplay, EGLSurface, EGLSurface, EGLContext);
    static eglMakeCurrent_pfn real_eglMakeCurrent = (eglMakeCurrent_pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_DEFAULT, "eglMakeCurrent");
    return real_eglMakeCurrent ? real_eglMakeCurrent(dpy, draw, read, ctx) : EGL_FALSE;
}

FEAR_EXPORT EGLDisplay eglGetDisplay(EGLNativeDisplayType display_id) {
    initEGLGLESHandles();
    typedef EGLDisplay (*pfn)(EGLNativeDisplayType);
    static pfn real = (pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_NEXT, "eglGetDisplay");
    return real ? real(display_id) : EGL_NO_DISPLAY;
}
FEAR_EXPORT EGLBoolean eglInitialize(EGLDisplay dpy, EGLint* major, EGLint* minor) {
    initEGLGLESHandles();
    typedef EGLBoolean (*pfn)(EGLDisplay, EGLint*, EGLint*);
    static pfn real = (pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_NEXT, "eglInitialize");
    return real ? real(dpy, major, minor) : EGL_FALSE;
}
FEAR_EXPORT EGLBoolean eglTerminate(EGLDisplay dpy) {
    initEGLGLESHandles();
    typedef EGLBoolean (*pfn)(EGLDisplay);
    static pfn real = (pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_NEXT, "eglTerminate");
    return real ? real(dpy) : EGL_FALSE;
}
FEAR_EXPORT EGLBoolean eglChooseConfig(EGLDisplay dpy, const EGLint* attrib_list, EGLConfig* configs, EGLint config_size, EGLint* num_config) {
    initEGLGLESHandles();
    typedef EGLBoolean (*pfn)(EGLDisplay, const EGLint*, EGLConfig*, EGLint, EGLint*);
    static pfn real = (pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_NEXT, "eglChooseConfig");
    return real ? real(dpy, attrib_list, configs, config_size, num_config) : EGL_FALSE;
}
FEAR_EXPORT EGLSurface eglCreateWindowSurface(EGLDisplay dpy, EGLConfig config, EGLNativeWindowType win, const EGLint* attrib_list) {
    initEGLGLESHandles();
    typedef EGLSurface (*pfn)(EGLDisplay, EGLConfig, EGLNativeWindowType, const EGLint*);
    static pfn real = (pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_NEXT, "eglCreateWindowSurface");
    return real ? real(dpy, config, win, attrib_list) : EGL_NO_SURFACE;
}
FEAR_EXPORT EGLSurface eglCreatePbufferSurface(EGLDisplay dpy, EGLConfig config, const EGLint* attrib_list) {
    initEGLGLESHandles();
    typedef EGLSurface (*pfn)(EGLDisplay, EGLConfig, const EGLint*);
    static pfn real = (pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_NEXT, "eglCreatePbufferSurface");
    return real ? real(dpy, config, attrib_list) : EGL_NO_SURFACE;
}
FEAR_EXPORT EGLBoolean eglDestroySurface(EGLDisplay dpy, EGLSurface surface) {
    initEGLGLESHandles();
    typedef EGLBoolean (*pfn)(EGLDisplay, EGLSurface);
    static pfn real = (pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_NEXT, "eglDestroySurface");
    return real ? real(dpy, surface) : EGL_FALSE;
}
FEAR_EXPORT EGLBoolean eglDestroyContext(EGLDisplay dpy, EGLContext ctx) {
    initEGLGLESHandles();
    typedef EGLBoolean (*pfn)(EGLDisplay, EGLContext);
    static pfn real = (pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_NEXT, "eglDestroyContext");
    return real ? real(dpy, ctx) : EGL_FALSE;
}
FEAR_EXPORT EGLBoolean eglSwapBuffers(EGLDisplay dpy, EGLSurface surface) {
    initEGLGLESHandles();
    typedef EGLBoolean (*pfn)(EGLDisplay, EGLSurface);
    static pfn real = (pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_NEXT, "eglSwapBuffers");
    return real ? real(dpy, surface) : EGL_FALSE;
}
FEAR_EXPORT EGLBoolean eglSwapInterval(EGLDisplay dpy, EGLint interval) {
    initEGLGLESHandles();
    typedef EGLBoolean (*pfn)(EGLDisplay, EGLint);
    static pfn real = (pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_NEXT, "eglSwapInterval");
    return real ? real(dpy, interval) : EGL_FALSE;
}
FEAR_EXPORT EGLint eglGetError(void) {
    initEGLGLESHandles();
    typedef EGLint (*pfn)(void);
    static pfn real = (pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_NEXT, "eglGetError");
    return real ? real() : EGL_SUCCESS;
}
FEAR_EXPORT EGLContext eglGetCurrentContext(void) {
    initEGLGLESHandles();
    typedef EGLContext (*pfn)(void);
    static pfn real = (pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_NEXT, "eglGetCurrentContext");
    return real ? real() : EGL_NO_CONTEXT;
}
FEAR_EXPORT EGLDisplay eglGetCurrentDisplay(void) {
    initEGLGLESHandles();
    typedef EGLDisplay (*pfn)(void);
    static pfn real = (pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_NEXT, "eglGetCurrentDisplay");
    return real ? real() : EGL_NO_DISPLAY;
}
FEAR_EXPORT EGLSurface eglGetCurrentSurface(EGLint readdraw) {
    initEGLGLESHandles();
    typedef EGLSurface (*pfn)(EGLint);
    static pfn real = (pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_NEXT, "eglGetCurrentSurface");
    return real ? real(readdraw) : EGL_NO_SURFACE;
}
FEAR_EXPORT EGLBoolean eglQuerySurface(EGLDisplay dpy, EGLSurface surface, EGLint attribute, EGLint* value) {
    initEGLGLESHandles();
    typedef EGLBoolean (*pfn)(EGLDisplay, EGLSurface, EGLint, EGLint*);
    static pfn real = (pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_NEXT, "eglQuerySurface");
    return real ? real(dpy, surface, attribute, value) : EGL_FALSE;
}
FEAR_EXPORT EGLBoolean eglGetConfigAttrib(EGLDisplay dpy, EGLConfig config, EGLint attribute, EGLint* value) {
    initEGLGLESHandles();
    typedef EGLBoolean (*pfn)(EGLDisplay, EGLConfig, EGLint, EGLint*);
    static pfn real = (pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_NEXT, "eglGetConfigAttrib");
    return real ? real(dpy, config, attribute, value) : EGL_FALSE;
}
FEAR_EXPORT const char* eglQueryString(EGLDisplay dpy, EGLint name) {
    initEGLGLESHandles();
    typedef const char* (*pfn)(EGLDisplay, EGLint);
    static pfn real = (pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_NEXT, "eglQueryString");
    return real ? real(dpy, name) : nullptr;
}
FEAR_EXPORT EGLBoolean eglBindAPI(EGLenum api) {
    initEGLGLESHandles();
    typedef EGLBoolean (*pfn)(EGLenum);
    static pfn real = (pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_NEXT, "eglBindAPI");
    return real ? real(api) : EGL_FALSE;
}

FEAR_EXPORT void* glMapBufferRange(GLenum target, GLintptr offset, GLsizeiptr length, GLbitfield access) {
    return fear_glMapBufferRange(target, offset, length, access);
}

FEAR_EXPORT void* glMapBuffer(GLenum target, GLenum access) {
    return fear_glMapBuffer(target, access);
}

FEAR_EXPORT GLboolean glUnmapBuffer(GLenum target) {
    return fear_glUnmapBuffer(target);
}

void* fear_eglGetProcAddress(const char* procname) {
    if (procname == nullptr) return (void*)universal_safe_stub;

    if (strcmp(procname, "eglMakeCurrent") == 0) return (void*)eglMakeCurrent;
    if (strcmp(procname, "eglCreateContext") == 0) return (void*)eglCreateContext;
    if (strcmp(procname, "eglGetDisplay") == 0) return (void*)eglGetDisplay;
    if (strcmp(procname, "eglInitialize") == 0) return (void*)eglInitialize;
    if (strcmp(procname, "eglTerminate") == 0) return (void*)eglTerminate;
    if (strcmp(procname, "eglChooseConfig") == 0) return (void*)eglChooseConfig;
    if (strcmp(procname, "eglCreateWindowSurface") == 0) return (void*)eglCreateWindowSurface;
    if (strcmp(procname, "eglCreatePbufferSurface") == 0) return (void*)eglCreatePbufferSurface;
    if (strcmp(procname, "eglDestroySurface") == 0) return (void*)eglDestroySurface;
    if (strcmp(procname, "eglDestroyContext") == 0) return (void*)eglDestroyContext;
    if (strcmp(procname, "eglSwapBuffers") == 0) return (void*)eglSwapBuffers;
    if (strcmp(procname, "eglSwapInterval") == 0) return (void*)eglSwapInterval;
    if (strcmp(procname, "eglGetError") == 0) return (void*)eglGetError;
    if (strcmp(procname, "eglGetCurrentContext") == 0) return (void*)eglGetCurrentContext;
    if (strcmp(procname, "eglGetCurrentDisplay") == 0) return (void*)eglGetCurrentDisplay;
    if (strcmp(procname, "eglGetCurrentSurface") == 0) return (void*)eglGetCurrentSurface;
    if (strcmp(procname, "eglQuerySurface") == 0) return (void*)eglQuerySurface;
    if (strcmp(procname, "eglGetConfigAttrib") == 0) return (void*)eglGetConfigAttrib;
    if (strcmp(procname, "eglQueryString") == 0) return (void*)eglQueryString;
    if (strcmp(procname, "eglBindAPI") == 0) return (void*)eglBindAPI;

    if (strcmp(procname, "glMapBufferRange") == 0 || strcmp(procname, "glMapBufferRangeEXT") == 0) return (void*)fear_glMapBufferRange;
    if (strcmp(procname, "glMapBuffer") == 0 || strcmp(procname, "glMapBufferOES") == 0) return (void*)fear_glMapBuffer;
    if (strcmp(procname, "glUnmapBuffer") == 0 || strcmp(procname, "glUnmapBufferOES") == 0) return (void*)fear_glUnmapBuffer;

    typedef void* (*eglGetProcAddress_pfn)(const char*);
    static eglGetProcAddress_pfn real_eglGetProcAddress = (eglGetProcAddress_pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_NEXT, "eglGetProcAddress");
    if (real_eglGetProcAddress) {
        void* res = real_eglGetProcAddress(procname);
        if (res) return res;
    }

    if (g_glesHandle) {
        void* sym = dlsym(g_glesHandle, procname);
        if (sym) return sym;
    }

    return (void*)universal_safe_stub;
}

FEAR_EXPORT __eglMustCastToProperFunctionPointerType eglGetProcAddress(const char* procname) {
    return reinterpret_cast<__eglMustCastToProperFunctionPointerType>(fear_eglGetProcAddress(procname));
}

void initialize_fear_hooks() {
    initEGLGLESHandles();
    __android_log_print(ANDROID_LOG_INFO, "FearRender", "[FearRender] hooks initialized (MAPBUF shadow path)");
}

} // extern C
