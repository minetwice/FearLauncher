//
// LWJGL dlopen/dlsym hooks — Quasar pure: no NVIDIA spoof
//
#include "jvm_hooks.h"
#include <android/api-level.h>
#include <dlfcn.h>
#include <string.h>
#include <stdlib.h>
#include <ctype.h>

#define TAG __FILE_NAME__
#include <log.h>
#include "../pojavexec.h"

#define GL_VERSION 0x1F02
#define GL_RENDERER 0x1F01
#define GL_VENDOR 0x1F00
#define GL_EXTENSIONS 0x1F03

static void glMemoryBarrier_stub(unsigned int barriers) {
    typedef void (*glFlush_pfn)();
    static glFlush_pfn real_glFlush = NULL;
    if (!real_glFlush) {
        real_glFlush = (glFlush_pfn) dlsym(RTLD_DEFAULT, "glFlush");
        if (!real_glFlush) real_glFlush = (glFlush_pfn) dlsym(RTLD_NEXT, "glFlush");
    }
    if (real_glFlush) real_glFlush();
    LOGI("glMemoryBarrier stub (Barriers: %u)", barriers);
}

static int is_quasar_pure(void) {
    static int cached = -1;
    if (cached < 0) {
        const char* v = getenv("QUASAR_PURE");
        cached = (v && v[0] == '1') ? 1 : 0;
    }
    return cached;
}

static const unsigned char* glGetString_hook(unsigned int name) {
    if (is_quasar_pure()) {
        if (name == GL_VERSION) return (const unsigned char*)"3.3.0 Quasar Core";
        if (name == GL_RENDERER) return (const unsigned char*)"Quasar GLES3 Translator";
        if (name == GL_VENDOR) return (const unsigned char*)"FearLauncher";
        typedef const unsigned char* (*glGetString_pfn)(unsigned int);
        static glGetString_pfn q = NULL;
        if (!q) {
            q = (glGetString_pfn) dlsym(RTLD_DEFAULT, "glGetString");
            if (!q) q = (glGetString_pfn) dlsym(RTLD_NEXT, "glGetString");
        }
        return q ? q(name) : (const unsigned char*)"";
    }
    if (name == GL_VERSION) return (const unsigned char*)"4.6.0 NVIDIA 545.29";
    if (name == GL_RENDERER) return (const unsigned char*)"NVIDIA GeForce RTX 4090";
    if (name == GL_VENDOR) return (const unsigned char*)"NVIDIA Corporation";
    typedef const unsigned char* (*glGetString_pfn)(unsigned int);
    static glGetString_pfn real = NULL;
    if (!real) {
        real = (glGetString_pfn) dlsym(RTLD_DEFAULT, "glGetString");
        if (!real) real = (glGetString_pfn) dlsym(RTLD_NEXT, "glGetString");
    }
    return real ? real(name) : (const unsigned char*)"";
}

typedef const unsigned char* (*glGetStringi_pfn)(unsigned int, unsigned int);
static const unsigned char* glGetStringi_hook(unsigned int name, unsigned int index) {
    if (name == GL_EXTENSIONS && !is_quasar_pure()) {
        static const char* fakeExt = "GL_ARB_sampler_objects GL_ARB_framebuffer_object GL_ARB_vertex_array_object GL_ARB_instanced_arrays GL_ARB_draw_instanced";
        if (index > 0) return (const unsigned char*)"";
        return (const unsigned char*)fakeExt;
    }
    static glGetStringi_pfn real = NULL;
    if (!real) {
        real = (glGetStringi_pfn) dlsym(RTLD_DEFAULT, "glGetStringi");
        if (!real) real = (glGetStringi_pfn) dlsym(RTLD_NEXT, "glGetStringi");
    }
    return real ? real(name, index) : (const unsigned char*)"";
}

