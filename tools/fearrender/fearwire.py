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
                useGles = false; /* FEARWIRE-HOLYZINK-DESKTOP: force_gles_context made GLFW force a GLES context -> MC died on error 1282; with false GLFW honors MC's desktop GL request */
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

# ---- HOLYZINK-DESKTOP fix: the GLES-forced context was the killer. With
# ---- force_gles_context=1 mojo's GLFW (android_window.c android_reconfigure_context)
# ---- rewrites the requested context to GLES 3 -> zink served "OpenGL ES 3.2"
# ---- and MC died: GL_INVALID_ENUM (GL_TEXTURE_CUBE_MAP_SEAMLESS /
# ---- GL_PROGRAM_POINT_SIZE are desktop-only) then fatal 1282 on
# ---- glTexImage2D(DEPTH_COMPONENT32, GL_FLOAT). With useGles=false GLFW keeps
# ---- MC's desktop GL 3.2 core request -> zink desktop GL over system Vulkan.
s = open(P).read()
if 'FEARWIRE-HOLYZINK-DESKTOP' not in s:
    old_case = '''                renderLibrary = "libEGL_mesa.so";
                useGles = true;
                bypassNamespace = false;
'''
    if s.count(old_case) != 1:
        fail("holy zink load case for DESKTOP migration not found (count = %d)" % s.count(old_case))
    s = s.replace(old_case, '''                renderLibrary = "libEGL_mesa.so";
                useGles = false; /* FEARWIRE-HOLYZINK-DESKTOP: GLFW honors MC's desktop GL request; zink serves GL 4.6 over system Vulkan */
                bypassNamespace = false;
''', 1)
    open(P, 'w').write(s)
    print("FEARWIRE OK: holy zink useGles=false (desktop GL context, not GLES)")
else:
    print("FEARWIRE SKIP: holy zink useGles already false")

# ---- HOLYZINK-LANDSCAPE fix (FEARWIRE-DISPSPEC): the runtime's prebuilt
# ---- libglfw.so builds its sole (fake) monitor video mode from
# ---- mojoexec_renderspec.disp_width/height/hz - fields the fork's pojavexec.h
# ---- dropped, so glfw read past the struct and MC sized its window from garbage
# ---- (portrait, latestlog-57). Restore the fields at the ABI-correct tail and
# ---- publish the real surface size when the bridge window is set.
PH = 'app_pojavlauncher/src/main/jni/pojavexec.h'
sh = open(PH).read()
if 'FEARWIRE-DISPSPEC' not in sh:
    old = '''    int force_gles_context;
    int override_major_version;
} pojavexec_renderspec_t;
'''
    if sh.count(old) != 1:
        fail("pojavexec.h struct anchor count = %d" % sh.count(old))
    sh = sh.replace(old, '''    int force_gles_context;
    int override_major_version;
    /* FEARWIRE-DISPSPEC: the runtime's prebuilt libglfw.so continues its
       mojoexec_renderspec ABI here (disp_width/height/hz) for the fake
       monitor video mode; without these fields it read past the struct. */
    int disp_width;
    int disp_height;
    float disp_hz;
} pojavexec_renderspec_t;
''', 1)
    old2 = 'void* pojavexec_loadVulkanDriver();'
    if sh.count(old2) != 1:
        fail("pojavexec.h decl anchor count = %d" % sh.count(old2))
    sh = sh.replace(old2, 'void pojavexec_setDisplayParams(int width, int height, float hz);\n' + old2, 1)
    open(PH, 'w').write(sh)
    print("FEARWIRE OK: pojavexec.h display fields restored (DISPSPEC)")
else:
    print("FEARWIRE SKIP: pojavexec.h display fields already present")

PM = 'app_pojavlauncher/src/main/jni/minibridge.c'
sm = open(PM).read()
if 'FEARWIRE-DISPSPEC' not in sm:
    anchor_mb = '''const pojavexec_renderspec_t* pojavexec_getRenderSpec() {
    return &renderspec;
}
'''
    if sm.count(anchor_mb) != 1:
        fail("minibridge.c getRenderSpec anchor count = %d" % sm.count(anchor_mb))
    sm = sm.replace(anchor_mb, anchor_mb + '''

/* FEARWIRE-DISPSPEC: publish the display size the prebuilt libglfw.so reports
   as the monitor video mode (see pojavexec.h). */
void pojavexec_setDisplayParams(int width, int height, float hz) {
    renderspec.disp_width = width;
    renderspec.disp_height = height;
    renderspec.disp_hz = hz;
    printf("Renderspec display params: %dx%d@%g\\n", width, height, hz);
}
''', 1)
    open(PM, 'w').write(sm)
    print("FEARWIRE OK: minibridge.c pojavexec_setDisplayParams added")
else:
    print("FEARWIRE SKIP: minibridge.c display setter already present")

PL = 'app_pojavlauncher/src/main/jni/jvm_hooks/lwjgl_dlopen_hook.c'
sl = open(PL).read()
if 'FEARWIRE-DISPSPEC' not in sl:
    old3 = '    if (osmesa_is_loaded()) osm_setup_window();'
    if sl.count(old3) != 1:
        fail("lwjgl_dlopen_hook.c setup-window anchor count = %d" % sl.count(old3))
    sl = sl.replace(old3, '''    /* FEARWIRE-DISPSPEC: publish the real surface size as the display mode
       so the prebuilt libglfw.so reports a sane landscape monitor to the game */
    pojavexec_setDisplayParams(bridge_environ.savedWidth, bridge_environ.savedHeight, 60);
    if (osmesa_is_loaded()) osm_setup_window();''', 1)
    open(PL, 'w').write(sl)
    print("FEARWIRE OK: setupBridgeWindow publishes display params")
else:
    print("FEARWIRE SKIP: setupBridgeWindow already publishes display params")

