//
// Created by maks on 05.06.2023.
//
#include <android/dlext.h>
#include <string.h>
#include <stdio.h>
#include <bytehook.h>
#include "native_hooks.h"
#include <stdlib.h>
#include <dlfcn.h>
#include <stdint.h> /* FEARWIRE-HOLYZINK-ROTATE */
// Silence the warnings about using reserved identifiers (we need to link to these to not pollute the global symtab)
//NOLINTBEGIN
static void* (*android_dlopen_ext_p)(const char* filename,
                                  int flags,
                                  const android_dlextinfo* extinfo,
                                  const void* caller_addr);
static struct android_namespace_t* (*android_get_exported_namespace_p)(const char* name);
//NOLINTEND
static void* ready_handle;

/* eglGetProcAddress_hook lives in pojavexec (lwjgl_dlopen_hook.c).
   liblinkerhook is a separate .so and is dlopened with RTLD_NOW, so the
   symbol must exist here or the ICD preload fails. Provide a local stub
   that forwards to the real eglGetProcAddress; install_global_egl_hook
   (AWT path) may later replace the bytehook target with the real hook. */
__attribute__((visibility("default"), used))
void* eglGetProcAddress_hook(const char* procname) {
    typedef void* (*eglGPA_t)(const char*);
    static eglGPA_t real_eglGPA = NULL;
    if (real_eglGPA == NULL) {
        real_eglGPA = (eglGPA_t)dlsym(RTLD_DEFAULT, "eglGetProcAddress");
        if (real_eglGPA == NULL) {
            void* egl = dlopen("libEGL.so", RTLD_NOW);
            if (egl == NULL) egl = dlopen("libEGL.so.1", RTLD_NOW);
            if (egl != NULL)
                real_eglGPA = (eglGPA_t)dlsym(egl, "eglGetProcAddress");
        }
    }
    if (real_eglGPA == NULL) return NULL;
    return real_eglGPA(procname);
}

/* FEARWIRE-HOLYZINK-ROTATE: rewrite 90/270 buffer transforms to identity
   (ROT_90=0x10, ROT_270=0x30 - both match & 0x10) for holy_zink_kopper so
   zink's unrotated landscape output is displayed upright. */
static int32_t (*real_setBuffersTransform_p)(void*, int32_t);
static int32_t holy_rotate_fix_active(void) {
    const char* fear = getenv("FEAR_RENDERER");
    return fear && strcmp(fear, "holy_zink_kopper") == 0;
}
static int32_t hooked_setBuffersTransform_impl(void* window, int32_t transform) {
    if (holy_rotate_fix_active() && (transform & 0x10)) {
        printf("FEARWIRE-ROTATE: ANativeWindow_setBuffersTransform(%d) -> 0 (zink rotation fix)\n", transform);
        transform = 0;
    }
    if (real_setBuffersTransform_p == NULL)
        real_setBuffersTransform_p = (int32_t (*)(void*, int32_t)) dlsym(RTLD_DEFAULT, "ANativeWindow_setBuffersTransform");
    if (real_setBuffersTransform_p == NULL) return 0;
    return real_setBuffersTransform_p(window, transform);
}

void install_global_egl_hook(bytehook_hook_all_t bytehook_hook_all_p) {
    // Forcefully hook eglGetProcAddress in native GL libraries using bytehook
    bytehook_hook_all_p(NULL, "eglGetProcAddress", (void*)eglGetProcAddress_hook, NULL, NULL);
    // FEARWIRE-HOLYZINK-ROTATE: stop the WSI from rotating zink's output sideways
    bytehook_hook_all_p(NULL, "ANativeWindow_setBuffersTransform", (void*)hooked_setBuffersTransform_impl, NULL, NULL);
}

static const char *sphal_namespaces[3] = {
        "sphal", "vendor", "default"
};


__attribute__((visibility("default"), used)) void app__pojav_linkerhook_pass_handles(void* data, void* android_dlopen_ext,
                                                                                    void* android_get_exported_namespace) {
    ready_handle = data;
    android_dlopen_ext_p = android_dlopen_ext;
    android_get_exported_namespace_p = android_get_exported_namespace;
}

__attribute__((visibility("default"), used)) void *android_dlopen_ext(const char *filename, int flags, const android_dlextinfo *extinfo) {
    if(!strstr(filename, "vulkan."))
        return android_dlopen_ext_p(filename, flags, extinfo, &android_dlopen_ext);
    return ready_handle;
}

__attribute__((visibility("default"), used)) void *android_load_sphal_library(const char *filename, int flags) {
    if(strstr(filename, "vulkan.")) {
        return ready_handle;
    }
    //printf("__loader_android_get_exported_namespace = %p\n__loader_android_dlopen_ext = %p\n", __loader_android_get_exported_namespace,
    //       __loader_android_dlopen_ext);
    struct android_namespace_t* androidNamespace;
    for(int i = 0; i < 3; i++) {
        androidNamespace = android_get_exported_namespace_p(sphal_namespaces[i]);
        if(androidNamespace != NULL) break;
    }
    android_dlextinfo info;
    info.flags = ANDROID_DLEXT_USE_NAMESPACE;
    info.library_namespace = androidNamespace;
    return android_dlopen_ext_p(filename, flags, &info, &android_dlopen_ext);
}

// This is done for older android versions which don't
// export this function. Technically this is wrong
// but for our usage it's fine enough
__attribute__((visibility("default"), used)) uint64_t atrace_get_enabled_tags() {
    return 0;
}
