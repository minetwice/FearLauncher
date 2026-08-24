//
// Created by maks on 06.01.2025.
//

#include "jvm_hooks.h"

#include <android/api-level.h>

#include <dlfcn.h>
#include <string.h>
#include <stdlib.h>

#define TAG __FILE_NAME__
#include <log.h>

#include "../pojavexec.h"

/**
 * Basically a verbatim implementation of ndlopen(), found at
 * https://github.com/PojavLauncherTeam/lwjgl3/blob/3.3.1/modules/lwjgl/core/src/generated/c/linux/org_lwjgl_system_linux_DynamicLinkLoader.c#L11
 * but with our own additions for stuff like vulkanmod.
 */
#define GL_VERSION 0x1F02
#define GL_RENDERER 0x1F01
#define GL_VENDOR 0x1F00
#define GL_EXTENSIONS 0x1F03

static void glMemoryBarrier_stub(unsigned int barriers) {
    typedef void (*glFlush_pfn)();
    static glFlush_pfn real_glFlush = NULL;
    if (!real_glFlush) {
        real_glFlush = (glFlush_pfn) dlsym(RTLD_DEFAULT, "glFlush");
        if (!real_glFlush) {
            real_glFlush = (glFlush_pfn) dlsym(RTLD_NEXT, "glFlush");
        }
    }
    if (real_glFlush) {
        real_glFlush();
    }
    LOGI("glMemoryBarrier stub called and flushed successfully (Barriers: %u)", barriers);
}

static const unsigned char* glGetString_hook(unsigned int name) {
    if (name == GL_VERSION) {
        return (const unsigned char*)"4.6.0 NVIDIA 545.29";
    } else if (name == GL_RENDERER) {
        return (const unsigned char*)"NVIDIA GeForce RTX 4090";
    } else if (name == GL_VENDOR) {
        return (const unsigned char*)"NVIDIA Corporation";
    } else if (name == GL_EXTENSIONS) {
        return (const unsigned char*)"GL_ARB_direct_state_access GL_ARB_buffer_storage GL_ARB_shader_image_load_store GL_NV_conditional_render GL_EXT_gpu_shader4 GL_EXT_texture_buffer GL_EXT_texture_cube_map_array GL_OES_EGL_image_external_essl3 GL_ARB_shader_texture_lod GL_ARB_shader_objects GL_ARB_vertex_shader GL_ARB_fragment_shader GL_EXT_blend_equation_separate GL_EXT_geometry_shader4 GL_EXT_gpu_program_parameters GL_ARB_instanced_arrays GL_ARB_draw_instanced";
    }

    typedef const unsigned char* (*glGetString_pfn)(unsigned int);
    static glGetString_pfn real_glGetString = NULL;
    if (!real_glGetString) {
        real_glGetString = (glGetString_pfn) dlsym(RTLD_DEFAULT, "glGetString");
        if (!real_glGetString) {
            real_glGetString = (glGetString_pfn) dlsym(RTLD_NEXT, "glGetString");
        }
    }
    if (real_glGetString) {
        return real_glGetString(name);
    }
    return (const unsigned char*)"";
}

static const unsigned char* glGetStringi_hook(unsigned int name, unsigned int index) {
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
            "GL_ARB_shader_texture_lod",
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
    static glGetStringi_pfn real_glGetStringi = NULL;
    if (!real_glGetStringi) {
        real_glGetStringi = (glGetStringi_pfn) dlsym(RTLD_DEFAULT, "glGetStringi");
        if (!real_glGetStringi) {
            real_glGetStringi = (glGetStringi_pfn) dlsym(RTLD_NEXT, "glGetStringi");
        }
    }
    if (real_glGetStringi) {
        return real_glGetStringi(name, index);
    }
    return (const unsigned char*)"";
}

/**
 * Strip unsupported GLSL keywords and extension directives from shader source
 * before it reaches the GLSL compiler. This fixes compatibility with shaderpacks
 * that use desktop-only features not supported by mobile GLSL compilers (LTW/Mali).
 *
 * Currently strips:
 * - "noperspective " keyword (GL_NV_shader_noperspective_interpolation)
 * - "#extension GL_NV_shader_noperspective_interpolation" directive
 */
