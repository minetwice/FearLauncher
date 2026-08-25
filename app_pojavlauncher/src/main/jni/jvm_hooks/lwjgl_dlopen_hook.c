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

static void* g_ltw_handle = NULL;

static void* get_gl_proc(const char* name) {
    void* proc = NULL;
    if (g_ltw_handle != NULL) {
        proc = dlsym(g_ltw_handle, name);
    }
    if (!proc) {
        proc = dlsym(RTLD_DEFAULT, name);
    }
    if (!proc) {
        proc = dlsym(RTLD_NEXT, name);
    }
    return proc;
}

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

static inline int is_word_char(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || (c == '_');
}

static int is_ltw_debug_enabled(void) {
    const char* env = getenv("LTW_DEBUG");
    return (env && strcmp(env, "1") == 0);
}

static void dump_shader_if_requested(const char* prefix, const char* source) {
    const char* dump_env = getenv("LTW_SHADER_DUMP");
    if (!dump_env || strcmp(dump_env, "1") != 0) return;

    static int counter = 0;
    counter++;

    const char* cache_dir = getenv("MESA_GLSL_CACHE_DIR");
    if (!cache_dir) cache_dir = getenv("MOD_ANDROID_RUNTIME");
    if (!cache_dir) cache_dir = "/sdcard";

    char path[512];
    snprintf(path, sizeof(path), "%s/quasar_shader_%04d_%s.glsl", cache_dir, counter, prefix);
    FILE* f = fopen(path, "w");
    if (f) {
        fputs(source, f);
        fclose(f);
    }
}

/**
 * Generic ES-compatibility shader patcher.
 * Applied to every shader before forwarding to driver.
 * Preserves line numbers, sanitizes unsupported extensions, rewrites noperspective,
 * converts desktop sampler functions to ES, and injects highp precision where needed.
 */
