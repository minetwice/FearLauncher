//
// FearLauncher OSMesa bridge — persistent CPU frontbuffer + ANativeWindow blit.
//
// Why the pure "lock → MakeCurrent(window bits) → unlock" path failed on Panfork:
//   - make_current bound a 1x1 dummy buffer for the whole load/title phase
//   - after unlockAndPost, window bits are invalid; GL context went missing
//     (log: "Cannot query extension without a current OpenGL context")
//   - OSMDIAG center/tl/br stayed 0x0; unlockAndPost then lock FAILED
//
// This path:
//   1) Allocate a page-aligned full-size CPU color_buffer as soon as the surface exists
//   2) Always OSMesaMakeCurrent(color_buffer) so context stays valid between frames
//   3) On swap: glFinish + glReadPixels (force GPU→CPU) → lock window → memcpy → unlock
//   4) Re-bind color_buffer after present
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

static unsigned g_diag_swaps = 0;
static void* g_pReadPixels = NULL;
static void* g_pGetError = NULL;
static int g_readback_flipped = 1; /* GL is bottom-up; Android buffer is top-down */

static void osm_resolve_gl_syms(void) {
    void* h = get_mesa_dl_handle();
    if (h == NULL) return;
    if (g_pReadPixels == NULL) {
        g_pReadPixels = dlsym(h, "glReadPixels");
        fprintf(stderr, "OSMDIAG: glReadPixels = %p\n", g_pReadPixels);
    }
    if (g_pGetError == NULL)
        g_pGetError = dlsym(h, "glGetError");
}

/** Page-aligned, 64-pixel row padding for Panfrost tiling friendliness. */
static int osm_ensure_color_buffer(osm_render_window_t* bundle, int width, int height) {
    if (bundle == NULL || width <= 0 || height <= 0) return -1;

    /* Round row to multiple of 16 pixels (64 bytes) for Mali/Panfrost */
    int row_pixels = (width + 15) & ~15;

    if (bundle->color_buffer != NULL
        && bundle->color_width == width
        && bundle->color_height == height
        && bundle->color_row_pixels == row_pixels) {
        return 0;
    }

    if (bundle->color_buffer != NULL) {
        free(bundle->color_buffer);
        bundle->color_buffer = NULL;
    }

    size_t bytes = (size_t)row_pixels * (size_t)height * 4u;
    void* mem = NULL;
#if defined(_POSIX_C_SOURCE) || defined(__ANDROID__)
    if (posix_memalign(&mem, 4096, bytes) != 0) mem = NULL;
#else
    mem = malloc(bytes);
#endif
    if (mem == NULL) {
        mem = malloc(bytes);
    }
    if (mem == NULL) {
        bundle->color_width = 0;
        bundle->color_height = 0;
        bundle->color_row_pixels = 0;
        __android_log_print(ANDROID_LOG_ERROR, g_LogTag,
            "malloc color buffer %dx%d (row=%d) failed", width, height, row_pixels);
        return -1;
    }
    memset(mem, 0, bytes);
    bundle->color_buffer = mem;
    bundle->color_width = width;
    bundle->color_height = height;
    bundle->color_row_pixels = row_pixels;
    __android_log_print(ANDROID_LOG_INFO, g_LogTag,
        "Allocated color buffer %dx%d row=%d (%zu bytes) at %p",
        width, height, row_pixels, bytes, mem);
    fprintf(stderr, "OSMDIAG: color_buffer %dx%d row=%d ptr=%p\n",
            width, height, row_pixels, mem);
    return 0;
}

static int osm_bind_color(osm_render_window_t* bundle) {
    if (bundle == NULL || bundle->context == NULL || OSMesaMakeCurrent_p == NULL)
        return -1;
    if (bundle->color_buffer == NULL || bundle->color_width <= 0 || bundle->color_height <= 0)
        return -1;

    GLboolean ok = OSMesaMakeCurrent_p(
        bundle->context,
        bundle->color_buffer,
        GL_UNSIGNED_BYTE,
        bundle->color_width,
        bundle->color_height);

    if (OSMesaPixelStore_p) {
        OSMesaPixelStore_p(OSMESA_ROW_LENGTH, bundle->color_row_pixels);
        OSMesaPixelStore_p(OSMESA_Y_UP, 0); /* top-down to match Android */
    }
    bundle->last_stride = bundle->color_row_pixels;

    if (!ok) {
        __android_log_print(ANDROID_LOG_ERROR, g_LogTag, "OSMesaMakeCurrent FAILED");
        fprintf(stderr, "OSMDIAG: OSMesaMakeCurrent FAILED %dx%d\n",
                bundle->color_width, bundle->color_height);
        return -1;
    }
    return 0;
}

