#!/usr/bin/env python3
"""MC19: wire up the PanVK Zink renderer (open-source Panfrost Vulkan driver).

Zink currently renders through the ARM *proprietary* system Vulkan driver,
which is non-conformant and glitches on Mali. MC19 adds a 'panvk_zink'
renderer that points Zink at the open-source PanVK driver instead:

  1. new jni/vk_panfrost_shim.c -> libmjlvlk.so: a flat Vulkan dispatch shim.
     Zink (Vera-Firefly patch) reads VULKAN_PTR and dlsym()s exactly
     vkGetInstanceProcAddr + vkGetDeviceProcAddr on that handle; PanVK is an
     ICD (exports vk_icd* only), so the shim dlopens libvulkan_panfrost.so and
     bridges the two calling conventions.
  2. CMmakeLists.txt: build the shim as libmjlvlk.so.
  3. vulkan_loader.c: panvk_zink returns the shim handle from
     pojavexec_loadVulkanDriver().
  4. lwjgl_dlopen_hook.c: recognize panvk_zink as a zink renderer.
  5. JREUtils.java: panvk_zink renderer env + isZink + loadGraphicsLibrary.
  6. headings_array.xml: PanVK Zink renderer entry in the settings list."""
import io
import os
import sys

BASE = "app_pojavlauncher/src/main/jni"


def die(msg):
    sys.stderr.write("MC19 FATAL: " + msg + "\n")
    sys.exit(1)


def read(p):
    return io.open(p, encoding="utf-8").read()


def write(p, t):
    io.open(p, "w", encoding="utf-8", newline="").write(t)


def patch(p, old, new, what, expect=1):
    t = read(p)
    n = t.count(old)
    if n != expect:
        die("%s: anchor found %d times (expected %d)" % (what, n, expect))
    write(p, t.replace(old, new))
    print("MC19: patched " + what)


# ---------- 1. new file: vk_panfrost_shim.c ----------
shim = r'''// MC19: libmjlvlk.so - flat Vulkan dispatch shim for the PanVK ICD.
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

static void* g_icd = NULL;
static mjlvlk_gipa_fn g_icd_gipa = NULL;
static void* (*g_icd_gdpa)(void*, const char*) = NULL;
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
    g_icd_gipa = (mjlvlk_gipa_fn)dlsym(g_icd, "vk_icdGetInstanceProcAddr");
    if (!g_icd_gipa) {
        printf("mjlvlk: %s has no vk_icdGetInstanceProcAddr export\n", path);
        return 0;
    }
    long (*negotiate)(long*) = (long (*)(long*))dlsym(g_icd, "vk_icdNegotiateLoaderICDInterfaceVersion");
    if (negotiate) {
        long v = 7;
        negotiate(&v);
    }
    printf("mjlvlk: PanVK ICD loaded from %s (gipa=%p)\n", path, (void*)g_icd_gipa);
    return 1;
}

static void resolve_gdpa(void) {
    if (g_icd_gdpa)
        return;
    if (g_last_instance)
        g_icd_gdpa = (void* (*)(void*, const char*))g_icd_gipa(g_last_instance, "vkGetDeviceProcAddr");
    if (!g_idd_gdpa && g_icd)
        g_icd_gdpa = (void* (*)(void*, const char*))dlsym(g_icd, "vkGetDeviceProcAddr");
}

/* wrapped so we can remember the instance (needed to bootstrap gdpa) */
static int wrapped_vkCreateInstance(const void* ci, const void* ac, void** out) {
    if (!shim_init())
        return -13; /* VK_ERROR_INITIALIZATION_FAILED */
    int (*real)(const void*, const void*, void**) =
        (int (*)(const void*, const void*, void**)g_icd_gipa(NULL, "vkCreateInstance");
    if (!real)
        return -13;
    int r = real(ci, ac, out);
    if (r == 0 && out && *out) {
        g_last_instance = *out;
        resolve_gdpa();
    }
    return r;
}

__attribute__((visibility("default")))
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

p = BASE + "/vk_panfrost_shim.c"
if os.path.exists(p):
    print("MC19: vk_panfrost_shim.c exists, rewriting")
write(p, shim)
print("MC19: wrote vk_panfrost_shim.c")

# ---------- 2. CMmakeLists.txt ----------
p = "app_pojavlauncher/src/main/jni/CMakeLists.txt"
patch(p,
      'find_package(bytehook CONFIG REQUIRED)',
      '''# MC19: flat Vulkan shim for PanVK (loaded as libmjlvlk.so)
