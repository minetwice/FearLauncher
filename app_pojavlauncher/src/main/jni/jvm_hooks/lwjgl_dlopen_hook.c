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

typedef struct {
    unsigned int target;
    unsigned int buffer_id;
    long offset;
    long length;
    void* shadow_ptr;
    int is_shadow;
    int in_use;
} ShadowBufferMap;

#define MAX_SHADOW_BUFFERS 8192
static ShadowBufferMap g_shadowBuffers[MAX_SHADOW_BUFFERS];
static int g_shadowCount = 0;
static pthread_mutex_t g_shadowMutex = PTHREAD_MUTEX_INITIALIZER;
static char s_fallback_buffer[2097152]; // 2MB static emergency fallback buffer

static void universal_stub_void(void) {
    LOGI("LWJGL linkerhook: universal GL stub executed");
}

static int find_free_shadow_slot(void) {
    for (int i = 0; i < g_shadowCount; i++) {
        if (!g_shadowBuffers[i].in_use) return i;
    }
    if (g_shadowCount < MAX_SHADOW_BUFFERS) return g_shadowCount++;
    for (int i = 0; i < MAX_SHADOW_BUFFERS; i++) {
        if (!g_shadowBuffers[i].in_use) return i;
    }
    return -1;
}

static unsigned int get_bound_buffer_id(unsigned int target) {
    typedef void (*glGetIntegerv_pfn)(unsigned int, int*);
    static glGetIntegerv_pfn real_glGetIntegerv = NULL;
    if (!real_glGetIntegerv) {
        real_glGetIntegerv = (glGetIntegerv_pfn) dlsym(RTLD_DEFAULT, "glGetIntegerv");
        if (!real_glGetIntegerv) real_glGetIntegerv = (glGetIntegerv_pfn) dlsym(RTLD_NEXT, "glGetIntegerv");
    }
    if (!real_glGetIntegerv) return 0;

    unsigned int pname = 0x8894; // GL_ARRAY_BUFFER_BINDING
    switch (target) {
        case 0x8892: pname = 0x8894; break; // GL_ARRAY_BUFFER -> GL_ARRAY_BUFFER_BINDING
        case 0x8893: pname = 0x8895; break; // GL_ELEMENT_ARRAY_BUFFER -> GL_ELEMENT_ARRAY_BUFFER_BINDING
        case 0x8A11: pname = 0x8A28; break; // GL_UNIFORM_BUFFER -> GL_UNIFORM_BUFFER_BINDING
        case 0x90D2: pname = 0x90D3; break; // GL_SHADER_STORAGE_BUFFER -> GL_SHADER_STORAGE_BUFFER_BINDING
        case 0x8F36: pname = 0x8F36; break; // GL_COPY_READ_BUFFER
        case 0x8F37: pname = 0x8F37; break; // GL_COPY_WRITE_BUFFER
        case 0x88EB: pname = 0x88ED; break; // GL_PIXEL_PACK_BUFFER -> GL_PIXEL_PACK_BUFFER_BINDING
        case 0x88EC: pname = 0x88EF; break; // GL_PIXEL_UNPACK_BUFFER -> GL_PIXEL_UNPACK_BUFFER_BINDING
        case 0x8C8E: pname = 0x8C8F; break; // GL_TRANSFORM_FEEDBACK_BUFFER -> GL_TRANSFORM_FEEDBACK_BUFFER_BINDING
        case 0x90EE: pname = 0x90EE; break; // GL_DISPATCH_INDIRECT_BUFFER
        case 0x8F39: pname = 0x8F43; break; // GL_DRAW_INDIRECT_BUFFER -> GL_DRAW_INDIRECT_BUFFER_BINDING
        default: pname = 0x8894; break;
    }

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
        for (int i = 0; i < count; i++) { if (samplers[i] == 0) { valid = 0; break; } }
        if (valid) return;
    }
    for (int i = 0; i < count; i++) samplers[i] = next_id++;
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
    static int callCount = 0;
    if (callCount < 5) {
        LOGI("LWJGL linkerhook: glMapBufferRange_hook CALLED target=0x%X offset=%ld len=%ld access=0x%X", target, offset, length, access);
        callCount++;
    }

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

    long alloc_len = length;
    if (alloc_len <= 0 && buf_size > 0) alloc_len = buf_size - offset;
    if (alloc_len <= 0) alloc_len = 1048576; // 1 MB fallback
    if (buf_size > 0 && (offset + alloc_len) < buf_size) {
        alloc_len = buf_size;
    }

    unsigned int buffer_id = get_bound_buffer_id(target);
    void* ptr = NULL;

    if (posix_memalign(&ptr, 64, alloc_len) != 0 || ptr == NULL) {
        ptr = malloc(alloc_len);
    }
    if (!ptr) ptr = calloc(1, alloc_len);

    if (!ptr) {
        LOGE("LWJGL linkerhook: Emergency fallback buffer used for alloc_len=%ld", alloc_len);
        ptr = s_fallback_buffer;
    }

    pthread_mutex_lock(&g_shadowMutex);
    int slot = find_free_shadow_slot();
    if (slot >= 0) {
        g_shadowBuffers[slot].target = target;
        g_shadowBuffers[slot].buffer_id = buffer_id;
        g_shadowBuffers[slot].offset = offset;
        g_shadowBuffers[slot].length = alloc_len;
        g_shadowBuffers[slot].shadow_ptr = ptr;
        g_shadowBuffers[slot].is_shadow = 1;
        g_shadowBuffers[slot].in_use = 1;
    } else {
        LOGW("LWJGL linkerhook: Shadow slots full, returning unmanaged buffer");
    }
    pthread_mutex_unlock(&g_shadowMutex);

    typedef unsigned int (*glGetError_pfn)(void);
    static glGetError_pfn real_glGetError = NULL;
    if (!real_glGetError) {
        real_glGetError = (glGetError_pfn) dlsym(RTLD_DEFAULT, "glGetError");
        if (!real_glGetError) real_glGetError = (glGetError_pfn) dlsym(RTLD_NEXT, "glGetError");
    }
    if (real_glGetError) { unsigned int err; do { err = real_glGetError(); } while (err != 0); }
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
    if (real_glGetBufferParameteriv) real_glGetBufferParameteriv(target, 0x8764, &buf_size);
    long len = (buf_size > 0) ? buf_size : 65536;
    unsigned int rangeAccess = 0x0002;
    if (access == 0x88B8) rangeAccess = 0x0001;
    else if (access == 0x88BA) rangeAccess = 0x0001 | 0x0002;
    return glMapBufferRange_hook(target, 0, len, rangeAccess);
}

