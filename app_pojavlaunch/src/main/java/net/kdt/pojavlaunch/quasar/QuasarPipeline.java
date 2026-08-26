package net.kdt.pojavlaunch.quasar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QuasarPipeline {
    private final ShaderPreprocessor preprocessor;
    private final ShaderProcessor processor;
    private final MaliShaderFixes maliFixes;
    private final MaliPolyfillInjector polyfillInjector;
    private final Stage4ShaderRefiner refiner;
    private final ShaderCache shaderCache;
    private ProcessingStats pipelineStats = new ProcessingStats();
    
    private boolean enablePreprocessing = true;
    private boolean enableStrategyProcessing = true;
    private boolean enableMaliFixes = true;
    private boolean enablePolyfills = true;
    private boolean enableRefinement = true;
    private boolean enableCaching = true;
    
    public QuasarPipeline() {
        this.preprocessor = new ShaderPreprocessor();
        this.processor = ShaderProcessor.getInstance();
        this.maliFixes = new MaliShaderFixes();
        this.polyfillInjector = new MaliPolyfillInjector();
        this.refiner = new Stage4ShaderRefiner();
        this.shaderCache = new ShaderCache();
    }
    
    public String processShader(String shaderSource, ShaderInfo shaderInfo) {
        if (shaderSource == null || shaderSource.isEmpty()) return shaderSource;
        String cacheKey = generateCacheKey(shaderSource, shaderInfo);
        if (enableCaching) {
            String cached = shaderCache.get(cacheKey);
            if (cached != null) { return cached; }
        }
        shaderInfo.setCapabilities(preprocessor.getCapabilities());
        String processed = shaderSource;
        if (enablePreprocessing) processed = preprocessor.preprocess(processed, shaderInfo);
        if (enableStrategyProcessing) processed = processor.processShader(processed, shaderInfo);
        if (enableMaliFixes && maliFixes.isMaliGpu()) processed = maliFixes.applyAllFixes(processed);
        if (enablePolyfills) processed = polyfillInjector.injectPolyfills(processed, shaderInfo);
        if (enableRefinement) processed = refiner.refine(processed, shaderInfo);
        if (enableCaching) shaderCache.put(cacheKey, processed);
        return processed;
    }
    
    public String processShader(String shaderSource, String shaderName, ShaderInfo.ShaderType type) {
        ShaderInfo info = new ShaderInfo(shaderName, type);
        return processShader(shaderSource, info);
    }
    
    private String generateCacheKey(String shaderSource, ShaderInfo shaderInfo) {
        return shaderInfo.getName() + "_" + shaderInfo.getType() + "_" + Integer.toHexString(shaderSource.hashCode());
    }
    
    public void clearCache() {
        shaderCache.clear();
        preprocessor.clearIncludeCache();
    }
    
    public ShaderCache getShaderCache() { return shaderCache; }
    public ShaderProcessor getShaderProcessor() { return processor; }
    public MaliShaderFixes getMaliFixes() { return maliFixes; }
    public MaliPolyfillInjector getPolyfillInjector() { return polyfillInjector; }
    public Stage4ShaderRefiner getRefiner() { return refiner; }
    public ShaderPreprocessor getPreprocessor() { return preprocessor; }
    public ProcessingStats getStats() { return pipelineStats; }
    public void resetStats() { pipelineStats.reset(); }
    
    public boolean isPreprocessingEnabled() { return enablePreprocessing; }
    public void setPreprocessingEnabled(boolean enabled) { this.enablePreprocessing = enabled; }
    public boolean isStrategyProcessingEnabled() { return enableStrategyProcessing; }
    public void setStrategyProcessingEnabled(boolean enabled) { this.enableStrategyProcessing = enabled; }
    public boolean isMaliFixesEnabled() { return enableMaliFixes; }
    public void setMaliFixesEnabled(boolean enabled) { this.enableMaliFixes = enabled; }
    public boolean isPolyfillsEnabled() { return enablePolyfills; }
    public void setPolyfillsEnabled(boolean enabled) { this.enablePolyfills = enabled; }
    public boolean isRefinementEnabled() { return enableRefinement; }
    public void setRefinementEnabled(boolean enabled) { this.enableRefinement = enabled; }
    public boolean isCachingEnabled() { return enableCaching; }
    public void setCachingEnabled(boolean enabled) { this.enableCaching = enabled; }
    public boolean isMaliGpu() { return maliFixes.isMaliGpu(); }
    public GpuCapabilities getCapabilities() { return preprocessor.getCapabilities(); }
    
    public static class ShaderPair {
        private final String vertexSource;
        private final String fragmentSource;
        public ShaderPair(String vertexSource, String fragmentSource) {
            this.vertexSource = vertexSource;
            this.fragmentSource = fragmentSource;
        }
        public String getVertexSource() { return vertexSource; }
        public String getFragmentSource() { return fragmentSource; }
    }
}