package net.kdt.pojavlaunch.quasar;

/**
 * FORCE TURNIP - This is the main file that ensures Turnip is used instead of LTW
 * 
 * This file MUST be loaded early in the classloading process.
 * It uses static initialization to force Turnip before LTW can initialize.
 */
public class QuasarTurnipForce {
    
    // Static initializer - runs when class is loaded
    static {
        forceTurnip();
    }
    
    /**
     * Force Turnip to be used instead of LTW
     * 
     * This is called automatically when the class is loaded.
     * It sets all necessary properties to prevent LTW from being used.
     */
    public static void forceTurnip() {
        try {
            // Block LTW
            System.setProperty("quasar.ltw.enabled", "false");
            System.setProperty("quasar.ltw.disabled", "true");
            System.setProperty("quasar.ltw.blocked", "true");
            
            // Force Turnip
            System.setProperty("quasar.renderer", "turnip");
            System.setProperty("quasar.backend", "turnip");
            System.setProperty("quasar.force_turnip", "true");
            System.setProperty("quasar.vulkan", "true");
            System.setProperty("quasar.vulkan.enabled", "true");
            
            // Disable OpenGL
            System.setProperty("LIBGL_ES", "0");
            System.setProperty("LIBGL_GL", "0");
            
            // Enable shaders
            System.setProperty("iris.enableShaders", "true");
            System.setProperty("iris.shaders.enabled", "true");
            System.setProperty("iris.shaderpacks.enabled", "true");
            System.setProperty("iris.shaderpacks.complementary.enabled", "true");
            System.setProperty("iris.shaderpacks.astra.enabled", "true");
            
            // Initialize Turnip
            TurnipSetup.initAll();
            
            System.out.println("[QuasarTurnipForce] LTW BLOCKED, Turnip FORCED!");
            
        } catch (Exception e) {
            System.err.println("[QuasarTurnipForce] Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
