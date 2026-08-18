#include "fear_hooks.h"
#include "fear_gl_emulation.h"
#include "fear_render_engine.h"
#include <android/log.h>
#include <dlfcn.h>
#include <string.h>
#include <stdlib.h>

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
        return (const unsigned char*)"Fear Render / FOGLTLOGLES";
    } else if (name == GL_EXTENSIONS) {
        LOGI("[FearRender] Spoofed GL_EXTENSIONS string");
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

    return nullptr;
}

} // extern "C"

void initialize_fear_hooks() {
    LOGI("Fear Hooking Engine successfully activated.");
}
