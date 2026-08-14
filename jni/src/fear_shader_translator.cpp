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
        } else if (version_num == "400" || version_num == "410" || version_num == "420" ||
                   version_num == "430" || version_num == "440" || version_num == "450" ||
                   version_num == "460") {
            target_version = "#version 320 es";
        } else {
            target_version = "#version 320 es";
        }
        glsl.replace(version_pos, version_line_end - version_pos, target_version);
    } else {
        glsl = "#version 300 es\n" + glsl;
        target_version = "#version 300 es";
    }

    // STEP 2.2 - PRECISION QUALIFIER INJECTION:
    bool has_precision = (glsl.find("precision") != std::string::npos);
    if (!has_precision) {
        std::string inject_text = "precision highp float;\nprecision highp int;";
        if (isFragmentShader(shaderType)) {
            inject_text += "\nprecision mediump sampler2D;";
        }
        insertAfterLine(glsl, target_version, inject_text);
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
        LOG_WARNING("[FearEngine] Noise/Sampler1D function or sampler replaced with constant/sampler2D");
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

    // STEP 2.12 - NOISE FUNCTION REMOVAL:
    if (glsl.find("noise1(") != std::string::npos || glsl.find("noise2(") != std::string::npos ||
        glsl.find("noise3(") != std::string::npos || glsl.find("noise4(") != std::string::npos) {
        replaceAll(glsl, "noise1(", "0.0 //");
        replaceAll(glsl, "noise2(", "vec2(0.0) //");
        replaceAll(glsl, "noise3(", "vec3(0.0) //");
        replaceAll(glsl, "noise4(", "vec4(0.0) //");
        LOG_WARNING("[FearEngine] Noise function replaced with constant");
    }

    return glsl;
}
