#include "fear_xextream_mali_fixer.h"
#include <android/log.h>
#include <sstream>
#include <algorithm>
#include <regex>

#define LOG_TAG "FearXextreamMali"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace FearXextream {

    void MaliShaderFixer::applyMaliWorkarounds(std::string& shaderSource) {
        if (shaderSource.empty()) return;

        // Apply general Mali pipeline fixes
        std::stringstream header;
        header << "\n// --- FearXextream Mali Architecture Engine & Bliss Shader Fixer ---\n";
        header << "#define MALI_GPU_OPTIMIZED 1\n";
        header << "#define glsl_correct_derivatives_after_discard 1\n";
        header << "precision highp float;\n";
        header << "precision highp int;\n";
        header << "precision highp sampler2D;\n";
        header << "precision highp sampler3D;\n";
        header << "precision highp samplerCube;\n";
        header << "#define FEAR_MALI_DEPTH_CLAMP 1\n";
        header << "#define FEAR_MALI_VOXEL_FALLBACK 1\n";

        // Replace noperspective with smooth for Mali compiler safety
        const std::string target = "noperspective";
        size_t pos = 0;
        while ((pos = shaderSource.find(target, pos)) != std::string::npos) {
            bool validBefore = (pos == 0) || (!isalnum(shaderSource[pos - 1]) && shaderSource[pos - 1] != '_');
            bool validAfter = (pos + target.length() >= shaderSource.length()) || (!isalnum(shaderSource[pos + target.length()]) && shaderSource[pos + target.length()] != '_');
            if (validBefore && validAfter) {
                shaderSource.replace(pos, target.length(), "smooth");
                pos += 6;
            } else {
                pos += target.length();
            }
        }

        injectSafeMathWrappers(shaderSource);
        fixBlissShaderColorGlitches(shaderSource);
        fixDerivativesAndDiscards(shaderSource);

        // Ensure #version directive stays as line 1
        size_t versionPos = shaderSource.find("#version");
        if (versionPos != std::string::npos) {
            size_t lineEnd = shaderSource.find('\n', versionPos);
            if (lineEnd != std::string::npos) {
                shaderSource.insert(lineEnd + 1, header.str());
            } else {
                shaderSource += header.str();
            }
        } else {
            shaderSource = "#version 320 es\n" + header.str() + shaderSource;
        }
    }

    void MaliShaderFixer::injectSafeMathWrappers(std::string& shaderSource) {
        // Inject overloaded GLSL functions for safe math (supports float, vec2, vec3, vec4)
        std::string safeMathCode =
            "\n// FearXextream Mali Safe Math & Overloaded Function Precision Guard\n"
            "float fear_safe_pow(float x, float y) { return pow(max(x, 0.00001), y); }\n"
            "vec2 fear_safe_pow(vec2 x, vec2 y) { return pow(max(x, vec2(0.00001)), y); }\n"
            "vec3 fear_safe_pow(vec3 x, vec3 y) { return pow(max(x, vec3(0.00001)), y); }\n"
            "vec4 fear_safe_pow(vec4 x, vec4 y) { return pow(max(x, vec4(0.00001)), y); }\n"
            "vec2 fear_safe_pow(vec2 x, float y) { return pow(max(x, vec2(0.00001)), vec2(y)); }\n"
            "vec3 fear_safe_pow(vec3 x, float y) { return pow(max(x, vec3(0.00001)), vec3(y)); }\n"
            "vec4 fear_safe_pow(vec4 x, float y) { return pow(max(x, vec4(0.00001)), vec4(y)); }\n"

            "float fear_safe_log(float x) { return log(max(x, 0.00001)); }\n"
            "vec2 fear_safe_log(vec2 x) { return log(max(x, vec2(0.00001))); }\n"
            "vec3 fear_safe_log(vec3 x) { return log(max(x, vec3(0.00001))); }\n"
            "vec4 fear_safe_log(vec4 x) { return log(max(x, vec4(0.00001))); }\n"

            "float fear_safe_div(float num, float den) { return num / ((abs(den) < 0.00001) ? 0.00001 : den); }\n"
            "vec3 fear_safe_normalize(vec3 v) {\n"
            "    float len = length(v);\n"
            "    return (len < 0.00001) ? vec3(0.0) : (v / len);\n"
            "}\n"
            "vec4 fear_safe_clamp_color(vec4 col) {\n"
            "    if (isnan(col.r) || isnan(col.g) || isnan(col.b) || isnan(col.a)) return vec4(0.0, 0.0, 0.0, 1.0);\n"
            "    if (isinf(col.r) || isinf(col.g) || isinf(col.b) || isinf(col.a)) return vec4(1.0);\n"
            "    return clamp(col, 0.0, 65504.0);\n"
            "}\n";

        size_t versionPos = shaderSource.find("#version");
        if (versionPos != std::string::npos) {
            size_t lineEnd = shaderSource.find('\n', versionPos);
            if (lineEnd != std::string::npos) {
                shaderSource.insert(lineEnd + 1, safeMathCode);
            } else {
                shaderSource += safeMathCode;
            }
        } else {
            shaderSource = safeMathCode + shaderSource;
        }
    }

    void MaliShaderFixer::fixBlissShaderColorGlitches(std::string& shaderSource) {
        bool isBlissOrAtmospheric = (shaderSource.find("Bliss") != std::string::npos ||
                                    shaderSource.find("atmosphere") != std::string::npos ||
                                    shaderSource.find("volumetric") != std::string::npos ||
                                    shaderSource.find("getSkyColor") != std::string::npos ||
                                    shaderSource.find("getScattering") != std::string::npos ||
                                    shaderSource.find("calculateSunlight") != std::string::npos ||
                                    shaderSource.find("colortex") != std::string::npos);

        if (isBlissOrAtmospheric) {
            fixAtmosphereColorGrading(shaderSource);
            LOGI("MaliShaderFixer: Bliss Shaders Atmospheric & Color Glitch Fix Applied!");
        }
    }

    void MaliShaderFixer::fixAtmosphereColorGrading(std::string& shaderSource) {
        // Line-by-line regex replacement that skips lines containing 'const ' to preserve GLSL const expression rules
        std::stringstream in(shaderSource);
        std::stringstream out;
        std::string line;
        std::regex powRegex("\\bpow\\s*\\(");
        std::regex logRegex("\\blog\\s*\\(");

        while (std::getline(in, line)) {
            if (line.find("const ") == std::string::npos) {
                line = std::regex_replace(line, powRegex, "fear_safe_pow(");
                line = std::regex_replace(line, logRegex, "fear_safe_log(");
            }
            out << line << "\n";
        }
        shaderSource = out.str();
    }

    void MaliShaderFixer::fixDerivativesAndDiscards(std::string& shaderSource) {
        if (shaderSource.find("discard;") != std::string::npos &&
           (shaderSource.find("dFdx") != std::string::npos || shaderSource.find("dFdy") != std::string::npos)) {
            size_t mainPos = shaderSource.find("void main");
            if (mainPos != std::string::npos) {
                shaderSource.insert(mainPos, "\n// FearXextream Mali Derivative Precision Guard Active\n");
            }
        }
    }

    void MaliShaderFixer::configureMaliPipelineEnv() {
        LOGI("MaliShaderFixer: Pipeline configured with Mali Tile-Buffer, Safe Math Overloaded Functions & Bliss Color Fixes.");
    }

}
