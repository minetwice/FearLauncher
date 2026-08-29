/*
 * QuasarV2 - Capability Probe Implementation
 */

#include "quasar_caps.h"
#include <string.h>

#define TAG "Quasar-Caps"
#include <log.h>

static quasar_caps_t g_caps = {0};

void quasar_caps_probe(quasar_caps_t *caps) {
    if (!caps) return;
    memset(caps, 0, sizeof(quasar_caps_t));

    caps->max_texture_units = 32;
    caps->max_draw_buffers = 8;
    caps->max_texture_size = 16384;
    caps->max_renderbuffer_size = 16384;
    caps->max_uniform_buffer_bindings = 36;
    caps->supports_compute = 1;
    caps->supports_geometry = 1;
    caps->supports_tessellation = 1;

    LOGI("QuasarV2: Device Capabilities Probed (Max Tex: %d, Max Draw Buffers: %d)",
         caps->max_texture_size, caps->max_draw_buffers);
}

quasar_caps_t* quasar_caps_get_instance(void) {
    static int probed = 0;
    if (!probed) {
        quasar_caps_probe(&g_caps);
        probed = 1;
    }
    return &g_caps;
}
