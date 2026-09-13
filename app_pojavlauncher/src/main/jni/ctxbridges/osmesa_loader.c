//
// Ported from ZalithLauncher (ctxbridges/osmesa_loader.c)
// Loads Mesa library via LIB_MESA_NAME and resolves OSMesa + GL symbols.
//
#include <stdio.h>
#include <stdlib.h>
#include <dlfcn.h>
#include <string.h>
#include <android/log.h>
#include "osmesa_loader.h"
#include "bridge_environ.h"

#define LOG_TAG "OSMesaLoader"
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

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

// OSMesaGetProcAddress — preferred path for GL symbols inside Zink/OSMesa
void* (*OSMesaGetProcAddress_p) (const char* funcName) = NULL;

static void* g_mesa_dl_handle = NULL;
static bool g_osmesa_loaded = false;
static bool g_tried = false;

bool is_renderer_vulkan() {
    return (bridge_environ.config_renderer == RENDERER_VK_ZINK);
}

char* construct_main_path(const char* mesa_name, const char* pojav_native_dir) {
    char* main_path = NULL;
    if (mesa_name != NULL && mesa_name[0] == '/') {
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
    // Allow retry if previous attempt had no env yet
    if (g_osmesa_loaded) return;

    char* mesa_name = getenv("LIB_MESA_NAME");
    char* pojav_native_dir = getenv("POJAV_NATIVEDIR");
    if (!mesa_name || !pojav_native_dir) {
        if (!g_tried) {
            ALOGE("LIB_MESA_NAME or POJAV_NATIVEDIR not set, skipping");
        }
        g_tried = true;
        return;
    }

    // Already have a handle from a partial load?
    if (g_mesa_dl_handle == NULL) {
        char* main_path = construct_main_path(mesa_name, pojav_native_dir);
        if (!main_path) {
            ALOGE("Failed to construct path");
            g_tried = true;
            return;
        }
        ALOGI("dlopen %s", main_path);
        g_mesa_dl_handle = dlopen(main_path, RTLD_LOCAL | RTLD_LAZY);
        if (!g_mesa_dl_handle) {
            // Try bare name (linker search path / namespace)
            g_mesa_dl_handle = dlopen(mesa_name, RTLD_LOCAL | RTLD_LAZY);
        }
        free(main_path);
        if (!g_mesa_dl_handle) {
            ALOGE("Failed to open %s: %s", mesa_name, dlerror());
            g_tried = true;
            return;
        }
    }

    OSMesaMakeCurrent_p = OSMGetProcAddress(g_mesa_dl_handle, "OSMesaMakeCurrent");
    OSMesaCreateContext_p = OSMGetProcAddress(g_mesa_dl_handle, "OSMesaCreateContext");
    OSMesaGetCurrentContext_p = OSMGetProcAddress(g_mesa_dl_handle, "OSMesaGetCurrentContext");
    OSMesaDestroyContext_p = OSMGetProcAddress(g_mesa_dl_handle, "OSMesaDestroyContext");
    OSMesaPixelStore_p = OSMGetProcAddress(g_mesa_dl_handle, "OSMesaPixelStore");
    OSMesaGetProcAddress_p = OSMGetProcAddress(g_mesa_dl_handle, "OSMesaGetProcAddress");
    glFinish_p = OSMGetProcAddress(g_mesa_dl_handle, "glFinish");
    glGetString_p = OSMGetProcAddress(g_mesa_dl_handle, "glGetString");

    // Prefer OSMesaGetProcAddress for GL entry points when direct dlsym missed
    if (OSMesaGetProcAddress_p) {
        if (!glGetString_p) glGetString_p = (void*)OSMesaGetProcAddress_p("glGetString");
        if (!glFinish_p) glFinish_p = (void*)OSMesaGetProcAddress_p("glFinish");
    }

    if (OSMesaMakeCurrent_p && OSMesaCreateContext_p && OSMesaPixelStore_p) {
        g_osmesa_loaded = true;
        ALOGI("symbols loaded successfully from %s (GetProcAddress=%p glGetString=%p)",
              mesa_name, (void*)OSMesaGetProcAddress_p, (void*)glGetString_p);
        printf("OSMesa: symbols loaded successfully from %s\n", mesa_name);
    } else {
        ALOGE("%s missing OSMesa symbols (MakeCurrent=%p Create=%p PixelStore=%p)",
              mesa_name, (void*)OSMesaMakeCurrent_p, (void*)OSMesaCreateContext_p, (void*)OSMesaPixelStore_p);
        printf("OSMesa: %s does not have OSMesa symbols — will use EGL fallback\n", mesa_name);
    }
    g_tried = true;
}