static char* patch_es_compat_glsl(const char* source) {
    if (source == NULL) return NULL;

    size_t src_len = strlen(source);
    if (src_len == 0) return NULL;

    int debug = is_ltw_debug_enabled();

    size_t buf_capacity = src_len + 512;
    char* result = (char*) malloc(buf_capacity);
    if (result == NULL) return NULL;

    memcpy(result, source, src_len + 1);

    int modified = 0;

    // 1. Sanitize unsupported #extension directives into //extension comments
    static const char* const unsupported_exts[] = {
        "GL_NV_shader_noperspective_interpolation",
        "GL_ARB_shader_texture_lod",
        "GL_EXT_gpu_shader4",
        "GL_ARB_gpu_shader5",
        "GL_ARB_draw_instanced",
        "GL_ARB_explicit_attrib_location",
        "GL_ARB_shader_draw_parameters",
        "GL_ARB_shading_language_420pack",
        "GL_ARB_bindless_texture",
        "GL_ARB_texture_rectangle",
        NULL
    };

    char* line = result;
    while (line && *line) {
        char* line_end = strchr(line, '\n');
        char* ext_pos = strstr(line, "#extension");
        if (ext_pos && (!line_end || ext_pos < line_end)) {
            for (int i = 0; unsupported_exts[i] != NULL; i++) {
                const char* ext_name = unsupported_exts[i];
                char* found = strstr(ext_pos, ext_name);
                if (found && (!line_end || found < line_end)) {
                    ext_pos[0] = '/';
                    ext_pos[1] = '/';
                    modified = 1;
                    if (debug) {
                        LOGI("LTW_DEBUG: Sanitized extension directive %s", ext_name);
                    }
                    break;
                }
            }
        }
        line = line_end ? line_end + 1 : NULL;
    }

    // 2. Replace 'noperspective' qualifier with 13 spaces
    char* p = result;
    while ((p = strstr(p, "noperspective")) != NULL) {
        char prev = (p > result) ? p[-1] : ' ';
        char next = p[13];
        if (!is_word_char(prev) && !is_word_char(next)) {
            memset(p, ' ', 13);
            modified = 1;
            if (debug) {
                LOGI("LTW_DEBUG: Replaced noperspective qualifier with spaces");
            }
            p += 13;
        } else {
            p += 1;
        }
    }

    // 3. Desktop GLSL -> GLES function renames (padded with spaces to preserve length)
    typedef struct {
        const char* name;
        size_t len;
        const char* replacement;
    } FuncRename;

    static const FuncRename renames[] = {
        {"texture2DProjLod", 16, "textureProjLod  "},
        {"texture2DLod",     12, "textureLod  "},
        {"texture3DLod",     12, "textureLod  "},
        {"textureCubeLod",   14, "textureLod    "},
        {"texture2DGradARB", 16, "textureGrad   "},
        {"texture2DGrad",    13, "textureGrad  "},
        {"texture2DProj",    13, "textureProj "},
        {"texture2D",        9,  "texture  "},
        {"textureCube",      11, "texture   "},
        {"texture3D",        9,  "texture  "},
        {"texture1D",        9,  "texture  "},
        {"shadow2DProj",     12, "textureProj "},
        {"shadow2D",         8,  "texture "},
        {NULL, 0, NULL}
    };

    for (int i = 0; renames[i].name != NULL; i++) {
        const FuncRename* r = &renames[i];
        p = result;
        while ((p = strstr(p, r->name)) != NULL) {
            char prev = (p > result) ? p[-1] : ' ';
            char next = p[r->len];
            if (!is_word_char(prev) && !is_word_char(next)) {
                memcpy(p, r->replacement, r->len);
                modified = 1;
                if (debug) {
                    LOGI("LTW_DEBUG: Renamed function %s -> %s", r->name, r->replacement);
                }
                p += r->len;
            } else {
                p += 1;
            }
        }
    }

    // 4. Upgrade #version directive to #version 320 es if desktop #version exists
    char* ver = strstr(result, "#version");
    if (ver) {
        char* line_end = strchr(ver, '\n');
        if (line_end) {
            size_t old_line_len = line_end - ver;
            const char* new_ver = "#version 320 es";
            size_t new_ver_len = strlen(new_ver);
            if (old_line_len != new_ver_len || strncmp(ver, new_ver, new_ver_len) != 0) {
                size_t tail_len = strlen(line_end);
                memmove(ver + new_ver_len, line_end, tail_len + 1);
                memcpy(ver, new_ver, new_ver_len);
                modified = 1;
                if (debug) {
                    LOGI("LTW_DEBUG: Rewrote version directive to #version 320 es");
                }
            }
        }
    }

    // 5. Synthesis of compat builtins & fragment outputs (gl_FragData / gl_FragColor / ftransform)
    int is_frag = (strstr(result, "gl_FragColor") != NULL ||
                   strstr(result, "gl_FragData") != NULL ||
                   strstr(result, "out vec4") != NULL ||
                   strstr(result, "out highp vec4") != NULL ||
                   strstr(result, "out mediump vec4") != NULL ||
                   strstr(result, "out lowp vec4") != NULL);

    if (is_frag) {
        // Handle gl_FragData[0..7] -> ltw_FragData0..7
        char injected_decls[512] = "";
        for (int i = 0; i < 8; i++) {
            char target_data[32];
            snprintf(target_data, sizeof(target_data), "gl_FragData[%d]", i);
            p = result;
            if (strstr(p, target_data) != NULL) {
                char replacement_var[32];
                snprintf(replacement_var, sizeof(replacement_var), "ltw_FragData%d ", i);
                size_t target_len = strlen(target_data);
                while ((p = strstr(p, target_data)) != NULL) {
                    memcpy(p, replacement_var, target_len);
                    modified = 1;
                    p += target_len;
                }
                char decl[64];
                snprintf(decl, sizeof(decl), "layout(location = %d) out vec4 ltw_FragData%d;\n", i, i);
                strcat(injected_decls, decl);
            }
        }

        // Handle gl_FragColor -> ltw_FragColor
        if (strstr(result, "gl_FragColor") != NULL) {
            p = result;
            while ((p = strstr(p, "gl_FragColor")) != NULL) {
                memcpy(p, "ltw_FragColor", 12);
                modified = 1;
                p += 12;
            }
            strcat(injected_decls, "layout(location = 0) out vec4 ltw_FragColor;\n");
        }

        // Precision injection for Fragment Shaders
        int has_precision = (strstr(result, "precision highp float") != NULL ||
                             strstr(result, "precision mediump float") != NULL ||
                             strstr(result, "precision lowp float") != NULL);

        if (!has_precision) {
            strcat(injected_decls, "#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\nprecision highp int;\n#endif\n");
        }

        if (injected_decls[0] != '\0') {
            ver = strstr(result, "#version");
            if (ver) {
                char* line_end = strchr(ver, '\n');
                if (line_end) {
                    size_t decls_len = strlen(injected_decls);
                    size_t tail_len = strlen(line_end);
                    memmove(line_end + decls_len, line_end, tail_len + 1);
                    memcpy(line_end, injected_decls, decls_len);
                    modified = 1;
                    if (debug) {
                        LOGI("LTW_DEBUG: Injected fragment output declarations & precision statement");
                    }
                }
            }
        }
    } else {
        // Vertex Shader synthesis (ftransform, gl_MultiTexCoord, gl_TextureMatrix, gl_ModelViewMatrix, gl_ProjectionMatrix)
        p = result;
        while ((p = strstr(p, "ftransform()")) != NULL) {
            char prev = (p > result) ? p[-1] : ' ';
            char next = p[12];
            if (!is_word_char(prev) && !is_word_char(next)) {
                memcpy(p, "gl_Position ", 12);
                modified = 1;
                p += 12;
            } else {
                p += 1;
            }
        }

        char injected_vert_decls[512] = "";
        if (strstr(result, "gl_MultiTexCoord0") != NULL && strstr(result, "in vec4 gl_MultiTexCoord0") == NULL) {
            strcat(injected_vert_decls, "in vec4 gl_MultiTexCoord0;\n");
        }
        if (strstr(result, "gl_MultiTexCoord1") != NULL && strstr(result, "in vec4 gl_MultiTexCoord1") == NULL) {
            strcat(injected_vert_decls, "in vec4 gl_MultiTexCoord1;\n");
        }
        if (strstr(result, "gl_TextureMatrix") != NULL && strstr(result, "uniform mat4 gl_TextureMatrix") == NULL) {
            strcat(injected_vert_decls, "uniform mat4 gl_TextureMatrix[8];\n");
        }
        if (strstr(result, "gl_ModelViewMatrix") != NULL && strstr(result, "uniform mat4 gl_ModelViewMatrix") == NULL) {
            strcat(injected_vert_decls, "uniform mat4 gl_ModelViewMatrix;\n");
        }
        if (strstr(result, "gl_ProjectionMatrix") != NULL && strstr(result, "uniform mat4 gl_ProjectionMatrix") == NULL) {
            strcat(injected_vert_decls, "uniform mat4 gl_ProjectionMatrix;\n");
        }

        if (injected_vert_decls[0] != '\0') {
            ver = strstr(result, "#version");
            if (ver) {
                char* line_end = strchr(ver, '\n');
                if (line_end) {
                    size_t decls_len = strlen(injected_vert_decls);
                    size_t tail_len = strlen(line_end);
                    memmove(line_end + decls_len, line_end, tail_len + 1);
                    memcpy(line_end, injected_vert_decls, decls_len);
                    modified = 1;
                    if (debug) {
                        LOGI("LTW_DEBUG: Injected vertex attribute & matrix uniform declarations");
                    }
                }
            }
        }
    }

    if (modified) {
        dump_shader_if_requested("pre_patch", source);
        dump_shader_if_requested("post_patch", result);
    }

    return result;
}

