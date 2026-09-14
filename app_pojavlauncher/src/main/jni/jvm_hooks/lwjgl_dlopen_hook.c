//
// FearLauncher — LWJGL dlopen/dlsym hook v2.12 (TURNIP-ZINK) - intermediate full hybrid
// Hybrid: real libglfw for window/input/pollEvents; OSMesa for GL context
//
#include "jvm_hooks.h"

#include <android/api-level.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <dlfcn.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <pthread.h>
#include <stdbool.h>
#include <stdint.h>

#define TAG __FILE_NAME__
#include <log.h>
#include "../pojavexec.h"
#include "ctxbridges/bridge_environ.h"
#include "ctxbridges/osm_bridge.h"
#include "ctxbridges/osmesa_loader.h"

bridge_environ_t bridge_environ = {0};

static int g_is_zink_cached = -1;

static bool is_zink_renderer() {
    if (g_is_zink_cached >= 0) return g_is_zink_cached == 1;
    const char* fear = getenv("FEAR_RENDERER");
    const char* gallium = getenv("GALLIUM_DRIVER");
    const char* renderer = getenv("POJAV_RENDERER");
    bool z = false;
    if (fear && (strcmp(fear, "turnip_zink") == 0 || strcmp(fear, "vulkan_zink") == 0))
        z = true;
    else if (gallium && strcmp(gallium, "zink") == 0)
        z = true;
    else if (renderer && (strcmp(renderer, "turnip_zink") == 0 || strcmp(renderer, "vulkan_zink") == 0))
        z = true;
    g_is_zink_cached = z ? 1 : 0;
    return z;
}

static void hide_pojav_from_sodium(void) {
    unsetenv("POJAV_RENDERER");
    unsetenv("POJAV_LAUNCHER");
    printf("LWJGL hook v2.12: unset POJAV_RENDERER/POJAV_LAUNCHER (Sodium bypass)\n");
}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_setupBridgeWindow(JNIEnv* env, jclass clazz, jobject surface) {
    if (surface == NULL) return;
    bridge_environ.pojavWindow = ANativeWindow_fromSurface(env, surface);
    bridge_environ.savedWidth = ANativeWindow_getWidth(bridge_environ.pojavWindow);
    bridge_environ.savedHeight = ANativeWindow_getHeight(bridge_environ.pojavWindow);
    LOGI("Bridge window set: %p (%dx%d)", bridge_environ.pojavWindow,
         bridge_environ.savedWidth, bridge_environ.savedHeight);
    if (osmesa_is_loaded()) osm_setup_window();
}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_releaseBridgeWindow(JNIEnv* env, jclass clazz) {
    if (bridge_environ.pojavWindow != NULL) {
        ANativeWindow_release(bridge_environ.pojavWindow);
        bridge_environ.pojavWindow = NULL;
    }
}

static void* g_libglfw = NULL;
static void* g_libglfw_window = NULL;

static void* load_libglfw(void) {
    if (g_libglfw) return g_libglfw;
    g_libglfw = dlopen("libglfw.so", RTLD_NOW | RTLD_GLOBAL);
    if (!g_libglfw) {
        const char* nd = getenv("POJAV_NATIVEDIR");
        if (nd && nd[0]) {
            char path[512];
            snprintf(path, sizeof(path), "%s/libglfw.so", nd);
            g_libglfw = dlopen(path, RTLD_NOW | RTLD_GLOBAL);
        }
    }
    if (!g_libglfw)
        printf("LWJGL hook v2.12: libglfw.so NOT found\n");
    else
        printf("LWJGL hook v2.12: libglfw.so loaded %p\n", g_libglfw);
    return g_libglfw;
}

static void* glfw_real(const char* name) {
    void* lib = load_libglfw();
    if (!lib) return NULL;
    return dlsym(lib, name);
}

static void* get_mesa_dl_handle(void) {
    void* h = dlopen("libOSMesa_8.so", RTLD_NOLOAD | RTLD_NOW);
    if (!h) h = dlopen("libOSMesa.so", RTLD_NOLOAD | RTLD_NOW);
    if (!h) {
        const char* name = getenv("LIB_MESA_NAME");
        if (name && name[0]) h = dlopen(name, RTLD_NOW | RTLD_GLOBAL);
    }
    if (!h) h = dlopen("libOSMesa_8.so", RTLD_NOW | RTLD_GLOBAL);
    return h;
}

