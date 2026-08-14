#ifndef FEAR_SHADER_H
#define FEAR_SHADER_H

#include <string>

const char* translate_glsl_shader_on_the_fly(const char* source);

std::string FearTranslateGLSL(const char* source, bool isFragment);
std::string calculate_sha256(const std::string& str);

#endif // FEAR_SHADER_H
