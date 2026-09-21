package net.kdt.pojavlaunch.gpu;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides workarounds for GPUs with missing Vulkan features required by Zink.
 * 
 * Zink requires certain Vulkan features for proper OpenGL emulation:
 * - logicOp
 * - fillModeNonSolid
 * - shaderClipDistance
 * 
 * Some GPUs (like Mali-G615) don't support all of these features, causing
 * rendering artifacts and texture glitches. This class provides software
 * fallbacks through environment variables.
 */
public class ZinkWorkarounds {
    
    /**
     * GPU patterns and their required workarounds.
     * Format: [gpu_pattern_1, gpu_pattern_2, ..., env_var_1, env_var_2, ...]
     */
    private static final String[][] GPU_WORKAROUNDS = {
        {
            // Mali GPUs run Zink on the ARM proprietary system Vulkan driver, which is
            // non-conformant for Zink (no fillModeNonSolid / shaderClipDistance / logicOp)
            // and mishandles out-of-order command submission -> flickering blocks/chunks.
            // REAL Mesa environment variables (verified against the bundled
            // Mesa 25.1.4 OSMesa/Zink build):
            //   ZINK_DEBUG=noreorder  - do not reorder command streams
            //   GALLIUM_THREAD=0      - disable gallium threaded context
            //   mesa_glthread=false    - disable Mesa GL thread
            "mali-g615", "mali g615", "mali", "arm mali",
            "ZINK_DEBUG=noreorder",
            "GALLIUM_THREAD=0",
            "mesa_glthread=false"
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
     * Check if the GPU needs Zink workarounds.
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
            return "Your Mali-G615 GPU does not fully support all Vulkan features required by Zink. " +
                   "Software fallbacks have been enabled, but you may experience reduced performance or " +
                   "some rendering artifacts. For best results, consider using a different renderer.";
        }
        
        if (gpuLower.contains("mali")) {
            return "Your Mali GPU may not fully support all Vulkan features required by Zink. " +
                   "Software fallbacks have been enabled for better compatibility.";
        }
        
        return "Your GPU may need additional configuration for optimal Zink performance.";
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
    
    /**
     * Check if a specific feature is missing from the GPU's Vulkan support.
     * This can be used to detect if workarounds are needed.
     * 
     * @param missingFeatures Comma-separated list of missing features from Zink
     * @return true if critical features are missing
     */
    public static boolean hasCriticalMissingFeatures(String missingFeatures) {
        if (missingFeatures == null || missingFeatures.isEmpty()) {
            return false;
        }
        
        String[] criticalFeatures = {
            "logicOp",
            "fillModeNonSolid", 
            "shaderClipDistance"
        };
        
        String lowerFeatures = missingFeatures.toLowerCase();
        
        for (String feature : criticalFeatures) {
            if (lowerFeatures.contains(feature.toLowerCase())) {
                return true;
            }
        }
        
        return false;
    }
}