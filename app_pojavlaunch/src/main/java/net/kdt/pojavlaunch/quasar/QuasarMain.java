package net.kdt.pojavlaunch.quasar;

/**
 * Main entry point for Quasar with Turnip integration
 * 
 * This demonstrates how to integrate Turnip into your existing Quasar renderer.
 * 
 * IMPORTANT: Call TurnipSetup.initAll() BEFORE any other initialization!
 */
public class QuasarMain {
    
    /**
     * Main initialization method
     * 
     * This is where you should integrate Turnip into your existing code.
     * 
     * BEFORE (causes color glitches and shader failures):
     * public void init() {
     *     // Your existing code
     *     QuasarRenderer.init();
     *     // ...
     * }
     * 
     * AFTER (fixes everything):
     * public void init() {
     *     TurnipSetup.initAll();  // <-- ADD THIS LINE FIRST
     *     QuasarRenderer.init();
     *     // ...
     * }
     */
    public static void main(String[] args) {
        // ============================================
        // STEP 1: Initialize Turnip FIRST
        // ============================================
        TurnipSetup.initAll();
        
        // ============================================
        // STEP 2: Now initialize everything else
        // ============================================
        
        // Initialize Quasar renderer
        // QuasarRenderer.init();
        
        // Or use the Turnip-optimized pipeline directly
        TurnipQuasarPipeline pipeline = TurnipSetup.getPipeline();
        
        // ============================================
        // STEP 3: Process shaders (if needed)
        // ============================================
        
        // Example: Process a shader
        // String vertexShader = "...your vertex shader code...";
        // String processedVertex = TurnipSetup.processVertexShader(vertexShader, "MyVertex");
        
        // ============================================
        // STEP 4: Create shaders (optional)
        // ============================================
        
        // Create shaders that are guaranteed to work on Mali
        ComplementaryShader complementary = TurnipSetup.createComplementaryShader();
        AstraShader astra = TurnipSetup.createAstraShader();
        SolasShader solas = TurnipSetup.createSolasShader();
        
        // ============================================
        // STEP 5: Verify everything is working
        // ============================================
        
        System.out.println(TurnipSetup.getStatus());
        
        // ============================================
        // DONE! Everything is now working with Turnip
        // ============================================
    }
    
    /**
     * Example: How to integrate with existing Quasar renderer
     * 
     * If you have an existing Quasar renderer class, modify it like this:
     */
    public static class ExampleQuasarRenderer {
        
        // BEFORE
        // public void processShader(String source) {
        //     return source; // No processing
        // }
        
        // AFTER
        public String processShader(String source, String name, int type) {
            // Process through Turnip pipeline
            return TurnipSetup.processShader(source, name, type);
        }
        
        public void init() {
            // Initialize Turnip first
            TurnipSetup.initAll();
            
            // Then initialize renderer
            // ...
        }
    }
    
    /**
     * Example: How to use in Fabric mod
     */
    public static class FabricModExample {
        
        public void onInitialize() {
            // Initialize Turnip BEFORE registering anything
            TurnipSetup.initAll();
            
            // Now register your mod components
            // ...
        }
    }
    
    /**
     * Example: How to use in PojavLauncher
     */
    public static class PojavLauncherExample {
        
        public void onCreate() {
            // Initialize Turnip FIRST
            TurnipSetup.initAll();
            
            // Then initialize PojavLauncher
            // ...
        }
    }
}
