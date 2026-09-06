#include "fear_xextream_dither_engine.h"
#include <android/log.h>

#define LOG_TAG "FearXextreamDither"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace FearXextream {

    DitherEngine& DitherEngine::getInstance() {
        static DitherEngine instance;
        return instance;
    }

    void DitherEngine::injectAntiFlickerLayer(std::string& shaderSource, GLenum shaderType) {
        if (shaderType == 0x8B30 /* GL_FRAGMENT_SHADER */) {
            std::string antiFlickerCode =
                "\n// FearXextream Anti-Flicker & Temporal Dithering Engine\n"
                "#define FEAR_ANTI_FLICKER 1\n"
                "vec3 applyTemporalDither(vec3 color, vec2 uv) {\n"
                "    float noise = fract(sin(dot(uv, vec2(12.9898, 78.233))) * 43758.5453);\n"
                "    return color + (noise - 0.5) * (1.0 / 255.0);\n"
                "}\n";

            injectColorEnhancerLayer(shaderSource, shaderType);

            // Insert definitions after #version directive line
            size_t versionPos = shaderSource.find("#version");
            if (versionPos != std::string::npos) {
                size_t lineEnd = shaderSource.find('\n', versionPos);
                if (lineEnd != std::string::npos) {
                    shaderSource.insert(lineEnd + 1, antiFlickerCode);
                } else {
                    shaderSource += antiFlickerCode;
                }
            } else {
                shaderSource = antiFlickerCode + shaderSource;
            }

            // Locate main() and track brace depth to insert hook calls before main()'s closing brace
            size_t mainPos = shaderSource.find("void main");
            if (mainPos == std::string::npos) {
                mainPos = shaderSource.find("main()");
            }

            if (mainPos != std::string::npos) {
                size_t openBrace = shaderSource.find('{', mainPos);
                if (openBrace != std::string::npos) {
                    int braceDepth = 1;
                    size_t mainEndPos = std::string::npos;
                    for (size_t i = openBrace + 1; i < shaderSource.length(); ++i) {
                        if (shaderSource[i] == '{') {
                            braceDepth++;
                        } else if (shaderSource[i] == '}') {
                            braceDepth--;
                            if (braceDepth == 0) {
                                mainEndPos = i;
                                break;
                            }
                        }
                    }

                    if (mainEndPos != std::string::npos) {
                        std::string hookCalls = "";
                        if (shaderSource.find("fear_FragColor") != std::string::npos) {
                            hookCalls = "\n    fear_FragColor.rgb = enhanceMinecraftColors(fear_FragColor.rgb);\n"
                                        "    fear_FragColor.rgb = applyTemporalDither(fear_FragColor.rgb, gl_FragCoord.xy);\n";
                        } else if (shaderSource.find("fear_FragData0") != std::string::npos) {
                            hookCalls = "\n    fear_FragData0.rgb = enhanceMinecraftColors(fear_FragData0.rgb);\n"
                                        "    fear_FragData0.rgb = applyTemporalDither(fear_FragData0.rgb, gl_FragCoord.xy);\n";
                        } else if (shaderSource.find("FragColor") != std::string::npos) {
                            hookCalls = "\n    FragColor.rgb = enhanceMinecraftColors(FragColor.rgb);\n"
                                        "    FragColor.rgb = applyTemporalDither(FragColor.rgb, gl_FragCoord.xy);\n";
                        }

                        if (!hookCalls.empty()) {
                            shaderSource.insert(mainEndPos, hookCalls);
                        }
                    }
                }
            }
        }
    }

    void DitherEngine::injectColorEnhancerLayer(std::string& shaderSource, GLenum shaderType) {
        if (shaderType == 0x8B30 /* GL_FRAGMENT_SHADER */) {
            std::string colorEnhanceCode =
                "\n// FearXextream Color Saturation, Vibrance & Better Contrast Enhancer\n"
                "#define FEAR_COLOR_ENHANCER 1\n"
                "vec3 enhanceMinecraftColors(vec3 color) {\n"
                "    // Saturation Booster (1.25x boost for vivid block textures)\n"
                "    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));\n"
                "    vec3 saturated = mix(vec3(luma), color, 1.25);\n"
                "    // Contrast Enhancer Pass\n"
                "    vec3 contrasted = (saturated - 0.5) * 1.08 + 0.5;\n"
                "    return clamp(contrasted, 0.0, 1.0);\n"
                "}\n";

            size_t versionPos = shaderSource.find("#version");
            if (versionPos != std::string::npos) {
                size_t lineEnd = shaderSource.find('\n', versionPos);
                if (lineEnd != std::string::npos) {
                    shaderSource.insert(lineEnd + 1, colorEnhanceCode);
                } else {
                    shaderSource += colorEnhanceCode;
                }
            } else {
                shaderSource = colorEnhanceCode + shaderSource;
            }
        }
    }

    void DitherEngine::configureColorPrecision() {
        LOGI("DitherEngine: Temporal anti-flicker, color saturation booster and contrast enhancer configured.");
    }

}
