#include "fear_glsl_transpiler.h"
#include "fear_shader_translator.h"
#include "fear_shader_cache.h"
#include "fear_shader_logger.h"
#include "fear_gl_emulation.h"
#include <unordered_map>
#include <vector>
#include <mutex>
#include <algorithm>
#include <dlfcn.h>
#include <stdlib.h>

static std::mutex g_interceptorMutex;
static std::unordered_map<GLuint, GLenum> g_shaderTypes;
static std::unordered_map<GLuint, std::string> g_shaderOriginalSources;
static std::unordered_map<GLuint, std::vector<std::string>> g_programShaders;
static int g_translatedShaderCount = 0;

static bool g_contextChecked = false;
static bool g_isGLESContext = true;

static void checkGLContext() {
    if (g_contextChecked) return;

    typedef const unsigned char* (*glGetString_pfn)(unsigned int);
    static glGetString_pfn real_glGetString = nullptr;
    if (!real_glGetString) {
        real_glGetString = (glGetString_pfn)dlsym(RTLD_NEXT, "glGetString");
    }

    if (real_glGetString) {
        const unsigned char* version = real_glGetString(0x1F02 /* GL_VERSION */);
        const unsigned char* renderer = real_glGetString(0x1F01 /* GL_RENDERER */);

        if (!version) {
            return;
        }

        g_contextChecked = true;

        std::string ver_str = (const char*)version;
        std::string rend_str = renderer ? (const char*)renderer : "";

        if (ver_str.find("OpenGL ES") != std::string::npos) {
            g_isGLESContext = true;
            LOG_INFO("[FearRender] Context: GLES 3.2 | GL_VERSION: %s | GL_RENDERER: %s", ver_str.c_str(), rend_str.c_str());
        } else {
            g_isGLESContext = false;
            LOG_INFO("[FearRender] Context: Desktop GL (passthrough mode) | GL_VERSION: %s | GL_RENDERER: %s", ver_str.c_str(), rend_str.c_str());
        }
    }
}

