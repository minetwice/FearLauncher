//
// FearLauncher — LWJGL dlopen/dlsym hook v2.13
// Non-Zink (Krypton): pass-through dlopen + route missing GL symbols via gl4es_GetProcAddress
// Zink: eglBindAPI hook so GLFW accepts desktop OpenGL (OSMesa provides GL)
//
#include "jvm_hooks.h"

#include <android/api-level.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <dlfcn.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <stdbool.h>
#include <stdint.h>

#define TAG __FILE_NAME__
#include <log.h>
#include "../pojavexec.h"
#include "ctxbridges/bridge_environ.h"
#include "ctxbridges/osm_bridge.h"
#include "ctxbridges/osmesa_loader.h"

bridge_environ_t bridge_environ = {0};

/* from egl_proc_hook.c */
extern void* eglGetProcAddress_hook(const char* procname);
extern int eglBindAPI_hook(int api);

static void* g_ng_handle = NULL;
static void* (*g_gl4es_getproc)(const char*) = NULL;

static void ensure_ng_gl4es(void) {
    if (g_ng_handle) return;
    const char* fear = getenv("FEAR_RENDERER");
    if (!fear || strcmp(fear, "ng_gl4es") != 0) return;
    const char* nd = getenv("POJAV_NATIVEDIR");
    if (nd && nd[0]) {
        char path[512];
        snprintf(path, sizeof(path), "%s/libng_gl4es.so", nd);
        g_ng_handle = dlopen(path, RTLD_NOW | RTLD_GLOBAL);
    }
    if (!g_ng_handle) g_ng_handle = dlopen("libng_gl4es.so", RTLD_NOW | RTLD_GLOBAL);
    if (g_ng_handle) {
        g_gl4es_getproc = (void*(*)(const char*))dlsym(g_ng_handle, "gl4es_GetProcAddress");
        if (!g_gl4es_getproc)
            g_gl4es_getproc = (void*(*)(const char*))dlsym(g_ng_handle, "glXGetProcAddress");
        printf("LWJGL hook v2.13: ng_gl4es=%p getproc=%p\n", g_ng_handle, (void*)g_gl4es_getproc);
    } else {
        printf("LWJGL hook v2.13: ng_gl4es load FAILED: %s\n", dlerror());
    }
}

