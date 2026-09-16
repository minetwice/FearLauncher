// eglGetProcAddress hook + GL symbol resolver for Krypton (NG-GL4ES)
#include <dlfcn.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>

static void* ng_handle = NULL;
static void* (*gl4es_GetProcAddress)(const char*) = NULL;
static void* (*real_eglGetProcAddress)(const char*) = NULL;
static int init_done = 0;

static void ensure_init(void) {
    if (init_done) return;
    init_done = 1;

    void* egl = dlopen("libEGL.so", RTLD_NOW | RTLD_NOLOAD);
    if (!egl) egl = dlopen("libEGL.so", RTLD_NOW);
    if (egl) real_eglGetProcAddress = (void*(*)(const char*))dlsym(egl, "eglGetProcAddress");

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
        printf("eglGetProcAddress_hook: ng_gl4es=%p getproc=%p\n", ng_handle, (void*)gl4es_GetProcAddress);
    } else {
        printf("eglGetProcAddress_hook: ng_gl4es NOT loaded (%s)\n", dlerror() ? dlerror() : "?");
    }
}

__attribute__((visibility("default")))
void* eglGetProcAddress_hook(const char* procname) {
    if (!procname) return NULL;
    ensure_init();

    if (gl4es_GetProcAddress &&
        (strncmp(procname, "gl", 2) == 0 || strncmp(procname, "GL", 2) == 0)) {
        void* sym = gl4es_GetProcAddress(procname);
        if (sym) return sym;
        if (ng_handle) {
            void* sym2 = dlsym(ng_handle, procname);
            if (sym2) return sym2;
        }
    }

    /* Always prefer translator for known missing UBO entry points */
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
