#include <jni.h>
#include <android/log.h>
#include <string>
#include "fear_hooks.h"
#include "fear_backend.h"
#include "fear_plugin_loader.h"

#define TAG "FEAR_RENDERER"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

static std::string g_jvmCachePath = "";

extern "C" {

int getTranslatedShaderCountInternal();
void initShaderCacheSystem(const std::string& cacheDir, int launcherVersion);
void clearShaderCacheDir();

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    LOGI("Fear Renderer Native Library Loading...");
    initialize_fear_hooks();
    detect_hardware_and_select_backend();
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_initFearShaderEngine(JNIEnv* env, jclass clazz, jstring cachePath, jint version) {
    if (cachePath) {
        const char* path = env->GetStringUTFChars(cachePath, nullptr);
        g_jvmCachePath = path;
        env->ReleaseStringUTFChars(cachePath, path);
        initShaderCacheSystem(g_jvmCachePath, version);
        LOGI("Fear Shader Engine initialized from JNI.");
    }
}

JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_destroyFearShaderEngine(JNIEnv* env, jclass clazz) {
    LOGI("Fear Shader Engine destroyed from JNI.");
}

JNIEXPORT jstring JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_getShaderCachePath(JNIEnv* env, jclass clazz) {
    return env->NewStringUTF(g_jvmCachePath.c_str());
}

JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_clearShaderCache(JNIEnv* env, jclass clazz) {
    clearShaderCacheDir();
}

JNIEXPORT jint JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_getTranslatedShaderCount(JNIEnv* env, jclass clazz) {
    return getTranslatedShaderCountInternal();
}

// ===== Custom Render Plugin Injection JNI Bridge =====

JNIEXPORT jboolean JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_loadRenderPlugin(JNIEnv* env, jclass clazz, jstring pluginPath) {
    if (!pluginPath) {
        LOGI("[FearPlugin] loadRenderPlugin called with null path");
        return JNI_FALSE;
    }
    const char* path = env->GetStringUTFChars(pluginPath, nullptr);
    if (!path) return JNI_FALSE;

    int result = fear_plugin_load(path);
    env->ReleaseStringUTFChars(pluginPath, path);

    if (result == 0) {
        LOGI("[FearPlugin] Plugin loaded successfully via JNI: %s v%s",
             fear_plugin_get_name() ? fear_plugin_get_name() : "unknown",
             fear_plugin_get_version() ? fear_plugin_get_version() : "unknown");
        return JNI_TRUE;
    } else {
        LOGI("[FearPlugin] Plugin load failed with code: %d", result);
        return JNI_FALSE;
    }
}

JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_unloadRenderPlugin(JNIEnv* env, jclass clazz) {
    LOGI("[FearPlugin] Unloading render plugin via JNI");
    fear_plugin_unload();
}

JNIEXPORT jboolean JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_isRenderPluginLoaded(JNIEnv* env, jclass clazz) {
    return fear_plugin_is_loaded() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_getRenderPluginName(JNIEnv* env, jclass clazz) {
    const char* name = fear_plugin_get_name();
    if (!name) return env->NewStringUTF("");
    return env->NewStringUTF(name);
}

JNIEXPORT jstring JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_getRenderPluginVersion(JNIEnv* env, jclass clazz) {
    const char* ver = fear_plugin_get_version();
    if (!ver) return env->NewStringUTF("");
    return env->NewStringUTF(ver);
}

JNIEXPORT jint JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_getRenderPluginOverrideCount(JNIEnv* env, jclass clazz) {
    return fear_plugin_get_override_count();
}

} // extern "C"
