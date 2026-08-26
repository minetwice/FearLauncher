package net.kdt.pojavlaunch.quasar;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class MaliShaderFixes {
    private static final Map<Pattern, String> MALI_FIXES = new HashMap<>();
    static {
        MALI_FIXES.put(Pattern.compile("texture\(([^,]+),\s*([^\)]+)\)"), "textureLod($1, $2, 0.0)");
        MALI_FIXES.put(Pattern.compile("u_ShadowBias\s*=\s*([0-9.]+)"), "u_ShadowBias = $1 * 2.0");
        MALI_FIXES.put(Pattern.compile("\bnoperspective\s+\([^)]+\)"), "smooth");
        MALI_FIXES.put(Pattern.compile("gl_ClipDistance"), "vec4(0.0, 0.0, 0.0, 0.0)");
        MALI_FIXES.put(Pattern.compile("EmitVertex"), "// EmitVertex emulated");
        MALI_FIXES.put(Pattern.compile("gl_WorkGroupID"), "vec3(0, 0, 0)");
        MALI_FIXES.put(Pattern.compile("sampler2DShadow"), "sampler2D");
        MALI_FIXES.put(Pattern.compile("shadow2D"), "texture");
        MALI_FIXES.put(Pattern.compile("gl_FragDepth"), "0.0");
    }
    
    private final ShaderProcessor processor;
    private final GpuCapabilities capabilities;
    
    public MaliShaderFixes() {
        this.processor = ShaderProcessor.getInstance();
        this.capabilities = processor.getCapabilities();
    }
    
    public String applyFixes(String shaderSource) {
        if (shaderSource == null || !isMaliGpu()) return shaderSource;
        String fixed = shaderSource;
        for (Map.Entry<Pattern, String> fix : MALI_FIXES.entrySet()) {
            fixed = fix.getKey().matcher(fixed).replaceAll(fix.getValue());
        }
        fixed = addMaliDefines(fixed);
        fixed = addMaliPolyfills(fixed);
        return fixed;
    }
    
    private String addMaliDefines(String shaderSource) {
        if (shaderSource.contains("#define MALI_GPU")) return shaderSource;
        int versionIndex = shaderSource.indexOf("#version");
        if (versionIndex >= 0) {
            int newlineIndex = shaderSource.indexOf('
', versionIndex);
            if (newlineIndex >= 0) {
                return shaderSource.substring(0, newlineIndex + 1) + "
#define MALI_GPU 1
#define MALI_G615 1
" + shaderSource.substring(newlineIndex + 1);
            }
        }
        return "#version 300 es
#define MALI_GPU 1
#define MALI_G615 1

" + shaderSource;
    }
    
    private String addMaliPolyfills(String shaderSource) {
        if (shaderSource.contains("srgbToLinear")) return shaderSource;
        String polyfills = "
vec3 srgbToLinear(vec3 srgb) {
vec3 linear;
for (int i = 0; i < 3; i++) {
if (srgb[i] <= 0.04045) linear[i] = srgb[i] / 12.92;
else linear[i] = pow((srgb[i] + 0.055) / 1.055, 2.4);
}
return linear;
}
vec3 linearToSrgb(vec3 linear) {
vec3 srgb;
for (int i = 0; i < 3; i++) {
if (linear[i] <= 0.0031308) srgb[i] = linear[i] * 12.92;
else srgb[i] = 1.055 * pow(linear[i], 1.0 / 2.4) - 0.055;
}
return srgb;
}
";
        int insertPos = shaderSource.indexOf("

");
        return shaderSource.substring(0, insertPos + 2) + polyfills + shaderSource.substring(insertPos + 2);
    }
    
    public boolean isMaliGpu() {
        return capabilities != null && (capabilities.getRenderer().contains("Mali") || capabilities.getVendor().contains("ARM"));
    }
    
    public boolean isMaliG615() {
        return capabilities != null && capabilities.getRenderer().contains("Mali-G615");
    }
    
    public GpuCapabilities getCapabilities() { return capabilities; }
    
    public String fixGreenPixels(String shaderSource) {
        String fixed = shaderSource;
        fixed = fixed.replaceAll("texture\(([^,]+),\s*([^\)]+)\)\.rgb", "srgbToLinear(textureLod($1, $2, 0.0)).rgb");
        fixed = fixed.replaceAll("fragColor\s*=\s*vec4\(([^,]+),\s*([^\)]+)\)", "fragColor = vec4(linearToSrgb($1), $2)");
        return fixed;
    }
    
    public String fixGridLines(String shaderSource) {
        return shaderSource;
    }
    
    public String fixShadows(String shaderSource) {
        String fixed = shaderSource;
        fixed = fixed.replaceAll("u_ShadowBias\s*=\s*([0-9.]+)", "u_ShadowBias = $1 * 4.0");
        fixed = fixed.replaceAll("texture\(u_ShadowMap", "textureLod(u_ShadowMap");
        fixed = fixed.replaceAll("textureLod\(u_ShadowMap,([^,]+)", "textureLod(u_ShadowMap, $1, 0.0)");
        return fixed;
    }
    
    public String applyAllFixes(String shaderSource) {
        String fixed = applyFixes(shaderSource);
        fixed = fixGreenPixels(fixed);
        fixed = fixGridLines(fixed);
        fixed = fixShadows(fixed);
        return fixed;
    }
}