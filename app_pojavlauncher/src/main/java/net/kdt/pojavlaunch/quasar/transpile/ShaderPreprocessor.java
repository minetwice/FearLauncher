package net.kdt.pojavlaunch.quasar.transpile;

import android.util.Log;

import net.kdt.pojavlaunch.quasar.capability.CapabilityTable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ShaderPreprocessor adapts desktop GLSL (Iris/OptiFine shaderpacks) for Android
 * GLES drivers, especially Mali which lacks several desktop extensions.
 *
 * Primary fix for Complementary / Solas style packs on Mali:
 *   Extension 'GL_NV_shader_noperspective_interpolation' not supported
 *   Keyword 'noperspective' is reserved
 *
 * Run this BEFORE GlslangCompiler so the source is already GLES-safe.
 */
public class ShaderPreprocessor {
    private static final String TAG = "Quasar-ShaderPreproc";

    // #extension GL_NV_shader_noperspective_interpolation : enable/require/warn/disable
    private static final Pattern EXT_NOPERSPECTIVE = Pattern.compile(
            "(?m)^\\s*#\\s*extension\\s+GL_NV_shader_noperspective_interpolation\\s*:\\s*\\w+\\s*;?\\s*$");

    // Desktop-only / Mali-hostile extensions — strip so missing support never hard-fails the pack
    private static final Pattern[] DESKTOP_ONLY_EXTENSIONS = new Pattern[]{
            Pattern.compile("(?m)^\\s*#\\s*extension\\s+GL_ARB_shader_texture_lod\\s*:\\s*\\w+\\s*;?\\s*$"),
            Pattern.compile("(?m)^\\s*#\\s*extension\\s+GL_EXT_shader_texture_lod\\s*:\\s*\\w+\\s*;?\\s*$"),
            Pattern.compile("(?m)^\\s*#\\s*extension\\s+GL_ARB_shader_storage_buffer_object\\s*:\\s*\\w+\\s*;?\\s*$"),
            Pattern.compile("(?m)^\\s*#\\s*extension\\s+GL_ARB_shader_image_load_store\\s*:\\s*\\w+\\s*;?\\s*$"),
            Pattern.compile("(?m)^\\s*#\\s*extension\\s+GL_ARB_compute_shader\\s*:\\s*\\w+\\s*;?\\s*$"),
            Pattern.compile("(?m)^\\s*#\\s*extension\\s+GL_ARB_geometry_shader4\\s*:\\s*\\w+\\s*;?\\s*$"),
            Pattern.compile("(?m)^\\s*#\\s*extension\\s+GL_EXT_geometry_shader4\\s*:\\s*\\w+\\s*;?\\s*$"),
            Pattern.compile("(?m)^\\s*#\\s*extension\\s+GL_ARB_tessellation_shader\\s*:\\s*\\w+\\s*;?\\s*$"),
            Pattern.compile("(?m)^\\s*#\\s*extension\\s+GL_NV_gpu_shader5\\s*:\\s*\\w+\\s*;?\\s*$"),
            Pattern.compile("(?m)^\\s*#\\s*extension\\s+GL_ARB_gpu_shader5\\s*:\\s*\\w+\\s*;?\\s*$"),
            Pattern.compile("(?m)^\\s*#\\s*extension\\s+GL_ARB_shader_bit_encoding\\s*:\\s*\\w+\\s*;?\\s*$"),
            Pattern.compile("(?m)^\\s*#\\s*extension\\s+GL_ARB_shader_subroutine\\s*:\\s*\\w+\\s*;?\\s*$"),
            Pattern.compile("(?m)^\\s*#\\s*extension\\s+GL_ARB_shader_atomic_counters\\s*:\\s*\\w+\\s*;?\\s*$"),
            Pattern.compile("(?m)^\\s*#\\s*extension\\s+GL_ARB_shader_draw_parameters\\s*:\\s*\\w+\\s*;?\\s*$"),
            Pattern.compile("(?m)^\\s*#\\s*extension\\s+GL_ARB_gpu_shader_fp64\\s*:\\s*\\w+\\s*;?\\s*$"),
            Pattern.compile("(?m)^\\s*#\\s*extension\\s+GL_ARB_gpu_shader_int64\\s*:\\s*\\w+\\s*;?\\s*$"),
            Pattern.compile("(?m)^\\s*#\\s*extension\\s+GL_EXT_shader_image_load_store\\s*:\\s*\\w+\\s*;?\\s*$"),
    };

    // noperspective as a standalone qualifier (not part of a larger identifier)
    private static final Pattern KEYWORD_NOPERSPECTIVE = Pattern.compile(
            "\\bnoperspective\\b");

    // Desktop #version NNN [core|compatibility] -> keep number, strip profile; GLES handled later by SPIRV-Cross
    private static final Pattern VERSION_DESKTOP = Pattern.compile(
            "(?m)^\\s*#\\s*version\\s+(\\d+)\\s+(core|compatibility)?\\s*$");

    /**
     * Preprocess shader source for the given device capability profile.
     *
     * @param source         Original desktop GLSL
     * @param capabilities   Device capability table (may be null -> conservative Mali-safe path)
     * @param shaderName     For logging
     * @return Safe source ready for glslang / SPIRV-Cross
     */
    public static String preprocess(String source, CapabilityTable capabilities, String shaderName) {
        if (source == null || source.isEmpty()) {
            return source;
        }

        boolean isMali = capabilities != null
                && "mali".equalsIgnoreCase(capabilities.getGpuVendor());
        boolean forceGlesSafe = capabilities == null
                || isMali
                || !capabilities.hasVulkan();

        String out = source;
        int changes = 0;

        // 1) Always strip NV noperspective extension on mobile/GLES path (Mali + many Adreno GLES paths)
        if (forceGlesSafe || isMali) {
            Matcher m = EXT_NOPERSPECTIVE.matcher(out);
            if (m.find()) {
                out = m.replaceAll("// Quasar: stripped unsupported GL_NV_shader_noperspective_interpolation\n");
                changes++;
            }

            // 2) Replace noperspective qualifier -> default smooth interpolation
            Matcher km = KEYWORD_NOPERSPECTIVE.matcher(out);
            if (km.find()) {
                out = km.replaceAll("/*noperspective*/");
                changes++;
            }
        }

        // 3) Strip other desktop-only extensions when we know we are on a limited device
        if (forceGlesSafe) {
            for (Pattern p : DESKTOP_ONLY_EXTENSIONS) {
                Matcher m = p.matcher(out);
                if (m.find()) {
                    out = m.replaceAll("// Quasar: stripped desktop-only extension\n");
                    changes++;
                }
            }
        }

        // 4) Soften desktop version line (SPIRV-Cross / glslang still accept the number)
        Matcher vm = VERSION_DESKTOP.matcher(out);
        if (vm.find()) {
            String ver = vm.group(1);
            out = vm.replaceAll("#version " + ver);
            changes++;
        }

        if (changes > 0) {
            Log.i(TAG, "Preprocessed " + (shaderName != null ? shaderName : "shader")
                    + " (" + changes + " adjustments, mali=" + isMali + ")");
        }

        return out;
    }

    /**
     * Convenience overload when capability table is not yet available.
     * Applies the full GLES/Mali-safe set of patches.
     */
    public static String preprocessForMobile(String source, String shaderName) {
        return preprocess(source, null, shaderName);
    }
}
