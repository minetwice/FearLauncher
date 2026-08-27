/*
 * QuasarV2 - Custom OpenGL-to-GLES Translator
 * Step 1: Core EGL context + GL function passthrough scaffold
 *
 * This library acts as a fake OpenGL library for LWJGL3.
 * It creates a GLES 3.2 context and translates desktop GL calls to GLES.
 */

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

/* ============================================================
 * EGL Context Management
 * ============================================================ */

static EGLDisplay g_display = EGL_NO_DISPLAY;
static EGLContext g_context = EGL_NO_CONTEXT;
static EGLSurface g_surface = EGL_NO_SURFACE;
static EGLConfig g_config = NULL;

/* Real GLES function pointers (resolved from the Mali driver) */
typedef void (*glShaderSource_t)(GLuint, GLsizei, const GLchar* const*, const GLint*);
typedef void (*glCompileShader_t)(GLuint);
typedef GLuint (*glCreateShader_t)(GLenum);
typedef void (*glDeleteShader_t)(GLuint);
typedef void (*glAttachShader_t)(GLuint, GLuint);
typedef void (*glLinkProgram_t)(GLuint);
typedef void (*glUseProgram_t)(GLuint);
typedef GLuint (*glCreateProgram_t)(void);
typedef void (*glDeleteProgram_t)(GLuint);
typedef void (*glGetShaderiv_t)(GLuint, GLenum, GLint*);
typedef void (*glGetShaderInfoLog_t)(GLuint, GLsizei, GLsizei*, GLchar*);
typedef void (*glGetProgramiv_t)(GLuint, GLenum, GLint*);
typedef void (*glGetProgramInfoLog_t)(GLuint, GLsizei, GLsizei*, GLchar*);

typedef void (*glBindBuffer_t)(GLenum, GLuint);
typedef void (*glBufferData_t)(GLenum, GLsizeiptr, const void*, GLenum);
typedef void (*glBufferSubData_t)(GLenum, GLintptr, GLsizeiptr, const void*);
typedef void (*glGenBuffers_t)(GLsizei, GLuint*);
typedef void (*glDeleteBuffers_t)(GLsizei, const GLuint*);
typedef void (*glVertexAttribPointer_t)(GLuint, GLint, GLenum, GLboolean, GLsizei, const void*);
typedef void (*glEnableVertexAttribArray_t)(GLuint);
typedef void (*glDisableVertexAttribArray_t)(GLuint);
typedef void (*glDrawArrays_t)(GLenum, GLint, GLsizei);
typedef void (*glDrawElements_t)(GLenum, GLsizei, GLenum, const void*);
typedef void (*glViewport_t)(GLint, GLint, GLsizei, GLsizei);
typedef void (*glClearColor_t)(GLfloat, GLfloat, GLfloat, GLfloat);
typedef void (*glClear_t)(GLbitfield);
typedef void (*glEnable_t)(GLenum);
typedef void (*glDisable_t)(GLenum);
typedef void (*glBlendFunc_t)(GLenum, GLenum);
typedef void (*glDepthFunc_t)(GLenum);
typedef void (*glActiveTexture_t)(GLenum);
typedef void (*glBindTexture_t)(GLenum, GLuint);
typedef void (*glGenTextures_t)(GLsizei, GLuint*);
typedef void (*glDeleteTextures_t)(GLsizei, const GLuint*);
typedef void (*glTexImage2D_t)(GLenum, GLint, GLint, GLsizei, GLsizei, GLint, GLenum, GLenum, const void*);
typedef void (*glTexSubImage2D_t)(GLenum, GLint, GLint, GLint, GLsizei, GLsizei, GLenum, GLenum, const void*);
typedef void (*glTexParameteri_t)(GLenum, GLenum, GLint);
typedef void (*glGenVertexArrays_t)(GLsizei, GLuint*);
typedef void (*glDeleteVertexArrays_t)(GLsizei, const GLuint*);
typedef void (*glBindVertexArray_t)(GLuint);
typedef void (*glGenFramebuffers_t)(GLsizei, GLuint*);
typedef void (*glDeleteFramebuffers_t)(GLsizei, const GLuint*);
typedef void (*glBindFramebuffer_t)(GLenum, GLuint);
typedef void (*glFramebufferTexture2D_t)(GLenum, GLenum, GLenum, GLuint, GLint);
typedef void (*glDrawBuffers_t)(GLsizei, const GLenum*);
typedef void (*glCheckFramebufferStatus_t)(GLenum);

