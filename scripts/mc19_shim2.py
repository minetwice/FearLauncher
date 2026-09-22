# MC19: PanVK ICD shim C source, part 2 (assembled into jni/vk_panfrost_shim.c).
SHIM_P2 = r'''/* wrapped so we can remember the instance (needed to bootstrap gdpa) */
static int wrapped_vkCreateInstance(const void* ci, const void* ac, void** out) {
    if (!shim_init())
        return -13; /* VK_ERROR_INITIALIZATION_FAILED */
    int (*real)(const void*, const void*, void**) =
        (int (*)(const void*, const void*, void**))g_icd_gipa(NULL, "vkCreateInstance");
    if (!real)
        return -13;
    int r = real(ci, ac, out);
    if (r == 0 && out && *out) {
        g_last_instance = *out;
        resolve_gdpa();
    }
    return r;
}

__attribute__((visibility("default"))
void* vkGetInstanceProcAddr(void* instance, const char* pName) {
    if (!pName)
        return NULL;
    if (strcmp(pName, "vkGetInstanceProcAddr") == 0)
        return (void*)&vkGetInstanceProcAddr;
    if (strcmp(pName, "vkGetDeviceProcAddr") == 0)
        return (void*)&vkGetDeviceProcAddr;
    if (!shim_init())
        return NULL;
    if (strcmp(pName, "vkCreateInstance") == 0)
        return (void*)&wrapped_vkCreateInstance;
    return g_icd_gipa(instance, pName);
}

__attribute__((visibility("default"))
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
'''
