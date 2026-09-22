// MC19: libmjlvlk.so - flat Vulkan dispatch shim for the PanVK ICD.
//
// Zink (libOSMesa_8.so, Vera-Firefly patch) reads the VULKAN_PTR env var and
// dlsym()s exactly two symbols on that handle: vkGetInstanceProcAddr and
// vkGetDeviceProcAddr. PanVK is a Vulkan *ICD* (it exports vk_icd* entry
// points, not the loader API), so this shim bridges the two: it dlopens
// libvulkan_panfrost.so from POJAV_NATIVEDIR, resolves vk_icdGetInstanceProcAddr,
// negotiates the ICD interface version and forwards everything else.
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef void* (*mjlvlk_gipa_fn)(void*, const char*);
typedef int (*mjlvlk_create_fn)(const void*, const void*, void**);
typedef long (*mjlvlk_negotiate_fn)(long*);

static void* g_icd = NULL;
static mjlvlk_gipa_fn g_icd_gipa = NULL;
static mjlvlk_gipa_fn g_icd_gdpa = NULL;
static void* g_last_instance = NULL;

static int shim_init(void) {
    if (g_icd_gipa)
        return 1;
    const char* nd = getenv("POJAV_NATIVEDIR");
    char path[512];
    if (nd && nd[0])
        snprintf(path, sizeof(path), "%s/libvulkan_panfrost.so", nd);
    else
        snprintf(path, sizeof(path), "libvulkan_panfrost.so");
    g_icd = dlopen(path, RTLD_NOW | RTLD_LOCAL);
    if (!g_icd) {
        printf("mjlvlk: cannot open %s: %s\n", path, dlerror());
        return 0;
    }
    g_icd_gipa = (mjlvlk_gipa_fn) dlsym(g_icd, "vk_icdGetInstanceProcAddr");
    if (!g_icd_gipa) {
        printf("mjlvlk: %s has no vk_icdGetInstanceProcAddr export\n", path);
        return 0;
    }
    mjlvlk_negotiate_fn negotiate = (mjlvlk_negotiate_fn) dlsym(g_icd, "vk_icdNegotiateLoaderICDInterfaceVersion");
    if (negotiate) {
        long v = 7;
        negotiate(&v);
    }
    printf("mjlvlk: PanVK ICD loaded from %s (gipa=%p)\n", path, g_icd_gipa);
    return 1;
}

static void resolve_gdpa(void) {
    if (g_icd_gdpa)
        return;
    if (g_last_instance)
        g_icd_gdpa = (mjlvlk_gipa_fn) g_icd_gipa(g_last_instance, "vkGetDeviceProcAddr");
    if (!g_icd_gdpa && g_icd)
        g_icd_gdpa = (mjlvlk_gipa_fn) dlsym(g_icd, "vkGetDeviceProcAddr");
}

/* wrapped so we can remember the instance (needed to bootstrap gdpa) */
static int wrapped_vkCreateInstance(const void* ci, const void* ac, void** out) {
    if (!shim_init())
        return -13; /* VK_ERROR_INITIALIZATION_FAILED */
    mjlvlk_create_fn real = (mjlvlk_create_fn) g_icd_gipa(NULL, "vkCreateInstance");
    if (!real)
        return -13;
    int r = real(ci, ac, out);
    if (r == 0 && out && *out) {
        g_last_instance = *out;
        resolve_gdpa();
    }
    return r;
}

__attribute__ ((visibility ("default")))
void* vkGetInstanceProcAddr(void* instance, const char* pName) {
    if (!pName)
        return NULL;
    if (strcmp(pName, "vkGetInstanceProcAddr") == 0)
        return &vkGetInstanceProcAddr;
    if (strcmp(pName, "vkGetDeviceProcAddr") == 0)
        return &vkGetDeviceProcAddr;
    if (!shim_init())
        return NULL;
    if (strcmp(pName, "vkCreateInstance") == 0)
        return &wrapped_vkCreateInstance;
    return g_icd_gipa(instance, pName);
}

__attribute__ ((visibility ("default")))
void* vkGetDeviceProcAddr(void* device, const char* pName) {
    if (!g_icd_gdpa) {
        if (!shim_init())
            return NULL;
        resolve_gdpa();
        if (!g_icd_gdpa)
            return NULL;
    }
    return g_icd_gdpa(device, pName);
}
