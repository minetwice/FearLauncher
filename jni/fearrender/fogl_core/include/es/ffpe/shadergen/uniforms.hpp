#pragma once

#include "es/ffpe/shadergen/cache.hpp"
#include "es/ffpe/shadergen/features/registry.hpp"
#include "es/raii_helpers.hpp"

#include <GLES3/gl32.h>

namespace FFPE::Rendering::ShaderGen::Uniforms {

inline void setupInputsForRendering(GLuint program) {
    GLDebugGroup gldg("Bind uniforms");

    for (const auto& feature : Feature::Registry::Data::registeredFeatures) {
        if (feature->isEnabled()) feature->sendData(program);
    }

    glUniform1i(
        Cache::Uniforms::getCachedUniformLocation(
            program, "tex0"
        ), 0
    );
}

}