typedef const GLubyte* (*glGetString_t)(GLenum);
typedef const GLubyte* (*glGetStringi_t)(GLenum, GLuint);
typedef void (*glGetIntegerv_t)(GLenum, GLint*);
typedef void (*glFlush_t)(void);
typedef void (*glFinish_t)(void);

/* Function pointer table */
static struct {
    glShaderSource_t glShaderSource;
    glCompileShader_t glCompileShader;
    glCreateShader_t glCreateShader;
    glDeleteShader_t glDeleteShader;
    glAttachShader_t glAttachShader;
    glLinkProgram_t glLinkProgram;
    glUseProgram_t glUseProgram;
    glCreateProgram_t glCreateProgram;
    glDeleteProgram_t glDeleteProgram;
    glGetShaderiv_t glGetShaderiv;
    glGetShaderInfoLog_t glGetShaderInfoLog;
    glGetProgramiv_t glGetProgramiv;
    glGetProgramInfoLog_t glGetProgramInfoLog;

    glBindBuffer_t glBindBuffer;
    glBufferData_t glBufferData;
    glBufferSubData_t glBufferSubData;
    glGenBuffers_t glGenBuffers;
    glDeleteBuffers_t glDeleteBuffers;
    glVertexAttribPointer_t glVertexAttribPointer;
    glEnableVertexAttribArray_t glEnableVertexAttribArray;
    glDisableVertexAttribArray_t glDisableVertexAttribArray;
    glDrawArrays_t glDrawArrays;
    glDrawElements_t glDrawElements;
    glViewport_t glViewport;
    glClearColor_t glClearColor;
    glClear_t glClear;
    glEnable_t glEnable;
    glDisable_t glDisable;
    glBlendFunc_t glBlendFunc;
    glDepthFunc_t glDepthFunc;
    glActiveTexture_t glActiveTexture;
    glBindTexture_t glBindTexture;
    glGenTextures_t glGenTextures;
    glDeleteTextures_t glDeleteTextures;
    glTexImage2D_t glTexImage2D;
    glTexSubImage2D_t glTexSubImage2D;
    glTexParameteri_t glTexParameteri;
    glGenVertexArrays_t glGenVertexArrays;
    glDeleteVertexArrays_t glDeleteVertexArrays;
    glBindVertexArray_t glBindVertexArray;
    glGenFramebuffers_t glGenFramebuffers;
    glDeleteFramebuffers_t glDeleteFramebuffers;
    glBindFramebuffer_t glBindFramebuffer;
    glFramebufferTexture2D_t glFramebufferTexture2D;
    glDrawBuffers_t glDrawBuffers;
    glCheckFramebufferStatus_t glCheckFramebufferStatus;

    glGetString_t glGetString;
    glGetStringi_t glGetStringi;
    glGetIntegerv_t glGetIntegerv;
    glFlush_t glFlush;
    glFinish_t glFinish;
} gles;

static void* g_egl_handle = NULL;

/* Resolve a GLES function pointer */
static void* resolve_gles(const char* name) {
    /* Try eglGetProcAddress first */
    typedef void* (*eglGetProcAddress_t)(const char*);
    static eglGetProcAddress_t eglGetProcAddress = NULL;
    if (!eglGetProcAddress) {
        if (!g_egl_handle) g_egl_handle = dlopen("libEGL.so", RTLD_LAZY | RTLD_LOCAL);
        if (g_egl_handle) eglGetProcAddress = (eglGetProcAddress_t) dlsym(g_egl_handle, "eglGetProcAddress");
    }
    if (eglGetProcAddress) {
        void* ptr = eglGetProcAddress(name);
        if (ptr) return ptr;
    }
    /* Fallback: try dlsym from libGLESv3.so */
    static void* gles_handle = NULL;
    if (!gles_handle) gles_handle = dlopen("libGLESv3.so", RTLD_LAZY | RTLD_GLOBAL);
    if (gles_handle) return dlsym(gles_handle, name);
    return NULL;
}

