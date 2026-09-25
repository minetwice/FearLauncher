//
// FearLauncher OSMesa bridge — persistent CPU frontbuffer + blit to ANativeWindow.
// Always keep OSMesaMakeCurrent on a stable full-size CPU buffer so the GL
// context never becomes "no current context" between frames. Present copies
// into the locked ANativeWindow (handles stride + optional Y flip).
//
#include <android/native_window.h>
#include <stdbool.h>
#ifndef FEARLAUNCHER_OSM_BRIDGE_H
#define FEARLAUNCHER_OSM_BRIDGE_H
#include "osmesa_loader.h"

typedef struct {
    char       state;
    struct ANativeWindow *nativeSurface;
    struct ANativeWindow *newNativeSurface;
    ANativeWindow_Buffer buffer; /* last locked window buffer (present only) */
    int32_t last_stride;
    bool disable_rendering;
    OSMesaContext context;
    /* Stable CPU frontbuffer — OSMesa always renders here */
    void* color_buffer;
    int color_width;
    int color_height;
    int color_row_pixels; /* ROW_LENGTH in pixels (may include padding) */
} osm_render_window_t;

bool osm_init();
osm_render_window_t* osm_get_current();
osm_render_window_t* osm_init_context(osm_render_window_t* share);
void osm_make_current(osm_render_window_t* bundle);
void osm_swap_buffers();
void osm_setup_window();
void osm_swap_interval(int swapInterval);
void osm_release_window();

#endif //FEARLAUNCHER_OSM_BRIDGE_H
