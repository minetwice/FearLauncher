package net.kdt.pojavlaunch.gpu;

import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Manager for TurnipZink renderer configuration and workarounds.
 * 
 * This class provides GPU-specific workarounds for TurnipZink renderer,
 * particularly for GPUs that don't support all required Vulkan features.
 */
public class TurnipZinkManager {
    
    private static final String TAG = "TurnipZinkManager";
    
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
            java.lang.reflect.Field field = Build.class.getDeclaredField("GPU");
            if (field != null) {
                field.setAccessible(true);
                cachedGpuModel = (String) field.get(null);
                return cachedGpuModel;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get GPU from Build.GPU", e);
        }
        
        cachedGpuModel = "";
        return cachedGpuModel;
    }
    
    /**
     * Check if TurnipZink is the selected renderer
     */
    public static boolean isTurnipZinkSelected() {
        String renderer = System.getenv("FEAR_RENDERER");
        if (renderer != null && renderer.toLowerCase().contains("turnip")) {
            return true;
        }
        
        // Also check from properties
        String fearRenderer = System.getProperty("FEAR_RENDERER", "");
        return fearRenderer.toLowerCase().contains("turnip");
    }
    
    /**
     * Get additional environment variables for TurnipZink based on GPU
     * 
     * @return Array of environment variables to add
     */
    public static String[] getTurnipZinkEnvVars() {
        List<String> envVars = new ArrayList<>();
        
        String gpuModel = getGpuModel();
        
        // Add GPU-specific workarounds
        String[] gpuWorkarounds = ZinkWorkarounds.getWorkaroundEnv(gpuModel);
        for (String workaround : gpuWorkarounds) {
            envVars.add(workaround);
            Log.i(TAG, "Added GPU workaround: " + workaround);
        }
        
        // Add general TurnipZink optimizations
        envVars.add("MESA_LOADER_DRIVER_OVERRIDE=zink");
        envVars.add("GALLIUM_DRIVER=zink");
        
        // Log all added variables
        for (String env : envVars) {
            Log.i(TAG, "Added custom env: " + env);
        }
        
        return envVars.toArray(new String[0]);
    }
    
    /**
     * Apply TurnipZink environment variables to the current process
     */
    public static void applyTurnipZinkEnvVars() {
        if (!isTurnipZinkSelected()) {
            Log.d(TAG, "TurnipZink not selected, skipping env var setup");
            return;
        }
        
        String[] envVars = getTurnipZinkEnvVars();
        
        for (String env : envVars) {
            String[] parts = env.split("=", 2);
            if (parts.length == 2) {
                System.setProperty(parts[0], parts[1]);
                // Note: System.setProperty doesn't affect native code,
                // but it's good for Java-side reference
            }
        }
        
        // Check for Mali-G615 and log warning
        String gpuModel = getGpuModel();
        String warning = ZinkWorkarounds.getWarningMessage(gpuModel);
        if (warning != null) {
            Log.w(TAG, warning);
        }
    }
    
    /**
     * Initialize TurnipZink with GPU-specific workarounds
     * This should be called early in the launch process
     */
    public static void initialize() {
        Log.i(TAG, "Initializing TurnipZink Manager");
        
        String gpuModel = getGpuModel();
        Log.i(TAG, "Detected GPU: " + gpuModel);
        
        applyTurnipZinkEnvVars();
        
        if (ZinkWorkarounds.needsWorkarounds(gpuModel)) {
            Log.i(TAG, "GPU requires Zink workarounds");
        }
    }
}