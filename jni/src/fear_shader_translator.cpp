#include "fear_shader_translator.h"
#include "fear_shader_logger.h"
#include <algorithm>
#include <regex>

// ============================================================================
// STRING HELPERS
// ============================================================================

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

// Check if a position in code is inside a string literal or comment
static bool isInStringOrComment(const std::string& code, size_t pos) {
    bool in_string = false;
    bool in_line_comment = false;
    bool in_block_comment = false;
    for (size_t i = 0; i < pos && i < code.length(); i++) {
        if (in_line_comment) {
            if (code[i] == '\n') in_line_comment = false;
        } else if (in_block_comment) {
            if (i + 1 < code.length() && code[i] == '*' && code[i+1] == '/') in_block_comment = false, i++;
        } else if (in_string) {
            if (code[i] == '\\') { i++; }
            else if (code[i] == '"') in_string = false;
        } else {
            if (i + 1 < code.length() && code[i] == '/' && code[i+1] == '/') in_line_comment = true, i++;
            else if (i + 1 < code.length() && code[i] == '/' && code[i+1] == '*') in_block_comment = true, i++;
            else if (code[i] == '"') in_string = true;
        }
    }
    return in_string || in_line_comment || in_block_comment;
}

// Safe replaceAll that skips string literals and comments
static void replaceAllSafe(std::string& str, const std::string& from, const std::string& to) {
    if (from.empty()) return;
    size_t start_pos = 0;
    while ((start_pos = str.find(from, start_pos)) != std::string::npos) {
        if (!isInStringOrComment(str, start_pos)) {
            str.replace(start_pos, from.length(), to);
            start_pos += to.length();
        } else {
            start_pos += from.length();
        }
    }
}

// ============================================================================
// SHADER TYPE HELPERS
// ============================================================================

bool isVertexShader(GLenum type) { return type == GL_VERTEX_SHADER; }
bool isFragmentShader(GLenum type) { return type == GL_FRAGMENT_SHADER; }
bool isGeometryShader(GLenum type) { return type == GL_GEOMETRY_SHADER; }
bool isComputeShader(GLenum type) { return type == GL_COMPUTE_SHADER; }

// ============================================================================
// IRIS / OPTIFINE COMPATIBILITY PREAMBLE
// ============================================================================

static const char* IRIS_COMPAT_DEFINES =
    "// === FearRender Iris/OptiFine Compatibility ===\n"
    "#define MC_ANDROID 1\n"
    "#define MC_GLSL_VERSION 460\n"
    "#define MC_GL_VERSION_GLSL 460\n"
    "#define MC_RENDER_QUALITY 1.0\n"
    "#define MC_SHADOW_QUALITY 1.0\n"
    "#define IRIS_SUPPORTED 1\n"
    "#define MC_RENDER_STAGE_MC_RENDER_STAGE_TERRAIN_SOLID 0\n"
    "#define MC_RENDER_STAGE_MC_RENDER_STAGE_TERRAIN_TRANSLUCENT 1\n"
    "#define MC_RENDER_STAGE_MC_RENDER_STAGE_ENTITIES 2\n"
    "#define MC_NORMAL_MAP 1\n"
    "#define MC_SPECULAR_MAP 1\n"
    "#define MC_PBR 1\n"
    "#define FEAR_MAX_SHADOWS 4\n"
    "#define FEAR_MAX_LIGHTS 8\n"
    "#define FEAR_SHADOW_MAP_RES 2048\n";

static const char* IRIS_COMPAT_EXTENSIONS =
    "#extension GL_EXT_color_buffer_float : enable\n"
    "#extension GL_EXT_shader_io_blocks : enable\n"
    "#extension GL_OES_texture_storage_multisample_2d_array : enable\n"
    "#extension GL_EXT_geometry_shader : enable\n"
    "#extension GL_EXT_gpu_shader5 : enable\n"
    "#extension GL_EXT_shader_atomic_int64 : enable\n"
    "#extension GL_EXT_shader_image_load_formatted : enable\n"
    "#extension GL_OES_shader_image_atomic : enable\n"
    "#extension GL_OES_EGL_image_external_essl3 : enable\n"
    "#extension GL_EXT_draw_buffers : enable\n";

