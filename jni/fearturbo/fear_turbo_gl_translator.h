#ifndef FEAR_TURBO_GL_TRANSLATOR_H
#define FEAR_TURBO_GL_TRANSLATOR_H

#include "fear_turbo_core.h"
#include <GLES3/gl3.h>
#include <functional>

namespace fear_turbo {

// ============================================================================
// OpenGL-to-OpenGL ES Function Translation Layer
// ============================================================================
// Desktop OpenGL functions that don't exist in GLES are translated here.
// We intercept them via the lwjgl_dlopen_hook and translate each call.
// ============================================================================

// --- Buffer Operations ---
void translate_glBufferStorage(GLenum target, GLsizeiptr size, const void* data, GLbitfield flags);
void* translate_glMapBufferRange(GLenum target, GLintptr offset, GLsizeiptr length, GLbitfield access);
GLboolean translate_glUnmapBuffer(GLenum target);
void translate_glFlushMappedBufferRange(GLenum target, GLintptr offset, GLsizeiptr length);

// --- Direct State Access (GL_ARB_direct_state_access) ---
void translate_glNamedBufferStorage(GLuint buffer, GLsizeiptr size, const void* data, GLbitfield flags);
void translate_glNamedBufferData(GLuint buffer, GLsizeiptr size, const void* data, GLenum usage);
void translate_glNamedBufferSubData(GLuint buffer, GLintptr offset, GLsizeiptr size, const void* data);
void* translate_glMapNamedBufferRange(GLuint buffer, GLintptr offset, GLsizeiptr length, GLbitfield access);
GLboolean translate_glUnmapNamedBuffer(GLuint buffer);
void translate_glCreateBuffers(GLsizei n, GLuint* buffers);
void translate_glCreateVertexArrays(GLsizei n, GLuint* arrays);
void translate_glCreateTextures(GLenum target, GLsizei n, GLuint* textures);
void translate_glTextureBufferRange(GLuint texture, GLenum internalFormat, GLuint buffer, GLintptr offset, GLsizeiptr size);

// --- Shader Storage Buffer Objects ---
void translate_glShaderStorageBlockBinding(GLuint program, GLuint storageBlockIndex, GLuint storageBlockBinding);

// --- Compute Shaders ---
void translate_glDispatchCompute(GLuint num_groups_x, GLuint num_groups_y, GLuint num_groups_z);
void translate_glDispatchComputeIndirect(GLintptr indirect);
void translate_glMemoryBarrier(GLbitfield barriers);

// --- Multi Draw Indirect ---
void translate_glMultiDrawArraysIndirect(GLenum mode, const void* indirect, GLsizei drawcount, GLsizei stride);
void translate_glMultiDrawElementsIndirect(GLenum mode, GLenum type, const void* indirect, GLsizei drawcount, GLsizei stride);

// --- Texture Operations ---
void translate_glTextureView(GLuint texture, GLenum target, GLuint origtexture, GLenum internalformat, GLuint minlevel, GLuint numlevels, GLuint minlayer, GLuint numlayers);
void translate_glBindTextureUnit(GLuint unit, GLuint texture);

// --- Sampler Objects ---
void translate_glGenSamplers(GLsizei count, GLuint* samplers);
void translate_glBindSampler(GLuint unit, GLuint sampler);
void translate_glSamplerParameteri(GLuint sampler, GLenum pname, GLint param);
void translate_glSamplerParameterf(GLuint sampler, GLenum pname, GLfloat param);
void translate_glDeleteSamplers(GLsizei count, const GLuint* samplers);

// --- Query Objects ---
void translate_glGenQueries(GLsizei n, GLuint* ids);
void translate_glDeleteQueries(GLsizei n, const GLuint* ids);
void translate_glBeginQuery(GLenum target, GLuint id);
void translate_glEndQuery(GLenum target);
void translate_glQueryCounter(GLuint id, GLenum target);
void translate_glGetQueryObjectiv(GLuint id, GLenum pname, GLint* params);
void translate_glGetQueryObjectui64v(GLuint id, GLenum pname, GLuint64* params);

// --- String overrides ---
const GLubyte* translate_glGetStringi(GLenum name, GLuint index);
const GLubyte* translate_glGetString(GLenum name);

} // namespace fear_turbo

#endif // FEAR_TURBO_GL_TRANSLATOR_H
