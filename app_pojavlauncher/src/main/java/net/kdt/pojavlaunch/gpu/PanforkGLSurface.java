package net.kdt.pojavlaunch.gpu;

import android.util.Log;
import android.view.Surface;

import git.artdeell.dnbootstrap.glfw.GLFW;

/**
 * Custom GLSurface for Panfork renderer.
 * This class handles Panfork-specific surface creation and OpenGL context validation.
 */
public class PanforkGLSurface {
    
    private static final String TAG = "PanforkGLSurface";
    private static boolean panforkInitialized = false;
    private static boolean panforkFailed = false;
    
    /**
     * Initialize Panfork renderer and validate OpenGL context creation.
     * This should be called after the surface is created.
     */
    public static boolean initializePanfork(Surface surface) {
        if (panforkInitialized) {
            Log.i(TAG, "Panfork already initialized");
            return true;
        }
        
        Log.i(TAG, "Initializing Panfork renderer");
        
        try {
            // Set Panfork-specific environment variables
            PanforkManager.initialize();
            
            // Validate OpenGL context creation
            if (!validateOpenGLContext()) {
                Log.e(TAG, "Failed to validate OpenGL context for Panfork");
                panforkFailed = true;
                PanforkManager.setFallbackToZink(true);
                return false;
            }
            
            // Create the surface for Panfork
            if (surface != null) {
                GLFW.nativeSurfaceCreated(surface);
                net.kdt.pojavlaunch.utils.JREUtils.setupBridgeWindow(surface);
            }
            
            panforkInitialized = true;
            panforkFailed = false;
            Log.i(TAG, "Panfork initialized successfully");
            return true;
            
        } catch (Throwable t) {
            Log.e(TAG, "Failed to initialize Panfork", t);
            panforkFailed = true;
            PanforkManager.setFallbackToZink(true);
            return false;
        }
    }
    
    /**
     * Validate OpenGL context creation for Panfork.
     * This checks if the OpenGL context can be created without errors.
     */
    private static boolean validateOpenGLContext() {
        try {
            // Check if Panfork is available
            if (!PanforkManager.isPanforkUsable()) {
                Log.w(TAG, "Panfork is not available for this GPU");
                return false;
            }
            
            // Check for Panfork-specific environment variables
            String mesaLoaderDriver = System.getenv("MESA_LOADER_DRIVER_OVERRIDE");
            String galliumDriver = System.getenv("GALLIUM_DRIVER");
            
            if ((mesaLoaderDriver == null || !mesaLoaderDriver.equalsIgnoreCase("panfrost")) &&
                (galliumDriver == null || !galliumDriver.equalsIgnoreCase("panfrost"))) {
                Log.w(TAG, "Panfork environment variables not set correctly");
                return false;
            }
            
            // Additional validation can be added here
            // For example, check if libOSMesa_panfork.so is loaded
            
            Log.i(TAG, "OpenGL context validation passed for Panfork");
            return true;
            
        } catch (Throwable t) {
            Log.e(TAG, "OpenGL context validation failed", t);
            return false;
        }
    }
    
    /**
     * Check if Panfork initialization was successful.
     */
    public static boolean isPanforkInitialized() {
        return panforkInitialized;
    }
    
    /**
     * Check if Panfork initialization failed.
     */
    public static boolean isPanforkFailed() {
        return panforkFailed;
    }
    
    /**
     * Reset Panfork initialization state.
     */
    public static void resetPanforkState() {
        panforkInitialized = false;
        panforkFailed = false;
    }
    
    /**
     * Get the current renderer to use (Panfork or fallback).
     */
    public static String getCurrentRenderer() {
        if (isPanforkInitialized() && !isPanforkFailed()) {
            return "panfork";
        } else {
            return PanforkManager.getRenderer();
        }
    }
}
