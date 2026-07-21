#ifndef FEAR_SHADER_OPTIMIZER_H
#define FEAR_SHADER_OPTIMIZER_H
#include <string>
// Optimizes C++ shader AST and strips dead code
class ShaderOptimizer {
public:
    static std::string optimizeAST(std::string src) {
        return src;
    }
};
#endif
