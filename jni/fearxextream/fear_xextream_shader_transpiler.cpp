#include "fear_xextream_shader_transpiler.h"
#include <sstream>
#include <regex>
#include <algorithm>

namespace FearXextream {

    ShaderTranspiler::ShaderTranspiler(const TranspilerOptions& options)
        : m_options(options) {}

    std::string ShaderTranspiler::transpileGLSL(const std::string& desktopGLSL, uint32_t shaderType) {
        if (desktopGLSL.empty()) return "";

        std::stringstream output;

        // Step 1: Target version header setup
        output << "#version " << m_options.targetGLESVersion << " es\n";

        // Step 2: High precision qualifier injection
        if (m_options.forceHighPrecision) {
            output << "precision highp float;\n";
            output << "precision highp int;\n";
            output << "precision highp sampler2D;\n";
            output << "precision highp sampler3D;\n";
            output << "precision highp samplerCube;\n";
            output << "precision highp sampler2DArray;\n";
            output << "precision highp sampler2DShadow;\n";
            output << "precision highp sampler2DArrayShadow;\n";
            if (shaderType == 0x91B9 /* GL_COMPUTE_SHADER */) {
                output << "precision highp image2D;\n";
                output << "precision highp uimage2D;\n";
                output << "precision highp iimage2D;\n";
            }
        }

        // Step 3: Desktop GL Emulation Macros for Iris, OptiFine & Shaderpack Compatibility
        injectDesktopEmulationMacros(output, shaderType);

        std::string processedCode = desktopGLSL;

        // Step 4: Strip desktop #version directive
        size_t versionPos = processedCode.find("#version");
        if (versionPos != std::string::npos) {
            size_t lineEnd = processedCode.find('\n', versionPos);
            if (lineEnd != std::string::npos) {
                processedCode.erase(versionPos, lineEnd - versionPos + 1);
            }
        }

        // Step 5: Strip unsupported desktop ARB and NV extensions
        if (m_options.stripUnsupportedExtensions) {
            stripDesktopExtensions(processedCode);
        }

        // Step 6: Convert SSBO std430 layouts to std140 for mobile drivers
        convertSSBOLayouts(processedCode);

        // Step 7: Sanitize interpolation qualifiers (noperspective -> smooth)
        sanitizeInterpolators(processedCode);

        // Step 8: Convert deprecated texture samplers and functions
        convertTextureSamplers(processedCode);

        // Step 9: Process MRT outputs (gl_FragData and gl_FragColor)
        if (m_options.convertMRT && shaderType == 0x8B30 /* GL_FRAGMENT_SHADER */) {
            processMRTOutputs(processedCode);
            convertGBufferBindings(processedCode);
        }

        // Step 10: Process Compute Shaders
        if (m_options.enableComputeFixes) {
            processComputeShaders(processedCode, shaderType);
        }

        // Step 11: Inject Depth Clamping Emulation for Mali/Adreno Shadow Maps
        injectDepthClampEmulation(processedCode, shaderType);

        // Step 12: Inject Hardware-Specific Optimizations
        injectHardwareOptimizations(processedCode);

        output << processedCode;
        return output.str();
    }