static void* resolve_gl_symbol(const char* symbol) {
    ensure_ng_gl4es();
    if (g_gl4es_getproc) {
        void* s = g_gl4es_getproc(symbol);
        if (s) return s;
    }
    if (g_ng_handle) {
        void* s = dlsym(g_ng_handle, symbol);
        if (s) return s;
    }
    return eglGetProcAddress_hook(symbol);
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

static jlong ndlopen_bugfix(__attribute__((unused)) JNIEnv *env,
                     __attribute__((unused)) jclass class,
                     jlong filename_ptr,
                     jint jmode) {
    const char* filename = (const char*) filename_ptr;
    if (!filename) return 0;
    if (strstr(filename, "libvulkan.so") == filename || strstr(filename, "vulkan.") != NULL)
        return (jlong) pojavexec_loadVulkanDriver();
    /* Prefer GLOBAL so GL symbols resolve process-wide */
    int mode = (int) jmode;
    if (strstr(filename, "ng_gl4es") || strstr(filename, "gl4es"))
        mode |= RTLD_GLOBAL;
    void* handle = dlopen(filename, mode);
    if (handle && (strstr(filename, "ng_gl4es") || strstr(filename, "gl4es"))) {
        g_ng_handle = handle;
        g_gl4es_getproc = (void*(*)(const char*))dlsym(handle, "gl4es_GetProcAddress");
        if (!g_gl4es_getproc)
            g_gl4es_getproc = (void*(*)(const char*))dlsym(handle, "glXGetProcAddress");
        printf("LWJGL hook v2.13: dlopen %s -> %p getproc=%p\n", filename, handle, (void*)g_gl4es_getproc);
    }
    return (jlong) handle;
}

static int is_gl_symbol(const char* symbol) {
    /* glfw* and glX* must NOT be treated as OpenGL entry points */
    if (strncmp(symbol, "glfw", 4) == 0 || strncmp(symbol, "GLFW", 4) == 0)
        return 0;
    if (strncmp(symbol, "glX", 3) == 0 || strncmp(symbol, "GLX", 3) == 0)
        return 0;
    if (strncmp(symbol, "egl", 3) == 0 || strncmp(symbol, "EGL", 3) == 0)
        return 0;
    return (strncmp(symbol, "gl", 2) == 0 || strncmp(symbol, "GL", 2) == 0);
}

static jlong ndlsym_hook(__attribute__((unused)) JNIEnv *env,
                 __attribute__((unused)) jclass class,
                 jlong handle,
                 jlong symbol_ptr) {
    const char* symbol = (const char*) symbol_ptr;
    if (!symbol) return 0;

    /* Only intercept real GL calls when Krypton (ng_gl4es) is active */
    if (is_gl_symbol(symbol)) {
        const char* fear = getenv("FEAR_RENDERER");
        if (fear && strcmp(fear, "ng_gl4es") == 0) {
            void* glsym = resolve_gl_symbol(symbol);
            if (glsym) {
                return (jlong) glsym;
            }
            printf("LWJGL hook v2.13: GL symbol MISS %s\n", symbol);
        }
    }

    /* eglGetProcAddress: only force hook for Krypton; otherwise use real symbol */
    if (strcmp(symbol, "eglGetProcAddress") == 0) {
        const char* fear = getenv("FEAR_RENDERER");
        if (fear && strcmp(fear, "ng_gl4es") == 0)
            return (jlong) eglGetProcAddress_hook;
        void* real = dlsym((void*) handle, symbol);
        if (!real) real = dlsym(RTLD_DEFAULT, symbol);
        if (!real) {
            void* egl = dlopen("libEGL.so", RTLD_NOW | RTLD_NOLOAD);
            if (!egl) egl = dlopen("libEGL.so", RTLD_NOW);
            if (!egl) egl = dlopen("/system/lib64/libEGL.so", RTLD_NOW);
            if (egl) real = dlsym(egl, "eglGetProcAddress");
        }
        return (jlong) real;
    }

    void* sym = dlsym((void*) handle, symbol);
    if (!sym) sym = dlsym(RTLD_DEFAULT, symbol);
    return (jlong) sym;
}

static void try_install_egl_bytehook(void) {
    const char* fear = getenv("FEAR_RENDERER");
    void* bh = dlopen("libbytehook.so", RTLD_NOW);
    if (!bh) {
        printf("LWJGL hook v2.13: libbytehook.so not found\n");
        return;
    }
    int (*bytehook_init)(int, int) = dlsym(bh, "bytehook_init");
    void* (*bytehook_hook_all)(const char*, const char*, void*, void*, void*) =
        dlsym(bh, "bytehook_hook_all");
    if (!bytehook_hook_all) {
        printf("LWJGL hook v2.13: bytehook_hook_all missing\n");
        return;
    }
    if (bytehook_init) {
        int st = bytehook_init(0, 0);
        printf("LWJGL hook v2.13: bytehook_init -> %d\n", st);
    }
    /* Krypton: full eglGetProcAddress hook for GL translation */
    if (fear && strcmp(fear, "ng_gl4es") == 0) {
        void* stub = bytehook_hook_all(NULL, "eglGetProcAddress", (void*)eglGetProcAddress_hook, NULL, NULL);
        printf("LWJGL hook v2.13: bytehook eglGetProcAddress -> %p\n", stub);
        return;
    }
    /* Zink/Fear Render: only eglBindAPI so GLFW accepts desktop OpenGL (OSMesa provides it) */
    if (fear && (strcmp(fear, "fear_render") == 0 || strcmp(fear, "panvk_zink") == 0
              || strcmp(fear, "turnip_zink") == 0 || strcmp(fear, "vulkan_zink") == 0)) {
        void* stub = bytehook_hook_all(NULL, "eglBindAPI", (void*)eglBindAPI_hook, NULL, NULL);
        printf("LWJGL hook v2.13: bytehook eglBindAPI (Zink) -> %p\n", stub);
        return;
    }
    printf("LWJGL hook v2.13: skip egl bytehook (renderer=%s)\n", fear ? fear : "null");
}

void installLwjglDlopenHook(JNIEnv *env) {
    LOGI("Installing LWJGL hooks (v2.13)");
    printf("LWJGL hook: installing hooks (v2.13)\n");
    ensure_ng_gl4es();
    try_install_egl_bytehook();

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
        printf("LWJGL hook: hooks installed (v2.13)\n");
    }
}
