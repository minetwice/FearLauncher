/**
 * Created by: artDev
 * Copyright (c) 2025 artDev, SerpentSpirale, CADIndie.
 * For use under LGPL-3.0
 *
 * FearLauncher overlay. This is the upstream file with two changes, both marked
 * FEARLAUNCHER. Everything else is byte-for-byte upstream, so the diff against
 * MojoLauncher/LTW stays small and reviewable.
 */

#include "unordered_map/unordered_map.h"
#include "vgpu_shaderconv/shaderconv.h"
#include "glsl_optimizer/src/code/c_wrapper.h"
#include <GLES3/gl3.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include "string_utils.h"
#include "egl.h"
#include "proc.h"

typedef struct {
    GLenum shader_type;
    const GLchar* source;
} shader_info_t;

typedef struct {
    GLuint frag_shader;
    GLchar* colorbindings[MAX_DRAWBUFFERS];
} program_info_t;

/* FEARLAUNCHER: in-process shader translation cache.
 *
 * The convert-and-optimize pass is pure overhead when the same source is uploaded
 * again, and that happens constantly - the game rebuilds its core shaders, mods
 * re-upload theirs on every resource reload, and a resource reload happens on every
 * world join. Keep the last N results in memory, keyed by a hash over the source and
 * everything that can change the output. Deliberately in memory only: nothing is
 * written to disk, so there is no state that can go stale between runs.
 */
#define FEAR_SHADER_CACHE_SLOTS 96

typedef struct {
    unsigned long long key;
    GLchar* source;
} fear_shader_cache_entry_t;

static fear_shader_cache_entry_t fear_shader_cache[FEAR_SHADER_CACHE_SLOTS];
static unsigned fear_shader_cache_next = 0;

static unsigned long long fear_shader_key(const GLchar* source, GLenum type, int version) {
    unsigned long long h = 1469598103934665603ULL; /* FNV-1a, 64 bit */
    const unsigned char* p = (const unsigned char*)source;
    if(p != NULL) {
        while(*p != 0) { h ^= (unsigned long long)*p++; h *= 1099511628211ULL; }
    }
    h ^= (unsigned long long)type;    h *= 1099511628211ULL;
    h ^= (unsigned long long)version; h *= 1099511628211ULL;
    return h;
}

static GLchar* fear_shader_cache_get(unsigned long long key) {
    if(key == 0) return NULL;
    for(unsigned i = 0; i < FEAR_SHADER_CACHE_SLOTS; i++) {
        if(fear_shader_cache[i].key == key && fear_shader_cache[i].source != NULL) {
            size_t len = strlen(fear_shader_cache[i].source) + 1;
            GLchar* copy = malloc(len);
            if(copy == NULL) return NULL;
            memcpy(copy, fear_shader_cache[i].source, len);
            return copy;
        }
    }
    return NULL;
}

static void fear_shader_cache_put(unsigned long long key, const GLchar* source) {
    if(source == NULL || key == 0) return;
    unsigned slot = fear_shader_cache_next++ % FEAR_SHADER_CACHE_SLOTS;
    size_t len = strlen(source) + 1;
    GLchar* copy = malloc(len);
    if(copy == NULL) return;
    memcpy(copy, source, len);
    if(fear_shader_cache[slot].source != NULL) free(fear_shader_cache[slot].source);
    fear_shader_cache[slot].source = copy;
    fear_shader_cache[slot].key = key;
}

GLuint glCreateProgram(void) {
    if(!current_context) return 0;
    GLuint phys_program = es3_functions.glCreateProgram();
    if(phys_program == 0) return phys_program;
    program_info_t *prog_info = calloc(1, sizeof(program_info_t));
    if(prog_info == NULL) {
        printf("LTWShdrWp: failed to allocate program_info\n");
        abort();
    }
    unordered_map_put(current_context->program_map, (void*)phys_program, prog_info);
    return phys_program;
}

void glDeleteProgram(GLuint program) {
    if(!current_context) return;
    es3_functions.glDeleteProgram(program);
    program_info_t *old_programinfo = unordered_map_remove(current_context->program_map, (void*)program);
    if(old_programinfo == NULL) return;
    for(GLuint i = 0; i < MAX_DRAWBUFFERS; i++) {
        const GLchar* binding = old_programinfo->colorbindings[i];
        if(binding != NULL) free((void*)binding);
    }
    free(old_programinfo);
}

