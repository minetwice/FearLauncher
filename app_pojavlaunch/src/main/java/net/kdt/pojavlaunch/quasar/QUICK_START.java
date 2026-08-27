package net.kdt.pojavlaunch.quasar;

/**
 * QUICK START - Just copy these lines into your code!
 * 
 * This is the ABSOLUTE MINIMUM you need to do to make Turnip work.
 * 
 * COPY THESE LINES INTO YOUR MAIN CLASS:
 * 
 * ============================================================================
 * 
 *     // AT THE VERY TOP OF YOUR MAIN METHOD:
 *     QuasarTurnipForce.forceTurnip();
 * 
 * ============================================================================
 * 
 * That is it! This ONE line will:
 * 1. Block LTW
 * 2. Force Turnip
 * 3. Enable shaders
 * 4. Fix color glitches
 * 5. Prevent crashes
 * 
 * If you want more control, also add:
 * 
 *     TurnipShaderManager.init();
 *     LTWBlocker.blockLTW();
 * 
 * But just the first line is enough for most cases.
 */
public class QUICK_START {
    
    // This is just a documentation class
    // The actual code you need is above in the comments
    
    /**
     * Example of how to use in your main class
     */
    public static class ExampleMain {
        public static void main(String[] args) {
            // ============================================================
            // ADD THIS ONE LINE AT THE TOP
            // ============================================================
            QuasarTurnipForce.forceTurnip();
            
            // ============================================================
            // Rest of your existing code
            // ============================================================
            // Your existing initialization code here
            // ...
        }
    }
    
    /**
     * If you want to process shaders manually
     */
    public static class ExampleShaderProcessing {
        public String processMyShader(String shaderSource) {
            // Process through Turnip
            return TurnipShaderManager.processFragmentShader(shaderSource, "MyShader");
        }
    }
    
    /**
     * If you want to create shaders
     */
    public static class ExampleShaderCreation {
        public void createShaders() {
            ComplementaryShader comp = TurnipSetup.createComplementaryShader();
            AstraShader astra = TurnipSetup.createAstraShader();
            SolasShader solas = TurnipSetup.createSolasShader();
        }
    }
}
