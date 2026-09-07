#include "fear_turbo_buffer_shadow.h"
#include "fear_turbo_gl_translator.h"
#include <EGL/egl.h>
#include <dlfcn.h>
#include <string.h>
#include <android/log.h>

#define LOG_TAG "FearTurbo"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

#define FEAR_EXPORT __attribute__((visibility("default")))

static void* g_gl4es = nullptr;
static void* g_sys_egl = nullptr;

static void ensure_backends() {
    if (!g_gl4es) {
        g_gl4es = dlopen("libgl4es_114.so", RTLD_NOW | RTLD_GLOBAL);
        if (!g_gl4es) g_gl4es = dlopen("libgl4es.so", RTLD_NOW | RTLD_GLOBAL);
        LOGI("FearTurbo: gl4es backend = %p", g_gl4es);
    }
    if (!g_sys_egl) {
        g_sys_egl = dlopen("libEGL.so", RTLD_NOW | RTLD_GLOBAL);
    }
}

static void* backend_sym(const char* name) {
    ensure_backends();
    void* s = nullptr;
    if (g_gl4es) s = dlsym(g_gl4es, name);
    if (!s && g_sys_egl) s = dlsym(g_sys_egl, name);
    if (!s) s = dlsym(RTLD_NEXT, name);
    return s;
}

