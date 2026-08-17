#include "build_info.hpp"
#include "main.hpp"
#include "es/limits.hpp"
#include "es/utils.hpp"
#include "gles/ffp/main.hpp"
#include "gles/main.hpp"
#include "gles20/main.hpp"
#include "gles20/ext/main.hpp"
#include "gles30/main.hpp"
#include "gles32/main.hpp"
#include "shader/cache.hpp"
#include "utils/log.hpp"

#include <GLES3/gl32.h>
#include <unordered_map>

inline std::unordered_map<std::string, FunctionPtr> registeredFunctions;

void FOGLTLOGLES::registerFunction(std::string name, FunctionPtr function) {
    if (registeredFunctions.find(name) != registeredFunctions.end()) {
        LOGI("Overriding %s", name.c_str());
    } else {
        LOGI("Registering %s", name.c_str());
    }

    registeredFunctions.insert({ name, function });
}

FunctionPtr FOGLTLOGLES::getFunctionAddress(std::string name) {
    if (name.empty()) return nullptr; // fast path
    if (auto it = registeredFunctions.find(name);
        it != registeredFunctions.end()) {
        // LOGI("Found function named %s", name.c_str());
        return it->second;
    }

    // LOGW("Function named %s not found", name.c_str());
    return nullptr;
}

void FOGLTLOGLES::init() {
    LOGI("Using FOGLTLOGLES %s", RENDERER_VERSION);

    ESUtils::init();
    ESLimits::init();

    LOGI("FOGLTLOGLES launched on:");
    if (ESUtils::isAngle)
        LOGI(
            "ANGLE %i.%i.%i",
            std::get<0>(ESUtils::angleVersion),
            std::get<1>(ESUtils::angleVersion),
            std::get<2>(ESUtils::angleVersion)
        );

    LOGI("GLES : %i.%i", ESUtils::version.first, ESUtils::version.second);
    LOGI("ESSL : %i", ESUtils::shadingVersion);

    ShaderConverter::Cache::init();

    GLES     ::wrapper->init();
    FFP      ::wrapper->init();
    GLES20   ::wrapper->init();
    GLES20Ext::wrapper->init();
    GLES30   ::wrapper->init();
    GLES32   ::wrapper->init();
}
