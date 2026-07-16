#include <string>
#include <string_view>
#include <android/log.h>
#include <stdlib.h>
#include <string.h>

#define TAG "MH_DRIVE"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

#include <dlfcn.h>

extern "C" {

// Export glMemoryBarrier to prevent server lobby world rendering crashes under MH DRIVE
void glMemoryBarrier(unsigned int barriers) {
    typedef void (*glFlush_pfn)();
    static glFlush_pfn real_glFlush = nullptr;
    if (!real_glFlush) {
        real_glFlush = (glFlush_pfn)dlsym(RTLD_NEXT, "glFlush");
    }
    if (real_glFlush) {
        real_glFlush();
    }
    __android_log_print(ANDROID_LOG_INFO, "MH_DRIVE", "glMemoryBarrier intercepted and flushed safely (Barriers: %u)", barriers);
}

void glMemoryBarrierEXT(unsigned int barriers) {
    glMemoryBarrier(barriers);
}

const char* mh_drive_preprocess_shader_ast(const char* glsl_source) {
    if (!glsl_source) return nullptr;

    std::string source_str(glsl_source);

    // Track 1 AST Preprocessor: Intercept and translate desktop layout qualifiers on Mali GPUs
    size_t pos;

    // Rewrite desktop output layout qualifiers to compatible GLES 3.2 variables
    while ((pos = source_str.find("layout(location = 0) out vec4 fragColor;")) != std::string::npos) {
        source_str.replace(pos, 40, "out vec4 fragColor;");
    }

    while ((pos = source_str.find("layout(location = 0) out vec4 out_Color;")) != std::string::npos) {
        source_str.replace(pos, 40, "out vec4 out_Color;");
    }

    // Strip un-supported desktop noperspective qualifiers
    while ((pos = source_str.find("noperspective")) != std::string::npos) {
        source_str.replace(pos, 13, "flat");
    }

    // Adapt layout binding layouts dynamically for GLES 3.2
    if (source_str.find("#version 330") != std::string::npos || source_str.find("#version 150") != std::string::npos) {
        pos = source_str.find("#version");
        if (pos != std::string::npos) {
            size_t end_line = source_str.find("\n", pos);
            source_str.replace(pos, end_line - pos, "#version 320 es\nprecision highp float;\nprecision highp int;");
        }
    }

    char* allocated_result = (char*)malloc(source_str.size() + 1);
    strcpy(allocated_result, source_str.c_str());

    LOGI("MH DRIVE: AST compilation string preprocess hook complete.");
    return allocated_result;
}

}