#define RESOLVE(name) gles.name = (name##_t) resolve_gles(#name)

static void init_gles_functions() {
    RESOLVE(glShaderSource);
    RESOLVE(glCompileShader);
    RESOLVE(glCreateShader);
    RESOLVE(glDeleteShader);
    RESOLVE(glAttachShader);
    RESOLVE(glLinkProgram);
    RESOLVE(glUseProgram);
    RESOLVE(glCreateProgram);
    RESOLVE(glDeleteProgram);
    RESOLVE(glGetShaderiv);
    RESOLVE(glGetShaderInfoLog);
    RESOLVE(glGetProgramiv);
    RESOLVE(glGetProgramInfoLog);

    RESOLVE(glBindBuffer);
    RESOLVE(glBufferData);
    RESOLVE(glBufferSubData);
    RESOLVE(glGenBuffers);
    RESOLVE(glDeleteBuffers);
    RESOLVE(glVertexAttribPointer);
    RESOLVE(glEnableVertexAttribArray);
    RESOLVE(glDisableVertexAttribArray);
    RESOLVE(glDrawArrays);
    RESOLVE(glDrawElements);
    RESOLVE(glViewport);
    RESOLVE(glClearColor);
    RESOLVE(glClear);
    RESOLVE(glEnable);
    RESOLVE(glDisable);
    RESOLVE(glBlendFunc);
    RESOLVE(glDepthFunc);
    RESOLVE(glActiveTexture);
    RESOLVE(glBindTexture);
    RESOLVE(glGenTextures);
    RESOLVE(glDeleteTextures);
    RESOLVE(glTexImage2D);
    RESOLVE(glTexSubImage2D);
    RESOLVE(glTexParameteri);
    RESOLVE(glGenVertexArrays);
    RESOLVE(glDeleteVertexArrays);
    RESOLVE(glBindVertexArray);
    RESOLVE(glGenFramebuffers);
    RESOLVE(glDeleteFramebuffers);
    RESOLVE(glBindFramebuffer);
    RESOLVE(glFramebufferTexture2D);
    RESOLVE(glDrawBuffers);
    RESOLVE(glCheckFramebufferStatus);

    RESOLVE(glGetString);
    RESOLVE(glGetStringi);
    RESOLVE(glGetIntegerv);
    RESOLVE(glFlush);
    RESOLVE(glFinish);

    LOGI("QuasarV2: GLES functions resolved (%d functions)", (int)(sizeof(gles)/sizeof(void*)));
}

/* ============================================================
 * EGL Context Creation
 * ============================================================ */

/* Forward declaration - implemented in quasar_shader_hook.c */
extern void quasar_glShaderSource(GLuint shader, GLsizei count, const GLchar* const* string, const GLint* length);

