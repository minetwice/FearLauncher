#include "fear_render.h"

GLenum fear_remap_internal_format(GLenum internalFormat) {
    switch (internalFormat) {
        case 0x8F99: // GL_RGB9_E5
            return GL_RGBA16F;
        case GL_DEPTH_COMPONENT32F:
            return GL_DEPTH_COMPONENT24;
        case GL_SRGB8_ALPHA8:
            return GL_RGBA8;
        default:
            return internalFormat;
    }
}

extern "C" {

void glTexImage2D(GLenum target, GLint level, GLint internalformat, GLsizei width, GLsizei height, GLint border, GLenum format, GLenum type, const void *pixels) {
    if (eglGetCurrentContext() == EGL_NO_CONTEXT) {
        LOGI("[FearRender][GUARD] glTexImage2D without context");
        return;
    }

    fear_init_deferred_if_needed();

    GLenum mappedInternal = fear_remap_internal_format((GLenum)internalformat);

    typedef void (*pfn_glTexImage2D)(GLenum, GLint, GLint, GLsizei, GLsizei, GLint, GLenum, GLenum, const void *);
    static pfn_glTexImage2D real_glTexImage2D = NULL;
    if (!real_glTexImage2D) {
        real_glTexImage2D = (pfn_glTexImage2D) dlsym(RTLD_DEFAULT, "glTexImage2D");
    }
    if (real_glTexImage2D) {
        real_glTexImage2D(target, level, (GLint)mappedInternal, width, height, border, format, type, pixels);
    }
}

GLenum glCheckFramebufferStatus(GLenum target) {
    if (eglGetCurrentContext() == EGL_NO_CONTEXT) {
        LOGI("[FearRender][GUARD] glCheckFramebufferStatus without context");
        return GL_FRAMEBUFFER_COMPLETE;
    }

    fear_init_deferred_if_needed();

    typedef GLenum (*pfn_glCheckFramebufferStatus)(GLenum);
    static pfn_glCheckFramebufferStatus real_glCheckFramebufferStatus = NULL;
    if (!real_glCheckFramebufferStatus) {
        real_glCheckFramebufferStatus = (pfn_glCheckFramebufferStatus) dlsym(RTLD_DEFAULT, "glCheckFramebufferStatus");
    }

    GLenum status = GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT;
    if (real_glCheckFramebufferStatus) {
        for (int retry = 0; retry < 3; ++retry) {
            status = real_glCheckFramebufferStatus(target);
            if (status == GL_FRAMEBUFFER_COMPLETE) {
                break;
            }
            LOGW("[FearRender][FBO] Status incomplete (0x%x) on attempt %d/3", status, retry + 1);
        }
    }
    return status;
}

} // extern "C"
