#include "fear_hooks.h"
#include <android/log.h>
#include <dlfcn.h>
#include <string.h>
#include <stdlib.h>
#include <string>

#define TAG "FEAR_RENDERER"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#define GL_VERSION 0x1F02
#define GL_RENDERER 0x1F01
#define GL_VENDOR 0x1F00
#define GL_EXTENSIONS 0x1F03

extern "C" {

const unsigned char* fear_glGetString(unsigned int name) {
    if (name == GL_VERSION) {
        return (const unsigned char*)"4.6.0 NVIDIA 545.29";
    } else if (name == GL_RENDERER) {
        return (const unsigned char*)"NVIDIA GeForce RTX 4090";
    } else if (name == GL_VENDOR) {
        return (const unsigned char*)"NVIDIA Corporation";
    } else if (name == GL_EXTENSIONS) {
        return (const unsigned char*)"GL_ARB_direct_state_access GL_ARB_buffer_storage GL_ARB_shader_image_load_store GL_NV_conditional_render GL_EXT_gpu_shader4 GL_EXT_texture_buffer GL_EXT_texture_cube_map_array GL_OES_EGL_image_external_essl3 GL_NV_shader_noperspective_interpolation GL_ARB_shader_objects GL_ARB_vertex_shader GL_ARB_fragment_shader GL_EXT_blend_equation_separate GL_EXT_geometry_shader4 GL_EXT_gpu_program_parameters GL_ARB_instanced_arrays GL_ARB_draw_instanced";
    }

    typedef const unsigned char* (*glGetString_pfn)(unsigned int);
    static glGetString_pfn real_glGetString = nullptr;
    if (!real_glGetString) {
        real_glGetString = (glGetString_pfn)dlsym(RTLD_NEXT, "glGetString");
    }
    if (real_glGetString) {
        return real_glGetString(name);
    }
    return (const unsigned char*)"";
}

const unsigned char* fear_glGetStringi(unsigned int name, unsigned int index) {
    if (name == GL_EXTENSIONS) {
        static const char* extensions[] = {
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

    typedef const unsigned char* (*glGetStringi_pfn)(unsigned int, unsigned int);
    static glGetStringi_pfn real_glGetStringi = nullptr;
    if (!real_glGetStringi) {
        real_glGetStringi = (glGetStringi_pfn)dlsym(RTLD_NEXT, "glGetStringi");
    }
    if (real_glGetStringi) {
        return real_glGetStringi(name, index);
    }
    return (const unsigned char*)"";
}

// Export the dynamic symbols exactly so LWJGL binds directly to them
void glMemoryBarrier(unsigned int barriers) {
    typedef void (*glFlush_pfn)();
    static glFlush_pfn real_glFlush = nullptr;
    if (!real_glFlush) {
        real_glFlush = (glFlush_pfn)dlsym(RTLD_NEXT, "glFlush");
    }
    if (real_glFlush) {
        real_glFlush();
    }
    LOGI("glMemoryBarrier intercepted and flushed safely to prevent world rendering crash (Barriers: %u)", barriers);
}

void glMemoryBarrierEXT(unsigned int barriers) {
    glMemoryBarrier(barriers);
}

// Hook and stub glMemoryBarrier to prevent JVM crashes on server lobbies
void fear_glMemoryBarrier(unsigned int barriers) {
    glMemoryBarrier(barriers);
}

// Intercept and bypass glMemoryBarrierEXT
void fear_glMemoryBarrierEXT(unsigned int barriers) {
    glMemoryBarrier(barriers);
}

// Hook glShaderSource to dynamically rewrite shaders for Complementary & Solas in real-time!
void glShaderSource(unsigned int shader, int count, const char* const* string, const int* length) {
    typedef void (*glShaderSource_pfn)(unsigned int, int, const char* const*, const int*);
    static glShaderSource_pfn real_glShaderSource = nullptr;
    if (!real_glShaderSource) {
        real_glShaderSource = (glShaderSource_pfn)dlsym(RTLD_NEXT, "glShaderSource");
    }

    if (!real_glShaderSource) {
        LOGE("Failed to find real glShaderSource symbol!");
        return;
    }

    if (count <= 0 || !string || !string[0]) {
        real_glShaderSource(shader, count, string, length);
        return;
    }

    // Allocate memory and combine multiple source blocks if needed
    std::string full_source = "";
    for (int i = 0; i < count; i++) {
        if (string[i]) {
            if (length && length[i] >= 0) {
                full_source.append(string[i], length[i]);
            } else {
                full_source.append(string[i]);
            }
        }
    }

    // Run translation logic on GLSL sources
    size_t pos;
    // Replace non-perspective interpolation with standard flat fallback
    while ((pos = full_source.find("noperspective")) != std::string::npos) {
        full_source.replace(pos, 13, "flat");
    }

    // Rewrite unsupported layout qualifiers for mobile GPUs
    while ((pos = full_source.find("layout(location = 0) out vec4 fragColor;")) != std::string::npos) {
        full_source.replace(pos, 40, "out vec4 fragColor;");
    }
    while ((pos = full_source.find("layout(location = 0) out vec4 out_Color;")) != std::string::npos) {
        full_source.replace(pos, 40, "out vec4 out_Color;");
    }

    // Set correct OpenGL ES 3.2 headers and precision markers on-the-fly
    if (full_source.find("#version 330") != std::string::npos || full_source.find("#version 150") != std::string::npos) {
        pos = full_source.find("#version");
        if (pos != std::string::npos) {
            size_t end_line = full_source.find("\n", pos);
            full_source.replace(pos, end_line - pos, "#version 320 es\nprecision highp float;\nprecision highp int;");
        }
    }

    const char* translated_cstr = full_source.c_str();
    real_glShaderSource(shader, 1, &translated_cstr, nullptr);
    LOGI("glShaderSource intercepted and transpiled dynamically on-the-fly (Shader: %u)", shader);
}

// Export fear_glShaderSource alias
void fear_glShaderSource(unsigned int shader, int count, const char* const* string, const int* length) {
    glShaderSource(shader, count, string, length);
}

} // extern "C"

void initialize_fear_hooks() {
    LOGI("Fear Hooking Engine successfully activated.");
}
