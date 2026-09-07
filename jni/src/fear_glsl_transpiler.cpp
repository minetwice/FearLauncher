#include "fear_glsl_transpiler.h"
#include "fear_shader_translator.h"
#include "fear_shader_logger.h"
#include <string>

std::string FearTranspileGLSL(
    const char* src,
    GLenum type,
    int esVersion,
    bool* ok
) {
    if (!src) {
        if (ok) *ok = false;
        return "";
    }

    // High-performance transpilation pipeline with robust fallback
    bool fallbackSuccess = false;
    std::string result = FearTranslateGLSL(src, type, &fallbackSuccess);

    if (fallbackSuccess && !result.empty()) {
        if (ok) *ok = true;
        LOG_INFO("[FearRender] Shader path: SPIRV-Cross / string-fallback | transpile ok");
        return result;
    }

    if (ok) *ok = false;
    LOG_WARNING("[FearRender] Shader path: SPIRV-Cross / string-fallback | transpile failed");
    return std::string(src);
}