static void glShaderSource_hook(unsigned int shader, int count, const char** string, const int* length) {
    typedef void (*fn)(unsigned int, int, const char**, const int*);
    static fn real = NULL;
    if (!real) {
        real = (fn) dlsym(RTLD_DEFAULT, "glShaderSource");
        if (!real) real = (fn) dlsym(RTLD_NEXT, "glShaderSource");
    }
    if (real) real(shader, count, string, length);
}

static void* eglGetProcAddress_hook(const char* name) {
    typedef void* (*fn)(const char*);
    static fn real = NULL;
    if (!real) {
        real = (fn) dlsym(RTLD_DEFAULT, "eglGetProcAddress");
        if (!real) real = (fn) dlsym(RTLD_NEXT, "eglGetProcAddress");
    }
    if (name) {
        if (strcmp(name, "glGetString") == 0) return (void*)glGetString_hook;
        if (strcmp(name, "glGetStringi") == 0) return (void*)glGetStringi_hook;
        if (strcmp(name, "glShaderSource") == 0) return (void*)glShaderSource_hook;
        if (strcmp(name, "glMemoryBarrier") == 0 || strcmp(name, "glMemoryBarrierEXT") == 0)
            return (void*)glMemoryBarrier_stub;
    }
    return real ? real(name) : NULL;
}

static jlong ndlopen_hook(JNIEnv *env, jclass class, jlong filename_ptr, jlong jmode) {
    (void)env; (void)class;
    const char* filename = (const char*) filename_ptr;
    int mode = (int)jmode;
    if (filename != NULL) {
        /* OpenGL lib redirect handled by renderspec / JREUtils for Quasar */
    }
    return (jlong) dlopen(filename, mode);
}

static jlong ndlsym_hook(JNIEnv *env, jclass class, jlong handle, jlong name_ptr) {
    (void)env; (void)class;
    const char* symbol = (const char*) name_ptr;
    if (symbol != NULL) {
        if (strcmp(symbol, "eglGetProcAddress") == 0) {
            printf("LWJGL linkerhook: successfully hooked eglGetProcAddress symbol directly\n");
            return (jlong) eglGetProcAddress_hook;
        }
        if (strcmp(symbol, "glGetString") == 0) {
            printf("LWJGL linkerhook: successfully hooked glGetString symbol directly\n");
            return (jlong) glGetString_hook;
        }
        if (strcmp(symbol, "glGetStringi") == 0) {
            printf("LWJGL linkerhook: successfully hooked glGetStringi symbol directly\n");
            return (jlong) glGetStringi_hook;
        }
        if (strcmp(symbol, "glShaderSource") == 0) {
            printf("LWJGL linkerhook: successfully hooked glShaderSource symbol directly\n");
            return (jlong) glShaderSource_hook;
        }
        if (strcmp(symbol, "glMemoryBarrier") == 0 || strcmp(symbol, "glMemoryBarrierEXT") == 0) {
            printf("LWJGL linkerhook: successfully hooked glMemoryBarrier symbol directly\n");
            return (jlong) glMemoryBarrier_stub;
        }
    }
    return (jlong) dlsym((void*) handle, symbol);
}

void installLwjglDlopenHook(JNIEnv *env) {
    LOGI("Installing LWJGL dlopen() and dlsym() hooks (Quasar-aware)");
    jclass dynamicLinkLoader = (*env)->FindClass(env, "org/lwjgl/system/linux/DynamicLinkLoader");
    if (dynamicLinkLoader == NULL) {
        LOGE("Failed to find DynamicLinkLoader class");
        (*env)->ExceptionClear(env);
        return;
    }
    JNINativeMethod hooks[] = {
        {"ndlopen", "(JJ)J", (void*) &ndlopen_hook},
        {"ndlsym",  "(JJ)J", (void*) &ndlsym_hook},
    };
    if ((*env)->RegisterNatives(env, dynamicLinkLoader, hooks, 2) != 0) {
        LOGE("Failed to register the hooked methods");
        (*env)->ExceptionClear(env);
    }
}
