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
    private static final String TAG = "JREUtils";
    private static final String VULKAN_LIB_URL = "https://github.com/minetwice/FearLauncher/releases/download/vulkan-libs/";
    private static final String[] VULKAN_LIBS = {"libvulkan.so", "libvulkan_mesa.so"};
    private static boolean sVulkanLibrariesChecked = false;

    static {
        try {
            Logger.appendToLog("[JREUtils] Static: Attempting early Vulkan preload for Zink compatibility...");
            ensureVulkanLibraries();
            preloadVulkan();
            Logger.appendToLog("[JREUtils] Static: Vulkan preloaded successfully!");
        } catch (Throwable t) {
            Logger.appendToLog("[JREUtils] Static: Early Vulkan preload failed (will retry later): " + t.getMessage());
        }
    }

    private static void ensureVulkanLibraries() {
        if (sVulkanLibrariesChecked) return;
        sVulkanLibrariesChecked = true;

        Context context = null;
        try {
            context = net.kdt.pojavlaunch.lifecycle.ContextExecutor.getApplication();
        } catch (Exception e) {
            Log.w(TAG, "Could not get application context for Vulkan library check", e);
        }

        if (context == null) {
            Log.w(TAG, "No context available, skipping Vulkan library check");
            return;
        }

        File nativeLibDir = new File(Tools.NATIVE_LIB_DIR);
        if (!nativeLibDir.exists() || !nativeLibDir.isDirectory()) {
            Log.w(TAG, "Native lib directory does not exist: " + Tools.NATIVE_LIB_DIR);
            return;
        }

        boolean allLibsExist = true;
        for (String libName : VULKAN_LIBS) {
            File libFile = new File(nativeLibDir, libName);
            if (!libFile.exists()) {
                allLibsExist = false;
                break;
            }
        }

        if (allLibsExist) {
            Log.i(TAG, "All Vulkan libraries already present");
            return;
        }

        new Thread(() -> {
            try {
                Logger.appendToLog("[JREUtils] Downloading Vulkan libraries for first-time setup...");
                for (String libName : VULKAN_LIBS) {
                    File libFile = new File(nativeLibDir, libName);
                    if (!libFile.exists()) {
                        String downloadUrl = VULKAN_LIB_URL + libName;
                        if (downloadLibrary(context, downloadUrl, libFile)) {
                            Logger.appendToLog("[JREUtils] Successfully downloaded: " + libName);
                        } else {
                            Log.w(TAG, "Failed to download: " + libName);
                        }
                    }
                }

                for (String libName : VULKAN_LIBS) {
                    File libFile = new File(nativeLibDir, libName);
                    if (libFile.exists()) {
                        libFile.setExecutable(true);
                        libFile.setReadable(true);
                    }
                }

                Logger.appendToLog("[JREUtils] Vulkan library setup complete!");
            } catch (Exception e) {
                Log.e(TAG, "Error downloading Vulkan libraries", e);
                Logger.appendToLog("[JREUtils] ERROR: Failed to download Vulkan libraries: " + e.getMessage());
            }
        }).start();
    }

    private static boolean downloadLibrary(Context context, String url, File destination) {
        try {
            java.net.URL downloadUrl = new java.net.URL(url);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) downloadUrl.openConnection();
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(60000);

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                Log.w(TAG, "HTTP " + responseCode + " for " + url);
                return false;
            }

            File parentDir = destination.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            try (InputStream inputStream = connection.getInputStream();
                 FileOutputStream outputStream = new FileOutputStream(destination)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to download library from " + url, e);
            return false;
        }
    }

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
                                Logger.appendToLog(line);
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
            case "turbov1":
                Logger.appendToLog("[TurboV1] Initializing Native Vulkan Engine Environment...");
                envMap.put("GALLIUM_DRIVER", "zink");
                envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "zink");
                envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
                envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");
                envMap.put("vblank_mode", "0");
                envMap.put("FORCE_VSYNC", "0");
                envMap.put("LIBGL_VSYNC", "0");
                envMap.put("MESA_VK_WSI_PRESENT_MODE", "mailbox");
                envMap.put("MESA_PRESENT_MODE", "mailbox");
                envMap.put("MESA_GLSL_CACHE_DISABLE", "false");
                envMap.put("MESA_GLSL_CACHE_MAX_SIZE", "4096MB");
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

        if ("turbov1".equals(renderer) || "vulkan_zink".equals(renderer)) {
            Logger.appendToLog("[TurboV1] setEnviroimentForGame: Preloading Vulkan driver...");
            try {
                ensureVulkanLibraries();
                preloadVulkan();
                Logger.appendToLog("[TurboV1] setEnviroimentForGame: Vulkan preloaded successfully!");
            } catch (Throwable t) {
                Log.e("JREUtils", "Failed to preload Vulkan in setEnviroimentForGame", t);
                Logger.appendToLog("[TurboV1] WARNING: Vulkan preload failed: " + t.getMessage());
            }
        }

        setupRendererEnv(envMap, renderer);

        envMap.put("POJAV_NATIVEDIR", Tools.NATIVE_LIB_DIR);
        if (!"turbov1".equals(renderer) && !"vulkan_zink".equals(renderer)) {
            envMap.put("EGL_PLATFORM", "android");
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
    }

    public static void launchJavaVM(final AppCompatActivity activity, final Runtime runtime, File gameDirectory, final List<String> JVMArgs, final String userArgsString) throws Throwable {
        Tools.fullyExit();
    }

    public static ArrayList<String> parseJavaArguments(String args){
        ArrayList<String> parsedArguments = new ArrayList<>(0);
        args = args.trim().replace("  ", "");
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
                if(parsedSubString.indexOf("=") == parsedSubString.lastIndexOf("=")) {
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
            case "turbov1":
                Logger.appendToLog("[TurboV1] Initializing Native Vulkan Engine Backend (Mesa Zink Core)...");
                renderLibrary = "libEGL_mesa.so";
                useGles = false;
                bypassNamespace = true;
                glesVersion = 3;
                if (preloadVk) {
                    try {
                        ensureVulkanLibraries();
                        preloadVulkan();
                    } catch (Throwable t) {
                        Log.e("JREUtils", "TurboV1 Vulkan preload failed", t);
                    }
                }

                try {
                    System.loadLibrary("turbov1");
                    String cachePath = Tools.DIR_GAME_HOME + "/turbov1_cache";
                    initTurboV1Engine(cachePath);
                    Logger.appendToLog("[TurboV1] Native Vulkan Engine initialized successfully!");
                } catch (Throwable t) {
                    Log.e("JREUtils", "TurboV1 native engine init failed", t);
                    Logger.appendToLog("[TurboV1] ERROR: Native engine initialization failed!");
                }
                break;
            case "vulkan_zink":
                renderLibrary = "libEGL_mesa.so";
                useGles = false;
                bypassNamespace = true;
                glesVersion = 3;
                if(preloadVk) {
                    try {
                        ensureVulkanLibraries();
                        preloadVulkan();
                    } catch (Throwable t) {
                        Log.e("JREUtils", "Vulkan Zink Vulkan preload failed", t);
                    }
                }
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

    public static native void initTurboV1Engine(String cachePath);

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