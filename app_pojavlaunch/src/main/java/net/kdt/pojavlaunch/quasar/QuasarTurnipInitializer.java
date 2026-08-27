package net.kdt.pojavlaunch.quasar;

/**
 * Initializer that forces Turnip and disables LTW in Quasar renderer
 * 
 * This MUST be called before Quasar renderer initialization.
 * It will:
 * 1. Force Turnip to be used instead of LTW
 * 2. Enable all shaders
 * 3. Setup all fixes for Mali GPUs
 */
public class QuasarTurnipInitializer {
    
    private static boolean initialized = false;
    
    /**
     * Initialize Turnip for Quasar - CALL THIS FIRST!
     * 
     * This should be called in your main class or launcher initialization,
     * BEFORE any Quasar renderer code is executed.
     */
    public static void initialize() {
        if (initialized) return;
        initialized = true;
        
        // Force Turnip to be used
        forceTurnip();
        
        // Enable shaders
        enableShaders();
        
        // Setup Mali fixes
        setupMaliFixes();
        
        // Initialize Turnip pipeline
        TurnipSetup.initAll();
        
        System.out.println("[QuasarTurnipInitializer] Turnip forced, LTW disabled");
    }
    
    /**
     * Force Turnip and disable LTW
     */
    private static void forceTurnip() {
        // Set system properties to force Turnip
        System.setProperty("quasar.renderer", "turnip");
        System.setProperty("quasar.backend", "turnip");
        System.setProperty("quasar.force_turnip", "true");
        System.setProperty("quasar.disable_ltw", "true");
        System.setProperty("quasar.ltw.enabled", "false");
        
        // Environment variables for LTW
        System.setProperty("LIBGL_ES", "0");  // Disable ES
        System.setProperty("LIBGL_GL", "0");  // Disable GL
        
        // Force Vulkan
        System.setProperty("quasar.vulkan", "true");
        System.setProperty("quasar.vulkan.enabled", "true");
    }
    
    /**
     * Enable shaders in Iris
     */
    private static void enableShaders() {
        System.setProperty("iris.enableShaders", "true");
        System.setProperty("iris.shaders.enabled", "true");
        System.setProperty("iris.shaderpacks.enabled", "true");
        System.setProperty("iris.shaderpacks.complementary.enabled", "true");
        System.setProperty("iris.shaderpacks.astra.enabled", "true");
        System.setProperty("iris.shaderpacks.solas.enabled", "true");
    }
    
    /**
     * Setup Mali-specific fixes
     */
    private static void setupMaliFixes() {
        System.setProperty("quasar.mali.fix_green_pixels", "true");
        System.setProperty("quasar.mali.fix_grid_lines", "true");
        System.setProperty("quasar.mali.fix_shadows", "true");
        System.setProperty("quasar.mali.fix_textures", "true");
        System.setProperty("quasar.mali.optimized", "true");
        System.setProperty("quasar.color_space.fix", "true");
    }
}