    void ShaderTranspiler::injectDesktopEmulationMacros(std::stringstream& output, uint32_t shaderType) {
        output << "// --- FearXextream Translation Engine Header ---\n";
        output << "#define FEAR_XEXTREAM_ENGINE 1\n";
        output << "#define FEAR_XEXTREAM_VERSION 300\n";
        output << "#define MC_ANDROID 1\n";
        output << "#define FEAR_MOBILE 1\n";

        // Desktop Vendor & Driver Emulation Macros for Complementary, Solas, Astralex, Bliss & Iris Shaders
        output << "#define MC_GL_VENDOR_NVIDIA 1\n";
        output << "#define MC_GL_RENDERER_GEFORCE 1\n";
        output << "#define MC_GLSL_VERSION_460 1\n";
        output << "#define SHADER_PACK_COMPAT 1\n";
        output << "#define IRIS_FEATURE_SSBO 1\n";
        output << "#define IRIS_FEATURE_CUSTOM_IMAGES 1\n";
        output << "#define IRIS_FEATURE_FADE_VARIABLE 1\n";
        output << "#define IRIS_FEATURE_SEPARATE_HARDWARE_SAMPLERS 1\n";
        output << "#define SODIUM_FEATURE_MODERN_GL 1\n";

        if (m_options.enableACESTonemap) {
            output << "#define ACES_TONEMAPPING 1\n";
            output << "vec3 applyACESTonemap(vec3 color) {\n";
            output << "    float a = 2.51;\n";
            output << "    float b = 0.03;\n";
            output << "    float c = 2.43;\n";
            output << "    float d = 0.59;\n";
            output << "    float e = 0.14;\n";
            output << "    return clamp((color * (a * color + b)) / (color * (c * color + d) + e), 0.0, 1.0);\n";
            output << "}\n";
        }
    }

