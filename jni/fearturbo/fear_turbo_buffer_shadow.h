#ifndef FEAR_TURBO_BUFFER_SHADOW_H
#define FEAR_TURBO_BUFFER_SHADOW_H

#include "fear_turbo_core.h"
#include <GLES3/gl3.h>
#include <pthread.h>

namespace fear_turbo {

namespace buffer_shadow {

// Shadow buffer entry — tracks a CPU-side buffer used in place of glMapBufferRange
struct ShadowEntry {
    GLenum   target;
    GLuint   buffer_id;
    GLintptr offset;
    GLsizeiptr length;
    void*    ptr;
    bool     in_use;
};

#define MAX_SHADOW_SLOTS 4096

// Initialize the shadow buffer system
void init();

// Map a buffer range — returns an aligned CPU shadow buffer
// Avoids driver bugs on mobile GPUs (Mali / Adreno)
void* map(GLenum target, GLintptr offset, GLsizeiptr length, GLbitfield access);

// Unmap a buffer — uploads shadow data to GPU via glBufferSubData, then frees
GLboolean unmap(GLenum target);

// Unmap a specific buffer pointer
GLboolean unmap_ptr(void* ptr);

// Flush a range of the shadow buffer to the GPU
void flush_range(GLenum target, GLintptr offset, GLsizeiptr length);

// Get the currently bound buffer ID for a target
GLuint get_bound_buffer_id(GLenum target);

} // namespace buffer_shadow

} // namespace fear_turbo

#endif // FEAR_TURBO_BUFFER_SHADOW_H
