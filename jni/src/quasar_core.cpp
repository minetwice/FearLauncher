/**
 * Quasar Core — pure custom desktop-GL → GLES3 translation layer.
 * NO LTW, NO gl4es, NO MobileGlues, NO Mesa/Zink.
 * Maps OpenGL 3.3 calls onto the device's real GLES 3.x driver (Mali/Adreno).
 */
#include <android/log.h>
#include <dlfcn.h>
#include <pthread.h>
#include <string.h>
#include <stdlib.h>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>
#include <EGL/egl.h>
#include <GLES3/gl3.h>

#define LOG_TAG "QuasarCore"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define Q_EXPORT __attribute__((visibility("default")))

#ifndef GL_MAJOR_VERSION
#define GL_MAJOR_VERSION 0x821B
#define GL_MINOR_VERSION 0x821C
#endif
#ifndef GL_NUM_EXTENSIONS
#define GL_NUM_EXTENSIONS 0x821D
#endif
#ifndef GL_CONTEXT_PROFILE_MASK
#define GL_CONTEXT_PROFILE_MASK 0x9126
#define GL_CONTEXT_CORE_PROFILE_BIT 0x00000001
#endif

static void* g_gles = nullptr;
static void* g_egl  = nullptr;
static std::once_flag g_init_flag;
static bool g_ready = false;

static void* q_sym(const char* name) {
    if (!g_gles) return nullptr;
    void* s = dlsym(g_gles, name);
    if (!s && g_egl) s = dlsym(g_egl, name);
    if (!s) s = dlsym(RTLD_DEFAULT, name);
    return s;
}

static void quasar_init_handles() {
    std::call_once(g_init_flag, []() {
        g_gles = dlopen("libGLESv3.so", RTLD_NOW | RTLD_GLOBAL);
        if (!g_gles) g_gles = dlopen("libGLESv2.so", RTLD_NOW | RTLD_GLOBAL);
        g_egl = dlopen("libEGL.so", RTLD_NOW | RTLD_GLOBAL);
        g_ready = (g_gles != nullptr);
        LOGI("QuasarCore init | gles=%p egl=%p ready=%d", g_gles, g_egl, (int)g_ready);
        if (g_ready) {
            LOGI("QuasarCore: pure GLES3 backend active — no LTW/gl4es/Glues");
        } else {
            LOGE("QuasarCore: FAILED to open libGLESv3/libGLESv2");
        }
    });
}

static const char* kVersion   = "3.3.0 Quasar Core";
static const char* kVendor    = "FearLauncher";
static const char* kRenderer  = "Quasar GLES3 Translator";
static const char* kGLSL      = "3.30 Quasar";

static const char* kExtList[] = {
    "GL_ARB_ES3_compatibility",
    "GL_ARB_sampler_objects",
    "GL_ARB_separate_shader_objects",
    "GL_ARB_instanced_arrays",
    "GL_ARB_draw_instanced",
    "GL_ARB_framebuffer_object",
    "GL_ARB_vertex_array_object",
    "GL_ARB_map_buffer_range",
    "GL_ARB_uniform_buffer_object",
    "GL_ARB_texture_storage",
    "GL_ARB_texture_swizzle",
    "GL_ARB_copy_buffer",
    "GL_ARB_sync",
    "GL_ARB_timer_query",
    "GL_ARB_occlusion_query2",
    "GL_ARB_blend_func_extended",
    "GL_ARB_explicit_attrib_location",
    "GL_ARB_shader_bit_encoding",
    "GL_ARB_texture_rgb10_a2ui",
    "GL_EXT_texture_filter_anisotropic",
    "GL_EXT_texture_compression_s3tc",
    "GL_EXT_texture_sRGB",
    "GL_EXT_framebuffer_blit",
    "GL_EXT_framebuffer_multisample",
    "GL_OES_texture_float",
    "GL_OES_texture_half_float",
    "GL_OES_element_index_uint",
    "GL_OES_depth_texture",
    "GL_OES_packed_depth_stencil",
    "GL_OES_standard_derivatives",
    "GL_OES_vertex_array_object",
    "GL_EXT_color_buffer_float",
    "GL_EXT_color_buffer_half_float",
    "GL_NV_shader_noperspective_interpolation",
};
static const int kExtCount = sizeof(kExtList) / sizeof(kExtList[0]);

