#ifndef TURBO_V1_HOOKS_H
#define TURBO_V1_HOOKS_H

#include "turbo_v1_core.h"

#ifdef __cplusplus
extern "C" {
#endif

void* turbo_v1_glMapBufferRange_hook(GLenum target, GLintptr offset, GLsizeiptr length, GLbitfield access);
GLboolean turbo_v1_glUnmapBuffer_hook(GLenum target);
void turbo_v1_glBufferStorage_hook(GLenum target, GLsizeiptr size, const void* data, GLbitfield flags);
void turbo_v1_glFlushMappedBufferRange_hook(GLenum target, GLintptr offset, GLsizeiptr length);

void turbo_v1_glCreateBuffers_hook(GLsizei n, GLuint* buffers);
void turbo_v1_glCreateVertexArrays_hook(GLsizei n, GLuint* arrays);
void turbo_v1_glCreateTextures_hook(GLenum target, GLsizei n, GLuint* textures);
void turbo_v1_glNamedBufferStorage_hook(GLuint buffer, GLsizeiptr size, const void* data, GLbitfield flags);
void turbo_v1_glNamedBufferData_hook(GLuint buffer, GLsizeiptr size, const void* data, GLenum usage);
void turbo_v1_glNamedBufferSubData_hook(GLuint buffer, GLintptr offset, GLsizeiptr size, const void* data);
void* turbo_v1_glMapNamedBufferRange_hook(GLuint buffer, GLintptr offset, GLsizeiptr length, GLbitfield access);
GLboolean turbo_v1_glUnmapNamedBuffer_hook(GLuint buffer);

void turbo_v1_glDispatchCompute_hook(GLuint x, GLuint y, GLuint z);
void turbo_v1_glMemoryBarrier_hook(GLbitfield barriers);

void turbo_v1_glMultiDrawArraysIndirect_hook(GLenum mode, const void* indirect, GLsizei drawcount, GLsizei stride);
void turbo_v1_glMultiDrawElementsIndirect_hook(GLenum mode, GLenum type, const void* indirect, GLsizei drawcount, GLsizei stride);

void turbo_v1_glGenSamplers_hook(GLsizei count, GLuint* samplers);
void turbo_v1_glBindSampler_hook(GLuint unit, GLuint sampler);
void turbo_v1_glSamplerParameteri_hook(GLuint sampler, GLenum pname, GLint param);
void turbo_v1_glSamplerParameterf_hook(GLuint sampler, GLenum pname, GLfloat param);
void turbo_v1_glDeleteSamplers_hook(GLsizei count, const GLuint* samplers);

void turbo_v1_glGenQueries_hook(GLsizei n, GLuint* ids);
void turbo_v1_glDeleteQueries_hook(GLsizei n, const GLuint* ids);
void turbo_v1_glBeginQuery_hook(GLenum target, GLuint id);
void turbo_v1_glEndQuery_hook(GLenum target);
void turbo_v1_glQueryCounter_hook(GLuint id, GLenum target);

const GLubyte* turbo_v1_glGetString_hook(GLenum name);
const GLubyte* turbo_v1_glGetStringi_hook(GLenum name, GLuint index);

void turbo_v1_glBindTextureUnit_hook(GLuint unit, GLuint texture);
const char* turbo_v1_translate_shader_source(const char* source, int stage);

#ifdef __cplusplus
}
#endif

#endif // TURBO_V1_HOOKS_H
