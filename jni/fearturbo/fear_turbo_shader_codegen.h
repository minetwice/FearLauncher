#ifndef FEAR_TURBO_SHADER_CODEGEN_H
#define FEAR_TURBO_SHADER_CODEGEN_H

#include "fear_turbo_shader_parser.h"
#include <sstream>

namespace fear_turbo {

// ============================================================================
// GLSL ES Code Generator
// ============================================================================
// Takes an AST produced by the parser and generates GLSL ES 3.2 code.
// This is the core of the shader translation — desktop GLSL -> GLSL ES.
//
// Key transformations:
// 1. Version directive: #version 330 core -> #version 320 es
// 2. Precision qualifiers: add default precision for float/int
// 3. Type translations: double -> float, dvec -> vec, sampler1D -> sampler2D
// 4. Layout qualifier translations: remove unsupported qualifiers
// 5. Function translations: texture() with 1D samplers -> 2D equivalents
// 6. gl_FragData -> layout(location) out variables
// 7. Remove deprecated features (gl_FragColor, etc.)
// ============================================================================

class CodeGenerator {
public:
    CodeGenerator(ShaderStage stage);
    std::string generate(const ASTNode* root);

private:
    ShaderStage stage_;
    std::ostringstream output_;
    int indent_level_ = 0;

    void emit(const std::string& text);
    void emit_line(const std::string& text);
    void emit_indent();

    void emit_translation_unit(const ASTNode* node);
    void emit_version_directive(const ASTNode* node);
    void emit_extension_directive(const ASTNode* node);
    void emit_precision_directive(const ASTNode* node);
    void emit_layout_qualifier(const ASTNode* node);
    void emit_struct_declaration(const ASTNode* node);
    void emit_uniform_declaration(const ASTNode* node);
    void emit_in_out_declaration(const ASTNode* node);
    void emit_function_declaration(const ASTNode* node);
    void emit_function_definition(const ASTNode* node);
    void emit_statement(const ASTNode* node);
    void emit_expression(const ASTNode* node);
    void emit_type(const ASTNode* node);
    void emit_qualifier(const ASTNode* node);

    std::string translate_type(const std::string& type);
    std::string translate_qualifier(const std::string& qual);
    std::string translate_function_name(const std::string& name);
};

// Main entry point: translate a desktop GLSL shader to GLSL ES
std::string translate_shader(const std::string& source, ShaderStage stage);

} // namespace fear_turbo

#endif // FEAR_TURBO_SHADER_CODEGEN_H