int quasar_init_egl(int width, int height) {
    LOGI("QuasarV2: Initializing EGL context (%dx%d)", width, height);

    g_display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (g_display == EGL_NO_DISPLAY) {
        LOGE("QuasarV2: eglGetDisplay failed");
        return -1;
    }

    EGLint major, minor;
    if (!eglInitialize(g_display, &major, &minor)) {
        LOGE("QuasarV2: eglInitialize failed");
        return -1;
    }
    LOGI("QuasarV2: EGL %d.%d initialized", major, minor);

    /* Request GLES 3.2 context with float color attachments */
    EGLint config_attribs[] = {
        EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_DEPTH_SIZE, 24,
        EGL_STENCIL_SIZE, 8,
        EGL_NONE
    };

    EGLint num_configs;
    if (!eglChooseConfig(g_display, config_attribs, &g_config, 1, &num_configs) || num_configs == 0) {
        LOGE("QuasarV2: eglChooseConfig failed");
        return -1;
    }

    /* Create pbuffer surface for offscreen rendering */
    EGLint surface_attribs[] = {
        EGL_WIDTH, width > 0 ? width : 16,
        EGL_HEIGHT, height > 0 ? height : 16,
        EGL_NONE
    };
    g_surface = eglCreatePbufferSurface(g_display, g_config, surface_attribs);
    if (g_surface == EGL_NO_SURFACE) {
        LOGE("QuasarV2: eglCreatePbufferSurface failed");
        return -1;
    }

    /* Create GLES 3.2 context */
    EGLint context_attribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, 3,
        EGL_CONTEXT_MINOR_VERSION, 2,
        EGL_NONE
    };
    g_context = eglCreateContext(g_display, g_config, EGL_NO_CONTEXT, context_attribs);
    if (g_context == EGL_NO_CONTEXT) {
        /* Try GLES 3.1, then 3.0 */
        context_attribs[3] = 1;
        g_context = eglCreateContext(g_display, g_config, EGL_NO_CONTEXT, context_attribs);
        if (g_context == EGL_NO_CONTEXT) {
            context_attribs[3] = 0;
            context_attribs[2] = EGL_CONTEXT_CLIENT_VERSION;
            context_attribs[3] = 3;
            context_attribs[4] = EGL_NONE;
            g_context = eglCreateContext(g_display, g_config, EGL_NO_CONTEXT, context_attribs);
            if (g_context == EGL_NO_CONTEXT) {
                LOGE("QuasarV2: Failed to create GLES 3.x context");
                return -1;
            }
        }
    }

    if (!eglMakeCurrent(g_display, g_surface, g_surface, g_context)) {
        LOGE("QuasarV2: eglMakeCurrent failed");
        return -1;
    }

    /* Initialize GLES function table */
    init_gles_functions();

    /* Log GL version */
    if (gles.glGetString) {
        const GLubyte* version = gles.glGetString(GL_VERSION);
        const GLubyte* renderer = gles.glGetString(GL_RENDERER);
        const GLubyte* vendor = gles.glGetString(GL_VENDOR);
        LOGI("QuasarV2: GL Version: %s", version ? (const char*)version : "unknown");
        LOGI("QuasarV2: GL Renderer: %s", renderer ? (const char*)renderer : "unknown");
        LOGI("QuasarV2: GL Vendor: %s", vendor ? (const char*)vendor : "unknown");
    }

    LOGI("QuasarV2: EGL context ready");
    return 0;
}

