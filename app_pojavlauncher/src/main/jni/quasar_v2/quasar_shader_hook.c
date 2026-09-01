/*
 * QuasarV2 - Shader Source Hook
 * Intercepts glShaderSource and transpiles desktop GLSL to GLSL ES 320
 *
 * Step 1: Basic implementation - strips noperspective keyword and
 * extension directive. Full glslang->SPIRV-Cross transpilation in Step 2.
 */

#include <GLES3/gl32.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <dlfcn.h>

#define TAG "QuasarV2"
#include <log.h>

/* Function pointer type for real glShaderSource from the GLES driver */
typedef void (*PFN_glShaderSource)(GLuint, GLsizei, const GLchar* const*, const GLint*);
typedef void* (*PFN_eglGetProcAddress)(const char*);

/* Extern reference to the real eglGetProcAddress from quasar_gl_core.c */
extern PFN_eglGetProcAddress real_eglGetProcAddress;

/* Resolve the real glShaderSource from the Mali driver */
static PFN_glShaderSource get_real_glShaderSource() {
    static PFN_glShaderSource real = NULL;
    if (real) return real;

    if (real_eglGetProcAddress) {
        real = (PFN_glShaderSource) real_eglGetProcAddress("glShaderSource");
        if (real) return real;
    }

    void* handle = dlopen("libGLESv3.so", RTLD_LAZY | RTLD_GLOBAL);
    if (handle) {
        real = (PFN_glShaderSource) dlsym(handle, "glShaderSource");
        if (real) return real;
    }

    LOGE("QuasarV2: Failed to resolve real glShaderSource!");
    return NULL;
}

/* ============================================================
 * Shader source preprocessing
 * ============================================================ */

static char* strip_unsupported_glsl(const char* source) {
    if (source == NULL) return NULL;

    size_t src_len = strlen(source);
    if (src_len == 0) return NULL;

    /* Extra buffer size for precision injections if needed */
    char* result = (char*) malloc(src_len + 1024);
    if (result == NULL) return NULL;

    const char* src = source;
    char* dst = result;
    const char* src_end = source + src_len;

    /* Inject GLES ES precision header if shader contains #version or desktop GLSL tag */
    if (strstr(source, "#version") != NULL && strstr(source, "precision highp") == NULL) {
        const char* header = "#version 320 es\nprecision highp float;\nprecision highp int;\nprecision highp sampler2D;\nprecision highp sampler3D;\nprecision highp samplerCube;\n";
        size_t hlen = strlen(header);
        memcpy(dst, header, hlen);
        dst += hlen;

        /* Skip original #version line */
        if (strncmp(src, "#version", 8) == 0) {
            const char* line_end = strchr(src, '\n');
            if (line_end) src = line_end + 1;
        }
    }

    while (src < src_end) {
        /* Strip "noperspective" keyword */
        if (src + 12 <= src_end && strncmp(src, "noperspective", 12) == 0) {
            char next_ch = (src + 12 < src_end) ? src[12] : ' ';
            if (next_ch == ' ' || next_ch == '\t' || next_ch == '\n' || next_ch == '\r') {
                src += 12;
                if (src < src_end && (*src == ' ' || *src == '\t')) src++;
                continue;
            }
        }

        /* Strip unsupported #extension directives */
        if (src + 10 <= src_end && strncmp(src, "#extension", 10) == 0) {
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
                    src = (line_end < src_end) ? line_end + 1 : src_end;
                    continue;
                }
            }
        }

        *dst++ = *src++;
    }

    *dst = '\0';
    return result;
}

/* ============================================================
 * glShaderSource hook
 * ============================================================ */

void quasar_glShaderSource(GLuint shader, GLsizei count, const GLchar* const* string, const GLint* length) {
    static int hook_count = 0;
    PFN_glShaderSource real = get_real_glShaderSource();
    if (!real) {
        LOGE("QuasarV2: No real glShaderSource, cannot upload shader!");
        return;
    }

    if (count == 1 && string != NULL && string[0] != NULL) {
        const char* original = string[0];
        char* stripped = strip_unsupported_glsl(original);

        if (stripped != NULL && strcmp(stripped, original) != 0) {
            const char* new_strings[1] = { stripped };
            int new_length = (int) strlen(stripped);
            real(shader, 1, new_strings, &new_length);
            hook_count++;
            LOGI("QuasarV2: Shader #%d transpiled (stripped noperspective, %zu->%zu bytes)",
                 hook_count, strlen(original), strlen(stripped));
        } else {
            real(shader, count, string, length);
        }

        if (stripped) free(stripped);
    } else if (count > 0 && count <= 64 && string != NULL) {
        const char* new_strings[64];
        char* stripped_ptrs[64];
        int modified = 0;
        int new_lengths[64];

        for (int i = 0; i < count; i++) {
            stripped_ptrs[i] = NULL;
            if (string[i] != NULL) {
                stripped_ptrs[i] = strip_unsupported_glsl(string[i]);
                if (stripped_ptrs[i]) {
                    new_strings[i] = stripped_ptrs[i];
                    new_lengths[i] = (int) strlen(stripped_ptrs[i]);
                    if (strcmp(stripped_ptrs[i], string[i]) != 0) modified = 1;
                } else {
                    new_strings[i] = string[i];
                    new_lengths[i] = length ? length[i] : 0;
                }
            } else {
                new_strings[i] = NULL;
                new_lengths[i] = 0;
            }
        }

        if (modified) {
            real(shader, count, new_strings, new_lengths);
            LOGI("QuasarV2: Multi-string shader transpiled (%d strings)", count);
        } else {
            real(shader, count, string, length);
        }

        for (int i = 0; i < count; i++)
            if (stripped_ptrs[i]) free(stripped_ptrs[i]);
    } else {
        real(shader, count, string, length);
    }
}
