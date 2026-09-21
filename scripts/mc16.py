# MC16: Turnip-Zink block-glitch fix (Mali / proprietary system Vulkan)
import io, sys

def rd(p):
    with io.open(p, encoding='utf-8') as f: return f.read()

def wr(p, s):
    with io.open(p, 'w', encoding='utf-8', newline='') as f: f.write(s)

base = 'app_pojavlauncher/src/main'

# ---------- 1. JREUtils.java : real conservative env for zink on system Vulkan ----------
p = base + '/java/net/kdt/pojavlaunch/utils/JREUtils.java'
t = rd(p)
old = '''                envMap.put("MESA_GLSL_CACHE_DISABLE", "false");
                envMap.put("FEAR_RENDERER", renderer);
                break;'''
new = '''                envMap.put("MESA_GLSL_CACHE_DISABLE", "false");
                envMap.put("FEAR_RENDERER", renderer);
                // MC16: block-glitch fix. On Mali (or any non-Adreno GPU) Zink runs on the
                // proprietary system Vulkan driver, which is non-conformant for Zink
                // (missing fillModeNonSolid/shaderClipDistance/logicOp) and mishandles
                // Zink's out-of-order command submission -> flickering/corrupted chunks.
                // Force conservative, in-order submission.
                if (!GLInfoUtils.getGlInfo().isAdreno()) {
                    envMap.put("ZINK_DEBUG", "noreorder");
                    envMap.put("GALLIUM_THREAD", "0");
                    envMap.put("mesa_glthread", "false");
                    Logger.appendToLog("[TurnipZink] System Vulkan (Mali/proprietary) detected - block-glitch fix active: ZINK_DEBUG=noreorder, GALLIUM_THREAD=0, mesa_glthread=false");
                } else {
                    envMap.put("mesa_glthread", "false");
                }
                break;'''
assert t.count(old) == 1, 'JREUtils anchor'
t = t.replace(old, new)
wr(p, t)

# ---------- 2. lwjgl_dlopen_hook.c : stop forcing lazy descriptors / NOERROR ----------
p = base + '/jni/jvm_hooks/lwjgl_dlopen_hook.c'
t = rd(p)
old = '''    setenv("LIBGL_NOERROR", "1", 1);
    setenv("mesa_glthread", "false", 1);
    unsetenv("LIBGL_ES");'''
new = '''    setenv("mesa_glthread", "false", 1);
    unsetenv("LIBGL_ES");'''
assert t.count(old) == 1, 'native anchor'
t = t.replace(old, new)
old = '''    unsetenv("MESA_VK_WSI_PRESENT_MODE");
    unsetenv("MESA_PRESENT_MODE");
    setenv("ZINK_DESCRIPTORS", "lazy", 1);
}'''
new = '''    unsetenv("MESA_VK_WSI_PRESENT_MODE");
    unsetenv("MESA_PRESENT_MODE");
    /* MC16: do NOT force ZINK_DESCRIPTORS/lazy here - it overwrites the
       ZINK_DEBUG=noreorder / conservative env applied from Java
       (JREUtils.setupRendererEnv) for Mali / system-Vulkan devices. */
}'''
assert t.count(old) == 1, 'native anchor 2'
t = t.replace(old, new)
wr(p, t)

# ---------- 3. TurnipZinkManager.java : real setenv instead of placebo System.setProperty ----------
p = base + '/java/net/kdt/pojavlaunch/gpu/TurnipZinkManager.java'
t = rd(p)
old = '''    public static void applyGPUWorkarounds() {
        String gpuModel = getGpuModel();
        Log.i(TAG, "Detected GPU: " + gpuModel);
        
        // Get workaround environment variables for this GPU
        String[] workarounds = ZinkWorkarounds.getWorkaroundEnv(gpuModel);
        
        if (workarounds.length > 0) {
            Log.i(TAG, "Applying " + workarounds.length + " workarounds for GPU");
            for (String workaround : workarounds) {
                String[] parts = workaround.split("=", 2);
                if (parts.length == 2) {
                    System.setProperty(parts[0], parts[1]);
                    Log.i(TAG, "Applied workaround: " + parts[0] + "=" + parts[1]);
                }
            }
        } else {
            Log.i(TAG, "No specific workarounds needed for GPU: " + gpuModel);
        }
        
        // Apply universal Zink optimizations
        applyUniversalWorkarounds();
    }
    
    /**
     * Apply universal Zink optimizations
     */
    private static void applyUniversalWorkarounds() {
        // Ensure Zink driver is used
        System.setProperty("MESA_LOADER_DRIVER_OVERRIDE", "zink");
        System.setProperty("GALLIUM_DRIVER", "zink");
        System.setProperty("MESA_GLSL_VERSION_OVERRIDE", "460");
        System.setProperty("MESA_GL_VERSION_OVERRIDE", "4.6");
        
        // Shader cache settings
        System.setProperty("MESA_SHADER_CACHE_DIR", 
            android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/Android/data/git.artdeell.mojo.debug/cache");
        System.setProperty("MESA_GLSL_CACHE_DIR", 
            android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/Android/data/git.artdeell.mojo.debug/cache");
        System.setProperty("MESA_GLSL_CACHE_DISABLE", "false");
        
        // Performance and compatibility
        System.setProperty("LIBGL_NOINTOVLHACK", "1");
        System.setProperty("LIBGL_NOERROR", "1");
        System.setProperty("LIBGL_NORMALIZE", "1");
        System.setProperty("LIBGL_MIPMAP", "3");
        
        // Turnip-Zink specific
        System.setProperty("POJAV_VSYNC_IN_ZINK", "1");
        System.setProperty("vblank_mode", "0");
        System.setProperty("FORCE_VSYNC", "true");
        System.setProperty("FEAR_RENDERER", "turnip_zink");
        
        Log.i(TAG, "Applied universal Zink workarounds");
    }'''
