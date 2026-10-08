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
/* FEAR-FPSUNLOCK: force the EGL swap interval to 0 so presentation is not pinned
   to the display's refresh rate (the "60 fps lock"); the frame rate is then
   bounded only by the GPU. Same technique as tools/fearrender/fearpatch.py, with
   the same escape hatch: MG_FORCE_VSYNC=1 restores the requested interval. */
typedef void* fear_EGLDisplay;
typedef int fear_EGLint;
typedef unsigned int fear_EGLBoolean;
static fear_EGLBoolean (*real_eglSwapInterval_p)(fear_EGLDisplay, fear_EGLint);
static fear_EGLBoolean hooked_eglSwapInterval_impl(fear_EGLDisplay dpy, fear_EGLint interval) {
    if (real_eglSwapInterval_p == NULL) {
        real_eglSwapInterval_p = (fear_EGLBoolean (*)(fear_EGLDisplay, fear_EGLint))
                dlsym(RTLD_DEFAULT, "eglSwapInterval");
        if (real_eglSwapInterval_p == NULL) {
            void* egl = dlopen("libEGL.so", RTLD_NOW);
            if (egl == NULL) egl = dlopen("libEGL.so.1", RTLD_NOW);
            if (egl != NULL)
                real_eglSwapInterval_p = (fear_EGLBoolean (*)(fear_EGLDisplay, fear_EGLint))
                        dlsym(egl, "eglSwapInterval");
        }
    }
    if (interval != 0) {
        static int fear_vsync_pref = -1;
        if (fear_vsync_pref == -1) {
            const char* fear_env = getenv("MG_FORCE_VSYNC");
            fear_vsync_pref = (fear_env != NULL && strcmp(fear_env, "1") == 0) ? 1 : 0;
        }
        if (!fear_vsync_pref) interval = 0;
    }
    if (real_eglSwapInterval_p == NULL) return 0;
    return real_eglSwapInterval_p(dpy, interval);
}

/* FEARBRAND (EGL proc-address path) ------------------------------------------------
   The LTW renderer resolves GL entry points through eglGetProcAddress, not a plain
   dlopen/dlsym, which is why the glGetString branding in jvm_hooks/lwjgl_dlopen_hook.c
   (the ndlsym / glfwGetProcAddress sites) never installed there and the upstream
   launcher name could still reach the in-game F3 screen. Brand GL_VENDOR /
   GL_RENDERER on this path too, using the SAME install-only-if-obtained rule: the
   real glGetString is resolved by calling the real eglGetProcAddress with the very
   argument this hook was handed - the exact lookup the un-hooked code performs - and
   the hook is installed ONLY when that returned a real function. Every other name is
   forwarded to that same real pointer, and if it cannot be obtained the lookup falls
   through to the un-hooked result, so the hook can never hand back a NULL that was
   not already there.

   The branding strings and the hook live in jvm_hooks/lwjgl_dlopen_hook.c, which is
   built into libpojavexec.so; this file is built into liblinkerhook.so, a separate
   shared object, so those statics are not reachable across the boundary. The minimal
   set (two static buffers + a forwarding hook) is therefore duplicated here. */
#define FEAR_GL_VENDOR   0x1F00u
#define FEAR_GL_RENDERER 0x1F01u

static unsigned char fear_egl_gl_vendor_str[]   = "FearLauncher";
static unsigned char fear_egl_gl_renderer_str[] = "FearLTW (OpenGL ES 3)";

static unsigned char* (*g_real_glGetString_egl)(unsigned int) = NULL;
static int g_fear_brand_egl_logged_ok = 0;
static int g_fear_brand_egl_logged_fail = 0;

/* One-time evidence line: records whether the real glGetString was obtained via
   eglGetProcAddress, so the branding can be confirmed on-device. */
static void fear_brand_egl_log(int ok) {
    if (ok) {
        if (g_fear_brand_egl_logged_ok) return;
        g_fear_brand_egl_logged_ok = 1;
    } else {
        if (g_fear_brand_egl_logged_fail) return;
        g_fear_brand_egl_logged_fail = 1;
    }
    printf("FEARBRAND: real glGetString %s (path=eglGetProcAddress) - GL_VENDOR/GL_RENDERER branded, all other names forwarded through the game's own lookup\n",
           ok ? "OBTAINED" : "NOT OBTAINED - falling through to the un-hooked lookup");
}

/* The branding hook itself. GL_VENDOR / GL_RENDERER come from the static buffers;
   every other name is forwarded to the real pointer. It is only installed when that
   pointer was obtained, so the fallback below is unreachable in practice and, if it
   were reached, returns exactly what the un-hooked game would have received (NULL)
   rather than a fabricated string. */
static unsigned char* fear_egl_glGetString_hook(unsigned int name) {
    if (name == FEAR_GL_VENDOR)   return fear_egl_gl_vendor_str;
    if (name == FEAR_GL_RENDERER) return fear_egl_gl_renderer_str;
    if (g_real_glGetString_egl != NULL) return g_real_glGetString_egl(name);
    return NULL;
}

__attribute__((visibility("default"), used))
void* eglGetProcAddress_hook(const char* procname) {
    typedef void* (*eglGPA_t)(const char*);
    static eglGPA_t real_eglGPA = NULL;
    if (procname != NULL && strcmp(procname, "eglSwapInterval") == 0)
        return (void*) hooked_eglSwapInterval_impl;
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
    /* FEARBRAND on the EGL proc-address path: resolve the real glGetString by
       calling the real eglGetProcAddress with the SAME argument this hook was handed
       - the pointer the game itself would have received - and only install the hook
       when that succeeded. Otherwise fall through to the un-hooked result. */
    if (procname != NULL && strcmp(procname, "glGetString") == 0) {
        if (g_real_glGetString_egl == NULL) {
            void* fear_p = real_eglGPA(procname);
            if (fear_p != NULL) g_real_glGetString_egl = (unsigned char* (*)(unsigned int)) fear_p;
        }
        if (g_real_glGetString_egl != NULL) {
            fear_brand_egl_log(1);
            return (void*) fear_egl_glGetString_hook;
        }
        fear_brand_egl_log(0);
        return real_eglGPA(procname); /* un-hooked result (may be NULL) */
    }
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
