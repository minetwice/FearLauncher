#include "turbo_v1_buffer.h"
#include <dlfcn.h>
#include <stdlib.h>

namespace turbo_v1 {

namespace buffer {

typedef void* (*PFN_glMapBufferRange)(GLenum, GLintptr, GLsizeiptr, GLbitfield);
typedef GLboolean (*PFN_glUnmapBuffer)(GLenum);
typedef void (*PFN_glFlushMappedBufferRange)(GLenum, GLintptr, GLsizeiptr);
typedef void (*PFN_glGetIntegerv)(GLenum, GLint*);

static PFN_glMapBufferRange          real_glMapBufferRange = nullptr;
static PFN_glUnmapBuffer             real_glUnmapBuffer = nullptr;
static PFN_glFlushMappedBufferRange  real_glFlushMappedBufferRange = nullptr;
static PFN_glGetIntegerv             real_glGetIntegerv = nullptr;

static void resolve_fns() {
    if (!real_glMapBufferRange) {
        real_glMapBufferRange = (PFN_glMapBufferRange) dlsym(RTLD_DEFAULT, "glMapBufferRange");
        if (!real_glMapBufferRange) real_glMapBufferRange = (PFN_glMapBufferRange) dlsym(RTLD_DEFAULT, "glMapBufferRangeEXT");
    }
    if (!real_glUnmapBuffer) {
        real_glUnmapBuffer = (PFN_glUnmapBuffer) dlsym(RTLD_DEFAULT, "glUnmapBuffer");
        if (!real_glUnmapBuffer) real_glUnmapBuffer = (PFN_glUnmapBuffer) dlsym(RTLD_DEFAULT, "glUnmapBufferOES");
    }
    if (!real_glFlushMappedBufferRange) {
        real_glFlushMappedBufferRange = (PFN_glFlushMappedBufferRange) dlsym(RTLD_DEFAULT, "glFlushMappedBufferRange");
        if (!real_glFlushMappedBufferRange) real_glFlushMappedBufferRange = (PFN_glFlushMappedBufferRange) dlsym(RTLD_DEFAULT, "glFlushMappedBufferRangeEXT");
    }
    if (!real_glGetIntegerv) {
        real_glGetIntegerv = (PFN_glGetIntegerv) dlsym(RTLD_DEFAULT, "glGetIntegerv");
    }
}

GLuint get_bound_buffer_id(GLenum target) {
    resolve_fns();
    if (!real_glGetIntegerv) return 0;

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
    real_glGetIntegerv(pname, &val);
    return (GLuint)val;
}

void init() {
    resolve_fns();
    LOGI("TurboV1: Direct OpenGL ES driver buffer mapping initialized (shadow buffering removed)");
}

void* map(GLenum target, GLintptr offset, GLsizeiptr length, GLbitfield access) {
    resolve_fns();
    if (real_glMapBufferRange) {
        return real_glMapBufferRange(target, offset, length, access);
    }
    return nullptr;
}

GLboolean unmap(GLenum target) {
    resolve_fns();
    if (real_glUnmapBuffer) {
        return real_glUnmapBuffer(target);
    }
    return GL_TRUE;
}

GLboolean unmap_ptr(void* ptr) {
    (void)ptr;
    return unmap(GL_ARRAY_BUFFER);
}

void flush_range(GLenum target, GLintptr offset, GLsizeiptr length) {
    resolve_fns();
    if (real_glFlushMappedBufferRange) {
        real_glFlushMappedBufferRange(target, offset, length);
    }
}

} // namespace buffer

} // namespace turbo_v1
