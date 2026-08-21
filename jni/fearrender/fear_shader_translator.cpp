#include "fear_shader_translator.h"
#include "fear_shader_logger.h"
#include "shader/converter.hpp"
#include <shaderc/shaderc.hpp>

void replaceAll(std::string& str, const std::string& from, const std::string& to) {
    if (from.empty()) return;
    size_t startPos = 0;
    while ((startPos = str.find(from, startPos)) != std::string::npos) {
        str.replace(startPos, from.length(), to);
        startPos += to.length();
    }
}

std::string FearTranslateGLSL(const char* source, GLenum shaderType, bool* success) {
    if (!source || !success) {
        if (success) *success = false;
        return "";
    }

    *success = false;
    std::string src(source);

    shaderc_shader_kind kind = shaderc_glsl_fragment_shader;
    if (shaderType == GL_VERTEX_SHADER) kind = shaderc_glsl_vertex_shader;
    else if (shaderType == GL_COMPUTE_SHADER) kind = shaderc_glsl_compute_shader;

    try {
        ShaderConverter::convertAndFix(kind, src);
        if (!src.empty()) {
            *success = true;
            return src;
        }
    } catch (const std::exception& e) {
        LOG_ERROR("[FearRender] FearTranslateGLSL failed: %s", e.what());
    } catch (...) {
        LOG_ERROR("[FearRender] FearTranslateGLSL failed: unknown error");
    }

    return "";
}
