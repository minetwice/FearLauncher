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

// ==============================================================================
// FULL TRANSPARENT PROXY WRAPPERS FOR ALL STANDARD EGL 1.4 SYMBOLS
// ==============================================================================

#define RESOLVE_EGL_SYM(name) \
    typedef decltype(name)* PFN_##name; \
    static PFN_##name fn = (PFN_##name)dlsym(s_fear_core_handle, #name);

extern "C" EGLAPI EGLint EGLAPIENTRY eglGetError(void) {
    RESOLVE_EGL_SYM(eglGetError);
    return fn ? fn() : EGL_SUCCESS;
}

extern "C" EGLAPI EGLDisplay EGLAPIENTRY eglGetDisplay(EGLNativeDisplayType display_id) {
    RESOLVE_EGL_SYM(eglGetDisplay);
    return fn ? fn(display_id) : EGL_NO_DISPLAY;
}

extern "C" EGLAPI EGLBoolean EGLAPIENTRY eglInitialize(EGLDisplay dpy, EGLint *major, EGLint *minor) {
    RESOLVE_EGL_SYM(eglInitialize);
    return fn ? fn(dpy, major, minor) : EGL_FALSE;
}

extern "C" EGLAPI EGLBoolean EGLAPIENTRY eglTerminate(EGLDisplay dpy) {
    RESOLVE_EGL_SYM(eglTerminate);
    return fn ? fn(dpy) : EGL_FALSE;
}

extern "C" EGLAPI const char* EGLAPIENTRY eglQueryString(EGLDisplay dpy, EGLint name) {
    RESOLVE_EGL_SYM(eglQueryString);
    return fn ? fn(dpy, name) : nullptr;
}

extern "C" EGLAPI EGLBoolean EGLAPIENTRY eglGetConfigs(EGLDisplay dpy, EGLConfig *configs, EGLint config_size, EGLint *num_config) {
    RESOLVE_EGL_SYM(eglGetConfigs);
    return fn ? fn(dpy, configs, config_size, num_config) : EGL_FALSE;
}

extern "C" EGLAPI EGLBoolean EGLAPIENTRY eglChooseConfig(EGLDisplay dpy, const EGLint *attrib_list, EGLConfig *configs, EGLint config_size, EGLint *num_config) {
    RESOLVE_EGL_SYM(eglChooseConfig);
    return fn ? fn(dpy, attrib_list, configs, config_size, num_config) : EGL_FALSE;
}

extern "C" EGLAPI EGLBoolean EGLAPIENTRY eglGetConfigAttrib(EGLDisplay dpy, EGLConfig config, EGLint attribute, EGLint *value) {
    RESOLVE_EGL_SYM(eglGetConfigAttrib);
    return fn ? fn(dpy, config, attribute, value) : EGL_FALSE;
}

extern "C" EGLAPI EGLSurface EGLAPIENTRY eglCreateWindowSurface(EGLDisplay dpy, EGLConfig config, EGLNativeWindowType win, const EGLint *attrib_list) {
    RESOLVE_EGL_SYM(eglCreateWindowSurface);
    return fn ? fn(dpy, config, win, attrib_list) : EGL_NO_SURFACE;
}

extern "C" EGLAPI EGLSurface EGLAPIENTRY eglCreatePbufferSurface(EGLDisplay dpy, EGLConfig config, const EGLint *attrib_list) {
    RESOLVE_EGL_SYM(eglCreatePbufferSurface);
    return fn ? fn(dpy, config, attrib_list) : EGL_NO_SURFACE;
}

extern "C" EGLAPI EGLSurface EGLAPIENTRY eglCreatePixmapSurface(EGLDisplay dpy, EGLConfig config, EGLNativePixmapType pixmap, const EGLint *attrib_list) {
    RESOLVE_EGL_SYM(eglCreatePixmapSurface);
    return fn ? fn(dpy, config, pixmap, attrib_list) : EGL_NO_SURFACE;
}

extern "C" EGLAPI EGLBoolean EGLAPIENTRY eglDestroySurface(EGLDisplay dpy, EGLSurface surface) {
    RESOLVE_EGL_SYM(eglDestroySurface);
    return fn ? fn(dpy, surface) : EGL_FALSE;
}

