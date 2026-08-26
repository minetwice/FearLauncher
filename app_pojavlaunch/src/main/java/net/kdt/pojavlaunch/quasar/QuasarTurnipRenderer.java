package net.kdt.pojavlaunch.quasar;

/**
 * Quasar Renderer with Turnip (Vulkan) Integration
 * 
 * This class provides the integration point between Quasar renderer and Turnip.
 * It automatically:
 * - Detects Mali/Adreno GPUs
 * - Initializes Turnip instead of LTW
 * - Processes all shaders through Turnip pipeline
 * - Prevents crashes on Mali GPUs
 * - Handles all shader types (Complementary, Astra, Solas)
 */
public class QuasarTurnipRenderer {
    
    private static boolean turnipInitialized = false;
    private static TurnipQuasarPipeline pipeline;
    private static boolean isMaliGpu = false;
    private static boolean isAdrenoGpu = false;
    private static boolean turnipAvailable = false;
    
    /**
     * Initialize Quasar renderer with Turnip support
     * 
     * Call this at the start of your renderer initialization
     */
    public static void initializeRenderer() {
        if (turnipInitialized) {
            return;
        }
        
        turnipInitialized = true;
        
        try {
            // Initialize Turnip integration
            QuasarTurnipIntegration.init();
            
            // Get pipeline
            pipeline = QuasarTurnipIntegration.getPipeline();
            
            // Check GPU type
            isMaliGpu = QuasarTurnipIntegration.isMaliGpu();
            isAdrenoGpu = QuasarTurnipIntegration.isAdrenoGpu();
            turnipAvailable = QuasarTurnipIntegration.isUsingTurnip();
            
            // Log initialization
            System.out.println("[QuasarTurnipRenderer] Initialized");
            System.out.println("[QuasarTurnipRenderer] Mali GPU: " + isMaliGpu);
            System.out.println("[QuasarTurnipRenderer] Adreno GPU: " + isAdrenoGpu);
            System.out.println("[QuasarTurnipRenderer] Turnip Available: " + turnipAvailable);
            
        } catch (Exception e) {
            System.err.println("[QuasarTurnipRenderer] Initialization failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Process a shader through Turnip pipeline
     * 
     * This should be called for ALL shaders in the Quasar renderer
     */
    public static String processShader(String shaderSource, String shaderName, int shaderType) {
        if (!turnipInitialized) {
            initializeRenderer();
        }
        
        try {
            ShaderInfo.ShaderType type = getShaderType(shaderType);
            return pipeline.processShader(shaderSource, shaderName, type);
        } catch (Exception e) {
            System.err.println("[QuasarTurnipRenderer] Shader processing failed for " + shaderName + ": " + e.getMessage());
            return shaderSource; // Return original on error
        }
    }
    
    /**
     * Process vertex shader
     */
    public static String processVertexShader(String shaderSource, String shaderName) {
        return processShader(shaderSource, shaderName, 0); // 0 = VERTEX
    }
    
    /**
     * Process fragment shader
     */
    public static String processFragmentShader(String shaderSource, String shaderName) {
        return processShader(shaderSource, shaderName, 1); // 1 = FRAGMENT
    }
    
    /**
     * Process geometry shader
     */
    public static String processGeometryShader(String shaderSource, String shaderName) {
        return processShader(shaderSource, shaderName, 2); // 2 = GEOMETRY
    }
    
    /**
     * Process compute shader
     */
    public static String processComputeShader(String shaderSource, String shaderName) {
        return processShader(shaderSource, shaderName, 3); // 3 = COMPUTE
    }
    
    /**
     * Convert numeric type to ShaderInfo.ShaderType
     */
    private static ShaderInfo.ShaderType getShaderType(int type) {
        switch (type) {
            case 0: return ShaderInfo.ShaderType.VERTEX;
            case 1: return ShaderInfo.ShaderType.FRAGMENT;
            case 2: return ShaderInfo.ShaderType.GEOMETRY;
            case 3: return ShaderInfo.ShaderType.COMPUTE;
            default: return ShaderInfo.ShaderType.UNKNOWN;
        }
    }
    
    /**
     * Create Complementary shader for Quasar
     */
    public static ComplementaryShader createComplementaryShader() {
        if (!turnipInitialized) {
            initializeRenderer();
        }
        return QuasarTurnipIntegration.createComplementaryShader();
    }
    
    /**
     * Create Astra shader for Quasar
     */
    public static AstraShader createAstraShader() {
        if (!turnipInitialized) {
            initializeRenderer();
        }
        return QuasarTurnipIntegration.createAstraShader();
    }
    
    /**
     * Create Solas shader for Quasar
     */
    public static SolasShader createSolasShader() {
        if (!turnipInitialized) {
            initializeRenderer();
        }
        return QuasarTurnipIntegration.createSolasShader();
    }
    
    /**
     * Check if Turnip is available
     */
    public static boolean isTurnipAvailable() {
        if (!turnipInitialized) {
            initializeRenderer();
        }
        return turnipAvailable;
    }
    
    /**
     * Check if current GPU is Mali
     */
    public static boolean isMaliGpu() {
        if (!turnipInitialized) {
            initializeRenderer();
        }
        return isMaliGpu;
    }
    
    /**
     * Check if current GPU is Adreno
     */
    public static boolean isAdrenoGpu() {
        if (!turnipInitialized) {
            initializeRenderer();
        }
        return isAdrenoGpu;
    }
    
    /**
     * Get the Turnip pipeline
     */
    public static TurnipQuasarPipeline getPipeline() {
        if (!turnipInitialized) {
            initializeRenderer();
        }
        return pipeline;
    }
    
    /**
     * Force Turnip to be used
     */
    public static void forceTurnip(boolean force) {
        QuasarTurnipIntegration.forceTurnip(force);
        turnipAvailable = force;
    }
    
    /**
     * Get GPU information
     */
    public static String getGpuInfo() {
        return QuasarTurnipIntegration.getGpuInfo();
    }
    
    /**
     * Get status
     */
    public static String getStatus() {
        return QuasarTurnipIntegration.getStatus();
    }
}
