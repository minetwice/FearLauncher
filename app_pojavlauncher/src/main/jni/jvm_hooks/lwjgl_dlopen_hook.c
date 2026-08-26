//
// Created by maks on 06.01.2025.
//

#include "jvm_hooks.h"

#include <android/api-level.h>

#include <dlfcn.h>
#include <string.h>
#include <stdlib.h>
#include <ctype.h>

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

typedef const unsigned char* (*glGetStringi_pfn)(unsigned int, unsigned int);
static const unsigned char* glGetStringi_hook(unsigned int name, unsigned int index) {
    if (name == GL_EXTENSIONS) {
        static const char* fakeExt = "GL_ARB_direct_state_access GL_ARB_buffer_storage GL_ARB_shader_image_load_store GL_NV_conditional_render GL_EXT_gpu_shader4 GL_EXT_texture_buffer GL_EXT_texture_cube_map_array GL_OES_EGL_image_external_essl3 GL_ARB_shader_texture_lod GL_ARB_shader_objects GL_ARB_vertex_shader GL_ARB_fragment_shader GL_EXT_blend_equation_separate GL_EXT_geometry_shader4 GL_EXT_gpu_program_parameters GL_ARB_instanced_arrays GL_ARB_draw_instanced";
        // Return empty for high indices to avoid infinite extension loops
        if (index > 0) return (const unsigned char*)"";
        return (const unsigned char*)fakeExt;
    }
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

/* Desktop-only / Mali-hostile extension names — drop entire #extension lines.
 * Goal: missing extension must NEVER hard-fail the pack; strip and continue. */
static int is_stripped_extension_line(const char* line) {
    static const char* bad[] = {
        "GL_NV_shader_noperspective_interpolation",
        "GL_ARB_shader_texture_lod",
        "GL_EXT_shader_texture_lod",
        "GL_ARB_shader_storage_buffer_object",
        "GL_ARB_shader_image_load_store",
        "GL_ARB_compute_shader",
        "GL_ARB_geometry_shader4",
        "GL_EXT_geometry_shader4",
        "GL_ARB_tessellation_shader",
        "GL_NV_gpu_shader5",
        "GL_ARB_gpu_shader5",
        "GL_ARB_shader_bit_encoding",
        "GL_ARB_shader_subroutine",
        "GL_EXT_shader_image_load_store",
        "GL_ARB_shader_atomic_counters",
        "GL_ARB_shader_draw_parameters",
        "GL_ARB_gpu_shader_fp64",
        "GL_ARB_gpu_shader_int64",
        "GL_AMD_gpu_shader_half_float",
        "GL_EXT_shader_explicit_arithmetic_types",
        "GL_ARB_shading_language_packing",
        "GL_ARB_shader_ballot",
        "GL_ARB_shader_group_vote",
        "GL_KHR_shader_subgroup",
        NULL
    };
    for (int i = 0; bad[i]; i++) {
        if (strstr(line, bad[i]) != NULL) return 1;
    }
    return 0;
}

/**
 * Aggressive mobile GLSL sanitizer for Mali/Adreno GLES.
 * Missing extensions / reserved keywords must not kill the pack —
 * we strip or rewrite so the driver always gets compilable source.
 *
 * - noperspective (13 chars) → /*noperspective*/
 * - drop unsupported #extension lines
 * - inject precision highp (reduces green NaN / TV flicker on Mali)
 */
static char* strip_unsupported_glsl(const char* source) {
    if (source == NULL) return NULL;

    size_t src_len = strlen(source);
    if (src_len == 0) return NULL;

    /* Room for precision inject + comment replacements */
    size_t alloc = src_len + 256;
    char* result = (char*) malloc(alloc);
    if (result == NULL) return NULL;

    const char* src = source;
    char* dst = result;
    const char* src_end = source + src_len;
    int after_version = 0;
    int injected = 0;

    while (src < src_end) {
        /* ---- noperspective keyword (exact 13 chars + non-identifier boundary) ----
         * Previous bug used length 12 → keyword never fully removed → Mali hard-fail. */
        if (src + 13 <= src_end && memcmp(src, "noperspective", 13) == 0) {
            char next_ch = (src + 13 < src_end) ? src[13] : ' ';
            char prev_ch = (src > source) ? *(src - 1) : ' ';
            int boundary =
                (next_ch == ' ' || next_ch == '\t' || next_ch == '\n' || next_ch == '\r' ||
                 next_ch == ',' || next_ch == ';' || next_ch == ')' || next_ch == '(') &&
                (prev_ch == ' ' || prev_ch == '\t' || prev_ch == '\n' || prev_ch == '\r' ||
                 prev_ch == '(' || prev_ch == ',' || src == source);
            if (boundary) {
                const char* repl = "/*noperspective*/";
                size_t rlen = 17;
                if ((size_t)(dst - result) + rlen + (size_t)(src_end - src) + 8 < alloc) {
                    memcpy(dst, repl, rlen);
                    dst += rlen;
                }
                src += 13;
                continue;
            }
        }

        /* ---- #extension lines for desktop-only / Mali-hostile features ---- */
        if (src + 10 <= src_end && memcmp(src, "#extension", 10) == 0) {
            const char* line_end = memchr(src, '\n', (size_t)(src_end - src));
            if (line_end == NULL) line_end = src_end;
            size_t line_len = (size_t)(line_end - src);
            char ext_line[512];
            if (line_len < sizeof(ext_line)) {
                memcpy(ext_line, src, line_len);
                ext_line[line_len] = '\0';
                if (is_stripped_extension_line(ext_line)) {
                    /* Skip entire line so driver never sees it */
                    src = (line_end < src_end) ? line_end + 1 : src_end;
                    continue;
                }
            }
        }

        /* ---- #version → inject highp precision after it (Mali green-pixel / flicker fix) ---- */
        if (!after_version && src + 8 <= src_end && memcmp(src, "#version", 8) == 0) {
            const char* line_end = memchr(src, '\n', (size_t)(src_end - src));
            if (line_end == NULL) {
                size_t rem = (size_t)(src_end - src);
                memcpy(dst, src, rem);
                dst += rem;
                src = src_end;
                break;
            }
            size_t line_len = (size_t)(line_end - src + 1);
            memcpy(dst, src, line_len);
            dst += line_len;
            src = line_end + 1;
            after_version = 1;

            if (!injected) {
                const char* inject =
                    "#ifdef GL_ES\n"
                    "precision highp float;\n"
                    "precision highp int;\n"
                    "#endif\n";
                size_t ilen = strlen(inject);
                if ((size_t)(dst - result) + ilen + (size_t)(src_end - src) + 8 < alloc) {
                    memcpy(dst, inject, ilen);
                    dst += ilen;
                    injected = 1;
                }
            }
            continue;
        }

        *dst++ = *src++;
    }

    *dst = '\0';
    return result;
}

/** Build a null-terminated copy when glShaderSource length[] is provided. */
static char* make_nt_copy(const char* s, int len) {
    if (s == NULL) return NULL;
    size_t n = (len >= 0) ? (size_t)len : strlen(s);
    char* buf = (char*) malloc(n + 1);
    if (!buf) return NULL;
    memcpy(buf, s, n);
    buf[n] = '\0';
    return buf;
}

/**
 * Hooked glShaderSource — always sanitize before the driver sees the source.
 * Missing extensions / reserved keywords are stripped so packs keep running
 * on Mali/Adreno instead of hard-failing the pipeline.
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
        char* owned = NULL;
        const char* original = string[0];
        if (length != NULL && length[0] >= 0) {
            owned = make_nt_copy(string[0], length[0]);
            if (owned) original = owned;
        }
        char* stripped = strip_unsupported_glsl(original);
        if (stripped != NULL) {
            const char* new_strings[1] = { stripped };
            int new_length = (int) strlen(stripped);
            real_glShaderSource(shader, 1, new_strings, &new_length);
            if (strcmp(stripped, original) != 0) {
                LOGI("glShaderSource_hook: sanitized shader (in=%zu out=%zu)",
                     strlen(original), (size_t)new_length);
            }
            free(stripped);
        } else {
            real_glShaderSource(shader, count, string, length);
        }
        if (owned) free(owned);
    } else if (count > 0 && count <= 64 && string != NULL) {
        const char* new_strings[64];
        char* stripped_ptrs[64];
        char* owned_ptrs[64];
        int new_lengths[64];
        int modified = 0;

        for (unsigned int i = 0; i < count; i++) {
            stripped_ptrs[i] = NULL;
            owned_ptrs[i] = NULL;
            if (string[i] != NULL) {
                const char* src = string[i];
                if (length != NULL && length[i] >= 0) {
                    owned_ptrs[i] = make_nt_copy(string[i], length[i]);
                    if (owned_ptrs[i]) src = owned_ptrs[i];
                }
                stripped_ptrs[i] = strip_unsupported_glsl(src);
                if (stripped_ptrs[i] != NULL) {
                    new_strings[i] = stripped_ptrs[i];
                    new_lengths[i] = (int) strlen(stripped_ptrs[i]);
                    if (strcmp(stripped_ptrs[i], src) != 0) modified = 1;
                } else {
                    new_strings[i] = string[i];
                    new_lengths[i] = (length && length[i] >= 0) ? length[i] : (int) strlen(string[i]);
                }
            } else {
                new_strings[i] = NULL;
                new_lengths[i] = 0;
            }
        }

        real_glShaderSource(shader, count, new_strings, new_lengths);
        if (modified) {
            LOGI("glShaderSource_hook: sanitized multi-string shader (%u strings)", count);
        }

        for (unsigned int i = 0; i < count; i++) {
            if (stripped_ptrs[i] != NULL) free(stripped_ptrs[i]);
            if (owned_ptrs[i] != NULL) free(owned_ptrs[i]);
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
                  jlong name_ptr) {
    const char* symbol = (const char*) name_ptr;
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
