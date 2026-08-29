/*
 * QuasarV2 - Custom OpenGL-to-GLES Translator
 * Core EGL context + GL function passthrough + EGL passthrough
 */

#include <jni.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl32.h>
#include <GLES2/gl2ext.h>
#include <dlfcn.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>

#define TAG "QuasarV2"
#include <log.h>

typedef void (*PFN_glShaderSource)(GLuint, GLsizei, const GLchar* const*, const GLint*);
typedef void (*PFN_glCompileShader)(GLuint);
typedef GLuint (*PFN_glCreateShader)(GLenum);
typedef void (*PFN_glDeleteShader)(GLuint);
typedef void (*PFN_glAttachShader)(GLuint, GLuint);
typedef void (*PFN_glLinkProgram)(GLuint);
typedef void (*PFN_glUseProgram)(GLuint);
typedef GLuint (*PFN_glCreateProgram)(void);
typedef void (*PFN_glDeleteProgram)(GLuint);
typedef void (*PFN_glGetShaderiv)(GLuint, GLenum, GLint*);
typedef void (*PFN_glGetShaderInfoLog)(GLuint, GLsizei, GLsizei*, GLchar*);
typedef void (*PFN_glGetProgramiv)(GLuint, GLenum, GLint*);
typedef void (*PFN_glGetProgramInfoLog)(GLuint, GLsizei, GLsizei*, GLchar*);
typedef void (*PFN_glBindBuffer)(GLenum, GLuint);
typedef void (*PFN_glBufferData)(GLenum, GLsizeiptr, const void*, GLenum);
typedef void (*PFN_glBufferSubData)(GLenum, GLintptr, GLsizeiptr, const void*);
typedef void (*PFN_glGenBuffers)(GLsizei, GLuint*);
typedef void (*PFN_glDeleteBuffers)(GLsizei, const GLuint*);
typedef void (*PFN_glVertexAttribPointer)(GLuint, GLint, GLenum, GLboolean, GLsizei, const void*);
typedef void (*PFN_glEnableVertexAttribArray)(GLuint);
typedef void (*PFN_glDisableVertexAttribArray)(GLuint);
typedef void (*PFN_glDrawArrays)(GLenum, GLint, GLsizei);
typedef void (*PFN_glDrawElements)(GLenum, GLsizei, GLenum, const void*);
typedef void (*PFN_glViewport)(GLint, GLint, GLsizei, GLsizei);
typedef void (*PFN_glClearColor)(GLfloat, GLfloat, GLfloat, GLfloat);
typedef void (*PFN_glClear)(GLbitfield);
typedef void (*PFN_glEnable)(GLenum);
typedef void (*PFN_glDisable)(GLenum);
typedef void (*PFN_glBlendFunc)(GLenum, GLenum);
typedef void (*PFN_glDepthFunc)(GLenum);
typedef void (*PFN_glActiveTexture)(GLenum);
typedef void (*PFN_glBindTexture)(GLenum, GLuint);
typedef void (*PFN_glGenTextures)(GLsizei, GLuint*);
typedef void (*PFN_glDeleteTextures)(GLsizei, const GLuint*);
typedef void (*PFN_glTexImage2D)(GLenum, GLint, GLint, GLsizei, GLsizei, GLint, GLenum, GLenum, const void*);
typedef void (*PFN_glTexStorage2D)(GLenum, GLsizei, GLenum, GLsizei, GLsizei);
typedef void (*PFN_glTexSubImage2D)(GLenum, GLint, GLint, GLint, GLsizei, GLsizei, GLenum, GLenum, const void*);
typedef void (*PFN_glTexParameteri)(GLenum, GLenum, GLint);
typedef void (*PFN_glGenVertexArrays)(GLsizei, GLuint*);
typedef void (*PFN_glDeleteVertexArrays)(GLsizei, const GLuint*);
typedef void (*PFN_glBindVertexArray)(GLuint);
typedef void (*PFN_glGenFramebuffers)(GLsizei, GLuint*);
typedef void (*PFN_glDeleteFramebuffers)(GLsizei, const GLuint*);
typedef void (*PFN_glBindFramebuffer)(GLenum, GLuint);
typedef void (*PFN_glGenRenderbuffers)(GLsizei, GLuint*);
typedef void (*PFN_glBindRenderbuffer)(GLenum, GLuint);
typedef void (*PFN_glFramebufferTexture2D)(GLenum, GLenum, GLenum, GLuint, GLint);
typedef void (*PFN_glDrawBuffers)(GLsizei, const GLenum*);
typedef void (*PFN_glGenerateMipmap)(GLenum);
typedef void (*PFN_glRenderbufferStorage)(GLenum, GLenum, GLsizei, GLsizei);
typedef void (*PFN_glFramebufferRenderbuffer)(GLenum, GLenum, GLenum, GLuint);
typedef GLenum (*PFN_glCheckFramebufferStatus)(GLenum);
typedef const GLubyte* (*PFN_glGetString)(GLenum);
typedef const GLubyte* (*PFN_glGetStringi)(GLenum, GLuint);
typedef void (*PFN_glGetIntegerv)(GLenum, GLint*);
typedef void (*PFN_glFlush)(void);
typedef void (*PFN_glFinish)(void);
typedef __eglMustCastToProperFunctionPointerType (*PFN_eglGetProcAddress)(const char*);

static struct {
    PFN_glShaderSource glShaderSource;
    PFN_glCompileShader glCompileShader;
    PFN_glCreateShader glCreateShader;
    PFN_glDeleteShader glDeleteShader;
    PFN_glAttachShader glAttachShader;
    PFN_glLinkProgram glLinkProgram;
    PFN_glUseProgram glUseProgram;
    PFN_glCreateProgram glCreateProgram;
    PFN_glDeleteProgram glDeleteProgram;
    PFN_glGetShaderiv glGetShaderiv;
    PFN_glGetShaderInfoLog glGetShaderInfoLog;
    PFN_glGetProgramiv glGetProgramiv;
    PFN_glGetProgramInfoLog glGetProgramInfoLog;
    PFN_glBindBuffer glBindBuffer;
    PFN_glBufferData glBufferData;
    PFN_glBufferSubData glBufferSubData;
    PFN_glGenBuffers glGenBuffers;
    PFN_glDeleteBuffers glDeleteBuffers;
    PFN_glVertexAttribPointer glVertexAttribPointer;
    PFN_glEnableVertexAttribArray glEnableVertexAttribArray;
    PFN_glDisableVertexAttribArray glDisableVertexAttribArray;
    PFN_glDrawArrays glDrawArrays;
    PFN_glDrawElements glDrawElements;
    PFN_glViewport glViewport;
    PFN_glClearColor glClearColor;
    PFN_glClear glClear;
    PFN_glEnable glEnable;
    PFN_glDisable glDisable;
    PFN_glBlendFunc glBlendFunc;
    PFN_glDepthFunc glDepthFunc;
    PFN_glActiveTexture glActiveTexture;
    PFN_glBindTexture glBindTexture;
    PFN_glGenTextures glGenTextures;
    PFN_glDeleteTextures glDeleteTextures;
    PFN_glTexImage2D glTexImage2D;
    PFN_glTexStorage2D glTexStorage2D;
    PFN_glTexSubImage2D glTexSubImage2D;
    PFN_glTexParameteri glTexParameteri;
    PFN_glGenVertexArrays glGenVertexArrays;
    PFN_glDeleteVertexArrays glDeleteVertexArrays;
    PFN_glBindVertexArray glBindVertexArray;
    PFN_glGenFramebuffers glGenFramebuffers;
    PFN_glDeleteFramebuffers glDeleteFramebuffers;
    PFN_glBindFramebuffer glBindFramebuffer;
    PFN_glGenRenderbuffers glGenRenderbuffers;
    PFN_glBindRenderbuffer glBindRenderbuffer;
    PFN_glFramebufferTexture2D glFramebufferTexture2D;
    PFN_glDrawBuffers glDrawBuffers;
    PFN_glGenerateMipmap glGenerateMipmap;
    PFN_glRenderbufferStorage glRenderbufferStorage;
    PFN_glFramebufferRenderbuffer glFramebufferRenderbuffer;
    PFN_glCheckFramebufferStatus glCheckFramebufferStatus;
    PFN_glGetString glGetString;
    PFN_glGetStringi glGetStringi;
    PFN_glGetIntegerv glGetIntegerv;
    PFN_glFlush glFlush;
    PFN_glFinish glFinish;
    int initialized;
} gles;

