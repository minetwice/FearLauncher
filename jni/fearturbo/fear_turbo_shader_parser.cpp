#include "fear_turbo_shader_parser.h"
#include <cctype>
#include <unordered_map>
#include <sstream>

namespace fear_turbo {

// ============================================================================
// Lexer Implementation
// ============================================================================

static const std::unordered_map<std::string, TokenType> s_keywords = {
    {"version", TokenType::KwVersion}, {"precision", TokenType::KwPrecision},
    {"extension", TokenType::KwExtension}, {"layout", TokenType::KwLayout},
    {"invariant", TokenType::KwInvariant},
    {"highp", TokenType::KwHighp}, {"mediump", TokenType::KwMediump}, {"lowp", TokenType::KwLowp},
    {"void", TokenType::KwVoid}, {"bool", TokenType::KwBool},
    {"int", TokenType::KwInt}, {"uint", TokenType::KwUint},
    {"float", TokenType::KwFloat}, {"double", TokenType::KwDouble},
    {"vec2", TokenType::KwVec2}, {"vec3", TokenType::KwVec3}, {"vec4", TokenType::KwVec4},
    {"dvec2", TokenType::KwDvec2}, {"dvec3", TokenType::KwDvec3}, {"dvec4", TokenType::KwDvec4},
    {"bvec2", TokenType::KwBvec2}, {"bvec3", TokenType::KwBvec3}, {"bvec4", TokenType::KwBvec4},
    {"ivec2", TokenType::KwIvec2}, {"ivec3", TokenType::KwIvec3}, {"ivec4", TokenType::KwIvec4},
    {"uvec2", TokenType::KwUvec2}, {"uvec3", TokenType::KwUvec3}, {"uvec4", TokenType::KwUvec4},
    {"mat2", TokenType::KwMat2}, {"mat3", TokenType::KwMat3}, {"mat4", TokenType::KwMat4},
    {"struct", TokenType::KwStruct},
    {"if", TokenType::KwIf}, {"else", TokenType::KwElse},
    {"for", TokenType::KwFor}, {"while", TokenType::KwWhile}, {"do", TokenType::KwDo},
    {"return", TokenType::KwReturn}, {"break", TokenType::KwBreak},
    {"continue", TokenType::KwContinue}, {"discard", TokenType::KwDiscard},
    {"true", TokenType::KwTrue}, {"false", TokenType::KwFalse},
    {"const", TokenType::KwConst}, {"in", TokenType::KwIn}, {"out", TokenType::KwOut},
    {"inout", TokenType::KwInout}, {"uniform", TokenType::KwUniform},
    {"attribute", TokenType::KwAttribute}, {"varying", TokenType::KwVarying},
    {"centroid", TokenType::KwCentroid}, {"flat", TokenType::KwFlat},
    {"smooth", TokenType::KwSmooth}, {"noperspective", TokenType::KwNoperspective},
    {"patch", TokenType::KwPatch}, {"sample", TokenType::KwSample},
    {"buffer", TokenType::KwBuffer}, {"shared", TokenType::KwShared},
    {"coherent", TokenType::KwCoherent}, {"volatile", TokenType::KwVolatile},
    {"restrict", TokenType::KwRestrict}, {"readonly", TokenType::KwReadonly},
    {"writeonly", TokenType::KwWriteonly},
    {"image1D", TokenType::KwImage1D}, {"image2D", TokenType::KwImage2D},
    {"image3D", TokenType::KwImage3D}, {"imageCube", TokenType::KwImageCube},
    {"image2DArray", TokenType::KwImage2DArray},
    {"sampler1D", TokenType::KwSampler1D}, {"sampler2D", TokenType::KwSampler2D},
    {"sampler3D", TokenType::KwSampler3D}, {"samplerCube", TokenType::KwSamplerCube},
    {"sampler2DArray", TokenType::KwSampler2DArray}, {"samplerCubeArray", TokenType::KwSamplerCubeArray},
    {"sampler1DShadow", TokenType::KwSampler1DShadow}, {"sampler2DShadow", TokenType::KwSampler2DShadow},
    {"isampler1D", TokenType::KwIsampler1D}, {"isampler2D", TokenType::KwIsampler2D},
    {"isampler3D", TokenType::KwIsampler3D}, {"isamplerCube", TokenType::KwIsamplerCube},
    {"usampler1D", TokenType::KwUsampler1D}, {"usampler2D", TokenType::KwUsampler2D},
    {"usampler3D", TokenType::KwUsampler3D}, {"usamplerCube", TokenType::KwUsamplerCube},
    {"atomic_uint", TokenType::KwAtomicCounter},
    {"subroutine", TokenType::KwSubroutine}, {"precise", TokenType::KwPrecise},
};

Lexer::Lexer(const std::string& source) : source_(source) {}

void Lexer::skip_whitespace() {
    while (pos_ < source_.size()) {
        char c = source_[pos_];
        if (c == ' ' || c == '\t' || c == '\r') {
            pos_++; column_++;
        } else if (c == '\n') {
            pos_++; line_++; column_ = 1;
        } else {
            break;
        }
    }
}

Token Lexer::scan_comment() {
    // Assume source_[pos_] == '/' and source_[pos_+1] == '/' or '*'
    if (source_[pos_] == '/' && pos_ + 1 < source_.size()) {
        if (source_[pos_ + 1] == '/') {
            // Line comment
            while (pos_ < source_.size() && source_[pos_] != '\n') {
                pos_++; column_++;
            }
            return next_token();
        } else if (source_[pos_ + 1] == '*') {
            // Block comment
            pos_ += 2; column_ += 2;
            while (pos_ + 1 < source_.size()) {
                if (source_[pos_] == '*' && source_[pos_ + 1] == '/') {
                    pos_ += 2; column_ += 2;
                    return next_token();
                }
                if (source_[pos_] == '\n') { line_++; column_ = 1; }
                else { column_++; }
                pos_++;
            }
            pos_ = source_.size();
            return next_token();
        }
    }
    return Token{TokenType::Unknown, "", line_, column_};
}

Token Lexer::scan_identifier() {
    int start_line = line_, start_col = column_;
    std::string ident;
    while (pos_ < source_.size() && (isalnum(source_[pos_]) || source_[pos_] == '_')) {
        ident += source_[pos_]; pos_++; column_++;
    }
    auto it = s_keywords.find(ident);
    TokenType type = (it != s_keywords.end()) ? it->second : TokenType::Identifier;
    return Token{type, ident, start_line, start_col};
}

Token Lexer::scan_number() {
    int start_line = line_, start_col = column_;
    std::string num;
    bool is_float = false;

    while (pos_ < source_.size() && isdigit(source_[pos_])) {
        num += source_[pos_]; pos_++; column_++;
    }
    if (pos_ < source_.size() && source_[pos_] == '.') {
        is_float = true;
        num += '.'; pos_++; column_++;
        while (pos_ < source_.size() && isdigit(source_[pos_])) {
            num += source_[pos_]; pos_++; column_++;
        }
    }
    // Exponent
    if (pos_ < source_.size() && (source_[pos_] == 'e' || source_[pos_] == 'E')) {
        is_float = true;
        num += source_[pos_]; pos_++; column_++;
        if (pos_ < source_.size() && (source_[pos_] == '+' || source_[pos_] == '-')) {
            num += source_[pos_]; pos_++; column_++;
        }
        while (pos_ < source_.size() && isdigit(source_[pos_])) {
            num += source_[pos_]; pos_++; column_++;
        }
    }
    // Type suffix (f, F, u, U, L, l)
    if (pos_ < source_.size() && (source_[pos_] == 'f' || source_[pos_] == 'F')) {
        is_float = true; pos_++; column_++;
    } else if (pos_ < source_.size() && (source_[pos_] == 'u' || source_[pos_] == 'U' || source_[pos_] == 'l' || source_[pos_] == 'L')) {
        pos_++; column_++;
    }

    return Token{is_float ? TokenType::FloatLiteral : TokenType::IntLiteral, num, start_line, start_col};
}

Token Lexer::scan_preprocessor() {
    // We're at '#' — read the directive name
    int start_line = line_, start_col = column_;
    pos_++; column_++; // skip '#'
    skip_whitespace();

    std::string directive;
    while (pos_ < source_.size() && isalpha(source_[pos_])) {
        directive += source_[pos_]; pos_++; column_++;
    }

    // Read rest of line
    std::string rest;
    while (pos_ < source_.size() && source_[pos_] != '\n') {
        rest += source_[pos_]; pos_++; column_++;
    }

    Token t;
    t.line = start_line; t.column = start_col;
    t.value = directive + " " + rest;

    if (directive == "version") t.type = TokenType::KwVersion;
    else if (directive == "extension") t.type = TokenType::KwExtension;
    else if (directive == "if") t.type = TokenType::HashIf;
    else if (directive == "ifdef") t.type = TokenType::HashIfdef;
    else if (directive == "ifndef") t.type = TokenType::HashIfndef;
    else if (directive == "else") t.type = TokenType::HashElse;
    else if (directive == "elif") t.type = TokenType::HashElif;
    else if (directive == "endif") t.type = TokenType::HashEndif;
    else if (directive == "define") t.type = TokenType::HashDefine;
    else if (directive == "undef") t.type = TokenType::HashUndef;
    else if (directive == "include") t.type = TokenType::HashInclude;
    else if (directive == "pragma") t.type = TokenType::HashPragma;
    else if (directive == "line") t.type = TokenType::HashLine;
    else if (directive == "error") t.type = TokenType::HashError;
    else t.type = TokenType::Hash;

    return t;
}

Token Lexer::next_token() {
    skip_whitespace();
    if (pos_ >= source_.size()) return Token{TokenType::EndOfFile, "", line_, column_};

    char c = source_[pos_];

    // Comments
    if (c == '/' && pos_ + 1 < source_.size() && (source_[pos_+1] == '/' || source_[pos_+1] == '*')) {
        return scan_comment();
    }

    // Preprocessor
    if (c == '#') return scan_preprocessor();

    // Identifiers/keywords
    if (isalpha(c) || c == '_') return scan_identifier();

    // Numbers
    if (isdigit(c)) return scan_number();

    // Punctuation & operators
    int sl = line_, sc = column_;
    pos_++; column_++;
    switch (c) {
        case ';': return {TokenType::Semicolon, ";", sl, sc};
        case ',': return {TokenType::Comma, ",", sl, sc};
        case '.': return {TokenType::Dot, ".", sl, sc};
        case ':': return {TokenType::Colon, ":", sl, sc};
        case '?': return {TokenType::QuestionMark, "?", sl, sc};
        case '(': return {TokenType::LParen, "(", sl, sc};
        case ')': return {TokenType::RParen, ")", sl, sc};
        case '{': return {TokenType::LBrace, "{", sl, sc};
        case '}': return {TokenType::RBrace, "}", sl, sc};
        case '[': return {TokenType::LBracket, "[", sl, sc};
        case ']': return {TokenType::RBracket, "]", sl, sc};
        case '~': return {TokenType::BitwiseNot, "~", sl, sc};
        case '%':
            if (pos_ < source_.size() && source_[pos_] == '=') { pos_++; column_++; return {TokenType::Percent, "%=", sl, sc}; }
            return {TokenType::Percent, "%", sl, sc};
        case '+':
            if (pos_ < source_.size()) {
                if (source_[pos_] == '=') { pos_++; column_++; return {TokenType::PlusAssign, "+=", sl, sc}; }
                if (source_[pos_] == '+') { pos_++; column_++; return {TokenType::Inc, "++", sl, sc}; }
            }
            return {TokenType::Plus, "+", sl, sc};
        case '-':
            if (pos_ < source_.size()) {
                if (source_[pos_] == '=') { pos_++; column_++; return {TokenType::MinusAssign, "-=", sl, sc}; }
                if (source_[pos_] == '-') { pos_++; column_++; return {TokenType::Dec, "--", sl, sc}; }
            }
            return {TokenType::Minus, "-", sl, sc};
        case '*':
            if (pos_ < source_.size() && source_[pos_] == '=') { pos_++; column_++; return {TokenType::StarAssign, "*=", sl, sc}; }
            return {TokenType::Star, "*", sl, sc};
        case '/':
            if (pos_ < source_.size() && source_[pos_] == '=') { pos_++; column_++; return {TokenType::SlashAssign, "/=", sl, sc}; }
            return {TokenType::Slash, "/", sl, sc};
        case '=':
            if (pos_ < source_.size() && source_[pos_] == '=') { pos_++; column_++; return {TokenType::Eq, "==", sl, sc}; }
            return {TokenType::Assign, "=", sl, sc};
        case '!':
            if (pos_ < source_.size() && source_[pos_] == '=') { pos_++; column_++; return {TokenType::Neq, "!=", sl, sc}; }
            return {TokenType::LogicalNot, "!", sl, sc};
        case '<':
            if (pos_ < source_.size()) {
                if (source_[pos_] == '=') { pos_++; column_++; return {TokenType::Le, "<=", sl, sc}; }
                if (source_[pos_] == '<') { pos_++; column_++; return {TokenType::ShiftLeft, "<<", sl, sc}; }
            }
            return {TokenType::Lt, "<", sl, sc};
        case '>':
            if (pos_ < source_.size()) {
                if (source_[pos_] == '=') { pos_++; column_++; return {TokenType::Ge, ">=", sl, sc}; }
                if (source_[pos_] == '>') { pos_++; column_++; return {TokenType::ShiftRight, ">>", sl, sc}; }
            }
            return {TokenType::Gt, ">", sl, sc};
        case '&':
            if (pos_ < source_.size()) {
                if (source_[pos_] == '&') { pos_++; column_++; return {TokenType::LogicalAnd, "&&", sl, sc}; }
            }
            return {TokenType::BitwiseAnd, "&", sl, sc};
        case '|':
            if (pos_ < source_.size()) {
                if (source_[pos_] == '|') { pos_++; column_++; return {TokenType::LogicalOr, "||", sl, sc}; }
            }
            return {TokenType::BitwiseOr, "|", sl, sc};
        case '^': return {TokenType::BitwiseXor, "^", sl, sc};
        default: return {TokenType::Unknown, std::string(1, c), sl, sc};
    }
}

std::vector<Token> Lexer::tokenize() {
    std::vector<Token> tokens;
    while (true) {
        Token t = next_token();
        tokens.push_back(t);
        if (t.type == TokenType::EndOfFile) break;
    }
    return tokens;
}

TokenType Lexer::keyword_to_type(const std::string& kw) {
    auto it = s_keywords.find(kw);
    return (it != s_keywords.end()) ? it->second : TokenType::Identifier;
}

// ============================================================================
// Parser Implementation
// ============================================================================

Parser::Parser(const std::vector<Token>& tokens) : tokens_(tokens) {}

const Token& Parser::peek(int offset) const {
    size_t idx = pos_ + offset;
    if (idx >= tokens_.size()) return tokens_.back();
    return tokens_[idx];
}

Token Parser::consume() {
    if (pos_ < tokens_.size()) return tokens_[pos_++];
    return tokens_.back();
}

bool Parser::match(TokenType type) {
    if (peek().type == type) { consume(); return true; }
    return false;
}

bool Parser::expect(TokenType type, const std::string& context) {
    if (peek().type != type) {
        LOGE("FearTurbo Parser: Expected token type %d but got %d ('%s') at line %d in %s",
             (int)type, (int)peek().type, peek().value.c_str(), peek().line, context.c_str());
        return false;
    }
    consume();
    return true;
}

// Parse a full translation unit (entire shader)
std::unique_ptr<ASTNode> Parser::parse_translation_unit() {
    auto root = std::make_unique<ASTNode>(ASTNodeType::TranslationUnit);

    while (peek().type != TokenType::EndOfFile) {
        const Token& t = peek();

        if (t.type == TokenType::KwVersion) {
            root->add_child(parse_version_directive());
        } else if (t.type == TokenType::KwExtension) {
            root->add_child(parse_extension_directive());
        } else if (t.type == TokenType::KwPrecision) {
            root->add_child(parse_precision_directive());
        } else if (t.type == TokenType::KwLayout) {
            auto layout = parse_layout_qualifier();
            // Layout can be followed by a declaration
            if (peek().type != TokenType::Semicolon) {
                auto decl = parse_global_declaration();
                if (decl) {
                    // Prepend layout qualifiers to the declaration
                    for (auto& c : layout->children) {
                        decl->children.insert(decl->children.begin(), std::move(c));
                    }
                    root->add_child(std::move(decl));
                }
            } else {
                consume(); // ;
                root->add_child(std::move(layout));
            }
        } else if (t.type == TokenType::KwStruct) {
            root->add_child(parse_struct_declaration());
        } else if (t.type == TokenType::KwConst || t.type == TokenType::KwIn || t.type == TokenType::KwOut ||
                   t.type == TokenType::KwInout || t.type == TokenType::KwUniform || t.type == TokenType::KwAttribute ||
                   t.type == TokenType::KwVarying || t.type == TokenType::KwFlat || t.type == TokenType::KwSmooth ||
                   t.type == TokenType::KwCentroid || t.type == TokenType::KwNoperspective ||
                   t.type == TokenType::KwBuffer || t.type == TokenType::KwShared || t.type == TokenType::KwCoherent ||
                   t.type == TokenType::KwVolatile || t.type == TokenType::KwRestrict ||
                   t.type == TokenType::KwReadonly || t.type == TokenType::KwWriteonly ||
                   t.type == TokenType::KwHighp || t.type == TokenType::KwMediump || t.type == TokenType::KwLowp ||
                   t.type == TokenType::KwInvariant) {
            root->add_child(parse_global_declaration());
        } else if (t.type == TokenType::KwVoid || t.type == TokenType::KwBool || t.type == TokenType::KwInt ||
                   t.type == TokenType::KwUint || t.type == TokenType::KwFloat || t.type == TokenType::KwDouble ||
                   t.type == TokenType::KwVec2 || t.type == TokenType::KwVec3 || t.type == TokenType::KwVec4 ||
                   t.type == TokenType::KwBvec2 || t.type == TokenType::KwBvec3 || t.type == TokenType::KwBvec4 ||
                   t.type == TokenType::KwIvec2 || t.type == TokenType::KwIvec3 || t.type == TokenType::KwIvec4 ||
                   t.type == TokenType::KwUvec2 || t.type == TokenType::KwUvec3 || t.type == TokenType::KwUvec4 ||
                   t.type == TokenType::KwMat2 || t.type == TokenType::KwMat3 || t.type == TokenType::KwMat4 ||
                   t.type == TokenType::KwSampler2D || t.type == TokenType::KwSamplerCube ||
                   t.type == TokenType::KwSampler2DArray || t.type == TokenType::KwSampler3D ||
                   t.type == TokenType::KwSampler1D || t.type == TokenType::KwImage2D) {
            // Could be a function definition or global declaration
            // Peek ahead to see if there's a '(' after the type and identifier
            if (peek(2).type == TokenType::LParen) {
                root->add_child(parse_function_definition());
            } else {
                root->add_child(parse_global_declaration());
            }
        } else if (t.type == TokenType::Identifier) {
            // Could be a user-defined type declaration or function
            if (peek(1).type == TokenType::Identifier && peek(2).type == TokenType::LParen) {
                root->add_child(parse_function_definition());
            } else {
                root->add_child(parse_global_declaration());
            }
        } else {
            // Unknown — skip the token
            LOGW("FearTurbo Parser: Skipping unexpected token '%s' (type %d) at line %d",
                 t.value.c_str(), (int)t.type, t.line);
            consume();
        }
    }

    return root;
}

std::unique_ptr<ASTNode> Parser::parse_version_directive() {
    auto node = std::make_unique<ASTNode>(ASTNodeType::VersionDirective);
    node->line = peek().line;
    node->text = consume().value; // includes "version XXX profile"
    return node;
}

std::unique_ptr<ASTNode> Parser::parse_extension_directive() {
    auto node = std::make_unique<ASTNode>(ASTNodeType::ExtensionDirective);
    node->line = peek().line;
    node->text = consume().value;
    return node;
}

std::unique_ptr<ASTNode> Parser::parse_precision_directive() {
    auto node = std::make_unique<ASTNode>(ASTNodeType::PrecisionDirective);
    node->line = peek().line;
    // precision highp float;
    while (peek().type != TokenType::Semicolon && peek().type != TokenType::EndOfFile) {
        node->text += consume().value + " ";
    }
    match(TokenType::Semicolon);
    return node;
}

std::unique_ptr<ASTNode> Parser::parse_layout_qualifier() {
    auto node = std::make_unique<ASTNode>(ASTNodeType::LayoutQualifier);
    node->line = peek().line;
    consume(); // 'layout'
    expect(TokenType::LParen, "layout qualifier");
    // Read everything inside the parens
    int depth = 1;
    while (depth > 0 && peek().type != TokenType::EndOfFile) {
        if (peek().type == TokenType::LParen) depth++;
        if (peek().type == TokenType::RParen) { depth--; if (depth == 0) break; }
        node->text += consume().value + " ";
    }
    match(TokenType::RParen);
    return node;
}

std::unique_ptr<ASTNode> Parser::parse_global_declaration() {
    auto node = std::make_unique<ASTNode>(ASTNodeType::GlobalDeclaration);
    node->line = peek().line;

    // Collect qualifiers
    while (peek().type == TokenType::KwConst || peek().type == TokenType::KwIn || peek().type == TokenType::KwOut ||
           peek().type == TokenType::KwInout || peek().type == TokenType::KwUniform || peek().type == TokenType::KwAttribute ||
           peek().type == TokenType::KwVarying || peek().type == TokenType::KwFlat || peek().type == TokenType::KwSmooth ||
           peek().type == TokenType::KwCentroid || peek().type == TokenType::KwNoperspective ||
           peek().type == TokenType::KwBuffer || peek().type == TokenType::KwShared ||
           peek().type == TokenType::KwCoherent || peek().type == TokenType::KwVolatile ||
           peek().type == TokenType::KwRestrict || peek().type == TokenType::KwReadonly ||
           peek().type == TokenType::KwWriteonly || peek().type == TokenType::KwHighp ||
           peek().type == TokenType::KwMediump || peek().type == TokenType::KwLowp ||
           peek().type == TokenType::KwInvariant) {
        node->qualifiers.push_back(consume().value);
    }

    // Parse type specifier
    auto type_spec = parse_type_specifier();
    if (type_spec) {
        node->data_type = type_spec->text;
        node->add_child(std::move(type_spec));
    }

    // Parse variable name(s)
    while (peek().type == TokenType::Identifier) {
        auto var = std::make_unique<ASTNode>(ASTNodeType::VariableDeclaration);
        var->text = consume().value;
        var->data_type = node->data_type;

        // Array specifier
        if (peek().type == TokenType::LBracket) {
            consume();
            auto arr = std::make_unique<ASTNode>(ASTNodeType::ArraySpecifier);
            while (peek().type != TokenType::RBracket && peek().type != TokenType::EndOfFile) {
                arr->text += consume().value + " ";
            }
            match(TokenType::RBracket);
            var->add_child(std::move(arr));
        }

        // Initializer
        if (peek().type == TokenType::Assign) {
            consume();
            auto init = parse_expression();
            if (init) var->add_child(std::move(init));
        }

        node->add_child(std::move(var));

        if (peek().type == TokenType::Comma) { consume(); continue; }
        break;
    }

    match(TokenType::Semicolon);
    return node;
}

std::unique_ptr<ASTNode> Parser::parse_type_specifier() {
    auto node = std::make_unique<ASTNode>(ASTNodeType::TypeSpecifier);
    // Read type name (could be keyword or identifier for user-defined types)
    if (peek().type != TokenType::EndOfFile) {
        node->text = consume().value;
    }
    return node;
}

std::unique_ptr<ASTNode> Parser::parse_function_definition() {
    auto node = std::make_unique<ASTNode>(ASTNodeType::FunctionDefinition);
    node->line = peek().line;

    // Return type
    auto ret_type = parse_type_specifier();
    node->data_type = ret_type->text;
    node->add_child(std::move(ret_type));

    // Function name
    if (peek().type == TokenType::Identifier) {
        node->text = consume().value;
    }

    // Parameters
    expect(TokenType::LParen, "function definition");
    while (peek().type != TokenType::RParen && peek().type != TokenType::EndOfFile) {
        auto param = std::make_unique<ASTNode>(ASTNodeType::VariableDeclaration);
        // Qualifiers
        while (peek().type == TokenType::KwIn || peek().type == TokenType::KwOut ||
               peek().type == TokenType::KwInout || peek().type == TokenType::KwConst ||
               peek().type == TokenType::KwHighp || peek().type == TokenType::KwMediump ||
               peek().type == TokenType::KwLowp) {
            param->qualifiers.push_back(consume().value);
        }
        // Type
        auto ptype = parse_type_specifier();
        param->data_type = ptype->text;
        param->add_child(std::move(ptype));
        // Name
        if (peek().type == TokenType::Identifier) {
            param->text = consume().value;
        }
        // Array
        if (peek().type == TokenType::LBracket) {
            consume();
            while (peek().type != TokenType::RBracket && peek().type != TokenType::EndOfFile) consume();
            match(TokenType::RBracket);
        }
        node->add_child(std::move(param));
        if (peek().type == TokenType::Comma) consume();
    }
    match(TokenType::RParen);

    // Function body
    if (peek().type == TokenType::LBrace) {
        auto body = parse_compound_statement();
        node->add_child(std::move(body));
    } else {
        match(TokenType::Semicolon); // forward declaration
    }

    return node;
}

std::unique_ptr<ASTNode> Parser::parse_struct_declaration() {
    auto node = std::make_unique<ASTNode>(ASTNodeType::StructDeclaration);
    node->line = peek().line;
    consume(); // 'struct'
    if (peek().type == TokenType::Identifier) {
        node->text = consume().value; // struct name
    }
    expect(TokenType::LBrace, "struct declaration");
    while (peek().type != TokenType::RBrace && peek().type != TokenType::EndOfFile) {
        auto field = std::make_unique<ASTNode>(ASTNodeType::VariableDeclaration);
        auto ftype = parse_type_specifier();
        field->data_type = ftype->text;
        field->add_child(std::move(ftype));
        if (peek().type == TokenType::Identifier) field->text = consume().value;
        if (peek().type == TokenType::LBracket) { consume(); while (peek().type != TokenType::RBracket) consume(); match(TokenType::RBracket); }
        match(TokenType::Semicolon);
        node->add_child(std::move(field));
    }
    match(TokenType::RBrace);
    // Optional variable name after struct
    if (peek().type == TokenType::Identifier) { consume(); }
    match(TokenType::Semicolon);
    return node;
}

std::unique_ptr<ASTNode> Parser::parse_compound_statement() {
    auto node = std::make_unique<ASTNode>(ASTNodeType::CompoundStatement);
    expect(TokenType::LBrace, "compound statement");
    while (peek().type != TokenType::RBrace && peek().type != TokenType::EndOfFile) {
        auto stmt = parse_statement();
        if (stmt) node->add_child(std::move(stmt));
    }
    match(TokenType::RBrace);
    return node;
}

std::unique_ptr<ASTNode> Parser::parse_statement() {
    const Token& t = peek();
    switch (t.type) {
        case TokenType::LBrace: return parse_compound_statement();
        case TokenType::KwIf: return parse_if_statement();
        case TokenType::KwFor: return parse_for_statement();
        case TokenType::KwWhile: return parse_while_statement();
        case TokenType::KwReturn: return parse_return_statement();
        case TokenType::KwBreak: consume(); match(TokenType::Semicolon); return std::make_unique<ASTNode>(ASTNodeType::BreakStatement);
        case TokenType::KwContinue: consume(); match(TokenType::Semicolon); return std::make_unique<ASTNode>(ASTNodeType::ContinueStatement);
        case TokenType::KwDiscard: consume(); match(TokenType::Semicolon); return std::make_unique<ASTNode>(ASTNodeType::DiscardStatement);
        // Type qualifier — declaration statement
        case TokenType::KwConst: case TokenType::KwInt: case TokenType::KwFloat:
        case TokenType::KwVec2: case TokenType::KwVec3: case TokenType::KwVec4:
        case TokenType::KwIvec2: case TokenType::KwIvec3: case TokenType::KwIvec4:
        case TokenType::KwBool: case TokenType::KwMat2: case TokenType::KwMat3: case TokenType::KwMat4:
        case TokenType::KwHighp: case TokenType::KwMediump: case TokenType::KwLowp: {
            // Simple: read until semicolon
            auto stmt = std::make_unique<ASTNode>(ASTNodeType::DeclarationStatement);
            while (peek().type != TokenType::Semicolon && peek().type != TokenType::EndOfFile) {
                stmt->text += consume().value + " ";
            }
            match(TokenType::Semicolon);
            return stmt;
        }
        default: {
            // Expression statement
            auto expr = parse_expression();
            match(TokenType::Semicolon);
            if (expr) return expr;
            // Skip unknown tokens to avoid infinite loop
            if (peek().type != TokenType::EndOfFile) consume();
            return nullptr;
        }
    }
}

std::unique_ptr<ASTNode> Parser::parse_if_statement() {
    auto node = std::make_unique<ASTNode>(ASTNodeType::IfStatement);
    consume(); // 'if'
    expect(TokenType::LParen, "if condition");
    auto cond = parse_expression();
    match(TokenType::RParen);
    auto then_stmt = parse_statement();
    if (cond) node->add_child(std::move(cond));
    if (then_stmt) node->add_child(std::move(then_stmt));
    if (peek().type == TokenType::KwElse) {
        consume();
        auto else_stmt = parse_statement();
        if (else_stmt) node->add_child(std::move(else_stmt));
    }
    return node;
}

std::unique_ptr<ASTNode> Parser::parse_for_statement() {
    auto node = std::make_unique<ASTNode>(ASTNodeType::ForStatement);
    consume(); // 'for'
    expect(TokenType::LParen, "for loop");
    // Read init, condition, increment as text
    while (peek().type != TokenType::Semicolon) { node->text += consume().value + " "; if (peek().type == TokenType::EndOfFile) break; }
    match(TokenType::Semicolon);
    while (peek().type != TokenType::Semicolon) { node->text += consume().value + " "; if (peek().type == TokenType::EndOfFile) break; }
    match(TokenType::Semicolon);
    while (peek().type != TokenType::RParen) { node->text += consume().value + " "; if (peek().type == TokenType::EndOfFile) break; }
    match(TokenType::RParen);
    auto body = parse_statement();
    if (body) node->add_child(std::move(body));
    return node;
}

std::unique_ptr<ASTNode> Parser::parse_while_statement() {
    auto node = std::make_unique<ASTNode>(ASTNodeType::WhileStatement);
    consume(); // 'while'
    expect(TokenType::LParen, "while condition");
    auto cond = parse_expression();
    match(TokenType::RParen);
    auto body = parse_statement();
    if (cond) node->add_child(std::move(cond));
    if (body) node->add_child(std::move(body));
    return node;
}

std::unique_ptr<ASTNode> Parser::parse_return_statement() {
    auto node = std::make_unique<ASTNode>(ASTNodeType::ReturnStatement);
    consume(); // 'return'
    if (peek().type != TokenType::Semicolon) {
        auto expr = parse_expression();
        if (expr) node->add_child(std::move(expr));
    }
    match(TokenType::Semicolon);
    return node;
}

// Expression parsing — recursive descent
std::unique_ptr<ASTNode> Parser::parse_expression() {
    return parse_assignment();
}

std::unique_ptr<ASTNode> Parser::parse_assignment() {
    auto left = parse_ternary();
    if (peek().type == TokenType::Assign || peek().type == TokenType::PlusAssign ||
        peek().type == TokenType::MinusAssign || peek().type == TokenType::StarAssign ||
        peek().type == TokenType::SlashAssign) {
        auto node = std::make_unique<ASTNode>(ASTNodeType::Assignment);
        node->text = consume().value; // operator
        auto right = parse_assignment();
        if (left) node->add_child(std::move(left));
        if (right) node->add_child(std::move(right));
        return node;
    }
    return left;
}

std::unique_ptr<ASTNode> Parser::parse_ternary() {
    auto cond = parse_logical_or();
    if (peek().type == TokenType::QuestionMark) {
        consume();
        auto true_expr = parse_expression();
        match(TokenType::Colon);
        auto false_expr = parse_assignment();
        auto node = std::make_unique<ASTNode>(ASTNodeType::TernaryOp);
        if (cond) node->add_child(std::move(cond));
        if (true_expr) node->add_child(std::move(true_expr));
        if (false_expr) node->add_child(std::move(false_expr));
        return node;
    }
    return cond;
}

std::unique_ptr<ASTNode> Parser::parse_logical_or() {
    auto left = parse_logical_and();
    while (peek().type == TokenType::LogicalOr) {
        consume();
        auto right = parse_logical_and();
        auto node = std::make_unique<ASTNode>(ASTNodeType::BinaryOp);
        node->text = "||";
        if (left) node->add_child(std::move(left));
        if (right) node->add_child(std::move(right));
        left = std::move(node);
    }
    return left;
}

std::unique_ptr<ASTNode> Parser::parse_logical_and() {
    auto left = parse_equality();
    while (peek().type == TokenType::LogicalAnd) {
        consume();
        auto right = parse_equality();
        auto node = std::make_unique<ASTNode>(ASTNodeType::BinaryOp);
        node->text = "&&";
        if (left) node->add_child(std::move(left));
        if (right) node->add_child(std::move(right));
        left = std::move(node);
    }
    return left;
}

std::unique_ptr<ASTNode> Parser::parse_equality() {
    auto left = parse_relational();
    while (peek().type == TokenType::Eq || peek().type == TokenType::Neq) {
        std::string op = consume().value;
        auto right = parse_relational();
        auto node = std::make_unique<ASTNode>(ASTNodeType::BinaryOp);
        node->text = op;
        if (left) node->add_child(std::move(left));
        if (right) node->add_child(std::move(right));
        left = std::move(node);
    }
    return left;
}

std::unique_ptr<ASTNode> Parser::parse_relational() {
    auto left = parse_additive();
    while (peek().type == TokenType::Lt || peek().type == TokenType::Gt ||
           peek().type == TokenType::Le || peek().type == TokenType::Ge) {
        std::string op = consume().value;
        auto right = parse_additive();
        auto node = std::make_unique<ASTNode>(ASTNodeType::BinaryOp);
        node->text = op;
        if (left) node->add_child(std::move(left));
        if (right) node->add_child(std::move(right));
        left = std::move(node);
    }
    return left;
}

std::unique_ptr<ASTNode> Parser::parse_additive() {
    auto left = parse_multiplicative();
    while (peek().type == TokenType::Plus || peek().type == TokenType::Minus) {
        std::string op = consume().value;
        auto right = parse_multiplicative();
        auto node = std::make_unique<ASTNode>(ASTNodeType::BinaryOp);
        node->text = op;
        if (left) node->add_child(std::move(left));
        if (right) node->add_child(std::move(right));
        left = std::move(node);
    }
    return left;
}

std::unique_ptr<ASTNode> Parser::parse_multiplicative() {
    auto left = parse_unary();
    while (peek().type == TokenType::Star || peek().type == TokenType::Slash || peek().type == TokenType::Percent) {
        std::string op = consume().value;
        auto right = parse_unary();
        auto node = std::make_unique<ASTNode>(ASTNodeType::BinaryOp);
        node->text = op;
        if (left) node->add_child(std::move(left));
        if (right) node->add_child(std::move(right));
        left = std::move(node);
    }
    return left;
}

std::unique_ptr<ASTNode> Parser::parse_unary() {
    if (peek().type == TokenType::Plus || peek().type == TokenType::Minus ||
        peek().type == TokenType::LogicalNot || peek().type == TokenType::BitwiseNot ||
        peek().type == TokenType::Inc || peek().type == TokenType::Dec) {
        std::string op = consume().value;
        auto operand = parse_unary();
        auto node = std::make_unique<ASTNode>(ASTNodeType::UnaryOp);
        node->text = op;
        if (operand) node->add_child(std::move(operand));
        return node;
    }
    return parse_primary();
}

std::unique_ptr<ASTNode> Parser::parse_primary() {
    const Token& t = peek();
    if (t.type == TokenType::IntLiteral) {
        consume();
        return std::make_unique<ASTNode>(ASTNodeType::IntLiteralExpr, t.value);
    }
    if (t.type == TokenType::FloatLiteral) {
        consume();
        return std::make_unique<ASTNode>(ASTNodeType::FloatLiteralExpr, t.value);
    }
    if (t.type == TokenType::KwTrue || t.type == TokenType::KwFalse) {
        consume();
        return std::make_unique<ASTNode>(ASTNodeType::BoolLiteralExpr, t.value);
    }
    if (t.type == TokenType::Identifier) {
        consume();
        auto node = std::make_unique<ASTNode>(ASTNodeType::IdentifierExpr, t.value);
        // Function call or constructor
        if (peek().type == TokenType::LParen) {
            return parse_function_call_with_base(std::move(node));
        }
        // Member access
        return parse_member_access_with_base(std::move(node));
    }
    // Type constructor (vec3, mat4, etc.)
    if (t.type == TokenType::KwVec2 || t.type == TokenType::KwVec3 || t.type == TokenType::KwVec4 ||
        t.type == TokenType::KwIvec2 || t.type == TokenType::KwIvec3 || t.type == TokenType::KwIvec4 ||
        t.type == TokenType::KwBvec2 || t.type == TokenType::KwBvec3 || t.type == TokenType::KwBvec4 ||
        t.type == TokenType::KwUvec2 || t.type == TokenType::KwUvec3 || t.type == TokenType::KwUvec4 ||
        t.type == TokenType::KwMat2 || t.type == TokenType::KwMat3 || t.type == TokenType::KwMat4 ||
        t.type == TokenType::KwFloat || t.type == TokenType::KwInt || t.type == TokenType::KwBool ||
        t.type == TokenType::KwUint || t.type == TokenType::KwDouble) {
        consume();
        auto node = std::make_unique<ASTNode>(ASTNodeType::ConstructorCall, t.value);
        if (peek().type == TokenType::LParen) {
            consume();
            while (peek().type != TokenType::RParen && peek().type != TokenType::EndOfFile) {
                auto arg = parse_expression();
                if (arg) node->add_child(std::move(arg));
                if (peek().type == TokenType::Comma) consume();
            }
            match(TokenType::RParen);
        }
        return node;
    }
    if (t.type == TokenType::LParen) {
        consume();
        auto expr = parse_expression();
        match(TokenType::RParen);
        return expr;
    }
    // Unknown — consume to avoid infinite loop
    consume();
    return nullptr;
}

// Helper: parse function call when we already have the callee
std::unique_ptr<ASTNode> Parser::parse_function_call_with_base(std::unique_ptr<ASTNode> base) {
    auto node = std::make_unique<ASTNode>(ASTNodeType::FunctionCall);
    node->text = base->text;
    expect(TokenType::LParen, "function call");
    while (peek().type != TokenType::RParen && peek().type != TokenType::EndOfFile) {
        auto arg = parse_expression();
        if (arg) node->add_child(std::move(arg));
        if (peek().type == TokenType::Comma) consume();
    }
    match(TokenType::RParen);
    // Member access after function call
    return parse_member_access_with_base(std::move(node));
}

std::unique_ptr<ASTNode> Parser::parse_member_access_with_base(std::unique_ptr<ASTNode> base) {
    while (peek().type == TokenType::Dot || peek().type == TokenType::LBracket) {
        if (peek().type == TokenType::Dot) {
            consume();
            auto member = std::make_unique<ASTNode>(ASTNodeType::MemberAccess);
            member->text = ".";
            if (peek().type == TokenType::Identifier) {
                auto child = std::make_unique<ASTNode>(ASTNodeType::IdentifierExpr, consume().value);
                member->add_child(std::move(child));
            }
            member->add_child(std::move(base));
            base = std::move(member);
        } else {
            consume(); // [
            auto idx = parse_expression();
            match(TokenType::RBracket);
            auto access = std::make_unique<ASTNode>(ASTNodeType::ArrayAccess);
            access->text = "[]";
            if (idx) access->add_child(std::move(idx));
            access->add_child(std::move(base));
            base = std::move(access);
        }
    }
    return base;
}

// ============================================================================
// Main parse function
// ============================================================================

ShaderInfo parse_shader(const std::string& source, ShaderStage stage) {
    ShaderInfo info;
    info.stage = stage;

    LOGI("FearTurbo: Parsing shader (stage=%d, source_len=%zu)", (int)stage, source.size());

    Lexer lexer(source);
    auto tokens = lexer.tokenize();
    LOGI("FearTurbo: Lexer produced %zu tokens", tokens.size());

    Parser parser(tokens);
    info.ast = parser.parse();

    // Extract version from the AST
    if (info.ast) {
        for (auto& child : info.ast->children) {
            if (child->type == ASTNodeType::VersionDirective) {
                // text = "version 330 core" etc.
                std::string vt = child->text;
                // Extract the version number
                size_t sp = vt.find(' ');
                if (sp != std::string::npos) {
                    std::string ver = vt.substr(sp + 1);
                    sp = ver.find(' ');
                    if (sp != std::string::npos) ver = ver.substr(0, sp);
                    info.version = ver;
                }
            } else if (child->type == ASTNodeType::ExtensionDirective) {
                info.extensions.push_back(child->text);
            }
        }
    }

    LOGI("FearTurbo: Parsed shader version=%s, extensions=%zu", info.version.c_str(), info.extensions.size());
    return info;
}

} // namespace fear_turbo
