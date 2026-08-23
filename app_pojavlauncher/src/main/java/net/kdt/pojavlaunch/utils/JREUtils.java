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
                    // Optimized high-speed log retrieval: no filtering at process level to avoid buffer backup
                    ProcessBuilder pb = new ProcessBuilder("logcat", "-v", "tag", "-S", "1").redirectErrorStream(true);
                    java.lang.Process p = pb.start();

                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"), 32768)) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            // Filter lines in-memory for speed and "Manufactured" feel
                            if (line.contains("jrelog") || line.contains("LIBGL") || line.contains("NativeInput") || line.contains("FEAR") || line.contains("FearRender") || line.contains("Mesa")) {
                               Logger.appendToLog(line + "\n");
                            }
                        }
                    }

                    int exitCode = p.waitFor();
                    if (exitCode != 0) {
                        Log.w("jrelog-logcat", "Logcat link lost. Sync code: " + exitCode + ". Re-establishing...");
                        failCount++;
                        Thread.sleep(500 * failCount); // Exponential backoff
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
            // Not use split() as only split first one
            int index = line.indexOf("=");
            envMap.put(line.substring(0, index), line.substring(index + 1));
        }
        reader.close();
    }

    // Sets up ANGLE driver environment
    public static void setupAngleEnv(Context ctx, Map<String, String> envMap) {
        if (!LauncherPreferences.PREF_USE_ANGLE) return;
        LibraryPlugin angle = LibraryPlugin.discoverPlugin(ctx, LibraryPlugin.ID_ANGLE_PLUGIN);
        if (angle == null) return;
        String[] angleLibs = {"libEGL_angle.so", "libGLE@v2_angle.so"};
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

    // Setup environment for mesa-based renderers
    public static void setupRendererEnv(Map<String, String> envMap, String renderer) {
        switch(renderer) {
            case "mh_drive":
                // MH DRIVE (Mali Hybrid Optimization Engine)
                Logger.appendToLog("[MH DRIVE] MULTI-TRACK MALI ENGINE INITIALIZED...");
                envMap.put("LIBGL_ES", "3");
                envMap.put("LIBGL_USEVBO", "1");
                envMap.put("LIBGL_BATCH", "1");
                envMap.put("LIBGL_SHRINK", "0");
                envMap.put("LIBGL_FASTEDID", "1");
                envMap.put("LIBGL_MIPMAP", "3");
                envMap.put("LIBGL_NOERROR", "1");
                envMap.put("LIBGL_GL", "46");
                envMap.put("LIBGL_VERSION", "4.6.0 NVIDIA 545.29");
                envMap.put("LIBGL_NOTEXTURERCT", "0");
                envMap.put("LIBGL_FBOTEXTURE2", "1");
                envMap.put("LIBGL_GLSL", "1");
                envMap.put("LIBGL_ALWAYSCURRENT", "1");
                envMap.put("LIBGL_NOCONTEXCCLEANUP", "1");
                envMap.put("LIBGL_FB", "1");
                envMap.put("LIBGL_FPE", "1");
                envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
                envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");
                envMap.put("allow_glsl_extension_directive_midshader", "true");
                envMap.put("allow_higher_compat_version", "true");
                envMap.put("allow_glsl_relaxed_es", "true");
                envMap.put("MESA_EXTENSION_OVERRIDE", "GL_EXT_gpu_shader4 GL_EXT_texture_buffer GL_EXT_texture_cube_map_array GL_OES_EGL_image_external_essl3 GL_NV_shader_noperspective_interpolation GL_ARB_shader_objects GL_ARB_vertex_shader GL_ARB_fragment_shader GL_EXT_blend_equation_separate GL_EXT_geometry_shader4 GL_EXT_gpu_program_parameters GL_ARB_instanced_arrays GL_ARB_draw_instanced");
                envMap.put("LIBGL_NO_VBO_BOUNDS", "1");

                // High-precision float depth and color attachments to fix shadow and ray color glitches:
                envMap.put("LIBGL_FLOAT_COLOR", "1");
                envMap.put("LIBGL_FLOAT_DEPTH", "1");
                envMap.put("LIBGL_DEPTH", "24");
                envMap.put("LIBGL_COLOR_RESCALE", "1");
                envMap.put("LIBGL_MAX_DRAW_BUFFERS", "8");
                envMap.put("LIBGL_MRT_FORMATS", "RGBA16F,RGBA32F");
                break;
            case "fear_engine":
                boolean isZinkActive = false;
                try {
                    preloadVulkan();
                    isZinkActive = true;
                } catch (Throwable t) {
                    isZinkActive = false;
                }

                if (isZinkActive) {
                    Logger.appendToLog("[FearRender] Configuring Mali-safe Zink environment profile");
                    envMap.put("GALLIUM_DRIVER", "zink");
                    envMap.put("EGL_PLATFORM", "android");
                    envMap.put("MESA_VK_WSI_PRESENT_MODE", "fifo");
                    envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");
                    envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
                    envMap.put("MESA_NO_MINMAX_CACHE", "1");
                    envMap.put("MESA_NO_ERROR", "0"); // Disable force no-error for Sodium compatibility
                } else {
                    Logger.appendToLog("[FearRender] Configuring GLES environment profile");
                }

                envMap.put("POJAV_BIG_CORE_AFFINITY", "1");
                envMap.put("LIBGL_NOERROR", "1");
                envMap.put("LIBGL_FBOTEXTURE2", "1");
                envMap.put("LIBGL_MIPMAP", "3");
                envMap.put("LIBGL_COLOR_RESCALE", "1");
                envMap.put("LIBGL_MRT_FORMATS", "RGBA16F,RGBA32F");
                envMap.put("gl_draw_buffers_override", "true");

                // GLSL behavior
                envMap.put("gsl_force_highp", "true");
                envMap.put("allow_glsl_extension_directive_midsader", "true");
                envMap.put("allow_higher_compat_version", "true");
                envMap.put("allow_glsl_relaxed_es", "true");
                envMap.put("allow_glsl_layout_qualifier_override", "true");
                envMap.put("gsl_ignore_noperspective", "true");

                // Shader Cache
                envMap.put("MESA_GLSL_CACHE_DISABLE", "false");
                envMap.put("vblank_mode", "0");

                // Extensions
                envMap.put("MESA_EXTENSION_OVERRIDE", "GL_EXT_gpu_shader4 GL_EXT_texture_buffer GL_EXT_texture_cube_map_array GL_OES_EGL_image_external_essl3 GL_NV_shader_noperspective_interpolation GL_ARB_shader_objects GL_ARB_vertex_shader GL_ARB_fragment_shader GL_EXT_blend_equation_separate GL_EXT_geometry_shader4 GL_EXT_gpu_program_parameters GL_ARB_instanced_arrays GL_ARB_draw_instanced");
                break;
            case "vulkan_zink":
                envMap.put("GALLIUM_DRIVER", "zink");
                envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "zink");
                // HACK: GLSL version override for Mesa-based renderers (i.e. Zink)
                // Required to run the game properly on some mobile Vulkan drivers (Minecraft fails to compile shaders without)
                envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
                break;
            case "freedreno_kgsl":
                if(GLInfoUtils.getGlInfo().isAdreno()) {
                    envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "kgsl");
                    // On Adreno 5XX and lower only Core 3.1 is exposed by default due to missing hardware extensions.
                    // 3.3 is required for modern Minecraft so let's force 3.3 if running on such GPU - it's known to be working.
                    if(GLInfoUtils.getGlInfo().isAdreno500Lower()) {
                        envMap.put("MESA_GL_VERSION_OVERRIDE", "3.3");
                        envMap.put("MESA_GLSL_VERSION_OVERRIDE", "330");
                    }
                }
                break;
            case "opengles3_mges":
                envMap.put("MG_DIR_PATH", Tools.MOBILEGLES_DIR);
                envMap.put("LIBGL_GLES", Tools.MOBILEGLES_DIR + "/libmobilegles.so");
                envMap.put("LIBGL_ES", "3");
                envMap.put("LIBGL_MIPMAP", "3");
                envMap.put("LIBGL_NOERROR", "1");
                envMap.put("LIBGL_NORMALIZE", "1");
                envMap.put("LIBGL_NOINTOVLHACK", "1");
                envMap.put("LIBGL_GL", "40");
                envMap.put("MG_maxGlslCacheSize", LauncherPreferences.MG_GLSL_CACHE_SIZE);
                envMap.put("MG_enableANGLE", LauncherPreferences.MG_ANGLE_OPTION);
                envMap.put("MG_enableNoError", LauncherPreferences.MG_NOERROR_OPTION);
                envMap.put("MG_multidrawMode", LauncherPreferences.MG_MULTIDRAWMODE_OPTION);
                envMap.put("MG_customGLVersion", LauncherPreferences.MG_GL_VERSION);
                envMap.put("MG_angleDepthClearFixMode", LauncherPreferences.MG_ANGLECLEARWORKMODE_OPTION);
                envMap.put("MG_enableExtGL43", LauncherPreferences.MG_EXT_GL43);
                envMap.put("MG_enableExtComputeShader", LauncherPreferences.MG_EXT_CS);
                envMap.put("MG_enableExtTimerQuery", LauncherPreferences.MG_EXT_TIMER_QUERY.equals("0") ? "1" : "0");
                envMap.put("MG_enableExtDirectStateAccess", LauncherPreferences.MG_EXT_DIRECT_STATE_ACCESS);
                envMap.put("MG_fsr1Setting", LauncherPreferences.MG_ENABLE_FSR1);
                break;
            case "opengles3_mggl":
                envMap.put("LIBGL_ES", "3");
                envMap.put("LIBGL_MIPMAP", "3");
                envMap.put("LIBGL_NOERROR", "1");
                envMap.put("LIBGL_NORMALIZE", "1");
                envMap.put("LIBGL_GL", "40");
                break;
            case "opengles3_nggl4es":
                envMap.put("LIBGL_ES", "3");
                envMap.put("LIBGL_MIPMAP", "3");
                envMap.put("LIBGL_NOERROR", "1");
                envMap.put("LIBGL_NORMALIZE", "1");
                envMap.put("LIBGL_NOINTOVLHACK", "1");
                envMap.put("LIBGL_GL", "31");
                break;
            case "custom_inject":
                envMap.put("LIBGL_ES", "3");
                envMap.put("LIBGL_MIPMAP", "3");
                envMap.put("LIBGL_NOERROR", "1");
                envMap.put("LIBGL_NORMALIZE", "1");
                envMap.put("LIBGL_NOINTOVLHACK", "1");
                break;
            case "quasar":
                Logger.appendToLog("[Quasar] Initializing Quasar renderer environment...");
                envMap.put("LIBGL_ES", "3");
                envMap.put("LIBGL_MIPMAP", "3");
                envMap.put("LIBGL_NOERROR", "1");
                envMap.put("LIBGL_NORMALIZE", "1");
                envMap.put("LIBGL_NOINTOVLHACK", "1");
                envMap.put("LIBGL_GL", "46");
                envMap.put("LIBGL_GLSL", "1");
                envMap.put("LIBGL_FB", "1");
                envMap.put("LIBGL_FPE", "1");
                envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
                envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");
                envMap.put("allow_glsl_extension_directive_midshader", "true");
                envMap.put("allow_higher_compat_version", "true");
                envMap.put("allow_glsl_relaxed_es", "true");
                envMap.put("MESA_GLSL_CACHE_DISABLE", "false");
                envMap.put("vblank_mode", "0");
                break;
        }
    }
    public static void setEnviroimentForGame(Context context, String renderer) throws Throwable {
        Map<String, String> envMap = new ArrayMap<>();
        envMap.put("LIBGL_MIPMAP", "3");

        // Prevent OptiFine (and other error-reporting stuff in Minecraft) from balooning the log
        envMap.put("LIBGL_NOERROR", "1");

        // On certain GLES drivers, overloading default functions shader hack fails, so disable it
        envMap.put("LIBGL_NOINTOVLHACK", "1");

        // Fix white color on banner and sheep, since GL4ES 1.1.5
        envMap.put("LIBGL_NORMALIZE", "1");

        if(PREF_DUMP_SHADERS)
            envMap.put("LIBGL_VGPU_DUMP", "1");
        if(PREF_VSYNC_IN_ZINk)
            envMap.put("POJAV_VSYNC_IN_ZINK", "1");

        // The OPEN GL version is changed according
        envMap.put("LIBGL_ES", (String) ExtraCore.getValue(ExtraConstants.OPEN_GL_VERSION));

        envMap.put("FORCE_VSYNC", String.valueOf(LauncherPreferences.PREF_FORCE_VSYNC));

        envMap.put("MESA_GLSL_CACHE_DIR", Tools.DIR_CACHE.getAbsolutePath());
        envMap.put("force_glsl_extensions_warn", "true");
        envMap.put("allow_higher_compat_version", "true");
        envMap.put("allow_glsl_extension_directive_midshader", "true");
		// This is currently required for YSM mod to function
		File modRuntimeDir = new File(Tools.DIR_CACHE, "app_runtime_mod");
		if (!modRuntimeDir.exists()) {
		modRuntimeDir.mkdirs();
		}
		envMap.put("MOD_ANDROID_RUNTIME", modRuntimeDir.getAbsolutePath());

        setupAngleEnv(context, envMap);
        setupFfmpegEnv(context, envMap);
        setupRendererEnv(envMap, renderer);

        // HACK
        envMap.put("POJAV_NATIVEDIR", Tools.NATIVE_LIB_DIR);
        envMap.put("EGL_PLATFORM", "android");

        if(LauncherPreferences.PREF_BIG_CORE_AFFINITY) envMap.put("POJAV_BIG_CORE_AFFINITY", "1");

        if(GLInfoUtils.getGlInfo().isAdreno() && !PREF_ZINK_PREFER_SYSTEM_DRIVER) {
            setUseTurnip(true);
        }

        if(LauncherPreferences.PREF_FREEDRENO_SYSMEM) {
            // We could also apply the FD_MESA_DEBUG only if freedreno is active but why making things complicated?
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

        // Force LWJGL to use the Freetype library intended for it, instead of using the one
        // that we ship with Java (since it may be older than what's needed)
        //
        Tools.fullyExit();
    }

    /**
     * Parse and separate java arguments in a user friendly fashion
     * It supports multi line and absence of spaces between arguments
     * The function also supports auto-removal of improper arguments, although it may miss some.
     *
     * @param args The un-parsed argument list.
     * @return Parsed args as an ArrayList
     */
    public static ArrayList<String> parseJavaArguments(String args){
        ArrayList<String> parsedArguments = new ArrayList<>(0);
        args = args.trim().replace(" ", "");
        //For each prefixes, we separate args.
        String[] separators = new String[]{"-XX:-","-XX:+", "-XX:","--", "-D", "-X", "-javaagent:", "-verbose"};
        for(String prefix : separators){
            while (true){
                int start = args.indexOf(prefix);
                if(start == -1) break;
                //Get the end of the current argument by checking the nearest separator
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
                //Fallback
                if(end == -1) end = args.length();

                //Extract it
                String parsedSubString = args.substring(start, end);
                args = args.replace(parsedSubString, "");

                //Check if two args aren't bundled together by mistake
                if(parsedSubString.indexOf('=') == parsedSubString.lastIndexOf('=')) {
                    int arraySize = parsedArguments.size();
                    if(arraySize > 0){
                        String lastString = parsedArguments.get(arraySize - 1);
                        // Looking for list elements
                        if(lastString.charAt(lastString.length() - 1) == ',' ||
                               parsedSubString.contains(",")){
                            parsedArguments.set(arraySize - 1, lastString + parsedSubString);
                            continue;
                        }
                    }
                    parsedArguments.add(parsedSubString);
                }
                else Log.w("JAVA ARG PARSER", "Removed improper arguments: " + parsedSubString);
                }
            }
            return parsedArguments;
        }

    /**
     * Open the render library in accordance to the settings.
     * It will fallback if it fails to load the library.
     * @return The name of the loaded library
     */
    public static String loadGraphicsLibrary(String renderer){
        String renderLibrary;
        boolean useGles;
        boolean bypassNamespace = false;
        boolean preloadVk = true;
        int glesVersion;
        switch (renderer){
            case "mh_drive":
                // Map to dynamic linking wrapper containing MH DRIVE Track 1 and integrated GLES/Vulkan overrides
                renderLibrary = "libltw.so";
                useGles = true;
                glesVersion = 3;
                try {
                    System.loadLibrary("mh_drive_gl_wrapper");
                    System.loadLibrary("mh_drive_vulkan_mesa");
                } catch (UnsatisfiedLinkError e) {
                    Log.w("JREUtils", "MH DRIVE specific native wrapper layers omitted or pre-installed inside system path.");
                }
                break;
            case "fear_engine":
                boolean vulkanOk = false;
                String vkVer = "none";
                try {
                    preloadVulkan();
                    vulkanOk = true;
                    vkVer = "1.3";
                } catch (Throwable t) {
                    vulkanOk = false;
                    vkVer = "none";
                }

                if (vulkanOk) {
                    Logger.appendToLog("[FearRender] probe: vulkan=" + vkVer + " -> ZINK);
                    Logger.appendToLog("[FearRender] backend=ZINK (Vulkan: 1.3)");
                    renderLibrary = "libEGL_mesa.so";
                    useGles = false;
                    bypassNamespace = true;
                    glesVersion = 3;
                } else {
                    Logger.appendToLog("[FearRender] probe: vulkan=" + vkVer + " -> GLES)");
                    Logger.appendToLog("[FearRender] backend=GLES core=FOGLTDOGLES+guards");
                    renderLibrary = "libGLFear.so";
                    useGles = true;
                    glesVersion = 3;
                }
                break;
            case "freedreno_kgsl":
                preloadVk = false;
            case "vulkan_zink":
                renderLibrary = "libEGL_mesa.so";
                useGles = false;
                bypassNamespace = true; // Mesa is linked to a bunch of libraries not available in the pojavexec namespace
                glesVersion = 3;
                if(preloadVk) preloadVulkan(); // Zink requires Vulkan library to be preloaded
                break;
            case "opengles3_ltw" :
                renderLibrary = "libltw.so";
                useGles = true;
                glesVersion = 3;
                break;
            case "opengles3_mges":
                renderLibrary = Tools.MOBILEGLES_DIR + "/libmobilegles.so";
                useGles = true;
                glesVersion = 3;
                break;
            case "opengles3_mggl":
                renderLibrary = Tools.MOBILEGL_DIR + "/libMobileGL.so";
                useGles = true;
                glesVersion = 3;
                break;
            case "opengles3_nggl4es":
                renderLibrary = Tools.NG_GL4ES_DIR + "/libng_gl4es.so";
                useGles = true;
                glesVersion = 3;
                break;
            case "custom_inject":
                Logger.appendToLog("[FearRender] Custom Render Injection mode selected");
                renderLibrary = "libGLFear.so";
                useGles = true;
                glesVersion = 3;
                break;
            case "quasar":
                Logger.appendToLog("[Quasar] Loading Quasar renderer...");
                renderLibrary = "libgl4es_114.so";
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

    public static String probeEGlPlatform() {
        try {
            Os.setenv("EGL_PLATFORM", "android", true);
            long eglDisplay = eglGetDisplay(0 /* EGL_DEFAULT_DISPLAY */);
            if (eglDisplay != 0) {
                Logger.appendToLog("EGL platform: android");
                return "android";
            }
        } catch (Exception e) {
            Log.e("JREUtils", "Failed to set EGL PLATFORM android", e);
        }
        Logger.appendToLog("EGL platform: fallback to null");
        return null;
    }

    public static String probeVulkanSupport() {
        // Skip on devices without Vulkan support
        if (Features.findFeature() != Features.FTURE_VULKAN) {
            Logger.appendToLog("Vulkan: Not supported on this device");
            return null;
        }
        return forceVULAN();
    }

    private static String forceVULAN() {
        String[] vulkanLibs = {
                "libvulkan.so",
                "libswagevelptv.so",
                "libmesa_vulkan.so"
        };
        String vulkanLibrary = null;
        for (int i = 0; i < vulkanLibs.length && vulkanLibrary == null; i++) {
            try {
                String libRelPath = Tools.getNativeLibpath(vulkanLibs[i]);
                if (libRelPath != null) {
                    Logger.appendToLog("Vulkan: Trying to load " + vulkanLibs[i]);
                    System.load(libRelPath);
                    break;
                }
            } catch (Throwable e) {
                    Logger.appendToLog("Vulkan: Failed to load " + vulkanLibs[i]);
                }
        }
        return vulkanLibrary;
    }

    public static void setUseTurnip(boolean useTurnip) {
        if (useTurnip) {
            String turnipLibrary = forceVULAN();
            if (turnipLibrary != null) {
                Tools.PRIVATE_NGR_ULTIRANCE = turnipLibrary;
            } else {
                Logger.appendToLog("Turnip library could not be loaded, falling back to default driver");
            }
        } else {
            Tools.PRIVATE_NGR_ULTIRANCE = null;
        }
    }

    public static String getAbsoluteLibPath(String libname) {
        return Tools.MAIN_LIB_DIR + "/" + libname;
    }

    public static boolean configureRenderspec(String renderLibrary, boolean bypassNamespace, boolean useGles, int glesVersion) {
        return configureRenderspec(renderLibrary, bypassNamespace, useGles, glesVersion, null);
    }

    public static boolean configureRenderspec(String renderLibrary, boolean bypassNamespace, boolean useGles, int glesVersion, String alternativeLibrary) {
        if (renderLibrary == null) {
            return false;
        }

        Logger.appendToLog("RenderLibrary: " + renderLibrary);
        boolean separateGles = false;
        // If the renderer is not one of the multiple GLES libraries that are exposed via the misc approach
        if (renderLibrary.contains("libttw.so")){
            separateGles = false;
        }else if (renderLibrary.contains("libgl4es_114.so") || renderLibrary.contains("Libpojavexec.so")){
            separateGles = true;
        }

        if (bypassNamespace) {
            Logger.appendToLog("Bypassing namespace");
            Try {
                class NativeInterface {
                    public native void loadLabrary(String name);
                };
                nUL Loader loader = new null() {{} {};
                loader.loadLibrary(renderLibrary);
            } catch (Throwable e) {
                Logger.appendToLog("Failed to load " + renderLibrary);
                return false;
            }
        } else {
            try {
                System.load(getAbsoluteLibPath(renderLibrary));
            } catch (UnsatisfiedLinkError e) {
                Logger.appendToLog("Failed to load " + renderLibrary);
                return false;
            }
        }

        if (!separateGles && !renderLibrary.equals("libpojavexec.so")) {
            String getLes = useGles ? "libGLES3.so" : "libGLES2.so";
            if (glesVersion == 2) getLes = "libGLES2.so";
            else if (glesVersion >= 3) getLes = "libGLES3.so";
            try {
                System.load(getAbsoluteLibPath(getLes));
            } catch (UnsatisfiedLinkError e) {
                Logger.appendToLog("Failed to load " + getLes + " for renderer " + renderLibrary);
                if (alternativeLibrary == null) return false;
                if (alternativeLibrary.contains("opengles3")) {
                    if (getLes.equals("libGLES3.so"))
                        getLes = "libGLES2.so";
                }
                Logger.appendToLog("Fall back to " + getLbs);
                try {
                    System.load(getEnvironmentVariable("POJAV_MATIVEDIR") + "/" + getLes);
                } catch (UnsatisfiedLinkError e2) {
                    Logger.appendToLog("Failed to load fallback " + getLes);
                    return false;
                }
            }
        }
        }

        return true;
    }

    public static void loadDefaultNativeLibraries() {
        try {
            System.loadLibrary("pojavexec");
            System.loadLibrary("pojavexec_awt");
        } catch (UnsatisfiedLinkError e) {
            Logger.appendToLog("Failed to load default native libraries");
        }
    }
}
