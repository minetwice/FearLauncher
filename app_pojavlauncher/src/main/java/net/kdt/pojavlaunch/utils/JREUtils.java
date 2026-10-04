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
        Logger.appendToLog("[TurnipZink] Scrubbed POJAV_RENDERER/POJAV_LAUNCHER (Sodium bypass)");
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
        envMap.put("POJAV_FFMPEG_PATH", ffmpeg.resolveAbsolutePath("libffmpeg.so"));
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
                    envMap.put("ZINK_DEBUG", "noreorder,sync");
                    envMap.put("GALLIUM_THREAD", "0");
                    envMap.put("mesa_glthread", "false");
                    envMap.put("ZINK_MALI_NOBINDLESS", "1");
                    envMap.put("ZINK_MALI_NOCOHERENT", "1");
                    envMap.put("ZINK_MALI_NOCOMPUTEUPLOAD", "1");
                    envMap.put("ZINK_MALI_NOREUSE", "1");
                    // FEARPATCH: Mali pre-G710 cannot do multi-draw (drawCount>1),
                    // which crashes the driver during Sodium's batched terrain
                    // draws. Force Zink to emit single draws instead.
                    envMap.put("ZINK_MALI_NOMULTIDRAW", "1");
                } else {
                    envMap.put("mesa_glthread", "false");
                }
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
        if(PREF_VSYNC_IN_ZINK) envMap.put("POJAV_VSYNC_IN_ZINK", "1");

        boolean isZink = "turnip_zink".equals(renderer) || "vulkan_zink".equals(renderer) || "holy_zink_kopper".equals(renderer) || "panvk_zink".equals(renderer);
        if (!isZink) {
            envMap.put("LIBGL_ES", (String) ExtraCore.getValue(ExtraConstants.OPEN_GL_VERSION));
        }

        if ("opengles3_ltw".equals(renderer)) {
            // FEARPATCH: upstream sets these for LTW; without POJAVEXEC_EGL the
            // native ctxbridge never selects the LTW EGL.
            envMap.put("LIBGL_ES", "3");
            envMap.put("POJAVEXEC_EGL", "libltw.so");
            envMap.put("POJAV_RENDERER", "opengles3_ltw");
        }
        envMap.put("FORCE_VSYNC", String.valueOf(LauncherPreferences.PREF_FORCE_VSYNC));
        envMap.put("MESA_GLSL_CACHE_DIR", Tools.DIR_CACHE.getAbsolutePath());
        envMap.put("MESA_SHADER_CACHE_DIR", Tools.DIR_CACHE.getAbsolutePath());
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

        envMap.put("POJAV_NATIVEDIR", Tools.NATIVE_LIB_DIR);
        if (isZink) {
            envMap.put("LIB_MESA_NAME", "libOSMesa_8.so");
        }

        if(LauncherPreferences.PREF_BIG_CORE_AFFINITY) envMap.put("POJAV_BIG_CORE_AFFINITY", "1");
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
        String renderLibrary;
        boolean useGles;
        boolean bypassNamespace = false;
        boolean preloadVk = true;
        int glesVersion;

        if (renderer != null && renderer.startsWith("plugin:")) {
            Log.w("RENDER_LIBRARY", "Plugin renderer load failed, falling back to Turnip Zink");
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

    public static native boolean renderAWTScreenFrame(ByteBuffer tempBuffer);
    static {
        System.loadLibrary("pojavexec");
        System.loadLibrary("pojavexec_awt");
    }
}
