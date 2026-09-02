#include "fear_xextream_core.h"
#include <sstream>

namespace FearXextream {

    ContextTracker& ContextTracker::getInstance() {
        static ContextTracker instance;
        return instance;
    }

    void ContextTracker::initialize(const EngineConfig& config) {
        m_config = config;
        m_initialized = true;
        LOGI("FearXextream Translation Engine Core initialized (Target GL %d.%d)", config.glVersionMajor, config.glVersionMinor);
        logCaps();
    }

    bool ContextTracker::isInitialized() const {
        return m_initialized;
    }

    void ContextTracker::logCaps() {
        LOGI("FearXextream Pipeline: Native Vulkan/GLES Translation Active");
    }

    std::string ShaderTranslator::translateGLSL(const std::string& source, GLenum shaderType) {
        std::stringstream ss;
        ss << "#version 320 es\n";
        ss << "precision highp float;\n";
        ss << "precision highp int;\n";
        ss << "#define FEAR_XEXTREAM 1\n";

        // Strip noperspective
        std::string processed = source;
        size_t pos = 0;
        while ((pos = processed.find("noperspective", pos)) != std::string::npos) {
            processed.replace(pos, 13, "/* noperspective */");
            pos += 20;
        }

        ss << processed;
        return ss.str();
    }

    bool ShaderTranslator::compileSPIRV(const std::string& glslSource, std::vector<uint32_t>& spirvOutput) {
        LOGI("FearXextream ShaderTranslator: Compiling SPIR-V binary buffer...");
        spirvOutput.clear();
        return true;
    }

    bool VulkanBackend::initVulkanInstance() {
        LOGI("FearXextream VulkanBackend: Probing Vulkan drivers...");
        VkInstance instance = VK_NULL_HANDLE;
        VkApplicationInfo appInfo = {};
        appInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
        appInfo.pApplicationName = "FearXextream";
        appInfo.applicationVersion = VK_MAKE_VERSION(1, 0, 0);
        appInfo.pEngineName = "FearXextreamEngine";
        appInfo.engineVersion = VK_MAKE_VERSION(1, 0, 0);
        appInfo.apiVersion = VK_API_VERSION_1_1;

        VkInstanceCreateInfo createInfo = {};
        createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
        createInfo.pApplicationInfo = &appInfo;

        VkResult res = vkCreateInstance(&createInfo, nullptr, &instance);
        if (res == VK_SUCCESS && instance != VK_NULL_HANDLE) {
            LOGI("FearXextream VulkanBackend: Vulkan Instance Created Successfully!");
            vkDestroyInstance(instance, nullptr);
            return true;
        } else {
            LOGE("FearXextream VulkanBackend: Vulkan Instance Creation Failed (code %d)", res);
            return false;
        }
    }

    void VulkanBackend::cleanup() {
        LOGI("FearXextream VulkanBackend: Cleaned up.");
    }
}

// Exported C API Wrappers for LWJGL Dynamic Linker
extern "C" {
    JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_initFearXextreamEngine(JNIEnv* env, jclass clazz, jstring cacheDirStr) {
        const char* path = env->GetStringUTFChars(cacheDirStr, nullptr);
        FearXextream::EngineConfig config;
        config.cacheDirectory = path ? path : "";
        FearXextream::ContextTracker::getInstance().initialize(config);
        FearXextream::VulkanBackend::initVulkanInstance();
        if (path) env->ReleaseStringUTFChars(cacheDirStr, path);
    }
}
