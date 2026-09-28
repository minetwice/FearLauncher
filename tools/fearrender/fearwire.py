#!/usr/bin/env python3
"""FEARWIRE v5: FearRender + FearVulkan launcher wiring (idempotent).

Wires BOTH custom renderers into the launcher:
  - fear_render  : GL on host GLES via MobileGlues (libFearRender.so)
  - fear_vulkan  : GL on Vulkan via our own Mesa/Zink build (libFearVulkan.so)
                   Mali: system ARM Vulkan driver; Adreno: Turnip via loader.
Safe to re-run: every block skips when already applied.

Usage: python3 tools/fearrender/fearwire.py   (from the repo root)
"""
import sys

def fail(msg):
    print("FEARWIRE FAIL: " + msg)
    sys.exit(1)

# ---------------------------------------------------------------- JREUtils
P = 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/JREUtils.java'
s = open(P).read()

# ---- fear_render env case (MobileGlues / GLES) ----
CASE_LINE = '            case "fear_render":\n'
ENV_CASE = (
    CASE_LINE +
    '                Logger.appendToLog("[FearRender] Initializing FearRender renderer (GL on host GLES - universal Mali/Adreno):");\n'
    '                envMap.put("FEAR_RENDERER", renderer);\n'
    '                envMap.put("vblank_mode", "0");\n'
    '                // [FearRender] MobileGlues tuning: config dir + shader-friendly defaults\n'
    '                try {\n'
    '                    java.io.File mgDir = new java.io.File(Tools.DIR_GAME_HOME, "MG");\n'
    '                    java.io.File mgCfg = new java.io.File(mgDir, "config.json");\n'
    '                    if (!mgCfg.exists()) {\n'
    '                        //noinspection ResultOfMethodCallIgnored\n'
    '                        mgDir.mkdirs();\n'
    '                        java.io.FileWriter fw = new java.io.FileWriter(mgCfg);\n'
    '                        fw.write("{\\\"enableNoError\\\":2,\\\"enableExtComputeShader\\\":1,\\\"enableExtTimerQuery\\\":1,\\\"enableExtDirectStateAccess\\\":1}");\n'
    '                        fw.close();\n'
    '                    }\n'
    '                    envMap.put("MG_DIR_PATH", mgDir.getAbsolutePath());\n'
    '                    Logger.appendToLog("[FearRender] MobileGlues config dir: " + mgDir.getAbsolutePath());\n'
    '                } catch (Throwable t) {\n'
    '                    Logger.appendToLog("[FearRender] MobileGlues config setup failed: " + t);\n'
    '                }\n'
    '                break;\n'
)

if 'MG_DIR_PATH' in s:
    print("FEARWIRE SKIP: fear_render env case fully wired (incl. MobileGlues config)")
else:
    if 'case "fear_render":' in s:
        fail('fear_render case exists but without MG_DIR_PATH and body not recognized')
    anchor = '            case "turnip_zink":'
    i = s.find(anchor)
    if i < 0:
        fail("turnip_zink anchor not found")
    s = s[:i] + ENV_CASE + s[i:]
    open(P, 'w').write(s)
    print("FEARWIRE OK: fear_render env case inserted (incl. MobileGlues config)")
    s = open(P).read()

# ---- fear_vulkan env case (our Mesa/Zink on Vulkan) ----
VULKAN_CASE_LINE = '            case "fear_vulkan":\n'
VULKAN_CASE = (
    VULKAN_CASE_LINE +
    '                Logger.appendToLog("[FearVulkan] Initializing FearVulkan renderer (GL 4.6 on Vulkan - our custom Mesa/Zink build):");\n'
    '                envMap.put("GALLIUM_DRIVER", "zink");\n'
    '                envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "zink");\n'
    '                envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");\n'
    '                envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");\n'
    '                envMap.put("vblank_mode", "0");\n'
    '                envMap.put("MESA_GLSL_CACHE_DISABLE", "false");\n'
    '                envMap.put("FEAR_RENDERER", renderer);\n'
    '                if (!GLInfoUtils.getGlInfo().isAdreno()) {\n'
    '                    envMap.put("ZINK_DEBUG", "noreorder,sync");\n'
    '                    envMap.put("GALLIUM_THREAD", "0");\n'
    '                    envMap.put("mesa_glthread", "false");\n'
    '                    Logger.appendToLog("[FearVulkan] Mali/system-Vulkan path: full-sync zink enabled (proven Mali stability fix)");\n'
    '                } else {\n'
    '                    envMap.put("mesa_glthread", "false");\n'
    '                }\n'
    '                break;\n'
)

