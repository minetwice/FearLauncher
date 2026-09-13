//
// Ported from ZalithLauncher (ctxbridges/osmesa_loader.c)
// Loads Mesa library via LIB_MESA_NAME env var and resolves OSMesa symbols.
// CHANGED: does NOT abort if library/symbols not found — returns false so
// the caller can fall back to EGL.
//
#include <stdio.h>
#include <stdlib.h>
#include <dlfcn.h>
#include <string.h>
#include "osmesa_loader.h"
#include "bridge_environ.h"

GLboolean (*OSMesaMakeCurrent_p) (OSMesaContext ctx, void *buffer, GLenum type, GLsizei width, GLsizei height);
OSMesaContext (*OSMesaGetCurrentContext_p) (void);
OSMesaContext (*OSMesaCreateContext_p) (GLenum format, OSMesaContext sharelist);
void (*OSMesaDestroyContext_p) (OSMesaContext ctx);
void (*OSMesaFlushFrontbuffer_p) ();
void (*OSMesaPixelStore_p) (GLint pname, GLint value);
GLubyte* (*glGetString_p) (GLenum name);
void (*glFinish_p) (void);
void (*glClearColor_p) (GLclampf red, GLclampf green, GLclampf blue, GLclampf alpha);
void (*glClear_p) (GLbitfield mask);
void (*glReadPixels_p) (GLint x, GLint y, GLsizei width, GLsizei height, GLenum format, GLenum type, void* data);
void (*glReadBuffer_p) (GLenum mode);

static void* g_mesa_dl_handle = NULL;
static bool g_osmesa_loaded = false;
static bool g_tried = false;

bool is_renderer_vulkan() {
    return (bridge_environ.config_renderer == RENDERER_VK_ZINK);
}

char* construct_main_path(const char* mesa_name, const char* pojav_native_dir) {
    char* main_path = NULL;
    if (mesa_name != NULL && strncmp(mesa_name, "/data", 5) == 0) {
        main_path = strdup(mesa_name);
    } else {
        if (asprintf(&main_path, "%s/%s", pojav_native_dir, mesa_name) == -1) {
            return NULL;
        }
    }
    return main_path;
}

static void* OSMGetProcAddress(void* handle, const char* symbol_name) {
    void* sym = dlsym(handle, symbol_name);
    if (!sym) {
        char buf[256];
        snprintf(buf, sizeof(buf), "%sEXT", symbol_name);
        sym = dlsym(handle, buf);
    }
    return sym;
}

void* get_mesa_dl_handle() {
    return g_mesa_dl_handle;
}

bool osmesa_is_loaded() {
    return g_osmesa_loaded;
}

void dlsym_OSMesa() {
    if (g_tried) return; // only try once
    g_tried = true;

    if (!is_renderer_vulkan()) return;

    char* mesa_name = getenv("LIB_MESA_NAME");
    char* pojav_native_dir = getenv("POJAV_NATIVEDIR");
    if (!mesa_name || !pojav_native_dir) {
        fprintf(stderr, "OSMesa: LIB_MESA_NAME or POJAV_NATIVEDIR not set, skipping\n");
        return;
    }

    char* main_path = construct_main_path(mesa_name, pojav_native_dir);
    if (!main_path) {
        fprintf(stderr, "OSMesa: Failed to construct path\n");
        return;
    }

    g_mesa_dl_handle = dlopen(main_path, RTLD_LOCAL | RTLD_LAZY);
    free(main_path);
    if (!g_mesa_dl_handle) {
        fprintf(stderr, "OSMesa: Failed to open library %s: %s (will use EGL fallback)\n", mesa_name, dlerror());
        return;
    }

    OSMesaMakeCurrent_p = OSMGetProcAddress(g_mesa_dl_handle, "OSMesaMakeCurrent");
    OSMesaCreateContext_p = OSMGetProcAddress(g_mesa_dl_handle, "OSMesaCreateContext");
    OSMesaGetCurrentContext_p = OSMGetProcAddress(g_mesa_dl_handle, "OSMesaGetCurrentContext");
    OSMesaDestroyContext_p = OSMGetProcAddress(g_mesa_dl_handle, "OSMesaDestroyContext");
    OSMesaPixelStore_p = OSMGetProcAddress(g_mesa_dl_handle, "OSMesaPixelStore");
    glFinish_p = OSMGetProcAddress(g_mesa_dl_handle, "glFinish");
    glGetString_p = OSMGetProcAddress(g_mesa_dl_handle, "glGetString");

    // Only consider OSMesa loaded if we have the critical functions
    if (OSMesaMakeCurrent_p && OSMesaCreateContext_p && OSMesaPixelStore_p && glFinish_p) {
        g_osmesa_loaded = true;
        printf("OSMesa: symbols loaded successfully from %s\n", mesa_name);
    } else {
        printf("OSMesa: %s does not have OSMesa symbols — will use EGL fallback\n", mesa_name);
        // Keep g_mesa_dl_handle for GL symbol resolution even without OSMesa
    }
}
