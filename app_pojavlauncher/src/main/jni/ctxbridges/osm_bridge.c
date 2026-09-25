//
// FearLauncher OSMesa bridge — direct lock / MakeCurrent / unlock present path.
//
// Root cause of Panfork black title screen (Mega-checkpoint):
// The previous bridge allocated a separate CPU color_buffer, rendered into it,
// then memcpy'd into ANativeWindow. With Panfrost/OSMesa (hardware Gallium path)
// that user buffer often stayed all zeros (audio + input still worked).
//
// Fix (same pattern as ZalithLauncher / classic Pojav OSMesa):
//   1) ANativeWindow_lock  → get bits + stride
//   2) OSMesaMakeCurrent(ctx, bits, ..., width, height)
//   3) OSMesaPixelStore(ROW_LENGTH, stride) + Y_UP=0
//   4) glFinish  (flush last frame into the locked buffer)
//   5) ANativeWindow_unlockAndPost
//
#include <malloc.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <dlfcn.h>
#include <android/log.h>
#include "osm_bridge.h"
#include "bridge_environ.h"

void setNativeWindowSwapInterval(struct ANativeWindow* nativeWindow, int swapInterval);

static const char* g_LogTag = "OSMBridge";
static __thread osm_render_window_t* currentBundle = NULL;

/* Tiny buffer when there is nowhere to present yet */
static char g_no_render_buffer[4];
static unsigned g_diag_swaps = 0;

static void osm_set_dummy_buffer(ANativeWindow_Buffer* buffer) {
    buffer->bits = g_no_render_buffer;
    buffer->width = 1;
    buffer->height = 1;
    buffer->stride = 1;
    buffer->format = WINDOW_FORMAT_RGBA_8888;
}

/** Bind OSMesa to the current ANativeWindow_Buffer (or dummy). */
static void osm_apply_current(void) {
    if (currentBundle == NULL || currentBundle->context == NULL || OSMesaMakeCurrent_p == NULL)
        return;

    ANativeWindow_Buffer* buf = &currentBundle->buffer;
    if (buf->bits == NULL || buf->width <= 0 || buf->height <= 0) {
        osm_set_dummy_buffer(buf);
    }

    OSMesaMakeCurrent_p(currentBundle->context, buf->bits, GL_UNSIGNED_BYTE,
                        buf->width, buf->height);

    if (OSMesaPixelStore_p) {
        /* ROW_LENGTH is in *pixels*; match the locked buffer's stride */
        int row_len = buf->stride > 0 ? buf->stride : buf->width;
        OSMesaPixelStore_p(OSMESA_ROW_LENGTH, row_len);
        OSMesaPixelStore_p(OSMESA_Y_UP, 0);
    }
    currentBundle->last_stride = buf->stride;
}

static void osm_diag_sample(const char* where) {
    osm_render_window_t* b = currentBundle;
    if (b == NULL) {
        fprintf(stderr, "OSMDIAG[%s]: NO current bundle\n", where);
        return;
    }
    unsigned long c = 0, tl = 0, br = 0;
    ANativeWindow_Buffer* buf = &b->buffer;
    if (buf->bits != NULL && buf->width > 1 && buf->height > 1 && buf->stride > 0) {
        const uint32_t* px = (const uint32_t*) buf->bits;
        int w = buf->width;
        int h = buf->height;
        int s = buf->stride;
        c  = px[(size_t)(h / 2) * (size_t)s + (size_t)(w / 2)];
        tl = px[0];
        br = px[(size_t)(h - 1) * (size_t)s + (size_t)(w - 1)];
    }
    fprintf(stderr, "OSMDIAG[%s]: bits=%p %dx%d stride=%d surf=%p win=%p disable=%d state=%d "
            "center=0x%08lx tl=0x%08lx br=0x%08lx\n",
            where, buf->bits, buf->width, buf->height, buf->stride,
            b->nativeSurface, bridge_environ.pojavWindow,
            (int)b->disable_rendering, (int)b->state, c, tl, br);
}

bool osm_init() {
    dlsym_OSMesa();
    if (!osmesa_is_loaded()) {
        __android_log_print(ANDROID_LOG_ERROR, g_LogTag, "OSMesa symbols not loaded");
        return false;
    }
    __android_log_print(ANDROID_LOG_INFO, g_LogTag, "OSMesa bridge init OK (direct ANativeWindow path)");
    return true;
}

osm_render_window_t* osm_get_current() {
    return currentBundle;
}

osm_render_window_t* osm_init_context(osm_render_window_t* share) {
    if (!osmesa_is_loaded()) {
        dlsym_OSMesa();
        if (!osmesa_is_loaded()) return NULL;
    }

    osm_render_window_t* render_window = calloc(1, sizeof(osm_render_window_t));
    if (render_window == NULL) return NULL;

    OSMesaContext osmesa_share = share != NULL ? share->context : NULL;
    OSMesaContext context = OSMesaCreateContext_p(GL_RGBA, osmesa_share);
    if (context == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, g_LogTag, "OSMesaCreateContext failed");
        free(render_window);
        return NULL;
    }
    __android_log_print(ANDROID_LOG_INFO, g_LogTag, "OSMesaCreateContext OK ctx=%p", context);
    fprintf(stderr, "I/OSMBridge: OSMesaCreateContext OK ctx=%p\n", context);
    render_window->context = context;
    osm_set_dummy_buffer(&render_window->buffer);
    return render_window;
}

