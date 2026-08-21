#include "fear_render.h"

static bool s_guarded_log_done = false;

extern "C" {

void glGetIntegerv(GLenum pname, GLint *data) {
    if (!data) return;

    if (eglGetCurrentContext() == EGL_NO_CONTEXT) {
        if (!s_guarded_log_done) {
            LOGI("[FearRender][GUARD] glGetIntegerv without context");
            s_guarded_log_done = true;
        }
        switch (pname) {
            case GL_MAX_TEXTURE_SIZE:
                *data = 16384;
                break;
            case GL_MAX_DRAW_BUFFERS:
                *data = 8;
                break;
            case GL_MAX_TEXTURE_IMAGE_UNITS:
            case GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS:
                *data = 16;
                break;
            default:
                *data = 0;
                break;
        }
        return;
    }

    fear_init_deferred_if_needed();

    typedef void (*pfn_glGetIntegerv)(GLenum, GLint*);
    static pfn_glGetIntegerv real_glGetIntegerv = NULL;
    if (!real_glGetIntegerv) {
        real_glGetIntegerv = (pfn_glGetIntegerv) dlsym(RTLD_DEFAULT, "glGetIntegerv");
    }
    if (real_glGetIntegerv) {
        real_glGetIntegerv(pname, data);
    }
}

const GLubyte* glGetString(GLenum name) {
    if (eglGetCurrentContext() == EGL_NO_CONTEXT) {
        LOGI("[FearRender][GUARD] glGetString without context");
        switch (name) {
            case GL_VERSION:
                return (const GLubyte*)"OpenGL ES 3.2 (FearRender GLES Core)";
            case GL_RENDERER:
                return (const GLubyte*)"Mali-G615 MC2 (FearRender)";
            case GL_VENDOR:
                return (const GLubyte*)"ARM";
            case GL_SHADING_LANGUAGE_VERSION:
                return (const GLubyte*)"OpenGL ES GLSL ES 3.20";
            default:
                return (const GLubyte*)"";
        }
    }

    fear_init_deferred_if_needed();

    typedef const GLubyte* (*pfn_glGetString)(GLenum);
    static pfn_glGetString real_glGetString = NULL;
    if (!real_glGetString) {
        real_glGetString = (pfn_glGetString) dlsym(RTLD_DEFAULT, "glGetString");
    }
    if (real_glGetString) {
        return real_glGetString(name);
    }
    return (const GLubyte*)"";
}

const GLubyte* glGetStringi(GLenum name, GLuint index) {
    if (eglGetCurrentContext() == EGL_NO_CONTEXT) {
        LOGI("[FearRender][GUARD] glGetStringi without context");
        return (const GLubyte*)"";
    }

    fear_init_deferred_if_needed();

    typedef const GLubyte* (*pfn_glGetStringi)(GLenum, GLuint);
    static pfn_glGetStringi real_glGetStringi = NULL;
    if (!real_glGetStringi) {
        real_glGetStringi = (pfn_glGetStringi) dlsym(RTLD_DEFAULT, "glGetStringi");
    }
    if (real_glGetStringi) {
        return real_glGetStringi(name, index);
    }
    return (const GLubyte*)"";
}

void glMemoryBarrier(GLbitfield barriers) {
    if (eglGetCurrentContext() == EGL_NO_CONTEXT) {
        LOGI("[FearRender][GUARD] glMemoryBarrier without context");
        return;
    }

    typedef void (*pfn_glMemoryBarrier)(GLbitfield);
    static pfn_glMemoryBarrier real_glMemoryBarrier = NULL;
    if (!real_glMemoryBarrier) {
        real_glMemoryBarrier = (pfn_glMemoryBarrier) dlsym(RTLD_DEFAULT, "glMemoryBarrier");
    }
    if (real_glMemoryBarrier) {
        real_glMemoryBarrier(barriers);
    } else {
        typedef void (*pfn_glFlush)(void);
        static pfn_glFlush real_glFlush = NULL;
        if (!real_glFlush) {
            real_glFlush = (pfn_glFlush) dlsym(RTLD_DEFAULT, "glFlush");
        }
        if (real_glFlush) real_glFlush();
    }
}

} // extern "C"