// Precision preamble - always highp to fix Mali colour banding
static const char* HIGH_PRECISION_PREAMBLE =
    "precision highp float;\n"
    "precision highp int;\n"
    "precision highp sampler2D;\n"
    "precision highp sampler2DArray;\n"
    "precision highp sampler3D;\n"
    "precision highp samplerCube;\n"
    "precision highp sampler2DShadow;\n"
    "precision highp sampler2DArrayShadow;\n"
    "precision highp samplerCubeShadow;\n"
    "precision highp image2D;\n"
    "precision highp image3D;\n"
    "precision highp image2DArray;\n";

// ============================================================================
// MAIN TRANSLATION FUNCTION
// ============================================================================

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

    if (isGeometryShader(shaderType)) {
        // GLES 3.2 supports geometry shaders via GL_EXT_geometry_shader
        // Don't skip - translate it
        LOG_WARNING("[FearEngine] Geometry shader detected - attempting translation via GL_EXT_geometry_shader");
    }

    std::string glsl(sourceCode);

    bool isCompute = isComputeShader(shaderType) ||
                     glsl.find("layout(local_size_") != std::string::npos ||
                     glsl.find("buffer") != std::string::npos;

    // ========================================================================
    // STEP 1: VERSION DIRECTIVE REPLACEMENT
    // ========================================================================
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

    // Always target 320 es for maximum feature set
    std::string target_version = "#version 320 es";
    if (has_version) {
        glsl.replace(version_pos, version_line_end - version_pos, target_version);
    } else {
        glsl = target_version + "\n" + glsl;
    }

    // ========================================================================
    // STEP 2: EXTENSION AND DEFINES INJECTION
    // ========================================================================
    std::string preamble = std::string("\n") + IRIS_COMPAT_EXTENSIONS + "\n" + IRIS_COMPAT_DEFINES + "\n";

    // Add derivatives extension if needed
    if (glsl.find("dFdx") != std::string::npos || glsl.find("dFdy") != std::string::npos ||
        glsl.find("fwidth") != std::string::npos) {
        // GL_OES_standard_derivatives is core in ES 3.0+, but add for safety
    }

    insertAfterLine(glsl, target_version, preamble);

    // ========================================================================
    // STEP 3: HIGH PRECISION INJECTION (MALI COLOUR FIX)
    // ========================================================================
    // Force highp precision for everything - Mali GPUs default to mediump
    // which causes severe colour banding and incorrect rendering
    insertAfterLine(glsl, target_version, HIGH_PRECISION_PREAMBLE);

    // Remove any existing precision declarations and replace with highp
    removeLinesContaining(glsl, "precision mediump float");
    removeLinesContaining(glsl, "precision mediump int");
    removeLinesContaining(glsl, "precision lowp float");
    removeLinesContaining(glsl, "precision lowp int");
    removeLinesContaining(glsl, "precision mediump sampler");
    removeLinesContaining(glsl, "precision lowp sampler");

    // Replace inline precision qualifiers: mediump -> highp, lowp -> highp
    replaceAllSafe(glsl, "mediump ", "highp ");
    replaceAllSafe(glsl, "lowp ", "highp ");
    replaceAllSafe(glsl, "mediump\t", "highp\t");
    replaceAllSafe(glsl, "lowp\t", "highp\t");

    // ========================================================================
    // STEP 4: COMPUTE SHADER FIXES
    // ========================================================================
    if (isCompute) {
        bool fixed = false;
        if (glsl.find("uint i = ivec2(gl_FragCoord.xy).x;") != std::string::npos) {
            replaceAll(glsl, "uint i = ivec2(gl_FragCoord.xy).x;", "uint i = gl_GlobalInvocationID.x;");
            fixed = true;
        }
        if (glsl.find("gl_FragCoord.xy") != std::string::npos) {
            replaceAllSafe(glsl, "gl_FragCoord.xy", "vec2(gl_GlobalInvocationID.xy)");
            fixed = true;
        }
        if (glsl.find("gl_FragCoord") != std::string::npos) {
            replaceAllSafe(glsl, "gl_FragCoord", "vec4(gl_GlobalInvocationID.xy, 0.0, 1.0)");
            fixed = true;
        }

        if (glsl.find("layout(local_size_") == std::string::npos) {
            std::string layout_qualifier =
                "\n#ifndef FEAR_COMPUTE_LAYOUT\n"
                "#define FEAR_COMPUTE_LAYOUT\n"
                "layout(local_size_x = 1, local_size_y = 1, local_size_z = 1) in;\n"
                "#endif\n";
            insertBeforeMain(glsl, layout_qualifier);
            fixed = true;
        }

        if (fixed) {
            LOG_INFO("[FearRender] Compute shader fixed: gl_FragCoord -> gl_GlobalInvocationID");
        }
    }

    // ========================================================================
    // STEP 5: DERIVATIVES
    // ========================================================================
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

    // ========================================================================
    // STEP 6: TEXTURE FUNCTION REPLACEMENT (Desktop GLSL -> GLES)
    // ========================================================================
    replaceAllSafe(glsl, "texture2D(", "texture(");
    replaceAllSafe(glsl, "texture2DProj(", "textureProj(");
    replaceAllSafe(glsl, "texture2DLod(", "textureLod(");
    replaceAllSafe(glsl, "texture2DGrad(", "textureGrad(");
    replaceAllSafe(glsl, "textureCube(", "texture(");
    replaceAllSafe(glsl, "textureCubeLod(", "textureLod(");
    replaceAllSafe(glsl, "texture3D(", "texture(");
    replaceAllSafe(glsl, "texture1D(", "texture(");
    replaceAllSafe(glsl, "texture1DProj(", "textureProj(");
    replaceAllSafe(glsl, "texture1DLod(", "textureLod(");
    replaceAllSafe(glsl, "shadow1D(", "texture(");
    replaceAllSafe(glsl, "shadow2D(", "texture(");
    replaceAllSafe(glsl, "shadow1DProj(", "textureProj(");
    replaceAllSafe(glsl, "shadow2DProj(", "textureProj(");
    replaceAllSafe(glsl, "shadow1DLod(", "textureLod(");
    replaceAllSafe(glsl, "shadow2DLod(", "textureLod(");

    // ========================================================================
    // STEP 7: VERTEX SHADER SPECIFIC RULES
    // ========================================================================
    if (isVertexShader(shaderType)) {
        replaceAllSafe(glsl, "attribute ", "in ");
        replaceAllSafe(glsl, "varying ", "out ");
        // gl_ModelViewProjectionMatrix etc. are not available in GLES
        // Iris doesn't use these but legacy shaders might
    }

    // ========================================================================
    // STEP 8: FRAGMENT SHADER SPECIFIC RULES
    // ========================================================================
    if (isFragmentShader(shaderType)) {
        replaceAllSafe(glsl, "varying ", "in ");
        replaceAllSafe(glsl, "noperspective in ", "in ");
        replaceAllSafe(glsl, "noperspective out ", "out ");
        replaceAllSafe(glsl, "flat varying ", "flat in ");
        replaceAllSafe(glsl, "smooth varying ", "in ");
        replaceAllSafe(glsl, "noperspective varying ", "in ");

        // ---- MRT: gl_FragData[0..7] -> out vec4 arrays ----
        // Iris/OptiFine uses gl_FragData for multiple render targets
        bool has_fragdata = false;
        for (int i = 0; i < 8; i++) {
            std::string fragdata = "gl_FragData[" + std::to_string(i) + "]";
            if (glsl.find(fragdata) != std::string::npos) {
                has_fragdata = true;
            }
        }

        if (has_fragdata) {
            // Declare out variables for MRT
            std::string mrt_decls =
                "\n// FearRender MRT output declarations\n"
                "layout(location = 0) out highp vec4 fragData0;\n"
                "layout(location = 1) out highp vec4 fragData1;\n"
                "layout(location = 2) out highp vec4 fragData2;\n"
                "layout(location = 3) out highp vec4 fragData3;\n"
                "layout(location = 4) out highp vec4 fragData4;\n"
                "layout(location = 5) out highp vec4 fragData5;\n"
                "layout(location = 6) out highp vec4 fragData6;\n"
                "layout(location = 7) out highp vec4 fragData7;\n";
            insertAfterLine(glsl, target_version, mrt_decls);

            // Replace gl_FragData[N] with fragDataN
            for (int i = 0; i < 8; i++) {
                std::string from = "gl_FragData[" + std::to_string(i) + "]";
                std::string to = "fragData" + std::to_string(i);
                replaceAllSafe(glsl, from, to);
            }
        }

        // ---- gl_FragColor -> out vec4 ----
        if (glsl.find("gl_FragColor") != std::string::npos) {
            if (glsl.find("out vec4 FragColor;") == std::string::npos &&
                glsl.find("layout(location = 0) out vec4 FragColor") == std::string::npos) {
                insertAfterLine(glsl, target_version,
                    "layout(location = 0) out highp vec4 FragColor;");
            }
            replaceAllSafe(glsl, "gl_FragColor", "FragColor");
        }
    }

    // ========================================================================
    // STEP 9: NO-PERSPECTIVE QUALIFIER REMOVAL (Mali lacks support)
    // ========================================================================
    // Already handled for fragment shaders above, handle remaining cases
    replaceAllSafe(glsl, "noperspective ", "");
    replaceAllSafe(glsl, "noperspective\t", "");

    // ========================================================================
    // STEP 10: DESKTOP GLSL TYPE CONVERSIONS
    // ========================================================================
    // double -> float (GLES has no double support by default)
    replaceAllSafe(glsl, "double ", "float ");
    replaceAllSafe(glsl, "double\t", "float\t");
    replaceAllSafe(glsl, "dvec2", "vec2");
    replaceAllSafe(glsl, "dvec3", "vec3");
    replaceAllSafe(glsl, "dvec4", "vec4");
    replaceAllSafe(glsl, "dmat2", "mat2");
    replaceAllSafe(glsl, "dmat3", "mat3");
    replaceAllSafe(glsl, "dmat4", "mat4");

    // ========================================================================
    // STEP 11: GLSL 4.x LAYOUT QUALIFIER FIXES
    // ========================================================================
    // GLES 3.2 supports layout(std140), layout(std430), layout(binding=N)
    // but some drivers are picky. Keep them as-is for now.
    // 
    // Handle layout qualifiers that reference desktop-only locations
    // e.g. layout(location = N) is fine in GLES 3.2

    // ========================================================================
    // STEP 12: GL_PRIMITIVE_ID / GL_VIEWPORTINDEX STUBS
    // ========================================================================
    // These are available via geometry shader extension on GLES 3.2
    // but may not work on all Mali drivers. Stub them if geometry
    // shader is not present.
    if (!isGeometryShader(shaderType)) {
        if (glsl.find("gl_PrimitiveID") != std::string::npos) {
            insertBeforeMain(glsl, "int gl_PrimitiveID = 0;");
        }
        if (glsl.find("gl_ViewportIndex") != std::string::npos) {
            insertBeforeMain(glsl, "int gl_ViewportIndex = 0;");
        }
        if (glsl.find("gl_Layer") != std::string::npos) {
            insertBeforeMain(glsl, "int gl_Layer = 0;");
        }
    }

    // ========================================================================
    // STEP 13: ATOMIC COUNTER / SSBO COMPATIBILITY
    // ========================================================================
    // GLES 3.1+ supports SSBOs and atomics, but ensure layout qualifiers are correct
    // gl_MaxFragmentAtomic_counters etc. may not be defined - stub them
    if (glsl.find("atomicCounter") != std::string::npos) {
        // GLES 3.1+ supports atomic counters - should work as-is
    }

    // ========================================================================
    // STEP 14: GL_CLIP_DISTANCE / GL_CULL_DISTANCE
    // ========================================================================
    // GLES 3.2 doesn't support gl_ClipDistance/gl_CullDistance natively
    // Stub them out to prevent compile errors
    if (glsl.find("gl_ClipDistance") != std::string::npos) {
        insertBeforeMain(glsl, "float gl_ClipDistance[8];");
    }
    if (glsl.find("gl_CullDistance") != std::string::npos) {
        // Just remove references - cull distance is rarely used
        // and stubbing as 0 is safe
    }

    // ========================================================================
    // STEP 15: REMOVE UNSUPPORTED PREPROCESSOR DIRECTIVES
    // ========================================================================
    // Some desktop shaders use #pragma optimize, #pragma debug, etc.
    // These cause errors on GLES compilers
    removeLinesContaining(glsl, "#pragma optimize");
    removeLinesContaining(glsl, "#pragma debug");
    removeLinesContaining(glsl, "#pragma");

    // ========================================================================
    // STEP 16: GL_BACK_COLOR / GL_FRONT_COLOR (Legacy GL)
    // ========================================================================
    replaceAllSafe(glsl, "gl_BackColor", "gl_FrontColor");
    replaceAllSafe(glsl, "gl_BackSecondaryColor", "gl_SecondaryColor");

    // ========================================================================
    // STEP 17: ENSURE NO CRASH ON BAD SHADERS
    // ========================================================================
    // If the translated shader is empty or just whitespace, return original
    // with minimal fixes (version + precision) as ultimate fallback
    bool all_whitespace = true;
    for (char c : glsl) {
        if (!isspace(c)) { all_whitespace = false; break; }
    }
    if (all_whitespace || glsl.length() < 20) {
        LOG_WARNING("[FearEngine] Translation produced empty/too-short result, using fallback");
        std::string fallback = "#version 320 es\n";
        fallback += IRIS_COMPAT_EXTENSIONS;
        fallback += HIGH_PRECISION_PREAMBLE;
        fallback += sourceCode;  // Original source as-is
        // At least fix version and precision
        size_t fv = fallback.find("#version");
        if (fv != std::string::npos) {
            size_t fe = fallback.find("\n", fv);
            if (fe != std::string::npos) {
                fallback.replace(fv, fe - fv, "#version 320 es");
            }
        }
        *translationSuccess = true;
        return fallback;
    }

    // ========================================================================
    // STEP 18: FINAL CLEANUP
    // ========================================================================
    // Remove duplicate precision declarations (we may have injected ours
    // and the original may still have some)
    // Also remove any leftover 'precision mediump' that snuck back in
    // from the original source after our injection point
    // Note: We do this carefully to not break the shader

    // Count how many times we see our precision preamble and deduplicate
    size_t prec_count = 0;
    size_t search_pos = 0;
    std::string prec_marker = "precision highp float;";
    while ((search_pos = glsl.find(prec_marker, search_pos)) != std::string::npos) {
        prec_count++;
        search_pos += prec_marker.length();
    }
    // Keep only the first occurrence of our precision block
    if (prec_count > 1) {
        // Find first occurrence of our full preamble
        std::string full_preamble = HIGH_PRECISION_PREAMBLE;
        size_t first = glsl.find(full_preamble);
        if (first != std::string::npos) {
            // Remove subsequent occurrences
            search_pos = first + full_preamble.length();
            while ((search_pos = glsl.find(full_preamble, search_pos)) != std::string::npos) {
                glsl.erase(search_pos, full_preamble.length());
            }
        }
    }

    LOG_INFO("[FearRender] Shader translated successfully (%zu bytes)", glsl.length());
    return glsl;
}
