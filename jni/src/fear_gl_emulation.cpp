#include "fear_gl_emulation.h"
#include "fear_shader_logger.h"
#include <dlfcn.h>
#include <mutex>

static std::mutex g_emulationMutex;
static int g_emulationCounts[16] = {0};

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
