#ifndef FEAR_SHADER_TRANSLATOR_H
#define FEAR_SHADER_TRANSLATOR_H

#include <string>
#include <GLES3/gl32.h>

#include <vector>
#include <cstdint>

// Core GLSL Translation function
std::string FearTranslateGLSL(
    const char* sourceCode,
    GLenum shaderType,
    bool* translationSuccess
);

// Module 1: GLSL to SPIR-V Cross-Compiler Pipeline
std::vector<uint32_t> FearCompileGLSLToSPIRV(
    const char* sourceCode,
    GLenum shaderType,
    const char* shaderName,
    bool* compileSuccess
);

// String Helpers
void replaceAll(std::string& str, const std::string& from, const std::string& to);
void insertAfterLine(std::string& code, const std::string& targetLine, const std::string& insertText);
void removeLinesContaining(std::string& code, const std::string& substring);

// Shader Type Helpers
bool isVertexShader(GLenum type);
bool isFragmentShader(GLenum type);
bool isGeometryShader(GLenum type);
bool isComputeShader(GLenum type);

#endif // FEAR_SHADER_TRANSLATOR_H
