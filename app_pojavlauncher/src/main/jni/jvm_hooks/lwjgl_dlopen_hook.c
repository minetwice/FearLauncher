//
// FearLauncher — LWJGL dlopen/dlsym hook v2.13
// Non-Zink (Krypton): pass-through dlopen + route missing GL symbols via gl4es_GetProcAddress
// Zink: eglBindAPI + eglQueryString + eglGetProcAddress for desktop OpenGL facade
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
extern void* eglGetDisplay(void* display_id);
extern int eglInitialize(void* dpy, int* major, int* minor);
extern int eglChooseConfig(void* dpy, const int* attrib_list, void** configs, int config_size, int* num_config);
extern void* eglCreateContext(void* dpy, void* config, void* share, const int* attrib_list);
extern int eglMakeCurrent(void* dpy, void* draw, void* read, void* ctx);
extern void* eglCreateWindowSurface(void* dpy, void* config, void* win, const int* attrib_list);
extern const char* eglQueryString(void* dpy, int name);

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
    return NULL;
}

jlong ndlopen_bugfix(JNIEnv *env, jclass clazz, jlong filename, jint mode) {
    const char* name = (const char*) filename;
    if (!name) return 0;
    int flags = (int) mode;
    if (flags == 0) flags = RTLD_LAZY;
    void* handle = dlopen(name, flags);
    if (!handle && strstr(name, "lib")) {
        /* retry with RTLD_NOW */
        handle = dlopen(name, RTLD_NOW);
    }
    return (jlong) handle;
}

jlong ndlsym_hook(JNIEnv *env, jclass clazz, jlong handle, jlong name) {
    const char* symbol = (const char*) name;
    if (!symbol) return 0;

    /* Skip intercepting GLFW symbols — let real libglfw provide them */
    if (strncmp(symbol, "glfw", 4) == 0 || strncmp(symbol, "GLFW", 4) == 0) {
        void* sym = dlsym((void*) handle, symbol);
        if (!sym) sym = dlsym(RTLD_DEFAULT, symbol);
        return (jlong) sym;
    }

    const char* fear = getenv("FEAR_RENDERER");
    if (fear && strcmp(fear, "ng_gl4es") == 0) {
        if (strncmp(symbol, "gl", 2) == 0) {
            void* s = resolve_gl_symbol(symbol);
            if (s) return (jlong) s;
        }
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
    if (fear && strcmp(fear, "ng_gl4es") == 0) {
        void* stub = bytehook_hook_all(NULL, "eglGetProcAddress", (void*)eglGetProcAddress_hook, NULL, NULL);
        printf("LWJGL hook v2.13: bytehook eglGetProcAddress -> %p\n", stub);
        return;
    }
    if (fear && (strcmp(fear, "fear_render") == 0 || strcmp(fear, "panvk_zink") == 0
              || strcmp(fear, "turnip_zink") == 0 || strcmp(fear, "vulkan_zink") == 0)) {
        extern const char* eglQueryString_hook(void* display, int name);
        void* a = bytehook_hook_all(NULL, "eglBindAPI", (void*)eglBindAPI_hook, NULL, NULL);
        void* b = bytehook_hook_all(NULL, "eglQueryString", (void*)eglQueryString_hook, NULL, NULL);
        void* c = bytehook_hook_all(NULL, "eglGetProcAddress", (void*)eglGetProcAddress_hook, NULL, NULL);
        /* Force ChooseConfig/CreateContext through facade — LWJGL often binds these via dlsym, not GetProc */
        void* d = bytehook_hook_all(NULL, "eglChooseConfig", (void*)eglChooseConfig, NULL, NULL);
        void* e = bytehook_hook_all(NULL, "eglGetDisplay", (void*)eglGetDisplay, NULL, NULL);
        void* f = bytehook_hook_all(NULL, "eglInitialize", (void*)eglInitialize, NULL, NULL);
        void* g = bytehook_hook_all(NULL, "eglCreateContext", (void*)eglCreateContext, NULL, NULL);
        void* h = bytehook_hook_all(NULL, "eglMakeCurrent", (void*)eglMakeCurrent, NULL, NULL);
        void* i = bytehook_hook_all(NULL, "eglCreateWindowSurface", (void*)eglCreateWindowSurface, NULL, NULL);
        printf("LWJGL hook v2.13: Zink egl hooks BindAPI=%p Query=%p GetProc=%p Choose=%p GetDisplay=%p Init=%p CreateCtx=%p MakeCurrent=%p CreateWin=%p\n",
               a, b, c, d, e, f, g, h, i);
        fflush(stdout);
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
