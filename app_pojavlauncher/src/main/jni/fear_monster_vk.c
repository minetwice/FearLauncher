/*
 * Fear Monster: the direct-Vulkan path's native shim.
 *
 * The engine renders Minecraft straight to Vulkan, so on this path the launcher has to
 * do things the GL renderers never need:
 *
 *   1. Make a Vulkan device reachable - open the loader, and pick the ICD the same way
 *      the renderers do.
 *   2. Hand the engine a window it can build a VkSurfaceKHR from.
 *   3. Build that surface. Vulkan on Android has no Win32 window and no X11 display -
 *      only an ANativeWindow - so vkCreateAndroidSurfaceKHR is the only way to get one,
 *      and GLFW's glfwCreateWindowSurface (which the engine calls) cannot do it.
 *
 * Deliberately built as its own small library and loaded only on the fear_monster path:
 * nothing here runs on any other renderer, so it cannot disturb a working session.
 */
#include <jni.h>
#include <dlfcn.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>
#include <android/log.h>
#include <android/native_window.h>

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

/*
 * ---- the Vulkan bits this file needs ----
 *
 * Declared here rather than included: the NDK ships no Vulkan headers, and this is the
 * whole of the API the surface creation touches. Getting these numbers or the field order
 * wrong would produce a failure nothing else explains, so they are spelled out.
 */

/* vkCreateAndroidSurfaceKHR is core Vulkan 1.0 functionality behind the KHR extension. */
#define VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR 1000008000
#define VK_SUCCESS 0

typedef struct VkAndroidSurfaceCreateInfoKHR {
    uint32_t    sType;
    const void* pNext;
    uint32_t    flags;
    void*       window; /* ANativeWindow* */
} VkAndroidSurfaceCreateInfoKHR;

typedef int32_t (*PFN_vkCreateAndroidSurfaceKHR)(void* instance,
                                                 const VkAndroidSurfaceCreateInfoKHR* pCreateInfo,
                                                 const void* pAllocator,
                                                 uint64_t* pSurface);

/* The loader, opened on demand so the engine can build a surface even if nothing opened
 * it earlier on this path. */
static void* loader_for_surface(void) {
    if (g_vulkan_loader != NULL) return g_vulkan_loader;
    g_vulkan_loader = dlopen("libvulkan.so", RTLD_NOW | RTLD_GLOBAL);
    if (g_vulkan_loader == NULL) {
        g_vulkan_loader = dlopen("libvulkan.so.1", RTLD_NOW | RTLD_GLOBAL);
    }
    if (g_vulkan_loader != NULL) LOGI("Vulkan loader opened for the surface");
    else LOGE("no Vulkan loader for the surface: %s", dlerror());
    return g_vulkan_loader;
}

/*
 * Builds the VkSurfaceKHR the engine renders into.
 *
 * instance and window are the pointers the engine and the launcher already hold; the
 * result is written into pSurface, which the caller passes as a direct LongBuffer. The
 * return value is a VkResult, so the engine can hand it straight to its own checkResult
 * and keep its error handling intact.
 */
JNIEXPORT jint JNICALL
Java_net_vulkanmod_vulkan_FearAndroidSurface_nativeCreateSurface(JNIEnv* env, jclass clazz,
                                                                 jlong instance, jlong window,
                                                                 jobject pSurfaceBuffer) {
    (void) clazz;
    if (instance == 0 || window == 0) {
        LOGE("no instance or no window (instance=%lld window=%lld)",
             (long long) instance, (long long) window);
        return -1;
    }
    if (pSurfaceBuffer == NULL) {
        LOGE("no surface buffer");
        return -1;
    }

    void* loader = loader_for_surface();
    if (loader == NULL) return -1;

    PFN_vkCreateAndroidSurfaceKHR create = (PFN_vkCreateAndroidSurfaceKHR)
            dlsym(loader, "vkCreateAndroidSurfaceKHR");
    if (create == NULL) {
        LOGE("vkCreateAndroidSurfaceKHR is not in this loader: %s", dlerror());
        return -1;
    }

    VkAndroidSurfaceCreateInfoKHR info;
    memset(&info, 0, sizeof(info));
    info.sType  = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR;
    info.pNext  = NULL;
    info.flags  = 0;
    info.window = (void*) (intptr_t) window;

    uint64_t surface = 0;
    int32_t result = create((void*) (intptr_t) instance, &info, NULL, &surface);
    if (result != VK_SUCCESS) {
        LOGE("vkCreateAndroidSurfaceKHR failed: %d", (int) result);
        return (jint) result;
    }

    uint64_t* out = (uint64_t*) (*env)->GetDirectBufferAddress(env, pSurfaceBuffer);
    if (out == NULL) {
        LOGE("the surface buffer is not a direct buffer");
        return -1;
    }
    out[0] = surface;
    LOGI("VkSurfaceKHR created: 0x%llx", (unsigned long long) surface);
    return (jint) VK_SUCCESS;
}