PFN_eglGetProcAddress real_eglGetProcAddress = NULL;
static void* g_egl_lib = NULL;

typedef EGLDisplay (*PFN_eglGetDisplay)(EGLNativeDisplayType);
typedef EGLBoolean (*PFN_eglInitialize)(EGLDisplay, EGLint*, EGLint*);
typedef EGLBoolean (*PFN_eglChooseConfig)(EGLDisplay, const EGLint*, EGLConfig*, EGLint, EGLint*);
typedef EGLContext (*PFN_eglCreateContext)(EGLDisplay, EGLConfig, EGLContext, const EGLint*);
typedef EGLSurface (*PFN_eglCreateWindowSurface)(EGLDisplay, EGLConfig, EGLNativeWindowType, const EGLint*);
typedef EGLSurface (*PFN_eglCreatePbufferSurface)(EGLDisplay, EGLConfig, const EGLint*);
typedef EGLBoolean (*PFN_eglMakeCurrent)(EGLDisplay, EGLSurface, EGLSurface, EGLContext);
typedef EGLBoolean (*PFN_eglSwapBuffers)(EGLDisplay, EGLSurface);
typedef EGLBoolean (*PFN_eglDestroyContext)(EGLDisplay, EGLContext);
typedef EGLBoolean (*PFN_eglDestroySurface)(EGLDisplay, EGLSurface);
typedef EGLBoolean (*PFN_eglTerminate)(EGLDisplay);
typedef EGLint (*PFN_eglGetError)(void);
typedef const char* (*PFN_eglQueryString)(EGLDisplay, EGLint);
typedef EGLBoolean (*PFN_eglGetConfigAttrib)(EGLDisplay, EGLConfig, EGLint, EGLint*);
typedef EGLBoolean (*PFN_eglGetConfigs)(EGLDisplay, EGLConfig*, EGLint, EGLint*);
typedef EGLBoolean (*PFN_eglSurfaceAttrib)(EGLDisplay, EGLSurface, EGLint, EGLint);
typedef EGLBoolean (*PFN_eglBindAPI)(EGLenum);
typedef EGLBoolean (*PFN_eglReleaseThread)(void);
typedef EGLBoolean (*PFN_eglWaitClient)(void);
typedef EGLBoolean (*PFN_eglWaitNative)(EGLint);
typedef EGLBoolean (*PFN_eglSwapInterval)(EGLDisplay, EGLint);
typedef EGLBoolean (*PFN_eglQuerySurface)(EGLDisplay, EGLSurface, EGLint, EGLint*);
typedef EGLBoolean (*PFN_eglQueryContext)(EGLDisplay, EGLContext, EGLint, EGLint*);
typedef EGLSurface (*PFN_eglGetCurrentSurface)(EGLint);
typedef EGLContext (*PFN_eglGetCurrentContext)(void);
typedef EGLDisplay (*PFN_eglGetCurrentDisplay)(void);

static PFN_eglGetDisplay real_eglGetDisplay;
static PFN_eglInitialize real_eglInitialize;
static PFN_eglChooseConfig real_eglChooseConfig;
static PFN_eglCreateContext real_eglCreateContext;
static PFN_eglCreateWindowSurface real_eglCreateWindowSurface;
static PFN_eglCreatePbufferSurface real_eglCreatePbufferSurface;
static PFN_eglMakeCurrent real_eglMakeCurrent;
static PFN_eglSwapBuffers real_eglSwapBuffers;
static PFN_eglDestroyContext real_eglDestroyContext;
static PFN_eglDestroySurface real_eglDestroySurface;
static PFN_eglTerminate real_eglTerminate;
static PFN_eglGetError real_eglGetError;
static PFN_eglQueryString real_eglQueryString;
static PFN_eglGetConfigAttrib real_eglGetConfigAttrib;
static PFN_eglGetConfigs real_eglGetConfigs;
static PFN_eglSurfaceAttrib real_eglSurfaceAttrib;
static PFN_eglBindAPI real_eglBindAPI;
static PFN_eglReleaseThread real_eglReleaseThread;
static PFN_eglWaitClient real_eglWaitClient;
static PFN_eglWaitNative real_eglWaitNative;
static PFN_eglSwapInterval real_eglSwapInterval;
static PFN_eglQuerySurface real_eglQuerySurface;
static PFN_eglQueryContext real_eglQueryContext;
static PFN_eglGetCurrentSurface real_eglGetCurrentSurface;
static PFN_eglGetCurrentContext real_eglGetCurrentContext;
static PFN_eglGetCurrentDisplay real_eglGetCurrentDisplay;

static void init_gles_functions(void);

