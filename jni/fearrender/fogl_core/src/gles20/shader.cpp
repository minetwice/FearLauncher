#include "es/state_tracking.hpp"
#include "shader/cache.hpp"
#include "shader/converter.hpp"
#include "shader/utils.hpp"
#include "gles20/main.hpp"
#include "gles20/shader_overrides.hpp"
#include "main.hpp"
#include "utils/env.hpp"

#include <GLES2/gl2.h>
#include <stdexcept>
#include <string>

static int g_failedShaderCount = 0;

void GLES20::registerShaderOverrides() {
    REGISTEROV(glShaderSource);
    REGISTEROV(glCompileShader);
    REGISTEROV(glLinkProgram);
    REGISTEROV(glUseProgram);
    REGISTEROV(glDeleteProgram);
}

inline size_t currentKey = 0;

void OV_glShaderSource(GLuint shader, GLsizei count, const GLchar* const* string, const GLint* length) {
    std::string combinedSource;
    combineSources(count, string, length, combinedSource);

    currentKey = ShaderConverter::Cache::getHash(combinedSource);

    if (combinedSource.empty()) {
        LOGW("glShaderSource was called without a shader source? Skipping...");
        return;
    }

    int version = 0;
    std::string profile = "";
    if (!getShaderVersion(combinedSource, version, profile)) {
        LOGW("Shader with no version preprocessor!");
    }

    // FIX 1: Compute shader transformation for Mali / Sodium
    if (combinedSource.find("layout(local_size_") != std::string::npos ||
        combinedSource.find("buffer") != std::string::npos ||
        combinedSource.find("layout(std430") != std::string::npos) {

        bool fixed = false;
        if (combinedSource.find("uint i = ivec2(gl_FragCoord.xy).x;") != std::string::npos) {
            replaceAll(combinedSource, "uint i = ivec2(gl_FragCoord.xy).x;", "uint i = gl_GlobalInvocationID.x;");
            fixed = true;
        }
        if (combinedSource.find("gl_FragCoord.xy") != std::string::npos) {
            replaceAll(combinedSource, "gl_FragCoord.xy", "vec2(gl_GlobalInvocationID.xy)");
            fixed = true;
        }
        if (combinedSource.find("gl_FragCoord") != std::string::npos) {
            replaceAll(combinedSource, "gl_FragCoord", "vec4(gl_GlobalInvocationID.xy, 0.0, 1.0)");
            fixed = true;
        }

        if (combinedSource.find("layout(local_size_") == std::string::npos) {
            std::string layout_qualifier = "\n#ifndef QUASAR_COMPUTE_LAYOUT\n#define QUASAR_COMPUTE_LAYOUT\nlayout(local_size_x = 1, local_size_y = 1, local_size_z = 1) in;\n#endif\n";
            size_t main_pos = combinedSource.find("void main");
            if (main_pos != std::string::npos) {
                combinedSource.insert(main_pos, layout_qualifier);
                fixed = true;
            }
        }

        if (fixed) {
            LOGI("[FearRender] Compute shader fixed: gl_FragCoord -> gl_GlobalInvocationID");
        }
    }

    if (ShaderConverter::Cache::isShaderInCache(currentKey)) {
        std::string cachedSource = ShaderConverter::Cache::getCachedShaderSource(currentKey);

        if (cachedSource.empty()) {
            LOGW("Returned cache source is empty! Invalidating cache hit.");
            ShaderConverter::Cache::invalidateShaderCache(currentKey);
            goto convert_and_fix;
        }

        const GLchar* newSource = cachedSource.c_str();

        if (getEnvironmentVar("LIBGL_VGPU_DUMP") == "1") {
            LOGI("Cache hit! Shader %u (%zu) was found in cache.", shader, currentKey);
        }

        glShaderSource(shader, 1, &newSource, nullptr);
        return;
    }

convert_and_fix:
    if (profile != "es") {
        try {
            ShaderConverter::convertAndFix(getKindFromShader(shader), combinedSource);
            const GLchar* newSource = combinedSource.c_str();
            glShaderSource(shader, 1, &newSource, nullptr);
            ShaderConverter::Cache::putShaderInCache(currentKey, combinedSource);
            return;
        } catch (const std::exception& e) {
            LOGE("[FearRender] Shader conversion failed: %s, passing through", e.what());
        } catch (...) {
            LOGE("[FearRender] Shader conversion unknown error, passing through");
        }
    }

    glShaderSource(shader, count, string, length);
}

void OV_glCompileShader(GLuint shader) {
    try {
        glCompileShader(shader);

        GLint success = 0;
        glGetShaderiv(shader, GL_COMPILE_STATUS, &success);
        if (success != GL_TRUE) {
            GLint logLength = 0;
            glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &logLength);
            std::string logMsg = "Unknown compile failure";
            if (logLength > 0) {
                GLchar* bufLog = new GLchar[logLength];
                glGetShaderInfoLog(shader, logLength, nullptr, bufLog);
                logMsg = bufLog;
                delete[] bufLog;
            }

            LOGE("[FearRender] Shader compile failed: %s", logMsg.c_str());

            GLint shaderType = GL_FRAGMENT_SHADER;
            glGetShaderiv(shader, GL_SHADER_TYPE, &shaderType);

            const GLchar* fallbackSrc = nullptr;
            if (shaderType == 0x91B9 /* GL_COMPUTE_SHADER */) {
                fallbackSrc = "#version 310 es\nlayout(local_size_x = 1) in;\nvoid main() {}\n";
            } else if (shaderType == 0x8B31 /* GL_VERTEX_SHADER */) {
                fallbackSrc = "#version 300 es\nlayout(location=0) in vec4 pos;\nvoid main() { gl_Position = pos; }\n";
            } else {
                fallbackSrc = "#version 300 es\nprecision mediump float;\nout vec4 fc;\nvoid main() { fc = vec4(1.0, 0.0, 1.0, 1.0); }\n";
            }

            glShaderSource(shader, 1, &fallbackSrc, nullptr);
            glCompileShader(shader);

            g_failedShaderCount++;
            if (g_failedShaderCount > 10) {
                LOGW("[FearRender] Pack compatibility low - many fallbacks used");
            }
        }
    } catch (const std::exception& e) {
        LOGE("[FearRender] Shader compile exception caught: %s", e.what());
    } catch (...) {
        LOGE("[FearRender] Shader compile unknown exception caught");
    }
}

void OV_glLinkProgram(GLuint program) {
    try {
        glLinkProgram(program);

        GLint success = 0;
        glGetProgramiv(program, GL_LINK_STATUS, &success);
        if (success != GL_TRUE) {
            GLint logLength = 0;
            glGetProgramiv(program, GL_INFO_LOG_LENGTH, &logLength);
            std::string logMsg = "Unknown link failure";
            if (logLength > 0) {
                GLchar* bufLog = new GLchar[logLength];
                glGetProgramInfoLog(program, logLength, nullptr, bufLog);
                logMsg = bufLog;
                delete[] bufLog;
            }

            LOGE("[FearRender] Program link failed: %s", logMsg.c_str());
        }
    } catch (const std::exception& e) {
        LOGE("[FearRender] Program link exception caught: %s", e.what());
    } catch (...) {
        LOGE("[FearRender] Program link unknown exception caught");
    }
}

void OV_glUseProgram(GLuint program) {
    glUseProgram(program);

    trackedStates->currentlyUsedProgram = program;
}

void OV_glDeleteProgram(GLuint program) {
    glDeleteProgram(program);

    if (trackedStates->currentlyUsedProgram == program) {
        trackedStates->currentlyUsedProgram = 0;
    }
}
