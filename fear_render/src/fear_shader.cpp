#include "fear_shader.h"
#include <regex>
#include <android/log.h>
#include <sstream>

#define LOG_TAG "FEAR_SHADER_COMPILER"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

std::string FearShaderCompiler::translateGLSL(const std::string& source, int shaderType) {
    if (source.empty()) return source;

    std::string result = source;

    // 1. Parse and update the GLSL Version Directive
    // Replace standard Desktop "#version 150", "#version 330", "#version 460 core" etc. with Android ES 3.2
    std::regex version_regex("#version\\s+[0-9]+(\\s+core|\\s+compatibility)?");
    result = std::regex_replace(result, version_regex, "#version 320 es");

    // 2. Inject default floating-point and integer precisions which are required in OpenGL ES
    std::stringstream injected;
    injected << "#version 320 es\n"
             << "#extension GL_OES_standard_derivatives : enable\n"
             << "#extension GL_EXT_gpu_shader5 : enable\n"
             << "precision highp float;\n"
             << "precision highp int;\n"
             << "precision highp sampler2D;\n"
             << "precision highp sampler2DArray;\n";

    size_t version_pos = result.find("#version 320 es");
    if (version_pos != std::string::npos) {
        result.replace(version_pos, 15, injected.str());
    } else {
        result = injected.str() + result;
    }

    // 3. Strip layout qualifiers that are not supported in OpenGL ES 3.2 but common in Desktop OpenGL
    std::regex layout_index_regex("layout\\s*\\([^)]*index\\s*=\\s*[0-9]+[^)]*\\)");
    result = std::regex_replace(result, layout_index_regex, "");

    // 4. Translate unsupported Double Precision types (since ARM GPUs lack 64-bit float support in hardware)
    result = std::regex_replace(result, std::regex("\\bdouble\\b"), "float");
    result = std::regex_replace(result, std::regex("\\bdvec2\\b"), "vec2");
    result = std::regex_replace(result, std::regex("\\bdvec3\\b"), "vec3");
    result = std::regex_replace(result, std::regex("\\bdvec4\\b"), "vec4");
    result = std::regex_replace(result, std::regex("\\bdmat2\\b"), "mat2");
    result = std::regex_replace(result, std::regex("\\bdmat3\\b"), "mat3");
    result = std::regex_replace(result, std::regex("\\bdmat4\\b"), "mat4");

    // 5. Strip 'noperspective' interpolation qualifier which is unsupported in OpenGL ES
    result = std::regex_replace(result, std::regex("\\bnoperspective\\b"), "flat");

    // 6. Fix legacy texture call built-ins
    result = std::regex_replace(result, std::regex("\\btexture2D\\b"), "texture");
    result = std::regex_replace(result, std::regex("\\btextureCube\\b"), "texture");
    result = std::regex_replace(result, std::regex("\\btexture2DLod\\b"), "textureLod");
    result = std::regex_replace(result, std::regex("\\btextureCubeLod\\b"), "textureLod");

    // 7. Prevent duplicate extension declarations or other version statements
    size_t second_version = result.find("#version", 15);
    while (second_version != std::string::npos) {
        size_t end_line = result.find('\n', second_version);
        if (end_line != std::string::npos) {
            result.erase(second_version, end_line - second_version + 1);
        } else {
            result.erase(second_version);
        }
        second_version = result.find("#version", 15);
    }

    return result;
}