static void load_real_egl() {
    if (g_egl_lib) return;
    g_egl_lib = dlopen("libEGL.so", RTLD_LAZY | RTLD_LOCAL);
    if (!g_egl_lib) { LOGE("QuasarV2: Failed to load libEGL.so: %s", dlerror()); return; }
    real_eglGetDisplay = (PFN_eglGetDisplay) dlsym(g_egl_lib, "eglGetDisplay");
    real_eglInitialize = (PFN_eglInitialize) dlsym(g_egl_lib, "eglInitialize");
    real_eglChooseConfig = (PFN_eglChooseConfig) dlsym(g_egl_lib, "eglChooseConfig");
    real_eglCreateContext = (PFN_eglCreateContext) dlsym(g_egl_lib, "eglCreateContext");
    real_eglCreateWindowSurface = (PFN_eglCreateWindowSurface) dlsym(g_egl_lib, "eglCreateWindowSurface");
    real_eglCreatePbufferSurface = (PFN_eglCreatePbufferSurface) dlsym(g_egl_lib, "eglCreatePbufferSurface");
    real_eglMakeCurrent = (PFN_eglMakeCurrent) dlsym(g_egl_lib, "eglMakeCurrent");
    real_eglSwapBuffers = (PFN_eglSwapBuffers) dlsym(g_egl_lib, "eglSwapBuffers");
    real_eglDestroyContext = (PFN_eglDestroyContext) dlsym(g_egl_lib, "eglDestroyContext");
    real_eglDestroySurface = (PFN_eglDestroySurface) dlsym(g_egl_lib, "eglDestroySurface");
    real_eglTerminate = (PFN_eglTerminate) dlsym(g_egl_lib, "eglTerminate");
    real_eglGetError = (PFN_eglGetError) dlsym(g_egl_lib, "eglGetError");
    real_eglQueryString = (PFN_eglQueryString) dlsym(g_egl_lib, "eglQueryString");
    real_eglGetConfigAttrib = (PFN_eglGetConfigAttrib) dlsym(g_egl_lib, "eglGetConfigAttrib");
    real_eglGetConfigs = (PFN_eglGetConfigs) dlsym(g_egl_lib, "eglGetConfigs");
    real_eglSurfaceAttrib = (PFN_eglSurfaceAttrib) dlsym(g_egl_lib, "eglSurfaceAttrib");
    real_eglBindAPI = (PFN_eglBindAPI) dlsym(g_egl_lib, "eglBindAPI");
    real_eglReleaseThread = (PFN_eglReleaseThread) dlsym(g_egl_lib, "eglReleaseThread");
    real_eglWaitClient = (PFN_eglWaitClient) dlsym(g_egl_lib, "eglWaitClient");
    real_eglWaitNative = (PFN_eglWaitNative) dlsym(g_egl_lib, "eglWaitNative");
    real_eglSwapInterval = (PFN_eglSwapInterval) dlsym(g_egl_lib, "eglSwapInterval");
    real_eglQuerySurface = (PFN_eglQuerySurface) dlsym(g_egl_lib, "eglQuerySurface");
    real_eglQueryContext = (PFN_eglQueryContext) dlsym(g_egl_lib, "eglQueryContext");
    real_eglGetCurrentSurface = (PFN_eglGetCurrentSurface) dlsym(g_egl_lib, "eglGetCurrentSurface");
    real_eglGetCurrentContext = (PFN_eglGetCurrentContext) dlsym(g_egl_lib, "eglGetCurrentContext");
    real_eglGetCurrentDisplay = (PFN_eglGetCurrentDisplay) dlsym(g_egl_lib, "eglGetCurrentDisplay");
    real_eglGetProcAddress = (PFN_eglGetProcAddress) dlsym(g_egl_lib, "eglGetProcAddress");
    LOGI("QuasarV2: Real EGL functions loaded from libEGL.so");
    /* Try to init GLES right away - in case glGetIntegerv is called before eglMakeCurrent */
    init_gles_functions();
}

static void ensure_init() {
    if (!g_egl_lib) load_real_egl();
    if (!gles.initialized) init_gles_functions();
}

/* Forward declarations for our own functions used by eglGetProcAddress */
const GLubyte* glGetString(GLenum name);
const GLubyte* glGetStringi(GLenum name, GLuint index);
void glGetIntegerv(GLenum pname, GLint* params);
void glShaderSource(GLuint shader, GLsizei count, const GLchar* const* string, const GLint* length);

void glCreateTextures(GLenum target, GLsizei n, GLuint* textures);
void glBindTextureUnit(GLuint unit, GLuint texture);
void glTextureStorage1D(GLuint texture, GLsizei levels, GLenum internalformat, GLsizei width);
void glTextureStorage2D(GLuint texture, GLsizei levels, GLenum internalformat, GLsizei width, GLsizei height);
void glTextureStorage3D(GLuint texture, GLsizei levels, GLenum internalformat, GLsizei width, GLsizei height, GLsizei depth);
void glTextureSubImage1D(GLuint texture, GLint level, GLint xoffset, GLsizei width, GLenum format, GLenum type, const void* pixels);
void glTextureSubImage2D(GLuint texture, GLint level, GLint xoffset, GLint yoffset, GLsizei width, GLsizei height, GLenum format, GLenum type, const void* pixels);
void glTextureParameteri(GLuint texture, GLenum pname, GLint param);
void glGenerateTextureMipmap(GLuint texture);
void glCreateBuffers(GLsizei n, GLuint* buffers);
void glNamedBufferData(GLuint buffer, GLsizeiptr size, const void* data, GLenum usage);
void glNamedBufferSubData(GLuint buffer, GLintptr offset, GLsizeiptr size, const void* data);
void glCreateFramebuffers(GLsizei n, GLuint* framebuffers);
void glNamedFramebufferTexture(GLuint framebuffer, GLenum attachment, GLuint texture, GLint level);
void glNamedFramebufferRenderbuffer(GLuint framebuffer, GLenum attachment, GLenum renderbuffertarget, GLuint renderbuffer);
void glNamedFramebufferDrawBuffers(GLuint framebuffer, GLsizei n, const GLenum* bufs);
GLenum glCheckNamedFramebufferStatus(GLuint framebuffer, GLenum target);
void glCreateVertexArrays(GLsizei n, GLuint* arrays);
void glCreateRenderbuffers(GLsizei n, GLuint* renderbuffers);
void glNamedRenderbufferStorage(GLuint renderbuffer, GLenum internalformat, GLsizei width, GLsizei height);

EGLDisplay eglGetDisplay(EGLNativeDisplayType display_id) { if (!real_eglGetDisplay) load_real_egl(); if (real_eglGetDisplay) return real_eglGetDisplay(display_id); return EGL_NO_DISPLAY; }
EGLBoolean eglInitialize(EGLDisplay dpy, EGLint* major, EGLint* minor) { if (!real_eglInitialize) load_real_egl(); if (real_eglInitialize) return real_eglInitialize(dpy, major, minor); return EGL_FALSE; }
EGLBoolean eglChooseConfig(EGLDisplay dpy, const EGLint* attrib_list, EGLConfig* configs, EGLint config_size, EGLint* num_config) { if (!real_eglChooseConfig) load_real_egl(); if (real_eglChooseConfig) return real_eglChooseConfig(dpy, attrib_list, configs, config_size, num_config); return EGL_FALSE; }
EGLContext eglCreateContext(EGLDisplay dpy, EGLConfig config, EGLContext share_context, const EGLint* attrib_list) { if (!real_eglCreateContext) load_real_egl(); if (real_eglCreateContext) return real_eglCreateContext(dpy, config, share_context, attrib_list); return EGL_NO_CONTEXT; }
EGLSurface eglCreateWindowSurface(EGLDisplay dpy, EGLConfig config, EGLNativeWindowType win, const EGLint* attrib_list) { if (!real_eglCreateWindowSurface) load_real_egl(); if (real_eglCreateWindowSurface) return real_eglCreateWindowSurface(dpy, config, win, attrib_list); return EGL_NO_SURFACE; }
EGLSurface eglCreatePbufferSurface(EGLDisplay dpy, EGLConfig config, const EGLint* attrib_list) { if (!real_eglCreatePbufferSurface) load_real_egl(); if (real_eglCreatePbufferSurface) return real_eglCreatePbufferSurface(dpy, config, attrib_list); return EGL_NO_SURFACE; }

