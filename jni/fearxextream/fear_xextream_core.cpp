#include "fear_xextream_core.h"
#include "fear_xextream_shader_transpiler.h"
#include "fear_xextream_state_tracker.h"
#include "fear_xextream_texture_translator.h"
#include "fear_xextream_mali_fixer.h"
#include "fear_xextream_dither_engine.h"
#include "fear_xextream_gpu_signal.h"
#include "fear_shader_translator.h"
#include <sstream>
#include <algorithm>
#include <dlfcn.h>
#include <cstring>

typedef VkResult (*PFN_vkCreateInstance_t)(const VkInstanceCreateInfo*, const VkAllocationCallbacks*, VkInstance*);
typedef void (*PFN_vkDestroyInstance_t)(VkInstance, const VkAllocationCallbacks*);

namespace FearXextream {

    std::atomic<bool> VulkanBackend::s_vulkanSupported{false};

    ContextTracker& ContextTracker::getInstance() {
        static ContextTracker instance;
        return instance;
    }

    void ContextTracker::initialize(const EngineConfig& config) {
        std::lock_guard<std::mutex> lock(m_mutex);
        if (m_initialized) {
            LOGI("FearXextream ContextTracker: Already initialized.");
            return;
        }

        m_config = config;
        detectGPUCapabilities();
        m_initialized = true;

        LOGI("FearXextream Core Engine Initialized Successfully (GL Target %d.%d, GPU Arch: %d)",
             config.glVersionMajor, config.glVersionMinor, static_cast<int>(m_caps.arch));
        logCaps();
    }

    bool ContextTracker::isInitialized() const {
        return m_initialized;
    }

    void ContextTracker::detectGPUCapabilities() {
        typedef const unsigned char* (*glGetString_pfn)(GLenum);
        static glGetString_pfn real_glGetString = (glGetString_pfn)dlsym(RTLD_DEFAULT, "glGetString");

        m_caps.arch = GPUArchitecture::GENERIC_MOBILE;

        if (real_glGetString) {
            const unsigned char* vendor = real_glGetString(GL_VENDOR);
            const unsigned char* renderer = real_glGetString(GL_RENDERER);
            const unsigned char* version = real_glGetString(GL_VERSION);
            const unsigned char* glslVer = real_glGetString(0x8B8C /* GL_SHADING_LANGUAGE_VERSION */);

            if (vendor) m_caps.vendor = reinterpret_cast<const char*>(vendor);
            if (renderer) m_caps.renderer = reinterpret_cast<const char*>(renderer);
            if (version) m_caps.version = reinterpret_cast<const char*>(version);
            if (glslVer) m_caps.glslVersion = reinterpret_cast<const char*>(glslVer);
        }

        std::string rendLower = m_caps.renderer;
        std::transform(rendLower.begin(), rendLower.end(), rendLower.begin(), ::tolower);

        if (rendLower.find("mali") != std::string::npos || rendLower.find("bifrost") != std::string::npos || rendLower.find("valhall") != std::string::npos) {
            m_caps.arch = GPUArchitecture::ARM_MALI;
            if (rendLower.find("g71") != std::string::npos || rendLower.find("g72") != std::string::npos || rendLower.find("g51") != std::string::npos || rendLower.find("g52") != std::string::npos || rendLower.find("g31") != std::string::npos) {
                m_caps.isMaliBifrost = true;
            } else {
                m_caps.isMaliValhall = true;
            }
            LOGI("FearXextream Detected ARM Mali GPU Architecture (Bifrost=%d, Valhall=%d)", m_caps.isMaliBifrost, m_caps.isMaliValhall);
        } else if (rendLower.find("adreno") != std::string::npos || rendLower.find("qualcomm") != std::string::npos) {
            m_caps.arch = GPUArchitecture::QUALCOMM_ADRENO;
            if (rendLower.find("adreno 6") != std::string::npos || rendLower.find("adreno (tm) 6") != std::string::npos) {
                m_caps.isAdreno6xx = true;
            } else if (rendLower.find("adreno 7") != std::string::npos || rendLower.find("adreno (tm) 7") != std::string::npos) {
                m_caps.isAdreno7xx = true;
            }
            LOGI("FearXextream Detected Qualcomm Adreno GPU Architecture (6xx=%d, 7xx=%d)", m_caps.isAdreno6xx, m_caps.isAdreno7xx);
        } else if (rendLower.find("powervr") != std::string::npos || rendLower.find("rogue") != std::string::npos) {
            m_caps.arch = GPUArchitecture::POWERVR;
            LOGI("FearXextream Detected PowerVR GPU Architecture");
        } else {
            m_caps.arch = GPUArchitecture::GENERIC_MOBILE;
            LOGI("FearXextream Generic Mobile/Desktop GPU detected");
        }

        typedef void (*glGetIntegerv_pfn)(GLenum, GLint*);
        static glGetIntegerv_pfn real_glGetIntegerv = (glGetIntegerv_pfn)dlsym(RTLD_DEFAULT, "glGetIntegerv");
        if (real_glGetIntegerv) {
            GLint maxTex = 8192;
            real_glGetIntegerv(GL_MAX_TEXTURE_SIZE, &maxTex);
            if (maxTex > 0) m_caps.maxTextureSize = maxTex;

            GLint maxBuf = 8;
            real_glGetIntegerv(0x8824 /* GL_MAX_DRAW_BUFFERS */, &maxBuf);
            if (maxBuf > 0) m_caps.maxDrawBuffers = maxBuf;
        }
    }

