#include "fear_turbo_shader_codegen.h"
#include "fear_turbo_shader_ast.h"
#include <sstream>
#include <unordered_map>

namespace fear_turbo {

// Type translation table: desktop GLSL type -> GLSL ES type
static const std::unordered_map<std::string, std::string> s_type_map = {
    {"double", "float"},
    {"dvec2", "vec2"}, {"dvec3", "vec3"}, {"dvec4", "vec4"},
    {"sampler1D", "sampler2D"},
    {"sampler1DShadow", "sampler2DShadow"},
    {"isampler1D", "isampler2D"},
    {"usampler1D", "usampler2D"},
    {"sampler1DArray", "sampler2DArray"},
    {"sampler2DRect", "sampler2D"},
    {"sampler2DRectShadow", "sampler2DShadow"},
    {"image1D", "image2D"},
    {"image1DArray", "image2DArray"},
    {"iimage1D", "iimage2D"},
    {"uimage1D", "uimage2D"},
    {"texture1D", "texture2D"},
    {"texture2DRect", "texture2D"},
};

// Qualifier translations
static const std::unordered_map<std::string, std::string> s_qualifier_map = {
    {"attribute", "in"},
    {"varying", "out"},
    {"noperspective", ""},
    {"centroid", "centroid"},
    {"patch", ""},
};

// Function name translations
static const std::unordered_map<std::string, std::string> s_func_map = {
    {"texture1D", "texture"},
    {"texture2D", "texture"},
    {"texture3D", "texture"},
    {"textureCube", "texture"},
    {"texture2DRect", "texture"},
    {"texture1DProj", "textureProj"},
    {"texture2DProj", "textureProj"},
    {"shadow1D", "texture"},
    {"shadow2D", "texture"},
    {"gl_FragColor", "fragColor"},
    {"gl_FragData", "fragData"},
};

CodeGenerator::CodeGenerator(ShaderStage stage) : stage_(stage) {}

void CodeGenerator::emit(const std::string& text) { output_ << text; }
void CodeGenerator::emit_indent() { for (int i = 0; i < indent_level_; i++) output_ << "    "; }
void CodeGenerator::emit_line(const std::string& text) { emit_indent(); output_ << text << "\n"; }

std::string CodeGenerator::translate_type(const std::string& type) {
    auto it = s_type_map.find(type);
    if (it != s_type_map.end()) return it->second;
    return type;
}

std::string CodeGenerator::translate_qualifier(const std::string& qual) {
    auto it = s_qualifier_map.find(qual);
    if (it != s_qualifier_map.end()) return it->second;
    return qual;
}

bool CodeGenerator::needs_type_translation(const std::string& type) {
    return s_type_map.find(type) != s_type_map.end();
}

std::string CodeGenerator::generate(const ASTNode* root) {
    output_.str(""); output_.clear(); indent_level_ = 0;
    if (!root) { LOGE("FearTurbo CodeGen: null AST root"); return ""; }
    LOGI("FearTurbo CodeGen: Generating GLSL ES (stage=%d, nodes=%d)", (int)stage_, count_nodes(root));
    emit_translation_unit(root);
    std::string result = output_.str();
    LOGI("FearTurbo CodeGen: Generated %zu bytes of GLSL ES", result.size());
    return result;
}

void CodeGenerator::emit_translation_unit(const ASTNode* node) {
    for (auto& child : node->children) {
        switch (child->type) {
            case ASTNodeType::VersionDirective: emit_version_directive(child.get()); break;
            case ASTNodeType::ExtensionDirective: emit_extension_directive(child.get()); break;
            case ASTNodeType::PrecisionDirective: emit_precision_directive(child.get()); break;
            case ASTNodeType::LayoutQualifier: emit_layout_qualifier(child.get()); break;
            case ASTNodeType::GlobalDeclaration: emit_global_declaration(child.get()); break;
            case ASTNodeType::FunctionDefinition: emit_function_definition(child.get()); break;
            case ASTNodeType::FunctionDeclaration: emit_function_definition(child.get()); break;
            case ASTNodeType::StructDeclaration: emit_struct_declaration(child.get()); break;
            default: if (!child->text.empty()) emit_line(child->text); break;
        }
    }
}

void CodeGenerator::emit_version_directive(const ASTNode* node) { emit_line("#version 320 es"); }

void CodeGenerator::emit_extension_directive(const ASTNode* node) {
    std::string ext = node->text;
    if (ext.find("GL_ARB_") != std::string::npos) return;
    emit_line("#" + ext);
}

void CodeGenerator::emit_precision_directive(const ASTNode* node) { emit_line(node->text); }

void CodeGenerator::emit_layout_qualifier(const ASTNode* node) {
    emit("layout(" + node->text + ") ");
}

void CodeGenerator::emit_global_declaration(const ASTNode* node) {
    emit_indent();
    for (auto& q : node->qualifiers) { std::string tq = translate_qualifier(q); if (!tq.empty()) emit(tq + " "); }
    if (!node->data_type.empty()) emit(translate_type(node->data_type) + " ");
    bool first = true;
    for (auto& child : node->children) {
        if (child->type == ASTNodeType::TypeSpecifier) continue;
        if (child->type == ASTNodeType::VariableDeclaration) { if (!first) emit(", "); first = false; emit_variable_declaration(child.get()); }
    }
    emit(";\n");
}

void CodeGenerator::emit_variable_declaration(const ASTNode* node) {
    emit(node->text);
    for (auto& child : node->children) { if (child->type == ASTNodeType::ArraySpecifier) emit("[" + child->text + "]"); }
    for (auto& child : node->children) { if (child->type != ASTNodeType::ArraySpecifier) { emit(" = "); emit_expression(child.get()); break; } }
}

void CodeGenerator::emit_function_definition(const ASTNode* node) {
    emit_indent();
    emit(translate_type(node->data_type) + " ");
    emit(node->text);
    emit("(");
    bool first = true;
    for (auto& child : node->children) {
        if (child->type == ASTNodeType::VariableDeclaration) {
            if (!first) emit(", "); first = false;
            for (auto& q : child->qualifiers) { std::string tq = translate_qualifier(q); if (!tq.empty()) emit(tq + " "); }
            emit(translate_type(child->data_type) + " ");
            emit(child->text);
        }
    }
    emit(")");
    if (node->children.size() > 0) {
        auto& last = node->children.back();
        if (last->type == ASTNodeType::CompoundStatement) {
            emit(" {\n"); indent_level_++;
            for (auto& stmt : last->children) {
                switch (stmt->type) {
                    case ASTNodeType::CompoundStatement: emit_compound_statement(stmt.get()); break;
                    case ASTNodeType::DeclarationStatement: emit_indent(); emit(stmt->text + ";\n"); break;
                    case ASTNodeType::IfStatement: emit_if_statement(stmt.get()); break;
                    case ASTNodeType::ForStatement: emit_for_statement(stmt.get()); break;
                    case ASTNodeType::ReturnStatement: emit_return_statement(stmt.get()); break;
                    case ASTNodeType::BreakStatement: emit_indent(); emit("break;\n"); break;
                    case ASTNodeType::ContinueStatement: emit_indent(); emit("continue;\n"); break;
                    case ASTNodeType::DiscardStatement: emit_indent(); emit("discard;\n"); break;
                    default: emit_indent(); emit_expression(stmt.get()); emit(";\n"); break;
                }
            }
            indent_level_--; emit_line("}");
        } else { emit(";\n"); }
    } else { emit(";\n"); }
    emit("\n");
}

void CodeGenerator::emit_struct_declaration(const ASTNode* node) {
    emit_indent(); emit("struct " + node->text + " {\n"); indent_level_++;
    for (auto& child : node->children) {
        if (child->type == ASTNodeType::VariableDeclaration) {
            emit_indent(); emit(translate_type(child->data_type) + " " + child->text);
            for (auto& c : child->children) { if (c->type == ASTNodeType::ArraySpecifier) emit("[" + c->text + "]"); }
            emit(";\n");
        }
    }
    indent_level_--; emit_line("};");
}

void CodeGenerator::emit_compound_statement(const ASTNode* node) {
    emit_indent(); emit("{\n"); indent_level_++;
    for (auto& child : node->children) {
        switch (child->type) {
            case ASTNodeType::DeclarationStatement: emit_indent(); emit(child->text + ";\n"); break;
            case ASTNodeType::CompoundStatement: emit_compound_statement(child.get()); break;
            case ASTNodeType::IfStatement: emit_if_statement(child.get()); break;
            case ASTNodeType::ForStatement: emit_for_statement(child.get()); break;
            case ASTNodeType::ReturnStatement: emit_return_statement(child.get()); break;
            case ASTNodeType::BreakStatement: emit_indent(); emit("break;\n"); break;
            case ASTNodeType::ContinueStatement: emit_indent(); emit("continue;\n"); break;
            case ASTNodeType::DiscardStatement: emit_indent(); emit("discard;\n"); break;
            default: emit_indent(); emit_expression(child.get()); emit(";\n"); break;
        }
    }
    indent_level_--; emit_indent(); emit("}\n");
}

void CodeGenerator::emit_if_statement(const ASTNode* node) {
    emit_indent(); emit("if (");
    if (node->children.size() > 0) emit_expression(node->children[0].get());
    emit(") ");
    if (node->children.size() > 1) {
        auto& then_stmt = node->children[1];
        if (then_stmt->type == ASTNodeType::CompoundStatement) emit_compound_statement(then_stmt.get());
        else { emit("\n"); indent_level_++; emit_indent(); emit_expression(then_stmt.get()); emit(";\n"); indent_level_--; }
    }
    if (node->children.size() > 2) {
        emit_indent(); emit("else ");
        auto& else_stmt = node->children[2];
        if (else_stmt->type == ASTNodeType::CompoundStatement) emit_compound_statement(else_stmt.get());
        else { emit("\n"); indent_level_++; emit_indent(); emit_expression(else_stmt.get()); emit(";\n"); indent_level_--; }
    }
    emit("\n");
}

void CodeGenerator::emit_for_statement(const ASTNode* node) {
    emit_indent(); emit("for (" + node->text + ") {\n"); indent_level_++;
    for (auto& child : node->children) {
        if (child->type == ASTNodeType::CompoundStatement) emit_compound_statement(child.get());
        else { emit_indent(); emit_expression(child.get()); emit(";\n"); }
    }
    indent_level_--; emit_indent(); emit("}\n");
}

void CodeGenerator::emit_return_statement(const ASTNode* node) {
    emit_indent(); emit("return");
    if (node->children.size() > 0) { emit(" "); emit_expression(node->children[0].get()); }
    emit(";\n");
}

void CodeGenerator::emit_expression(const ASTNode* node) {
    if (!node) return;
    switch (node->type) {
        case ASTNodeType::IdentifierExpr: emit(node->text); break;
        case ASTNodeType::IntLiteralExpr: emit(node->text); break;
        case ASTNodeType::FloatLiteralExpr: emit(node->text); break;
        case ASTNodeType::BoolLiteralExpr: emit(node->text); break;
        case ASTNodeType::FunctionCall: emit_function_call(node); break;
        case ASTNodeType::ConstructorCall:
            emit(translate_type(node->text)); emit("(");
            for (size_t i = 0; i < node->children.size(); i++) { if (i > 0) emit(", "); emit_expression(node->children[i].get()); }
            emit(")"); break;
        case ASTNodeType::MemberAccess: emit_member_access(node); break;
        case ASTNodeType::ArrayAccess:
            if (node->children.size() >= 2) { emit_expression(node->children[1].get()); emit("["); emit_expression(node->children[0].get()); emit("]"); }
            break;
        case ASTNodeType::UnaryOp: emit(node->text); if (node->children.size() > 0) emit_expression(node->children[0].get()); break;
        case ASTNodeType::BinaryOp:
            if (node->children.size() >= 2) { emit_expression(node->children[0].get()); emit(" " + node->text + " "); emit_expression(node->children[1].get()); }
            break;
        case ASTNodeType::TernaryOp:
            if (node->children.size() >= 3) { emit_expression(node->children[0].get()); emit(" ? "); emit_expression(node->children[1].get()); emit(" : "); emit_expression(node->children[2].get()); }
            break;
        case ASTNodeType::Assignment:
            if (node->children.size() >= 2) { emit_expression(node->children[0].get()); emit(" " + node->text + " "); emit_expression(node->children[1].get()); }
            break;
        case ASTNodeType::DeclarationStatement: emit(node->text); break;
        default: emit(node->text); break;
    }
}

void CodeGenerator::emit_function_call(const ASTNode* node) {
    std::string func_name = node->text;
    auto it = s_func_map.find(func_name);
    if (it != s_func_map.end()) func_name = it->second;
    emit(func_name + "(");
    for (size_t i = 0; i < node->children.size(); i++) { if (i > 0) emit(", "); emit_expression(node->children[i].get()); }
    emit(")");
}

void CodeGenerator::emit_member_access(const ASTNode* node) {
    if (node->children.size() >= 2) { emit_expression(node->children[1].get()); emit("."); emit_expression(node->children[0].get()); }
}

static std::unordered_map<std::size_t, std::string> s_shader_cache;
static std::mutex s_cache_mutex;

static std::string fast_transpile_shader(const std::string& source, ShaderStage stage) {
    std::string res;
    res.reserve(source.size() + 256);

    // 1. Version directive replacement
    size_t ver_pos = source.find("#version");
    if (ver_pos != std::string::npos) {
        size_t line_end = source.find('\n', ver_pos);
        res += "#version 320 es\n";
        if (line_end != std::string::npos) {
            res += source.substr(line_end + 1);
        }
    } else {
        res = "#version 320 es\n" + source;
    }

    // 2. Precision headers & Mali GPU safe math macros
    std::string headers = "\nprecision highp float;\nprecision highp int;\nprecision highp sampler2D;\nprecision highp sampler2DArray;\nprecision highp sampler3D;\nprecision highp samplerCube;\n"
                          "#ifndef FEAR_SAFE_MATH\n#define FEAR_SAFE_MATH\n"
                          "#define pow(x, y) pow(max(abs(x), 0.00001), y)\n"
                          "#define log(x) log(max(x, 0.00001))\n"
                          "#define log2(x) log2(max(x, 0.00001))\n"
                          "#define inversesqrt(x) inversesqrt(max(x, 0.00001))\n"
                          "#endif\n";
    size_t first_newline = res.find('\n');
    if (first_newline != std::string::npos) {
        res.insert(first_newline + 1, headers);
    }

    // 3. String replacements for Mali GPU & GLES compatibility
    // Strip unsupported GL_ARB extensions
    size_t pos = 0;
    while ((pos = res.find("#extension GL_ARB_", pos)) != std::string::npos) {
        res.replace(pos, 18, "// #extension GL_ARB_");
        pos += 21;
    }
    pos = 0;
    while ((pos = res.find("#extension GL_EXT_gpu_shader4", pos)) != std::string::npos) {
        res.replace(pos, 29, "// #extension GL_EXT_gpu_shader4");
        pos += 32;
    }

    // Replace noperspective
    pos = 0;
    while ((pos = res.find("noperspective", pos)) != std::string::npos) {
        res.replace(pos, 13, "smooth");
        pos += 6;
    }

    // Legacy texture function names
    const std::pair<const char*, const char*> tex_replaces[] = {
        {"texture1D(", "texture("},
        {"texture2D(", "texture("},
        {"texture3D(", "texture("},
        {"textureCube(", "texture("},
        {"texture2DRect(", "texture("},
        {"shadow1D(", "texture("},
        {"shadow2D(", "texture("},
    };
    for (const auto& rep : tex_replaces) {
        size_t rlen = strlen(rep.first);
        size_t nlen = strlen(rep.second);
        pos = 0;
        while ((pos = res.find(rep.first, pos)) != std::string::npos) {
            res.replace(pos, rlen, rep.second);
            pos += nlen;
        }
    }

    // gl_FragData -> MRT layout outputs for Fragment shaders
    if (stage == ShaderStage::Fragment && (res.find("gl_FragData") != std::string::npos || res.find("gl_FragColor") != std::string::npos)) {
        std::string mrt_decls = "\nlayout(location = 0) out highp vec4 fear_fragData0;\n"
                                "layout(location = 1) out highp vec4 fear_fragData1;\n"
                                "layout(location = 2) out highp vec4 fear_fragData2;\n"
                                "layout(location = 3) out highp vec4 fear_fragData3;\n";
        size_t header_idx = res.find("precision highp float;");
        if (header_idx != std::string::npos) {
            res.insert(header_idx, mrt_decls);
        }
        pos = 0;
        while ((pos = res.find("gl_FragData[0]", pos)) != std::string::npos) {
            res.replace(pos, 14, "fear_fragData0"); pos += 14;
        }
        pos = 0;
        while ((pos = res.find("gl_FragData[1]", pos)) != std::string::npos) {
            res.replace(pos, 14, "fear_fragData1"); pos += 14;
        }
        pos = 0;
        while ((pos = res.find("gl_FragData[2]", pos)) != std::string::npos) {
            res.replace(pos, 14, "fear_fragData2"); pos += 14;
        }
        pos = 0;
        while ((pos = res.find("gl_FragData[3]", pos)) != std::string::npos) {
            res.replace(pos, 14, "fear_fragData3"); pos += 14;
        }
        pos = 0;
        while ((pos = res.find("gl_FragColor", pos)) != std::string::npos) {
            res.replace(pos, 12, "fear_fragData0"); pos += 14;
        }
    }

    return res;
}

std::string translate_shader(const std::string& source, ShaderStage stage) {
    if (source.empty()) return "";

    // 1. Thread-safe hash cache lookup (0ms overhead for compiled shaders)
    std::size_t source_hash = std::hash<std::string>{}(source) ^ ((std::size_t)stage << 16);
    {
        std::lock_guard<std::mutex> lock(s_cache_mutex);
        auto it = s_shader_cache.find(source_hash);
        if (it != s_shader_cache.end()) {
            return it->second;
        }
    }

    // 2. Fast transpile pass
    std::string result = fast_transpile_shader(source, stage);

    // 3. Cache result
    {
        std::lock_guard<std::mutex> lock(s_cache_mutex);
        // Limit cache size to 4096 shaders to prevent memory growth
        if (s_shader_cache.size() > 4096) s_shader_cache.clear();
        s_shader_cache[source_hash] = result;
    }

    return result;
}

} // namespace fear_turbo
