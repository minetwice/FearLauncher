//
// Vulkan driver loader: system | Turnip (Adreno) | PanVK (Mali / panfrost)
//

#include <android/api-level.h>
#include <stdio.h>
#include <string.h>
#include <dlfcn.h>
#include <stdlib.h>
#include <jni.h>

#define TAG __FILE_NAME__
#include <log.h>

#include <driver_helper/nsbypass.h>
#include <android/dlext.h>

static bool turnip_enabled = false;
static bool panvk_enabled = false;

#ifdef ENABLE_TURNIP_LOADER

/* Shared path: load a Mesa ICD .so via linker namespace + hook, expose as libmjlvlk.so */
static bool load_mesa_vulkan_icd(const char* driver_soname, const char* label) {
    static bool driver_loaded = false;
    static char loaded_label[32] = {0};
    if (driver_loaded) {
        if (label && loaded_label[0] && strcmp(loaded_label, label) == 0)
            return true;
        printf("DriverHook: ICD already loaded as %s, skip re-load of %s\n", loaded_label, label ? label : "?");
        return true;
    }

    const char* native_dir = getenv("POJAV_NATIVEDIR");
    const char* cache_dir = getenv("TMPDIR");
    if (!native_dir) {
        printf("DriverHook: POJAV_NATIVEDIR not set\n");
        return false;
    }
    if (!linker_ns_load(native_dir)) {
        printf("DriverHook: linker_ns_load failed\n");
        return false;
    }

    void* linkerhook = linker_ns_dlopen("liblinkerhook.so", RTLD_LOCAL | RTLD_NOW);
    if (linkerhook == NULL) {
        printf("DriverHook: liblinkerhook.so failed: %s\n", dlerror());
        return false;
    }

    void* driver_handle = linker_ns_dlopen(driver_soname, RTLD_LOCAL | RTLD_NOW);
    if (driver_handle == NULL) {
        printf("DriverHook: Failed to load %s (%s)!\n%s\n", label ? label : "ICD", driver_soname, dlerror());
        dlclose(linkerhook);
        return false;
    }

    void* dl_android = linker_ns_dlopen("libdl_android.so", RTLD_LOCAL | RTLD_LAZY);
    if (dl_android == NULL) {
        printf("DriverHook: libdl_android.so failed: %s\n", dlerror());
        dlclose(driver_handle);
        dlclose(linkerhook);
        return false;
    }

    void* android_get_exported_namespace = dlsym(dl_android, "android_get_exported_namespace");
    void (*linkerhook_pass_handles)(void*, void*, void*) =
        (void (*)(void*, void*, void*))dlsym(linkerhook, "app__pojav_linkerhook_pass_handles");

    if (linkerhook_pass_handles == NULL || android_get_exported_namespace == NULL) {
        printf("DriverHook: missing symbols\n");
        dlclose(dl_android);
        dlclose(driver_handle);
        dlclose(linkerhook);
        return false;
    }
    linkerhook_pass_handles(driver_handle, android_dlopen_ext, android_get_exported_namespace);

    void* libvulkan = linker_ns_dlopen_unique(cache_dir, "libvulkan.so", "libmjlvlk.so", RTLD_LOCAL | RTLD_NOW);
    printf("DriverHook: Loaded %s as mjlvlk, ptr=%p\n", label ? label : "ICD", libvulkan);
    if (libvulkan) {
        driver_loaded = true;
        if (label) {
            strncpy(loaded_label, label, sizeof(loaded_label) - 1);
            loaded_label[sizeof(loaded_label) - 1] = 0;
        }
        return true;
    }

    dlclose(dl_android);
    dlclose(driver_handle);
    dlclose(linkerhook);
    return false;
}

bool load_turnip_vulkan() {
    return load_mesa_vulkan_icd("libvulkan_freedreno.so", "Turnip");
}

bool load_panvk_vulkan() {
    if (load_mesa_vulkan_icd("libvulkan_panfrost.so", "PanVK"))
        return true;
    return load_mesa_vulkan_icd("libvulkan_panvk.so", "PanVK");
}
#endif

void* pojavexec_loadVulkanDriver() {
#ifdef ENABLE_TURNIP_LOADER
    if (android_get_device_api_level() >= 28) {
        if (panvk_enabled && load_panvk_vulkan())
            return linker_ns_dlopen("libmjlvlk.so", RTLD_LOCAL);
        if (turnip_enabled && load_turnip_vulkan())
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
    if (panvk_enabled) {
        if (!load_panvk_vulkan())
            printf("Failed to preload PanVK — will try system Vulkan\n");
        return;
    }
    if (!turnip_enabled) return;
    if (!load_turnip_vulkan())
        printf("Failed to preload Turnip!\n");
#endif
}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_setUseTurnip(JNIEnv *env, jclass clazz, jboolean enable) {
    turnip_enabled = enable;
    if (enable) panvk_enabled = false;
}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_setUsePanvk(JNIEnv *env, jclass clazz, jboolean enable) {
    panvk_enabled = enable;
    if (enable) turnip_enabled = false;
}