static int glUnmapBuffer_hook(unsigned int target) {
    typedef void (*glBufferSubData_pfn)(unsigned int, long, long, const void*);
    typedef void (*glBindBuffer_pfn)(unsigned int, unsigned int);
    typedef unsigned int (*glGetError_pfn)(void);

    static glBufferSubData_pfn real_glBufferSubData = NULL;
    static glBindBuffer_pfn real_glBindBuffer = NULL;
    static glGetError_pfn real_glGetError = NULL;

    if (!real_glBufferSubData) {
        real_glBufferSubData = (glBufferSubData_pfn) dlsym(RTLD_DEFAULT, "glBufferSubData");
        if (!real_glBufferSubData) real_glBufferSubData = (glBufferSubData_pfn) dlsym(RTLD_DEFAULT, "glBufferSubDataARB");
    }
    if (!real_glBindBuffer) {
        real_glBindBuffer = (glBindBuffer_pfn) dlsym(RTLD_DEFAULT, "glBindBuffer");
    }
    if (!real_glGetError) {
        real_glGetError = (glGetError_pfn) dlsym(RTLD_DEFAULT, "glGetError");
        if (!real_glGetError) real_glGetError = (glGetError_pfn) dlsym(RTLD_NEXT, "glGetError");
    }

    unsigned int current_buffer_id = get_bound_buffer_id(target);
    int found_slot = -1;

    pthread_mutex_lock(&g_shadowMutex);
    for (int i = 0; i < g_shadowCount; i++) {
        if (g_shadowBuffers[i].in_use && g_shadowBuffers[i].is_shadow &&
            g_shadowBuffers[i].target == target &&
            (current_buffer_id == 0 || g_shadowBuffers[i].buffer_id == current_buffer_id)) {
            found_slot = i; break;
        }
    }

    // Fallback search if buffer ID mismatch
    if (found_slot < 0) {
        for (int i = 0; i < g_shadowCount; i++) {
            if (g_shadowBuffers[i].in_use && g_shadowBuffers[i].is_shadow && g_shadowBuffers[i].target == target) {
                found_slot = i; break;
            }
        }
    }

    if (found_slot >= 0) {
        ShadowBufferMap entry = g_shadowBuffers[found_slot];
        g_shadowBuffers[found_slot].in_use = 0;
        g_shadowBuffers[found_slot].is_shadow = 0;
        g_shadowBuffers[found_slot].shadow_ptr = NULL;
        pthread_mutex_unlock(&g_shadowMutex);

        if (entry.shadow_ptr) {
            // CRITICAL FIX: Bind the buffer BEFORE calling glBufferSubData
            if (real_glBindBuffer && entry.buffer_id != 0) {
                real_glBindBuffer(target, entry.buffer_id);
            }
            if (real_glBufferSubData) {
                real_glBufferSubData(target, entry.offset, entry.length, entry.shadow_ptr);
            }
            // Free memory if not using the static fallback buffer
            if ((char*)entry.shadow_ptr < s_fallback_buffer || (char*)entry.shadow_ptr >= (s_fallback_buffer + sizeof(s_fallback_buffer))) {
                free(entry.shadow_ptr);
            }
        }
        if (real_glGetError) { unsigned int err; do { err = real_glGetError(); } while (err != 0); }
        return 1; // Success
    }
    pthread_mutex_unlock(&g_shadowMutex);

    // Fallback to real glUnmapBuffer if not in shadow map
    typedef int (*glUnmapBuffer_pfn)(unsigned int);
    static glUnmapBuffer_pfn real_glUnmapBuffer = NULL;
    if (!real_glUnmapBuffer) {
        real_glUnmapBuffer = (glUnmapBuffer_pfn) dlsym(RTLD_DEFAULT, "glUnmapBuffer");
        if (!real_glUnmapBuffer) real_glUnmapBuffer = (glUnmapBuffer_pfn) dlsym(RTLD_DEFAULT, "glUnmapBufferOES");
    }
    int res = 1;
    if (real_glUnmapBuffer) res = real_glUnmapBuffer(target);
    if (real_glGetError) { unsigned int err; do { err = real_glGetError(); } while (err != 0); }
    return res ? res : 1;
}

