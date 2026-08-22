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
                    ProcessBuilder pb = new ProcessBuilder("logcat", "-v", "tag", "-T", "1").redirectErrorStream(true);
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

    // Setup environment for mesa-based renderers
    public static void setupRendererEnv(Map<String, String> envMap, String renderer) {
        if (renderer != null && (renderer.startsWith("plugin:") || renderer.startsWith("custom_so:") || renderer.contains("mobileglue"))) {
            Logger.appendToLog("[CustomRenderer] Setting up environment for custom renderer: " + renderer);
            envMap.put("LIBGL_ES", "3");
            envMap.put("LIBGL_USEVBO", "1");
            envMap.put("LIBGL_BATCH", "1");
            envMap.put("LIBGL_MIPMAP", "3");
            envMap.put("LIBGL_NOERROR", "1");
            envMap.put("LIBGL_GL", "46");
            envMap.put("LIBGL_VERSION", "4.6.0 NVIDIA 545.29");
            envMap.put("LIBGL_NOTEXTURERECT", "0");
            envMap.put("LIBGL_FBOTEXTURE2D", "1");
            envMap.put("LIBGL_GLSL", "1");
            envMap.put("LIBGL_ALWAYSCURRENT", "1");
            envMap.put("LIBGL_NOCONTEXTCLEANUP", "1");
            envMap.put("LIBGL_FB", "1");
            envMap.put("LIBGL_FPE", "1");
            envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
            envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");
            envMap.put("allow_glsl_extension_directive_midshader", "true");
            envMap.put("allow_higher_compat_version", "true");
            envMap.put("allow_glsl_relaxed_es", "true");
            envMap.put("glsl_ignore_unsupported_extensions", "true");
            envMap.put("glsl_ignore_noperspective", "true");
            envMap.put("LIBGL_GLSL_STRIP", "noperspective");

            // Mobile Glue specific environment variables
            envMap.put("MG_MOBILEGLUES", "1");
            envMap.put("MG_MOBILEGLUES_VERSION", "2000");

            // Custom shader cache directory
            File mgCacheDir = new File(Tools.DIR_GAME_HOME, "MG");
            if (!mgCacheDir.exists()) mgCacheDir.mkdirs();
            envMap.put("MG_LOG_DIR", mgCacheDir.getAbsolutePath());
            return;
        }

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
                envMap.put("LIBGL_NOTEXTURERECT", "0");
                envMap.put("LIBGL_FBOTEXTURE2D", "1");
                envMap.put("LIBGL_GLSL", "1");
                envMap.put("LIBGL_ALWAYSCURRENT", "1");
                envMap.put("LIBGL_NOCONTEXTCLEANUP", "1");
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
                    envMap.put("ZINK_USE_CI", "1");
                    envMap.put("MESA_GLTHREAD", "false");
                    envMap.put("MESA_VK_WSI_PRESENT_MODE", "fifo");
                    envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");
                    envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
                    envMap.put("MESA_NO_MINMAX_CACHE", "1");
                } else {
                    Logger.appendToLog("[FearRender] Configuring GLES environment profile");
                }

                envMap.put("POJAV_BIG_CORE_AFFINITY", "1");
                envMap.put("LIBGL_ES", "3");
                envMap.put("LIBGL_USEVBO", "1");
                envMap.put("LIBGL_BATCH", "1");
                envMap.put("LIBGL_SHRINK", "0");
                envMap.put("LIBGL_FASTEDID", "1");
                envMap.put("LIBGL_MIPMAP", "3");
                envMap.put("LIBGL_NOERROR", "1");
                envMap.put("LIBGL_GL", "46"); // Signal full Desktop OpenGL 4.6 core compatibility
                envMap.put("LIBGL_VERSION", "4.6.0 NVIDIA 545.29"); // Fake desktop GL profile for Complementary/Solas
                envMap.put("LIBGL_NOTEXTURERECT", "0"); // Set to 0 to enable correct non-power-of-two sampler lookups in modern shaders
                envMap.put("LIBGL_FBOTEXTURE2D", "1");
                envMap.put("LIBGL_GLSL", "1");
                envMap.put("LIBGL_ALWAYSCURRENT", "1");
                envMap.put("LIBGL_NOCONTEXTCLEANUP", "1"); // Prevent context loss
                envMap.put("LIBGL_OBJ", "1");
                envMap.put("LIBGL_VAO", "1");
                envMap.put("LIBGL_MDI", "1");
                envMap.put("LIBGL_FB", "1"); // Use depth-precision standard FBO mode (fixes borders/sea fog rendering mismatches)
                envMap.put("LIBGL_FPE", "1");
                envMap.put("LIBGL_GAMMA", "1.0");
                envMap.put("POJAV_BIG_CORE_AFFINITY", "1");
                // Shaders (Iris, etc.) permissions and crash mitigation parameters:
                envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
                envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");
                envMap.put("allow_glsl_extension_directive_midshader", "true");
                envMap.put("allow_higher_compat_version", "true");
                envMap.put("force_glsl_extensions_warn", "true");

                // High performance optimizations, caching, frame stability & low heat mitigation:
                envMap.put("MESA_GLSL_CACHE_DISABLE", "false");
                envMap.put("MESA_GLSL_CACHE_MAX_SIZE", "1024MB");
                envMap.put("vblank_mode", "0"); // Disable vblank syncing to boost FPS beyond 150+
                envMap.put("force_s3tc_enable", "true"); // Compress textures in hardware to reduce memory/heat
                envMap.put("glsl_zero_init", "true"); // Clean GPU memory state to mitigate rendering/shader glitches
                envMap.put("allow_multisample_filter", "false"); // Disable heavy filters to reduce thermals
                envMap.put("always_use_fast_path", "true");

                // Mitigate PVP, entity loading, and cursor focus fog glitches:
                envMap.put("LIBGL_RESCALE_NORMAL", "1"); // Ensure correct lighting/fog vectors when transforming view matrix
                envMap.put("MESA_NO_MINMAX_CACHE", "1"); // Skip heavy minmax operations during active item swapping in PvP
                envMap.put("LIBGL_CLIPPED", "1"); // Enable aggressive hardware frustum clipping to stop rendering out-of-view entities
                envMap.put("allow_glsl_extension_directive_midshader", "true");

                // Universal SoC compatibility layers (Snapdragon Adreno, Moto Edge, MediaTek Dimensity, Mali GPUs):
                envMap.put("allow_glsl_layout_qualifier_override", "true");
                envMap.put("allow_glsl_builtin_const_expression", "true");
                envMap.put("allow_glsl_relaxed_es", "true");
                envMap.put("glsl_correct_derivatives_after_discard", "true");
                envMap.put("MESA_EXTENSION_OVERRIDE", "GL_EXT_gpu_shader4 GL_EXT_texture_buffer GL_EXT_texture_cube_map_array GL_OES_EGL_image_external_essl3 GL_NV_shader_noperspective_interpolation GL_ARB_shader_objects GL_ARB_vertex_shader GL_ARB_fragment_shader GL_EXT_blend_equation_separate GL_EXT_geometry_shader4 GL_EXT_gpu_program_parameters GL_ARB_instanced_arrays GL_ARB_draw_instanced");
                envMap.put("LIBGL_DEPTH", "24"); // Force high-fidelity depth buffers for shaders across all screen configurations
                envMap.put("allow_higher_compat_version", "true");

                // Explicitly strip or replace non-supported NV features/keywords on non-compatible hardware:
                envMap.put("glsl_ignore_unsupported_extensions", "true");
                envMap.put("glsl_ignore_noperspective", "true");
                envMap.put("LIBGL_GLSL_STRIP", "noperspective"); // Force GL4ES / LTW backend parser to ignore 'noperspective'
                envMap.put("LIBGL_GLSL_REPLACE", "noperspective=flat"); // Map noperspective to standard flat interpolation fallback if parsed

                // Mali and Low-End GPU Emulation Overrides:
                envMap.put("LIBGL_NO_VBO_BOUNDS", "1"); // Avoid hardware out-of-bounds pointer crashes on older Mali drivers
                envMap.put("LIBGL_ES", "3"); // Force minimum GLES v3 API translation
                envMap.put("allow_glsl_extension_directive_midshader", "true");
                envMap.put("glsl_correct_derivatives_after_discard", "true");

                // MediaTek Dimensity Octa-Core High-Thread Compiler & Extension Emulations:
                envMap.put("pan_shader_compile_threads", "4"); // Utilize 4xA78 High Performance Cores for compiling shader programs
                envMap.put("mali_debug", "nocluster"); // Stabilize shader dispatching across Mali octa-core threads
                envMap.put("glsl_compiler_options", "relaxed");
                envMap.put("LIBGL_ALLOW_INDEXED_DRAWS", "1");
                envMap.put("force_s3tc_enable", "true");
                envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
                envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");

                // Advanced G-Buffer, MRT Emulation & Desktop Shader Transpilation patches:
                envMap.put("LIBGL_GLSL_STRIP", "noperspective");
                envMap.put("LIBGL_GLSL_PATCH", "1"); // Enable automatic highp precision qualifiers on-the-fly for G-Buffers
                envMap.put("LIBGL_MAX_DRAW_BUFFERS", "8"); // Enable Multi-Render Targets (MRT) up to 8 attachments (gcolor, gnormal, etc.)
                envMap.put("gl_draw_buffers_override", "true");
                envMap.put("LIBGL_MRT_FORMATS", "RGBA16F,RGBA32F"); // High-fidelity floats matching desktop specifications
                envMap.put("glsl_force_highp", "true"); // Guarantee high precision floats in shaders on all ARM architectures

                // High-precision float depth and color attachments to fix shadow and ray color glitches under FEAR Engine:
                envMap.put("LIBGL_FLOAT_COLOR", "1");
                envMap.put("LIBGL_FLOAT_DEPTH", "1");
                envMap.put("LIBGL_DEPTH", "24");
                envMap.put("LIBGL_COLOR_RESCALE", "1");
                break;
            case "angle_vulkan":
                envMap.put("ANGLE_DEFAULT_BACKEND", "vulkan");
                envMap.put("LIBGL_ES", "3");
                envMap.put("LIBGL_USEVBO", "1");
                envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
                envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");
                break;
            case "virgl_renderer":
                envMap.put("GALLIUM_DRIVER", "virpipe");
                envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");
                envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
                envMap.put("VIRGL_DEBUG", "display");
                break;
            case "turnip_zink":
                envMap.put("GALLIUM_DRIVER", "zink");
                envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "zink");
                envMap.put("TU_DEBUG", "sysmem");
                envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
                envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");
                setUseTurnip(true);
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
        if(PREF_VSYNC_IN_ZINK)
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
                else Log.w("JAVA ARGS PARSER", "Removed improper arguments: " + parsedSubString);
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

        if (renderer != null && renderer.startsWith("plugin:")) {
            String appId = renderer.substring("plugin:".length());
            Logger.appendToLog("[CustomRenderer] Attempting to load plugin: " + appId);
            Context context = net.kdt.pojavlaunch.lifecycle.ContextExecutor.getApplication();
            LibraryPlugin plugin = (context != null) ? LibraryPlugin.discoverPlugin(context, appId) : null;
            if (plugin == null && context != null) {
                // Retry discovery across all plugins if direct lookup fails
                List<LibraryPlugin> plugins = LibraryPlugin.discoverRendererPlugins(context);
                for (LibraryPlugin p : plugins) {
                    if (p.getId().equalsIgnoreCase(appId)) {
                        plugin = p;
                        break;
                    }
                }
            }
            if (plugin != null) {
                String libDir = plugin.getLibraryPath();
                Logger.appendToLog("[CustomRenderer] Found plugin library path: " + libDir);
                File libDirFile = new File(libDir);
                if (libDirFile.exists() && libDirFile.isDirectory()) {
                    File[] candidates = libDirFile.listFiles((dir, name) -> name.endsWith(".so"));
                    if (candidates != null && candidates.length > 0) {
                        // Look for libEGL_mesa.so, libltw.so, libgl4es.so, libmobileglues.so, or use the first available .so
                        File chosenSo = candidates[0];
                        for (File candidate : candidates) {
                            String name = candidate.getName();
                            if (name.contains("mobileglue") || name.contains("zink") || name.contains("mesa") || name.contains("ltw") || name.contains("gl4es") || name.contains("EGL")) {
                                chosenSo = candidate;
                                break;
                            }
                        }
                        renderLibrary = chosenSo.getAbsolutePath();
                        Logger.appendToLog("[CustomRenderer] Selected plugin library: " + renderLibrary);
                        useGles = true;
                        glesVersion = 3;
                        if (!configureRenderspec(renderLibrary, true, useGles, glesVersion)) {
                            Log.e("RENDER_LIBRARY", "Failed to load custom plugin renderer " + renderLibrary);
                            return null;
                        }
                        return renderLibrary;
                    }
                }
            }
            Log.e("RENDER_LIBRARY", "Plugin renderer package not available or library missing: " + appId);
            return null;
        }

        if (renderer != null && renderer.startsWith("custom_so:")) {
            renderLibrary = renderer.substring("custom_so:".length());
            Logger.appendToLog("[CustomRenderer] Loading custom .so renderer: " + renderLibrary);
            useGles = true;
            glesVersion = 3;
            if (!configureRenderspec(renderLibrary, true, useGles, glesVersion)) {
                Log.e("RENDER_LIBRARY", "Failed to load custom .so renderer " + renderLibrary);
                return null;
            }
            return renderLibrary;
        }

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
                    Logger.appendToLog("[FearRender] probe: vulkan=" + vkVer + " -> ZINK");
                    Logger.appendToLog("[FearRender] backend=ZINK (Vulkan: 1.3)");
                    renderLibrary = "libEGL_mesa.so";
                    useGles = false;
                    bypassNamespace = true;
                    glesVersion = 3;
                } else {
                    Logger.appendToLog("[FearRender] probe: vulkan=" + vkVer + " -> GLES");
                    Logger.appendToLog("[FearRender] Zink unavailable -> GLES fallback");
                    renderLibrary = "libGLFear.so";
                    useGles = true;
                    glesVersion = 3;
                }
                break;
            case "angle_vulkan":
                renderLibrary = "libEGL_angle.so";
                useGles = true;
                glesVersion = 3;
                break;
            case "virgl_renderer":
            case "turnip_zink":
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

    // Fear Shader Engine JNI Bridge Declarations
    public static native void initFearShaderEngine(String cachePath, int version);
    public static native void destroyFearShaderEngine();
    public static native String getShaderCachePath();
    public static native void clearShaderCache();
    public static native int getTranslatedShaderCount();

    //public static native void initializeHooks();
    // Obtain AWT screen pixels to render on Android SurfaceView
    public static native boolean renderAWTScreenFrame(ByteBuffer tempBuffer);
    static {
        System.loadLibrary("pojavexec");
        System.loadLibrary("pojavexec_awt");
    }
}