EGLBoolean eglMakeCurrent(EGLDisplay dpy, EGLSurface draw, EGLSurface read, EGLContext ctx) {
    if (!real_eglMakeCurrent) load_real_egl();
    EGLBoolean ret = EGL_FALSE;
    if (real_eglMakeCurrent) ret = real_eglMakeCurrent(dpy, draw, read, ctx);
    if (ret) init_gles_functions();
    return ret;
}
EGLBoolean eglSwapBuffers(EGLDisplay dpy, EGLSurface surface) { if (real_eglSwapBuffers) return real_eglSwapBuffers(dpy, surface); return EGL_FALSE; }
EGLBoolean eglDestroyContext(EGLDisplay dpy, EGLContext ctx) { if (real_eglDestroyContext) return real_eglDestroyContext(dpy, ctx); return EGL_FALSE; }
EGLBoolean eglDestroySurface(EGLDisplay dpy, EGLSurface surface) { if (real_eglDestroySurface) return real_eglDestroySurface(dpy, surface); return EGL_FALSE; }
EGLBoolean eglTerminate(EGLDisplay dpy) { if (real_eglTerminate) return real_eglTerminate(dpy); return EGL_FALSE; }
EGLint eglGetError(void) { if (real_eglGetError) return real_eglGetError(); return EGL_NOT_INITIALIZED; }
const char* eglQueryString(EGLDisplay dpy, EGLint name) { if (real_eglQueryString) return real_eglQueryString(dpy, name); return NULL; }
EGLBoolean eglGetConfigAttrib(EGLDisplay dpy, EGLConfig config, EGLint attribute, EGLint* value) { if (real_eglGetConfigAttrib) return real_eglGetConfigAttrib(dpy, config, attribute, value); return EGL_FALSE; }
EGLBoolean eglGetConfigs(EGLDisplay dpy, EGLConfig* configs, EGLint config_size, EGLint* num_config) { if (real_eglGetConfigs) return real_eglGetConfigs(dpy, configs, config_size, num_config); return EGL_FALSE; }
EGLBoolean eglSurfaceAttrib(EGLDisplay dpy, EGLSurface surface, EGLint attribute, EGLint value) { if (real_eglSurfaceAttrib) return real_eglSurfaceAttrib(dpy, surface, attribute, value); return EGL_FALSE; }
EGLBoolean eglBindAPI(EGLenum api) { if (real_eglBindAPI) return real_eglBindAPI(api); return EGL_FALSE; }
EGLBoolean eglReleaseThread(void) { if (real_eglReleaseThread) return real_eglReleaseThread(); return EGL_FALSE; }
EGLBoolean eglWaitClient(void) { if (real_eglWaitClient) return real_eglWaitClient(); return EGL_FALSE; }
EGLBoolean eglWaitNative(EGLint engine) { if (real_eglWaitNative) return real_eglWaitNative(engine); return EGL_FALSE; }
EGLBoolean eglSwapInterval(EGLDisplay dpy, EGLint interval) { if (real_eglSwapInterval) return real_eglSwapInterval(dpy, interval); return EGL_FALSE; }
EGLBoolean eglQuerySurface(EGLDisplay dpy, EGLSurface surface, EGLint attribute, EGLint* value) { if (real_eglQuerySurface) return real_eglQuerySurface(dpy, surface, attribute, value); return EGL_FALSE; }
EGLBoolean eglQueryContext(EGLDisplay dpy, EGLContext ctx, EGLint attribute, EGLint* value) { if (real_eglQueryContext) return real_eglQueryContext(dpy, ctx, attribute, value); return EGL_FALSE; }
EGLSurface eglGetCurrentSurface(EGLint readdraw) { if (real_eglGetCurrentSurface) return real_eglGetCurrentSurface(readdraw); return EGL_NO_SURFACE; }
EGLContext eglGetCurrentContext(void) { if (real_eglGetCurrentContext) return real_eglGetCurrentContext(); return EGL_NO_CONTEXT; }
EGLDisplay eglGetCurrentDisplay(void) { if (real_eglGetCurrentDisplay) return real_eglGetCurrentDisplay(); return EGL_NO_DISPLAY; }

__eglMustCastToProperFunctionPointerType eglGetProcAddress(const char* procname) {
    if (!real_eglGetProcAddress) load_real_egl();
    if (procname == NULL) return NULL;
    if (strcmp(procname, "glShaderSource") == 0) return (__eglMustCastToProperFunctionPointerType) glShaderSource;
    if (strcmp(procname, "glGetString") == 0) return (__eglMustCastToProperFunctionPointerType) glGetString;
    if (strcmp(procname, "glGetStringi") == 0) return (__eglMustCastToProperFunctionPointerType) glGetStringi;
    if (strcmp(procname, "glGetIntegerv") == 0) return (__eglMustCastToProperFunctionPointerType) glGetIntegerv;
    if (strcmp(procname, "glCreateTextures") == 0) return (__eglMustCastToProperFunctionPointerType) glCreateTextures;
    if (strcmp(procname, "glBindTextureUnit") == 0) return (__eglMustCastToProperFunctionPointerType) glBindTextureUnit;
    if (strcmp(procname, "glTextureStorage1D") == 0) return (__eglMustCastToProperFunctionPointerType) glTextureStorage1D;
    if (strcmp(procname, "glTextureStorage2D") == 0) return (__eglMustCastToProperFunctionPointerType) glTextureStorage2D;
    if (strcmp(procname, "glTextureStorage3D") == 0) return (__eglMustCastToProperFunctionPointerType) glTextureStorage3D;
    if (strcmp(procname, "glTextureSubImage1D") == 0) return (__eglMustCastToProperFunctionPointerType) glTextureSubImage1D;
    if (strcmp(procname, "glTextureSubImage2D") == 0) return (__eglMustCastToProperFunctionPointerType) glTextureSubImage2D;
    if (strcmp(procname, "glTextureParameteri") == 0) return (__eglMustCastToProperFunctionPointerType) glTextureParameteri;
    if (strcmp(procname, "glGenerateTextureMipmap") == 0) return (__eglMustCastToProperFunctionPointerType) glGenerateTextureMipmap;
    if (strcmp(procname, "glCreateBuffers") == 0) return (__eglMustCastToProperFunctionPointerType) glCreateBuffers;
    if (strcmp(procname, "glNamedBufferData") == 0) return (__eglMustCastToProperFunctionPointerType) glNamedBufferData;
    if (strcmp(procname, "glNamedBufferSubData") == 0) return (__eglMustCastToProperFunctionPointerType) glNamedBufferSubData;
    if (strcmp(procname, "glCreateFramebuffers") == 0) return (__eglMustCastToProperFunctionPointerType) glCreateFramebuffers;
    if (strcmp(procname, "glNamedFramebufferTexture") == 0) return (__eglMustCastToProperFunctionPointerType) glNamedFramebufferTexture;
    if (strcmp(procname, "glNamedFramebufferRenderbuffer") == 0) return (__eglMustCastToProperFunctionPointerType) glNamedFramebufferRenderbuffer;
    if (strcmp(procname, "glNamedFramebufferDrawBuffers") == 0) return (__eglMustCastToProperFunctionPointerType) glNamedFramebufferDrawBuffers;
    if (strcmp(procname, "glCheckNamedFramebufferStatus") == 0) return (__eglMustCastToProperFunctionPointerType) glCheckNamedFramebufferStatus;
    if (strcmp(procname, "glCreateVertexArrays") == 0) return (__eglMustCastToProperFunctionPointerType) glCreateVertexArrays;
    if (strcmp(procname, "glCreateRenderbuffers") == 0) return (__eglMustCastToProperFunctionPointerType) glCreateRenderbuffers;
    if (strcmp(procname, "glNamedRenderbufferStorage") == 0) return (__eglMustCastToProperFunctionPointerType) glNamedRenderbufferStorage;
    if (real_eglGetProcAddress) return (__eglMustCastToProperFunctionPointerType) real_eglGetProcAddress(procname);
    return NULL;
}