new = '''    public static void applyGPUWorkarounds() {
        String gpuModel = getGpuModel();
        Log.i(TAG, "Detected GPU: " + gpuModel);

        // Get workaround environment variables for this GPU
        String[] workarounds = ZinkWorkarounds.getWorkaroundEnv(gpuModel);

        if (workarounds.length > 0) {
            Log.i(TAG, "Applying " + workarounds.length + " workarounds for GPU");
            for (String workaround : workarounds) {
                String[] parts = workaround.split("=", 2);
                if (parts.length == 2) {
                    // MC16: these must be REAL process environment variables -
                    // Mesa (native) never reads Java System properties, and the
                    // previous fake ZINK_* variables (ZINK_FEATURES,
                    // ZINK_EMULATE_LOGIC_OP, ...) are not read by Mesa at all.
                    try {
                        android.system.Os.setenv(parts[0], parts[1], true);
                        Log.i(TAG, "Applied workaround: " + parts[0] + "=" + parts[1]);
                    } catch (Throwable t) {
                        Log.w(TAG, "setenv failed for " + parts[0], t);
                    }
                }
            }
        } else {
            Log.i(TAG, "No specific workarounds needed for GPU: " + gpuModel);
        }
        // NOTE (MC16): the old applyUniversalWorkarounds() only set Java System
        // properties, which the native Mesa stack never reads - it was inert.
        // The real launch-time environment is set by JREUtils.setEnviroimentForGame().
    }'''
assert t.count(old) == 1, 'TurnipZinkManager anchor'
t = t.replace(old, new)
wr(p, t)

# ---------- 4. ZinkWorkarounds.java : real Mesa env vars + fixed pattern parsing ----------
p = base + '/java/net/kdt/pojavlaunch/gpu/ZinkWorkarounds.java'
t = rd(p)
start = t.index('    private static final String[][] GPU_WORKAROUNDS = {')
end = t.index('    /**\n     * Get workaround environment variables for a specific GPU model.')
new_block = '''    private static final String[][] GPU_WORKAROUNDS = {
        {
            // Mali GPUs run Zink on the ARM proprietary system Vulkan driver, which is
            // non-conformant for Zink (no fillModeNonSolid / shaderClipDistance / logicOp)
            // and mishandles out-of-order command submission -> flickering blocks/chunks.
            // REAL Mesa environment variables (verified against the bundled
            // Mesa 25.1.4 OSMesa/Zink build):
            //   ZINK_DEBUG=noreorder  - do not reorder command streams
            //   GALLIUM_THREAD=0      - disable gallium threaded context
            //   mesa_glthread=false    - disable Mesa GL thread
            "mali-g615", "mali g615", "mali", "arm mali",
            "ZINK_DEBUG=noreorder",
            "GALLIUM_THREAD=0",
            "mesa_glthread=false"
        }
    };

'''
t = t[:start] + new_block + t[end:]
old = '''        for (String[] workaround : GPU_WORKAROUNDS) {
            // First elements are GPU patterns to match
            int patternCount = workaround.length - 1;
            boolean matches = false;
            
            for (int i = 0; i < patternCount; i++) {
                if (gpuLower.contains(workaround[i].toLowerCase())) {
                    matches = true;
                    break;
                }
            }
            
            if (matches) {
                // Return all elements after the patterns (the environment variables)
                String[] envVars = new String[workaround.length - patternCount];
                System.arraycopy(workaround, patternCount, envVars, 0, envVars.length);
                return envVars;
            }
        }'''
new = '''        for (String[] workaround : GPU_WORKAROUNDS) {
            // Elements after the first KEY=VALUE string are env vars; those before are GPU patterns.
            int firstEnv = -1;
            for (int i = 0; i < workaround.length; i++) {
                if (workaround[i].indexOf('=') >= 0) { firstEnv = i; break; }
            }
            if (firstEnv <= 0) continue;

            for (int i = 0; i < firstEnv; i++) {
                if (gpuLower.contains(workaround[i].toLowerCase())) {
                    String[] envVars = new String[workaround.length - firstEnv];
                    System.arraycopy(workaround, firstEnv, envVars, 0, envVars.length);
                    return envVars;
                }
            }
        }'''
assert t.count(old) == 1, 'ZinkWorkarounds parse anchor'
t = t.replace(old, new)
wr(p, t)
print('MC16 OK')
