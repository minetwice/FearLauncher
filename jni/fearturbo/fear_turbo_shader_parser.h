#ifndef FEAR_TURBO_SHADER_PARSER_H
#define FEAR_TURBO_SHADER_PARSER_H

#include "fear_turbo_core.h"
#include <string>
#include <vector>
#include <memory>
#include <unordered_map>

namespace fear_turbo {

enum class TokenType {
    Identifier, IntLiteral, FloatLiteral, BoolLiteral,
    Semicolon, Comma, Dot, Colon, QuestionMark,
    LParen, RParen, LBrace, RBrace, LBracket, RBracket,
    Assign, Plus, Minus, Star, Slash, Percent,
    PlusAssign, MinusAssign, StarAssign, SlashAssign,
    Inc, Dec, LogicalAnd, LogicalOr, LogicalNot,
    BitwiseAnd, BitwiseOr, BitwiseXor, BitwiseNot,
    ShiftLeft, ShiftRight,
    Eq, Neq, Lt, Gt, Le, Ge,
    KwVersion, KwPrecision, KwExtension, KwLayout,
    KwInvariant, KwHighp, KwMediump, KwLowp,
    KwVoid, KwBool, KwInt, KwUint, KwFloat, KwDouble,
    KwVec2, KwVec3, KwVec4, KwDvec2, KwDvec3, KwDvec4,
    KwBvec2, KwBvec3, KwBvec4, KwIvec2, KwIvec3, KwIvec4,
    KwUvec2, KwUvec3, KwUvec4, KwMat2, KwMat3, KwMat4,
    KwStruct, KwIf, KwElse, KwFor, KwWhile, KwDo,
    KwReturn, KwBreak, KwContinue, KwDiscard,
    KwTrue, KwFalse, KwNullptr,
    KwConst, KwIn, KwOut, KwInout, KwUniform, KwAttribute, KwVarying,
    KwCentroid, KwFlat, KwSmooth, KwNoperspective,
    KwPatch, KwSample,
    KwImage1D, KwImage2D, KwImage3D, KwImageCube, KwImage2DArray,
    KwSampler1D, KwSampler2D, KwSampler3D, KwSamplerCube,
    KwSampler2DArray, KwSamplerCubeArray,
    KwSampler1DShadow, KwSampler2DShadow,
    KwIsampler1D, KwIsampler2D, KwIsampler3D, KwIsamplerCube,
    KwUsampler1D, KwUsampler2D, KwUsampler3D, KwUsamplerCube,
    KwBuffer, KwShared, KwCoherent, KwVolatile, KwRestrict,
    KwReadonly, KwWriteonly, KwAtomicCounter,
    KwSubroutine, KwPrecise,
    Hash, HashDefine, HashIfdef, HashIfndef, HashIf, HashEndif,
    HashElse, HashElif, HashInclude, HashUndef, HashPragma, HashLine, HashError,
    Unknown, EndOfFile,
};

struct Token {
    TokenType type;
    std::string value;
    int line;
    int column;
};

enum class ASTNodeType {
    TranslationUnit, VersionDirective, ExtensionDirective, PrecisionDirective,
    LayoutQualifier, GlobalDeclaration, FunctionDeclaration, FunctionDefinition,
    StructDeclaration,
    CompoundStatement, DeclarationStatement, ExpressionStatement,
    IfStatement, ForStatement, WhileStatement, DoWhileStatement,
    ReturnStatement, BreakStatement, ContinueStatement, DiscardStatement,
    VariableDeclaration, TypeQualifier, TypeSpecifier, ArraySpecifier,
    IdentifierExpr, IntLiteralExpr, FloatLiteralExpr, BoolLiteralExpr,
    FunctionCall, ConstructorCall, MemberAccess, ArrayAccess,
    UnaryOp, BinaryOp, TernaryOp, Assignment,
    BasicType, VectorType, MatrixType, SamplerType, ImageType, StructType,
};

struct ASTNode {
    ASTNodeType type;
    std::string text;
    std::string data_type;
    std::string qualifier;
    std::vector<std::string> qualifiers;
    std::vector<std::unique_ptr<ASTNode>> children;
    ASTNode* parent = nullptr;
    int line = 0;

