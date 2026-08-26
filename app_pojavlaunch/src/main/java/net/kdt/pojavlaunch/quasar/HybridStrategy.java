package net.kdt.pojavlaunch.quasar;

public class HybridStrategy extends BaseShaderStrategy {
    public HybridStrategy() { super("hybrid", "Combined CPU/GPU processing"); }
    
    @Override
    public boolean canProcess(ShaderInfo shaderInfo) { return isEnabled(); }
    
    @Override
    public int getPriority() { return 30; }
    
    @Override
    protected String doProcess(String shaderSource, ShaderInfo shaderInfo) {
        ShaderProcessor processor = ShaderProcessor.getInstance();
        String gpuResult = processor.processWithStrategy(shaderSource, shaderInfo, "mali_gpu");
        if (gpuResult != null && !gpuResult.equals(shaderSource)) {
            return gpuResult;
        }
        return processor.processWithStrategy(shaderSource, shaderInfo, "cpu_fallback");
    }
}