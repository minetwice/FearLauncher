package net.kdt.pojavlaunch.quasar;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MaliPolyfillInjector {
    private static final Map<String, String> EXTENSION_REPLACEMENTS = new HashMap<>();
    private static final Map<String, String> FUNCTION_POLYFILLS = new HashMap<>();
    
    static {
        EXTENSION_REPLACEMENTS.put("GL_ARB_shader_texture_lod", "GL_EXT_shader_texture_lod");
        EXTENSION_REPLACEMENTS.put("GL_NV_shader_noperspective_interpolation", "");
        EXTENSION_REPLACEMENTS.put("GL_EXT_geometry_shader4", "");
        EXTENSION_REPLACEMENTS.put("GL_ARB_compute_shader", "");
        
        FUNCTION_POLYFILLS.put("EmitVertex", "// EmitVertex emulated");
        FUNCTION_POLYFILLS.put("gl_ClipDistance", "vec4(0.0)");
        FUNCTION_POLYFILLS.put("atomicAdd", "maliAtomicAdd");
        FUNCTION_POLYFILLS.put("shadow2D", "texture");
    }
    
    private final MaliShaderFixes maliFixes;
    
    public MaliPolyfillInjector() {
        this.maliFixes = new MaliShaderFixes();
    }
    
    public String injectPolyfills(String shaderSource, ShaderInfo shaderInfo) {
        if (shaderSource == null || !maliFixes.isMaliGpu()) return shaderSource;
        String processed = shaderSource;
        processed = replaceExtensions(processed);
        processed = replaceFunctions(processed);
        processed = injectPolyfillFunctions(processed);
        return processed;
    }
    
    private String replaceExtensions(String shaderSource) {
        String processed = shaderSource;
        for (Map.Entry<String, String> entry : EXTENSION_REPLACEMENTS.entrySet()) {
            String ext = entry.getKey();
            String replacement = entry.getValue();
            if (!replacement.isEmpty()) {
                processed = processed.replace("#extension " + ext + " : enable", "#extension " + replacement + " : enable");
            } else {
                processed = processed.replace("#extension " + ext + " : enable
", "");
            }
        }
        return processed;
    }
    
    private String replaceFunctions(String shaderSource) {
        String processed = shaderSource;
        for (Map.Entry<String, String> entry : FUNCTION_POLYFILLS.entrySet()) {
            processed = processed.replaceAll("\b" + entry.getKey() + "\s*\(", entry.getValue() + "(");
        }
        return processed;
    }
    
    private String injectPolyfillFunctions(String shaderSource) {
        if (shaderSource.contains("maliAtomicAdd")) return shaderSource;
        String polyfills = "
int maliAtomicAdd(inout int mem, int data) { int old = mem; mem = old + data; return old; }
";
        int insertPos = shaderSource.lastIndexOf("
");
        return shaderSource.substring(0, insertPos) + polyfills + shaderSource.substring(insertPos);
    }
    
    public boolean arePolyfillsNeeded() { return maliFixes.isMaliGpu(); }
    public MaliShaderFixes getMaliFixes() { return maliFixes; }
}