static std::string g_ext_joined;
static bool g_ext_joined_ready = false;

static const char* joined_extensions() {
    if (!g_ext_joined_ready) {
        g_ext_joined.clear();
        for (int i = 0; i < kExtCount; i++) {
            if (i) g_ext_joined += ' ';
            g_ext_joined += kExtList[i];
        }
        g_ext_joined_ready = true;
    }
    return g_ext_joined.c_str();
}

static std::string rewrite_shader(const char* src) {
    if (!src) return "";
    std::string s(src);
    if (s.find("#version") != std::string::npos) {
        size_t p = s.find("#version");
        size_t eol = s.find('\n', p);
        if (eol == std::string::npos) eol = s.size();
        s.replace(p, eol - p, "#version 300 es");
    } else {
        s = "#version 300 es\n" + s;
    }
    if (s.find("precision ") == std::string::npos) {
        size_t after = s.find('\n');
        if (after != std::string::npos)
            s.insert(after + 1, "precision highp float;\nprecision highp int;\n");
    }
    auto rep = [&](const char* a, const char* b) {
        size_t pos = 0;
        while ((pos = s.find(a, pos)) != std::string::npos) {
            s.replace(pos, strlen(a), b);
            pos += strlen(b);
        }
    };
    rep("texture2D(", "texture(");
    rep("textureCube(", "texture(");
    rep("texture2DLod(", "textureLod(");
    rep("textureCubeLod(", "textureLod(");
    rep("noperspective ", "smooth ");
    rep("attribute ", "in ");
    return s;
}

