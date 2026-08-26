package net.kdt.pojavlaunch.quasar.shield;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * ExtensionVault — CPU-side "extension shield" database.
 *
 * Tracks which desktop GLSL extensions are hostile on Mali/Adreno GLES,
 * which can be safely stripped, and which can be joined/substituted with
 * mobile-safe equivalents so packs keep compiling at high speed.
 */
public final class ExtensionVault {

    /** Strip entirely — presence of #extension line causes hard-fail on Mali. */
    public static final Set<String> STRIP_ALWAYS;

    /** Prefer strip on GLES path even if driver claims partial support. */
    public static final Set<String> STRIP_ON_GLES;

    /** Map desktop extension → mobile-safe substitute extension name (join). */
    public static final Map<String, String> JOIN_MAP;

    /** Keywords that must be rewritten (not just extension lines). */
    public static final Map<String, String> KEYWORD_REWRITE;

    static {
        Set<String> strip = new HashSet<>();
        strip.add("GL_NV_shader_noperspective_interpolation");
        strip.add("GL_EXT_shader_non_constant_global_initializers");
        strip.add("GL_ARB_shader_texture_lod");
        strip.add("GL_EXT_shader_texture_lod");
        strip.add("GL_ARB_shader_storage_buffer_object");
        strip.add("GL_ARB_shader_image_load_store");
        strip.add("GL_EXT_shader_image_load_store");
        strip.add("GL_ARB_compute_shader");
        strip.add("GL_ARB_shader_atomic_counters");
        strip.add("GL_ARB_shader_draw_parameters");
        strip.add("GL_ARB_geometry_shader4");
        strip.add("GL_EXT_geometry_shader4");
        strip.add("GL_ARB_tessellation_shader");
        strip.add("GL_NV_gpu_shader5");
        strip.add("GL_ARB_gpu_shader5");
        strip.add("GL_ARB_shader_bit_encoding");
        strip.add("GL_ARB_shader_subroutine");
        strip.add("GL_ARB_gpu_shader_fp64");
        strip.add("GL_ARB_gpu_shader_int64");
        strip.add("GL_AMD_gpu_shader_half_float");
        strip.add("GL_EXT_shader_explicit_arithmetic_types");
        strip.add("GL_ARB_shading_language_packing");
        strip.add("GL_ARB_shader_ballot");
        strip.add("GL_ARB_shader_group_vote");
        strip.add("GL_KHR_shader_subgroup");
        strip.add("GL_ARB_shader_viewport_layer_array");
        strip.add("GL_ARB_fragment_layer_viewport");
        strip.add("GL_NV_viewport_array2");
        strip.add("GL_ARB_cull_distance");
        strip.add("GL_ARB_shader_clock");
        strip.add("GL_ARB_sparse_texture");
        strip.add("GL_ARB_sparse_texture2");
        STRIP_ALWAYS = Collections.unmodifiableSet(strip);

        Set<String> gles = new HashSet<>(strip);
        gles.add("GL_ARB_enhanced_layouts");
        gles.add("GL_ARB_explicit_attrib_location");
        gles.add("GL_ARB_separate_shader_objects");
        STRIP_ON_GLES = Collections.unmodifiableSet(gles);

        Map<String, String> join = new HashMap<>();
        join.put("GL_ARB_shader_texture_lod", "GL_EXT_shader_texture_lod");
        join.put("GL_ARB_gpu_shader5", "GL_EXT_gpu_shader5");
        join.put("GL_ARB_shader_image_load_store", "GL_OES_shader_image_atomic");
        join.put("GL_ARB_geometry_shader4", "GL_EXT_geometry_shader");
        join.put("GL_ARB_tessellation_shader", "GL_OES_tessellation_shader");
        join.put("GL_ARB_compute_shader", "GL_OES_texture_storage_multisample_2d_array");
        JOIN_MAP = Collections.unmodifiableMap(join);

        Map<String, String> kw = new HashMap<>();
        kw.put("noperspective", "/*noperspective*/");
        kw.put("invariant", "invariant");
        KEYWORD_REWRITE = Collections.unmodifiableMap(kw);
    }

    private ExtensionVault() {}

    public static boolean shouldStrip(String extensionName, boolean glesPath) {
        if (extensionName == null) return false;
        String e = extensionName.trim();
        if (STRIP_ALWAYS.contains(e)) return true;
        return glesPath && STRIP_ON_GLES.contains(e);
    }

    public static String joinOrNull(String extensionName) {
        if (extensionName == null) return null;
        return JOIN_MAP.get(extensionName.trim());
    }

    public static boolean isHostileKeyword(String word) {
        return word != null && KEYWORD_REWRITE.containsKey(word.toLowerCase(Locale.ROOT));
    }
}
