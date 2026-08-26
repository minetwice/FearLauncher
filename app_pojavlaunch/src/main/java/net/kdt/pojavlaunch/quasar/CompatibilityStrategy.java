package net.kdt.pojavlaunch.quasar;

public class CompatibilityStrategy extends BaseShaderStrategy {
    public CompatibilityStrategy() { super("compatibility", "General compatibility fixes"); }
    
    @Override
    public boolean canProcess(ShaderInfo shaderInfo) { return isEnabled(); }
    
    @Override
    public int getPriority() { return 60; }
    
    @Override
    protected String doProcess(String shaderSource, ShaderInfo shaderInfo) {
        String processed = shaderSource;
        processed = ensurePrecision(processed);
        processed = fixInterpolation(processed);
        processed = fixVersion(processed);
        return processed;
    }
    
    private String ensurePrecision(String source) {
        if (!source.contains("precision")) {
            int versionEnd = source.indexOf("
");
            if (versionEnd > 0) {
                return source.substring(0, versionEnd + 1) + "precision highp float;
" + source.substring(versionEnd + 1);
            }
        }
        return source;
    }
    
    private String fixInterpolation(String source) {
        return source.replace("noperspective", "smooth");
    }
    
    private String fixVersion(String source) {
        if (!source.contains("#version")) {
            return "#version 300 es
" + source;
        }
        return source;
    }
}