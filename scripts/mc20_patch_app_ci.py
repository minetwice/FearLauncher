#!/usr/bin/env python3
"""MC20: apply Panfork renderer wiring to FearLauncher app files. Idempotent.

Run from the repository root. Patches:
  1. res/values/headings_array.xml - restore customctrl_selectdefault (lost in a
     bad push), add the Panfork renderer to the renderer arrays.
  2. utils/JREUtils.java - panfork renderer case + env + lib selection.
  3. jni/jvm_hooks/lwjgl_dlopen_hook.c - panfork branch in glfwInit (no Vulkan
     preload, Gallium_driver=panfrost, PAN_MESA_DEBUG=gl3,noafbc).
"""
import sys
from pathlib import Path

ok = True

def must(cond, msg):
    global ok
    if not cond:
        print('FAIL:', msg)
        ok = False
    else:
        print('ok:', msg)

# ---------------- 1) headings_array.xml ----------------
p = Path('app_pojavlauncher/src/main/res/values/headings_array.xml')
t = p.read_text()

# 1a. restore the accidentally dropped menu entry (belongs in BOTH menu arrays)
SEL = '        <item>@string/customctrl_selectdefault</item>\n'
for arr_name, next_item in [
    ('menu_customcontrol', '        <item>@string/customctrl_editor_exit</item>'),
    ('menu_customcontrol_customactivity', '        <item>@string/customctrl_export</item>'),
]:
    ai = t.find('name="%s"' % arr_name)
    if ai == -1:
        must(False, 'array %s not found' % arr_name)
        continue
    ae = t.find('</string-array>', ai)
    block = t[ai:ae]
    if 'customctrl_selectdefault' in block:
        print('ok: selectdefault already in %s' % arr_name)
    else:
        ni = block.find(next_item)
        if ni == -1:
            must(False, '%s next-item anchor not found' % arr_name)
        else:
            ins = ai + ni
            t = t[:ins] + SEL + t[ins:]
            must(True, 'restored customctrl_selectdefault in %s' % arr_name)

# 1b. Panfork renderer display name
if 'Panfork (OpenGL' not in t:
    anchor = '        <item>PanVK Zink (Vulkan \u2014 open-source Mali driver, glitch-free)</item>\n'
    must(anchor in t, 'PanVK display-name anchor found')
    if anchor in t:
        t = t.replace(anchor, anchor + '        <item>Panfork (OpenGL \u2014 open-source Mali driver, MC20)</item>\n', 1)

# 1c. Panfork renderer value
if '<item>panfork</item>' not in t:
    anchor = '        <item>panvk_zink</item> <!-- MC19 PanVK Zink: Zink on the open-source Panfrost Vulkan driver -->\n'
    must(anchor in t, 'panvk_zink value anchor found')
    if anchor in t:
        t = t.replace(anchor, anchor + '        <item>panfork</item> <!-- MC20 Panfork: open-source Panfrost GL directly on the kbase kernel driver, via OSMesa -->\n', 1)

p.write_text(t)
t = p.read_text()
must(t.count('<item>@string/customctrl_selectdefault</item>') == 2, 'XML: menu entries present in both arrays')
must(t.count('<item>panfork</item>') == 1, 'XML: panfork value added')
must('Panfork (OpenGL' in t, 'XML: panfork display name added')

# ---------------- 2) JREUtils.java ----------------
p = Path('app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/JREUtils.java')
t = p.read_text()
if '"panfork"' not in t:
    old = '''        switch(renderer) {
            case "panvk_zink":'''
    new = '''        switch(renderer) {
            case "panfork":
                // MC20: Panfork - open-source Panfrost Gallium driver talking
                // directly to the ARM kbase kernel driver, via OSMesa.
                // No proprietary userspace blob (the glitch source), no Vulkan,
                // no Zink translation layer. GL 3.3 via PAN_MESA_DEBUG=gl3.
                Logger.appendToLog("[Panfork] Initializing Panfork renderer (open-source Panfrost GL on kbase kernel driver)...");
                envMap.put("GALLIUM_DRIVER", "panfrost");
                envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "panfrost");
                envMap.put("PAN_MESA_DEBUG", "gl3,noafbc");
                envMap.put("vblank_mode", "0");
                envMap.put("MESA_GLSL_CACHE_DISABLE", "false");
                envMap.put("FEAR_RENDERER", renderer);
                break;
            case "panvk_zink":'''
    must(old in t, 'JREUtils setupRendererEnv anchor found')
    if old in t:
        t = t.replace(old, new, 1)

    old = '''        boolean isZink = "turnip_zink".equals(renderer) || "vulkan_zink".equals(renderer) || "panvk_zink".equals(renderer);'''
    new = '''        boolean isZink = "turnip_zink".equals(renderer) || "vulkan_zink".equals(renderer) || "panvk_zink".equals(renderer) || "panfork".equals(renderer);'''
    must(old in t, 'JREUtils isZink anchor found')
    if old in t:
        t = t.replace(old, new, 1)

    old = '''        if (isZink) {
            envMap.put("LIB_MESA_NAME", "libOSMesa_8.so");'''
    new = '''        if (isZink) {
            envMap.put("LIB_MESA_NAME", "panfork".equals(renderer) ? "libOSMesa_panfork.so" : "libOSMesa_8.so");'''
    must(old in t, 'JREUtils LIB_MESA_NAME anchor found')
    if old in t:
        t = t.replace(old, new, 1)

    old = '''        switch (renderer){
            case "panvk_zink":'''
    new = '''        if ("panfork".equals(renderer)) preloadVk = false;

        switch (renderer){
            case "panfork":
                Logger.appendToLog("[Panfork] Loading Panfork OSMesa (libOSMesa_panfork.so)...");
                renderLibrary = "libOSMesa_panfork.so";
                useGles = false;
                bypassNamespace = true;
                glesVersion = 3;
                if(preloadVk) preloadVulkan();
                break;
            case "panvk_zink":'''
    must(old in t, 'JREUtils loadGraphicsLibrary anchor found')
    if old in t:
        t = t.replace(old, new, 1)
    p.write_text(t)

