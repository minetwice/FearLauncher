#include "fear_hooks.h"
#include <dlfcn.h>
#include <string.h>
#include <android/log.h>
#include <vector>
#include <string>
#include <jni.h>

#define LOG_TAG "FEAR_RENDERER"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static void* s_fear_core_handle = nullptr;

typedef const GLubyte* (*PFN_glGetString)(GLenum name);
typedef const GLubyte* (*PFN_glGetStringi)(GLenum name, GLuint index);

static PFN_glGetString s_fc_glGetString = nullptr;
static PFN_glGetStringi s_fc_glGetStringi = nullptr;

static std::vector<std::string> s_simulated_extensions;
static std::vector<const GLubyte*> s_extension_pointers;

void init_fear_hooks() {
    LOGI("Initializing Fear Renderer Hook Engine wrapping libFearCore.so...");

    const char* native_dir = getenv("POJAV_NATIVEDIR");
    std::string fc_path = "libFearCore.so";
    if (native_dir) {
        fc_path = std::string(native_dir) + "/libFearCore.so";
    }

    s_fear_core_handle = dlopen(fc_path.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (!s_fear_core_handle) {
        s_fear_core_handle = dlopen("libFearCore.so", RTLD_NOW | RTLD_GLOBAL);
    }

    if (!s_fear_core_handle) {
        LOGE("Failed to load underlying libFearCore.so library!");
        return;
    }

    s_fc_glGetString = (PFN_glGetString)dlsym(s_fear_core_handle, "glGetString");
    s_fc_glGetStringi = (PFN_glGetStringi)dlsym(s_fear_core_handle, "glGetStringi");

    // Populate simulated desktop extensions
    s_simulated_extensions = {
        "GL_ARB_direct_state_access",
        "GL_ARB_buffer_storage",
        "GL_ARB_shader_image_load_store",
        "GL_NV_conditional_render",
        "GL_ARB_vertex_attrib_binding",
        "GL_ARB_multi_draw_indirect",
        "GL_ARB_texture_storage",
        "GL_ARB_instanced_arrays",
        "GL_ARB_draw_instanced",
        "GL_ARB_draw_buffers",
        "GL_EXT_texture_filter_anisotropic",
        "GL_ARB_compute_shader"
    };

    // Grab actual system/FearCore extensions and append them
    if (s_fc_glGetString) {
        const char* fc_exts = (const char*)s_fc_glGetString(GL_EXTENSIONS);
        if (fc_exts) {
            std::string exts(fc_exts);
            size_t pos = 0;
            while ((pos = exts.find(' ')) != std::string::npos) {
                std::string ext = exts.substr(0, pos);
                if (!ext.empty()) {
                    s_simulated_extensions.push_back(ext);
                }
                exts.erase(0, pos + 1);
            }
            if (!exts.empty()) {
                s_simulated_extensions.push_back(exts);
            }
        }
    }

    for (const auto& ext : s_simulated_extensions) {
        s_extension_pointers.push_back((const GLubyte*)ext.c_str());
    }

    LOGI("Fear Renderer successfully loaded simulated extensions count: %zu", s_simulated_extensions.size());
}

const GLubyte* fear_glGetString(GLenum name) {
    switch (name) {
        case GL_VERSION:
            return (const GLubyte*)"4.6.0 NVIDIA 535.113.01";
        case GL_RENDERER:
            return (const GLubyte*)"NVIDIA GeForce RTX 4090/PCIe/SSE2 (Fear Renderer Virtual Engine)";
        case GL_VENDOR:
            return (const GLubyte*)"NVIDIA Corporation / AMD Ryzen 9 5900X";
        case GL_SHADING_LANGUAGE_VERSION:
            return (const GLubyte*)"4.60 NVIDIA";
        case GL_EXTENSIONS: {
            static std::string s_flattened_exts;
            if (s_flattened_exts.empty()) {
                for (size_t i = 0; i < s_simulated_extensions.size(); ++i) {
                    s_flattened_exts += s_simulated_extensions[i];
                    if (i != s_simulated_extensions.size() - 1) {
                        s_flattened_exts += " ";
                    }
                }
            }
            return (const GLubyte*)s_flattened_exts.c_str();
        }
        default:
            if (s_fc_glGetString) {
                return s_fc_glGetString(name);
            }
            return (const GLubyte*)"";
    }
}

const GLubyte* fear_glGetStringi(GLenum name, GLuint index) {
    if (name == GL_EXTENSIONS) {
        if (index < s_extension_pointers.size()) {
            return s_extension_pointers[index];
        }
        return (const GLubyte*)"";
    }
    if (s_fc_glGetStringi) {
        return s_fc_glGetStringi(name, index);
    }
    return (const GLubyte*)"";
}

void* fear_glGetProcAddress(const char* procname) {
    if (strcmp(procname, "glGetString") == 0) {
        return (void*)fear_glGetString;
    }
    if (strcmp(procname, "glGetStringi") == 0) {
        return (void*)fear_glGetStringi;
    }

    // Resolve via libFearCore.so first
    if (s_fear_core_handle) {
        typedef void* (*PFN_eglGetProcAddress)(const char*);
        static PFN_eglGetProcAddress s_fc_eglGetProcAddress = (PFN_eglGetProcAddress)dlsym(s_fear_core_handle, "eglGetProcAddress");
        if (s_fc_eglGetProcAddress) {
            void* addr = s_fc_eglGetProcAddress(procname);
            if (addr) return addr;
        }
        void* addr = dlsym(s_fear_core_handle, procname);
        if (addr) return addr;
    }

    return nullptr;
}

// ==============================================================================
// FULL TRANSPARENT PROXY WRAPPERS FOR ALL STANDARD EGL 1.4 SYMBOLS
// ==============================================================================

extern "C" EGLAPI EGLDisplay EGLAPIENTRY eglGetDisplay(EGLNativeDisplayType display_id) {
    typedef EGLDisplay (EGLAPIENTRY *PFN_eglGetDisplay)(EGLNativeDisplayType);
    static PFN_eglGetDisplay fn = (PFN_eglGetDisplay)dlsym(s_fear_core_handle, "eglGetDisplay");
    return fn ? fn(display_id) : EGL_NO_DISPLAY;
}

extern "C" EGLAPI EGLBoolean EGLAPIENTRY eglInitialize(EGLDisplay dpy, EGLint *major, EGLint *minor) {
    typedef EGLBoolean (EGLAPIENTRY *PFN_eglInitialize)(EGLDisplay, EGLint*, EGLint*);
    static PFN_eglInitialize fn = (PFN_eglInitialize)dlsym(s_fear_core_handle, "eglInitialize");
    return fn ? fn(dpy, major, minor) : EGL_FALSE;
}

extern "C" EGLAPI EGLBoolean EGLAPIENTRY eglTerminate(EGLDisplay dpy) {
    typedef EGLBoolean (EGLAPIENTRY *PFN_eglTerminate)(EGLDisplay);
    static PFN_eglTerminate fn = (PFN_eglTerminate)dlsym(s_fear_core_handle, "eglTerminate");
    return fn ? fn(dpy) : EGL_FALSE;
}

extern "C" EGLAPI const char* EGLAPIENTRY eglQueryString(EGLDisplay dpy, EGLint name) {
    typedef const char* (EGLAPIENTRY *PFN_eglQueryString)(EGLDisplay, EGLint);
    static PFN_eglQueryString fn = (PFN_eglQueryString)dlsym(s_fear_core_handle, "eglQueryString");
    return fn ? fn(dpy, name) : nullptr;
}

extern "C" EGLAPI EGLBoolean EGLAPIENTRY eglGetConfigs(EGLDisplay dpy, EGLConfig *configs, EGLint config_size, EGLint *num_config) {
    typedef EGLBoolean (EGLAPIENTRY *PFN_eglGetConfigs)(EGLDisplay, EGLConfig*, EGLint, EGLint*);
    static PFN_eglGetConfigs fn = (PFN_eglGetConfigs)dlsym(s_fear_core_handle, "eglGetConfigs");
    return fn ? fn(dpy, configs, config_size, num_config) : EGL_FALSE;
}

extern "C" EGLAPI EGLBoolean EGLAPIENTRY eglChooseConfig(EGLDisplay dpy, const EGLint *attrib_list, EGLConfig *configs, EGLint config_size, EGLint *num_config) {
    typedef EGLBoolean (EGLAPIENTRY *PFN_eglChooseConfig)(EGLDisplay, const EGLint*, EGLConfig*, EGLint, EGLint*);
    static PFN_eglChooseConfig fn = (PFN_eglChooseConfig)dlsym(s_fear_core_handle, "eglChooseConfig");
    return fn ? fn(dpy, attrib_list, configs, config_size, num_config) : EGL_FALSE;
}

extern "C" EGLAPI EGLSurface EGLAPIENTRY eglCreateWindowSurface(EGLDisplay dpy, EGLConfig config, EGLNativeWindowType win, const EGLint *attrib_list) {
    typedef EGLSurface (EGLAPIENTRY *PFN_eglCreateWindowSurface)(EGLDisplay, EGLConfig, EGLNativeWindowType, const EGLint*);
    static PFN_eglCreateWindowSurface fn = (PFN_eglCreateWindowSurface)dlsym(s_fear_core_handle, "eglCreateWindowSurface");
    return fn ? fn(dpy, config, win, attrib_list) : EGL_NO_SURFACE;
}

extern "C" EGLAPI EGLSurface EGLAPIENTRY eglCreatePbufferSurface(EGLDisplay dpy, EGLConfig config, const EGLint *attrib_list) {
    typedef EGLSurface (EGLAPIENTRY *PFN_eglCreatePbufferSurface)(EGLDisplay, EGLConfig, const EGLint*);
    static PFN_eglCreatePbufferSurface fn = (PFN_eglCreatePbufferSurface)dlsym(s_fear_core_handle, "eglCreatePbufferSurface");
    return fn ? fn(dpy, config, attrib_list) : EGL_NO_SURFACE;
}

extern "C" EGLAPI EGLSurface EGLAPIENTRY eglCreatePixmapSurface(EGLDisplay dpy, EGLConfig config, EGLNativePixmapType pixmap, const EGLint *attrib_list) {
    typedef EGLSurface (EGLAPIENTRY *PFN_eglCreatePixmapSurface)(EGLDisplay, EGLConfig, EGLNativePixmapType, const EGLint*);
    static PFN_eglCreatePixmapSurface fn = (PFN_eglCreatePixmapSurface)dlsym(s_fear_core_handle, "eglCreatePixmapSurface");
    return fn ? fn(dpy, config, pixmap, attrib_list) : EGL_NO_SURFACE;
}

extern "C" EGLAPI EGLBoolean EGLAPIENTRY eglDestroySurface(EGLDisplay dpy, EGLSurface surface) {
    typedef EGLBoolean (EGLAPIENTRY *PFN_eglDestroySurface)(EGLDisplay, EGLSurface);
    static PFN_eglDestroySurface fn = (PFN_eglDestroySurface)dlsym(s_fear_core_handle, "eglDestroySurface");
    return fn ? fn(dpy, surface) : EGL_FALSE;
}

extern "C" EGLAPI EGLBoolean EGLAPIENTRY eglQuerySurface(EGLDisplay dpy, EGLSurface surface, EGLint attribute, EGLint *value) {
    typedef EGLBoolean (EGLAPIENTRY *PFN_eglQuerySurface)(EGLDisplay, EGLSurface, EGLint, EGLint*);
    static PFN_eglQuerySurface fn = (PFN_eglQuerySurface)dlsym(s_fear_core_handle, "eglQuerySurface");
    return fn ? fn(dpy, surface, attribute, value) : EGL_FALSE;
}

extern "C" EGLAPI EGLBoolean EGLAPIENTRY eglBindAPI(EGLenum api) {
    typedef EGLBoolean (EGLAPIENTRY *PFN_eglBindAPI)(EGLenum);
    static PFN_eglBindAPI fn = (PFN_eglBindAPI)dlsym(s_fear_core_handle, "eglBindAPI");
    return fn ? fn(api) : EGL_FALSE;
}

extern "C" EGLAPI EGLenum EGLAPIENTRY eglQueryAPI(void) {
    typedef EGLenum (EGLAPIENTRY *PFN_eglQueryAPI)(void);
    static PFN_eglQueryAPI fn = (PFN_eglQueryAPI)dlsym(s_fear_core_handle, "eglQueryAPI");
    return fn ? fn() : (EGLenum)0;
}

extern "C" EGLAPI EGLBoolean EGLAPIENTRY eglWaitClient(void) {
    typedef EGLBoolean (EGLAPIENTRY *PFN_eglWaitClient)(void);
    static PFN_eglWaitClient fn = (PFN_eglWaitClient)dlsym(s_fear_core_handle, "eglWaitClient");
    return fn ? fn() : EGL_FALSE;
}

extern "C" EGLAPI EGLBoolean EGLAPIENTRY eglReleaseThread(void) {
    typedef EGLBoolean (EGLAPIENTRY *PFN_eglReleaseThread)(void);
    static PFN_eglReleaseThread fn = (PFN_eglReleaseThread)dlsym(s_fear_core_handle, "eglReleaseThread");
    return fn ? fn() : EGL_FALSE;
}

extern "C" EGLAPI EGLSurface EGLAPIENTRY eglCreatePbufferFromClientBuffer(EGLDisplay dpy, EGLenum buftype, EGLClientBuffer buffer, EGLConfig config, const EGLint *attrib_list) {
    typedef EGLSurface (EGLAPIENTRY *PFN_eglCreatePbufferFromClientBuffer)(EGLDisplay, EGLenum, EGLClientBuffer, EGLConfig, const EGLint*);
    static PFN_eglCreatePbufferFromClientBuffer fn = (PFN_eglCreatePbufferFromClientBuffer)dlsym(s_fear_core_handle, "eglCreatePbufferFromClientBuffer");
    return fn ? fn(dpy, buftype, buffer, config, attrib_list) : EGL_NO_SURFACE;
}

extern "C" EGLAPI EGLContext EGLAPIENTRY eglCreateContext(EGLDisplay dpy, EGLConfig config, EGLContext share_context, const EGLint *attrib_list) {
    typedef EGLContext (EGLAPIENTRY *PFN_eglCreateContext)(EGLDisplay, EGLConfig, EGLContext, const EGLint*);
    static PFN_eglCreateContext fn = (PFN_eglCreateContext)dlsym(s_fear_core_handle, "eglCreateContext");
    return fn ? fn(dpy, config, share_context, attrib_list) : EGL_NO_CONTEXT;
}

extern "C" EGLAPI EGLBoolean EGLAPIENTRY eglDestroyContext(EGLDisplay dpy, EGLContext ctx) {
    typedef EGLBoolean (EGLAPIENTRY *PFN_eglDestroyContext)(EGLDisplay, EGLContext);
    static PFN_eglDestroyContext fn = (PFN_eglDestroyContext)dlsym(s_fear_core_handle, "eglDestroyContext");
    return fn ? fn(dpy, ctx) : EGL_FALSE;
}

extern "C" EGLAPI EGLBoolean EGLAPIENTRY eglMakeCurrent(EGLDisplay dpy, EGLSurface draw, EGLSurface read, EGLContext ctx) {
    typedef EGLBoolean (EGLAPIENTRY *PFN_eglMakeCurrent)(EGLDisplay, EGLSurface, EGLSurface, EGLContext);
    static PFN_eglMakeCurrent fn = (PFN_eglMakeCurrent)dlsym(s_fear_core_handle, "eglMakeCurrent");
    return fn ? fn(dpy, draw, read, ctx) : EGL_FALSE;
}

extern "C" EGLAPI EGLContext EGLAPIENTRY eglGetCurrentContext(void) {
    typedef EGLContext (EGLAPIENTRY *PFN_eglGetCurrentContext)(void);
    static PFN_eglGetCurrentContext fn = (PFN_eglGetCurrentContext)dlsym(s_fear_core_handle, "eglGetCurrentContext");
    return fn ? fn() : EGL_NO_CONTEXT;
}

extern "C" EGLAPI EGLSurface EGLAPIENTRY eglGetCurrentSurface(EGLint readdraw) {
    typedef EGLSurface (EGLAPIENTRY *PFN_eglGetCurrentSurface)(EGLint);
    static PFN_eglGetCurrentSurface fn = (PFN_eglGetCurrentSurface)dlsym(s_fear_core_handle, "eglGetCurrentSurface");
    return fn ? fn(readdraw) : EGL_NO_SURFACE;
}

extern "C" EGLAPI EGLDisplay EGLAPIENTRY eglGetCurrentDisplay(void) {
    typedef EGLDisplay (EGLAPIENTRY *PFN_eglGetCurrentDisplay)(void);
    static PFN_eglGetCurrentDisplay fn = (PFN_eglGetCurrentDisplay)dlsym(s_fear_core_handle, "eglGetCurrentDisplay");
    return fn ? fn() : EGL_NO_DISPLAY;
}

extern "C" EGLAPI EGLBoolean EGLAPIENTRY eglQueryContext(EGLDisplay dpy, EGLContext ctx, EGLint attribute, EGLint *value) {
    typedef EGLBoolean (EGLAPIENTRY *PFN_eglQueryContext)(EGLDisplay, EGLContext, EGLint, EGLint*);
    static PFN_eglQueryContext fn = (PFN_eglQueryContext)dlsym(s_fear_core_handle, "eglQueryContext");
    return fn ? fn(dpy, ctx, attribute, value) : EGL_FALSE;
}

extern "C" EGLAPI EGLBoolean EGLAPIENTRY eglWaitGL(void) {
    typedef EGLBoolean (EGLAPIENTRY *PFN_eglWaitGL)(void);
    static PFN_eglWaitGL fn = (PFN_eglWaitGL)dlsym(s_fear_core_handle, "eglWaitGL");
    return fn ? fn() : EGL_FALSE;
}

extern "C" EGLAPI EGLBoolean EGLAPIENTRY eglWaitNative(EGLint engine) {
    typedef EGLBoolean (EGLAPIENTRY *PFN_eglWaitNative)(EGLint);
    static PFN_eglWaitNative fn = (PFN_eglWaitNative)dlsym(s_fear_core_handle, "eglWaitNative");
    return fn ? fn(engine) : EGL_FALSE;
}

extern "C" EGLAPI EGLBoolean EGLAPIENTRY eglSwapBuffers(EGLDisplay dpy, EGLSurface surface) {
    typedef EGLBoolean (EGLAPIENTRY *PFN_eglSwapBuffers)(EGLDisplay, EGLSurface);
    static PFN_eglSwapBuffers fn = (PFN_eglSwapBuffers)dlsym(s_fear_core_handle, "eglSwapBuffers");
    return fn ? fn(dpy, surface) : EGL_FALSE;
}

extern "C" EGLAPI EGLBoolean EGLAPIENTRY eglCopyBuffers(EGLDisplay dpy, EGLSurface surface, EGLNativePixmapType target) {
    typedef EGLBoolean (EGLAPIENTRY *PFN_eglCopyBuffers)(EGLDisplay, EGLSurface, EGLNativePixmapType);
    static PFN_eglCopyBuffers fn = (PFN_eglCopyBuffers)dlsym(s_fear_core_handle, "eglCopyBuffers");
    return fn ? fn(dpy, surface, target) : EGL_FALSE;
}

extern "C" EGLAPI EGLBoolean EGLAPIENTRY eglSurfaceAttrib(EGLDisplay dpy, EGLSurface surface, EGLint attribute, EGLint value) {
    typedef EGLBoolean (EGLAPIENTRY *PFN_eglSurfaceAttrib)(EGLDisplay, EGLSurface, EGLint, EGLint);
    static PFN_eglSurfaceAttrib fn = (PFN_eglSurfaceAttrib)dlsym(s_fear_core_handle, "eglSurfaceAttrib");
    return fn ? fn(dpy, surface, attribute, value) : EGL_FALSE;
}

extern "C" EGLAPI EGLBoolean EGLAPIENTRY eglSwapInterval(EGLDisplay dpy, EGLint interval) {
    typedef EGLBoolean (EGLAPIENTRY *PFN_eglSwapInterval)(EGLDisplay, EGLint);
    static PFN_eglSwapInterval fn = (PFN_eglSwapInterval)dlsym(s_fear_core_handle, "eglSwapInterval");
    return fn ? fn(dpy, interval) : EGL_FALSE;
}
