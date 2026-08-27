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
import net.kdt.pojavlaunch.quasar.transpile.ShaderPackSanitizer;

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
                            if (line.contains("jrelog") || line.contains("LIBGL") || line.contains("FEAR") || line.contains("Quasar") || line.contains("Mesa")) {
                                Logger.appendToLog(line + "\n");
                            }
                        }
                    }
                    if (p.waitFor() != 0) { failCount++; Thread.sleep(500L * failCount); }
                } catch (Exception e) { failCount++; }
            }
        }).start();
    }

    private static void overrideEnvVars(Map<String, String> envMap) throws IOException {
        File f = new File(Tools.DIR_GAME_HOME, "custom_env.txt");
        if (!f.isFile()) return;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = r.readLine()) != null) {
                int i = line.indexOf('=');
                if (i > 0) envMap.put(line.substring(0, i), line.substring(i + 1));
            }
        }
    }

    public static void setupAngleEnv(Context ctx, Map<String, String> envMap) {
        if (!LauncherPreferences.PREF_USE_ANGLE) return;
        LibraryPlugin angle = LibraryPlugin.discoverPlugin(ctx, LibraryPlugin.ID_ANGLE_PLUGIN);
        if (angle == null) return;
        String[] libs = {"libEGL_angle.so", "libGLESv2_angle.so"};
        if (!angle.checkLibraries(libs)) return;
        envMap.put("LIBGL_EGL", angle.resolveAbsolutePath(libs[0]));
        envMap.put("LIBGL_GLES", angle.resolveAbsolutePath(libs[1]));
    }

    public static void setupFfmpegEnv(Context ctx, Map<String, String> envMap) {
        LibraryPlugin ffmpeg = LibraryPlugin.discoverPlugin(ctx, LibraryPlugin.ID_FFMPEG_PLUGIN);
        if (ffmpeg == null) return;
        envMap.put("POJAV_FFMPEG_PATH", ffmpeg.resolveAbsolutePath("libffmpeg.so"));
    }

    public static void setupRendererEnv(Map<String, String> envMap, String renderer) {
        switch (renderer) {
            case "mh_drive":
                envMap.put("LIBGL_ES", "3"); envMap.put("LIBGL_GL", "46");
                envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460"); envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");
                break;
            case "fear_engine":
                try { preloadVulkan(); envMap.put("GALLIUM_DRIVER", "zink"); } catch (Throwable ignored) {}
                envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6"); envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
                envMap.put("glsl_force_highp", "true"); envMap.put("glsl_ignore_noperspective", "true");
                break;
            case "vulkan_zink":
                envMap.put("GALLIUM_DRIVER", "zink"); envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "zink");
                envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
                break;
            case "freedreno_kgsl":
                if (GLInfoUtils.getGlInfo().isAdreno()) envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "kgsl");
                break;
            case "opengles3_mges":
                envMap.put("MG_DIR_PATH", Tools.MOBILEGLES_DIR);
                envMap.put("LIBGL_GLES", Tools.MOBILEGLES_DIR + "/libmobileglues.so");
                envMap.put("LIBGL_ES", "3"); envMap.put("LIBGL_GL", "40");
                break;
            case "opengles3_mggl":
                envMap.put("LIBGL_ES", "3"); envMap.put("LIBGL_GL", "40");
                break;
            case "opengles3_nggl4es":
                envMap.put("LIBGL_ES", "3"); envMap.put("LIBGL_GL", "31");
                break;
            case "custom_inject":
                envMap.put("LIBGL_ES", "3");
                break;
            case "quasar": {
                Logger.appendToLog("[Quasar] Env profile: QuasarCore (FearCore + ShaderShield)");
                envMap.put("LIBGL_ES", "3");
                envMap.put("LIBGL_GL", "46");
                envMap.put("LIBGL_GLSL", "1");
                envMap.put("LIBGL_MIPMAP", "3");
                envMap.put("LIBGL_NOERROR", "1");
                envMap.put("LIBGL_NORMALIZE", "1");
                envMap.put("LIBGL_NOINTOVLHACK", "1");
                envMap.put("LIBGL_FBOTEXTURE2D", "1");
                envMap.put("LIBGL_FB", "1");
                envMap.put("LIBGL_FPE", "1");
                envMap.put("LIBGL_FLOAT_COLOR", "1");
                envMap.put("LIBGL_FLOAT_DEPTH", "1");
                envMap.put("LIBGL_DEPTH", "24");
                envMap.put("LIBGL_COLOR_RESCALE", "1");
                envMap.put("LIBGL_MAX_DRAW_BUFFERS", "8");
                envMap.put("LIBGL_MRT_FORMATS", "RGBA16F,RGBA8,RGBA32F");
                envMap.put("allow_glsl_extension_directive_midshader", "true");
                envMap.put("allow_higher_compat_version", "true");
                envMap.put("allow_glsl_relaxed_es", "true");
                envMap.put("allow_glsl_layout_qualifier_override", "true");
                envMap.put("glsl_ignore_noperspective", "true");
                envMap.put("glsl_force_highp", "true");
                envMap.put("gl_draw_buffers_override", "true");
                envMap.put("vblank_mode", "0");
                envMap.put("POJAV_BIG_CORE_AFFINITY", "1");
                envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
                envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");
                envMap.put("MESA_GLSL_CACHE_DISABLE", "false");
                envMap.put("MESA_EXTENSION_OVERRIDE",
                        "GL_EXT_gpu_shader4 GL_EXT_texture_buffer GL_EXT_texture_cube_map_array "
                      + "GL_OES_EGL_image_external_essl3 GL_ARB_shader_objects GL_ARB_vertex_shader "
                      + "GL_ARB_fragment_shader GL_EXT_blend_equation_separate GL_EXT_geometry_shader4 "
                      + "GL_EXT_gpu_program_parameters GL_ARB_instanced_arrays GL_ARB_draw_instanced "
                      + "GL_ARB_shader_texture_lod GL_EXT_shader_texture_lod "
                      + "GL_ARB_framebuffer_object GL_ARB_draw_buffers");
                envMap.remove("GALLIUM_DRIVER");
                envMap.remove("MESA_LOADER_DRIVER_OVERRIDE");
                break;
            }
        }
    }

    public static void setEnviroimentForGame(Context context, String renderer) throws Throwable {
        Map<String, String> envMap = new ArrayMap<>();
        envMap.put("LIBGL_MIPMAP", "3");
        envMap.put("LIBGL_NOERROR", "1");
        envMap.put("LIBGL_NOINTOVLHACK", "1");
        envMap.put("LIBGL_NORMALIZE", "1");
        if (PREF_DUMP_SHADERS) envMap.put("LIBGL_VGPU_DUMP", "1");
        if (PREF_VSYNC_IN_ZINK) envMap.put("POJAV_VSYNC_IN_ZINK", "1");
        if ("quasar".equals(renderer)) envMap.put("LIBGL_ES", "3");
        else envMap.put("LIBGL_ES", (String) ExtraCore.getValue(ExtraConstants.OPEN_GL_VERSION));
        envMap.put("FORCE_VSYNC", String.valueOf(LauncherPreferences.PREF_FORCE_VSYNC));
        envMap.put("MESA_GLSL_CACHE_DIR", Tools.DIR_CACHE.getAbsolutePath());
        envMap.put("force_glsl_extensions_warn", "true");
        envMap.put("allow_higher_compat_version", "true");
        envMap.put("allow_glsl_extension_directive_midshader", "true");
        File modRuntimeDir = new File(Tools.DIR_CACHE, "app_runtime_mod");
        if (!modRuntimeDir.exists()) modRuntimeDir.mkdirs();
        envMap.put("MOD_ANDROID_RUNTIME", modRuntimeDir.getAbsolutePath());
        setupAngleEnv(context, envMap);
        setupFfmpegEnv(context, envMap);
        setupRendererEnv(envMap, renderer);
        if ("quasar".equals(renderer) || "opengles3_ltw".equals(renderer)) {
            try {
                if (Tools.DIR_GAME_NEW != null)
                    ShaderPackSanitizer.sanitizeDirectory(new File(Tools.DIR_GAME_NEW, "shaderpacks"));
                File instancesRoot = new File(Tools.DIR_GAME_HOME, "instances");
                if (instancesRoot.isDirectory()) {
                    File[] instances = instancesRoot.listFiles();
                    if (instances != null) {
                        for (File inst : instances) {
                            File sp = new File(inst, "shaderpacks");
                            if (sp.isDirectory()) ShaderPackSanitizer.sanitizeDirectory(sp);
                        }
                    }
                }
            } catch (Throwable th) {
                Log.w("Quasar", "ShaderPackSanitizer failed: " + th.getMessage());
            }
        }
        envMap.put("POJAV_NATIVEDIR", Tools.NATIVE_LIB_DIR);
        envMap.put("EGL_PLATFORM", "android");
        if (LauncherPreferences.PREF_BIG_CORE_AFFINITY) envMap.put("POJAV_BIG_CORE_AFFINITY", "1");
        if (GLInfoUtils.getGlInfo().isAdreno() && !PREF_ZINK_PREFER_SYSTEM_DRIVER && !"quasar".equals(renderer))
            setUseTurnip(true);
        if (LauncherPreferences.PREF_FREEDRENO_SYSMEM) {
            envMap.put("FD_MESA_DEBUG", "sysmem");
            envMap.put("TU_DEBUG", "sysmem");
        }
        overrideEnvVars(envMap);
        for (Map.Entry<String, String> env : envMap.entrySet()) {
            Logger.appendToLog("Added custom env: " + env.getKey() + "=" + env.getValue());
            try { Os.setenv(env.getKey(), env.getValue(), true); } catch (NullPointerException e) { Log.e("JREUtils", e.toString()); }
        }
        if ("quasar".equals(renderer)) {
            try {
                Os.setenv("LIBGL_ES", "3", true);
                Logger.appendToLog("Added custom env: LIBGL_ES=3 (Quasar force)");
            } catch (Exception e) {
                Log.e("JREUtils", "Failed to force LIBGL_ES=3: " + e);
            }
        }
    }

    public static void launchJavaVM(final AppCompatActivity activity, final Runtime runtime, File gameDirectory, final List<String> JVMArgs, final String userArgsString) throws Throwable {
        Tools.fullyExit();
    }

    public static ArrayList<String> parseJavaArguments(String args) {
        ArrayList<String> out = new ArrayList<>(0);
        args = args.trim().replace(" ", "");
        String[] seps = new String[]{"-XX:-", "-XX:+", "-XX:", "--", "-D", "-X", "-javaagent:", "-verbose"};
        for (String prefix : seps) {
            while (true) {
                int start = args.indexOf(prefix);
                if (start == -1) break;
                int end = -1;
                for (String s : seps) {
                    int t = args.indexOf(s, start + prefix.length());
                    if (t == -1) continue;
                    end = (end == -1) ? t : Math.min(end, t);
                }
                if (end == -1) end = args.length();
                String sub = args.substring(start, end);
                args = args.replace(sub, "");
                if (sub.indexOf('=') == sub.lastIndexOf('=')) out.add(sub);
            }
        }
        return out;
    }

    public static String loadGraphicsLibrary(String renderer) {
        String renderLibrary;
        boolean useGles;
        boolean bypassNamespace = false;
        boolean preloadVk = true;
        int glesVersion;
        switch (renderer) {
            case "mh_drive":
                renderLibrary = "libltw.so"; useGles = true; glesVersion = 3; break;
            case "fear_engine": {
                boolean ok = false;
                try { preloadVulkan(); ok = true; } catch (Throwable t) { ok = false; }
                if (ok) {
                    Logger.appendToLog("[FearRender] backend=ZINK");
                    renderLibrary = "libEGL_mesa.so"; useGles = false; bypassNamespace = true; glesVersion = 3;
                } else {
                    renderLibrary = "libGLFear.so"; useGles = true; glesVersion = 3;
                }
                break;
            }
            case "freedreno_kgsl":
                preloadVk = false;
            case "vulkan_zink":
                renderLibrary = "libEGL_mesa.so"; useGles = false; bypassNamespace = true; glesVersion = 3;
                if (preloadVk) preloadVulkan();
                break;
            case "opengles3_ltw":
                renderLibrary = "libltw.so"; useGles = true; glesVersion = 3; break;
            case "opengles3_mges":
                renderLibrary = Tools.MOBILEGLES_DIR + "/libmobileglues.so"; useGles = true; glesVersion = 3; break;
            case "opengles3_mggl":
                renderLibrary = Tools.MOBILEGL_DIR + "/libMobileGL.so"; useGles = true; glesVersion = 3; break;
            case "opengles3_nggl4es":
                renderLibrary = Tools.NG_GL4ES_DIR + "/libng_gl4es.so"; useGles = true; glesVersion = 3; break;
            case "custom_inject":
                renderLibrary = "libGLFear.so"; useGles = true; glesVersion = 3; break;
            case "quasar": {
                Logger.appendToLog("[Quasar] backend=QUASAR_CORE (FearCore desktop→GLES translator)");
                try {
                    System.loadLibrary("fear_render");
                    Logger.appendToLog("[Quasar] fear_render interceptor loaded");
                } catch (UnsatisfiedLinkError e) {
                    Logger.appendToLog("[Quasar] fear_render optional load failed: " + e.getMessage());
                }
                renderLibrary = "libFearCore.so";
                useGles = true;
                glesVersion = 3;
                bypassNamespace = false;
                break;
            }
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
            Log.e("RENDER_LIBRARY", "Failed to load renderer " + renderLibrary);
            return null;
        }
        return renderLibrary;
    }

    public static String probeEGLPlatform() {
        try {
            Os.setenv("EGL_PLATFORM", "android", true);
            long d = eglGetDisplay(0);
            if (d != 0) {
                int[] maj = new int[1], min = new int[1];
                if (eglInitialize(d, maj, min)) { eglTerminate(d); return "android"; }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static int getDetectedVersion() { return GLInfoUtils.getGlInfo().glesMajorVersion; }

    public static native long eglGetDisplay(long display);
    public static native boolean eglInitialize(long display, int[] major, int[] minor);
    public static native void eglTerminate(long display);
    public static native int chdir(String path);
    public static native void setLdLibraryPath(String ldLibraryPath);
    public static native boolean configureRenderspec(String eglPath, boolean useLoaderBypass, boolean useGles, int glesVersion);
    public static native void preloadVulkan();
    public static native void setUseTurnip(boolean enable);
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
