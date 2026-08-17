#pragma once

#include "shaderc/shaderc.hpp"
#include "spirv_glsl.hpp"
#include "utils/log.hpp"
#include "utils/env.hpp"

#include <stdexcept>

inline void upgradeTo330(shaderc_shader_kind kind, std::string& src) {
    shaderc::CompileOptions options;
    options.SetGenerateDebugInfo();
    options.SetSourceLanguage(shaderc_source_language_glsl);
    options.SetTargetEnvironment(shaderc_target_env_opengl, 330);
    options.SetForcedVersionProfile(330, shaderc_profile_core);
    options.SetOptimizationLevel(shaderc_optimization_level_zero);
    options.SetVulkanRulesRelaxed(true);

    options.SetAutoMapLocations(true);
    options.SetAutoBindUniforms(true);

    shaderc::Compiler spirvCompiler;
    shaderc::SpvCompilationResult module = spirvCompiler.CompileGlslToSpv(
        src, kind,
        "shader_upgto330", options
    );

    if (module.GetCompilationStatus() != shaderc_compilation_status_success) {
        throw std::runtime_error("Shader upgrade error: " + module.GetErrorMessage());
    }

    spirv_cross::CompilerGLSL glslCompiler({ module.cbegin(), module.cend() });
    spirv_cross::CompilerGLSL::Options glslOptions;
    glslOptions.version = 330;
    glslOptions.es = false;
    glslOptions.vulkan_semantics = false;
    glslOptions.enable_420pack_extension = false;
    glslOptions.force_flattened_io_blocks = true;
    glslOptions.enable_storage_image_qualifier_deduction = false;
    glslCompiler.set_common_options(glslOptions);

    src = glslCompiler.compile();

    if (getEnvironmentVar("LIBGL_VGPU_DUMP") == "1") {
        LOGI("Upgraded shader source:");
        LOGI("%s", src.c_str());
    }
}
