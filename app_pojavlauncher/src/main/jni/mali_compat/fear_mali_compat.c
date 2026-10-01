/*
 * VkLayer_fear_mali_compat
 * ============================================================================
 * A small Vulkan layer that lets Mesa's Zink (OpenGL-over-Vulkan) work on ARM
 * Mali GPUs, for the FearLauncher "Fear Render" (Zink) renderer.
 *
 * THE PROBLEM
 * -----------
 * Zink needs a handful of VkPhysicalDeviceFeatures to behave correctly. ARM's
 * Mali vendor Vulkan driver does not expose several of them, and when an app
 * tries to enable an unsupported feature the Mali driver can fail device
 * creation. Zink then prints:
 *
 *   "Some incorrect rendering might occur because the selected Vulkan device
 *    (Mali-G615) doesn't support base Zink requirements:
 *    feats.features.logicOp feats.features.fillModeNonSolid
 *    feats.features.shaderClipDistance"
 *
 * ...and Minecraft's world textures come out corrupted.
 *
 * WHAT THIS LAYER DOES
 * --------------------
 * It sits between Zink and the Mali driver and "lies" only about the features
 * the driver is genuinely missing:
 *
 *   - vkGetPhysicalDeviceFeatures(2): if the driver reports a target feature as
 *     VK_FALSE, we flip it to VK_TRUE so Zink's requirement check passes.
 *   - vkCreateDevice: before handing the create-info to the real driver, we
 *     clear exactly the bits we lied about, so the Mali driver never sees an
 *     unsupported feature.
 *
 * Because we only lie where the driver is missing the feature, this is safe on
 * GPUs (or newer drivers) that already support them - those are passed through
 * untouched.
 *
 * Target features: logicOp, fillModeNonSolid, shaderClipDistance, alphaToOne.
 * Configurable at runtime via the FEAR_MALI_COMPAT_FEATURES env var.
 *
 * BUILD: see CMakeLists.txt. Enable: see README.md.
 * License: MIT.
 * ============================================================================
 */

#include <vulkan/vulkan.h>

#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

#ifdef __ANDROID__
#include <android/log.h>
#define LOG_TAG "FearMaliCompat"
/* Log to logcat AND stderr, so the lines also land in the launcher's
 * latestlog.txt (which captures the JRE stdout/stderr). */
#define LOGI(...) do { __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__); \
    fprintf(stderr, "[FearMaliCompat] " __VA_ARGS__); fprintf(stderr, "\n"); fflush(stderr); } while (0)
#define LOGW(...) do { __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__); \
    fprintf(stderr, "[FearMaliCompat] " __VA_ARGS__); fprintf(stderr, "\n"); fflush(stderr); } while (0)
#define LOGE(...) do { __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__); \
    fprintf(stderr, "[FearMaliCompat] " __VA_ARGS__); fprintf(stderr, "\n"); fflush(stderr); } while (0)
#else
#define LOGI(...) do { fprintf(stderr, "[FearMaliCompat] " __VA_ARGS__); fprintf(stderr, "\n"); } while (0)
#define LOGW(...) LOGI(__VA_ARGS__)
#define LOGE(...) LOGI(__VA_ARGS__)
#endif

/* ------------------------------------------------------------------------- */
/* Minimal loader-layer plumbing.                                            */
/*                                                                           */
/* These are the structs/constants the Vulkan loader uses to chain layers.   */
/* They are copied here (from Vulkan-Loader's vk_layer.h) so this layer has  */
/* no build dependency on the loader sources.                                */
/* ------------------------------------------------------------------------- */

#define VK_STRUCTURE_TYPE_LOADER_INSTANCE_CREATE_INFO ((VkStructureType)47)
#define VK_STRUCTURE_TYPE_LOADER_DEVICE_CREATE_INFO   ((VkStructureType)48)

typedef enum VkLayerFunction {
    VK_LAYER_LINK_INFO = 0,
    VK_LOADER_DATA_CALLBACK = 1,
} VkLayerFunction;

typedef VkResult (VKAPI_PTR *PFN_vkSetInstanceLoaderData)(VkInstance instance, void *object);
typedef VkResult (VKAPI_PTR *PFN_vkSetDeviceLoaderData)(VkDevice device, void *object);
typedef PFN_vkVoidFunction (VKAPI_PTR *PFN_vkGetPhysicalDeviceProcAddr)(VkInstance instance, const char *pName);