static void* resolve_gles(const char* name) {
    if (!real_eglGetProcAddress) load_real_egl();
    if (real_eglGetProcAddress) { void* ptr = (void*) real_eglGetProcAddress(name); if (ptr) return ptr; }
    static void* gles_handle = NULL;
    if (!gles_handle) gles_handle = dlopen("libGLESv3.so", RTLD_LAZY | RTLD_GLOBAL);
    if (gles_handle) return dlsym(gles_handle, name);
    return NULL;
}
#define RESOLVE(name) gles.name = (PFN_##name) resolve_gles(#name)

static void init_gles_functions() {
    if (gles.initialized) return;
    RESOLVE(glShaderSource); RESOLVE(glCompileShader); RESOLVE(glCreateShader); RESOLVE(glDeleteShader);
    RESOLVE(glAttachShader); RESOLVE(glLinkProgram); RESOLVE(glUseProgram); RESOLVE(glCreateProgram);
    RESOLVE(glDeleteProgram); RESOLVE(glGetShaderiv); RESOLVE(glGetShaderInfoLog); RESOLVE(glGetProgramiv);
    RESOLVE(glGetProgramInfoLog);
    RESOLVE(glBindBuffer); RESOLVE(glBufferData); RESOLVE(glBufferSubData); RESOLVE(glGenBuffers);
    RESOLVE(glDeleteBuffers); RESOLVE(glVertexAttribPointer); RESOLVE(glEnableVertexAttribArray);
    RESOLVE(glDisableVertexAttribArray); RESOLVE(glDrawArrays); RESOLVE(glDrawElements);
    RESOLVE(glViewport); RESOLVE(glClearColor); RESOLVE(glClear); RESOLVE(glEnable); RESOLVE(glDisable);
    RESOLVE(glBlendFunc); RESOLVE(glDepthFunc); RESOLVE(glActiveTexture); RESOLVE(glBindTexture);
    RESOLVE(glGenTextures); RESOLVE(glDeleteTextures); RESOLVE(glTexImage2D); RESOLVE(glTexStorage2D); RESOLVE(glTexSubImage2D);
    RESOLVE(glTexParameteri); RESOLVE(glGenVertexArrays); RESOLVE(glDeleteVertexArrays);
    RESOLVE(glBindVertexArray); RESOLVE(glGenFramebuffers); RESOLVE(glDeleteFramebuffers);
    RESOLVE(glBindFramebuffer); RESOLVE(glGenRenderbuffers); RESOLVE(glBindRenderbuffer);
    RESOLVE(glFramebufferTexture2D); RESOLVE(glDrawBuffers);
    RESOLVE(glGenerateMipmap); RESOLVE(glRenderbufferStorage); RESOLVE(glFramebufferRenderbuffer);
    RESOLVE(glCheckFramebufferStatus);
    RESOLVE(glGetString); RESOLVE(glGetStringi); RESOLVE(glGetIntegerv); RESOLVE(glFlush); RESOLVE(glFinish);
    gles.initialized = 1;
    LOGI("QuasarV2: GLES functions resolved");
}

extern void quasar_glShaderSource(GLuint shader, GLsizei count, const GLchar* const* string, const GLint* length);

JNIEXPORT jint JNICALL Java_net_kdt_pojavlaunch_quasar_QuasarV2_initEGL(JNIEnv* env, jclass cls, jint width, jint height) {
    LOGI("QuasarV2: initEGL stub called - real EGL is handled by passthrough");
    return 0;
}
JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_quasar_QuasarV2_shutdownEGL(JNIEnv* env, jclass cls) {
    LOGI("QuasarV2: shutdownEGL called");
}

/* ============================================================
 * glGetString - Fake desktop GL version
 * ============================================================ */

static const char* FAKE_EXTENSIONS_LIST[] = {
    "GL_ARB_direct_state_access", "GL_ARB_buffer_storage", "GL_ARB_shader_image_load_store",
    "GL_NV_conditional_render", "GL_EXT_gpu_shader4", "GL_EXT_texture_buffer",
    "GL_EXT_texture_cube_map_array", "GL_OES_EGL_image_external_essl3",
    "GL_ARB_shader_texture_lod", "GL_ARB_shader_objects", "GL_ARB_vertex_shader",
    "GL_ARB_fragment_shader", "GL_EXT_blend_equation_separate", "GL_EXT_geometry_shader4",
    "GL_EXT_gpu_program_parameters", "GL_ARB_instanced_arrays", "GL_ARB_draw_instanced",
    "GL_ARB_framebuffer_object", "GL_ARB_texture_float", "GL_ARB_color_buffer_float",
    "GL_ARB_half_float_vertex", "GL_ARB_half_float_pixel", "GL_ARB_depth_buffer_float",
    "GL_ARB_draw_buffers", "GL_ARB_shader_storage_buffer_object", "GL_ARB_uniform_buffer_object"
};
static const int FAKE_EXTENSIONS_COUNT = sizeof(FAKE_EXTENSIONS_LIST)/sizeof(FAKE_EXTENSIONS_LIST[0]);


void glCreateTextures(GLenum target, GLsizei n, GLuint* textures) {
    ensure_init();
    if (gles.glGenTextures) gles.glGenTextures(n, textures);
}

void glBindTextureUnit(GLuint unit, GLuint texture) {
    ensure_init();
    if (gles.glActiveTexture) gles.glActiveTexture(GL_TEXTURE0 + unit);
    if (gles.glBindTexture) gles.glBindTexture(GL_TEXTURE_2D, texture);
}

void glTextureStorage1D(GLuint texture, GLsizei levels, GLenum internalformat, GLsizei width) {
    ensure_init();
    if (gles.glBindTexture) gles.glBindTexture(GL_TEXTURE_2D, texture);
    if (gles.glTexImage2D) gles.glTexImage2D(GL_TEXTURE_2D, 0, internalformat, width, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, NULL);
}

