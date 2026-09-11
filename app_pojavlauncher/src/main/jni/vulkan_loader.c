//
// Created by maks on 10.04.2026.
// Modified to ensure Vulkan loader is available for Zink on Android
//

#include <android/api-level.h>
#include <stdio.h>
#include <dlfcn.h>
#include <stdlib.h>
#include <jni.h>

#define TAG __FILE_NAME__
#include <log.h>

#include <driver_helper/nsbypass.h>
#include <android/dlext.h>

static bool turnip_enabled = false;

#ifdef ENABLE_TURNIP_LOADER
bool load_turnip_vulkan() {
    static bool driver_loaded = false;
    if(driver_loaded) return true;

    const char* native_dir = getenv("POJAV_NATIVEDIR");
    const char* cache_dir = getenv("TMPDIR");
    if(!linker_ns_load(native_dir)) return NULL;
    void* linkerhook = linker_ns_dlopen("liblinkerhook.so", RTLD_LOCAL | RTLD_NOW);
    if(linkerhook == NULL) return NULL;
    void* turnip_driver_handle = linker_ns_dlopen("libvulkan_freedreno.so", RTLD_LOCAL | RTLD_NOW);
    if(turnip_driver_handle == NULL) {
        printf("DriverHook: Failed to load Turnip!
%s
", dlerror());
        goto fail_l;
    }

    void* dl_android = linker_ns_dlopen("libdl_android.so", RTLD_LOCAL | RTLD_LAZY);
    if(dl_android == NULL) goto fail_t;

    void* android_get_exported_namespace = dlsym(dl_android, "android_get_exported_namespace");
    void (*linkerhook_pass_handles)(void*, void*, void*) = dlsym(linkerhook, "app__pojav_linkerhook_pass_handles");

    if(linkerhook_pass_handles == NULL || android_get_exported_namespace == NULL) goto fail_d;
    linkerhook_pass_handles(turnip_driver_handle, android_dlopen_ext, android_get_exported_namespace);

    void* libvulkan = linker_ns_dlopen_unique(cache_dir, "libvulkan.so", "libmjlvlk.so", RTLD_LOCAL | RTLD_NOW);
    printf("DriverHook: Loaded mjlvlk, ptr=%p
", libvulkan);
    if(libvulkan) {
        driver_loaded = true;
        return true;
    }
    fail_d: dlclose(dl_android);
    fail_t: dlclose(turnip_driver_handle);
    fail_l: dlclose(linkerhook);
    return false;
}
#endif

void* pojavexec_loadVulkanDriver() {
#ifdef ENABLE_TURNIP_LOADER
    if(android_get_device_api_level() >= 28) { // the loader does not support below that
        if(turnip_enabled && load_turnip_vulkan())
            // Reference the vulkan driver separately to avoid weirdness from libraries calling dlclose
            return linker_ns_dlopen("libmjlvlk.so", RTLD_LOCAL);
    }
#endif
    
    // For Zink/Mesa: Try to load libvulkan.so from multiple locations
    void* vulkan_ptr = NULL;
    
    // First, try system vulkan
    vulkan_ptr = dlopen("libvulkan.so", RTLD_LAZY | RTLD_LOCAL);
    if(vulkan_ptr) {
        printf("VulkanLoader: loaded system vulkan, ptr=%p
", vulkan_ptr);
        return vulkan_ptr;
    }
    
    // If system vulkan not found, try from POJAV_NATIVEDIR (for Zink/Mesa)
    const char* native_dir = getenv("POJAV_NATIVEDIR");
    if(native_dir != NULL) {
        char vulkan_path[1024];
        snprintf(vulkan_path, sizeof(vulkan_path), "%s/libvulkan.so", native_dir);
        vulkan_ptr = dlopen(vulkan_path, RTLD_LAZY | RTLD_LOCAL);
        if(vulkan_ptr) {
            printf("VulkanLoader: loaded vulkan from NATIVEDIR, ptr=%p
", vulkan_ptr);
            return vulkan_ptr;
        }
    }
    
    // Try from standard library paths
    const char* lib_paths[] = {
        "/vendor/lib64/libvulkan.so",
        "/system/lib64/libvulkan.so",
        "/vendor/lib/libvulkan.so",
        "/system/lib/libvulkan.so",
        NULL
    };
    
    for(int i = 0; lib_paths[i] != NULL; i++) {
        vulkan_ptr = dlopen(lib_paths[i], RTLD_LAZY | RTLD_LOCAL);
        if(vulkan_ptr) {
            printf("VulkanLoader: loaded vulkan from %s, ptr=%p
", lib_paths[i], vulkan_ptr);
            return vulkan_ptr;
        }
    }
    
    printf("VulkanLoader: WARNING - Failed to load Vulkan driver from any location!
");
    printf("VulkanLoader: Zink requires libvulkan.so to be present!
");
    
    return vulkan_ptr;
}

// Does nothing if Turnip is unsupported - Mesa will load system driver automatically
JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_preloadVulkan(JNIEnv *env, jclass clazz) {
#ifdef ENABLE_TURNIP_LOADER
    if(!turnip_enabled) return;
    if(!load_turnip_vulkan()) {
        printf("Failed to preload Turnip!
");
    }
#endif
    
    // Always try to load system vulkan for Zink compatibility
    void* vulkan_ptr = pojavexec_loadVulkanDriver();
    if(vulkan_ptr == NULL) {
        printf("VulkanLoader: CRITICAL - Vulkan driver could not be loaded!
");
        printf("VulkanLoader: Zink/Mesa requires libvulkan.so to function!
");
    }
}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_setUseTurnip(JNIEnv *env, jclass clazz, jboolean enable) {
    turnip_enabled = enable;
}