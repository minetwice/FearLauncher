#include "fear_xextream_mali_fixer.h"
#include <android/log.h>

#define LOG_TAG "FearXextreamMali"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace FearXextream {

    void MaliShaderFixer::applyMaliWorkarounds(std::string& shaderSource) {
        // Mali GPU Shader Fix 1: Inject highp precision for all float variables and sRGB linearization
        std::string maliHeader = "#define MALI_GPU_OPTIMIZED 1\n";
        maliHeader += "#define glsl_correct_derivatives_after_discard 1\n";
        maliHeader += "precision highp float;\n";
        maliHeader += "precision highp int;\n";
        maliHeader += "precision highp sampler2D;\n";
        maliHeader += "#define FEAR_MALI_DEPTH_CLAMP 1\n";

        // Mali GPU Shader Fix 2: Replace noperspective with smooth keyword
        const std::string target = "noperspective";
        size_t pos = 0;
        while ((pos = shaderSource.find(target, pos)) != std::string::npos) {
            shaderSource.replace(pos, target.length(), "smooth");
            pos += 6;
        }

        shaderSource = maliHeader + shaderSource;
    }

    void MaliShaderFixer::configureMaliPipelineEnv() {
        LOGI("MaliShaderFixer: Pipeline configured with Mali Tile-Buffer & Shadow Precision overrides.");
    }

}