void glTextureStorage2D(GLuint texture, GLsizei levels, GLenum internalformat, GLsizei width, GLsizei height) {
    ensure_init();
    if (gles.glBindTexture) gles.glBindTexture(GL_TEXTURE_2D, texture);
    if (gles.glTexStorage2D) {
        gles.glTexStorage2D(GL_TEXTURE_2D, levels, internalformat, width, height);
    } else if (gles.glTexImage2D) {
        GLenum format = GL_RGBA;
        GLenum type = GL_UNSIGNED_BYTE;
        if (internalformat == GL_DEPTH_COMPONENT || internalformat == GL_DEPTH_COMPONENT16 || internalformat == GL_DEPTH_COMPONENT24 || internalformat == GL_DEPTH_COMPONENT32F) {
            format = GL_DEPTH_COMPONENT;
            type = GL_UNSIGNED_INT;
        } else if (internalformat == GL_DEPTH24_STENCIL8 || internalformat == GL_DEPTH32F_STENCIL8) {
            format = GL_DEPTH_STENCIL;
            type = GL_UNSIGNED_INT_24_8;
        }
        gles.glTexImage2D(GL_TEXTURE_2D, 0, internalformat, width, height, 0, format, type, NULL);
    }
}

void glTextureStorage3D(GLuint texture, GLsizei levels, GLenum internalformat, GLsizei width, GLsizei height, GLsizei depth) {
    ensure_init();
    if (gles.glBindTexture) gles.glBindTexture(GL_TEXTURE_2D, texture);
    if (gles.glTexImage2D) gles.glTexImage2D(GL_TEXTURE_2D, 0, internalformat, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, NULL);
}

void glTextureSubImage1D(GLuint texture, GLint level, GLint xoffset, GLsizei width, GLenum format, GLenum type, const void* pixels) {
    ensure_init();
    if (gles.glBindTexture) gles.glBindTexture(GL_TEXTURE_2D, texture);
    if (gles.glTexSubImage2D) gles.glTexSubImage2D(GL_TEXTURE_2D, level, xoffset, 0, width, 1, format, type, pixels);
}

void glTextureSubImage2D(GLuint texture, GLint level, GLint xoffset, GLint yoffset, GLsizei width, GLsizei height, GLenum format, GLenum type, const void* pixels) {
    ensure_init();
    if (gles.glBindTexture) gles.glBindTexture(GL_TEXTURE_2D, texture);
    if (gles.glTexSubImage2D) gles.glTexSubImage2D(GL_TEXTURE_2D, level, xoffset, yoffset, width, height, format, type, pixels);
}

void glTextureParameteri(GLuint texture, GLenum pname, GLint param) {
    ensure_init();
    if (gles.glBindTexture) gles.glBindTexture(GL_TEXTURE_2D, texture);
    if (gles.glTexParameteri) gles.glTexParameteri(GL_TEXTURE_2D, pname, param);
}

void glGenerateTextureMipmap(GLuint texture) {
    ensure_init();
    if (gles.glBindTexture) gles.glBindTexture(GL_TEXTURE_2D, texture);
    if (gles.glGenerateMipmap) gles.glGenerateMipmap(GL_TEXTURE_2D);
}

void glCreateBuffers(GLsizei n, GLuint* buffers) {
    ensure_init();
    if (gles.glGenBuffers) gles.glGenBuffers(n, buffers);
}

void glNamedBufferData(GLuint buffer, GLsizeiptr size, const void* data, GLenum usage) {
    ensure_init();
    if (gles.glBindBuffer) gles.glBindBuffer(GL_ARRAY_BUFFER, buffer);
    if (gles.glBufferData) gles.glBufferData(GL_ARRAY_BUFFER, size, data, usage);
}

void glNamedBufferSubData(GLuint buffer, GLintptr offset, GLsizeiptr size, const void* data) {
    ensure_init();
    if (gles.glBindBuffer) gles.glBindBuffer(GL_ARRAY_BUFFER, buffer);
    if (gles.glBufferSubData) gles.glBufferSubData(GL_ARRAY_BUFFER, offset, size, data);
}

void glCreateFramebuffers(GLsizei n, GLuint* framebuffers) {
    ensure_init();
    if (gles.glGenFramebuffers) gles.glGenFramebuffers(n, framebuffers);
}

void glNamedFramebufferTexture(GLuint framebuffer, GLenum attachment, GLuint texture, GLint level) {
    ensure_init();
    if (gles.glBindFramebuffer) gles.glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
    if (gles.glFramebufferTexture2D) gles.glFramebufferTexture2D(GL_FRAMEBUFFER, attachment, GL_TEXTURE_2D, texture, level);
}

void glNamedFramebufferRenderbuffer(GLuint framebuffer, GLenum attachment, GLenum renderbuffertarget, GLuint renderbuffer) {
    ensure_init();
    if (gles.glBindFramebuffer) gles.glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
    if (gles.glFramebufferRenderbuffer) gles.glFramebufferRenderbuffer(GL_FRAMEBUFFER, attachment, renderbuffertarget, renderbuffer);
}

void glNamedFramebufferDrawBuffers(GLuint framebuffer, GLsizei n, const GLenum* bufs) {
    ensure_init();
    if (gles.glBindFramebuffer) gles.glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
    if (gles.glDrawBuffers) gles.glDrawBuffers(n, bufs);
}

GLenum glCheckNamedFramebufferStatus(GLuint framebuffer, GLenum target) {
    ensure_init();
    if (gles.glBindFramebuffer) gles.glBindFramebuffer(target, framebuffer);
    return gles.glCheckFramebufferStatus ? gles.glCheckFramebufferStatus(target) : GL_FRAMEBUFFER_COMPLETE;
}

void glCreateVertexArrays(GLsizei n, GLuint* arrays) {
    ensure_init();
    if (gles.glGenVertexArrays) gles.glGenVertexArrays(n, arrays);
}

void glCreateRenderbuffers(GLsizei n, GLuint* renderbuffers) {
    ensure_init();
    if (gles.glGenRenderbuffers) gles.glGenRenderbuffers(n, renderbuffers);
}

void glNamedRenderbufferStorage(GLuint renderbuffer, GLenum internalformat, GLsizei width, GLsizei height) {
    ensure_init();
    if (gles.glBindRenderbuffer) gles.glBindRenderbuffer(GL_RENDERBUFFER, renderbuffer);
    if (gles.glRenderbufferStorage) gles.glRenderbufferStorage(GL_RENDERBUFFER, internalformat, width, height);
}

const GLubyte* glGetString(GLenum name) {
    switch(name) {
        case GL_VERSION:  return (const GLubyte*)"4.6.0 QuasarV2 1.0";
        case GL_RENDERER: return (const GLubyte*)"QuasarV2 Translator (Mali-G615)";
        case GL_VENDOR:   return (const GLubyte*)"QuasarV2";
        case GL_EXTENSIONS: return (const GLubyte*)"";
        case GL_SHADING_LANGUAGE_VERSION: return (const GLubyte*)"4.60 QuasarV2";
    }
    ensure_init();
    if (gles.glGetString) return gles.glGetString(name);
    return (const GLubyte*)"";
}

