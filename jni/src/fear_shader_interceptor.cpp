#include "fear_shader_translator.h"
#include "fear_shader_cache.h"
#include "fear_shader_logger.h"
#include <unordered_map>
#include <vector>
#include <mutex>
#include <algorithm>
#include <dlfcn.h>
#include <stdlib.h>

static std::mutex g_interceptorMutex;
static std::unordered_map<GLuint, GLenum> g_shaderTypes; // shaderID -> shaderType
static std::unordered_map<GLuint, std::string> g_shaderOriginalSources; // shaderID -> originalSource
static std::unordered_map<GLuint, std::vector<std::string>> g_programShaders; // programID -> list of shader hashes

// OpenGL ES Error converter helper
static const char* get_gl_error_string(unsigned int err) {
    switch (err) {
        case 0x0500: return "GL_INVALID_ENUM";
        case 0x0501: return "GL_INVALID_VALUE";
        case 0x0502: return "GL_INVALID_OPERATION";
        case 0x0503: return "GL_STACK_OVERFLOW";
        case 0x0504: return "GL_STACK_UNDERFLOW";
        case 0x0505: return "GL_OUT_OF_MEMORY";
        case 0x0506: return "GL_INVALID_FRAMEBUFFER_OPERATION";
        default: return "GL_UNKNOWN_ERROR";
    }
}

static int g_translatedShaderCount = 0;

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
    typedef void (*glShaderSource_pfn)(GLuint, GLsizei, const GLchar *const*, const GLint *);
    static glShaderSource_pfn real_glShaderSource = nullptr;
    if (!real_glShaderSource) {
        real_glShaderSource = (glShaderSource_pfn)dlsym(RTLD_NEXT, "glShaderSource");
    }

    if (!real_glShaderSource) return;

    if (count <= 0 || !string || !string[0]) {
        real_glShaderSource(shader, count, string, length);
        return;
    }

    // Extract original source
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

    // Track original source
    {
        std::lock_guard<std::mutex> lock(g_interceptorMutex);
        g_shaderOriginalSources[shader] = full_source;
    }

    // Get shader type
    GLenum type = GL_FRAGMENT_SHADER;
    {
        std::lock_guard<std::mutex> lock(g_interceptorMutex);
        auto it = g_shaderTypes.find(shader);
        if (it != g_shaderTypes.end()) {
            type = it->second;
        }
    }

    bool success = false;
    std::string translated = FearTranslateGLSL(full_source.c_str(), type, &success);

    if (success) {
        const char* translated_cstr = translated.c_str();
        real_glShaderSource(shader, 1, &translated_cstr, nullptr);
        {
            std::lock_guard<std::mutex> lock(g_interceptorMutex);
            g_translatedShaderCount++;
        }
        LOG_INFO("[FearEngine] Shader translated successfully (type: %s, size: %u bytes)",
                 (type == GL_VERTEX_SHADER ? "VERTEX" : (type == GL_FRAGMENT_SHADER ? "FRAGMENT" : "OTHER")),
                 (unsigned int)translated.size());
    } else {
        real_glShaderSource(shader, count, string, length);
        LOG_WARNING("[FearEngine] Shader translation failed, passing original code");
    }
}

// Export fear_glShaderSource alias
void fear_glShaderSource(unsigned int shader, int count, const char* const* string, const int* length) {
    glShaderSource(shader, count, string, length);
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

    // Check compilation status
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
                LOG_ERROR("[FearEngine] SHADER COMPILE ERROR: %s", log_buffer);
            }
            free(log_buffer);
        } else {
            LOG_ERROR("[FearEngine] SHADER COMPILE ERROR: Unknown compile failure");
        }
    } else {
        LOG_INFO("[FearEngine] Shader compiled successfully");
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
        loaded_from_cache = loadProgramBinaryFromCache(program, program_hash);
    }

    if (!loaded_from_cache) {
        if (real_glLinkProgram) {
            real_glLinkProgram(program);
        }

        // Check link status and save
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
            saveProgramBinaryToCache(program, program_hash);
        }
    }
}

