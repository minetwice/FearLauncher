package net.kdt.pojavlaunch.gpu;

import android.os.Build;
import android.util.Log;

import java.lang.reflect.Field;

/**
 * Manager for TurnipZink renderer configuration and workarounds.
 * This class provides GPU-specific workarounds for TurnipZink renderer,
 * particularly for GPUs that don't support all required Vulkan features.
 */
public class TurnipZinkManager {
    
    private static final String TAG = "TurnipZinkManager";
    private static boolean initialized = false;
    
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
     * Apply GPU-specific workarounds for Zink renderer.
     * This MUST be called early in the application lifecycle, before
     * any native libraries are loaded.
     */
    public static void applyGPUWorkarounds() {
        String gpuModel = getGpuModel();
        Log.i(TAG, "Detected GPU: " + gpuModel);

        // Get workaround environment variables for this GPU
        String[] workarounds = ZinkWorkarounds.getWorkaroundEnv(gpuModel);

        if (workarounds.length > 0) {
            Log.i(TAG, "Applying " + workarounds.length + " workarounds for GPU");
            for (String workaround : workarounds) {
                String[] parts = workaround.split("=", 2);
                if (parts.length == 2) {
                    // MC16: these must be REAL process environment variables -
                    // Mesa (native) never reads Java System properties, and the
                    // previous fake ZINK_* variables (ZINK_FEATURES,
                    // ZINK_EMULATE_LOGIC_OP, ...) are not read by Mesa at all.
                    try {
                        android.system.Os.setenv(parts[0], parts[1], true);
                        Log.i(TAG, "Applied workaround: " + parts[0] + "=" + parts[1]);
                    } catch (Throwable t) {
                        Log.w(TAG, "setenv failed for " + parts[0], t);
                    }
                }
            }
        } else {
            Log.i(TAG, "No specific workarounds needed for GPU: " + gpuModel);
        }
        // NOTE (MC16): the old applyUniversalWorkarounds() only set Java System
        // properties, which the native Mesa stack never reads - it was inert.
        // The real launch-time environment is set by JREUtils.setEnviroimentForGame().
    }
    
    /**
     * Initialize TurnipZink manager. This should be called early in
     * Application.onCreate() before any other initialization.
     */
    public static void initialize() {
        if (initialized) {
            Log.w(TAG, "TurnipZinkManager already initialized");
            return;
        }
        
        Log.i(TAG, "Initializing TurnipZink Manager");
        applyGPUWorkarounds();
        initialized = true;
        
        // Check if GPU needs workarounds
        String gpuModel = getGpuModel();
        if (ZinkWorkarounds.needsWorkarounds(gpuModel)) {
            String warning = ZinkWorkarounds.getWarningMessage(gpuModel);
            if (warning != null) {
                Log.w(TAG, warning);
            }
        }
    }
    
    /**
     * Check if TurnipZink workarounds have been applied.
     */
    public static boolean isInitialized() {
        return initialized;
    }
}