void quasar_shutdown_egl() {
    LOGI("QuasarV2: Shutting down EGL context");
    if (g_display != EGL_NO_DISPLAY) {
        eglMakeCurrent(g_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (g_context != EGL_NO_CONTEXT) eglDestroyContext(g_display, g_context);
        if (g_surface != EGL_NO_SURFACE) eglDestroySurface(g_display, g_surface);
        eglTerminate(g_display);
    }
    g_display = EGL_NO_DISPLAY;
    g_context = EGL_NO_CONTEXT;
    g_surface = EGL_NO_SURFACE;
}

/* ============================================================
 * EGL Functions (exported for LWJGL3)
 * ============================================================ */

EGLDisplay quasar_eglGetDisplay(EGLNativeDisplayType display) {
    return eglGetDisplay(display);
}

EGLBoolean quasar_eglInitialize(EGLDisplay display, EGLint* major, EGLint* minor) {
    return eglInitialize(display, major, minor);
}

EGLBoolean quasar_eglTerminate(EGLDisplay display) {
    return eglTerminate(display);
}

EGLBoolean quasar_eglMakeCurrent(EGLDisplay display, EGLSurface draw, EGLSurface read, EGLContext context) {
    return eglMakeCurrent(display, draw, read, context);
}

/* ============================================================
 * glGetString - Fake desktop GL version for Iris/LWJGL3
 * ============================================================ */

static const char* FAKE_GL_VERSION = "4.6.0 QuasarV2 1.0";
static const char* FAKE_GL_RENDERER = "QuasarV2 Translator (Mali-G615)";
static const char* FAKE_GL_VENDOR = "QuasarV2";
static const char* FAKE_EXTENSIONS =
    "GL_ARB_direct_state_access GL_ARB_buffer_storage "
    "GL_ARB_shader_image_load_store GL_NV_conditional_render "
    "GL_EXT_gpu_shader4 GL_EXT_texture_buffer "
    "GL_EXT_texture_cube_map_array GL_OES_EGL_image_external_essl3 "
    "GL_ARB_shader_texture_lod GL_ARB_shader_objects "
    "GL_ARB_vertex_shader GL_ARB_fragment_shader "
    "GL_EXT_blend_equation_separate GL_EXT_geometry_shader4 "
    "GL_EXT_gpu_program_parameters GL_ARB_instanced_arrays "
    "GL_ARB_draw_instanced GL_ARB_framebuffer_object "
    "GL_ARB_texture_float GL_ARB_color_buffer_float "
    "GL_ARB_half_float_vertex GL_ARB_half_float_pixel "
    "GL_ARB_depth_buffer_float GL_ARB_draw_buffers "
    "GL_ARB_shader_storage_buffer_object GL_ARB_uniform_buffer_object";

static const char* FAKE_EXTENSIONS_LIST[] = {
    "GL_ARB_direct_state_access",
    "GL_ARB_buffer_storage",
    "GL_ARB_shader_image_load_store",
    "GL_NV_conditional_render",
    "GL_EXT_gpu_shader4",
    "GL_EXT_texture_buffer",
    "GL_EXT_texture_cube_map_array",
    "GL_OES_EGL_image_external_essl3",
    "GL_ARB_shader_texture_lod",
    "GL_ARB_shader_objects",
    "GL_ARB_vertex_shader",
    "GL_ARB_fragment_shader",
    "GL_EXT_blend_equation_separate",
    "GL_EXT_geometry_shader4",
    "GL_EXT_gpu_program_parameters",
    "GL_ARB_instanced_arrays",
    "GL_ARB_draw_instanced",
    "GL_ARB_framebuffer_object",
    "GL_ARB_texture_float",
    "GL_ARB_color_buffer_float",
    "GL_ARB_half_float_vertex",
    "GL_ARB_half_float_pixel",
    "GL_ARB_depth_buffer_float",
    "GL_ARB_draw_buffers",
    "GL_ARB_shader_storage_buffer_object",
    "GL_ARB_uniform_buffer_object"
};
static const int FAKE_EXTENSIONS_COUNT = sizeof(FAKE_EXTENSIONS_LIST)/sizeof(FAKE_EXTENSIONS_LIST[0]);

const GLubyte* glGetString(GLenum name) {
    switch(name) {
        case GL_VERSION:  return (const GLubyte*)FAKE_GL_VERSION;
        case GL_RENDERER: return (const GLubyte*)FAKE_GL_RENDERER;
        case GL_VENDOR:   return (const GLubyte*)FAKE_GL_VENDOR;
        case GL_EXTENSIONS: return (const GLubyte*)FAKE_EXTENSIONS;
        case GL_SHADING_LANGUAGE_VERSION: return (const GLubyte*)"4.60 QuasarV2";
    }
    if (gles.glGetString) return gles.glGetString(name);
    return (const GLubyte*)"";
}

const GLubyte* glGetStringi(GLenum name, GLuint index) {
    if (name == GL_EXTENSIONS && index < (GLuint)FAKE_EXTENSIONS_COUNT) {
        return (const GLubyte*)FAKE_EXTENSIONS_LIST[index];
    }
    if (gles.glGetStringi) return gles.glGetStringi(name, index);
    return (const GLubyte*)"";
}

void glGetIntegerv(GLenum pname, GLint* params) {
    if (gles.glGetIntegerv) {
        gles.glGetIntegerv(pname, params);
        /* Override some values for desktop GL compatibility */
        switch(pname) {
            case 0x8B4D: /* GL_MAX_VARYING_FLOATS - Iris checks this */
                *params = 60;
                break;
            case 0x8824: /* GL_MAX_DRAW_BUFFERS */
                if (*params < 8) *params = 8;
                break;
            case 0x8B49: /* GL_MAX_VERTEX_UNIFORM_COMPONENTS */
                if (*params < 4096) *params = 4096;
                break;
            case 0x8B4A: /* GL_MAX_FRAGMENT_UNIFORM_COMPONENTS */
                if (*params < 4096) *params = 4096;
                break;
        }
        return;
    }
    if (params) *params = 0;
}

/* ============================================================
 * GL Functions - Pass through to GLES driver
 * ============================================================ */

/* Shader functions use our hook for glShaderSource */
void glShaderSource(GLuint shader, GLsizei count, const GLchar* const* string, const GLint* length) {
    /* Route through our shader hook for transpilation */
    quasar_glShaderSource(shader, count, string, length);
}

void glCompileShader(GLuint shader) { if (gles.glCompileShader) gles.glCompileShader(shader); }
GLuint glCreateShader(GLenum type) { return gles.glCreateShader ? gles.glCreateShader(type) : 0; }
void glDeleteShader(GLuint shader) { if (gles.glDeleteShader) gles.glDeleteShader(shader); }
void glAttachShader(GLuint program, GLuint shader) { if (gles.glAttachShader) gles.glAttachShader(program, shader); }
void glLinkProgram(GLuint program) { if (gles.glLinkProgram) gles.glLinkProgram(program); }
void glUseProgram(GLuint program) { if (gles.glUseProgram) gles.glUseProgram(program); }
GLuint glCreateProgram(void) { return gles.glCreateProgram ? gles.glCreateProgram() : 0; }
void glDeleteProgram(GLuint program) { if (gles.glDeleteProgram) gles.glDeleteProgram(program); }
void glGetShaderiv(GLuint shader, GLenum pname, GLint* params) { if (gles.glGetShaderiv) gles.glGetShaderiv(shader, pname, params); }
void glGetShaderInfoLog(GLuint shader, GLsizei bufSize, GLsizei* length, GLchar* infoLog) { if (gles.glGetShaderInfoLog) gles.glGetShaderInfoLog(shader, bufSize, length, infoLog); }
void glGetProgramiv(GLuint program, GLenum pname, GLint* params) { if (gles.glGetProgramiv) gles.glGetProgramiv(program, pname, params); }
void glGetProgramInfoLog(GLuint program, GLsizei bufSize, GLsizei* length, GLchar* infoLog) { if (gles.glGetProgramInfoLog) gles.glGetProgramInfoLog(program, bufSize, length, infoLog); }

/* Buffer functions */
void glBindBuffer(GLenum target, GLuint buffer) { if (gles.glBindBuffer) gles.glBindBuffer(target, buffer); }
void glBufferData(GLenum target, GLsizeiptr size, const void* data, GLenum usage) { if (gles.glBufferData) gles.glBufferData(target, size, data, usage); }
void glBufferSubData(GLenum target, GLintptr offset, GLsizeiptr size, const void* data) { if (gles.glBufferSubData) gles.glBufferSubData(target, offset, size, data); }
void glGenBuffers(GLsizei n, GLuint* buffers) { if (gles.glGenBuffers) gles.glGenBuffers(n, buffers); }
void glDeleteBuffers(GLsizei n, const GLuint* buffers) { if (gles.glDeleteBuffers) gles.glDeleteBuffers(n, buffers); }

/* Vertex functions */
void glVertexAttribPointer(GLuint index, GLint size, GLenum type, GLboolean normalized, GLsizei stride, const void* pointer) { if (gles.glVertexAttribPointer) gles.glVertexAttribPointer(index, size, type, normalized, stride, pointer); }
void glEnableVertexAttribArray(GLuint index) { if (gles.glEnableVertexAttribArray) gles.glEnableVertexAttribArray(index); }
void glDisableVertexAttribArray(GLuint index) { if (gles.glDisableVertexAttribArray) gles.glDisableVertexAttribArray(index); }

/* Draw functions */
void glDrawArrays(GLenum mode, GLint first, GLsizei count) { if (gles.glDrawArrays) gles.glDrawArrays(mode, first, count); }
void glDrawElements(GLenum mode, GLsizei count, GLenum type, const void* indices) { if (gles.glDrawElements) gles.glDrawElements(mode, count, type, indices); }

/* State functions */
void glViewport(GLint x, GLint y, GLsizei width, GLsizei height) { if (gles.glViewport) gles.glViewport(x, y, width, height); }
void glClearColor(GLfloat r, GLfloat g, GLfloat b, GLfloat a) { if (gles.glClearColor) gles.glClearColor(r, g, b, a); }
void glClear(GLbitfield mask) { if (gles.glClear) gles.glClear(mask); }
void glEnable(GLenum cap) { if (gles.glEnable) gles.glEnable(cap); }
void glDisable(GLenum cap) { if (gles.glDisable) gles.glDisable(cap); }
void glBlendFunc(GLenum sfactor, GLenum dfactor) { if (gles.glBlendFunc) gles.glBlendFunc(sfactor, dfactor); }
void glDepthFunc(GLenum func) { if (gles.glDepthFunc) gles.glDepthFunc(func); }

/* Texture functions */
void glActiveTexture(GLenum texture) { if (gles.glActiveTexture) gles.glActiveTexture(texture); }
void glBindTexture(GLenum target, GLuint texture) { if (gles.glBindTexture) gles.glBindTexture(target, texture); }
void glGenTextures(GLsizei n, GLuint* textures) { if (gles.glGenTextures) gles.glGenTextures(n, textures); }
void glDeleteTextures(GLsizei n, const GLuint* textures) { if (gles.glDeleteTextures) gles.glDeleteTextures(n, textures); }
void glTexImage2D(GLenum target, GLint level, GLint internalformat, GLsizei width, GLsizei height, GLint border, GLenum format, GLenum type, const void* pixels) { if (gles.glTexImage2D) gles.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels); }
void glTexSubImage2D(GLenum target, GLint level, GLint xoffset, GLint yoffset, GLsizei width, GLsizei height, GLenum format, GLenum type, const void* pixels) { if (gles.glTexSubImage2D) gles.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels); }
void glTexParameteri(GLenum target, GLenum pname, GLint param) { if (gles.glTexParameteri) gles.glTexParameteri(target, pname, param); }

