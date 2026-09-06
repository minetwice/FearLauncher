//
// Created by maks on 06.01.2025.
//

#include "jvm_hooks.h"

#include <android/api-level.h>

#include <dlfcn.h>
#include <string.h>
#include <stdlib.h>

#define TAG __FILE_NAME__
#include <log.h>

#include "../pojavexec.h"

#define GL_VERSION 0x1F02
#define GL_RENDERER 0x1F01
#define GL_VENDOR 0x1F00
#define GL_EXTENSIONS 0x1F03

static void universal_stub_void(void) {
    LOGI("LWJGL linkerhook: universal GL stub executed");
}

static void glGenSamplers_fallback(int count, unsigned int* samplers) {
    static unsigned int next_id = 1;
    if (!samplers || count <= 0) return;
    typedef void (*glGenSamplers_pfn)(int, unsigned int*);
    static glGenSamplers_pfn real_fn = NULL;
    if (!real_fn) {
        real_fn = (glGenSamplers_pfn) dlsym(RTLD_DEFAULT, "glGenSamplers");
        if (!real_fn) real_fn = (glGenSamplers_pfn) dlsym(RTLD_DEFAULT, "glGenSamplersOES");
    }
    if (real_fn) {
        real_fn(count, samplers);
        int valid = 1;
        for (int i = 0; i < count; i++) {
            if (samplers[i] == 0) { valid = 0; break; }
        }
        if (valid) return;
    }
    for (int i = 0; i < count; i++) {
        samplers[i] = next_id++;
    }
    LOGI("LWJGL linkerhook: glGenSamplers fallback generated %d sampler(s)", count);
}

static void glBindSampler_fallback(unsigned int unit, unsigned int sampler) {
    typedef void (*glBindSampler_pfn)(unsigned int, unsigned int);
    static glBindSampler_pfn real_fn = NULL;
    if (!real_fn) {
        real_fn = (glBindSampler_pfn) dlsym(RTLD_DEFAULT, "glBindSampler");
        if (!real_fn) real_fn = (glBindSampler_pfn) dlsym(RTLD_DEFAULT, "glBindSamplerOES");
    }
    if (real_fn) real_fn(unit, sampler);
}

static void glDeleteSamplers_fallback(int count, const unsigned int* samplers) {
    if (!samplers || count <= 0) return;
    typedef void (*glDeleteSamplers_pfn)(int, const unsigned int*);
    static glDeleteSamplers_pfn real_fn = NULL;
    if (!real_fn) {
        real_fn = (glDeleteSamplers_pfn) dlsym(RTLD_DEFAULT, "glDeleteSamplers");
        if (!real_fn) real_fn = (glDeleteSamplers_pfn) dlsym(RTLD_DEFAULT, "glDeleteSamplersOES");
    }
    if (real_fn) real_fn(count, samplers);
}

static void glSamplerParameteri_fallback(unsigned int sampler, unsigned int pname, int param) {
    typedef void (*glSamplerParameteri_pfn)(unsigned int, unsigned int, int);
    static glSamplerParameteri_pfn real_fn = NULL;
    if (!real_fn) {
        real_fn = (glSamplerParameteri_pfn) dlsym(RTLD_DEFAULT, "glSamplerParameteri");
        if (!real_fn) real_fn = (glSamplerParameteri_pfn) dlsym(RTLD_DEFAULT, "glSamplerParameteriOES");
    }
    if (real_fn) real_fn(sampler, pname, param);
}

static void glMemoryBarrier_stub(unsigned int barriers) {
    typedef void (*glFlush_pfn)();
    static glFlush_pfn real_glFlush = NULL;
    if (!real_glFlush) {
        real_glFlush = (glFlush_pfn) dlsym(RTLD_DEFAULT, "glFlush");
        if (!real_glFlush) {
            real_glFlush = (glFlush_pfn) dlsym(RTLD_NEXT, "glFlush");
        }
    }
    if (real_glFlush) {
        real_glFlush();
    }
    LOGI("glMemoryBarrier stub called and flushed successfully (Barriers: %u)", barriers);
}

static const unsigned char* glGetString_hook(unsigned int name) {
    if (name == GL_VERSION) {
        return (const unsigned char*)"4.6.0 NVIDIA 545.29";
    } else if (name == GL_RENDERER) {
        return (const unsigned char*)"NVIDIA GeForce RTX 4090";
    } else if (name == GL_VENDOR) {
        return (const unsigned char*)"NVIDIA Corporation";
    } else if (name == GL_EXTENSIONS) {
        return (const unsigned char*)"GL_ARB_direct_state_access GL_ARB_buffer_storage GL_ARB_shader_image_load_store GL_NV_conditional_render GL_EXT_gpu_shader4 GL_EXT_texture_buffer GL_EXT_texture_cube_map_array GL_OES_EGL_image_external_essl3 GL_NV_shader_noperspective_interpolation GL_ARB_shader_objects GL_ARB_vertex_shader GL_ARB_fragment_shader GL_EXT_blend_equation_separate GL_EXT_geometry_shader4 GL_EXT_gpu_program_parameters GL_ARB_instanced_arrays GL_ARB_draw_instanced";
    }

    typedef const unsigned char* (*glGetString_pfn)(unsigned int);
    static glGetString_pfn real_glGetString = NULL;
    if (!real_glGetString) {
        real_glGetString = (glGetString_pfn) dlsym(RTLD_DEFAULT, "glGetString");
        if (!real_glGetString) {
            real_glGetString = (glGetString_pfn) dlsym(RTLD_NEXT, "glGetString");
        }
    }
    if (real_glGetString) {
        return real_glGetString(name);
    }
    return (const unsigned char*)"";
}

