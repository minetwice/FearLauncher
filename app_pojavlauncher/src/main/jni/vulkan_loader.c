//
// Vulkan loader: system / Turnip (Adreno) / PanVK (Mali)
//

#include <android/api-level.h>
#include <stdio.h>
#include <dlfcn.h>
#include <stdlib.h>
#include <string.h>
#include <jni.h>

#define TAG __FILE_NAME__
#include <log.h>

#include <driver_helper/nsbypass.h>
#include <android/dlext.h>

static bool turnip_enabled = false;
static bool panvk_enabled = false;

#ifdef ENABLE_TURNIP_LOADER
bool load_turnip_vulkan() {
    static bool driver_loaded = false;
    if(driver_loaded) return true;

    const char* native_dir = getenv("POJAV_NATIVEDIR");
    const char* cache_dir = getenv("TMPDIR");
    if(!linker_ns_load(native_dir)) return false;
    void* linkerhook = linker_ns_dlopen("liblinkerhook.so", RTLD_LOCAL | RTLD_LAZY);
    if(linkerhook == NULL) return false;
    void* turnip_driver_handle = linker_ns_dlopen("libvulkan_freedreno.so", RTLD_LOCAL | RTLD_NOW);
    if(turnip_driver_handle == NULL) {
        printf("DriverHook: Failed to load Turnip!\n%s\n", dlerror());
        goto fail_l;
    }

    void* dl_android = linker_ns_dlopen("libdl_android.so", RTLD_LOCAL | RTLD_LAZY);
    if(dl_android == NULL) goto fail_t;

    void* android_get_exported_namespace = dlsym(dl_android, "android_get_exported_namespace");
    void (*linkerhook_pass_handles)(void*, void*, void*) = dlsym(linkerhook, "app__pojav_linkerhook_pass_handles");

    if(linkerhook_pass_handles == NULL || android_get_exported_namespace == NULL) goto fail_d;
    linkerhook_pass_handles(turnip_driver_handle, android_dlopen_ext, android_get_exported_namespace);

    void* libvulkan = linker_ns_dlopen_unique(cache_dir, "libvulkan.so", "libmjlvlk.so", RTLD_LOCAL | RTLD_NOW);
    printf("DriverHook: Loaded Turnip mjlvlk, ptr=%p\n", libvulkan);
    if(libvulkan) {
        driver_loaded = true;
        return true;
    }
    fail_d: dlclose(dl_android);
    fail_t: dlclose(turnip_driver_handle);
    fail_l: dlclose(linkerhook);
    return false;
}

/** Load Mesa PanVK (libvulkan_panfrost.so) — Mali equivalent of Turnip. */
bool load_panvk_vulkan() {
    static bool driver_loaded = false;
    if(driver_loaded) return true;

    const char* native_dir = getenv("POJAV_NATIVEDIR");
    const char* cache_dir = getenv("TMPDIR");
    if(!linker_ns_load(native_dir)) {
        printf("DriverHook: linker_ns_load failed for PanVK\n");
        return false;
    }
    void* linkerhook = linker_ns_dlopen("liblinkerhook.so", RTLD_LOCAL | RTLD_LAZY);
    if(linkerhook == NULL) {
        printf("DriverHook: liblinkerhook.so missing for PanVK\n");
        return false;
    }
    void* panvk_handle = linker_ns_dlopen("libvulkan_panfrost.so", RTLD_LOCAL | RTLD_NOW);
    if(panvk_handle == NULL) {
        printf("DriverHook: Failed to load PanVK (libvulkan_panfrost.so)!\n%s\n", dlerror());
        dlclose(linkerhook);
        return false;
    }

    void* dl_android = linker_ns_dlopen("libdl_android.so", RTLD_LOCAL | RTLD_LAZY);
    if(dl_android == NULL) {
        dlclose(panvk_handle);
        dlclose(linkerhook);
        return false;
    }

    void* android_get_exported_namespace = dlsym(dl_android, "android_get_exported_namespace");
    void (*linkerhook_pass_handles)(void*, void*, void*) = dlsym(linkerhook, "app__pojav_linkerhook_pass_handles");

    if(linkerhook_pass_handles == NULL || android_get_exported_namespace == NULL) {
        dlclose(dl_android);
        dlclose(panvk_handle);
        dlclose(linkerhook);
        return false;
    }
    linkerhook_pass_handles(panvk_handle, android_dlopen_ext, android_get_exported_namespace);

    void* libvulkan = linker_ns_dlopen_unique(cache_dir, "libvulkan.so", "libmjlvpanvk.so", RTLD_LOCAL | RTLD_NOW);
    printf("DriverHook: Loaded PanVK mjlvpanvk, ptr=%p\n", libvulkan);
    if(libvulkan) {
        driver_loaded = true;
        return true;
    }
    dlclose(dl_android);
    dlclose(panvk_handle);
    dlclose(linkerhook);
    return false;
}
#endif

void* pojavexec_loadVulkanDriver() {
#ifdef ENABLE_TURNIP_LOADER
    if(android_get_device_api_level() >= 28) {
        // PanVK (libvulkan_panfrost.so) is a desktop Linux build — it has
        // libdrm.so.2, libxcb.so.1, libwayland-client.so.0 etc. as DT_NEEDED
        // entries which don't exist on Android. Skip it and use system Vulkan
        // (Mali's proprietary driver supports Vulkan 1.1+, enough for Zink).
        if(turnip_enabled && load_turnip_vulkan())
            return linker_ns_dlopen("libmjlvlk.so", RTLD_LOCAL);
    }
#endif
    void* vulkan_ptr = dlopen("libvulkan.so", RTLD_LAZY | RTLD_LOCAL);
    printf("VulkanLoader: loaded system vulkan, ptr=%p\n", vulkan_ptr);
    return vulkan_ptr;
}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_preloadVulkan(JNIEnv *env, jclass clazz) {
#ifdef ENABLE_TURNIP_LOADER
    // PanVK driver (libvulkan_panfrost.so) is a desktop Linux build that
    // cannot load on Android. Skip preloading — system Vulkan will be used.
    if(!turnip_enabled) return;
    if(!load_turnip_vulkan()) {
        printf("Failed to preload Turnip!\n");
    }
#endif
}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_setUseTurnip(JNIEnv *env, jclass clazz, jboolean enable) {
    turnip_enabled = enable;
    if(enable) panvk_enabled = false;
}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_setUsePanvk(JNIEnv *env, jclass clazz, jboolean enable) {
    panvk_enabled = enable;
    if(enable) turnip_enabled = false;
    printf("VulkanLoader: setUsePanvk=%d\n", (int)enable);
}