if '[FearVulkan] Initializing' in s:
    print("FEARWIRE SKIP: fear_vulkan env case wired")
else:
    if 'case "fear_vulkan":' in s:
        fail('fear_vulkan case exists but body not recognized')
    anchor = '            case "turnip_zink":'
    i = s.find(anchor)
    if i < 0:
        fail("turnip_zink anchor not found (fear_vulkan env)")
    s = s[:i] + VULKAN_CASE + s[i:]
    open(P, 'w').write(s)
    print("FEARWIRE OK: fear_vulkan env case inserted")
    s = open(P).read()

# ---- FV2 (A/B test): revert FV1 lazy descriptors so fear_vulkan matches
# ---- turnip_zink EXACTLY (same lib bytes + same env). If it still crashes,
# ---- the problem is NOT in our renderer at all. ----
FV1_CODE = ('                    // FV1: world-texture glitch fix - lazy descriptor updates on Mali/system Vulkan\n'
            '                    // (zink template-descriptor reuse glitched world textures on Mali proprietary driver)\n'
            '                    envMap.put("ZINK_DESCRIPTORS", "lazy");\n')
if FV1_CODE in s:
    s = s.replace(FV1_CODE, '', 1)
    open(P, 'w').write(s)
    print("FEARWIRE OK: FV2 - FV1 ZINK_DESCRIPTORS=lazy REMOVED (A/B: fear_vulkan == turnip_zink)")
    s = open(P).read()
elif '"ZINK_DESCRIPTORS"' in s:
    fail("ZINK_DESCRIPTORS present but not in expected FV1 form")
else:
    print("FEARWIRE SKIP: FV2 already applied (no ZINK_DESCRIPTORS in JREUtils)")

# ---- loadGraphicsLibrary cases ----
if 'renderLibrary = "libFearRender.so"' not in s:
    lib_case = (
        '            case "fear_render":\n'
        '                Logger.appendToLog("[FearRender] Loading FearRender (libFearRender.so - MobileGlues core, GL on GLES)...");\n'
        '                renderLibrary = "libFearRender.so";\n'
        '                useGles = true;\n'
        '                glesVersion = 3;\n'
        '                break;\n'
    )
    anchor = '            case "opengles3_ltw":'
    i = s.find(anchor)
    if i < 0:
        fail("opengles3_ltw anchor not found")
    s = s[:i] + lib_case + s[i:]
    open(P, 'w').write(s)
    print("FEARWIRE OK: JREUtils.java wired (fear_render loadGraphicsLibrary case)")
    s = open(P).read()
else:
    print("FEARWIRE SKIP: JREUtils fear_render loadGraphicsLibrary case already wired")

