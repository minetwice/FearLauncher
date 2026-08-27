package net.kdt.pojavlaunch.quasar.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** A–Z catalog of desktop OpenGL → Android GLES translation kinds. */
public final class TranslatorTable {

    public enum Kind { NATIVE_ES, EMULATE, SHADER_REWRITE, STUB }

    public static final class Entry {
        public final String name;
        public final Kind kind;
        public final String note;
        Entry(String name, Kind kind, String note) {
            this.name = name; this.kind = kind; this.note = note;
        }
    }

    private static final Map<String, Entry> MAP;
    static {
        Map<String, Entry> m = new LinkedHashMap<>();
        put(m, "ARB_framebuffer_object", Kind.NATIVE_ES, "FBO core ES");
        put(m, "ARB_draw_buffers", Kind.NATIVE_ES, "MRT ES3");
        put(m, "ARB_instanced_arrays", Kind.NATIVE_ES, "ES3");
        put(m, "buffer_storage", Kind.EMULATE, "FearCore glBufferStorage");
        put(m, "bindless_texture", Kind.EMULATE, "handle table");
        put(m, "compute_shader", Kind.NATIVE_ES, "ES 3.1");
        put(m, "clip_control", Kind.STUB, "optional");
        put(m, "direct_state_access", Kind.EMULATE, "bind-then-op");
        put(m, "draw_indirect", Kind.NATIVE_ES, "ES 3.1");
        put(m, "explicit_attrib_location", Kind.NATIVE_ES, "layout(location)");
        put(m, "early_fragment_tests", Kind.SHADER_REWRITE, "strip if missing");
        put(m, "float_texture_color", Kind.EMULATE, "RGBA16F / RGBA8 fallback");
        put(m, "framebuffer_srgb", Kind.STUB, "EXT if present");
        put(m, "geometry_shader", Kind.STUB, "ES 3.2 rare on Mali");
        put(m, "gpu_shader5", Kind.SHADER_REWRITE, "join EXT or strip");
        put(m, "highp_default", Kind.SHADER_REWRITE, "inject precision highp");
        put(m, "image_load_store", Kind.NATIVE_ES, "ES 3.1");
        put(m, "invalidate_framebuffer", Kind.EMULATE, "FearCore");
        put(m, "KHR_debug", Kind.NATIVE_ES, "optional");
        put(m, "layout_binding", Kind.SHADER_REWRITE, "soften if needed");
        put(m, "multi_draw_indirect", Kind.EMULATE, "loop draw");
        put(m, "multi_draw_arrays", Kind.EMULATE, "FearCore");
        put(m, "noperspective", Kind.SHADER_REWRITE, "→ smooth / comment");
        put(m, "named_buffer", Kind.EMULATE, "DSA emulation");
        put(m, "occlusion_query", Kind.NATIVE_ES, "boolean query");
        put(m, "program_pipeline", Kind.NATIVE_ES, "SSO ES3");
        put(m, "primitive_restart", Kind.NATIVE_ES, "ES3");
        put(m, "robustness", Kind.STUB, "optional");
        put(m, "ssbo", Kind.NATIVE_ES, "ES 3.1");
        put(m, "separate_shader_objects", Kind.NATIVE_ES, "ES3");
        put(m, "sample_shading", Kind.STUB, "OES rare");
        put(m, "texture_lod", Kind.SHADER_REWRITE, "textureLod / strip ext");
        put(m, "texture_storage", Kind.NATIVE_ES, "texStorage ES3");
        put(m, "tessellation", Kind.STUB, "ES 3.2 rare");
        put(m, "timer_query", Kind.STUB, "EXT disjoint");
        put(m, "uniform_buffer_object", Kind.NATIVE_ES, "ES3 UBO");
        put(m, "vertex_attrib_binding", Kind.NATIVE_ES, "ES 3.1");
        put(m, "viewport_array", Kind.STUB, "OES");
        put(m, "workaround_mali_mrt", Kind.EMULATE, "RGBA8 fallback incomplete FBO");
        put(m, "workaround_mali_highp", Kind.SHADER_REWRITE, "force highp");
        MAP = Collections.unmodifiableMap(m);
    }

    private static void put(Map<String, Entry> m, String n, Kind k, String note) {
        m.put(n, new Entry(n, k, note));
    }

    private TranslatorTable() {}
    public static Map<String, Entry> all() { return MAP; }
    public static int size() { return MAP.size(); }
    public static String summary() {
        int n = 0, e = 0, s = 0, st = 0;
        for (Entry x : MAP.values()) {
            switch (x.kind) {
                case NATIVE_ES: n++; break;
                case EMULATE: e++; break;
                case SHADER_REWRITE: s++; break;
                case STUB: st++; break;
            }
        }
        return "TranslatorTable size=" + MAP.size()
                + " native=" + n + " emulate=" + e + " rewrite=" + s + " stub=" + st;
    }
}
