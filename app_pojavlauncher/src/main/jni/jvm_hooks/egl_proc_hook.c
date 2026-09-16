// eglGetProcAddress hook for Krypton (NG-GL4ES)
// Routes GL symbols through gl4es_GetProcAddress so UBO queries work.
#include <dlfcn.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>

__attribute__((visibility("default")))
void* eglGetProcAddress_hook(const char* procname) {
    if (!procname) return NULL;

    static void* (*real_eglGetProcAddress)(const char*) = NULL;
    static void* (*gl4es_GetProcAddress)(const char*) = NULL;
    static void* ng_handle = NULL;
    static int init_done = 0;

    if (!init_done) {
        init_done = 1;
        void* egl = dlopen("libEGL.so", RTLD_NOW | RTLD_NOLOAD);
        if (!egl) egl = dlopen("libEGL.so", RTLD_NOW);
        if (egl) real_eglGetProcAddress = (void*(*)(const char*))dlsym(egl, "eglGetProcAddress");

        const char* fear = getenv("FEAR_RENDERER");
        const char* native_dir = getenv("POJAV_NATIVEDIR");
        if (fear && strcmp(fear, "ng_gl4es") == 0) {
            if (native_dir && native_dir[0]) {
                char path[512];
                snprintf(path, sizeof(path), "%s/libng_gl4es.so", native_dir);
                ng_handle = dlopen(path, RTLD_NOW | RTLD_NOLOAD);
                if (!ng_handle) ng_handle = dlopen(path, RTLD_NOW);
            }
            if (!ng_handle) ng_handle = dlopen("libng_gl4es.so", RTLD_NOW | RTLD_NOLOAD);
            if (!ng_handle) ng_handle = dlopen("libng_gl4es.so", RTLD_NOW);
            if (ng_handle) {
                gl4es_GetProcAddress = (void*(*)(const char*))dlsym(ng_handle, "gl4es_GetProcAddress");
                if (!gl4es_GetProcAddress)
                    gl4es_GetProcAddress = (void*(*)(const char*))dlsym(ng_handle, "glXGetProcAddress");
                printf("eglGetProcAddress_hook: ng_gl4es getproc=%p\n", (void*)gl4es_GetProcAddress);
            }
        }
    }

    if (gl4es_GetProcAddress &&
        (strncmp(procname, "gl", 2) == 0 || strncmp(procname, "GL", 2) == 0)) {
        void* sym = gl4es_GetProcAddress(procname);
        if (sym) return sym;
        if (ng_handle) {
            void* sym2 = dlsym(ng_handle, procname);
            if (sym2) return sym2;
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