    ASTNode() = default;
    ASTNode(ASTNodeType t) : type(t) {}
    ASTNode(ASTNodeType t, const std::string& txt) : type(t), text(txt) {}

    void add_child(std::unique_ptr<ASTNode> child) {
        child->parent = this;
        children.push_back(std::move(child));
    }
};

class Lexer {
public:
    explicit Lexer(const std::string& source);
    std::vector<Token> tokenize();
private:
    std::string source_;
    size_t pos_ = 0;
    int line_ = 1;
    int column_ = 1;
    Token next_token();
    Token scan_identifier();
    Token scan_number();
    Token scan_preprocessor();
    Token scan_comment();
    void skip_whitespace();
    static TokenType keyword_to_type(const std::string& kw);
};

class Parser {
public:
    explicit Parser(const std::vector<Token>& tokens);
    std::unique_ptr<ASTNode> parse();
private:
    std::vector<Token> tokens_;
    size_t pos_ = 0;
    const Token& peek(int offset = 0) const;
    Token consume();
    bool match(TokenType type);
    bool expect(TokenType type, const std::string& context);
    std::unique_ptr<ASTNode> parse_translation_unit();
    std::unique_ptr<ASTNode> parse_version_directive();
    std::unique_ptr<ASTNode> parse_extension_directive();
    std::unique_ptr<ASTNode> parse_precision_directive();
    std::unique_ptr<ASTNode> parse_global_declaration();
    std::unique_ptr<ASTNode> parse_function_definition();
    std::unique_ptr<ASTNode> parse_struct_declaration();
    std::unique_ptr<ASTNode> parse_layout_qualifier();
    std::unique_ptr<ASTNode> parse_type_qualifier();
    std::unique_ptr<ASTNode> parse_type_specifier();
    std::unique_ptr<ASTNode> parse_variable_declaration();
    std::unique_ptr<ASTNode> parse_compound_statement();
    std::unique_ptr<ASTNode> parse_statement();
    std::unique_ptr<ASTNode> parse_if_statement();
    std::unique_ptr<ASTNode> parse_for_statement();
    std::unique_ptr<ASTNode> parse_while_statement();
    std::unique_ptr<ASTNode> parse_return_statement();
    std::unique_ptr<ASTNode> parse_expression();
    std::unique_ptr<ASTNode> parse_assignment();
    std::unique_ptr<ASTNode> parse_ternary();
    std::unique_ptr<ASTNode> parse_logical_or();
    std::unique_ptr<ASTNode> parse_logical_and();
    std::unique_ptr<ASTNode> parse_equality();
    std::unique_ptr<ASTNode> parse_relational();
    std::unique_ptr<ASTNode> parse_additive();
    std::unique_ptr<ASTNode> parse_multiplicative();
    std::unique_ptr<ASTNode> parse_unary();
    std::unique_ptr<ASTNode> parse_primary();
    std::unique_ptr<ASTNode> parse_function_call_with_base(std::unique_ptr<ASTNode> base);
    std::unique_ptr<ASTNode> parse_member_access_with_base(std::unique_ptr<ASTNode> base);
};

enum class ShaderStage {
    Vertex, Fragment, Geometry, Compute, TessControl, TessEval
};

struct ShaderInfo {
    ShaderStage stage;
    std::string version;
    std::vector<std::string> extensions;
    std::unique_ptr<ASTNode> ast;
};

ShaderInfo parse_shader(const std::string& source, ShaderStage stage);

} // namespace fear_turbo

#endif // FEAR_TURBO_SHADER_PARSER_H
