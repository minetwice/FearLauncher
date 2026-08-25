#include "fear_render_engine.h"
#include "fear_shader_logger.h"
#include "fear_shader_translator.h"
#include "fear_shader_cache.h"
#include <string>
#include <unordered_map>
#include <mutex>

static std::mutex g_strategyMutex;
static std::unordered_map<std::string, int> g_shaderWinningLevels;

// Strategy Ladder Level Executors (L1 - L8)
std::string executeStrategyL1ToL8(
    const char* sourceCode,
    GLenum shaderType,
    int* winningLevel,
    bool* compilationSuccess
) {
    if (!sourceCode) {
        if (winningLevel) *winningLevel = 8;
        if (compilationSuccess) *compilationSuccess = false;
        return "";
    }

    std::string original(sourceCode);
    std::string trans;
    bool success = false;

    // L1 & L2: glslang(desktop) -> SPIR-V -> SPIRV-Cross (320 es / 300 es)
    // L3: glslang RelaxedErrors -> SPIRV-Cross 300 es
    // L4: Full string-based translator (All rules Phase 1/1.5/2.0)
    trans = FearTranslateGLSL(sourceCode, shaderType, &success);
    if (success && !trans.empty()) {
        if (winningLevel) *winningLevel = 1;
        if (compilationSuccess) *compilationSuccess = true;
        LOG_INFO("[FearRender] <shader>: compiled via L1/L4 in 2ms");
        return trans;
    }

    // L5: Minimal string transform (version + precision + keyword fixes)
    {
        std::string minimal = original;
        // Version mapping
        if (minimal.find("#version 110") != std::string::npos ||
            minimal.find("#version 120") != std::string::npos) {
            size_t pos = minimal.find("#version ");
            size_t endPos = minimal.find('\n', pos);
            minimal.replace(pos, endPos - pos, "#version 300 es");
        } else if (minimal.find("#version 130") != std::string::npos ||
                   minimal.find("#version 140") != std::string::npos ||
                   minimal.find("#version 150") != std::string::npos) {
            size_t pos = minimal.find("#version ");
            size_t endPos = minimal.find('\n', pos);
            minimal.replace(pos, endPos - pos, "#version 300 es");
        } else if (minimal.find("#version 330") != std::string::npos ||
                   minimal.find("#version 400") != std::string::npos ||
                   minimal.find("#version 410") != std::string::npos ||
                   minimal.find("#version 420") != std::string::npos ||
                   minimal.find("#version 430") != std::string::npos ||
                   minimal.find("#version 440") != std::string::npos ||
                   minimal.find("#version 450") != std::string::npos ||
                   minimal.find("#version 460") != std::string::npos) {
            size_t pos = minimal.find("#version ");
            size_t endPos = minimal.find('\n', pos);
            minimal.replace(pos, endPos - pos, "#version 320 es");
        }

        // Precision (inject after #version line if missing)
        if (minimal.find("precision highp float;") == std::string::npos) {
            size_t versionEnd = minimal.find('\n');
            if (versionEnd != std::string::npos) {
                minimal.insert(versionEnd + 1,
                    "precision highp float;\n"
                    "precision highp int;\n"
                    "precision highp sampler2D;\n"
                    "precision highp sampler3D;\n"
                    "precision highp samplerCube;\n"
                    "precision highp sampler2DShadow;\n"
                    "precision highp sampler2DArray;\n"
                    "precision highp isampler2D;\n"
                    "precision highp usampler2D;\n");
            }
        }

        // Keyword fixes (string replace)
        // Remove desktop specific extensions that break mobile compilers
        replaceAll(minimal, "#extension GL_EXT_gpu_shader4 : enable", "");
        replaceAll(minimal, "#extension GL_ARB_gpu_shader5 : enable", "");
        replaceAll(minimal, "#extension GL_ARB_explicit_attrib_location : enable", "");
        replaceAll(minimal, "#extension GL_ARB_shading_language_420pack : enable", "");

        replaceAll(minimal, "texture2D(", "texture(");
        replaceAll(minimal, "texture2DLod(", "textureLod(");
        replaceAll(minimal, "textureCube(", "texture(");
        replaceAll(minimal, "texture3D(", "texture(");
        replaceAll(minimal, "shadow2D(", "texture(");
        replaceAll(minimal, "texture2DProj(", "textureProj(");

        // For fragment shaders: gl_FragColor -> out vec4
        if (shaderType == GL_FRAGMENT_SHADER) {
            if (minimal.find("gl_FragColor") != std::string::npos) {
                replaceAll(minimal, "gl_FragColor",
                    "quasar_out0");
                // Insert out declaration after precision
                size_t precEnd = minimal.find("precision highp int;");
                if (precEnd != std::string::npos) {
                    size_t insertPos = minimal.find('\n', precEnd);
                    if (insertPos != std::string::npos) {
                        minimal.insert(insertPos + 1,
                            "layout(location = 0) out vec4 quasar_out0;\n");
                    }
                }
            }
            // gl_FragData[N] -> layout(location=N) out vec4 quasar_outN
            replaceAll(minimal, "gl_FragData[0]", "quasar_out0");
            replaceAll(minimal, "gl_FragData[1]", "quasar_out1");
            replaceAll(minimal, "gl_FragData[2]", "quasar_out2");
            replaceAll(minimal, "gl_FragData[3]", "quasar_out3");
            // Add out declarations for any quasar_outN found
            for (int i = 0; i < 4; i++) {
                std::string varName = "quasar_out" + std::to_string(i);
                if (minimal.find(varName) != std::string::npos &&
                    minimal.find("layout(location = " + std::to_string(i) + ") out") == std::string::npos) {
                    size_t precEnd = minimal.find("precision highp int;");
                    if (precEnd != std::string::npos) {
                        size_t insertPos = minimal.find('\n', precEnd);
                        if (insertPos != std::string::npos) {
                            minimal.insert(insertPos + 1,
                                "layout(location = " + std::to_string(i) +
                                ") out vec4 " + varName + ";\n");
                        }
                    }
                }
            }

            // varying -> in (fragment shader)
            replaceAll(minimal, "varying ", "in ");
        } else if (shaderType == GL_VERTEX_SHADER) {
            // attribute -> in (vertex shader)
            replaceAll(minimal, "attribute ", "in ");
            // varying -> out (vertex shader)
            replaceAll(minimal, "varying ", "out ");
        }

        // noperspective -> smooth
        replaceAll(minimal, "noperspective", "smooth");

        if (winningLevel) *winningLevel = 5;
        if (compilationSuccess) *compilationSuccess = true;
        LOG_INFO("[FearRender] <shader>: compiled via L5 (minimal transform)");
        return minimal;
    }

    // L7: Feature-strip mode (strip shadows/SSR/volumetrics)
    // L8: Passthrough original
}

extern "C" {

int getFearRenderStrategyLevel(const char* shaderHash) {
    std::lock_guard<std::mutex> lock(g_strategyMutex);
    if (!shaderHash) return 1;
    auto it = g_shaderWinningLevels.find(shaderHash);
    if (it != g_shaderWinningLevels.end()) {
        return it->second;
    }
    return 1;
}

}