static char* strip_unsupported_glsl(const char* source) {
    if (source == NULL) return NULL;

    size_t src_len = strlen(source);
    if (src_len == 0) return NULL;

    /* Allocate buffer same size - stripped result will always be <= source size */
    char* result = (char*) malloc(src_len + 1);
    if (result == NULL) return NULL;

    const char* src = source;
    char* dst = result;
    const char* src_end = source + src_len;

    while (src < src_end) {
        /* Check for "noperspective" keyword (preceded by whitespace, not part of identifier) */
        if (src + 13 <= src_end && strncmp(src, "noperspective", 13) == 0) {
            /* Make sure it's a standalone keyword (next char is space/tab/newline) */
            char next_ch = (src + 13 < src_end) ? src[13] : ' ';
            if (next_ch == ' ' || next_ch == '\t' || next_ch == '\n' || next_ch == '\r') {
                /* Skip the keyword and the trailing space */
                src += 13;
                /* Skip one trailing space if present */
                if (src < src_end && (*src == ' ' || *src == '\t')) {
                    src++;
                }
                continue;
            }
        }

        /* Check for "#extension GL_NV_shader_noperspective_interpolation" directive */
        if (src + 12 <= src_end && strncmp(src, "#extension", 10) == 0) {
            /* Find the end of this line */
            const char* line_end = strchr(src, '\n');
            if (line_end == NULL) line_end = src_end;
            size_t line_len = line_end - src;

            /* Check if this line mentions noperspective */
            char ext_line[512];
            if (line_len < sizeof(ext_line)) {
                memcpy(ext_line, src, line_len);
                ext_line[line_len] = '\0';
                if (strstr(ext_line, "GL_NV_shader_noperspective") != NULL) {
                    /* Skip the entire line including the newline */
                    src = (line_end < src_end) ? line_end + 1 : src_end;
                    continue;
                }
            }
        }

        /* Copy character as-is */
        *dst++ = *src++;
    }

    *dst = '\0';
    return result;
}

/**
 * Hooked glShaderSource that strips unsupported GLSL keywords before compilation.
 * This intercepts ALL shader source submissions (vertex, fragment, geometry, etc.)
 * and removes keywords that LTW's GLSL compiler doesn't support.
 */
typedef void (*glShaderSource_pfn)(unsigned int shader, unsigned int count, const char* const* string, const int* length);

static void glShaderSource_hook(unsigned int shader, unsigned int count, const char* const* string, const int* length) {
    static glShaderSource_pfn real_glShaderSource = NULL;
    if (!real_glShaderSource) {
        real_glShaderSource = (glShaderSource_pfn) dlsym(RTLD_DEFAULT, "glShaderSource");
        if (!real_glShaderSource) {
            real_glShaderSource = (glShaderSource_pfn) dlsym(RTLD_NEXT, "glShaderSource");
        }
    }
    if (!real_glShaderSource) {
        LOGE("glShaderSource_hook: real glShaderSource not found!");
        return;
    }

    if (count == 1 && string != NULL && string[0] != NULL) {
        const char* original = string[0];
        char* stripped = strip_unsupported_glsl(original);
        if (stripped != NULL && strcmp(stripped, original) != 0) {
            const char* new_strings[1] = { stripped };
            int new_length = (int) strlen(stripped);
            real_glShaderSource(shader, 1, new_strings, &new_length);
            LOGI("glShaderSource_hook: Stripped noperspective from shader (original=%zu bytes, stripped=%zu bytes)", strlen(original), strlen(stripped));
        } else {
            real_glShaderSource(shader, count, string, length);
        }
        if (stripped != NULL) free(stripped);
    } else if (count > 0 && count <= 64 && string != NULL) {
        const char* new_strings[64];
        char* stripped_ptrs[64];
        int modified = 0;
        int new_lengths[64];

        for (unsigned int i = 0; i < count; i++) {
            stripped_ptrs[i] = NULL;
            if (string[i] != NULL) {
                stripped_ptrs[i] = strip_unsupported_glsl(string[i]);
                if (stripped_ptrs[i] != NULL) {
                    new_strings[i] = stripped_ptrs[i];
                    new_lengths[i] = (int) strlen(stripped_ptrs[i]);
                    if (strcmp(stripped_ptrs[i], string[i]) != 0) {
                        modified = 1;
                    }
                } else {
                    new_strings[i] = string[i];
                    new_lengths[i] = length ? length[i] : (int) strlen(string[i]);
                }
            } else {
                new_strings[i] = NULL;
                new_lengths[i] = 0;
            }
        }

        if (modified) {
            real_glShaderSource(shader, count, new_strings, new_lengths);
            LOGI("glShaderSource_hook: Stripped noperspective from multi-string shader (%u strings)", count);
        } else {
            real_glShaderSource(shader, count, string, length);
        }

        for (unsigned int i = 0; i < count; i++) {
            if (stripped_ptrs[i] != NULL) free(stripped_ptrs[i]);
        }
    } else {
        real_glShaderSource(shader, count, string, length);
    }
}