typedef struct VkLayerInstanceLink {
    struct VkLayerInstanceLink *pNext;
    PFN_vkGetInstanceProcAddr pfnNextGetInstanceProcAddr;
    PFN_vkGetPhysicalDeviceProcAddr pfnNextGetPhysicalDeviceProcAddr;
} VkLayerInstanceLink;

typedef struct VkLayerDeviceLink {
    struct VkLayerDeviceLink *pNext;
    PFN_vkGetInstanceProcAddr pfnNextGetInstanceProcAddr;
    PFN_vkGetDeviceProcAddr pfnNextGetDeviceProcAddr;
} VkLayerDeviceLink;

typedef struct VkLayerInstanceCreateInfo {
    VkStructureType sType;
    const void *pNext;
    VkLayerFunction function;
    union {
        VkLayerInstanceLink *pLayerInfo;
        PFN_vkSetInstanceLoaderData pfnSetInstanceLoaderData;
        PFN_vkSetDeviceLoaderData pfnSetDeviceLoaderData;
    } u;
} VkLayerInstanceCreateInfo;

typedef struct VkLayerDeviceCreateInfo {
    VkStructureType sType;
    const void *pNext;
    VkLayerFunction function;
    union {
        VkLayerDeviceLink *pLayerInfo;
        PFN_vkSetDeviceLoaderData pfnSetDeviceLoaderData;
    } u;
} VkLayerDeviceCreateInfo;

/* ------------------------------------------------------------------------- */
/* Target features                                                           */
/* ------------------------------------------------------------------------- */

#define FF_LOGIC_OP       (1u << 0)
#define FF_FILL_NON_SOLID (1u << 1)
#define FF_CLIP_DISTANCE  (1u << 2)
#define FF_ALPHA_TO_ONE   (1u << 3)
/* not a base VkPhysicalDeviceFeatures bit: lives in
 * VkPhysicalDeviceRobustness2FeaturesEXT.nullDescriptor */
#define FF_NULL_DESC      (1u << 4)

#define FF_ALL (FF_LOGIC_OP | FF_FILL_NON_SOLID | FF_CLIP_DISTANCE | FF_ALPHA_TO_ONE | FF_NULL_DESC)

/* Which features we are allowed to lie about (from env). Default: the two
 * things the Mali vendor driver is missing and Zink needs. */
static uint32_t g_enabled_mask = FF_FILL_NON_SOLID | FF_NULL_DESC;

static const char *bit_name(unsigned bit)
{
    switch (bit) {
    case FF_LOGIC_OP:       return "logicOp";
    case FF_FILL_NON_SOLID: return "fillModeNonSolid";
    case FF_CLIP_DISTANCE:  return "shaderClipDistance";
    case FF_ALPHA_TO_ONE:   return "alphaToOne";
    case FF_NULL_DESC:      return "nullDescriptor";
    default:                return "?";
    }
}

static VkBool32 *feature_ptr(VkPhysicalDeviceFeatures *f, unsigned bit)
{
    switch (bit) {
    case FF_LOGIC_OP:       return &f->logicOp;
    case FF_FILL_NON_SOLID: return &f->fillModeNonSolid;
    case FF_CLIP_DISTANCE:  return &f->shaderClipDistance;
    case FF_ALPHA_TO_ONE:   return &f->alphaToOne;
    default:                return NULL;
    }
}

static VkPhysicalDeviceRobustness2FeaturesEXT *
find_rb2_features(void *pNext)
{
    for (VkBaseOutStructure *s = (VkBaseOutStructure *)pNext; s; s = s->pNext)
        if (s->sType == VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_ROBUSTNESS_2_FEATURES_EXT)
            return (VkPhysicalDeviceRobustness2FeaturesEXT *)s;
    return NULL;
}

static void parse_features_env(void)
{
    const char *v = getenv("FEAR_MALI_COMPAT_FEATURES");
    uint32_t mask = 0;
    if (!v) return; /* keep default */

    if (strstr(v, "none") || strstr(v, "off")) {
        g_enabled_mask = 0;
        LOGI("feature override: none");
        return;
    }
    if (strstr(v, "logicOp") || strstr(v, "logicop"))       mask |= FF_LOGIC_OP;
    if (strstr(v, "fillModeNonSolid") || strstr(v, "fill")) mask |= FF_FILL_NON_SOLID;
    if (strstr(v, "shaderClipDistance") || strstr(v, "clip")) mask |= FF_CLIP_DISTANCE;
    if (strstr(v, "alphaToOne") || strstr(v, "alpha"))      mask |= FF_ALPHA_TO_ONE;
    if (strstr(v, "nullDescriptor") || strstr(v, "null"))   mask |= FF_NULL_DESC;
    g_enabled_mask = mask;
    LOGI("feature override from env: mask=0x%x", mask);
}