# ---- HOLYZINK-ROTATE fix: the game rendered 90 degrees sideways (screenshot
# ---- 20260929-165242, latestlog-58/59): zink does not apply the Android
# ---- surface pre-rotation, so the 90/270 buffer transform the Vulkan WSI puts
# ---- on the window makes the whole game appear rotated in a portrait strip.
# ---- Fix: intercept ANativeWindow_setBuffersTransform (bytehook, holy only)
# ---- and rewrite 90/270 to identity; also pre-clear the transform on the
# ---- bridge window. Belt and braces, guarded to holy_zink_kopper.
PH = 'app_pojavlauncher/src/main/jni/driver_helper/hook.c'
sh = open(PH).read()
if 'FEARWIRE-HOLYZINK-ROTATE' not in sh:
    old_inc = '''#include <android/dlext.h>
#include <string.h>
#include <stdio.h>
#include <bytehook.h>
#include "native_hooks.h"'''
    if sh.count(old_inc) != 1:
        fail("hook.c include anchor count = %d" % sh.count(old_inc))
    sh = sh.replace(old_inc, old_inc + '''
#include <stdlib.h>
#include <dlfcn.h>
#include <stdint.h> /* FEARWIRE-HOLYZINK-ROTATE */''', 1)
    old_fn = '''void install_global_egl_hook(bytehook_hook_all_t bytehook_hook_all_p) {
    // Forcefully hook eglGetProcAddress in native GL libraries using bytehook
    bytehook_hook_all_p(NULL, "eglGetProcAddress", (void*)eglGetProcAddress_hook, NULL, NULL);
}'''
    if sh.count(old_fn) != 1:
        fail("hook.c install_global_egl_hook anchor count = %d" % sh.count(old_fn))
    new_fn = '''/* FEARWIRE-HOLYZINK-ROTATE: rewrite 90/270 buffer transforms to identity
   (ROT_90=0x10, ROT_270=0x30 - both match & 0x10) for holy_zink_kopper so
   zink's unrotated landscape output is displayed upright. */
static int32_t (*real_setBuffersTransform_p)(void*, int32_t);
static int32_t holy_rotate_fix_active(void) {
    const char* fear = getenv("FEAR_RENDERER");
    return fear && strcmp(fear, "holy_zink_kopper") == 0;
}
static int32_t hooked_setBuffersTransform_impl(void* window, int32_t transform) {
    if (holy_rotate_fix_active() && (transform & 0x10)) {
        printf("FEARWIRE-ROTATE: ANativeWindow_setBuffersTransform(%d) -> 0 (zink rotation fix)\\n", transform);
        transform = 0;
    }
    if (real_setBuffersTransform_p == NULL)
        real_setBuffersTransform_p = (int32_t (*)(void*, int32_t)) dlsym(RTLD_DEFAULT, "ANativeWindow_setBuffersTransform");
    if (real_setBuffersTransform_p == NULL) return 0;
    return real_setBuffersTransform_p(window, transform);
}

void install_global_egl_hook(bytehook_hook_all_t bytehook_hook_all_p) {
    // Forcefully hook eglGetProcAddress in native GL libraries using bytehook
    bytehook_hook_all_p(NULL, "eglGetProcAddress", (void*)eglGetProcAddress_hook, NULL, NULL);
    // FEARWIRE-HOLYZINK-ROTATE: stop the WSI from rotating zink's output sideways
    bytehook_hook_all_p(NULL, "ANativeWindow_setBuffersTransform", (void*)hooked_setBuffersTransform_impl, NULL, NULL);
}'''
    sh = sh.replace(old_fn, new_fn, 1)
    open(PH, 'w').write(sh)
    print("FEARWIRE OK: hook.c rotation transform intercept added")
else:
    print("FEARWIRE SKIP: hook.c rotation intercept already present")

PL = 'app_pojavlauncher/src/main/jni/jvm_hooks/lwjgl_dlopen_hook.c'
sl = open(PL).read()
if 'FEARWIRE-HOLYZINK-ROTATE' not in sl:
    old_b = '''    pojavexec_setDisplayParams(bridge_environ.savedWidth, bridge_environ.savedHeight, 60);
    if (osmesa_is_loaded()) osm_setup_window();'''
    if sl.count(old_b) != 1:
        fail("setupBridgeWindow anchor count = %d" % sl.count(old_b))
    new_b = '''    pojavexec_setDisplayParams(bridge_environ.savedWidth, bridge_environ.savedHeight, 60);
    /* FEARWIRE-HOLYZINK-ROTATE: clear the window's buffer transform before the
       game starts (belt+braces with the linkerhook intercept) - zink renders
       unrotated landscape, so any pending 90-degree transform shows it sideways */
    {
        const char* fearRenderer = getenv("FEAR_RENDERER");
        if (fearRenderer && strcmp(fearRenderer, "holy_zink_kopper") == 0) {
            int32_t (*setTransform)(void*, int32_t) =
                (int32_t (*)(void*, int32_t)) dlsym(RTLD_DEFAULT, "ANativeWindow_setBuffersTransform");
            if (setTransform != NULL) {
                setTransform(bridge_environ.pojavWindow, 0);
                printf("FEARWIRE-ROTATE: cleared ANativeWindow buffer transform\\n");
            }
        }
    }
    if (osmesa_is_loaded()) osm_setup_window();'''
    sl = sl.replace(old_b, new_b, 1)
    open(PL, 'w').write(sl)
    print("FEARWIRE OK: setupBridgeWindow clears buffer transform for holy")
else:
    print("FEARWIRE SKIP: setupBridgeWindow rotation clear already present")

# ---- fix the "off" fullscreen value MC cannot parse (parse error every launch)
PMJ = 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/MinecraftGLSurface.java'
sj = open(PMJ).read()
if 'MCOptionUtils.set("fullscreen", "off");' in sj:
    sj = sj.replace('MCOptionUtils.set("fullscreen", "off");',
                    'MCOptionUtils.set("fullscreen", "false"); /* FEARWIRE: "off" is not a boolean MC parses (error in latestlog) */', 1)
    open(PMJ, 'w').write(sj)
    print("FEARWIRE OK: fullscreen option written as false (was unparseable \"off\")")
elif 'FEARWIRE: "off" is not a boolean' in sj:
    print("FEARWIRE SKIP: fullscreen false already written")
else:
    print("FEARWIRE INFO: fullscreen set line not found (already changed upstream)")

# ---- HOLYZINK-ROTATE2: v10.4's bytehook intercept was dead code - the
# ---- hs_err maps (pid 23862) show libexithook.so and liblinkerhook.so are
# ---- never loaded in the game process, so install_global_egl_hook (which
# ---- installs the hook) never ran. libpojavexec_awt.so IS always loaded and
# ---- links bytehook directly, so install the intercept from its JNI_OnLoad.
PA = 'app_pojavlauncher/src/main/jni/awt_bridge.c'
sa = open(PA).read()
if 'FEARWIRE-HOLYZINK-ROTATE2' not in sa:
    old_inc = '''#include <jni.h>
#include <assert.h>
#include <string.h>
#include <stdio.h>
#include <dlfcn.h>
#include "native_hooks.h"'''
    if sa.count(old_inc) != 1:
        fail("awt_bridge.c include anchor count = %d" % sa.count(old_inc))
    sa = sa.replace(old_inc, old_inc + '''
#include <stdlib.h>
#include <stdint.h> /* FEARWIRE-HOLYZINK-ROTATE2 */''', 1)

    old_jni = '''jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    // Install global EGL hook first - get bytehook_hook_all from exithook'''
    if sa.count(old_jni) != 1:
        fail("awt_bridge.c JNI_OnLoad anchor count = %d" % sa.count(old_jni))
    new_jni = '''/* FEARWIRE-HOLYZINK-ROTATE2: rewrite 90/270 buffer transforms to identity
   (ROT_90=0x10, ROT_270=0x30 - both match & 0x10) so zink's unrotated
   landscape output is displayed upright instead of sideways. */
static int32_t (*real_holy_setBuffersTransform_p)(void*, int32_t);
static int32_t hooked_holy_setBuffersTransform_impl(void* window, int32_t transform) {
    const char* fearRenderer = getenv("FEAR_RENDERER");
    if (fearRenderer && strcmp(fearRenderer, "holy_zink_kopper") == 0 && (transform & 0x10)) {
        printf("FEARWIRE-ROTATE: ANativeWindow_setBuffersTransform(%d) -> 0 (zink rotation fix)\\n", transform);
        transform = 0;
    }
    if (real_holy_setBuffersTransform_p == NULL)
        real_holy_setBuffersTransform_p = (int32_t (*)(void*, int32_t)) dlsym(RTLD_DEFAULT, "ANativeWindow_setBuffersTransform");
    if (real_holy_setBuffersTransform_p == NULL) return 0;
    return real_holy_setBuffersTransform_p(window, transform);
}

jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    /* FEARWIRE-HOLYZINK-ROTATE2: install the transform intercept directly via
       bytehook - libpojavexec_awt always loads and links it, unlike the
       exithook/linkerhook chain which is dead in this fork's game process. */
    bytehook_hook_all(NULL, "ANativeWindow_setBuffersTransform",
                      (void*) hooked_holy_setBuffersTransform_impl, NULL, NULL);

    // Install global EGL hook first - get bytehook_hook_all from exithook'''
    sa = sa.replace(old_jni, new_jni, 1)
    open(PA, 'w').write(sa)
    print("FEARWIRE OK: awt_bridge.c installs rotation intercept via direct bytehook")
