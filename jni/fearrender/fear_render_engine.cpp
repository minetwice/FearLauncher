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
static int g_failedShaderCountEngine = 0;

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
        const unsigned char* vendor = real_glGetString(GL_VENDOR);
        const unsigned char* sh_ver = real_glGetString(0x8B8C /* GL_SHADING_LANGUAGE_VERSION */);

        if (!version) return; // GL context not ready yet

        g_contextChecked = true;

        std::string ver_str = (const char*)version;
        std::string rend_str = renderer ? (const char*)renderer : "";
        std::string vend_str = vendor ? (const char*)vendor : "";
        std::string sh_str = sh_ver ? (const char*)sh_ver : "";

        std::string combined = ver_str + " " + rend_str + " " + vend_str + " " + sh_str;
        std::string lower_c = combined;
        std::transform(lower_c.begin(), lower_c.end(), lower_c.begin(), ::tolower);

        bool is_mobile = (lower_c.find("openltw") != std::string::npos ||
                          lower_c.find("ltw") != std::string::npos ||
                          lower_c.find("fogltlogles") != std::string::npos ||
                          lower_c.find("mali") != std::string::npos ||
                          lower_c.find("adreno") != std::string::npos ||
                          lower_c.find("powervr") != std::string::npos ||
                          lower_c.find("snapdragon") != std::string::npos ||
                          lower_c.find("tegra") != std::string::npos ||
                          lower_c.find("llvmpipe") != std::string::npos ||
                          lower_c.find("virgl") != std::string::npos ||
                          lower_c.find("opengl es") != std::string::npos ||
                          lower_c.find("glsl es") != std::string::npos);

        if (is_mobile) {
            g_isGLESContext = true;
            LOG_INFO("[Quasar] Mobile context detected via renderer string: %s (isGLES=true es=3.2)", rend_str.c_str());
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

    // FIX: Compute shader compatibility
    // Only fix if this is ACTUALLY a compute shader (by type) AND
    // the source genuinely has issues. Don't blindly replace.
    if (type == GL_COMPUTE_SHADER) {
        bool needsFix = false;

        // Check if compute shader is missing local_size layout
        if (full_source.find("layout(local_size_") == std::string::npos &&
            full_source.find("layout (local_size_") == std::string::npos) {
            // Missing workgroup size - inject default
            std::string layoutQualifier =
                "\n#ifndef FEAR_COMPUTE_LAYOUT\n"
                "#define FEAR_COMPUTE_LAYOUT\n"
                "layout(local_size_x = 1, local_size_y = 1, local_size_z = 1) in;\n"
                "#endif\n";
            size_t mainPos = full_source.find("void main");
            if (mainPos != std::string::npos) {
                full_source.insert(mainPos, layoutQualifier);
                needsFix = true;
            }
        }

        // Check if compute shader incorrectly uses gl_FragCoord
        // Only fix if gl_GlobalInvocationID is NOT already present
        if (full_source.find("gl_GlobalInvocationID") == std::string::npos) {
            size_t fragCoordPos = full_source.find("gl_FragCoord");
            bool inComment = false;
            bool inString = false;
            bool actuallyUsed = false;

            // Walk the source to check if gl_FragCoord is in actual code
            for (size_t i = 0; i < full_source.size(); i++) {
                if (i == fragCoordPos && !inComment && !inString) {
                    actuallyUsed = true;
                    break;
                }
                if (i < full_source.size() - 1) {
                    if (!inComment && !inString &&
                        full_source[i] == '/' && full_source[i+1] == '/') {
                        inComment = true;
                    }
                    if (inComment && full_source[i] == '\n') {
                        inComment = false;
                    }
                    if (!inComment && full_source[i] == '"') {
                        inString = !inString;
                    }
                }
            }

            if (actuallyUsed) {
                replaceAll(full_source, "gl_FragCoord.xy",
                    "vec2(float(gl_GlobalInvocationID.x), float(gl_GlobalInvocationID.y))");
                replaceAll(full_source, "gl_FragCoord.x",
                    "float(gl_GlobalInvocationID.x)");
                replaceAll(full_source, "gl_FragCoord.y",
                    "float(gl_GlobalInvocationID.y)");
                replaceAll(full_source, "gl_FragCoord",
                    "vec4(float(gl_GlobalInvocationID.x), float(gl_GlobalInvocationID.y), 0.0, 1.0)");
                needsFix = true;
            }
        }

        if (needsFix) {
            LOG_INFO("[FearRender] Compute shader compatibility fix applied");
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

    // Level 3-5: Strategy Ladder (L3 full transform -> L5 minimal transform)
    {
        int winningLevel = 0;
        bool compileSuccess = false;

        std::string transformed = executeStrategyL1ToL8(
            full_source.c_str(), type, &winningLevel, &compileSuccess);

        if (compileSuccess && !transformed.empty()) {
            LOG_INFO("[FearShader] Winner: Level %d (Strategy Ladder)", winningLevel);
            const char* cstr = transformed.c_str();
            real_glShaderSource(shader, 1, &cstr, nullptr);
            return;
        }
    }

    // Level 4: Passthrough
    LOG_INFO("[FearShader] Winner: Level 4 (Passthrough)");
    real_glShaderSource(shader, count, string, length);
}

void fear_glCompileShader(GLuint shader) {
    typedef void (*glCompileShader_pfn)(GLuint);
    static glCompileShader_pfn real_glCompileShader = (glCompileShader_pfn)dlsym(RTLD_NEXT, "glCompileShader");

    try {
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
            std::string log_msg = "Unknown compile failure";
            if (log_len > 0) {
                char* log_buffer = (char*)malloc(log_len);
                typedef void (*glGetShaderInfoLog_pfn)(GLuint, GLsizei, GLsizei*, GLchar*);
                static glGetShaderInfoLog_pfn real_glGetShaderInfoLog = (glGetShaderInfoLog_pfn)dlsym(RTLD_NEXT, "glGetShaderInfoLog");
                if (real_glGetShaderInfoLog) {
                    real_glGetShaderInfoLog(shader, log_len, nullptr, log_buffer);
                    log_msg = log_buffer;
                }
                free(log_buffer);
            }
            LOG_ERROR("[FearRender] Shader compile failed: %s", log_msg.c_str());

            GLint shaderType = GL_FRAGMENT_SHADER;
            if (real_glGetShaderiv) {
                real_glGetShaderiv(shader, GL_SHADER_TYPE, &shaderType);
            }

            const char* fallbackSrc = nullptr;
            if (shaderType == 0x91B9 /* GL_COMPUTE_SHADER */) {
                fallbackSrc = "#version 310 es\nlayout(local_size_x = 1) in;\nvoid main() {}\n";
            } else if (shaderType == 0x8B31 /* GL_VERTEX_SHADER */) {
                fallbackSrc = "#version 300 es\nlayout(location=0) in vec4 pos;\nvoid main() { gl_Position = pos; }\n";
            } else {
                fallbackSrc = "#version 300 es\nprecision mediump float;\nout vec4 fc;\nvoid main() { fc = vec4(1.0, 0.0, 1.0, 1.0); }\n";
            }

            typedef void (*glShaderSource_pfn)(GLuint, GLsizei, const GLchar* const*, const GLint*);
            static glShaderSource_pfn real_glShaderSource = (glShaderSource_pfn)dlsym(RTLD_NEXT, "glShaderSource");
            if (real_glShaderSource && real_glCompileShader) {
                real_glShaderSource(shader, 1, &fallbackSrc, nullptr);
                real_glCompileShader(shader);
            }

            g_failedShaderCountEngine++;
            if (g_failedShaderCountEngine > 10) {
                LOG_WARNING("[FearRender] Pack compatibility low - many fallbacks used");
            }
        } else {
            LOG_INFO("[FearRender] Shader compiled successfully");
        }
    } catch (...) {
        LOG_ERROR("[FearRender] Exception during fear_glCompileShader");
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

        // RGB9_E5 (0x8F99) -> RGBA16F / RGBA8
        if (internalformat == 0x8F99 /* GL_RGB9_E5 */) {
            internalformat = 0x881A /* GL_RGBA16F */;
            LOG_INFO("[FearRender] format downgrade: RGB9_E5 -> RGBA16F");
        }

        // SRGB white color fix
        if (internalformat == 0x8C43 /* GL_SRGB8_ALPHA8 */) {
            internalformat = 0x8058 /* GL_RGBA8 */;
        } else if (internalformat == 0x8C41 /* GL_SRGB8 */) {
            internalformat = 0x8051 /* GL_RGB8 */;
        }

        // Floating point color buffer chain downgrade: RGBA32F -> RGBA16F -> RGBA8
        if (internalformat == 0x8814 /* GL_RGBA32F */ || internalformat == 0x8815 /* GL_RGB32F */) {
            typedef const unsigned char* (*glGetString_pfn)(GLenum);
            static glGetString_pfn real_glGetString = (glGetString_pfn)dlsym(RTLD_NEXT, "glGetString");
            const char* exts = real_glGetString ? (const char*)real_glGetString(GL_EXTENSIONS) : nullptr;
            bool hasFloatColorBuffer = exts && (strstr(exts, "GL_EXT_color_buffer_float") || strstr(exts, "GL_EXT_color_buffer_half_float"));

            if (hasFloatColorBuffer) {
                internalformat = 0x881A /* GL_RGBA16F */;
                LOG_INFO("[FearRender] format downgrade: RGBA32F/RGB32F -> RGBA16F (EXT_color_buffer_float OK)");
            } else {
                internalformat = 0x8058 /* GL_RGBA8 */;
                LOG_INFO("[FearRender] format downgrade: RGBA32F/RGB32F -> RGBA8 (no float color buffer support)");
            }
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

void fear_glFramebufferTexture2D(GLenum target, GLenum attachment,
        GLenum textarget, GLuint texture, GLint level) {
    detectGLContext();

    typedef void (*glFramebufferTexture2D_pfn)(GLenum, GLenum, GLenum, GLuint, GLint);
    static glFramebufferTexture2D_pfn real_glFramebufferTexture2D =
        (glFramebufferTexture2D_pfn)dlsym(RTLD_NEXT, "glFramebufferTexture2D");

    typedef GLenum (*glCheckFramebufferStatus_pfn)(GLenum);
    static glCheckFramebufferStatus_pfn real_glCheckFramebufferStatus =
        (glCheckFramebufferStatus_pfn)dlsym(RTLD_NEXT, "glCheckFramebufferStatus");

    typedef void (*glBindTexture_pfn)(GLenum, GLuint);
    static glBindTexture_pfn real_glBindTexture =
        (glBindTexture_pfn)dlsym(RTLD_NEXT, "glBindTexture");

    typedef void (*glGetTexLevelParameteriv_pfn)(GLenum, GLint, GLenum, GLint*);
    static glGetTexLevelParameteriv_pfn real_glGetTexLevelParameteriv =
        (glGetTexLevelParameteriv_pfn)dlsym(RTLD_NEXT, "glGetTexLevelParameteriv");

    if (!real_glFramebufferTexture2D) return;

    // First attempt: attach as-is
    real_glFramebufferTexture2D(target, attachment, textarget, texture, level);

    if (!g_isGLESContext || !real_glCheckFramebufferStatus) return;

    // Check status
    GLenum status = real_glCheckFramebufferStatus(target);
    int attempt = 1;

    while (status != 0x8CD5 /* GL_FRAMEBUFFER_COMPLETE */ && attempt <= 3) {
        LOG_WARNING("[Quasar] FBO repair attempt %d: status=0x%X attachment=0x%X",
                    attempt, status, attachment);

        // Only attempt repair on color attachments
        if (attachment >= 0x8CE0 /* GL_COLOR_ATTACHMENT0 */ &&
            attachment <= 0x8CEF /* GL_COLOR_ATTACHMENT15 */) {

            // Query the texture's current internal format
            GLint texFormat = 0;
            if (real_glBindTexture && real_glGetTexLevelParameteriv && texture != 0) {
                GLenum bindTarget = (textarget == 0x8D63 /* GL_TEXTURE_2D_ARRAY */)
                    ? 0x8D63 : 0x0DE1 /* GL_TEXTURE_2D */;
                real_glBindTexture(bindTarget, texture);
                real_glGetTexLevelParameteriv(bindTarget, 0,
                    0x8E1C /* GL_TEXTURE_INTERNAL_FORMAT */, &texFormat);
            }

            // Downgrade the format
            GLint newFormat = 0; // 0 = can't downgrade further
            switch (texFormat) {
                case 0x8814 /* GL_RGBA32F */:
                    newFormat = 0x881A /* GL_RGBA16F */;
                    break;
                case 0x881A /* GL_RGBA16F */:
                    newFormat = 0x8058 /* GL_RGBA8 */;
                    break;
                case 0x881B /* GL_RGB16F */:
                    newFormat = 0x8051 /* GL_RGB8 */;
                    break;
                case 0x8815 /* GL_RGB32F */:
                    newFormat = 0x881A /* GL_RGBA16F */;
                    break;
                case 0x8F99 /* GL_RGB9_E5 */:
                    newFormat = 0x8058 /* GL_RGBA8 */;
                    break;
                default:
                    newFormat = 0x8058 /* GL_RGBA8 */; // fallback to safest
                    break;
            }

            if (newFormat != 0 && texture != 0) {
                LOG_INFO("[Quasar] FBO repair: downgrading texture format "
                         "0x%X -> 0x%X for attachment 0x%X",
                         texFormat, newFormat, attachment);

                real_glFramebufferTexture2D(target, attachment, textarget, 0, level);

                // Re-allocate the texture with the safer format
                GLint texWidth = 0, texHeight = 0;
                if (real_glBindTexture && real_glGetTexLevelParameteriv) {
                    GLenum bindTarget = (textarget == 0x8D63)
                        ? 0x8D63 : 0x0DE1;
                    real_glBindTexture(bindTarget, texture);
                    real_glGetTexLevelParameteriv(bindTarget, 0,
                        0x1000 /* GL_TEXTURE_WIDTH */, &texWidth);
                    real_glGetTexLevelParameteriv(bindTarget, 0,
                        0x1001 /* GL_TEXTURE_HEIGHT */, &texHeight);
                }

                // Re-upload with safer format (via our wrapper)
                if (texWidth > 0 && texHeight > 0) {
                    fear_glTexImage2D(textarget, 0, newFormat,
                        texWidth, texHeight, 0,
                        0x1908 /* GL_RGBA */, 0x1406 /* GL_UNSIGNED_BYTE */, nullptr);
                }

                // Re-attach
                real_glFramebufferTexture2D(target, attachment, textarget, texture, level);
            }
        } else {
            // Depth attachment: downgrade depth format
            LOG_INFO("[Quasar] FBO repair: depth attachment, detaching non-float depth");
            real_glFramebufferTexture2D(target, attachment, textarget, 0, level);
            real_glFramebufferTexture2D(target, attachment, textarget, texture, level);
        }

        status = real_glCheckFramebufferStatus(target);
        attempt++;
    }

    if (status == 0x8CD5) {
        LOG_INFO("[Quasar] FBO status reached COMPLETE after %d attempts", attempt - 1);
    } else {
        LOG_ERROR("[Quasar] FBO still INCOMPLETE (0x%X) after %d attempts, rendering may be broken",
                  status, attempt - 1);
    }
}

// Export standard GL ABI symbols
void glTexImage2D(GLenum target, GLint level, GLint internalformat, GLsizei width, GLsizei height, GLint border, GLenum format, GLenum type, const void* pixels) {
    fear_glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
}

void glTexImage3D(GLenum target, GLint level, GLint internalformat, GLsizei width, GLsizei height, GLsizei depth, GLint border, GLenum format, GLenum type, const void* pixels) {
    fear_glTexImage3D(target, level, internalformat, width, height, depth, border, format, type, pixels);
}

void glRenderbufferStorage(GLenum target, GLenum internalformat, GLsizei width, GLsizei height) {
    fear_glRenderbufferStorage(target, internalformat, width, height);
}

void glFramebufferTexture2D(GLenum target, GLenum attachment, GLenum textarget, GLuint texture, GLint level) {
    fear_glFramebufferTexture2D(target, attachment, textarget, texture, level);
}

} // extern "C"
