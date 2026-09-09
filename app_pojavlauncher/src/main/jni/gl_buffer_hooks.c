//
// gl_buffer_hooks.c - Global exported shadow buffer implementations
// These symbols are exported so native dlsym(RTLD_DEFAULT, "glMapBufferRange") finds them first.
//
#include <dlfcn.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#include <stdio.h>

#define TAG "gl_buffer_hooks"
#include "log.h"

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
static char s_fallback_buffer[4 * 1024 * 1024]; // 4MB emergency fallback
static size_t s_fallback_offset = 0;

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
        case 0x8892: pname = 0x8894; break; // GL_ARRAY_BUFFER
        case 0x8893: pname = 0x8895; break; // GL_ELEMENT_ARRAY_BUFFER
        case 0x8A11: pname = 0x8A28; break; // GL_UNIFORM_BUFFER
        case 0x90D2: pname = 0x90D3; break; // GL_SHADER_STORAGE_BUFFER
        case 0x8F36: pname = 0x8F36; break; // GL_COPY_READ_BUFFER
        case 0x8F37: pname = 0x8F37; break; // GL_COPY_WRITE_BUFFER
        case 0x88EB: pname = 0x88ED; break; // GL_PIXEL_PACK_BUFFER
        case 0x88EC: pname = 0x88EF; break; // GL_PIXEL_UNPACK_BUFFER
        case 0x8C8E: pname = 0x8C8F; break; // GL_TRANSFORM_FEEDBACK_BUFFER
        case 0x90EE: pname = 0x90EE; break; // GL_DISPATCH_INDIRECT_BUFFER
        case 0x8F39: pname = 0x8F43; break; // GL_DRAW_INDIRECT_BUFFER
        default: pname = 0x8894; break;
    }
    int val = 0;
    real_glGetIntegerv(pname, &val);
    return (unsigned int) val;
}

// ========== GLOBAL EXPORTED SYMBOLS (LAYER C) ==========
// These must NOT be static so dlsym(RTLD_DEFAULT, ...) finds them.

__attribute__((visibility("default")))
void* glMapBufferRange(unsigned int target, long offset, long length, unsigned int access) {
    LOGI("GLOBAL glMapBufferRange called target=0x%X offset=%ld len=%ld access=0x%X", target, offset, length, access);

    if (length <= 0 || length > 67108864) length = 65536; // safe 64KB

    void* ptr = NULL;
    if (posix_memalign(&ptr, 64, (size_t)length) != 0 || !ptr) {
        ptr = malloc((size_t)length);
    }
    if (!ptr) ptr = calloc(1, (size_t)length);

    if (!ptr) {
        // emergency static fallback
        if (s_fallback_offset + (size_t)length > sizeof(s_fallback_buffer)) s_fallback_offset = 0;
        ptr = &s_fallback_buffer[s_fallback_offset];
        s_fallback_offset += (size_t)length;
        LOGW("Using emergency static fallback buffer");
    }

    unsigned int buffer_id = get_bound_buffer_id(target);

    pthread_mutex_lock(&g_shadowMutex);
    int slot = find_free_shadow_slot();
    if (slot >= 0) {
        g_shadowBuffers[slot].target = target;
        g_shadowBuffers[slot].buffer_id = buffer_id;
        g_shadowBuffers[slot].offset = offset;
        g_shadowBuffers[slot].length = length;
        g_shadowBuffers[slot].shadow_ptr = ptr;
        g_shadowBuffers[slot].is_shadow = 1;
        g_shadowBuffers[slot].in_use = 1;
    }
    pthread_mutex_unlock(&g_shadowMutex);

    // clear GL errors
    typedef unsigned int (*glGetError_pfn)(void);
    static glGetError_pfn real_glGetError = NULL;
    if (!real_glGetError) real_glGetError = (glGetError_pfn) dlsym(RTLD_DEFAULT, "glGetError");
    if (real_glGetError) while (real_glGetError() != 0) {}

    return ptr; // NEVER NULL
}

