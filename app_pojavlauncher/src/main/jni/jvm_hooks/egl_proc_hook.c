// eglGetProcAddress + eglBindAPI + eglQueryString hooks for Zink/Fear Render
#include <dlfcn.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>

#ifndef EGL_OPENGL_API
#define EGL_OPENGL_API 0x30A2
#endif
#ifndef EGL_OPENGL_ES_API
#define EGL_OPENGL_ES_API 0x30A0
#endif
#ifndef EGL_TRUE
#define EGL_TRUE 1
#endif
#ifndef EGL_FALSE
#define EGL_FALSE 0
#endif
#ifndef EGL_CLIENT_APIS
#define EGL_CLIENT_APIS 0x308D
#endif
#ifndef EGL_EXTENSIONS
#define EGL_EXTENSIONS 0x3055
#endif
#ifndef EGL_VENDOR
#define EGL_VENDOR 0x3053
#endif
#ifndef EGL_VERSION
#define EGL_VERSION 0x3054
#endif

static void* ng_handle = NULL;
static void* (*gl4es_GetProcAddress)(const char*) = NULL;
static void* (*real_eglGetProcAddress)(const char*) = NULL;
static int (*real_eglBindAPI)(int) = NULL;
static const char* (*real_eglQueryString)(void*, int) = NULL;
static int init_done = 0;

static int is_zink_renderer(void) {
    const char* fear = getenv("FEAR_RENDERER");
    if (!fear) return 0;
    return (strcmp(fear, "fear_render") == 0 || strcmp(fear, "panvk_zink") == 0
         || strcmp(fear, "turnip_zink") == 0 || strcmp(fear, "vulkan_zink") == 0);
}

static void ensure_init(void) {
    if (init_done) return;
    init_done = 1;

    void* egl = dlopen("libEGL.so", RTLD_NOW | RTLD_NOLOAD);
    if (!egl) egl = dlopen("libEGL.so", RTLD_NOW);
    if (!egl) egl = dlopen("/system/lib64/libEGL.so", RTLD_NOW);
    if (egl) {
        real_eglGetProcAddress = (void*(*)(const char*))dlsym(egl, "eglGetProcAddress");
        real_eglBindAPI = (int(*)(int))dlsym(egl, "eglBindAPI");
        real_eglQueryString = (const char*(*)(void*, int))dlsym(egl, "eglQueryString");
    }

    const char* native_dir = getenv("POJAV_NATIVEDIR");
    if (native_dir && native_dir[0]) {
        char path[512];
        snprintf(path, sizeof(path), "%s/libng_gl4es.so", native_dir);
        ng_handle = dlopen(path, RTLD_NOW | RTLD_GLOBAL | RTLD_NOLOAD);
        if (!ng_handle) ng_handle = dlopen(path, RTLD_NOW | RTLD_GLOBAL);
    }
    if (!ng_handle) ng_handle = dlopen("libng_gl4es.so", RTLD_NOW | RTLD_GLOBAL | RTLD_NOLOAD);
    if (!ng_handle) ng_handle = dlopen("libng_gl4es.so", RTLD_NOW | RTLD_GLOBAL);

    if (ng_handle) {
        gl4es_GetProcAddress = (void*(*)(const char*))dlsym(ng_handle, "gl4es_GetProcAddress");
        if (!gl4es_GetProcAddress)
            gl4es_GetProcAddress = (void*(*)(const char*))dlsym(ng_handle, "glXGetProcAddress");
    }
}

__attribute__((visibility("default")))
int eglBindAPI_hook(int api) {
    ensure_init();
    printf("eglBindAPI_hook: api=0x%x zink=%d\n", api, is_zink_renderer());
    if (is_zink_renderer() && (api == EGL_OPENGL_API || api == 0x30A2)) {
        printf("eglBindAPI_hook: ALLOW OpenGL for Zink/OSMesa\n");
        return EGL_TRUE;
    }
    if (real_eglBindAPI) return real_eglBindAPI(api);
    return EGL_FALSE;
}

__attribute__((visibility("default")))
const char* eglQueryString_hook(void* display, int name) {
    ensure_init();
    if (is_zink_renderer()) {
        if (name == EGL_CLIENT_APIS) {
            printf("eglQueryString_hook: CLIENT_APIS -> OpenGL OpenGL_ES\n");
            return "OpenGL OpenGL_ES";
        }
        if (name == EGL_EXTENSIONS) {
            const char* real = real_eglQueryString ? real_eglQueryString(display, name) : "";
            static char buf[2048];
            snprintf(buf, sizeof(buf), "%s%s EGL_KHR_create_context EGL_KHR_create_context_no_error",
                     real ? real : "", (real && real[0]) ? "" : "");
            return buf;
        }
    }
    if (real_eglQueryString) return real_eglQueryString(display, name);
    return NULL;
}

__attribute__((visibility("default")))
void* eglGetProcAddress_hook(const char* procname) {
    if (!procname) return NULL;
    ensure_init();

    if (strcmp(procname, "eglBindAPI") == 0)
        return (void*)eglBindAPI_hook;
    if (strcmp(procname, "eglQueryString") == 0)
        return (void*)eglQueryString_hook;

    if (gl4es_GetProcAddress &&
        (strncmp(procname, "gl", 2) == 0 || strncmp(procname, "GL", 2) == 0)) {
        if (strncmp(procname, "glfw", 4) != 0 && strncmp(procname, "glX", 3) != 0) {
            void* sym = gl4es_GetProcAddress(procname);
            if (sym) return sym;
            if (ng_handle) {
                void* sym2 = dlsym(ng_handle, procname);
                if (sym2) return sym2;
            }
        }
    }

    if (real_eglGetProcAddress) {
        void* sym = real_eglGetProcAddress(procname);
        if (sym) return sym;
    }

    if (ng_handle) {
        void* sym = dlsym(ng_handle, procname);
        if (sym) return sym;
    }
    return dlsym(RTLD_DEFAULT, procname);
}

/* Install bytehooks as early as possible (called from minibridge) */
__attribute__((visibility("default")))
void fear_install_zink_egl_hooks(void) {
    if (!is_zink_renderer()) return;
    void* bh = dlopen("libbytehook.so", RTLD_NOW);
    if (!bh) {
        printf("fear_install_zink_egl_hooks: no bytehook\n");
        return;
    }
    int (*bytehook_init)(int, int) = dlsym(bh, "bytehook_init");
    void* (*bytehook_hook_all)(const char*, const char*, void*, void*, void*) =
        dlsym(bh, "bytehook_hook_all");
    if (bytehook_init) bytehook_init(0, 0);
    if (bytehook_hook_all) {
        void* a = bytehook_hook_all(NULL, "eglBindAPI", (void*)eglBindAPI_hook, NULL, NULL);
        void* b = bytehook_hook_all(NULL, "eglQueryString", (void*)eglQueryString_hook, NULL, NULL);
        void* c = bytehook_hook_all(NULL, "eglGetProcAddress", (void*)eglGetProcAddress_hook, NULL, NULL);
        printf("fear_install_zink_egl_hooks: BindAPI=%p QueryString=%p GetProc=%p\n", a, b, c);
    }
}
