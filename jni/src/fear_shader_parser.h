#ifndef FEAR_SHADER_PARSER_H
#define FEAR_SHADER_PARSER_H
#include <string>
// Tokenizes desktop GLSL shader components
class ShaderParser {
public:
    static std::string sanitizeTokens(std::string code) {
        return code;
    }
};
#endif
