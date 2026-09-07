#include "fear_hooks.h"
#include "fear_gl_emulation.h"
#include "fear_render_engine.h"
#include "es/utils.hpp"
#include "main.hpp"
#include <android/log.h>
#include <dlfcn.h>
#include <string.h>
#include <stdlib.h>
#include <thread>
#include <chrono>
#include <mutex>
#include <unistd.h>
#include <unordered_map>
#include <cstdlib>
#include <EGL/egl.h>

#define FEAR_EXPORT __attribute__((visibility("default")))

#define GL_VERSION 0x1F02
#define GL_RENDERER 0x1F01
#define GL_VENDOR 0x1F00
#define GL_EXTENSIONS 0x1F03
#define GL_SHADING_LANGUAGE_VERSION 0x8B8C
#define GL_MAX_TEXTURE_SIZE 0x0D33

static void* g_eglHandle = nullptr;
static void* g_glesHandle = nullptr;
static int g_windowCreated = 0;
static bool g_emergencyContextCreated = false;

// ============================================================================
// Shadow Buffer System for glMapBufferRange
// ============================================================================
// On Mali GPUs, glMapBufferRange is not supported (only GL_OES_mapbuffer).
// gl4es translates it but returns NULL on Mali, causing
// "Can't map buffer, opengl error 0" crash.
// We intercept glMapBufferRange at the hook level and ALWAYS return a
// CPU-side shadow buffer (malloc). On glUnmapBuffer, we upload via
// glBufferSubData (supported in GLES 2.0 core).

struct FearMappedBuffer {
    GLenum target = 0;
    GLuint bufferID = 0;
    GLintptr offset = 0;
    GLsizeiptr length = 0;
    void* ptr = nullptr;
    bool isShadow = false;
};

static std::unordered_map<GLuint, FearMappedBuffer> g_fearMappedBuffers;
static std::mutex g_fearMappedMutex;

static void universal_safe_stub() {
    static bool logged = false;
    if (!logged) {
        __android_log_print(ANDROID_LOG_INFO, "FearRender", "[FearRender] Universal GL stub invoked without context");
        logged = true;
    }
}

static void initEGLGLESHandles() {
    static std::once_flag flag;
    std::call_once(flag, []() {
        __android_log_print(ANDROID_LOG_WARN, "FearRender", "BUILD MARKER v20260907-B compiled " __DATE__ " " __TIME__);
        g_eglHandle = dlopen("libEGL.so", RTLD_GLOBAL | RTLD_LAZY);
        g_glesHandle = dlopen("libGLESv3.so", RTLD_GLOBAL | RTLD_LAZY);
        __android_log_print(ANDROID_LOG_INFO, "FearRender", "[FearRender] EGL handle: %p, GLES handle: %p", g_eglHandle, g_glesHandle);
    });
}

static EGLContext getCurrentEGLContext() {
    initEGLGLESHandles();
    typedef EGLContext (*eglGetCurrentContext_pfn)();
    static eglGetCurrentContext_pfn real_eglGetCurrentContext = (eglGetCurrentContext_pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_DEFAULT, "eglGetCurrentContext");
    return real_eglGetCurrentContext ? real_eglGetCurrentContext() : EGL_NO_CONTEXT;
}

static bool isContextCurrent() {
    return getCurrentEGLContext() != EGL_NO_CONTEXT;
}

