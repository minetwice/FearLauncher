#include "fear_hooks.h"
#include <android/log.h>
#include <dlfcn.h>
#include <string.h>
#include <stdlib.h>

#define TAG "FEAR_RENDERER"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

#define GL_VERSION 0x1F02
#define GL_RENDERER 0x1F01
#define GL_VENDOR 0x1F00
#define GL_EXTENSIONS 0x1F03

extern "C" {

// Declarations of intercepted GL4.2+ and shader functions (defined in fear_shader_interceptor.cpp)
void glMemoryBarrier(unsigned int barriers);
void glMemoryBarrierEXT(unsigned int barriers);
void fear_glMemoryBarrier(unsigned int barriers);
void fear_glMemoryBarrierEXT(unsigned int barriers);
void glBindImageTexture(unsigned int unit, unsigned int texture, int level, unsigned char layered, int layer, unsigned int access, unsigned int format);
void glDrawElementsInstancedBaseVertex(unsigned int mode, int count, unsigned int type, const void* indices, int primcount, int basevertex);
void glDrawArraysInstancedBaseInstance(unsigned int mode, int first, int count, int primcount, unsigned int baseinstance);
void glDrawElementsInstancedBaseVertexBaseInstance(unsigned int mode, int count, unsigned int type, const void* indices, int primcount, int basevertex, unsigned int baseinstance);
void glMultiDrawArrays(unsigned int mode, const int* first, const int* count, int drawcount);
void glMultiDrawElements(unsigned int mode, const int* count, unsigned int type, const void* const* indices, int drawcount);
void glInvalidateFramebuffer(unsigned int target, int numAttachments, const unsigned int* attachments);
void glBufferStorage(unsigned int target, long size, const void* data, unsigned int flags);
void glClearTexImage(unsigned int texture, int level, unsigned int format, unsigned int type, const void* data);
void glClearTexSubImage(unsigned int texture, int level, int xoffset, int yoffset, int zoffset, int width, int height, int depth, unsigned int format, unsigned int type, const void* data);
void glTextureBarrier();
void glCreateBuffers(int n, unsigned int* buffers);
void glNamedBufferData(unsigned int buffer, long size, const void* data, unsigned int usage);
void glNamedBufferSubData(unsigned int buffer, long offset, long size, const void* data);
void glBindTextureUnit(unsigned int unit, unsigned int texture);
void glShaderSource(unsigned int shader, int count, const char* const* string, const int* length);
void fear_glShaderSource(unsigned int shader, int count, const char* const* string, const int* length);
void glCompileShader(unsigned int shader);
void glAttachShader(unsigned int program, unsigned int shader);
void glDetachShader(unsigned int program, unsigned int shader);
void glLinkProgram(unsigned int program);
void glDeleteShader(unsigned int shader);
void glDeleteProgram(unsigned int program);
void glTexImage2D(unsigned int target, int level, int internalformat, int width, int height, int border, unsigned int format, unsigned int type, const void* pixels);
void glTexImage3D(unsigned int target, int level, int internalformat, int width, int height, int depth, int border, unsigned int format, unsigned int type, const void* pixels);
void glRenderbufferStorage(unsigned int target, unsigned int internalformat, int width, int height);
void glFramebufferTexture2D(unsigned int target, unsigned int attachment, unsigned int textarget, unsigned int texture, int level);
void glDispatchCompute(unsigned int num_groups_x, unsigned int num_groups_y, unsigned int num_groups_z);

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

// Override eglGetProcAddress to proxy desktop-only GL42+ functions safely to GLES 3.2 fallbacks
void* eglGetProcAddress(const char* procname) {
    if (procname == nullptr) return nullptr;

    if (strcmp(procname, "glMemoryBarrier") == 0 || strcmp(procname, "glMemoryBarrierEXT") == 0) return (void*)glMemoryBarrier;
    if (strcmp(procname, "glBindImageTexture") == 0) return (void*)glBindImageTexture;
    if (strcmp(procname, "glDrawElementsInstancedBaseVertex") == 0) return (void*)glDrawElementsInstancedBaseVertex;
    if (strcmp(procname, "glDrawArraysInstancedBaseInstance") == 0) return (void*)glDrawArraysInstancedBaseInstance;
    if (strcmp(procname, "glDrawElementsInstancedBaseVertexBaseInstance") == 0) return (void*)glDrawElementsInstancedBaseVertexBaseInstance;
    if (strcmp(procname, "glMultiDrawArrays") == 0) return (void*)glMultiDrawArrays;
    if (strcmp(procname, "glMultiDrawElements") == 0) return (void*)glMultiDrawElements;
    if (strcmp(procname, "glInvalidateFramebuffer") == 0) return (void*)glInvalidateFramebuffer;
    if (strcmp(procname, "glBufferStorage") == 0) return (void*)glBufferStorage;
    if (strcmp(procname, "glClearTexImage") == 0) return (void*)glClearTexImage;
    if (strcmp(procname, "glClearTexSubImage") == 0) return (void*)glClearTexSubImage;
    if (strcmp(procname, "glTextureBarrier") == 0) return (void*)glTextureBarrier;
    if (strcmp(procname, "glCreateBuffers") == 0) return (void*)glCreateBuffers;
    if (strcmp(procname, "glNamedBufferData") == 0) return (void*)glNamedBufferData;
    if (strcmp(procname, "glNamedBufferSubData") == 0) return (void*)glNamedBufferSubData;
    if (strcmp(procname, "glBindTextureUnit") == 0) return (void*)glBindTextureUnit;
    if (strcmp(procname, "glDispatchCompute") == 0) return (void*)glDispatchCompute;

    if (strcmp(procname, "glShaderSource") == 0 || strcmp(procname, "glShaderSourceARB") == 0) return (void*)glShaderSource;
    if (strcmp(procname, "glCompileShader") == 0 || strcmp(procname, "glCompileShaderARB") == 0) return (void*)glCompileShader;
    if (strcmp(procname, "glAttachShader") == 0) return (void*)glAttachShader;
    if (strcmp(procname, "glDetachShader") == 0) return (void*)glDetachShader;
    if (strcmp(procname, "glLinkProgram") == 0) return (void*)glLinkProgram;
    if (strcmp(procname, "glDeleteShader") == 0) return (void*)glDeleteShader;
    if (strcmp(procname, "glDeleteProgram") == 0) return (void*)glDeleteProgram;
    if (strcmp(procname, "glTexImage2D") == 0) return (void*)glTexImage2D;
    if (strcmp(procname, "glTexImage3D") == 0) return (void*)glTexImage3D;
    if (strcmp(procname, "glRenderbufferStorage") == 0) return (void*)glRenderbufferStorage;
    if (strcmp(procname, "glFramebufferTexture2D") == 0) return (void*)glFramebufferTexture2D;
    if (strcmp(procname, "glGetString") == 0) return (void*)fear_glGetString;
    if (strcmp(procname, "glGetStringi") == 0) return (void*)fear_glGetStringi;

    // Call real eglGetProcAddress
    typedef void* (*eglGetProcAddress_pfn)(const char*);
    static eglGetProcAddress_pfn real_eglGetProcAddress = nullptr;
    if (!real_eglGetProcAddress) {
        real_eglGetProcAddress = (eglGetProcAddress_pfn)dlsym(RTLD_NEXT, "eglGetProcAddress");
    }
    if (real_eglGetProcAddress) {
        return real_eglGetProcAddress(procname);
    }

    // Fallback to dlsym
    return dlsym(RTLD_NEXT, procname);
}

} // extern "C"

void initialize_fear_hooks() {
    LOGI("Fear Hooking Engine successfully activated.");
}
