# MC19: PanVK ICD shim C source, part 2 (assembled into jni/vk_panfrost_shim.c).
# v4: paren-light - typedefs and spaced-out attribute parens keep the
# base64 free of dense paren runs that break byte-exact pushes.
SHIM_P2 = r'''/* wrapped so we can remember the instance (needed to bootstrap gdpa) */
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
    return g_id_gipa(instance, pName);
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
'''