const GLubyte* glGetStringi(GLenum name, GLuint index) {
    if (name == GL_EXTENSIONS && index < (GLuint)FAKE_EXTENSIONS_COUNT) return (const GLubyte*)FAKE_EXTENSIONS_LIST[index];
    ensure_init();
    if (gles.glGetStringi) return gles.glGetStringi(name, index);
    return (const GLubyte*)"";
}

void glGetIntegerv(GLenum pname, GLint* params) {
    if (!params) return;

    /* Handle Desktop GL queries first so LWJGL/Blaze3D startup never fails even if EGL context is not active */
    switch (pname) {
        case 0x821B: *params = 4; return;    /* GL_MAJOR_VERSION */
        case 0x821C: *params = 6; return;    /* GL_MINOR_VERSION */
        case 0x821D: *params = 24; return;   /* GL_NUM_EXTENSIONS */
        case 0x821E: *params = 0; return;    /* GL_CONTEXT_FLAGS */
        case 0x9126: *params = 1; return;    /* GL_CONTEXT_PROFILE_MASK */
        case 0x8B4D: *params = 60; return;   /* GL_MAX_VARYING_FLOATS */
        case 0x8824: *params = 8; return;    /* GL_MAX_DRAW_BUFFERS */
        case 0x8B49: *params = 4096; return;  /* GL_MAX_VERTEX_UNIFORM_COMPONENTS */
        case 0x8B4A: *params = 4096; return;  /* GL_MAX_FRAGMENT_UNIFORM_COMPONENTS */
        case 0x851C: *params = 16; return;    /* GL_MAX_TEXTURE_COORDS */
        case 0x807A: *params = 32; return;    /* GL_MAX_TEXTURE_IMAGE_UNITS */
        case 0x8B4B: *params = 32; return;    /* GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS */
        case 0x8842: *params = 32; return;    /* GL_MAX_TEXTURE_UNITS */
        case 0x84E8: *params = 16; return;    /* GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS */
        case 0x8DFB: *params = 16; return;    /* GL_MAX_VERTEX_OUTPUT_COMPONENTS */
        case 0x8DFC: *params = 16; return;    /* GL_MAX_FRAGMENT_INPUT_COMPONENTS */
        case 0x8B4C: *params = 64; return;    /* GL_MAX_VERTEX_ATTRIBS */
        case 0x8DFD: *params = 64; return;    /* GL_MAX_GEOMETRY_OUTPUT_VERTICES */
        case 0x8A32: *params = 256; return;   /* GL_MAX_GEOMETRY_TOTAL_OUTPUT_COMPONENTS */
        case 0x0D33: *params = 16384; return; /* GL_MAX_TEXTURE_SIZE */
    }

    ensure_init();
    EGLContext current_ctx = EGL_NO_CONTEXT;
    if (real_eglGetCurrentContext) current_ctx = real_eglGetCurrentContext();
    if (current_ctx != EGL_NO_CONTEXT && gles.glGetIntegerv) {
        gles.glGetIntegerv(pname, params);
        return;
    }
    *params = 0;
}

/* ============================================================
 * GL function passthrough
 * ============================================================ */

void glShaderSource(GLuint shader, GLsizei count, const GLchar* const* string, const GLint* length) { quasar_glShaderSource(shader, count, string, length); }
void glCompileShader(GLuint s) { ensure_init(); if (gles.glCompileShader) gles.glCompileShader(s); }
GLuint glCreateShader(GLenum t) { ensure_init(); return gles.glCreateShader ? gles.glCreateShader(t) : 0; }
void glDeleteShader(GLuint s) { if (gles.glDeleteShader) gles.glDeleteShader(s); }
void glAttachShader(GLuint p, GLuint s) { if (gles.glAttachShader) gles.glAttachShader(p, s); }
void glLinkProgram(GLuint p) { if (gles.glLinkProgram) gles.glLinkProgram(p); }
void glUseProgram(GLuint p) { if (gles.glUseProgram) gles.glUseProgram(p); }
GLuint glCreateProgram(void) { ensure_init(); return gles.glCreateProgram ? gles.glCreateProgram() : 0; }
void glDeleteProgram(GLuint p) { if (gles.glDeleteProgram) gles.glDeleteProgram(p); }
void glGetShaderiv(GLuint s, GLenum p, GLint* v) { if (gles.glGetShaderiv) gles.glGetShaderiv(s, p, v); else if (v) *v = 0; }
void glGetShaderInfoLog(GLuint s, GLsizei b, GLsizei* l, GLchar* i) { if (gles.glGetShaderInfoLog) gles.glGetShaderInfoLog(s, b, l, i); else if (l) *l = 0; }
void glGetProgramiv(GLuint p, GLenum n, GLint* v) { if (gles.glGetProgramiv) gles.glGetProgramiv(p, n, v); else if (v) *v = 0; }
void glGetProgramInfoLog(GLuint p, GLsizei b, GLsizei* l, GLchar* i) { if (gles.glGetProgramInfoLog) gles.glGetProgramInfoLog(p, b, l, i); else if (l) *l = 0; }
void glBindBuffer(GLenum t, GLuint b) { if (gles.glBindBuffer) gles.glBindBuffer(t, b); }
void glBufferData(GLenum t, GLsizeiptr s, const void* d, GLenum u) { if (gles.glBufferData) gles.glBufferData(t, s, d, u); }
void glBufferSubData(GLenum t, GLintptr o, GLsizeiptr s, const void* d) { if (gles.glBufferSubData) gles.glBufferSubData(t, o, s, d); }
void glGenBuffers(GLsizei n, GLuint* b) { if (gles.glGenBuffers) gles.glGenBuffers(n, b); }
void glDeleteBuffers(GLsizei n, const GLuint* b) { if (gles.glDeleteBuffers) gles.glDeleteBuffers(n, b); }
void glVertexAttribPointer(GLuint i, GLint s, GLenum t, GLboolean n, GLsizei st, const void* p) { if (gles.glVertexAttribPointer) gles.glVertexAttribPointer(i, s, t, n, st, p); }
void glEnableVertexAttribArray(GLuint i) { if (gles.glEnableVertexAttribArray) gles.glEnableVertexAttribArray(i); }
void glDisableVertexAttribArray(GLuint i) { if (gles.glDisableVertexAttribArray) gles.glDisableVertexAttribArray(i); }
void glDrawArrays(GLenum m, GLint f, GLsizei c) { if (gles.glDrawArrays) gles.glDrawArrays(m, f, c); }
void glDrawElements(GLenum m, GLsizei c, GLenum t, const void* i) { if (gles.glDrawElements) gles.glDrawElements(m, c, t, i); }
void glViewport(GLint x, GLint y, GLsizei w, GLsizei h) { if (gles.glViewport) gles.glViewport(x, y, w, h); }
void glClearColor(GLfloat r, GLfloat g, GLfloat b, GLfloat a) { if (gles.glClearColor) gles.glClearColor(r, g, b, a); }
void glClear(GLbitfield m) { if (gles.glClear) gles.glClear(m); }
void glEnable(GLenum c) { if (gles.glEnable) gles.glEnable(c); }
void glDisable(GLenum c) { if (gles.glDisable) gles.glDisable(c); }
void glBlendFunc(GLenum s, GLenum d) { if (gles.glBlendFunc) gles.glBlendFunc(s, d); }
void glDepthFunc(GLenum f) { if (gles.glDepthFunc) gles.glDepthFunc(f); }
void glActiveTexture(GLenum t) { if (gles.glActiveTexture) gles.glActiveTexture(t); }
void glBindTexture(GLenum t, GLuint b) { if (gles.glBindTexture) gles.glBindTexture(t, b); }
void glGenTextures(GLsizei n, GLuint* t) { if (gles.glGenTextures) gles.glGenTextures(n, t); }
void glDeleteTextures(GLsizei n, const GLuint* t) { if (gles.glDeleteTextures) gles.glDeleteTextures(n, t); }
void glTexImage2D(GLenum t, GLint l, GLint i, GLsizei w, GLsizei h, GLint b, GLenum f, GLenum ty, const void* p) { if (gles.glTexImage2D) gles.glTexImage2D(t, l, i, w, h, b, f, ty, p); }
void glTexSubImage2D(GLenum t, GLint l, GLint x, GLint y, GLsizei w, GLsizei h, GLenum f, GLenum ty, const void* p) { if (gles.glTexSubImage2D) gles.glTexSubImage2D(t, l, x, y, w, h, f, ty, p); }
void glTexParameteri(GLenum t, GLenum p, GLint v) { if (gles.glTexParameteri) gles.glTexParameteri(t, p, v); }
void glGenVertexArrays(GLsizei n, GLuint* a) { if (gles.glGenVertexArrays) gles.glGenVertexArrays(n, a); }
void glDeleteVertexArrays(GLsizei n, const GLuint* a) { if (gles.glDeleteVertexArrays) gles.glDeleteVertexArrays(n, a); }
void glBindVertexArray(GLuint a) { if (gles.glBindVertexArray) gles.glBindVertexArray(a); }
void glGenFramebuffers(GLsizei n, GLuint* f) { if (gles.glGenFramebuffers) gles.glGenFramebuffers(n, f); }
void glDeleteFramebuffers(GLsizei n, const GLuint* f) { if (gles.glDeleteFramebuffers) gles.glDeleteFramebuffers(n, f); }
void glBindFramebuffer(GLenum t, GLuint f) { if (gles.glBindFramebuffer) gles.glBindFramebuffer(t, f); }
void glFramebufferTexture2D(GLenum t, GLenum a, GLenum tt, GLuint tx, GLint l) { if (gles.glFramebufferTexture2D) gles.glFramebufferTexture2D(t, a, tt, tx, l); }
void glDrawBuffers(GLsizei n, const GLenum* b) { if (gles.glDrawBuffers) gles.glDrawBuffers(n, b); }
void glFlush(void) { if (gles.glFlush) gles.glFlush(); }
void glFinish(void) { if (gles.glFinish) gles.glFinish(); }

