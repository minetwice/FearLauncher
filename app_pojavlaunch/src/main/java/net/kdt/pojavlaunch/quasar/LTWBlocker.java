package net.kdt.pojavlaunch.quasar;

/**
 * Blocks LTW from being used and forces Turnip instead
 * 
 * This class intercepts LTW initialization and replaces it with Turnip.
 */
public class LTWBlocker {
    
    private static boolean ltwBlocked = false;
    
    /**
     * Block LTW initialization
     * 
     * Call this BEFORE any LTW code runs.
     */
    public static void blockLTW() {
        if (ltwBlocked) return;
        ltwBlocked = true;
        
        // Set properties to block LTW
        System.setProperty("quasar.ltw.blocked", "true");
        System.setProperty("quasar.ltw.enabled", "false");
        System.setProperty("quasar.ltw.disabled", "true");
        
        // Force Turnip
        QuasarTurnipInitializer.initialize();
        
        System.out.println("[LTWBlocker] LTW blocked, Turnip forced");
    }
    
    /**
     * Check if LTW is blocked
     */
    public static boolean isLTWBlocked() {
        return ltwBlocked || 
               Boolean.getBoolean("quasar.ltw.blocked") ||
               Boolean.getBoolean("quasar.ltw.disabled");
    }
    
    /**
     * Force Turnip if LTW is detected
     */
    public static void forceTurnipIfLTW() {
        String backend = System.getProperty("quasar.backend", "");
        String renderer = System.getProperty("quasar.renderer", "");
        
        if (backend.contains("LTW") || renderer.contains("LTW")) {
            System.out.println("[LTWBlocker] LTW detected! Forcing Turnip...");
            blockLTW();
        }
    }
}
