/*
 * Fear Monster: the direct-Vulkan path's native shim.
 *
 * The engine renders Minecraft straight to Vulkan, so on this path the launcher has to
 * do two things the GL renderers never need:
 *
 *   1. Make a Vulkan device reachable - open the loader, and pick the ICD the same way
 *      the renderers do (Mesa Turnip on Adreno, the vendor driver elsewhere).
 *   2. Hand the engine a window it can build a VkSurfaceKHR from.
 *
 * This file is the first of those. It is deliberately built as its own small library
 * and is not loaded by anything yet: until the render path is wired up and tested on a
 * real device, nothing here runs, so it cannot disturb a working session. The rule for
 * this whole path is that it becomes reachable only once it has been proven.
 */
#include <jni.h>
#include <dlfcn.h>
#include <android/log.h>
#include <stddef.h>

#define LOG_TAG "FearMonsterVK"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/* The opened Vulkan implementation, kept resident for the engine. */
static void* g_vulkan_loader = NULL;

/*
 * Opens the Vulkan loader. The caller may pass the loader it wants first (the ICD
 * selection decides that); if that fails or is empty, the system loader is tried. The
 * handle is kept with RTLD_GLOBAL so the engine and LWJGL's Vulkan bindings resolve
 * their symbols against it.
 *
 * Returns true when a loader is open, false when none could be.
 */
JNIEXPORT jboolean JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_nativeFearMonsterLoadVulkan(JNIEnv* env, jclass clazz, jstring path) {
    if (g_vulkan_loader != NULL) return JNI_TRUE;

    const char* cpath = NULL;
    if (path != NULL) cpath = (*env)->GetStringUTFChars(env, path, NULL);

    const char* candidates[3];
    int n = 0;
    if (cpath != NULL && cpath[0] != '\0') candidates[n++] = cpath;
    candidates[n++] = "libvulkan.so";
    candidates[n++] = "libvulkan.so.1";

    jboolean ok = JNI_FALSE;
    for (int i = 0; i < n; i++) {
        if (candidates[i] == NULL) continue;
        g_vulkan_loader = dlopen(candidates[i], RTLD_NOW | RTLD_GLOBAL);
        if (g_vulkan_loader != NULL) {
            LOGI("Vulkan loader opened: %s", candidates[i]);
            ok = JNI_TRUE;
            break;
        }
        LOGE("could not open %s: %s", candidates[i], dlerror());
    }

    if (cpath != NULL) (*env)->ReleaseStringUTFChars(env, path, cpath);
    return ok;
}
