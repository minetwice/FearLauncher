/*
 * QuasarV2 - FBO Manager Implementation
 */

#include "quasar_fbo.h"
#include <stdlib.h>
#include <string.h>
#include <dlfcn.h>

#define TAG "Quasar-FBO"
#include <log.h>

typedef void (*PFN_glGenTextures)(GLsizei, GLuint*);
typedef void (*PFN_glBindTexture)(GLenum, GLuint);
typedef void (*PFN_glTexImage2D)(GLenum, GLint, GLint, GLsizei, GLsizei, GLint, GLenum, GLenum, const void*);
typedef void (*PFN_glTexParameteri)(GLenum, GLenum, GLint);
typedef void (*PFN_glGenRenderbuffers)(GLsizei, GLuint*);
typedef void (*PFN_glBindRenderbuffer)(GLenum, GLuint);
typedef void (*PFN_glRenderbufferStorage)(GLenum, GLenum, GLsizei, GLsizei);
typedef void (*PFN_glGenFramebuffers)(GLsizei, GLuint*);
typedef void (*PFN_glBindFramebuffer)(GLenum, GLuint);
typedef void (*PFN_glFramebufferTexture2D)(GLenum, GLenum, GLenum, GLuint, GLint);
typedef void (*PFN_glFramebufferRenderbuffer)(GLenum, GLenum, GLenum, GLuint);
typedef GLenum (*PFN_glCheckFramebufferStatus)(GLenum);
typedef void (*PFN_glDeleteFramebuffers)(GLsizei, const GLuint*);
typedef void (*PFN_glDeleteTextures)(GLsizei, const GLuint*);
typedef void (*PFN_glDeleteRenderbuffers)(GLsizei, const GLuint*);
typedef void (*PFN_glBlitFramebuffer)(GLint, GLint, GLint, GLint, GLint, GLint, GLint, GLint, GLbitfield, GLenum);

static struct {
    PFN_glGenTextures glGenTextures;
    PFN_glBindTexture glBindTexture;
    PFN_glTexImage2D glTexImage2D;
    PFN_glTexParameteri glTexParameteri;
    PFN_glGenRenderbuffers glGenRenderbuffers;
    PFN_glBindRenderbuffer glBindRenderbuffer;
    PFN_glRenderbufferStorage glRenderbufferStorage;
    PFN_glGenFramebuffers glGenFramebuffers;
    PFN_glBindFramebuffer glBindFramebuffer;
    PFN_glFramebufferTexture2D glFramebufferTexture2D;
    PFN_glFramebufferRenderbuffer glFramebufferRenderbuffer;
    PFN_glCheckFramebufferStatus glCheckFramebufferStatus;
    PFN_glDeleteFramebuffers glDeleteFramebuffers;
    PFN_glDeleteTextures glDeleteTextures;
    PFN_glDeleteRenderbuffers glDeleteRenderbuffers;
    PFN_glBlitFramebuffer glBlitFramebuffer;
    int inited;
} gles_fbo_funcs;

static void* resolve_fbo_func(const char* name) {
    static void* gles_handle = NULL;
    if (!gles_handle) gles_handle = dlopen("libGLESv3.so", RTLD_LAZY | RTLD_GLOBAL);
    if (!gles_handle) gles_handle = dlopen("libGLESv2.so", RTLD_LAZY | RTLD_GLOBAL);
    if (gles_handle) return dlsym(gles_handle, name);
    return NULL;
}

static void resolve_gles_fbo_funcs() {
    if (gles_fbo_funcs.inited) return;
    gles_fbo_funcs.glGenTextures = (PFN_glGenTextures) resolve_fbo_func("glGenTextures");
    gles_fbo_funcs.glBindTexture = (PFN_glBindTexture) resolve_fbo_func("glBindTexture");
    gles_fbo_funcs.glTexImage2D = (PFN_glTexImage2D) resolve_fbo_func("glTexImage2D");
    gles_fbo_funcs.glTexParameteri = (PFN_glTexParameteri) resolve_fbo_func("glTexParameteri");
    gles_fbo_funcs.glGenRenderbuffers = (PFN_glGenRenderbuffers) resolve_fbo_func("glGenRenderbuffers");
    gles_fbo_funcs.glBindRenderbuffer = (PFN_glBindRenderbuffer) resolve_fbo_func("glBindRenderbuffer");
    gles_fbo_funcs.glRenderbufferStorage = (PFN_glRenderbufferStorage) resolve_fbo_func("glRenderbufferStorage");
    gles_fbo_funcs.glGenFramebuffers = (PFN_glGenFramebuffers) resolve_fbo_func("glGenFramebuffers");
    gles_fbo_funcs.glBindFramebuffer = (PFN_glBindFramebuffer) resolve_fbo_func("glBindFramebuffer");
    gles_fbo_funcs.glFramebufferTexture2D = (PFN_glFramebufferTexture2D) resolve_fbo_func("glFramebufferTexture2D");
    gles_fbo_funcs.glFramebufferRenderbuffer = (PFN_glFramebufferRenderbuffer) resolve_fbo_func("glFramebufferRenderbuffer");
    gles_fbo_funcs.glCheckFramebufferStatus = (PFN_glCheckFramebufferStatus) resolve_fbo_func("glCheckFramebufferStatus");
    gles_fbo_funcs.glDeleteFramebuffers = (PFN_glDeleteFramebuffers) resolve_fbo_func("glDeleteFramebuffers");
    gles_fbo_funcs.glDeleteTextures = (PFN_glDeleteTextures) resolve_fbo_func("glDeleteTextures");
    gles_fbo_funcs.glDeleteRenderbuffers = (PFN_glDeleteRenderbuffers) resolve_fbo_func("glDeleteRenderbuffers");
    gles_fbo_funcs.glBlitFramebuffer = (PFN_glBlitFramebuffer) resolve_fbo_func("glBlitFramebuffer");
    gles_fbo_funcs.inited = 1;
}