/* VAO functions */
void glGenVertexArrays(GLsizei n, GLuint* arrays) { if (gles.glGenVertexArrays) gles.glGenVertexArrays(n, arrays); }
void glDeleteVertexArrays(GLsizei n, const GLuint* arrays) { if (gles.glDeleteVertexArrays) gles.glDeleteVertexArrays(n, arrays); }
void glBindVertexArray(GLuint array) { if (gles.glBindVertexArray) gles.glBindVertexArray(array); }

/* Framebuffer functions */
void glGenFramebuffers(GLsizei n, GLuint* framebuffers) { if (gles.glGenFramebuffers) gles.glGenFramebuffers(n, framebuffers); }
void glDeleteFramebuffers(GLsizei n, const GLuint* framebuffers) { if (gles.glDeleteFramebuffers) gles.glDeleteFramebuffers(n, framebuffers); }
void glBindFramebuffer(GLenum target, GLuint framebuffer) { if (gles.glBindFramebuffer) gles.glBindFramebuffer(target, framebuffer); }
void glFramebufferTexture2D(GLenum target, GLenum attachment, GLenum textarget, GLuint texture, GLint level) { if (gles.glFramebufferTexture2D) gles.glFramebufferTexture2D(target, attachment, textarget, texture, level); }
void glDrawBuffers(GLsizei n, const GLenum* bufs) { if (gles.glDrawBuffers) gles.glDrawBuffers(n, bufs); }