void glAttachShader( 	GLuint program,
                        GLuint shader) {
    if(!current_context) return;
    es3_functions.glAttachShader(program, shader);
    program_info_t* program_info = unordered_map_get(current_context->program_map, (void*)program);
    shader_info_t* shader_info = unordered_map_get(current_context->shader_map, (void*)shader);
    if(program_info == NULL || shader_info == NULL || shader_info->shader_type != GL_FRAGMENT_SHADER) return;
    program_info->frag_shader = shader;
}

void glBindFragDataLocation( 	GLuint program,
                                GLuint colorNumber,
                                const char * name) {
    if(!current_context) return;
    program_info_t *program_info = unordered_map_get(current_context->program_map, (void*)program);
    if(program_info == NULL || colorNumber >= MAX_DRAWBUFFERS) return;
    // Insert binding name at the specific index
    GLchar** pname = &program_info->colorbindings[colorNumber];
    if(asprintf(pname, "%s", name) == -1) {
        *pname = NULL;
    }
}

void glGetShaderiv(GLuint shader, GLuint pname, GLint* params) {
    if(!current_context) return;
    shader_info_t* shader_info = unordered_map_get(current_context->shader_map, (void*)shader);
    if(shader_info != NULL && shader_info->shader_type == GL_FRAGMENT_SHADER && pname == GL_COMPILE_STATUS) {
        // HACK: ignore compile results for frag shaders, as some drivers may not compile them without explicit fragouts
        // (which we add at link-time)
        *params = GL_TRUE;
        return;
    }
    es3_functions.glGetShaderiv(shader, pname, params);
}

static void insert_fragout_pos(char* source, int* size, const char* name, GLuint pos) {
    char src_string[256] = { 0 };
    char dst_string[256] = { 0 };
    snprintf(src_string, sizeof(src_string), "/* LTW INSERT LOCATION %s LTW */", name);
    snprintf(dst_string, sizeof(dst_string), "layout(location = %u) ", pos);
    gl4es_inplace_replace_simple(source, size, src_string, dst_string);
}

void glLinkProgram(GLuint program) {
    if(!current_context) return;
    program_info_t* program_info = unordered_map_get(current_context->program_map, (void*)program);
    if(program_info == NULL || program_info->frag_shader == 0) {
        // Don't have any fragment shader to patch the locations in, fall through.
        goto fallthrough;
    }
    shader_info_t *shader = unordered_map_get(current_context->shader_map, (void*)program_info->frag_shader);
    if(shader == NULL) {
        printf("LTWShdrWp: failed to patch frag data location due to missing shader info\n");
        goto fallthrough;
    }
    /* FEARLAUNCHER: shader->source can legitimately be NULL if the shader was never
     * given a source at all. strlen() on it walked into address zero. */
    if(shader->source == NULL) {
        goto fallthrough;
    }
    int nsrc_size = (int)(strlen(shader->source) + 1);
    char* new_source = malloc(nsrc_size);
    if(new_source == NULL) {
        goto fallthrough;
    }
    memcpy(new_source, shader->source, nsrc_size);
    bool changesMade = false;
    for(GLuint i = 0; i < MAX_DRAWBUFFERS; i++) {
        const char* colorbind = program_info->colorbindings[i];
        if(colorbind == NULL) continue;
        insert_fragout_pos(new_source, &nsrc_size, colorbind, i);
        changesMade = true;
    }
    if(!changesMade) {
        free(new_source);
        goto fallthrough;
    }else {
        //printf("\n\n\nShader Result POST PATCH\n%s\n\n\n", new_source);
    }
    const GLchar* const_source = (const GLchar*)new_source;
    GLuint patched_shader = es3_functions.glCreateShader(GL_FRAGMENT_SHADER);
    if(patched_shader == 0) {
        free(new_source);
        printf("LTWShdrWp: failed to initialize patched shader\n");
        goto fallthrough;
    }
    es3_functions.glShaderSource(patched_shader, 1, &const_source, NULL);
    es3_functions.glCompileShader(patched_shader);
    free(new_source);
    GLint compileStatus;
    es3_functions.glGetShaderiv(patched_shader, GL_COMPILE_STATUS, &compileStatus);
    if(compileStatus != GL_TRUE) {
        GLint logSize;
        es3_functions.glGetShaderiv(patched_shader, GL_INFO_LOG_LENGTH, &logSize);
        GLchar log[logSize];
        es3_functions.glGetShaderInfoLog(patched_shader, logSize, NULL, log);
        printf("LTWShdrWp: failed to compile patched fragment shader, using default. Log:\n\n%s\n\nShader content:\n\n%s\n\n", log, const_source);
        goto fallthrough;
    }
    es3_functions.glDetachShader(program, program_info->frag_shader);
    es3_functions.glAttachShader(program, patched_shader);
    es3_functions.glLinkProgram(program);
    es3_functions.glDeleteShader(patched_shader);
    return;
    fallthrough:
    es3_functions.glLinkProgram(program);
}

