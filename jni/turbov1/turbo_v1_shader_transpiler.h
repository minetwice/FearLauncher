#ifndef TURBO_V1_SHADER_TRANSPILER_H
#define TURBO_V1_SHADER_TRANSPILER_H

#include "turbo_v1_core.h"
#include <string>
#include <vector>
#include <cstdint>

namespace turbo_v1 {

enum class ShaderStage {
    Vertex, Fragment, Geometry, Compute, TessControl, TessEval
};

struct SpirvOptimizationOptions {
    bool strip_debug;
    bool strip_nonsemantic;
    bool relax_struct_store;
    bool eliminate_dead_code;
};

struct ShaderCompilationResult {
    bool success;
    std::string glsl_source;
    std::vector<uint32_t> spirv_binary;
    std::string error_log;
    uint32_t layout_index_failure;
};

// NextGen High-FPS GLSL Shader Transpiler for Minecraft Java Shaders (OptiFine / Iris / Complementary / Solas)
std::string transpile_shader(const std::string& source, ShaderStage stage);

// SPIR-V Optimizer & Binary Stripper Pipeline for Mali GPUs
ShaderCompilationResult compile_and_optimize_spirv(const std::string& glsl_source, ShaderStage stage, const SpirvOptimizationOptions& options);

void log_mali_driver_compilation_failure(const std::string& stage_name, uint32_t layout_idx, const std::string& driver_info, const std::string& message);

} // namespace turbo_v1

#endif // TURBO_V1_SHADER_TRANSPILER_H
