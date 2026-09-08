#include "turbo_v1_gl_translator.h"
#include "turbo_v1_buffer.h"
#include <dlfcn.h>

namespace turbo_v1 {

typedef void (*PFN_glBufferData)(GLenum, GLsizeiptr, const void*, GLenum);
typedef void (*PFN_glBufferSubData)(GLenum, GLintptr, GLsizeiptr, const void*);
typedef void* (*PFN_glMapBufferRange)(GLenum, GLintptr, GLsizeiptr, GLbitfield);
typedef GLboolean (*PFN_glUnmapBuffer)(GLenum);
typedef void (*PFN_glFlush)(void);
typedef void (*PFN_glGetIntegerv)(GLenum, GLint*);
typedef void (*PFN_glGenBuffers)(GLsizei, GLuint*);
typedef void (*PFN_glBindBuffer)(GLenum, GLuint);
typedef GLenum (*PFN_glGetError)(void);
typedef const GLubyte* (*PFN_glGetString)(GLenum);
typedef const GLubyte* (*PFN_glGetStringi)(GLenum, GLuint);

static PFN_glBufferData    real_glBufferData = nullptr;
static PFN_glBufferSubData real_glBufferSubData = nullptr;
static PFN_glMapBufferRange real_glMapBufferRange = nullptr;
static PFN_glUnmapBuffer   real_glUnmapBuffer = nullptr;
static PFN_glFlush         real_glFlush = nullptr;
static PFN_glGetIntegerv   real_glGetIntegerv = nullptr;
static PFN_glGenBuffers    real_glGenBuffers = nullptr;
static PFN_glBindBuffer    real_glBindBuffer = nullptr;
static PFN_glGetError      real_glGetError = nullptr;
static PFN_glGetString     real_glGetString = nullptr;
static PFN_glGetStringi    real_glGetStringi = nullptr;

static void resolve_gles() {
    if (real_glBufferData) return;
    real_glBufferData    = (PFN_glBufferData)    dlsym(RTLD_DEFAULT, "glBufferData");
    real_glBufferSubData = (PFN_glBufferSubData) dlsym(RTLD_DEFAULT, "glBufferSubData");
    real_glMapBufferRange= (PFN_glMapBufferRange)dlsym(RTLD_DEFAULT, "glMapBufferRange");
    real_glUnmapBuffer   = (PFN_glUnmapBuffer)   dlsym(RTLD_DEFAULT, "glUnmapBuffer");
    real_glFlush         = (PFN_glFlush)         dlsym(RTLD_DEFAULT, "glFlush");
    real_glGetIntegerv   = (PFN_glGetIntegerv)   dlsym(RTLD_DEFAULT, "glGetIntegerv");
    real_glGenBuffers    = (PFN_glGenBuffers)    dlsym(RTLD_DEFAULT, "glGenBuffers");
    real_glBindBuffer    = (PFN_glBindBuffer)    dlsym(RTLD_DEFAULT, "glBindBuffer");
    real_glGetError      = (PFN_glGetError)      dlsym(RTLD_DEFAULT, "glGetError");
    real_glGetString     = (PFN_glGetString)     dlsym(RTLD_DEFAULT, "glGetString");
    real_glGetStringi    = (PFN_glGetStringi)    dlsym(RTLD_DEFAULT, "glGetStringi");
}

static void flush_errors() {
    resolve_gles();
    if (real_glGetError) { GLenum e; do { e = real_glGetError(); } while (e != GL_NO_ERROR); }
}

void translate_glBufferStorage(GLenum target, GLsizeiptr size, const void* data, GLbitfield flags) {
    resolve_gles();
    GLenum usage = GL_STATIC_DRAW;
    if (flags & 0x0100) usage = GL_DYNAMIC_DRAW;
    if (flags & 0x0040) usage = GL_DYNAMIC_READ;
    if (data && real_glBufferData) real_glBufferData(target, size, data, usage);
    flush_errors();
}

void* translate_glMapBufferRange(GLenum target, GLintptr offset, GLsizeiptr length, GLbitfield access) {
    return buffer::map(target, offset, length, access);
}

GLboolean translate_glUnmapBuffer(GLenum target) {
    return buffer::unmap(target);
}

void translate_glFlushMappedBufferRange(GLenum target, GLintptr offset, GLsizeiptr length) {
    buffer::flush_range(target, offset, length);
}

void translate_glNamedBufferStorage(GLuint buffer, GLsizeiptr size, const void* data, GLbitfield flags) {
    resolve_gles();
    GLenum usage = GL_STATIC_DRAW;
    if (flags & 0x0100) usage = GL_DYNAMIC_DRAW;
    if (real_glBindBuffer && real_glBufferData) {
        GLint prev_binding = 0;
        if (real_glGetIntegerv) real_glGetIntegerv(0x8894 /* GL_ARRAY_BUFFER_BINDING */, &prev_binding);
        real_glBindBuffer(GL_ARRAY_BUFFER, buffer);
        real_glBufferData(GL_ARRAY_BUFFER, size, data, usage);
        real_glBindBuffer(GL_ARRAY_BUFFER, (GLuint)prev_binding);
    }
    flush_errors();
}

void translate_glNamedBufferData(GLuint buffer, GLsizeiptr size, const void* data, GLenum usage) {
    resolve_gles();
    if (real_glBindBuffer && real_glBufferData) {
        GLint prev_binding = 0;
        if (real_glGetIntegerv) real_glGetIntegerv(0x8894 /* GL_ARRAY_BUFFER_BINDING */, &prev_binding);
        real_glBindBuffer(GL_ARRAY_BUFFER, buffer);
        real_glBufferData(GL_ARRAY_BUFFER, size, data, usage);
        real_glBindBuffer(GL_ARRAY_BUFFER, (GLuint)prev_binding);
    }
    flush_errors();
}

void translate_glNamedBufferSubData(GLuint buffer, GLintptr offset, GLsizeiptr size, const void* data) {
    resolve_gles();
    if (real_glBindBuffer && real_glBufferSubData) {
        GLint prev_binding = 0;
        if (real_glGetIntegerv) real_glGetIntegerv(0x8894 /* GL_ARRAY_BUFFER_BINDING */, &prev_binding);
        real_glBindBuffer(GL_ARRAY_BUFFER, buffer);
        real_glBufferSubData(GL_ARRAY_BUFFER, offset, size, data);
        real_glBindBuffer(GL_ARRAY_BUFFER, (GLuint)prev_binding);
    }
    flush_errors();
}

void* translate_glMapNamedBufferRange(GLuint buffer, GLintptr offset, GLsizeiptr length, GLbitfield access) {
    resolve_gles();
    if (real_glBindBuffer) {
        GLint prev_binding = 0;
        if (real_glGetIntegerv) real_glGetIntegerv(0x8894 /* GL_ARRAY_BUFFER_BINDING */, &prev_binding);
        real_glBindBuffer(GL_ARRAY_BUFFER, buffer);
        void* ptr = buffer::map(GL_ARRAY_BUFFER, offset, length, access);
        real_glBindBuffer(GL_ARRAY_BUFFER, (GLuint)prev_binding);
        return ptr;
    }
    return buffer::map(GL_ARRAY_BUFFER, offset, length, access);
}

GLboolean translate_glUnmapNamedBuffer(GLuint buffer) {
    resolve_gles();
    GLint prev_binding = 0;
    if (real_glGetIntegerv) real_glGetIntegerv(0x8894 /* GL_ARRAY_BUFFER_BINDING */, &prev_binding);
    if (real_glBindBuffer) real_glBindBuffer(GL_ARRAY_BUFFER, buffer);
    GLboolean res = buffer::unmap(GL_ARRAY_BUFFER);
    if (real_glBindBuffer) real_glBindBuffer(GL_ARRAY_BUFFER, (GLuint)prev_binding);
    return res;
}

void translate_glCreateBuffers(GLsizei n, GLuint* buffers) {
    resolve_gles();
    if (real_glGenBuffers) real_glGenBuffers(n, buffers);
}

void translate_glCreateVertexArrays(GLsizei n, GLuint* arrays) {
    typedef void (*PFN_glGenVertexArrays)(GLsizei, GLuint*);
    static PFN_glGenVertexArrays real_fn = nullptr;
    if (!real_fn) real_fn = (PFN_glGenVertexArrays) dlsym(RTLD_DEFAULT, "glGenVertexArrays");
    if (real_fn) real_fn(n, arrays);
}

void translate_glCreateTextures(GLenum target, GLsizei n, GLuint* textures) {
    typedef void (*PFN_glGenTextures)(GLsizei, GLuint*);
    static PFN_glGenTextures real_fn = nullptr;
    if (!real_fn) real_fn = (PFN_glGenTextures) dlsym(RTLD_DEFAULT, "glGenTextures");
    if (real_fn) real_fn(n, textures);
}

void translate_glTextureBufferRange(GLuint texture, GLenum internalFormat, GLuint buffer, GLintptr offset, GLsizeiptr size) {
    typedef void (*PFN_glTexBufferRange)(GLenum, GLenum, GLuint, GLintptr, GLsizeiptr);
    typedef void (*PFN_glBindTexture)(GLenum, GLuint);
    typedef void (*PFN_glGetIntegerv)(GLenum, GLint*);
    static PFN_glTexBufferRange real_tex_buf_range = nullptr;
    static PFN_glBindTexture real_bind_tex = nullptr;
    static PFN_glGetIntegerv real_get_int = nullptr;
    if (!real_tex_buf_range) real_tex_buf_range = (PFN_glTexBufferRange) dlsym(RTLD_DEFAULT, "glTexBufferRangeOES");
    if (!real_tex_buf_range) real_tex_buf_range = (PFN_glTexBufferRange) dlsym(RTLD_DEFAULT, "glTexBufferRange");
    if (!real_bind_tex) real_bind_tex = (PFN_glBindTexture) dlsym(RTLD_DEFAULT, "glBindTexture");
    if (!real_get_int) real_get_int = (PFN_glGetIntegerv) dlsym(RTLD_DEFAULT, "glGetIntegerv");

    if (real_bind_tex && real_tex_buf_range) {
        GLint prev_binding = 0;
        if (real_get_int) real_get_int(0x8C2C /* GL_TEXTURE_BINDING_BUFFER */, &prev_binding);
        real_bind_tex(0x8C2A /* GL_TEXTURE_BUFFER */, texture);
        real_tex_buf_range(0x8C2A /* GL_TEXTURE_BUFFER */, internalFormat, buffer, offset, size);
        real_bind_tex(0x8C2A /* GL_TEXTURE_BUFFER */, (GLuint)prev_binding);
    }
    flush_errors();
}

void translate_glTextureView(GLuint texture, GLenum target, GLuint origtexture, GLenum internalformat, GLuint minlevel, GLuint numlevels, GLuint minlayer, GLuint numlayers) {
    typedef void (*PFN_glTextureView)(GLuint, GLenum, GLuint, GLenum, GLuint, GLuint, GLuint, GLuint);
    static PFN_glTextureView real_tex_view = nullptr;
    if (!real_tex_view) real_tex_view = (PFN_glTextureView) dlsym(RTLD_DEFAULT, "glTextureViewOES");
    if (!real_tex_view) real_tex_view = (PFN_glTextureView) dlsym(RTLD_DEFAULT, "glTextureView");
    if (real_tex_view) {
        real_tex_view(texture, target, origtexture, internalformat, minlevel, numlevels, minlayer, numlayers);
    }
    flush_errors();
}

void translate_glBindTextureUnit(GLuint unit, GLuint texture) {
    typedef void (*PFN_glActiveTexture)(GLenum);
    typedef void (*PFN_glBindTexture)(GLenum, GLenum);
    static PFN_glActiveTexture real_active = nullptr;
    static PFN_glBindTexture real_bind = nullptr;
    if (!real_active) real_active = (PFN_glActiveTexture) dlsym(RTLD_DEFAULT, "glActiveTexture");
    if (!real_bind) real_bind = (PFN_glBindTexture) dlsym(RTLD_DEFAULT, "glBindTexture");
    if (real_active) real_active(GL_TEXTURE0 + unit);
    if (real_bind) real_bind(GL_TEXTURE_2D, texture);
    flush_errors();
}

void translate_glDispatchCompute(GLuint x, GLuint y, GLuint z) {
    typedef void (*PFN_glDispatchCompute)(GLuint, GLuint, GLuint);
    static PFN_glDispatchCompute real_fn = nullptr;
    if (!real_fn) real_fn = (PFN_glDispatchCompute) dlsym(RTLD_DEFAULT, "glDispatchCompute");
    if (real_fn) real_fn(x, y, z);
    flush_errors();
}

void translate_glDispatchComputeIndirect(GLintptr indirect) {
    typedef void (*PFN_glDispatchComputeIndirect)(GLintptr);
    static PFN_glDispatchComputeIndirect real_fn = nullptr;
    if (!real_fn) real_fn = (PFN_glDispatchComputeIndirect) dlsym(RTLD_DEFAULT, "glDispatchComputeIndirect");
    if (real_fn) real_fn(indirect);
    flush_errors();
}

void translate_glMemoryBarrier(GLbitfield barriers) {
    typedef void (*PFN_glMemoryBarrier)(GLbitfield);
    static PFN_glMemoryBarrier real_fn = nullptr;
    if (!real_fn) real_fn = (PFN_glMemoryBarrier) dlsym(RTLD_DEFAULT, "glMemoryBarrier");
    if (real_fn) { real_fn(barriers); }
    else { resolve_gles(); if (real_glFlush) real_glFlush(); }
    flush_errors();
}

void translate_glMultiDrawArraysIndirect(GLenum mode, const void* indirect, GLsizei drawcount, GLsizei stride) {
    typedef void (*PFN_glMultiDrawArraysIndirect)(GLenum, const void*, GLsizei, GLsizei);
    static PFN_glMultiDrawArraysIndirect real_fn = nullptr;
    if (!real_fn) real_fn = (PFN_glMultiDrawArraysIndirect) dlsym(RTLD_DEFAULT, "glMultiDrawArraysIndirect");
    if (real_fn) real_fn(mode, indirect, drawcount, stride);
    flush_errors();
}

void translate_glMultiDrawElementsIndirect(GLenum mode, GLenum type, const void* indirect, GLsizei drawcount, GLsizei stride) {
    typedef void (*PFN_glMultiDrawElementsIndirect)(GLenum, GLenum, const void*, GLsizei, GLsizei);
    static PFN_glMultiDrawElementsIndirect real_fn = nullptr;
    if (!real_fn) real_fn = (PFN_glMultiDrawElementsIndirect) dlsym(RTLD_DEFAULT, "glMultiDrawElementsIndirect");
    if (real_fn) real_fn(mode, type, indirect, drawcount, stride);
    flush_errors();
}

void translate_glGenSamplers(GLsizei count, GLuint* samplers) {
    typedef void (*PFN_glGenSamplers)(GLsizei, GLuint*);
    static PFN_glGenSamplers real_fn = nullptr;
    if (!real_fn) real_fn = (PFN_glGenSamplers) dlsym(RTLD_DEFAULT, "glGenSamplers");
    if (real_fn) {
        real_fn(count, samplers);
        bool valid = true;
        for (int i = 0; i < count; i++) { if (samplers[i] == 0) { valid = false; break; } }
        if (!valid) { static GLuint next_id = 1; for (int i = 0; i < count; i++) samplers[i] = next_id++; }
    } else { static GLuint next_id = 1; for (int i = 0; i < count; i++) samplers[i] = next_id++; }
}

void translate_glBindSampler(GLuint unit, GLuint sampler) {
    typedef void (*PFN_glBindSampler)(GLuint, GLuint);
    static PFN_glBindSampler real_fn = nullptr;
    if (!real_fn) real_fn = (PFN_glBindSampler) dlsym(RTLD_DEFAULT, "glBindSampler");
    if (real_fn) real_fn(unit, sampler);
    flush_errors();
}

void translate_glSamplerParameteri(GLuint sampler, GLenum pname, GLint param) {
    typedef void (*PFN_glSamplerParameteri)(GLuint, GLenum, GLint);
    static PFN_glSamplerParameteri real_fn = nullptr;
    if (!real_fn) real_fn = (PFN_glSamplerParameteri) dlsym(RTLD_DEFAULT, "glSamplerParameteri");
    if (real_fn) real_fn(sampler, pname, param);
    flush_errors();
}

void translate_glSamplerParameterf(GLuint sampler, GLenum pname, GLfloat param) {
    typedef void (*PFN_glSamplerParameterf)(GLuint, GLenum, GLfloat);
    static PFN_glSamplerParameterf real_fn = nullptr;
    if (!real_fn) real_fn = (PFN_glSamplerParameterf) dlsym(RTLD_DEFAULT, "glSamplerParameterf");
    if (real_fn) real_fn(sampler, pname, param);
    flush_errors();
}

void translate_glDeleteSamplers(GLsizei count, const GLuint* samplers) {
    typedef void (*PFN_glDeleteSamplers)(GLsizei, const GLuint*);
    static PFN_glDeleteSamplers real_fn = nullptr;
    if (!real_fn) real_fn = (PFN_glDeleteSamplers) dlsym(RTLD_DEFAULT, "glDeleteSamplers");
    if (real_fn) real_fn(count, samplers);
    flush_errors();
}

void translate_glGenQueries(GLsizei n, GLuint* ids) {
    typedef void (*PFN_glGenQueries)(GLsizei, GLuint*);
    static PFN_glGenQueries real_fn = nullptr;
    if (!real_fn) real_fn = (PFN_glGenQueries) dlsym(RTLD_DEFAULT, "glGenQueries");
    if (real_fn) real_fn(n, ids);
    else { for (int i = 0; i < n; i++) ids[i] = 0; }
    flush_errors();
}

void translate_glDeleteQueries(GLsizei n, const GLuint* ids) {
    typedef void (*PFN_glDeleteQueries)(GLsizei, const GLuint*);
    static PFN_glDeleteQueries real_fn = nullptr;
    if (!real_fn) real_fn = (PFN_glDeleteQueries) dlsym(RTLD_DEFAULT, "glDeleteQueries");
    if (real_fn) real_fn(n, ids);
    flush_errors();
}

void translate_glBeginQuery(GLenum target, GLuint id) {
    typedef void (*PFN_glBeginQuery)(GLenum, GLuint);
    static PFN_glBeginQuery real_fn = nullptr;
    if (!real_fn) real_fn = (PFN_glBeginQuery) dlsym(RTLD_DEFAULT, "glBeginQuery");
    if (real_fn) real_fn(target, id);
    flush_errors();
}

void translate_glEndQuery(GLenum target) {
    typedef void (*PFN_glEndQuery)(GLenum);
    static PFN_glEndQuery real_fn = nullptr;
    if (!real_fn) real_fn = (PFN_glEndQuery) dlsym(RTLD_DEFAULT, "glEndQuery");
    if (real_fn) real_fn(target);
    flush_errors();
}

void translate_glQueryCounter(GLuint id, GLenum target) {
    typedef void (*PFN_glQueryCounter)(GLuint, GLenum);
    static PFN_glQueryCounter real_fn = nullptr;
    if (!real_fn) real_fn = (PFN_glQueryCounter) dlsym(RTLD_DEFAULT, "glQueryCounter");
    if (real_fn) real_fn(id, target);
    flush_errors();
}

void translate_glGetQueryObjectiv(GLuint id, GLenum pname, GLint* params) {
    typedef void (*PFN_glGetQueryObjectiv)(GLuint, GLenum, GLint*);
    static PFN_glGetQueryObjectiv real_fn = nullptr;
    if (!real_fn) real_fn = (PFN_glGetQueryObjectiv) dlsym(RTLD_DEFAULT, "glGetQueryObjectiv");
    if (real_fn) real_fn(id, pname, params);
    else if (params) *params = 0;
    flush_errors();
}

void translate_glGetQueryObjectui64v(GLuint id, GLenum pname, GLuint64* params) {
    typedef void (*PFN_glGetQueryObjectui64v)(GLuint, GLenum, GLuint64*);
    static PFN_glGetQueryObjectui64v real_fn = nullptr;
    if (!real_fn) real_fn = (PFN_glGetQueryObjectui64v) dlsym(RTLD_DEFAULT, "glGetQueryObjectui64v");
    if (real_fn) real_fn(id, pname, params);
    else if (params) *params = 0;
    flush_errors();
}

static const char* FAKE_EXTENSIONS[] = {
    "GL_ARB_direct_state_access","GL_ARB_buffer_storage","GL_ARB_shader_image_load_store",
    "GL_NV_conditional_render","GL_EXT_gpu_shader4","GL_EXT_texture_buffer",
    "GL_EXT_texture_cube_map_array","GL_OES_EGL_image_external_essl3",
    "GL_NV_shader_noperspective_interpolation","GL_ARB_shader_objects",
    "GL_ARB_vertex_shader","GL_ARB_fragment_shader","GL_EXT_blend_equation_separate",
    "GL_EXT_geometry_shader4","GL_EXT_gpu_program_parameters",
    "GL_ARB_instanced_arrays","GL_ARB_draw_instanced","GL_ARB_compute_shader",
    "GL_ARB_shader_storage_buffer_object","GL_ARB_uniform_buffer_object",
    "GL_ARB_multi_draw_indirect","GL_ARB_texture_cube_map_array","GL_ARB_texture_view",
    "GL_ARB_sampler_objects","GL_ARB_sync","GL_ARB_timer_query",
    "GL_ARB_transform_feedback2","GL_ARB_transform_feedback3",
    "GL_ARB_explicit_uniform_location","GL_ARB_shading_language_420pack",
    "GL_ARB_separate_shader_objects","GL_ARB_conservative_depth",
    "GL_ARB_shader_draw_parameters","GL_ARB_gpu_shader5","GL_ARB_gpu_shader_fp64",
    "GL_ARB_shader_precision","GL_ARB_vertex_attrib_64bit",
    "GL_ARB_shader_atomic_counters","GL_ARB_shader_atomic_counter_ops",
    "GL_ARB_shader_clock","GL_ARB_enhanced_layouts","GL_ARB_bindless_texture",
    "GL_ARB_texture_barrier","GL_ARB_texture_filter_anisotropic",
    "GL_ARB_ES3_2_compatibility","GL_EXT_texture_compression_s3tc",
    "GL_EXT_texture_filter_anisotropic","GL_EXT_texture_sRGB_decode",
    "GL_EXT_texture_norm16","GL_EXT_shader_io_blocks","GL_EXT_tessellation_shader",
    "GL_EXT_primitive_bounding_box","GL_EXT_geometry_point_size","GL_EXT_geometry_shader",
    "GL_GPU_shader5","GL_OES_texture_buffer","GL_OES_texture_cube_map_array",
    "GL_OES_geometry_shader","GL_OES_shader_image_atomic",
    "GL_OES_shader_multisample_interpolation","GL_OES_sample_variables",
    "GL_OES_texture_stencil8","GL_OES_texture_storage_multisample_2d_array",
    "GL_KHR_debug","GL_KHR_context_flush_control","GL_KHR_robustness","GL_KHR_robust_buffer_access_behavior",
};
static const int FAKE_EXT_COUNT = sizeof(FAKE_EXTENSIONS) / sizeof(FAKE_EXTENSIONS[0]);

const GLubyte* translate_glGetString(GLenum name) {
    switch (name) {
        case 0x1F02: return (const GLubyte*)get_version_string();
        case 0x1F01: return (const GLubyte*)get_renderer_string();
        case 0x1F00: return (const GLubyte*)get_vendor_string();
        case 0x1F03: return (const GLubyte*)
            "GL_ARB_direct_state_access GL_ARB_buffer_storage GL_ARB_shader_image_load_store "
            "GL_NV_conditional_render GL_EXT_gpu_shader4 GL_EXT_texture_buffer "
            "GL_EXT_texture_cube_map_array GL_OES_EGL_image_external_essl3 "
            "GL_NV_shader_noperspective_interpolation GL_ARB_shader_objects "
            "GL_ARB_vertex_shader GL_ARB_fragment_shader GL_EXT_blend_equation_separate "
            "GL_EXT_geometry_shader4 GL_EXT_gpu_program_parameters "
            "GL_ARB_instanced_arrays GL_ARB_draw_instanced GL_ARB_compute_shader "
            "GL_ARB_shader_storage_buffer_object GL_ARB_uniform_buffer_object "
            "GL_ARB_multi_draw_indirect GL_ARB_texture_cube_map_array GL_ARB_texture_view "
            "GL_ARB_sampler_objects GL_KHR_debug GL_KHR_robustness";
        default:
            resolve_gles();
            if (real_glGetString) return real_glGetString(name);
            return (const GLubyte*)"";
    }
}

const GLubyte* translate_glGetStringi(GLenum name, GLuint index) {
    if (name == 0x1F03 && index < (GLuint)FAKE_EXT_COUNT) {
        return (const GLubyte*)FAKE_EXTENSIONS[index];
    }
    resolve_gles();
    if (real_glGetStringi) return real_glGetStringi(name, index);
    return (const GLubyte*)"";
}

} // namespace turbo_v1
