package net.kdt.pojavlaunch.utils;

import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_DUMP_SHADERS;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_VSYNC_IN_ZINK;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_ZINK_PREFER_SYSTEM_DRIVER;

import android.content.*;
import android.system.*;
import android.util.*;

import androidx.appcompat.app.AppCompatActivity;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.*;
import net.kdt.pojavlaunch.*;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.plugins.LibraryPlugin;
import net.kdt.pojavlaunch.prefs.*;

public class JREUtils {
    public static void redirectAndPrintJRELog() {
        Log.i("jrelog", "FEAR CORE LOG INITIALIZED");
        new Thread(() -> {
            int failCount = 0;
            while (failCount < 15) {
                try {
                    ProcessBuilder pb = new ProcessBuilder("logcat", "-v", "tag", "-T", "1").redirectErrorStream(true);
                    java.lang.Process p = pb.start();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"), 32768)) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.contains("jrelog") || line.contains("LIBGL") || line.contains("NativeInput") || line.contains("FEAR") || line.contains("Mesa") || line.contains("OSMesa")) {
                                Logger.appendToLog(line + "\n");
                            }
                        }
                    }
                    int exitCode = p.waitFor();
                    if (exitCode != 0) {
                        failCount++;
                        Thread.sleep(500 * failCount);
                    }
                } catch (Exception e) {
                    failCount++;
                }
            }
            Logger.appendToLog("[FEAR LOG] FATAL: STREAMING DISCONNECTED PERMANENTLY.");
        }).start();
    }

    private static void overrideEnvVars(Map<String, String> envMap) throws IOException {
        File customEnvFile = new File(Tools.DIR_GAME_HOME, "custom_env.txt");
        if(!customEnvFile.exists() || !customEnvFile.isFile()) return;
        BufferedReader reader = new BufferedReader(new FileReader(customEnvFile));
        String line;
        while ((line = reader.readLine()) != null) {
            int index = line.indexOf("=");
            if (index > 0) envMap.put(line.substring(0, index), line.substring(index + 1));
        }
        reader.close();
    }

    private static void scrubPojavDetectorEnv() {
        for (String key : new String[]{"POJAV_RENDERER", "POJAV_LAUNCHER"}) {
            try { Os.unsetenv(key); } catch (Throwable t) {}
            try {
                Class<?> pe = Class.forName("java.lang.ProcessEnvironment");
                for (String fieldName : new String[]{"theEnvironment", "theUnmodifiableEnvironment", "theCaseInsensitiveEnvironment"}) {
                    try {
                        Field field = pe.getDeclaredField(fieldName);
                        field.setAccessible(true);
                        Object mapObj = field.get(null);
                        if (mapObj instanceof Map) ((Map<?, ?>) mapObj).remove(key);
                    } catch (NoSuchFieldException ignored) {}
                }
            } catch (Throwable t) {}
        }
        Logger.appendToLog("[TurnipZink] Scrubbed renderer detector env (Sodium bypass)");
    }

    public static void setupAngleEnv(Context ctx, Map<String, String> envMap) {
        if (!LauncherPreferences.PREF_USE_ANGLE) return;
        LibraryPlugin angle = LibraryPlugin.discoverPlugin(ctx, LibraryPlugin.ID_ANGLE_PLUGIN);
        if (angle == null) return;
        String[] angleLibs = {"libEGL_angle.so", "libGLESv2_angle.so"};
        if (!angle.checkLibraries(angleLibs)) return;
        envMap.put("LIBGL_EGL", angle.resolveAbsolutePath(angleLibs[0]));
        envMap.put("LIBGL_GLES", angle.resolveAbsolutePath(angleLibs[1]));
    }

    public static void setupFfmpegEnv(Context ctx, Map<String, String> envMap) {
        LibraryPlugin ffmpeg = LibraryPlugin.discoverPlugin(ctx, LibraryPlugin.ID_FFMPEG_PLUGIN);
        if(ffmpeg == null) return;
        envMap.put("FEAR_FFMPEG_PATH", ffmpeg.resolveAbsolutePath("libffmpeg.so"));
    }

    public static void setupRendererEnv(Map<String, String> envMap, String renderer) {
        switch(renderer) {
            case "panvk_zink":
                Logger.appendToLog("[PanVK] Initializing Zink over Mesa PanVK (Mali open-source Vulkan ICD)...");
                envMap.put("GALLIUM_DRIVER", "zink");
                envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "zink");
                envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
                envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");
                envMap.put("vblank_mode", "0");
                envMap.put("MESA_GLSL_CACHE_DISABLE", "false");
                envMap.put("FEAR_RENDERER", renderer);
                envMap.put("PAN_I_WANT_A_BROKEN_VULKAN_DRIVER", "1");
                envMap.put("mesa_glthread", "false");
                envMap.put("GALLIUM_THREAD", "0");
                envMap.put("ZINK_DEBUG", "noreorder,sync");
                envMap.put("ZINK_MALI_NOBINDLESS", "1");
                envMap.put("ZINK_MALI_NOCOHERENT", "1");
                envMap.put("ZINK_MALI_NOCOMPUTEUPLOAD", "1");
                envMap.put("ZINK_MALI_NOREUSE", "1");
                break;
            case "holy_zink_kopper":
                Logger.appendToLog("[HolyZink] Initializing Zink renderer (OSMesa + Mesa Zink over the system Vulkan driver)...");
                envMap.put("GALLIUM_DRIVER", "zink");
                envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "zink");
                envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
                envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");
                envMap.put("vblank_mode", "0");
                envMap.put("MESA_GLSL_CACHE_DISABLE", "false");
                envMap.put("FEAR_RENDERER", renderer);
                if (!GLInfoUtils.getGlInfo().isAdreno()) {
                    // Quasar performance profile (Mali).
                    //
                    // The old block was tuned purely for correctness and gave away a
                    // lot of frame rate for nothing you can see on screen:
                    //
                    //   ZINK_DEBUG=sync       serialises every batch. It is a stall,
                    //                         not a fix, and it is the single biggest
                    //                         reason Quasar crawled at ~20 fps.
                    //   GALLIUM_THREAD=0 and
                    //   mesa_glthread=false   kept all GL submission on the render
                    //                         thread, so a heavy frame also stalled
                    //                         camera movement.
                    //
                    // Threading is back on, descriptor updates are lazy (far fewer
                    // descriptor sets rewritten per draw) and GL error checking is
                    // off. None of that changes what a frame looks like.
                    //
                    // The Mali workarounds that actually stop crashes stay on:
                    // bindless, coherent-memory handling and the compute-upload path.
                    // Multi-draw is left to the driver again - this is a G615, well
                    // past the pre-G710 parts that could not do it, and allowing it
                    // is what lets Sodium's batched terrain draw at speed.
                    envMap.put("ZINK_DEBUG", "noreorder");
                    envMap.put("ZINK_DESCRIPTORS", "lazy");
                    envMap.put("GALLIUM_THREAD", "1");
                    envMap.put("mesa_glthread", "true");
                    envMap.put("MESA_NO_ERROR", "1");
                    envMap.put("ZINK_MALI_NOBINDLESS", "1");
                    envMap.put("ZINK_MALI_NOCOHERENT", "1");
                    envMap.put("ZINK_MALI_NOCOMPUTEUPLOAD", "1");
                    envMap.put("ZINK_MALI_NOREUSE", "1");
                } else {
                    // The same threading and descriptor wins on Adreno, which needs
                    // none of the Mali workarounds.
                    envMap.put("mesa_glthread", "true");
                    envMap.put("ZINK_DESCRIPTORS", "lazy");
                    envMap.put("MESA_NO_ERROR", "1");
                }
                // Pin the game's hot threads onto the big cores, the same way the
                // LTW profile does - it is most of why LTW feels smoother.
                envMap.put("FEAR_BIG_CORE_AFFINITY", "1");
                break;
            case "turnip_zink":
            case "vulkan_zink":
            default:
                Logger.appendToLog("[TurnipZink] Initializing Zink renderer (OSMesa + Mesa Zink)...");
                envMap.put("GALLIUM_DRIVER", "zink");
                envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "zink");
                envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
                envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");
                envMap.put("vblank_mode", "0");
                envMap.put("MESA_GLSL_CACHE_DISABLE", "false");
                envMap.put("FEAR_RENDERER", renderer != null ? renderer : "turnip_zink");
                if (!GLInfoUtils.getGlInfo().isAdreno()) {
                    envMap.put("ZINK_DEBUG", "noreorder,sync");
                    envMap.put("GALLIUM_THREAD", "0");
                    envMap.put("mesa_glthread", "false");
                } else {
                    envMap.put("mesa_glthread", "false");
                }
                break;
        }
    }

    public static void setEnviroimentForGame(Context context, String renderer) throws Throwable {
        Map<String, String> envMap = new ArrayMap<>();
        envMap.put("LIBGL_MIPMAP", "3");
        envMap.put("LIBGL_NOERROR", "1");
        envMap.put("LIBGL_NOINTOVLHACK", "1");
        envMap.put("LIBGL_NORMALIZE", "1");
        if(PREF_DUMP_SHADERS) envMap.put("LIBGL_VGPU_DUMP", "1");
        if(PREF_VSYNC_IN_ZINK) envMap.put("FEAR_VSYNC_IN_ZINK", "1");

        boolean isZink = "turnip_zink".equals(renderer) || "vulkan_zink".equals(renderer) || "holy_zink_kopper".equals(renderer) || "panvk_zink".equals(renderer);
        if (!isZink) {
            envMap.put("LIBGL_ES", (String) ExtraCore.getValue(ExtraConstants.OPEN_GL_VERSION));
        }

        if ("opengles3_ltw".equals(renderer)) {
            // FEARPATCH: the LTW performance profile. LTW is a prebuilt native
            // library (libltw.so) with no source in this repo, so it is tuned
            // entirely through the environment. Every var below either moves work
            // off the render thread or skips a redundant per-call check - none of
            // them change what a frame looks like, and the render resolution,
            // mipmap and texture-filtering settings are deliberately untouched.
            //
            //   LIBGL_ES / FEAREXEC_EGL / FEAR_RENDERER - select the LTW EGL
            //     (libltw.so). Without POJAVEXEC_EGL the native ctxbridge never
            //     picks LTW at all.
            //   LIBGL_NOERROR / MESA_NO_ERROR - skip the per-call GL error checks,
            //     each of which forces a driver sync on the render thread.
            //   mesa_glthread - keep GL submission off the render thread so a busy
            //     frame cannot stall camera movement.
            //   FEAR_BIG_CORE_AFFINITY - pin the game's hot threads (render, client,
            //     main, JIT) onto the performance CPU cluster.
            // The shader/GLSL cache vars (MESA_GLSL_CACHE_DIR, MESA_SHADER_CACHE_DIR,
            // XDG_CACHE_HOME, XDG_CONFIG_HOME, HOME) are set for every renderer
            // further down, so LTW compiles its shaders once and reuses them.
            // FORCE_VSYNC / FEAR_VSYNC_IN_ZINK are deliberately NOT forced on for
            // LTW: either one caps the frame rate at the panel refresh.
            envMap.put("LIBGL_ES", "3");
            envMap.put("FEAREXEC_EGL", "libltw.so");
            envMap.put("FEAR_RENDERER", "opengles3_ltw");
            envMap.put("LIBGL_NOERROR", "1");
            envMap.put("MESA_NO_ERROR", "1");
            envMap.put("mesa_glthread", "true");
            envMap.put("FEAR_BIG_CORE_AFFINITY", "1");
        }

        if ("opengles2".equals(renderer)) {
            // The low-end profile. gl4es turns desktop OpenGL into OpenGL ES 2, which is
            // what lets this launcher run on phones with no Vulkan and no OpenGL ES 3 at
            // all. On hardware that weak the translation cost is the whole budget, so
            // everything here is about making that translation cheap: no per-call GL error
            // checks, GL submission kept off the render thread, and the hot threads pinned
            // to whatever fast cores the device has.
            Logger.appendToLog("[GLES2] Applying the low-end profile (gl4es)...");
            envMap.put("LIBGL_ES", "2");
            envMap.put("FEAR_RENDERER", "opengles2");
            envMap.put("LIBGL_NOERROR", "1");
            envMap.put("MESA_NO_ERROR", "1");
            envMap.put("mesa_glthread", "true");
            envMap.put("LIBGL_NORMALIZE", "1");
            envMap.put("FEAR_BIG_CORE_AFFINITY", "1");
        }

        if ("fear_monster".equals(renderer)) {
            // The direct-Vulkan profile. None of the GL tuning applies here - there is no
            // translation layer to tune, which is the entire point of this path. What the
            // launcher contributes is a reachable Vulkan device: the loader is what the
            // engine and LWJGL's Vulkan bindings open, and the ICD choice mirrors the
            // renderer logic (Mesa Turnip on Adreno, the vendor driver everywhere else).
            Logger.appendToLog("[FearMonster] Applying the direct-Vulkan profile...");
            envMap.put("FEAR_RENDERER", "fear_monster");
            envMap.put("FEAR_MONSTER_ENGINE", "1");
            envMap.put("FEAR_BIG_CORE_AFFINITY", "1");
            envMap.put("MESA_NO_ERROR", "1");
            if (GLInfoUtils.getGlInfo().isAdreno() && !PREF_ZINK_PREFER_SYSTEM_DRIVER) {
                setUseTurnip(true);
                Logger.appendToLog("[FearMonster] Adreno: requesting the Mesa Turnip Vulkan ICD");
            }
        }

        envMap.put("FORCE_VSYNC", String.valueOf(LauncherPreferences.PREF_FORCE_VSYNC));
        envMap.put("MESA_GLSL_CACHE_DIR", Tools.DIR_CACHE.getAbsolutePath());
        envMap.put("MESA_SHADER_CACHE_DIR", Tools.DIR_CACHE.getAbsolutePath());
        // Keep the shader cache in one file and give it room to actually hold a modpack's
        // shaders. The default evicts aggressively, and every eviction is a shader that
        // has to be recompiled mid-game - which is felt as a stutter, not as a lower
        // average. The single-file form is also much quicker to open and index.
        envMap.put("MESA_SHADER_CACHE_MAX_SIZE", "1G");
        envMap.put("MESA_DISK_CACHE_SINGLE_FILE", "1");
        envMap.put("XDG_CACHE_HOME", Tools.DIR_CACHE.getAbsolutePath());
        envMap.put("XDG_CONFIG_HOME", Tools.DIR_CACHE.getAbsolutePath());
        envMap.put("HOME", Tools.DIR_CACHE.getAbsolutePath());
        envMap.put("force_glsl_extensions_warn", "true");
        envMap.put("allow_higher_compat_version", "true");
        envMap.put("allow_glsl_extension_directive_midshader", "true");
        File modRuntimeDir = new File(Tools.DIR_CACHE, "app_runtime_mod");
        if (!modRuntimeDir.exists()) modRuntimeDir.mkdirs();
        envMap.put("MOD_ANDROID_RUNTIME", modRuntimeDir.getAbsolutePath());

        setupAngleEnv(context, envMap);
        setupFfmpegEnv(context, envMap);
        // FEARPATCH: setupRendererEnv() is a Zink-only env block.
        if (isZink) setupRendererEnv(envMap, renderer);

        envMap.put("FEAR_NATIVEDIR", Tools.NATIVE_LIB_DIR);
        if (isZink) {
            envMap.put("LIB_MESA_NAME", "libOSMesa_8.so");
        }

        if(LauncherPreferences.PREF_BIG_CORE_AFFINITY) envMap.put("FEAR_BIG_CORE_AFFINITY", "1");
        if ("panvk_zink".equals(renderer)) {
            setUsePanvk(true);
            Logger.appendToLog("[PanVK] Requesting Mesa PanVK ICD (libvulkan_panfrost.so)");
        } else if (GLInfoUtils.getGlInfo().isAdreno() && !PREF_ZINK_PREFER_SYSTEM_DRIVER) {
            setUseTurnip(true);
        } else if (GLInfoUtils.getGlInfo().isArm() && ("turnip_zink".equals(renderer) || "vulkan_zink".equals(renderer))) {
            setUsePanvk(true);
            Logger.appendToLog("[PanVK] Mali detected — using PanVK ICD under Zink");
        }
        if(LauncherPreferences.PREF_FREEDRENO_SYSMEM) {
            envMap.put("FD_MESA_DEBUG", "sysmem");
            envMap.put("TU_DEBUG", "sysmem");
        }

        overrideEnvVars(envMap);
        for (Map.Entry<String, String> env : envMap.entrySet()) {
            Logger.appendToLog("Added custom env: " + env.getKey() + "=" + env.getValue());
            try { Os.setenv(env.getKey(), env.getValue(), true); } catch (NullPointerException exception) {
                Log.e("JREUtils", exception.toString());
            }
        }
        // The renderer/native env vars are published under FEAR_* names now. The
        // prebuilt native libraries still look for the old POJAV_* names, so set
        // those as well - silently, so the launcher's own log never mentions Pojav.
        for (Map.Entry<String, String> env : envMap.entrySet()) {
            String key = env.getKey();
            String legacy;
            if (key.startsWith("FEAREXEC_")) legacy = "POJAVEXEC_" + key.substring("FEAREXEC_".length());
            else if (key.startsWith("FEAR_")) legacy = "POJAV_" + key.substring("FEAR_".length());
            else continue;
            try { Os.setenv(legacy, env.getValue(), true); } catch (Throwable ignored) {}
        }
        // FEARPATCH: Sodium aborts with "PojavLauncher is not supported"
        // whenever it sees POJAV_LAUNCHER, so scrub the detector env for every
        // renderer, not just Zink.
        scrubPojavDetectorEnv();
    }

    public static void launchJavaVM(final AppCompatActivity activity, final Runtime runtime, File gameDirectory, final List<String> JVMArgs, final String userArgsString) throws Throwable {
        // If this process dies with the game still marked as running, the next launcher
        // start knows the run never came back and can say so.
        net.kdt.pojavlaunch.utils.FearCrashGuard.markLaunching(activity);
        Tools.fullyExit();
    }

    public static ArrayList<String> parseJavaArguments(String args){
        ArrayList<String> parsedArguments = new ArrayList<>(0);
        args = args.trim().replace(" ", "");
        String[] separators = new String[]{"-XX:-","-XX:+", "-XX:", "--", "-D", "-X", "-javaagent:", "-verbose"};
        for(String prefix : separators){
            while (true){
                int start = args.indexOf(prefix);
                if(start == -1) break;
                int end = -1;
                for(String separator: separators){
                    int tempEnd = args.indexOf(separator, start + prefix.length());
                    if(tempEnd == -1) continue;
                    if(end == -1){ end = tempEnd; continue; }
                    end = Math.min(end, tempEnd);
                }
                if(end == -1) end = args.length();
                String parsedSubString = args.substring(start, end);
                args = args.replace(parsedSubString, "");
                if(parsedSubString.indexOf('=') == parsedSubString.lastIndexOf('=')) {
                    int arraySize = parsedArguments.size();
                    if(arraySize > 0){
                        String lastString = parsedArguments.get(arraySize - 1);
                        if(lastString.charAt(lastString.length() - 1) == ',' || parsedSubString.contains(",")){
                            parsedArguments.set(arraySize - 1, lastString + parsedSubString);
                            continue;
                        }
                    }
                    parsedArguments.add(parsedSubString);
                }
            }
        }
        return parsedArguments;
    }

    public static String loadGraphicsLibrary(String renderer){
        return loadGraphicsLibrary(renderer, null);
    }

    /**
     * FEARPATCH: plugin renderers (MobileGlues, etc.) live inside a third-party
     * app's native library directory. loadGraphicsLibrary has no Context, so the
     * caller - which does have one - resolves the plugin library path and hands
     * it down here. pluginLibraryPath is the absolute path to the plugin's
     * libmobileglues.so, or null when the renderer is not a plugin or the plugin
     * could not be resolved.
     */
    public static String loadGraphicsLibrary(String renderer, String pluginLibraryPath){
        String renderLibrary;
        boolean useGles;
        boolean bypassNamespace = false;
        boolean preloadVk = true;
        int glesVersion;

        if (renderer != null && renderer.startsWith("plugin:")) {
            if (pluginLibraryPath != null && new File(pluginLibraryPath).exists()) {
                // Load the renderer library that actually lives inside the plugin app.
                Logger.appendToLog("[MobileGlues] Loading plugin renderer library " + pluginLibraryPath);
                if (!configureRenderspec(pluginLibraryPath, false, true, 3)) {
                    Log.e("RENDER_LIBRARY", "Failed to load plugin renderer library " + pluginLibraryPath);
                    return null;
                }
                return pluginLibraryPath;
            }
            Log.w("RENDER_LIBRARY", "Plugin renderer " + renderer
                    + " has no loadable libmobileglues.so, falling back to Turnip Zink");
            renderer = "turnip_zink";
        }

        switch (renderer){
            case "panvk_zink":
                Logger.appendToLog("[PanVK] Loading Mesa OSMesa + PanVK ICD path...");
                renderLibrary = "libOSMesa_8.so";
                useGles = false;
                bypassNamespace = true;
                glesVersion = 3;
                if(preloadVk) preloadVulkan();
                break;
            case "holy_zink_kopper":
                Logger.appendToLog("[HolyZink] Loading Mesa OSMesa bridge (zink over the system Vulkan driver)...");
                renderLibrary = "libOSMesa_8.so";
                useGles = false;
                bypassNamespace = true;
                glesVersion = 3;
                if(preloadVk) preloadVulkan();
                break;
            case "opengles2":
                Logger.appendToLog("[GLES2] Loading gl4es (desktop GL -> OpenGL ES 2)...");
                renderLibrary = "libgl4es_114.so";
                useGles = true;
                glesVersion = 2;
                break;
            case "fear_monster":
                // The direct-Vulkan path. Nothing here translates OpenGL: the engine
                // renders straight to Vulkan, so the launcher's whole job is to make a
                // Vulkan device reachable. There is no GL library to load, which is why
                // the "render library" is the Vulkan loader itself and the GLES switches
                // are left off. Handing the engine the window's Vulkan surface is the
                // remaining native work; until that lands this case is unreachable,
                // because no renderer id selects it.
                Logger.appendToLog("[FearMonster] Direct-Vulkan path: no GL layer, the engine owns the frame");
                // Deliberately no configureRenderspec() here, unlike every other case.
                // That call builds an EGL/GL context, and on this path there is no GL
                // context to build - the engine opens Vulkan itself. Returning the loader
                // directly lets the launch proceed without pretending a GL driver was
                // configured. This is the "no-GL render spec": the spec is that there is
                // none.
                return "libvulkan.so";
            case "opengles3_ltw":
                renderLibrary = "libltw.so";
                useGles = true;
                glesVersion = 3;
                break;
            case "turnip_zink":
            case "vulkan_zink":
            default:
                Logger.appendToLog("[TurnipZink] Loading real Mesa OSMesa (libOSMesa_8.so)...");
                renderLibrary = "libOSMesa_8.so";
                useGles = false;
                bypassNamespace = true;
                glesVersion = 3;
                if(preloadVk) preloadVulkan();
                break;
        }

        if (!configureRenderspec(renderLibrary, bypassNamespace, useGles, glesVersion)) {
            Log.e("RENDER_LIBRARY","Failed to load renderer " + renderLibrary );
            return null;
        }
        return renderLibrary;
    }

    public static int getDetectedVersion() {
        return GLInfoUtils.getGlInfo().glesMajorVersion;
    }
    public static native int chdir(String path);
    public static native void setLdLibraryPath(String ldLibraryPath);
    public static native boolean configureRenderspec(String eglPath, boolean useLoaderBypass, boolean useGles, int glesVersion);
    public static native void preloadVulkan();

    /**
     * Fear Monster: opens the Vulkan loader for the direct-Vulkan path. Declared here
     * but deliberately not called yet - the render path that would use it is not
     * reachable, and nothing loads fearmonster_vk, so this cannot affect a session on
     * any other renderer.
     */
    public static native boolean nativeFearMonsterLoadVulkan(String path);
    public static native void setUseTurnip(boolean enable);
    public static native void setUsePanvk(boolean enable);

    public static volatile int sFearRotateDir = 90;

    public static native void setupBridgeWindow(android.view.Surface surface);
    public static native void releaseBridgeWindow();

    public static native void initFearShaderEngine(String cachePath, int version);
    public static native void destroyFearShaderEngine();
    public static native String getShaderCachePath();
    public static native void clearShaderCache();
    public static native int getTranslatedShaderCount();

    /* FEARPATCH native performance engine: pins the game's hot threads
     * (Render thread / Client thread / main / JIT compilers) onto the
     * performance CPU cluster. Non-root, best-effort, renderer-agnostic. */
    public static native int nativeFearPerfStart();
    public static native int nativeFearPinGameThreads();

    /* FEARFRAMECOUNT: monotonic count of frames the renderer has actually
     * presented (glfwSwapBuffers / eglSwapBuffers), kept by the native present
     * hooks. The lag report's frame rate is measured from this instead of being
     * guessed from a line of the game log. Returns 0 until the first frame. */
    public static native long getPresentedFrameCount();

    public static native boolean renderAWTScreenFrame(ByteBuffer tempBuffer);
    static {
        System.loadLibrary("pojavexec");
        System.loadLibrary("pojavexec_awt");
    }
}
