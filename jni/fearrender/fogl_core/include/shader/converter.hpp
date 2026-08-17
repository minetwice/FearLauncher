#pragma once

#include "compilers.hpp"
#include "postprocess.hpp"
#include "preprocess.hpp"
#include "es/utils.hpp"
#include "gl/shader.hpp"
#include "shaderc/shaderc.h"
#include "shaderc/status.h"
#include "spirv_glsl.hpp"
#include "utils.hpp"

#include <stdexcept>
#include <string>
#include <GLES2/gl2.h>
#include <shaderc/shaderc.hpp>

namespace ShaderConverter {
    inline shaderc::SpvCompilationResult compileToSPV(shaderc_shader_kind kind, std::string& source) {
        int shaderVersion = 0;
        std::string shaderProfile = ""; // useless
        getShaderVersion(source, shaderVersion, shaderProfile);

        shaderc::Compiler compiler;
        shaderc::SpvCompilationResult result;

        GLSLRegexPreprocessor::moveVariableInitialization(source);
        GLSLRegexPreprocessor::removeLineDirectives(source);
        GLSLRegexPreprocessor::removeUselessExtensions(source);

        if (shaderVersion <= 120) {
            GLSLRegexPreprocessor::fixDeprecatedTextureFunction(source);
        }

        if (getEnvironmentVar("LIBGL_VGPU_DUMP") == "1") {
            LOGE("Preprocessed source:");
            LOGE("%s", source.c_str());
        }

        if (shaderVersion < 330) {
            upgradeTo330(kind, source);
            shaderVersion = 330;
        }

        shaderc::CompileOptions options = generateGLSL2SPVOptions(shaderVersion);
        result = compiler.CompileGlslToSpv(source, kind, "glslShader", options);

        if (result.GetCompilationStatus() != shaderc_compilation_status_success) {
            LOGE("Failed to compile shader: %s", result.GetErrorMessage().c_str());
            throw std::runtime_error("Shader compilation failed!");
        }

        return result;
    }

    inline void transpileSPV2ESSL(shaderc_shader_kind kind, shaderc::SpvCompilationResult& module, std::string& target) {
        spirv_cross::CompilerGLSL::Options options = generateSPV2ESSLOptions(ESUtils::shadingVersion);

        SPVCExposed_CompilerGLSL compiler({ module.cbegin(), module.cend() });
        compiler.set_common_options(options);

        SPVCPostprocessor::processSPVBytecode(compiler, kind);

        target = compiler.compile();

        if (getEnvironmentVar("LIBGL_VGPU_DUMP") == "1") {
            LOGI("Transpiled GLSL -> ESSL source:");
            LOGI("%s", target.c_str());
        }
    }

    inline void convertAndFix(shaderc_shader_kind kind, std::string& source) {
        if (getEnvironmentVar("LIBGL_VGPU_DUMP") == "1") {
            LOGI("Converting and fixing %s!", getKindStringFromKind(kind));
        }

        if (getEnvironmentVar("LIBGL_VGPU_DUMP") == "1") {
            LOGI("Received GLSL source:");
            LOGI("%s", source.c_str());
        }

        shaderc::SpvCompilationResult spirv = compileToSPV(kind, source);
        transpileSPV2ESSL(kind, spirv, source);
    }
}; // namespace ShaderConverter
