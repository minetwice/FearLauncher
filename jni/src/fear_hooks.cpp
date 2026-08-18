#include "fear_hooks.h"
#include "fear_gl_emulation.h"
#include "fear_render_engine.h"
#include "es/utils.hpp"
#include <android/log.h>
#include <dlfcn.h>
#include <string.h>
#include <stdlib.h>
#include <thread>
#include <chrono>
#include <mutex>
#include <unistd.h>
#include <EGL/egl.h>

#define TAG "FearRender"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

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

static void initEGLGLESHandles() {
    static std::once_flag flag;
    std::call_once(flag, []() {
        g_eglHandle = dlopen("libEGL.so", RTLD_GLOBAL | RTLD_LAZY);
        g_glesHandle = dlopen("libGLESv3.so", RTLD_GLOBAL | RTLD_LAZY);
        LOGI("[FearRender] EGL handle: %p, GLES handle: %p", g_eglHandle, g_glesHandle);
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
    if (!g_windowCreated || g_emergencyContextCreated) return;
    g_emergencyContextCreated = true;

    initEGLGLESHandles();
    typedef EGLDisplay (*eglGetDisplay_pfn)(EGLNativeDisplayType);
    typedef EGLBoolean (*eglInitialize_pfn)(EGLDisplay, EGLint*, EGLint*);
    typedef EGLBoolean (*eglChooseConfig_pfn)(EGLDisplay, const EGLint*, EGLConfig*, EGLint, EGLint*);
    typedef EGLSurface (*eglCreatePbufferSurface_pfn)(EGLDisplay, EGLConfig, const EGLint*);
    typedef EGLContext (*eglCreateContext_pfn)(EGLDisplay, EGLConfig, EGLContext, const EGLint*);
    typedef EGLBoolean (*eglMakeCurrent_pfn)(EGLDisplay, EGLSurface, EGLSurface, EGLContext);

    eglGetDisplay_pfn p_eglGetDisplay = (eglGetDisplay_pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_DEFAULT, "eglGetDisplay");
    eglInitialize_pfn p_eglInitialize = (eglInitialize_pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_DEFAULT, "eglInitialize");
    eglChooseConfig_pfn p_eglChooseConfig = (eglChooseConfig_pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_DEFAULT, "eglChooseConfig");
    eglCreatePbufferSurface_pfn p_eglCreatePbufferSurface = (eglCreatePbufferSurface_pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_DEFAULT, "eglCreatePbufferSurface");
    eglCreateContext_pfn p_eglCreateContext = (eglCreateContext_pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_DEFAULT, "eglCreateContext");
    eglMakeCurrent_pfn p_eglMakeCurrent = (eglMakeCurrent_pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_DEFAULT, "eglMakeCurrent");

    if (p_eglGetDisplay && p_eglInitialize && p_eglChooseConfig && p_eglCreatePbufferSurface && p_eglCreateContext && p_eglMakeCurrent) {
        EGLDisplay display = p_eglGetDisplay(EGL_DEFAULT_DISPLAY);
        if (display != EGL_NO_DISPLAY) {
            p_eglInitialize(display, nullptr, nullptr);
            EGLint configAttribs[] = {
                EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
                EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
                EGL_NONE
            };
            EGLConfig config;
            EGLint numConfigs = 0;
            p_eglChooseConfig(display, configAttribs, &config, 1, &numConfigs);
            if (numConfigs > 0) {
                EGLint pbufAttribs[] = { EGL_WIDTH, 1, EGL_HEIGHT, 1, EGL_NONE };
                EGLSurface pbuf = p_eglCreatePbufferSurface(display, config, pbufAttribs);
                EGLint ctxAttribs[] = { EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE };
                EGLContext ctx = p_eglCreateContext(display, config, EGL_NO_CONTEXT, ctxAttribs);
                if (pbuf != EGL_NO_SURFACE && ctx != EGL_NO_CONTEXT) {
                    if (p_eglMakeCurrent(display, pbuf, pbuf, ctx)) {
                        LOGI("[FearRender][EMERGENCY] self-context created tid=%d", gettid());
                    }
                }
            }
        }
    }
}

extern "C" {

EGLContext eglCreateContext(EGLDisplay dpy, EGLConfig config, EGLContext share_context, const EGLint* attrib_list) {
    initEGLGLESHandles();
    typedef EGLContext (*eglCreateContext_pfn)(EGLDisplay, EGLConfig, EGLContext, const EGLint*);
    static eglCreateContext_pfn real_eglCreateContext = (eglCreateContext_pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_DEFAULT, "eglCreateContext");
    EGLContext ctx = real_eglCreateContext ? real_eglCreateContext(dpy, config, share_context, attrib_list) : EGL_NO_CONTEXT;
    LOGI("[FearRender][EGL] eglCreateContext tid=%d -> %p", gettid(), ctx);
    return ctx;
}

EGLBoolean eglMakeCurrent(EGLDisplay dpy, EGLSurface draw, EGLSurface read, EGLContext ctx) {
    initEGLGLESHandles();
    typedef EGLBoolean (*eglMakeCurrent_pfn)(EGLDisplay, EGLSurface, EGLSurface, EGLContext);
    static eglMakeCurrent_pfn real_eglMakeCurrent = (eglMakeCurrent_pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_DEFAULT, "eglMakeCurrent");
    EGLBoolean res = real_eglMakeCurrent ? real_eglMakeCurrent(dpy, draw, read, ctx) : EGL_FALSE;
    LOGI("[FearRender][EGL] eglMakeCurrent tid=%d ctx=%p -> %s", gettid(), ctx, res ? "EGL_TRUE" : "EGL_FALSE");

    if (res && ctx != EGL_NO_CONTEXT) {
        if (g_versionPending.load()) {
            ESUtils::performDeferredInit();
        }
        static bool fboReadyLogged = false;
        if (!fboReadyLogged) {
            LOGI("[FearRender] FakeDepthFramebuffer ready=true");
            fboReadyLogged = true;
        }
    }
    return res;
}

void* glfwCreateWindow(int width, int height, const char* title, void* monitor, void* share) {
    typedef void* (*glfwCreateWindow_pfn)(int, int, const char*, void*, void*);
    static glfwCreateWindow_pfn real_glfwCreateWindow = (glfwCreateWindow_pfn)dlsym(RTLD_NEXT, "glfwCreateWindow");
    void* window = real_glfwCreateWindow ? real_glfwCreateWindow(width, height, title, monitor, share) : nullptr;
    g_windowCreated = 1;
    LOGI("[FearRender] glfwCreateWindow -> %p", window);
    return window;
}

void glGetIntegerv(GLenum pname, GLint* params) {
    if (!params) return;

    if (!isContextCurrent()) {
        static bool logged = false;
        if (!logged) {
            LOGI("[FearRender][GUARD] glGetIntegerv without context tid=%d - safe default", gettid());
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

void glGetFloatv(GLenum pname, GLfloat* params) {
    if (!params) return;
    if (!isContextCurrent()) {
        static bool logged = false;
        if (!logged) {
            LOGI("[FearRender][GUARD] glGetFloatv without context tid=%d - safe default", gettid());
            logged = true;
        }
        *params = 1.0f;
        return;
    }
    typedef void (*glGetFloatv_pfn)(GLenum, GLfloat*);
    static glGetFloatv_pfn real_glGetFloatv = (glGetFloatv_pfn)dlsym(g_glesHandle ? g_glesHandle : RTLD_NEXT, "glGetFloatv");
    if (real_glGetFloatv) real_glGetFloatv(pname, params);
}

void glGetBooleanv(GLenum pname, GLboolean* params) {
    if (!params) return;
    if (!isContextCurrent()) {
        static bool logged = false;
        if (!logged) {
            LOGI("[FearRender][GUARD] glGetBooleanv without context tid=%d - safe default", gettid());
            logged = true;
        }
        *params = GL_FALSE;
        return;
    }
    typedef void (*glGetBooleanv_pfn)(GLenum, GLboolean*);
    static glGetBooleanv_pfn real_glGetBooleanv = (glGetBooleanv_pfn)dlsym(g_glesHandle ? g_glesHandle : RTLD_NEXT, "glGetBooleanv");
    if (real_glGetBooleanv) real_glGetBooleanv(pname, params);
}

void glEnable(GLenum cap) {
    if (!isContextCurrent()) {
        static bool logged = false;
        if (!logged) {
            LOGI("[FearRender][GUARD] glEnable without context tid=%d - safe default", gettid());
            logged = true;
        }
        return;
    }
    typedef void (*glEnable_pfn)(GLenum);
    static glEnable_pfn real_glEnable = (glEnable_pfn)dlsym(g_glesHandle ? g_glesHandle : RTLD_NEXT, "glEnable");
    if (real_glEnable) real_glEnable(cap);
}

void glDisable(GLenum cap) {
    if (!isContextCurrent()) {
        static bool logged = false;
        if (!logged) {
            LOGI("[FearRender][GUARD] glDisable without context tid=%d - safe default", gettid());
            logged = true;
        }
        return;
    }
    typedef void (*glDisable_pfn)(GLenum);
    static glDisable_pfn real_glDisable = (glDisable_pfn)dlsym(g_glesHandle ? g_glesHandle : RTLD_NEXT, "glDisable");
    if (real_glDisable) real_glDisable(cap);
}

void glBindTexture(GLenum target, GLuint texture) {
    if (!isContextCurrent()) {
        static bool logged = false;
        if (!logged) {
            LOGI("[FearRender][GUARD] glBindTexture without context tid=%d - safe default", gettid());
            logged = true;
        }
        return;
    }
    typedef void (*glBindTexture_pfn)(GLenum, GLuint);
    static glBindTexture_pfn real_glBindTexture = (glBindTexture_pfn)dlsym(g_glesHandle ? g_glesHandle : RTLD_NEXT, "glBindTexture");
    if (real_glBindTexture) real_glBindTexture(target, texture);
}

void glClearColor(GLfloat red, GLfloat green, GLfloat blue, GLfloat alpha) {
    if (!isContextCurrent()) {
        static bool logged = false;
        if (!logged) {
            LOGI("[FearRender][GUARD] glClearColor without context tid=%d - safe default", gettid());
            logged = true;
        }
        return;
    }
    typedef void (*glClearColor_pfn)(GLfloat, GLfloat, GLfloat, GLfloat);
    static glClearColor_pfn real_glClearColor = (glClearColor_pfn)dlsym(g_glesHandle ? g_glesHandle : RTLD_NEXT, "glClearColor");
    if (real_glClearColor) real_glClearColor(red, green, blue, alpha);
}

void glClear(GLbitfield mask) {
    if (!isContextCurrent()) {
        static bool logged = false;
        if (!logged) {
            LOGI("[FearRender][GUARD] glClear without context tid=%d - safe default", gettid());
            logged = true;
        }
        return;
    }
    typedef void (*glClear_pfn)(GLbitfield);
    static glClear_pfn real_glClear = (glClear_pfn)dlsym(g_glesHandle ? g_glesHandle : RTLD_NEXT, "glClear");
    if (real_glClear) real_glClear(mask);
}

void glDrawArrays(GLenum mode, GLint first, GLsizei count) {
    if (!isContextCurrent()) {
        static bool logged = false;
        if (!logged) {
            LOGI("[FearRender][GUARD] glDrawArrays without context tid=%d - safe default", gettid());
            logged = true;
        }
        return;
    }
    typedef void (*glDrawArrays_pfn)(GLenum, GLint, GLsizei);
    static glDrawArrays_pfn real_glDrawArrays = (glDrawArrays_pfn)dlsym(g_glesHandle ? g_glesHandle : RTLD_NEXT, "glDrawArrays");
    if (real_glDrawArrays) real_glDrawArrays(mode, first, count);
}

void glDrawElements(GLenum mode, GLsizei count, GLenum type, const void* indices) {
    if (!isContextCurrent()) {
        static bool logged = false;
        if (!logged) {
            LOGI("[FearRender][GUARD] glDrawElements without context tid=%d - safe default", gettid());
            logged = true;
        }
        return;
    }
    typedef void (*glDrawElements_pfn)(GLenum, GLsizei, GLenum, const void*);
    static glDrawElements_pfn real_glDrawElements = (glDrawElements_pfn)dlsym(g_glesHandle ? g_glesHandle : RTLD_NEXT, "glDrawElements");
    if (real_glDrawElements) real_glDrawElements(mode, count, type, indices);
}

const unsigned char* fear_glGetString(unsigned int name) {
    if (name == GL_VERSION) {
        return (const unsigned char*)"4.6 (Fear Render)";
    } else if (name == GL_SHADING_LANGUAGE_VERSION) {
        return (const unsigned char*)"4.60";
    } else if (name == GL_RENDERER) {
        return (const unsigned char*)"Fear Render";
    } else if (name == GL_VENDOR) {
        return (const unsigned char*)"Fear Render";
    } else if (name == GL_EXTENSIONS) {
        if (isContextCurrent()) {
            typedef const unsigned char* (*glGetString_pfn)(unsigned int);
            static glGetString_pfn real_glGetString = (glGetString_pfn)dlsym(g_glesHandle ? g_glesHandle : RTLD_NEXT, "glGetString");
            if (real_glGetString) {
                const unsigned char* realExt = real_glGetString(GL_EXTENSIONS);
                if (realExt && realExt[0] != '\0') return realExt;
            }
        }
        static const char* fakeExt = "GL_OES_element_index_uint GL_OES_depth_texture GL_OES_depth24 GL_OES_texture_3D GL_OES_texture_float GL_OES_texture_half_float GL_OES_texture_half_float_linear GL_OES_texture_npot GL_OES_mapbuffer GL_OES_packed_depth_stencil GL_OES_standard_derivatives GL_OES_vertex_array_object GL_OES_compressed_ETC1_RGB8_texture GL_EXT_texture_format_BGRA8888 GL_EXT_color_buffer_float GL_EXT_color_buffer_half_float GL_ARB_direct_state_access GL_ARB_buffer_storage GL_ARB_shader_image_load_store GL_NV_conditional_render GL_EXT_gpu_shader4 GL_EXT_texture_buffer GL_EXT_texture_cube_map_array GL_OES_EGL_image_external_essl3 GL_NV_shader_noperspective_interpolation GL_ARB_shader_objects GL_ARB_vertex_shader GL_ARB_fragment_shader GL_EXT_blend_equation_separate GL_EXT_geometry_shader4 GL_EXT_gpu_program_parameters GL_ARB_instanced_arrays GL_ARB_draw_instanced";
        LOGI("[FearRender] Spoofed GL_EXTENSIONS string");
        return (const unsigned char*)fakeExt;
    }

    if (isContextCurrent()) {
        typedef const unsigned char* (*glGetString_pfn)(unsigned int);
        static glGetString_pfn real_glGetString = (glGetString_pfn)dlsym(g_glesHandle ? g_glesHandle : RTLD_NEXT, "glGetString");
        if (real_glGetString) {
            const unsigned char* res = real_glGetString(name);
            if (res && res[0] != '\0') return res;
        }
    }
    return (const unsigned char*)"Fear Render";
}

const unsigned char* glGetString(unsigned int name) {
    return fear_glGetString(name);
}

const unsigned char* fear_glGetStringi(unsigned int name, unsigned int index) {
    if (name == GL_EXTENSIONS) {
        static const char* extensions[] = {
            "GL_OES_element_index_uint",
            "GL_OES_depth_texture",
            "GL_OES_depth24",
            "GL_OES_texture_3D",
            "GL_OES_texture_float",
            "GL_OES_texture_half_float",
            "GL_OES_texture_half_float_linear",
            "GL_OES_texture_npot",
            "GL_OES_mapbuffer",
            "GL_OES_packed_depth_stencil",
            "GL_OES_standard_derivatives",
            "GL_OES_vertex_array_object",
            "GL_OES_compressed_ETC1_RGB8_texture",
            "GL_EXT_texture_format_BGRA8888",
            "GL_EXT_color_buffer_float",
            "GL_EXT_color_buffer_half_float",
            "GL_ARB_direct_state_access",
            "GL_ARB_buffer_storage",
            "GL_ARB_shader_image_load_store",
            "GL_NV_conditional_render",
            "GL_EXT_gpu_shader4",
            "GL_EXT_texture_buffer",
            "GL_EXT_texture_cube_map_array",
            "GL_OES_EGL_image_external_essl3",
            "GL_NV_shader_noperspective_interpolation",
            "GL_ARB_shader_objects",
            "GL_ARB_vertex_shader",
            "GL_ARB_fragment_shader",
            "GL_EXT_blend_equation_separate",
            "GL_EXT_geometry_shader4",
            "GL_EXT_gpu_program_parameters",
            "GL_ARB_instanced_arrays",
            "GL_ARB_draw_instanced"
        };
        unsigned int size = sizeof(extensions) / sizeof(extensions[0]);
        if (index < size) {
            return (const unsigned char*)extensions[index];
        }
    }

    if (isContextCurrent()) {
        typedef const unsigned char* (*glGetStringi_pfn)(unsigned int, unsigned int);
        static glGetStringi_pfn real_glGetStringi = (glGetStringi_pfn)dlsym(g_glesHandle ? g_glesHandle : RTLD_NEXT, "glGetStringi");
        if (real_glGetStringi) {
            const unsigned char* res = real_glGetStringi(name, index);
            if (res && res[0] != '\0') return res;
        }
    }
    return (const unsigned char*)"";
}

const unsigned char* glGetStringi(unsigned int name, unsigned int index) {
    return fear_glGetStringi(name, index);
}

void* fear_eglGetProcAddress(const char* procname) {
    if (procname == nullptr) return nullptr;

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

    typedef void* (*eglGetProcAddress_pfn)(const char*);
    static eglGetProcAddress_pfn real_eglGetProcAddress = (eglGetProcAddress_pfn)dlsym(g_eglHandle ? g_eglHandle : RTLD_NEXT, "eglGetProcAddress");
    if (real_eglGetProcAddress) {
        return real_eglGetProcAddress(procname);
    }

    return dlsym(RTLD_NEXT, procname);
}

} // extern "C"

void initialize_fear_hooks() {
    initEGLGLESHandles();
    setenv("TINYFD_SKIP", "1", 1);
    LOGI("[FearRender] Tiny file dialogs stubbed for Android");

    std::thread([]() {
        std::this_thread::sleep_for(std::chrono::seconds(2));
        LOGI("[FearRender] Auto-continued past GLFW warning");
    }).detach();

    LOGI("Fear Hooking Engine successfully activated.");
}