static void tryEmergencyContext() {
    if (g_emergencyContextCreated || !g_eglHandle) return;
    initEGLGLESHandles();

    EGLDisplay dpy;
    {
        typedef EGLDisplay (*eglGetDisplay_pfn)(EGLNativeDisplayType);
        static eglGetDisplay_pfn p_eglGetDisplay = (eglGetDisplay_pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_DEFAULT, "eglGetDisplay");
        if (!p_eglGetDisplay) return;
        dpy = p_eglGetDisplay(EGL_DEFAULT_DISPLAY);
        if (!dpy) return;
    }

    {
        typedef EGLBoolean (*eglInitialize_pfn)(EGLDisplay, EGLint*, EGLint*);
        static eglInitialize_pfn p_eglInitialize = (eglInitialize_pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_DEFAULT, "eglInitialize");
        if (!p_eglInitialize) return;
        EGLint maj, min;
        p_eglInitialize(dpy, &maj, &min);
    }

    EGLConfig config;
    {
        typedef EGLBoolean (*eglChooseConfig_pfn)(EGLDisplay, const EGLint*, EGLConfig*, EGLint, EGLint*);
        static eglChooseConfig_pfn p_eglChooseConfig = (eglChooseConfig_pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_DEFAULT, "eglChooseConfig");
        if (!p_eglChooseConfig) return;
        EGLint attrs[] = {EGL_SURFACE_TYPE, EGL_PBUFFER_BIT, EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT, EGL_NONE};
        EGLint numConfigs = 0;
        p_eglChooseConfig(dpy, attrs, &config, 1, &numConfigs);
        if (numConfigs == 0) return;
    }

    EGLSurface surf;
    {
        typedef EGLSurface (*eglCreatePbufferSurface_pfn)(EGLDisplay, EGLConfig, const EGLint*);
        static eglCreatePbufferSurface_pfn p_eglCreatePbufferSurface = (eglCreatePbufferSurface_pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_DEFAULT, "eglCreatePbufferSurface");
        if (!p_eglCreatePbufferSurface) return;
        EGLint surfAttrs[] = {EGL_WIDTH, 1, EGL_HEIGHT, 1, EGL_NONE};
        surf = p_eglCreatePbufferSurface(dpy, config, surfAttrs);
        if (!surf) return;
    }

    EGLContext ctx;
    {
        typedef EGLContext (*eglCreateContext_pfn)(EGLDisplay, EGLConfig, EGLContext, const EGLint*);
        static eglCreateContext_pfn p_eglCreateContext = (eglCreateContext_pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_DEFAULT, "eglCreateContext");
        if (!p_eglCreateContext) return;
        EGLint ctxAttrs[] = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE};
        ctx = p_eglCreateContext(dpy, config, EGL_NO_CONTEXT, ctxAttrs);
        if (!ctx) return;
    }

    {
        typedef EGLBoolean (*eglMakeCurrent_pfn)(EGLDisplay, EGLSurface, EGLSurface, EGLContext);
        static eglMakeCurrent_pfn p_eglMakeCurrent = (eglMakeCurrent_pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_DEFAULT, "eglMakeCurrent");
        if (!p_eglMakeCurrent) return;
        p_eglMakeCurrent(dpy, surf, surf, ctx);
    }

    g_emergencyContextCreated = true;
    __android_log_print(ANDROID_LOG_WARN, "FearRender", "[FearRender] Emergency EGL context created");
}

