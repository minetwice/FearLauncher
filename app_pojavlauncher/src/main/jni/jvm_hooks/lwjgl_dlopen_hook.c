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

/* DSA Emulation helper function prototypes */
typedef void (*glGenTextures_pfn)(int, unsigned int*);
typedef void (*glBindTexture_pfn)(unsigned int, unsigned int);
typedef void (*glGenFramebuffers_pfn)(int, unsigned int*);
typedef void (*glBindFramebuffer_pfn)(unsigned int, unsigned int);
typedef void (*glGenBuffers_pfn)(int, unsigned int*);
typedef void (*glBindBuffer_pfn)(unsigned int, unsigned int);
typedef void (*glGenVertexArrays_pfn)(int, unsigned int*);
typedef void (*glBindVertexArray_pfn)(unsigned int);
typedef void (*glGenRenderbuffers_pfn)(int, unsigned int*);

static void glCreateTextures_stub(unsigned int target, int n, unsigned int* textures) {
    static glGenTextures_pfn p_gen = NULL;
    if (!p_gen) p_gen = (glGenTextures_pfn) dlsym(RTLD_DEFAULT, "glGenTextures");
    if (!p_gen) p_gen = (glGenTextures_pfn) dlsym(RTLD_NEXT, "glGenTextures");
    if (p_gen && textures) p_gen(n, textures);
}

static void glBindTextureUnit_stub(unsigned int unit, unsigned int texture) {
    typedef void (*glActiveTexture_pfn)(unsigned int);
    static glActiveTexture_pfn p_act = NULL;
    static glBindTexture_pfn p_bind = NULL;
    if (!p_act) p_act = (glActiveTexture_pfn) dlsym(RTLD_DEFAULT, "glActiveTexture");
    if (!p_act) p_act = (glActiveTexture_pfn) dlsym(RTLD_NEXT, "glActiveTexture");
    if (!p_bind) p_bind = (glBindTexture_pfn) dlsym(RTLD_DEFAULT, "glBindTexture");
    if (!p_bind) p_bind = (glBindTexture_pfn) dlsym(RTLD_NEXT, "glBindTexture");
    if (p_act && p_bind) {
        p_act(0x84C0 + unit);
        p_bind(0x0DE1, texture); // GL_TEXTURE_2D
    }
}

static void glCreateFramebuffers_stub(int n, unsigned int* framebuffers) {
    static glGenFramebuffers_pfn p_gen = NULL;
    if (!p_gen) p_gen = (glGenFramebuffers_pfn) dlsym(RTLD_DEFAULT, "glGenFramebuffers");
    if (!p_gen) p_gen = (glGenFramebuffers_pfn) dlsym(RTLD_NEXT, "glGenFramebuffers");
    if (p_gen && framebuffers) p_gen(n, framebuffers);
}

static void glCreateBuffers_stub(int n, unsigned int* buffers) {
    static glGenBuffers_pfn p_gen = NULL;
    if (!p_gen) p_gen = (glGenBuffers_pfn) dlsym(RTLD_DEFAULT, "glGenBuffers");
    if (!p_gen) p_gen = (glGenBuffers_pfn) dlsym(RTLD_NEXT, "glGenBuffers");
    if (p_gen && buffers) p_gen(n, buffers);
}

static void glCreateVertexArrays_stub(int n, unsigned int* arrays) {
    static glGenVertexArrays_pfn p_gen = NULL;
    if (!p_gen) p_gen = (glGenVertexArrays_pfn) dlsym(RTLD_DEFAULT, "glGenVertexArrays");
    if (!p_gen) p_gen = (glGenVertexArrays_pfn) dlsym(RTLD_NEXT, "glGenVertexArrays");
    if (p_gen && arrays) p_gen(n, arrays);
}

static void glCreateRenderbuffers_stub(int n, unsigned int* renderbuffers) {
    static glGenRenderbuffers_pfn p_gen = NULL;
    if (!p_gen) p_gen = (glGenRenderbuffers_pfn) dlsym(RTLD_DEFAULT, "glGenRenderbuffers");
    if (!p_gen) p_gen = (glGenRenderbuffers_pfn) dlsym(RTLD_NEXT, "glGenRenderbuffers");
    if (p_gen && renderbuffers) p_gen(n, renderbuffers);
}

