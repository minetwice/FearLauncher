#!/usr/bin/env python3
"""FEARWIRE v2: FearRender launcher wiring (idempotent).

Patches JREUtils.java, headings_array.xml and GameRunner.java for the
fear_render renderer. Safe to re-run: every block skips when already applied.

v2 (post-MC31): rewritten for the cleaned default launcher
(Mali+Adreno: Turnip Zink / LTW / Holy GL4ES only). Old anchors that
referenced panvk_zink/panfork/krypton are gone; this version wires
FearRender into the clean state.

Usage: python3 tools/fearrender/fearwire.py   (from the repo root)
"""
import sys

def fail(msg):
    print("FEARWIRE FAIL: " + msg)
    sys.exit(1)

# ---------------------------------------------------------------- JREUtils
P = 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/JREUtils.java'
s = open(P).read()

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
    print("FEARWIRE OK: JREUtils.java wired (loadGraphicsLibrary case)")
else:
    print("FEARWIRE SKIP: JREUtils loadGraphicsLibrary case already wired")

# ------------------------------------------------- JREUtils: Sodium bypass
# Sodium PostLaunchChecks hard-fails when POJAV_RENDERER is present in env.
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
if 'fear_render' in s2:
    print("FEARWIRE SKIP: headings already wired")
else:
    a1 = '        <item>Turnip Zink (Vulkan — best for Mali/Adreno)</item>'
    if a1 not in s2:
        fail("headings renderer item anchor")
    s2 = s2.replace(a1, a1 + '\n        <item>FearRender (GL on GLES — universal Mali/Adreno, shaders)</item>', 1)
    a2 = '        <item>turnip_zink</item> <!-- Turnip Zink: OSMesa-based Zink (GL→Vulkan via Mesa) -->'
    if a2 not in s2:
        fail("headings renderer_values anchor")
    s2 = s2.replace(a2, a2 + '\n        <item>fear_render</item> <!-- FearRender: GL on host GLES via MobileGlues core -->', 1)
    open(P2, 'w').write(s2)
    print("FEARWIRE OK: headings_array.xml wired")

# ------------------------------------------------- GameRunner: LWJGL libname
P3 = 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/jre/GameRunner.java'
s3 = open(P3).read()
if 'rendererName.equals("fear_render") ? "libFearRender.so"' in s3:
    print("FEARWIRE SKIP: GameRunner libname already patched")
else:
    old = '? "libmh_drive_vulkan_mesa.so" : "libGL.so"'
    n = s3.count(old)
    if n != 1:
        fail("GameRunner anchor count = %d" % n)
    s3 = s3.replace(old, '? "libmh_drive_vulkan_mesa.so" : rendererName.equals("fear_render") ? "libFearRender.so" : "libGL.so"', 1)
    open(P3, 'w').write(s3)
    print("FEARWIRE OK: GameRunner libname patched (fear_render -> libFearRender.so)")

print("FEARWIRE DONE")
