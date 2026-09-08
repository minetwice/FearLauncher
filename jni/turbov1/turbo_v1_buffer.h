#ifndef TURBO_V1_BUFFER_H
#define TURBO_V1_BUFFER_H

#include "turbo_v1_core.h"
#include <pthread.h>

namespace turbo_v1 {

namespace buffer {

struct Entry {
    GLenum   target;
    GLuint   buffer_id;
    GLintptr offset;
    GLsizeiptr length;
    void*    ptr;
    bool     in_use;
};

#define TURBO_V1_MAX_SLOTS 4096

void init();

void* map(GLenum target, GLintptr offset, GLsizeiptr length, GLbitfield access);
GLboolean unmap(GLenum target);
GLboolean unmap_ptr(void* ptr);
void flush_range(GLenum target, GLintptr offset, GLsizeiptr length);

GLuint get_bound_buffer_id(GLenum target);

} // namespace buffer

} // namespace turbo_v1

#endif // TURBO_V1_BUFFER_H
