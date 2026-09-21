# MC17: Ultimate zink glitch fix for Mali / proprietary system Vulkan
# - mipmapLevels=0 (zink's async-compute mipmap generation corrupts terrain textures
#   on ARM proprietary drivers; MC block atlas used mipmaps x2)
# - ZINK_DEBUG=noreorder,sync (fully synchronized command submission: eliminates
#   every draw/upload race with the per-tick animated texture uploads)
import io

def rd(p):
    with io.open(p, encoding='utf-8') as f: return f.read()

def wr(p, s):
    with io.open(p, 'w', encoding='utf-8', newline='') as f: f.write(s)

# ---------- 1. GameRunner.java : mipmap off at launch for zink-on-Mali ----------
p = 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/jre/GameRunner.java'
t = rd(p)
old = '''        JREUtils.setEnviroimentForGame(activity, rendererName);
        JREUtils.chdir(instance.getGameDirectory().getAbsolutePath());'''
new = '''        JREUtils.setEnviroimentForGame(activity, rendererName);
        JREUtils.chdir(instance.getGameDirectory().getAbsolutePath());

        // MC17: Zink on Mali / proprietary system Vulkan - ultimate glitch fix.
        // Zink generates mipmaps on an async compute queue which corrupts terrain
        // texture data on ARM proprietary drivers, making block textures appear to
        // slide/move rapidly. Mipmaps are disabled at launch to keep the terrain
        // clean (ZINK_DEBUG=..,sync is set in JREUtils for the remaining races).
        if ((rendererName.equals("turnip_zink") || rendererName.equals("vulkan_zink"))
                && !GLInfoUtils.getGlInfo().isAdreno()) {
            try {
                MCOptionUtils.load(instance.getGameDirectory().getAbsolutePath());
                MCOptionUtils.set("mipmapLevels", "0");
                MCOptionUtils.save();
                try { net.kdt.pojavlaunch.Logger.appendToLog("[TurnipZink] MC17: mipmapLevels=0 + full-sync zink (Mali block texture glitch fix)"); } catch (Throwable ignored) {}
            } catch (Throwable t2) {
                Log.w("GameRunner", "MC17 mipmap tweak failed", t2);
            }
        }'''
assert t.count(old) == 1, 'GameRunner anchor'
t = t.replace(old, new)
wr(p, t)

# ---------- 2. JREUtils.java : upgrade ZINK_DEBUG to full sync mode ----------
p = 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/JREUtils.java'
t = rd(p)
old = '''                if (!GLInfoUtils.getGlInfo().isAdreno()) {
                    envMap.put("ZINK_DEBUG", "noreorder");
                    envMap.put("GALLIUM_THREAD", "0");
                    envMap.put("mesa_glthread", "false");
                    Logger.appendToLog("[TurnipZink] System Vulkan (Mali/proprietary) detected - block-glitch fix active: ZINK_DEBUG=noreorder, GALLIUM_THREAD=0, mesa_glthread=false");
                } else {'''
new = '''                if (!GLInfoUtils.getGlInfo().isAdreno()) {
                    // MC17: noreorder + sync = fully in-order, synchronized submission.
                    // Costs FPS but eliminates block-texture glitching on Mali.
                    envMap.put("ZINK_DEBUG", "noreorder,sync");
                    envMap.put("GALLIUM_THREAD", "0");
                    envMap.put("mesa_glthread", "false");
                    Logger.appendToLog("[TurnipZink] System Vulkan (Mali/proprietary) detected - MC17 ultimate fix active: ZINK_DEBUG=noreorder,sync, GALLIUM_THREAD=0, mesa_glthread=false, mipmapLevels=0");
                } else {'''
assert t.count(old) == 1, 'JREUtils anchor'
t = t.replace(old, new)
wr(p, t)
print('MC17 OK')