// ---- Shadow MapBuffer entry points (ALWAYS used) ----
extern "C" {

FEAR_EXPORT void* glMapBufferRange(GLenum target, GLintptr offset, GLsizeiptr length, GLbitfield access) {
    return fear_turbo::translate_glMapBufferRange(target, offset, length, access);
}

FEAR_EXPORT void* glMapBuffer(GLenum target, GLenum access) {
    GLbitfield bits = 0x0002; // WRITE
    if (access == 0x88B8 /* READ_ONLY */) bits = 0x0001;
    else if (access == 0x88BA /* READ_WRITE */) bits = 0x0003;
    // length unknown → shadow layer uses 64K default then unmap uploads
    return fear_turbo::translate_glMapBufferRange(target, 0, 0, bits);
}

FEAR_EXPORT GLboolean glUnmapBuffer(GLenum target) {
    return fear_turbo::translate_glUnmapBuffer(target);
}

FEAR_EXPORT void glFlushMappedBufferRange(GLenum target, GLintptr offset, GLsizeiptr length) {
    fear_turbo::translate_glFlushMappedBufferRange(target, offset, length);
}

FEAR_EXPORT void* glMapNamedBufferRange(GLuint buffer, GLintptr offset, GLsizeiptr length, GLbitfield access) {
    return fear_turbo::translate_glMapNamedBufferRange(buffer, offset, length, access);
}

FEAR_EXPORT GLboolean glUnmapNamedBuffer(GLuint buffer) {
    return fear_turbo::translate_glUnmapNamedBuffer(buffer);
}

// ---- eglGetProcAddress: route MapBuffer to us, everything else to gl4es ----
FEAR_EXPORT void* eglGetProcAddress(const char* name) {
    if (!name) return nullptr;

    if (strcmp(name, "glMapBufferRange") == 0 || strcmp(name, "glMapBufferRangeEXT") == 0)
        return (void*)glMapBufferRange;
    if (strcmp(name, "glMapBuffer") == 0 || strcmp(name, "glMapBufferOES") == 0)
        return (void*)glMapBuffer;
    if (strcmp(name, "glUnmapBuffer") == 0 || strcmp(name, "glUnmapBufferOES") == 0)
        return (void*)glUnmapBuffer;
    if (strcmp(name, "glFlushMappedBufferRange") == 0 || strcmp(name, "glFlushMappedBufferRangeEXT") == 0)
        return (void*)glFlushMappedBufferRange;
    if (strcmp(name, "glMapNamedBufferRange") == 0)
        return (void*)glMapNamedBufferRange;
    if (strcmp(name, "glUnmapNamedBuffer") == 0)
        return (void*)glUnmapNamedBuffer;

    // Prefer gl4es translation for desktop GL
    void* s = backend_sym(name);
    if (s) return s;

    // eglGetProcAddress from backend for extensions
    typedef void* (*PFN_eglGPA)(const char*);
    static PFN_eglGPA real_gpa = nullptr;
    if (!real_gpa) real_gpa = (PFN_eglGPA)backend_sym("eglGetProcAddress");
    if (real_gpa) {
        void* r = real_gpa(name);
        if (r) return r;
    }
    return nullptr;
}

// ---- Minimal EGL forwards so libFearTurbo.so can be the EGL library ----
#define FWD1(ret, name, t1, a1) \
FEAR_EXPORT ret name(t1 a1) { \
    typedef ret (*pfn)(t1); \
    static pfn real = nullptr; \
    if (!real) real = (pfn)backend_sym(#name); \
    return real ? real(a1) : (ret)0; \
}

#define FWD2(ret, name, t1, a1, t2, a2) \
FEAR_EXPORT ret name(t1 a1, t2 a2) { \
    typedef ret (*pfn)(t1, t2); \
    static pfn real = nullptr; \
    if (!real) real = (pfn)backend_sym(#name); \
    return real ? real(a1, a2) : (ret)0; \
}

#define FWD3(ret, name, t1, a1, t2, a2, t3, a3) \
FEAR_EXPORT ret name(t1 a1, t2 a2, t3 a3) { \
    typedef ret (*pfn)(t1, t2, t3); \
    static pfn real = nullptr; \
    if (!real) real = (pfn)backend_sym(#name); \
    return real ? real(a1, a2, a3) : (ret)0; \
}

#define FWD4(ret, name, t1, a1, t2, a2, t3, a3, t4, a4) \
FEAR_EXPORT ret name(t1 a1, t2 a2, t3 a3, t4 a4) { \
    typedef ret (*pfn)(t1, t2, t3, t4); \
    static pfn real = nullptr; \
    if (!real) real = (pfn)backend_sym(#name); \
    return real ? real(a1, a2, a3, a4) : (ret)0; \
}

FEAR_EXPORT EGLDisplay eglGetDisplay(EGLNativeDisplayType d) {
    typedef EGLDisplay (*pfn)(EGLNativeDisplayType);
    static pfn real = nullptr;
    if (!real) real = (pfn)backend_sym("eglGetDisplay");
    return real ? real(d) : EGL_NO_DISPLAY;
}

FEAR_EXPORT EGLBoolean eglInitialize(EGLDisplay d, EGLint* maj, EGLint* min) {
    fear_turbo::buffer_shadow::init();
    typedef EGLBoolean (*pfn)(EGLDisplay, EGLint*, EGLint*);
    static pfn real = nullptr;
    if (!real) real = (pfn)backend_sym("eglInitialize");
    LOGI("FearTurbo: eglInitialize (shadow MapBuffer ACTIVE)");
    return real ? real(d, maj, min) : EGL_FALSE;
}

FEAR_EXPORT EGLBoolean eglTerminate(EGLDisplay d) {
    typedef EGLBoolean (*pfn)(EGLDisplay);
    static pfn real = nullptr;
    if (!real) real = (pfn)backend_sym("eglTerminate");
    return real ? real(d) : EGL_FALSE;
}

FEAR_EXPORT EGLBoolean eglChooseConfig(EGLDisplay d, const EGLint* a, EGLConfig* c, EGLint n, EGLint* out) {
    typedef EGLBoolean (*pfn)(EGLDisplay, const EGLint*, EGLConfig*, EGLint, EGLint*);
    static pfn real = nullptr;
    if (!real) real = (pfn)backend_sym("eglChooseConfig");
    return real ? real(d, a, c, n, out) : EGL_FALSE;
}

FEAR_EXPORT EGLSurface eglCreateWindowSurface(EGLDisplay d, EGLConfig c, EGLNativeWindowType w, const EGLint* a) {
    typedef EGLSurface (*pfn)(EGLDisplay, EGLConfig, EGLNativeWindowType, const EGLint*);
    static pfn real = nullptr;
    if (!real) real = (pfn)backend_sym("eglCreateWindowSurface");
    return real ? real(d, c, w, a) : EGL_NO_SURFACE;
}

FEAR_EXPORT EGLSurface eglCreatePbufferSurface(EGLDisplay d, EGLConfig c, const EGLint* a) {
    typedef EGLSurface (*pfn)(EGLDisplay, EGLConfig, const EGLint*);
    static pfn real = nullptr;
    if (!real) real = (pfn)backend_sym("eglCreatePbufferSurface");
    return real ? real(d, c, a) : EGL_NO_SURFACE;
}

FEAR_EXPORT EGLContext eglCreateContext(EGLDisplay d, EGLConfig c, EGLContext share, const EGLint* a) {
    typedef EGLContext (*pfn)(EGLDisplay, EGLConfig, EGLContext, const EGLint*);
    static pfn real = nullptr;
    if (!real) real = (pfn)backend_sym("eglCreateContext");
    return real ? real(d, c, share, a) : EGL_NO_CONTEXT;
}

FEAR_EXPORT EGLBoolean eglMakeCurrent(EGLDisplay d, EGLSurface draw, EGLSurface read, EGLContext ctx) {
    typedef EGLBoolean (*pfn)(EGLDisplay, EGLSurface, EGLSurface, EGLContext);
    static pfn real = nullptr;
    if (!real) real = (pfn)backend_sym("eglMakeCurrent");
    return real ? real(d, draw, read, ctx) : EGL_FALSE;
}

FEAR_EXPORT EGLBoolean eglSwapBuffers(EGLDisplay d, EGLSurface s) {
    typedef EGLBoolean (*pfn)(EGLDisplay, EGLSurface);
    static pfn real = nullptr;
    if (!real) real = (pfn)backend_sym("eglSwapBuffers");
    return real ? real(d, s) : EGL_FALSE;
}

FEAR_EXPORT EGLBoolean eglDestroySurface(EGLDisplay d, EGLSurface s) {
    typedef EGLBoolean (*pfn)(EGLDisplay, EGLSurface);
    static pfn real = nullptr;
    if (!real) real = (pfn)backend_sym("eglDestroySurface");
    return real ? real(d, s) : EGL_FALSE;
}

FEAR_EXPORT EGLBoolean eglDestroyContext(EGLDisplay d, EGLContext c) {
    typedef EGLBoolean (*pfn)(EGLDisplay, EGLContext);
    static pfn real = nullptr;
    if (!real) real = (pfn)backend_sym("eglDestroyContext");
    return real ? real(d, c) : EGL_FALSE;
}

FEAR_EXPORT EGLBoolean eglSwapInterval(EGLDisplay d, EGLint i) {
    typedef EGLBoolean (*pfn)(EGLDisplay, EGLint);
    static pfn real = nullptr;
    if (!real) real = (pfn)backend_sym("eglSwapInterval");
    return real ? real(d, i) : EGL_FALSE;
}

FEAR_EXPORT EGLint eglGetError(void) {
    typedef EGLint (*pfn)(void);
    static pfn real = nullptr;
    if (!real) real = (pfn)backend_sym("eglGetError");
    return real ? real() : EGL_SUCCESS;
}

FEAR_EXPORT EGLContext eglGetCurrentContext(void) {
    typedef EGLContext (*pfn)(void);
    static pfn real = nullptr;
    if (!real) real = (pfn)backend_sym("eglGetCurrentContext");
    return real ? real() : EGL_NO_CONTEXT;
}

FEAR_EXPORT EGLDisplay eglGetCurrentDisplay(void) {
    typedef EGLDisplay (*pfn)(void);
    static pfn real = nullptr;
    if (!real) real = (pfn)backend_sym("eglGetCurrentDisplay");
    return real ? real() : EGL_NO_DISPLAY;
}

FEAR_EXPORT EGLSurface eglGetCurrentSurface(EGLint which) {
    typedef EGLSurface (*pfn)(EGLint);
    static pfn real = nullptr;
    if (!real) real = (pfn)backend_sym("eglGetCurrentSurface");
    return real ? real(which) : EGL_NO_SURFACE;
}

FEAR_EXPORT EGLBoolean eglQuerySurface(EGLDisplay d, EGLSurface s, EGLint attr, EGLint* v) {
    typedef EGLBoolean (*pfn)(EGLDisplay, EGLSurface, EGLint, EGLint*);
    static pfn real = nullptr;
    if (!real) real = (pfn)backend_sym("eglQuerySurface");
    return real ? real(d, s, attr, v) : EGL_FALSE;
}

FEAR_EXPORT EGLBoolean eglGetConfigAttrib(EGLDisplay d, EGLConfig c, EGLint attr, EGLint* v) {
    typedef EGLBoolean (*pfn)(EGLDisplay, EGLConfig, EGLint, EGLint*);
    static pfn real = nullptr;
    if (!real) real = (pfn)backend_sym("eglGetConfigAttrib");
    return real ? real(d, c, attr, v) : EGL_FALSE;
}

FEAR_EXPORT const char* eglQueryString(EGLDisplay d, EGLint name) {
    typedef const char* (*pfn)(EGLDisplay, EGLint);
    static pfn real = nullptr;
    if (!real) real = (pfn)backend_sym("eglQueryString");
    return real ? real(d, name) : nullptr;
}

FEAR_EXPORT EGLBoolean eglBindAPI(EGLenum api) {
    typedef EGLBoolean (*pfn)(EGLenum);
    static pfn real = nullptr;
    if (!real) real = (pfn)backend_sym("eglBindAPI");
    return real ? real(api) : EGL_FALSE;
}

} // extern "C"
