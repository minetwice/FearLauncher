/*
 * QuasarV2 - FBO Manager Implementation
 */

#include "quasar_fbo.h"
#include <stdlib.h>

#define TAG "Quasar-FBO"
#include <log.h>

static quasar_fbo_manager_t g_fbo_mgr = {0};

void quasar_fbo_init(quasar_fbo_manager_t *mgr, GLsizei w, GLsizei h) {
    if (!mgr) return;
    if (w <= 0) w = 1920;
    if (h <= 0) h = 1080;

    mgr->width = w;
    mgr->height = h;

    /* Create Color Texture Attachment */
    glGenTextures(1, &mgr->main_color_tex);
    glBindTexture(GL_TEXTURE_2D, mgr->main_color_tex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, NULL);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

    /* Create Depth + Stencil Renderbuffer Attachment */
    glGenRenderbuffers(1, &mgr->main_depth_rbo);
    glBindRenderbuffer(GL_RENDERBUFFER, mgr->main_depth_rbo);
    glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, w, h);

    /* Create Main FBO */
    glGenFramebuffers(1, &mgr->main_fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, mgr->main_fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, mgr->main_color_tex, 0);
    glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, mgr->main_depth_rbo);

    if (glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE) {
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
    glBindFramebuffer(GL_FRAMEBUFFER, mgr->main_fbo);
}

void quasar_fbo_blit_to_screen(quasar_fbo_manager_t *mgr) {
    if (!mgr || !mgr->initialized) return;
    glBindFramebuffer(GL_READ_FRAMEBUFFER, mgr->main_fbo);
    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
    glBlitFramebuffer(0, 0, mgr->width, mgr->height,
                       0, 0, mgr->width, mgr->height,
                       GL_COLOR_BUFFER_BIT, GL_NEAREST);
}

void quasar_fbo_cleanup(quasar_fbo_manager_t *mgr) {
    if (!mgr || !mgr->initialized) return;
    if (mgr->main_fbo) glDeleteFramebuffers(1, &mgr->main_fbo);
    if (mgr->main_color_tex) glDeleteTextures(1, &mgr->main_color_tex);
    if (mgr->main_depth_rbo) glDeleteRenderbuffers(1, &mgr->main_depth_rbo);
    mgr->initialized = 0;
}

quasar_fbo_manager_t* quasar_fbo_get_instance(void) {
    return &g_fbo_mgr;
}
