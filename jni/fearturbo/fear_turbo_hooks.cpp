#include "fear_turbo_core.h"
#include "fear_turbo_gl_translator.h"
#include "fear_turbo_buffer_shadow.h"
#include "fear_turbo_shader_codegen.h"
#include <dlfcn.h>
#include <jni.h>
#include <android/log.h>
#include <string.h>

#define LOG_TAG "FearTurbo"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ============================================================================
// Fear Turbo Hook System
// ============================================================================
// This file provides the hook functions that get called via the
// lwjgl_dlopen_hook.c ndlsym_hook and eglGetProcAddress_hook.
// These functions are the actual entry points that LWJGL calls
// when it resolves OpenGL functions.
// ============================================================================

extern "C" {

// --- Buffer Operations ---
void* fear_turbo_glMapBufferRange_hook(GLenum target, GLintptr offset, GLsizeiptr length, GLbitfield access) {
    return fear_turbo::translate_glMapBufferRange(target, offset, length, access);
}

GLboolean fear_turbo_glUnmapBuffer_hook(GLenum target) {
    return fear_turbo::translate_glUnmapBuffer(target);
}

void fear_turbo_glBufferStorage_hook(GLenum target, GLsizeiptr size, const void* data, GLbitfield flags) {
    fear_turbo::translate_glBufferStorage(target, size, data, flags);
}

void fear_turbo_glFlushMappedBufferRange_hook(GLenum target, GLintptr offset, GLsizeiptr length) {
    fear_turbo::translate_glFlushMappedBufferRange(target, offset, length);
}

// --- DSA ---
void fear_turbo_glCreateBuffers_hook(GLsizei n, GLuint* buffers) {
    fear_turbo::translate_glCreateBuffers(n, buffers);
}

void fear_turbo_glCreateVertexArrays_hook(GLsizei n, GLuint* arrays) {
    fear_turbo::translate_glCreateVertexArrays(n, arrays);
}

void fear_turbo_glCreateTextures_hook(GLenum target, GLsizei n, GLuint* textures) {
    fear_turbo::translate_glCreateTextures(target, n, textures);
}

void fear_turbo_glNamedBufferStorage_hook(GLuint buffer, GLsizeiptr size, const void* data, GLbitfield flags) {
    fear_turbo::translate_glNamedBufferStorage(buffer, size, data, flags);
}

void fear_turbo_glNamedBufferData_hook(GLuint buffer, GLsizeiptr size, const void* data, GLenum usage) {
    fear_turbo::translate_glNamedBufferData(buffer, size, data, usage);
}

void fear_turbo_glNamedBufferSubData_hook(GLuint buffer, GLintptr offset, GLsizeiptr size, const void* data) {
    fear_turbo::translate_glNamedBufferSubData(buffer, offset, size, data);
}

void* fear_turbo_glMapNamedBufferRange_hook(GLuint buffer, GLintptr offset, GLsizeiptr length, GLbitfield access) {
    return fear_turbo::translate_glMapNamedBufferRange(buffer, offset, length, access);
}

GLboolean fear_turbo_glUnmapNamedBuffer_hook(GLuint buffer) {
    return fear_turbo::translate_glUnmapNamedBuffer(buffer);
}

// --- Compute ---
void fear_turbo_glDispatchCompute_hook(GLuint x, GLuint y, GLuint z) {
    fear_turbo::translate_glDispatchCompute(x, y, z);
}

void fear_turbo_glMemoryBarrier_hook(GLbitfield barriers) {
    fear_turbo::translate_glMemoryBarrier(barriers);
}

// --- Multi Draw Indirect ---
void fear_turbo_glMultiDrawArraysIndirect_hook(GLenum mode, const void* indirect, GLsizei drawcount, GLsizei stride) {
    fear_turbo::translate_glMultiDrawArraysIndirect(mode, indirect, drawcount, stride);
}

void fear_turbo_glMultiDrawElementsIndirect_hook(GLenum mode, GLenum type, const void* indirect, GLsizei drawcount, GLsizei stride) {
    fear_turbo::translate_glMultiDrawElementsIndirect(mode, type, indirect, drawcount, stride);
}

// --- Samplers ---
void fear_turbo_glGenSamplers_hook(GLsizei count, GLuint* samplers) {
    fear_turbo::translate_glGenSamplers(count, samplers);
}

void fear_turbo_glBindSampler_hook(GLuint unit, GLuint sampler) {
    fear_turbo::translate_glBindSampler(unit, sampler);
}

void fear_turbo_glSamplerParameteri_hook(GLuint sampler, GLenum pname, GLint param) {
    fear_turbo::translate_glSamplerParameteri(sampler, pname, param);
}

void fear_turbo_glSamplerParameterf_hook(GLuint sampler, GLenum pname, GLfloat param) {
    fear_turbo::translate_glSamplerParameterf(sampler, pname, param);
}

void fear_turbo_glDeleteSamplers_hook(GLsizei count, const GLuint* samplers) {
    fear_turbo::translate_glDeleteSamplers(count, samplers);
}

// --- Queries ---
void fear_turbo_glGenQueries_hook(GLsizei n, GLuint* ids) {
    fear_turbo::translate_glGenQueries(n, ids);
}
void fear_turbo_glDeleteQueries_hook(GLsizei n, const GLuint* ids) {
    fear_turbo::translate_glDeleteQueries(n, ids);
}
void fear_turbo_glBeginQuery_hook(GLenum target, GLuint id) {
    fear_turbo::translate_glBeginQuery(target, id);
}
void fear_turbo_glEndQuery_hook(GLenum target) {
    fear_turbo::translate_glEndQuery(target);
}
void fear_turbo_glQueryCounter_hook(GLuint id, GLenum target) {
    fear_turbo::translate_glQueryCounter(id, target);
}

// --- Strings ---
const GLubyte* fear_turbo_glGetString_hook(GLenum name) {
    return fear_turbo::translate_glGetString(name);
}

const GLubyte* fear_turbo_glGetStringi_hook(GLenum name, GLuint index) {
    return fear_turbo::translate_glGetStringi(name, index);
}

// --- Texture ---
void fear_turbo_glBindTextureUnit_hook(GLuint unit, GLuint texture) {
    fear_turbo::translate_glBindTextureUnit(unit, texture);
}

// --- Shader Translation ---
const char* fear_turbo_translate_shader_source(const char* source, int stage) {
    fear_turbo::ShaderStage s = (fear_turbo::ShaderStage)stage;
    static thread_local std::string translated;
    translated = fear_turbo::translate_shader(source, s);
    return translated.c_str();
}

} // extern "C"
