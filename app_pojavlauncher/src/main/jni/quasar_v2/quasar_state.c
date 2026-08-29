/*
 * QuasarV2 - Lazy State Tracker Engine Implementation
 */

#include "quasar_state.h"
#include <stdlib.h>
#include <string.h>

#define TAG "Quasar-State"
#include <log.h>

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
    glViewport(ctx->viewport[0], ctx->viewport[1], ctx->viewport[2], ctx->viewport[3]);
}

static void handle_blend(quasar_context_t *ctx) {
    if (ctx->blend_enabled) {
        glEnable(GL_BLEND);
        glBlendFuncSeparate(ctx->blend_src_rgb, ctx->blend_dst_rgb, ctx->blend_src_a, ctx->blend_dst_a);
    } else {
        glDisable(GL_BLEND);
    }
}

static void handle_depth(quasar_context_t *ctx) {
    if (ctx->depth_test) {
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(ctx->depth_func);
    } else {
        glDisable(GL_DEPTH_TEST);
    }
    glDepthMask(ctx->depth_mask);
}

static void handle_cull(quasar_context_t *ctx) {
    if (ctx->cull_face) {
        glEnable(GL_CULL_FACE);
        glCullFace(ctx->cull_mode);
        glFrontFace(ctx->front_face);
    } else {
        glDisable(GL_CULL_FACE);
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