static void glNamedBufferData_stub(unsigned int buffer, long size, const void* data, unsigned int usage) {
    typedef void (*glBufferData_pfn)(unsigned int, long, const void*, unsigned int);
    static glBindBuffer_pfn p_bind = NULL;
    static glBufferData_pfn p_data = NULL;
    if (!p_bind) p_bind = (glBindBuffer_pfn) dlsym(RTLD_DEFAULT, "glBindBuffer");
    if (!p_bind) p_bind = (glBindBuffer_pfn) dlsym(RTLD_NEXT, "glBindBuffer");
    if (!p_data) p_data = (glBufferData_pfn) dlsym(RTLD_DEFAULT, "glBufferData");
    if (!p_data) p_data = (glBufferData_pfn) dlsym(RTLD_NEXT, "glBufferData");
    if (p_bind && p_data) {
        p_bind(0x8892, buffer); // GL_ARRAY_BUFFER
        p_data(0x8892, size, data, usage);
    }
}

static void glTextureParameteri_stub(unsigned int texture, unsigned int pname, int param) {
    static glBindTexture_pfn p_bind = NULL;
    typedef void (*glTexParameteri_pfn)(unsigned int, unsigned int, int);
    static glTexParameteri_pfn p_param = NULL;
    if (!p_bind) p_bind = (glBindTexture_pfn) dlsym(RTLD_DEFAULT, "glBindTexture");
    if (!p_bind) p_bind = (glBindTexture_pfn) dlsym(RTLD_NEXT, "glBindTexture");
    if (!p_param) p_param = (glTexParameteri_pfn) dlsym(RTLD_DEFAULT, "glTexParameteri");
    if (!p_param) p_param = (glTexParameteri_pfn) dlsym(RTLD_NEXT, "glTexParameteri");
    if (p_bind && p_param) {
        p_bind(0x0DE1, texture);
        p_param(0x0DE1, pname, param);
    }
}

static void glTextureStorage2D_stub(unsigned int texture, int levels, unsigned int internalformat, int width, int height) {
    typedef void (*glTexStorage2D_pfn)(unsigned int, int, unsigned int, int, int);
    typedef void (*glTexImage2D_pfn)(unsigned int, int, int, int, int, int, unsigned int, unsigned int, const void*);
    static glBindTexture_pfn p_bind = NULL;
    static glTexStorage2D_pfn p_stor = NULL;
    static glTexImage2D_pfn p_img = NULL;
    if (!p_bind) p_bind = (glBindTexture_pfn) dlsym(RTLD_DEFAULT, "glBindTexture");
    if (!p_bind) p_bind = (glBindTexture_pfn) dlsym(RTLD_NEXT, "glBindTexture");
    if (!p_stor) p_stor = (glTexStorage2D_pfn) dlsym(RTLD_DEFAULT, "glTexStorage2D");
    if (!p_stor) p_stor = (glTexStorage2D_pfn) dlsym(RTLD_NEXT, "glTexStorage2D");
    if (!p_img) p_img = (glTexImage2D_pfn) dlsym(RTLD_DEFAULT, "glTexImage2D");
    if (!p_img) p_img = (glTexImage2D_pfn) dlsym(RTLD_NEXT, "glTexImage2D");

    /* Convert unsized formats to sized internal formats required by GLES glTexStorage2D */
    unsigned int sized_format = internalformat;
    if (internalformat == 0x1908) sized_format = 0x8058; // GL_RGBA -> GL_RGBA8
    else if (internalformat == 0x1907) sized_format = 0x8051; // GL_RGB -> GL_RGB8
    else if (internalformat == 0x1902) sized_format = 0x81A6; // GL_DEPTH_COMPONENT -> GL_DEPTH_COMPONENT24
    else if (internalformat == 0x84F9) sized_format = 0x88F0; // GL_DEPTH_STENCIL -> GL_DEPTH24_STENCIL8

    if (p_bind) p_bind(0x0DE1, texture); // GL_TEXTURE_2D
    if (p_stor) {
        p_stor(0x0DE1, levels, sized_format, width, height);
    } else if (p_img) {
        unsigned int format = 0x1908; // GL_RGBA
        unsigned int type = 0x1401; // GL_UNSIGNED_BYTE
        if (sized_format == 0x1902 || sized_format == 0x81A5 || sized_format == 0x81A6 || sized_format == 0x8C3E) {
            format = 0x1902; type = 0x1405; // GL_DEPTH_COMPONENT, GL_UNSIGNED_INT
        } else if (sized_format == 0x88F0 || sized_format == 0x8CAD) {
            format = 0x84F9; type = 0x84FA; // GL_DEPTH_STENCIL, GL_UNSIGNED_INT_24_8
        }
        p_img(0x0DE1, 0, (int)sized_format, width, height, 0, format, type, NULL);
    }
}

