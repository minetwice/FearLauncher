//
// Created by maks on 06.01.2025.
//

#include "jvm_hooks.h"

#include <android/api-level.h>

#include <dlfcn.h>
#include <string.h>
#include <stdlib.h>
#include <pthread.h>

#define TAG __FILE_NAME__
#include <log.h>

#include "../pojavexec.h"

#define GL_VERSION 0x1F02
#define GL_RENDERER 0x1F01
#define GL_VENDOR 0x1F00
#define GL_EXTENSIONS 0x1F03

// ============================================================================
// Shadow Buffer System for glMapBufferRange
// ============================================================================
// On Mali GPUs, glMapBufferRange is not supported (only GL_OES_mapbuffer).
// gl4es translates desktop glMapBufferRange but returns NULL on Mali.
// We ALWAYS use a CPU-side shadow buffer (malloc). On glUnmapBuffer,
// we upload the data to the real GPU buffer via glBufferSubData.
//
// Thread-safe: uses a mutex to protect the shadow buffer table.
// ============================================================================

typedef struct {
    unsigned int target;
    unsigned int buffer_id;
    long offset;
    long length;
    void* shadow_ptr;
    int is_shadow;
    int in_use;
} ShadowBufferMap;

#define MAX_SHADOW_BUFFERS 512
static ShadowBufferMap g_shadowBuffers[MAX_SHADOW_BUFFERS];
static int g_shadowCount = 0;
static pthread_mutex_t g_shadowMutex = PTHREAD_MUTEX_INITIALIZER;

static void universal_stub_void(void) {
    LOGI("LWJGL linkerhook: universal GL stub executed");
}

// Find a free slot in the shadow buffer table
static int find_free_shadow_slot(void) {
    // First, try to find an unused slot
    for (int i = 0; i < g_shadowCount; i++) {
        if (!g_shadowBuffers[i].in_use) {
            return i;
        }
    }
    // Otherwise, allocate a new slot
    if (g_shadowCount < MAX_SHADOW_BUFFERS) {
        return g_shadowCount++;
    }
    // Table is full — recycle slot 0 (best effort)
    if (g_shadowBuffers[0].shadow_ptr) {
        free(g_shadowBuffers[0].shadow_ptr);
        g_shadowBuffers[0].shadow_ptr = NULL;
    }
    return 0;
}

