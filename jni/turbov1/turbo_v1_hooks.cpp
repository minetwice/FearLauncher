#include "turbo_v1_core.h"
#include "turbo_v1_gl_translator.h"
#include "turbo_v1_buffer.h"
#include "turbo_v1_shader_transpiler.h"
#include "turbo_v1_perf.h"
#include "turbo_v1_entity.h"
#include <dlfcn.h>
#include <jni.h>
#include <string.h>

extern "C" {

void* turbo_v1_glMapBufferRange_hook(GLenum target, GLintptr offset, GLsizeiptr length, GLbitfield access) { return turbo_v1::translate_glMapBufferRange(target, offset, length, access); }
GLboolean turbo_v1_glUnmapBuffer_hook(GLenum target) { return turbo_v1::translate_glUnmapBuffer(target); }
void turbo_v1_glBufferStorage_hook(GLenum target, GLsizeiptr size, const void* data, GLbitfield flags) { turbo_v1::translate_glBufferStorage(target, size, data, flags); }
void turbo_v1_glFlushMappedBufferRange_hook(GLenum target, GLintptr offset, GLsizeiptr length) { turbo_v1::translate_glFlushMappedBufferRange(target, offset, length); }
void turbo_v1_glCreateBuffers_hook(GLsizei n, GLuint* buffers) { turbo_v1::translate_glCreateBuffers(n, buffers); }
void turbo_v1_glCreateVertexArrays_hook(GLsizei n, GLuint* arrays) { turbo_v1::translate_glCreateVertexArrays(n, arrays); }
void turbo_v1_glCreateTextures_hook(GLenum target, GLsizei n, GLuint* textures) { turbo_v1::translate_glCreateTextures(target, n, textures); }
void turbo_v1_glNamedBufferStorage_hook(GLuint buffer, GLsizeiptr size, const void* data, GLbitfield flags) { turbo_v1::translate_glNamedBufferStorage(buffer, size, data, flags); }
void turbo_v1_glNamedBufferData_hook(GLuint buffer, GLsizeiptr size, const void* data, GLenum usage) { turbo_v1::translate_glNamedBufferData(buffer, size, data, usage); }
void turbo_v1_glNamedBufferSubData_hook(GLuint buffer, GLintptr offset, GLsizeiptr size, const void* data) { turbo_v1::translate_glNamedBufferSubData(buffer, offset, size, data); }
void* turbo_v1_glMapNamedBufferRange_hook(GLuint buffer, GLintptr offset, GLsizeiptr length, GLbitfield access) { return turbo_v1::translate_glMapNamedBufferRange(buffer, offset, length, access); }
GLboolean turbo_v1_glUnmapNamedBuffer_hook(GLuint buffer) { return turbo_v1::translate_glUnmapNamedBuffer(buffer); }
void turbo_v1_glDispatchCompute_hook(GLuint x, GLuint y, GLuint z) { turbo_v1::translate_glDispatchCompute(x, y, z); }
void turbo_v1_glMemoryBarrier_hook(GLbitfield barriers) { turbo_v1::translate_glMemoryBarrier(barriers); }
void turbo_v1_glMultiDrawArraysIndirect_hook(GLenum mode, const void* indirect, GLsizei drawcount, GLsizei stride) { turbo_v1::translate_glMultiDrawArraysIndirect(mode, indirect, drawcount, stride); }
void turbo_v1_glMultiDrawElementsIndirect_hook(GLenum mode, GLenum type, const void* indirect, GLsizei drawcount, GLsizei stride) { turbo_v1::translate_glMultiDrawElementsIndirect(mode, type, indirect, drawcount, stride); }
void turbo_v1_glGenSamplers_hook(GLsizei count, GLuint* samplers) { turbo_v1::translate_glGenSamplers(count, samplers); }
void turbo_v1_glBindSampler_hook(GLuint unit, GLuint sampler) { turbo_v1::translate_glBindSampler(unit, sampler); }
void turbo_v1_glSamplerParameteri_hook(GLuint sampler, GLenum pname, GLint param) { turbo_v1::translate_glSamplerParameteri(sampler, pname, param); }
void turbo_v1_glSamplerParameterf_hook(GLuint sampler, GLenum pname, GLfloat param) { turbo_v1::translate_glSamplerParameterf(sampler, pname, param); }
void turbo_v1_glDeleteSamplers_hook(GLsizei count, const GLuint* samplers) { turbo_v1::translate_glDeleteSamplers(count, samplers); }
void turbo_v1_glGenQueries_hook(GLsizei n, GLuint* ids) { turbo_v1::translate_glGenQueries(n, ids); }
void turbo_v1_glDeleteQueries_hook(GLsizei n, const GLuint* ids) { turbo_v1::translate_glDeleteQueries(n, ids); }
void turbo_v1_glBeginQuery_hook(GLenum target, GLuint id) { turbo_v1::translate_glBeginQuery(target, id); }
void turbo_v1_glEndQuery_hook(GLenum target) { turbo_v1::translate_glEndQuery(target); }
void turbo_v1_glQueryCounter_hook(GLuint id, GLenum target) { turbo_v1::translate_glQueryCounter(id, target); }
const GLubyte* turbo_v1_glGetString_hook(GLenum name) { return turbo_v1::translate_glGetString(name); }
const GLubyte* turbo_v1_glGetStringi_hook(GLenum name, GLuint index) { return turbo_v1::translate_glGetStringi(name, index); }
void turbo_v1_glBindTextureUnit_hook(GLuint unit, GLuint texture) { turbo_v1::translate_glBindTextureUnit(unit, texture); }

const char* turbo_v1_translate_shader_source(const char* source, int stage) {
    turbo_v1::ShaderStage s = (turbo_v1::ShaderStage)stage;
    static thread_local std::string translated;
    translated = turbo_v1::transpile_shader(source, s);
    return translated.c_str();
}

void turbo_v1_perf_frame_begin_hook() { turbo_v1::perf_frame_begin(); }
void turbo_v1_perf_frame_end_hook() { turbo_v1::perf_frame_end(); }
int turbo_v1_perf_should_render_hook() { return turbo_v1::perf_should_render_frame() ? 1 : 0; }
int turbo_v1_perf_get_resolution_scale_hook() { return turbo_v1::perf_get_resolution_scale(); }
void turbo_v1_perf_pin_render_thread_hook() { turbo_v1::perf_pin_render_thread(); }
void turbo_v1_perf_gpu_boost_hook(int enable) { turbo_v1::perf_gpu_boost(enable != 0); }
double turbo_v1_perf_get_fps_hook() { return turbo_v1::perf_get_state().fps.load(); }
void turbo_v1_entity_init_hook() { turbo_v1::EntityManager::instance().init(); }
void turbo_v1_entity_shutdown_hook() { turbo_v1::EntityManager::instance().shutdown(); }
void turbo_v1_entity_submit_batched_hook() { turbo_v1::EntityManager::instance().submit_batched_draws(); }
int turbo_v1_entity_get_drawn_hook() { return turbo_v1::EntityManager::instance().get_drawn_count(); }
int turbo_v1_entity_get_batched_hook() { return turbo_v1::EntityManager::instance().get_batched_count(); }
int turbo_v1_entity_get_culled_hook() { return turbo_v1::EntityManager::instance().get_culled_count(); }

} // extern "C"
