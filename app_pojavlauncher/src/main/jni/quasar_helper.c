//
// Quasar Helper - Native GPU detection and shader utilities for Mali GPUs
//
// This native library provides low-level GPU information and helps with
// shader compatibility on Mali GPUs.
//

#include <jni.h>
#include <string.h>
#include <android/log.h>
#include <GLES2/gl2.h>
#include <GLES3/gl3.h>
#include <GLES3/gl31.h>

#define LOG_TAG "QuasarHelper"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Mali GPU patterns
static const char *MALI_GPU_PATTERNS[] = {
    "Mali-T",
    "Mali-G",
    "Mali-B",
    "Immortalis"
};

// Missing extensions on Mali
static const char *MISSING_EXTENSIONS[] = {
    "GL_EXT_shader_framebuffer_fetch",
    "GL_ARB_shader_image_load_store",
    "GL_NV_shader_framebuffer_fetch"
};

/**
 * Check if the current GPU is a Mali GPU
 */
JNIEXPORT jboolean JNICALL
Java_net_kdt_pojavlaunch_utils_QuasarShaderFixer_isMaliGPUNative(JNIEnv *env, jclass clazz) {
    const char *renderer = (const char *)glGetString(GL_RENDERER);
    const char *vendor = (const char *)glGetString(GL_VENDOR);
    
    if (renderer == NULL || vendor == NULL) {
        LOGW("Failed to get GPU info: renderer=%p, vendor=%p", renderer, vendor);
        return JNI_FALSE;
    }
    
    LOGD("GPU Info - Vendor: %s, Renderer: %s", vendor, renderer);
    
    // Check for Mali GPUs
    for (int i = 0; i < sizeof(MALI_GPU_PATTERNS) / sizeof(MALI_GPU_PATTERNS[0]); i++) {
        if (strstr(renderer, MALI_GPU_PATTERNS[i]) != NULL) {
            if (strcmp(vendor, "ARM") == 0) {
                LOGD("Detected Mali GPU: %s", renderer);
                return JNI_TRUE;
            }
        }
    }
    
    return JNI_FALSE;
}

/**
 * Check if framebuffer fetch is supported
 */
JNIEXPORT jboolean JNICALL
Java_net_kdt_pojavlaunch_utils_QuasarShaderFixer_hasFramebufferFetchNative(JNIEnv *env, jclass clazz) {
    const char *extensions = (const char *)glGetString(GL_EXTENSIONS);
    
    if (extensions == NULL) {
        LOGW("Failed to get extensions string");
        return JNI_FALSE;
    }
    
    // Check for various framebuffer fetch extensions
    for (int i = 0; i < sizeof(MISSING_EXTENSIONS) / sizeof(MISSING_EXTENSIONS[0]); i++) {
        if (strstr(extensions, MISSING_EXTENSIONS[i]) != NULL) {
            LOGD("Found extension: %s", MISSING_EXTENSIONS[i]);
            return JNI_TRUE;
        }
    }
    
    LOGD("Framebuffer fetch not supported");
    return JNI_FALSE;
}

/**
 * Get GPU information as a string
 */
JNIEXPORT jstring JNICALL
Java_net_kdt_pojavlaunch_utils_QuasarShaderFixer_getGpuInfoNative(JNIEnv *env, jclass clazz) {
    const char *vendor = (const char *)glGetString(GL_VENDOR);
    const char *renderer = (const char *)glGetString(GL_RENDERER);
    const char *version = (const char *)glGetString(GL_VERSION);
    
    if (vendor == NULL) vendor = "Unknown";
    if (renderer == NULL) renderer = "Unknown";
    if (version == NULL) version = "Unknown";
    
    char info[512];
    snprintf(info, sizeof(info), "Vendor: %s | Renderer: %s | Version: %s", vendor, renderer, version);
    
    return (*env)->NewStringUTF(env, info);
}

/**
 * Get GLSL version string
 */