else:
    print("FEARWIRE SKIP: awt_bridge.c rotation intercept already present")

# ---- HOLYZINK-ROTATE3 (Pojav-classic): the transform intercepts (v10.4/10.5)
# ---- never fire - nothing calls ANativeWindow_setBuffersTransform. The real
# ---- mechanism: the compositor displays this window's buffers ROTATED (the
# ---- landscape-locked activity on a portrait-native display). zink renders
# ---- straight into the buffers, so the game shows sideways. Fix: give the
# ---- window PORTRAIT buffer geometry (identity/native orientation), make MC
# ---- render portrait (swapped override dims + swapped monitor mode), and let
# ---- the compositor rotate it back upright. Touch input is remapped in
# ---- dnbglfw GLFW (screen landscape -> MC portrait space).
PL = 'app_pojavlauncher/src/main/jni/jvm_hooks/lwjgl_dlopen_hook.c'
sl = open(PL).read()
if 'FEARWIRE-HOLYZINK-ROTATE3' not in sl:
    old = '''    pojavexec_setDisplayParams(bridge_environ.savedWidth, bridge_environ.savedHeight, 60);'''
    if sl.count(old) != 1:
        fail("setDisplayParams anchor count = %d" % sl.count(old))
    new = '''    /* FEARWIRE-HOLYZINK-ROTATE3: the compositor shows this window's buffers
       rotated 90 (landscape-locked activity on a portrait-native display).
       zink renders straight into the buffers, so force PORTRAIT geometry:
       MC renders portrait, Android rotates it upright. */
    {
        const char* fearRenderer = getenv("FEAR_RENDERER");
        if (fearRenderer && strcmp(fearRenderer, "holy_zink_kopper") == 0
            && bridge_environ.savedHeight > 0 && bridge_environ.savedWidth > 0) {
            int (*setGeometry)(void*, int, int, int) =
                (int (*)(void*, int, int, int)) dlsym(RTLD_DEFAULT, "ANativeWindow_setBuffersGeometry");
            int (*getFormat)(void*) =
                (int (*)(void*)) dlsym(RTLD_DEFAULT, "ANativeWindow_getFormat");
            if (setGeometry != NULL && getFormat != NULL) {
                setGeometry(bridge_environ.pojavWindow,
                            bridge_environ.savedHeight, /* portrait width */
                            bridge_environ.savedWidth,  /* portrait height */
                            getFormat(bridge_environ.pojavWindow));
                printf("FEARWIRE-ROTATE3: portrait buffer geometry %dx%d\\n",
                       bridge_environ.savedHeight, bridge_environ.savedWidth);
            }
            pojavexec_setDisplayParams(bridge_environ.savedHeight, bridge_environ.savedWidth, 60);
        } else {
            pojavexec_setDisplayParams(bridge_environ.savedWidth, bridge_environ.savedHeight, 60);
        }
    }'''
    sl = sl.replace(old, new, 1)
    open(PL, 'w').write(sl)
    print("FEARWIRE OK: setupBridgeWindow forces portrait geometry + monitor for holy")
else:
    print("FEARWIRE SKIP: portrait geometry already forced")

PA = 'app_pojavlauncher/src/main/jni/awt_bridge.c'
sa = open(PA).read()
if 'ROTATE3-guard' not in sa:
    old_fn = '''jint JNI_OnLoad(JavaVM* vm, void* reserved) {'''
    if sa.count(old_fn) != 1:
        fail("awt_bridge JNI_OnLoad anchor count = %d" % sa.count(old_fn))
    new_fn = '''/* FEARWIRE-HOLYZINK-ROTATE3-guard: mojo's glfw resets the buffer geometry to
   the app-landscape default with (0,0); keep the portrait geometry we forced. */
static int (*real_holy_setBuffersGeometry_p)(void*, int, int, int);
static int (*real_holy_getWinWidth_p)(void*);
static int (*real_holy_getWinHeight_p)(void*);
static int hooked_holy_setBuffersGeometry_impl(void* window, int width, int height, int format) {
    const char* fearRenderer = getenv("FEAR_RENDERER");
    if (fearRenderer && strcmp(fearRenderer, "holy_zink_kopper") == 0
        && width == 0 && height == 0 && window != NULL) {
        if (real_holy_getWinWidth_p == NULL) {
            real_holy_getWinWidth_p = (int (*)(void*)) dlsym(RTLD_DEFAULT, "ANativeWindow_getWidth");
            real_holy_getWinHeight_p = (int (*)(void*)) dlsym(RTLD_DEFAULT, "ANativeWindow_getHeight");
        }
        if (real_holy_getWinWidth_p != NULL && real_holy_getWinHeight_p != NULL) {
            width = real_holy_getWinWidth_p(window);
            height = real_holy_getWinHeight_p(window);
            printf("FEARWIRE-ROTATE3: kept geometry reset -> %dx%d\\n", width, height);
        }
    }
    if (real_holy_setBuffersGeometry_p == NULL)
        real_holy_setBuffersGeometry_p = (int (*)(void*, int, int, int)) dlsym(RTLD_DEFAULT, "ANativeWindow_setBuffersGeometry");
    if (real_holy_setBuffersGeometry_p == NULL) return -1;
    return real_holy_setBuffersGeometry_p(window, width, height, format);
}

jint JNI_OnLoad(JavaVM* vm, void* reserved) {'''
    sa = sa.replace(old_fn, new_fn, 1)
    old_reg = '''    bytehook_hook_all(NULL, "ANativeWindow_setBuffersTransform",
                      (void*) hooked_holy_setBuffersTransform_impl, NULL, NULL);'''
    if sa.count(old_reg) != 1:
        fail("awt_bridge bytehook register anchor count = %d" % sa.count(old_reg))
    new_reg = old_reg + '''
    bytehook_hook_all(NULL, "ANativeWindow_setBuffersGeometry",
                      (void*) hooked_holy_setBuffersGeometry_impl, NULL, NULL); /* ROTATE3-guard */'''
    sa = sa.replace(old_reg, new_reg, 1)
    open(PA, 'w').write(sa)
    print("FEARWIRE OK: awt_bridge guards geometry resets (ROTATE3)")
