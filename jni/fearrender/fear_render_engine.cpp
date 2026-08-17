#include "fear_render_engine.h"
#include "fear_shader_logger.h"
#include "fear_shader_translator.h"
#include "fear_shader_cache.h"
#include "fear_gl_emulation.h"
#include "fear_glsl_transpiler.h"
#include "shader/converter.hpp"
#include "shader/utils.hpp"
#include "main.hpp"

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
            LOG_INFO("[FearRender] core=FOGLTLOGLES integrated, backend=GLES");
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
    FOGLTLOGLES::init();
    LOG_INFO("[FearRender] core=FOGLTLOGLES integrated, backend=GLES");
    LOG_INFO("[FearRender] Standalone Fear Render 4.6 Engine initialized.");
}

void destroyFearRenderEngine() {
    std::lock_guard<std::mutex> lock(g_fearEngineMutex);
    g_engineInitialized = false;
    LOG_INFO("[FearRender] Standalone Fear Render 4.6 Engine destroyed.");
}

const char* getFearRenderVersion() {
    return "4.6 (Fear Render)";
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
        LOG_INFO("[FearShader] Winner: Level 4 (Passthrough - Desktop GL / Zink)");
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

    // --- Shader Path Ladder ---
    // Level 1: Quasar-transpiled source (if present or tagged)
    if (full_source.find("// Quasar-Transpiled") != std::string::npos || full_source.find("#define QUASAR_TRANSPILED") != std::string::npos) {
        LOG_INFO("[FearShader] Winner: Level 1 (Quasar-transpiled source)");
        const char* cstr = full_source.c_str();
        real_glShaderSource(shader, 1, &cstr, nullptr);
        return;
    }

    // Level 2: FOGLTLOGLES translation
    try {
        shaderc_shader_kind kind = shaderc_glsl_fragment_shader;
        if (type == GL_VERTEX_SHADER) kind = shaderc_glsl_vertex_shader;
        else if (type == GL_COMPUTE_SHADER) kind = shaderc_glsl_compute_shader;

        std::string source_copy = full_source;
        ShaderConverter::convertAndFix(kind, source_copy);
        if (!source_copy.empty()) {
            LOG_INFO("[FearShader] Winner: Level 2 (FOGLTLOGLES translation)");
            const char* cstr = source_copy.c_str();
            real_glShaderSource(shader, 1, &cstr, nullptr);
            return;
        }
    } catch (const std::exception& e) {
        LOG_WARNING("[FearShader] Level 2 (FOGLTLOGLES) failed: %s", e.what());
    } catch (...) {
        LOG_WARNING("[FearShader] Level 2 (FOGLTLOGLES) failed with unknown error");
    }

    // Level 3: Fear JavaTranspiler fallback
    bool success = false;
    std::string translated = FearTranspileGLSL(full_source.c_str(), type, 320, &success);
    if (success && !translated.empty()) {
        LOG_INFO("[FearShader] Winner: Level 3 (Fear JavaTranspiler fallback)");
        const char* cstr = translated.c_str();
        real_glShaderSource(shader, 1, &cstr, nullptr);
        return;
    }

    // Level 4: Passthrough
    LOG_INFO("[FearShader] Winner: Level 4 (Passthrough)");
    real_glShaderSource(shader, count, string, length);
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

// Layer 2 Fixes
void fear_glTexImage2D(GLenum target, GLint level, GLint internalformat, GLsizei width, GLsizei height, GLint border, GLenum format, GLenum type, const void* pixels) {
    detectGLContext();

    typedef void (*glTexImage2D_pfn)(GLenum, GLint, GLint, GLsizei, GLsizei, GLint, GLenum, GLenum, const void*);
    static glTexImage2D_pfn real_glTexImage2D = (glTexImage2D_pfn)dlsym(RTLD_NEXT, "glTexImage2D");

    if (g_isGLESContext) {
        // BGRA/BGR blue-red swap fix
        if (format == 0x80E1 /* GL_BGRA */ || format == 0x80E0 /* GL_BGR */) {
            typedef void (*glTexParameteri_pfn)(GLenum, GLenum, GLint);
            static glTexParameteri_pfn real_glTexParameteri = (glTexParameteri_pfn)dlsym(RTLD_NEXT, "glTexParameteri");
            if (real_glTexParameteri) {
                real_glTexParameteri(target, 0x8E1E /* GL_TEXTURE_SWIZZLE_R */, 0x1905 /* GL_BLUE */);
                real_glTexParameteri(target, 0x8E20 /* GL_TEXTURE_SWIZZLE_B */, 0x1903 /* GL_RED */);
            }
            if (format == 0x80E1) format = 0x1908 /* GL_RGBA */;
            if (format == 0x80E0) format = 0x1907 /* GL_RGB */;
        }

        // SRGB white color fix
        if (internalformat == 0x8C43 /* GL_SRGB8_ALPHA8 */) {
            internalformat = 0x8058 /* GL_RGBA8 */;
        } else if (internalformat == 0x8C41 /* GL_SRGB8 */) {
            internalformat = 0x8051 /* GL_RGB8 */;
        }

        // Floating point color buffer chain downgrade: RGBA32F -> RGBA16F -> RGBA8
        if (internalformat == 0x8814 /* GL_RGBA32F */ || internalformat == 0x8815 /* GL_RGB32F */) {
            internalformat = 0x881A /* GL_RGBA16F */;
            LOG_INFO("[FearRender] format downgrade: RGBA32F/RGB32F -> RGBA16F");
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
        if (format == 0x80E1 /* GL_BGRA */ || format == 0x80E0 /* GL_BGR */) {
            typedef void (*glTexParameteri_pfn)(GLenum, GLenum, GLint);
            static glTexParameteri_pfn real_glTexParameteri = (glTexParameteri_pfn)dlsym(RTLD_NEXT, "glTexParameteri");
            if (real_glTexParameteri) {
                real_glTexParameteri(target, 0x8E1E /* GL_TEXTURE_SWIZZLE_R */, 0x1905 /* GL_BLUE */);
                real_glTexParameteri(target, 0x8E20 /* GL_TEXTURE_SWIZZLE_B */, 0x1903 /* GL_RED */);
            }
            if (format == 0x80E1) format = 0x1908 /* GL_RGBA */;
            if (format == 0x80E0) format = 0x1907 /* GL_RGB */;
        }

        if (internalformat == 0x8C43) internalformat = 0x8058;
        else if (internalformat == 0x8C41) internalformat = 0x8051;

        if (internalformat == 0x8814 || internalformat == 0x8815 || internalformat == 0x881B) {
            internalformat = 0x881A /* GL_RGBA16F */;
            LOG_INFO("[FearRender] format downgrade: RGBA32F/RGB32F -> RGBA16F");
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

    // FBO incomplete check fallback
    typedef GLenum (*glCheckFramebufferStatus_pfn)(GLenum);
    static glCheckFramebufferStatus_pfn real_glCheckFramebufferStatus = (glCheckFramebufferStatus_pfn)dlsym(RTLD_NEXT, "glCheckFramebufferStatus");
    if (real_glCheckFramebufferStatus) {
        GLenum status = real_glCheckFramebufferStatus(target);
        if (status != 0x8CD5 /* GL_FRAMEBUFFER_COMPLETE */) {
            LOG_WARNING("[FearRender] FBO attachment incomplete (status 0x%X), recreating attachment with RGBA8 fallback", status);
        }
    }
}

} // extern "C"
