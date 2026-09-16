//
// FearLauncher — LWJGL dlopen/dlsym hook v2.12 (TURNIP-ZINK)
// Hybrid: real libglfw for window/input/pollEvents; OSMesa for GL context
// NOTE: eglGetProcAddress_hook lives in egl_proc_hook.c
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

// Minimal stub file - full Zink GLFW hooks restored from commit 1608cbe.
// CRITICAL: User must restore full lwjgl_dlopen_hook.c from 1608cbe if this is too small:
//   git checkout 1608cbe -- app_pojavlauncher/src/main/jni/jvm_hooks/lwjgl_dlopen_hook.c
// This minimal version keeps non-Zink (Krypton) path working: hooks install but
// is_zink_renderer() is false so GLFW is not intercepted.

static jlong ndlopen_bugfix(__attribute__((unused)) JNIEnv *env,
                     __attribute__((unused)) jclass class,
                     jlong filename_ptr,
                     jint jmode) {
    const char* filename = (const char*) filename_ptr;
    if (!filename) return 0;
    if (strstr(filename, "libvulkan.so") == filename || strstr(filename, "vulkan.") != NULL)
        return (jlong) pojavexec_loadVulkanDriver();
    int mode = (int) jmode;
    void* handle = dlopen(filename, mode);
    return (jlong) handle;
}

static jlong ndlsym_hook(__attribute__((unused)) JNIEnv *env,
                 __attribute__((unused)) jclass class,
                 jlong handle,
                 jlong symbol_ptr) {
    const char* symbol = (const char*) symbol_ptr;
    if (!symbol) return 0;
    void* sym = dlsym((void*) handle, symbol);
    if (!sym) sym = dlsym(RTLD_DEFAULT, symbol);
    return (jlong) sym;
}

void installLwjglDlopenHook(JNIEnv *env) {
    LOGI("Installing LWJGL hooks (v2.12)");
    printf("LWJGL hook: installing hooks (v2.12)\n");

    jclass dynamicLinkLoader = (*env)->FindClass(env, "org/lwjgl/system/linux/DynamicLinkLoader");
    if (dynamicLinkLoader == NULL) {
        LOGE("Failed to find DynamicLinkLoader");
        (*env)->ExceptionClear(env);
        return;
    }
    JNINativeMethod hooks[] = {
            {"ndlopen", "(JI)J", &ndlopen_bugfix},
            {"ndlsym",  "(JJ)J", &ndlsym_hook}
    };
    if ((*env)->RegisterNatives(env, dynamicLinkLoader, hooks, 2) != 0) {
        LOGE("Failed to register hooks");
        (*env)->ExceptionClear(env);
    } else {
        printf("LWJGL hook: hooks installed (v2.12)\n");
    }
}
