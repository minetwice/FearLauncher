package net.kdt.pojavlaunch.quasar;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages all shaders with Turnip processing
 * 
 * This ensures all shaders are processed through the Turnip pipeline
 * to fix glitches and enable all features on Mali GPUs.
 */
public class TurnipShaderManager {
    
    private static final Map<String, String> shaderCache = new HashMap<>();
    private static boolean initialized = false;
    
    /**
     * Initialize the shader manager
     */
    public static void init() {
        if (initialized) return;
        initialized = true;
        
        // Force Turnip
        QuasarTurnipForce.forceTurnip();
        
        System.out.println("[TurnipShaderManager] Initialized");
    }
    
    /**
     * Process a shader
     */
    public static String processShader(String shaderSource, String shaderName, int shaderType) {
        if (!initialized) init();
        
        String cacheKey = shaderName + "_" + shaderType;
        
        // Check cache
        if (shaderCache.containsKey(cacheKey)) {
            return shaderCache.get(cacheKey);
        }
        
        // Process through Turnip
        String processed = ShaderGlitchFixer.fixShader(shaderSource, shaderName, shaderType);
        
        // Cache the result
        shaderCache.put(cacheKey, processed);
        
        return processed;
    }
    
    /**
     * Process vertex shader
     */
    public static String processVertexShader(String shaderSource, String shaderName) {
        return processShader(shaderSource, shaderName, 0);
    }
    
    /**
     * Process fragment shader
     */
    public static String processFragmentShader(String shaderSource, String shaderName) {
        return processShader(shaderSource, shaderName, 1);
    }
    
    /**
     * Clear shader cache
     */
    public static void clearCache() {
        shaderCache.clear();
    }
    
    /**
     * Get shader count
     */
    public static int getShaderCount() {
        return shaderCache.size();
    }
}
