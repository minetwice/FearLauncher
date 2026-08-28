#include <android/log.h>
#include <jni.h>
#include <string>

#define TAG "FEAR_RENDERER"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

extern "C" void detect_hardware_and_select_backend();

#if defined(QUASAR_PURE)
extern "C" void quasar_core_boot();
#else
/* FOGL / GLFear path: optional full hook init if linked */
extern "C" void initialize_fear_hooks() __attribute__((weak));
static void quasar_core_boot() {
    if (initialize_fear_hooks)
        initialize_fear_hooks();
}
#endif

static std::string g_jvmCachePath;

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    (void)vm; (void)reserved;
    LOGI("Fear Renderer Native Library Loading...");
    quasar_core_boot();
    detect_hardware_and_select_backend();
#if defined(QUASAR_PURE)
    LOGI("QuasarCore pure GLES3 backend ready (no LTW/gl4es/Glues)");
#else
    LOGI("FearRender FOGL path ready");
#endif
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_initFearShaderEngine(JNIEnv* env, jclass clazz, jstring cachePath, jint version) {
    (void)clazz; (void)version;
    if (cachePath) {
        const char* path = env->GetStringUTFChars(cachePath, nullptr);
        g_jvmCachePath = path;
        env->ReleaseStringUTFChars(cachePath, path);
        LOGI("Fear Shader Engine path: %s", g_jvmCachePath.c_str());
    }
}

JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_destroyFearShaderEngine(JNIEnv* env, jclass clazz) {
    (void)env; (void)clazz;
    LOGI("Fear Shader Engine destroyed");
}

JNIEXPORT jstring JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_getShaderCachePath(JNIEnv* env, jclass clazz) {
    (void)clazz;
    return env->NewStringUTF(g_jvmCachePath.c_str());
}

JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_clearShaderCache(JNIEnv* env, jclass clazz) {
    (void)env; (void)clazz;
}

JNIEXPORT jint JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_getTranslatedShaderCount(JNIEnv* env, jclass clazz) {
    (void)env; (void)clazz;
    return 0;
}

} // extern "C"