static const unsigned char* glGetStringi_hook(unsigned int name, unsigned int index) {
    if (name == GL_EXTENSIONS) {
        static const char* extensions[] = {
            "GL_ARB_direct_state_access",
            "GL_ARB_buffer_storage",
            "GL_ARB_shader_image_load_store",
            "GL_NV_conditional_render",
            "GL_EXT_gpu_shader4",
            "GL_EXT_texture_buffer",
            "GL_EXT_texture_cube_map_array",
            "GL_OES_EGL_image_external_essl3",
            "GL_NV_shader_noperspective_interpolation",
            "GL_ARB_shader_objects",
            "GL_ARB_vertex_shader",
            "GL_ARB_fragment_shader",
            "GL_EXT_blend_equation_separate",
            "GL_EXT_geometry_shader4",
            "GL_EXT_gpu_program_parameters",
            "GL_ARB_instanced_arrays",
            "GL_ARB_draw_instanced"
        };
        unsigned int size = sizeof(extensions) / sizeof(extensions[0]);
        if (index < size) {
            return (const unsigned char*)extensions[index];
        }
    }

    typedef const unsigned char* (*glGetStringi_pfn)(unsigned int, unsigned int);
    static glGetStringi_pfn real_glGetStringi = NULL;
    if (!real_glGetStringi) {
        real_glGetStringi = (glGetStringi_pfn) dlsym(RTLD_DEFAULT, "glGetStringi");
        if (!real_glGetStringi) {
            real_glGetStringi = (glGetStringi_pfn) dlsym(RTLD_NEXT, "glGetStringi");
        }
    }
    if (real_glGetStringi) {
        return real_glGetStringi(name, index);
    }
    return (const unsigned char*)"";
}

static void* eglGetProcAddress_hook(const char* procname) {
    if (procname == NULL) return NULL;
    if (strcmp(procname, "glMemoryBarrier") == 0 || strcmp(procname, "glMemoryBarrierEXT") == 0) {
        LOGI("eglGetProcAddress_hook: Intercepted and returned custom glMemoryBarrier stub!");
        return (void*) glMemoryBarrier_stub;
    }
    if (strcmp(procname, "glGetString") == 0) {
        return (void*) glGetString_hook;
    }
    if (strcmp(procname, "glGetStringi") == 0) {
        return (void*) glGetStringi_hook;
    }

    if (strcmp(procname, "glGenSamplers") == 0 || strcmp(procname, "glGenSamplersOES") == 0) {
        typedef void* (*pfn)(const char*);
        static pfn real_eglGetProcAddress = NULL;
        if (!real_eglGetProcAddress) real_eglGetProcAddress = (pfn) dlsym(RTLD_DEFAULT, "eglGetProcAddress");
        if (real_eglGetProcAddress) {
            void* sym = real_eglGetProcAddress(procname);
            if (sym) return sym;
        }
        void* sym = dlsym(RTLD_DEFAULT, procname);
        if (sym) return sym;
        return (void*) glGenSamplers_fallback;
    }

    if (strcmp(procname, "glBindSampler") == 0 || strcmp(procname, "glBindSamplerOES") == 0) {
        typedef void* (*pfn)(const char*);
        static pfn real_eglGetProcAddress = NULL;
        if (!real_eglGetProcAddress) real_eglGetProcAddress = (pfn) dlsym(RTLD_DEFAULT, "eglGetProcAddress");
        if (real_eglGetProcAddress) {
            void* sym = real_eglGetProcAddress(procname);
            if (sym) return sym;
        }
        void* sym = dlsym(RTLD_DEFAULT, procname);
        if (sym) return sym;
        return (void*) glBindSampler_fallback;
    }

    if (strcmp(procname, "glDeleteSamplers") == 0 || strcmp(procname, "glDeleteSamplersOES") == 0) {
        typedef void* (*pfn)(const char*);
        static pfn real_eglGetProcAddress = NULL;
        if (!real_eglGetProcAddress) real_eglGetProcAddress = (pfn) dlsym(RTLD_DEFAULT, "eglGetProcAddress");
        if (real_eglGetProcAddress) {
            void* sym = real_eglGetProcAddress(procname);
            if (sym) return sym;
        }
        void* sym = dlsym(RTLD_DEFAULT, procname);
        if (sym) return sym;
        return (void*) glDeleteSamplers_fallback;
    }

    if (strcmp(procname, "glSamplerParameteri") == 0 || strcmp(procname, "glSamplerParameteriOES") == 0) {
        typedef void* (*pfn)(const char*);
        static pfn real_eglGetProcAddress = NULL;
        if (!real_eglGetProcAddress) real_eglGetProcAddress = (pfn) dlsym(RTLD_DEFAULT, "eglGetProcAddress");
        if (real_eglGetProcAddress) {
            void* sym = real_eglGetProcAddress(procname);
            if (sym) return sym;
        }
        void* sym = dlsym(RTLD_DEFAULT, procname);
        if (sym) return sym;
        return (void*) glSamplerParameteri_fallback;
    }

    typedef void* (*eglGetProcAddress_pfn)(const char*);
    static eglGetProcAddress_pfn real_eglGetProcAddress = NULL;
    if (!real_eglGetProcAddress) {
        real_eglGetProcAddress = (eglGetProcAddress_pfn) dlsym(RTLD_DEFAULT, "eglGetProcAddress");
        if (!real_eglGetProcAddress) {
            real_eglGetProcAddress = (eglGetProcAddress_pfn) dlsym(RTLD_NEXT, "eglGetProcAddress");
        }
    }
    if (real_eglGetProcAddress) {
        void* sym = real_eglGetProcAddress(procname);
        if (sym) return sym;
    }

    void* sym = dlsym(RTLD_DEFAULT, procname);
    if (sym) return sym;

    return (void*) universal_stub_void;
}

