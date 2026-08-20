#include "fear_shader_translator.h"
#include "fear_shader_logger.h"
#include <shaderc/shaderc.hpp>
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

    if (isGeometryShader(shaderType)) {
        *translationSuccess = false;
        LOG_WARNING("[FearEngine] WARNING: Geometry shader detected - not supported on mobile, skipping");
        return "";
    }

    std::string glsl(sourceCode);

    bool isCompute = isComputeShader(shaderType) ||
                     glsl.find("layout(local_size_") != std::string::npos ||
                     glsl.find("buffer") != std::string::npos ||
                     glsl.find("layout(std430") != std::string::npos;

    // Clean up Desktop ARB extensions that cause GLES compiler syntax errors
    removeLinesContaining(glsl, "#extension GL_ARB_");
    removeLinesContaining(glsl, "#extension GL_EXT_gpu_shader4");

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

    std::string target_version = "#version 310 es";
    if (has_version) {
        if (version_num == "100" || version_num == "110" || version_num == "120" ||
            version_num == "130" || version_num == "140" || version_num == "150" ||
            version_num == "330") {
            target_version = isCompute ? "#version 310 es" : "#version 300 es";
        } else {
            target_version = "#version 320 es";
        }
        glsl.replace(version_pos, version_line_end - version_pos, target_version);
    } else {
        target_version = isCompute ? "#version 310 es" : "#version 300 es";
        glsl = target_version + "\n" + glsl;
    }

    // SECTION B10: MOBILE DEFINES & EXTENSIONS
    std::string mobile_defines = "\n#define MC_ANDROID\n#define FEAR_MOBILE\n#define FEAR_MAX_SHADOWS 2\n#define FEAR_MAX_LIGHTS 4\n#define FEAR_SHADOW_MAP_RES 1024\n#define FEAR_RENDER_ENGINE_4_6\n";
    if (glsl.find("dFdx") != std::string::npos || glsl.find("dFdy") != std::string::npos || glsl.find("fwidth") != std::string::npos) {
        mobile_defines += "#extension GL_OES_standard_derivatives : enable\n";
    }
    if (glsl.find("gl_FragDepth") != std::string::npos || glsl.find("gl_FragDepthEXT") != std::string::npos) {
        mobile_defines += "#extension GL_EXT_frag_depth : enable\n";
    }
    insertAfterLine(glsl, target_version, mobile_defines);

    // STEP 2.2 - PRECISION QUALIFIER INJECTION FOR MALI/ADRENO COLOR STABILITY:
    std::string inject_precision = "precision highp float;\nprecision highp int;\n"
                                   "precision highp sampler2D;\nprecision highp sampler2DArray;\n"
                                   "precision highp sampler3D;\nprecision highp samplerCube;\n"
                                   "precision highp sampler2DShadow;\nprecision highp sampler2DArrayShadow;\n";
    if (isCompute) {
        inject_precision += "precision highp image2D;\nprecision highp uimage2D;\nprecision highp iimage2D;\n";
    }

    if (glsl.find("precision ") == std::string::npos) {
        insertAfterLine(glsl, target_version, inject_precision);
    } else {
        // Upgrade mediump float to highp float for color and lighting calculations on Mali/Adreno GPUs
        replaceAll(glsl, "precision mediump float;", "precision highp float;");
        replaceAll(glsl, "precision lowp float;", "precision highp float;");
    }

    // FIX 1: COMPUTE SHADER FIXES
    if (isCompute) {
        bool fixed = false;
        if (glsl.find("uint i = ivec2(gl_FragCoord.xy).x;") != std::string::npos) {
            replaceAll(glsl, "uint i = ivec2(gl_FragCoord.xy).x;", "uint i = gl_GlobalInvocationID.x;");
            fixed = true;
        }
        if (glsl.find("gl_FragCoord.xy") != std::string::npos) {
            replaceAll(glsl, "gl_FragCoord.xy", "vec2(gl_GlobalInvocationID.xy)");
            fixed = true;
        }
        if (glsl.find("gl_FragCoord") != std::string::npos) {
            replaceAll(glsl, "gl_FragCoord", "vec4(gl_GlobalInvocationID.xy, 0.0, 1.0)");
            fixed = true;
        }

        if (glsl.find("layout(local_size_") == std::string::npos) {
            std::string layout_qualifier = "\n#ifndef QUASAR_COMPUTE_LAYOUT\n#define QUASAR_COMPUTE_LAYOUT\nlayout(local_size_x = 1, local_size_y = 1, local_size_z = 1) in;\n#endif\n";
            insertBeforeMain(glsl, layout_qualifier);
            fixed = true;
        }

        if (fixed) {
            LOG_INFO("[FearRender] Compute shader fixed: gl_FragCoord -> gl_GlobalInvocationID");
        }
    }

    // SECTION B1: DERIVATIVES
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

    // STEP 2.4 - VERTEX SHADER SPECIFIC RULES:
    if (isVertexShader(shaderType)) {
        replaceAll(glsl, "attribute ", "in ");
        replaceAll(glsl, "varying ", "out ");
    }

    // STEP 2.5 - FRAGMENT SHADER SPECIFIC RULES (MRT & Output translation):
    if (isFragmentShader(shaderType)) {
        replaceAll(glsl, "varying ", "in ");
        replaceAll(glsl, "noperspective in ", "in ");
        replaceAll(glsl, "noperspective out ", "out ");
        replaceAll(glsl, "flat varying ", "flat in ");

        // Frag Depth
        replaceAll(glsl, "gl_FragDepthEXT", "gl_FragDepth");

        // Multiple Render Targets (gl_FragData[0..7]) translation for Solas & Complementary shaders
        bool uses_fragdata = false;
        for (int i = 0; i < 8; i++) {
            std::string fragDataName = "gl_FragData[" + std::to_string(i) + "]";
            if (glsl.find(fragDataName) != std::string::npos) {
                uses_fragdata = true;
                std::string targetOutName = "fear_FragData" + std::to_string(i);
                std::string decl = "layout(location = " + std::to_string(i) + ") out vec4 " + targetOutName + ";";
                if (glsl.find(targetOutName) == std::string::npos) {
                    insertAfterLine(glsl, target_version, decl);
                }
                replaceAll(glsl, fragDataName, targetOutName);
            }
        }

        if (!uses_fragdata && glsl.find("gl_FragColor") != std::string::npos) {
            if (glsl.find("out vec4 FragColor;") == std::string::npos && glsl.find("fear_FragData0") == std::string::npos) {
                insertAfterLine(glsl, target_version, "layout(location = 0) out vec4 FragColor;");
            }
            replaceAll(glsl, "gl_FragColor", "FragColor");
        }
    }

    return glsl;
}