GLuint glCreateShader(GLenum shaderType) {
    if(!current_context) return 0;
    GLuint phys_shader = es3_functions.glCreateShader(shaderType);
    if(phys_shader == 0) return 0;
    shader_info_t* info_struct = calloc(1, sizeof(shader_info_t));
    if(info_struct == NULL) {
        printf("LTWShdrWp: failed to allocate shader_info\n");
        abort();
    }
    info_struct->shader_type = shaderType;
    unordered_map_put(current_context->shader_map, (void*)phys_shader, info_struct);
    return phys_shader;
}

void glDeleteShader(GLuint shader) {
    if(!current_context) return;
    es3_functions.glDeleteShader(shader);
    shader_info_t * old_shaderinfo = unordered_map_remove(current_context->shader_map, (void*)shader);
    if(old_shaderinfo == NULL) return;
    if(old_shaderinfo->source != NULL) free((void*)old_shaderinfo->source);
    free(old_shaderinfo);
}

void glShaderSource(GLuint shader, GLsizei count, const GLchar *const*string, const GLint *length) {
    if(!current_context) return;
    shader_info_t* shader_info = unordered_map_get(current_context->shader_map, (void*)shader);
    if(shader_info == NULL) {
        printf("LTWShdrWp: shader_info missing for shader %u\n", shader);
        es3_functions.glShaderSource(shader, count, string, length);
        return;
    }

    size_t target_length = 0;
#define SRC_LEN(x) length != NULL ? length[x] : strlen(string[x])
    for(GLsizei i = 0; i < count; i++) target_length += SRC_LEN(i);
    GLchar* target_string = malloc((target_length + 1) * sizeof(GLchar));
    if(target_string == NULL) {
        es3_functions.glShaderSource(shader, count, string, length);
        return;
    }
    size_t offset = 0;
    for(GLsizei i = 0; i < count; i++) {
        memcpy(&target_string[offset], string[i], SRC_LEN(i));
        offset += SRC_LEN(i);
    }
    target_string[target_length] = 0;

#undef SRC_LEN

    /* FEARLAUNCHER: consult the in-process cache before paying for the translation pass. */
    unsigned long long shader_key = fear_shader_key(target_string, shader_info->shader_type,
                                                    current_context->shader_version);
    GLchar* new_source = fear_shader_cache_get(shader_key);
    if(new_source == NULL) {
        new_source = optimize_shader(target_string, shader_info->shader_type, 460, current_context->shader_version);
        if(new_source != NULL) fear_shader_cache_put(shader_key, new_source);
    }

    /* FEARLAUNCHER: the old code freed the stored source, then, when the translator
     * failed, jumped straight past the real glShaderSource call and left
     * shader_info->source holding freed memory. The physical shader ended up with no
     * source at all, and the next glLinkProgram ran strlen() over that dangling
     * pointer - which is the crash this path used to cause. Now the source is cleared
     * to NULL on free, and a failed translation falls back to the original text: not
     * optimised, but correct, compilable, and safe for glLinkProgram to walk. */
    if(shader_info->source != NULL) {
        free((void*)shader_info->source);
        shader_info->source = NULL;
    }
    if(new_source == NULL) {
        printf("LTWShdrWp: failed to convert&optimize shader %u, using the original source\n", shader);
        size_t fallback_length = strlen(target_string) + 1;
        GLchar* fallback = malloc(fallback_length);
        if(fallback != NULL) {
            memcpy(fallback, target_string, fallback_length);
            shader_info->source = fallback;
        }
    } else {
        shader_info->source = new_source;
    }

    if(shader_info->source != NULL) {
        es3_functions.glShaderSource(shader, 1, &shader_info->source, 0);
    } else {
        /* Even the fallback could not be allocated. Hand the driver the caller's own
         * text rather than leaving the shader with no source at all. */
        es3_functions.glShaderSource(shader, count, string, length);
    }
    free(target_string);
}