static quasar_fbo_manager_t g_fbo_mgr = {0};

void quasar_fbo_init(quasar_fbo_manager_t *mgr, GLsizei w, GLsizei h) {
    if (!mgr) return;
    resolve_gles_fbo_funcs();

    if (w <= 0) w = 1920;
    if (h <= 0) h = 1080;

    mgr->width = w;
    mgr->height = h;

    /* Create Color Texture Attachment */
    if (gles_fbo_funcs.glGenTextures) gles_fbo_funcs.glGenTextures(1, &mgr->main_color_tex);
    if (gles_fbo_funcs.glBindTexture) gles_fbo_funcs.glBindTexture(GL_TEXTURE_2D, mgr->main_color_tex);
    if (gles_fbo_funcs.glTexImage2D) gles_fbo_funcs.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, NULL);
    if (gles_fbo_funcs.glTexParameteri) {
        gles_fbo_funcs.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        gles_fbo_funcs.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    }

    /* Create Depth + Stencil Renderbuffer Attachment */
    if (gles_fbo_funcs.glGenRenderbuffers) gles_fbo_funcs.glGenRenderbuffers(1, &mgr->main_depth_rbo);
    if (gles_fbo_funcs.glBindRenderbuffer) gles_fbo_funcs.glBindRenderbuffer(GL_RENDERBUFFER, mgr->main_depth_rbo);
    if (gles_fbo_funcs.glRenderbufferStorage) gles_fbo_funcs.glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, w, h);

    /* Create Main FBO */
    if (gles_fbo_funcs.glGenFramebuffers) gles_fbo_funcs.glGenFramebuffers(1, &mgr->main_fbo);
    if (gles_fbo_funcs.glBindFramebuffer) gles_fbo_funcs.glBindFramebuffer(GL_FRAMEBUFFER, mgr->main_fbo);
    if (gles_fbo_funcs.glFramebufferTexture2D) gles_fbo_funcs.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, mgr->main_color_tex, 0);
    if (gles_fbo_funcs.glFramebufferRenderbuffer) gles_fbo_funcs.glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, mgr->main_depth_rbo);

    if (gles_fbo_funcs.glCheckFramebufferStatus && gles_fbo_funcs.glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE) {
        mgr->initialized = 1;
        LOGI("QuasarV2: Main FBO created (%dx%d)", w, h);
    } else {
        LOGE("QuasarV2: Main FBO creation failed!");
    }
}

void quasar_fbo_resize(quasar_fbo_manager_t *mgr, GLsizei w, GLsizei h) {
    if (!mgr || !mgr->initialized) return;
    if (mgr->width == w && mgr->height == h) return;
    quasar_fbo_cleanup(mgr);
    quasar_fbo_init(mgr, w, h);
}

void quasar_fbo_bind_main(quasar_fbo_manager_t *mgr) {
    if (!mgr || !mgr->initialized) return;
    resolve_gles_fbo_funcs();
    if (gles_fbo_funcs.glBindFramebuffer) {
        gles_fbo_funcs.glBindFramebuffer(GL_FRAMEBUFFER, mgr->main_fbo);
    }
}

void quasar_fbo_blit_to_screen(quasar_fbo_manager_t *mgr) {
    if (!mgr || !mgr->initialized) return;
    resolve_gles_fbo_funcs();
    if (gles_fbo_funcs.glBindFramebuffer) {
        gles_fbo_funcs.glBindFramebuffer(GL_READ_FRAMEBUFFER, mgr->main_fbo);
        gles_fbo_funcs.glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
    }
    if (gles_fbo_funcs.glBlitFramebuffer) {
        gles_fbo_funcs.glBlitFramebuffer(0, 0, mgr->width, mgr->height,
                                          0, 0, mgr->width, mgr->height,
                                          GL_COLOR_BUFFER_BIT, GL_NEAREST);
    }
}

void quasar_fbo_cleanup(quasar_fbo_manager_t *mgr) {
    if (!mgr || !mgr->initialized) return;
    resolve_gles_fbo_funcs();
    if (mgr->main_fbo && gles_fbo_funcs.glDeleteFramebuffers) gles_fbo_funcs.glDeleteFramebuffers(1, &mgr->main_fbo);
    if (mgr->main_color_tex && gles_fbo_funcs.glDeleteTextures) gles_fbo_funcs.glDeleteTextures(1, &mgr->main_color_tex);
    if (mgr->main_depth_rbo && gles_fbo_funcs.glDeleteRenderbuffers) gles_fbo_funcs.glDeleteRenderbuffers(1, &mgr->main_depth_rbo);
    mgr->initialized = 0;
}

quasar_fbo_manager_t* quasar_fbo_get_instance(void) {
    return &g_fbo_mgr;
}
