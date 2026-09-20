//
// FearLauncher Vulkan loader — Turnip (Adreno) + Fear Render / PanVK (Mali)
// Fixed: never claim "PanVK ready" when unique libmjlvlk open fails (libgpud_sys.so).
// Sets FEAR_PANVK_OK=0/1 for egl_proc_hook (skip OSMesa hang when 0).
//

#include <android/api-level.h>
#include <stdio.h>
#include <dlfcn.h>
#include <stdlib.h>
#include <string.h>
#include <jni.h>
#include <limits.h>

#define TAG __FILE_NAME__
#include <log.h>

#include <driver_helper/nsbypass.h>
#include <android/dlext.h>

static bool turnip_enabled = false;

#ifdef ENABLE_TURNIP_LOADER
static void* dlopen_in_native_dir(const char* native_dir, const char* soname, int flags) {
    if (native_dir && native_dir[0]) {
        char path[PATH_MAX];
        snprintf(path, sizeof(path), "%s/%s", native_dir, soname);
        void* h = dlopen(path, flags);
        if (h) {
            printf("DriverHook: dlopen(%s) ok\n", path);
            return h;
        }
        printf("DriverHook: dlopen(%s) failed: %s\n", path, dlerror());
    }
    void* h = dlopen(soname, flags);
    if (h) {
        printf("DriverHook: dlopen(%s) ok (default search)\n", soname);
        return h;
    }
    printf("DriverHook: dlopen(%s) failed: %s\n", soname, dlerror());
    return NULL;
}

static bool load_named_vulkan_driver(const char* driver_soname, const char* label) {
    static char loaded_name[128];
    static bool driver_loaded = false;
    if (driver_loaded && strcmp(loaded_name, driver_soname) == 0) return true;

    const char* native_dir = getenv("POJAV_NATIVEDIR");
    const char* cache_dir = getenv("TMPDIR");
    if (!cache_dir || !cache_dir[0]) cache_dir = getenv("HOME");

    printf("DriverHook: loading %s (%s), native_dir=%s\n",
           label, driver_soname, native_dir ? native_dir : "(null)");

    if (!native_dir || !native_dir[0]) {
        printf("DriverHook: POJAV_NATIVEDIR not set\n");
        return false;
    }

    {
        char path[PATH_MAX];
        snprintf(path, sizeof(path), "%s/%s", native_dir, driver_soname);
        FILE* f = fopen(path, "rb");
        if (!f) {
            printf("DriverHook: %s not present at %s — skip custom driver\n", driver_soname, path);
            return false;
        }
        fclose(f);
    }

    if (!linker_ns_load(native_dir)) {
        printf("DriverHook: linker_ns_load failed for %s\n", label);
        return false;
    }

    void* linkerhook = linker_ns_dlopen("liblinkerhook.so", RTLD_LOCAL | RTLD_NOW);
    if (linkerhook == NULL) {
        printf("DriverHook: linker_ns_dlopen(liblinkerhook.so) failed, trying absolute path\n");
        linkerhook = dlopen_in_native_dir(native_dir, "liblinkerhook.so", RTLD_LOCAL | RTLD_NOW);
    }
    if (linkerhook == NULL) {
        printf("DriverHook: liblinkerhook.so missing for %s\n", label);
        return false;
    }

    void* driver_handle = linker_ns_dlopen(driver_soname, RTLD_LOCAL | RTLD_NOW);
    if (driver_handle == NULL) {
        printf("DriverHook: linker_ns_dlopen(%s) failed, trying absolute path\n", driver_soname);
        driver_handle = dlopen_in_native_dir(native_dir, driver_soname, RTLD_LOCAL | RTLD_NOW);
    }
    if (driver_handle == NULL) {
        printf("DriverHook: Failed to load %s (%s)! %s\n", label, driver_soname, dlerror());
        dlclose(linkerhook);
        return false;
    }

    void* dl_android = linker_ns_dlopen("libdl_android.so", RTLD_LOCAL | RTLD_LAZY);
    if (dl_android == NULL) {
        dl_android = dlopen("libdl_android.so", RTLD_LOCAL | RTLD_LAZY);
    }
    if (dl_android == NULL) {
        printf("DriverHook: libdl_android.so missing: %s\n", dlerror());
        dlclose(driver_handle);
        dlclose(linkerhook);
        return false;
    }

    void* android_get_exported_namespace = dlsym(dl_android, "android_get_exported_namespace");
    void (*linkerhook_pass_handles)(void*, void*, void*) =
            (void (*)(void*, void*, void*))dlsym(linkerhook, "app__pojav_linkerhook_pass_handles");

    if (linkerhook_pass_handles == NULL || android_get_exported_namespace == NULL) {
        printf("DriverHook: missing symbols pass_handles=%p exported_ns=%p\n",
               (void*)linkerhook_pass_handles, android_get_exported_namespace);
        dlclose(dl_android);
        dlclose(driver_handle);
        dlclose(linkerhook);
        return false;
    }
    linkerhook_pass_handles(driver_handle, (void*)android_dlopen_ext, android_get_exported_namespace);

    void* libvulkan = linker_ns_dlopen_unique(cache_dir, "libvulkan.so", "libmjlvlk.so", RTLD_LOCAL | RTLD_NOW);
    printf("DriverHook: %s unique mjlvlk ptr=%p\n", label, libvulkan);
    if (!libvulkan) {
        printf("DriverHook: unique open failed for %s — aborting custom driver (no half-hook)\n", label);
        dlclose(dl_android);
        dlclose(driver_handle);
        dlclose(linkerhook);
        return false;
    }
    strncpy(loaded_name, driver_soname, sizeof(loaded_name) - 1);
    driver_loaded = true;
    if (strstr(driver_soname, "panfrost"))
        setenv("FEAR_PANVK_OK", "1", 1);
    printf("DriverHook: %s ready (vulkan handle=%p, driver=%s)\n", label, libvulkan, driver_soname);
    return true;
}

