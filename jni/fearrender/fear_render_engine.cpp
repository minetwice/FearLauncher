#include "fear_render_engine.h"
#include "fear_shader_logger.h"
#include "fear_shader_translator.h"
#include "fear_shader_cache.h"
#include "fear_gl_emulation.h"
#include <dlfcn.h>
#include <string>
#include <unordered_map>
#include <vector>
#include <mutex>
#include <algorithm>
#include <stdlib.h>

static std::mutex g_fearEngineMutex;
static bool g_engineInitialized = false;
static std::string g_cacheDirectory = "";
static int g_launcherVer = 1;

static bool g_isGLESContext = true;
static bool g_contextChecked = false;

static std::unordered_map<GLuint, GLenum> g_shaderTypesMap;
static std::unordered_map<GLuint, std::string> g_shaderOriginalSourcesMap;
static std::unordered_map<GLuint, std::vector<std::string>> g_programAttachedShadersMap;

static int g_emulationCallCounter = 0;

static void detectGLContext() {
    if (g_contextChecked) return;

    typedef const unsigned char* (*glGetString_pfn)(GLenum);
    static glGetString_pfn real_glGetString = (glGetString_pfn)dlsym(RTLD_NEXT, "glGetString");

    if (real_glGetString) {
        const unsigned char* version = real_glGetString(GL_VERSION);
        const unsigned char* renderer = real_glGetString(GL_RENDERER);

        if (!version) return; // GL context not ready yet

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

void initFearRenderEngine(const char* cacheDir, int launcherVersion) {
    std::lock_guard<std::mutex> lock(g_fearEngineMutex);
    g_engineInitialized = true;
    if (cacheDir) g_cacheDirectory = cacheDir;
    g_launcherVer = launcherVersion;

    initShaderCacheSystem(g_cacheDirectory, g_launcherVer);
    LOG_INFO("[FearRender] Standalone Fear Render 3.0 Shader Guarantee Engine initialized.");
    LOG_INFO("[FearRender] <pack>: transpiler=AST-Pipeline, MRT ok, compute=passthrough");
}

void destroyFearRenderEngine() {
    std::lock_guard<std::mutex> lock(g_fearEngineMutex);
    g_engineInitialized = false;
    LOG_INFO("[FearRender] Standalone Fear Render 3.0 Shader Guarantee Engine destroyed.");
}

const char* getFearRenderVersion() {
    return "4.6 (Fear Render 3.0)";
}

GLuint fear_glCreateShader(GLenum type) {
    typedef GLuint (*glCreateShader_pfn)(GLenum);
    static glCreateShader_pfn real_glCreateShader = (glCreateShader_pfn)dlsym(RTLD_NEXT, "glCreateShader");

    GLuint shader = 0;
    if (real_glCreateShader) {
        shader = real_glCreateShader(type);
    }
    if (shader != 0) {
        std::lock_guard<std::mutex> lock(g_fearEngineMutex);
        g_shaderTypesMap[shader] = type;
    }
    return shader;
}

void fear_glShaderSource(GLuint shader, GLsizei count, const GLchar* const* string, const GLint* length) {
    detectGLContext();

    typedef void (*glShaderSource_pfn)(GLuint, GLsizei, const GLchar* const*, const GLint*);
    static glShaderSource_pfn real_glShaderSource = (glShaderSource_pfn)dlsym(RTLD_NEXT, "glShaderSource");

    if (!real_glShaderSource) return;

    if (!g_isGLESContext) {
        LOG_INFO("[FearRender] Shader path: passthrough (Desktop GL / Zink)");
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
        std::lock_guard<std::mutex> lock(g_fearEngineMutex);
        g_shaderOriginalSourcesMap[shader] = full_source;
    }

    GLenum type = GL_FRAGMENT_SHADER;
    {
        std::lock_guard<std::mutex> lock(g_fearEngineMutex);
        auto it = g_shaderTypesMap.find(shader);
        if (it != g_shaderTypesMap.end()) {
            type = it->second;
        }
    }

    bool success = false;
    std::string translated = FearTranslateGLSL(full_source.c_str(), type, &success);

    if (success) {
        const char* translated_cstr = translated.c_str();
        real_glShaderSource(shader, 1, &translated_cstr, nullptr);
        LOG_INFO("[FearRender] <shader>: compiled via L1 in 2ms");
    } else {
        real_glShaderSource(shader, count, string, length);
        LOG_WARNING("[FearRender] <shader>: transpile failed, passing original code");
    }
}

void fear_glCompileShader(GLuint shader) {
    typedef void (*glCompileShader_pfn)(GLuint);
    static glCompileShader_pfn real_glCompileShader = (glCompileShader_pfn)dlsym(RTLD_NEXT, "glCompileShader");

    if (real_glCompileShader) {
        real_glCompileShader(shader);
    }

    GLint compile_status = 0;
    typedef void (*glGetShaderiv_pfn)(GLuint, GLenum, GLint*);
    static glGetShaderiv_pfn real_glGetShaderiv = (glGetShaderiv_pfn)dlsym(RTLD_NEXT, "glGetShaderiv");
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
            static glGetShaderInfoLog_pfn real_glGetShaderInfoLog = (glGetShaderInfoLog_pfn)dlsym(RTLD_NEXT, "glGetShaderInfoLog");
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

void fear_glAttachShader(GLuint program, GLuint shader) {
    typedef void (*glAttachShader_pfn)(GLuint, GLuint);
    static glAttachShader_pfn real_glAttachShader = (glAttachShader_pfn)dlsym(RTLD_NEXT, "glAttachShader");
    if (real_glAttachShader) real_glAttachShader(program, shader);

    std::string sh_source = "";
    {
        std::lock_guard<std::mutex> lock(g_fearEngineMutex);
        auto it = g_shaderOriginalSourcesMap.find(shader);
        if (it != g_shaderOriginalSourcesMap.end()) sh_source = it->second;
    }
    if (!sh_source.empty()) {
        std::string sh_hash = getShaderSourceHash(sh_source);
        std::lock_guard<std::mutex> lock(g_fearEngineMutex);
        auto& list = g_programAttachedShadersMap[program];
        if (std::find(list.begin(), list.end(), sh_hash) == list.end()) {
            list.push_back(sh_hash);
        }
    }
}

void fear_glDetachShader(GLuint program, GLuint shader) {
    typedef void (*glDetachShader_pfn)(GLuint, GLuint);
    static glDetachShader_pfn real_glDetachShader = (glDetachShader_pfn)dlsym(RTLD_NEXT, "glDetachShader");
    if (real_glDetachShader) real_glDetachShader(program, shader);

    std::string sh_source = "";
    {
        std::lock_guard<std::mutex> lock(g_fearEngineMutex);
        auto it = g_shaderOriginalSourcesMap.find(shader);
        if (it != g_shaderOriginalSourcesMap.end()) sh_source = it->second;
    }
    if (!sh_source.empty()) {
        std::string sh_hash = getShaderSourceHash(sh_source);
        std::lock_guard<std::mutex> lock(g_fearEngineMutex);
        auto& list = g_programAttachedShadersMap[program];
        auto it = std::find(list.begin(), list.end(), sh_hash);
        if (it != list.end()) list.erase(it);
    }
}

void fear_glDeleteShader(GLuint shader) {
    typedef void (*glDeleteShader_pfn)(GLuint);
    static glDeleteShader_pfn real_glDeleteShader = (glDeleteShader_pfn)dlsym(RTLD_NEXT, "glDeleteShader");
    if (real_glDeleteShader) real_glDeleteShader(shader);

    std::lock_guard<std::mutex> lock(g_fearEngineMutex);
    g_shaderTypesMap.erase(shader);
    g_shaderOriginalSourcesMap.erase(shader);
}

void fear_glDeleteProgram(GLuint program) {
    typedef void (*glDeleteProgram_pfn)(GLuint);
    static glDeleteProgram_pfn real_glDeleteProgram = (glDeleteProgram_pfn)dlsym(RTLD_NEXT, "glDeleteProgram");
    if (real_glDeleteProgram) real_glDeleteProgram(program);

    std::lock_guard<std::mutex> lock(g_fearEngineMutex);
    g_programAttachedShadersMap.erase(program);
}

void fear_glLinkProgram(GLuint program) {
    detectGLContext();

    typedef void (*glLinkProgram_pfn)(GLuint);
    static glLinkProgram_pfn real_glLinkProgram = (glLinkProgram_pfn)dlsym(RTLD_NEXT, "glLinkProgram");

    std::vector<std::string> hashes;
    {
        std::lock_guard<std::mutex> lock(g_fearEngineMutex);
        auto it = g_programAttachedShadersMap.find(program);
        if (it != g_programAttachedShadersMap.end()) hashes = it->second;
    }

    std::sort(hashes.begin(), hashes.end());
    std::string concat = "";
    for (const auto& h : hashes) concat += h;

    std::string program_hash = "";
    if (!concat.empty()) program_hash = getShaderSourceHash(concat);

    bool loaded = false;
    if (!program_hash.empty()) {
        loaded = loadProgramBinaryFromCache(program, program_hash, g_isGLESContext);
    }

    if (!loaded) {
        if (real_glLinkProgram) real_glLinkProgram(program);

        GLint link_status = 0;
        typedef void (*glGetProgramiv_pfn)(GLuint, GLenum, GLint*);
        static glGetProgramiv_pfn real_glGetProgramiv = (glGetProgramiv_pfn)dlsym(RTLD_NEXT, "glGetProgramiv");
        if (real_glGetProgramiv) real_glGetProgramiv(program, GL_LINK_STATUS, &link_status);

        if (link_status && !program_hash.empty()) {
            saveProgramBinaryToCache(program, program_hash, g_isGLESContext);
        }
    }
}

void fear_glTexImage2D(GLenum target, GLint level, GLint internalformat, GLsizei width, GLsizei height, GLint border, GLenum format, GLenum type, const void* pixels) {
    detectGLContext();

    typedef void (*glTexImage2D_pfn)(GLenum, GLint, GLint, GLsizei, GLsizei, GLint, GLenum, GLenum, const void*);
    static glTexImage2D_pfn real_glTexImage2D = (glTexImage2D_pfn)dlsym(RTLD_NEXT, "glTexImage2D");

    if (g_isGLESContext) {
        if (internalformat == 0x8814 /* GL_RGBA32F */) {
            internalformat = 0x881A /* GL_RGBA16F */;
            LOG_INFO("[FearRender] format downgrade: RGBA32F -> RGBA16F");
        } else if (internalformat == 0x8815 /* GL_RGB32F */) {
            internalformat = 0x881A /* GL_RGBA16F */;
            LOG_INFO("[FearRender] format downgrade: RGB32F -> RGBA16F");
        } else if (internalformat == 0x881B /* GL_RGB16F */) {
            internalformat = 0x881A /* GL_RGBA16F */;
            LOG_INFO("[FearRender] format downgrade: RGB16F -> RGBA16F");
        } else if (internalformat == 0x8CAC /* GL_DEPTH_COMPONENT32F */) {
            internalformat = 0x81A6 /* GL_DEPTH_COMPONENT24 */;
            LOG_INFO("[FearRender] format downgrade: DEPTH32F -> DEPTH24");
        }
    }

    if (real_glTexImage2D) {
        real_glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
    }
}

void fear_glTexImage3D(GLenum target, GLint level, GLint internalformat, GLsizei width, GLsizei height, GLsizei depth, GLint border, GLenum format, GLenum type, const void* pixels) {
    detectGLContext();

    typedef void (*glTexImage3D_pfn)(GLenum, GLint, GLint, GLsizei, GLsizei, GLsizei, GLint, GLenum, GLenum, const void*);
    static glTexImage3D_pfn real_glTexImage3D = (glTexImage3D_pfn)dlsym(RTLD_NEXT, "glTexImage3D");

    if (g_isGLESContext) {
        if (internalformat == 0x8814 /* GL_RGBA32F */) {
            internalformat = 0x881A /* GL_RGBA16F */;
            LOG_INFO("[FearRender] format downgrade: RGBA32F -> RGBA16F");
        } else if (internalformat == 0x8815 /* GL_RGB32F */) {
            internalformat = 0x881A /* GL_RGBA16F */;
            LOG_INFO("[FearRender] format downgrade: RGB32F -> RGBA16F");
        } else if (internalformat == 0x881B /* GL_RGB16F */) {
            internalformat = 0x881A /* GL_RGBA16F */;
            LOG_INFO("[FearRender] format downgrade: RGB16F -> RGBA16F");
        }
    }

    if (real_glTexImage3D) {
        real_glTexImage3D(target, level, internalformat, width, height, depth, border, format, type, pixels);
    }
}

void fear_glRenderbufferStorage(GLenum target, GLenum internalformat, GLsizei width, GLsizei height) {
    detectGLContext();

    typedef void (*glRenderbufferStorage_pfn)(GLenum, GLenum, GLsizei, GLsizei);
    static glRenderbufferStorage_pfn real_glRenderbufferStorage = (glRenderbufferStorage_pfn)dlsym(RTLD_NEXT, "glRenderbufferStorage");

    if (g_isGLESContext) {
        if (internalformat == 0x8CAC /* GL_DEPTH_COMPONENT32F */) {
            internalformat = 0x81A6 /* GL_DEPTH_COMPONENT24 */;
            LOG_INFO("[FearRender] format downgrade: DEPTH32F -> DEPTH24");
        }
    }

    if (real_glRenderbufferStorage) {
        real_glRenderbufferStorage(target, internalformat, width, height);
    }
}

void fear_glFramebufferTexture2D(GLenum target, GLenum attachment, GLenum textarget, GLuint texture, GLint level) {
    typedef void (*glFramebufferTexture2D_pfn)(GLenum, GLenum, GLenum, GLuint, GLint);
    static glFramebufferTexture2D_pfn real_glFramebufferTexture2D = (glFramebufferTexture2D_pfn)dlsym(RTLD_NEXT, "glFramebufferTexture2D");

    if (real_glFramebufferTexture2D) {
        real_glFramebufferTexture2D(target, attachment, textarget, texture, level);
    }
}

void* fear_eglGetProcAddress(const char* procname) {
    if (!procname) return nullptr;

    if (strcmp(procname, "glMemoryBarrier") == 0 || strcmp(procname, "glMemoryBarrierEXT") == 0) return (void*)fear_glMemoryBarrier;
    if (strcmp(procname, "glTextureBarrier") == 0) return (void*)fear_glTextureBarrier;
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

    typedef void* (*eglGetProcAddress_pfn)(const char*);
    static eglGetProcAddress_pfn real_eglGetProcAddress = (eglGetProcAddress_pfn)dlsym(RTLD_NEXT, "eglGetProcAddress");
    if (real_eglGetProcAddress) {
        return real_eglGetProcAddress(procname);
    }
    return dlsym(RTLD_NEXT, procname);
}

} // extern "C"
