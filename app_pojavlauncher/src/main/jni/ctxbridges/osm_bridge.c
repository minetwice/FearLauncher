//
// Ported from ZalithLauncher (ctxbridges/osm_bridge.c)
// Adapted to use bridge_environ instead of pojav_environ.
//
#include <malloc.h>
#include <string.h>
#include <android/log.h>
#include "osm_bridge.h"
#include "bridge_environ.h"

// Forward decl from swap_interval_no_egl.c
void setNativeWindowSwapInterval(struct ANativeWindow* nativeWindow, int swapInterval);

static const char* g_LogTag = "OSMBridge";
static __thread osm_render_window_t* currentBundle;
static char no_render_buffer[4];
static bool hasSetNoRendererBuffer = false;

bool osm_init() {
    dlsym_OSMesa();
    return true;
}

osm_render_window_t* osm_get_current() {
    return currentBundle;
}

osm_render_window_t* osm_init_context(osm_render_window_t* share) {
    osm_render_window_t* render_window = malloc(sizeof(osm_render_window_t));
    if(render_window == NULL) return NULL;
    memset(render_window, 0, sizeof(osm_render_window_t));
    OSMesaContext osmesa_share = NULL;
    if(share != NULL) osmesa_share = share->context;
    OSMesaContext context = OSMesaCreateContext_p(GL_RGBA, osmesa_share);
    if(context == NULL) {
        free(render_window);
        return NULL;
    }
    render_window->context = context;
    return render_window;
}

void osm_set_no_render_buffer(ANativeWindow_Buffer* buffer) {
    buffer->bits = &no_render_buffer;
    buffer->width = 1;
    buffer->height = 1;
    buffer->stride = 0;
}

void osm_swap_surfaces(osm_render_window_t* bundle) {
    if(bundle->nativeSurface != NULL && bundle->newNativeSurface != bundle->nativeSurface) {
        if(!bundle->disable_rendering) {
            __android_log_print(ANDROID_LOG_INFO, g_LogTag, "Unlocking for cleanup...");
            ANativeWindow_unlockAndPost(bundle->nativeSurface);
        }
        ANativeWindow_release(bundle->nativeSurface);
    }
    if(bundle->newNativeSurface != NULL) {
        __android_log_print(ANDROID_LOG_ERROR, g_LogTag, "Switching to new native surface");
        bundle->nativeSurface = bundle->newNativeSurface;
        bundle->newNativeSurface = NULL;
        ANativeWindow_acquire(bundle->nativeSurface);
        ANativeWindow_setBuffersGeometry(bundle->nativeSurface, 0, 0, WINDOW_FORMAT_RGBX_8888);
        bundle->disable_rendering = false;
        return;
    } else {
        __android_log_print(ANDROID_LOG_ERROR, g_LogTag,
                            "No new native surface, switching to dummy framebuffer");
        bundle->nativeSurface = NULL;
        osm_set_no_render_buffer(&bundle->buffer);
        bundle->disable_rendering = true;
    }
}

void osm_release_window() {
    currentBundle->newNativeSurface = NULL;
    osm_swap_surfaces(currentBundle);
}

void osm_apply_current_ll() {
    ANativeWindow_Buffer* buffer = &currentBundle->buffer;
    OSMesaMakeCurrent_p(currentBundle->context, buffer->bits, GL_UNSIGNED_BYTE, buffer->width, buffer->height);
    if(buffer->stride != currentBundle->last_stride)
        OSMesaPixelStore_p(OSMESA_ROW_LENGTH, buffer->stride);
    currentBundle->last_stride = buffer->stride;
}

void osm_make_current(osm_render_window_t* bundle) {
    if(bundle == NULL) {
        OSMesaMakeCurrent_p(NULL, NULL, 0, 0, 0);
        currentBundle = NULL;
        return;
    }
    bool hasSetMainWindow = false;
    currentBundle = bundle;
    if(bridge_environ.mainWindowBundle == NULL) {
        bridge_environ.mainWindowBundle = (basic_render_window_t*) bundle;
        __android_log_print(ANDROID_LOG_INFO, g_LogTag, "Main window bundle is now %p", bridge_environ.mainWindowBundle);
        bridge_environ.mainWindowBundle->newNativeSurface = bridge_environ.pojavWindow;
        hasSetMainWindow = true;
    }
    if(bundle->nativeSurface == NULL) {
        osm_swap_surfaces(bundle);
        if(hasSetMainWindow) bridge_environ.mainWindowBundle->state = STATE_RENDERER_ALIVE;
    }
    if (!hasSetNoRendererBuffer) {
        osm_set_no_render_buffer(&bundle->buffer);
        hasSetNoRendererBuffer = true;
    }
    osm_apply_current_ll();
    OSMesaPixelStore_p(OSMESA_Y_UP, 0);
}

void osm_swap_buffers() {
    if(currentBundle->state == STATE_RENDERER_NEW_WINDOW) {
        osm_swap_surfaces(currentBundle);
        currentBundle->state = STATE_RENDERER_ALIVE;
    }

    if(currentBundle->nativeSurface != NULL && !currentBundle->disable_rendering)
        if(ANativeWindow_lock(currentBundle->nativeSurface, &currentBundle->buffer, NULL) != 0)
            osm_release_window();

    osm_apply_current_ll();
    glFinish_p();

    if(currentBundle->nativeSurface != NULL && !currentBundle->disable_rendering)
        if(ANativeWindow_unlockAndPost(currentBundle->nativeSurface) != 0)
            osm_release_window();
}

void osm_setup_window() {
    if(bridge_environ.mainWindowBundle != NULL) {
        __android_log_print(ANDROID_LOG_INFO, g_LogTag, "Main window bundle is not NULL, changing state");
        bridge_environ.mainWindowBundle->state = STATE_RENDERER_NEW_WINDOW;
        bridge_environ.mainWindowBundle->newNativeSurface = bridge_environ.pojavWindow;
    }
}

void osm_swap_interval(int swapInterval) {
    if(bridge_environ.mainWindowBundle != NULL && bridge_environ.mainWindowBundle->nativeSurface != NULL) {
        setNativeWindowSwapInterval(bridge_environ.mainWindowBundle->nativeSurface, swapInterval);
    }
}
