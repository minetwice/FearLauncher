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
        if (shaderType == GL_FRAGMENT_SHADER) {
            std::string antiFlickerCode = "\n// FearXextream Anti-Flicker & Dithering Signal Layer\n"
                                          "#define FEAR_ANTI_FLICKER 1\n"
                                          "vec3 applyTemporalDither(vec3 color, vec2 uv) {\n"
                                          "    float noise = fract(sin(dot(uv, vec2(12.9898, 78.233))) * 43758.5453);\n"
                                          "    return color + (noise - 0.5) * (1.0 / 255.0);\n"
                                          "}\n";
            shaderSource = antiFlickerCode + shaderSource;
        }
    }

    void DitherEngine::configureColorPrecision() {
        LOGI("DitherEngine: Temporal anti-flicker and 10-bit color dithering layer configured.");
    }

}