if 'renderer.equals("fear_vulkan") ? "libFearVulkan.so"' not in s:
    vulk_lib_case = (
        '            case "fear_vulkan":\n'
        '            case "turnip_zink":\n'
        '            case "vulkan_zink":\n'
        '                if (renderer.equals("fear_vulkan")) {\n'
        '                    Logger.appendToLog("[FearVulkan] Loading FearVulkan OSMesa (libFearVulkan.so - our Mesa/Zink build)...");\n'
        '                } else {\n'
        '                    Logger.appendToLog("[TurnipZink] Loading real Mesa OSMesa (libOSMesa_8.so)...");\n'
        '                }\n'
        '                renderLibrary = renderer.equals("fear_vulkan") ? "libFearVulkan.so" : "libOSMesa_8.so";\n'
        '                useGles = false;\n'
        '                bypassNamespace = true;\n'
        '                glesVersion = 3;\n'
        '                if(preloadVk) preloadVulkan();\n'
        '                break;\n'
    )
    anchor = ('            case "turnip_zink":\n'
              '            case "vulkan_zink":\n'
              '                Logger.appendToLog("[TurnipZink] Loading real Mesa OSMesa (libOSMesa_8.so)...");\n'
              '                renderLibrary = "libOSMesa_8.so";\n'
              '                useGles = false;\n'
              '                bypassNamespace = true;\n'
              '                glesVersion = 3;\n'
              '                if(preloadVk) preloadVulkan();\n'
              '                break;\n')
    n = s.count(anchor)
    if n != 1:
        fail("turnip_zink load-case anchor count = %d" % n)
    s = s.replace(anchor, vulk_lib_case, 1)
    open(P, 'w').write(s)
    print("FEARWIRE OK: JREUtils.java wired (fear_vulkan loadGraphicsLibrary case)")
    s = open(P).read()
else:
    print("FEARWIRE SKIP: JREUtils fear_vulkan loadGraphicsLibrary case already wired")

# ---- isZink must include fear_vulkan ----
old = 'boolean isZink = "turnip_zink".equals(renderer) || "vulkan_zink".equals(renderer);'
if old in s:
    s = s.replace(old, 'boolean isZink = "turnip_zink".equals(renderer) || "vulkan_zink".equals(renderer) || "fear_vulkan".equals(renderer);', 1)
    open(P, 'w').write(s)
    print("FEARWIRE OK: isZink includes fear_vulkan")
    s = open(P).read()
elif '"fear_vulkan".equals(renderer);' in s:
    print("FEARWIRE SKIP: isZink already includes fear_vulkan")
else:
    fail("isZink line not found")

# ---- LIB_MESA_NAME must point at libFearVulkan.so for fear_vulkan ----
old = 'envMap.put("LIB_MESA_NAME", "libOSMesa_8.so");'
if old in s:
    s = s.replace(old, 'envMap.put("LIB_MESA_NAME", "fear_vulkan".equals(renderer) ? "libFearVulkan.so" : "libOSMesa_8.so");', 1)
    open(P, 'w').write(s)
    print("FEARWIRE OK: LIB_MESA_NAME wired for fear_vulkan")
    s = open(P).read()
elif 'libFearVulkan.so" : "libOSMesa_8.so"' in s:
    print("FEARWIRE SKIP: LIB_MESA_NAME already wired")
else:
    fail("LIB_MESA_NAME line not found")

# ------------------------------------------------- JREUtils: Sodium bypass
s = open(P).read()
sodium_done = ('if (!"fear_render".equals(renderer)) envMap.put("POJAV_RENDERER", renderer);' in s
               or '} else if (!"fear_render".equals(renderer)) {' in s
               or 'isZink || "fear_render".equals(renderer)' in s)
if sodium_done:
    print("FEARWIRE SKIP: POJAV_RENDERER gate already patched")
else:
    old = '            envMap.put("POJAV_RENDERER", renderer);'
    n = s.count(old)
    if n != 1:
        fail("POJAV_RENDERER set-anchor count = %d" % n)
    s = s.replace(old, '            if (!"fear_render".equals(renderer)) envMap.put("POJAV_RENDERER", renderer);', 1)

    old = '        if (isZink) {\n            scrubPojavDetectorEnv();\n        }'
    n = s.count(old)
    if n != 1:
        fail("scrub-anchor count = %d" % n)
    s = s.replace(old, '        if (isZink || "fear_render".equals(renderer)) {\n            scrubPojavDetectorEnv();\n        }', 1)
    open(P, 'w').write(s)
    print("FEARWIRE OK: Sodium bypass wired (no POJAV_RENDERER + scrub for fear_render)")

