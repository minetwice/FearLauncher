//
// FearLauncher — LWJGL dlopen/dlsym hook v2.12 (TURNIP-ZINK)
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

// Forward declarations for the full implementation
static jlong ndlopen_bugfix(JNIEnv *env, jclass clazz, jlong filename, jint mode);
static jlong ndlsym_hook(JNIEnv *env, jclass clazz, jlong handle, jlong symbol);

void installLwjglDlopenHook(JNIEnv *env) {
    LOGI("Installing LWJGL hooks (TURNIP-ZINK v2.12)");
    printf("LWJGL hook: installing hooks (TURNIP-ZINK v2.12)\n");

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
        printf("LWJGL hook: hooks installed (TURNIP-ZINK v2.12)\n");
    }
}

// Minimal stub implementations to avoid linker errors while full body is restored
static jlong ndlopen_bugfix(JNIEnv *env, jclass clazz, jlong filename, jint mode) {
    const char* name = (const char*)(uintptr_t)filename;
    if (name) printf("LWJGL hook ndlopen: %s\n", name);
    void* h = dlopen(name, mode);
    return (jlong)(uintptr_t)h;
}

static jlong ndlsym_hook(JNIEnv *env, jclass clazz, jlong handle, jlong symbol) {
    const char* sym = (const char*)(uintptr_t)symbol;
    void* s = dlsym((void*)(uintptr_t)handle, sym);
    if (!s) s = dlsym(RTLD_DEFAULT, sym);
    return (jlong)(uintptr_t)s;
}