JNIEXPORT jstring JNICALL
Java_net_kdt_pojavlaunch_utils_QuasarShaderFixer_getGlslVersionNative(JNIEnv *env, jclass clazz) {
    const char *version = (const char *)glGetString(GL_SHADING_LANGUAGE_VERSION);
    
    if (version == NULL) {
        // Try to determine from GL version
        const char *glVersion = (const char *)glGetString(GL_VERSION);
        if (glVersion != NULL) {
            if (strstr(glVersion, "OpenGL ES 3.1") != NULL) {
                return (*env)->NewStringUTF(env, "#version 310 es");
            } else if (strstr(glVersion, "OpenGL ES 3.0") != NULL) {
                return (*env)->NewStringUTF(env, "#version 300 es");
            } else if (strstr(glVersion, "OpenGL ES 2.0") != NULL) {
                return (*env)->NewStringUTF(env, "#version 100");
            }
        }
        return (*env)->NewStringUTF(env, "#version 100");
    }
    
    return (*env)->NewStringUTF(env, version);
}

/**
 * Check if a specific extension is supported
 */
JNIEXPORT jboolean JNICALL
Java_net_kdt_pojavlaunch_utils_QuasarShaderFixer_hasExtensionNative(JNIEnv *env, jclass clazz, jstring extension) {
    const char *extensions = (const char *)glGetString(GL_EXTENSIONS);
    const char *ext = (*env)->GetStringUTFChars(env, extension, NULL);
    
    if (extensions == NULL || ext == NULL) {
        return JNI_FALSE;
    }
    
    int result = strstr(extensions, ext) != NULL;
    
    (*env)->ReleaseStringUTFChars(env, extension, ext);
    
    return result;
}

/**
 * Native initialization
 */
JNIEXPORT jboolean JNICALL
Java_net_kdt_pojavlaunch_utils_QuasarShaderFixer_nativeInit(JNIEnv *env, jclass clazz) {
    LOGD("Quasar Helper native initialization");
    
    const char *renderer = (const char *)glGetString(GL_RENDERER);
    const char *vendor = (const char *)glGetString(GL_VENDOR);
    
    if (renderer == NULL || vendor == NULL) {
        LOGE("Failed to get GPU info during init");
        return JNI_FALSE;
    }
    
    LOGD("GPU: %s (%s)", renderer, vendor);
    
    return JNI_TRUE;
}

// JNI method registration
static JNINativeMethod methods[] = {
    {"isMaliGPUNative", "()Z", Java_net_kdt_pojavlaunch_utils_QuasarShaderFixer_isMaliGPUNative},
    {"hasFramebufferFetchNative", "()Z", Java_net_kdt_pojavlaunch_utils_QuasarShaderFixer_hasFramebufferFetchNative},
    {"getGpuInfoNative", "()Ljava/lang/String;", Java_net_kdt_pojavlaunch_utils_QuasarShaderFixer_getGpuInfoNative},
    {"getGlslVersionNative", "()Ljava/lang/String;", Java_net_kdt_pojavlaunch_utils_QuasarShaderFixer_getGlslVersionNative},
    {"hasExtensionNative", "(Ljava/lang/String;)Z", Java_net_kdt_pojavlaunch_utils_QuasarShaderFixer_hasExtensionNative},
    {"nativeInit", "()Z", Java_net_kdt_pojavlaunch_utils_QuasarShaderFixer_nativeInit}
};

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        LOGE("Failed to get JNI environment");
        return JNI_ERR;
    }
    
    jclass clazz = (*env)->FindClass(env, "net/kdt/pojavlaunch/utils/QuasarShaderFixer");
    if (clazz == NULL) {
        LOGE("Failed to find QuasarShaderFixer class");
        return JNI_ERR;
    }
    
    int result = (*env)->RegisterNatives(env, clazz, methods, 
                                          sizeof(methods) / sizeof(methods[0]));
    if (result < 0) {
        LOGE("Failed to register native methods");
        return JNI_ERR;
    }
    
    LOGD("Quasar Helper JNI loaded successfully");
    
    return JNI_VERSION_1_6;
}
