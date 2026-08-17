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

    int version = 0; // useless
    std::string profile = "";
    if (!getShaderVersion(combinedSource, version, profile)) {
        throw std::runtime_error("Shader with no version preprocessor!");
    }

    if (ShaderConverter::Cache::isShaderInCache(currentKey)) {
        std::string cachedSource = ShaderConverter::Cache::getCachedShaderSource(currentKey);

        if (cachedSource.empty()) {
            LOGW("Returned cache source is empty! That's interesting....");
            LOGW("Gonna act like it's a cache miss!");
            ShaderConverter::Cache::invalidateShaderCache(currentKey);

            goto convert_and_fix;
        }

        const GLchar* newSource = cachedSource.c_str();

        if (getEnvironmentVar("LIBGL_VGPU_DUMP") == "1") {
            LOGI("Cache hit! Shader %u (%zu) was found in cache.", shader, currentKey);
            LOGI(
                "Cached source of shader with type '%s':",
                getKindStringFromKind(getKindFromShader(shader))
            );
            LOGI("%s", newSource);
        }

        glShaderSource(shader, 1, &newSource, nullptr);

        return;
    }

convert_and_fix:
    if (profile != "es") {
        // if (profile == "core" || profile == "compatibility") LOGI("Shader is on '%s' profile! Let's see how this goes...", profile.c_str());

        ShaderConverter::convertAndFix(getKindFromShader(shader), combinedSource);

        const GLchar* newSource = combinedSource.c_str();
        glShaderSource(shader, 1, &newSource, nullptr);

        ShaderConverter::Cache::putShaderInCache(currentKey, combinedSource);
    } else {
        LOGI("Shader already ESSL, no need to convert");
        glShaderSource(shader, 1, string, nullptr);
    }
}

void OV_glCompileShader(GLuint shader) {
    glCompileShader(shader);

    GLint success = 0;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &success);
    if (success != GL_TRUE) {
        GLint logLength = 0;
        glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &logLength);
        if (logLength > 0) {
            GLchar* bufLog = new GLchar[logLength];
            glGetShaderInfoLog(shader, logLength, nullptr, bufLog);

            LOGE("Compile error for shader %u:", shader);
            LOGE("%s", bufLog);

            delete[] bufLog;
        }

        GLint sourceLength = 0;
        glGetShaderiv(shader, GL_SHADER_SOURCE_LENGTH, &sourceLength);
        if (sourceLength > 0) {
            GLchar* source = new GLchar[sourceLength];
            glGetShaderSource(shader, sourceLength, nullptr, source);

            LOGE("Shader %u source:", shader);
            LOGE("%s", source);

            delete[] source;
        }

        if (ShaderConverter::Cache::invalidateShaderCache(currentKey)) LOGI("Shader invalidated as it failed to compile.");
        throw std::runtime_error("Failed to compile shader!");
    }
}

void OV_glLinkProgram(GLuint program) {
    glLinkProgram(program);

    GLint success = 0;
    glGetProgramiv(program, GL_LINK_STATUS, &success);
    if (success != GL_TRUE) {
        GLint logLength = 0;
        glGetProgramiv(program, GL_INFO_LOG_LENGTH, &logLength);

        if (logLength > 0) {
            GLchar* bufLog = new GLchar[logLength];
            glGetProgramInfoLog(program, logLength, nullptr, bufLog);

            LOGE("Link error for program %u:", program);
            LOGE("%s", bufLog);

            delete[] bufLog;
        }

        throw std::runtime_error("Failed to link program!");
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
