#ifndef FEAR_XEXTREAM_SHADER_TRANSPILER_H
#define FEAR_XEXTREAM_SHADER_TRANSPILER_H

#include "fear_xextream_core.h"
#include <string>
#include <vector>

namespace FearXextream {

    struct TranspilerOptions {
        GPUArchitecture gpuArch = GPUArchitecture::ARM_MALI;
        int targetGLESVersion = 320;
        bool forceHighPrecision = true;
        bool convertMRT = true;
        bool stripUnsupportedExtensions = true;
        bool enableBlissFix = true;
        bool enableComputeFixes = true;
        bool enableACESTonemap = true;
    };

    class ShaderTranspiler {
    public:
        explicit ShaderTranspiler(const TranspilerOptions& options = TranspilerOptions());
        std::string transpileGLSL(const std::string& desktopGLSL, uint32_t shaderType);

    private:
        TranspilerOptions m_options;

        void injectDesktopEmulationMacros(std::stringstream& output, uint32_t shaderType);
        void processMRTOutputs(std::string& code);
        void convertGBufferBindings(std::string& code);
        void convertSSBOLayouts(std::string& code);
        void stripDesktopExtensions(std::string& code);
        void sanitizeInterpolators(std::string& code);
        void convertTextureSamplers(std::string& code);
        void processComputeShaders(std::string& code, uint32_t shaderType);
        void injectDepthClampEmulation(std::string& code, uint32_t shaderType);
        void injectHardwareOptimizations(std::string& code);
    };

}

#endif // FEAR_XEXTREAM_SHADER_TRANSPILER_H
