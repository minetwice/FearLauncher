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
            case "fear_turbo":
                Logger.appendToLog("[FearTurbo] Initializing Standalone GLES Engine Environment...");
                
                boolean isMaliGPU = false;
                try {
                    isMaliGPU = GLInfoUtils.getGlInfo().isArm();
                    if (isMaliGPU) {
                        Logger.appendToLog("[FearTurbo] Mali GPU detected, using OpenGL 2.1 for compatibility");
                    }
                } catch (Throwable t) {
                    Log.w("FearTurbo", "Failed to detect GPU info, assuming non-Mali", t);
                }
                
                if (isMaliGPU) {
                    envMap.put("LIBGL_ES", "2");
                    envMap.put("LIBGL_USEVBO", "1");
                    envMap.put("LIBGL_BATCH", "1");
                    envMap.put("LIBGL_MIPMAP", "3");
                    envMap.put("LIBGL_NOERROR", "1");
                    envMap.put("LIBGL_GL", "21");
                    envMap.put("LIBGL_VERSION", "2.1.0");
                    envMap.put("LIBGL_NOTEXTURERECT", "0");
                    envMap.put("LIBGL_FBOTEXTURE2D", "1");
                    envMap.put("LIBGL_GLSL", "1");
                    envMap.put("LIBGL_ALWAYSCURRENT", "1");
                    envMap.put("LIBGL_NOCONTEXTCLEANUP", "1");
                    envMap.put("LIBGL_FB", "1");
                    envMap.put("LIBGL_FPE", "1");
                    envMap.put("LIBGL_MAX_DRAW_BUFFERS", "8");
                    envMap.put("LIBGL_MRT_FORMATS", "RGBA16F,RGBA32F");
                    envMap.put("LIBGL_FLOAT_COLOR", "1");
                    envMap.put("LIBGL_FLOAT_DEPTH", "1");
                    envMap.put("LIBGL_DEPTH", "24");
                    envMap.put("LIBGL_COLOR_RESCALE", "1");
                    envMap.put("MESA_GLSL_VERSION_OVERRIDE", "120");
                    envMap.put("MESA_GL_VERSION_OVERRIDE", "2.1");
                    envMap.put("allow_glsl_extension_directive_midshader", "true");
                    envMap.put("allow_higher_compat_version", "true");
                    envMap.put("allow_glsl_relaxed_es", "true");
                    envMap.put("glsl_ignore_unsupported_extensions", "true");
                    envMap.put("glsl_ignore_noperspective", "true");
                    envMap.put("LIBGL_GLSL_STRIP", "noperspective");
                    envMap.put("LIBGL_GLSL_REPLACE", "noperspective=smooth");
                    envMap.put("glsl_force_highp", "true");
                    envMap.put("mali_debug", "nocluster");
                    envMap.put("pan_shader_compile_threads", "4");
                    envMap.put("vblank_mode", "0");
                    envMap.put("force_s3tc_enable", "true");
                    envMap.put("glsl_zero_init", "true");
                    envMap.put("MESA_GLSL_CACHE_DISABLE", "false");
                    envMap.put("MESA_GLSL_CACHE_MAX_SIZE", "2048MB");
                } else {
                    envMap.put("LIBGL_ES", "3");
                    envMap.put("LIBGL_USEVBO", "1");
                    envMap.put("LIBGL_BATCH", "1");
                    envMap.put("LIBGL_MIPMAP", "3");
                    envMap.put("LIBGL_NOERROR", "1");
                    envMap.put("LIBGL_GL", "43");
                    envMap.put("LIBGL_VERSION", "4.3.0 NVIDIA 545.29");
                    envMap.put("LIBGL_NOTEXTURERECT", "0");
                    envMap.put("LIBGL_FBOTEXTURE2D", "1");
                    envMap.put("LIBGL_GLSL", "1");
                    envMap.put("LIBGL_ALWAYSCURRENT", "1");
                    envMap.put("LIBGL_NOCONTEXTCLEANUP", "1");
                    envMap.put("LIBGL_FB", "1");
                    envMap.put("LIBGL_FPE", "1");
                    envMap.put("LIBGL_MAX_DRAW_BUFFERS", "8");
                    envMap.put("LIBGL_MRT_FORMATS", "RGBA16F,RGBA32F");
                    envMap.put("LIBGL_FLOAT_COLOR", "1");
                    envMap.put("LIBGL_FLOAT_DEPTH", "1");
                    envMap.put("LIBGL_DEPTH", "24");
                    envMap.put("LIBGL_COLOR_RESCALE", "1");
                    envMap.put("MESA_GLSL_VERSION_OVERRIDE", "430");
                    envMap.put("MESA_GL_VERSION_OVERRIDE", "4.3");
                    envMap.put("allow_glsl_extension_directive_midshader", "true");
                    envMap.put("allow_higher_compat_version", "true");
                    envMap.put("allow_glsl_relaxed_es", "true");
                    envMap.put("glsl_ignore_unsupported_extensions", "true");
                    envMap.put("glsl_ignore_noperspective", "true");
                    envMap.put("LIBGL_GLSL_STRIP", "noperspective");
                    envMap.put("LIBGL_GLSL_REPLACE", "noperspective=smooth");
                    envMap.put("glsl_force_highp", "true");
                    envMap.put("mali_debug", "nocluster");
                    envMap.put("pan_shader_compile_threads", "4");
                    envMap.put("vblank_mode", "0");
                    envMap.put("force_s3tc_enable", "true");
                    envMap.put("glsl_zero_init", "true");
                    envMap.put("MESA_GLSL_CACHE_DISABLE", "false");
                    envMap.put("MESA_GLSL_CACHE_MAX_SIZE", "2048MB");
                }
                break;
            case "vulkan_zink":
                envMap.put("GALLIUM_DRIVER", "zink");
                envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "zink");
                envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
                break;
        }
    }