else:
    print("FEARWIRE SKIP: geometry reset guard already present")

PG = 'dnbglfw/src/main/java/git/artdeell/dnbootstrap/glfw/GLFW.java'
sg = open(PG).read()
if 'FEARWIRE-HOLYZINK-ROTATE3' not in sg:
    old_flag = '''    public static double cursorX = 0.5, cursorY = 0.5;'''
    if sg.count(old_flag) != 1:
        fail("GLFW.java cursorX anchor count = %d" % sg.count(old_flag))
    sg = sg.replace(old_flag, old_flag + '''
    /* FEARWIRE-HOLYZINK-ROTATE3: true when the game renders portrait while the
       screen is landscape (holy zink) - touch coords are remapped at the final
       native call so every input path funnels through one transform. */
    public static boolean holyRotate = false;''', 1)
    old_send = '''        sendMousePosition0(cursorX, cursorY);
    }'''
    if sg.count(old_send) != 1:
        fail("GLFW.java sendMousePosition0 anchor count = %d" % sg.count(old_send))
    new_send = '''        /* FEARWIRE-HOLYZINK-ROTATE3: MC's window is portrait, the screen is
           landscape - map screen coords into the game's portrait space. */
        double sendX = cursorX, sendY = cursorY;
        if (holyRotate) { sendX = cursorY; sendY = 1 - cursorX; }
        sendMousePosition0(sendX, sendY);
    }'''
    sg = sg.replace(old_send, new_send, 1)
    old_recv = '''    private static void receiveCursorPos(double x, double y) {
        cursorX = x;
        cursorY = y;'''
    if sg.count(old_recv) != 1:
        fail("GLFW.java receiveCursorPos anchor count = %d" % sg.count(old_recv))
    new_recv = '''    private static void receiveCursorPos(double x, double y) {
        if (holyRotate) { double t = x; x = 1 - y; y = t; } /* FEARWIRE-HOLYZINK-ROTATE3: MC portrait -> screen */
        cursorX = x;
        cursorY = y;'''
    sg = sg.replace(old_recv, new_recv, 1)
    open(PG, 'w').write(sg)
    print("FEARWIRE OK: dnbglfw GLFW input remap for holy")
else:
    print("FEARWIRE SKIP: GLFW input remap already present")

PMJ = 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/MinecraftGLSurface.java'
sj = open(PMJ).read()
if 'FEARWIRE-HOLYZINK-ROTATE3' not in sj:
    old_opt = '''        MCOptionUtils.set("overrideWidth", String.valueOf(windowWidth));
        MCOptionUtils.set("overrideHeight", String.valueOf(windowHeight));'''
    if sj.count(old_opt) != 1:
        fail("MinecraftGLSurface override anchor count = %d" % sj.count(old_opt))
    new_opt = '''        /* FEARWIRE-HOLYZINK-ROTATE3: holy zink renders portrait (the compositor
           rotates it upright), so give MC swapped dims and enable the dnbglfw
           input remap. */
        boolean holyZink = false;
        try {
            net.kdt.pojavlaunch.instances.Instance sel =
                    net.kdt.pojavlaunch.instances.Instances.loadSelectedInstance();
            holyZink = sel != null && "holy_zink_kopper".equals(sel.renderer);
        } catch (Throwable ignored) {}
        GLFW.holyRotate = holyZink;
        MCOptionUtils.set("overrideWidth", String.valueOf(holyZink ? windowHeight : windowWidth));
        MCOptionUtils.set("overrideHeight", String.valueOf(holyZink ? windowWidth : windowHeight));'''
    sj = sj.replace(old_opt, new_opt, 1)
    open(PMJ, 'w').write(sj)
    print("FEARWIRE OK: MinecraftGLSurface swaps override dims for holy")
else:
    print("FEARWIRE SKIP: MinecraftGLSurface override swap already present")