static unsigned int glGetError_stub(void) {
    return 0; // GL_NO_ERROR
}

static void glTextureSubImage2D_stub(unsigned int texture, int level, int xoffset, int yoffset, int width, int height, unsigned int format, unsigned int type, const void* pixels) {
    typedef void (*glTexSubImage2D_pfn)(unsigned int, int, int, int, int, int, unsigned int, unsigned int, const void*);
    static glBindTexture_pfn p_bind = NULL;
    static glTexSubImage2D_pfn p_sub = NULL;
    if (!p_bind) p_bind = (glBindTexture_pfn) dlsym(RTLD_DEFAULT, "glBindTexture");
    if (!p_bind) p_bind = (glBindTexture_pfn) dlsym(RTLD_NEXT, "glBindTexture");
    if (!p_sub) p_sub = (glTexSubImage2D_pfn) dlsym(RTLD_DEFAULT, "glTexSubImage2D");
    if (!p_sub) p_sub = (glTexSubImage2D_pfn) dlsym(RTLD_NEXT, "glTexSubImage2D");
    if (p_bind && p_sub) {
        p_bind(0x0DE1, texture);
        p_sub(0x0DE1, level, xoffset, yoffset, width, height, format, type, pixels);
    }
}

static void glGenerateTextureMipmap_stub(unsigned int texture) {
    typedef void (*glGenerateMipmap_pfn)(unsigned int);
    static glBindTexture_pfn p_bind = NULL;
    static glGenerateMipmap_pfn p_gen = NULL;
    if (!p_bind) p_bind = (glBindTexture_pfn) dlsym(RTLD_DEFAULT, "glBindTexture");
    if (!p_bind) p_bind = (glBindTexture_pfn) dlsym(RTLD_NEXT, "glBindTexture");
    if (!p_gen) p_gen = (glGenerateMipmap_pfn) dlsym(RTLD_DEFAULT, "glGenerateMipmap");
    if (!p_gen) p_gen = (glGenerateMipmap_pfn) dlsym(RTLD_NEXT, "glGenerateMipmap");
    if (p_bind && p_gen) {
        p_bind(0x0DE1, texture);
        p_gen(0x0DE1);
    }
}

static void glNamedBufferSubData_stub(unsigned int buffer, long offset, long size, const void* data) {
    typedef void (*glBufferSubData_pfn)(unsigned int, long, long, const void*);
    static glBindBuffer_pfn p_bind = NULL;
    static glBufferSubData_pfn p_sub = NULL;
    if (!p_bind) p_bind = (glBindBuffer_pfn) dlsym(RTLD_DEFAULT, "glBindBuffer");
    if (!p_bind) p_bind = (glBindBuffer_pfn) dlsym(RTLD_NEXT, "glBindBuffer");
    if (!p_sub) p_sub = (glBufferSubData_pfn) dlsym(RTLD_DEFAULT, "glBufferSubData");
    if (!p_sub) p_sub = (glBufferSubData_pfn) dlsym(RTLD_NEXT, "glBufferSubData");
    if (p_bind && p_sub) {
        p_bind(0x8892, buffer);
        p_sub(0x8892, offset, size, data);
    }
}