/* glXGetProcAddress for LWJGL3 compatibility */
void* glXGetProcAddress(const char* procname) {
    if (procname == NULL) return NULL;
    if (strcmp(procname, "glShaderSource") == 0) return (__eglMustCastToProperFunctionPointerType) glShaderSource;
    if (strcmp(procname, "glGetString") == 0) return (__eglMustCastToProperFunctionPointerType) glGetString;
    if (strcmp(procname, "glGetStringi") == 0) return (__eglMustCastToProperFunctionPointerType) glGetStringi;
    if (strcmp(procname, "glGetIntegerv") == 0) return (__eglMustCastToProperFunctionPointerType) glGetIntegerv;
    if (strcmp(procname, "glCreateTextures") == 0) return (__eglMustCastToProperFunctionPointerType) glCreateTextures;
    if (strcmp(procname, "glBindTextureUnit") == 0) return (__eglMustCastToProperFunctionPointerType) glBindTextureUnit;
    if (strcmp(procname, "glTextureStorage1D") == 0) return (__eglMustCastToProperFunctionPointerType) glTextureStorage1D;
    if (strcmp(procname, "glTextureStorage2D") == 0) return (__eglMustCastToProperFunctionPointerType) glTextureStorage2D;
    if (strcmp(procname, "glTextureStorage3D") == 0) return (__eglMustCastToProperFunctionPointerType) glTextureStorage3D;
    if (strcmp(procname, "glTextureSubImage1D") == 0) return (__eglMustCastToProperFunctionPointerType) glTextureSubImage1D;
    if (strcmp(procname, "glTextureSubImage2D") == 0) return (__eglMustCastToProperFunctionPointerType) glTextureSubImage2D;
    if (strcmp(procname, "glTextureParameteri") == 0) return (__eglMustCastToProperFunctionPointerType) glTextureParameteri;
    if (strcmp(procname, "glGenerateTextureMipmap") == 0) return (__eglMustCastToProperFunctionPointerType) glGenerateTextureMipmap;
    if (strcmp(procname, "glCreateBuffers") == 0) return (__eglMustCastToProperFunctionPointerType) glCreateBuffers;
    if (strcmp(procname, "glNamedBufferData") == 0) return (__eglMustCastToProperFunctionPointerType) glNamedBufferData;
    if (strcmp(procname, "glNamedBufferSubData") == 0) return (__eglMustCastToProperFunctionPointerType) glNamedBufferSubData;
    if (strcmp(procname, "glCreateFramebuffers") == 0) return (__eglMustCastToProperFunctionPointerType) glCreateFramebuffers;
    if (strcmp(procname, "glNamedFramebufferTexture") == 0) return (__eglMustCastToProperFunctionPointerType) glNamedFramebufferTexture;
    if (strcmp(procname, "glNamedFramebufferRenderbuffer") == 0) return (__eglMustCastToProperFunctionPointerType) glNamedFramebufferRenderbuffer;
    if (strcmp(procname, "glNamedFramebufferDrawBuffers") == 0) return (__eglMustCastToProperFunctionPointerType) glNamedFramebufferDrawBuffers;
    if (strcmp(procname, "glCheckNamedFramebufferStatus") == 0) return (__eglMustCastToProperFunctionPointerType) glCheckNamedFramebufferStatus;
    if (strcmp(procname, "glCreateVertexArrays") == 0) return (__eglMustCastToProperFunctionPointerType) glCreateVertexArrays;
    if (strcmp(procname, "glCreateRenderbuffers") == 0) return (__eglMustCastToProperFunctionPointerType) glCreateRenderbuffers;
    if (strcmp(procname, "glNamedRenderbufferStorage") == 0) return (__eglMustCastToProperFunctionPointerType) glNamedRenderbufferStorage;
    if (!real_eglGetProcAddress) load_real_egl();
    if (real_eglGetProcAddress) return (void*) real_eglGetProcAddress(procname);
    return NULL;
}
void* glXGetProcAddressARB(const char* procname) { return glXGetProcAddress(procname); }
