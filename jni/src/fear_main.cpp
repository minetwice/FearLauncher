#include <jni.h>
#include <android/log.h>
#include <string>
#include "fear_hooks.h"
#include "fear_backend.h"

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

} // extern "C"
