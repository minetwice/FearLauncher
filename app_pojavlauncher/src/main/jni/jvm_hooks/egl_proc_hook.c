// eglGetProcAddress + eglBindAPI hooks
// - Krypton (ng_gl4es): route GL symbols through gl4es_GetProcAddress
// - Zink/Fear Render: allow eglBindAPI(OpenGL) so GLFW can proceed; GL via OSMesa
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

static void* ng_handle = NULL;
static void* (*gl4es_GetProcAddress)(const char*) = NULL;
static void* (*real_eglGetProcAddress)(const char*) = NULL;
static int (*real_eglBindAPI)(int) = NULL;
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
        printf("egl_hook: ng_gl4es=%p getproc=%p\n", ng_handle, (void*)gl4es_GetProcAddress);
    }
}

/* Zink/OSMesa: pretend desktop OpenGL is available under EGL */
__attribute__((visibility("default")))
int eglBindAPI_hook(int api) {
    ensure_init();
    if (is_zink_renderer() && api == EGL_OPENGL_API) {
        printf("eglBindAPI_hook: allowing EGL_OPENGL_API for Zink/OSMesa\n");
        return EGL_TRUE;
    }
    if (real_eglBindAPI) return real_eglBindAPI(api);
    return EGL_FALSE;
}

__attribute__((visibility("default")))
void* eglGetProcAddress_hook(const char* procname) {
    if (!procname) return NULL;
    ensure_init();

    if (strcmp(procname, "eglBindAPI") == 0)
        return (void*)eglBindAPI_hook;

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

    if (gl4es_GetProcAddress &&
        (strcmp(procname, "glGetActiveUniformBlockiv") == 0 ||
         strcmp(procname, "glGetActiveUniformBlockName") == 0 ||
         strcmp(procname, "glGetActiveUniformsiv") == 0 ||
         strcmp(procname, "glGetUniformIndices") == 0 ||
         strcmp(procname, "glGetUniformBlockIndex") == 0 ||
         strcmp(procname, "glUniformBlockBinding") == 0)) {
        void* sym = gl4es_GetProcAddress(procname);
        if (sym) return sym;
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