// Module 1 Implementation: Runtime GLSL-to-SPIRV Cross-Compiler Pipeline using Shaderc
std::vector<uint32_t> FearCompileGLSLToSPIRV(
    const char* sourceCode,
    GLenum shaderType,
    const char* shaderName,
    bool* compileSuccess
) {
    if (!sourceCode) {
        if (compileSuccess) *compileSuccess = false;
        return {};
    }

    bool transSuccess = false;
    std::string sanitizedGLSL = FearTranslateGLSL(sourceCode, shaderType, &transSuccess);
    if (!transSuccess || sanitizedGLSL.empty()) {
        if (compileSuccess) *compileSuccess = false;
        return {};
    }

    // Convert GLES version headers to Vulkan GLSL (#version 450)
    if (sanitizedGLSL.find("#version 300 es") != std::string::npos) {
        replaceAll(sanitizedGLSL, "#version 300 es", "#version 450");
    } else if (sanitizedGLSL.find("#version 310 es") != std::string::npos) {
        replaceAll(sanitizedGLSL, "#version 310 es", "#version 450");
    } else if (sanitizedGLSL.find("#version 320 es") != std::string::npos) {
        replaceAll(sanitizedGLSL, "#version 320 es", "#version 450");
    }

    // Remove GLES-specific extension directives and precision statements incompatible with Vulkan GLSL (#version 450)
    removeLinesContaining(sanitizedGLSL, "#extension GL_OES_");
    removeLinesContaining(sanitizedGLSL, "#extension GL_EXT_frag_depth");
    removeLinesContaining(sanitizedGLSL, "precision ");

    shaderc::Compiler compiler;
    shaderc::CompileOptions options;

    options.SetTargetEnvironment(shaderc_target_env_vulkan, shaderc_env_version_vulkan_1_2);
    options.SetOptimizationLevel(shaderc_optimization_level_performance);

    shaderc_shader_kind kind = shaderc_glsl_fragment_shader;
    if (shaderType == GL_VERTEX_SHADER) {
        kind = shaderc_glsl_vertex_shader;
    } else if (shaderType == GL_COMPUTE_SHADER) {
        kind = shaderc_glsl_compute_shader;
    }

    std::string nameStr = shaderName ? shaderName : "minecraft_glsl_shader";
    shaderc::SpvCompilationResult module = compiler.CompileGlslToSpv(sanitizedGLSL, kind, nameStr.c_str(), options);

    if (module.GetCompilationStatus() != shaderc_compilation_status_success) {
        LOG_WARNING("[FearRender SPIRV Compiler Warning] %s - Falling back to GLES Translation Pipeline", module.GetErrorMessage().c_str());
        if (compileSuccess) *compileSuccess = false;
        return {};
    }

    if (compileSuccess) *compileSuccess = true;
    return {module.cbegin(), module.cend()};
}