/**
 * Hooked glShaderSource that applies ES-compatibility patching before compilation.
 */
typedef void (*glShaderSource_pfn)(unsigned int shader, unsigned int count, const char* const* string, const int* length);

static void glShaderSource_hook(unsigned int shader, unsigned int count, const char* const* string, const int* length) {
    static glShaderSource_pfn real_glShaderSource = NULL;
    if (!real_glShaderSource) {
        real_glShaderSource = (glShaderSource_pfn) get_gl_proc("glShaderSource");
    }
    if (!real_glShaderSource) {
        LOGE("glShaderSource_hook: real glShaderSource not found!");
        return;
    }

    if (count <= 0 || string == NULL) {
        real_glShaderSource(shader, count, string, length);
        return;
    }

    // Concatenate input strings into single source buffer
    size_t total_len = 0;
    for (unsigned int i = 0; i < count; i++) {
        if (string[i]) {
            total_len += (length && length[i] >= 0) ? (size_t)length[i] : strlen(string[i]);
        }
    }

    char* full_source = (char*) malloc(total_len + 1);
    if (full_source == NULL) {
        real_glShaderSource(shader, count, string, length);
        return;
    }

    size_t offset = 0;
    for (unsigned int i = 0; i < count; i++) {
        if (string[i]) {
            size_t len = (length && length[i] >= 0) ? (size_t)length[i] : strlen(string[i]);
            memcpy(full_source + offset, string[i], len);
            offset += len;
        }
    }
    full_source[offset] = '\0';

    char* patched = patch_es_compat_glsl(full_source);
    int total_len_int = (int) total_len;
    if (patched != NULL) {
        const char* patched_ptrs[1] = { patched };
        int patched_len = (int) strlen(patched);
        real_glShaderSource(shader, 1, patched_ptrs, &patched_len);
        free(patched);
    } else {
        const char* full_ptrs[1] = { full_source };
        real_glShaderSource(shader, 1, full_ptrs, &total_len_int);
    }

    free(full_source);
}