# ---- HOLYZINK-ROTATE4: v10.6 ran but nothing changed - the log proves it:
# ---- dlsym(RTLD_DEFAULT, "ANativeWindow_*") returns NULL inside the game's
# ---- isolated linker namespace (pojav loads game libs in a custom namespace
# ---- where the platform libnativewindow.so is not in the global scope).
# ---- Every ROTATE block so far silently skipped. Fix: fall back to an
# ---- explicit dlopen("libnativewindow.so") / dlopen("libandroid.so") and
# ---- print the resolution result, plus take the rotation direction from
# ---- Android's Display.getRotation() in Java instead of hardcoding 90.
PL = 'app_pojavlauncher/src/main/jni/jvm_hooks/lwjgl_dlopen_hook.c'
sl = open(PL).read()
if 'FEARWIRE-HOLYZINK-ROTATE4' not in sl and 'FEARWIRE-HOLYZINK-ROTATE6' not in sl:
    old = '''            int (*setGeometry)(void*, int, int, int) =
                (int (*)(void*, int, int, int)) dlsym(RTLD_DEFAULT, "ANativeWindow_setBuffersGeometry");
            int (*getFormat)(void*) =
                (int (*)(void*)) dlsym(RTLD_DEFAULT, "ANativeWindow_getFormat");
            if (setGeometry != NULL && getFormat != NULL) {
                setGeometry(bridge_environ.pojavWindow,
                            bridge_environ.savedHeight, /* portrait width */
                            bridge_environ.savedWidth,  /* portrait height */
                            getFormat(bridge_environ.pojavWindow));
                printf("FEARWIRE-ROTATE3: portrait buffer geometry %dx%d\\n",
                       bridge_environ.savedHeight, bridge_environ.savedWidth);
            }'''
    if sl.count(old) != 1:
        fail("ROTATE4 geometry anchor count = %d" % sl.count(old))
    new = '''            int (*setGeometry)(void*, int, int, int) =
                (int (*)(void*, int, int, int)) dlsym(RTLD_DEFAULT, "ANativeWindow_setBuffersGeometry");
            int (*getFormat)(void*) =
                (int (*)(void*)) dlsym(RTLD_DEFAULT, "ANativeWindow_getFormat");
            /* FEARWIRE-HOLYZINK-ROTATE4: RTLD_DEFAULT cannot see the platform
               libnativewindow.so from this isolated namespace - dlopen it. */
            void* fearNatWin = NULL;
            if (setGeometry == NULL || getFormat == NULL) {
                fearNatWin = dlopen("libnativewindow.so", RTLD_NOW);
                if (fearNatWin == NULL) fearNatWin = dlopen("libandroid.so", RTLD_NOW);
                if (fearNatWin != NULL) {
                    if (setGeometry == NULL)
                        setGeometry = (int (*)(void*, int, int, int)) dlsym(fearNatWin, "ANativeWindow_setBuffersGeometry");
                    if (getFormat == NULL)
                        getFormat = (int (*)(void*)) dlsym(fearNatWin, "ANativeWindow_getFormat");
                }
                printf("FEARWIRE-ROTATE4: RTLD_DEFAULT missed, dlopen handle=%p setGeometry=%p getFormat=%p\\n",
                       fearNatWin, (void*) setGeometry, (void*) getFormat);
            }
            if (setGeometry != NULL && getFormat != NULL) {
                setGeometry(bridge_environ.pojavWindow,
                            bridge_environ.savedHeight, /* portrait width */
                            bridge_environ.savedWidth,  /* portrait height */
                            getFormat(bridge_environ.pojavWindow));
                printf("FEARWIRE-ROTATE3: portrait buffer geometry %dx%d APPLIED\\n",
                       bridge_environ.savedHeight, bridge_environ.savedWidth);
            } else {
                printf("FEARWIRE-ROTATE3: FAILED to resolve ANativeWindow symbols, geometry NOT swapped!\\n");
            }'''
    sl = sl.replace(old, new, 1)
    old_t = '''            int32_t (*setTransform)(void*, int32_t) =
                (int32_t (*)(void*, int32_t)) dlsym(RTLD_DEFAULT, "ANativeWindow_setBuffersTransform");
            if (setTransform != NULL) {'''
    if sl.count(old_t) != 1:
        fail("ROTATE4 transform anchor count = %d" % sl.count(old_t))
    new_t = '''            int32_t (*setTransform)(void*, int32_t) =
                (int32_t (*)(void*, int32_t)) dlsym(RTLD_DEFAULT, "ANativeWindow_setBuffersTransform");
            if (setTransform == NULL) {
                void* fearNatWinT = dlopen("libnativewindow.so", RTLD_NOW);
                if (fearNatWinT == NULL) fearNatWinT = dlopen("libandroid.so", RTLD_NOW);
                if (fearNatWinT != NULL)
                    setTransform = (int32_t (*)(void*, int32_t)) dlsym(fearNatWinT, "ANativeWindow_setBuffersTransform");
                printf("FEARWIRE-ROTATE4: setBuffersTransform resolved=%p\\n", (void*) setTransform);
            }
            if (setTransform != NULL) {'''
    sl = sl.replace(old_t, new_t, 1)
    open(PL, 'w').write(sl)
    print("FEARWIRE OK: lwjgl_dlopen_hook ROTATE4 dlopen fallback")
else:
    print("FEARWIRE SKIP: lwjgl ROTATE4 already present")

PA = 'app_pojavlauncher/src/main/jni/awt_bridge.c'
sa = open(PA).read()
if 'FEARWIRE-HOLYZINK-ROTATE4' not in sa:
    old_tr = '''    if (real_holy_setBuffersTransform_p == NULL)
        real_holy_setBuffersTransform_p = (int32_t (*)(void*, int32_t)) dlsym(RTLD_DEFAULT, "ANativeWindow_setBuffersTransform");'''
    if sa.count(old_tr) != 1:
        fail("awt ROTATE4 transform anchor count = %d" % sa.count(old_tr))
    new_tr = '''    if (real_holy_setBuffersTransform_p == NULL) {
        real_holy_setBuffersTransform_p = (int32_t (*)(void*, int32_t)) dlsym(RTLD_DEFAULT, "ANativeWindow_setBuffersTransform");
        /* FEARWIRE-HOLYZINK-ROTATE4: isolated namespace - dlopen fallback */
        if (real_holy_setBuffersTransform_p == NULL) {
            void* fearNatWin = dlopen("libnativewindow.so", RTLD_NOW);
            if (fearNatWin == NULL) fearNatWin = dlopen("libandroid.so", RTLD_NOW);
            if (fearNatWin != NULL)
                real_holy_setBuffersTransform_p = (int32_t (*)(void*, int32_t)) dlsym(fearNatWin, "ANativeWindow_setBuffersTransform");
        }
        printf("FEARWIRE-ROTATE4: awt setBuffersTransform resolved=%p\\n", (void*) real_holy_setBuffersTransform_p);
    }'''
    sa = sa.replace(old_tr, new_tr, 1)
    old_wh = '''            real_holy_getWinWidth_p = (int (*)(void*)) dlsym(RTLD_DEFAULT, "ANativeWindow_getWidth");
            real_holy_getWinHeight_p = (int (*)(void*)) dlsym(RTLD_DEFAULT, "ANativeWindow_getHeight");'''
    if sa.count(old_wh) != 1:
        fail("awt ROTATE4 wh anchor count = %d" % sa.count(old_wh))
    new_wh = '''            real_holy_getWinWidth_p = (int (*)(void*)) dlsym(RTLD_DEFAULT, "ANativeWindow_getWidth");
            real_holy_getWinHeight_p = (int (*)(void*)) dlsym(RTLD_DEFAULT, "ANativeWindow_getHeight");
            if (real_holy_getWinWidth_p == NULL || real_holy_getWinHeight_p == NULL) {
                void* fearNatWinG = dlopen("libnativewindow.so", RTLD_NOW);
                if (fearNatWinG == NULL) fearNatWinG = dlopen("libandroid.so", RTLD_NOW);
                if (fearNatWinG != NULL) {
                    if (real_holy_getWinWidth_p == NULL)
                        real_holy_getWinWidth_p = (int (*)(void*)) dlsym(fearNatWinG, "ANativeWindow_getWidth");
                    if (real_holy_getWinHeight_p == NULL)
                        real_holy_getWinHeight_p = (int (*)(void*)) dlsym(fearNatWinG, "ANativeWindow_getHeight");
                }
            }'''
    sa = sa.replace(old_wh, new_wh, 1)
    old_g = '''        real_holy_setBuffersGeometry_p = (int (*)(void*, int, int, int)) dlsym(RTLD_DEFAULT, "ANativeWindow_setBuffersGeometry");'''
    if sa.count(old_g) != 1:
        fail("awt ROTATE4 geometry anchor count = %d" % sa.count(old_g))
    new_g = '''        real_holy_setBuffersGeometry_p = (int (*)(void*, int, int, int)) dlsym(RTLD_DEFAULT, "ANativeWindow_setBuffersGeometry");
        if (real_holy_setBuffersGeometry_p == NULL) {
            void* fearNatWinH = dlopen("libnativewindow.so", RTLD_NOW);
            if (fearNatWinH == NULL) fearNatWinH = dlopen("libandroid.so", RTLD_NOW);
            if (fearNatWinH != NULL)
                real_holy_setBuffersGeometry_p = (int (*)(void*, int, int, int)) dlsym(fearNatWinH, "ANativeWindow_setBuffersGeometry");
        }'''
    sa = sa.replace(old_g, new_g, 1)
    open(PA, 'w').write(sa)
    print("FEARWIRE OK: awt_bridge ROTATE4 dlopen fallbacks")