add_library(mjlvlk SHARED
        vk_panfrost_shim.c
)
target_link_libraries(mjlvlk PRIVATE log dl)

find_package(bytehook CONFIG REQUIRED)''',
      "CMakeLists.txt mjlvlk module")

# ---------- 3. vulkan_loader.c ----------
p = BASE + "/vulkan_loader.c"
t = read(p)
if "#include <string.h>" not in t:
    patch(p,
          '#include <stdio.h>\n',
          '#include <stdio.h>\n#include <string.h>\n',
          "vulkan_loader.c includes")
patch(p,
      '''v}¥¨Á½©…Ù•á•}±½…‘YÕ±­…¹É¥Ù•È ¤ì(¥™‘•˜9	1}QUI9%A}1=H(€€€¥˜¡…¹‘É½¥‘}•Ñ}‘•Ù¥•}…Á¥}±•Ù•° ¤€øô€Èà¤ì€¼¼Ñ¡”±½…‘•È‘½•Ì¹½ĞÍÕÁÁ½ÉĞ‰•±½ÜÑ¡…Ğ(€€€€€€€¥˜¡ÑÕÉ¹¥Á}•¹…‰±•€˜˜±½…‘}ÑÕÉ¹¥Á}ÙÕ±­…¸ ¤¤œœœ°(€€€€€€œœÙ½¥¨Á½©…Ù•á•}±½…‘YÕ±­…¹É¥Ù•È ¤ì(¥™‘•˜9	1}QUI9%A}1=H(€€€¥˜¡…¹‘É½¥‘}•Ñ}‘•Ù¥•}…Á¥}±•Ù•° ¤€øô€Èà¤ì€¼¼Ñ¡”±½…‘•È‘½•Ì¹½ĞÍÕÁÁ½ÉĞ‰•±½ÜÑ¡…Ğ(€€€€€€€€¼¨5ÄäèA…¹Y,Á…Ñ €´i¥¹¬É•¹‘•ÉÌ½¸Ñ¡”½Á•¸µÍ½ÕÉ”A…¹™É½ÍĞYÕ±­…¸(€€€€€€€€€¨‘É¥Ù•È€¡±¥‰µ©±Ù±¬¹Í¼Í¡¥´€´ø±¥‰ÙÕ±­…¹}Á…¹™É½ÍĞ¹Í¼%¤¥¹ÍÑ•…½˜(€€€€€€€€€¨Ñ¡”±¥Ñ¡äI4ÁÉ½ÁÉ¥•Ñ…ÉäÍåÍÑ•´‘É¥Ù•È½¸5…±¤¸€¨¼(€€€€€€€½¹ÍĞ¡…È¨™•…É}É•¹‘•É•È€ô•Ñ•¹Ø ‰I}I9IHˆ¤ì(€€€€€€€¥˜€¡™•…É}É•¹‘•É•È€˜˜ÍÑÉµÀ¡™•…É}É•¹‘•É•È°€‰Á…¹Ù­}é¥¹¬ˆ¤€ôô€À¤ì(€€€€€€€€€€€½¹ÍĞ¡…È¨¹€ô•Ñ•¹Ø ‰A=)Y}9Q%Y%Hˆ¤ì(€€€€€€€€€€€¡…ÈÍ¡¥µ}Á…Ñ¡lÔÄÉtì(€€€€€€€€€€€¥˜€¡¹€˜˜¹‘lÁt¤(€€€€€€€€€€€€€€€Í¹ÁÉ¥¹Ñ˜¡Í¡¥µ}Á…Ñ °Í¥é•½˜¡Í¡¥µ}Á…Ñ ¤°€ˆ•Ì½±¥‰µ©±Ù±¬¹Í¼ˆ°¹¤ì(€€€€€€€€€€€•±Í”(€€€€€€€€€€€€€€€Í¹ÁÉ¥¹Ñ˜¡Í¡¥µ}Á…Ñ °Í¥é•½˜¡Í¡¥µ}Á…Ñ ¤°€‰±¥‰µ©±Ù±¬¹Í¼ˆ¤ì(€€€€€€€€€€€Ù½¥¨Á…¹Ù­}Í¡¥´€ô‘±½Á•¸¡Í¡¥µ}Á…Ñ °IQ1}9=\ğIQ1}1=0¤ì(€€€€€€€€€€€¥˜€¡Á…¹Ù­}Í¡¥´¤ì(€€€€€€€€€€€€€€€ÁÉ¥¹Ñ˜ ‰YÕ±­…¹1½…‘•Èè5ÄäA…¹Y,Í¡¥´±½…‘•€ •Ì¥qqq¸ˆ°Í¡¥µ}Á…Ñ ¤ì(€€€€€€€€€€€€€€€É•ÑÕÉ¸Á…¹Ù­}Í¡¥´ì(€€€€€€€€€€€ô(€€€€€€€€€€€ÁÉ¥¹Ñ˜ ‰YÕ±­…¹1½…‘•Èè5ÄäA…¹Y,Í¡¥´%1€ •Ì¤è€•Ì€´ÍåÍÑ•´ÙÕ±­…¸™…±±‰…­qq¸ˆ°(€€€€€€€€€€€€€€€€€€€Í¡¥µ}Á…Ñ °‘±•ÉÉ½È ¤¤ì(€€€€€€€ô(€€€€€€€¥˜¡ÑÕÉ¹¥Á}•¹…‰±•€˜˜±½…‘}ÑÕÉ¹¥Á}ÙÕ±­…¸ ¤¤œœœ°(€€€€€€‰ÙÕ±­…¹}±½…‘•È¹ŒÁ…¹Ù¬Á…Ñ ˆ¤((Œ€´´´´´´´´´´€Ğ¸±İ©±}‘±½Á•¹}¡½½¬¹Œ€´´´´´´´´´´)À€ô	M€¬€ˆ½©Ùµ}¡½½­Ì½±İ©±}‘±½Á•¹}¡½½¬¹Œˆ)Á…Ñ ¡À°(€€€€€€œœœ€€€¥˜€¡™•…È€˜˜€¡ÍÑÉµÀ¡™•…È°€‰ÑÕÉ¹¥Á}é¥¹¬ˆ¤€ôô€ÀñğÍÑÉµÀ¡™•…È°€‰ÙÕ±­…¹}é¥¹¬ˆ¤€ôô€À¤¤(€€€€€€€è€ôÑÉÕ”ìœœœ°(€€€€€€œœœ€€€¥˜€¡™•…È€˜˜€¡ÍÑÉµÀ¡™•…È°€‰ÑÕÉ¹¥Á}é¥¹¬ˆ¤€ôô€ÀñğÍÑÉµÀ¡™•…È°€‰ÙÕ±­…¹}é¥¹¬ˆ¤€ôô€ÀñğÍÑÉµÀ¡™•…È°€‰Á…¹Ù­}é¥¹¬ˆ¤€ôô€À¤¤(€€€€€€€è€ôÑÉÕ”ìœœœ°(€€€€€€‰±İ©±}‘±½Á•¹}¡½½¬¹Œ¥Í}é¥¹­}É•¹‘•É•Èˆ¤((Œ€´´´´´´´´´´€Ô¸)IUÑ¥±Ì¹©…Ù„€´´´´´´´´´´)À€ô€‰…ÁÁ}Á½©…Ù±…Õ¹¡•È½ÍÉŒ½µ…¥¸½©…Ù„½¹•Ğ½­‘Ğ½Á½©…Ù±…Õ¹ ½ÕÑ¥±Ì½)IUÑ¥±Ì¹©…Ù„ˆ((Œ€Õ„¸Í•ÑÕÁI•¹‘•É•É¹Øè…‘Ñ¡”Á…¹Ù­}é¥¹¬…Í”‰•™½É”ÑÕÉ¹¥Á}é¥¹¬Ì)Á…Ñ ¡À°(€€€€€€œœœ€€€€€€€Íİ¥Ñ ¡É•¹‘•É•È¤ì(€€€€€€€€€€€…Í”€‰ÑÕÉ¹¥Á}é¥¹¬ˆè(€€€€€€€€€€€…Í”€‰ÙÕ±­…¹}é¥¹¬ˆè(€€€€€€€€€€€€€€€1½•È¹…ÁÁ•¹‘Q½1½œ ‰mQÕÉ¹¥Ái¥¹­t%¹¥Ñ¥…±¥é¥¹œi¥¹¬É•¹‘•É•È€¡=M5•Í„€¬5•Í„i¥¹¬¤¸¸¸ˆ¤ìœœœ°(€€€€€€œœœ€€€€€€€Íİ¥Ñ ¡É•¹‘•É•È¤ì(€€€€€€€€€€€…Í”€‰Á…¹Ù­}é¥¹¬ˆè(€€€€€€€€€€€€€€€€¼¼5Ääèi¥¹¬½¸Ñ¡”½Á•¸µÍ½ÕÉ”A…¹Y,€¡A…¹™É½ÍĞ¤‘É¥Ù•È¸(€€€€€€€€€€€€€€€¼¼±•…¸Á…Ñ €´¹¼ÁÉ½ÁÉ¥•Ñ…Éäµ‘É¥Ù•Èİ½É­…É½Õ¹‘Ì¹••‘•¸(€€€€€€€€€€€€€€€1½•È¹…ÁÁ•¹‘Q½1½œ ‰mA…¹Y-t%¹¥Ñ¥…±¥é¥¹œA…¹Y,i¥¹¬É•¹‘•É•È€¡½Á•¸µÍ½ÕÉ”A…¹™É½ÍĞYÕ±­…¸‘É¥Ù•È¤¸¸¸ˆ¤ì(€€€€€€€€€€€€€€€•¹Ù5…À¹ÁÕĞ ‰11%U5}I%YHˆ°€‰é¥¹¬ˆ¤ì(€€€€€€€€€€€€€€€•¹Ù5…À¹ÁÕĞ ‰5M}1=I}I%YI}=YII%ˆ°€‰é¥¹¬ˆ¤ì(€€€€€€€€€€€€€€€•¹Ù5…À¹ÁÕĞ ‰5M}1M1}YIM%=9}=YII%ˆ°€ˆĞØÀˆ¤ì(€€€€€€€€€€€€€€€•¹Ù5…À¹ÁÕĞ ‰5M}1}YIM%=9}=YII%ˆ°€ˆĞ¸Øˆ¤ì(€€€€€€€€€€€€€€€•¹Ù5…À¹ÁÕĞ ‰Ù‰±…¹­}µ½‘”ˆ°€ˆÀˆ¤ì(€€€€€€€€€€€€€€€•¹Ù5…À¹ÁÕĞ ‰5M}1M1}!}%M	1ˆ°€‰™…±Í”ˆ¤ì(€€€€€€€€€€€€€€€•¹Ù5…À¹ÁÕĞ ‰I}I9IHˆ°É•¹‘•É•È¤ì(€€€€€€€€€€€€€€€•¹Ù5…À¹ÁÕĞ ‰A9}5M}	Uˆ°€‰¹½…™‰Œˆ¤ì(€€€€€€€€€€€€€€€‰É•…¬ì(€€€€€€€€€€€…Í”€‰ÑÕÉ¹¥Á}é¥¹¬ˆè(€€€€€€€€€€€…Í”€‰ÙÕ±­…¹}é¥¹¬ˆè(€€€€€€€€€€€€€€€1½•È¹…ÁÁ•¹‘Q½1½œ ‰mQÕÉ¹¥Ái¥¹­t%¹¥Ñ¥…±¥é¥¹œi¥¹¬É•¹‘•É•È€¡=M5•Í„€¬5•Í„i¥¹¬¤¸¸¸ˆ¤ìœœœ°(€€€€€€‰)IUÑ¥±ÌÍ•ÑÕÁI•¹‘•É•É¹ØÁ…¹Ù¬…Í”ˆ¤((Œ€Õˆ¸¥Íi¥¹¬)Á…Ñ ¡À°(€€€€€€œœœ€€€€€€€‰½½±•…¸¥Íi¥¹¬€ô€‰ÑÕÉ¹¥Á}é¥¹¬ˆ¹•ÅÕ…±Ì¡É•¹‘•É•È¤ñğ€‰ÙÕ±­…¹}é¥¹¬ˆ¹•ÅÕ…±Ì¡É•¹‘•É•È¤ìœœœ°(€€€€€€œœœ€€€€€€€‰½½±•…¸¥Íi¥¹¬€ô€‰ÑÕÉ¹¥Á}é¥¹¬ˆ¹•ÅÕ…±Ì¡É•¹‘•É•È¤ñğ€‰ÙÕ±­…¹}é¥¹¬ˆ¹•ÅÕ…±Ì¡É•¹‘•É•È¤ñğ€‰Á…¹Ù­}é¥¹¬ˆ¹•ÅÕ…±Ì¡É•¹‘•É•È¤ìœœœ°(€€€€€€‰)IUÑ¥±Ì¥Íi¥¹¬ˆ¤((Œ€ÕŒ¸±½…‘É…Á¡¥Í1¥‰É…Éä)Á…Ñ ¡À°(€€€€€€œœœ€€€€€€€Íİ¥Ñ €¡É•¹‘•É•È¥ì(€€€€€€€€€€€…Í”€‰ÑÕÉ¹¥Á}é¥¹¬ˆè(€€€€€€€€€€€…Í”€‰ÙÕ±­…¹}é¥¹¬ˆè(€€€€€€€€€€€€€€€1½•È¹…ÁÁ•¹‘Q½1½œ ‰mQÕÉ¹¥Ái¥¹­t1½…‘¥¹œÉ•…°5•Í„=M5•Í„€¡±¥‰=M5•Í…|à¹Í¼¤¸¸¸ˆ¤ìœœœ°(€€€€€€œœœ€€€€€€€Íİ¥Ñ €¡É•¹‘•É•È¥ì(€€€€€€€€€€€…Í”€‰Á…¹Ù­}é¥¹¬ˆè(€€€€€€€€€€€…Í”€‰ÑÕÉ¹¥Á}é¥¹¬ˆè(€€€€€€€€€€€…Í”€‰ÙÕ±­…¹}é¥¹¬ˆè(€€€€€€€€€€€€€€€1½•È¹…ÁÁ•¹‘Q½1½œ ‰mQÕÉ¹¥Ái¥¹­t1½…‘¥¹œÉ•…°5•Í„=M5•Í„€¡±¥‰=M5•Í…|à¹Í¼¤¸¸¸ˆ¤ìœœœ°(€€€€€€‰)IUÑ¥±Ì±½…‘É…Á¡¥Í1¥‰É…ÉäÁ…¹Ù¬…Í”ˆ¤((Œ€´´´´´´´´´´€Ø¸¡•…‘¥¹Í}…ÉÉ…ä¹áµ°€´´´´´´´´´´
p = "app_pojavlauncher/src/main/res/values/headings_array.xml"
patch(p,
      '''    <string-array name="renderer">
        <item>Turnip Zink (Vulkan â€” best for Mali/Adreno)</item>''',
      '''    <string-array name="renderer">
        <item>Turnip Zink (Vulkan â€” best for Mali/Adreno)</item>
        <item>PanVK Zink (Vulkan â€” open-source Mali driver, glitch-free)</item>''',
      "headings_array renderer labels")
patch(p,
      '''        <item>turnip_zink</item> <!-- Turnip Zink: OSMesa-based Zink (GLâ†’Vulkan via Mesa) -->''',
      '''        <item>turnip_zink</item> <!-- Turnip Zink: OSMesa-based Zink (GLâ†’Vulkan via Mesa) -->
        <item>panvk_zink</item> <!-- MC19 PanVK Zink: Zink on the open-source Panfrost Vulkan driver -->''',
      "headings_array renderer_values")

print("MC19: all patches applied OK")