static void glMemoryBarrier_stub(unsigned int barriers) {
    typedef void (*glFlush_pfn)();
    static glFlush_pfn real_glFlush = NULL;
    if (!real_glFlush) {
        real_glFlush = (glFlush_pfn) dlsym(RTLD_DEFAULT, "glFlush");
        if (!real_glFlush) real_glFlush = (glFlush_pfn) dlsym(RTLD_NEXT, "glFlush");
    }
    if (real_glFlush) real_glFlush();
    LOGI("glMemoryBarrier stub called and flushed successfully (Barriers: %u)", barriers);
}

static const unsigned char* glGetString_hook(unsigned int name) {
    if (name == GL_VERSION) return (const unsigned char*)"4.6.0 NVIDIA 545.29";
    else if (name == GL_RENDERER) return (const unsigned char*)"NVIDIA GeForce RTX 4090";
    else if (name == GL_VENDOR) return (const unsigned char*)"NVIDIA Corporation";
    else if (name == GL_EXTENSIONS) return (const unsigned char*)"GL_ARB_direct_state_access GL_ARB_buffer_storage GL_ARB_shader_image_load_store GL_NV_conditional_render GL_EXT_gpu_shader4 GL_EXT_texture_buffer GL_EXT_texture_cube_map_array GL_OES_EGL_image_external_essl3 GL_NV_shader_noperspective_interpolation GL_ARB_shader_objects GL_ARB_vertex_shader GL_ARB_fragment_shader GL_EXT_blend_equation_separate GL_EXT_geometry_shader4 GL_EXT_gpu_program_parameters GL_ARB_instanced_arrays GL_ARB_draw_instanced";
    typedef const unsigned char* (*glGetString_pfn)(unsigned int);
    static glGetString_pfn real_glGetString = NULL;
    if (!real_glGetString) {
        real_glGetString = (glGetString_pfn) dlsym(RTLD_DEFAULT, "glGetString");
        if (!real_glGetString) real_glGetString = (glGetString_pfn) dlsym(RTLD_NEXT, "glGetString");
    }
    if (real_glGetString) return real_glGetString(name);
    return (const unsigned char*)"";
}

