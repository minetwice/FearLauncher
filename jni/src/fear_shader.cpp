#include "fear_shader.h"
#include "fear_shader_engine.h"
#include <string>
#include <string_view>
#include <android/log.h>

#define TAG "FEAR_RENDERER"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

const char* translate_glsl_shader_on_the_fly(const char* source) {
    if (!source) return nullptr;

    std::string glsl_code(source);

    // Initialize FEAR high-core shader engine structures
    ShaderEngine engine;
    engine.vkState.enableVulkanBridge = true;
    engine.pipeline.enableMRT = true;

    // On-the-fly Desktop-to-Mobile GLSL translations
    size_t pos;
    while ((pos = glsl_code.find("noperspective")) != std::string::npos) {
        glsl_code.replace(pos, 13, "flat"); // Map noperspective to flat fallback safely
    }

    // Translate desktop layout qualifiers
    while ((pos = glsl_code.find("layout(binding = ")) != std::string::npos) {
        size_t end_bracket = glsl_code.find(")", pos);
        if (end_bracket != std::string::npos) {
            glsl_code.replace(pos, end_bracket + 1 - pos, "/* layout binding mapped */");
        }
    }

    // Replace and transpile high-end desktop versions to compliant GLES 3.20 ES layouts
    if (glsl_code.find("#version 330") != std::string::npos ||
        glsl_code.find("#version 150") != std::string::npos ||
        glsl_code.find("#version 400") != std::string::npos ||
        glsl_code.find("#version 410") != std::string::npos ||
        glsl_code.find("#version 430") != std::string::npos ||
        glsl_code.find("#version 450") != std::string::npos ||
        glsl_code.find("#version 460") != std::string::npos) {
        pos = glsl_code.find("#version");
        if (pos != std::string::npos) {
            size_t end_line = glsl_code.find("\n", pos);
            glsl_code.replace(pos, end_line - pos, "#version 320 es\nprecision highp float;\nprecision highp int;\nprecision highp sampler2D;\nprecision highp sampler2DShadow;");
        }
    }

    // Allocate return buffer
    char* result = (char*)malloc(glsl_code.size() + 1);
    strcpy(result, glsl_code.c_str());
    return result;
}
