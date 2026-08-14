#include "fear_shader_translator.h"
#include "fear_shader_logger.h"
#include <algorithm>

// String Helpers implementation
void replaceAll(std::string& str, const std::string& from, const std::string& to) {
    if (from.empty()) return;
    size_t start_pos = 0;
    while ((start_pos = str.find(from, start_pos)) != std::string::npos) {
        str.replace(start_pos, from.length(), to);
        start_pos += to.length();
    }
}

void insertAfterLine(std::string& code, const std::string& targetLine, const std::string& insertText) {
    size_t pos = code.find(targetLine);
    if (pos == std::string::npos) return;

    size_t end_line = code.find("\n", pos);
    if (end_line == std::string::npos) {
        code += "\n" + insertText + "\n";
    } else {
        size_t line_start = code.rfind("\n", pos);
        if (line_start == std::string::npos) line_start = 0;
        else line_start += 1;

        std::string indent = "";
        while (line_start < code.length() && (code[line_start] == ' ' || code[line_start] == '\t')) {
            indent += code[line_start];
            line_start++;
        }

        code.insert(end_line + 1, indent + insertText + "\n");
    }
}

void insertBeforeMain(std::string& code, const std::string& insertText) {
    size_t main_pos = code.find("void main");
    if (main_pos == std::string::npos) {
        main_pos = code.find("main()");
    }
    if (main_pos != std::string::npos) {
        code.insert(main_pos, insertText + "\n");
    } else {
        code += "\n" + insertText + "\n";
    }
}

void removeLinesContaining(std::string& code, const std::string& substring) {
    size_t pos = 0;
    while ((pos = code.find(substring, pos)) != std::string::npos) {
        size_t line_start = code.rfind("\n", pos);
        if (line_start == std::string::npos) line_start = 0;
        else line_start += 1;

        size_t line_end = code.find("\n", pos);
        if (line_end == std::string::npos) line_end = code.length();

        code.replace(line_start, line_end - line_start, "");
        pos = line_start;
    }
}

// Shader Type Helpers implementation
bool isVertexShader(GLenum type) { return type == GL_VERTEX_SHADER; }
bool isFragmentShader(GLenum type) { return type == GL_FRAGMENT_SHADER; }
bool isGeometryShader(GLenum type) { return type == GL_GEOMETRY_SHADER; }
bool isComputeShader(GLenum type) { return type == GL_COMPUTE_SHADER; }

