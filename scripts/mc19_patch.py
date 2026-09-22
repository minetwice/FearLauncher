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
  2. CMakeLists.txt: build the shim as libmjlvlk.so.
  3. vulkan_loader.c: panvk_zink returns the shim handle from
     pojavexec_loadVulkanDriver().
  4. lwjgl_dlopen_hook.c: recognize panvk_zink as a zink renderer.
  5. JREUtils.java: panvk_zink renderer env + isZink + loadGraphicsLibrary.
  6. headings_array.xml: PanVK Zink renderer entry in the settings list.
"""
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
    if (!g_icd_gdpa && g_icd)
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

__attribute__((visibility("default")))
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

p = BASE + "/vk_panfrost_shim.c 
if os.path.exists(p):
    print("MC19: vk_panfrost_shim.c exists, rewriting")
write(p, shim)
print("MC19: wrote vk_panfrost_shim.c")

# --------- 2. CMmakeLists.txt ----------
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
      '''v[ÚY
ˆÚ˜]™^X×ÛØY[Ø[‘š]™\Š
HÂˆÚY™YˆSP“WÕT“’TÓĞQT‚ˆYŠ[™›ÚYÙÙ]Ù]šXÙWØ\WÛ]™[

HH
HÈËÈHØY\ˆÙ\È›İİ\Ü™[İÈ]ˆYŠ\›š\Ù[˜X›Y	‰ˆØYİ\›š\İ[Ø[Š
JIÉÉËˆ	ÉÉİ›ÚY
ˆÚ˜]™^X×ÛØY[Ø[‘š]™\Š
HÂˆÚY™YˆSP“WÕT“’TÓĞQT‚ˆYŠ[™›ÚYÙÙ]Ù]šXÙWØ\WÛ]™[

HH
HÈËÈHØY\ˆÙ\È›İİ\Ü™[İÈ]ˆÊˆPÌNNˆ[•’È]Hš[šÈ™[™\œÈÛˆHÜ[‹\Ûİ\˜ÙH[™œ›Üİ[Ø[‚ˆ
ˆš]™\ˆ
X›Z››ËœÛÈÚ[HOˆX[Ø[—Ü[™œ›ÜİœÛÈPÑ
H[œİXYÙ‚ˆ
ˆHÛ]ÚHT“H›ÜšY]\HŞ\İ[Hš]™\ˆÛˆX[Kˆ
‹ÂˆÛÛœİÚ\Šˆ™X\—Ü™[™\™\ˆHÙ][Š‘‘PT—Ô‘S‘T‘TˆŠNÂˆYˆ
™X\—Ü™[™\™\ˆ	‰ˆİ˜Û\
™X\—Ü™[™\™\‹œ[š×Şš[šÈŠHOH
HÂˆÛÛœİÚ\Šˆ™HÙ][Š”ÒU—ÓUU‘QTˆŠNÂˆÚ\ˆÚ[WÜ]ÍLL—NÂˆYˆ
™	‰ˆ™ÌJBˆÛœš[ŠÚ[WÜ]Ú^™[ÙŠÚ[WÜ]
K‰\ËÛX›Z››ËœÛÈ‹™
NÂˆ[ÙBˆÛœš[ŠÚ[WÜ]Ú^™[ÙŠÚ[WÜ]
K›X›Z››ËœÛÈŠNÂˆ›ÚY
ˆ[š×ÜÚ[HHÜ[ŠÚ[WÜ]•Ó“ÕÈ•ÓĞĞS
NÂˆYˆ
[š×ÜÚ[JHÂˆš[Š•[Ø[“ØY\ˆPÌNH[•’ÈÚ[HØYY
	\ÊWˆ‹Ú[WÜ]
NÂˆ™]\›ˆ[š×ÜÚ[NÂˆBˆš[Š•[Ø[“ØY\ˆPÌNH[•’ÈÚ[HRSQ
	\ÊNˆ	\ÈHŞ\İ[H[Ø[ˆ˜[˜XÚ×ˆ‹ˆÚ[WÜ]\œ›ÜŠ
JNÂˆBˆYŠ\›š\Ù[˜X›Y	‰ˆØYİ\›š\İ[Ø[Š
JIÉÉËˆ[Ø[—ÛØY\‹˜È[šÈ]ŠB‚ˆÈKKKKKKKKKHˆÚ™ÛÙÜ[—ÚÛÚË˜ÈKKKKKKKKKBœHTÑH
È‹Ú›WÚÛÚÜËÛÚ™ÛÙÜ[—ÚÛÚË˜È‚œ]Ú
ˆ	ÉÉÈYˆ
™X\ˆ	‰ˆ
İ˜Û\
™X\‹\›š\Şš[šÈŠHOHİ˜Û\
™X\‹[Ø[—Şš[šÈŠHOH
JBˆˆHYNÉÉÉËˆ	ÉÉÈYˆ
™X\ˆ	‰ˆ
İ˜Û\
™X\‹\›š\Şš[šÈŠHOHİ˜Û\
™X\‹[Ø[—Şš[šÈŠHOHİ˜Û\
™X\‹œ[š×Şš[šÈŠHOH
JBˆˆHYNÉÉÉËˆ›Ú™ÛÙÜ[—ÚÛÚË˜È\×Şš[š×Ü™[™\™\ˆŠB‚ˆÈKKKKKKKKKHKˆ”‘U][Ëš˜]˜HKKKKKKKKKBœH˜\ÜÚ˜]›][˜Ú\‹ÜÜ˜ËÛXZ[‹Ú˜]˜KÛ™]ÚÙÜÚ˜]›][˜Úİ][ËÒ”‘U][Ëš˜]˜H‚‚ˆÈXKˆÙ]\™[™\™\‘[ˆYH[š×Şš[šÈØ\ÙH™Y›Ü™H\›š\Şš[šÉÜÂœ]Ú
ˆ	ÉÉÈİÚ]Ú
™[™\™\ŠHÂˆØ\ÙH\›š\Şš[šÈ‚ˆØ\ÙH[Ø[—Şš[šÈ‚ˆÙÙÙ\‹˜\[™ÓÙÊ–Õ\›š\š[š×H[š]X[^š[™Èš[šÈ™[™\™\ˆ
ÔÓY\ØH
ÈY\ØHš[šÊK‹‹ˆŠNÉÉÉËˆ	ÉÉÈİÚ]Ú
™[™\™\ŠHÂˆØ\ÙHœ[š×Şš[šÈ‚ˆËÈPÌNNˆš[šÈÛˆHÜ[‹\Ûİ\˜ÙH[•’È
[™œ›Üİ
Hš]™\‹‚ˆËÈÛX[ˆ]H›È›ÜšY]\KYš]™\ˆÛÜšØ\›İ[™È™YYY‚ˆÙÙÙ\‹˜\[™ÓÙÊ–Ô[•’×H[š]X[^š[™È[•’Èš[šÈ™[™\™\ˆ
Ü[‹\Ûİ\˜ÙH[™œ›Üİ[Ø[ˆš]™\ŠK‹‹ˆŠNÂˆ[“X\œ]
‘ĞSUSWÑ’U‘Tˆ‹š[šÈŠNÂˆ[“X\œ]
“QTĞWÓĞQT—Ñ’U‘T—ÓÕ‘T”’QH‹š[šÈŠNÂˆ[“X\œ]
“QTĞWÑÓÓÕ‘T”ÒSÓ—ÓÕ‘T”’QH‹ŒŠNÂˆ[“X\œ]
“QTĞWÑÓÕ‘T”ÒSÓ—ÓÕ‘T”’QH‹ˆŠNÂˆ[“X\œ]
˜›[š×Û[ÙH‹ŒŠNÂˆ[“X\œ]
“QTĞWÑÓÓĞĞPÒWÑTĞP“H‹™˜[ÙHŠNÂˆ[“X\œ]
‘‘PT—Ô‘S‘T‘Tˆ‹™[™\™\ŠNÂˆ[“X\œ]
”S—ÓQTĞWÑP•QÈ‹››ØY˜˜ÈŠNÂˆœ™XZÎÂˆØ\ÙH\›š\Şš[šÈ‚ˆØ\ÙH[Ø[—Şš[šÈ‚ˆÙÙÙ\‹˜\[™ÓÙÊ–Õ\›š\š[š×H[š]X[^š[™Èš[šÈ™[™\™\ˆ
ÔÓY\ØH
ÈY\ØHš[šÊK‹‹ˆŠNÉÉÉËˆ’”‘U][ÈÙ]\™[™\™\‘[ˆ[šÈØ\ÙHŠB‚ˆÈX‹ˆ\Öš[šÂœ]Ú
ˆ	ÉÉÈ›ÛÛX[ˆ\Öš[šÈH\›š\Şš[šÈ‹™\]X[Ê™[™\™\ŠH[Ø[—Şš[šÈ‹™\]X[Ê™[™\™\ŠNÉÉÉËˆ	ÉÉÈ›ÛÛX[ˆ\Öš[šÈH\›š\Şš[šÈ‹™\]X[Ê™[™\™\ŠH[Ø[—Şš[šÈ‹™\]X[Ê™[™\™\ŠHœ[š×Şš[šÈ‹™\]X[Ê™[™\™\ŠNÉÉÉËˆ’”‘U][È\Öš[šÈŠB‚ˆÈXËˆØYÜ˜\XÜÓXœ˜\Bœ]Ú
ˆ	ÉÉÈİÚ]Ú
™[™\™\Š^ÂˆØ\ÙH\›š\Şš[šÈ‚ˆØ\ÙH[Ø[—Şš[šÈ‚ˆÙÙÙ\‹˜\[™ÓÙÊ–Õ\›š\š[š×HØY[™È™X[Y\ØHÔÓY\ØH
X“ÔÓY\ØWÎœÛÊK‹‹ˆŠNÉÉÉËˆ	ÉÉÈİÚ]Ú
™[™\™\Š^ÂˆØ\ÙHœ[š×Şš[šÈ‚ˆØ\ÙH\›š\Şš[šÈ‚ˆØ\ÙH[Ø[—Şš[šÈ‚ˆÙÙÙ\‹˜\[™ÓÙÊ–Õ\›š\š[š×HØY[™È™X[Y\ØHÔÓY\ØH
X“ÔÓY\ØWÎœÛÊK‹‹ˆŠNÉÉÉËˆ’”‘U][ÈØYÜ˜\XÜÓXœ˜\H[šÈØ\ÙHŠB‚ˆÈKKKKKKKKKH‹ˆXY[™Ü×Ø\œ˜^K[KKKKKKKKKBœH˜\ÜÚ˜]›][˜Ú\‹ÜÜ˜ËÛXZ[‹Ü™\Ëİ˜[Y\ËÚXY[™Ü×Ø\œ˜^K[‚œ]Ú
ˆ	ÉÉÈİš[™ËX\œ˜^H˜[YOHœ™[™\™\ˆ‚ˆ][O•\›š\š[šÈ
[Ø[ˆ8 $™\İ›ÜˆX[KĞY™[›ÊOÚ][O‰ÉÉËˆ	ÉÉÈİš[™ËX\œ˜^H˜[YOHœ™[™\™\ˆ‚ˆ][O•\›š\š[šÈ
[Ø[ˆ8 %™\İ›ÜˆX[KĞY™[›ÊOÚ][O‚ˆ][O”[•’Èš[šÈ
[Ø[ˆ8 %Ü[‹\Ûİ\˜ÙHX[Hš]™\‹Û]ÚYœ™YJOÚ][O‰ÉÉËˆšXY[™Ü×Ø\œ˜^H™[™\™\ˆX™[ÈŠBœ]Ú
ˆ	ÉÉÈ][O\›š\Şš[šÏÚ][OˆKKH\›š\š[šÎˆÔÓY\ØKX˜\ÙYš[šÈ
Ó8¡¤•[Ø[ˆšXHY\ØJHKO‰ÉÉËˆ	ÉÉÈ][O\›š\Şš[šÏÚ][OˆKKH\›š\š[šÎˆÔÓY\ØKX˜\ÙYš[šÈ
Ó8¡¤•[Ø[ˆšXHY\ØJHKO‚ˆ][Oœ[š×Şš[šÏÚ][OˆKKHPÌNH[•’Èš[šÎˆš[šÈÛˆHÜ[‹\Ûİ\˜ÙH[™œ›Üİ[Ø[ˆš]™\ˆKO‰ÉÉËˆšXY[™Ü×Ø\œ˜^H™[™\™\—İ˜[Y\ÈŠB‚œš[
“PÌNNˆ[]Ú\È\YYÒÈŠB