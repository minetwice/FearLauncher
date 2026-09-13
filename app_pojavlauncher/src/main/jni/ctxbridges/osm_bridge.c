//
// FearLauncher OSMesa bridge — persistent CPU framebuffer + blit present.
// Zink/OSMesa renders into a tightly-packed RGBA buffer we allocate;
// each swap copies rows into the locked ANativeWindow (handles stride).
//
#include <malloc.h>
#include <string.h>
#include <stdlib.h>
#include <android/log.h>
#include "osm_bridge.h"
#include "bridge_environ.h"

void setNativeWindowSwapInterval(struct ANativeWindow* nativeWindow, int swapInterval);

static const char* g_LogTag = "OSMBridge";
static __thread osm_render_window_t* currentBundle;

bool osm_init() {
    dlsym_OSMesa();
    return osmesa_is_loaded();
}

osm_render_window_t* osm_get_current() {
    return currentBundle;
}

osm_render_window_t* osm_init_context(osm_render_window_t* share) {
    if (!osmesa_is_loaded()) {
        dlsym_OSMesa();
        if (!osmesa_is_loaded()) {
            __android_log_print(ANDROID_LOG_ERROR, g_LogTag, "osm_init_context: OSMesa not loaded");
            return NULL;
        }
    }
    if (OSMesaCreateContext_p == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, g_LogTag, "OSMesaCreateContext symbol is NULL");
        return NULL;
    }
    osm_render_window_t* render_window = malloc(sizeof(osm_render_window_t));
    if (render_window == NULL) return NULL;
    memset(render_window, 0, sizeof(osm_render_window_t));
    OSMesaContext osmesa_share = NULL;
    if (share != NULL) osmesa_share = share->context;
    OSMesaContext context = OSMesaCreateContext_p(GL_RGBA, osmesa_share);
    if (context == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, g_LogTag,
            "OSMesaCreateContext failed (Zink/Vulkan may have failed to init).");
        free(render_window);
        return NULL;
    }
    __android_log_print(ANDROID_LOG_INFO, g_LogTag, "OSMesaCreateContext OK ctx=%p", context);
    render_window->context = context;
    return render_window;
}

/** Ensure color_buffer matches desired size; bind it as OSMesa front buffer. */
static int osm_ensure_color_buffer(osm_render_window_t* bundle, int width, int height) {
    if (bundle == NULL || width <= 0 || height <= 0) return -1;
    if (bundle->color_buffer != NULL
        && bundle->color_width == width
        && bundle->color_height == height) {
        return 0;
    }
    free(bundle->color_buffer);
    size_t bytes = (size_t)width * (size_t)height * 4u;
    bundle->color_buffer = malloc(bytes);
    if (bundle->color_buffer == NULL) {
        bundle->color_width = 0;
        bundle->color_height = 0;
        __android_log_print(ANDROID_LOG_ERROR, g_LogTag, "malloc color buffer %dx%d failed", width, height);
        return -1;
    }
    memset(bundle->color_buffer, 0, bytes);
    bundle->color_width = width;
    bundle->color_height = height;
    __android_log_print(ANDROID_LOG_INFO, g_LogTag,
        "Allocated color buffer %dx%d (%zu bytes) at %p", width, height, bytes, bundle->color_buffer);
    return 0;
}

static void osm_bind_color(osm_render_window_t* bundle) {
    if (bundle == NULL || bundle->context == NULL || OSMesaMakeCurrent_p == NULL) return;
    if (bundle->color_buffer == NULL) return;
    OSMesaMakeCurrent_p(bundle->context, bundle->color_buffer, GL_UNSIGNED_BYTE,
                        bundle->color_width, bundle->color_height);
    if (OSMesaPixelStore_p) {
        OSMesaPixelStore_p(OSMESA_ROW_LENGTH, 0); /* tightly packed = width */
        OSMesaPixelStore_p(OSMESA_Y_UP, 0);
    }
    bundle->last_stride = bundle->color_width;
}

static int osm_resolve_size(int* out_w, int* out_h) {
    int w = 0, h = 0;
    if (bridge_environ.pojavWindow != NULL) {
        w = ANativeWindow_getWidth(bridge_environ.pojavWindow);
        h = ANativeWindow_getHeight(bridge_environ.pojavWindow);
    }
    if (w <= 0) w = bridge_environ.savedWidth;
    if (h <= 0) h = bridge_environ.savedHeight;
    if (w <= 0) w = 1280;
    if (h <= 0) h = 720;
    *out_w = w;
    *out_h = h;
    return 0;
}

void osm_swap_surfaces(osm_render_window_t* bundle) {
    if (bundle->nativeSurface != NULL && bundle->newNativeSurface != bundle->nativeSurface) {
        ANativeWindow_release(bundle->nativeSurface);
        bundle->nativeSurface = NULL;
    }
    if (bundle->newNativeSurface != NULL) {
        __android_log_print(ANDROID_LOG_INFO, g_LogTag, "Switching to new native surface %p",
                            bundle->newNativeSurface);
        bundle->nativeSurface = bundle->newNativeSurface;
        bundle->newNativeSurface = NULL;
        ANativeWindow_acquire(bundle->nativeSurface);
        ANativeWindow_setBuffersGeometry(bundle->nativeSurface, 0, 0, WINDOW_FORMAT_RGBA_8888);
        bundle->disable_rendering = false;

        int w = ANativeWindow_getWidth(bundle->nativeSurface);
        int h = ANativeWindow_getHeight(bundle->nativeSurface);
        if (w > 0) bridge_environ.savedWidth = w;
        if (h > 0) bridge_environ.savedHeight = h;
        if (osm_ensure_color_buffer(bundle, w > 0 ? w : 1280, h > 0 ? h : 720) == 0)
            osm_bind_color(bundle);
        return;
    }
    __android_log_print(ANDROID_LOG_WARN, g_LogTag, "No native surface — color buffer only");
    bundle->nativeSurface = NULL;
    bundle->disable_rendering = true;
    int w, h;
    osm_resolve_size(&w, &h);
    if (osm_ensure_color_buffer(bundle, w, h) == 0)
        osm_bind_color(bundle);
}

