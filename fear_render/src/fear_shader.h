#ifndef FEAR_SHADER_H
#define FEAR_SHADER_H

#include <string>

class FearShaderCompiler {
public:
    static std::string translateGLSL(const std::string& source, int shaderType);
};

#endif // FEAR_SHADER_H
