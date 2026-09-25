package net.kdt.pojavlaunch.gpu;

import android.os.Build;
import android.util.Log;

import java.lang.reflect.Field;

/**
 * Manager for Panfork renderer configuration and workarounds.
 * Panfork is an open-source Panfrost-based OpenGL implementation for Mali GPUs.
 * This class provides GPU-specific workarounds and fallback mechanisms for Panfork.
 */
public class PanforkManager {
    
    private static final String TAG = "PanforkManager";
    private static boolean initialized = false;
    private static boolean panforkAvailable = false;
    private static boolean fallbackToZink = false;
    
    // GPU detection
    private static String cachedGpuModel = null;
    
    /**
     * Get the current GPU model
     */
    public static String getGpuModel() {
        if (cachedGpuModel != null) {
            return cachedGpuModel;
        }
        
        // Try different methods to get GPU model
        String gpu = System.getProperty("ro.product.gpu", "");
        if (!gpu.isEmpty()) {
            cachedGpuModel = gpu;
            return gpu;
        }
        
        gpu = System.getProperty("ro.board.platform", "");
        if (!gpu.isEmpty()) {
            cachedGpuModel = gpu;
            return gpu;
        }
        
        // Try Build class
        try {
            Field field = Build.class.getDeclaredField("GPU");
            if (field != null) {
                field.setAccessible(true);
                cachedGpuModel = (String) field.get(null);
                return cachedGpuModel;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get GPU from Build.GPU", e);
        }
        
        // Fallback: check for Mali-G615 in device model
        String deviceModel = Build.MODEL != null ? Build.MODEL.toLowerCase() : "";
        if (deviceModel.contains("motorola") && deviceModel.contains("edge 60 fusion")) {
            cachedGpuModel = "Mali-G615";
            return cachedGpuModel;
        }
        
        cachedGpuModel = "";
        return cachedGpuModel;
    }
    
    /**
     * Check if Panfork is available and can be used for rendering.
     * This checks for the presence of Panfork-specific environment variables
     * and validates the GPU compatibility.
     */
    public static boolean isPanforkAvailable() {
        // Check if Panfork is explicitly enabled via environment variable
        String renderer = System.getenv("FEAR_RENDERER");
        if (renderer != null && renderer.equalsIgnoreCase("panfork")) {
            panforkAvailable = true;
            return true;
        }
        
        // Check for Panfork-specific environment variables
        String mesaLoaderDriver = System.getenv("MESA_LOADER_DRIVER_OVERRIDE");
        String galliumDriver = System.getenv("GALLIUM_DRIVER");
        
        if ((mesaLoaderDriver != null && mesaLoaderDriver.equalsIgnoreCase("panfrost")) ||
            (galliumDriver != null && galliumDriver.equalsIgnoreCase("panfrost"))) {
            panforkAvailable = true;
            return true;
        }
        
        // Check GPU compatibility
        String gpuModel = getGpuModel();
        if (gpuModel.toLowerCase().contains("mali")) {
            panforkAvailable = true;
            return true;
        }
        
        panforkAvailable = false;
        return false;
    }
    
    /**
     * Apply GPU-specific workarounds for Panfork renderer.
     * This MUST be called early in the application lifecycle, before
     * any native libraries are loaded.
     */
    public static void applyGPUWorkarounds() {
        String gpuModel = getGpuModel();
        Log.i(TAG, "Detected GPU: " + gpuModel);
        
        // Apply Panfork-specific environment variables
        String[] panforkEnv = {
            "GALLIUM_DRIVER=panfrost",
            "MESA_LOADER_DRIVER_OVERRIDE=panfrost",
            "LIBGL_NORMALIZE=1",
            "LIBGL_NOERROR=1",
            "PAN_MESA_DEBUG=gl3,noafbc"
        };
        
        for (String env : panforkEnv) {
            String[] parts = env.split("=", 2);
            if (parts.length == 2) {
                try {
                    android.system.Os.setenv(parts[0], parts[1], true);
                    Log.i(TAG, "Applied Panfork workaround: " + parts[0] + "=" + parts[1]);
                } catch (Throwable t) {
                    Log.w(TAG, "setenv failed for " + parts[0], t);
                }
            }
        }
        
        // Apply additional workarounds for Mali GPUs
        if (gpuModel.toLowerCase().contains("mali")) {
            String[] maliWorkarounds = {
                "MESA_GLSL_CACHE_DIR=/data/user/0/git.artdeell.mojo.debug/cache",
                "MESA_GLSL_CACHE_DISABLE=false",
                "allow_glsl_extension_directive_midshader=true",
                "allow_higher_compat_version=true"
            };
            
            for (String workaround : maliWorkarounds) {
                String[] parts = workaround.split("=", 2);
                if (parts.length == 2) {
                    try {
                        android.system.Os.setenv(parts[0], parts[1], true);
                        Log.i(TAG, "Applied Mali workaround: " + parts[0] + "=" + parts[1]);
                    } catch (Throwable t) {
                        Log.w(TAG, "setenv failed for " + parts[0], t);
                    }
                }
            }
        }
    }
    
    /**
     * Initialize Panfork manager. This should be called early in
     * Application.onCreate() before any other initialization.
     */
    public static void initialize() {
        if (initialized) {
            Log.w(TAG, "PanforkManager already initialized");
            return;
        }
        
        Log.i(TAG, "Initializing Panfork Manager");
        applyGPUWorkarounds();
        initialized = true;
        panforkAvailable = isPanforkAvailable();
        
        if (!panforkAvailable) {
            Log.w(TAG, "Panfork is not available, will fall back to Zink or other renderer");
            fallbackToZink = true;
        }
    }
    
    /**
     * Check if Panfork workarounds have been applied.
     */
    public static boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Check if Panfork is available for rendering.
     */
    public static boolean isPanforkUsable() {
        return panforkAvailable && !fallbackToZink;
    }
    
    /**
     * Check if fallback to Zink is required.
     */
    public static boolean shouldFallbackToZink() {
        return fallbackToZink;
    }
    
    /**
     * Set fallback to Zink if Panfork fails to initialize.
     */
    public static void setFallbackToZink(boolean fallback) {
        fallbackToZink = fallback;
        if (fallback) {
            Log.w(TAG, "Falling back to Zink renderer due to Panfork initialization failure");
        }
    }
    
    /**
     * Get the current renderer to use (Panfork or fallback).
     */
    public static String getRenderer() {
        if (isPanforkUsable()) {
            return "panfork";
        } else {
            return "zink";
        }
    }
}