__attribute__((visibility("default")))
void* glMapBuffer(unsigned int target, unsigned int access) {
    // map entire buffer
    typedef void (*glGetBufferParameteriv_pfn)(unsigned int, unsigned int, int*);
    static glGetBufferParameteriv_pfn real_glGetBufferParameteriv = NULL;
    if (!real_glGetBufferParameteriv) {
        real_glGetBufferParameteriv = (glGetBufferParameteriv_pfn) dlsym(RTLD_DEFAULT, "glGetBufferParameteriv");
        if (!real_glGetBufferParameteriv)
            real_glGetBufferParameteriv = (glGetBufferParameteriv_pfn) dlsym(RTLD_DEFAULT, "glGetBufferParameterivARB");
    }
    int buf_size = 0;
    if (real_glGetBufferParameteriv) real_glGetBufferParameteriv(target, 0x8764 /* GL_BUFFER_SIZE */, &buf_size);
    long len = (buf_size > 0) ? buf_size : 65536;
    unsigned int rangeAccess = 0x0002; // GL_MAP_WRITE_BIT
    if (access == 0x88B8) rangeAccess = 0x0001; // GL_READ_ONLY
    else if (access == 0x88BA) rangeAccess = 0x0001 | 0x0002; // GL_READ_WRITE
    return glMapBufferRange(target, 0, len, rangeAccess);
}

__attribute__((visibility("default")))
int glUnmapBuffer(unsigned int target) {
    LOGI("GLOBAL glUnmapBuffer called target=0x%X", target);

    typedef void (*glBindBuffer_pfn)(unsigned int, unsigned int);
    typedef void (*glBufferSubData_pfn)(unsigned int, long, long, const void*);
    typedef unsigned int (*glGetError_pfn)(void);

    static glBindBuffer_pfn real_glBindBuffer = NULL;
    static glBufferSubData_pfn real_glBufferSubData = NULL;
    static glGetError_pfn real_glGetError = NULL;

    if (!real_glBindBuffer) real_glBindBuffer = (glBindBuffer_pfn) dlsym(RTLD_DEFAULT, "glBindBuffer");
    if (!real_glBufferSubData) {
        real_glBufferSubData = (glBufferSubData_pfn) dlsym(RTLD_DEFAULT, "glBufferSubData");
        if (!real_glBufferSubData)
            real_glBufferSubData = (glBufferSubData_pfn) dlsym(RTLD_DEFAULT, "glBufferSubDataARB");
    }
    if (!real_glGetError) real_glGetError = (glGetError_pfn) dlsym(RTLD_DEFAULT, "glGetError");

    unsigned int current_buffer_id = get_bound_buffer_id(target);
    int found_slot = -1;

    pthread_mutex_lock(&g_shadowMutex);
    for (int i = 0; i < g_shadowCount; i++) {
        if (g_shadowBuffers[i].in_use && g_shadowBuffers[i].is_shadow &&
            g_shadowBuffers[i].target == target &&
            (current_buffer_id == 0 || g_shadowBuffers[i].buffer_id == current_buffer_id)) {
            found_slot = i;
            break;
        }
    }
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
        ShadowBufferMap entry = g_shadowBuffers[found_slot];
        g_shadowBuffers[found_slot].in_use = 0;
        g_shadowBuffers[found_slot].is_shadow = 0;
        g_shadowBuffers[found_slot].shadow_ptr = NULL;
        pthread_mutex_unlock(&g_shadowMutex);

        if (entry.shadow_ptr && entry.length > 0) {
            // CRITICAL: bind the correct buffer BEFORE uploading
            if (real_glBindBuffer && entry.buffer_id != 0) {
                real_glBindBuffer(target, entry.buffer_id);
            }
            if (real_glBufferSubData) {
                real_glBufferSubData(target, entry.offset, entry.length, entry.shadow_ptr);
            }
            // free only if not the static fallback
            if ((char*)entry.shadow_ptr < s_fallback_buffer ||
                (char*)entry.shadow_ptr >= (s_fallback_buffer + sizeof(s_fallback_buffer))) {
                free(entry.shadow_ptr);
            }
        }
    } else {
        pthread_mutex_unlock(&g_shadowMutex);
    }

    if (real_glGetError) while (real_glGetError() != 0) {}
    return 1; // always GL_TRUE
}
