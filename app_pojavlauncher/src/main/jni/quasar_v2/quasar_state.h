/*
 * QuasarV2 - State Tracker Header
 */

#ifndef QUASAR_STATE_H
#define QUASAR_STATE_H

#include <GLES3/gl32.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef enum {
    QUASAR_DIRTY_VIEWPORT       = 1ULL << 0,
    QUASAR_DIRTY_SCISSOR        = 1ULL << 1,
    QUASAR_DIRTY_BLEND          = 1ULL << 2,
    QUASAR_DIRTY_DEPTH          = 1ULL << 3,
    QUASAR_DIRTY_STENCIL        = 1ULL << 4,
    QUASAR_DIRTY_CULL           = 1ULL << 5,
    QUASAR_DIRTY_TEXTURES       = 1ULL << 6,
    QUASAR_DIRTY_PROGRAM        = 1ULL << 7,
    QUASAR_DIRTY_FRAMEBUFFER    = 1ULL << 8,
    QUASAR_DIRTY_VERTEX_ARRAY   = 1ULL << 9,
    QUASAR_DIRTY_DRAW_BUFFERS   = 1ULL << 10
} quasar_dirty_bit_t;

typedef struct quasar_context {
    uint64_t dirty_bits;

    /* Viewport & Scissor */
    GLint viewport[4];
    GLint scissor_box[4];
    GLboolean scissor_enabled;

    /* Blend State */
    GLboolean blend_enabled;
    GLenum blend_src_rgb, blend_dst_rgb, blend_src_a, blend_dst_a;
    GLfloat blend_color[4];

    /* Depth State */
    GLboolean depth_test;
    GLenum depth_func;
    GLboolean depth_mask;

    /* Cull State */
    GLboolean cull_face;
    GLenum cull_mode, front_face;

    /* Texture State */
    GLuint bound_textures[32];
    GLint active_texture_unit;

    /* Program State */
    GLuint current_program;

    /* Framebuffer State */
    GLuint draw_fbo, read_fbo;
    GLenum draw_buffers[8];
    GLint draw_buffer_count;

    /* VAO State */
    GLuint current_vao;
} quasar_context_t;

void quasar_state_init(quasar_context_t *ctx);
void quasar_state_flush(quasar_context_t *ctx);
quasar_context_t* quasar_get_current_context(void);

#ifdef __cplusplus
}
#endif

#endif /* QUASAR_STATE_H */
