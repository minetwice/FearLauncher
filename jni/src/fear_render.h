#ifndef FEAR_RENDER_H
#define FEAR_RENDER_H

#include <EGL/egl.h>
#include <GLES3/gl32.h>
#include <android/log.h>
#include <dlfcn.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <string>
#include <unordered_map>

#define FEAR_TAG "FearRender"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, FEAR_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, FEAR_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, FEAR_TAG, __VA_ARGS__)

#ifdef __cplusplus
extern "C" {
#endif

// EGL Function Pointers
typedef EGLContext (*pfn_eglGetCurrentContext)(void);
typedef EGLBoolean (*pfn_eglMakeCurrent)(EGLDisplay dpy, EGLSurface draw, EGLSurface read, EGLContext ctx);
typedef EGLContext (*pfn_eglCreateContext)(EGLDisplay dpy, EGLConfig config, EGLContext share_context, const EGLint *attrib_list);
typedef EGLBoolean (*pfn_eglDestroyContext)(EGLDisplay dpy, EGLContext ctx);
typedef __eglMustCastToProperFunctionPointerType (*pfn_eglGetProcAddress)(const char *procname);

extern pfn_eglGetCurrentContext real_eglGetCurrentContext;
extern pfn_eglMakeCurrent real_eglMakeCurrent;
extern pfn_eglCreateContext real_eglCreateContext;
extern pfn_eglDestroyContext real_eglDestroyContext;
extern pfn_eglGetProcAddress real_eglGetProcAddress;

// Deferred init state
extern bool g_fear_initialized;
extern bool g_fake_depth_fbo_ready;
extern bool g_is_mali_gpu;

void fear_init_deferred_if_needed(void);
GLenum fear_remap_internal_format(GLenum internalFormat);

#ifdef __cplusplus
}
#endif

#endif // FEAR_RENDER_H
