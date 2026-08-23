#ifndef FEAR_GLSL_TRANSPILER_H
#define FEAR_GLSL_TRANSPILER_H

#include <string>
#include <GLES3/gl32.h>

bool transpileGLSLToGLES(const std::string& input, GLenum shaderType, std::string& output);
const char* getShaderKindString(GLenum shaderType);

#endif // FEAR_GLSL_TRANSPILER_H