// TODO: PHASE 2 PREPARATION (Texture/FBO Interceptor placeholder hooks)
void glTexImage2D(unsigned int target, int level, int internalformat, int width, int height, int border, unsigned int format, unsigned int type, const void* pixels) {
    typedef void (*glTexImage2D_pfn)(unsigned int, int, int, int, int, int, unsigned int, unsigned int, const void*);
    static glTexImage2D_pfn real_glTexImage2D = nullptr;
    if (!real_glTexImage2D) {
        real_glTexImage2D = (glTexImage2D_pfn)dlsym(RTLD_NEXT, "glTexImage2D");
    }

    int original_internalformat = internalformat;
    if (internalformat == 0x8814 /* GL_RGBA32F */) {
        internalformat = 0x881A /* GL_RGBA16F */;
        LOG_INFO("[FearEngine] Downgraded 32F/16F texture to mobile-safe format (GL_RGBA32F -> GL_RGBA16F).");
    } else if (internalformat == 0x8815 /* GL_RGB32F */) {
        internalformat = 0x881A /* GL_RGBA16F */;
        LOG_INFO("[FearEngine] Downgraded 32F/16F texture to mobile-safe format (GL_RGB32F -> GL_RGBA16F).");
    } else if (internalformat == 0x881B /* GL_RGB16F */) {
        internalformat = 0x881A /* GL_RGBA16F */;
        LOG_INFO("[FearEngine] Map non-renderable GL_RGB16F texture to renderable GL_RGBA16F format.");
    } else if (internalformat == 0x8CAC /* GL_DEPTH_COMPONENT32F */) {
        internalformat = 0x81A6 /* GL_DEPTH_COMPONENT24 */;
        LOG_INFO("[FearEngine] Downgraded depth texture format (GL_DEPTH_COMPONENT32F -> GL_DEPTH_COMPONENT24).");
    } else if (internalformat == 0x8CAD /* GL_DEPTH32F_STENCIL8 */) {
        internalformat = 0x88F0 /* GL_DEPTH24_STENCIL8 */;
        LOG_INFO("[FearEngine] Downgraded depth-stencil texture format (GL_DEPTH32F_STENCIL8 -> GL_DEPTH24_STENCIL8).");
    }

    if (real_glTexImage2D) {
        real_glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
    }

    typedef unsigned int (*glGetError_pfn)();
    static glGetError_pfn real_glGetError = nullptr;
    if (!real_glGetError) {
        real_glGetError = (glGetError_pfn)dlsym(RTLD_NEXT, "glGetError");
    }
    if (real_glGetError) {
        unsigned int err = real_glGetError();
        if (err != 0) {
            LOG_ERROR("[FearEngine] Error in glTexImage2D (0x%04X: %s) with params: [target=0x%X, level=%d, internalformat=0x%X, width=%d, height=%d]",
                      err, get_gl_error_string(err), target, level, internalformat, width, height);
        }
    }
}

void glTexImage3D(unsigned int target, int level, int internalformat, int width, int height, int depth, int border, unsigned int format, unsigned int type, const void* pixels) {
    typedef void (*glTexImage3D_pfn)(unsigned int, int, int, int, int, int, int, unsigned int, unsigned int, const void*);
    static glTexImage3D_pfn real_glTexImage3D = nullptr;
    if (!real_glTexImage3D) {
        real_glTexImage3D = (glTexImage3D_pfn)dlsym(RTLD_NEXT, "glTexImage3D");
    }

    int original_internalformat = internalformat;
    if (internalformat == 0x8814 /* GL_RGBA32F */) {
        internalformat = 0x881A /* GL_RGBA16F */;
        LOG_INFO("[FearEngine] Downgraded 32F/16F texture to mobile-safe format (GL_RGBA32F -> GL_RGBA16F).");
    } else if (internalformat == 0x8815 /* GL_RGB32F */) {
        internalformat = 0x881A /* GL_RGBA16F */;
        LOG_INFO("[FearEngine] Downgraded 32F/16F texture to mobile-safe format (GL_RGB32F -> GL_RGBA16F).");
    } else if (internalformat == 0x881B /* GL_RGB16F */) {
        internalformat = 0x881A /* GL_RGBA16F */;
        LOG_INFO("[FearEngine] Map non-renderable GL_RGB16F texture to renderable GL_RGBA16F format.");
    } else if (internalformat == 0x8CAC /* GL_DEPTH_COMPONENT32F */) {
        internalformat = 0x81A6 /* GL_DEPTH_COMPONENT24 */;
        LOG_INFO("[FearEngine] Downgraded depth texture format (GL_DEPTH_COMPONENT32F -> GL_DEPTH_COMPONENT24).");
    } else if (internalformat == 0x8CAD /* GL_DEPTH32F_STENCIL8 */) {
        internalformat = 0x88F0 /* GL_DEPTH24_STENCIL8 */;
        LOG_INFO("[FearEngine] Downgraded depth-stencil texture format (GL_DEPTH32F_STENCIL8 -> GL_DEPTH24_STENCIL8).");
    }

    if (real_glTexImage3D) {
        real_glTexImage3D(target, level, internalformat, width, height, depth, border, format, type, pixels);
    }

    typedef unsigned int (*glGetError_pfn)();
    static glGetError_pfn real_glGetError = nullptr;
    if (!real_glGetError) {
        real_glGetError = (glGetError_pfn)dlsym(RTLD_NEXT, "glGetError");
    }
    if (real_glGetError) {
        unsigned int err = real_glGetError();
        if (err != 0) {
            LOG_ERROR("[FearEngine] Error in glTexImage3D (0x%04X: %s) with params: [target=0x%X, level=%d, internalformat=0x%X, width=%d, height=%d, depth=%d]",
                      err, get_gl_error_string(err), target, level, internalformat, width, height, depth);
        }
    }
}