    void ContextTracker::logCaps() {
        LOGI("FearXextream Pipeline Configuration:");
        LOGI("  Vendor:   %s", m_caps.vendor.empty() ? "N/A" : m_caps.vendor.c_str());
        LOGI("  Renderer: %s", m_caps.renderer.empty() ? "N/A" : m_caps.renderer.c_str());
        LOGI("  Version:  %s", m_caps.version.empty() ? "N/A" : m_caps.version.c_str());
        LOGI("  Max Draw Buffers: %d", m_caps.maxDrawBuffers);
        LOGI("  Max Texture Size: %d", m_caps.maxTextureSize);
    }

    std::string ShaderTranslator::translateGLSL(const std::string& source, GLenum shaderType) {
        if (source.empty()) return "";
        TranspilerOptions options;
        options.gpuArch = ContextTracker::getInstance().getGPUArchitecture();
        ShaderTranspiler transpiler(options);
        std::string result = transpiler.transpileGLSL(source, shaderType);
        if (options.gpuArch == GPUArchitecture::ARM_MALI) {
            MaliShaderFixer::applyMaliWorkarounds(result);
        }
        DitherEngine::getInstance().injectAntiFlickerLayer(result, shaderType);
        return result;
    }

    bool ShaderTranslator::compileSPIRV(const std::string& glslSource, std::vector<uint32_t>& spirvOutput) {
        spirvOutput.clear();
        if (glslSource.empty()) return false;
        bool success = false;
        spirvOutput = FearCompileGLSLToSPIRV(glslSource.c_str(), GL_FRAGMENT_SHADER, "fear_xextream_shader", &success);
        LOGI("FearXextream ShaderTranslator: Compiled SPIR-V bytecode buffer size = %zu words", spirvOutput.size());
        return success && !spirvOutput.empty();
    }

    bool VulkanBackend::initVulkanInstance() {
        s_vulkanSupported = false;
        void* handle = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
        if (!handle) {
            LOGW("FearXextream VulkanBackend: libvulkan.so not accessible on device.");
            return false;
        }

        PFN_vkCreateInstance_t pfnCreateInstance = (PFN_vkCreateInstance_t)dlsym(handle, "vkCreateInstance");
        PFN_vkDestroyInstance_t pfnDestroyInstance = (PFN_vkDestroyInstance_t)dlsym(handle, "vkDestroyInstance");

        if (!pfnCreateInstance || !pfnDestroyInstance) {
            LOGW("FearXextream VulkanBackend: Vulkan instance entry points missing.");
            dlclose(handle);
            return false;
        }

        VkInstance instance = VK_NULL_HANDLE;
        VkApplicationInfo appInfo = {};
        appInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
        appInfo.pApplicationName = "FearXextreamEngine";
        appInfo.applicationVersion = VK_MAKE_VERSION(2, 0, 0);
        appInfo.pEngineName = "FearXextream";
        appInfo.engineVersion = VK_MAKE_VERSION(2, 0, 0);
        appInfo.apiVersion = VK_API_VERSION_1_1;

        VkInstanceCreateInfo createInfo = {};
        createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
        createInfo.pApplicationInfo = &appInfo;

        VkResult res = pfnCreateInstance(&createInfo, nullptr, &instance);
        if (res == VK_SUCCESS && instance != VK_NULL_HANDLE) {
            LOGI("FearXextream VulkanBackend: Vulkan Driver Probe Succeeded!");
            pfnDestroyInstance(instance, nullptr);
            dlclose(handle);
            s_vulkanSupported = true;
            return true;
        } else {
            LOGW("FearXextream VulkanBackend: Vulkan Instance creation returned code %d", res);
            dlclose(handle);
            return false;
        }
    }