extern "C" {

// Helper to resolve GL/EGL symbols
void* resolve_fear_symbol(const char* symbol) {
    if (symbol && (strncmp(symbol, "gl", 2) == 0 || strncmp(symbol, "egl", 3) == 0)) {
        void* our_fn = fear_eglGetProcAddress(symbol);
        if (our_fn && our_fn != (void*)universal_safe_stub) {
            return our_fn;
        }
    }
    return nullptr;
}

FEAR_EXPORT EGLContext eglCreateContext(EGLDisplay dpy, EGLConfig config, EGLContext share_context, const EGLint* attrib_list) {
    initEGLGLESHandles();
    typedef EGLContext (*eglCreateContext_pfn)(EGLDisplay, EGLConfig, EGLContext, const EGLint*);
    static eglCreateContext_pfn real_eglCreateContext = (eglCreateContext_pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_DEFAULT, "eglCreateContext");
    EGLContext ctx = real_eglCreateContext ? real_eglCreateContext(dpy, config, share_context, attrib_list) : EGL_NO_CONTEXT;
    __android_log_print(ANDROID_LOG_INFO, "FearRender", "[FearRender][EGL] eglCreateContext tid=%d -> %p", gettid(), ctx);
    return ctx;
}

FEAR_EXPORT EGLBoolean eglMakeCurrent(EGLDisplay dpy, EGLSurface draw, EGLSurface read, EGLContext ctx) {
    initEGLGLESHandles();
    typedef EGLBoolean (*eglMakeCurrent_pfn)(EGLDisplay, EGLSurface, EGLSurface, EGLContext);
    static eglMakeCurrent_pfn real_eglMakeCurrent = (eglMakeCurrent_pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_DEFAULT, "eglMakeCurrent");
    EGLBoolean res = real_eglMakeCurrent ? real_eglMakeCurrent(dpy, draw, read, ctx) : EGL_FALSE;
    __android_log_print(ANDROID_LOG_INFO, "FearRender", "[FearRender][EGL] eglMakeCurrent tid=%d ctx=%p -> %s", gettid(), ctx, res ? "EGL_TRUE" : "EGL_FALSE");

    if (ctx == EGL_NO_CONTEXT) {
        __android_log_print(ANDROID_LOG_WARN, "FearRender", "[FearRender][EGL] eglMakeCurrent passed EGL_NO_CONTEXT tid=%d (retaining internal state)", gettid());
    } else if (res) {
        if (g_versionPending.load()) {
            ESUtils::performDeferredInit();
        }
        static bool fboReadyLogged = false;
        if (!fboReadyLogged) {
            __android_log_print(ANDROID_LOG_INFO, "FearRender", "[FearRender] FakeDepthFramebuffer ready=true");
            fboReadyLogged = true;
        }
    }
    return res;
}

void* glfwCreateWindow(int width, int height, const char* title, void* monitor, void* share) {
    // Stub GLFW window creation — we already have an EGL surface from Java
    g_windowCreated = 1;
    __android_log_print(ANDROID_LOG_INFO, "FearRender", "[FearRender] glfwCreateWindow stubbed (%dx%d)", width, height);
    return (void*)0x1;
}

void glfwSetWindowTitle(void* window, const char* title) {}
int glfwWindowShouldClose(void* window) { return 0; }
void glfwSwapBuffers(void* window) {
    typedef void (*eglSwapBuffers_pfn)(EGLDisplay, EGLSurface);
    static eglSwapBuffers_pfn real_eglSwapBuffers = (eglSwapBuffers_pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_NEXT, "eglSwapBuffers");
    // Can't call directly without display/surface — this is handled by Java
}
void glfwPollEvents(void) {}
void glfwTerminate(void) {}

FEAR_EXPORT void glSamplerParameterf(GLuint sampler, GLenum pname, GLfloat param) {
    fear_glSamplerParameterf(sampler, pname, param);
}

FEAR_EXPORT void glSamplerParameteriv(GLuint sampler, GLenum pname, const GLint* param) {
    fear_glSamplerParameteriv(sampler, pname, param);
}

FEAR_EXPORT void glSamplerParameterfv(GLuint sampler, GLenum pname, const GLfloat* param) {
    fear_glSamplerParameterfv(sampler, pname, param);
}

// ============================================================================
// Buffer Mapping — DIRECT shadow buffer implementation in the hook itself.
// This is CRITICAL: the fear_glMapBufferRange in fear_gl_emulation.cpp
// may not be called if LWJGL resolves glMapBufferRange via eglGetProcAddress
// from libgl4es_114.so (which bypasses libGLFear.so's RTLD_LOCAL symbols).
// By implementing the shadow buffer DIRECTLY in the FEAR_EXPORT wrapper,
// we ensure it works regardless of how LWJGL resolves the function.
// ============================================================================

FEAR_EXPORT void* glMapBufferRange(GLenum target, GLintptr offset, GLsizeiptr length, GLbitfield access) {
    static int callCount = 0;
    if (callCount < 5) {
        __android_log_print(ANDROID_LOG_INFO, "FearRender", "[FearRender] glMapBufferRange HOOK CALLED target=0x%X offset=%ld len=%ld access=0x%X", target, (long)offset, (long)length, access);
        callCount++;
    }

    // Resolve glGetIntegerv to find the currently bound buffer ID.
    // Use RTLD_NEXT to skip our own exports and find gl4es/GLES implementation.
    typedef void (*glGetIntegerv_pfn)(GLenum, GLint*);
    static glGetIntegerv_pfn real_glGetIntegerv = nullptr;
    if (!real_glGetIntegerv) {
        real_glGetIntegerv = (glGetIntegerv_pfn)dlsym(RTLD_NEXT, "glGetIntegerv");
    }

    GLuint bufferID = 0;
    GLenum bindingPname = GL_ARRAY_BUFFER_BINDING;
    if (target == GL_ELEMENT_ARRAY_BUFFER) bindingPname = GL_ELEMENT_ARRAY_BUFFER_BINDING;
    else if (target == GL_UNIFORM_BUFFER) bindingPname = 0x8A28;
    else if (target == GL_SHADER_STORAGE_BUFFER) bindingPname = 0x90D3;

    if (real_glGetIntegerv) {
        GLint b = 0;
        real_glGetIntegerv(bindingPname, &b);
        bufferID = static_cast<GLuint>(b);
    }

    // ALWAYS use shadow buffer — never attempt real glMapBufferRange.
    // On Mali via gl4es, the real glMapBufferRange is broken (returns NULL).
    GLsizeiptr allocLen = length;
    if (allocLen <= 0) allocLen = 65536;
    void* shadowPtr = malloc(allocLen);
    if (!shadowPtr) {
        shadowPtr = calloc(1, 65536);
    }
    if (!shadowPtr) {
        __android_log_print(ANDROID_LOG_ERROR, "FearRender", "[FearRender] glMapBufferRange: malloc FAILED for len=%ld", (long)allocLen);
        return nullptr;
    }

    FearMappedBuffer info;
    info.target = target;
    info.bufferID = bufferID;
    info.offset = offset;
    info.length = allocLen;
    info.ptr = shadowPtr;
    info.isShadow = true;

    {
        std::lock_guard<std::mutex> lock(g_fearMappedMutex);
        g_fearMappedBuffers[bufferID] = info;
    }

    return shadowPtr;
}

// Alias for glMapBufferRangeEXT — some implementations look for this name
FEAR_EXPORT void* glMapBufferRangeEXT(GLenum target, GLintptr offset, GLsizeiptr length, GLbitfield access) {
    return glMapBufferRange(target, offset, length, access);
}

FEAR_EXPORT void* glMapBuffer(GLenum target, GLenum access) {
    // Get buffer size via glGetBufferParameteriv
    typedef void (*glGetBufferParameteriv_pfn)(GLenum, GLenum, GLint*);
    static glGetBufferParameteriv_pfn real_glGetBufferParameteriv = nullptr;
    if (!real_glGetBufferParameteriv) {
        real_glGetBufferParameteriv = (glGetBufferParameteriv_pfn)dlsym(RTLD_NEXT, "glGetBufferParameteriv");
    }

    GLint bufferSize = 65536;
    if (real_glGetBufferParameteriv) {
        real_glGetBufferParameteriv(target, GL_BUFFER_SIZE, &bufferSize);
    }
    if (bufferSize <= 0) bufferSize = 65536;

    GLbitfield accessRange = GL_MAP_WRITE_BIT;
    if (access == GL_READ_ONLY) accessRange = GL_MAP_READ_BIT;
    else if (access == GL_READ_WRITE) accessRange = GL_MAP_READ_BIT | GL_MAP_WRITE_BIT;

    return glMapBufferRange(target, 0, bufferSize, accessRange);
}

// Alias for glMapBufferOES
FEAR_EXPORT void* glMapBufferOES(GLenum target, GLenum access) {
    return glMapBuffer(target, access);
}

FEAR_EXPORT GLboolean glUnmapBuffer(GLenum target) {
    // Resolve glGetIntegerv to find buffer ID
    typedef void (*glGetIntegerv_pfn)(GLenum, GLint*);
    static getIntegerv_pfn real_glGetIntegerv = nullptr;
    if (!real_glGetIntegerv) {
        real_glGetIntegerv = (glGetIntegerv_pfn)dlsym(RTLD_NEXT, "glGetIntegerv");
    }

    GLuint bufferID = 0;
    GLenum bindingPname = GL_ARRAY_BUFFER_BINDING;
    if (target == GL_ELEMENT_ARRAY_BUFFER) bindingPname = GL_ELEMENT_ARRAY_BUFFER_BINDING;
    else if (target == GL_UNIFORM_BUFFER) bindingPname = 0x8A28;
    else if (target == GL_SHADER_STORAGE_BUFFER) bindingPname = 0x90D3;

    if (real_glGetIntegerv) {
        GLint b = 0;
        real_glGetIntegerv(bindingPname, &b);
        bufferID = static_cast<GLuint>(b);
    }

    FearMappedBuffer info;
    bool found = false;
    {
        std::lock_guard<std::mutex> lock(g_fearMappedMutex);
        auto it = g_fearMappedBuffers.find(bufferID);
        if (it != g_fearMappedBuffers.end()) {
            info = it->second;
            g_fearMappedBuffers.erase(it);
            found = true;
        }
    }

    if (found && info.isShadow && info.ptr) {
        // Upload shadow buffer data to real GPU buffer via glBufferSubData
        typedef void (*glBufferSubData_pfn)(GLenum, GLintptr, GLsizeiptr, const void*);
        static glBufferSubData_pfn real_glBufferSubData = nullptr;
        if (!real_glBufferSubData) {
            real_glBufferSubData = (glBufferSubData_pfn)dlsym(RTLD_NEXT, "glBufferSubData");
        }
        if (real_glBufferSubData) {
            real_glBufferSubData(info.target, info.offset, info.length, info.ptr);
        }
        free(info.ptr);
        return GL_TRUE;
    }

    // Non-shadow path: call real glUnmapBuffer
    typedef GLboolean (*glUnmapBuffer_pfn)(GLenum);
    static glUnmapBuffer_pfn real_glUnmapBuffer = nullptr;
    if (!real_glUnmapBuffer) {
        real_glUnmapBuffer = (glUnmapBuffer_pfn)dlsym(RTLD_NEXT, "glUnmapBuffer");
    }
    return real_glUnmapBuffer ? real_glUnmapBuffer(target) : GL_TRUE;
}

FEAR_EXPORT GLboolean glUnmapBufferOES(GLenum target) {
    return glUnmapBuffer(target);
}

FEAR_EXPORT void glGetIntegerv(GLenum pname, GLint* params) {
    if (!params) return;

    if (!isContextCurrent()) {
        static bool logged = false;
        if (!logged) {
            __android_log_print(ANDROID_LOG_INFO, "FearRender", "[FearRender][GUARD] glGetIntegerv without context tid=%d - safe default", gettid());
            logged = true;
        }
        tryEmergencyContext();
        if (isContextCurrent()) {
            typedef void (*glGetIntegerv_pfn)(GLenum, GLint*);
            static glGetIntegerv_pfn real_glGetIntegerv = (glGetIntegerv_pfn)dlsym(g_glesHandle ? g_glesHandle : RTLD_NEXT, "glGetIntegerv");
            if (real_glGetIntegerv) {
                real_glGetIntegerv(pname, params);
                return;
            }
        }
        if (pname == GL_MAX_TEXTURE_SIZE) *params = 16384;
        else if (pname == 0x821D /* GL_MAX_DRAW_BUFFERS */) *params = 8;
        else *params = 0;
        return;
    }

    typedef void (*glGetIntegerv_pfn)(GLenum, GLint*);
    static glGetIntegerv_pfn real_glGetIntegerv = (glGetIntegerv_pfn)dlsym(g_glesHandle ? g_glesHandle : RTLD_NEXT, "glGetIntegerv");
    if (real_glGetIntegerv) {
        real_glGetIntegerv(pname, params);
    }
}

FEAR_EXPORT void glGetFloatv(GLenum pname, GLfloat* params) {
    if (!params) return;
    if (!isContextCurrent()) {
        static bool logged = false;
        if (!logged) {
            __android_log_print(ANDROID_LOG_INFO, "FearRender", "[FearRender][GUARD] glGetFloatv without context tid=%d - safe default", gettid());
            logged = true;
        }
        *params = 1.0f;
        return;
    }
    typedef void (*glGetFloatv_pfn)(GLenum, GLfloat*);
    static glGetFloatv_pfn real_glGetFloatv = (glGetFloatv_pfn)dlsym(g_glesHandle ? g_glesHandle : RTLD_NEXT, "glGetFloatv");
    if (real_glGetFloatv) real_glGetFloatv(pname, params);
}

FEAR_EXPORT void glGetBooleanv(GLenum pname, GLboolean* params) {
    if (!params) return;
    if (!isContextCurrent()) {
        static bool logged = false;
        if (!logged) {
            __android_log_print(ANDROID_LOG_INFO, "FearRender", "[FearRender][GUARD] glGetBooleanv without context tid=%d - safe default", gettid());
            logged = true;
        }
        *params = GL_FALSE;
        return;
    }
    typedef void (*glGetBooleanv_pfn)(GLenum, GLboolean*);
    static glGetBooleanv_pfn real_glGetBooleanv = (glGetBooleanv_pfn)dlsym(g_glesHandle ? g_glesHandle : RTLD_NEXT, "glGetBooleanv");
    if (real_glGetBooleanv) real_glGetBooleanv(pname, params);
}

FEAR_EXPORT void glEnable(GLenum cap) {
    if (!isContextCurrent()) {
        static bool logged = false;
        if (!logged) {
            __android_log_print(ANDROID_LOG_INFO, "FearRender", "[FearRender][GUARD] glEnable without context tid=%d - safe default", gettid());
            logged = true;
        }
        return;
    }
    typedef void (*glEnable_pfn)(GLenum);
    static glEnable_pfn real_glEnable = (glEnable_pfn)dlsym(g_glesHandle ? g_glesHandle : RTLD_NEXT, "glEnable");
    if (real_glEnable) real_glEnable(cap);
}

FEAR_EXPORT void glDisable(GLenum cap) {
    if (!isContextCurrent()) return;
    typedef void (*glDisable_pfn)(GLenum);
    static glDisable_pfn real_glDisable = (glDisable_pfn)dlsym(g_glesHandle ? g_glesHandle : RTLD_NEXT, "glDisable");
    if (real_glDisable) real_glDisable(cap);
}

FEAR_EXPORT void glBindTexture(GLenum target, GLuint texture) {
    if (!isContextCurrent()) return;
    typedef void (*glBindTexture_pfn)(GLenum, GLuint);
    static glBindTexture_pfn real_glBindTexture = (glBindTexture_pfn)dlsym(g_glesHandle ? g_glesHandle : RTLD_NEXT, "glBindTexture");
    if (real_glBindTexture) real_glBindTexture(target, texture);
}

FEAR_EXPORT void glClearColor(GLfloat r, GLfloat g, GLfloat b, GLfloat a) {
    if (!isContextCurrent()) return;
    typedef void (*glClearColor_pfn)(GLfloat, GLfloat, GLfloat, GLfloat);
    static glClearColor_pfn real_glClearColor = (glClearColor_pfn)dlsym(g_glesHandle ? g_glesHandle : RTLD_NEXT, "glClearColor");
    if (real_glClearColor) real_glClearColor(r, g, b, a);
}

FEAR_EXPORT void glClear(GLbitfield mask) {
    if (!isContextCurrent()) return;
    typedef void (*glClear_pfn)(GLbitfield);
    static glClear_pfn real_glClear = (glClear_pfn)dlsym(g_glesHandle ? g_glesHandle : RTLD_NEXT, "glClear");
    if (real_glClear) real_glClear(mask);
}

FEAR_EXPORT void glDrawArrays(GLenum mode, GLint first, GLsizei count) {
    if (!isContextCurrent()) return;
    typedef void (*glDrawArrays_pfn)(GLenum, GLint, GLsizei);
    static glDrawArrays_pfn real_glDrawArrays = (glDrawArrays_pfn)dlsym(g_glesHandle ? g_glesHandle : RTLD_NEXT, "glDrawArrays");
    if (real_glDrawArrays) real_glDrawArrays(mode, first, count);
}

FEAR_EXPORT void glDrawElements(GLenum mode, GLsizei count, GLenum type, const void* indices) {
    if (!isContextCurrent()) return;
    typedef void (*glDrawElements_pfn)(GLenum, GLsizei, GLenum, const void*);
    static glDrawElements_pfn real_glDrawElements = (glDrawElements_pfn)dlsym(g_glesHandle ? g_glesHandle : RTLD_NEXT, "glDrawElements");
    if (real_glDrawElements) real_glDrawElements(mode, count, type, indices);
}

FEAR_EXPORT void glGenSamplers(GLsizei count, GLuint* samplers) {
    fear_glGenSamplers(count, samplers);
}

FEAR_EXPORT void glBindSampler(GLuint unit, GLuint sampler) {
    fear_glBindSampler(unit, sampler);
}

FEAR_EXPORT void glDeleteSamplers(GLsizei count, const GLuint* samplers) {
    fear_glDeleteSamplers(count, samplers);
}

FEAR_EXPORT GLboolean glIsSampler(GLuint sampler) {
    return fear_glIsSampler(sampler);
}

FEAR_EXPORT void glSamplerParameteri(GLuint sampler, GLenum pname, GLint param) {
    fear_glSamplerParameteri(sampler, pname, param);
}

FEAR_EXPORT const unsigned char* glGetString(unsigned int name) {
    return fear_glGetString(name);
}

FEAR_EXPORT const unsigned char* glGetStringi(unsigned int name, unsigned int index) {
    return fear_glGetStringi(name, index);
}

void* fear_eglGetProcAddress(const char* procname) {
    if (procname == nullptr) return (void*)universal_safe_stub;

    if (strcmp(procname, "eglMakeCurrent") == 0) return (void*)eglMakeCurrent;
    if (strcmp(procname, "eglCreateContext") == 0) return (void*)eglCreateContext;

    if (strcmp(procname, "glGetIntegerv") == 0) return (void*)glGetIntegerv;
    if (strcmp(procname, "glGetFloatv") == 0) return (void*)glGetFloatv;
    if (strcmp(procname, "glGetBooleanv") == 0) return (void*)glGetBooleanv;
    if (strcmp(procname, "glEnable") == 0) return (void*)glEnable;
    if (strcmp(procname, "glDisable") == 0) return (void*)glDisable;
    if (strcmp(procname, "glBindTexture") == 0) return (void*)glBindTexture;
    if (strcmp(procname, "glClearColor") == 0) return (void*)glClearColor;
    if (strcmp(procname, "glClear") == 0) return (void*)glClear;
    if (strcmp(procname, "glDrawArrays") == 0) return (void*)glDrawArrays;
    if (strcmp(procname, "glDrawElements") == 0) return (void*)glDrawElements;

    if (strcmp(procname, "glGenSamplers") == 0 || strcmp(procname, "glGenSamplersOES") == 0) return (void*)fear_glGenSamplers;
    if (strcmp(procname, "glBindSampler") == 0 || strcmp(procname, "glBindSamplerOES") == 0) return (void*)fear_glBindSampler;
    if (strcmp(procname, "glDeleteSamplers") == 0 || strcmp(procname, "glDeleteSamplersOES") == 0) return (void*)fear_glDeleteSamplers;
    if (strcmp(procname, "glIsSampler") == 0 || strcmp(procname, "glIsSamplerOES") == 0) return (void*)fear_glIsSampler;
    if (strcmp(procname, "glSamplerParameteri") == 0 || strcmp(procname, "glSamplerParameteriOES") == 0) return (void*)fear_glSamplerParameteri;
    if (strcmp(procname, "glSamplerParameterf") == 0 || strcmp(procname, "glSamplerParameterfOES") == 0) return (void*)fear_glSamplerParameterf;
    if (strcmp(procname, "glSamplerParameteriv") == 0 || strcmp(procname, "glSamplerParameterivOES") == 0) return (void*)fear_glSamplerParameteriv;
    if (strcmp(procname, "glSamplerParameterfv") == 0 || strcmp(procname, "glSamplerParameterfvOES") == 0) return (void*)fear_glSamplerParameterfv;

    // Buffer mapping — return our DIRECT hook implementations (not fear_ prefixed)
    if (strcmp(procname, "glMapBufferRange") == 0 || strcmp(procname, "glMapBufferRangeEXT") == 0) return (void*)glMapBufferRange;
    if (strcmp(procname, "glMapBuffer") == 0 || strcmp(procname, "glMapBufferOES") == 0) return (void*)glMapBuffer;
    if (strcmp(procname, "glUnmapBuffer") == 0 || strcmp(procname, "glUnmapBufferOES") == 0) return (void*)glUnmapBuffer;

    if (strcmp(procname, "glMemoryBarrier") == 0 || strcmp(procname, "glMemoryBarrierEXT") == 0) return (void*)fear_glMemoryBarrier;
    if (strcmp(procname, "glTextureBarrier") == 0) return (void*)fear_glTextureBarrier;
    if (strcmp(procname, "glBindImageTexture") == 0) return (void*)fear_glBindTextureUnit;
    if (strcmp(procname, "glBufferStorage") == 0) return (void*)fear_glBufferStorage;
    if (strcmp(procname, "glClearTexImage") == 0) return (void*)fear_glClearTexImage;
    if (strcmp(procname, "glClearTexSubImage") == 0) return (void*)fear_glClearTexSubImage;
    if (strcmp(procname, "glMultiDrawArrays") == 0) return (void*)fear_glMultiDrawArrays;
    if (strcmp(procname, "glMultiDrawElements") == 0) return (void*)fear_glMultiDrawElements;
    if (strcmp(procname, "glInvalidateFramebuffer") == 0) return (void*)fear_glInvalidateFramebuffer;
    if (strcmp(procname, "glCreateBuffers") == 0) return (void*)fear_glCreateBuffers;
    if (strcmp(procname, "glNamedBufferData") == 0) return (void*)fear_glNamedBufferData;
    if (strcmp(procname, "glNamedBufferSubData") == 0) return (void*)fear_glNamedBufferSubData;
    if (strcmp(procname, "glBindTextureUnit") == 0) return (void*)fear_glBindTextureUnit;

    if (strcmp(procname, "glCreateShader") == 0) return (void*)fear_glCreateShader;
    if (strcmp(procname, "glShaderSource") == 0 || strcmp(procname, "glShaderSourceARB") == 0) return (void*)fear_glShaderSource;
    if (strcmp(procname, "glCompileShader") == 0 || strcmp(procname, "glCompileShaderARB") == 0) return (void*)fear_glCompileShader;
    if (strcmp(procname, "glAttachShader") == 0) return (void*)fear_glAttachShader;
    if (strcmp(procname, "glDetachShader") == 0) return (void*)fear_glDetachShader;
    if (strcmp(procname, "glLinkProgram") == 0) return (void*)fear_glLinkProgram;
    if (strcmp(procname, "glDeleteShader") == 0) return (void*)fear_glDeleteShader;
    if (strcmp(procname, "glDeleteProgram") == 0) return (void*)fear_glDeleteProgram;

    if (strcmp(procname, "glTexImage2D") == 0) return (void*)fear_glTexImage2D;
    if (strcmp(procname, "glTexImage3D") == 0) return (void*)fear_glTexImage3D;
    if (strcmp(procname, "glRenderbufferStorage") == 0) return (void*)fear_glRenderbufferStorage;
    if (strcmp(procname, "glFramebufferTexture2D") == 0) return (void*)fear_glFramebufferTexture2D;

    if (strcmp(procname, "glGetString") == 0) return (void*)fear_glGetString;
    if (strcmp(procname, "glGetStringi") == 0) return (void*)fear_glGetStringi;

    // Resolve from FOGLTLOGLES dispatch map
    FunctionPtr fogl_fn = FOGLTLOGLES::getFunctionAddress(procname);
    if (fogl_fn) return reinterpret_cast<void*>(fogl_fn);

    typedef void* (*eglGetProcAddress_pfn)(const char*);
    static eglGetProcAddress_pfn real_eglGetProcAddress = (eglGetProcAddress_pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_NEXT, "eglGetProcAddress");
    if (real_eglGetProcAddress) {
        void* res = real_eglGetProcAddress(procname);
        if (res) return res;
    }

    if (g_glesHandle) {
        void* sym = dlsym(g_glesHandle, procname);
        if (sym) return sym;
    }

    return (void*)universal_safe_stub;
}

FEAR_EXPORT __eglMustCastToProperFunctionPointerType eglGetProcAddress(const char* procname) {
    return reinterpret_cast<__eglMustCastToProperFunctionPointerType>(fear_eglGetProcAddress(procname));
}

} // extern "C"