// Main Core Translation function
std::string FearTranslateGLSL(
    const char* sourceCode,
    GLenum shaderType,
    bool* translationSuccess
) {
    if (!sourceCode) {
        *translationSuccess = false;
        return "";
    }

    *translationSuccess = true;

    // STEP 2.6 - GEOMETRY SHADER HANDLING:
    if (isGeometryShader(shaderType)) {
        *translationSuccess = false;
        LOG_WARNING("[FearEngine] WARNING: Geometry shader detected - not supported on mobile, skipping");
        return "";
    }

    std::string glsl(sourceCode);

    // SECTION 3: COMPUTE SHADER EMULATION DETECTION
    bool isCompute = isComputeShader(shaderType) ||
                     glsl.find("layout(local_size_x") != std::string::npos;

    // STEP 2.1 - VERSION DIRECTIVE REPLACEMENT:
    size_t version_pos = glsl.find("#version");
    bool has_version = false;
    std::string version_num = "";
    size_t version_line_end = 0;
    if (version_pos != std::string::npos) {
        has_version = true;
        version_line_end = glsl.find("\n", version_pos);
        if (version_line_end != std::string::npos) {
            std::string line = glsl.substr(version_pos, version_line_end - version_pos);
            size_t num_pos = line.find_first_of("0123456789");
            if (num_pos != std::string::npos) {
                size_t space_pos = line.find_first_not_of("0123456789", num_pos);
                if (space_pos != std::string::npos) {
                    version_num = line.substr(num_pos, space_pos - num_pos);
                } else {
                    version_num = line.substr(num_pos);
                }
            }
        }
    }

    std::string target_version = "#version 300 es";
    if (has_version) {
        if (version_num == "100" || version_num == "110" || version_num == "120" ||
            version_num == "130" || version_num == "140" || version_num == "150" ||
            version_num == "330") {
            target_version = "#version 300 es";
        } else {
            target_version = "#version 320 es";
        }
        glsl.replace(version_pos, version_line_end - version_pos, target_version);
    } else {
        glsl = "#version 300 es\n" + glsl;
        target_version = "#version 300 es";
    }

    // SECTION 13: MOBILE DEFINES & EXTENSIONS
    std::string mobile_defines = "\n#define MC_ANDROID\n#define FEAR_MOBILE\n#define FEAR_MAX_SHADOWS 2\n#define FEAR_MAX_LIGHTS 4\n";
    if (glsl.find("dFdx") != std::string::npos || glsl.find("dFdy") != std::string::npos || glsl.find("fwidth") != std::string::npos) {
        mobile_defines += "#extension GL_OES_standard_derivatives : enable\n";
    }
    insertAfterLine(glsl, target_version, mobile_defines);

    // STEP 2.2 - PRECISION QUALIFIER INJECTION:
    bool has_precision = (glsl.find("precision") != std::string::npos);
    if (!has_precision) {
        std::string inject_text = "precision highp float;\nprecision highp int;";
        if (isFragmentShader(shaderType) || isCompute) {
            inject_text += "\nprecision mediump sampler2D;\nprecision mediump sampler2DArray;";
        }
        insertAfterLine(glsl, target_version, inject_text);
    }

    // SECTION 1: DERIVATIVES (Balanced parens parser)
    size_t fwidth_pos = 0;
    while ((fwidth_pos = glsl.find("fwidth(", fwidth_pos)) != std::string::npos) {
        size_t close_paren = glsl.find(")", fwidth_pos);
        if (close_paren != std::string::npos) {
            std::string arg = glsl.substr(fwidth_pos + 7, close_paren - (fwidth_pos + 7));
            std::string replacement = "(abs(dFdx(" + arg + ")) + abs(dFdy(" + arg + ")))";
            glsl.replace(fwidth_pos, close_paren + 1 - fwidth_pos, replacement);
            fwidth_pos += replacement.length();
        } else {
            fwidth_pos += 7;
        }
    }

    if (glsl.find("fear_calcNormal") == std::string::npos && glsl.find("dFdx(") != std::string::npos) {
        std::string normal_helper = "vec3 fear_calcNormal(vec3 pos) {\n"
                                    "    vec3 dp1 = dFdx(pos);\n"
                                    "    vec3 dp2 = dFdy(pos);\n"
                                    "    return normalize(cross(dp1, dp2));\n"
                                    "}\n";
        insertBeforeMain(glsl, normal_helper);
    }

    // SECTION 2: TEXTURE ARRAYS
    if (glsl.find("sampler2DArray") != std::string::npos && glsl.find("fear_sampleArray") == std::string::npos) {
        std::string array_helper = "vec4 fear_sampleArray(sampler2DArray sampler, vec2 uv, float layer) {\n"
                                   "    return texture(sampler, vec3(uv, layer));\n"
                                   "}\n";
        insertBeforeMain(glsl, array_helper);
    }

    replaceAll(glsl, "uniform sampler2D lightmap;", "uniform sampler2DArray lightmap;");
    if (glsl.find("sampler2DArray lightmap;") != std::string::npos && glsl.find("uniform int lightmap_layer;") == std::string::npos) {
        insertAfterLine(glsl, target_version, "uniform int lightmap_layer;");
    }
    replaceAll(glsl, "texture(lightmap, uv)", "texture(lightmap, vec3(uv, float(lightmap_layer)))");

    // SECTION 3: COMPUTE SHADER EMULATION
    if (isCompute) {
        removeLinesContaining(glsl, "layout(local_size_x");
        removeLinesContaining(glsl, "layout (local_size_x");
        replaceAll(glsl, "gl_GlobalInvocationID", "gl_FragCoord.xy");
        replaceAll(glsl, "gl_WorkGroupID", "vec2(0.0)");
        replaceAll(glsl, "gl_LocalInvocationID", "gl_FragCoord.xy");
        replaceAll(glsl, "gl_NumWorkGroups", "vec2(textureSize(outputImage, 0))");

        shaderType = GL_FRAGMENT_SHADER;
    }

    // SECTION 4: IMAGE LOAD/STORE OPERATIONS
    replaceAll(glsl, "writeonly image2D", "image2D");
    replaceAll(glsl, "readonly image2D", "sampler2D");
    replaceAll(glsl, "image2D", "sampler2D");
    replaceAll(glsl, "imageLoad(", "texelFetch(");
    replaceAll(glsl, "imageSize(", "textureSize(");

    // STEP 2.3 - TEXTURE FUNCTION REPLACEMENT:
    replaceAll(glsl, "texture2D(", "texture(");
    replaceAll(glsl, "texture2DProj(", "textureProj(");
    replaceAll(glsl, "texture2DLod(", "textureLod(");
    replaceAll(glsl, "texture2DGrad(", "textureGrad(");
    replaceAll(glsl, "textureCube(", "texture(");
    replaceAll(glsl, "textureCubeLod(", "textureLod(");
    replaceAll(glsl, "texture3D(", "texture(");
    replaceAll(glsl, "texture1D(", "texture(");
    replaceAll(glsl, "shadow2D(", "texture(");
    replaceAll(glsl, "shadow2DProj(", "textureProj(");

    // SECTION 5: ADVANCED TEXTURE FUNCTIONS (Balanced parens parser)
    size_t gather_pos = 0;
    while ((gather_pos = glsl.find("textureGather(", gather_pos)) != std::string::npos) {
        size_t close_paren = glsl.find(")", gather_pos);
        if (close_paren != std::string::npos) {
            std::string args = glsl.substr(gather_pos + 14, close_paren - (gather_pos + 14));
            std::string replacement = "vec4(texture(" + args + ").r)";
            glsl.replace(gather_pos, close_paren + 1 - gather_pos, replacement);
            gather_pos += replacement.length();
        } else {
            gather_pos += 14;
        }
    }

    replaceAll(glsl, "textureQueryLod(", "vec2(0.0, 0.0)");
    replaceAll(glsl, "textureQueryLevels(", "1");
    replaceAll(glsl, "textureSamples(", "1");

    // SECTION 6: MATRIX OPERATIONS HELPERS
    if (glsl.find("inverse(") != std::string::npos || glsl.find("transpose(") != std::string::npos) {
        std::string matrix_helpers = "mat3 fear_transpose(mat3 m) {\n"
                                     "    return mat3(m[0][0], m[1][0], m[2][0],\n"
                                     "                m[0][1], m[1][1], m[2][1],\n"
                                     "                m[0][2], m[1][2], m[2][2]);\n"
                                     "}\n"
                                     "mat3 fear_inverse(mat3 m) {\n"
                                     "    float a00 = m[0][0], a01 = m[0][1], a02 = m[0][2];\n"
                                     "    float a10 = m[1][0], a11 = m[1][1], a12 = m[1][2];\n"
                                     "    float a20 = m[2][0], a21 = m[2][1], a22 = m[2][2];\n"
                                     "    float b01 = a22 * a11 - a12 * a21;\n"
                                     "    float b11 = -a22 * a10 + a12 * a20;\n"
                                     "    float b21 = a21 * a10 - a11 * a20;\n"
                                     "    float det = a00 * b01 + a01 * b11 + a02 * b21;\n"
                                     "    return mat3(b01, b11, b21,\n"
                                     "                -a22 * a01 + a02 * a21, a22 * a00 - a02 * a20, -a21 * a00 + a01 * a20,\n"
                                     "                a12 * a01 - a02 * a11, -a12 * a00 + a02 * a10, a11 * a00 - a01 * a10) / det;\n"
                                     "}\n";
        insertBeforeMain(glsl, matrix_helpers);
        replaceAll(glsl, "inverse(", "fear_inverse(");
        replaceAll(glsl, "transpose(", "fear_transpose(");
    }

    // SECTION 7: BITFIELD OPERATIONS
    if (glsl.find("bitfieldExtract(") != std::string::npos || glsl.find("bitfieldInsert(") != std::string::npos) {
        std::string bitfield_helpers = "uint fear_bitfieldExtract(uint value, int offset, int bits) {\n"
                                       "    uint mask = (1u << bits) - 1u;\n"
                                       "    return (value >> offset) & mask;\n"
                                       "}\n"
                                       "uint fear_bitfieldInsert(uint base, uint insert, int offset, int bits) {\n"
                                       "    uint mask = ((1u << bits) - 1u) << offset;\n"
                                       "    return (base & ~mask) | ((insert << offset) & mask);\n"
                                       "}\n";
        insertBeforeMain(glsl, bitfield_helpers);
        replaceAll(glsl, "bitfieldExtract(", "fear_bitfieldExtract(");
        replaceAll(glsl, "bitfieldInsert(", "fear_bitfieldInsert(");
    }

    // SECTION 8: FORMAT SPECIFIER DOWNGRADES
    replaceAll(glsl, "layout(rgba32f)", "layout(rgba16f)");
    replaceAll(glsl, "layout(r32f)", "layout(r16f)");
    replaceAll(glsl, "layout(rg32f)", "layout(rg16f)");
    replaceAll(glsl, "layout(rgb32f)", "layout(rgba16f)");

    // STEP 2.4 - VERTEX SHADER SPECIFIC RULES:
    if (isVertexShader(shaderType)) {
        replaceAll(glsl, "attribute ", "in ");
        replaceAll(glsl, "varying ", "out ");

        replaceAll(glsl, "gl_ModelViewProjectionMatrix", "(gl_ProjectionMatrix * gl_ModelViewMatrix)");

        if (glsl.find("gl_ModelViewMatrix") != std::string::npos && glsl.find("uniform mat4 gl_ModelViewMatrix") == std::string::npos) {
            replaceAll(glsl, "gl_ModelViewMatrix", "u_ModelViewMatrix");
            if (glsl.find("uniform mat4 u_ModelViewMatrix") == std::string::npos) {
                insertAfterLine(glsl, target_version, "uniform mat4 u_ModelViewMatrix;");
            }
        }

        if (glsl.find("gl_ProjectionMatrix") != std::string::npos && glsl.find("uniform mat4 gl_ProjectionMatrix") == std::string::npos) {
            if (glsl.find("uniform mat4 gl_ProjectionMatrix") == std::string::npos) {
                insertAfterLine(glsl, target_version, "uniform mat4 gl_ProjectionMatrix;");
            }
        }

        replaceAll(glsl, "ftransform()", "(gl_ProjectionMatrix * gl_ModelViewMatrix * gl_Vertex)");

        if (glsl.find("gl_Vertex") != std::string::npos && glsl.find("in vec4 gl_Vertex") == std::string::npos && glsl.find("attribute vec4 gl_Vertex") == std::string::npos) {
            insertAfterLine(glsl, target_version, "in vec4 gl_Vertex;");
        }

        if (glsl.find("gl_MultiTexCoord0") != std::string::npos && glsl.find("in vec4 gl_MultiTexCoord0") == std::string::npos && glsl.find("attribute vec4 gl_MultiTexCoord0") == std::string::npos) {
            insertAfterLine(glsl, target_version, "in vec4 gl_MultiTexCoord0;");
        }
    }

    // STEP 2.5 - FRAGMENT SHADER SPECIFIC RULES:
    if (isFragmentShader(shaderType)) {
        replaceAll(glsl, "varying ", "in ");
        replaceAll(glsl, "noperspective in ", "in ");
        replaceAll(glsl, "noperspective out ", "out ");
        replaceAll(glsl, "flat varying ", "flat in ");

        if (glsl.find("gl_FragColor") != std::string::npos || glsl.find("gl_FragData[0]") != std::string::npos) {
            if (glsl.find("out vec4 FragColor;") == std::string::npos) {
                insertAfterLine(glsl, target_version, "out vec4 FragColor;");
            }
            replaceAll(glsl, "gl_FragColor", "FragColor");
            replaceAll(glsl, "gl_FragData[0]", "FragColor");
        }

        if (glsl.find("gl_FragData[1]") != std::string::npos) {
            if (glsl.find("out vec4 FragData1;") == std::string::npos) {
                insertAfterLine(glsl, target_version, "out vec4 FragData1;");
            }
            replaceAll(glsl, "gl_FragData[1]", "FragData1");
        }
    }

    // STEP 2.7 - EXTENSION REMOVAL:
    size_t ext_pos = 0;
    while ((ext_pos = glsl.find("#extension", ext_pos)) != std::string::npos) {
        size_t end_line = glsl.find("\n", ext_pos);
        if (end_line != std::string::npos) {
            std::string line = glsl.substr(ext_pos, end_line - ext_pos);
            if (line.find("GL_OES_standard_derivatives") == std::string::npos &&
                line.find("GL_EXT_shader_texture_lod") == std::string::npos) {
                if (line.find("GL_ARB_") != std::string::npos ||
                    line.find("GL_EXT_") != std::string::npos ||
                    line.find("GL_NV_") != std::string::npos) {
                    glsl.replace(ext_pos, end_line - ext_pos, "// removed extension");
                }
            }
        }
        ext_pos = glsl.find("#extension", ext_pos + 1);
    }

    // STEP 2.8 - BUILTIN VARIABLE FIXES:
    replaceAll(glsl, "gl_MaxLights", "8");
    replaceAll(glsl, "gl_MaxClipPlanes", "6");
    replaceAll(glsl, "gl_MaxTextureUnits", "8");
    replaceAll(glsl, "gl_MaxTextureCoords", "8");
    replaceAll(glsl, "gl_MaxVertexAttribs", "16");
    replaceAll(glsl, "gl_MaxVertexTextureImageUnits", "16");
    replaceAll(glsl, "gl_MaxCombinedTextureImageUnits", "32");
    replaceAll(glsl, "gl_MaxTextureImageUnits", "16");
    replaceAll(glsl, "gl_MaxFragmentUniformComponents", "1024");
    replaceAll(glsl, "gl_MaxVertexUniformComponents", "1024");
    replaceAll(glsl, "gl_MaxVaryingFloats", "32");
    replaceAll(glsl, "gl_MaxVaryingComponents", "16");

    // STEP 2.10 - SAMPLER TYPE FIXES:
    if (glsl.find("sampler1D") != std::string::npos) {
        replaceAll(glsl, "sampler1DShadow", "sampler2DShadow");
        replaceAll(glsl, "sampler1D", "sampler2D");
        LOG_WARNING("[FearEngine] Sampler1D replaced with sampler2D");
    }

    // STEP 2.11 - LAYOUT QUALIFIER HANDLING:
    if (target_version == "#version 300 es") {
        size_t layout_pos = 0;
        while ((layout_pos = glsl.find("layout(location", layout_pos)) != std::string::npos) {
            size_t end_bracket = glsl.find(")", layout_pos);
            if (end_bracket != std::string::npos) {
                glsl.replace(layout_pos, end_bracket + 1 - layout_pos, "");
            } else {
                layout_pos += 15;
            }
        }

        layout_pos = 0;
        while ((layout_pos = glsl.find("layout(binding", layout_pos)) != std::string::npos) {
            size_t end_bracket = glsl.find(")", layout_pos);
            if (end_bracket != std::string::npos) {
                glsl.replace(layout_pos, end_bracket + 1 - layout_pos, "");
            } else {
                layout_pos += 14;
            }
        }
    }

    // STEP 2.12 - NOISE FUNCTION REMOVAL (Clean inline replacement)
    size_t noise_pos = 0;
    while ((noise_pos = glsl.find("noise1(", noise_pos)) != std::string::npos) {
        size_t close_paren = glsl.find(")", noise_pos);
        if (close_paren != std::string::npos) {
            glsl.replace(noise_pos, close_paren + 1 - noise_pos, "0.0");
        } else {
            noise_pos += 7;
        }
    }
    noise_pos = 0;
    while ((noise_pos = glsl.find("noise2(", noise_pos)) != std::string::npos) {
        size_t close_paren = glsl.find(")", noise_pos);
        if (close_paren != std::string::npos) {
            glsl.replace(noise_pos, close_paren + 1 - noise_pos, "vec2(0.0)");
        } else {
            noise_pos += 7;
        }
    }
    noise_pos = 0;
    while ((noise_pos = glsl.find("noise3(", noise_pos)) != std::string::npos) {
        size_t close_paren = glsl.find(")", noise_pos);
        if (close_paren != std::string::npos) {
            glsl.replace(noise_pos, close_paren + 1 - noise_pos, "vec3(0.0)");
        } else {
            noise_pos += 7;
        }
    }
    noise_pos = 0;
    while ((noise_pos = glsl.find("noise4(", noise_pos)) != std::string::npos) {
        size_t close_paren = glsl.find(")", noise_pos);
        if (close_paren != std::string::npos) {
            glsl.replace(noise_pos, close_paren + 1 - noise_pos, "vec4(0.0)");
        } else {
            noise_pos += 7;
        }
    }

    // SECTION 13: PERFORMANCE OPTIMIZATIONS
    replaceAll(glsl, "const int shadowMapResolution = 2048;", "const int shadowMapResolution = 1024;");
    replaceAll(glsl, "const int RAY_MARCH_STEPS = 64;", "const int RAY_MARCH_STEPS = 32;");

    // Remove vendor-specific desktop blocks
    if (glsl.find("#ifdef MC_GL_VENDOR_INTEL") != std::string::npos) {
        removeLinesContaining(glsl, "#ifdef MC_GL_VENDOR_INTEL");
    }

    return glsl;
}
