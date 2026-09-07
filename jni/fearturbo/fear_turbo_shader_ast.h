#ifndef FEAR_TURBO_SHADER_AST_H
#define FEAR_TURBO_SHADER_AST_H

#include "fear_turbo_shader_parser.h"

namespace fear_turbo {

// AST utility functions
std::string ast_to_string(const ASTNode* node, int depth = 0);
void print_ast(const ASTNode* node, int depth = 0);
int count_nodes(const ASTNode* node);

// AST visitor for transformations
class ASTVisitor {
public:
    virtual ~ASTVisitor() = default;
    virtual void visit(const ASTNode* node);
    virtual void visit_children(const ASTNode* node);
};

} // namespace fear_turbo

#endif // FEAR_TURBO_SHADER_AST_H
