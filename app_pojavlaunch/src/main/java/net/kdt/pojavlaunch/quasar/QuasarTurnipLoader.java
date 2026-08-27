package net.kdt.pojavlaunch.quasar;

/**
 * Loader that ensures Turnip is loaded before Quasar renderer
 * 
 * This class uses Java's ServiceLoader mechanism to ensure it's loaded
 * early in the classloading process, before LTW can initialize.
 */
public class QuasarTurnipLoader {
    
    // This static block runs when the class is loaded
    static {
        loadTurnip();
    }
    
    /**
     * Load Turnip integration
     */
    public static void loadTurnip() {
        try {
            // Force Turnip
            QuasarTurnipForce.forceTurnip();
            
            // Initialize shader manager
            TurnipShaderManager.init();
            
            // Block LTW
            LTWBlocker.blockLTW();
            
            System.out.println("[QuasarTurnipLoader] Turnip loaded, LTW blocked");
            
        } catch (Exception e) {
            System.err.println("[QuasarTurnipLoader] Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Process a shader
     */
    public static String processShader(String shaderSource, String shaderName, int shaderType) {
        return TurnipShaderManager.processShader(shaderSource, shaderName, shaderType);
    }
}
