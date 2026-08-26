package net.kdt.pojavlaunch.quasar;

/**
 * Main integration class for Quasar with Turnip support
 * 
 * This is the EASIEST way to fix:
 * 1. Red/Green color glitches
 * 2. Complementary/Astra shaders not working
 * 3. LTW limitations on Mali/Adreno GPUs
 * 
 * SIMPLY CALL: QuasarTurnipIntegration.init()
 * 
 * This will automatically:
 * - Replace LTW with Turnip for Mali/Adreno
 * - Fix color space issues
 * - Enable shaders in Iris
 * - Optimize shader processing
 */
public class QuasarTurnipIntegration {
    
    private static boolean initialized = false;
    private static TurnipQuasarPipeline pipeline;
    private static TurnipShaderProcessor shaderProcessor;
    
    /**
     * Initialize Turnip integration - CALL THIS FIRST!
     * 
     * Add this line at the very beginning of your application:
     * QuasarTurnipIntegration.init();
     */
    public static void init() {
        if (initialized) return;
        initialized = true;
        
        // Initialize Turnip configuration
        TurnipConfig.initialize();
        
        // Create pipeline and processor
        pipeline = TurnipConfig.createPipeline();
        shaderProcessor = TurnipConfig.createShaderProcessor();
        
        // Force enable shaders in Iris
        System.setProperty("iris.enableShaders", "true");
        System.setProperty("iris.shaders.enabled", "true");
        System.setProperty("iris.shaderpacks.enabled", "true");
        
        // Log initialization
        System.out.println("[QuasarTurnip] Turnip integration initialized!");
        System.out.println("[QuasarTurnip] Color glitches will be fixed");
        System.out.println("[QuasarTurnip] Complementary/Astra shaders will work");
        System.out.println("[QuasarTurnip] Using Turnip instead of LTW");
    }
    
    /**
     * Get the Turnip-optimized pipeline
     */
    public static TurnipQuasarPipeline getPipeline() {
        if (!initialized) init();
        return pipeline;
    }
    
    /**
     * Get the Turnip shader processor
     */
    public static TurnipShaderProcessor getShaderProcessor() {
        if (!initialized) init();
        return shaderProcessor;
    }
    
    /**
     * Process a shader through Turnip pipeline
     */
    public static String processShader(String shaderSource, ShaderInfo shaderInfo) {
        if (!initialized) init();
        return pipeline.processShader(shaderSource, shaderInfo);
    }
    
    /**
     * Process a shader with name and type
     */
    public static String processShader(String shaderSource, String shaderName, ShaderInfo.ShaderType type) {
        if (!initialized) init();
        return pipeline.processShader(shaderSource, shaderName, type);
    }
    
    /**
     * Process vertex and fragment shaders together
     */
    public static TurnipQuasarPipeline.ShaderPair processShaderPair(
            String vertexSource, String fragmentSource, String shaderName) {
        if (!initialized) init();
        return pipeline.processShaderPair(vertexSource, fragmentSource, shaderName);
    }
    
    /**
     * Create Complementary shader (FIXED for Turnip)
     */
    public static ComplementaryShader createComplementaryShader() {
        if (!initialized) init();
        return pipeline.createComplementaryShader();
    }
    
    /**
     * Create Astra shader (FIXED for Turnip)
     */
    public static AstraShader createAstraShader() {
        if (!initialized) init();
        return pipeline.createAstraShader();
    }
    
    /**
     * Create Solas shader (FIXED for Turnip)
     */
    public static SolasShader createSolasShader() {
        if (!initialized) init();
        return pipeline.createSolasShader();
    }
    
    /**
     * Check if Turnip is being used
     */
    public static boolean isUsingTurnip() {
        if (!initialized) init();
        return pipeline.isUsingTurnip();
    }
    
    /**
     * Check if current GPU is Mali
     */
    public static boolean isMaliGpu() {
        if (!initialized) init();
        return pipeline.isMaliGpu();
    }
    
    /**
     * Check if current GPU is Adreno
     */
    public static boolean isAdrenoGpu() {
        if (!initialized) init();
        return pipeline.isAdrenoGpu();
    }
    
    /**
     * Force Turnip to be used (for testing)
     */
    public static void forceTurnip(boolean force) {
        if (!initialized) init();
        TurnipConfig.FORCE_TURNIP = force;
        pipeline.setUseTurnip(force);
        System.out.println("[QuasarTurnip] Turnip forced: " + force);
    }
    
    /**
     * Enable or disable color space fixes
     */
    public static void enableColorSpaceFixes(boolean enable) {
        TurnipConfig.ENABLE_COLOR_SPACE_FIXES = enable;
        System.out.println("[QuasarTurnip] Color space fixes: " + enable);
    }
    
    /**
     * Enable or disable shader caching
     */
    public static void enableShaderCache(boolean enable) {
        TurnipConfig.ENABLE_SHADER_CACHE = enable;
        System.out.println("[QuasarTurnip] Shader cache: " + enable);
    }
    
    /**
     * Get current GPU information
     */
    public static String getGpuInfo() {
        TurnipIntegration turnip = new TurnipIntegration();
        return "GPU: " + turnip.getGpuRenderer() + 
               " (Vendor: " + turnip.getGpuVendor() + ")" +
               " | Turnip: " + (turnip.isTurnipEnabled() ? "Enabled" : "Disabled");
    }
}