#define GL_FRAMEBUFFER_SRGB 0x8DB9

static int g_srgb_enabled = 0;

static void glEnable_hook(unsigned int cap) {
    if (cap == GL_FRAMEBUFFER_SRGB) {
        g_srgb_enabled = 1;
    }
    typedef void (*glEnable_pfn)(unsigned int);
    static glEnable_pfn real_glEnable = NULL;
    if (!real_glEnable) real_glEnable = (glEnable_pfn) get_gl_proc("glEnable");
    if (real_glEnable) {
        real_glEnable(cap);
    }
}

static void glDisable_hook(unsigned int cap) {
    if (cap == GL_FRAMEBUFFER_SRGB) {
        g_srgb_enabled = 0;
    }
    typedef void (*glDisable_pfn)(unsigned int);
    static glDisable_pfn real_glDisable = NULL;
    if (!real_glDisable) real_glDisable = (glDisable_pfn) get_gl_proc("glDisable");
    if (real_glDisable) {
        real_glDisable(cap);
    }
}

static unsigned char glIsEnabled_hook(unsigned int cap) {
    if (cap == GL_FRAMEBUFFER_SRGB) {
        return (unsigned char) g_srgb_enabled;
    }
    typedef unsigned char (*glIsEnabled_pfn)(unsigned int);
    static glIsEnabled_pfn real_glIsEnabled = NULL;
    if (!real_glIsEnabled) real_glIsEnabled = (glIsEnabled_pfn) get_gl_proc("glIsEnabled");
    if (real_glIsEnabled) {
        return real_glIsEnabled(cap);
    }
    return 0;
}

