#ifndef FEAR_XEXTREAM_CORE_H
#define FEAR_XEXTREAM_CORE_H

#include <jni.h>
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES3/gl32.h>
#include <vulkan/vulkan.h>
#include <string>
#include <vector>
#include <mutex>
#include <atomic>

#define LOG_TAG "FearXextream"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace FearXextream {

    enum class GPUArchitecture {
        UNKNOWN,
        ARM_MALI,
        QUALCOMM_ADRENO,
        POWERVR,
        GENERIC_MOBILE
    };

    struct GPUCapabilities {
        GPUArchitecture arch = GPUArchitecture::UNKNOWN;
        std::string vendor;
        std::string renderer;
        std::string version;
        std::string glslVersion;
        int maxTextureSize = 8192;
        int maxDrawBuffers = 8;
        int maxColorAttachments = 8;
        bool supportsFloatTextures = true;
        bool supportsDepth32F = true;
        bool supportsComputeShaders = true;
        bool supportsSwizzle = true;
        bool isMaliBifrost = false;
        bool isMaliValhall = false;
        bool isAdreno6xx = false;
        bool isAdreno7xx = false;
    };

    struct EngineConfig {
        bool enableVulkanBackend = true;
        bool enableShaderCaching = true;
        bool enableFPSBooster = true;
        bool enableColorEnhancer = true;
        bool enableBlissShaderFix = true;
        int glVersionMajor = 4;
        int glVersionMinor = 6;
        std::string cacheDirectory;
    };

    class ContextTracker {
    public:
        static ContextTracker& getInstance();
        void initialize(const EngineConfig& config);
        bool isInitialized() const;
        void detectGPUCapabilities();
        const GPUCapabilities& getCaps() const { return m_caps; }
        const EngineConfig& getConfig() const { return m_config; }
        GPUArchitecture getGPUArchitecture() const { return m_caps.arch; }
        void logCaps();
    private:
        ContextTracker() = default;
        std::atomic<bool> m_initialized{false};
        std::mutex m_mutex;
        EngineConfig m_config;
        GPUCapabilities m_caps;
    };

    class ShaderTranslator {
    public:
        static std::string translateGLSL(const std::string& source, GLenum shaderType);
        static bool compileSPIRV(const std::string& glslSource, std::vector<uint32_t>& spirvOutput);
    };

    class VulkanBackend {
    public:
        static bool initVulkanInstance();
        static void cleanup();
        static bool isVulkanAvailable();
    private:
        static std::atomic<bool> s_vulkanSupported;
    };

    // Global C Exports for Runtime Shader Transpilation, Texture Swizzle Hooks & Engine Ops
    extern "C" {
        const char* FearXextreamTranspileShader(const char* sourceCode, uint32_t shaderType);
        void FearXextreamApplyTextureSwizzle(uint32_t target, uint32_t format, uint32_t internalFormat);
        void FearXextreamOptimizeState();
        int FearXextreamGetGPUArchitectureCode();
    }

}

#endif // FEAR_XEXTREAM_CORE_H
