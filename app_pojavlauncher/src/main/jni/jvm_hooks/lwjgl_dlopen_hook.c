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

typedef struct {
    unsigned int buffer_id;
    unsigned int target;
    long offset;
    long length;
    void* shadow_ptr;
    int is_shadow;
} ShadowBufferMap;

#define MAX_SHADOW_BUFFERS 256
static ShadowBufferMap g_shadowBuffers[MAX_SHADOW_BUFFERS];
static int g_shadowCount = 0;

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

static void* glMapBufferRange_hook(unsigned int target, long offset, long length, unsigned int access) {
    typedef void (*glGetBufferParameteriv_pfn)(unsigned int, unsigned int, int*);
    typedef void* (*glMapBufferRange_pfn)(unsigned int, long, long, unsigned int);
    typedef unsigned int (*glGetError_pfn)(void);

    static glGetBufferParameteriv_pfn real_glGetBufferParameteriv = NULL;
    static glMapBufferRange_pfn real_glMapBufferRange = NULL;
    static glGetError_pfn real_glGetError = NULL;

    if (!real_glGetBufferParameteriv) {
        real_glGetBufferParameteriv = (glGetBufferParameteriv_pfn) dlsym(RTLD_DEFAULT, "glGetBufferParameteriv");
        if (!real_glGetBufferParameteriv) real_glGetBufferParameteriv = (glGetBufferParameteriv_pfn) dlsym(RTLD_DEFAULT, "glGetBufferParameterivARB");
    }
    if (!real_glMapBufferRange) {
        real_glMapBufferRange = (glMapBufferRange_pfn) dlsym(RTLD_DEFAULT, "glMapBufferRange");
        if (!real_glMapBufferRange) real_glMapBufferRange = (glMapBufferRange_pfn) dlsym(RTLD_DEFAULT, "glMapBufferRangeEXT");
    }
    if (!real_glGetError) {
        real_glGetError = (glGetError_pfn) dlsym(RTLD_DEFAULT, "glGetError");
    }

    int buf_size = 0;
    if (real_glGetBufferParameteriv) {
        real_glGetBufferParameteriv(target, 0x8764 /* GL_BUFFER_SIZE */, &buf_size);
    }

    if (length <= 0) {
        length = (buf_size > 0 && buf_size > offset) ? (buf_size - offset) : 1048576;
    }

    // Strip unsupported MAP_PERSISTENT_BIT (0x40) and MAP_COHERENT_BIT (0x80)
    unsigned int safeAccess = access & ~(0x0040 | 0x0080);
    void* ptr = NULL;

    if (real_glMapBufferRange && buf_size > 0 && offset < buf_size) {
        long valid_len = length;
        if (offset + valid_len > buf_size) {
            valid_len = buf_size - offset;
        }
        if (valid_len > 0) {
            ptr = real_glMapBufferRange(target, offset, valid_len, safeAccess);
        }
    }

    // If real mapping failed or buf_size was 0/invalid, allocate shadow buffer
    if (!ptr) {
        long alloc_len = (length > 0) ? length : 1048576;
        ptr = malloc(alloc_len);
        if (!ptr) ptr = calloc(1, alloc_len);

        if (g_shadowCount < MAX_SHADOW_BUFFERS) {
            g_shadowBuffers[g_shadowCount].target = target;
            g_shadowBuffers[g_shadowCount].offset = offset;
            g_shadowBuffers[g_shadowCount].length = alloc_len;
            g_shadowBuffers[g_shadowCount].shadow_ptr = ptr;
            g_shadowBuffers[g_shadowCount].is_shadow = 1;
            g_shadowCount++;
        }

        LOGI("LWJGL linkerhook: Shadow buffer allocated for target=0x%X (len=%ld)", target, alloc_len);
    }

    // Clear any error status from driver
    if (real_glGetError) {
        real_glGetError();
    }

    return ptr;
}