else:
    print("FEARWIRE SKIP: awt ROTATE4 already present")

PG = 'dnbglfw/src/main/java/git/artdeell/dnbootstrap/glfw/GLFW.java'
sg = open(PG).read()
if 'FEARWIRE-HOLYZINK-ROTATE4' not in sg:
    old_flag = '''    public static boolean holyRotate = false;'''
    if sg.count(old_flag) != 1:
        fail("GLFW ROTATE4 flag anchor count = %d" % sg.count(old_flag))
    sg = sg.replace(old_flag, old_flag + '''
    /* FEARWIRE-HOLYZINK-ROTATE4: real rotation of the display (from
       Display.getRotation()), so the remap direction is not a guess. */
    public static int holyRotateDir = 90;''', 1)
    old_send = '''        if (holyRotate) { sendX = cursorY; sendY = 1 - cursorX; }'''
    if sg.count(old_send) != 1:
        fail("GLFW ROTATE4 send anchor count = %d" % sg.count(old_send))
    new_send = '''        if (holyRotate) { /* FEARWIRE-HOLYZINK-ROTATE4 */
            if (holyRotateDir == 270) { sendX = 1 - cursorY; sendY = cursorX; }
            else if (holyRotateDir == 180) { sendX = 1 - cursorX; sendY = 1 - cursorY; }
            else { sendX = cursorY; sendY = 1 - cursorX; }
        }'''
    sg = sg.replace(old_send, new_send, 1)
    old_recv = '''        if (holyRotate) { double t = x; x = 1 - y; y = t; } /* FEARWIRE-HOLYZINK-ROTATE3: MC portrait -> screen */'''
    if sg.count(old_recv) != 1:
        fail("GLFW ROTATE4 recv anchor count = %d" % sg.count(old_recv))
    new_recv = '''        if (holyRotate) { /* FEARWIRE-HOLYZINK-ROTATE4: MC portrait -> screen */
            if (holyRotateDir == 270) { double t = x; x = y; y = 1 - t; }
            else if (holyRotateDir == 180) { x = 1 - x; y = 1 - y; }
            else { double t = x; x = 1 - y; y = t; }
        }'''
    sg = sg.replace(old_recv, new_recv, 1)
    open(PG, 'w').write(sg)
    print("FEARWIRE OK: GLFW direction-aware remap")
else:
    print("FEARWIRE SKIP: GLFW ROTATE4 already present")

PMJ = 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/MinecraftGLSurface.java'
sj = open(PMJ).read()
if 'FEARWIRE-HOLYZINK-ROTATE4' not in sj:
    old_dir = '''        GLFW.holyRotate = holyZink;'''
    if sj.count(old_dir) != 1:
        fail("MGLS ROTATE4 anchor count = %d" % sj.count(old_dir))
    new_dir = '''        GLFW.holyRotate = holyZink;
        /* FEARWIRE-HOLYZINK-ROTATE4: take the real display rotation so the
           input remap direction matches the compositor. */
        try {
            int dispRot = getDisplay().getRotation(); /* 0/1/2/3 = 0/90/180/270 */
            GLFW.holyRotateDir = (dispRot == 3 ? 270 : dispRot == 2 ? 180 : 90);
        } catch (Throwable ignored) { GLFW.holyRotateDir = 90; }'''
    sj = sj.replace(old_dir, new_dir, 1)
    open(PMJ, 'w').write(sj)
    print("FEARWIRE OK: MinecraftGLSurface feeds real display rotation")
else:
    print("FEARWIRE SKIP: MinecraftGLSurface ROTATE4 already present")

# ---- HOLYZINK-ROTATE5: log-61 proof - the whole ROTATE3 block in
# ---- setupBridgeWindow still printed nothing while Renderspec printed
# ---- right next to it: getenv("FEAR_RENDERER") is EMPTY at surface-setup
# ---- time. The renderer env (envMap from setupRendererEnv) is applied later,
# ---- at game launch - AFTER setupBridgeWindow already ran and published
# ---- landscape params (2584x1220 in the log). Fix: publish the selected
# ---- renderer into the process env from Java BEFORE the native surface
# ---- setup (android.system.Os.setenv - inherited by a forked game process
# ---- too, and re-applied identically by setupRendererEnv later).
PMJ = 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/MinecraftGLSurface.java'
sj = open(PMJ).read()
if 'FEARWIRE-HOLYZINK-ROTATE5' not in sj:
    old = '''    @Override
    public void onSurfaceAvailable(Surface surface) {
        GLFW.nativeSurfaceCreated(surface);'''
    if sj.count(old) != 1:
        fail("ROTATE5 onSurfaceAvailable anchor count = %d" % sj.count(old))
    new = '''    @Override
    public void onSurfaceAvailable(Surface surface) {
        /* FEARWIRE-HOLYZINK-ROTATE5: publish the selected renderer into the
           process env BEFORE the native surface setup - setupRendererEnv's
           envMap is applied later (at game launch), so every env-gated native
           rotation fix silently skipped during window setup. */
        try {
            net.kdt.pojavlaunch.instances.Instance sel =
                    net.kdt.pojavlaunch.instances.Instances.loadSelectedInstance();
            if (sel != null && sel.renderer != null && !sel.renderer.isEmpty())
                android.system.Os.setenv("FEAR_RENDERER", sel.renderer, true);
        } catch (Throwable ignored) {}
        GLFW.nativeSurfaceCreated(surface);'''
    sj = sj.replace(old, new, 1)
    open(PMJ, 'w').write(sj)
    print("FEARWIRE OK: MinecraftGLSurface sets FEAR_RENDERER before surface setup")
else:
    print("FEARWIRE SKIP: MinecraftGLSurface ROTATE5 already present")