static void glNamedFramebufferTexture_stub(unsigned int framebuffer, unsigned int attachment, unsigned int texture, int level) {
    typedef void (*glFramebufferTexture2D_pfn)(unsigned int, unsigned int, unsigned int, unsigned int, int);
    static glBindFramebuffer_pfn p_bind = NULL;
    static glFramebufferTexture2D_pfn p_tex = NULL;
    if (!p_bind) p_bind = (glBindFramebuffer_pfn) dlsym(RTLD_DEFAULT, "glBindFramebuffer");
    if (!p_bind) p_bind = (glBindFramebuffer_pfn) dlsym(RTLD_NEXT, "glBindFramebuffer");
    if (!p_tex) p_tex = (glFramebufferTexture2D_pfn) dlsym(RTLD_DEFAULT, "glFramebufferTexture2D");
    if (!p_tex) p_tex = (glFramebufferTexture2D_pfn) dlsym(RTLD_NEXT, "glFramebufferTexture2D");
    if (p_bind && p_tex) {
        p_bind(0x8D40, framebuffer);
        p_tex(0x8D40, attachment, 0x0DE1, texture, level);
    }
}

static const unsigned char* glGetString_hook(unsigned int name) {
    if (name == GL_VERSION) {
        return (const unsigned char*)"4.6.0 NVIDIA 545.29";
    } else if (name == GL_RENDERER) {
        return (const unsigned char*)"NVIDIA GeForce RTX 4090";
    } else if (name == GL_VENDOR) {
        return (const unsigned char*)"NVIDIA Corporation";
    } else if (name == GL_EXTENSIONS) {
        return (const unsigned char*)"GL_ARB_direct_state_access GL_ARB_buffer_storage GL_ARB_shader_image_load_store GL_NV_conditional_render GL_EXT_gpu_shader4 GL_EXT_texture_buffer GL_EXT_texture_cube_map_array GL_OES_EGL_image_external_essl3 GL_ARB_shader_texture_lod GL_ARB_shader_objects GL_ARB_vertex_shader GL_ARB_fragment_shader GL_EXT_blend_equation_separate GL_EXT_geometry_shader4 GL_EXT_gpu_program_parameters GL_ARB_instanced_arrays GL_ARB_draw_instanced GL_ARB_multi_bind GL_ARB_explicit_attrib_location GL_ARB_separate_shader_objects GL_ARB_get_program_binary GL_ARB_gpu_shader5 GL_ARB_texture_query_levels GL_ARB_texture_gather";
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

static const char* g_quasar_extensions[] = {
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
    "GL_ARB_draw_instanced",
    "GL_ARB_multi_bind",
    "GL_ARB_explicit_attrib_location",
    "GL_ARB_separate_shader_objects",
    "GL_ARB_get_program_binary",
    "GL_ARB_gpu_shader5",
    "GL_ARB_texture_query_levels",
    "GL_ARB_texture_gather"
};
static const unsigned int g_quasar_extensions_count = sizeof(g_quasar_extensions) / sizeof(g_quasar_extensions[0]);

static const unsigned char* glGetStringi_hook(unsigned int name, unsigned int index) {
    if (name == GL_EXTENSIONS) {
        if (index < g_quasar_extensions_count) {
            return (const unsigned char*)g_quasar_extensions[index];
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
        if (src + 12 <= src_end && strncmp(src, "noperspective", 12) == 0) {
            /* Make sure it's a standalone keyword (next char is space/tab/newline) */
            char next_ch = (src + 12 < src_end) ? src[12] : ' ';
            if (next_ch == ' ' || next_ch == '\t' || next_ch == '\n' || next_ch == '\r') {
                /* Skip the keyword and the trailing space */
                src += 12;
                /* Skip one trailing space if present */
                if (src < src_end && (*src == ' ' || *src == '\t')) {
                    src++;
                }
                continue;
            }
        }

        /* Check for unsupported "#extension ..." directives (e.g. GL_NV_shader_noperspective, GL_ARB_gpu_shader5, GL_ARB_explicit_attrib_location, etc.) */
        if (src + 10 <= src_end && strncmp(src, "#extension", 10) == 0) {
            /* Find the end of this line */
            const char* line_end = strchr(src, '\n');
            if (line_end == NULL) line_end = src_end;
            size_t line_len = line_end - src;

            char ext_line[512];
            if (line_len < sizeof(ext_line)) {
                memcpy(ext_line, src, line_len);
                ext_line[line_len] = '\0';
                if (strstr(ext_line, "GL_NV_shader_noperspective") != NULL ||
                    strstr(ext_line, "GL_ARB_gpu_shader5") != NULL ||
                    strstr(ext_line, "GL_ARB_explicit_attrib_location") != NULL ||
                    strstr(ext_line, "GL_ARB_shader_bit_encoding") != NULL ||
                    strstr(ext_line, "GL_ARB_shader_texture_lod") != NULL) {
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

/**
 * Hooked glGetIntegerv that checks for EGL context before calling real GLES.
 * LWJGL calls glGetIntegerv BEFORE eglMakeCurrent, so calling the real
 * Mali GLES function with no context causes "No context is current" crash.
 */
static void glGetIntegerv_hook(unsigned int pname, int* params) {
    if (!params) return;

    /* Handle Desktop GL spoofed queries first to prevent GLES driver errors or crash during Blaze3D init */
    switch (pname) {
        case 0x821B: *params = 4; return;    /* GL_MAJOR_VERSION */
        case 0x821C: *params = 6; return;    /* GL_MINOR_VERSION */
        case 0x821D: *params = g_quasar_extensions_count; return;   /* GL_NUM_EXTENSIONS */
        case 0x821E: *params = 0; return;    /* GL_CONTEXT_FLAGS */
        case 0x9126: *params = 1; return;    /* GL_CONTEXT_PROFILE_MASK (GL_CONTEXT_CORE_PROFILE_BIT) */
        case 0x8B4D: *params = 60; return;   /* GL_MAX_VARYING_FLOATS */
        case 0x8824: *params = 8; return;    /* GL_MAX_DRAW_BUFFERS */
        case 0x8B49: *params = 4096; return;  /* GL_MAX_VERTEX_UNIFORM_COMPONENTS */
        case 0x8B4A: *params = 4096; return;  /* GL_MAX_FRAGMENT_UNIFORM_COMPONENTS */
        case 0x851C: *params = 16; return;    /* GL_MAX_TEXTURE_COORDS */
        case 0x807A: *params = 32; return;    /* GL_MAX_TEXTURE_IMAGE_UNITS */
        case 0x8B4B: *params = 32; return;    /* GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS */
        case 0x8842: *params = 32; return;    /* GL_MAX_TEXTURE_UNITS */
        case 0x84E8: *params = 16; return;    /* GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS */
        case 0x8DFB: *params = 16; return;    /* GL_MAX_VERTEX_OUTPUT_COMPONENTS */
        case 0x8DFC: *params = 16; return;    /* GL_MAX_FRAGMENT_INPUT_COMPONENTS */
        case 0x8B4C: *params = 64; return;    /* GL_MAX_VERTEX_ATTRIBS */
        case 0x8DFD: *params = 64; return;    /* GL_MAX_GEOMETRY_OUTPUT_VERTICES */
        case 0x8A32: *params = 256; return;   /* GL_MAX_GEOMETRY_TOTAL_OUTPUT_COMPONENTS */
        case 0x0D33: *params = 16384; return; /* GL_MAX_TEXTURE_SIZE */
    }

    /* Check if an EGL context is current */
    typedef void* (*eglGetCurrentContext_pfn)(void);
    static eglGetCurrentContext_pfn real_eglGetCurrentContext = NULL;
    if (!real_eglGetCurrentContext) {
        real_eglGetCurrentContext = (eglGetCurrentContext_pfn) dlsym(RTLD_DEFAULT, "eglGetCurrentContext");
        if (!real_eglGetCurrentContext) {
            real_eglGetCurrentContext = (eglGetCurrentContext_pfn) dlsym(RTLD_NEXT, "eglGetCurrentContext");
        }
    }
    void* current_ctx = NULL;
    if (real_eglGetCurrentContext) current_ctx = real_eglGetCurrentContext();

    if (current_ctx != NULL) {
        /* Context is current - call real glGetIntegerv */
        typedef void (*glGetIntegerv_pfn)(unsigned int, int*);
        static glGetIntegerv_pfn real_glGetIntegerv = NULL;
        if (!real_glGetIntegerv) {
            real_glGetIntegerv = (glGetIntegerv_pfn) dlsym(RTLD_DEFAULT, "glGetIntegerv");
            if (!real_glGetIntegerv) {
                real_glGetIntegerv = (glGetIntegerv_pfn) dlsym(RTLD_NEXT, "glGetIntegerv");
            }
        }
        if (real_glGetIntegerv) {
            real_glGetIntegerv(pname, params);
            return;
        }
    }

    /* Fallback if no context or real call failed */
    *params = 0;
    LOGI("glGetIntegerv_hook: No context, returning 0 for pname=0x%x", pname);
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
    if (strcmp(procname, "glGetIntegerv") == 0) {
        return (void*) glGetIntegerv_hook;
    }
    if (strcmp(procname, "glGetError") == 0) {
        return (void*) glGetError_stub;
    }
    if (strcmp(procname, "glCreateTextures") == 0) return (void*) glCreateTextures_stub;
    if (strcmp(procname, "glBindTextureUnit") == 0) return (void*) glBindTextureUnit_stub;
    if (strcmp(procname, "glTextureStorage2D") == 0) return (void*) glTextureStorage2D_stub;
    if (strcmp(procname, "glTextureSubImage2D") == 0) return (void*) glTextureSubImage2D_stub;
    if (strcmp(procname, "glGenerateTextureMipmap") == 0) return (void*) glGenerateTextureMipmap_stub;
    if (strcmp(procname, "glCreateFramebuffers") == 0) return (void*) glCreateFramebuffers_stub;
    if (strcmp(procname, "glNamedFramebufferTexture") == 0) return (void*) glNamedFramebufferTexture_stub;
    if (strcmp(procname, "glCreateBuffers") == 0) return (void*) glCreateBuffers_stub;
    if (strcmp(procname, "glCreateVertexArrays") == 0) return (void*) glCreateVertexArrays_stub;
    if (strcmp(procname, "glCreateRenderbuffers") == 0) return (void*) glCreateRenderbuffers_stub;
    if (strcmp(procname, "glNamedBufferData") == 0) return (void*) glNamedBufferData_stub;
    if (strcmp(procname, "glNamedBufferSubData") == 0) return (void*) glNamedBufferSubData_stub;
    if (strcmp(procname, "glTextureParameteri") == 0) return (void*) glTextureParameteri_stub;

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

    // Handle libflite.so for Mojang Narrator gracefully on Android
    if(filename != NULL && strstr(filename, "libflite.so") != NULL) {
        LOGI("LWJGL linkerhook: Bypassing libflite.so load for Mojang Narrator");
        return (jlong) dlopen(NULL, RTLD_LAZY);
    }

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
        if (strcmp(symbol, "glGetIntegerv") == 0) {
            printf("LWJGL linkerhook: successfully hooked glGetIntegerv symbol directly\n");
            return (jlong) glGetIntegerv_hook;
        }
        if (strcmp(symbol, "glGetError") == 0) {
            printf("LWJGL linkerhook: successfully hooked glGetError symbol directly\n");
            return (jlong) glGetError_stub;
        }
        if (strcmp(symbol, "glCreateTextures") == 0) return (jlong) glCreateTextures_stub;
        if (strcmp(symbol, "glBindTextureUnit") == 0) return (jlong) glBindTextureUnit_stub;
        if (strcmp(symbol, "glTextureStorage2D") == 0) return (jlong) glTextureStorage2D_stub;
        if (strcmp(symbol, "glTextureSubImage2D") == 0) return (jlong) glTextureSubImage2D_stub;
        if (strcmp(symbol, "glGenerateTextureMipmap") == 0) return (jlong) glGenerateTextureMipmap_stub;
        if (strcmp(symbol, "glCreateFramebuffers") == 0) return (jlong) glCreateFramebuffers_stub;
        if (strcmp(symbol, "glNamedFramebufferTexture") == 0) return (jlong) glNamedFramebufferTexture_stub;
        if (strcmp(symbol, "glCreateBuffers") == 0) return (jlong) glCreateBuffers_stub;
        if (strcmp(symbol, "glCreateVertexArrays") == 0) return (jlong) glCreateVertexArrays_stub;
        if (strcmp(symbol, "glCreateRenderbuffers") == 0) return (jlong) glCreateRenderbuffers_stub;
        if (strcmp(symbol, "glNamedBufferData") == 0) return (jlong) glNamedBufferData_stub;
        if (strcmp(symbol, "glNamedBufferSubData") == 0) return (jlong) glNamedBufferSubData_stub;
        if (strcmp(symbol, "glTextureParameteri") == 0) return (jlong) glTextureParameteri_stub;
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
