#!/usr/bin/env python3
"""FEARWIRE v7: FearRender (MobileGlues / GL-on-GLES) launcher wiring.

Renderer lineup (user decision): Turnip Zink, FearRender, LTW (hidden until
libltw.so is bundled), Holy GL4ES. FearVulkan was removed after the zink-on-
Mali crash investigation; its history lives in git (build-fearvulkan.yml).
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

# ---- loadGraphicsLibrary case ----
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

# ------------------------------------------------- JREUtils: Sodium bypass
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
else:
    print("FEARWIRE SKIP: GameRunner fear_render libname already patched")


# ---- remove fear_vulkan (user decision: zink stays as turnip_zink only) ----
if 'case "fear_vulkan":' in s and '[FearVulkan] Initializing' in s:
    start = s.find('            case "fear_vulkan":\n                Logger.appendToLog("[FearVulkan] Initializing')
    assert start > 0
    brk = s.find('                break;\n', start)
    assert brk > start
    s = s[:start] + s[brk + len('                break;\n'):]
    open(P, 'w').write(s)
    print("FEARWIRE OK: fear_vulkan env case removed")
    s = open(P).read()
old_load = '''            case "fear_vulkan":
            case "turnip_zink":
            case "vulkan_zink":
                if (renderer.equals("fear_vulkan")) {
                    Logger.appendToLog("[FearVulkan] Loading FearVulkan OSMesa (libFearVulkan.so - our Mesa/Zink build)...");
                } else {
                    Logger.appendToLog("[TurnipZink] Loading real Mesa OSMesa (libOSMesa_8.so)...");
                }
                renderLibrary = renderer.equals("fear_vulkan") ? "libFearVulkan.so" : "libOSMesa_8.so";
'''
new_load = '''            case "turnip_zink":
            case "vulkan_zink":
                Logger.appendToLog("[TurnipZink] Loading real Mesa OSMesa (libOSMesa_8.so)...");
                renderLibrary = "libOSMesa_8.so";
'''
if old_load in s:
    s = s.replace(old_load, new_load, 1)
    open(P, 'w').write(s)
    print("FEARWIRE OK: turnip_zink load case restored (fear_vulkan removed)")
    s = open(P).read()
old = 'boolean isZink = "turnip_zink".equals(renderer) || "vulkan_zink".equals(renderer) || "fear_vulkan".equals(renderer);'
if old in s:
    s = s.replace(old, 'boolean isZink = "turnip_zink".equals(renderer) || "vulkan_zink".equals(renderer);', 1)
    open(P, 'w').write(s)
    print("FEARWIRE OK: isZink fear_vulkan removed")
    s = open(P).read()
old = 'envMap.put("LIB_MESA_NAME", "fear_vulkan".equals(renderer) ? "libFearVulkan.so" : "libOSMesa_8.so");'
if old in s:
    s = s.replace(old, 'envMap.put("LIB_MESA_NAME", "libOSMesa_8.so");', 1)
    open(P, 'w').write(s)
    print("FEARWIRE OK: LIB_MESA_NAME restored to libOSMesa_8.so")
    s = open(P).read()

# ---- GameRunner: remove fear_vulkan from ternary ----
s3 = open(P3).read()
old = '(rendererName.equals("turnip_zink") || rendererName.equals("vulkan_zink") || rendererName.equals("fear_vulkan") ? "libmh_drive_vulkan_mesa.so"'
if old in s3:
    s3 = s3.replace(old, '(rendererName.equals("turnip_zink") || rendererName.equals("vulkan_zink") ? "libmh_drive_vulkan_mesa.so"', 1)
    open(P3, 'w').write(s3)
    print("FEARWIRE OK: GameRunner fear_vulkan removed")
else:
    print("FEARWIRE SKIP: GameRunner fear_vulkan already removed")

# ---- headings: remove FearVulkan entries ----
import re as _re
s2 = open(P2).read()
if 'fear_vulkan' in s2:
    s2 = _re.sub(r'\n        <item>FearVulkan \(GL 4\.6 on Vulkan — our custom Zink build\)</item>', '', s2)
    s2 = _re.sub(r'\n        <item>fear_vulkan</item> <!-- FearVulkan: our Mesa/Zink build over system Vulkan/Turnip -->', '', s2)
    open(P2, 'w').write(s2)
    print("FEARWIRE OK: headings FearVulkan removed")
else:
    print("FEARWIRE SKIP: headings FearVulkan already removed")

# ---- final sanity: no fear_vulkan leftovers anywhere ----
sj = open(P).read()
if 'fear_vulkan' in sj:
    fail("fear_vulkan still referenced in JREUtils after removal")
sg = open(P3).read()
if 'fear_vulkan' in sg:
    fail("fear_vulkan still referenced in GameRunner after removal")
sh = open(P2).read()
if 'fear_vulkan' in sh:
    fail("fear_vulkan still referenced in headings after removal")

print("FEARWIRE DONE")
