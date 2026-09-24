//
// FearLauncher Vulkan loader
// PanVK path: libmjlvlk.so (vk_panfrost_shim.c) -> libvulkan_panfrost.so ICD
// Turnip path: linker namespace + libvulkan_freedreno.so (Adreno only)
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
    return dlopen(soname, flags);
}

/* Primary PanVK path: CMake-built libmjlvlk.so (vk_panfrost_shim.c).
 * Shim dlopens libvulkan_panfrost.so and exports vkGetInstanceProcAddr.
 * Zink uses this handle via VULKAN_PTR — never the system / Turnip loader. */
static void* load_panvk_mjlvlk_shim(void) {
    const char* native_dir = getenv("POJAV_NATIVEDIR");
    char path[PATH_MAX];
    if (native_dir && native_dir[0])
        snprintf(path, sizeof(path), "%s/libmjlvlk.so", native_dir);
    else
        snprintf(path, sizeof(path), "libmjlvlk.so");

    void* h = dlopen(path, RTLD_NOW | RTLD_LOCAL);
    if (!h) {
        printf("VulkanLoader: PanVK mjlvlk shim FAILED (%s): %s\n", path, dlerror());
        return NULL;
    }
    /* Touch symbols so Zink can dlsym them via VULKAN_PTR */
    void* gipa = dlsym(h, "vkGetInstanceProcAddr");
    void* gdpa = dlsym(h, "vkGetDeviceProcAddr");
    if (!gipa) {
        printf("VulkanLoader: mjlvlk missing vkGetInstanceProcAddr\n");
        dlclose(h);
        return NULL;
    }
    setenv("FEAR_PANVK_OK", "1", 1);
    printf("VulkanLoader: PanVK mjlvlk shim OK path=%s handle=%p gipa=%p gdpa=%p\n",
           path, h, gipa, gdpa);
    return h;
}

static bool load_named_vulkan_driver(const char* driver_soname, const char* label) {
    static char loaded_name[128];
    static bool driver_loaded = false;
    if (driver_loaded && strcmp(loaded_name, driver_soname) == 0) return true;

    const char* native_dir = getenv("POJAV_NATIVEDIR");
    const char* cache_dir = getenv("TMPDIR");
    if (!cache_dir || !cache_dir[0]) cache_dir = getenv("HOME");
    if (!cache_dir || !cache_dir[0]) cache_dir = "/data/local/tmp";

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
            printf("DriverHook: %s not present at %s\n", driver_soname, path);
            return false;
        }
        fclose(f);
    }

    if (!linker_ns_load(native_dir)) {
        printf("DriverHook: linker_ns_load failed for %s\n", label);
        return false;
    }

    void* linkerhook = linker_ns_dlopen("liblinkerhook.so", RTLD_LOCAL | RTLD_NOW);
    if (!linkerhook)
        linkerhook = dlopen_in_native_dir(native_dir, "liblinkerhook.so", RTLD_LOCAL | RTLD_NOW);
    if (!linkerhook) {
        printf("DriverHook: liblinkerhook.so missing for %s\n", label);
        return false;
    }

    void* driver_handle = linker_ns_dlopen(driver_soname, RTLD_LOCAL | RTLD_NOW);
    if (!driver_handle)
        driver_handle = dlopen_in_native_dir(native_dir, driver_soname, RTLD_LOCAL | RTLD_NOW);
    if (!driver_handle) {
        printf("DriverHook: Failed to load %s (%s): %s\n", label, driver_soname, dlerror());
        dlclose(linkerhook);
        return false;
    }

    void* dl_android = linker_ns_dlopen("libdl_android.so", RTLD_LOCAL | RTLD_LAZY);
    if (!dl_android) dl_android = dlopen("libdl_android.so", RTLD_LOCAL | RTLD_LAZY);
    if (!dl_android) {
        printf("DriverHook: libdl_android.so missing\n");
        dlclose(driver_handle);
        dlclose(linkerhook);
        return false;
    }

    void* android_get_exported_namespace = dlsym(dl_android, "android_get_exported_namespace");
    void (*linkerhook_pass_handles)(void*, void*, void*) =
            (void (*)(void*, void*, void*))dlsym(linkerhook, "app__pojav_linkerhook_pass_handles");

    if (!linkerhook_pass_handles || !android_get_exported_namespace) {
        printf("DriverHook: missing symbols for %s\n", label);
        dlclose(dl_android);
        dlclose(driver_handle);
        dlclose(linkerhook);
        return false;
    }
    linkerhook_pass_handles(driver_handle, (void*)android_dlopen_ext, android_get_exported_namespace);

    void* libvulkan = linker_ns_dlopen_unique(cache_dir, "libvulkan.so", "libmjlvlk_turnip.so", RTLD_LOCAL | RTLD_NOW);
    printf("DriverHook: %s unique ptr=%p\n", label, libvulkan);
    if (!libvulkan) {
        printf("DriverHook: unique open failed for %s — abort\n", label);
        dlclose(dl_android);
        dlclose(driver_handle);
        dlclose(linkerhook);
        return false;
    }

    strncpy(loaded_name, driver_soname, sizeof(loaded_name) - 1);
    driver_loaded = true;
    printf("DriverHook: %s ready (handle=%p, driver=%s)\n", label, libvulkan, driver_soname);
    return true;
}

