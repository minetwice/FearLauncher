package net.kdt.pojavlaunch.quasar;

public abstract class BaseShaderStrategy implements ShaderStrategy {
    private final String name;
    private final String description;
    private boolean enabled = true;
    private boolean lastSuccess = false;
    private StrategyStats stats = new StrategyStats();
    
    protected BaseShaderStrategy(String name, String description) {
        this.name = name;
        this.description = description;
    }
    
    @Override public String getName() { return name; }
    @Override public String getDescription() { return description; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean enabled) { this.enabled = enabled; }
    @Override public boolean isSuccessful() { return lastSuccess; }
    @Override public StrategyStats getStats() { return stats; }
    @Override public void resetStats() { stats = new StrategyStats(); }
    
    protected String processWithTracking(String shaderSource, ShaderInfo shaderInfo) {
        stats.incrementAttempts();
        long startTime = System.nanoTime();
        try {
            String result = doProcess(shaderSource, shaderInfo);
            lastSuccess = (result != null && !result.equals(shaderSource));
            stats.incrementSuccesses();
            stats.addProcessingTime(System.nanoTime() - startTime);
            return result;
        } catch (Exception e) {
            lastSuccess = false;
            stats.incrementFailures();
            stats.addProcessingTime(System.nanoTime() - startTime);
            System.err.println("[" + name + "] Processing failed: " + e.getMessage());
            return shaderSource;
        }
    }
    
    protected abstract String doProcess(String shaderSource, ShaderInfo shaderInfo);
    protected boolean usesExtension(String shaderSource, String extension) {
        return shaderSource.contains("#extension " + extension) || shaderSource.contains("GL_" + extension);
    }
    protected boolean usesFeature(String shaderSource, String feature) { return shaderSource.contains(feature); }
    protected String replaceAll(String source, String pattern, String replacement) { return source.replace(pattern, replacement); }
}