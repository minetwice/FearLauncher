#!/usr/bin/env python3
"""FEARWIRE: FearRender launcher wiring (idempotent).

Patches JREUtils.java, headings_array.xml and GameRunner.java for the
fear_render renderer. Safe to re-run: every block skips when already applied.

MC19: restores the zink MC17 workarounds (mipmapLevels=0 + ZINK_DEBUG sync)
after Mesa 25.2.1 crashed at context creation on non-Adreno system Vulkan.
MC20: removes the dead renderers (Panfork, old Zink (Vulkan), LTW) from
the renderer menu.

Usage: python3 tools/fearrender/fearwire.py   (from the repo root)
"""
import sys

def fail(msg):
    print("FEARWIRE FAIL: " + msg)
    sys.exit(1)

# The full fear_render env-case body (16-space indented, MobileGlues config
# included). Used for both fresh insertion and upgrading older wiring.
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

# Old committed variant of the case body (6-space indentation, no MG config).
OLD_BODY = (
    '      Logger.appendToLog("[FearRender] Initializing FearRender renderer (GL on host GLES - universal Mali/Adreno):");\n'
    '      envMap.put("FEAR_RENDERER", renderer);\n'
    '      envMap.put("vblank_mode", "0");\n'
    '      break;\n'
)

# ---------------------------------------------------------------- JREUtils
P = 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/JREUtils.java'
s = open(P).read()

if 'MG_DIR_PATH' in s:
    print("FEARWIRE SKIP: fear_render env case fully wired (incl. MobileGlues config)")
else:
    if OLD_BODY in s:
        # Upgrade the old 6-space-indented case body to the full version.
        # (The case line itself stays; only the body is replaced.)
        n = s.count(OLD_BODY)
        if n != 1:
            fail("old fear_render case body count = %d" % n)
        s = s.replace(OLD_BODY, ENV_CASE[len(CASE_LINE):], 1)
        print("FEARWIRE OK: upgraded fear_render env case (+ MobileGlues config)")
    elif 'case "fear_render":' in s:
        # Case present in some other form without MG config.
        fail('fear_render case exists but without MG_DIR_PATH and body not recognized')
    else:
        anchor = '            case "panvk_zink":'
        i = s.find(anchor)
        if i < 0:
            fail("panvk_zink anchor not found")
        s = s[:i] + ENV_CASE + s[i:]
        print("FEARWIRE OK: fear_render env case inserted (incl. MobileGlues config)")
    open(P, 'w').write(s)
    s = open(P).read()

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
    a1 = '        <item>Panfork (GL — open-source Panfrost on kbase)</item>'
    if a1 not in s2:
        fail("headings renderer item anchor")
    s2 = s2.replace(a1, a1 + '\n        <item>FearRender (GL on GLES — universal Mali/Adreno)</item>', 1)
    a2 = '<item>panfork</item> <!-- MC20 Panfork: open-source Panfrost GL directly on the kbase kernel driver, via OSMesa -->'
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
    old = '? "libNG-GL4ES.so" : "libGL.so"'
    n = s3.count(old)
    if n != 1:
        fail("GameRunner anchor count = %d" % n)
    s3 = s3.replace(old, '? "libNG-GL4ES.so" : rendererName.equals("fear_render") ? "libFearRender.so" : "libGL.so"', 1)
    open(P3, 'w').write(s3)
    print("FEARWIRE OK: GameRunner libname patched (fear_render -> libFearRender.so)")

# ------------------------------------------- MC18/MC19: zink workaround state
# MC19 (current): Mesa 25.2.1 zink crashed at OSMesaCreateContextAttribs on
# non-Adreno system Vulkan (SIGSEGV at window creation), so the MC17
# workarounds are RESTORED on top of Mesa 25.1.4 (proven working).
s = open(P).read()
if 'MC19: restored MC17' in s:
    print("FEARWIRE SKIP: MC19 zink workaround restore (JREUtils) done")