bool load_turnip_vulkan() {
    return load_named_vulkan_driver("libvulkan_freedreno.so", "Turnip");
}
#endif

void* pojavexec_loadVulkanDriver() {
#ifdef ENABLE_TURNIP_LOADER
    if (android_get_device_api_level() >= 28) {
        const char* fear = getenv("FEAR_RENDERER");
        /* PanVK: ONLY mjlvlk shim — never Turnip, never bare system first */
        if (fear && (strcmp(fear, "panvk") == 0 || strcmp(fear, "panvk_zink") == 0)) {
            void* h = load_panvk_mjlvlk_shim();
            if (h) return h;
            setenv("FEAR_PANVK_OK", "0", 1);
            printf("VulkanLoader: PanVK mjlvlk missing — NOT using Turnip; system vulkan last resort\n");
            void* sys = dlopen("libvulkan.so", RTLD_LAZY | RTLD_LOCAL);
            printf("VulkanLoader: system vulkan fallback ptr=%p\n", sys);
            return sys;
        }
        /* Turnip only when explicitly enabled (Adreno) */
        if (turnip_enabled && load_turnip_vulkan()) {
            void* h = linker_ns_dlopen("libmjlvlk_turnip.so", RTLD_LOCAL);
            if (h) return h;
            h = linker_ns_dlopen("libmjlvlk.so", RTLD_LOCAL);
            if (h) return h;
        }
    }
#endif
    void* vulkan_ptr = dlopen("libvulkan.so", RTLD_LAZY | RTLD_LOCAL);
    printf("VulkanLoader: system vulkan ptr=%p\n", vulkan_ptr);
    return vulkan_ptr;
}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_preloadVulkan(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
#ifdef ENABLE_TURNIP_LOADER
    const char* fear = getenv("FEAR_RENDERER");
    if (fear && (strcmp(fear, "panvk") == 0 || strcmp(fear, "panvk_zink") == 0)) {
        void* h = load_panvk_mjlvlk_shim();
        if (h) {
            printf("VulkanLoader: PanVK preload OK (mjlvlk shim)\n");
            return;
        }
        setenv("FEAR_PANVK_OK", "0", 1);
        printf("VulkanLoader: PanVK preload FAILED (no libmjlvlk.so)\n");
        return;
    }
    if (!turnip_enabled) return;
    if (!load_turnip_vulkan())
        printf("VulkanLoader: Turnip preload failed\n");
#endif
}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_setUseTurnip(JNIEnv *env, jclass clazz, jboolean enable) {
    (void)env; (void)clazz;
    turnip_enabled = enable;
    printf("VulkanLoader: setUseTurnip(%d)\n", (int)enable);
}
