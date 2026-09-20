//
// FearLauncher — LWJGL dlopen/dlsym hook v2.13
// ROOT FIX: no global EGL bytehook for GLES/Krypton (breaks window + input).
// OSMesa facade hooks only when FEAR_PANVK_OK=1 (or FEAR_FORCE_ZINK=1).
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
static void* (*g_real_dlsym)(void*, const char*) = NULL;

static int is_zink_renderer_local(void) {
    const char* fear = getenv("FEAR_RENDERER");
    if (!fear) return 0;
    return (strcmp(fear, "fear_render") == 0 || strcmp(fear, "panvk_zink") == 0
         || strcmp(fear, "turnip_zink") == 0 || strcmp(fear, "vulkan_zink") == 0
         || strcmp(fear, "mesa_softpipe") == 0);
}

static void* dlsym_egl_redirect(void* handle, const char* symbol) {
    if (symbol && is_zink_renderer_local()) {
        if (strcmp(symbol, "eglChooseConfig") == 0) return (void*)eglChooseConfig;
        if (strcmp(symbol, "eglGetDisplay") == 0) return (void*)eglGetDisplay;
        if (strcmp(symbol, "eglInitialize") == 0) return (void*)eglInitialize;
        if (strcmp(symbol, "eglCreateContext") == 0) return (void*)eglCreateContext;
        if (strcmp(symbol, "eglMakeCurrent") == 0) return (void*)eglMakeCurrent;
        if (strcmp(symbol, "eglCreateWindowSurface") == 0) return (void*)eglCreateWindowSurface;
        if (strcmp(symbol, "eglBindAPI") == 0) return (void*)eglBindAPI_hook;
        if (strcmp(symbol, "eglGetProcAddress") == 0) return (void*)eglGetProcAddress_hook;
        if (strcmp(symbol, "eglQueryString") == 0) return (void*)eglQueryString;
    }
    if (g_real_dlsym) return g_real_dlsym(handle, symbol);
    return NULL;
}

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

static jlong ndlopen_bugfix(JNIEnv *env, jclass clazz, jlong filename, jint flag) {
    (void)env; (void)clazz;
    return (jlong)(uintptr_t)dlopen((const char*)(uintptr_t)filename, flag);
}

static jlong ndlsym_hook(JNIEnv *env, jclass clazz, jlong handle, jlong symbol_name) {
    (void)env; (void)clazz;
    const char* symbol = (const char*)(uintptr_t)symbol_name;
    if (!symbol) return 0;

    if (is_zink_renderer_local() && strncmp(symbol, "egl", 3) == 0) {
        void* r = dlsym_egl_redirect((void*)(uintptr_t)handle, symbol);
        if (r) return (jlong)(uintptr_t)r;
    }

    const char* fear = getenv("FEAR_RENDERER");
    if (fear && strcmp(fear, "ng_gl4es") == 0) {
        if (strncmp(symbol, "gl", 2) == 0) {
            void* s = resolve_gl_symbol(symbol);
            if (s) return (jlong)(uintptr_t)s;
        }
    }

    void* sym = dlsym((void*)(uintptr_t)handle, symbol);
    if (!sym) sym = dlsym(RTLD_DEFAULT, symbol);
    return (jlong)(uintptr_t)sym;
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
    /* ROOT FIX: never global-hook system EGL for GLES/Krypton */
    if (fear && strcmp(fear, "ng_gl4es") == 0) {
        printf("LWJGL hook v2.13: skip all egl bytehook for ng_gl4es (system EGL)\n");
        return;
    }
    if (fear && strcmp(fear, "opengles2") == 0) {
        printf("LWJGL hook v2.13: skip all egl bytehook for opengles2\n");
        return;
    }
    const char* panvk = getenv("FEAR_PANVK_OK");
    int zink_ok = (panvk && panvk[0] == '1') || (getenv("FEAR_FORCE_ZINK") && getenv("FEAR_FORCE_ZINK")[0] == '1');
    if (fear && (strcmp(fear, "fear_render") == 0 || strcmp(fear, "panvk_zink") == 0
              || strcmp(fear, "turnip_zink") == 0 || strcmp(fear, "vulkan_zink") == 0
              || strcmp(fear, "mesa_softpipe") == 0)) {
        if (!zink_ok && strcmp(fear, "mesa_softpipe") != 0) {
            printf("LWJGL hook v2.13: skip OSMesa egl bytehook (FEAR_PANVK_OK!=1, renderer=%s)\n", fear);
            return;
        }
        extern const char* eglQueryString_hook(void* display, int name);
        void* a = bytehook_hook_all(NULL, "eglBindAPI", (void*)eglBindAPI_hook, NULL, NULL);
        void* b = bytehook_hook_all(NULL, "eglQueryString", (void*)eglQueryString_hook, NULL, NULL);
        void* c = bytehook_hook_all(NULL, "eglGetProcAddress", (void*)eglGetProcAddress_hook, NULL, NULL);
        void* d = bytehook_hook_all(NULL, "eglChooseConfig", (void*)eglChooseConfig, NULL, NULL);
        void* e = bytehook_hook_all(NULL, "eglGetDisplay", (void*)eglGetDisplay, NULL, NULL);
        void* f = bytehook_hook_all(NULL, "eglInitialize", (void*)eglInitialize, NULL, NULL);
        void* g = bytehook_hook_all(NULL, "eglCreateContext", (void*)eglCreateContext, NULL, NULL);
        void* h = bytehook_hook_all(NULL, "eglMakeCurrent", (void*)eglMakeCurrent, NULL, NULL);
        void* i = bytehook_hook_all(NULL, "eglCreateWindowSurface", (void*)eglCreateWindowSurface, NULL, NULL);
        if (!g_real_dlsym) {
            void* libc = dlopen("libc.so", RTLD_NOW | RTLD_NOLOAD);
            if (!libc) libc = dlopen("libc.so.6", RTLD_NOW | RTLD_NOLOAD);
            if (libc) g_real_dlsym = (void*(*)(void*, const char*))dlsym(libc, "dlsym");
            if (!g_real_dlsym) g_real_dlsym = (void*(*)(void*, const char*))dlsym(RTLD_NEXT, "dlsym");
            printf("LWJGL hook v2.13: real_dlsym=%p\n", (void*)g_real_dlsym);
        }
        void* j = bytehook_hook_all(NULL, "dlsym", (void*)dlsym_egl_redirect, NULL, NULL);
        printf("LWJGL hook v2.13: OSMesa egl hooks (renderer=%s) BindAPI=%p CreateCtx=%p MakeCurrent=%p\n",
               fear, a, g, h);
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