static void* hooked_glfwCreateWindow_impl(int width, int height, const char* title, void* monitor, void* share) {
    printf("LWJGL hook v2.12: glfwCreateWindow %dx%d (zink=%d)\n", width, height, is_zink_renderer());
    typedef void* (*create_fn)(int, int, const char*, void*, void*);
    create_fn real_create = (create_fn) glfw_real("glfwCreateWindow");
    if (real_create) {
        g_libglfw_window = real_create(width, height, title, monitor, share);
        printf("LWJGL hook v2.12: real glfwCreateWindow -> %p\n", g_libglfw_window);
        return g_libglfw_window;
    }
    printf("LWJGL hook v2.12: real glfwCreateWindow missing\n");
    return NULL;
}

static jlong ndlopen_bugfix(__attribute__((unused)) JNIEnv *env, __attribute__((unused)) jclass clazz, jlong filename, jint mode) {
    const char* name = (const char*)(uintptr_t)filename;
    if (name) {
        printf("LWJGL hook ndlopen: %s mode=%d\n", name, mode);
        if (strstr(name, "libglfw") || strstr(name, "OSMesa") || strstr(name, "libGL")) {
            void* h = dlopen(name, mode);
            if (h) return (jlong)(uintptr_t)h;
        }
    }
    void* h = dlopen(name, mode);
    return (jlong)(uintptr_t)h;
}

static jlong ndlsym_hook(__attribute__((unused)) JNIEnv *env, __attribute__((unused)) jclass clazz, jlong handle, jlong symbol_ptr) {
    const char* symbol = (const char*)(uintptr_t)symbol_ptr;
    if (!symbol) return 0;

    if (is_zink_renderer()) {
        if (strcmp(symbol, "eglGetProcAddress") == 0 || strcmp(symbol, "glXGetProcAddress") == 0 ||
            strcmp(symbol, "glXGetProcAddressARB") == 0) {
            void* mesa = get_mesa_dl_handle();
            if (mesa) {
                void* (*osm_get_proc)(const char*) = dlsym(mesa, "OSMesaGetProcAddress");
                if (osm_get_proc) {
                    printf("LWJGL hook: %s -> OSMesaGetProcAddress\n", symbol);
                    return (jlong)(uintptr_t)osm_get_proc;
                }
            }
        }

        if (strncmp(symbol, "gl", 2) == 0 || strncmp(symbol, "egl", 3) == 0) {
            void* mesa = get_mesa_dl_handle();
            if (mesa) {
                void* (*osm_get_proc)(const char*) = dlsym(mesa, "OSMesaGetProcAddress");
                if (osm_get_proc) {
                    void* sym = osm_get_proc(symbol);
                    if (sym) return (jlong)(uintptr_t)sym;
                }
                void* sym = dlsym(mesa, symbol);
                if (sym) return (jlong)(uintptr_t)sym;
            }
        }

        if (strcmp(symbol, "glfwCreateWindow") == 0) {
            return (jlong)(uintptr_t)hooked_glfwCreateWindow_impl;
        }
        if (strncmp(symbol, "glfw", 4) == 0) {
            void* real = glfw_real(symbol);
            if (real) return (jlong)(uintptr_t)real;
        }
    }

    void* sym = dlsym((void*)(uintptr_t)handle, symbol);
    if (!sym) sym = dlsym(RTLD_DEFAULT, symbol);
    return (jlong)(uintptr_t)sym;
}

void installLwjglDlopenHook(JNIEnv *env) {
    LOGI("Installing LWJGL hooks (TURNIP-ZINK v2.12)");
    printf("LWJGL hook: installing hooks (TURNIP-ZINK v2.12)\n");

    if (is_zink_renderer()) {
        hide_pojav_from_sodium();
    }

    jclass dynamicLinkLoader = (*env)->FindClass(env, "org/lwjgl/system/linux/DynamicLinkLoader");
    if (dynamicLinkLoader == NULL) {
        LOGE("Failed to find DynamicLinkLoader");
        (*env)->ExceptionClear(env);
        return;
    }
    JNINativeMethod hooks[] = {
            {"ndlopen", "(JI)J", (void*)&ndlopen_bugfix},
            {"ndlsym",  "(JJ)J", (void*)&ndlsym_hook}
    };
    if ((*env)->RegisterNatives(env, dynamicLinkLoader, hooks, 2) != 0) {
        LOGE("Failed to register hooks");
        (*env)->ExceptionClear(env);
    } else {
        printf("LWJGL hook: hooks installed (TURNIP-ZINK v2.12 - hybrid)\n");
    }
}