static void* glMapBuffer_hook(unsigned int target, unsigned int access) {
    typedef void (*glGetBufferParameteriv_pfn)(unsigned int, unsigned int, int*);
    static glGetBufferParameteriv_pfn real_glGetBufferParameteriv = NULL;
    if (!real_glGetBufferParameteriv) {
        real_glGetBufferParameteriv = (glGetBufferParameteriv_pfn) dlsym(RTLD_DEFAULT, "glGetBufferParameteriv");
        if (!real_glGetBufferParameteriv) real_glGetBufferParameteriv = (glGetBufferParameteriv_pfn) dlsym(RTLD_DEFAULT, "glGetBufferParameterivARB");
    }

    int buf_size = 0;
    if (real_glGetBufferParameteriv) {
        real_glGetBufferParameteriv(target, 0x8764 /* GL_BUFFER_SIZE */, &buf_size);
    }
    long len = (buf_size > 0) ? buf_size : 1048576;

    unsigned int rangeAccess = 0x0002; // GL_MAP_WRITE_BIT
    if (access == 0x88B8 /* GL_READ_ONLY */) rangeAccess = 0x0001;
    else if (access == 0x88BA /* GL_READ_WRITE */) rangeAccess = 0x0001 | 0x0002;

    return glMapBufferRange_hook(target, 0, len, rangeAccess);
}

static int glUnmapBuffer_hook(unsigned int target) {
    typedef void (*glBufferSubData_pfn)(unsigned int, long, long, const void*);
    typedef int (*glUnmapBuffer_pfn)(unsigned int);
    typedef unsigned int (*glGetError_pfn)(void);

    static glBufferSubData_pfn real_glBufferSubData = NULL;
    static glUnmapBuffer_pfn real_glUnmapBuffer = NULL;
    static glGetError_pfn real_glGetError = NULL;

    if (!real_glBufferSubData) {
        real_glBufferSubData = (glBufferSubData_pfn) dlsym(RTLD_DEFAULT, "glBufferSubData");
        if (!real_glBufferSubData) real_glBufferSubData = (glBufferSubData_pfn) dlsym(RTLD_DEFAULT, "glBufferSubDataARB");
    }
    if (!real_glUnmapBuffer) {
        real_glUnmapBuffer = (glUnmapBuffer_pfn) dlsym(RTLD_DEFAULT, "glUnmapBuffer");
        if (!real_glUnmapBuffer) real_glUnmapBuffer = (glUnmapBuffer_pfn) dlsym(RTLD_DEFAULT, "glUnmapBufferOES");
    }
    if (!real_glGetError) {
        real_glGetError = (glGetError_pfn) dlsym(RTLD_DEFAULT, "glGetError");
    }

    for (int i = 0; i < g_shadowCount; i++) {
        if (g_shadowBuffers[i].target == target && g_shadowBuffers[i].is_shadow && g_shadowBuffers[i].shadow_ptr) {
            if (real_glBufferSubData) {
                real_glBufferSubData(target, g_shadowBuffers[i].offset, g_shadowBuffers[i].length, g_shadowBuffers[i].shadow_ptr);
            }
            free(g_shadowBuffers[i].shadow_ptr);
            g_shadowBuffers[i].shadow_ptr = NULL;
            g_shadowBuffers[i].is_shadow = 0;

            if (real_glGetError) real_glGetError();
            return 1;
        }
    }

    int res = 1;
    if (real_glUnmapBuffer) {
        res = real_glUnmapBuffer(target);
    }
    if (real_glGetError) real_glGetError();
    return res ? res : 1;
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

    if (strcmp(procname, "glMapBufferRange") == 0 || strcmp(procname, "glMapBufferRangeEXT") == 0 || strcmp(procname, "glMapBufferRangeARB") == 0) {
        return (void*) glMapBufferRange_hook;
    }
    if (strcmp(procname, "glMapBuffer") == 0 || strcmp(procname, "glMapBufferOES") == 0 || strcmp(procname, "glMapBufferARB") == 0) {
        return (void*) glMapBuffer_hook;
    }
    if (strcmp(procname, "glUnmapBuffer") == 0 || strcmp(procname, "glUnmapBufferOES") == 0 || strcmp(procname, "glUnmapBufferARB") == 0) {
        return (void*) glUnmapBuffer_hook;
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
        if (strcmp(symbol, "glMapBufferRange") == 0 || strcmp(symbol, "glMapBufferRangeEXT") == 0 || strcmp(symbol, "glMapBufferRangeARB") == 0) {
            return (jlong) glMapBufferRange_hook;
        }
        if (strcmp(symbol, "glMapBuffer") == 0 || strcmp(symbol, "glMapBufferOES") == 0 || strcmp(symbol, "glMapBufferARB") == 0) {
            return (jlong) glMapBuffer_hook;
        }
        if (strcmp(symbol, "glUnmapBuffer") == 0 || strcmp(symbol, "glUnmapBufferOES") == 0 || strcmp(symbol, "glUnmapBufferARB") == 0) {
            return (jlong) glUnmapBuffer_hook;
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
