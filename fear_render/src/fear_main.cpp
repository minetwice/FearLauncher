#include <jni.h>
#include <android/log.h>
#include "fear_hooks.h"
#include "fear_backend.h"
#include "fear_shader.h"
#include "fear_memory.h"
#include <string.h>

#define LOG_TAG "FEAR_RENDERER"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// Intercept glShaderSource to perform on-the-fly shader parsing/translation
typedef void (*PFN_glShaderSource)(GLuint shader, GLsizei count, const GLchar* const* string, const GLint* length);
static PFN_glShaderSource s_real_glShaderSource = nullptr;

extern "C" JNIEXPORT void JNICALL
fear_glShaderSource(GLuint shader, GLsizei count, const GLchar* const* string, const GLint* length) {
    if (!s_real_glShaderSource) {
        s_real_glShaderSource = (PFN_glShaderSource)fear_glGetProcAddress("glShaderSource");
    }

    if (count > 0 && string && string[0]) {
        // Retrieve and translate shader GLSL code
        std::string glsl_source(string[0]);
        std::string translated = FearShaderCompiler::translateGLSL(glsl_source, 0);

        const GLchar* translated_strings[1] = { translated.c_str() };
        GLint translated_lengths[1] = { (GLint)translated.size() };

        if (s_real_glShaderSource) {
            s_real_glShaderSource(shader, 1, translated_strings, translated_lengths);
        }
    } else {
        if (s_real_glShaderSource) {
            s_real_glShaderSource(shader, count, string, length);
        }
    }
}

// Map eglGetProcAddress through our hooks
extern "C" JNIEXPORT __eglMustCastToProperFunctionPointerType EGLAPIENTRY
eglGetProcAddress(const char* procname) {
    if (strcmp(procname, "glShaderSource") == 0) {
        return (__eglMustCastToProperFunctionPointerType)fear_glShaderSource;
    }
    return (__eglMustCastToProperFunctionPointerType)fear_glGetProcAddress(procname);
}

// Standard dynamic linker library init hook
extern "C" JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* reserved) {
    LOGI("=========================================================");
    LOGI("   FEAR RENDERER ENGINE v4.0 [PRO] LOADING AUTONOMOUSLY  ");
    LOGI("=========================================================");

    // Initialize EGL/GL hook systems
    init_fear_hooks();

    // Query hardware GPU drivers and active the safest rendering backend
    FearBackendManager::getInstance().detectAndInitializeBackend();

    LOGI("Fear Renderer JNI_OnLoad Completed successfully. System ready.");
    return JNI_VERSION_1_6;
}