bool load_turnip_vulkan() {
    return load_named_vulkan_driver("libvulkan_freedreno.so", "Turnip");
}

bool load_panvk_vulkan() {
    return load_named_vulkan_driver("libvulkan_panfrost.so", "FearRender/PanVK");
}
#endif

void* pojavexec_loadVulkanDriver() {
#ifdef ENABLE_TURNIP_LOADER
    if (android_get_device_api_level() >= 28) {
        const char* fear = getenv("FEAR_RENDERER");
        if (fear && (strcmp(fear, "fear_render") == 0 || strcmp(fear, "panvk_zink") == 0)) {
            if (load_panvk_vulkan()) {
                void* h = linker_ns_dlopen("libmjlvlk.so", RTLD_LOCAL);
                if (h) return h;
                h = linker_ns_dlopen("libvulkan.so", RTLD_LOCAL);
                if (h) return h;
            }
            printf("VulkanLoader: Fear Render / PanVK path failed — using system Vulkan for Zink\n");
        }
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
    (void)env; (void)clazz;
#ifdef ENABLE_TURNIP_LOADER
    const char* fear = getenv("FEAR_RENDERER");
    if (fear && (strcmp(fear, "fear_render") == 0 || strcmp(fear, "panvk_zink") == 0)) {
        if (load_panvk_vulkan()) {
            setenv("FEAR_PANVK_OK", "1", 1);
            printf("VulkanLoader: FEAR_PANVK_OK=1\n");
        } else {
            setenv("FEAR_PANVK_OK", "0", 1);
            printf("VulkanLoader: preload Fear Render PanVK failed — FEAR_PANVK_OK=0 (no OSMesa Zink hang)\n");
            void* sys = dlopen("libvulkan.so", RTLD_LAZY | RTLD_LOCAL);
            printf("VulkanLoader: system libvulkan.so = %p\n", sys);
        }
        return;
    }
    if (!turnip_enabled) return;
    if (!load_turnip_vulkan()) {
        printf("VulkanLoader: preload Turnip failed\n");
    }
#endif
}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_setUseTurnip(JNIEnv *env, jclass clazz, jboolean enable) {
    (void)env; (void)clazz;
    turnip_enabled = enable;
}
