#ifndef FEAR_XEXTREAM_SHADER_TRANSPILER_H
#define FEAR_XEXTREAM_SHADER_TRANSPILER_H

#include <string>
#include <vector>
#include <unordered_map>
#include <cstdint>

namespace FearXextream {

    enum class GPUArchitecture {
        ARM_MALI,
        QUALCOMM_ADRENO,
        GENERIC_MOBILE
    };

    struct TranspilerOptions {
        GPUArchitecture gpuArch = GPUArchitecture::GENERIC_MOBILE;
        bool forceHighPrecision = true;
        bool enableFastMath = true;
        bool stripUnsupportedExtensions = true;
        bool convertMRT = true;
        int targetGLESVersion = 320; // OpenGL ES 3.2
    };

    class ShaderTranspiler {
    public:
        explicit ShaderTranspiler(const TranspilerOptions& options);

        // High-performance GLSL Desktop to GLES / SPIR-V Transpilation
        std::string transpileGLSL(const std::string& desktopGLSL, uint32_t shaderType);

        // Optimize generated shader for Mali/Adreno pipelines
        void injectHardwareOptimizations(std::string& shaderCode);

    private:
        TranspilerOptions m_options;

        void processMRTOutputs(std::string& code);
        void stripDesktopExtensions(std::string& code);
        void sanitizeInterpolators(std::string& code);
    };

}

#endif // FEAR_XEXTREAM_SHADER_TRANSPILER_H
