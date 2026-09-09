#include "turbo_v1_buffer.h"
#include <dlfcn.h>
#include <stdlib.h>
#include <string.h>

namespace turbo_v1 {

namespace buffer {

static Entry g_slots[TURBO_V1_MAX_SLOTS];
static int g_count = 0;
static pthread_mutex_t g_mutex = PTHREAD_MUTEX_INITIALIZER;

static int find_free_slot() {
    for (int i = 0; i < g_count; i++) {
        if (!g_slots[i].in_use) return i;
    }
    if (g_count < TURBO_V1_MAX_SLOTS) return g_count++;
    for (int i = 0; i < TURBO_V1_MAX_SLOTS; i++) {
        if (!g_slots[i].in_use) return i;
    }
    return -1;
}

GLuint get_bound_buffer_id(GLenum target) {
    typedef void (*PFN_glGetIntegerv)(GLenum, GLint*);
    static PFN_glGetIntegerv real_fn = nullptr;
    if (!real_fn) real_fn = (PFN_glGetIntegerv) dlsym(RTLD_DEFAULT, "glGetIntegerv");
    if (!real_fn) return 0;

    GLenum pname = 0x8894; // GL_ARRAY_BUFFER_BINDING
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

    GLint val = 0;
    real_fn(pname, &val);
    return (GLuint)val;
}

void init() {
    pthread_mutex_lock(&g_mutex);
    memset(g_slots, 0, sizeof(g_slots));
    g_count = 0;
    pthread_mutex_unlock(&g_mutex);
    LOGI("TurboV1: 64-byte aligned shadow buffer pool initialized (%d slots)", TURBO_V1_MAX_SLOTS);
}

void* map(GLenum target, GLintptr offset, GLsizeiptr length, GLbitfield access) {
    GLuint buffer_id = get_bound_buffer_id(target);
    GLsizeiptr alloc_len = (length > 0) ? length : 65536;

    void* ptr = nullptr;
    if (posix_memalign(&ptr, 64, alloc_len) != 0 || !ptr) {
        ptr = malloc(alloc_len);
    }
    if (!ptr) ptr = calloc(1, alloc_len);
    if (!ptr) {
        LOGE("TurboV1: Buffer allocation failed for length %ld", (long)alloc_len);
        return nullptr;
    }

    pthread_mutex_lock(&g_mutex);
    int slot = find_free_slot();
    if (slot < 0) {
        pthread_mutex_unlock(&g_mutex);
        LOGW("TurboV1: Slots full, returning unmanaged aligned buffer");
        return ptr;
    }

    g_slots[slot].target = target;
    g_slots[slot].buffer_id = buffer_id;
    g_slots[slot].offset = offset;
    g_slots[slot].length = alloc_len;
    g_slots[slot].ptr = ptr;
    g_slots[slot].in_use = true;
    pthread_mutex_unlock(&g_mutex);

    typedef GLenum (*PFN_glGetError)(void);
    static PFN_glGetError real_err = nullptr;
    if (!real_err) real_err = (PFN_glGetError) dlsym(RTLD_DEFAULT, "glGetError");
    if (real_err) { GLenum e; do { e = real_err(); } while (e != GL_NO_ERROR); }

    return ptr;
}

GLboolean unmap(GLenum target) {
    typedef void (*PFN_glBufferSubData)(GLenum, GLintptr, GLsizeiptr, const void*);
    typedef void (*PFN_glBindBuffer)(GLenum, GLuint);
    typedef GLenum (*PFN_glGetError)(void);

    static PFN_glBufferSubData real_sub = nullptr;
    static PFN_glBindBuffer real_bind = nullptr;
    static PFN_glGetError real_err = nullptr;

    if (!real_sub) real_sub = (PFN_glBufferSubData) dlsym(RTLD_DEFAULT, "glBufferSubData");
    if (!real_bind) real_bind = (PFN_glBindBuffer) dlsym(RTLD_DEFAULT, "glBindBuffer");
    if (!real_err) real_err = (PFN_glGetError) dlsym(RTLD_DEFAULT, "glGetError");

    GLuint current_id = get_bound_buffer_id(target);
    int found = -1;

    pthread_mutex_lock(&g_mutex);
    for (int i = 0; i < g_count; i++) {
        if (g_slots[i].in_use && g_slots[i].target == target &&
            (current_id == 0 || g_slots[i].buffer_id == current_id)) {
            found = i; break;
        }
    }
    if (found < 0) {
        for (int i = 0; i < g_count; i++) {
            if (g_slots[i].in_use && g_slots[i].target == target) {
                found = i; break;
            }
        }
    }

    if (found >= 0) {
        Entry entry = g_slots[found];
        g_slots[found].in_use = false;
        g_slots[found].ptr = nullptr;
        pthread_mutex_unlock(&g_mutex);

        if (entry.ptr) {
            if (real_bind && entry.buffer_id != 0) {
                real_bind(target, entry.buffer_id);
            }
            if (real_sub) real_sub(target, entry.offset, entry.length, entry.ptr);
            free(entry.ptr);
        }
        if (real_err) { GLenum e; do { e = real_err(); } while (e != GL_NO_ERROR); }
        return GL_TRUE;
    }
    pthread_mutex_unlock(&g_mutex);

    typedef GLboolean (*PFN_glUnmapBuffer)(GLenum);
    static PFN_glUnmapBuffer real_unmap = nullptr;
    if (!real_unmap) real_unmap = (PFN_glUnmapBuffer) dlsym(RTLD_DEFAULT, "glUnmapBuffer");
    GLboolean res = GL_TRUE;
    if (real_unmap) res = real_unmap(target);
    if (real_err) { GLenum e; do { e = real_err(); } while (e != GL_NO_ERROR); }
    return res ? res : GL_TRUE;
}

GLboolean unmap_ptr(void* ptr) {
    if (!ptr) return GL_TRUE;
    typedef void (*PFN_glBufferSubData)(GLenum, GLintptr, GLsizeiptr, const void*);
    typedef void (*PFN_glBindBuffer)(GLenum, GLuint);
    static PFN_glBufferSubData real_sub = nullptr;
    static PFN_glBindBuffer real_bind = nullptr;

    if (!real_sub) real_sub = (PFN_glBufferSubData) dlsym(RTLD_DEFAULT, "glBufferSubData");
    if (!real_bind) real_bind = (PFN_glBindBuffer) dlsym(RTLD_DEFAULT, "glBindBuffer");

    pthread_mutex_lock(&g_mutex);
    for (int i = 0; i < g_count; i++) {
        if (g_slots[i].in_use && g_slots[i].ptr == ptr) {
            Entry entry = g_slots[i];
            g_slots[i].in_use = false;
            g_slots[i].ptr = nullptr;
            pthread_mutex_unlock(&g_mutex);

            if (entry.ptr) {
                if (real_bind && entry.buffer_id != 0) {
                    real_bind(entry.target, entry.buffer_id);
                }
                if (real_sub) real_sub(entry.target, entry.offset, entry.length, entry.ptr);
            }
            free(ptr);
            return GL_TRUE;
        }
    }
    pthread_mutex_unlock(&g_mutex);
    free(ptr);
    return GL_TRUE;
}

void flush_range(GLenum target, GLintptr offset, GLsizeiptr length) {
    typedef void (*PFN_glBufferSubData)(GLenum, GLintptr, GLsizeiptr, const void*);
    typedef void (*PFN_glBindBuffer)(GLenum, GLuint);
    static PFN_glBufferSubData real_sub = nullptr;
    static PFN_glBindBuffer real_bind = nullptr;

    if (!real_sub) real_sub = (PFN_glBufferSubData) dlsym(RTLD_DEFAULT, "glBufferSubData");
    if (!real_bind) real_bind = (PFN_glBindBuffer) dlsym(RTLD_DEFAULT, "glBindBuffer");

    pthread_mutex_lock(&g_mutex);
    for (int i = 0; i < g_count; i++) {
        if (g_slots[i].in_use && g_slots[i].target == target) {
            if (real_bind && g_slots[i].buffer_id != 0) {
                real_bind(target, g_slots[i].buffer_id);
            }
            if (real_sub && g_slots[i].ptr) {
                real_sub(target, offset, length, (char*)g_slots[i].ptr + offset - g_slots[i].offset);
            }
            break;
        }
    }
    pthread_mutex_unlock(&g_mutex);
}

} // namespace buffer

} // namespace turbo_v1
