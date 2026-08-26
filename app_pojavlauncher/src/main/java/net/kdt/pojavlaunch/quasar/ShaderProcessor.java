package net.kdt.pojavlaunch.quasar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

public class ShaderProcessor {
    private static final ShaderProcessor INSTANCE = new ShaderProcessor();
    private final Map<String, ShaderStrategy> strategies;
    private final ShaderCache shaderCache;
    private final ExtensionDetector extensionDetector;
    private final PerformanceOptimizer performanceOptimizer;
    private final List<ShaderStrategy> processingPipeline;
    private static final String[] STRATEGY_PRIORITY = {"fast_path", "mali_gpu", "adreno_gpu", "extension_polyfill", "compatibility", "cpu_fallback", "hybrid"};
    
    private ShaderProcessor() {
        this.strategies = new HashMap<>();
        this.shaderCache = new ShaderCache();
        this.extensionDetector = new ExtensionDetector();
        this.performanceOptimizer = new PerformanceOptimizer();
        this.processingPipeline = new ArrayList<>();
        initializeStrategies();
        buildProcessingPipeline();
    }
    
    public static ShaderProcessor getInstance() { return INSTANCE; }
    private void initializeStrategies() {
        registerStrategy(new FastPathStrategy());
        registerStrategy(new MaliGpuStrategy());
        registerStrategy(new AdrenoGpuStrategy());
        registerStrategy(new ExtensionPolyfillStrategy());
        registerStrategy(new CompatibilityStrategy());
        registerStrategy(new CpuFallbackStrategy());
        registerStrategy(new HybridStrategy());
        ServiceLoader<ShaderStrategy> loader = ServiceLoader.load(ShaderStrategy.class);
        for (ShaderStrategy strategy : loader) { registerStrategy(strategy); }
    }
    public void registerStrategy(ShaderStrategy strategy) { strategies.put(strategy.getName(), strategy); }
    private void buildProcessingPipeline() {
        processingPipeline.clear();
        for (String strategyName : STRATEGY_PRIORITY) {
            ShaderStrategy strategy = strategies.get(strategyName);
            if (strategy != null) { processingPipeline.add(strategy); }
        }
    }
    public String processShader(String shaderSource, ShaderInfo shaderInfo) {
        if (shaderSource == null || shaderSource.isEmpty()) { return shaderSource; }
        String cacheKey = generateCacheKey(shaderSource, shaderInfo);
        String cached = shaderCache.get(cacheKey);
        if (cached != null) { return cached; }
        GpuCapabilities capabilities = extensionDetector.detectCapabilities();
        shaderInfo.setCapabilities(capabilities);
        String optimized = performanceOptimizer.optimize(shaderSource, shaderInfo);
        String processed = optimized;
        for (ShaderStrategy strategy : processingPipeline) {
            if (strategy.canProcess(shaderInfo)) {
                processed = strategy.process(processed, shaderInfo);
                if (strategy.isSuccessful()) { shaderCache.put(cacheKey, processed); return processed; }
            }
        }
        System.err.println("[ShaderProcessor] All strategies failed for shader: " + shaderInfo.getName());
        shaderCache.put(cacheKey, processed);
        return processed;
    }
    public String processShader(String shaderSource, String shaderName, ShaderInfo.ShaderType type) {
        ShaderInfo info = new ShaderInfo(shaderName, type);
        return processShader(shaderSource, info);
    }
    private String generateCacheKey(String shaderSource, ShaderInfo shaderInfo) {
        return shaderInfo.getName() + "_" + shaderInfo.getType() + "_" + Integer.toHexString(shaderSource.hashCode());
    }
    public void clearCache() { shaderCache.clear(); }
    public GpuCapabilities getCapabilities() { return extensionDetector.detectCapabilities(); }
    public String processWithStrategy(String shaderSource, ShaderInfo shaderInfo, String strategyName) {
        ShaderStrategy strategy = strategies.get(strategyName);
        if (strategy != null && strategy.canProcess(shaderInfo)) { return strategy.process(shaderSource, shaderInfo); }
        return shaderSource;
    }
    public List<String> getAvailableStrategies() { return new ArrayList<>(strategies.keySet()); }
    public List<String> getProcessingPipeline() {
        List<String> names = new ArrayList<>();
        for (ShaderStrategy strategy : processingPipeline) { names.add(strategy.getName()); }
        return names;
    }
    public void setStrategyPriority(String[] priorityOrder) {
        System.arraycopy(priorityOrder, 0, STRATEGY_PRIORITY, 0, Math.min(priorityOrder.length, STRATEGY_PRIORITY.length));
        buildProcessingPipeline();
    }
    public void setStrategyEnabled(String strategyName, boolean enabled) {
        ShaderStrategy strategy = strategies.get(strategyName);
        if (strategy != null) { strategy.setEnabled(enabled); buildProcessingPipeline(); }
    }
    public ProcessingStats getStats() {
        ProcessingStats stats = new ProcessingStats();
        for (ShaderStrategy strategy : strategies.values()) { stats.addStrategyStats(strategy.getName(), strategy.getStats()); }
        stats.setCacheStats(shaderCache.getStats());
        return stats;
    }
    public void resetStats() {
        for (ShaderStrategy strategy : strategies.values()) { strategy.resetStats(); }
        shaderCache.resetStats();
    }
}