/* ------------------------------------------------------------------------- */
/* Per-object bookkeeping                                                    */
/* ------------------------------------------------------------------------- */

typedef struct FearInstance {
    VkInstance instance;
    PFN_vkDestroyInstance DestroyInstance;
    struct FearInstance *next;
} FearInstance;

typedef struct FearDevice {
    VkDevice device;
    PFN_vkGetDeviceProcAddr next_gdpa;
    PFN_vkDestroyDevice DestroyDevice;
    struct FearDevice *next;
} FearDevice;

/* One per physical device; remembers which features we lied about. */
typedef struct FearPhysDev {
    VkPhysicalDevice pd;
    FearInstance *inst;
    uint32_t lied_mask;
    struct FearPhysDev *next;
} FearPhysDev;

static PFN_vkGetInstanceProcAddr g_next_gipa = NULL;
static PFN_vkGetDeviceProcAddr   g_next_gdpa = NULL;   /* fallback device dispatch */
static VkInstance                g_last_instance = VK_NULL_HANDLE;

static FearInstance *g_instances = NULL;
static FearDevice   *g_devices   = NULL;
static FearPhysDev  *g_physdevs  = NULL;

static FearInstance *find_instance(VkInstance instance)
{
    for (FearInstance *i = g_instances; i; i = i->next)
        if (i->instance == instance) return i;
    return NULL;
}

static FearDevice *find_device(VkDevice device)
{
    for (FearDevice *d = g_devices; d; d = d->next)
        if (d->device == device) return d;
    return NULL;
}

static FearPhysDev *find_physdev(VkPhysicalDevice pd)
{
    for (FearPhysDev *p = g_physdevs; p; p = p->next)
        if (p->pd == pd) return p;
    return NULL;
}

static FearPhysDev *add_physdev(VkPhysicalDevice pd, FearInstance *inst)
{
    FearPhysDev *p = find_physdev(pd);
    if (p) { p->inst = inst; return p; }
    p = (FearPhysDev *)calloc(1, sizeof(*p));
    if (!p) return NULL;
    p->pd = pd;
    p->inst = inst;
    p->next = g_physdevs;
    g_physdevs = p;
    return p;
}

/* ------------------------------------------------------------------------- */
/* Forward declarations of our wrappers                                      */
/* ------------------------------------------------------------------------- */

VKAPI_ATTR VkResult VKAPI_CALL fear_CreateInstance(
    const VkInstanceCreateInfo *pCreateInfo,
    const VkAllocationCallbacks *pAllocator,
    VkInstance *pInstance);

VKAPI_ATTR void VKAPI_CALL fear_DestroyInstance(
    VkInstance instance, const VkAllocationCallbacks *pAllocator);

VKAPI_ATTR VkResult VKAPI_CALL fear_EnumeratePhysicalDevices(
    VkInstance instance, uint32_t *pPhysicalDeviceCount,
    VkPhysicalDevice *pPhysicalDevices);

VKAPI_ATTR void VKAPI_CALL fear_GetPhysicalDeviceFeatures(
    VkPhysicalDevice physicalDevice, VkPhysicalDeviceFeatures *pFeatures);

VKAPI_ATTR void VKAPI_CALL fear_GetPhysicalDeviceFeatures2(
    VkPhysicalDevice physicalDevice, VkPhysicalDeviceFeatures2 *pFeatures);

VKAPI_ATTR VkResult VKAPI_CALL fear_CreateDevice(
    VkPhysicalDevice physicalDevice, const VkDeviceCreateInfo *pCreateInfo,
    const VkAllocationCallbacks *pAllocator, VkDevice *pDevice);

VKAPI_ATTR void VKAPI_CALL fear_DestroyDevice(
    VkDevice device, const VkAllocationCallbacks *pAllocator);

VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL fear_GetInstanceProcAddr(
    VkInstance instance, const char *pName);

VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL fear_GetDeviceProcAddr(
    VkDevice device, const char *pName);

