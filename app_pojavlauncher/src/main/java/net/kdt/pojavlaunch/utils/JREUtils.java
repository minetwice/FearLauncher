package net.kdt.pojavlaunch.utils;

import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_DUMP_SHADERS;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_VSYNC_IN_ZINK;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_ZINK_PREFER_SYSTEM_DRIVER;

import android.content.*;
import android.system.*;
import android.util.*;

import androidx.appcompat.app.AppCompatActivity;

import java.io.*;
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
                            if (line.contains("jrelog") || line.contains("LIBGL") || line.contains("NativeInput") || line.contains("FEAR") || line.contains("FearRender") || line.contains("Mesa")) {
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
            envMap.put(line.substring(0, index), line.substring(index + 1));
        }
        reader.close();
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
            case "fear_xextream":
                Logger.appendToLog("[FearXextream] Initializing High-FPS GLES Engine Environment (v2)...");
                envMap.put("LIBGL_ES", "3");
                envMap.put("LIBGL_GL", "46");
                envMap.put("LIBGL_VERSION", "4.6.0 NVIDIA 545.29");
                envMap.put("LIBGL_GLSL", "1");
                envMap.put("LIBGL_USEVBO", "1");
                envMap.put("LIBGL_BATCH", "1");
                envMap.put("LIBGL_BEGINEND", "1");
                envMap.put("LIBGL_RECYCLEFBO", "1");
                envMap.put("LIBGL_FB", "1");
                envMap.put("LIBGL_FPE", "1");
                envMap.put("LIBGL_FBOTEXTURE2D", "1");
                envMap.put("LIBGL_ALWAYSCURRENT", "1");
                envMap.put("LIBGL_NOCONTEXTCLEANUP", "1");
                envMap.put("LIBGL_NOERROR", "1");
                envMap.put("LIBGL_NOINTOVLHACK", "1");
                envMap.put("LIBGL_NORMALIZE", "1");
                envMap.put("LIBGL_MIPMAP", "3");
                envMap.put("LIBGL_COPY", "1");
                envMap.put("LIBGL_AVOID16BITS", "1");
                envMap.put("LIBGL_SHRINK", "1");
                envMap.put("LIBGL_MAX_DRAW_BUFFERS", "8");
                envMap.put("LIBGL_MRT_FORMATS", "RGBA16F,RGBA8");
                envMap.put("LIBGL_FLOAT_COLOR", "1");
                envMap.put("LIBGL_FLOAT_DEPTH", "0");
                envMap.put("LIBGL_DEPTH", "24");
                envMap.put("LIBGL_COLOR_RESCALE", "0");
                envMap.put("LIBGL_NOTEXTURERECT", "1");
                envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");
                envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
                envMap.put("MESA_GLSL_CACHE_DISABLE", "false");
                envMap.put("MESA_GLSL_CACHE_MAX_SIZE", "2048MB");
                envMap.put("MESA_NO_ERROR", "1");
                envMap.put("MESA_SHADER_CACHE_DISABLE", "false");
                envMap.put("allow_glsl_extension_directive_midshader", "true");
                envMap.put("allow_higher_compat_version", "true");
                envMap.put("allow_glsl_relaxed_es", "true");
                envMap.put("glsl_ignore_unsupported_extensions", "true");
                envMap.put("glsl_ignore_noperspective", "true");
                envMap.put("LIBGL_GLSL_STRIP", "noperspective");
                envMap.put("LIBGL_GLSL_REPLACE", "noperspective=smooth");
                envMap.put("glsl_force_highp", "false");
                envMap.put("glsl_zero_init", "false");
                envMap.put("mali_debug", "nocluster");
                envMap.put("pan_shader_compile_threads", "4");
                envMap.put("vblank_mode", "0");
                envMap.put("force_s3tc_enable", "true");
                envMap.put("POJAV_VSYNC_IN_ZINK", "0");
                envMap.put("FORCE_VSYNC", "false");
                envMap.put("LIBGL_VSYNC", "0");
                envMap.put("FEAR_XEXTREAM_FPS_MODE", "1");
                envMap.put("FEAR_XEXTREAM_STATE_CACHE", "1");
                envMap.put("FEAR_XEXTREAM_SKIP_DITHER", "1");
                envMap.put("FEAR_XEXTREAM_FAST_MATH", "1");
                envMap.put("FEAR_XEXTREAM_SHADER_CACHE", "1");
                break;
            case "vulkan_zink":
                envMap.put("GALLIUM_DRIVER", "zink");
                envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "zink");
                envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
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

        envMap.put("LIBGL_ES", (String) ExtraCore.getValue(ExtraConstants.OPEN_GL_VERSION));
        envMap.put("FORCE_VSYNC", String.valueOf(LauncherPreferences.PREF_FORCE_VSYNC));
        envMap.put("MESA_GLSL_CACHE_DIR", Tools.DIR_CACHE.getAbsolutePath());
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
        envMap.put("EGL_PLATFORM", "android");

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
    }

    public static void launchJavaVM(final AppCompatActivity activity, final Runtime runtime, File gameDirectory, final List<String> JVMArgs, final String userArgsString) throws Throwable {
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
                            if (name.contains("mobileglue") || name.contains("zink") || name.contains("mesa") || name.contains("ltw") || name.contains("gl4es") || name.contains("EGL")) {
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

        switch (renderer){
            case "fear_xextream":
                Logger.appendToLog("[FearXextream] Initializing High-FPS GLES Engine Backend (v2)...");
                renderLibrary = "libgl4es_114.so";
                useGles = true;
                bypassNamespace = false;
                glesVersion = 3;

                try {
                    boolean loaded = false;
                    String[] nativeCandidates = new String[] {
                            "FearXextream", "FearCore", "fear_render"
                    };
                    for (String libName : nativeCandidates) {
                        try {
                            System.loadLibrary(libName);
                            Logger.appendToLog("[FearXextream] Loaded native lib: " + libName);
                            loaded = true;
                            break;
                        } catch (UnsatisfiedLinkError ule) {
                            Log.w("JREUtils", "Native lib not found: " + libName);
                        }
                    }
                    if (loaded) {
                        String cachePath = Tools.DIR_GAME_HOME + "/fear_xextream_cache";
                        File cacheDir = new File(cachePath);
                        if (!cacheDir.exists()) {
                            cacheDir.mkdirs();
                        }
                        initFearXextreamEngine(cachePath);
                        Logger.appendToLog("[FearXextream] Engine init OK, cache=" + cachePath);
                    } else {
                        Logger.appendToLog("[FearXextream] No native engine lib found; running pure GL4ES + env boosters");
                    }
                } catch (Throwable t) {
                    Log.e("JREUtils", "FearXextream native engine init failed", t);
                }
                break;
            case "vulkan_zink":
                renderLibrary = "libEGL_mesa.so";
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

    public static native void initFearXextreamEngine(String cachePath);

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