extern "C" {

// HOOK glCreateShader:
GLuint glCreateShader(GLenum type) {
    typedef GLuint (*glCreateShader_pfn)(GLenum);
    static glCreateShader_pfn real_glCreateShader = nullptr;
    if (!real_glCreateShader) {
        real_glCreateShader = (glCreateShader_pfn)dlsym(RTLD_NEXT, "glCreateShader");
    }

    GLuint shader = 0;
    if (real_glCreateShader) {
        shader = real_glCreateShader(type);
    }

    if (shader != 0) {
        std::lock_guard<std::mutex> lock(g_interceptorMutex);
        g_shaderTypes[shader] = type;
    }
    return shader;
}

// HOOK glShaderSource:
void glShaderSource(GLuint shader, GLsizei count, const GLchar *const*string, const GLint *length) {
    checkGLContext();

    typedef void (*glShaderSource_pfn)(GLuint, GLsizei, const GLchar *const*, const GLint *);
    static glShaderSource_pfn real_glShaderSource = nullptr;
    if (!real_glShaderSource) {
        real_glShaderSource = (glShaderSource_pfn)dlsym(RTLD_NEXT, "glShaderSource");
    }

    if (!real_glShaderSource) return;

    if (!g_isGLESContext) {
        LOG_INFO("[FearRender] Shader path: passthrough");
        real_glShaderSource(shader, count, string, length);
        return;
    }

    if (count <= 0 || !string || !string[0]) {
        real_glShaderSource(shader, count, string, length);
        return;
    }

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

    {
        std::lock_guard<std::mutex> lock(g_interceptorMutex);
        g_shaderOriginalSources[shader] = full_source;
    }

    GLenum type = GL_FRAGMENT_SHADER;
    {
        std::lock_guard<std::mutex> lock(g_interceptorMutex);
        auto it = g_shaderTypes.find(shader);
        if (it != g_shaderTypes.end()) {
            type = it->second;
        }
    }

    bool success = false;
    std::string translated = FearTranspileGLSL(full_source.c_str(), type, 320, &success);

    if (success) {
        const char* translated_cstr = translated.c_str();
        real_glShaderSource(shader, 1, &translated_cstr, nullptr);
        {
            std::lock_guard<std::mutex> lock(g_interceptorMutex);
            g_translatedShaderCount++;
        }
    } else {
        real_glShaderSource(shader, count, string, length);
    }
}

// HOOK glCompileShader:
void glCompileShader(GLuint shader) {
    typedef void (*glCompileShader_pfn)(GLuint);
    static glCompileShader_pfn real_glCompileShader = nullptr;
    if (!real_glCompileShader) {
        real_glCompileShader = (glCompileShader_pfn)dlsym(RTLD_NEXT, "glCompileShader");
    }

    if (real_glCompileShader) {
        real_glCompileShader(shader);
    }

    GLint compile_status = 0;
    typedef void (*glGetShaderiv_pfn)(GLuint, GLenum, GLint*);
    static glGetShaderiv_pfn real_glGetShaderiv = nullptr;
    if (!real_glGetShaderiv) {
        real_glGetShaderiv = (glGetShaderiv_pfn)dlsym(RTLD_NEXT, "glGetShaderiv");
    }
    if (real_glGetShaderiv) {
        real_glGetShaderiv(shader, GL_COMPILE_STATUS, &compile_status);
    }

    if (compile_status == GL_FALSE) {
        GLint log_len = 0;
        if (real_glGetShaderiv) {
            real_glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &log_len);
        }
        if (log_len > 0) {
            char* log_buffer = (char*)malloc(log_len);
            typedef void (*glGetShaderInfoLog_pfn)(GLuint, GLsizei, GLsizei*, GLchar*);
            static glGetShaderInfoLog_pfn real_glGetShaderInfoLog = nullptr;
            if (!real_glGetShaderInfoLog) {
                real_glGetShaderInfoLog = (glGetShaderInfoLog_pfn)dlsym(RTLD_NEXT, "glGetShaderInfoLog");
            }
            if (real_glGetShaderInfoLog) {
                real_glGetShaderInfoLog(shader, log_len, nullptr, log_buffer);
                LOG_ERROR("[FearRender] SHADER COMPILE ERROR: %s", log_buffer);
            }
            free(log_buffer);
        } else {
            LOG_ERROR("[FearRender] SHADER COMPILE ERROR: Unknown compile failure");
        }
    } else {
        LOG_INFO("[FearRender] Shader compiled successfully");
    }
}

// HOOK glDeleteShader:
void glDeleteShader(GLuint shader) {
    typedef void (*glDeleteShader_pfn)(GLuint);
    static glDeleteShader_pfn real_glDeleteShader = nullptr;
    if (!real_glDeleteShader) {
        real_glDeleteShader = (glDeleteShader_pfn)dlsym(RTLD_NEXT, "glDeleteShader");
    }
    if (real_glDeleteShader) {
        real_glDeleteShader(shader);
    }

    std::lock_guard<std::mutex> lock(g_interceptorMutex);
    g_shaderTypes.erase(shader);
    g_shaderOriginalSources.erase(shader);
}

// HOOK glAttachShader:
void glAttachShader(GLuint program, GLuint shader) {
    typedef void (*glAttachShader_pfn)(GLuint, GLuint);
    static glAttachShader_pfn real_glAttachShader = nullptr;
    if (!real_glAttachShader) {
        real_glAttachShader = (glAttachShader_pfn)dlsym(RTLD_NEXT, "glAttachShader");
    }
    if (real_glAttachShader) {
        real_glAttachShader(program, shader);
    }

    std::string sh_source = "";
    {
        std::lock_guard<std::mutex> lock(g_interceptorMutex);
        auto it = g_shaderOriginalSources.find(shader);
        if (it != g_shaderOriginalSources.end()) {
            sh_source = it->second;
        }
    }
    if (!sh_source.empty()) {
        std::string sh_hash = getShaderSourceHash(sh_source);
        std::lock_guard<std::mutex> lock(g_interceptorMutex);
        auto& list = g_programShaders[program];
        if (std::find(list.begin(), list.end(), sh_hash) == list.end()) {
            list.push_back(sh_hash);
        }
    }
}

