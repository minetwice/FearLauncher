#include <jni.h>
#include <android/log.h>
#include <string>
#include <dlfcn.h>
#include <EGL/egl.h>
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

JNIEXPORT jlong JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_eglGetDisplay(JNIEnv* env, jclass clazz, jlong display) {
    typedef EGLDisplay (*eglGetDisplay_pfn)(EGLNativeDisplayType);
    static eglGetDisplay_pfn p_eglGetDisplay = (eglGetDisplay_pfn)dlsym(RTLD_DEFAULT, "eglGetDisplay");
    if (p_eglGetDisplay) {
        return (jlong)(intptr_t)p_eglGetDisplay((EGLNativeDisplayType)(intptr_t)display);
    }
    return 0;
}

JNIEXPORT jboolean JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_eglInitialize(JNIEnv* env, jclass clazz, jlong display, jintArray majorArr, jintArray minorArr) {
    typedef EGLBoolean (*eglInitialize_pfn)(EGLDisplay, EGLint*, EGLint*);
    static eglInitialize_pfn p_eglInitialize = (eglInitialize_pfn)dlsym(RTLD_DEFAULT, "eglInitialize");
    if (p_eglInitialize && display != 0) {
        EGLint major = 0, minor = 0;
        EGLBoolean res = p_eglInitialize((EGLDisplay)(intptr_t)display, &major, &minor);
        if (res == EGL_TRUE) {
            if (majorArr) {
                jint maj = major;
                env->SetIntArrayRegion(majorArr, 0, 1, &maj);
            }
            if (minorArr) {
                jint min = minor;
                env->SetIntArrayRegion(minorArr, 0, 1, &min);
            }
            return JNI_TRUE;
        }
    }
    return JNI_FALSE;
}

JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_eglTerminate(JNIEnv* env, jclass clazz, jlong display) {
    typedef EGLBoolean (*eglTerminate_pfn)(EGLDisplay);
    static eglTerminate_pfn p_eglTerminate = (eglTerminate_pfn)dlsym(RTLD_DEFAULT, "eglTerminate");
    if (p_eglTerminate && display != 0) {
        p_eglTerminate((EGLDisplay)(intptr_t)display);
    }
}

} // extern "C"