void osm_release_window() {
    if (currentBundle == NULL) return;
    currentBundle->newNativeSurface = NULL;
    osm_swap_surfaces(currentBundle);
}

void osm_make_current(osm_render_window_t* bundle) {
    if (bundle == NULL) {
        if (OSMesaMakeCurrent_p) OSMesaMakeCurrent_p(NULL, NULL, 0, 0, 0);
        currentBundle = NULL;
        return;
    }
    currentBundle = bundle;

    if (bridge_environ.mainWindowBundle == NULL) {
        bridge_environ.mainWindowBundle = (basic_render_window_t*) bundle;
        __android_log_print(ANDROID_LOG_INFO, g_LogTag, "Main window bundle is now %p",
                            bridge_environ.mainWindowBundle);
        if (bridge_environ.pojavWindow != NULL)
            bundle->newNativeSurface = bridge_environ.pojavWindow;
    }

    /* If Java already gave us a surface but we haven't attached, attach now */
    if (bundle->nativeSurface == NULL && bridge_environ.pojavWindow != NULL) {
        bundle->newNativeSurface = bridge_environ.pojavWindow;
        osm_swap_surfaces(bundle);
        bundle->state = STATE_RENDERER_ALIVE;
    } else if (bundle->nativeSurface == NULL && bundle->newNativeSurface != NULL) {
        osm_swap_surfaces(bundle);
        bundle->state = STATE_RENDERER_ALIVE;
    }

    /* Always ensure we have a color buffer + bound context */
    int w, h;
    osm_resolve_size(&w, &h);
    if (osm_ensure_color_buffer(bundle, w, h) == 0)
        osm_bind_color(bundle);
    else if (OSMesaMakeCurrent_p)
        OSMesaMakeCurrent_p(bundle->context, NULL, GL_UNSIGNED_BYTE, 0, 0);
}

/** Copy tightly-packed RGBA color_buffer into locked ANativeWindow (respect stride). */
static void osm_blit_to_native(osm_render_window_t* bundle) {
    if (bundle == NULL || bundle->nativeSurface == NULL || bundle->color_buffer == NULL)
        return;
    if (bundle->disable_rendering) return;

    ANativeWindow_Buffer nb;
    memset(&nb, 0, sizeof(nb));
    if (ANativeWindow_lock(bundle->nativeSurface, &nb, NULL) != 0) {
        __android_log_print(ANDROID_LOG_ERROR, g_LogTag, "ANativeWindow_lock failed");
        return;
    }

    const int src_w = bundle->color_width;
    const int src_h = bundle->color_height;
    const int dst_w = nb.width;
    const int dst_h = nb.height;
    const int copy_w = src_w < dst_w ? src_w : dst_w;
    const int copy_h = src_h < dst_h ? src_h : dst_h;
    const uint8_t* src = (const uint8_t*) bundle->color_buffer;
    uint8_t* dst = (uint8_t*) nb.bits;
    const int src_stride_bytes = src_w * 4;
    const int dst_stride_bytes = nb.stride * 4; /* stride is in pixels */

    if (dst != NULL && copy_w > 0 && copy_h > 0) {
        for (int y = 0; y < copy_h; y++) {
            memcpy(dst + (size_t)y * dst_stride_bytes,
                   src + (size_t)y * src_stride_bytes,
                   (size_t)copy_w * 4u);
        }
    }

    if (ANativeWindow_unlockAndPost(bundle->nativeSurface) != 0) {
        __android_log_print(ANDROID_LOG_ERROR, g_LogTag, "unlockAndPost failed");
    }
}

void osm_swap_buffers() {
    if (currentBundle == NULL) return;

    if (currentBundle->state == STATE_RENDERER_NEW_WINDOW) {
        currentBundle->newNativeSurface = bridge_environ.pojavWindow;
        osm_swap_surfaces(currentBundle);
        currentBundle->state = STATE_RENDERER_ALIVE;
    }

    /* If surface arrived late, pick it up */
    if (currentBundle->nativeSurface == NULL && bridge_environ.pojavWindow != NULL) {
        currentBundle->newNativeSurface = bridge_environ.pojavWindow;
        osm_swap_surfaces(currentBundle);
    }

    if (glFinish_p) glFinish_p();

    osm_blit_to_native(currentBundle);

    /* Keep context bound for next frame */
    osm_bind_color(currentBundle);
}

void osm_setup_window() {
    if (bridge_environ.mainWindowBundle != NULL) {
        __android_log_print(ANDROID_LOG_INFO, g_LogTag,
            "setup_window: marking NEW_WINDOW, pojavWindow=%p",
            bridge_environ.pojavWindow);
        bridge_environ.mainWindowBundle->state = STATE_RENDERER_NEW_WINDOW;
        bridge_environ.mainWindowBundle->newNativeSurface = bridge_environ.pojavWindow;
    } else {
        __android_log_print(ANDROID_LOG_WARN, g_LogTag,
            "setup_window: mainWindowBundle NULL, pojavWindow=%p",
            bridge_environ.pojavWindow);
    }
}

void osm_swap_interval(int swapInterval) {
    if (bridge_environ.mainWindowBundle != NULL
        && bridge_environ.mainWindowBundle->nativeSurface != NULL) {
        setNativeWindowSwapInterval(bridge_environ.mainWindowBundle->nativeSurface, swapInterval);
    }
}