static unsigned int eglSwapBuffers_hook(void* dpy, void* surface) {
    typedef unsigned int (*eglSwapBuffers_pfn)(void*, void*);
    static eglSwapBuffers_pfn real_eglSwapBuffers = NULL;
    if (!real_eglSwapBuffers) real_eglSwapBuffers = (eglSwapBuffers_pfn) get_gl_proc("eglSwapBuffers");
    if (real_eglSwapBuffers) {
        return real_eglSwapBuffers(dpy, surface);
    }
    return 0;
}

static inline unsigned int preserve_format(unsigned int format) {
    switch (format) {
        case 0x881A: // GL_RGBA16F
        case 0x8814: // GL_RGBA32F
        case 0x881B: // GL_RGB16F
        case 0x8815: // GL_RGB32F
        case 0x822F: // GL_RG16F
        case 0x8230: // GL_RG32F
        case 0x822D: // GL_R16F
        case 0x822E: // GL_R32F
        case 0x8C3A: // GL_R11F_G11F_B10F
        case 0x8C43: // GL_SRGB8_ALPHA8
        case 0x8C41: // GL_SRGB8
        case 0x8051: // GL_RGB8
        case 0x8058: // GL_RGBA8
        case 0x81A6: // GL_DEPTH_COMPONENT24
        case 0x8CAC: // GL_DEPTH_COMPONENT32F
        case 0x88F0: // GL_DEPTH24_STENCIL8
        case 0x8CAD: // GL_DEPTH32F_STENCIL8
            return format;
        default:
            return format;
    }
}

static void glTexImage2D_hook(unsigned int target, int level, int internalformat, int width, int height, int border, unsigned int format, unsigned int type, const void* pixels) {
    int preserved_fmt = (int) preserve_format((unsigned int) internalformat);
    typedef void (*glTexImage2D_pfn)(unsigned int, int, int, int, int, int, unsigned int, unsigned int, const void*);
    static glTexImage2D_pfn real_glTexImage2D = NULL;
    if (!real_glTexImage2D) real_glTexImage2D = (glTexImage2D_pfn) get_gl_proc("glTexImage2D");
    if (real_glTexImage2D) {
        real_glTexImage2D(target, level, preserved_fmt, width, height, border, format, type, pixels);
    }
}

static void glTexStorage2D_hook(unsigned int target, int levels, unsigned int internalformat, int width, int height) {
    unsigned int preserved_fmt = preserve_format(internalformat);
    typedef void (*glTexStorage2D_pfn)(unsigned int, int, unsigned int, int, int);
    static glTexStorage2D_pfn real_glTexStorage2D = NULL;
    if (!real_glTexStorage2D) real_glTexStorage2D = (glTexStorage2D_pfn) get_gl_proc("glTexStorage2D");
    if (real_glTexStorage2D) {
        real_glTexStorage2D(target, levels, preserved_fmt, width, height);
    }
}

static void glRenderbufferStorage_hook(unsigned int target, unsigned int internalformat, int width, int height) {
    unsigned int preserved_fmt = preserve_format(internalformat);
    typedef void (*glRenderbufferStorage_pfn)(unsigned int, unsigned int, int, int);
    static glRenderbufferStorage_pfn real_glRenderbufferStorage = NULL;
    if (!real_glRenderbufferStorage) real_glRenderbufferStorage = (glRenderbufferStorage_pfn) get_gl_proc("glRenderbufferStorage");
    if (real_glRenderbufferStorage) {
        real_glRenderbufferStorage(target, preserved_fmt, width, height);
    }
}

static void glFramebufferTexture2D_hook(unsigned int target, unsigned int attachment, unsigned int textarget, unsigned int texture, int level) {
    typedef void (*glFramebufferTexture2D_pfn)(unsigned int, unsigned int, unsigned int, unsigned int, int);
    static glFramebufferTexture2D_pfn real_glFramebufferTexture2D = NULL;
    if (!real_glFramebufferTexture2D) real_glFramebufferTexture2D = (glFramebufferTexture2D_pfn) get_gl_proc("glFramebufferTexture2D");
    if (real_glFramebufferTexture2D) {
        real_glFramebufferTexture2D(target, attachment, textarget, texture, level);
    }
}

