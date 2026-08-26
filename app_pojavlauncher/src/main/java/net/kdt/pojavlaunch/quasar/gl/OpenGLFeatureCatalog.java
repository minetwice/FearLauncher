package net.kdt.pojavlaunch.quasar.gl;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Catalog of desktop OpenGL 3.3 → 4.6 / ARB features and Android / GLES equivalents.
 * Quasar: strip / join / emulate / passthrough.
 */
public final class OpenGLFeatureCatalog {

    public enum Support {
        NATIVE_ES30, NATIVE_ES31, NATIVE_ES32, EMULATE, STRIP, JOIN
    }

    public static final class Entry {
        public final String desktopName;
        public final String desktopExt;
        public final String mobileExt;
        public final Support support;
        public final int minEsVersion;
        public final String note;

        Entry(String desktopName, String desktopExt, String mobileExt,
              Support support, int minEsVersion, String note) {
            this.desktopName = desktopName;
            this.desktopExt = desktopExt;
            this.mobileExt = mobileExt;
            this.support = support;
            this.minEsVersion = minEsVersion;
            this.note = note;
        }
    }

    private static final Map<String, Entry> CATALOG;

    static {
        Map<String, Entry> m = new LinkedHashMap<>();
        put(m, "geometry_shader", "GL_ARB_geometry_shader4", "GL_EXT_geometry_shader", Support.JOIN, 32, "ES 3.2 or EXT_geometry_shader");
        put(m, "tessellation_shader", "GL_ARB_tessellation_shader", "GL_OES_tessellation_shader", Support.JOIN, 32, "ES 3.2 or OES");
        put(m, "compute_shader", "GL_ARB_compute_shader", null, Support.NATIVE_ES31, 31, "Core ES 3.1");
        put(m, "shader_subroutine", "GL_ARB_shader_subroutine", null, Support.STRIP, 0, "No ES equivalent");
        put(m, "gpu_shader5", "GL_ARB_gpu_shader5", "GL_EXT_gpu_shader5", Support.JOIN, 32, "EXT_gpu_shader5");
        put(m, "gpu_shader_fp64", "GL_ARB_gpu_shader_fp64", null, Support.STRIP, 0, "No double on ES");
        put(m, "gpu_shader_int64", "GL_ARB_gpu_shader_int64", "GL_EXT_shader_explicit_arithmetic_types_int64", Support.JOIN, 0, "Rare");
        put(m, "noperspective", "GL_NV_shader_noperspective_interpolation", null, Support.STRIP, 0, "Mali reserved");
        put(m, "sample_shading", "GL_ARB_sample_shading", "GL_OES_sample_shading", Support.JOIN, 32, "OES");
        put(m, "shader_viewport_layer", "GL_ARB_shader_viewport_layer_array", "GL_OES_viewport_array", Support.JOIN, 0, "Optional");
        put(m, "texture_lod", "GL_ARB_shader_texture_lod", "GL_EXT_shader_texture_lod", Support.JOIN, 30, "textureLod core ES 3.0");
        put(m, "texture_gather", "GL_ARB_texture_gather", null, Support.NATIVE_ES30, 30, "Core");
        put(m, "texture_cube_map_array", "GL_ARB_texture_cube_map_array", "GL_EXT_texture_cube_map_array", Support.JOIN, 32, "EXT");
        put(m, "texture_multisample", "GL_ARB_texture_multisample", null, Support.NATIVE_ES31, 31, "Core ES 3.1");
        put(m, "texture_storage", "GL_ARB_texture_storage", null, Support.NATIVE_ES30, 30, "Core");
        put(m, "texture_view", "GL_ARB_texture_view", "GL_OES_texture_view", Support.JOIN, 0, "OES");
        put(m, "texture_buffer", "GL_ARB_texture_buffer_object", "GL_EXT_texture_buffer", Support.JOIN, 32, "EXT");
        put(m, "seamless_cube_map", "GL_ARB_seamless_cube_map", null, Support.NATIVE_ES30, 30, "Always seamless ES");
        put(m, "anisotropic", "GL_EXT_texture_filter_anisotropic", "GL_EXT_texture_filter_anisotropic", Support.NATIVE_ES30, 30, "Wide");
        put(m, "shader_image_load_store", "GL_ARB_shader_image_load_store", null, Support.NATIVE_ES31, 31, "Core ES 3.1");
        put(m, "shader_storage_buffer", "GL_ARB_shader_storage_buffer_object", null, Support.NATIVE_ES31, 31, "Core ES 3.1");
        put(m, "shader_atomic_counters", "GL_ARB_shader_atomic_counters", null, Support.NATIVE_ES31, 31, "Core ES 3.1");
        put(m, "shader_image_size", "GL_ARB_shader_image_size", null, Support.NATIVE_ES31, 31, "Core");
        put(m, "shader_atomic_counter_ops", "GL_ARB_shader_atomic_counter_ops", null, Support.EMULATE, 31, "Partial");
        put(m, "draw_indirect", "GL_ARB_draw_indirect", null, Support.NATIVE_ES31, 31, "Core");
        put(m, "multi_draw_indirect", "GL_ARB_multi_draw_indirect", "GL_EXT_multi_draw_indirect", Support.JOIN, 31, "EXT");
        put(m, "base_instance", "GL_ARB_base_instance", "GL_EXT_base_instance", Support.JOIN, 0, "Optional");
        put(m, "transform_feedback", "GL_ARB_transform_feedback2", null, Support.NATIVE_ES30, 30, "Core");
        put(m, "transform_feedback3", "GL_ARB_transform_feedback3", null, Support.NATIVE_ES30, 30, "Partial");
        put(m, "instanced_arrays", "GL_ARB_instanced_arrays", null, Support.NATIVE_ES30, 30, "Core");
        put(m, "uniform_buffer_object", "GL_ARB_uniform_buffer_object", null, Support.NATIVE_ES30, 30, "Core");
        put(m, "map_buffer_range", "GL_ARB_map_buffer_range", null, Support.NATIVE_ES30, 30, "Core");
        put(m, "buffer_storage", "GL_ARB_buffer_storage", "GL_EXT_buffer_storage", Support.JOIN, 0, "EXT");
        put(m, "direct_state_access", "GL_ARB_direct_state_access", null, Support.EMULATE, 0, "bind then op");
        put(m, "vertex_attrib_binding", "GL_ARB_vertex_attrib_binding", null, Support.NATIVE_ES31, 31, "Core");
        put(m, "separate_shader_objects", "GL_ARB_separate_shader_objects", null, Support.NATIVE_ES30, 30, "Pipelines");
        put(m, "framebuffer_object", "GL_ARB_framebuffer_object", null, Support.NATIVE_ES30, 30, "Core");
        put(m, "draw_buffers", "GL_ARB_draw_buffers", null, Support.NATIVE_ES30, 30, "MRT core");
        put(m, "draw_buffers_blend", "GL_ARB_draw_buffers_blend", "GL_EXT_draw_buffers_indexed", Support.JOIN, 32, "EXT");
        put(m, "framebuffer_srgb", "GL_ARB_framebuffer_sRGB", "GL_EXT_sRGB_write_control", Support.JOIN, 0, "Optional");
        put(m, "invalidate_subdata", "GL_ARB_invalidate_subdata", null, Support.NATIVE_ES30, 30, "Core");
        put(m, "sync", "GL_ARB_sync", null, Support.NATIVE_ES30, 30, "Core");
        put(m, "occlusion_query", "GL_ARB_occlusion_query2", "GL_EXT_occlusion_query_boolean", Support.JOIN, 30, "Boolean");
        put(m, "timer_query", "GL_ARB_timer_query", "GL_EXT_disjoint_timer_query", Support.JOIN, 0, "EXT");
        put(m, "pipeline_statistics", "GL_ARB_pipeline_statistics_query", null, Support.STRIP, 0, "Desktop only");
        put(m, "debug_output", "GL_KHR_debug", "GL_KHR_debug", Support.NATIVE_ES30, 30, "KHR");
        put(m, "robustness", "GL_KHR_robustness", "GL_EXT_robustness", Support.JOIN, 0, "Optional");
        put(m, "shader_group_vote", "GL_ARB_shader_group_vote", "GL_EXT_shader_group_vote", Support.JOIN, 0, "Rare");
        put(m, "shader_ballot", "GL_ARB_shader_ballot", "GL_EXT_shader_ballot", Support.JOIN, 0, "Rare");
        put(m, "shader_subgroup", "GL_KHR_shader_subgroup", "GL_KHR_shader_subgroup", Support.JOIN, 0, "Limited GLES");
        put(m, "cull_distance", "GL_ARB_cull_distance", "GL_EXT_clip_cull_distance", Support.JOIN, 0, "Optional");
        put(m, "clip_control", "GL_ARB_clip_control", "GL_EXT_clip_control", Support.JOIN, 0, "Optional");
        put(m, "spirv_shader", "GL_ARB_gl_spirv", null, Support.EMULATE, 0, "Offline SPIR-V");
        put(m, "shader_bit_encoding", "GL_ARB_shader_bit_encoding", null, Support.NATIVE_ES30, 30, "Core");
        put(m, "shading_language_packing", "GL_ARB_shading_language_packing", null, Support.NATIVE_ES30, 30, "Core");
        put(m, "explicit_attrib_location", "GL_ARB_explicit_attrib_location", null, Support.NATIVE_ES30, 30, "layout(location)");
        put(m, "conservative_depth", "GL_ARB_conservative_depth", "GL_EXT_conservative_depth", Support.JOIN, 0, "Optional");
        CATALOG = Collections.unmodifiableMap(m);
    }

    private static void put(Map<String, Entry> m, String name, String desk, String mob,
                            Support s, int minEs, String note) {
        m.put(name, new Entry(name, desk, mob, s, minEs, note));
    }

    private OpenGLFeatureCatalog() {}

    public static Map<String, Entry> all() { return CATALOG; }
    public static Entry get(String featureName) { return CATALOG.get(featureName); }
    public static int size() { return CATALOG.size(); }
}
