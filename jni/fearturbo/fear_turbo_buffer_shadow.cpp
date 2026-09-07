#include "fear_turbo_buffer_shadow.h"
#include <dlfcn.h>
#include <stdlib.h>

namespace fear_turbo {

namespace buffer_shadow {

static ShadowEntry g_slots[MAX_SHADOW_SLOTS];
static int g_count = 0;
static pthread_mutex_t g_mutex = PTHREAD_MUTEX_INITIALIZER;

static int find_free_slot() {
    for (int i = 0; i < g_count; i++) {
        if (!g_slots[i].in_use) return i;
    }
    if (g_count < MAX_SHADOW_SLOTS) return g_count++;
    if (g_slots[0].ptr) { free(g_slots[0].ptr); g_slots[0].ptr = nullptr; }
    return 0;
}

GLuint get_bound_buffer_id(GLenum target) {
    typedef void (*PFN_glGetIntegerv)(GLenum, GLint*);
    static PFN_glGetIntegerv real_fn = nullptr;
    if (!real_fn) real_fn = (PFN_glGetIntegerv) dlsym(RTLD_DEFAULT, "glGetIntegerv");
    if (!real_fn) return 0;

    GLenum pname = 0x8894; // GL_ARRAY_BUFFER_BINDING
    if (target == 0x8893) pname = 0x8895;
    else if (target == 0x8A11) pname = 0x8A28;
    else if (target == 0x90D2) pname = 0x90D3;

    GLint val = 0;
    real_fn(pname, &val);
    return (GLuint)val;
}

void init() {
    memset(g_slots, 0, sizeof(g_slots));
    g_count = 0;
    LOGI("FearTurbo: Shadow buffer system initialized (%d max slots)", MAX_SHADOW_SLOTS);
}

void* map(GLenum target, GLintptr offset, GLsizeiptr length, GLbitfield access) {
    static int callCount = 0;
    if (callCount < 5) {
        LOGI("FearTurbo: shadow map(target=0x%X, offset=%ld, len=%ld, access=0x%X)", target, (long)offset, (long)length, access);
        callCount++;
    }

    GLuint buffer_id = get_bound_buffer_id(target);

    GLsizeiptr alloc_len = (length > 0) ? length : 65536;
    void* ptr = malloc(alloc_len);
    if (!ptr) ptr = calloc(1, alloc_len);
    if (!ptr) { alloc_len = 65536; ptr = malloc(alloc_len); }
    if (!ptr) {
        LOGE("FearTurbo: shadow buffer malloc FAILED for len=%ld", (long)alloc_len);
        return nullptr;
    }

    pthread_mutex_lock(&g_mutex);
    int slot = find_free_slot();
    g_slots[slot].target = target;
    g_slots[slot].buffer_id = buffer_id;
    g_slots[slot].offset = offset;
    g_slots[slot].length = alloc_len;
    g_slots[slot].ptr = ptr;
    g_slots[slot].in_use = true;
    pthread_mutex_unlock(&g_mutex);

    if (callCount <= 5) {
        LOGI("FearTurbo: shadow buffer slot=%d target=0x%X bufID=%u len=%ld", slot, target, buffer_id, (long)alloc_len);
    }

    typedef GLenum (*PFN_glGetError)(void);
    static PFN_glGetError real_err = nullptr;
    if (!real_err) real_err = (PFN_glGetError) dlsym(RTLD_DEFAULT, "glGetError");
    if (real_err) { GLenum e; do { e = real_err(); } while (e != GL_NO_ERROR); }

    return ptr;
}

GLboolean unmap(GLenum target) {
    typedef void (*PFN_glBufferSubData)(GLenum, GLintptr, GLsizeiptr, const void*);
    typedef GLenum (*PFN_glGetError)(void);
    static PFN_glBufferSubData real_sub = nullptr;
    static PFN_glGetError real_err = nullptr;
    if (!real_sub) real_sub = (PFN_glBufferSubData) dlsym(RTLD_DEFAULT, "glBufferSubData");
    if (!real_err) real_err = (PFN_glGetError) dlsym(RTLD_DEFAULT, "glGetError");

    GLuint current_id = get_bound_buffer_id(target);
    int found = -1;

    pthread_mutex_lock(&g_mutex);
    for (int i = 0; i < g_count; i++) {
        if (g_slots[i].in_use && g_slots[i].buffer_id == current_id && current_id != 0) {
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
        ShadowEntry& entry = g_slots[found];
        void* shadow_ptr = entry.ptr;
        GLintptr shadow_offset = entry.offset;
        GLsizeiptr shadow_length = entry.length;
        entry.in_use = false;
        entry.ptr = nullptr;
        pthread_mutex_unlock(&g_mutex);

        if (shadow_ptr) {
            if (real_sub) real_sub(target, shadow_offset, shadow_length, shadow_ptr);
            free(shadow_ptr);
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

void flush_range(GLenum target, GLintptr offset, GLsizeiptr length) {
    typedef void (*PFN_glBufferSubData)(GLenum, GLintptr, GLsizeiptr, const void*);
    static PFN_glBufferSubData real_sub = nullptr;
    if (!real_sub) real_sub = (PFN_glBufferSubData) dlsym(RTLD_DEFAULT, "glBufferSubData");

    pthread_mutex_lock(&g_mutex);
    for (int i = 0; i < g_count; i++) {
        if (g_slots[i].in_use && g_slots[i].target == target) {
            if (real_sub && g_slots[i].ptr) {
                real_sub(target, offset, length, (char*)g_slots[i].ptr + offset - g_slots[i].offset);
            }
            break;
        }
    }
    pthread_mutex_unlock(&g_mutex);

    typedef GLenum (*PFN_glGetError)(void);
    static PFN_glGetError real_err = nullptr;
    if (!real_err) real_err = (PFN_glGetError) dlsym(RTLD_DEFAULT, "glGetError");
    if (real_err) { GLenum e; do { e = real_err(); } while (e != GL_NO_ERROR); }
}

} // namespace buffer_shadow

} // namespace fear_turbo