else:
    old = (
        '                // MC18: Mesa 26.2 zink is expected to handle the non-conformant system\n'
        '                // Vulkan driver natively. The old MC17 in-order/sync workaround has been\n'
        '                // retired; if artifacts return on non-Adreno GPUs, re-enable per-test with\n'
        '                // ZINK_DEBUG=noreorder,sync from a custom env var.\n'
        '                envMap.put("mesa_glthread", "false");\n'
        '                if (!GLInfoUtils.getGlInfo().isAdreno()) {\n'
        '                    Logger.appendToLog("[TurnipZink] System Vulkan (Mali/proprietary) detected - Mesa 26.2 zink, legacy MC17 workaround retired");\n'
        '                }\n'
        '                break;'
    )
    new = (
        '                // MC19: restored MC17 - Mesa 25.2.1 zink crashed at context creation\n'
        '                // (SIGSEGV in OSMesaCreateContextAttribs) on non-Adreno system Vulkan.\n'
        '                // Back on Mesa 25.1.4 with the proven in-order/sync workaround.\n'
        '                if (!GLInfoUtils.getGlInfo().isAdreno()) {\n'
        '                    envMap.put("ZINK_DEBUG", "noreorder,sync");\n'
        '                    envMap.put("GALLIUM_THREAD", "0");\n'
        '                    envMap.put("mesa_glthread", "false");\n'
        '                    Logger.appendToLog("[TurnipZink] System Vulkan (Mali/proprietary) detected - MC17 fix (Mesa 25.1.4): ZINK_DEBUG=noreorder,sync, GALLIUM_THREAD=0, mesa_glthread=false, mipmapLevels=0");\n'
        '                } else {\n'
        '                    envMap.put("mesa_glthread", "false");\n'
        '                }\n'
        '                break;'
    )
    n = s.count(old)
    if n != 1:
        fail("MC19 JREUtils anchor count = %d" % n)
    s = s.replace(old, new, 1)
    open(P, 'w').write(s)
    print("FEARWIRE OK: MC19 zink workaround restored (JREUtils)")

s3 = open(P3).read()
if 'MC19: restored MC17' in s3:
    print("FEARWIRE SKIP: MC19 zink workaround restore (GameRunner) done")
else:
    old = (
        '        // MC18: turnip_zink on Mali / proprietary system Vulkan - legacy workaround retired.\n'
        '        // The MC17 mipmapLevels=0 force (async-compute mipmap corruption on ARM\n'
        '        // proprietary Vulkan) has been retired together with the ZINK_DEBUG sync\n'
        '        // workaround. Mesa 26.2 zink is expected to be clean; if the block-texture\n'
        '        // sliding ever returns, re-enable via options.txt.\n'
    )
    new = (
        '        // MC19: restored MC17 - block-texture glitch fix for zink on ARM\n'
        '        // proprietary Vulkan (async-compute mipmap corruption).\n'
        '        if ((rendererName.equals("turnip_zink") || rendererName.equals("vulkan_zink"))\n'
        '                && !GLInfoUtils.getGlInfo().isAdreno()) {\n'
        '            try {\n'
        '                MCOptionUtils.load(instance.getGameDirectory().getAbsolutePath());\n'
        '                MCOptionUtils.set("mipmapLevels", "0");\n'
        '                MCOptionUtils.save();\n'
        '                try { net.kdt.pojavlaunch.Logger.appendToLog("[TurnipZink] MC19: mipmapLevels=0 + full-sync zink (Mali block texture glitch fix, restored)"); } catch (Throwable ignored) {}\n'
        '            } catch (Throwable t2) {\n'
        '                Log.w("GameRunner", "MC19 mipmap tweak failed", t2);\n'
        '            }\n'
        '        }\n'
    )
    n = s3.count(old)
    if n != 1:
        fail("MC19 GameRunner anchor count = %d" % n)
    s3 = s3.replace(old, new, 1)
    open(P3, 'w').write(s3)
    print("FEARWIRE OK: MC19 mipmap force restored (GameRunner)")

# ------------------------------------------- MC20: remove dead renderers
# Clean up the renderer menu: drop the 3 legacy/unused entries (Panfork,
# old Zink (Vulkan), LTW). Keep: Turnip Zink, PanVK Zink, FearRender,
# Krypton, gl4es. Idempotent via absence of 'panfork' in the menu.
P4 = 'app_pojavlauncher/src/main/res/values/headings_array.xml'
s4 = open(P4).read()
if '<item>panfork</item>' not in s4:
    print("FEARWIRE SKIP: MC20 dead renderers already removed")
else:
    drop = [
        '        <item>Panfork (GL — open-source Panfrost on kbase)</item>\n',
        '        <item>@string/mcl_setting_renderer_vulkan_zink</item>\n',
        '        <item>@string/mcl_setting_renderer_ltw</item>\n',
        '        <item>panfork</item> <!-- MC20 Panfork: open-source Panfrost GL directly on the kbase kernel driver, via OSMesa -->\n',
        '        <item>vulkan_zink</item> <!-- zink with OpenGL -->\n',
        '        <item>opengles3_ltw</item> <!-- GL Core on GLES wrapper with GL3/4 -->\n',
    ]
    for d in drop:
        if d in s4:
            s4 = s4.replace(d, '', 1)
        else:
            fail("MC20 headings line not found: %r" % d[:60])
    if '<item>panfork</item>' in s4 or 'renderer_ltw' in s4 or '<item>vulkan_zink</item>' in s4:
        fail("MC20 removal incomplete - leftovers remain")
    open(P4, 'w').write(s4)
    print("FEARWIRE OK: MC20 removed Panfork + old Zink + LTW from renderer menu")

print("FEARWIRE DONE")