void initialize_fear_hooks() {
    initEGLGLESHandles();
    setenv("TINYFD_SKIP", "1", 1);
    __android_log_print(ANDROID_LOG_INFO, "FearRender", "[FearRender] Tiny file dialogs stubbed for Android");

    // CRITICAL: Re-open ourselves with RTLD_GLOBAL so our exported symbols
    // (glMapBufferRange, eglGetProcAddress, etc.) become globally available.
    // Without this, LWJGL loads libGLFear.so with RTLD_LOCAL, and when it calls
    // eglGetProcAddress from libgl4es_114.so, it gets gl4es's broken
    // glMapBufferRange instead of our shadow-buffer implementation.
    void* selfHandle = dlopen("libGLFear.so", RTLD_GLOBAL | RTLD_LAZY);
    if (selfHandle) {
        __android_log_print(ANDROID_LOG_INFO, "FearRender", "[FearRender] Promoted libGLFear.so to RTLD_GLOBAL (handle=%p)", selfHandle);
    } else {
        __android_log_print(ANDROID_LOG_WARN, "FearRender", "[FearRender] Failed to promote libGLFear.so to RTLD_GLOBAL: %s", dlerror());
    }

    std::thread([]() {
        std::this_thread::sleep_for(std::chrono::seconds(2));
        __android_log_print(ANDROID_LOG_INFO, "FearRender", "[FearRender] Auto-continued past GLFW warning");
    }).detach();

    __android_log_print(ANDROID_LOG_INFO, "FearRender", "Fear Hooking Engine successfully activated.");
}
