package net.kdt.pojavlaunch.quasar;

import java.util.HashMap;
import java.util.Map;

public class ExtensionPolyfillStrategy extends BaseShaderStrategy {
    private static final Map<String, String> EXTENSION_POLYFILLS = new HashMap<>();
    private static final Map<String, String> FUNCTION_POLYFILLS = new HashMap<>();
    
    static {
        EXTENSION_POLYFILLS.put("GL_ARB_shader_texture_lod", "GL_EXT_shader_texture_lod");
        EXTENSION_POLYFILLS.put("GL_NV_shader_noperspective_interpolation", "");
        EXTENSION_POLYFILLS.put("GL_EXT_geometry_shader4", "");
        EXTENSION_POLYFILLS.put("GL_ARB_compute_shader", "");
        
        FUNCTION_POLYFILLS.put("EmitVertex", "// EmitVertex emulated");
        FUNCTION_POLYFILLS.put("EndPrimitive", "// EndPrimitive emulated");
        FUNCTION_POLYFILLS.put("gl_ClipDistance", "vec4(0.0)");
        FUNCTION_POLYFILLS.put("gl_CullDistance", "vec2(0.0)");
        FUNCTION_POLYFILLS.put("atomicAdd", "maliAtomicAdd");
        FUNCTION_POLYFILLS.put("shadow2D", "texture");
        FUNCTION_POLYFILLS.put("gl_FragDepth", "0.0");
    }
    
    public ExtensionPolyfillStrategy() { super("extension_polyfill", "Polyfill missing extensions and functions"); }
    
    @Override
    public boolean canProcess(ShaderInfo shaderInfo) { return isEnabled(); }
    
    @Override
    public int getPriority() { return 70; }
    
    @Override
    protected String doProcess(String shaderSource, ShaderInfo shaderInfo) {
        String processed = shaderSource;
        processed = replaceExtensions(processed);
        processed = replaceFunctions(processed);
        processed = injectPolyfillFunctions(processed);
        return processed;
    }
    
    private String replaceExtensions(String source) {
        for (Map.Entry<String, String> entry : EXTENSION_POLYFILLS.entrySet()) {
            String ext = entry.getKey();
            String replacement = entry.getValue();
            if (!replacement.isEmpty()) {
                source = source.replace("#extension " + ext + " : enable", "#extension " + replacement + " : enable");
            } else {
                source = source.replace("#extension " + ext + " : enable", "");
            }
        }
        return source;
    }
    
    private String replaceFunctions(String source) {
        for (Map.Entry<String, String> entry : FUNCTION_POLYFILLS.entrySet()) {
            source = source.replaceAll("\b" + entry.getKey() + "\s*\(", entry.getValue() + "(");
        }
        return source;
    }
    
    private String injectPolyfillFunctions(String source) {
        if (source.contains("maliAtomicAdd")) return source;
        String polyfills = "
int maliAtomicAdd(inout int mem, int data) { int old = mem; mem = old + data; return old; }
";
        int insertPos = source.lastIndexOf("
");
        return source.substring(0, insertPos) + polyfills + source.substring(insertPos);
    }
}