static void osm_diag_sample(const char* where) {
    osm_render_window_t* b = currentBundle;
    if (b == NULL) {
        fprintf(stderr, "OSMDIAG[%s]: NO current bundle\n", where);
        return;
    }
    unsigned long c = 0, tl = 0, br = 0;
    if (b->color_buffer != NULL && b->color_width > 1 && b->color_height > 1) {
        const uint32_t* px = (const uint32_t*) b->color_buffer;
        int w = b->color_width;
        int h = b->color_height;
        int s = b->color_row_pixels > 0 ? b->color_row_pixels : w;
        c  = px[(size_t)(h / 2) * (size_t)s + (size_t)(w / 2)];
        tl = px[0];
        br = px[(size_t)(h - 1) * (size_t)s + (size_t)(w - 1)];
    }
    fprintf(stderr, "OSMDIAG[%s]: buf=%p %dx%d row=%d surf=%p disable=%d state=%d "
            "center=0x%08lx tl=0x%08lx br=0x%08lx\n",
            where, b->color_buffer, b->color_width, b->color_height, b->color_row_pixels,
            b->nativeSurface, (int)b->disable_rendering, (int)b->state, c, tl, br);
}

static void osm_force_readback(osm_render_window_t* b) {
    void (*pReadPixels)(int, int, int, int, unsigned, unsigned, void*);
    if (b == NULL || b->color_buffer == NULL) return;
    osm_resolve_gl_syms();
    if (g_pReadPixels == NULL) return;
    memcpy(&pReadPixels, &g_pReadPixels, sizeof(g_pReadPixels));
    /* GL_RGBA = 0x1908, GL_UNSIGNED_BYTE = 0x1401 */
    pReadPixels(0, 0, b->color_width, b->color_height, 0x1908u, 0x1401u, b->color_buffer);
    g_readback_flipped = 1; /* glReadPixels is bottom-up */
}