extern "C" {

Q_EXPORT const GLubyte* glGetString(GLenum name) {
    quasar_init_handles();
    switch (name) {
        case GL_VERSION: return (const GLubyte*)kVersion;
        case GL_VENDOR: return (const GLubyte*)kVendor;
        case GL_RENDERER: return (const GLubyte*)kRenderer;
        case GL_SHADING_LANGUAGE_VERSION: return (const GLubyte*)kGLSL;
        case GL_EXTENSIONS: return (const GLubyte*)joined_extensions();
        default: {
            typedef const GLubyte* (*fn)(GLenum);
            static fn real = (fn)q_sym("glGetString");
            return real ? real(name) : (const GLubyte*)"";
        }
    }
}

Q_EXPORT const GLubyte* glGetStringi(GLenum name, GLuint index) {
    if (name == GL_EXTENSIONS && index < (GLuint)kExtCount)
        return (const GLubyte*)kExtList[index];
    typedef const GLubyte* (*fn)(GLenum, GLuint);
    static fn real = (fn)q_sym("glGetStringi");
    return real ? real(name, index) : (const GLubyte*)"";
}

Q_EXPORT void glGetIntegerv(GLenum pname, GLint* params) {
    quasar_init_handles();
    if (!params) return;
    switch (pname) {
        case GL_MAJOR_VERSION: *params = 3; return;
        case GL_MINOR_VERSION: *params = 3; return;
        case GL_NUM_EXTENSIONS: *params = kExtCount; return;
        case GL_CONTEXT_PROFILE_MASK: *params = GL_CONTEXT_CORE_PROFILE_BIT; return;
        default: {
            typedef void (*fn)(GLenum, GLint*);
            static fn real = (fn)q_sym("glGetIntegerv");
            if (real) real(pname, params);
            else *params = 0;
        }
    }
}

Q_EXPORT void glGenSamplers(GLsizei n, GLuint* samplers) {
    typedef void (*fn)(GLsizei, GLuint*);
    static fn real = (fn)q_sym("glGenSamplers");
    if (real) real(n, samplers);
    else if (samplers && n > 0) { for (GLsizei i = 0; i < n; i++) samplers[i] = (GLuint)(1000 + i); }
}

Q_EXPORT void glDeleteSamplers(GLsizei n, const GLuint* samplers) {
    typedef void (*fn)(GLsizei, const GLuint*);
    static fn real = (fn)q_sym("glDeleteSamplers");
    if (real) real(n, samplers);
}

Q_EXPORT void glBindSampler(GLuint unit, GLuint sampler) {
    typedef void (*fn)(GLuint, GLuint);
    static fn real = (fn)q_sym("glBindSampler");
    if (real) real(unit, sampler);
}

Q_EXPORT GLboolean glIsSampler(GLuint sampler) {
    typedef GLboolean (*fn)(GLuint);
    static fn real = (fn)q_sym("glIsSampler");
    return real ? real(sampler) : GL_FALSE;
}

Q_EXPORT void glSamplerParameteri(GLuint sampler, GLenum pname, GLint param) {
    typedef void (*fn)(GLuint, GLenum, GLint);
    static fn real = (fn)q_sym("glSamplerParameteri");
    if (real) real(sampler, pname, param);
}

Q_EXPORT void glSamplerParameterf(GLuint sampler, GLenum pname, GLfloat param) {
    typedef void (*fn)(GLuint, GLenum, GLfloat);
    static fn real = (fn)q_sym("glSamplerParameterf");
    if (real) real(sampler, pname, param);
}

Q_EXPORT void glSamplerParameteriv(GLuint sampler, GLenum pname, const GLint* params) {
    typedef void (*fn)(GLuint, GLenum, const GLint*);
    static fn real = (fn)q_sym("glSamplerParameteriv");
    if (real) real(sampler, pname, params);
}

Q_EXPORT void glSamplerParameterfv(GLuint sampler, GLenum pname, const GLfloat* params) {
    typedef void (*fn)(GLuint, GLenum, const GLfloat*);
    static fn real = (fn)q_sym("glSamplerParameterfv");
    if (real) real(sampler, pname, params);
}

Q_EXPORT void glGetSamplerParameteriv(GLuint sampler, GLenum pname, GLint* params) {
    typedef void (*fn)(GLuint, GLenum, GLint*);
    static fn real = (fn)q_sym("glGetSamplerParameteriv");
    if (real) real(sampler, pname, params);
}

Q_EXPORT void glGetSamplerParameterfv(GLuint sampler, GLenum pname, GLfloat* params) {
    typedef void (*fn)(GLuint, GLenum, GLfloat*);
    static fn real = (fn)q_sym("glGetSamplerParameterfv");
    if (real) real(sampler, pname, params);
}

Q_EXPORT void glShaderSource(GLuint shader, GLsizei count, const GLchar* const* string, const GLint* length) {
    typedef void (*fn)(GLuint, GLsizei, const GLchar* const*, const GLint*);
    static fn real = (fn)q_sym("glShaderSource");
    if (!real || !string || count <= 0) return;
    std::string combined;
    for (GLsizei i = 0; i < count; i++) {
        if (!string[i]) continue;
        if (length && length[i] >= 0) combined.append(string[i], (size_t)length[i]);
        else combined.append(string[i]);
    }
    std::string rewritten = rewrite_shader(combined.c_str());
    const GLchar* ptr = rewritten.c_str();
    GLint len = (GLint)rewritten.size();
    real(shader, 1, &ptr, &len);
}

Q_EXPORT void glEnable(GLenum cap) {
    typedef void (*fn)(GLenum); static fn real = (fn)q_sym("glEnable"); if (real) real(cap);
}
Q_EXPORT void glDisable(GLenum cap) {
    typedef void (*fn)(GLenum); static fn real = (fn)q_sym("glDisable"); if (real) real(cap);
}
Q_EXPORT void glViewport(GLint x, GLint y, GLsizei w, GLsizei h) {
    typedef void (*fn)(GLint,GLint,GLsizei,GLsizei); static fn real = (fn)q_sym("glViewport"); if (real) real(x,y,w,h);
}
Q_EXPORT void glScissor(GLint x, GLint y, GLsizei w, GLsizei h) {
    typedef void (*fn)(GLint,GLint,GLsizei,GLsizei); static fn real = (fn)q_sym("glScissor"); if (real) real(x,y,w,h);
}
Q_EXPORT void glClearColor(GLfloat r, GLfloat g, GLfloat b, GLfloat a) {
    typedef void (*fn)(GLfloat,GLfloat,GLfloat,GLfloat); static fn real = (fn)q_sym("glClearColor"); if (real) real(r,g,b,a);
}
Q_EXPORT void glClearDepthf(GLfloat d) {
    typedef void (*fn)(GLfloat); static fn real = (fn)q_sym("glClearDepthf"); if (real) real(d);
}
Q_EXPORT void glClearStencil(GLint s) {
    typedef void (*fn)(GLint); static fn real = (fn)q_sym("glClearStencil"); if (real) real(s);
}
Q_EXPORT void glClear(GLbitfield mask) {
    typedef void (*fn)(GLbitfield); static fn real = (fn)q_sym("glClear"); if (real) real(mask);
}
Q_EXPORT void glBlendFunc(GLenum s, GLenum d) {
    typedef void (*fn)(GLenum,GLenum); static fn real = (fn)q_sym("glBlendFunc"); if (real) real(s,d);
}
Q_EXPORT void glBlendFuncSeparate(GLenum a, GLenum b, GLenum c, GLenum d) {
    typedef void (*fn)(GLenum,GLenum,GLenum,GLenum); static fn real = (fn)q_sym("glBlendFuncSeparate"); if (real) real(a,b,c,d);
}
Q_EXPORT void glBlendEquation(GLenum mode) {
    typedef void (*fn)(GLenum); static fn real = (fn)q_sym("glBlendEquation"); if (real) real(mode);
}
Q_EXPORT void glDepthFunc(GLenum f) {
    typedef void (*fn)(GLenum); static fn real = (fn)q_sym("glDepthFunc"); if (real) real(f);
}
Q_EXPORT void glDepthMask(GLboolean m) {
    typedef void (*fn)(GLboolean); static fn real = (fn)q_sym("glDepthMask"); if (real) real(m);
}
Q_EXPORT void glColorMask(GLboolean r, GLboolean g, GLboolean b, GLboolean a) {
    typedef void (*fn)(GLboolean,GLboolean,GLboolean,GLboolean); static fn real = (fn)q_sym("glColorMask"); if (real) real(r,g,b,a);
}
Q_EXPORT void glCullFace(GLenum mode) {
    typedef void (*fn)(GLenum); static fn real = (fn)q_sym("glCullFace"); if (real) real(mode);
}
Q_EXPORT void glFrontFace(GLenum mode) {
    typedef void (*fn)(GLenum); static fn real = (fn)q_sym("glFrontFace"); if (real) real(mode);
}
Q_EXPORT void glLineWidth(GLfloat w) {
    typedef void (*fn)(GLfloat); static fn real = (fn)q_sym("glLineWidth"); if (real) real(w);
}
Q_EXPORT void glPolygonOffset(GLfloat f, GLfloat u) {
    typedef void (*fn)(GLfloat,GLfloat); static fn real = (fn)q_sym("glPolygonOffset"); if (real) real(f,u);
}
Q_EXPORT void glActiveTexture(GLenum texture) {
    typedef void (*fn)(GLenum); static fn real = (fn)q_sym("glActiveTexture"); if (real) real(texture);
}
Q_EXPORT void glGenBuffers(GLsizei n, GLuint* b) {
    typedef void (*fn)(GLsizei,GLuint*); static fn real = (fn)q_sym("glGenBuffers"); if (real) real(n,b);
}
Q_EXPORT void glDeleteBuffers(GLsizei n, const GLuint* b) {
    typedef void (*fn)(GLsizei,const GLuint*); static fn real = (fn)q_sym("glDeleteBuffers"); if (real) real(n,b);
}
Q_EXPORT void glBindBuffer(GLenum t, GLuint b) {
    typedef void (*fn)(GLenum,GLuint); static fn real = (fn)q_sym("glBindBuffer"); if (real) real(t,b);
}
Q_EXPORT void glBufferData(GLenum t, GLsizeiptr s, const void* d, GLenum u) {
    typedef void (*fn)(GLenum,GLsizeiptr,const void*,GLenum); static fn real = (fn)q_sym("glBufferData"); if (real) real(t,s,d,u);
}
Q_EXPORT void glBufferSubData(GLenum t, GLintptr o, GLsizeiptr s, const void* d) {
    typedef void (*fn)(GLenum,GLintptr,GLsizeiptr,const void*); static fn real = (fn)q_sym("glBufferSubData"); if (real) real(t,o,s,d);
}
Q_EXPORT void glGenTextures(GLsizei n, GLuint* tex) {
    typedef void (*fn)(GLsizei,GLuint*); static fn real = (fn)q_sym("glGenTextures"); if (real) real(n,tex);
}
Q_EXPORT void glDeleteTextures(GLsizei n, const GLuint* tex) {
    typedef void (*fn)(GLsizei,const GLuint*); static fn real = (fn)q_sym("glDeleteTextures"); if (real) real(n,tex);
}
Q_EXPORT void glBindTexture(GLenum t, GLuint tex) {
    typedef void (*fn)(GLenum,GLuint); static fn real = (fn)q_sym("glBindTexture"); if (real) real(t,tex);
}
Q_EXPORT void glTexParameteri(GLenum t, GLenum p, GLint v) {
    typedef void (*fn)(GLenum,GLenum,GLint); static fn real = (fn)q_sym("glTexParameteri"); if (real) real(t,p,v);
}
Q_EXPORT void glTexParameterf(GLenum t, GLenum p, GLfloat v) {
    typedef void (*fn)(GLenum,GLenum,GLfloat); static fn real = (fn)q_sym("glTexParameterf"); if (real) real(t,p,v);
}
Q_EXPORT void glGenFramebuffers(GLsizei n, GLuint* f) {
    typedef void (*fn)(GLsizei,GLuint*); static fn real = (fn)q_sym("glGenFramebuffers"); if (real) real(n,f);
}
Q_EXPORT void glDeleteFramebuffers(GLsizei n, const GLuint* f) {
    typedef void (*fn)(GLsizei,const GLuint*); static fn real = (fn)q_sym("glDeleteFramebuffers"); if (real) real(n,f);
}
Q_EXPORT void glBindFramebuffer(GLenum t, GLuint f) {
    typedef void (*fn)(GLenum,GLuint); static fn real = (fn)q_sym("glBindFramebuffer"); if (real) real(t,f);
}
Q_EXPORT void glFramebufferTexture2D(GLenum t, GLenum a, GLenum tt, GLuint tex, GLint level) {
    typedef void (*fn)(GLenum,GLenum,GLenum,GLuint,GLint); static fn real = (fn)q_sym("glFramebufferTexture2D"); if (real) real(t,a,tt,tex,level);
}
Q_EXPORT void glGenRenderbuffers(GLsizei n, GLuint* r) {
    typedef void (*fn)(GLsizei,GLuint*); static fn real = (fn)q_sym("glGenRenderbuffers"); if (real) real(n,r);
}
Q_EXPORT void glDeleteRenderbuffers(GLsizei n, const GLuint* r) {
    typedef void (*fn)(GLsizei,const GLuint*); static fn real = (fn)q_sym("glDeleteRenderbuffers"); if (real) real(n,r);
}
Q_EXPORT void glBindRenderbuffer(GLenum t, GLuint r) {
    typedef void (*fn)(GLenum,GLuint); static fn real = (fn)q_sym("glBindRenderbuffer"); if (real) real(t,r);
}
Q_EXPORT void glRenderbufferStorage(GLenum t, GLenum fmt, GLsizei w, GLsizei h) {
    typedef void (*fn)(GLenum,GLenum,GLsizei,GLsizei); static fn real = (fn)q_sym("glRenderbufferStorage"); if (real) real(t,fmt,w,h);
}
Q_EXPORT void glGenerateMipmap(GLenum t) {
    typedef void (*fn)(GLenum); static fn real = (fn)q_sym("glGenerateMipmap"); if (real) real(t);
}
Q_EXPORT void glDrawArrays(GLenum mode, GLint first, GLsizei count) {
    typedef void (*fn)(GLenum,GLint,GLsizei); static fn real = (fn)q_sym("glDrawArrays"); if (real) real(mode,first,count);
}
Q_EXPORT void glDrawElements(GLenum mode, GLsizei count, GLenum type, const void* indices) {
    typedef void (*fn)(GLenum,GLsizei,GLenum,const void*); static fn real = (fn)q_sym("glDrawElements"); if (real) real(mode,count,type,indices);
}
Q_EXPORT void glDrawBuffers(GLsizei n, const GLenum* bufs) {
    typedef void (*fn)(GLsizei,const GLenum*); static fn real = (fn)q_sym("glDrawBuffers"); if (real) real(n,bufs);
}
Q_EXPORT void glGenVertexArrays(GLsizei n, GLuint* a) {
    typedef void (*fn)(GLsizei,GLuint*); static fn real = (fn)q_sym("glGenVertexArrays"); if (real) real(n,a);
}
Q_EXPORT void glDeleteVertexArrays(GLsizei n, const GLuint* a) {
    typedef void (*fn)(GLsizei,const GLuint*); static fn real = (fn)q_sym("glDeleteVertexArrays"); if (real) real(n,a);
}
Q_EXPORT void glBindVertexArray(GLuint a) {
    typedef void (*fn)(GLuint); static fn real = (fn)q_sym("glBindVertexArray"); if (real) real(a);
}
Q_EXPORT void glEnableVertexAttribArray(GLuint i) {
    typedef void (*fn)(GLuint); static fn real = (fn)q_sym("glEnableVertexAttribArray"); if (real) real(i);
}
Q_EXPORT void glDisableVertexAttribArray(GLuint i) {
    typedef void (*fn)(GLuint); static fn real = (fn)q_sym("glDisableVertexAttribArray"); if (real) real(i);
}
Q_EXPORT void glVertexAttribPointer(GLuint index, GLint size, GLenum type, GLboolean normalized, GLsizei stride, const void* pointer) {
    typedef void (*fn)(GLuint,GLint,GLenum,GLboolean,GLsizei,const void*); static fn real = (fn)q_sym("glVertexAttribPointer"); if (real) real(index,size,type,normalized,stride,pointer);
}
Q_EXPORT GLuint glCreateShader(GLenum type) {
    typedef GLuint (*fn)(GLenum); static fn real = (fn)q_sym("glCreateShader"); return real ? real(type) : 0;
}
Q_EXPORT void glDeleteShader(GLuint s) {
    typedef void (*fn)(GLuint); static fn real = (fn)q_sym("glDeleteShader"); if (real) real(s);
}
Q_EXPORT void glCompileShader(GLuint s) {
    typedef void (*fn)(GLuint); static fn real = (fn)q_sym("glCompileShader"); if (real) real(s);
}
Q_EXPORT GLuint glCreateProgram(void) {
    typedef GLuint (*fn)(void); static fn real = (fn)q_sym("glCreateProgram"); return real ? real() : 0;
}
Q_EXPORT void glDeleteProgram(GLuint p) {
    typedef void (*fn)(GLuint); static fn real = (fn)q_sym("glDeleteProgram"); if (real) real(p);
}
Q_EXPORT void glAttachShader(GLuint p, GLuint s) {
    typedef void (*fn)(GLuint,GLuint); static fn real = (fn)q_sym("glAttachShader"); if (real) real(p,s);
}
Q_EXPORT void glDetachShader(GLuint p, GLuint s) {
    typedef void (*fn)(GLuint,GLuint); static fn real = (fn)q_sym("glDetachShader"); if (real) real(p,s);
}
Q_EXPORT void glLinkProgram(GLuint p) {
    typedef void (*fn)(GLuint); static fn real = (fn)q_sym("glLinkProgram"); if (real) real(p);
}
Q_EXPORT void glUseProgram(GLuint p) {
    typedef void (*fn)(GLuint); static fn real = (fn)q_sym("glUseProgram"); if (real) real(p);
}
Q_EXPORT void glGetShaderiv(GLuint s, GLenum pname, GLint* params) {
    typedef void (*fn)(GLuint,GLenum,GLint*); static fn real = (fn)q_sym("glGetShaderiv"); if (real) real(s,pname,params);
}
Q_EXPORT void glGetProgramiv(GLuint p, GLenum pname, GLint* params) {
    typedef void (*fn)(GLuint,GLenum,GLint*); static fn real = (fn)q_sym("glGetProgramiv"); if (real) real(p,pname,params);
}
Q_EXPORT void glBindAttribLocation(GLuint program, GLuint index, const GLchar* name) {
    typedef void (*fn)(GLuint,GLuint,const GLchar*); static fn real = (fn)q_sym("glBindAttribLocation"); if (real) real(program,index,name);
}
Q_EXPORT GLint glGetUniformLocation(GLuint program, const GLchar* name) {
    typedef GLint (*fn)(GLuint,const GLchar*); static fn real = (fn)q_sym("glGetUniformLocation"); return real ? real(program,name) : -1;
}
Q_EXPORT GLint glGetAttribLocation(GLuint program, const GLchar* name) {
    typedef GLint (*fn)(GLuint,const GLchar*); static fn real = (fn)q_sym("glGetAttribLocation"); return real ? real(program,name) : -1;
}
Q_EXPORT void glUniform1i(GLint loc, GLint v) {
    typedef void (*fn)(GLint,GLint); static fn real = (fn)q_sym("glUniform1i"); if (real) real(loc,v);
}
Q_EXPORT void glUniform1f(GLint loc, GLfloat v) {
    typedef void (*fn)(GLint,GLfloat); static fn real = (fn)q_sym("glUniform1f"); if (real) real(loc,v);
}
Q_EXPORT void glUniform2f(GLint loc, GLfloat a, GLfloat b) {
    typedef void (*fn)(GLint,GLfloat,GLfloat); static fn real = (fn)q_sym("glUniform2f"); if (real) real(loc,a,b);
}
Q_EXPORT void glUniform3f(GLint loc, GLfloat a, GLfloat b, GLfloat c) {
    typedef void (*fn)(GLint,GLfloat,GLfloat,GLfloat); static fn real = (fn)q_sym("glUniform3f"); if (real) real(loc,a,b,c);
}
Q_EXPORT void glUniform4f(GLint loc, GLfloat a, GLfloat b, GLfloat c, GLfloat d) {
    typedef void (*fn)(GLint,GLfloat,GLfloat,GLfloat,GLfloat); static fn real = (fn)q_sym("glUniform4f"); if (real) real(loc,a,b,c,d);
}
Q_EXPORT void glUniformMatrix4fv(GLint location, GLsizei count, GLboolean transpose, const GLfloat* value) {
    typedef void (*fn)(GLint,GLsizei,GLboolean,const GLfloat*); static fn real = (fn)q_sym("glUniformMatrix4fv"); if (real) real(location,count,transpose,value);
}
Q_EXPORT GLenum glGetError(void) {
    typedef GLenum (*fn)(void); static fn real = (fn)q_sym("glGetError"); return real ? real() : GL_NO_ERROR;
}

Q_EXPORT __eglMustCastToProperFunctionPointerType eglGetProcAddress(const char* name) {
    quasar_init_handles();
    if (!name) return nullptr;
    #define MAP(n) if (strcmp(name, #n) == 0) return (__eglMustCastToProperFunctionPointerType)(void*)n;
    MAP(glGetString) MAP(glGetStringi) MAP(glGetIntegerv)
    MAP(glGenSamplers) MAP(glDeleteSamplers) MAP(glBindSampler) MAP(glIsSampler)
    MAP(glSamplerParameteri) MAP(glSamplerParameterf)
    MAP(glSamplerParameteriv) MAP(glSamplerParameterfv)
    MAP(glGetSamplerParameteriv) MAP(glGetSamplerParameterfv)
    MAP(glShaderSource)
    MAP(glEnable) MAP(glDisable) MAP(glViewport) MAP(glScissor)
    MAP(glClearColor) MAP(glClear) MAP(glDrawArrays) MAP(glDrawElements)
    MAP(glGenBuffers) MAP(glBindBuffer) MAP(glBufferData)
    MAP(glGenTextures) MAP(glBindTexture) MAP(glActiveTexture)
    MAP(glGenFramebuffers) MAP(glBindFramebuffer) MAP(glFramebufferTexture2D)
    MAP(glGenVertexArrays) MAP(glBindVertexArray)
    MAP(glCreateShader) MAP(glCompileShader) MAP(glCreateProgram) MAP(glLinkProgram) MAP(glUseProgram)
    MAP(glGetUniformLocation) MAP(glUniformMatrix4fv)
    MAP(glGetError)
    #undef MAP
    typedef void* (*eglGPA)(const char*);
    static eglGPA real_egl = (eglGPA)q_sym("eglGetProcAddress");
    if (real_egl) {
        void* p = real_egl(name);
        if (p) return (__eglMustCastToProperFunctionPointerType)p;
    }
    void* p = q_sym(name);
    if (p) return (__eglMustCastToProperFunctionPointerType)p;
    static void (*noop)() = [](){};
    return (__eglMustCastToProperFunctionPointerType)(void*)noop;
}

Q_EXPORT void quasar_core_boot() {
    quasar_init_handles();
    LOGI("QuasarCore boot complete | version=%s", kVersion);
}

} // extern "C"
