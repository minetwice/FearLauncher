#include <jni.h>
#include <android/log.h>
#include "fear_hooks.h"
#include "fear_backend.h"

#define TAG "FEAR_RENDERER"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    LOGI("Fear Renderer Native Library Loading...");
    initialize_fear_hooks();
    detect_hardware_and_select_backend();
    return JNI_VERSION_1_6;
}

} // extern "C"
