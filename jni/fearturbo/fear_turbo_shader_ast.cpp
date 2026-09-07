#include "fear_turbo_shader_ast.h"
#include <sstream>

namespace fear_turbo {

static const char* node_type_name(ASTNodeType t) {
    switch (t) {
        case ASTNodeType::TranslationUnit: return "TranslationUnit";
        case ASTNodeType::VersionDirective: return "VersionDirective";
        case ASTNodeType::ExtensionDirective: return "ExtensionDirective";
        case ASTNodeType::PrecisionDirective: return "PrecisionDirective";
        case ASTNodeType::LayoutQualifier: return "LayoutQualifier";
        case ASTNodeType::GlobalDeclaration: return "GlobalDeclaration";
        case ASTNodeType::FunctionDeclaration: return "FunctionDeclaration";
        case ASTNodeType::FunctionDefinition: return "FunctionDefinition";
        case ASTNodeType::StructDeclaration: return "StructDeclaration";
        case ASTNodeType::CompoundStatement: return "CompoundStatement";
        case ASTNodeType::DeclarationStatement: return "DeclarationStatement";
        case ASTNodeType::ExpressionStatement: return "ExpressionStatement";
        case ASTNodeType::IfStatement: return "IfStatement";
        case ASTNodeType::ForStatement: return "ForStatement";
        case ASTNodeType::WhileStatement: return "WhileStatement";
        case ASTNodeType::DoWhileStatement: return "DoWhileStatement";
        case ASTNodeType::ReturnStatement: return "ReturnStatement";
        case ASTNodeType::BreakStatement: return "BreakStatement";
        case ASTNodeType::ContinueStatement: return "ContinueStatement";
        case ASTNodeType::DiscardStatement: return "DiscardStatement";
        case ASTNodeType::VariableDeclaration: return "VariableDeclaration";
        case ASTNodeType::TypeQualifier: return "TypeQualifier";
        case ASTNodeType::TypeSpecifier: return "TypeSpecifier";
        case ASTNodeType::ArraySpecifier: return "ArraySpecifier";
        case ASTNodeType::IdentifierExpr: return "IdentifierExpr";
        case ASTNodeType::IntLiteralExpr: return "IntLiteralExpr";
        case ASTNodeType::FloatLiteralExpr: return "FloatLiteralExpr";
        case ASTNodeType::BoolLiteralExpr: return "BoolLiteralExpr";
        case ASTNodeType::FunctionCall: return "FunctionCall";
        case ASTNodeType::ConstructorCall: return "ConstructorCall";
        case ASTNodeType::MemberAccess: return "MemberAccess";
        case ASTNodeType::ArrayAccess: return "ArrayAccess";
        case ASTNodeType::UnaryOp: return "UnaryOp";
        case ASTNodeType::BinaryOp: return "BinaryOp";
        case ASTNodeType::TernaryOp: return "TernaryOp";
        case ASTNodeType::Assignment: return "Assignment";
        default: return "Unknown";
    }
}

std::string ast_to_string(const ASTNode* node, int depth) {
    if (!node) return "";
    std::ostringstream ss;
    for (int i = 0; i < depth; i++) ss << "  ";
    ss << node_type_name(node->type);
    if (!node->text.empty()) ss << " [\"" << node->text << "\"]";
    if (!node->data_type.empty()) ss << " (type: " << node->data_type << ")";
    if (!node->qualifiers.empty()) {
        ss << " {qualifiers:";
        for (auto& q : node->qualifiers) ss << " " << q;
        ss << "}";
    }
    ss << "\n";
    for (auto& child : node->children) {
        ss << ast_to_string(child.get(), depth + 1);
    }
    return ss.str();
}

void print_ast(const ASTNode* node, int depth) {
    LOGI("%s", ast_to_string(node, depth).c_str());
}

int count_nodes(const ASTNode* node) {
    if (!node) return 0;
    int count = 1;
    for (auto& child : node->children) {
        count += count_nodes(child.get());
    }
    return count;
}

void ASTVisitor::visit(const ASTNode* node) {
    if (!node) return;
    visit_children(node);
}

void ASTVisitor::visit_children(const ASTNode* node) {
    if (!node) return;
    for (auto& child : node->children) {
        visit(child.get());
    }
}

} // namespace fear_turbo
