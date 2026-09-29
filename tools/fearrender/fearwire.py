#!/usr/bin/env python3
"""FEARWIRE v10: FearRender (MobileGlues / GL-on-GLES) launcher wiring.

Renderer lineup (user decision): Turnip Zink, FearRender, LTW (hidden until
libltw.so is bundled), Holy GL4ES. FearVulkan was removed after the zink-on-
Mali crash investigation; its history lives in git (build-fearvulkan.yml).
Safe to re-run: every block skips when already applied.

v8: config.json is built with org.json so missing keys (fsr1Setting,
maxGlslCacheSize) merge into an existing file; GameRunner gets the options.txt
FPS unlock (maxFps=260 + vsync off) for fear_render.

v9 (FEAR-TURBO): GameRunner additionally caps biomeBlendRadius at 1 and
simulationDistance at 6 for fear_render (per-chunk entity ticking is the
dominant stutter source on heavy modpacks - the multi-second worst-frame
spikes in FEAR-PERF logs), with a migration path for the committed v8 block.
Per-launch FSR switching lives in the renderer (FEAR_FSR env, fearpatch FV8).

v10 (FEARWIRE-HOLYZINK): Holy Zink (Kopper) renderer added - Mesa Zink +
Kopper (the AngelAuraMC build vendored from GoyDevv/IronizedZink, downloaded
by build-fearrender.yml) over the SYSTEM Vulkan driver: real desktop GL 4.6
for Iris/Sodium + shader packs, no translation layer, no turnip/panvk. The
bridge presents through libEGL_mesa.so and LWJGL loads libglxshim.so.
v10.1: POJAVEXEC_EGL=libEGL_mesa.so - glxshim locates the EGL library
through that env var (the "EGL lib envvar not found" crash in latestlog-55).

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
        '                Logger.appendToLog("[FearRender] Loading FearRender (libFearRender.so - MobileGlues core, GL on GLES):");\n'
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
if 'FEAR-TURBO' in s3:
    print("FEARWIRE SKIP: GameRunner fps unlock + FEAR-TURBO already wired")
else:
    if 'import net.kdt.pojavlaunch.utils.MCOptionUtils; // FEAR-FPSUNLOCK' not in s3:
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
    turbo_body = '''
        // [FearRender] FEAR-FPSUNLOCK + FEAR-TURBO: vanilla defaults cap the game
        // at maxFps=60 with vsync on - bump to Unlimited (260) + vsync off (the
        // renderer also forces EGL swap interval 0); never fights a deliberate
        // low setting like 30. FEAR-TURBO then trims hidden CPU costs that never
        // pay off visually on heavy modpacks: biome blending above 1 and
        // simulation distance above 6 (per-chunk entity ticking is the main
        // stutter source - see the multi-second worst-frame spikes in FEAR-PERF
        // logs). Values are only lowered, never raised, and only for
        // fear_render.
        if (rendererName.equals("fear_render")) {
            try {
                MCOptionUtils.load(gamedir.getAbsolutePath());
                String maxFps = MCOptionUtils.get("maxFps");
                boolean fearChanged = false;
                if (maxFps == null || "60".equals(maxFps) || "120".equals(maxFps)) {
                    MCOptionUtils.set("maxFps", "260");
                    MCOptionUtils.set("vsync", "false");
                    fearChanged = true;
                }
                String fearBiome = MCOptionUtils.get("biomeBlendRadius");
                if (fearBiome == null || Integer.parseInt(fearBiome.trim()) > 1) {
                    MCOptionUtils.set("biomeBlendRadius", "1");
                    fearChanged = true;
                }
                String fearSim = MCOptionUtils.get("simulationDistance");
                if (fearSim == null || Integer.parseInt(fearSim.trim()) > 6) {
                    MCOptionUtils.set("simulationDistance", "6");
                    fearChanged = true;
                }
                if (fearChanged) MCOptionUtils.save();
            } catch (Throwable t) {
                Log.w("FearRender", "Could not apply fps unlock / FEAR-TURBO in options.txt", t);
            }
        }
'''
    fps_block_old = anchor_gr + '''
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
    turbo_block = anchor_gr + turbo_body
    if s3.count(fps_block_old) == 1:
        # migration: a previous CI run committed the v8 block into the repo
        s3 = s3.replace(fps_block_old, turbo_block, 1)
        open(P3, 'w').write(s3)
        print("FEARWIRE OK: GameRunner FEAR-TURBO upgraded from the committed v8 block")
    else:
        s3 = s3.replace(anchor_gr, turbo_block, 1)
        open(P3, 'w').write(s3)
        print("FEARWIRE OK: GameRunner options.txt fps unlock + FEAR-TURBO wired (fear_render)")


# ------------------------------------------------- Holy Zink (Kopper) renderer
# Mesa Zink + Kopper (the AngelAuraMC mesa_zink_kopper build, vendored from
# GoyDevv/IronizedZink at the pinned commit) over the SYSTEM Vulkan driver.
# This is the Zalith/FCL-style zink: real desktop OpenGL 4.6 (GLSL 460), so
# Iris/Sodium + shader packs run natively, with NO translation layer and NO
# bundled turnip (Adreno-only) or panvk (the Mali watchdog hangs) - the phone's
# own Vulkan driver does the work. The bridge presents through Mesa's EGL
# (libEGL_mesa.so, like the FCL plugin contract name:gl:EGL), and LWJGL loads
# libglxshim.so as the GL library.
if 'FEARWIRE-HOLYZINK' not in open(P).read():
    # ---- JREUtils: env case (before the turnip_zink one) ----
    a = '''            case "turnip_zink":
            case "vulkan_zink":
                Logger.appendToLog("[TurnipZink] Initializing Zink renderer (OSMesa + Mesa Zink)...");
'''
    n = s.count(a)
    if n != 1:
        fail("JREUtils turnip_zink env case anchor count = %d" % n)
    holy_env = '''            case "holy_zink_kopper": /* FEARWIRE-HOLYZINK */
                Logger.appendToLog("[HolyZink] Initializing Zink Kopper renderer (Mesa EGL + Zink over the system Vulkan driver)...");
                envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "zink");
                envMap.put("LIBGL_ES", "3");
                envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");
                envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
                envMap.put("vblank_mode", "0");
                envMap.put("POJAVEXEC_EGL", "libEGL_mesa.so"); /* glxshim finds the EGL lib through this env var (FCL plugin contract) */
                envMap.put("FEAR_RENDERER", renderer);
                break;
'''
    s = s.replace(a, holy_env + a, 1)

    # ---- JREUtils: loadGraphicsLibrary case ----
    a = '''            case "turnip_zink":
            case "vulkan_zink":
                Logger.appendToLog("[TurnipZink] Loading real Mesa OSMesa (libOSMesa_8.so)...");
'''
    n = s.count(a)
    if n != 1:
        fail("JREUtils turnip_zink load case anchor count = %d" % n)
    holy_load = '''            case "holy_zink_kopper": /* FEARWIRE-HOLYZINK */
                Logger.appendToLog("[HolyZink] Loading Mesa Kopper EGL (libEGL_mesa.so - Zink over the system Vulkan driver)...");
                renderLibrary = "libEGL_mesa.so";
                useGles = true;
                bypassNamespace = false;
                glesVersion = 3;
                break;
'''
    s = s.replace(a, holy_load + a, 1)

    # ---- JREUtils: POJAV_RENDERER gate (Sodium hard-fails on zink ids) ----
    a = '''        if (!"fear_render".equals(renderer)) envMap.put("POJAV_RENDERER", renderer);'''
    if s.count(a) == 1:
        s = s.replace(a, '''        if (!"fear_render".equals(renderer) && !"holy_zink_kopper".equals(renderer)) envMap.put("POJAV_RENDERER", renderer); /* FEARWIRE-HOLYZINK */''', 1)
    else:
        fail("POJAV_RENDERER gate anchor count = %d" % s.count(a))

    # ---- JREUtils: scrub Pojav detector env for holy zink too ----
    a = '''        if (isZink || "fear_render".equals(renderer)) {
            scrubPojavDetectorEnv();
        }'''
    n = s.count(a)
    if n != 1:
        fail("scrub anchor count = %d" % n)
    s = s.replace(a, '''        if (isZink || "fear_render".equals(renderer) || "holy_zink_kopper".equals(renderer)) { /* FEARWIRE-HOLYZINK */
            scrubPojavDetectorEnv();
        }''', 1)
    open(P, 'w').write(s)

    # ---- GameRunner: LWJGL GL libname ----
    if 'libglxshim.so' in s3:
        print("FEARWIRE SKIP: GameRunner holy zink libname already patched")
    else:
        a = '''javaArgList.add("-Dorg.lwjgl.opengl.libname=" + (rendererName.equals("turnip_zink") || rendererName.equals("vulkan_zink") ? "libmh_drive_vulkan_mesa.so" : rendererName.equals("fear_render") ? "libFearRender.so" : "libGL.so"));'''
        n = s3.count(a)
        if n != 1:
            fail("GameRunner libname ternary anchor count = %d" % n)
        s3 = s3.replace(a, '''javaArgList.add("-Dorg.lwjgl.opengl.libname=" + (rendererName.equals("turnip_zink") || rendererName.equals("vulkan_zink") ? "libmh_drive_vulkan_mesa.so" : rendererName.equals("holy_zink_kopper") ? "libglxshim.so" : rendererName.equals("fear_render") ? "libFearRender.so" : "libGL.so")); /* FEARWIRE-HOLYZINK */''', 1)
        open(P3, 'w').write(s3)

    # ---- headings ----
    a1 = '        <item>Turnip Zink (Vulkan — best for Mali/Adreno)</item>'
    a2 = '        <item>turnip_zink</item> <!-- Turnip Zink: OSMesa-based Zink (GL→Vulkan via Mesa) -->'
    if 'holy_zink_kopper' not in s2:
        if a1 not in s2 or a2 not in s2:
            fail("headings holy-zink anchors not found")
        s2 = s2.replace(a1, a1 + '\n        <item>Holy Zink (Kopper — GL 4.6 over system Vulkan, shaders)</item>', 1)
        s2 = s2.replace(a2, a2 + '\n        <item>holy_zink_kopper</item> <!-- HolyZink: Mesa Zink+Kopper (AngelAuraMC build) over the system Vulkan driver -->', 1)
        open(P2, 'w').write(s2)

    print("FEARWIRE OK: Holy Zink (Kopper) wired (env + EGL bridge + libglxshim + headings)")
else:
    print("FEARWIRE SKIP: Holy Zink (Kopper) already wired")

# ---- HOLYZINK-EGL fix: glxshim locates the EGL library through POJAVEXEC_EGL
# ---- ("GLXShim: context init failed: EGL lib envvar not found!" in
# ---- latestlog-55). FCL's plugin contract sets it; our launcher never did.
s = open(P).read()
if 'POJAVEXEC_EGL' not in s:
    old_case = '''                envMap.put("vblank_mode", "0");
                envMap.put("FEAR_RENDERER", renderer);
                break;
'''
    if s.count(old_case) != 1:
        fail("holy zink env case for POJAVEXEC_EGL migration not found (count = %d)" % s.count(old_case))
    s = s.replace(old_case, '''                envMap.put("vblank_mode", "0");
                envMap.put("POJAVEXEC_EGL", "libEGL_mesa.so"); /* FEARWIRE-HOLYZINK-EGL: glxshim finds the EGL lib through this env var */
                envMap.put("FEAR_RENDERER", renderer);
                break;
''', 1)
    open(P, 'w').write(s)
    print("FEARWIRE OK: holy zink POJAVEXEC_EGL added (glxshim EGL lib env var)")
else:
    print("FEARWIRE SKIP: holy zink POJAVEXEC_EGL already set")

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