/* Sync */
void glFlush(void) { if (gles.glFlush) gles.glFlush(); }
void glFinish(void) { if (gles.glFinish) gles.glFinish(); }

/* ============================================================
 * eglGetProcAddress - Function resolver for LWJGL3
 * ============================================================ */

eglGetProcAddress_t real_eglGetProcAddress = NULL;

void* eglGetProcAddress(const char* procname) {
    if (procname == NULL) return NULL;

    /* Return our own implementations for intercepted functions */
    if (strcmp(procname, "glShaderSource") == 0) return (void*) glShaderSource;
    if (strcmp(procname, "glGetString") == 0) return (void*) glGetString;
    if (strcmp(procname, "glGetStringi") == 0) return (void*) glGetStringi;
    if (strcmp(procname, "glGetIntegerv") == 0) return (void*) glGetIntegerv;

    /* For everything else, delegate to real GLES driver */
    if (!real_eglGetProcAddress) {
        if (!g_egl_handle) g_egl_handle = dlopen("libEGL.so", RTLD_LAZY | RTLD_LOCAL);
        if (g_egl_handle) real_eglGetProcAddress = (eglGetProcAddress_t) dlsym(g_egl_handle, "eglGetProcAddress");
    }
    if (real_eglGetProcAddress) return real_eglGetProcAddress(procname);
    return NULL;
}

/* Also export glXGetProcAddress for LWJGL3's Linux symbol resolver */
void* glXGetProcAddress(const char* procname) {
    return eglGetProcAddress(procname);
}

void* glXGetProcAddressARB(const char* procname) {
    return eglGetProcAddress(procname);
}
