/*
 * QuasarV2 - Lazy State Tracker Engine Implementation
 */

#include "quasar_state.h"
#include <stdlib.h>
#include <string.h>
#include <dlfcn.h>

#define TAG "Quasar-State"
#include <log.h>

typedef void (*PFN_glViewport)(GLint, GLint, GLsizei, GLsizei);
typedef void (*PFN_glEnable)(GLenum);
typedef void (*PFN_glDisable)(GLenum);
typedef void (*PFN_glBlendFuncSeparate)(GLenum, GLenum, GLenum, GLenum);
typedef void (*PFN_glDepthFunc)(GLenum);
typedef void (*PFN_glDepthMask)(GLboolean);
typedef void (*PFN_glCullFace)(GLenum);
typedef void (*PFN_glFrontFace)(GLenum);

static struct {
    PFN_glViewport glViewport;
    PFN_glEnable glEnable;
    PFN_glDisable glDisable;
    PFN_glBlendFuncSeparate glBlendFuncSeparate;
    PFN_glDepthFunc glDepthFunc;
    PFN_glDepthMask glDepthMask;
    PFN_glCullFace glCullFace;
    PFN_glFrontFace glFrontFace;
    int inited;
} gles_state_funcs;

static void* resolve_func(const char* name) {
    static void* gles_handle = NULL;
    if (!gles_handle) gles_handle = dlopen("libGLESv3.so", RTLD_LAZY | RTLD_GLOBAL);
    if (!gles_handle) gles_handle = dlopen("libGLESv2.so", RTLD_LAZY | RTLD_GLOBAL);
    if (gles_handle) return dlsym(gles_handle, name);
    return NULL;
}

static void resolve_gles_funcs() {
    if (gles_state_funcs.inited) return;
    gles_state_funcs.glViewport = (PFN_glViewport) resolve_func("glViewport");
    gles_state_funcs.glEnable = (PFN_glEnable) resolve_func("glEnable");
    gles_state_funcs.glDisable = (PFN_glDisable) resolve_func("glDisable");
    gles_state_funcs.glBlendFuncSeparate = (PFN_glBlendFuncSeparate) resolve_func("glBlendFuncSeparate");
    gles_state_funcs.glDepthFunc = (PFN_glDepthFunc) resolve_func("glDepthFunc");
    gles_state_funcs.glDepthMask = (PFN_glDepthMask) resolve_func("glDepthMask");
    gles_state_funcs.glCullFace = (PFN_glCullFace) resolve_func("glCullFace");
    gles_state_funcs.glFrontFace = (PFN_glFrontFace) resolve_func("glFrontFace");
    gles_state_funcs.inited = 1;
}

static quasar_context_t g_main_context;

void quasar_state_init(quasar_context_t *ctx) {
    if (!ctx) return;
    memset(ctx, 0, sizeof(quasar_context_t));

    ctx->viewport[0] = 0; ctx->viewport[1] = 0;
    ctx->viewport[2] = 1920; ctx->viewport[3] = 1080;
    ctx->depth_func = GL_LESS;
    ctx->depth_mask = GL_TRUE;
    ctx->cull_mode = GL_BACK;
    ctx->front_face = GL_CCW;
    ctx->blend_src_rgb = GL_ONE; ctx->blend_dst_rgb = GL_ZERO;
    ctx->blend_src_a = GL_ONE; ctx->blend_dst_a = GL_ZERO;
    ctx->draw_buffers[0] = GL_COLOR_ATTACHMENT0;
    ctx->draw_buffer_count = 1;

    LOGI("QuasarV2: State Tracker Initialized");
}

quasar_context_t* quasar_get_current_context(void) {
    static int inited = 0;
    if (!inited) {
        quasar_state_init(&g_main_context);
        inited = 1;
    }
    return &g_main_context;
}

static void handle_viewport(quasar_context_t *ctx) {
    resolve_gles_funcs();
    if (gles_state_funcs.glViewport) {
        gles_state_funcs.glViewport(ctx->viewport[0], ctx->viewport[1], ctx->viewport[2], ctx->viewport[3]);
    }
}

static void handle_blend(quasar_context_t *ctx) {
    resolve_gles_funcs();
    if (ctx->blend_enabled) {
        if (gles_state_funcs.glEnable) gles_state_funcs.glEnable(GL_BLEND);
        if (gles_state_funcs.glBlendFuncSeparate) {
            gles_state_funcs.glBlendFuncSeparate(ctx->blend_src_rgb, ctx->blend_dst_rgb, ctx->blend_src_a, ctx->blend_dst_a);
        }
    } else {
        if (gles_state_funcs.glDisable) gles_state_funcs.glDisable(GL_BLEND);
    }
}

static void handle_depth(quasar_context_t *ctx) {
    resolve_gles_funcs();
    if (ctx->depth_test) {
        if (gles_state_funcs.glEnable) gles_state_funcs.glEnable(GL_DEPTH_TEST);
        if (gles_state_funcs.glDepthFunc) gles_state_funcs.glDepthFunc(ctx->depth_func);
    } else {
        if (gles_state_funcs.glDisable) gles_state_funcs.glDisable(GL_DEPTH_TEST);
    }
    if (gles_state_funcs.glDepthMask) gles_state_funcs.glDepthMask(ctx->depth_mask);
}

static void handle_cull(quasar_context_t *ctx) {
    resolve_gles_funcs();
    if (ctx->cull_face) {
        if (gles_state_funcs.glEnable) gles_state_funcs.glEnable(GL_CULL_FACE);
        if (gles_state_funcs.glCullFace) gles_state_funcs.glCullFace(ctx->cull_mode);
        if (gles_state_funcs.glFrontFace) gles_state_funcs.glFrontFace(ctx->front_face);
    } else {
        if (gles_state_funcs.glDisable) gles_state_funcs.glDisable(GL_CULL_FACE);
    }
}

typedef void (*quasar_handler_t)(quasar_context_t *ctx);

static const quasar_handler_t handlers[] = {
    handle_viewport, /* Bit 0 */
    NULL,            /* Bit 1 Scissor */
    handle_blend,    /* Bit 2 */
    handle_depth,    /* Bit 3 */
    NULL,            /* Bit 4 Stencil */
    handle_cull      /* Bit 5 */
};

void quasar_state_flush(quasar_context_t *ctx) {
    if (!ctx || ctx->dirty_bits == 0) return;

    for (size_t i = 0; i < sizeof(handlers)/sizeof(handlers[0]); i++) {
        if ((ctx->dirty_bits & (1ULL << i)) && handlers[i]) {
            handlers[i](ctx);
        }
    }
    ctx->dirty_bits = 0;
}
