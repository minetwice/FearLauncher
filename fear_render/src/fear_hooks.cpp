#include "fear_hooks.h"
#include <dlfcn.h>
#include <string.h>
#include <android/log.h>
#include <vector>
#include <string>

#define LOG_TAG "FEAR_RENDERER"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static void* s_ltw_handle = nullptr;

typedef const GLubyte* (*PFN_glGetString)(GLenum name);
typedef const GLubyte* (*PFN_glGetStringi)(GLenum name, GLuint index);

static PFN_glGetString s_ltw_glGetString = nullptr;
static PFN_glGetStringi s_ltw_glGetStringi = nullptr;

static std::vector<std::string> s_simulated_extensions;
static std::vector<const GLubyte*> s_extension_pointers;

void init_fear_hooks() {
    LOGI("Initializing Fear Renderer Hook Engine as a wrapper around libltw.so...");

    // Load the pre-installed libltw.so library from the application's native directory
    const char* native_dir = getenv("POJAV_NATIVEDIR");
    std::string ltw_path = "libltw.so";
    if (native_dir) {
        ltw_path = std::string(native_dir) + "/libltw.so";
    }

    s_ltw_handle = dlopen(ltw_path.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (!s_ltw_handle) {
        // Fallback to searching library path
        s_ltw_handle = dlopen("libltw.so", RTLD_NOW | RTLD_GLOBAL);
    }

    if (!s_ltw_handle) {
        LOGE("Failed to load underlying libltw.so library!");
        return;
    }

    s_ltw_glGetString = (PFN_glGetString)dlsym(s_ltw_handle, "glGetString");
    s_ltw_glGetStringi = (PFN_glGetStringi)dlsym(s_ltw_handle, "glGetStringi");

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

    // Grab actual system/LTW extensions and append them
    if (s_ltw_glGetString) {
        const char* ltw_exts = (const char*)s_ltw_glGetString(GL_EXTENSIONS);
        if (ltw_exts) {
            std::string exts(ltw_exts);
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
            return (const GLubyte*)"4.6 (Core Profile)";
        case GL_RENDERER:
            return (const GLubyte*)"NVIDIA GeForce GTX 1080 (Fear Virtual Desktop Engine)";
        case GL_VENDOR:
            return (const GLubyte*)"Fear Open-Source Technologies";
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
            if (s_ltw_glGetString) {
                return s_ltw_glGetString(name);
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
    if (s_ltw_glGetStringi) {
        return s_ltw_glGetStringi(name, index);
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

    // Resolve via libltw.so first
    if (s_ltw_handle) {
        typedef void* (*PFN_eglGetProcAddress)(const char*);
        static PFN_eglGetProcAddress s_ltw_eglGetProcAddress = (PFN_eglGetProcAddress)dlsym(s_ltw_handle, "eglGetProcAddress");
        if (s_ltw_eglGetProcAddress) {
            void* addr = s_ltw_eglGetProcAddress(procname);
            if (addr) return addr;
        }
        void* addr = dlsym(s_ltw_handle, procname);
        if (addr) return addr;
    }

    return nullptr;
}