t = p.read_text()
must(t.count('"panfork"') >= 5, 'JREUtils: panfork wired (5+ refs)')

# ---------------- 3) lwjgl_dlopen_hook.c ----------------
p = Path('app_pojavlauncher/src/main/jni/jvm_hooks/lwjgl_dlopen_hook.c')
t = p.read_text()
if 'is_panfork_renderer' not in t:
    old = '''    if (fear && (strcmp(fear, "turnip_zink") == 0 || strcmp(fear, "vulkan_zink") == 0 || strcmp(fear, "panvk_zink") == 0))
        z = true;'''
    new = '''    if (fear && (strcmp(fear, "turnip_zink") == 0 || strcmp(fear, "vulkan_zink") == 0 || strcmp(fear, "panvk_zink") == 0 || strcmp(fear, "panfork") == 0))
        z = true;'''
    must(old in t, 'hook is_zink_renderer anchor found')
    if old in t:
        t = t.replace(old, new, 1)

    old = '''static void hide_pojav_from_sodium(void) {
    unsetenv("POJAV_RENDERER");
    unsetenv("POJAV_LAUNCHER");
    printf("LWJGL hook v2.12: unset POJAV_RENDERER/POJAV_LAUNCHER (Sodium bypass)\\n");
}'''
    new = old + '''

/* MC20: Panfork = Gallium panfrost on the ARM kbase kernel driver, via OSMesa.
   No Vulkan loader is needed at all. GL 3.3 unlocked via PAN_MESA_DEBUG=gl3,
   AFBC off (Minecraft block-texture glitches on Mali). */
static bool is_panfork_renderer(void) {
    const char* fear = getenv("FEAR_RENDERER");
    return fear && strcmp(fear, "panfork") == 0;
}

static void force_panfork_env(void) {
    setenv("GALLIUM_DRIVER", "panfrost", 1);
    setenv("MESA_LOADER_DRIVER_OVERRIDE", "panfrost", 1);
    setenv("PAN_MESA_DEBUG", "gl3,noafbc", 1);
    setenv("mesa_glthread", "false", 1);
    unsetenv("LIBGL_ES");
    const char* cache = getenv("MESA_GLSL_CACHE_DIR");
    if (cache && cache[0]) {
        setenv("MESA_SHADER_CACHE_DIR", cache, 1);
        setenv("XDG_CACHE_HOME", cache, 0);
        setenv("XDG_CONFIG_HOME", cache, 0);
    }
    if (!getenv("HOME") || !getenv("HOME")[0]) {
        setenv("HOME", cache && cache[0] ? cache : "/data/local/tmp", 1);
    }
    printf("LWJGL hook v2.12: PANFORK env active (GALLIUM_DRIVER=panfrost, PAN_MESA_DEBUG=gl3,noafbc)\\n");
}'''
    must(old in t, 'hook hide_pojav anchor found')
    if old in t:
       t = t.replace(old, new, 1)

    old = '''    if (!g_glfw_initialized) {
        force_zink_env();
        bridge_environ.config_renderer = RENDERER_VK_ZINC;
        ensure_vulkan_ptr();'''
    new = '''    if (!g_glfw_initialized) {
        if (is_panfork_renderer()) {
            force_panfork_env();
            /* Same OSMesa present path as zink; no Vulkan driver is loaded. */
            bridge_environ.config_renderer = RENDERER_VK_ZINK;
        } else {
            force_zink_env();
            bridge_environ.config_renderer = RENDERER_VK_ZINC;
            ensure_vulkan_ptr();
        }'''
    must(old in t, 'hook glfwInit anchor found')
    if old in t:
        t = t.replace(old, new, 1)
    p.write_text(t)

t = p.read_text()
must(t.counv('is_panfork_renderer') == 2, 'hook: is_panfork_renderer defined+used')
must('force_panfork_env' in t, 'hook: force_panfork_env present')

print()
print('ALL OK' if ok else 'FAILURES PRESENT - NOT COMMITTING')
sys.exit(0 if ok else 1)
