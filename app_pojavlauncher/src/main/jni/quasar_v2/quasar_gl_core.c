/*
 * QuasarV2 - Custom OpenGL-to-GLES Translator
 * Step 1: Core EGL context + GL function passthrough scaffold
 *
 * This library acts as a fake OpenGL library for LWJGL3.
 * It creates a GLES 3.2 context and translates desktop GL calls to GLES.
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
typedef void (*PFN_glTexSubImage2D)(GLenum, GLint, GLint, GLint, GLsizei, GLsizei, GLenum, GLenum, const void*);
typedef void (*PFN_glTexParameteri)(GLenum, GLenum, GLint);
typedef void (*PFN_glGenVertexArrays)(GLsizei, GLuint*);
typedef void (*PFN_glDeleteVertexArrays)(GLsizei, const GLuint*);
typedef void (*PFN_glBindVertexArray)(GLuint);
typedef void (*PFN_glGenFramebuffers)(GLsizei, GLuint*);
typedef void (*PFN_glDeleteFramebuffers)(GLsizei, const GLuint*);
typedef void (*PFN_glBindFramebuffer)(GLenum, GLuint);
typedef void (*PFN_glFramebufferTexture2D)(GLenum, GLenum, GLenum, GLuint, GLint);
typedef void (*PFN_glDrawBuffers)(GLsizei, const GLenum*);
typedef void (*PFN_glCheckFramebufferStatus)(GLenum);
typedef const GLubyte* (*PFN_glGetString)(GLenum);
typedef const GLubyte* (*PFN_glGetStringi)(GLenum, GLuint);
typedef void (*PFN_glGetIntegerv)(GLenum, GLint*);
typedef void (*PFN_glFlush)(void);
typedef void (*PFN_glFinish)(void);
typedef void* (*PFN_eglGetProcAddress)(const char*);

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
    PFN_glTexSubImage2D glTexSubImage2D;
    PFN_glTexParameteri glTexParameteri;
    PFN_glGenVertexArrays glGenVertexArrays;
    PFN_glDeleteVertexArrays glDeleteVertexArrays;
    PFN_glBindVertexArray glBindVertexArray;
    PFN_glGenFramebuffers glGenFramebuffers;
    PFN_glDeleteFramebuffers glDeleteFramebuffers;
    PFN_glBindFramebuffer glBindFramebuffer;
    PFN_glFramebufferTexture2D glFramebufferTexture2D;
    PFN_glDrawBuffers glDrawBuffers;
    PFN_glCheckFramebufferStatus glCheckFramebufferStatus;
    PFN_glGetString glGetString;
    PFN_glGetStringi glGetStringi;
    PFN_glGetIntegerv glGetIntegerv;
    PFN_glFlush glFlush;
    PFN_glFinish glFinish;
} gles;

PFN_eglGetProcAddress real_eglGetProcAddress = NULL;
static void* g_egl_handle = NULL;

static void* resolve_gles(const char* name) {
    if (!real_eglGetProcAddress) {
        if (!g_egl_handle) g_egl_handle = dlopen("libEGL.so", RTLD_LAZY | RTLD_LOCAL);
        if (g_egl_handle) real_eglGetProcAddress = (PFN_eglGetProcAddress) dlsym(g_egl_handle, "eglGetProcAddress");
    }
    if (real_eglGetProcAddress) {
        void* ptr = real_eglGetProcAddress(name);
        if (ptr) return ptr;
    }
    static void* gles_handle = NULL;
    if (!gles_handle) gles_handle = dlopen("libGLESv3.so", RTLD_LAZY | RTLD_GLOBAL);
    if (gles_handle) return dlsym(gles_handle, name);
    return NULL;
}

#define RESOLVE(name) gles.name = (PFN_##name) resolve_gles(#name)

static void init_gles_functions() {
    RESOLVE(glShaderSource); RESOLVE(glCompileShader); RESOLVE(glCreateShader); RESOLVE(glDeleteShader);
    RESOLVE(glAttachShader); RESOLVE(glLinkProgram); RESOLVE(glUseProgram); RESOLVE(glCreateProgram);
    RESOLVE(glDeleteProgram); RESOLVE(glGetShaderiv); RESOLVE(glGetShaderInfoLog); RESOLVE(glGetProgramiv);
    RESOLVE(glGetProgramInfoLog);
    RESOLVE(glBindBuffer); RESOLVE(glBufferData); RESOLVE(glBufferSubData); RESOLVE(glGenBuffers);
    RESOLVE(glDeleteBuffers); RESOLVE(glVertexAttribPointer); RESOLVE(glEnableVertexAttribArray);
    RESOLVE(glDisableVertexAttribArray); RESOLVE(glDrawArrays); RESOLVE(glDrawElements);
    RESOLVE(glViewport); RESOLVE(glClearColor); RESOLVE(glClear); RESOLVE(glEnable); RESOLVE(glDisable);
    RESOLVE(glBlendFunc); RESOLVE(glDepthFunc); RESOLVE(glActiveTexture); RESOLVE(glBindTexture);
    RESOLVE(glGenTextures); RESOLVE(glDeleteTextures); RESOLVE(glTexImage2D); RESOLVE(glTexSubImage2D);
    RESOLVE(glTexParameteri); RESOLVE(glGenVertexArrays); RESOLVE(glDeleteVertexArrays);
    RESOLVE(glBindVertexArray); RESOLVE(glGenFramebuffers); RESOLVE(glDeleteFramebuffers);
    RESOLVE(glBindFramebuffer); RESOLVE(glFramebufferTexture2D); RESOLVE(glDrawBuffers);
    RESOLVE(glCheckFramebufferStatus);
    RESOLVE(glGetString); RESOLVE(glGetStringi); RESOLVE(glGetIntegerv); RESOLVE(glFlush); RESOLVE(glFinish);
    LOGI("QuasarV2: GLES functions resolved");
}

extern void quasar_glShaderSource(GLuint shader, GLsizei count, const GLchar* const* string, const GLint* length);

JNIEXPORT jint JNICALL
Java_net_kdt_pojavlaunch_quasar_QuasarV2_initEGL(JNIEnv* env, jclass cls, jint width, jint height) {
    LOGI("QuasarV2: Initializing EGL context (%dx%d)", width, height);
    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display == EGL_NO_DISPLAY) { LOGE("QuasarV2: eglGetDisplay failed"); return -1; }
    EGLint major, minor;
    if (!eglInitialize(display, &major, &minor)) { LOGE("QuasarV2: eglInitialize failed"); return -1; }
    LOGI("QuasarV2: EGL %d.%d initialized", major, minor);
    EGLint config_attribs[] = { EGL_SURFACE_TYPE, EGL_PBUFFER_BIT, EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
        EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8, EGL_DEPTH_SIZE, 24, EGL_STENCIL_SIZE, 8, EGL_NONE };
    EGLConfig config; EGLint num_configs;
    if (!eglChooseConfig(display, config_attribs, &config, 1, &num_configs) || num_configs == 0) { LOGE("QuasarV2: eglChooseConfig failed"); return -1; }
    EGLint surface_attribs[] = { EGL_WIDTH, width > 0 ? width : 16, EGL_HEIGHT, height > 0 ? height : 16, EGL_NONE };
    EGLSurface surface = eglCreatePbufferSurface(display, config, surface_attribs);
    if (surface == EGL_NO_SURFACE) { LOGE("QuasarV2: eglCreatePbufferSurface failed"); return -1; }
    EGLint context_attribs[] = { EGL_CONTEXT_CLIENT_VERSION, 3, EGL_CONTEXT_MINOR_VERSION, 2, EGL_NONE };
    EGLContext context = eglCreateContext(display, config, EGL_NO_CONTEXT, context_attribs);
    if (context == EGL_NO_CONTEXT) { context_attribs[3] = 1; context = eglCreateContext(display, config, EGL_NO_CONTEXT, context_attribs);
        if (context == EGL_NO_CONTEXT) { context_attribs[3] = 0; context = eglCreateContext(display, config, EGL_NO_CONTEXT, context_attribs);
            if (context == EGL_NO_CONTEXT) { LOGE("QuasarV2: Failed to create GLES 3.x context"); return -1; } } }
    if (!eglMakeCurrent(display, surface, surface, context)) { LOGE("QuasarV2: eglMakeCurrent failed"); return -1; }
    init_gles_functions();
    if (gles.glGetString) {
        LOGI("QuasarV2: GL Version: %s", gles.glGetString(GL_VERSION));
        LOGI("QuasarV2: GL Renderer: %s", gles.glGetString(GL_RENDERER));
    }
    LOGI("QuasarV2: EGL context ready");
    return 0;
}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_quasar_QuasarV2_shutdownEGL(JNIEnv* env, jclass cls) {
    LOGI("QuasarV2: Shutting down EGL context");
    EGLDisplay display = eglGetCurrentDisplay();
    if (display != EGL_NO_DISPLAY) {
        eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        EGLContext context = eglGetCurrentContext();
        EGLSurface surface = eglGetCurrentSurface(EGL_DRAW);
        if (context != EGL_NO_CONTEXT) eglDestroyContext(display, context);
        if (surface != EGL_NO_SURFACE) eglDestroySurface(display, surface);
        eglTerminate(display);
    }
}

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

const GLubyte* glGetString(GLenum name) {
    switch(name) {
        case GL_VERSION:  return (const GLubyte*)"4.6.0 QuasarV2 1.0";
        case GL_RENDERER: return (const GLubyte*)"QuasarV2 Translator (Mali-G615)";
        case GL_VENDOR:   return (const GLubyte*)"QuasarV2";
        case GL_EXTENSIONS: return (const GLubyte*)"";
        case GL_SHADING_LANGUAGE_VERSION: return (const GLubyte*)"4.60 QuasarV2";
    }
    if (gles.glGetString) return gles.glGetString(name);
    return (const GLubyte*)"";
}

const GLubyte* glGetStringi(GLenum name, GLuint index) {
    if (name == GL_EXTENSIONS && index < (GLuint)FAKE_EXTENSIONS_COUNT)
        return (const GLubyte*)FAKE_EXTENSIONS_LIST[index];
    if (gles.glGetStringi) return gles.glGetStringi(name, index);
    return (const GLubyte*)"";
}

void glGetIntegerv(GLenum pname, GLint* params) {
    if (gles.glGetIntegerv) {
        gles.glGetIntegerv(pname, params);
        switch(pname) {
            case 0x8B4D: *params = 60; break;
            case 0x8824: if (*params < 8) *params = 8; break;
            case 0x8B49: if (*params < 4096) *params = 4096; break;
            case 0x8B4A: if (*params < 4096) *params = 4096; break;
        }
        return;
    }
    if (params) *params = 0;
}

void glShaderSource(GLuint shader, GLsizei count, const GLchar* const* string, const GLint* length) {
    quasar_glShaderSource(shader, count, string, length);
}
void glCompileShader(GLuint s) { if (gles.glCompileShader) gles.glCompileShader(s); }
GLuint glCreateShader(GLenum t) { return gles.glCreateShader ? gles.glCreateShader(t) : 0; }
void glDeleteShader(GLuint s) { if (gles.glDeleteShader) gles.glDeleteShader(s); }
void glAttachShader(GLuint p, GLuint s) { if (gles.glAttachShader) gles.glAttachShader(p, s); }
void glLinkProgram(GLuint p) { if (gles.glLinkProgram) gles.glLinkProgram(p); }
void glUseProgram(GLuint p) { if (gles.glUseProgram) gles.glUseProgram(p); }
GLuint glCreateProgram(void) { return gles.glCreateProgram ? gles.glCreateProgram() : 0; }
void glDeleteProgram(GLuint p) { if (gles.glDeleteProgram) gles.glDeleteProgram(p); }
void glGetShaderiv(GLuint s, GLenum p, GLint* v) { if (gles.glGetShaderiv) gles.glGetShaderiv(s, p, v); }
void glGetShaderInfoLog(GLuint s, GLsizei b, GLsizei* l, GLchar* i) { if (gles.glGetShaderInfoLog) gles.glGetShaderInfoLog(s, b, l, i); }
void glGetProgramiv(GLuint p, GLenum n, GLint* v) { if (gles.glGetProgramiv) gles.glGetProgramiv(p, n, v); }
void glGetProgramInfoLog(GLuint p, GLsizei b, GLsizei* l, GLchar* i) { if (gles.glGetProgramInfoLog) gles.glGetProgramInfoLog(p, b, l, i); }
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

void* glXGetProcAddress(const char* procname) {
    if (procname == NULL) return NULL;
    if (strcmp(procname, "glShaderSource") == 0) return (void*) glShaderSource;
    if (strcmp(procname, "glGetString") == 0) return (void*) glGetString;
    if (strcmp(procname, "glGetStringi") == 0) return (void*) glGetStringi;
    if (strcmp(procname, "glGetIntegerv") == 0) return (void*) glGetIntegerv;
    if (!real_eglGetProcAddress) {
        if (!g_egl_handle) g_egl_handle = dlopen("libEGL.so", RTLD_LAZY | RTLD_LOCAL);
        if (g_egl_handle) real_eglGetProcAddress = (PFN_eglGetProcAddress) dlsym(g_egl_handle, "eglGetProcAddress");
    }
    if (real_eglGetProcAddress) return real_eglGetProcAddress(procname);
    return NULL;
}
void* glXGetProcAddressARB(const char* procname) { return glXGetProcAddress(procname); }
