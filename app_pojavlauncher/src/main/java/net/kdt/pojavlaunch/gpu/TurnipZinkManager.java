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
                    System.setProperty(parts[0], parts[1]);
                    Log.i(TAG, "Applied workaround: " + parts[0] + "=" + parts[1]);
                }
            }
        } else {
            Log.i(TAG, "No specific workarounds needed for GPU: " + gpuModel);
        }
        
        // Apply universal Zink optimizations
        applyUniversalWorkarounds();
    }
    
    /**
     * Apply universal Zink optimizations
     */
    private static void applyUniversalWorkarounds() {
        // Ensure Zink driver is used
        System.setProperty("MESA_LOADER_DRIVER_OVERRIDE", "zink");
        System.setProperty("GALLIUM_DRIVER", "zink");
        System.setProperty("MESA_GLSL_VERSION_OVERRIDE", "460");
        System.setProperty("MESA_GL_VERSION_OVERRIDE", "4.6");
        
        // Shader cache settings
        System.setProperty("MESA_SHADER_CACHE_DIR", 
            android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/Android/data/git.artdeell.mojo.debug/cache");
        System.setProperty("MESA_GLSL_CACHE_DIR", 
            android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/Android/data/git.artdeell.mojo.debug/cache");
        System.setProperty("MESA_GLSL_CACHE_DISABLE", "false");
        
        // Performance and compatibility
        System.setProperty("LIBGL_NOINTOVLHACK", "1");
        System.setProperty("LIBGL_NOERROR", "1");
        System.setProperty("LIBGL_NORMALIZE", "1");
        System.setProperty("LIBGL_MIPMAP", "3");
        
        // Turnip-Zink specific
        System.setProperty("POJAV_VSYNC_IN_ZINK", "1");
        System.setProperty("vblank_mode", "0");
        System.setProperty("FORCE_VSYNC", "true");
        System.setProperty("FEAR_RENDERER", "turnip_zink");
        
        Log.i(TAG, "Applied universal Zink workarounds");
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
