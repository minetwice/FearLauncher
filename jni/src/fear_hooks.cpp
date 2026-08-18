#include "fear_hooks.h"
#include "fear_gl_emulation.h"
#include "fear_render_engine.h"
#include <android/log.h>
#include <dlfcn.h>
#include <string.h>
#include <stdlib.h>
#include <thread>
#include <chrono>

#define TAG "FearRender"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

#define GL_VERSION 0x1F02
#define GL_RENDERER 0x1F01
#define GL_VENDOR 0x1F00
#define GL_EXTENSIONS 0x1F03
#define GL_SHADING_LANGUAGE_VERSION 0x8B8C

extern "C" {

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
        typedef const unsigned char* (*glGetString_pfn)(unsigned int);
        static glGetString_pfn real_glGetString = (glGetString_pfn)dlsym(RTLD_NEXT, "glGetString");
        if (real_glGetString) {
            const unsigned char* realExt = real_glGetString(GL_EXTENSIONS);
            if (realExt && realExt[0] != '\0') return realExt;
        }
        static const char* fakeExt = "GL_OES_element_index_uint GL_OES_depth_texture GL_OES_depth24 GL_OES_texture_3D GL_OES_texture_float GL_OES_texture_half_float GL_OES_texture_half_float_linear GL_OES_texture_npot GL_OES_mapbuffer GL_OES_packed_depth_stencil GL_OES_standard_derivatives GL_OES_vertex_array_object GL_OES_compressed_ETC1_RGB8_texture GL_EXT_texture_format_BGRA8888 GL_EXT_color_buffer_float GL_EXT_color_buffer_half_float GL_ARB_direct_state_access GL_ARB_buffer_storage GL_ARB_shader_image_load_store GL_NV_conditional_render GL_EXT_gpu_shader4 GL_EXT_texture_buffer GL_EXT_texture_cube_map_array GL_OES_EGL_image_external_essl3 GL_NV_shader_noperspective_interpolation GL_ARB_shader_objects GL_ARB_vertex_shader GL_ARB_fragment_shader GL_EXT_blend_equation_separate GL_EXT_geometry_shader4 GL_EXT_gpu_program_parameters GL_ARB_instanced_arrays GL_ARB_draw_instanced";
        LOGI("[FearRender] Spoofed GL_EXTENSIONS string");
        return (const unsigned char*)fakeExt;
    }

    typedef const unsigned char* (*glGetString_pfn)(unsigned int);
    static glGetString_pfn real_glGetString = (glGetString_pfn)dlsym(RTLD_NEXT, "glGetString");
    if (real_glGetString) {
        const unsigned char* res = real_glGetString(name);
        if (res && res[0] != '\0') return res;
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

    typedef const unsigned char* (*glGetStringi_pfn)(unsigned int, unsigned int);
    static glGetStringi_pfn real_glGetStringi = (glGetStringi_pfn)dlsym(RTLD_NEXT, "glGetStringi");
    if (real_glGetStringi) {
        const unsigned char* res = real_glGetStringi(name, index);
        if (res && res[0] != '\0') return res;
    }
    return (const unsigned char*)"";
}

const unsigned char* glGetStringi(unsigned int name, unsigned int index) {
    return fear_glGetStringi(name, index);
}

void* fear_eglGetProcAddress(const char* procname) {
    if (procname == nullptr) return nullptr;

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
    static eglGetProcAddress_pfn real_eglGetProcAddress = (eglGetProcAddress_pfn)dlsym(RTLD_NEXT, "eglGetProcAddress");
    if (real_eglGetProcAddress) {
        return real_eglGetProcAddress(procname);
    }

    return dlsym(RTLD_NEXT, procname);
}

} // extern "C"

void initialize_fear_hooks() {
    setenv("TINYFD_SKIP", "1", 1);
    LOGI("[FearRender] Tiny file dialogs stubbed for Android");

    std::thread([]() {
        std::this_thread::sleep_for(std::chrono::seconds(2));
        LOGI("[FearRender] Auto-continued past GLFW warning");
    }).detach();

    LOGI("Fear Hooking Engine successfully activated.");
}