/* ------------------------------------------------------------------------- */
/* Feature patching                                                          */
/* ------------------------------------------------------------------------- */

/* Flip missing target features to TRUE in an outgoing feature struct, and
 * record what we lied about on the physical device. */
static void patch_features_up(FearPhysDev *pdev, VkPhysicalDeviceFeatures *f)
{
    if (!f || !pdev) return;
    for (unsigned bit = 0; bit < 4; bit++) {
        unsigned m = 1u << bit;
        if (!(g_enabled_mask & m)) continue;
        VkBool32 *fp = feature_ptr(f, m);
        if (fp && *fp == VK_FALSE) {
            *fp = VK_TRUE;
            pdev->lied_mask |= m;
            LOGI("reporting %s = VK_TRUE (driver lacks it)", bit_name(m));
        }
    }
}

/* Clear the features we lied about from an incoming (to-driver) feature
 * struct, so the real driver never receives an unsupported bit. */
static void patch_features_down(FearPhysDev *pdev, VkPhysicalDeviceFeatures *f)
{
    if (!f || !pdev) return;
    for (unsigned bit = 0; bit < 4; bit++) {
        unsigned m = 1u << bit;
        if (!(pdev->lied_mask & m)) continue;
        VkBool32 *fp = feature_ptr(f, m);
        if (fp && *fp == VK_TRUE) {
            *fp = VK_FALSE;
            LOGI("stripping %s before driver create", bit_name(m));
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Instance-level entry points                                               */
/* ------------------------------------------------------------------------- */

VKAPI_ATTR VkResult VKAPI_CALL fear_CreateInstance(
    const VkInstanceCreateInfo *pCreateInfo,
    const VkAllocationCallbacks *pAllocator,
    VkInstance *pInstance)
{
    LOGI("fear_mali_compat layer loaded (v2), creating instance");
    parse_features_env();

    /* Find the loader link so we can reach the next layer / driver. */
    VkLayerInstanceCreateInfo *chain =
        (VkLayerInstanceCreateInfo *)pCreateInfo->pNext;
    while (chain && !(chain->sType == VK_STRUCTURE_TYPE_LOADER_INSTANCE_CREATE_INFO &&
                      chain->function == VK_LAYER_LINK_INFO)) {
        chain = (VkLayerInstanceCreateInfo *)chain->pNext;
    }
    if (chain && chain->u.pLayerInfo) {
        g_next_gipa = chain->u.pLayerInfo->pfnNextGetInstanceProcAddr;
        /* advance the chain so the next layer sees the following link */
        chain->u.pLayerInfo = chain->u.pLayerInfo->pNext;
    }
    if (!g_next_gipa) {
        LOGE("could not find next vkGetInstanceProcAddr - layer misconfigured");
        return VK_ERROR_INITIALIZATION_FAILED;
    }

    PFN_vkCreateInstance fpCreate =
        (PFN_vkCreateInstance)g_next_gipa(NULL, "vkCreateInstance");
    if (!fpCreate) return VK_ERROR_INITIALIZATION_FAILED;

    VkResult res = fpCreate(pCreateInfo, pAllocator, pInstance);
    if (res != VK_SUCCESS) return res;

    FearInstance *inst = (FearInstance *)calloc(1, sizeof(*inst));
    if (!inst) return VK_ERROR_OUT_OF_HOST_MEMORY;
    inst->instance = *pInstance;
    inst->DestroyInstance =
        (PFN_vkDestroyInstance)g_next_gipa(*pInstance, "vkDestroyInstance");
    inst->next = g_instances;
    g_instances = inst;
    g_last_instance = *pInstance;

    LOGI("instance created, compat layer active (feature mask 0x%x)", g_enabled_mask);
    return VK_SUCCESS;
}

VKAPI_ATTR void VKAPI_CALL fear_DestroyInstance(
    VkInstance instance, const VkAllocationCallbacks *pAllocator)
{
    FearInstance *inst = find_instance(instance);

    /* drop physical-device records for this instance */
    FearPhysDev **pp = &g_physdevs;
    while (*pp) {
        if ((*pp)->inst && (*pp)->inst->instance == instance) {
            FearPhysDev *dead = *pp;
            *pp = dead->next;
            free(dead);
        } else {
            pp = &(*pp)->next;
        }
    }

    if (inst) {
        if (inst->DestroyInstance) inst->DestroyInstance(instance, pAllocator);
        FearInstance **ip = &g_instances;
        while (*ip) {
            if (*ip == inst) { *ip = inst->next; break; }
            ip = &(*ip)->next;
        }
        free(inst);
    }
}

VKAPI_ATTR VkResult VKAPI_CALL fear_EnumeratePhysicalDevices(
    VkInstance instance, uint32_t *pPhysicalDeviceCount,
    VkPhysicalDevice *pPhysicalDevices)
{
    PFN_vkEnumeratePhysicalDevices fp =
        (PFN_vkEnumeratePhysicalDevices)g_next_gipa(instance, "vkEnumeratePhysicalDevices");
    if (!fp) return VK_ERROR_INITIALIZATION_FAILED;

    VkResult res = fp(instance, pPhysicalDeviceCount, pPhysicalDevices);
    if (res == VK_SUCCESS && pPhysicalDevices) {
        FearInstance *inst = find_instance(instance);
        for (uint32_t i = 0; i < *pPhysicalDeviceCount; i++)
            add_physdev(pPhysicalDevices[i], inst);
    }
    return res;
}

VKAPI_ATTR void VKAPI_CALL fear_GetPhysicalDeviceFeatures(
    VkPhysicalDevice physicalDevice, VkPhysicalDeviceFeatures *pFeatures)
{
    FearPhysDev *pdev = find_physdev(physicalDevice);
    FearInstance *inst = pdev ? pdev->inst : NULL;
    if (!inst) return; /* not one of ours; shouldn't happen */

    PFN_vkGetPhysicalDeviceFeatures fp =
        (PFN_vkGetPhysicalDeviceFeatures)g_next_gipa(inst->instance,
                                                     "vkGetPhysicalDeviceFeatures");
    if (!fp) return;
    fp(physicalDevice, pFeatures);
    patch_features_up(pdev, pFeatures);
}

VKAPI_ATTR void VKAPI_CALL fear_GetPhysicalDeviceFeatures2(
    VkPhysicalDevice physicalDevice, VkPhysicalDeviceFeatures2 *pFeatures)
{
    FearPhysDev *pdev = find_physdev(physicalDevice);
    FearInstance *inst = pdev ? pdev->inst : NULL;
    if (!inst) return;

    /* vkGetPhysicalDeviceFeatures2 lives in the instance dispatch table. */
    PFN_vkGetPhysicalDeviceFeatures2 fp =
        (PFN_vkGetPhysicalDeviceFeatures2)g_next_gipa(inst->instance,
                                                      "vkGetPhysicalDeviceFeatures2");
    if (!fp) {
        fp = (PFN_vkGetPhysicalDeviceFeatures2)g_next_gipa(inst->instance,
                                                           "vkGetPhysicalDeviceFeatures2KHR");
    }
    if (!fp) return;
    fp(physicalDevice, pFeatures);

    /* Zink queries features2; patch the base feature struct it points at. */
    patch_features_up(pdev, &pFeatures->features);

    /* Zink also hard-requires robustness2 nullDescriptor. If the driver lacks
     * it, report it as supported so Zink's screen init passes; we strip it
     * again in vkCreateDevice. */
    if (g_enabled_mask & FF_NULL_DESC) {
        VkPhysicalDeviceRobustness2FeaturesEXT *rb2 = find_rb2_features(pFeatures->pNext);
        if (rb2 && rb2->nullDescriptor == VK_FALSE) {
            rb2->nullDescriptor = VK_TRUE;
            if (pdev) pdev->lied_mask |= FF_NULL_DESC;
            LOGI("reporting robustness2 nullDescriptor = VK_TRUE (driver lacks it)");
        }
    }
}

VKAPI_ATTR VkResult VKAPI_CALL fear_CreateDevice(
    VkPhysicalDevice physicalDevice, const VkDeviceCreateInfo *pCreateInfo,
    const VkAllocationCallbacks *pAllocator, VkDevice *pDevice)
{
    FearPhysDev *pdev = find_physdev(physicalDevice);
    FearInstance *inst = pdev ? pdev->inst : NULL;
    VkInstance instance_handle = inst ? inst->instance : g_last_instance;
    if (!instance_handle)
        LOGE("create device: no instance handle, passing through");

    PFN_vkGetDeviceProcAddr next_gdpa = NULL;

    /* Find the device link to reach the next layer / driver's device table. */
    VkLayerDeviceCreateInfo *chain =
        (VkLayerDeviceCreateInfo *)pCreateInfo->pNext;
    while (chain && !(chain->sType == VK_STRUCTURE_TYPE_LOADER_DEVICE_CREATE_INFO &&
                      chain->function == VK_LAYER_LINK_INFO)) {
        chain = (VkLayerDeviceCreateInfo *)chain->pNext;
    }
    if (chain && chain->u.pLayerInfo) {
        next_gdpa = chain->u.pLayerInfo->pfnNextGetDeviceProcAddr;
        chain->u.pLayerInfo = chain->u.pLayerInfo->pNext;
    }
    /* Fall back to asking the next layer/driver for its device dispatch. */
    if (!next_gdpa && g_next_gipa && instance_handle)
        next_gdpa = (PFN_vkGetDeviceProcAddr)g_next_gipa(instance_handle, "vkGetDeviceProcAddr");
    if (next_gdpa) g_next_gdpa = next_gdpa;
    if (!next_gdpa)
        LOGE("create device: no device dispatch found");

    /* Build a modified create-info with the lied-about features stripped. */
    VkDeviceCreateInfo local = *pCreateInfo;
    VkPhysicalDeviceFeatures stripped;
    VkPhysicalDeviceFeatures2 *feat2 = NULL;

    if (pCreateInfo->pEnabledFeatures) {
        stripped = *pCreateInfo->pEnabledFeatures;
        patch_features_down(pdev, &stripped);
        local.pEnabledFeatures = &stripped;
    }

    /* Walk pNext for a VkPhysicalDeviceFeatures2 and strip in place (a copy). */
    VkBaseOutStructure *prev = NULL;
    for (VkBaseOutStructure *s = (VkBaseOutStructure *)local.pNext; s; s = s->pNext) {
        if (s->sType == VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2) {
            feat2 = (VkPhysicalDeviceFeatures2 *)s;
            break;
        }
        prev = s;
    }
    (void)prev;
    VkPhysicalDeviceFeatures2 feat2_copy;
    if (feat2) {
        feat2_copy = *feat2;
        patch_features_down(pdev, &feat2_copy.features);
        /* replace the node in the (local) chain with our copy */
        feat2_copy.pNext = feat2->pNext;
        feat2_copy.sType = feat2->sType;
        /* Relink: easiest is to rebuild the chain up to this node. */
        VkDeviceCreateInfo relinked = local;
        /* Rebuild pNext chain with feat2_copy substituted. */
        /* Simple approach: if feat2 is the head of pNext, swap directly. */
        if ((void *)feat2 == local.pNext) {
            relinked.pNext = &feat2_copy;
        } else {
            /* patch the predecessor's pNext to point at our copy */
            VkBaseOutStructure *walk = (VkBaseOutStructure *)relinked.pNext;
            while (walk && walk->pNext != (VkBaseOutStructure *)feat2)
                walk = walk->pNext;
            if (walk) walk->pNext = (VkBaseOutStructure *)&feat2_copy;
        }
        local = relinked;
    }

    PFN_vkCreateDevice fpCreate = (g_next_gipa && instance_handle)
        ? (PFN_vkCreateDevice)g_next_gipa(instance_handle, "vkCreateDevice") : NULL;
    if (!fpCreate) return VK_ERROR_INITIALIZATION_FAILED;

    /* If we lied about robustness2 nullDescriptor, strip it (temporarily) so
     * the Mali driver never sees a feature it does not support. */
    VkPhysicalDeviceRobustness2FeaturesEXT *rb2 =
        (pdev && (pdev->lied_mask & FF_NULL_DESC)) ? find_rb2_features((void *)local.pNext) : NULL;
    VkBool32 rb2_saved = VK_FALSE;
    if (rb2) {
        rb2_saved = rb2->nullDescriptor;
        rb2->nullDescriptor = VK_FALSE;
        LOGI("stripping robustness2 nullDescriptor before driver create");
    }

    VkResult res = fpCreate(physicalDevice, &local, pAllocator, pDevice);

    if (rb2) rb2->nullDescriptor = rb2_saved;
    if (res != VK_SUCCESS) return res;

    FearDevice *dev = (FearDevice *)calloc(1, sizeof(*dev));
    if (!dev) return VK_ERROR_OUT_OF_HOST_MEMORY;
    dev->device = *pDevice;
    dev->next_gdpa = next_gdpa;
    dev->DestroyDevice =
        (PFN_vkDestroyDevice)(next_gdpa ? next_gdpa(*pDevice, "vkDestroyDevice") : NULL);
    dev->next = g_devices;
    g_devices = dev;

    LOGI("device created (next_gdpa=%p, lied feature mask 0x%x)",
         (void *)next_gdpa, pdev ? pdev->lied_mask : 0);
    return VK_SUCCESS;
}

VKAPI_ATTR void VKAPI_CALL fear_DestroyDevice(
    VkDevice device, const VkAllocationCallbacks *pAllocator)
{
    FearDevice *dev = find_device(device);
    if (dev) {
        if (dev->DestroyDevice) dev->DestroyDevice(device, pAllocator);
        FearDevice **dp = &g_devices;
        while (*dp) {
            if (*dp == dev) { *dp = dev->next; break; }
            dp = &(*dp)->next;
        }
        free(dev);
    }
}

/* ------------------------------------------------------------------------- */
/* Dispatch                                                                  */
/* ------------------------------------------------------------------------- */

VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL fear_GetInstanceProcAddr(
    VkInstance instance, const char *pName)
{
    if (!pName) return NULL;

    if (!strcmp(pName, "vkCreateInstance"))            return (PFN_vkVoidFunction)fear_CreateInstance;
    if (!strcmp(pName, "vkGetInstanceProcAddr"))       return (PFN_vkVoidFunction)fear_GetInstanceProcAddr;
    if (!strcmp(pName, "vkDestroyInstance"))           return (PFN_vkVoidFunction)fear_DestroyInstance;
    if (!strcmp(pName, "vkEnumeratePhysicalDevices"))  return (PFN_vkVoidFunction)fear_EnumeratePhysicalDevices;
    if (!strcmp(pName, "vkGetPhysicalDeviceFeatures")) return (PFN_vkVoidFunction)fear_GetPhysicalDeviceFeatures;
    if (!strcmp(pName, "vkGetPhysicalDeviceFeatures2"))    return (PFN_vkVoidFunction)fear_GetPhysicalDeviceFeatures2;
    if (!strcmp(pName, "vkGetPhysicalDeviceFeatures2KHR")) return (PFN_vkVoidFunction)fear_GetPhysicalDeviceFeatures2;
    if (!strcmp(pName, "vkCreateDevice"))              return (PFN_vkVoidFunction)fear_CreateDevice;
    if (!strcmp(pName, "vkDestroyDevice"))             return (PFN_vkVoidFunction)fear_DestroyDevice;
    if (!strcmp(pName, "vkGetDeviceProcAddr"))         return (PFN_vkVoidFunction)fear_GetDeviceProcAddr;

    if (!g_next_gipa) return NULL;
    return g_next_gipa(instance, pName);
}

VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL fear_GetDeviceProcAddr(
    VkDevice device, const char *pName)
{
    if (!pName) return NULL;

    if (!strcmp(pName, "vkGetDeviceProcAddr")) return (PFN_vkVoidFunction)fear_GetDeviceProcAddr;
    if (!strcmp(pName, "vkDestroyDevice"))     return (PFN_vkVoidFunction)fear_DestroyDevice;

    FearDevice *dev = find_device(device);
    PFN_vkGetDeviceProcAddr gdpa = (dev && dev->next_gdpa) ? dev->next_gdpa : g_next_gdpa;
    if (gdpa) return gdpa(device, pName);
    LOGE("vkGetDeviceProcAddr(%s): no device dispatch, returning NULL", pName);
    return NULL;
}

/* ------------------------------------------------------------------------- */
/* Exported entry points                                                     */
/* ------------------------------------------------------------------------- */

/* The loader looks for vkNegotiateLoaderLayerInterfaceVersion first; if a
 * layer does not export it, the loader falls back to the classic
 * vkGetInstanceProcAddr / vkGetDeviceProcAddr entry points, which is what we
 * provide. */

#define VK_LAYER_EXPORT __attribute__((visibility("default")))

VK_LAYER_EXPORT PFN_vkVoidFunction VKAPI_CALL vkGetInstanceProcAddr(
    VkInstance instance, const char *pName)
{
    return fear_GetInstanceProcAddr(instance, pName);
}

VK_LAYER_EXPORT PFN_vkVoidFunction VKAPI_CALL vkGetDeviceProcAddr(
    VkDevice device, const char *pName)
{
    return fear_GetDeviceProcAddr(device, pName);
}
