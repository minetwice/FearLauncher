#!/usr/bin/env python3
"""FEARWIRE v8: FearRender (MobileGlues / GL-on-GLES) launcher wiring.

Renderer lineup (user decision): Turnip Zink, FearRender, LTW (hidden until
libltw.so is bundled), Holy GL4ES. FearVulkan was removed after the zink-on-
Mali crash investigation; its history lives in git (build-fearvulkan.yml).
Safe to re-run: every block skips when already applied.

v8: config.json is built with org.json so missing keys (fsr1Setting,
maxGlslCacheSize) merge into an existing file; GameRunner gets the options.txt
FPS unlock (maxFps=260 + vsync off) for fear_render.

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
# v8 (FEAR-FSR1): build config.json with org.json so missing keys (fsr1Setting,
# maxGlslCacheSize) are merged into an existing file instead of being skipped.
ENV_CASE = (
    CASE_LINE +
    '                Logger.appendToLog("[FearRender] Initializing FearRender renderer (GL on host GLES - universal Mali/Adreno):");\n'
    '                envMap.put("FEAR_RENDERER", renderer);\n'
    '                envMap.put("vblank_mode", "0");\n'
    '                // [FearRender] MobileGlues tuning: config dir + shader-friendly defaults + FSR1\n'
    '                try {\n'
    '                    java.io.File mgDir = new java.io.File(Tools.DIR_GAME_HOME, "MG");\n'
    '                    java.io.File mgCfg = new java.io.File(mgDir, "config.json");\n'
    '                    //noinspection ResultOfMethodCallIgnored\n'
    '                    mgDir.mkdirs();\n'
    '                    org.json.JSONObject cfg = new org.json.JSONObject();\n'
    '                    if (mgCfg.exists()) {\n'
    '                        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(mgCfg));\n'
    '                        StringBuilder sb = new StringBuilder();\n'
    '                        String ln;\n'
    '                        while ((ln = br.readLine()) != null) sb.append(ln);\n'
    '                        br.close();\n'
    '                        try { cfg = new org.json.JSONObject(sb.toString()); } catch (Throwable ignored) {}\n'
    '                    }\n'
    '                    boolean changed = false;\n'
    '                    if (!cfg.has("enableNoError")) { cfg.put("enableNoError", 2); changed = true; }\n'
    '                    if (!cfg.has("enableExtComputeShader")) { cfg.put("enableExtComputeShader", 1); changed = true; }\n'
    '                    if (!cfg.has("enableExtTimerQuery")) { cfg.put("enableExtTimerQuery", 1); changed = true; }\n'
    '                    if (!cfg.has("enableExtDirectStateAccess")) { cfg.put("enableExtDirectStateAccess", 1); changed = true; }\n'
    '                    // FSR1 UltraQuality: render at ~77% + AMD FidelityFX sharpen upscale -> more FPS, clean crisp image\n'
    '                    if (!cfg.has("fsr1Setting")) { cfg.put("fsr1Setting", 1); changed = true; }\n'
    '                    // 64MB on-disk shader cache, so translated shaders are not recompiled after eviction\n'
    '                    if (!cfg.has("maxGlslCacheSize")) { cfg.put("maxGlslCacheSize", 64); changed = true; }\n'
    '                    if (changed || !mgCfg.exists()) {\n'
    '                        java.io.FileWriter fw = new java.io.FileWriter(mgCfg);\n'
    '                        fw.write(cfg.toString());\n'
    '                        fw.close();\n'
    '                    }\n'
    '                    envMap.put("MG_DIR_PATH", mgDir.getAbsolutePath());\n'
    '                    Logger.appendToLog("[FearRender] MobileGlues config dir: " + mgDir.getAbsolutePath() + (cfg.optInt("fsr1Setting", 0) > 0 ? " [FSR1 UltraQuality: fps boost + sharpen]" : ""));\n'
    '                } catch (Throwable t) {\n'
    '                    Logger.appendToLog("[FearRender] MobileGlues config setup failed: " + t);\n'
    '                }\n'
    '                break;\n'
)

# ---- v8 (FEAR-FSR1-MIGRATE): replace the old fixed-JSON config block with the
# ---- org.json merge version (adds fsr1Setting + maxGlslCacheSize upgrades).
# ---- Boundary-based replacement: the old block's only fragile line was the
# ---- raw JSON write; locating it by markers keeps this file transmission-safe.
NEW_CFG_BODY = ENV_CASE[ENV_CASE.index('                // [FearRender] MobileGlues tuning'):]
NEW_CFG_BODY = NEW_CFG_BODY[:NEW_CFG_BODY.index('                break;\n')]
if 'org.json.JSONObject cfg' in s:
    print("FEARWIRE SKIP: JREUtils config block already migrated (org.json)")
elif 'MG_DIR_PATH' not in s:
    print("FEARWIRE INFO: fresh install, insert path will carry the v8 block")
else:
    i0 = s.find('                // [FearRender] MobileGlues tuning')
    i1 = s.find('                }\n', s.find('config setup failed', i0))
    if i0 > 0 and i1 > i0:
        s = s[:i0] + NEW_CFG_BODY + s[i1 + len('                }\n'):]
        open(P, 'w').write(s)
        print("FEARWIRE OK: JREUtils config block migrated to org.json merge (FSR1 + shader cache)")
        s = open(P).read()
    else:
        fail("JREUtils has MG_DIR_PATH but the old config block was not found by markers")

if 'MG_DIR_PATH' in s and 'org.json.JSONObject cfg' in s:
    print("FEARWIRE SKIP: fear_render env case fully wired (incl. MobileGlues config + FSR1)")
elif 'MG_DIR_PATH' in s:
    pass  # migrated above, message already printed
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


# ------------------------------------------------- GameRunner: options.txt fps unlock
# Vanilla Minecraft defaults to maxFps=60 with vsync on - the "60 fps lock".
# For fear_render, bump to Unlimited (260) + vsync off right before launch.
# Never fights a deliberate low setting (30) - only the defaults (60/120/absent).
s3 = open(P3).read()
if 'FEAR-FPSUNLOCK' in s3:
    print("FEARWIRE SKIP: GameRunner fps unlock already wired")
else:
    old_import = 'import net.kdt.pojavlaunch.multirt.MultiRTUtils;'
    n = s3.count(old_import)
    if n != 1:
        fail("GameRunner MultiRTUtils import anchor count = %d" % n)
    s3 = s3.replace(old_import, old_import + '\nimport net.kdt.pojavlaunch.utils.MCOptionUtils; // FEAR-FPSUNLOCK', 1)

    anchor_gr = '''        File gamedir = instance.getGameDirectory();
        JMinecraftVersionList.Version versionInfo = Tools.getVersionInfo(versionId);
'''
    n = s3.count(anchor_gr)
    if n != 1:
        fail("GameRunner gamedir anchor count = %d" % n)
    fps_block = anchor_gr + '''
        // [FearRender] FEAR-FPSUNLOCK: vanilla defaults cap the game at maxFps=60
        // with vsync on. Bump to Unlimited (260) and disable vsync so the frame
        // rate is bounded only by the GPU (the renderer also forces EGL swap
        // interval 0). Never fights a deliberate low user setting like 30.
        if (rendererName.equals("fear_render")) {
            try {
                MCOptionUtils.load(gamedir.getAbsolutePath());
                String maxFps = MCOptionUtils.get("maxFps");
                if (maxFps == null || "60".equals(maxFps) || "120".equals(maxFps)) {
                    MCOptionUtils.set("maxFps", "260");
                    MCOptionUtils.set("vsync", "false");
                    MCOptionUtils.save();
                }
            } catch (Throwable t) {
                Log.w("FearRender", "Could not unlock fps in options.txt", t);
            }
        }
'''
    s3 = s3.replace(anchor_gr, fps_block, 1)
    open(P3, 'w').write(s3)
    print("FEARWIRE OK: GameRunner options.txt fps unlock wired (fear_render)")

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