extern "C" EGLAPI EGLBoolean EGLAPIENTRY eglQuerySurface(EGLDisplay dpy, EGLSurface surface, EGLint attribute, EGLint *value) {
    RESOLVE_EGL_SYM(eglQuerySurface);
    return fn ? fn(dpy, surface, attribute, value) : EGL_FALSE;
}

extern "C" EGLAPI EGLBoolean EGLAPIENTRY eglBindAPI(EGLenum api) {
    RESOLVE_EGL_SYM(eglBindAPI);
    return fn ? fn(api) : EGL_FALSE;
}

extern "C" EGLAPI EGLenum EGLAPIENTRY eglQueryAPI(void) {
    RESOLVE_EGL_SYM(eglQueryAPI);
    return fn ? fn() : (EGLenum)0;
}

extern "C" EGLAPI EGLBoolean EGLAPIENTRY eglWaitClient(void) {
    RESOLVE_EGL_SYM(eglWaitClient);
    return fn ? fn() : EGL_FALSE;
}

extern "C" EGLAPI EGLBoolean EGLAPIENTRY eglReleaseThread(void) {
    RESOLVE_EGL_SYM(eglReleaseThread);
    return fn ? fn() : EGL_FALSE;
}

extern "C" EGLAPI EGLSurface EGLAPIENTRY eglCreatePbufferFromClientBuffer(EGLDisplay dpy, EGLenum buftype, EGLClientBuffer buffer, EGLConfig config, const EGLint *attrib_list) {
    RESOLVE_EGL_SYM(eglCreatePbufferFromClientBuffer);
    return fn ? fn(dpy, buftype, buffer, config, attrib_list) : EGL_NO_SURFACE;
}

extern "C" EGLAPI EGLContext EGLAPIENTRY eglCreateContext(EGLDisplay dpy, EGLConfig config, EGLContext share_context, const EGLint *attrib_list) {
    RESOLVE_EGL_SYM(eglCreateContext);
    return fn ? fn(dpy, config, share_context, attrib_list) : EGL_NO_CONTEXT;
}

extern "C" EGLAPI EGLBoolean EGLAPIENTRY eglDestroyContext(EGLDisplay dpy, EGLContext ctx) {
    RESOLVE_EGL_SYM(eglDestroyContext);
    return fn ? fn(dpy, ctx) : EGL_FALSE;
}

extern "C" EGLAPI EGLBoolean EGLAPIENTRY eglMakeCurrent(EGLDisplay dpy, EGLSurface draw, EGLSurface read, EGLContext ctx) {
    RESOLVE_EGL_SYM(eglMakeCurrent);
    return fn ? fn(dpy, draw, read, ctx) : EGL_FALSE;
}

extern "C" EGLAPI EGLContext EGLAPIENTRY eglGetCurrentContext(void) {
    RESOLVE_EGL_SYM(eglGetCurrentContext);
    return fn ? fn() : EGL_NO_CONTEXT;
}

extern "C" EGLAPI EGLSurface EGLAPIENTRY eglGetCurrentSurface(EGLint readdraw) {
    RESOLVE_EGL_SYM(eglGetCurrentSurface);
    return fn ? fn(readdraw) : EGL_NO_SURFACE;
}

extern "C" EGLAPI EGLDisplay EGLAPIENTRY eglGetCurrentDisplay(void) {
    RESOLVE_EGL_SYM(eglGetCurrentDisplay);
    return fn ? fn() : EGL_NO_DISPLAY;
}

extern "C" EGLAPI EGLBoolean EGLAPIENTRY eglQueryContext(EGLDisplay dpy, EGLContext ctx, EGLint attribute, EGLint *value) {
    RESOLVE_EGL_SYM(eglQueryContext);
    return fn ? fn(dpy, ctx, attribute, value) : EGL_FALSE;
}

extern "C" EGLAPI EGLBoolean EGLAPIENTRY eglWaitGL(void) {
    RESOLVE_EGL_SYM(eglWaitGL);
    return fn ? fn() : EGL_FALSE;
}

