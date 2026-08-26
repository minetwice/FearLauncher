package net.kdt.pojavlaunch.quasar;

public class FastPathStrategy extends BaseShaderStrategy {
    public FastPathStrategy() {
        super("fast_path", "Direct pass-through for already compatible shaders");
    }
    
    @Override
    public boolean canProcess(ShaderInfo shaderInfo) {
        if (!isEnabled()) return false;
        GpuCapabilities caps = shaderInfo.getCapabilities();
        if (caps == null) return true;
        return caps.isMobile() && (caps.getVersion().contains("ES 3.0") || caps.getVersion().contains("ES 3.1") || caps.getVersion().contains("ES 3.2"));
    }
    
    @Override
    public int getPriority() { return 100; }
    
    @Override
    protected String doProcess(String shaderSource, ShaderInfo shaderInfo) {
        GpuCapabilities caps = shaderInfo.getCapabilities();
        if (shaderSource.contains("gl_ClipDistance") && !caps.supportsFramebufferFetch()) { return null; }
        if (shaderSource.contains("atomic") && !caps.supportsAtomicOperations()) { return null; }
        if (shaderSource.contains("EmitVertex") && !caps.supportsGeometryShaders()) { return null; }
        
        String processed = shaderSource;
        if (!processed.contains("#version")) {
            processed = "#version 300 es
" + processed;
        } else if (!processed.contains("es")) {
            processed = processed.replace("#version", "#version 300 es");
        }
        if (!processed.contains("precision")) {
            int versionEnd = processed.indexOf("
");
            if (versionEnd > 0) {
                processed = processed.substring(0, versionEnd + 1) + "precision highp float;
" + processed.substring(versionEnd + 1);
            }
        }
        return processed;
    }
}