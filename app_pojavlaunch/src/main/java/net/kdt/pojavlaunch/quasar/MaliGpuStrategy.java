package net.kdt.pojavlaunch.quasar;

public class MaliGpuStrategy extends BaseShaderStrategy {
    public MaliGpuStrategy() { super("mali_gpu", "Mali-specific shader optimizations"); }
    
    @Override
    public boolean canProcess(ShaderInfo shaderInfo) {
        if (!isEnabled()) return false;
        GpuCapabilities caps = shaderInfo.getCapabilities();
        return caps != null && (caps.getRenderer().contains("Mali") || caps.getVendor().contains("ARM"));
    }
    
    @Override
    public int getPriority() { return 90; }
    
    @Override
    protected String doProcess(String shaderSource, ShaderInfo shaderInfo) {
        String processed = shaderSource;
        processed = fixColorSpace(processed);
        processed = fixShadows(processed);
        processed = fixTextures(processed);
        processed = addMaliDefines(processed);
        return processed;
    }
    
    private String fixColorSpace(String source) {
        if (source.contains("texture(") && !source.contains("srgbToLinear")) {
            source = source.replaceAll("texture(([^,]+),s*([^)]+))\.rgb", "srgbToLinear(textureLod($1, $2, 0.0)).rgb");
        }
        if (source.contains("fragColor") && !source.contains("linearToSrgb")) {
            source = source.replaceAll("fragColor\s*=\s*vec4(([^,]+),s*([^)]+))", "fragColor = vec4(linearToSrgb($1), $2)");
        }
        return source;
    }
    
    private String fixShadows(String source) {
        if (source.contains("u_ShadowBias")) {
            source = source.replaceAll("u_ShadowBias\s*=\s*([0-9.]+)", "u_ShadowBias = $1 * 4.0");
        }
        if (source.contains("texture(u_ShadowMap")) {
            source = source.replaceAll("texture(u_ShadowMap", "textureLod(u_ShadowMap");
        }
        return source;
    }
    
    private String fixTextures(String source) {
        return source;
    }
    
    private String addMaliDefines(String source) {
        if (!source.contains("#define MALI_GPU")) {
            int versionIndex = source.indexOf("#version");
            if (versionIndex >= 0) {
                int newlineIndex = source.indexOf('
', versionIndex);
                if (newlineIndex >= 0) {
                    String defines = "
#define MALI_GPU 1
#define MALI_G615 1
";
                    return source.substring(0, newlineIndex + 1) + defines + source.substring(newlineIndex + 1);
                }
            }
        }
        return source;
    }
}