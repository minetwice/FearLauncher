#ifndef FEAR_XEXTREAM_CORE_H
#define FEAR_XEXTREAM_CORE_H

#include <jni.h>
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES3/gl32.h>
#include <vulkan/vulkan.h>
#include <string>
#include <vector>

#define LOG_TAG "FearXextream"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace FearXextream {

    struct EngineConfig {
        bool enableVulkanBackend = true;
        bool enableShaderCaching = true;
        int glVersionMajor = 4;
        int glVersionMinor = 6;
        std::string cacheDirectory;
    };

    class ContextTracker {
    public:
        static ContextTracker& getInstance();
        void initialize(const EngineConfig& config);
        bool isInitialized() const;
        void logCaps();
    private:
        ContextTracker() = default;
        bool m_initialized = false;
        EngineConfig m_config;
    };

    class ShaderTranslator {
    public:
        static std::string translateGLSL(const std::string& source, GLenum shaderType);
        static bool compileSPIRV(const std::string& glslSource, std::vector<uint32_t>& spirvOutput);
    };

    // Global C Export for Runtime Shader Transpilation Hook
    extern "C" {
        const char* FearXextreamTranspileShader(const char* sourceCode, uint32_t shaderType);
    }

    class VulkanBackend {
    public:
        static bool initVulkanInstance();
        static void cleanup();
    };

}

#endif // FEAR_XEXTREAM_CORE_H