    void VulkanBackend::cleanup() {
        s_vulkanSupported = false;
        LOGI("FearXextream VulkanBackend: Resources released.");
    }

    bool VulkanBackend::isVulkanAvailable() {
        return s_vulkanSupported;
    }

}

extern "C" {

    void FearXextreamApplyTextureSwizzle(uint32_t target, uint32_t format, uint32_t internalFormat) {
        FearXextream::TextureTranslator::getInstance().applyMaliTextureSwizzleFix(target, format, internalFormat);
    }

    const char* FearXextreamTranspileShader(const char* sourceCode, uint32_t shaderType) {
        if (!sourceCode) return "";
        static thread_local std::string lastResult;
        try {
            FearXextream::TranspilerOptions options;
            options.gpuArch = FearXextream::ContextTracker::getInstance().getGPUArchitecture();
            FearXextream::ShaderTranspiler transpiler(options);
            lastResult = transpiler.transpileGLSL(sourceCode, shaderType);
            if (options.gpuArch == FearXextream::GPUArchitecture::ARM_MALI || options.gpuArch == FearXextream::GPUArchitecture::UNKNOWN) {
                FearXextream::MaliShaderFixer::applyMaliWorkarounds(lastResult);
            }
            FearXextream::DitherEngine::getInstance().injectAntiFlickerLayer(lastResult, shaderType);
        } catch (...) {
            LOGE("FearXextreamTranspileShader: Exception during transpilation, using fallback source.");
            lastResult = sourceCode;
        }
        return lastResult.c_str();
    }

    void FearXextreamOptimizeState() {
        FearXextream::StateTracker::getInstance().optimizeCurrentState();
        FearXextream::GPUSignalOptimizer::getInstance().optimizeGPUSignals();
    }

    int FearXextreamGetGPUArchitectureCode() {
        return static_cast<int>(FearXextream::ContextTracker::getInstance().getGPUArchitecture());
    }

    JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_initFearXextreamEngine(JNIEnv* env, jclass clazz, jstring cacheDirStr) {
        if (!env) return;
        const char* path = cacheDirStr ? env->GetStringUTFChars(cacheDirStr, nullptr) : nullptr;
        try {
            FearXextream::EngineConfig config;
            config.cacheDirectory = path ? path : "";
            FearXextream::ContextTracker::getInstance().initialize(config);
            FearXextream::VulkanBackend::initVulkanInstance();

            FearXextream::TextureTranslator::getInstance().translateFormat(GL_RGBA16F, GL_RGBA, GL_HALF_FLOAT);
            FearXextream::MaliShaderFixer::configureMaliPipelineEnv();
            FearXextream::DitherEngine::getInstance().configureColorPrecision();
            FearXextream::GPUSignalOptimizer::getInstance().optimizeGPUSignals();

            LOGI("FearXextream Engine initialized safely without crash risks on Adreno & Mali GPUs.");
        } catch (const std::exception& e) {
            LOGE("FearXextream Engine JNI initialization exception: %s", e.what());
        } catch (...) {
            LOGE("FearXextream Engine JNI initialization unknown exception.");
        }

        if (path && cacheDirStr) env->ReleaseStringUTFChars(cacheDirStr, path);
    }

}