# ---------------------------------------------------------------- headings
P2 = 'app_pojavlauncher/src/main/res/values/headings_array.xml'
s2 = open(P2).read()
if 'fear_render' not in s2:
    a1 = '        <item>Turnip Zink (Vulkan — best for Mali/Adreno)</item>'
    if a1 not in s2:
        fail("headings renderer item anchor")
    s2 = s2.replace(a1, a1 + '\n        <item>FearRender (GL on GLES — universal Mali/Adreno, shaders)</item>', 1)
    a2 = '        <item>turnip_zink</item> <!-- Turnip Zink: OSMesa-based Zink (GL→Vulkan via Mesa) -->'
    if a2 not in s2:
        fail("headings renderer_values anchor")
    s2 = s2.replace(a2, a2 + '\n        <item>fear_render</item> <!-- FearRender: GL on host GLES via MobileGlues core -->', 1)
    open(P2, 'w').write(s2)
    print("FEARWIRE OK: headings_array.xml wired (fear_render)")
else:
    print("FEARWIRE SKIP: headings fear_render already wired")

s2 = open(P2).read()
if 'fear_vulkan' not in s2:
    a1 = '        <item>FearRender (GL on GLES — universal Mali/Adreno, shaders)</item>'
    if a1 not in s2:
        fail("headings FearRender item anchor (fear_vulkan)")
    s2 = s2.replace(a1, a1 + '\n        <item>FearVulkan (GL 4.6 on Vulkan — our custom Zink build)</item>', 1)
    a2 = '        <item>fear_render</item> <!-- FearRender: GL on host GLES via MobileGlues core -->'
    if a2 not in s2:
        fail("headings fear_render value anchor (fear_vulkan)")
    s2 = s2.replace(a2, a2 + '\n        <item>fear_vulkan</item> <!-- FearVulkan: our Mesa/Zink build over system Vulkan/Turnip -->', 1)
    open(P2, 'w').write(s2)
    print("FEARWIRE OK: headings_array.xml wired (fear_vulkan)")
else:
    print("FEARWIRE SKIP: headings fear_vulkan already wired")

# ------------------------------------------------- GameRunner: LWJGL libname
P3 = 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/jre/GameRunner.java'
s3 = open(P3).read()
if 'rendererName.equals("fear_render") ? "libFearRender.so"' not in s3:
    old = '? "libmh_drive_vulkan_mesa.so" : "libGL.so"'
    n = s3.count(old)
    if n != 1:
        fail("GameRunner anchor count = %d" % n)
    s3 = s3.replace(old, '? "libmh_drive_vulkan_mesa.so" : rendererName.equals("fear_render") ? "libFearRender.so" : "libGL.so"', 1)
    open(P3, 'w').write(s3)
    print("FEARWIRE OK: GameRunner libname patched (fear_render -> libFearRender.so)")
    s3 = open(P3).read()
else:
    print("FEARWIRE SKIP: GameRunner fear_render libname already patched")

# fear_vulkan uses the Vulkan-present GL wrapper (same as turnip_zink)
old = '(rendererName.equals("turnip_zink") || rendererName.equals("vulkan_zink") ? "libmh_drive_vulkan_mesa.so"'
n = s3.count(old)
if n == 1 and 'rendererName.equals("fear_vulkan") ? "libmh_drive_vulkan_mesa.so"' not in s3:
    s3 = s3.replace(old, '(rendererName.equals("turnip_zink") || rendererName.equals("vulkan_zink") || rendererName.equals("fear_vulkan") ? "libmh_drive_vulkan_mesa.so"', 1)
    open(P3, 'w').write(s3)
    print("FEARWIRE OK: GameRunner libname patched (fear_vulkan -> libmh_drive_vulkan_mesa.so)")
elif 'rendererName.equals("fear_vulkan") ? "libmh_drive_vulkan_mesa.so"' in s3 or 'rendererName.equals("fear_vulkan") ?' in s3:
    print("FEARWIRE SKIP: GameRunner fear_vulkan libname already patched")
else:
    fail("GameRunner fear_vulkan anchor not found")

print("FEARWIRE DONE")