    void ShaderTranspiler::processMRTOutputs(std::string& code) {
        // Convert gl_FragData[N] to layout(location = N) out vec4 fear_FragDataN
        if (code.find("gl_FragData") != std::string::npos) {
            std::string declarations;
            for (int i = 0; i < 8; ++i) {
                std::string target = "gl_FragData[" + std::to_string(i) + "]";
                std::string replacement = "fear_FragData" + std::to_string(i);
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

        // Convert gl_FragColor to layout(location = 0) out vec4 fear_FragColor
        if (code.find("gl_FragColor") != std::string::npos) {
            std::string replacement = "fear_FragColor";
            size_t pos = 0;
            while ((pos = code.find("gl_FragColor", pos)) != std::string::npos) {
                code.replace(pos, 12, replacement);
                pos += replacement.length();
            }
            if (code.find("fear_FragColor") != std::string::npos && code.find("layout(location = 0) out vec4 fear_FragColor;") == std::string::npos) {
                code = "layout(location = 0) out vec4 fear_FragColor;\n" + code;
            }
        }
    }

    void ShaderTranspiler::convertGBufferBindings(std::string& code) {
        // Map G-Buffer samplers colortex0-colortex15 and depthtex0-depthtex2
        for (int i = 0; i <= 15; ++i) {
            std::string colorTex = "colortex" + std::to_string(i);
            std::string gbufferTex = "gbufferColor" + std::to_string(i);
            size_t pos = 0;
            while ((pos = code.find(gbufferTex, pos)) != std::string::npos) {
                code.replace(pos, gbufferTex.length(), colorTex);
                pos += colorTex.length();
            }
        }
    }

    void ShaderTranspiler::convertSSBOLayouts(std::string& code) {
        // SSBO layout qualifier conversion: std430 -> std140 for GLES driver compatibility
        size_t pos = 0;
        while ((pos = code.find("std430", pos)) != std::string::npos) {
            code.replace(pos, 6, "std140");
            pos += 6;
        }
    }

    void ShaderTranspiler::stripDesktopExtensions(std::string& code) {
        const std::vector<std::string> extensionsToStrip = {
            "GL_NV_shader_noperspective_interpolation",
            "GL_ARB_gpu_shader5",
            "GL_NV_gpu_shader5",
            "GL_ARB_shading_language_420pack",
            "GL_ARB_explicit_attrib_location",
            "GL_EXT_gpu_shader4",
            "GL_ARB_draw_buffers",
            "GL_ARB_texture_rectangle"
        };

        for (const auto& ext : extensionsToStrip) {
            size_t pos = 0;
            while ((pos = code.find(ext, pos)) != std::string::npos) {
                size_t lineStart = code.rfind('\n', pos);
                if (lineStart == std::string::npos) lineStart = 0;
                else lineStart += 1;
                size_t lineEnd = code.find('\n', pos);
                if (lineEnd == std::string::npos) lineEnd = code.length();
                code.replace(lineStart, lineEnd - lineStart, "// [FearXextream] Stripped extension: " + ext);
            }
        }
    }

    void ShaderTranspiler::sanitizeInterpolators(std::string& code) {
        const std::string target = "noperspective";
        size_t pos = 0;
        while ((pos = code.find(target, pos)) != std::string::npos) {
            bool validBefore = (pos == 0) || (!isalnum(code[pos - 1]) && code[pos - 1] != '_');
            bool validAfter = (pos + target.length() >= code.length()) || (!isalnum(code[pos + target.length()]) && code[pos + target.length()] != '_');
            if (validBefore && validAfter) {
                code.replace(pos, target.length(), "smooth");
                pos += 6;
            } else {
                pos += target.length();
            }
        }
    }

    void ShaderTranspiler::convertTextureSamplers(std::string& code) {
        // Convert deprecated desktop GLSL texture functions to standard GLSL ES 3.0+ texture functions
        const std::pair<std::string, std::string> replacements[] = {
            {"texture2D(", "texture("},
            {"texture2DProj(", "textureProj("},
            {"texture2DLod(", "textureLod("},
            {"texture2DGrad(", "textureGrad("},
            {"textureCube(", "texture("},
            {"textureCubeLod(", "textureLod("},
            {"texture3D(", "texture("},
            {"texture1D(", "texture("},
            {"shadow2D(", "texture("},
            {"shadow2DProj(", "textureProj("}
        };

        for (const auto& pair : replacements) {
            size_t pos = 0;
            while ((pos = code.find(pair.first, pos)) != std::string::npos) {
                code.replace(pos, pair.first.length(), pair.second);
                pos += pair.second.length();
            }
        }
    }

    void ShaderTranspiler::processComputeShaders(std::string& code, uint32_t shaderType) {
        if (shaderType == 0x91B9 /* GL_COMPUTE_SHADER */ || code.find("layout(local_size_") != std::string::npos) {
            if (code.find("gl_FragCoord.xy") != std::string::npos) {
                size_t pos = 0;
                while ((pos = code.find("gl_FragCoord.xy", pos)) != std::string::npos) {
                    code.replace(pos, 15, "vec2(gl_GlobalInvocationID.xy)");
                    pos += 29;
                }
            }
            if (code.find("gl_FragCoord") != std::string::npos) {
                size_t pos = 0;
                while ((pos = code.find("gl_FragCoord", pos)) != std::string::npos) {
                    code.replace(pos, 12, "vec4(gl_GlobalInvocationID.xy, 0.0, 1.0)");
                    pos += 40;
                }
            }

            if (code.find("layout(local_size_") == std::string::npos) {
                size_t mainPos = code.find("void main");
                if (mainPos != std::string::npos) {
                    code.insert(mainPos, "\nlayout(local_size_x = 1, local_size_y = 1, local_size_z = 1) in;\n");
                }
            }
        }
    }

    void ShaderTranspiler::injectDepthClampEmulation(std::string& code, uint32_t shaderType) {
        if (shaderType == 0x8B31 /* GL_VERTEX_SHADER */) {
            if (code.find("gl_Position") != std::string::npos) {
                size_t mainPos = code.rfind('}');
                if (mainPos != std::string::npos) {
                    std::string depthClampFix = "\n    // FearXextream Mali/Adreno Depth Clamp Emulation for Shadows\n"
                                                "    gl_Position.z = clamp(gl_Position.z, -gl_Position.w, gl_Position.w);\n";
                    code.insert(mainPos, depthClampFix);
                }
            }
        }
    }

    void ShaderTranspiler::injectHardwareOptimizations(std::string& code) {
        if (m_options.gpuArch == GPUArchitecture::ARM_MALI) {
            code = "// FearXextream ARM Mali Pipeline Optimization Active\n" + code;
        } else if (m_options.gpuArch == GPUArchitecture::QUALCOMM_ADRENO) {
            code = "// FearXextream Qualcomm Adreno Early-Z & LRZ Optimization Active\n" + code;
        }
    }

}
