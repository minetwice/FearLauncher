package net.kdt.pojavlaunch.gpu;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides workarounds for Panfork renderer on Mali GPUs.
 * Panfork is an open-source Panfrost-based OpenGL implementation for Mali GPUs.
 * This class provides GPU-specific workarounds and fallback mechanisms.
 */
public class PanforkWorkarounds {
    
    /**
     * GPU patterns and their required workarounds for Panfork.
     * Format: [gpu_pattern_1, gpu_pattern_2, ..., env_var_1, env_var_2, ...]
     */
    private static final String[][] GPU_WORKAROUNDS = {
        {
            // Mali-G615: Panfork may fail to initialize OpenGL context
            // Workarounds: Enable debug logging and disable unsupported features
            "mali-g615", "mali g615", "mali", "arm mali",
            "PAN_MESA_DEBUG=gl3,noafbc",
            "MESA_GLSL_CACHE_DISABLE=false",
            "allow_glsl_extension_directive_midshader=true",
            "allow_higher_compat_version=true",
            "LIBGL_NORMALIZE=1",
            "LIBGL_NOERROR=1"
        }
    };
    
    /**
     * Get workaround environment variables for a specific GPU model.
     * 
     * @param gpuModel The GPU model string (case-insensitive)
     * @return Array of environment variables to add, or empty array if no workarounds needed
     */
    public static String[] getWorkaroundEnv(String gpuModel) {
        if (gpuModel == null || gpuModel.isEmpty()) {
            return new String[0];
        }
        
        String gpuLower = gpuModel.toLowerCase().trim();
        
        for (String[] workaround : GPU_WORKAROUNDS) {
            // Elements after the first KEY=VALUE string are env vars; those before are GPU patterns.
            int firstEnv = -1;
            for (int i = 0; i < workaround.length; i++) {
                if (workaround[i].indexOf('=') >= 0) { firstEnv = i; break; }
            }
            if (firstEnv <= 0) continue;
            
            for (int i = 0; i < firstEnv; i++) {
                if (gpuLower.contains(workaround[i].toLowerCase())) {
                    String[] envVars = new String[workaround.length - firstEnv];
                    System.arraycopy(workaround, firstEnv, envVars, 0, envVars.length);
                    return envVars;
                }
            }
        }
        
        return new String[0];
    }
    
    /**
     * Check if the GPU needs Panfork workarounds.
     * 
     * @param gpuModel The GPU model string
     * @return true if workarounds are available for this GPU
     */
    public static boolean needsWorkarounds(String gpuModel) {
        return getWorkaroundEnv(gpuModel).length > 0;
    }
    
    /**
     * Get a user-friendly warning message for GPUs that need workarounds.
     * 
     * @param gpuModel The GPU model string
     * @return Warning message, or null if no workarounds needed
     */
    public static String getWarningMessage(String gpuModel) {
        if (gpuModel == null || !needsWorkarounds(gpuModel)) {
            return null;
        }
        
        String gpuLower = gpuModel.toLowerCase();
        
        if (gpuLower.contains("mali-g615") || gpuLower.contains("mali g615")) {
            return "Your Mali-G615 GPU may not fully support Panfork. " +
                   "Software fallbacks have been enabled, but you may experience reduced performance or " +
                   "some rendering artifacts. For best results, consider using a different renderer.";
        }
        
        if (gpuLower.contains("mali")) {
            return "Your Mali GPU may not fully support Panfork. " +
                   "Software fallbacks have been enabled for better compatibility.";
        }
        
        return "Your GPU may need additional configuration for optimal Panfork performance.";
    }
    
    /**
     * Merge workaround environment variables with existing ones.
     * Avoids duplicates by checking variable names.
     * 
     * @param existingEnv Existing environment variables (can be null)
     * @param gpuModel The GPU model string
     * @return Combined environment variables, or existingEnv if no workarounds needed
     */
    public static String[] mergeEnvVars(String[] existingEnv, String gpuModel) {
        String[] workarounds = getWorkaroundEnv(gpuModel);
        if (workarounds.length == 0) {
            return existingEnv != null ? existingEnv : new String[0];
        }
        
        List<String> merged = new ArrayList<>();
        
        // Add existing environment variables
        if (existingEnv != null) {
            for (String env : existingEnv) {
                if (env != null && !env.isEmpty()) {
                    merged.add(env);
                }
            }
        }
        
        // Add workarounds, avoiding duplicates
        for (String workaround : workarounds) {
            if (workaround == null || workaround.isEmpty()) {
                continue;
            }
            
            String key = workaround.split("=")[0];
            boolean found = false;
            
            for (String env : merged) {
                if (env.startsWith(key + "=")) {
                    found = true;
                    break;
                }
            }
            
            if (!found) {
                merged.add(workaround);
            }
        }
        
        return merged.toArray(new String[0]);
    }
}
