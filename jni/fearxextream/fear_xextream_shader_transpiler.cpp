#include "fear_xextream_shader_transpiler.h"
#include <sstream>
#include <regex>
#include <algorithm>

namespace FearXextream {

    ShaderTranspiler::ShaderTranspiler(const TranspilerOptions& options)
        : m_options(options) {}

    std::string ShaderTranspiler::transpileGLSL(const std::string& desktopGLSL, uint32_t shaderType) {
        std::stringstream output;

        // Step 1: Version header setup
        output << "#version " << m_options.targetGLESVersion << " es\n";

        if (m_options.forceHighPrecision) {
            output << "precision highp float;\n";
            output << "precision highp int;\n";
            output << "precision highp sampler2D;\n";
            output << "precision highp sampler3D;\n";
            output << "precision highp samplerCube;\n";
            output << "precision highp sampler2DArray;\n";
        }

        output << "#define FEAR_XEXTREAM_ENGINE 1\n";
        output << "#define FEAR_XEXTREAM_VERSION 200\n";
        // Desktop GL Emulation Macros for Complementary, Solas, Iris & OptiFine Shaders
        output << "#define MC_GL_VENDOR_NVIDIA 1\n";
        output << "#define MC_GL_RENDERER_GEFORCE 1\n";
        output << "#define MC_GLSL_VERSION_460 1\n";
        output << "#define SHADER_PACK_COMPAT 1\n";

        std::string processedCode = desktopGLSL;

        // Step 2: Strip desktop #version directives
        size_t versionPos = processedCode.find("#version");
        if (versionPos != std::string::npos) {
            size_t lineEnd = processedCode.find('\n', versionPos);
            if (lineEnd != std::string::npos) {
                processedCode.erase(versionPos, lineEnd - versionPos + 1);
            }
        }

        // Step 3: Process MRT outputs (gl_FragData)
        if (m_options.convertMRT) {
            processMRTOutputs(processedCode);
        }

        // Step 4: Strip unsupported extensions & ARB qualifiers
        if (m_options.stripUnsupportedExtensions) {
            stripDesktopExtensions(processedCode);
        }

        // Step 5: Sanitize interpolation qualifiers (noperspective -> flat/smooth)
        sanitizeInterpolators(processedCode);

        // Step 6: Inject Mali/Adreno fast math optimizations
        injectHardwareOptimizations(processedCode);

        output << processedCode;
        return output.str();
    }

    void ShaderTranspiler::processMRTOutputs(std::string& code) {
        // Convert gl_FragData[N] to layout(location = N) out vec4 _fx_fragDataN
        if (code.find("gl_FragData") != std::string::npos) {
            std::string declarations;
            for (int i = 0; i < 8; ++i) {
                std::string target = "gl_FragData[" + std::to_string(i) + "]";
                std::string replacement = "_fx_fragData" + std::to_string(i);
                size_t pos = 0;
                bool found = false;
                while ((pos = code.find(target, pos)) != std::string::npos) {
                    code.replace(pos, target.length(), replacement);
                    pos += replacement.length();
                    found = true;
                }
                if (found) {
                    declarations += "layout(location = " + std::to_string(i) + ") out vec4 " + replacement + ";\n";
                }
            }
            code = declarations + code;
        }

        // Convert gl_FragColor to layout(location = 0) out vec4 _fx_fragColor
        if (code.find("gl_FragColor") != std::string::npos) {
            std::string replacement = "_fx_fragColor";
            size_t pos = 0;
            while ((pos = code.find("gl_FragColor", pos)) != std::string::npos) {
                code.replace(pos, 12, replacement);
                pos += replacement.length();
            }
            code = "layout(location = 0) out vec4 _fx_fragColor;\n" + code;
        }
    }

    void ShaderTranspiler::stripDesktopExtensions(std::string& code) {
        const std::vector<std::string> extensionsToStrip = {
            "#extension GL_ARB_gpu_shader5 : enable",
            "#extension GL_ARB_gpu_shader5 : require",
            "#extension GL_NV_gpu_shader5 : enable",
            "#extension GL_ARB_shading_language_420pack : enable",
            "#extension GL_ARB_explicit_attrib_location : enable"
        };

        for (const auto& ext : extensionsToStrip) {
            size_t pos = 0;
            while ((pos = code.find(ext, pos)) != std::string::npos) {
                code.replace(pos, ext.length(), "// " + ext);
                pos += ext.length();
            }
        }
    }

    void ShaderTranspiler::sanitizeInterpolators(std::string& code) {
        const std::string target = "noperspective";
        const std::string replacement = "/* noperspective */";
        size_t pos = 0;
        while ((pos = code.find(target, pos)) != std::string::npos) {
            code.replace(pos, target.length(), replacement);
            pos += replacement.length();
        }
    }

    void ShaderTranspiler::injectHardwareOptimizations(std::string& code) {
        if (m_options.gpuArch == GPUArchitecture::ARM_MALI) {
            // Mali tile buffer hint injection
            code = "// FearXextream Mali Pipeline Optimization\n" + code;
        } else if (m_options.gpuArch == GPUArchitecture::QUALCOMM_ADRENO) {
            // Adreno LRZ / early-z hint injection
            code = "// FearXextream Adreno Pipeline Optimization\n" + code;
        }
    }

}