void glRenderbufferStorage(unsigned int target, unsigned int internalformat, int width, int height) {
    typedef void (*glRenderbufferStorage_pfn)(unsigned int, unsigned int, int, int);
    static glRenderbufferStorage_pfn real_glRenderbufferStorage = nullptr;
    if (!real_glRenderbufferStorage) {
        real_glRenderbufferStorage = (glRenderbufferStorage_pfn)dlsym(RTLD_NEXT, "glRenderbufferStorage");
    }

    unsigned int original_internalformat = internalformat;
    if (internalformat == 0x8CAC /* GL_DEPTH_COMPONENT32F */) {
        internalformat = 0x81A6 /* GL_DEPTH_COMPONENT24 */;
        LOG_INFO("[FearEngine] Downgraded renderbuffer depth format (GL_DEPTH_COMPONENT32F -> GL_DEPTH_COMPONENT24).");
    } else if (internalformat == 0x8CAD /* GL_DEPTH32F_STENCIL8 */) {
        internalformat = 0x88F0 /* GL_DEPTH24_STENCIL8 */;
        LOG_INFO("[FearEngine] Downgraded renderbuffer depth-stencil format (GL_DEPTH32F_STENCIL8 -> GL_DEPTH24_STENCIL8).");
    } else if (internalformat == 0x8814 /* GL_RGBA32F */) {
        internalformat = 0x881A /* GL_RGBA16F */;
        LOG_INFO("[FearEngine] Downgraded renderbuffer color format (GL_RGBA32F -> GL_RGBA16F).");
    } else if (internalformat == 0x8815 /* GL_RGB32F */ || internalformat == 0x881B /* GL_RGB16F */) {
        internalformat = 0x881A /* GL_RGBA16F */;
        LOG_INFO("[FearEngine] Downgraded renderbuffer color format (GL_RGB32F/16F -> GL_RGBA16F).");
    }

    if (real_glRenderbufferStorage) {
        real_glRenderbufferStorage(target, internalformat, width, height);
    }

    typedef unsigned int (*glGetError_pfn)();
    static glGetError_pfn real_glGetError = nullptr;
    if (!real_glGetError) {
        real_glGetError = (glGetError_pfn)dlsym(RTLD_NEXT, "glGetError");
    }
    if (real_glGetError) {
        unsigned int err = real_glGetError();
        if (err != 0) {
            LOG_ERROR("[FearEngine] Error in glRenderbufferStorage (0x%04X: %s) with params: [target=0x%X, internalformat=0x%X, width=%d, height=%d]",
                      err, get_gl_error_string(err), target, internalformat, width, height);
        }
    }
}

void glFramebufferTexture2D(unsigned int target, unsigned int attachment, unsigned int textarget, unsigned int texture, int level) {
    typedef void (*glFramebufferTexture2D_pfn)(unsigned int, unsigned int, unsigned int, unsigned int, int);
    static glFramebufferTexture2D_pfn real_glFramebufferTexture2D = nullptr;
    if (!real_glFramebufferTexture2D) {
        real_glFramebufferTexture2D = (glFramebufferTexture2D_pfn)dlsym(RTLD_NEXT, "glFramebufferTexture2D");
    }

    if (real_glFramebufferTexture2D) {
        real_glFramebufferTexture2D(target, attachment, textarget, texture, level);
    }

    typedef unsigned int (*glGetError_pfn)();
    static glGetError_pfn real_glGetError = nullptr;
    if (!real_glGetError) {
        real_glGetError = (glGetError_pfn)dlsym(RTLD_NEXT, "glGetError");
    }
    if (real_glGetError) {
        unsigned int err = real_glGetError();
        if (err != 0) {
            LOG_ERROR("[FearEngine] Error in glFramebufferTexture2D (0x%04X: %s) with params: [target=0x%X, attachment=0x%X, textarget=0x%X, texture=%u, level=%d]",
                      err, get_gl_error_string(err), target, attachment, textarget, texture, level);
        }
    }
}

// TODO: PHASE 3 PREPARATION (Compute Shaders placeholder hooks)
void glDispatchCompute(unsigned int num_groups_x, unsigned int num_groups_y, unsigned int num_groups_z) {
    typedef void (*glDispatchCompute_pfn)(unsigned int, unsigned int, unsigned int);
    static glDispatchCompute_pfn real_glDispatchCompute = nullptr;
    if (!real_glDispatchCompute) {
        real_glDispatchCompute = (glDispatchCompute_pfn)dlsym(RTLD_NEXT, "glDispatchCompute");
    }
    if (real_glDispatchCompute) {
        real_glDispatchCompute(num_groups_x, num_groups_y, num_groups_z);
    }
}

int getTranslatedShaderCountInternal() {
    std::lock_guard<std::mutex> lock(g_interceptorMutex);
    return g_translatedShaderCount;
}

} // extern "C"
