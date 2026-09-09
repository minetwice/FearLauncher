#ifndef TURBO_V1_SHADER_TRANSPILER_H
#define TURBO_V1_SHADER_TRANSPILER_H

#include "turbo_v1_core.h"
#include <string>

namespace turbo_v1 {

enum class ShaderStage {
    Vertex, Fragment, Geometry, Compute, TessControl, TessEval
};

// NextGen High-FPS GLSL Shader Transpiler for Minecraft Java Shaders (OptiFine / Iris / Complementary / Solas)
std::string transpile_shader(const std::string& source, ShaderStage stage);

} // namespace turbo_v1

#endif // TURBO_V1_SHADER_TRANSPILER_H