extern "C" EGLAPI EGLBoolean EGLAPIENTRY eglWaitNative(EGLint engine) {
    RESOLVE_EGL_SYM(eglWaitNative);
    return fn ? fn(engine) : EGL_FALSE;
}

extern "C" EGLAPI EGLBoolean EGLAPIENTRY eglSwapBuffers(EGLDisplay dpy, EGLSurface surface) {
    RESOLVE_EGL_SYM(eglSwapBuffers);
    return fn ? fn(dpy, surface) : EGL_FALSE;
}

extern "C" EGLAPI EGLBoolean EGLAPIENTRY eglCopyBuffers(EGLDisplay dpy, EGLSurface surface, EGLNativePixmapType target) {
    RESOLVE_EGL_SYM(eglCopyBuffers);
    return fn ? fn(dpy, surface, target) : EGL_FALSE;
}

extern "C" EGLAPI EGLBoolean EGLAPIENTRY eglSurfaceAttrib(EGLDisplay dpy, EGLSurface surface, EGLint attribute, EGLint value) {
    RESOLVE_EGL_SYM(eglSurfaceAttrib);
    return fn ? fn(dpy, surface, attribute, value) : EGL_FALSE;
}

extern "C" EGLAPI EGLBoolean EGLAPIENTRY eglSwapInterval(EGLDisplay dpy, EGLint interval) {
    RESOLVE_EGL_SYM(eglSwapInterval);
    return fn ? fn(dpy, interval) : EGL_FALSE;
}

void* fear_glGetProcAddress(const char* procname) {
    if (strcmp(procname, "glGetString") == 0) {
        return (void*)fear_glGetString;
    }
    if (strcmp(procname, "glGetStringi") == 0) {
        return (void*)fear_glGetStringi;
    }

    // Explicitly return our wrapped EGL symbols to ensure GLFW and LWJGL load them flawlessly
#define HOOK_EGL_SYM(name) \
    if (strcmp(procname, #name) == 0) return (void*)name;

    HOOK_EGL_SYM(eglGetError)
    HOOK_EGL_SYM(eglGetDisplay)
    HOOK_EGL_SYM(eglInitialize)
    HOOK_EGL_SYM(eglTerminate)
    HOOK_EGL_SYM(eglQueryString)
    HOOK_EGL_SYM(eglGetConfigs)
    HOOK_EGL_SYM(eglChooseConfig)
    HOOK_EGL_SYM(eglGetConfigAttrib)
    HOOK_EGL_SYM(eglCreateWindowSurface)
    HOOK_EGL_SYM(eglCreatePbufferSurface)
    HOOK_EGL_SYM(eglCreatePixmapSurface)
    HOOK_EGL_SYM(eglDestroySurface)
    HOOK_EGL_SYM(eglQuerySurface)
    HOOK_EGL_SYM(eglBindAPI)
    HOOK_EGL_SYM(eglQueryAPI)
    HOOK_EGL_SYM(eglWaitClient)
    HOOK_EGL_SYM(eglReleaseThread)
    HOOK_EGL_SYM(eglCreatePbufferFromClientBuffer)
    HOOK_EGL_SYM(eglCreateContext)
    HOOK_EGL_SYM(eglDestroyContext)
    HOOK_EGL_SYM(eglMakeCurrent)
    HOOK_EGL_SYM(eglGetCurrentContext)
    HOOK_EGL_SYM(eglGetCurrentSurface)
    HOOK_EGL_SYM(eglGetCurrentDisplay)
    HOOK_EGL_SYM(eglQueryContext)
    HOOK_EGL_SYM(eglWaitGL)
    HOOK_EGL_SYM(eglWaitNative)
    HOOK_EGL_SYM(eglSwapBuffers)
    HOOK_EGL_SYM(eglCopyBuffers)
    HOOK_EGL_SYM(eglSurfaceAttrib)
    HOOK_EGL_SYM(eglSwapInterval)
#undef HOOK_EGL_SYM

    // Resolve other dynamic/extension EGL/GL functions via libFearCore.so under the hood
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
