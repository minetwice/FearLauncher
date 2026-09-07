#include "fear_gl_emulation.h"
#include "fear_shader_logger.h"
#include <dlfcn.h>
#include <mutex>
#include <atomic>
#include <unordered_map>
#include <cstdlib>

static std::mutex g_emulationMutex;
static int g_emulationCounts[16] = {0};
static std::atomic<GLuint> s_nextSamplerID{1};

struct MappedBufferInfo {
    GLenum target = 0;
    GLuint bufferID = 0;
    GLintptr offset = 0;
    GLsizeiptr length = 0;
    GLbitfield access = 0;
    void* ptr = nullptr;
    bool isFallback = false;
};

static std::unordered_map<GLuint, MappedBufferInfo> g_mappedBuffers;
static std::mutex g_mappedBufferMutex;

static void logEmulation(int idx, const char* name) {
    std::lock_guard<std::mutex> lock(g_emulationMutex);
    if (g_emulationCounts[idx] < 10) {
        g_emulationCounts[idx]++;
        LOG_INFO("[FearRender] emulated: %s", name);
    }
}

extern "C" {

void glDrawElementsBaseVertex(GLenum mode, GLsizei count, GLenum type, const void* indices, GLint basevertex) {
    typedef void (*glDrawElementsBaseVertex_pfn)(GLenum, GLsizei, GLenum, const void*, GLint);
    static glDrawElementsBaseVertex_pfn real_fn = (glDrawElementsBaseVertex_pfn)dlsym(RTLD_NEXT, "glDrawElementsBaseVertex");
    if (real_fn) {
        real_fn(mode, count, type, indices, basevertex);
    } else {
        typedef void (*glDrawElements_pfn)(GLenum, GLsizei, GLenum, const void*);
        static glDrawElements_pfn real_draw = (glDrawElements_pfn)dlsym(RTLD_NEXT, "glDrawElements");
        if (real_draw) real_draw(mode, count, type, indices);
    }
}

void glPushDebugGroup(GLenum source, GLuint id, GLsizei length, const GLchar* message) {
    typedef void (*glPushDebugGroup_pfn)(GLenum, GLuint, GLsizei, const GLchar*);
    static glPushDebugGroup_pfn real_fn = (glPushDebugGroup_pfn)dlsym(RTLD_NEXT, "glPushDebugGroup");
    if (real_fn) real_fn(source, id, length, message);
}

void glPopDebugGroup() {
    typedef void (*glPopDebugGroup_pfn)();
    static glPopDebugGroup_pfn real_fn = (glPopDebugGroup_pfn)dlsym(RTLD_NEXT, "glPopDebugGroup");
    if (real_fn) real_fn();
}

void glDebugMessageCallback(GLDEBUGPROC callback, const void* userParam) {
    typedef void (*glDebugMessageCallback_pfn)(GLDEBUGPROC, const void*);
    static glDebugMessageCallback_pfn real_fn = (glDebugMessageCallback_pfn)dlsym(RTLD_NEXT, "glDebugMessageCallback");
    if (real_fn) real_fn(callback, userParam);
}

void fear_glMemoryBarrier(GLbitfield barriers) {
    logEmulation(0, "glMemoryBarrier");
    typedef void (*glMemoryBarrier_pfn)(GLbitfield);
    static glMemoryBarrier_pfn real_glMemoryBarrier = (glMemoryBarrier_pfn)dlsym(RTLD_NEXT, "glMemoryBarrier");
    if (real_glMemoryBarrier) {
        real_glMemoryBarrier(barriers);
    } else {
        typedef void (*glFinish_pfn)();
        static glFinish_pfn real_glFinish = (glFinish_pfn)dlsym(RTLD_NEXT, "glFinish");
        if (real_glFinish) real_glFinish();
    }
}

void fear_glTextureBarrier() {
    logEmulation(1, "glTextureBarrier");
    typedef void (*glFinish_pfn)();
    static glFinish_pfn real_glFinish = (glFinish_pfn)dlsym(RTLD_NEXT, "glFinish");
    if (real_glFinish) real_glFinish();
}

void fear_glBufferStorage(GLenum target, GLsizeiptr size, const void* data, GLbitfield flags) {
    logEmulation(2, "glBufferStorage");
    typedef void (*glBufferData_pfn)(GLenum, GLsizeiptr, const void*, GLenum);
    static glBufferData_pfn real_glBufferData = (glBufferData_pfn)dlsym(RTLD_NEXT, "glBufferData");
    if (real_glBufferData) {
        real_glBufferData(target, size, data, GL_DYNAMIC_DRAW);
    }
}

void fear_glClearTexImage(GLuint texture, GLint level, GLenum format, GLenum type, const void* data) {
    logEmulation(3, "glClearTexImage");
    typedef void (*glGenFramebuffers_pfn)(GLsizei, GLuint*);
    typedef void (*glBindFramebuffer_pfn)(GLenum, GLuint);
    typedef void (*glFramebufferTexture2D_pfn)(GLenum, GLenum, GLenum, GLuint, GLint);
    typedef void (*glClear_pfn)(GLbitfield);
    typedef void (*glDeleteFramebuffers_pfn)(GLsizei, const GLuint*);

    static glGenFramebuffers_pfn real_glGenFramebuffers = (glGenFramebuffers_pfn)dlsym(RTLD_NEXT, "glGenFramebuffers");
    static glBindFramebuffer_pfn real_glBindFramebuffer = (glBindFramebuffer_pfn)dlsym(RTLD_NEXT, "glBindFramebuffer");
    static glFramebufferTexture2D_pfn real_glFramebufferTexture2D = (glFramebufferTexture2D_pfn)dlsym(RTLD_NEXT, "glFramebufferTexture2D");
    static glClear_pfn real_glClear = (glClear_pfn)dlsym(RTLD_NEXT, "glClear");
    static glDeleteFramebuffers_pfn real_glDeleteFramebuffers = (glDeleteFramebuffers_pfn)dlsym(RTLD_NEXT, "glDeleteFramebuffers");

    if (real_glGenFramebuffers && real_glBindFramebuffer && real_glFramebufferTexture2D && real_glClear && real_glDeleteFramebuffers) {
        GLuint fbo = 0;
        real_glGenFramebuffers(1, &fbo);
        real_glBindFramebuffer(0x8D40 /* GL_FRAMEBUFFER */, fbo);
        real_glFramebufferTexture2D(0x8D40 /* GL_FRAMEBUFFER */, 0x8CE0 /* GL_COLOR_ATTACHMENT0 */, 0x0DE1 /* GL_TEXTURE_2D */, texture, level);
        real_glClear(0x00004000 /* GL_COLOR_BUFFER_BIT */ | 0x00000100 /* GL_DEPTH_BUFFER_BIT */);
        real_glBindFramebuffer(0x8D40 /* GL_FRAMEBUFFER */, 0);
        real_glDeleteFramebuffers(1, &fbo);
    }
}

void fear_glClearTexSubImage(GLuint texture, GLint level, GLint xoffset, GLint yoffset, GLint zoffset, GLsizei width, GLsizei height, GLsizei depth, GLenum format, GLenum type, const void* data) {
    logEmulation(4, "glClearTexSubImage");
    fear_glClearTexImage(texture, level, format, type, data);
}

void fear_glMultiDrawArrays(GLenum mode, const GLint* first, const GLsizei* count, GLsizei drawcount) {
    logEmulation(5, "glMultiDrawArrays");
    typedef void (*glDrawArrays_pfn)(GLenum, GLint, GLsizei);
    static glDrawArrays_pfn real_glDrawArrays = (glDrawArrays_pfn)dlsym(RTLD_NEXT, "glDrawArrays");
    if (real_glDrawArrays) {
        for (GLsizei i = 0; i < drawcount; i++) {
            real_glDrawArrays(mode, first[i], count[i]);
        }
    }
}

void fear_glMultiDrawElements(GLenum mode, const GLsizei* count, GLenum type, const void* const* indices, GLsizei drawcount) {
    logEmulation(6, "glMultiDrawElements");
    typedef void (*glDrawElements_pfn)(GLenum, GLsizei, GLenum, const void*);
    static glDrawElements_pfn real_glDrawElements = (glDrawElements_pfn)dlsym(RTLD_NEXT, "glDrawElements");
    if (real_glDrawElements) {
        for (GLsizei i = 0; i < drawcount; i++) {
            real_glDrawElements(mode, count[i], type, indices[i]);
        }
    }
}

void fear_glInvalidateFramebuffer(GLenum target, GLsizei numAttachments, const GLenum* attachments) {
    logEmulation(7, "glInvalidateFramebuffer");
    typedef void (*glInvalidateFramebuffer_pfn)(GLenum, GLsizei, const GLenum*);
    static glInvalidateFramebuffer_pfn real_glInvalidateFramebuffer = (glInvalidateFramebuffer_pfn)dlsym(RTLD_NEXT, "glInvalidateFramebuffer");
    if (real_glInvalidateFramebuffer) {
        real_glInvalidateFramebuffer(target, numAttachments, attachments);
    }
}

void fear_glCreateBuffers(GLsizei n, GLuint* buffers) {
    logEmulation(8, "glCreateBuffers");
    typedef void (*glGenBuffers_pfn)(GLsizei, GLuint*);
    static glGenBuffers_pfn real_glGenBuffers = (glGenBuffers_pfn)dlsym(RTLD_NEXT, "glGenBuffers");
    if (real_glGenBuffers) {
        real_glGenBuffers(n, buffers);
    }
}

void fear_glNamedBufferData(GLuint buffer, GLsizeiptr size, const void* data, GLenum usage) {
    logEmulation(9, "glNamedBufferData");
    typedef void (*glBindBuffer_pfn)(GLenum, GLuint);
    typedef void (*glBufferData_pfn)(GLenum, GLsizeiptr, const void*, GLenum);
    static glBindBuffer_pfn real_glBindBuffer = (glBindBuffer_pfn)dlsym(RTLD_NEXT, "glBindBuffer");
    static glBufferData_pfn real_glBufferData = (glBufferData_pfn)dlsym(RTLD_NEXT, "glBufferData");

    if (real_glBindBuffer && real_glBufferData) {
        real_glBindBuffer(GL_ARRAY_BUFFER, buffer);
        real_glBufferData(GL_ARRAY_BUFFER, size, data, usage);
    }
}

void fear_glNamedBufferSubData(GLuint buffer, GLintptr offset, GLsizeiptr size, const void* data) {
    logEmulation(10, "glNamedBufferSubData");
    typedef void (*glBindBuffer_pfn)(GLenum, GLuint);
    typedef void (*glBufferSubData_pfn)(GLenum, GLintptr, GLsizeiptr, const void*);
    static glBindBuffer_pfn real_glBindBuffer = (glBindBuffer_pfn)dlsym(RTLD_NEXT, "glBindBuffer");
    static glBufferSubData_pfn real_glBufferSubData = (glBufferSubData_pfn)dlsym(RTLD_NEXT, "glBufferSubData");

    if (real_glBindBuffer && real_glBufferSubData) {
        real_glBindBuffer(GL_ARRAY_BUFFER, buffer);
        real_glBufferSubData(GL_ARRAY_BUFFER, offset, size, data);
    }
}

void fear_glBindTextureUnit(GLuint unit, GLuint texture) {
    logEmulation(11, "glBindTextureUnit");
    typedef void (*glActiveTexture_pfn)(GLenum);
    typedef void (*glBindTexture_pfn)(GLenum, GLuint);
    static glActiveTexture_pfn real_glActiveTexture = (glActiveTexture_pfn)dlsym(RTLD_NEXT, "glActiveTexture");
    static glBindTexture_pfn real_glBindTexture = (glBindTexture_pfn)dlsym(RTLD_NEXT, "glBindTexture");

    if (real_glActiveTexture && real_glBindTexture) {
        real_glActiveTexture(GL_TEXTURE0 + unit);
        real_glBindTexture(GL_TEXTURE_2D, texture);
    }
}

// Buffer Mapping Implementation
void* fear_glMapBufferRange(GLenum target, GLintptr offset, GLsizeiptr length, GLbitfield access) {
    typedef void* (*glMapBufferRange_pfn)(GLenum, GLintptr, GLsizeiptr, GLbitfield);
    static glMapBufferRange_pfn real_glMapBufferRange = (glMapBufferRange_pfn)dlsym(RTLD_DEFAULT, "glMapBufferRange");

    GLuint bufferID = 0;
    typedef void (*glGetIntegerv_pfn)(GLenum, GLint*);
    static glGetIntegerv_pfn real_glGetIntegerv = (glGetIntegerv_pfn)dlsym(RTLD_DEFAULT, "glGetIntegerv");

    GLenum bindingPname = GL_ARRAY_BUFFER_BINDING;
    if (target == GL_ELEMENT_ARRAY_BUFFER) bindingPname = GL_ELEMENT_ARRAY_BUFFER_BINDING;
    else if (target == GL_UNIFORM_BUFFER) bindingPname = 0x8A28;
    else if (target == GL_SHADER_STORAGE_BUFFER) bindingPname = 0x90D3;

    if (real_glGetIntegerv) {
        GLint b = 0;
        real_glGetIntegerv(bindingPname, &b);
        bufferID = static_cast<GLuint>(b);
    }

    GLbitfield safeAccess = access & ~(0x0040 | 0x0080); // Strip persistent/coherent bits if unsupported
    void* result = nullptr;
    if (real_glMapBufferRange) {
        result = real_glMapBufferRange(target, offset, length, safeAccess);
    }

    if (result != nullptr) {
        std::lock_guard<std::mutex> lock(g_mappedBufferMutex);
        MappedBufferInfo info;
        info.target = target;
        info.bufferID = bufferID;
        info.offset = offset;
        info.length = length;
        info.access = access;
        info.ptr = result;
        info.isFallback = false;
        g_mappedBuffers[bufferID] = info;
        return result;
    }

    // Fallback: allocate non-null shadow buffer
    if (length <= 0) length = 65536;
    void* shadowPtr = malloc(length);
    if (!shadowPtr) shadowPtr = calloc(1, 65536);

    MappedBufferInfo info;
    info.target = target;
    info.bufferID = bufferID;
    info.offset = offset;
    info.length = length;
    info.access = access;
    info.ptr = shadowPtr;
    info.isFallback = true;

    {
        std::lock_guard<std::mutex> lock(g_mappedBufferMutex);
        g_mappedBuffers[bufferID] = info;
    }

    LOG_INFO("[FearRender] glMapBufferRange fallback buffer mapped (target=0x%X, len=%ld)", target, (long)length);
    return shadowPtr;
}

void* fear_glMapBuffer(GLenum target, GLenum access) {
    GLint bufferSize = 65536;
    typedef void (*glGetBufferParameteriv_pfn)(GLenum, GLenum, GLint*);
    static glGetBufferParameteriv_pfn real_glGetBufferParameteriv = (glGetBufferParameteriv_pfn)dlsym(RTLD_DEFAULT, "glGetBufferParameteriv");
    if (real_glGetBufferParameteriv) {
        real_glGetBufferParameteriv(target, GL_BUFFER_SIZE, &bufferSize);
    }
    if (bufferSize <= 0) bufferSize = 65536;

    GLbitfield accessRange = GL_MAP_WRITE_BIT;
    if (access == GL_READ_ONLY) accessRange = GL_MAP_READ_BIT;
    else if (access == GL_READ_WRITE) accessRange = GL_MAP_READ_BIT | GL_MAP_WRITE_BIT;

    return fear_glMapBufferRange(target, 0, bufferSize, accessRange);
}

GLboolean fear_glUnmapBuffer(GLenum target) {
    GLuint bufferID = 0;
    typedef void (*glGetIntegerv_pfn)(GLenum, GLint*);
    static glGetIntegerv_pfn real_glGetIntegerv = (glGetIntegerv_pfn)dlsym(RTLD_DEFAULT, "glGetIntegerv");

    GLenum bindingPname = GL_ARRAY_BUFFER_BINDING;
    if (target == GL_ELEMENT_ARRAY_BUFFER) bindingPname = GL_ELEMENT_ARRAY_BUFFER_BINDING;
    else if (target == GL_UNIFORM_BUFFER) bindingPname = 0x8A28;
    else if (target == GL_SHADER_STORAGE_BUFFER) bindingPname = 0x90D3;

    if (real_glGetIntegerv) {
        GLint b = 0;
        real_glGetIntegerv(bindingPname, &b);
        bufferID = static_cast<GLuint>(b);
    }

    MappedBufferInfo info;
    bool found = false;
    {
        std::lock_guard<std::mutex> lock(g_mappedBufferMutex);
        auto it = g_mappedBuffers.find(bufferID);
        if (it != g_mappedBuffers.end()) {
            info = it->second;
            g_mappedBuffers.erase(it);
            found = true;
        }
    }

    if (found && info.isFallback && info.ptr) {
        typedef void (*glBufferSubData_pfn)(GLenum, GLintptr, GLsizeiptr, const void*);
        static glBufferSubData_pfn real_glBufferSubData = (glBufferSubData_pfn)dlsym(RTLD_DEFAULT, "glBufferSubData");
        if (real_glBufferSubData) {
            real_glBufferSubData(info.target, info.offset, info.length, info.ptr);
        }
        free(info.ptr);
        return GL_TRUE;
    }

    typedef GLboolean (*glUnmapBuffer_pfn)(GLenum);
    static glUnmapBuffer_pfn real_glUnmapBuffer = (glUnmapBuffer_pfn)dlsym(RTLD_DEFAULT, "glUnmapBuffer");
    return real_glUnmapBuffer ? real_glUnmapBuffer(target) : GL_TRUE;
}

// Sampler Emulation Implementation
void fear_glGenSamplers(GLsizei count, GLuint* samplers) {
    if (!samplers || count <= 0) return;
    typedef void (*glGenSamplers_pfn)(GLsizei, GLuint*);
    static glGenSamplers_pfn real_glGenSamplers = (glGenSamplers_pfn)dlsym(RTLD_DEFAULT, "glGenSamplers");
    if (!real_glGenSamplers) real_glGenSamplers = (glGenSamplers_pfn)dlsym(RTLD_DEFAULT, "glGenSamplersOES");

    if (real_glGenSamplers) {
        real_glGenSamplers(count, samplers);
        bool valid = true;
        for (GLsizei i = 0; i < count; ++i) {
            if (samplers[i] == 0) { valid = false; break; }
        }
        if (valid) return;
    }

    for (GLsizei i = 0; i < count; ++i) {
        samplers[i] = s_nextSamplerID.fetch_add(1);
    }
}

void fear_glBindSampler(GLuint unit, GLuint sampler) {
    typedef void (*glBindSampler_pfn)(GLuint, GLuint);
    static glBindSampler_pfn real_glBindSampler = (glBindSampler_pfn)dlsym(RTLD_DEFAULT, "glBindSampler");
    if (!real_glBindSampler) real_glBindSampler = (glBindSampler_pfn)dlsym(RTLD_DEFAULT, "glBindSamplerOES");
    if (real_glBindSampler) real_glBindSampler(unit, sampler);
}

void fear_glDeleteSamplers(GLsizei count, const GLuint* samplers) {
    if (!samplers || count <= 0) return;
    typedef void (*glDeleteSamplers_pfn)(GLsizei, const GLuint*);
    static glDeleteSamplers_pfn real_glDeleteSamplers = (glDeleteSamplers_pfn)dlsym(RTLD_DEFAULT, "glDeleteSamplers");
    if (!real_glDeleteSamplers) real_glDeleteSamplers = (glDeleteSamplers_pfn)dlsym(RTLD_DEFAULT, "glDeleteSamplersOES");
    if (real_glDeleteSamplers) real_glDeleteSamplers(count, samplers);
}

GLboolean fear_glIsSampler(GLuint sampler) {
    typedef GLboolean (*glIsSampler_pfn)(GLuint);
    static glIsSampler_pfn real_glIsSampler = (glIsSampler_pfn)dlsym(RTLD_DEFAULT, "glIsSampler");
    if (!real_glIsSampler) real_glIsSampler = (glIsSampler_pfn)dlsym(RTLD_DEFAULT, "glIsSamplerOES");
    return real_glIsSampler ? real_glIsSampler(sampler) : GL_TRUE;
}

void fear_glSamplerParameteri(GLuint sampler, GLenum pname, GLint param) {
    typedef void (*glSamplerParameteri_pfn)(GLuint, GLenum, GLint);
    static glSamplerParameteri_pfn real_glSamplerParameteri = (glSamplerParameteri_pfn)dlsym(RTLD_DEFAULT, "glSamplerParameteri");
    if (!real_glSamplerParameteri) real_glSamplerParameteri = (glSamplerParameteri_pfn)dlsym(RTLD_DEFAULT, "glSamplerParameteriOES");
    if (real_glSamplerParameteri) real_glSamplerParameteri(sampler, pname, param);
}

void fear_glSamplerParameterf(GLuint sampler, GLenum pname, GLfloat param) {
    typedef void (*glSamplerParameterf_pfn)(GLuint, GLenum, GLfloat);
    static glSamplerParameterf_pfn real_glSamplerParameterf = (glSamplerParameterf_pfn)dlsym(RTLD_DEFAULT, "glSamplerParameterf");
    if (!real_glSamplerParameterf) real_glSamplerParameterf = (glSamplerParameterf_pfn)dlsym(RTLD_DEFAULT, "glSamplerParameterfOES");
    if (real_glSamplerParameterf) real_glSamplerParameterf(sampler, pname, param);
}

void fear_glSamplerParameteriv(GLuint sampler, GLenum pname, const GLint* param) {
    typedef void (*glSamplerParameteriv_pfn)(GLuint, GLenum, const GLint*);
    static glSamplerParameteriv_pfn real_glSamplerParameteriv = (glSamplerParameteriv_pfn)dlsym(RTLD_DEFAULT, "glSamplerParameteriv");
    if (!real_glSamplerParameteriv) real_glSamplerParameteriv = (glSamplerParameteriv_pfn)dlsym(RTLD_DEFAULT, "glSamplerParameterivOES");
    if (real_glSamplerParameteriv) real_glSamplerParameteriv(sampler, pname, param);
}

void fear_glSamplerParameterfv(GLuint sampler, GLenum pname, const GLfloat* param) {
    typedef void (*glSamplerParameterfv_pfn)(GLuint, GLenum, const GLfloat*);
    static glSamplerParameterfv_pfn real_glSamplerParameterfv = (glSamplerParameterfv_pfn)dlsym(RTLD_DEFAULT, "glSamplerParameterfv");
    if (!real_glSamplerParameterfv) real_glSamplerParameterfv = (glSamplerParameterfv_pfn)dlsym(RTLD_DEFAULT, "glSamplerParameterfvOES");
    if (real_glSamplerParameterfv) real_glSamplerParameterfv(sampler, pname, param);
}

// Module 2 Implementation: OpenGL-to-Vulkan Extension Emulation Layer
static std::unordered_map<uint64_t, GLuint> g_bindlessTextureRegistry;
static std::mutex g_bindlessRegistryMutex;

uint64_t fear_glGetTextureHandleARB(GLuint texture) {
    logEmulation(12, "glGetTextureHandleARB (emulated bindless handle)");
    uint64_t handle = 0xFEA1000000000000ULL | (uint64_t)texture;
    std::lock_guard<std::mutex> lock(g_bindlessRegistryMutex);
    g_bindlessTextureRegistry[handle] = texture;
    return handle;
}

void fear_glMakeTextureHandleResidentARB(uint64_t handle) {
    logEmulation(13, "glMakeTextureHandleResidentARB");
    GLuint texID = 0;
    {
        std::lock_guard<std::mutex> lock(g_bindlessRegistryMutex);
        auto it = g_bindlessTextureRegistry.find(handle);
        if (it != g_bindlessTextureRegistry.end()) {
            texID = it->second;
        }
    }
    if (texID != 0) {
        typedef void (*glBindTexture_pfn)(GLenum, GLuint);
        static glBindTexture_pfn real_glBindTexture = (glBindTexture_pfn)dlsym(RTLD_NEXT, "glBindTexture");
        if (real_glBindTexture) {
            real_glBindTexture(GL_TEXTURE_2D, texID);
        }
    }
}

void fear_glMakeTextureHandleNonResidentARB(uint64_t handle) {
    logEmulation(14, "glMakeTextureHandleNonResidentARB");
}

void fear_glBindImageTexture(GLuint unit, GLuint texture, GLint level, GLboolean layered, GLint layer, GLenum access, GLenum format) {
    logEmulation(15, "glBindImageTexture");
    typedef void (*glBindImageTexture_pfn)(GLuint, GLuint, GLint, GLboolean, GLint, GLenum, GLenum);
    static glBindImageTexture_pfn real_fn = (glBindImageTexture_pfn)dlsym(RTLD_NEXT, "glBindImageTexture");
    if (real_fn) {
        real_fn(unit, texture, level, layered, layer, access, format);
    }
}

} // extern "C"
