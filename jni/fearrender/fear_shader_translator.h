#ifndef FEAR_SHADER_TRANSLATOR_H
#define FEAR_SHADER_TRANSLATOR_H

#include <string>
#include <GLES3/gl32.h>

std::string FearTranslateGLSL(const char* source, GLenum shaderType, bool* success);
void replaceAll(std::string& str, const std::string& from, const std::string& to);

#endif // FEAR_SHADER_TRANSLATOR_H