# ---- HOLYZINK-ROTATE6 (v10.9): log-62 still shows the landscape else-branch
# ---- and zero FEARWIRE prints even though v10.8 sets FEAR_RENDERER from Java
# ---- before the native call. Two possibilities remain: the user tested a
# ---- stale APK artifact, or the env/instance read silently failed. This pass
# ---- (a) prints an unconditional BUILD MARKER from native (setupBridgeWindow
# ---- entry: FEAR_RENDERER value + surface size) and from Java
# ---- (onSurfaceAvailable: what was set / what failed), so ONE log resolves
# ---- everything, and (b) drops the namespace-blind dlsym dance entirely -
# ---- ANativeWindow_setBuffersGeometry/getFormat are NDK-public and pojavexec
# ---- already links libnativewindow, so call them directly.
PL = 'app_pojavlauncher/src/main/jni/jvm_hooks/lwjgl_dlopen_hook.c'
sl = open(PL).read()
if 'FEARWIRE-HOLYZINK-ROTATE6' not in sl:
    old_marker = '''    LOGI("Bridge window set: %p (%dx%d)", bridge_environ.pojavWindow,
         bridge_environ.savedWidth, bridge_environ.savedHeight);'''
    if sl.count(old_marker) != 1:
        fail("ROTATE6 LOGI anchor count = %d" % sl.count(old_marker))
    new_marker = '''    LOGI("Bridge window set: %p (%dx%d)", bridge_environ.pojavWindow,
         bridge_environ.savedWidth, bridge_environ.savedHeight);
    {
        /* FEARWIRE-HOLYZINK-ROTATE6: unconditional marker - proves which
           build is running and whether the renderer env is visible here. */
        const char* fearRendererDbg = getenv("FEAR_RENDERER");
        printf("FEARWIRE v10.9: setupBridgeWindow FEAR_RENDERER=%s surface=%dx%d\\n",
               fearRendererDbg ? fearRendererDbg : "(null)",
               bridge_environ.savedWidth, bridge_environ.savedHeight);
    }'''
    sl = sl.replace(old_marker, new_marker, 1)
    old_geom = '''            int (*setGeometry)(void*, int, int, int) =
                (int (*)(void*, int, int, int)) dlsym(RTLD_DEFAULT, "ANativeWindow_setBuffersGeometry");
            int (*getFormat)(void*) =
                (int (*)(void*)) dlsym(RTLD_DEFAULT, "ANativeWindow_getFormat");
            /* FEARWIRE-HOLYZINK-ROTATE4: RTLD_DEFAULT cannot see the platform
               libnativewindow.so from this isolated namespace - dlopen it. */
            void* fearNatWin = NULL;
            if (setGeometry == NULL || getFormat == NULL) {
                fearNatWin = dlopen("libnativewindow.so", RTLD_NOW);
                if (fearNatWin == NULL) fearNatWin = dlopen("libandroid.so", RTLD_NOW);
                if (fearNatWin != NULL) {
                    if (setGeometry == NULL)
                        setGeometry = (int (*)(void*, int, int, int)) dlsym(fearNatWin, "ANativeWindow_setBuffersGeometry");
                    if (getFormat == NULL)
                        getFormat = (int (*)(void*)) dlsym(fearNatWin, "ANativeWindow_getFormat");
                }
                printf("FEARWIRE-ROTATE4: RTLD_DEFAULT missed, dlopen handle=%p setGeometry=%p getFormat=%p\\n",
                       fearNatWin, (void*) setGeometry, (void*) getFormat);
            }
            if (setGeometry != NULL && getFormat != NULL) {
                setGeometry(bridge_environ.pojavWindow,
                            bridge_environ.savedHeight, /* portrait width */
                            bridge_environ.savedWidth,  /* portrait height */
                            getFormat(bridge_environ.pojavWindow));
                printf("FEARWIRE-ROTATE3: portrait buffer geometry %dx%d APPLIED\\n",
                       bridge_environ.savedHeight, bridge_environ.savedWidth);
            } else {
                printf("FEARWIRE-ROTATE3: FAILED to resolve ANativeWindow symbols, geometry NOT swapped!\\n");
            }'''
    if sl.count(old_geom) != 1:
        fail("ROTATE6 geometry anchor count = %d" % sl.count(old_geom))
    new_geom = '''            /* FEARWIRE-HOLYZINK-ROTATE6: these are NDK-public functions and
               pojavexec links libnativewindow directly - no dlsym needed. */
            ANativeWindow_setBuffersGeometry(bridge_environ.pojavWindow,
                            bridge_environ.savedHeight, /* portrait width */
                            bridge_environ.savedWidth,  /* portrait height */
                            ANativeWindow_getFormat(bridge_environ.pojavWindow));
            printf("FEARWIRE-ROTATE3: portrait buffer geometry %dx%d APPLIED (direct NDK call)\\n",
                   bridge_environ.savedHeight, bridge_environ.savedWidth);'''
    sl = sl.replace(old_geom, new_geom, 1)
    open(PL, 'w').write(sl)
    print("FEARWIRE OK: lwjgl ROTATE6 marker + direct NDK geometry call")
else:
    print("FEARWIRE SKIP: lwjgl ROTATE6 already present")

PMJ = 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/MinecraftGLSurface.java'
sj = open(PMJ).read()
if 'FEARWIRE v10.9: onSurfaceAvailable' not in sj and 'FEARWIRE v10.10: onSurfaceAvailable' not in sj:
    old_env = '''        try {
            net.kdt.pojavlaunch.instances.Instance sel =
                    net.kdt.pojavlaunch.instances.Instances.loadSelectedInstance();
            if (sel != null && sel.renderer != null && !sel.renderer.isEmpty())
                android.system.Os.setenv("FEAR_RENDERER", sel.renderer, true);
        } catch (Throwable ignored) {}'''
    if sj.count(old_env) != 1:
        fail("ROTATE6 Java anchor count = %d" % sj.count(old_env))
    new_env = '''        String fearRenderer5 = "(unset)";
        try {
            net.kdt.pojavlaunch.instances.Instance sel =
                    net.kdt.pojavlaunch.instances.Instances.loadSelectedInstance();
            if (sel != null && sel.renderer != null && !sel.renderer.isEmpty()) {
                android.system.Os.setenv("FEAR_RENDERER", sel.renderer, true);
                fearRenderer5 = sel.renderer;
            }
        } catch (Throwable t) { fearRenderer5 = "ERR " + t; }
        System.out.println("FEARWIRE v10.9: onSurfaceAvailable FEAR_RENDERER=" + fearRenderer5);'''
    sj = sj.replace(old_env, new_env, 1)
    open(PMJ, 'w').write(sj)
    print("FEARWIRE OK: MinecraftGLSurface ROTATE6 Java marker with feedback")
else:
    print("FEARWIRE SKIP: MinecraftGLSurface ROTATE6 already present")

