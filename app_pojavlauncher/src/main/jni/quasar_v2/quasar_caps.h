/*
 * QuasarV2 - Capability Probe Header
 */

#ifndef QUASAR_CAPS_H
#define QUASAR_CAPS_H

#include <GLES3/gl32.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    GLint max_texture_units;
    GLint max_draw_buffers;
    GLint max_texture_size;
    GLint max_renderbuffer_size;
    GLint max_uniform_buffer_bindings;
    int supports_compute;
    int supports_geometry;
    int supports_tessellation;
} quasar_caps_t;

void quasar_caps_probe(quasar_caps_t *caps);
quasar_caps_t* quasar_caps_get_instance(void);

#ifdef __cplusplus
}
#endif

#endif /* QUASAR_CAPS_H */
