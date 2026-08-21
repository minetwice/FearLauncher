#include "fear_render.h"

pfn_eglGetCurrentContext real_eglGetCurrentContext = NULL;
pfn_eglMakeCurrent real_eglMakeCurrent = NULL;
pfn_eglCreateContext real_eglCreateContext = NULL;
pfn_eglDestroyContext real_eglDestroyContext = NULL;
pfn_eglGetProcAddress real_eglGetProcAddress = NULL;

static void init_egl_symbols_once(void) {
    if (real_eglGetCurrentContext != NULL) return;

    real_eglGetCurrentContext = (pfn_eglGetCurrentContext) dlsym(RTLD_DEFAULT, "eglGetCurrentContext");
    real_eglMakeCurrent = (pfn_eglMakeCurrent) dlsym(RTLD_DEFAULT, "eglMakeCurrent");
    real_eglCreateContext = (pfn_eglCreateContext) dlsym(RTLD_DEFAULT, "eglCreateContext");
    real_eglDestroyContext = (pfn_eglDestroyContext) dlsym(RTLD_DEFAULT, "eglDestroyContext");
    real_eglGetProcAddress = (pfn_eglGetProcAddress) dlsym(RTLD_DEFAULT, "eglGetProcAddress");

    if (!real_eglGetCurrentContext) {
        LOGE("Failed to resolve real_eglGetCurrentContext via RTLD_DEFAULT");
    }
}

extern "C" {

EGLContext eglGetCurrentContext(void) {
    init_egl_symbols_once();
    if (real_eglGetCurrentContext) {
        return real_eglGetCurrentContext();
    }
    return EGL_NO_CONTEXT;
}

EGLBoolean eglMakeCurrent(EGLDisplay dpy, EGLSurface draw, EGLSurface read, EGLContext ctx) {
    init_egl_symbols_once();
    EGLBoolean res = EGL_FALSE;
    if (real_eglMakeCurrent) {
        res = real_eglMakeCurrent(dpy, draw, read, ctx);
    }
    LOGI("[FearRender][EGL] eglMakeCurrent tid=%ld ctx=%p -> %s", (long)gettid(), (void*)ctx, res ? "TRUE" : "FALSE");
    if (res && ctx != EGL_NO_CONTEXT) {
        fear_init_deferred_if_needed();
    }
    return res;
}

EGLContext eglCreateContext(EGLDisplay dpy, EGLConfig config, EGLContext share_context, const EGLint *attrib_list) {
    init_egl_symbols_once();
    EGLContext ctx = EGL_NO_CONTEXT;
    if (real_eglCreateContext) {
        ctx = real_eglCreateContext(dpy, config, share_context, attrib_list);
    }
    LOGI("[FearRender][EGL] eglCreateContext tid=%ld -> ctx=%p", (long)gettid(), (void*)ctx);
    return ctx;
}

EGLBoolean eglDestroyContext(EGLDisplay dpy, EGLContext ctx) {
    init_egl_symbols_once();
    if (real_eglDestroyContext) {
        return real_eglDestroyContext(dpy, ctx);
    }
    return EGL_FALSE;
}

__eglMustCastToProperFunctionPointerType eglGetProcAddress(const char *procname) {
    init_egl_symbols_once();
    if (real_eglGetProcAddress) {
        return real_eglGetProcAddress(procname);
    }
    return NULL;
}

} // extern "C"