static const unsigned char* glGetStringi_hook(unsigned int name, unsigned int index) {
    if (name == GL_EXTENSIONS) {
        static const char* extensions[] = {
            "GL_ARB_direct_state_access","GL_ARB_buffer_storage","GL_ARB_shader_image_load_store",
            "GL_NV_conditional_render","GL_EXT_gpu_shader4","GL_EXT_texture_buffer",
            "GL_EXT_texture_cube_map_array","GL_OES_EGL_image_external_essl3",
            "GL_NV_shader_noperspective_interpolation","GL_ARB_shader_objects",
            "GL_ARB_vertex_shader","GL_ARB_fragment_shader","GL_EXT_blend_equation_separate",
            "GL_EXT_geometry_shader4","GL_EXT_gpu_program_parameters",
            "GL_ARB_instanced_arrays","GL_ARB_draw_instanced"
        };
        unsigned int size = sizeof(extensions) / sizeof(extensions[0]);
        if (index < size) return (const unsigned char*)extensions[index];
    }
    typedef const unsigned char* (*glGetStringi_pfn)(unsigned int, unsigned int);
    static glGetStringi_pfn real_glGetStringi = NULL;
    if (!real_glGetStringi) {
        real_glGetStringi = (glGetStringi_pfn) dlsym(RTLD_DEFAULT, "glGetStringi");
        if (!real_glGetStringi) real_glGetStringi = (glGetStringi_pfn) dlsym(RTLD_NEXT, "glGetStringi");
    }
    if (real_glGetStringi) return real_glGetStringi(name, index);
    return (const unsigned char*)"";
}

