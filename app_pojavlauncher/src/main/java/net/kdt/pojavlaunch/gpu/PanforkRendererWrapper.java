package net.kdt.pojavlaunch.gpu;

import android.util.Log;

/**
 * Wrapper class for Panfork renderer integration.
 * This class provides a unified interface for Panfork renderer initialization
 * and fallback handling.
 */
public class PanforkRendererWrapper {
    
    private static final String TAG = "PanforkRendererWrapper";
    
    // Panfork renderer state
    private static boolean panforkEnabled = false;
    private static boolean fallbackEnabled = false;
    
    /**
     * Initialize Panfork renderer with fallback support.
     * This method attempts to initialize Panfork and falls back to Zink if it fails.
     */
    public static void initializeRenderer() {
        Log.i(TAG, "Initializing Panfork renderer with fallback support");
        
        // Initialize Panfork manager
        PanforkManager.initialize();
        
        // Check if Panfork is available
        if (PanforkManager.isPanforkUsable()) {
            Log.i(TAG, "Panfork is available, attempting to initialize");
            panforkEnabled = true;
            
            // Apply Panfork-specific workarounds
            PanforkManager.applyGPUWorkarounds();
            
            // Validate Panfork initialization
            if (!validatePanforkInitialization()) {
                Log.w(TAG, "Panfork initialization failed, falling back to Zink");
                panforkEnabled = false;
                fallbackEnabled = true;
                
                // Apply Zink workarounds as fallback
                TurnipZinkManager.initialize();
                TurnipZinkManager.applyGPUWorkarounds();
            }
        } else {
            Log.w(TAG, "Panfork is not available, falling back to Zink");
            panforkEnabled = false;
            fallbackEnabled = true;
            
            // Apply Zink workarounds as fallback
            TurnipZinkManager.initialize();
            TurnipZinkManager.applyGPUWorkarounds();
        }
    }
    
    /**
     * Validate Panfork initialization.
     * This checks if Panfork can be used for rendering.
     */
    private static boolean validatePanforkInitialization() {
        try {
            // Check if Panfork is explicitly enabled
            String renderer = System.getenv("FEAR_RENDERER");
            if (renderer != null && renderer.equalsIgnoreCase("panfork")) {
                Log.i(TAG, "Panfork explicitly enabled via FEAR_RENDERER");
                return true;
            }
            
            // Check for Panfork-specific environment variables
            String mesaLoaderDriver = System.getenv("MESA_LOADER_DRIVER_OVERRIDE");
            String galliumDriver = System.getenv("GALLIUM_DRIVER");
            
            if ((mesaLoaderDriver != null && mesaLoaderDriver.equalsIgnoreCase("panfrost")) ||
                (galliumDriver != null && galliumDriver.equalsIgnoreCase("panfrost"))) {
                Log.i(TAG, "Panfork environment variables detected");
                return true;
            }
            
            // Check GPU compatibility
            String gpuModel = PanforkManager.getGpuModel();
            if (gpuModel.toLowerCase().contains("mali")) {
                Log.i(TAG, "Mali GPU detected, Panfork may be usable");
                return true;
            }
            
            Log.w(TAG, "Panfork validation failed");
            return false;
            
        } catch (Throwable t) {
            Log.e(TAG, "Panfork validation error", t);
            return false;
        }
    }
    
    /**
     * Check if Panfork renderer is enabled.
     */
    public static boolean isPanforkEnabled() {
        return panforkEnabled;
    }
    
    /**
     * Check if fallback renderer is enabled.
     */
    public static boolean isFallbackEnabled() {
        return fallbackEnabled;
    }
    
    /**
     * Get the current renderer name.
     */
    public static String getRendererName() {
        if (panforkEnabled) {
            return "panfork";
        } else if (fallbackEnabled) {
            return "zink";
        } else {
            return "unknown";
        }
    }
    
    /**
     * Reset renderer state.
     */
    public static void resetRendererState() {
        panforkEnabled = false;
        fallbackEnabled = false;
    }
    
    /**
     * Force fallback to Zink renderer.
     */
    public static void forceFallbackToZink() {
        panforkEnabled = false;
        fallbackEnabled = true;
        Log.i(TAG, "Forced fallback to Zink renderer");
    }
}