void osm_swap_surfaces(osm_render_window_t* bundle) {
    if (bundle == NULL) return;

    if (bundle->nativeSurface != NULL && bundle->newNativeSurface != bundle->nativeSurface) {
        ANativeWindow_release(bundle->nativeSurface);
        bundle->nativeSurface = NULL;
    }

    if (bundle->newNativeSurface != NULL) {
        __android_log_print(ANDROID_LOG_INFO, g_LogTag, "Switching to new native surface %p",
                            bundle->newNativeSurface);
        fprintf(stderr, "OSMDIAG: attaching native surface %p\n", bundle->newNativeSurface);
        bundle->nativeSurface = bundle->newNativeSurface;
        bundle->newNativeSurface = NULL;
        ANativeWindow_acquire(bundle->nativeSurface);
        ANativeWindow_setBuffersGeometry(bundle->nativeSurface, 0, 0, WINDOW_FORMAT_RGBA_8888);
        bundle->disable_rendering = false;

        int w = ANativeWindow_getWidth(bundle->nativeSurface);
        int h = ANativeWindow_getHeight(bundle->nativeSurface);
        if (w > 0) bridge_environ.savedWidth = w;
        if (h > 0) bridge_environ.savedHeight = h;
        return;
    }

    __android_log_print(ANDROID_LOG_WARN, g_LogTag, "No native surface — dummy framebuffer");
    fprintf(stderr, "OSMDIAG: no native surface (pojavWindow=%p) — rendering disabled\n",
            bridge_environ.pojavWindow);
    bundle->nativeSurface = NULL;
    bundle->disable_rendering = true;
    osm_set_dummy_buffer(&bundle->buffer);
}

void osm_release_window() {
    if (currentBundle == NULL) return;
    currentBundle->newNativeSurface = NULL;
    if (currentBundle->nativeSurface != NULL) {
        ANativeWindow_release(currentBundle->nativeSurface);
        currentBundle->nativeSurface = NULL;
    }
    currentBundle->disable_rendering = true;
    osm_set_dummy_buffer(&currentBundle->buffer);
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

    if (bundle->nativeSurface == NULL) {
        if (bundle->newNativeSurface == NULL && bridge_environ.pojavWindow != NULL)
            bundle->newNativeSurface = bridge_environ.pojavWindow;
        if (bundle->newNativeSurface != NULL)
            osm_swap_surfaces(bundle);
    }

    /* Bind whatever buffer we currently have (dummy until first successful lock in swap). */
    osm_apply_current();

    if (g_diag_swaps < 3)
        osm_diag_sample("make_current");
}

void osm_swap_buffers() {
    if (currentBundle == NULL) {
        if ((g_diag_swaps++ % 100) == 0)
            fprintf(stderr, "OSMDIAG: swap with NO current bundle\n");
        return;
    }

    if (currentBundle->state == STATE_RENDERER_NEW_WINDOW) {
        currentBundle->newNativeSurface = bridge_environ.pojavWindow;
        osm_swap_surfaces(currentBundle);
        currentBundle->state = STATE_RENDERER_ALIVE;
    }

    if (currentBundle->nativeSurface == NULL && bridge_environ.pojavWindow != NULL) {
        currentBundle->newNativeSurface = bridge_environ.pojavWindow;
        osm_swap_surfaces(currentBundle);
    }

    /*
     * Zalith-compatible present:
     *   lock → MakeCurrent(bits, stride) → glFinish → unlockAndPost
     * Game draw calls between swaps are flushed on the next glFinish into the
     * newly locked buffer.
     */
    if (currentBundle->nativeSurface != NULL && !currentBundle->disable_rendering) {
        ANativeWindow_Buffer nb;
        memset(&nb, 0, sizeof(nb));
        if (ANativeWindow_lock(currentBundle->nativeSurface, &nb, NULL) != 0) {
            __android_log_print(ANDROID_LOG_ERROR, g_LogTag, "ANativeWindow_lock failed");
            fprintf(stderr, "OSMDIAG: ANativeWindow_lock FAILED\n");
            /* Surface likely destroyed (pause/background). Disable until osm_setup_window. */
            currentBundle->disable_rendering = true;
            osm_set_dummy_buffer(&currentBundle->buffer);
            osm_apply_current();
            if (glFinish_p) glFinish_p();
            g_diag_swaps++;
            return;
        }
        currentBundle->buffer = nb;
        if ((g_diag_swaps % 120) == 0)
            fprintf(stderr, "OSMDIAG: locked dst=%dx%d stride=%d\n",
                    nb.width, nb.height, nb.stride);
    } else {
        osm_set_dummy_buffer(&currentBundle->buffer);
    }

    osm_apply_current();

    if (glFinish_p) glFinish_p();

    if (g_diag_swaps < 2 || (g_diag_swaps % 500) == 0)
        osm_diag_sample("swap");

    if (currentBundle->nativeSurface != NULL && !currentBundle->disable_rendering) {
        if (ANativeWindow_unlockAndPost(currentBundle->nativeSurface) != 0) {
            __android_log_print(ANDROID_LOG_ERROR, g_LogTag, "unlockAndPost failed");
            fprintf(stderr, "OSMDIAG: unlockAndPost FAILED\n");
        }
    }

    g_diag_swaps++;
}

void osm_setup_window() {
    if (bridge_environ.mainWindowBundle != NULL) {
        __android_log_print(ANDROID_LOG_INFO, g_LogTag,
            "setup_window: marking NEW_WINDOW, pojavWindow=%p",
            bridge_environ.pojavWindow);
        bridge_environ.mainWindowBundle->state = STATE_RENDERER_NEW_WINDOW;
        bridge_environ.mainWindowBundle->newNativeSurface = bridge_environ.pojavWindow;
        /* Re-enable rendering if we previously disabled after a lock failure */
        {
            osm_render_window_t* b = (osm_render_window_t*) bridge_environ.mainWindowBundle;
            b->disable_rendering = false;
        }
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