static void glDrawBuffers_hook(int n, const unsigned int* bufs) {
    typedef void (*glDrawBuffers_pfn)(int, const unsigned int*);
    static glDrawBuffers_pfn real_glDrawBuffers = NULL;
    if (!real_glDrawBuffers) real_glDrawBuffers = (glDrawBuffers_pfn) get_gl_proc("glDrawBuffers");
    if (real_glDrawBuffers) {
        real_glDrawBuffers(n, bufs);
    }
}

static void glReadPixels_hook(int x, int y, int width, int height, unsigned int format, unsigned int type, void* pixels) {
    typedef void (*glReadPixels_pfn)(int, int, int, int, unsigned int, unsigned int, void*);
    static glReadPixels_pfn real_glReadPixels = NULL;
    if (!real_glReadPixels) real_glReadPixels = (glReadPixels_pfn) get_gl_proc("glReadPixels");
    if (real_glReadPixels) {
        real_glReadPixels(x, y, width, height, format, type, pixels);
    }
}

static void glBlitFramebuffer_hook(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, unsigned int mask, unsigned int filter) {
    typedef void (*glBlitFramebuffer_pfn)(int, int, int, int, int, int, int, int, unsigned int, unsigned int);
    static glBlitFramebuffer_pfn real_glBlitFramebuffer = NULL;
    if (!real_glBlitFramebuffer) real_glBlitFramebuffer = (glBlitFramebuffer_pfn) get_gl_proc("glBlitFramebuffer");
    if (real_glBlitFramebuffer) {
        real_glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }
}

static void* eglGetProcAddress_hook(const char* procname) {
    if (procname == NULL) return NULL;
    if (strcmp(procname, "eglGetProcAddress") == 0) {
        return (void*) eglGetProcAddress_hook;
    }
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
    if (strcmp(procname, "glEnable") == 0) return (void*) glEnable_hook;
    if (strcmp(procname, "glDisable") == 0) return (void*) glDisable_hook;
    if (strcmp(procname, "glIsEnabled") == 0) return (void*) glIsEnabled_hook;
    if (strcmp(procname, "eglSwapBuffers") == 0) return (void*) eglSwapBuffers_hook;
    if (strcmp(procname, "glTexImage2D") == 0) return (void*) glTexImage2D_hook;
    if (strcmp(procname, "glTexStorage2D") == 0) return (void*) glTexStorage2D_hook;
    if (strcmp(procname, "glRenderbufferStorage") == 0 || strcmp(procname, "glRenderbufferStorageEXT") == 0) return (void*) glRenderbufferStorage_hook;
    if (strcmp(procname, "glFramebufferTexture2D") == 0 || strcmp(procname, "glFramebufferTexture2DEXT") == 0) return (void*) glFramebufferTexture2D_hook;
    if (strcmp(procname, "glDrawBuffers") == 0 || strcmp(procname, "glDrawBuffersARB") == 0) return (void*) glDrawBuffers_hook;
    if (strcmp(procname, "glReadPixels") == 0) return (void*) glReadPixels_hook;
    if (strcmp(procname, "glBlitFramebuffer") == 0 || strcmp(procname, "glBlitFramebufferEXT") == 0) return (void*) glBlitFramebuffer_hook;

    typedef void* (*eglGetProcAddress_pfn)(const char*);
    static eglGetProcAddress_pfn real_eglGetProcAddress = NULL;
    if (!real_eglGetProcAddress) {
        real_eglGetProcAddress = (eglGetProcAddress_pfn) get_gl_proc("eglGetProcAddress");
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
        void* handle = rspec->egl_acquire(rspec->egl_path);
        if (handle != NULL) {
            g_ltw_handle = handle;
        }
        return (jlong) handle;
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
        void* hooked_proc = eglGetProcAddress_hook(symbol);
        if (hooked_proc != NULL) {
            return (jlong) hooked_proc;
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
