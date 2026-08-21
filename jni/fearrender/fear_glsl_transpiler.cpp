#include "fear_glsl_transpiler.h"
#include "shader/converter.hpp"
#include <shaderc/shaderc.hpp>

bool transpileGLSLToGLES(const std::string& input, GLenum shaderType,
                          std::string& output) {
    shaderc_shader_kind kind = shaderc_glsl_fragment_shader;
    if (shaderType == GL_VERTEX_SHADER) kind = shaderc_glsl_vertex_shader;
    else if (shaderType == GL_COMPUTE_SHADER) kind = shaderc_glsl_compute_shader;

    try {
        output = input;
        ShaderConverter::convertAndFix(kind, output);
        return !output.empty();
    } catch (...) {
        return false;
    }
}

const char* getShaderKindString(GLenum shaderType) {
    switch (shaderType) {
        case GL_VERTEX_SHADER: return "vertex";
        case GL_FRAGMENT_SHADER: return "fragment";
        case GL_COMPUTE_SHADER: return "compute";
        default: return "unknown";
    }
}