// Helper: get the currently bound buffer ID for a target
static unsigned int get_bound_buffer_id(unsigned int target) {
    typedef void (*glGetIntegerv_pfn)(unsigned int, int*);
    static glGetIntegerv_pfn real_glGetIntegerv = NULL;
    if (!real_glGetIntegerv) {
        real_glGetIntegerv = (glGetIntegerv_pfn) dlsym(RTLD_DEFAULT, "glGetIntegerv");
        if (!real_glGetIntegerv) real_glGetIntegerv = (glGetIntegerv_pfn) dlsym(RTLD_NEXT, "glGetIntegerv");
    }
    if (!real_glGetIntegerv) return 0;

    unsigned int pname = 0x8894; // GL_ARRAY_BUFFER_BINDING
    if (target == 0x8893) pname = 0x8895; // GL_ELEMENT_ARRAY_BUFFER_BINDING
    else if (target == 0x8A11) pname = 0x8A28; // GL_UNIFORM_BUFFER_BINDING
    else if (target == 0x90D2) pname = 0x90D3; // GL_SHADER_STORAGE_BUFFER_BINDING

    int val = 0;
    real_glGetIntegerv(pname, &val);
    return (unsigned int) val;
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

// ============================================================================
// glMapBufferRange hook — ALWAYS uses shadow buffer
// ============================================================================
// NEVER attempt to call the real glMapBufferRange. On Mali via gl4es,
// it is broken (returns NULL). Always allocate a CPU-side shadow buffer
// via malloc(). The game writes vertex data to this buffer.
// On glUnmapBuffer, we upload via glBufferSubData (GLES 2.0 core).
// ============================================================================

static void* glMapBufferRange_hook(unsigned int target, long offset, long length, unsigned int access) {
    static int callCount = 0;
    if (callCount < 5) {
        LOGI("LWJGL linkerhook: glMapBufferRange_hook CALLED target=0x%X offset=%ld len=%ld access=0x%X", target, offset, length, access);
        callCount++;
    }

    // Get the currently bound buffer ID for tracking
    unsigned int buffer_id = get_bound_buffer_id(target);

    // ALWAYS use shadow buffer — never try real glMapBufferRange
    long alloc_len = (length > 0) ? length : 65536;
    void* ptr = malloc(alloc_len);
    if (!ptr) {
        ptr = calloc(1, alloc_len);
    }
    if (!ptr) {
        // Last resort: try a smaller allocation
        alloc_len = 65536;
        ptr = malloc(alloc_len);
    }
    if (!ptr) {
        LOGE("LWJGL linkerhook: glMapBufferRange_hook CRITICAL: malloc FAILED for len=%ld", alloc_len);
        return NULL;
    }

    // Store shadow buffer info
    pthread_mutex_lock(&g_shadowMutex);
    int slot = find_free_shadow_slot();
    g_shadowBuffers[slot].target = target;
    g_shadowBuffers[slot].buffer_id = buffer_id;
    g_shadowBuffers[slot].offset = offset;
    g_shadowBuffers[slot].length = alloc_len;
    g_shadowBuffers[slot].shadow_ptr = ptr;
    g_shadowBuffers[slot].is_shadow = 1;
    g_shadowBuffers[slot].in_use = 1;
    pthread_mutex_unlock(&g_shadowMutex);

    if (callCount <= 5) {
        LOGI("LWJGL linkerhook: Shadow buffer allocated slot=%d target=0x%X bufID=%u len=%ld ptr=%p", slot, target, buffer_id, alloc_len, ptr);
    }

    // Clear any GL error state
    typedef unsigned int (*glGetError_pfn)(void);
    static glGetError_pfn real_glGetError = NULL;
    if (!real_glGetError) {
        real_glGetError = (glGetError_pfn) dlsym(RTLD_DEFAULT, "glGetError");
        if (!real_glGetError) real_glGetError = (glGetError_pfn) dlsym(RTLD_NEXT, "glGetError");
    }
    if (real_glGetError) {
        // Flush all errors
        unsigned int err;
        do { err = real_glGetError(); } while (err != 0); // 0 = GL_NO_ERROR
    }

    return ptr;
}

static void* glMapBuffer_hook(unsigned int target, unsigned int access) {
    // Get buffer size via glGetBufferParameteriv
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
    long len = (buf_size > 0) ? buf_size : 65536;

    unsigned int rangeAccess = 0x0002; // GL_MAP_WRITE_BIT
    if (access == 0x88B8 /* GL_READ_ONLY */) rangeAccess = 0x0001;
    else if (access == 0x88BA /* GL_READ_WRITE */) rangeAccess = 0x0001 | 0x0002;

    return glMapBufferRange_hook(target, 0, len, rangeAccess);
}

static int glUnmapBuffer_hook(unsigned int target) {
    typedef void (*glBufferSubData_pfn)(unsigned int, long, long, const void*);
    typedef unsigned int (*glGetError_pfn)(void);

    static glBufferSubData_pfn real_glBufferSubData = NULL;
    static glGetError_pfn real_glGetError = NULL;

    if (!real_glBufferSubData) {
        real_glBufferSubData = (glBufferSubData_pfn) dlsym(RTLD_DEFAULT, "glBufferSubData");
        if (!real_glBufferSubData) real_glBufferSubData = (glBufferSubData_pfn) dlsym(RTLD_DEFAULT, "glBufferSubDataARB");
    }
    if (!real_glGetError) {
        real_glGetError = (glGetError_pfn) dlsym(RTLD_DEFAULT, "glGetError");
        if (!real_glGetError) real_glGetError = (glGetError_pfn) dlsym(RTLD_NEXT, "glGetError");
    }

    // Find matching shadow buffer — prefer matching buffer_id, fall back to target
    int found_slot = -1;
    unsigned int current_buffer_id = get_bound_buffer_id(target);

    pthread_mutex_lock(&g_shadowMutex);
    // First, try to match by buffer_id
    for (int i = 0; i < g_shadowCount; i++) {
        if (g_shadowBuffers[i].in_use && g_shadowBuffers[i].is_shadow &&
            g_shadowBuffers[i].buffer_id == current_buffer_id && current_buffer_id != 0) {
            found_slot = i;
            break;
        }
    }
    // If no match by buffer_id, match by target (first match)
    if (found_slot < 0) {
        for (int i = 0; i < g_shadowCount; i++) {
            if (g_shadowBuffers[i].in_use && g_shadowBuffers[i].is_shadow &&
                g_shadowBuffers[i].target == target) {
                found_slot = i;
                break;
            }
        }
    }

    if (found_slot >= 0) {
        ShadowBufferMap* entry = &g_shadowBuffers[found_slot];
        void* shadow_ptr = entry->shadow_ptr;
        long shadow_offset = entry->offset;
        long shadow_length = entry->length;
        entry->in_use = 0;
        entry->is_shadow = 0;
        entry->shadow_ptr = NULL;
        pthread_mutex_unlock(&g_shadowMutex);

        if (shadow_ptr) {
            if (real_glBufferSubData) {
                real_glBufferSubData(target, shadow_offset, shadow_length, shadow_ptr);
            }
            free(shadow_ptr);
        }

        // Clear any GL error from glBufferSubData
        if (real_glGetError) {
            unsigned int err;
            do { err = real_glGetError(); } while (err != 0);
        }
        return 1; // GL_TRUE
    }
    pthread_mutex_unlock(&g_shadowMutex);

    // No shadow buffer found — call real glUnmapBuffer
    typedef int (*glUnmapBuffer_pfn)(unsigned int);
    static glUnmapBuffer_pfn real_glUnmapBuffer = NULL;
    if (!real_glUnmapBuffer) {
        real_glUnmapBuffer = (glUnmapBuffer_pfn) dlsym(RTLD_DEFAULT, "glUnmapBuffer");
        if (!real_glUnmapBuffer) real_glUnmapBuffer = (glUnmapBuffer_pfn) dlsym(RTLD_DEFAULT, "glUnmapBufferOES");
    }
    int res = 1;
    if (real_glUnmapBuffer) {
        res = real_glUnmapBuffer(target);
    }
    if (real_glGetError) {
        unsigned int err;
        do { err = real_glGetError(); } while (err != 0);
    }
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
        LOGI("eglGetProcAddress_hook: Returning glMapBufferRange_hook (shadow buffer)");
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

    // Override vulkan loading to let us load vulkan ourselves
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
        // Buffer mapping — ALWAYS return our hook (shadow buffer)
        if (strcmp(symbol, "glMapBufferRange") == 0 || strcmp(symbol, "glMapBufferRangeEXT") == 0 || strcmp(symbol, "glMapBufferRangeARB") == 0) {
            printf("LWJGL linkerhook: hooked glMapBufferRange -> shadow buffer hook\n");
            return (jlong) glMapBufferRange_hook;
        }
        if (strcmp(symbol, "glMapBuffer") == 0 || strcmp(symbol, "glMapBufferOES") == 0 || strcmp(symbol, "glMapBufferARB") == 0) {
            printf("LWJGL linkerhook: hooked glMapBuffer -> shadow buffer hook\n");
            return (jlong) glMapBuffer_hook;
        }
        if (strcmp(symbol, "glUnmapBuffer") == 0 || strcmp(symbol, "glUnmapBufferOES") == 0 || strcmp(symbol, "glUnmapBufferARB") == 0) {
            printf("LWJGL linkerhook: hooked glUnmapBuffer -> shadow buffer hook\n");
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
    LOGI("Installing LWJGL dlopen() and dlsym() hooks (BUILD v20260907-C)");
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
