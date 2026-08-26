package net.kdt.pojavlaunch.quasar;

/**
 * Hook for injecting Turnip into existing Quasar renderer
 * 
 * This class provides static methods that can be called from the Quasar renderer
 * to automatically use Turnip for shader processing.
 */
public class QuasarTurnipHook {
    
    /**
     * Initialize Turnip - Call this FIRST
     */
    public static void init() {
        QuasarTurnipIntegration.init();
    }
    
    /**
     * Process a shader - Call this for EVERY shader
     * 
     * @param shaderSource The original shader source
     * @param shaderName The name of the shader
     * @param shaderType 0=VERTEX, 1=FRAGMENT, 2=GEOMETRY, 3=COMPUTE
     * @return Processed shader source
     */
    public static String processShader(String shaderSource, String shaderName, int shaderType) {
        return QuasarTurnipRenderer.processShader(shaderSource, shaderName, shaderType);
    }
    
    /**
     * Process vertex shader
     */
    public static String processVertexShader(String shaderSource, String shaderName) {
        return QuasarTurnipRenderer.processVertexShader(shaderSource, shaderName);
    }
    
    /**
     * Process fragment shader
     */
    public static String processFragmentShader(String shaderSource, String shaderName) {
        return QuasarTurnipRenderer.processFragmentShader(shaderSource, shaderName);
    }
    
    /**
     * Check if Turnip is available
     */
    public static boolean isTurnipAvailable() {
        return QuasarTurnipRenderer.isTurnipAvailable();
    }
    
    /**
     * Check if Mali GPU
     */
    public static boolean isMaliGpu() {
        return QuasarTurnipRenderer.isMaliGpu();
    }
    
    /**
     * Check if Adreno GPU
     */
    public static boolean isAdrenoGpu() {
        return QuasarTurnipRenderer.isAdrenoGpu();
    }
    
    /**
     * Force Turnip
     */
    public static void forceTurnip(boolean force) {
        QuasarTurnipRenderer.forceTurnip(force);
    }
    
    /**
     * Create Complementary shader
     */
    public static ComplementaryShader createComplementary() {
        return QuasarTurnipRenderer.createComplementaryShader();
    }
    
    /**
     * Create Astra shader
     */
    public static AstraShader createAstra() {
        return QuasarTurnipRenderer.createAstraShader();
    }
    
    /**
     * Create Solas shader
     */
    public static SolasShader createSolas() {
        return QuasarTurnipRenderer.createSolasShader();
    }
}
