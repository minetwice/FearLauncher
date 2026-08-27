package net.kdt.pojavlaunch.quasar;

/**
 * Entry point for Quasar with Turnip integration
 * 
 * This class should be loaded as early as possible to ensure
 * Turnip is used instead of LTW.
 */
public class QuasarEntryPoint {
    
    static {
        // Force Turnip before anything else
        QuasarTurnipForce.forceTurnip();
    }
    
    /**
     * Initialize Quasar with Turnip
     * 
     * Call this at the start of your application.
     */
    public static void init() {
        // Already initialized in static block
        QuasarTurnipForce.forceTurnip();
    }
}