void* eglGetProcAddress_hook(const char* procname) {
    if (procname == NULL) return NULL;
    if (strcmp(procname, "glMemoryBarrier") == 0 || strcmp(procname, "glMemoryBarrierEXT") == 0) return (void*) glMemoryBarrier_stub;
    if (strcmp(procname, "glGetString") == 0) return (void*) glGetString_hook;
    if (strcmp(procname, "glGetStringi") == 0) return (void*) glGetStringi_hook;
    if (strcmp(procname, "glMapBufferRange") == 0 || strcmp(procname, "glMapBufferRangeEXT") == 0 || strcmp(procname, "glMapBufferRangeARB") == 0) {
        LOGI("eglGetProcAddress_hook: glMapBufferRange -> shadow buffer");
        return (void*) glMapBufferRange_hook;
    }
    if (strcmp(procname, "glMapBuffer") == 0 || strcmp(procname, "glMapBufferOES") == 0 || strcmp(procname, "glMapBufferARB") == 0) return (void*) glMapBuffer_hook;
    if (strcmp(procname, "glUnmapBuffer") == 0 || strcmp(procname, "glUnmapBufferOES") == 0 || strcmp(procname, "glUnmapBufferARB") == 0) return (void*) glUnmapBuffer_hook;
    if (strcmp(procname, "glGenSamplers") == 0 || strcmp(procname, "glGenSamplersOES") == 0) {
        typedef void* (*pfn)(const char*); static pfn real = NULL;
        if (!real) real = (pfn) dlsym(RTLD_DEFAULT, "eglGetProcAddress");
        if (real) { void* s = real(procname); if (s) return s; }
        void* s = dlsym(RTLD_DEFAULT, procname); if (s) return s;
        return (void*) glGenSamplers_fallback;
    }
    if (strcmp(procname, "glBindSampler") == 0 || strcmp(procname, "glBindSamplerOES") == 0) {
        typedef void* (*pfn)(const char*); static pfn real = NULL;
        if (!real) real = (pfn) dlsym(RTLD_DEFAULT, "eglGetProcAddress");
        if (real) { void* s = real(procname); if (s) return s; }
        void* s = dlsym(RTLD_DEFAULT, procname); if (s) return s;
        return (void*) glBindSampler_fallback;
    }
    if (strcmp(procname, "glDeleteSamplers") == 0 || strcmp(procname, "glDeleteSamplersOES") == 0) {
        typedef void* (*pfn)(const char*); static pfn real = NULL;
        if (!real) real = (pfn) dlsym(RTLD_DEFAULT, "eglGetProcAddress");
        if (real) { void* s = real(procname); if (s) return s; }
        void* s = dlsym(RTLD_DEFAULT, procname); if (s) return s;
        return (void*) glDeleteSamplers_fallback;
    }
    if (strcmp(procname, "glSamplerParameteri") == 0 || strcmp(procname, "glSamplerParameteriOES") == 0) {
        typedef void* (*pfn)(const char*); static pfn real = NULL;
        if (!real) real = (pfn) dlsym(RTLD_DEFAULT, "eglGetProcAddress");
        if (real) { void* s = real(procname); if (s) return s; }
        void* s = dlsym(RTLD_DEFAULT, procname); if (s) return s;
        return (void*) glSamplerParameteri_fallback;
    }
    if (strcmp(procname, "glMapBufferRange") == 0 || strcmp(procname, "glMapBufferRangeEXT") == 0 || strcmp(procname, "glMapBufferRangeARB") == 0) {
        printf("LWJGL linkerhook: eglGetProcAddress hooked glMapBufferRange -> shadow buffer\n");
        return (void*) glMapBufferRange_hook;
    }
    if (strcmp(procname, "glMapBuffer") == 0 || strcmp(procname, "glMapBufferOES") == 0 || strcmp(procname, "glMapBufferARB") == 0) {
        printf("LWJGL linkerhook: eglGetProcAddress hooked glMapBuffer -> shadow buffer\n");
        return (void*) glMapBuffer_hook;
    }
    if (strcmp(procname, "glUnmapBuffer") == 0 || strcmp(procname, "glUnmapBufferOES") == 0 || strcmp(procname, "glUnmapBufferARB") == 0) {
        printf("LWJGL linkerhook: eglGetProcAddress hooked glUnmapBuffer -> shadow buffer\n");
        return (void*) glUnmapBuffer_hook;
    }
    if (strcmp(procname, "glMemoryBarrier") == 0 || strcmp(procname, "glMemoryBarrierEXT") == 0) {
        printf("LWJGL linkerhook: eglGetProcAddress hooked glMemoryBarrier\n");
        return (void*) glMemoryBarrier_stub;
    }
    typedef void* (*eglGetProcAddress_pfn)(const char*);
    static eglGetProcAddress_pfn real_eglGetProcAddress = NULL;
    if (!real_eglGetProcAddress) {
        real_eglGetProcAddress = (eglGetProcAddress_pfn) dlsym(RTLD_DEFAULT, "eglGetProcAddress");
        if (!real_eglGetProcAddress) real_eglGetProcAddress = (eglGetProcAddress_pfn) dlsym(RTLD_NEXT, "eglGetProcAddress");
    }
    if (real_eglGetProcAddress) { void* sym = real_eglGetProcAddress(procname); if (sym) return sym; }
    void* sym = dlsym(RTLD_DEFAULT, procname); if (sym) return sym;
    return (void*) universal_stub_void;
}

static jlong ndlopen_bugfix(__attribute__((unused)) JNIEnv *env,
                     __attribute__((unused)) jclass class,
                     jlong filename_ptr, jint jmode) {
    const char* filename = (const char*) filename_ptr;
    if(filename != NULL) {
        if(strcmp(filename, "libvulkan.so") == 0) {
            printf("LWJGL linkerhook: replacing load for libvulkan.so with custom driver\n");
            return (jlong) pojavexec_loadVulkanDriver();
        }
        if(strcmp(filename, "libTurboV1.so") == 0 || strcmp(filename, "libGL.so") == 0 || strcmp(filename, "libGL.so.1") == 0) {
            printf("LWJGL linkerhook: replacing OpenGL with renderspec driver (%s)\n", filename);
            const pojavexec_renderspec_t *rspec = pojavexec_getRenderSpec();
            if (rspec && rspec->egl_acquire && rspec->egl_path) {
                return (jlong) rspec->egl_acquire(rspec->egl_path);
            }
        }
    }
    return (jlong) dlopen(filename, (int)jmode);
}

