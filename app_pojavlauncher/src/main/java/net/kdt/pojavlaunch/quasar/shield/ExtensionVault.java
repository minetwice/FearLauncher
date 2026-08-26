package net.kdt.pojavlaunch.quasar.shield;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** ExtensionVault — CPU-side extension shield, synced with OpenGLFeatureCatalog. */
public final class ExtensionVault {

    public static final Set<String> STRIP_ALWAYS;
    public static final Set<String> STRIP_ON_GLES;
    public static final Map<String, String> JOIN_MAP;
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
        strip.add("GL_ARB_pipeline_statistics_query");
        strip.add("GL_ARB_gl_spirv");
        strip.add("GL_ARB_sample_shading");
        strip.add("GL_ARB_texture_cube_map_array");
        strip.add("GL_ARB_texture_buffer_object");
        strip.add("GL_ARB_texture_view");
        strip.add("GL_ARB_buffer_storage");
        strip.add("GL_ARB_base_instance");
        strip.add("GL_ARB_multi_draw_indirect");
        strip.add("GL_ARB_draw_buffers_blend");
        strip.add("GL_ARB_framebuffer_sRGB");
        strip.add("GL_ARB_timer_query");
        strip.add("GL_ARB_clip_control");
        strip.add("GL_ARB_conservative_depth");
        strip.add("GL_ARB_direct_state_access");
        strip.add("GL_ARB_enhanced_layouts");
        STRIP_ALWAYS = Collections.unmodifiableSet(strip);

        Set<String> gles = new HashSet<>(strip);
        gles.add("GL_ARB_explicit_attrib_location");
        gles.add("GL_ARB_separate_shader_objects");
        STRIP_ON_GLES = Collections.unmodifiableSet(gles);

        Map<String, String> join = new HashMap<>();
        join.put("GL_ARB_shader_texture_lod", "GL_EXT_shader_texture_lod");
        join.put("GL_ARB_gpu_shader5", "GL_EXT_gpu_shader5");
        join.put("GL_ARB_shader_image_load_store", "GL_OES_shader_image_atomic");
        join.put("GL_ARB_geometry_shader4", "GL_EXT_geometry_shader");
        join.put("GL_ARB_tessellation_shader", "GL_OES_tessellation_shader");
        join.put("GL_ARB_texture_cube_map_array", "GL_EXT_texture_cube_map_array");
        join.put("GL_ARB_texture_buffer_object", "GL_EXT_texture_buffer");
        join.put("GL_ARB_texture_view", "GL_OES_texture_view");
        join.put("GL_ARB_buffer_storage", "GL_EXT_buffer_storage");
        join.put("GL_ARB_base_instance", "GL_EXT_base_instance");
        join.put("GL_ARB_multi_draw_indirect", "GL_EXT_multi_draw_indirect");
        join.put("GL_ARB_draw_buffers_blend", "GL_EXT_draw_buffers_indexed");
        join.put("GL_ARB_framebuffer_sRGB", "GL_EXT_sRGB_write_control");
        join.put("GL_ARB_timer_query", "GL_EXT_disjoint_timer_query");
        join.put("GL_ARB_sample_shading", "GL_OES_sample_shading");
        join.put("GL_ARB_clip_control", "GL_EXT_clip_control");
        join.put("GL_ARB_cull_distance", "GL_EXT_clip_cull_distance");
        join.put("GL_ARB_conservative_depth", "GL_EXT_conservative_depth");
        join.put("GL_ARB_shader_group_vote", "GL_EXT_shader_group_vote");
        join.put("GL_ARB_shader_ballot", "GL_EXT_shader_ballot");
        join.put("GL_ARB_shader_viewport_layer_array", "GL_OES_viewport_array");
        JOIN_MAP = Collections.unmodifiableMap(join);

        Map<String, String> kw = new HashMap<>();
        kw.put("noperspective", "/*noperspective*/");
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
