package net.kdt.pojavlaunch.quasar;

public class CpuFallbackStrategy extends BaseShaderStrategy {
    public CpuFallbackStrategy() { super("cpu_fallback", "CPU-based shader emulation"); }
    
    @Override
    public boolean canProcess(ShaderInfo shaderInfo) { return isEnabled(); }
    
    @Override
    public int getPriority() { return 40; }
    
    @Override
    protected String doProcess(String shaderSource, ShaderInfo shaderInfo) {
        GpuCapabilities caps = shaderInfo.getCapabilities();
        if (caps == null) return shaderSource;
        
        String processed = shaderSource;
        processed = emulateGeometryShaders(processed);
        processed = emulateAtomicOperations(processed);
        processed = simplifyComplexOperations(processed);
        return processed;
    }
    
    private String emulateGeometryShaders(String source) {
        return source.replaceAll("EmitVertex\s*\(\)", "// CPU: Vertex emitted");
    }
    
    private String emulateAtomicOperations(String source) {
        return source.replaceAll("atomicAdd\s*\(", "cpuAtomicAdd(");
    }
    
    private String simplifyComplexOperations(String source) {
        return source;
    }
}