// HOOK glDetachShader:
void glDetachShader(GLuint program, GLuint shader) {
    typedef void (*glDetachShader_pfn)(GLuint, GLuint);
    static glDetachShader_pfn real_glDetachShader = nullptr;
    if (!real_glDetachShader) {
        real_glDetachShader = (glDetachShader_pfn)dlsym(RTLD_NEXT, "glDetachShader");
    }
    if (real_glDetachShader) {
        real_glDetachShader(program, shader);
    }

    std::string sh_source = "";
    {
        std::lock_guard<std::mutex> lock(g_interceptorMutex);
        auto it = g_shaderOriginalSources.find(shader);
        if (it != g_shaderOriginalSources.end()) {
            sh_source = it->second;
        }
    }
    if (!sh_source.empty()) {
        std::string sh_hash = getShaderSourceHash(sh_source);
        std::lock_guard<std::mutex> lock(g_interceptorMutex);
        auto& list = g_programShaders[program];
        auto it = std::find(list.begin(), list.end(), sh_hash);
        if (it != list.end()) {
            list.erase(it);
        }
    }
}

// HOOK glDeleteProgram:
void glDeleteProgram(GLuint program) {
    typedef void (*glDeleteProgram_pfn)(GLuint);
    static glDeleteProgram_pfn real_glDeleteProgram = nullptr;
    if (!real_glDeleteProgram) {
        real_glDeleteProgram = (glDeleteProgram_pfn)dlsym(RTLD_NEXT, "glDeleteProgram");
    }
    if (real_glDeleteProgram) {
        real_glDeleteProgram(program);
    }

    std::lock_guard<std::mutex> lock(g_interceptorMutex);
    g_programShaders.erase(program);
}

// HOOK glLinkProgram:
void glLinkProgram(GLuint program) {
    checkGLContext();

    typedef void (*glLinkProgram_pfn)(GLuint);
    static glLinkProgram_pfn real_glLinkProgram = nullptr;
    if (!real_glLinkProgram) {
        real_glLinkProgram = (glLinkProgram_pfn)dlsym(RTLD_NEXT, "glLinkProgram");
    }

    std::vector<std::string> hashes;
    {
        std::lock_guard<std::mutex> lock(g_interceptorMutex);
        auto it = g_programShaders.find(program);
        if (it != g_programShaders.end()) {
            hashes = it->second;
        }
    }

    std::sort(hashes.begin(), hashes.end());
    std::string concat = "";
    for (const auto& h : hashes) {
        concat += h;
    }

    std::string program_hash = "";
    if (!concat.empty()) {
        program_hash = getShaderSourceHash(concat);
    }

    bool loaded_from_cache = false;
    if (!program_hash.empty()) {
        loaded_from_cache = loadProgramBinaryFromCache(program, program_hash, g_isGLESContext);
    }

    if (!loaded_from_cache) {
        if (real_glLinkProgram) {
            real_glLinkProgram(program);
        }

        GLint link_status = 0;
        typedef void (*glGetProgramiv_pfn)(GLuint, GLenum, GLint*);
        static glGetProgramiv_pfn real_glGetProgramiv = nullptr;
        if (!real_glGetProgramiv) {
            real_glGetProgramiv = (glGetProgramiv_pfn)dlsym(RTLD_NEXT, "glGetProgramiv");
        }
        if (real_glGetProgramiv) {
            real_glGetProgramiv(program, GL_LINK_STATUS, &link_status);
        }

        if (link_status && !program_hash.empty()) {
            saveProgramBinaryToCache(program, program_hash, g_isGLESContext);
        }
    }
}

int getTranslatedShaderCountInternal() {
    std::lock_guard<std::mutex> lock(g_interceptorMutex);
    return g_translatedShaderCount;
}

} // extern "C"