static void osm_blit_to_native(osm_render_window_t* bundle) {
    if (bundle == NULL || bundle->nativeSurface == NULL || bundle->color_buffer == NULL)
        return;
    if (bundle->disable_rendering) return;

    ANativeWindow_Buffer nb;
    memset(&nb, 0, sizeof(nb));
    if (ANativeWindow_lock(bundle->nativeSurface, &nb, NULL) != 0) {
        __android_log_print(ANDROID_LOG_ERROR, g_LogTag, "ANativeWindow_lock failed");
        fprintf(stderr, "OSMDIAG: ANativeWindow_lock FAILED\n");
        return;
    }

    if ((g_diag_swaps % 120) == 0)
        fprintf(stderr, "OSMDIAG: blit locked dst=%dx%d stride=%d src=%dx%d flipped=%d\n",
                nb.width, nb.height, nb.stride,
                bundle->color_width, bundle->color_height, g_readback_flipped);

    const int src_w = bundle->color_width;
    const int src_h = bundle->color_height;
    const int src_row = bundle->color_row_pixels > 0 ? bundle->color_row_pixels : src_w;
    const int dst_w = nb.width;
    const int dst_h = nb.height;
    const int copy_w = src_w < dst_w ? src_w : dst_w;
    const int copy_h = src_h < dst_h ? src_h : dst_h;
    const uint8_t* src = (const uint8_t*) bundle->color_buffer;
    uint8_t* dst = (uint8_t*) nb.bits;
    const int src_stride_bytes = src_row * 4;
    const int dst_stride_bytes = nb.stride * 4;

    if (dst != NULL && copy_w > 0 && copy_h > 0) {
        for (int y = 0; y < copy_h; y++) {
            int sy = g_readback_flipped ? (copy_h - 1 - y) : y;
            memcpy(dst + (size_t)y * dst_stride_bytes,
                   src + (size_t)sy * src_stride_bytes,
                   (size_t)copy_w * 4u);
        }
    }

    if (ANativeWindow_unlockAndPost(bundle->nativeSurface) != 0) {
        __android_log_print(ANDROID_LOG_ERROR, g_LogTag, "unlockAndPost failed");
        fprintf(stderr, "OSMDIAG: unlockAndPost FAILED\n");
    }
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

bool osm_init() {
    dlsym_OSMesa();
    if (!osmesa_is_loaded()) {
        __android_log_print(ANDROID_LOG_ERROR, g_LogTag, "OSMesa symbols not loaded");
        return false;
    }
    __android_log_print(ANDROID_LOG_INFO, g_LogTag,
        "OSMesa bridge init OK (persistent CPU frontbuffer + blit)");
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
        /* RGBX matches Zalith; some devices reject RGBA for lock */
        ANativeWindow_setBuffersGeometry(bundle->nativeSurface, 0, 0, WINDOW_FORMAT_RGBX_8888);
        bundle->disable_rendering = false;

        int w = ANativeWindow_getWidth(bundle->nativeSurface);
        int h = ANativeWindow_getHeight(bundle->nativeSurface);
        if (w > 0) bridge_environ.savedWidth = w;
        if (h > 0) bridge_environ.savedHeight = h;
        if (w <= 0 || h <= 0) osm_resolve_size(&w, &h);

        if (osm_ensure_color_buffer(bundle, w, h) == 0)
            osm_bind_color(bundle);
        return;
    }

    __android_log_print(ANDROID_LOG_WARN, g_LogTag, "No native surface — color buffer only");
    fprintf(stderr, "OSMDIAG: no native surface (pojavWindow=%p)\n", bridge_environ.pojavWindow);
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
    if (currentBundle->nativeSurface != NULL) {
        ANativeWindow_release(currentBundle->nativeSurface);
        currentBundle->nativeSurface = NULL;
    }
    currentBundle->disable_rendering = true;
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

    /* Ensure full-size buffer even if surface attach path did not run yet */
    if (bundle->color_buffer == NULL) {
        int w, h;
        osm_resolve_size(&w, &h);
        osm_ensure_color_buffer(bundle, w, h);
    }

    osm_bind_color(bundle);

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

    /* Keep context on stable CPU buffer for the whole frame */
    if (currentBundle->color_buffer == NULL) {
        int w, h;
        osm_resolve_size(&w, &h);
        osm_ensure_color_buffer(currentBundle, w, h);
    }
    osm_bind_color(currentBundle);

    if (glFinish_p) glFinish_p();

    /* Force GPU → CPU if the frontbuffer mapping stayed empty (Panfrost HW path) */
    {
        const uint32_t* px = (const uint32_t*) currentBundle->color_buffer;
        int s = currentBundle->color_row_pixels > 0 ? currentBundle->color_row_pixels
                                                    : currentBundle->color_width;
        uint32_t sample = 0;
        if (px != NULL && currentBundle->color_width > 0 && currentBundle->color_height > 0)
            sample = px[(size_t)(currentBundle->color_height / 2) * (size_t)s
                        + (size_t)(currentBundle->color_width / 2)];
        if (sample == 0)
            osm_force_readback(currentBundle);
        else
            g_readback_flipped = 0; /* direct frontbuffer write, Y_UP=0 already top-down */
    }

    if (g_diag_swaps < 3 || (g_diag_swaps % 300) == 0)
        osm_diag_sample("swap");

    osm_blit_to_native(currentBundle);

    /* Critical: re-bind CPU buffer so the next frame has a current context */
    osm_bind_color(currentBundle);

    g_diag_swaps++;
}

void osm_setup_window() {
    if (bridge_environ.mainWindowBundle != NULL) {
        __android_log_print(ANDROID_LOG_INFO, g_LogTag,
            "setup_window: marking NEW_WINDOW, pojavWindow=%p",
            bridge_environ.pojavWindow);
        bridge_environ.mainWindowBundle->state = STATE_RENDERER_NEW_WINDOW;
        bridge_environ.mainWindowBundle->newNativeSurface = bridge_environ.pojavWindow;
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