static void* eglGetProcAddress_hook(const char* procname) {
    if (procname == NULL) return NULL;
    if (strcmp(procname, "glMemoryBarrier") == 0 || strcmp(procname, "glMemoryBarrierEXT") == 0) {
        LOGI("eglGetProcAddress_hook: Intercepted and returned custom glMemoryBarrier stub!");
        return (void*) glMemoryBarrier_stub;
    }
    if (strcmp(procname, "glGetString") == 0) {
        return (void*) glGetString_hook;
    }
    if (strcmp(procname, "glGetStringi") == 0) {
        return (void*) glGetStringi_hook;
    }
    if (strcmp(procname, "glShaderSource") == 0) {
        return (void*) glShaderSource_hook;
    }

    typedef void* (*eglGetProcAddress_pfn)(const char*);
    static eglGetProcAddress_pfn real_eglGetProcAddress = NULL;
    if (!real_eglGetProcAddress) {
        real_eglGetProcAddress = (eglGetProcAddress_pfn) dlsym(RTLD_DEFAULT, "eglGetProcAddress");
        if (!real_eglGetProcAddress) {
            real_eglGetProcAddress = (eglGetProcAddress_pfn) dlsym(RTLD_NEXT, "eglGetProcAddress");
        }
    }
    if (real_eglGetProcAddress) {
        return real_eglGetProcAddress(procname);
    }
    return NULL;
}

static jlong ndlopen_bugfix(__attribute__((unused)) JNIEnv *env,
                     __attribute__((unused)) jclass class,
                     jlong filename_ptr,
                     jint jmode) {
    const char* filename = (const char*) filename_ptr;

    // Oveeride vulkan loading to let us load vulkan ourselves
    if(strstr(filename, "libvulkan.so") == filename) {
        printf("LWJGL linkerhook: replacing load for libvulkan.so with custom driver\n");
        return (jlong) pojavexec_loadVulkanDriver();
    }
    // Load renderer using egl_acquire
    if(strstr(filename, "libGLFear.so") == filename) {
        printf("LWJGL linkerhook: replacing OpenGL with renderspec driver\n");
        const pojavexec_renderspec_t *rspec = pojavexec_getRenderSpec();
        return (jlong) rspec->egl_acquire(rspec->egl_path);
    }

    // This hook also serves the task of mitigating a bug: the idea is that since, on Android 10 and
    // earlier, the linker doesn't really do namespace nesting.
    // It is not a problem as most of the libraries are in the launcher path, but when you try to run
    // VulkanMod which loads shaderc outside of the default jni libs directory through this method,
    // it can't load it because the path is not in the allowed paths for the anonymous namesapce.
    // This method fixes the issue by being in libpojavexec, and thus being in the classloader namespace

    int mode = (int)jmode;
    return (jlong) dlopen(filename, mode);
}

static jlong ndlsym_hook(__attribute__((unused)) JNIEnv *env,
                  __attribute__((unused)) jclass class,
                  jlong handle,
                  jlong symbol_ptr) {
    const char* symbol = (const char*) symbol_ptr;
    if (symbol != NULL) {
        if (strcmp(symbol, "eglGetProcAddress") == 0) {
            printf("LWJGL linkerhook: successfully hooked eglGetProcAddress symbol directly\n");
            return (jlong) eglGetProcAddress_hook;
        }
        if (strcmp(symbol, "glGetString") == 0) {
            printf("LWJGL linkerhook: successfully hooked glGetString symbol directly\n");
            return (jlong) glGetString_hook;
        }
        if (strcmp(symbol, "glGetStringi") == 0) {
            printf("LWJGL linkerhook: successfully hooked glGetStringi symbol directly\n");
            return (jlong) glGetStringi_hook;
        }
        if (strcmp(symbol, "glShaderSource") == 0) {
            printf("LWJGL linkerhook: successfully hooked glShaderSource symbol directly\n");
            return (jlong) glShaderSource_hook;
        }
        if (strcmp(symbol, "glMemoryBarrier") == 0 || strcmp(symbol, "glMemoryBarrierEXT") == 0) {
            printf("LWJGL linkerhook: successfully hooked glMemoryBarrier symbol directly\n");
            return (jlong) glMemoryBarrier_stub;
        }
    }

    // Call real dlsym
    return (jlong) dlsym((void*) handle, symbol);
}

/**
 * Install the LWJGL dlopen hook. This allows us to mitigate linker bugs and add custom library overrides.
 */
void installLwjglDlopenHook(JNIEnv *env) {
    LOGI("Installing LWJGL dlopen() and dlsym() hooks");
    jclass dynamicLinkLoader = (*env)->FindClass(env, "org/lwjgl/system/linux/DynamicLinkLoader");
    if(dynamicLinkLoader == NULL) {
        LOGE("Failed to find the target class");
        (*env)->ExceptionClear(env);
        return;
    }
    JNINativeMethod hooks[] = {
            {"ndlopen", "(JI)J", &ndlopen_bugfix},
            {"ndlsym", "(JJ)J", &ndlsym_hook}
    };
    if((*env)->RegisterNatives(env, dynamicLinkLoader, hooks, 2) != 0) {
        LOGE("Failed to register the hooked methods");
        (*env)->ExceptionClear(env);
    }
}