static jlong ndlsym_hook(__attribute__((unused)) JNIEnv *env,
                  __attribute__((unused)) jclass class,
                  jlong handle, jlong symbol_ptr) {
    const char* symbol = (const char*) symbol_ptr;
    if (symbol != NULL) {
        if (strcmp(symbol, "eglGetProcAddress") == 0) {
            printf("LWJGL linkerhook: hooked eglGetProcAddress\n");
            return (jlong) eglGetProcAddress_hook;
        }
        if (strcmp(symbol, "glGetString") == 0) {
            printf("LWJGL linkerhook: hooked glGetString\n");
            return (jlong) glGetString_hook;
        }
        if (strcmp(symbol, "glGetStringi") == 0) {
            printf("LWJGL linkerhook: hooked glGetStringi\n");
            return (jlong) glGetStringi_hook;
        }
        if (strcmp(symbol, "glMemoryBarrier") == 0 || strcmp(symbol, "glMemoryBarrierEXT") == 0) {
            printf("LWJGL linkerhook: hooked glMemoryBarrier\n");
            return (jlong) glMemoryBarrier_stub;
        }
        if (strcmp(symbol, "glMapBufferRange") == 0 || strcmp(symbol, "glMapBufferRangeEXT") == 0 || strcmp(symbol, "glMapBufferRangeARB") == 0) {
            printf("LWJGL linkerhook: hooked glMapBufferRange -> shadow buffer\n");
            return (jlong) glMapBufferRange_hook;
        }
        if (strcmp(symbol, "glMapBuffer") == 0 || strcmp(symbol, "glMapBufferOES") == 0 || strcmp(symbol, "glMapBufferARB") == 0) {
            printf("LWJGL linkerhook: hooked glMapBuffer -> shadow buffer\n");
            return (jlong) glMapBuffer_hook;
        }
        if (strcmp(symbol, "glUnmapBuffer") == 0 || strcmp(symbol, "glUnmapBufferOES") == 0 || strcmp(symbol, "glUnmapBufferARB") == 0) {
            printf("LWJGL linkerhook: hooked glUnmapBuffer -> shadow buffer\n");
            return (jlong) glUnmapBuffer_hook;
        }
        if (strcmp(symbol, "glGenSamplers") == 0 || strcmp(symbol, "glGenSamplersOES") == 0) {
            void* sym = dlsym((void*) handle, symbol); if (sym) return (jlong) sym;
            return (jlong) glGenSamplers_fallback;
        }
        if (strcmp(symbol, "glBindSampler") == 0 || strcmp(symbol, "glBindSamplerOES") == 0) {
            void* sym = dlsym((void*) handle, symbol); if (sym) return (jlong) sym;
            return (jlong) glBindSampler_fallback;
        }
        if (strcmp(symbol, "glDeleteSamplers") == 0 || strcmp(symbol, "glDeleteSamplersOES") == 0) {
            void* sym = dlsym((void*) handle, symbol); if (sym) return (jlong) sym;
            return (jlong) glDeleteSamplers_fallback;
        }
        if (strcmp(symbol, "glSamplerParameteri") == 0 || strcmp(symbol, "glSamplerParameteriOES") == 0) {
            void* sym = dlsym((void*) handle, symbol); if (sym) return (jlong) sym;
            return (jlong) glSamplerParameteri_fallback;
        }
    }
    void* sym = dlsym((void*) handle, symbol);
    if (!sym && symbol && strncmp(symbol, "gl", 2) == 0) return (jlong) universal_stub_void;
    return (jlong) sym;
}

void installLwjglDlopenHook(JNIEnv *env) {
    LOGI("Installing LWJGL dlopen() and dlsym() hooks (BUILD v20260907-E)");
    printf("LWJGL linkerhook: installing dlopen/dlsym hooks (BUILD v20260907-E)\n");
    jclass dynamicLinkLoader = (*env)->FindClass(env, "org/lwjgl/system/linux/DynamicLinkLoader");
    if(dynamicLinkLoader == NULL) {
        LOGE("Failed to find the target class");
        printf("LWJGL linkerhook ERROR: Failed to find DynamicLinkLoader class\n");
        (*env)->ExceptionClear(env);
        return;
    }
    JNINativeMethod hooks[] = {
            {"ndlopen", "(JI)J", &ndlopen_bugfix},
            {"ndlsym", "(JJ)J", &ndlsym_hook}
    };
    if((*env)->RegisterNatives(env, dynamicLinkLoader, hooks, 2) != 0) {
        printf("LWJGL linkerhook: RegisterNatives failed\n");
        LOGE("Failed to register the hooked methods");
        printf("LWJGL linkerhook ERROR: Failed to register hooked methods\n");
        (*env)->ExceptionClear(env);
    }
    printf("LWJGL linkerhook: dlopen/dlsym hooks installed successfully\n");
}