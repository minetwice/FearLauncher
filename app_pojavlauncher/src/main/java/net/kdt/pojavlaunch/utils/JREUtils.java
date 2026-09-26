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
                            if (line.contains("jrelog") || line.contains("LIBGL") || line.contains("NativeInput") || line.contains("FEAR") || line.contains("FearRender") || line.contains("Mesa") || line.contains("OSMesa")) {
                                Logger.appendToLog(line + "\n");
                            }
                        }
                    }

                    int exitCode = p.waitFor();
                    if (exitCode != 0) {
                        Log.w("jrelog-logcat", "Logcat link lost. Sync code: " + exitCode + ". Re-establishing...");
                        failCount++;
                        Thread.sleep(500 * failCount);
                    }
                } catch (Exception e) {
                    Log.e("jrelog-logcat", "Log stream error", e);
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

    /**
     * Sodium (and Create) call System.getenv("POJAV_RENDERER").
     * Os.unsetenv only updates libc — Java ProcessEnvironment is a separate cache.
     * Clear both so PostLaunchChecks does not abort.
     */
    private static void scrubPojavDetectorEnv() {
        for (String key : new String[]{"POJAV_RENDERER", "POJAV_LAUNCHER"}) {
            try {
                Os.unsetenv(key);
            } catch (Throwable t) {
                Log.w("JREUtils", "Os.unsetenv(" + key + ") failed: " + t);
            }
            try {
                Class<?> pe = Class.forName("java.lang.ProcessEnvironment");
                for (String fieldName : new String[]{
                        "theEnvironment",
                        "theUnmodifiableEnvironment",
                        "theCaseInsensitiveEnvironment"
                }) {
                    try {
                        Field field = pe.getDeclaredField(fieldName);
                        field.setAccessible(true);
                        Object mapObj = field.get(null);
                        if (mapObj instanceof Map) {
                            ((Map<?, ?>) mapObj).remove(key);
                        }
                    } catch (NoSuchFieldException ignored) {
                    }
                }
            } catch (Throwable t) {
                Log.w("JREUtils", "Java env scrub for " + key + " failed: " + t);
            }
        }
        Logger.appendToLog("[TurnipZink] Scrubbed POJAV_RENDERER/POJAV_LAUNCHER (Sodium bypass)");
    }

    public static void setupAngleEnv(Context ctx, Map<String, String> envMap) {
        if (!LauncherPreferences.PREF_USE_ANGLE) return;
        LibraryPlugin angle = LibraryPlugin.discoverPlugin(ctx, LibraryPlugin.ID_ANGLE_PLUGIN);
        if (angle == null) return;
        String[] angleLibs = {"libEGL_angle.so", "libGLESv2_angle.so"};
        if (!angle.checkLibraries(angleLibs)) {
            Log.e("AngleEnvSetup", "AnglePlugin exists, but the ANGLE libraries are not present. Is the plugin corrupted?");
            return;
        }
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
            case "panfork":
                // MC20: Panfork - open-source Panfrost Gallium driver talking
                // directly to the ARM kbase kernel driver, via OSMesa.
                // No proprietary userspace blob (the glitch source), no Vulkan,
                // no Zink translation layer. GL 3.3 via PAN_MESA_DEBUG=gl3.
                Logger.appendToLog("[Panfork] Initializing Panfork renderer (open-source Panfrost GL on kbase kernel driver)...");
                envMap.put("GALLIUM_DRIVER", "panfrost");
                envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "panfrost");
                envMap.put("PAN_MESA_DEBUG", "gl3,noafbc");
                envMap.put("vblank_mode", "0");
                envMap.put("MESA_GLSL_CACHE_DISABLE", "false");
                envMap.put("FEAR_RENDERER", renderer);
                break;
            case "fear_render":
                Logger.appendToLog("[FearRender] Initializing FearRender renderer (GL on host GLES - universal Mali/Adreno)...");
                envMap.put("FEAR_RENDERER", renderer);
                envMap.put("vblank_mode", "0");
                // [FearRender] MobileGlues tuning: config dir + shader-friendly defaults
                try {
                    java.io.File mgDir = new java.io.File(Tools.DIR_GAME_HOME, "MG");
                    java.io.File mgCfg = new java.io.File(mgDir, "config.json");
                    if (!mgCfg.exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        mgDir.mkdirs();
                        java.io.FileWriter fw = new java.io.FileWriter(mgCfg);
                        fw.write("{\"enableNoError\":2,\"enableExtComputeShader\":1,\"enableExtTimerQuery\":1,\"enableExtDirectStateAccess\":1}");
                        fw.close();
                    }
                    envMap.put("MG_DIR_PATH", mgDir.getAbsolutePath());
                    Logger.appendToLog("[FearRender] MobileGlues config dir: " + mgDir.getAbsolutePath());
                } catch (Throwable t) {
                    Logger.appendToLog("[FearRender] MobileGlues config setup failed: " + t);
                }
                break;
            case "panvk_zink":
                // MC19: Zink on the open-source PanVK (Panfrost) driver.
                // Clean path - no proprietary-driver workarounds needed.
                Logger.appendToLog("[PanVK] Initializing PanVK Zink renderer (open-source Panfrost Vulkan driver)...");
                envMap.put("GALLIUM_DRIVER", "zink");
                envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "zink");
                envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
                envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");
                envMap.put("vblank_mode", "0");
                envMap.put("MESA_GLSL_CACHE_DISABLE", "false");
                envMap.put("FEAR_RENDERER", renderer);
                envMap.put("PAN_MESA_DEBUG", "noafbc");
                // MC20: atlas-stage freeze fix - zink 25.1.4's deferred/reordered
                // submits race the kbase backend sync machinery (hang with no CS
                // error, stuck in kernel wait). Same proven full-sync workaround
                // as the turnip Mali path.
                envMap.put("ZINK_DEBUG", "noreorder,sync");
                envMap.put("GALLIUM_THREAD", "0");
                envMap.put("mesa_glthread", "false");
                Logger.appendToLog("[PanVK] MC20: full-sync zink enabled (ZINK_DEBUG=noreorder,sync, GALLIUM_THREAD=0, mipmapLevels=0)");
                break;
            case "turnip_zink":
            case "vulkan_zink":
                Logger.appendToLog("[TurnipZink] Initializing Zink renderer (OSMesa + Mesa Zink)...");
                envMap.put("GALLIUM_DRIVER", "zink");
                envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "zink");
                envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
                envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");
                envMap.put("vblank_mode", "0");
                envMap.put("MESA_GLSL_CACHE_DISABLE", "false");
                envMap.put("FEAR_RENDERER", renderer);
                // MC19: restored MC17 - Mesa 25.2.1 zink crashed at context creation
                // (SIGSEGV in OSMesaCreateContextAttribs) on non-Adreno system Vulkan.
                // Back on Mesa 25.1.4 with the proven in-order/sync workaround.
                if (!GLInfoUtils.getGlInfo().isAdreno()) {
                    envMap.put("ZINK_DEBUG", "noreorder,sync");
                    envMap.put("GALLIUM_THREAD", "0");
                    envMap.put("mesa_glthread", "false");
                    Logger.appendToLog("[TurnipZink] System Vulkan (Mali/proprietary) detected - MC17 fix (Mesa 25.1.4): ZINK_DEBUG=noreorder,sync, GALLIUM_THREAD=0, mesa_glthread=false, mipmapLevels=0");
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

        if(PREF_DUMP_SHADERS)
            envMap.put("LIBGL_VGPU_DUMP", "1");
        if(PREF_VSYNC_IN_ZINK)
            envMap.put("POJAV_VSYNC_IN_ZINK", "1");

        boolean isZink = "turnip_zink".equals(renderer) || "vulkan_zink".equals(renderer) || "panvk_zink".equals(renderer) || "panfork".equals(renderer);
        if (!isZink) {
            envMap.put("LIBGL_ES", (String) ExtraCore.getValue(ExtraConstants.OPEN_GL_VERSION));
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
        if (!modRuntimeDir.exists()) {
            modRuntimeDir.mkdirs();
        }
        envMap.put("MOD_ANDROID_RUNTIME", modRuntimeDir.getAbsolutePath());

        setupAngleEnv(context, envMap);
        setupFfmpegEnv(context, envMap);
        setupRendererEnv(envMap, renderer);

        envMap.put("POJAV_NATIVEDIR", Tools.NATIVE_LIB_DIR);
        if (isZink) {
            envMap.put("LIB_MESA_NAME", "panfork".equals(renderer) ? "libOSMesa_panfork.so" : "libOSMesa_8.so");
            // Do NOT set POJAV_RENDERER — Sodium treats it as hard fail.
            // Hooks detect Zink via GALLIUM_DRIVER=zink / FEAR_RENDERER.
        } else if (!"fear_render".equals(renderer)) {
            envMap.put("POJAV_RENDERER", renderer);
        }

        if(LauncherPreferences.PREF_BIG_CORE_AFFINITY) envMap.put("POJAV_BIG_CORE_AFFINITY", "1");

        if(GLInfoUtils.getGlInfo().isAdreno() && !PREF_ZINK_PREFER_SYSTEM_DRIVER) {
            setUseTurnip(true);
        }

        if(LauncherPreferences.PREF_FREEDRENO_SYSMEM) {
            Logger.appendToLog("Will use sysmem rendering for Turnip/Freedreno");
            envMap.put("FD_MESA_DEBUG", "sysmem");
            envMap.put("TU_DEBUG", "sysmem");
        }

        overrideEnvVars(envMap);

        for (Map.Entry<String, String> env : envMap.entrySet()) {
            Logger.appendToLog("Added custom env: " + env.getKey() + "=" + env.getValue());
            try {
                Os.setenv(env.getKey(), env.getValue(), true);
            }catch (NullPointerException exception){
                Log.e("JREUtils", exception.toString());
            }
        }

        // Sodium System.getenv("POJAV_RENDERER") — scrub Java + libc
        if (isZink || "fear_render".equals(renderer)) {
            scrubPojavDetectorEnv();
        }
    }

    public static void launchJavaVM(final AppCompatActivity activity, final Runtime runtime, File gameDirectory, final List<String> JVMArgs, final String userArgsString) throws Throwable {
        Tools.fullyExit();
    }

    public static ArrayList<String> parseJavaArguments(String args){
        ArrayList<String> parsedArguments = new ArrayList<>(0);
        args = args.trim().replace(" ", "");
        String[] separators = new String[]{"-XX:-","-XX:+", "-XX:","--", "-D", "-X", "-javaagent:", "-verbose"};
        for(String prefix : separators){
            while (true){
                int start = args.indexOf(prefix);
                if(start == -1) break;
                int end = -1;
                for(String separator: separators){
                    int tempEnd = args.indexOf(separator, start + prefix.length());
                    if(tempEnd == -1) continue;
                    if(end == -1){
                        end = tempEnd;
                        continue;
                    }
                    end = Math.min(end, tempEnd);
                }
                if(end == -1) end = args.length();
                String parsedSubString = args.substring(start, end);
                args = args.replace(parsedSubString, "");
                if(parsedSubString.indexOf('=') == parsedSubString.lastIndexOf('=')) {
                    int arraySize = parsedArguments.size();
                    if(arraySize > 0){
                        String lastString = parsedArguments.get(arraySize - 1);
                        if(lastString.charAt(lastString.length() - 1) == ',' ||
                                parsedSubString.contains(",")){
                            parsedArguments.set(arraySize - 1, lastString + parsedSubString);
                            continue;
                        }
                    }
                    parsedArguments.add(parsedSubString);
                }
                else Log.w("JAVA ARGS PARSER", "Removed improper arguments: " + parsedSubString);
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
            String appId = renderer.substring("plugin:".length());
            Logger.appendToLog("[CustomRenderer] Loading plugin: " + appId);
            Context context = net.kdt.pojavlaunch.lifecycle.ContextExecutor.getApplication();
            LibraryPlugin plugin = (context != null) ? LibraryPlugin.discoverPlugin(context, appId) : null;
            if (plugin != null) {
                String libDir = plugin.getLibraryPath();
                File libDirFile = new File(libDir);
                if (libDirFile.exists() && libDirFile.isDirectory()) {
                    File[] candidates = libDirFile.listFiles((dir, name) -> name.endsWith(".so"));
                    if (candidates != null && candidates.length > 0) {
                        File chosenSo = candidates[0];
                        for (File candidate : candidates) {
                            String name = candidate.getName();
                            if (name.contains("mobileglue") || name.contains("zink") || name.contains("mesa") || name.contains("ltw") || name.contains("gl4es") || name.contains("EGL") || name.contains("OSMesa")) {
                                chosenSo = candidate;
                                break;
                            }
                        }
                        renderLibrary = chosenSo.getAbsolutePath();
                        useGles = true;
                        glesVersion = 3;
                        if (configureRenderspec(renderLibrary, true, useGles, glesVersion)) {
                            return renderLibrary;
                        }
                    }
                }
            }
            Log.w("RENDER_LIBRARY", "Plugin renderer load failed, falling back to GL4ES");
            renderer = "opengles2";
        }


        if ("panfork".equals(renderer)) preloadVk = false;

        switch (renderer){
            case "panfork":
                Logger.appendToLog("[Panfork] Loading Panfork OSMesa (libOSMesa_panfork.so)...");
                renderLibrary = "libOSMesa_panfork.so";
                useGles = false;
                bypassNamespace = true;
                glesVersion = 3;
                if(preloadVk) preloadVulkan();
                break;
            case "panvk_zink":
            case "turnip_zink":
            case "vulkan_zink":
                Logger.appendToLog("[TurnipZink] Loading real Mesa OSMesa (libOSMesa_8.so)...");
                renderLibrary = "libOSMesa_8.so";
                useGles = false;
                bypassNamespace = true;
                glesVersion = 3;
                if(preloadVk) preloadVulkan();
                break;
            case "fear_render":
                Logger.appendToLog("[FearRender] Loading FearRender (libFearRender.so - MobileGlues core, GL on GLES)...");
                renderLibrary = "libFearRender.so";
                useGles = true;
                glesVersion = 3;
                break;
            case "opengles3_ltw":
                renderLibrary = "libltw.so";
                useGles = true;
                glesVersion = 3;
                break;
            case "opengles2":
            case "opengles2_5":
            case "opengles3":
            default:
                renderLibrary = "libgl4es_114.so";
                useGles = true;
                glesVersion = Integer.parseInt((String) ExtraCore.getValue(ExtraConstants.OPEN_GL_VERSION));
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

    public static native void setupBridgeWindow(android.view.Surface surface);
    public static native void releaseBridgeWindow();

    public static native void initFearShaderEngine(String cachePath, int version);
    public static native void destroyFearShaderEngine();
    public static native String getShaderCachePath();
    public static native void clearShaderCache();
    public static native int getTranslatedShaderCount();

    public static native boolean renderAWTScreenFrame(ByteBuffer tempBuffer);
    static {
        System.loadLibrary("pojavexec");
        System.loadLibrary("pojavexec_awt");
    }
}
