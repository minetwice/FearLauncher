#ifndef FEAR_GL_EMULATION_H
#define FEAR_GL_EMULATION_H

#include <GLES3/gl32.h>

extern "C" {

void fear_glMemoryBarrier(GLbitfield barriers);
void fear_glTextureBarrier();
void fear_glBufferStorage(GLenum target, GLsizeiptr size, const void* data, GLbitfield flags);
void fear_glClearTexImage(GLuint texture, GLint level, GLenum format, GLenum type, const void* data);
void fear_glClearTexSubImage(GLuint texture, GLint level, GLint xoffset, GLint yoffset, GLint zoffset, GLsizei width, GLsizei height, GLsizei depth, GLenum format, GLenum type, const void* data);
void fear_glMultiDrawArrays(GLenum mode, const GLint* first, const GLsizei* count, GLsizei drawcount);
void fear_glMultiDrawElements(GLenum mode, const GLsizei* count, GLenum type, const void* const* indices, GLsizei drawcount);
void fear_glInvalidateFramebuffer(GLenum target, GLsizei numAttachments, const GLenum* attachments);
void fear_glCreateBuffers(GLsizei n, GLuint* buffers);
void fear_glNamedBufferData(GLuint buffer, GLsizeiptr size, const void* data, GLenum usage);
void fear_glNamedBufferSubData(GLuint buffer, GLintptr offset, GLsizeiptr size, const void* data);
void fear_glBindTextureUnit(GLuint unit, GLuint texture);

// Buffer Mapping Fallback API
void* fear_glMapBufferRange(GLenum target, GLintptr offset, GLsizeiptr length, GLbitfield access);
void* fear_glMapBuffer(GLenum target, GLenum access);
GLboolean fear_glUnmapBuffer(GLenum target);

// Sampler Emulation API
void fear_glGenSamplers(GLsizei count, GLuint* samplers);
void fear_glBindSampler(GLuint unit, GLuint sampler);
void fear_glDeleteSamplers(GLsizei count, const GLuint* samplers);
GLboolean fear_glIsSampler(GLuint sampler);
void fear_glSamplerParameteri(GLuint sampler, GLenum pname, GLint param);
void fear_glSamplerParameterf(GLuint sampler, GLenum pname, GLfloat param);
void fear_glSamplerParameteriv(GLuint sampler, GLenum pname, const GLint* param);
void fear_glSamplerParameterfv(GLuint sampler, GLenum pname, const GLfloat* param);

// Module 2: Extension Emulation API
uint64_t fear_glGetTextureHandleARB(GLuint texture);
void fear_glMakeTextureHandleResidentARB(uint64_t handle);
void fear_glMakeTextureHandleNonResidentARB(uint64_t handle);
void fear_glBindImageTexture(GLuint unit, GLuint texture, GLint level, GLboolean layered, GLint layer, GLenum access, GLenum format);

}

#endif // FEAR_GL_EMULATION_H