# ---- HOLYZINK-ROTATE7 (v10.10): ROOT CAUSE FOUND in log-63: the launcher
# ---- resolves the renderer with Instance.getLaunchRenderer(), which falls
# ---- back to the GLOBAL pref when instance.renderer is empty -
# ---- "Selected renderer: holy_zink_kopper" (log line 12) came from that
# ---- path. My checks used sel.renderer directly -> always false -> no setenv,
# ---- no overrideWidth swap, no input remap. Fix: use getLaunchRenderer()/
# ---- PREF_RENDERER (same resolution as the launcher), plus a native
# ---- background watcher that applies the portrait geometry the moment the
# ---- renderer env appears at game launch - so the geometry swap happens even
# ---- if the Java path fails again.
PMJ = 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/MinecraftGLSurface.java'
sj = open(PMJ).read()
if 'FEARWIRE-HOLYZINK-ROTATE7' not in sj:
    old_env = '''        String fearRenderer5 = "(unset)";
        try {
            net.kdt.pojavlaunch.instances.Instance sel =
                    net.kdt.pojavlaunch.instances.Instances.loadSelectedInstance();
            if (sel != null && sel.renderer != null && !sel.renderer.isEmpty()) {
                android.system.Os.setenv("FEAR_RENDERER", sel.renderer, true);
                fearRenderer5 = sel.renderer;
            }
        } catch (Throwable t) { fearRenderer5 = "ERR " + t; }
        System.out.println("FEARWIRE v10.9: onSurfaceAvailable FEAR_RENDERER=" + fearRenderer5);'''
    if sj.count(old_env) != 1:
        fail("ROTATE7 env anchor count = %d" % sj.count(old_env))
    new_env = '''        String fearRenderer5 = "(unset)";
        try {
            net.kdt.pojavlaunch.instances.Instance sel =
                    net.kdt.pojavlaunch.instances.Instances.loadSelectedInstance();
            /* FEARWIRE-HOLYZINK-ROTATE7: use getLaunchRenderer() - the exact
               method the launcher itself uses. instance.renderer can be empty
               while the real renderer comes from the global PREF_RENDERER. */
            fearRenderer5 = sel != null ? sel.getLaunchRenderer()
                    : net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_RENDERER;
        } catch (Throwable t) { fearRenderer5 = "ERR " + t; }
        if (fearRenderer5 != null && !fearRenderer5.isEmpty() && !fearRenderer5.startsWith("ERR")) {
            try { android.system.Os.setenv("FEAR_RENDERER", fearRenderer5, true); } catch (Throwable ignored) {}
        }
        System.out.println("FEARWIRE v10.10: onSurfaceAvailable FEAR_RENDERER=" + fearRenderer5);'''
    sj = sj.replace(old_env, new_env, 1)
    old_holy = '''        boolean holyZink = false;
        try {
            net.kdt.pojavlaunch.instances.Instance sel =
                    net.kdt.pojavlaunch.instances.Instances.loadSelectedInstance();
            holyZink = sel != null && "holy_zink_kopper".equals(sel.renderer);
        } catch (Throwable ignored) {}'''
    if sj.count(old_holy) != 1:
        fail("ROTATE7 holy anchor count = %d" % sj.count(old_holy))
    new_holy = '''        boolean holyZink = false;
        try {
            net.kdt.pojavlaunch.instances.Instance sel =
                    net.kdt.pojavlaunch.instances.Instances.loadSelectedInstance();
            /* FEARWIRE-HOLYZINK-ROTATE7: same renderer resolution as the
               launcher itself (getLaunchRenderer falls back to PREF_RENDERER
               when instance.renderer is empty). */
            String fearR = sel != null ? sel.getLaunchRenderer()
                    : net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_RENDERER;
            holyZink = "holy_zink_kopper".equals(fearR);
        } catch (Throwable ignored) {}'''
    sj = sj.replace(old_holy, new_holy, 1)
    open(PMJ, 'w').write(sj)
    print("FEARWIRE OK: MinecraftGLSurface uses getLaunchRenderer (launcher-identical)")
else:
    print("FEARWIRE SKIP: MinecraftGLSurface ROTATE7 already present")

PL = 'app_pojavlauncher/src/main/jni/jvm_hooks/lwjgl_dlopen_hook.c'
sl = open(PL).read()
if 'FEARWIRE-HOLYZINK-ROTATE7' not in sl:
    old_fn = '''JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_setupBridgeWindow(JNIEnv* env, jclass clazz, jobject surface) {'''
    if sl.count(old_fn) != 1:
        fail("ROTATE7 native fn anchor count = %d" % sl.count(old_fn))
    new_fn = '''#include <unistd.h>
/* FEARWIRE-HOLYZINK-ROTATE7: waits (up to 60s) for FEAR_RENDERER - the env is
   set at game launch, after the surface already exists - then applies the
   portrait buffer geometry for holy zink before the game creates its window. */
static volatile int fear_rotate_started = 0;
static void* fear_rotate_env_wait(void* arg) {
    (void) arg;
    for (int i = 0; i < 300; i++) {
        const char* r = getenv("FEAR_RENDERER");
        if (r != NULL) {
            if (strcmp(r, "holy_zink_kopper") != 0) {
                printf("FEARWIRE-ROTATE7: renderer=%s, no rotation needed\\n", r);
                return NULL;
            }
            if (bridge_environ.pojavWindow != NULL
                && bridge_environ.savedWidth > 0 && bridge_environ.savedHeight > 0) {
                ANativeWindow_setBuffersGeometry(bridge_environ.pojavWindow,
                        bridge_environ.savedHeight, bridge_environ.savedWidth,
                        ANativeWindow_getFormat(bridge_environ.pojavWindow));
                pojavexec_setDisplayParams(bridge_environ.savedHeight, bridge_environ.savedWidth, 60);
                printf("FEARWIRE-ROTATE7: late renderer pick-up, portrait geometry %dx%d APPLIED\\n",
                       bridge_environ.savedHeight, bridge_environ.savedWidth);
            }
            return NULL;
        }
        usleep(200 * 1000);
    }
    printf("FEARWIRE-ROTATE7: renderer env never appeared\\n");
    return NULL;
}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_setupBridgeWindow(JNIEnv* env, jclass clazz, jobject surface) {'''
    sl = sl.replace(old_fn, new_fn, 1)
    old_start = '''    /* FEARWIRE-HOLYZINK-ROTATE: clear the window's buffer transform before the'''
    if sl.count(old_start) != 1:
        fail("ROTATE7 poller anchor count = %d" % sl.count(old_start))
    new_start = '''    /* FEARWIRE-HOLYZINK-ROTATE7: the renderer env usually appears only at
       game launch (after the surface exists) - watch for it in the background
       and apply the portrait geometry for holy zink as soon as it shows up. */
    {
        const char* fearRendererNow = getenv("FEAR_RENDERER");
        if ((fearRendererNow == NULL || strcmp(fearRendererNow, "holy_zink_kopper") != 0)
            && !fear_rotate_started) {
            fear_rotate_started = 1;
            pthread_t fearRotateThread;
            if (pthread_create(&fearRotateThread, NULL, fear_rotate_env_wait, NULL) == 0)
                pthread_detach(fearRotateThread);
            printf("FEARWIRE-ROTATE7: renderer env watcher thread started\\n");
        }
    }
    /* FEARWIRE-HOLYZINK-ROTATE: clear the window's buffer transform before the'''
    sl = sl.replace(old_start, new_start, 1)
    open(PL, 'w').write(sl)
    print("FEARWIRE OK: native env watcher thread added")
else:
    print("FEARWIRE SKIP: native ROTATE7 already present")

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
