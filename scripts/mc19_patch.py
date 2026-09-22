#!/usr/bin/env python3
# MC19: wire up the PanVK Zink renderer (open-source Panfrost Vulkan driver).
# Zink currently renders through the ARM proprietary system Vulkan driver,
# which glitches on Mali. MC19 adds 'panvk_zink' which points Zink at PanVK.
# The C shim source is split across mc19_shim1.py / mc19_shim2.py.
import io
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mc19_shim1
import mc19_shim2

shim = mc19_shim1.SHIM_P1 + mc19_shim2.SHIM_P2

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


# 1. new file: vk_panfrost_shim.c
p = BASE + "/vk_panfrost_shim.c"
if os.path.exists(p):
    print("MC19: vk_panfrost_shim.c exists, rewriting")
write(p, shim)
print("MC19: wrote vk_panfrost_shim.c")

# 2. CMakeLists.txt
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

# 3. vulkan_loader.c
p = BASE + "/vulkan_loader.c"
t = read(p)
if "#include <string.h>" not in t:
    patch(p,
          '#include <stdio.h>\n',
          '#include <stdio.h>\n#include <string.h>\n',
          "vulkan_loader.c includes")
patch(p,
      '''void* pojavexec_loadVulkanDriver() {
#ifdef ENABLE_TURNIP_LOADER
    if(android_get_device_api_level() >= 28) { // the loader does not support below that
        if(turnip_enabled && load_turnip_vulkan())''',
      '''void* pojavexec_loadVulkanDriver() {
#ifdef ENABLE_TURNIP_LOADER
    if(android_get_device_api_level() >= 28) { // the loader does not support below that
        /* MC19: PanVK path - Zink renders on the open-source Panfrost Vulkan
         * driver (libmjlvlk.so shim -> libvulkan_panfrost.so ICD) instead of
         * the glitchy ARM proprietary system driver on Mali. */
        const char* fear_renderer = getenv("FEAR_RENDERER");
        if (fear_renderer && strcmp(fear_renderer, "panvk_zink") == 0) {
            const char* nd = getenv("POJAV_NATIVEDIR");
            char shim_path[512];
            if (nd && nd[0])
                snprintf(shim_path, sizeof(shim_path), "%s/libmjlvlk.so", nd);
            else
                snprintf(shim_path, sizeof(shim_path), "libmjlvlk.so");
            void* panvk_shim = dlopen(shim_path, RTLD_NOW | RTLD_LOCAL);
            if (panvk_shim) {
                printf("VulkanLoader: MC19 PanVK shim loaded (%s)\\n", shim_path);
                return panvk_shim;
            }
            printf("VulkanLoader: MC19 PanVK shim FAILED (%s): %s - system vulkan fallback\\n",
                   shim_path, dlerror());
        }
        if(turnip_enabled && load_turnip_vulkan())''',
      "vulkan_loader.c panvk path")

# 4. lwjgl_dlopen_hook.c
p = BASE + "/jvm_hooks/lwjgl_dlopen_hook.c"
patch(p,
      '''    if (fear && (strcmp(fear, "turnip_zink") == 0 || strcmp(fear, "vulkan_zink") == 0))
        z = true;''',
      '''    if (fear && (strcmp(fear, "turnip_zink") == 0 || strcmp(fear, "vulkan_zink") == 0 || strcmp(fear, "panvk_zink") == 0))
        z = true;''',
      "lwjgl_dlopen_hook.c is_zink_renderer")

# 5. JREUtils.java
p = "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/JREUtils.java"

patch(p,
      '''        switch(renderer) {
            case "turnip_zink":
            case "vulkan_zink":
                Logger.appendToLog("[TurnipZink] Initializing Zink renderer (OSMesa + Mesa Zink)...");''',
      '''        switch(renderer) {
            case "panvk_zink":
                // MC19: Zink on the open-source PanVK (Panfrost) driver.
                // Clean path - no proprietary-driver workarounds needed.
                Logger.appendToLog("[PanVK] Initializing PanVK Zink renderer (open-source Panfrost Vulkan driver)...");
                envMap.put("GALLIUM_DRIVER", "zink");
                envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "zink");
                envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
                envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");
                envMap.put("vblank_mode", "0");
                envMap.put("MESA_GLSL_CACHE_DISABLE", "false");
                envMap.put("FEAR_RENDERER", renderer);
                envMap.put("PAN_MESA_DEBUG", "noafbc");
                break;
            case "turnip_zink":
            case "vulkan_zink":
                Logger.appendToLog("[TurnipZink] Initializing Zink renderer (OSMesa + Mesa Zink)...");''',
      "JREUtils setupRendererEnv panvk case")

patch(p,
      '''        boolean isZink = "turnip_zink".equals(renderer) || "vulkan_zink".equals(renderer);''',
      '''        boolean isZink = "turnip_zink".equals(renderer) || "vulkan_zink".equals(renderer) || "panvk_zink".equals(renderer);''',
      "JREUtils isZink")

patch(p,
      '''        switch (renderer){
            case "turnip_zink":
            case "vulkan_zink":
                Logger.appendToLog("[TurnipZink] Loading real Mesa OSMesa (libOSMesa_8.so)...");''',
      '''        switch (renderer){
            case "panvk_zink":
            case "turnip_zink":
            case "vulkan_zink":
                Logger.appendToLog("[TurnipZink] Loading real Mesa OSMesa (libOSMesa_8.so)...");''',
      "JREUtils loadGraphicsLibrary panvk case")

# 6. headings_array.xml
p = "app_pojavlauncher/src/main/res/values/headings_array.xml"
patch(p,
      '''    <string-array name="renderer">
        <item>Turnip Zink (Vulkan — best for Mali/Adreno)</item>''',
      '''    <string-array name="renderer">
        <item>Turnip Zink (Vulkan — best for Mali/Adreno)</item>
        <item>PanVK Zink (Vulkan — open-source Mali driver, glitch-free)</item>''',
      "headings_array renderer labels")
patch(p,
      '''        <item>turnip_zink</item> <!-- Turnip Zink: OSMesa-based Zink (GL→Vulkan via Mesa) -->''',
      '''        <item>turnip_zink</item> <!-- Turnip Zink: OSMesa-based Zink (GL→Vulkan via Mesa) -->
        <item>panvk_zink</item> <!-- MC19 PanVK Zink: Zink on the open-source Panfrost Vulkan driver -->''',
      "headings_array renderer_values")

print("MC19: all patches applied OK")
