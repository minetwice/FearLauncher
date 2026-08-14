#ifndef FEAR_SHADER_TRANSLATOR_H
#define FEAR_SHADER_TRANSLATOR_H

#include <string>
#include <GLES3/gl32.h>

// Core GLSL Translation function
std::string FearTranslateGLSL(
    const char* sourceCode,
    GLenum shaderType,
    bool* translationSuccess
);

// String Helpers
void replaceAll(std::string& str, const std::string& from, const std::string& to);
void insertAfterLine(std::string& code, const std::string& targetLine, const std::string& insertText);
void removeLinesContaining(std::string& code, const std::string& substring);

// Shader Type Helpers
bool isVertexShader(GLenum type);
bool isFragmentShader(GLenum type);
bool isGeometryShader(GLenum type);

#endif // FEAR_SHADER_TRANSLATOR_H