static jlong ndlopen_bugfix(__attribute__((unused)) JNIEnv *env,
                     __attribute__((unused)) jclass class,
                     jlong filename_ptr,
                     jint jmode) {
    const char* filename = (const char*) filename_ptr;

    // Oveeride vulkan loading to let us load vulkan ourselves
    if(strstr(filename, "libvulkan.so") == filename) {
        printf("LWJGL linkerhook: replacing load for libvulkan.so with custom driver\n");
        return (jlong) pojavexec_loadVulkanDriver();
    }
    // Load renderer using egl_acquire
    if(strstr(filename, "libGLFear.so") == filename) {
        printf("LWJGL linkerhook: replacing OpenGL with renderspec driver\n");
        const pojavexec_renderspec_t *rspec = pojavexec_getRenderSpec();
        return (jlong) rspec->egl_acquire(rspec->egl_path);
    }

    int mode = (int)jmode;
    return (jlong) dlopen(filename, mode);
}

static jlong ndlsym_hook(__attribute__((unused)) JNIEnv *env,
                  __attribute__((unused)) jclass class,
                  jlong handle,
                  jlong symbol_ptr) {
    const char* symbol = (const char*) symbol_ptr;
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
        if (strcmp(symbol, "glMemoryBarrier") == 0 || strcmp(symbol, "glMemoryBarrierEXT") == 0) {
            printf("LWJGL linkerhook: successfully hooked glMemoryBarrier symbol directly\n");
            return (jlong) glMemoryBarrier_stub;
        }
        if (strcmp(symbol, "glGenSamplers") == 0 || strcmp(symbol, "glGenSamplersOES") == 0) {
            void* sym = dlsym((void*) handle, symbol);
            if (sym) return (jlong) sym;
            return (jlong) glGenSamplers_fallback;
        }
        if (strcmp(symbol, "glBindSampler") == 0 || strcmp(symbol, "glBindSamplerOES") == 0) {
            void* sym = dlsym((void*) handle, symbol);
            if (sym) return (jlong) sym;
            return (jlong) glBindSampler_fallback;
        }
        if (strcmp(symbol, "glDeleteSamplers") == 0 || strcmp(symbol, "glDeleteSamplersOES") == 0) {
            void* sym = dlsym((void*) handle, symbol);
            if (sym) return (jlong) sym;
            return (jlong) glDeleteSamplers_fallback;
        }
        if (strcmp(symbol, "glSamplerParameteri") == 0 || strcmp(symbol, "glSamplerParameteriOES") == 0) {
            void* sym = dlsym((void*) handle, symbol);
            if (sym) return (jlong) sym;
            return (jlong) glSamplerParameteri_fallback;
        }
    }

    // Call real dlsym
    void* sym = dlsym((void*) handle, symbol);
    if (!sym && symbol && strncmp(symbol, "gl", 2) == 0) {
        return (jlong) universal_stub_void;
    }
    return (jlong) sym;
}

/**
 * Install the LWJGL dlopen hook. This allows us to mitigate linker bugs and add custom library overrides.
 */
void installLwjglDlopenHook(JNIEnv *env) {
    LOGI("Installing LWJGL dlopen() and dlsym() hooks");
    jclass dynamicLinkLoader = (*env)->FindClass(env, "org/lwjgl/system/linux/DynamicLinkLoader");
    if(dynamicLinkLoader == NULL) {
        LOGE("Failed to find the target class");
        (*env)->ExceptionClear(env);
        return;
    }
    JNINativeMethod hooks[] = {
            {"ndlopen", "(JI)J", &ndlopen_bugfix},
            {"ndlsym", "(JJ)J", &ndlsym_hook}
    };
    if((*env)->RegisterNatives(env, dynamicLinkLoader, hooks, 2) != 0) {
        LOGE("Failed to register the hooked methods");
        (*env)->ExceptionClear(env);
    }
}
