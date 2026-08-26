package net.kdt.pojavlaunch.quasar;

public class AdrenoGpuStrategy extends BaseShaderStrategy {
    public AdrenoGpuStrategy() { super("adreno_gpu", "Adreno-specific shader optimizations"); }
    
    @Override
    public boolean canProcess(ShaderInfo shaderInfo) {
        if (!isEnabled()) return false;
        GpuCapabilities caps = shaderInfo.getCapabilities();
        return caps != null && caps.getRenderer().contains("Adreno");
    }
    
    @Override
    public int getPriority() { return 85; }
    
    @Override
    protected String doProcess(String shaderSource, ShaderInfo shaderInfo) {
        String processed = shaderSource;
        processed = fixPrecision(processed);
        processed = fixExtensions(processed);
        return processed;
    }
    
    private String fixPrecision(String source) {
        return source.replace("precision mediump float", "precision highp float");
    }
    
    private String fixExtensions(String source) {
        return source;
    }
}