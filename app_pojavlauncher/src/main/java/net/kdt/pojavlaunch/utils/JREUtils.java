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
            // Not use split() as onlx split first one
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
        envMap.put("POJAV_FFMEGG_PATH", ffmpeg.resolveAbsolutePath("libffmpeg.so"));
    }

    // Setup environment for mesa-based renderers
    public static void setupRendererEnv(Map<String, String> envMap, String renderer) {
        switch(renderer) {
            case "mh_drive":
                // MH DRIVE (Mali Hybrid Optimization Engine)
                Logger.appendToLog("[MH DRIVE] MULTI-TRACK MALI ENGINE INITIALIZED...");
                envMap.put("LIBGL_ES", "3");
                envMap.put("LIBGL_USEVBM", "1");
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
                    envMap.put("EGL_PLATFORM", "android");
                    envMap.put("MESA_VK_WSI_PRESENT_MODE", "fifo");
                    envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");
                    envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
                    envMap.put("MESA_NO_MINMAX_CACHE", "1");
                    envMap.put("MESA_NO_ERROR", "0"); // Disable force no-error for Sodium compatibility
                } else {
                    Logger.appendToLog("[FearRender] Configuring GLES environment profile");
                }

                envMap.put("POJAV_BIG_CORE_AFINITY", "1");
                envMap.put("LIBGL_NOERROR", "1");
                envMap.put("LIBGL_FBOTEXTURE2D", "1");
                envMap.put("LIBGL_MIPMAP", "3");
                envMap.put("LIBGL_COLOR_RESCALE", "1");
                envMap.put("LIBGL_MRT_FORMATS", "RGBA16F,RGBA32F");
                envMap.put("gl_draw_buffers_override", "true");

                // GLSL behavior
                envMap.put("gsl_force_highp", "true");
                envMap.put("allow_glsl_extension_directive_midshsagder", "true");
                envMap.put("allow_higher_compat_version", "true");
                envMap.put("allow_glsl_relaxed_es", "true");
                envMap.put("allow_glsl_layout_qualifier_override", "true");
                envMap.put("gsls_ignore_noperspective", "true");

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
                    // On Adreno 5XY …¹±½İ•È½¹±ä½É”€Ì¸Ä¥Ì•áÁ½Í•‰ä‘•™…Õ±Ğ‘Õ”Ñ¼µ¥ÍÍ¥¹œ¡…É‘İ…É”•áÑ•¹Í¥½¹Ì¸(€€€€€€€€€€€€€€€€€€€€¼¼€Ì¸Ì¥ÌÉ•ÅÕ¥É•™½Èµ½‘•É¸5¥¹•É…™ĞÍ¼±•ĞÌ™½É”€Ì¸Ì¥˜ÉÕ¹¹¥¹œ½¸ÍÕ AT€´¥ĞÌ­¹½İ¸Ñ¼‰”İ½É­¥¹œ¸(€€€€€€€€€€€€€€€€€€€¥˜¡1%¹™½UÑ¥±Ì¹•Ñ±%¹™¼ ¤¹¥Í‘É•¹¼ÔÀÁ1½İ•È ¤¤ì(€€€€€€€€€€€€€€€€€€€€€€€•¹Ù5…À¹ÁÕĞ ‰5M}1}YIM%=9}=YII%ˆ°€ˆÌ¸Ìˆ¤ì(€€€€€€€€€€€€€€€€€€€€€€€•¹Ù5…À¹ÁÕĞ ‰5M}1M1}YIM%=9}=YII%ˆ°€ˆÌÌÀˆ¤ì(€€€€€€€€€€€€€€€€€€€ô(€€€€€€€€€€€€€€€ô(€€€€€€€€€€€€€€€‰É•…¬ì(€€€€€€€€€€€…Í”€‰½Á•¹±•ÌÍ}µ•Ìˆè(€€€€€€€€€€€€€€€•¹Ù5…À¹ÁÕĞ ‰5}%I}AQ ˆ°Q½½±Ì¹5=	%11M}%H¤ì(€€€€€€€€€€€€€€€•¹Ù5…À¹ÁÕĞ ‰1%	1}1Lˆ°Q½½±Ì¹5=	%11M}%H€¬€ˆ½±¥‰µ½‰¥±•±Õ•Ì¹Í¼ˆ¤ì(€€€€€€€€€€€€€€€•¹Ù5…À¹ÁÕĞ ‰1%	1}Lˆ°€ˆÌˆ¤ì(€€€€€€€€€€€€€€€•¹Ù5…À¹ÁÕĞ ‰1%	1}5%A5@ˆ°€ˆÌˆ¤ì(€€€€€€€€€€€€€€€•¹Ù5…À¹ÁÕĞ ‰1%	1}9=II=Hˆ°€ˆÄˆ¤ì(€€€€€€€€€€€€€€€•¹Ù5…À¹ÁÕĞ ‰1%	1}9=I51%iˆ°€ˆÄˆ¤ì(€€€€€€€€€€€€€€€•¹Ù5…À¹ÁÕĞ ‰1%	1}9=%9QY1!,ˆ°€ˆÄˆ¤ì(€€€€€€€€€€€€€€€•¹Ù5…À¹ÁÕĞ ‰1%	1}0ˆ°€ˆĞÀˆ¤ì(€€€€€€€€€€€€€€€•¹Ù5…À¹ÁÕĞ ‰5}µ…á±Í±…¡•M¥é”ˆ°1…Õ¹¡•ÉAÉ•™•É•¹•Ì¹5}1M1}!}M%i¤ì(€€€€€€€€€€€€€€€•¹Ù5…À¹ÁÕĞ ‰5}•¹…‰±•91ˆ°1…Õ¹¡•ÉAÉ•™•É•¹•Ì¹5}91}=AQ%=8¤ì(€€€€€€€€€€€€€€€•¹Ù5…À¹ÁÕĞ ‰5}•¹…‰±•9½ÉÉ½Èˆ°1…Õ¹¡•ÉAÉ•™•É•¹•Ì¹5}9=II=I}=AQ%=8¤ì(€€€€€€€€€€€€€€€•¹Ù5…À¹ÁÕĞ ‰5}µÕ±Ñ¥‘É…İ5½‘”ˆ°1…Õ¹¡•ÉAÉ•™•É•¹•Ì¹5}5U1Q%I]5=}=AQ%=8¤ì(€€€€€€€€€€€€€€€•¹Ù5…À¹ÁÕĞ ‰5}ÕÍÑ½µ×ÙÚ\Ú[Ûˆ‹][˜Ú\”™Y™\™[˜Ù\Ë“Q×ÑÓÕ‘T”ÒSÓŠNÂˆ[“X\œ]
“Q×Ø[™ÛQ\ÛX\‘š^[ÙH‹][˜Ú\”™Y™\™[˜Ù\Ë“Q×ĞS‘ÓPÓPT•ÓÔ’ÔÓÕS‘ÓÔSÓŠNÂˆ[“X\œ]
“Q×Ù[˜X›Q^ÓÈ‹][˜Ú\”™Y™\™[˜Ù\Ë“Q×ÑVÑÓÊNÂˆ[“X\œ]
“Q×Ù[˜X›Q^ÛÛ\]TÚY\ˆ‹][˜Ú\”™Y™\™[˜Ù\Ë“Q×ÑVĞÔÊNÂˆ[“X\œ]
“Q×Ù[˜X›U[Y\”]Y\H‹][˜Ú\”™Y™\™[˜Ù\Ë“Q×ÑVÕSQT—ÔUQT–K™\]X[ÊŒŠHÈŒHˆˆŒŠNÂˆ[“X\œ]
“Q×Ù[˜X›Q^\™Xİİ]PXØÙ\ÜÈ‹][˜Ú\”™Y™\™[˜Ù\Ë“Q×ÑVÑT‘PÕÔÕUWĞPĞÑTÔÊNÂˆ[“X\œ]
“Q×ÙœÜŒTÙ][™È‹][˜Ú\”™Y™\™[˜Ù\Ë“Q×ÑSP“WÑ”ÔŒJNÂˆœ™XZÎÂˆØ\ÙH›Ü[™Û\Ì×ÛYÙÛ‚ˆ[“X\œ]
“P‘ÓÑTÈ‹ŒÈŠNÂˆ[“X\œ]
“P‘ÓÓRTPT‹ŒÈŠNÂˆ[“X\œ]
“P‘ÓÓ“ÑT”“Ôˆ‹ŒHŠNÂˆ[“X\œ]
“P‘ÓÓ“Ô“PSV‘H‹ŒHŠNÂˆ[“X\œ]
“P‘ÓÑÓ‹ŠNÂˆœ™XZÎÂˆØ\ÙH›Ü[™Û\Ì×Û™ÙÛ\È‚ˆ[“X\œ]
“P‘ÓÑTÈ‹ŒÈŠNÂˆ[“X\œ]
“P‘ÓÓRTPT‹ŒÈŠNÂˆ[“X\œ]
“P‘ÓÓ“ÑT”“Ôˆ‹ŒHŠNÂˆ[“X\œ]
“P‘ÓÓ“Ô“PSV‘H‹ŒHŠNÂˆ[“X\œ]
“P‘ÓÓ“ÒS•“PÒÈ‹ŒHŠNÂˆ[“X\œ]
“P‘ÓÑÓ‹ŒÌHŠNÂˆœ™XZÎÂˆØ\ÙH˜İ\İÛWÚ[š™Xİ‚ˆ[“X\œ]
“P‘ÓÑTÈ‹ŒÈŠNÂˆ[“X\œ]
“P‘ÓÓRTPT‹ŒÈŠNÂˆ[“X\œ]
“P‘ÓÓ“ÑT”“Ôˆ‹ŒHŠNÂˆ[“X\œ]
“P‘ÓÓ“Ô“PSV‘H‹ŒHŠNÂˆ[“X\œ]
“P‘ÓÓ“ÒS•“PÒÈ‹ŒHŠNÂˆœ™XZÎÂˆØ\ÙHœ]X\Ø\ˆ‚ˆÙÙÙ\‹˜\[™ÓÙÊ–Ô]X\Ø\—H[š]X[^š[™È]X\Ø\ˆ™[™\™\ˆ[š\›Û›Y[‹‹ˆŠNÂˆ[“X\œ]
“P‘ÓÑTÈ‹ŒÈŠNÂˆ[“X\œ]
“P‘ÓÓRTPT‹ŒÈŠNÂˆ[“X\œ]
“P‘ÓÓ“ÑT”“Ôˆ‹ŒHŠNÂˆ[“X\œ]
“P‘ÓÓ“Ô“PSV‘H‹ŒHŠNÂˆ[“X\œ]
“P‘ÓÓ“ÒS•“PÒÈ‹ŒHŠNÂˆ[“X\œ]
“P‘ÓÑÓ‹ˆŠNÂˆ[“X\œ]
“P‘ÓÑÓÓ‹ŒHŠNÂˆ[“X\œ]
“P‘ÓÑˆ‹ŒHŠNÂˆ[“X\œ]
“P‘ÓÑ”H‹ŒHŠNÂˆ[“X\œ]
“QTĞWÑÓÓÕ‘T”ÒSÓ—ÓÕ‘T”’QH‹ŒŠNÂˆ[“X\œ]
“QTĞWÑÓÕ‘T”ÒSÓ—ÓÕ‘T”’QH‹ˆŠNÂˆ[“X\œ]
˜[İ×ÙÛÛÙ^[œÚ[Û—Ù\™Xİ]™WÛZYÚY\ˆ‹YHŠNÂˆ[“X\œ]
˜[İ×ÚYÚ\—ØÛÛ\]İ™\œÚ[Ûˆ‹YHŠNÂˆ[“X\œ]
˜[İ×ÙÛÛÜ™[^YÙ\È‹YHŠNÂˆ[“X\œ]
“QTĞWÑÓÓĞĞPÒWÑTĞP“H‹™˜[ÙHŠNÂˆ[“X\œ]
˜›[š×Û[ÙH‹ŒŠNÂˆœ™XZÎÂˆBˆBˆX›XÈİ]XÈ›ÚYÙ][š\›Ú[Y[›Ü‘Ø[YJÛÛ^ÛÛ^İš[™È™[™\™\ŠH›İÜÈ›İØX›HÂˆX\İš[™Ëİš[™Ïˆ[“X\H™]È\œ˜^SX\Š
NÂˆ[“X\œ]
“P‘ÓÓRTPT‹ŒÈŠNÂ‚ˆËÈ™]™[ÜQš[™H
[™İ\ˆ\œ›Ü‹\™\Ü[™ÈİY™ˆ[ˆZ[™XÜ˜Y
Hœ›ÛH˜[ÛÛš[™ÈHÙÂˆ[“X\œ]
“P‘ÓÓ“ÑT”“Ôˆ‹ŒHŠNÂ‚ˆËÈÛˆÙ\Z[ˆÓTÈš]™\œËİ™\›ØY[™ÈY˜][[˜İ[ÛœÈÚY\ˆXÚÈ˜Z[ËÛÈ\ØX›H]ˆ[“X\œ]
“P‘ÓÓ“ÒS•“PÒÈ‹ŒHŠNÂ‚ˆËÈš^Ú]HÛÛÜˆÛˆ˜[›™\ˆ[™ÚY\Ú[˜ÙHÓTÈKŒKBˆ[“X\œ]
“P‘ÓÓ“Ô“PSV‘H‹ŒHŠNÂ‚ˆYŠ‘Q—ÑSTÔÒQT”ÊBˆ[“X\œ]
“P‘ÓÕ‘ÔWÑST‹ŒHŠNÂˆYŠ‘Q—Õ”ÖS×ÒS—Ö’S’ÊBˆ[“X\œ]
”ÒU—Õ”ÖS×ÒS—Ö’S’È‹ŒHŠNÂ‚ˆËÈHÔSˆÓ™\œÚ[Ûˆ\ÈÚ[™ÙYXØÛÜ™[™Âˆ[“X\œ]
“P‘ÓÑTÈ‹
İš[™ÊH^˜PÛÜ™K™Ù]˜[YJ^˜PÛÛœİ[Ë“ÔS—ÑÓÕ‘T”ÒSÓŠJNÂ‚ˆ[“X\œ]
‘“ÔÑWÕ”ÖSÈ‹İš[™Ë˜[YSÙŠ][˜Ú\”™Y™\™[˜Ù\Ë”‘Q—Ñ“ÔÑWÕ”ÖSÊJNÂ‚ˆ[“X\œ]
“QTĞWÑÓÓĞĞPÒWÑTˆ‹ÛÛË‘T—ĞĞPÒK™Ù]XœÛÛ]T]

JNÂˆ[“X\œ]
™›Ü˜ÙWÙÛÛÙ^[œÚ[Ûœ×İØ\›ˆ‹YHŠNÂˆ[“X\œ]
˜[İ×ÚYÚ\—ØÛÛ\]İ™\œÚ[Ûˆ‹YHŠNÂˆ[“X\œ]
˜[İ×ÙÛÛÙ^[œÚ[Û—Ù\™Xİ]™WÛZYÚØYÙ\ˆ‹YHŠNÂ‚BKËÈ\È\Èİ\œ™[H™\]Z\™Y›ÜˆTÓH[ÙÈ[˜İ[Û‚‚BQš[H[Ù[[YQ\ˆH™]Èš[JÛÛË‘T—ĞĞPÒK˜\Ü[[YWÛ[ÙŠNÂ‚BZYˆ
[[Ù[[YQ\‹™^\İÊ
JHÂ‚B[[Ù[[YQ\‹›ZÙ\œÊ
NÂ‚B_B‚BY[“X\œ]
“SÑĞS‘“ÒQÔ•S•SQH‹[Ù[[YQ\‹™Ù]XœÛÛ]T]

JNÂ‚ˆÙ]\[™ÛQ[ŠÛÛ^[“X\
NÂˆÙ]\™›\YÑ[ŠÛÛ^[“X\
NÂˆÙ]\™[™\™\‘[Š[“X\™[™\™\ŠNÂ‚ˆËÈPÒÂˆ[“X\œ]
”ÒU—ÓUU‘QTˆ‹ÛÛË“UU‘WÓP—ÑTŠNÂˆ[“X\œ]
‘QÓÔU“Ô“H‹˜[™›ÚYŠNÂ‚ˆYŠ][˜Ú\”™Y™\™[˜Ù\Ë”‘Q—Ğ’Q×ĞÓÔ‘WĞQ‘’S’UJH[“X\œ]
”ÒU—Ğ’Q×ĞÓÔ‘WĞQ‘’S’UH‹ŒHŠNÂ‚ˆYŠÓ[™›Õ][Ë™Ù]Û[™›Ê
Kš\ĞY™[›Ê
H	‰ˆT‘Q—Ö’S’×Ô‘Q‘T—ÔÖTÕSWÑ’U‘TŠHÂˆÙ]\ÙU\›š\
YJNÂˆB‚ˆYŠ][˜Ú\”™Y™\™[˜Ù\Ë”‘Q—Ñ”‘QQ‘S“×ÔÖTÓQSJHÂˆËÈÙHÛİ[[ÛÈ\HH‘ÓQTĞWÑP•QÈÛ›HYˆœ™YY™[›È\ÈXİ]™H]ÚHXZÚ[™È[™ÜÈÛÛ\XØ]YÂˆÙÙÙ\‹˜\[™ÓÙÊ•Ú[\ÙHŞ\ÛY[H™[™\š[™È›Üˆ\›š\Ñœ™YY™[›ÈŠNÂˆ[“X\œ]
‘‘ÓQTĞWÑP•QÈ‹œŞ\ÛY[HŠNÂˆ[“X\œ]
•WÑP•QÈ‹œŞ\ÛY[HŠNÂˆB‚ˆİ™\œšYQ[•˜\œÊ[“X\
NÂ‚ˆ›Üˆ
X\‘[Oİš[™Ëİš[™Ïˆ[ˆˆ[“X\™[TÙ]

JHÂˆÙÙÙ\‹˜\[™ÓÙÊYYİ\İÛH[ˆˆ
È[‹™Ù]Ù^J
H
ÈHˆ
È[‹™Ù]˜[YJ
JNÂˆHÂˆÜËœÙ][Š[‹™Ù]Ù^J
K[‹™Ù]˜[YJ
KYJNÂˆXØ]Ú
[Ú[\‘^Ù\[Ûˆ^Ù\[ÛŠ^ÂˆÙË™J’”‘U][È‹^Ù\[Û‹Ôİš[™Ê
JNÂˆBˆBˆB‚ˆX›XÈİ]XÈ›ÚY][˜Ú˜]˜U“Jš[˜[\ÛÛ\]Xİ]š]HXİ]š]Kš[˜[[[YH[[YKš[HØ[YQ\™XİÜKš[˜[\İİš[™Ïˆ•“P\™ÜËš[˜[İš[™È\Ù\\™ÜÔİš[™ÊH›İÜÈ›İØX›HÂ‚ˆËÈ›Ü˜ÙHÒ‘ÓÈ\ÙHHœ™Y]\HXœ˜\H[[™Y›Üˆ][œİXYÙˆ\Ú[™ÈHÛ™BˆËÈ]ÙHÚ\Ú]˜]˜H
Ú[˜ÙH]X^H™HÛ\ˆ[ˆÚ]	ÜÈ™YYY
BˆËÂˆÛÛË™[Q^]

NÂˆB‚ˆÊŠ‚ˆ
ˆ\œÙH[™Ù\\˜]H˜]˜H\™İ[Y[È[ˆH\Ù\ˆœšY[™H˜\Ú[Û‚ˆ
ˆ]İ\ÜÈ][H[™H[™XœÙ[˜ÙHÙˆÜXÙ\È™]ÙY[ˆ\™İ[Y[Âˆ
ˆH[˜İ[Ûˆ[ÛÈİ\ÜÈ]]Ë\™[[İ˜[Ùˆ[\›Ü\ˆ\™İ[Y[Ë[İYÚ]X^HZ\ÜÈÛÛYK‚ˆ
‚ˆ
ˆ\˜[H\™ÜÈH[‹\\œÙY\™İ[Y[\İ‚ˆ
ˆ™]\›ˆ\œÙY\™ÜÈ\È[ˆ\œ˜^S\İˆ
‹ÂˆX›XÈİ]XÈ\œ˜^S\İİš[™Ïˆ\œÙR˜]˜P\™İ[Y[Êİš[™È\™ÜÊ^Âˆ\œ˜^S\İİš[™Ïˆ\œÙY\™İ[Y[ÈH™]È\œ˜^S\İŠ
NÂˆ\™ÜÈH\™ÜËš[J
Kœ™\XÙJˆ‹ˆŠNÂˆËÑ›ÜˆXXÚ™Yš^\ËÙHÙ\\˜]H\™ÜË‚ˆİš[™Ö×HÙ\\˜]ÜœÈH™]Èİš[™Ö×^È‹V‹H‹‹VŠÈ‹‹Vˆ‹‹KH‹‹Q‹‹V‹‹Z˜]˜XYÙ[ˆ‹‹]™\˜›ÜÙHŸNÂˆ›ÜŠİš[™È™Yš^ˆÙ\\˜]ÜœÊ^ÂˆÚ[H
YJ^Âˆ[İ\H\™ÜËš[™^ÙŠ™Yš^
NÂˆYŠİ\OHLJHœ™XZÎÂˆËÑÙ]H[™ÙˆHİ\œ™[\™İ[Y[HÚXÚÚ[™ÈH™X\™\İÙ\\˜]Ü‚ˆ[[™HLNÂˆ›ÜŠİš[™ÈÙ\\˜]ÜˆÙ\\˜]ÜœÊ^Âˆ[[\[™H\™ÜËš[™^ÙŠÙ\\˜]Ü‹İ\
È™Yš^›[™İ

JNÂˆYŠ[\[™OHLJHÛÛ[YNÂˆYŠ[™OHLJ^Âˆ[™H[\[™ÂˆÛÛ[YNÂˆBˆ[™HX]›Z[Š[™[\[™
NÂˆBˆËÑ˜[˜XÚÂˆYŠ[™OHLJH[™H\™ÜË›[™İ

NÂ‚ˆËÑ^˜Xİ]ˆİš[™È\œÙYİX”İš[™ÈH\™ÜËœİXœİš[™Êİ\[™
NÂˆ\™ÜÈH\™ÜËœ™\XÙJ\œÙYİX”İš[™ËˆŠNÂ‚ˆËĞÚXÚÈYˆÛÈ\™ÜÈ\™[‰İ[™YÙÙ]\ˆHZ\İZÙBˆYŠ\œÙYİX”İš[™Ëš[™^ÙŠ	ÏIÊHOH\œÙYİX”İš[™Ë›\İ[™^ÙŠ	ÏIÊJHÂˆ[\œ˜^TÚ^™HH\œÙY\™İ[Y[ËœÚ^™J
NÂˆYŠ\œ˜^TÚ^™Hˆ
^Âˆİš[™È\İİš[™ÈH\œÙY\™İ[Y[Ë™Ù]
\œ˜^TÚ^™HHJNÂˆËÈÛÚÚ[™È›Üˆ\İ[[Y[ÂˆYŠ\İİš[™Ë˜Ú\]
\İİš[™Ë›[™İ

HHJHOH	Ë	Èˆ\œÙYİX”İš[™Ë˜ÛÛZ[œÊ‹ŠJ^Âˆ\œÙY\™İ[Y[ËœÙ]
\œ˜^TÚ^™HHK\İİš[™È
È\œÙYİX”İš[™ÊNÂˆÛÛ[YNÂˆBˆBˆ\œÙY\™İ[Y[Ë˜Y
\œÙYİX”İš[™ÊNÂˆBˆ[ÙHÙËÊ’UHT‘ÔÈT”ÑTˆ‹”™[[İ™Y[\›Ü\ˆ\™İ[Y[Îˆˆ
È\œÙYİX”İš[™ÊNÂˆBˆBˆ™]\›ˆ\œÙY\™İ[Y[ÎÂˆB‚ˆÊŠ‚ˆ
ˆÜ[ˆH™[™\ˆXœ˜\H[ˆXØÛÜ™[˜ÙHÈHÙ][™ÜË‚ˆ
ˆ]Ú[˜[˜XÚÈYˆ]˜Z[ÈÈØYHXœ˜\K‚ˆ
ˆ™]\›ˆH˜[YHÙˆHØYYXœ˜\Bˆ
‹ÂˆX›XÈİ]XÈİš[™ÈØYÜ˜\XÜÓXœ˜\Jİš[™È™[™\™\Š^Âˆİš[™È™[™\“Xœ˜\NÂˆ›ÛÛX[ˆ\ÙQÛ\ÎÂˆ›ÛÛX[ˆ\\ÜÓ˜[Y\ÜXÙHH˜[ÙNÂˆ›ÛÛX[ˆ™[ØYšÈHYNÂˆ[Û\Õ™\œÚ[ÛÂˆİÚ]Ú
™[™\™\Š^ÂˆØ\ÙH›ZÙš]™H‚ˆËÈX\È[˜[ZXÈ[šÚ[™ÈÜ˜\\ˆÛÛZ[š[™ÈR’U‘H˜XÚÈH[™[YÜ˜]YÓTËÕ[Ø[ˆİ™\œšY\Âˆ™[™\“Xœ˜\HH›X›ËœÛÈÂˆ\ÙQÛ\ÈHYNÂˆÛ\Õ™\œÚ[ÛˆHÎÂˆHÂˆŞ\İ[K›ØYXœ˜\J›ZÙš]™WÙÛİÜ˜\\ˆŠNÂˆŞ\İ[K›ØYXœ˜\J›ZÙš]™Wİ[Ø[—ÛY\ØHŠNÂˆHØ]Ú
[œØ]\ÙšYY[šÑ\œ›ÜˆJHÂˆÙËÊ’”‘U][È‹“R’U‘HÜXÚYšXÈ˜]]™HÜ˜\\ˆ^Y\œÈÛZ]YÜˆ™KZ[œİ[Y[œÚYHŞ\İ[H]ˆŠNÂˆBˆœ™XZÎÂˆØ\ÙH™™X\—Ù[™Ú[™H‚ˆ›ÛÛX[ˆ[Ø[“ÚÈH˜[ÙNÂˆİš[™ÈšÕ™\ˆH››Û™HÂˆHÂˆ™[ØY[Ø[Š
NÂˆ[Ø[“ÚÈHYNÂˆšÕ™\ˆHŒKŒÈÂˆHØ]Ú
›İØX›H
HÂˆ[Ø[“ÚÈH˜[ÙNÂˆšÕ™\ˆH››Û™HÂˆB‚ˆYˆ
[Ø[“ÚÊHÂˆÙÙÙ\‹˜\[™ÓÙÊ–Ñ™X\”™[™\—H›Ø™Nˆ[Ø[Hˆ
ÈšÕ™\ˆ
ÈˆOˆ’S’ÈŠNÂˆÙÙÙ\‹˜\[™ÓÙÊ–Ñ™X\”™[™\—H˜XÚÙ[™V’S’È
[Ø[ˆKŒÊHŠNÂˆ™[™\“Xœ˜\HH›X‘QÓÛY\ØKœÛÈÂˆ\ÙQÛ\ÈH˜[ÙNÂˆ\\ÜÓ˜[Y\ÜXÙHHYNÂˆÛ\Õ™\œÚ[ÛˆHÎÂˆH[ÙHÂˆÙÙÙ\‹˜\[™ÓÙÊ–Ñ™X\”™[™\—H›Ø™Nˆ[Ø[Hˆ
ÈšÕ™\ˆ
ÈˆOˆÓTÈŠNÂˆÙÙÙ\‹˜\[™ÓÙÊ–Ñ™X\”™[™\—H˜XÚÙ[™QÓTÈÛÜ™OQ“ÑÓÑÓTÊÙİX\™ÈŠNÂˆ™[™\“Xœ˜\HH›X‘Ó™X\‹œÛÈÂˆ\ÙQÛ\ÈHYNÂˆÛ\Õ™\œÚ[ÛˆHÎÂˆBˆœ™XZÎÂˆØ\ÙH™œ™YY™[›×ÚÙÜÛ‚ˆ™[ØYšÈH˜[ÙNÂˆØ\ÙH[Ø[—Şš[šÈ‚ˆ™[™\“Xœ˜\HH›X‘QÓÛY\ØKœÛÈÂˆ\ÙQÛ\ÈH˜[ÙNÂˆ\\ÜÓ˜[Y\ÜXÙHHYNÈËÈY\ØH\È[šÙYÈH[˜ÚÙˆXœ˜\šY\È›İ]˜Z[X›H[ˆHÚ˜]™^XÈ˜[Y\ÜXÙBˆÛ\Õ™\œÚ[ÛˆHÎÂˆYŠ™[ØYšÊH™[ØY[Ø[Š
NÈËÈš[šÈ™\]Z\™\È[Ø[ˆXœ˜\HÈ™H™[ØYYˆœ™XZÎÂˆØ\ÙH›Ü[™Û\Ì×ÛÈˆ‚ˆ™[™\“Xœ˜\HH›X›ËœÛÈÂˆ\ÙQÛ\ÈHYNÂˆÛ\Õ™\œÚ[ÛˆHÎÂˆœ™XZÎÂˆØ\ÙH›Ü[™Û\Ì×ÛYÙ\È‚ˆ™[™\“Xœ˜\HHÛÛË“SĞ’SQÓT×ÑTˆ
È‹ÛX›[Øš[YÛY\ËœÛÈÂˆ\ÙQÛ\ÈHYNÂˆÛ\Õ™\œÚ[ÛˆHÎÂˆœ™XZÎÂˆØ\ÙH›Ü[™Û\Ì×ÛYÙÛ‚ˆ™[™\“Xœ˜\HHÛÛË“SĞ’SQÓÑTˆ
È‹ÛX“[Øš[QÓœÛÈÂˆ\ÙQÛ\ÈHYNÂˆÛ\Õ™\œÚ[ÛˆHÎÂˆœ™XZÎÂˆØ\ÙH›Ü[™Û\Ì×Û™ÙÛ\È‚ˆ™[™\“Xœ˜\HHÛÛË“‘×ÑÓT×ÑTˆ
È‹ÛX›™×ÙÛ\ËœÛÈÂˆ\ÙQÛ\ÈHYNÂˆÛ\Õ™\œÚ[ÛˆHÎÂˆœ™XZÎÂˆØ\ÙH˜İ\İÛWÚ[š™Xİ‚ˆÙÙÙ\‹˜\[™ÓÙÊ–Ñ™X\”™[™\—Hİ\İÛH™[™\ˆ[š™Xİ[Ûˆ[ÙHÙ[XİYŠNÂˆ™[™\“Xœ˜\HH›X‘Ó™X\‹œÛÈÂˆ\ÙQÛ\ÈHYNÂˆÛ\Õ™\œÚ[ÛˆHÎÂˆœ™XZÎÂˆØ\ÙHœ]X\Ø\ˆ‚ˆÙÙÙ\‹˜\[™ÓÙÊ–Ô]X\Ø\—HØY[™È]X\Ø\ˆ™[™\™\‹‹‹ˆŠNÂˆ™[™\“Xœ˜\HH›X™Û\×ÌLMœÛÈÂˆ\ÙQÛ\ÈHYNÂˆÛ\Õ™\œÚ[ÛˆHÎÂˆœ™XZÎÂˆØ\ÙH›Ü[™Û\Ìˆ‚ˆØ\ÙH›Ü[™Û\Ì—ÍH‚ˆØ\ÙH›Ü[™Û\ÌÈ‚ˆY˜][‚ˆ™[™\“Xœ˜\HH›X™Û\×ÌLMœÛÈÂˆ\ÙQÛ\ÈHYNÂˆÛ\Õ™\œÚ[ÛˆH[YÙ\‹œ\œÙR[

İš[™ÊH^˜PÛÜ™K™Ù]˜[YJ^˜PÛÛœİ[Ë“ÔS—ÑÓÕ‘T”ÒSÓŠJNÂˆœ™XZÎÂˆB‚ˆYˆ
XÛÛ™šYİ\™T™[™\œÜXÊ™[™\“Xœ˜\K\\ÜÓ˜[Y\ÜXÙK\ÙQÛ\ËÛ\Õ™\œÚ[ÛŠJHÂˆÙË™J”‘S‘T—ÓP”T–H‹‘˜Z[YÈØY™[™\™\ˆˆ
È™[™\“Xœ˜\H
NÂˆ™]\›ˆ[ÂˆBˆ™]\›ˆ™[™\“Xœ˜\NÂˆB‚ˆX›XÈİ]XÈİš[™È›Ø™QQÓ]›Ü›J
HÂˆHÂˆÜËœÙ][Š‘QÓÔU“Ô“H‹˜[™›ÚY‹YJNÂˆÛ™ÈYÛ\Ü^HHYÛÙ]\Ü^JÊˆQÓÑQUSÑTÔVH
‹ÊNÂˆYˆ
YÛ\Ü^HOH
HÂˆ[×HXZ›ÜˆH™]È[ÌWNÂˆ[×HZ[›ÜˆH™]È[ÌWNÂˆYˆ
YÛ[š]X[^™JYÛ\Ü^KXZ›Ü‹Z[›ÜŠJHÂˆYÛ\›Z[˜]JYÛ\Ü^JNÂˆÙËšJ‘™X\”™[™\ˆ‹‘QÓ›Ø™Nˆ[™›ÚY]›Ü›HÒÈ
QÓˆ
ÈXZ›Ü–ÌH
È‹ˆˆ
ÈZ[›Ü–ÌH
ÈŠHŠNÂˆ™]\›ˆ˜[™›ÚYÂˆBˆBˆHØ]Ú
^Ù\[ÛˆJHÂˆÙËÊ‘™X\”™[™\ˆ‹‘QÓ›Ø™Nˆ[™›ÚY]›Ü›H˜Z[Yˆˆ
ÈK™Ù]Y\ÜØYÙJ
JNÂˆB‚ˆHÂˆÜËœÙ][Š‘QÓÔU“Ô“H‹œİ\™˜XÙ[\ÜÈ‹YJNÂˆÛ™ÈYÛ\Ü^HHYÛÙ]\Ü^J
NÂˆYˆ
YÛ\Ü^HOH
HÂˆ[×HXZ›ÜˆH™]È[ÌWNÂˆ[×HZ[›ÜˆH™]È[ÌWNÂˆYˆ
YÛ[š]X[^™JYÛ\Ü^KXZ›Ü‹Z[›ÜŠJHÂˆYÛ\›Z[˜]JYÛ\Ü^JNÂˆÙËšJ‘™X\”™[™\ˆ‹‘QÓ›Ø™Nˆİ\™˜XÙ[\ÜÈ]›Ü›HÒÈ
QÓˆ
ÈXZ›Ü–ÌH
È‹ˆˆ
ÈZ[›Ü–ÌH
ÈŠHŠNÂˆ™]\›ˆœİ\™˜XÙ[\ÜÈÂˆBˆBˆHØ]Ú
^Ù\[ÛˆJHÂˆÙËÊ‘™X\”™[™\ˆ‹‘QÓ›Ø™Nˆİ\™˜XÙ[\ÜÈ]›Ü›H˜Z[Yˆˆ
ÈK™Ù]Y\ÜØYÙJ
JNÂˆB‚ˆÙË™J‘™X\”™[™\ˆ‹‘QÓ›Ø™NˆS]›Ü›\È˜Z[YQÓ›İ]˜Z[X›HŠNÂˆ™]\›ˆ[ÂˆB‚ˆX›XÈİ]XÈ[Ù]]XİY™\œÚ[ÛŠ
HÂˆ™]\›ˆÓ[™›Õ][Ë™Ù]Û[™›Ê
K™Û\ÓXZ›Ü•™\œÚ[ÛÂˆB‚ˆX›XÈİ]XÈ˜]]™HÛ™ÈYÛÙ]\Ü^JÛ™È\Ü^JNÂˆX›XÈİ]XÈ˜]]™H›ÛÛX[ˆYÛ[š]X[^™JÛ™È\Ü^K[×HXZ›Ü‹[×HZ[›ÜŠNÂˆX›XÈİ]XÈ˜]]™H›ÚYYÛ\›Z[˜]JÛ™È\Ü^JNÂˆX›XÈİ]XÈ˜]]™H[Ú\Šİš[™È]
NÂ‚ˆX›XÈİ]XÈ˜]]™H›ÚYÙ]Xœ˜\T]
İš[™ÈXœ˜\T]
NÂˆX›XÈİ]XÈ˜]]™H›ÛÛX[ˆÛÛ™šYİ\™T™[™\œÜXÊİš[™ÈYÛ]›ÛÛX[ˆ\ÙSØY\\\ÜË›ÛÛX[ˆ\ÙQÛ\Ë[Û\Õ™\œÚ[ÛŠNÂˆX›XÈİ]XÈ˜]]™H›ÚY™[ØY[Ø[Š
NÂˆX›XÈİ]XÈ˜]]™H›ÚYÙ]\ÙU\›š\
›ÛÛX[ˆ[˜X›JNÂ‚ˆËÈ™X\ˆÚY\ˆ[™Ú[™H“’HœšYÙHXÛ\˜][ÛœÂˆX›XÈİ]XÈ˜]]™H›ÚY[š]™X\”ÚY\‘[™Ú[™Jİš[™ÈØXÚT][™\œÚ[ÛŠNÂˆX›XÈİ]XÈ˜]]™H›ÚY\İ›ŞQ™X\”ÚY\‘[™Ú[™J
NÂˆX›XÈİ]XÈ˜]]™Hİš[™ÈÙ]ÚY\ØXÚT]

NÂˆX›XÈİ]XÈ˜]]™H›ÚYÛX\”ÚY\ØXÚJ
NÂˆX›XÈİ]XÈ˜]]™H[Ù]˜[œÛ]YÚY\Ûİ[

NÂ‚ˆËÜX›XÈİ]XÈ˜]]™H›ÚY[š]X[^™RÛÚÜÊ
NÂˆËÈØZ[ˆUÕØÜ™Y[ˆ^[ÈÈ™[™\ˆÛˆ[™›ÚYİ\™˜XÙUšY]ÂˆX›XÈİ]XÈ˜]]™H›ÛÛX[ˆ™[™\UÕØÜ™Y[‘œ˜[YJ]PY™™\ˆ[\Y™™\ŠNÂˆİ]XÈÂˆŞ\İ[K›ØYXœ˜\JœÚ˜]™^XÈŠNÂˆŞ\İ[K›ØYXœ˜\JœÚ˜]™^X×Ø]İŠNÂˆBŸB