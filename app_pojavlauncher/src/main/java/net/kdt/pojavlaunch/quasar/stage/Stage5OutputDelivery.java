package net.kdt.pojavlaunch.quasar.stage;

import android.util.Log;

import net.kdt.pojavlaunch.quasar.capability.CapabilityTable;
import net.kdt.pojavlaunch.quasar.transpile.ShaderCache;

/**
 * Stage 5: Final GPU Output Delivery & Shader Caching Net.
 *
 * Manages cache lookup, stores refined shaders into disk cache,
 * and delivers the final optimized GLSL shader to the GPU driver/Zink/GL4ES.
 */
public class Stage5OutputDelivery {
    private static final String TAG = "Quasar-Stage5Delivery";

    public static String deliver(String originalSource,
                                 Stage1FastDetector.DetectionResult detection,
                                 CapabilityTable capability,
                                 String refinedSource,
                                 ShaderCache shaderCache,
                                 String shaderpackHash) {
        long startTime = System.nanoTime();

        if (shaderCache != null && shaderpackHash != null) {
            String profileKey = capability != null ? capability.getProfileKey() : "default";
            String cacheKey = shaderCache.computeKey(shaderpackHash, profileKey, detection.shaderName, originalSource);

            if (shaderCache.contains(cacheKey)) {
                String cached = shaderCache.get(cacheKey);
                if (cached != null && !cached.isEmpty()) {
                    long elapsedUs = (System.nanoTime() - startTime) / 1000;
                    Log.i(TAG, "[Stage 5] Cache HIT for shader: " + detection.shaderName + " (" + elapsedUs + " µs)");
                    return cached;
                }
            }

            // Put in cache
            shaderCache.put(cacheKey, refinedSource);
            Log.d(TAG, "[Stage 5] Cached refined shader: " + detection.shaderName + " with key: " + cacheKey);
        }

        long elapsedUs = (System.nanoTime() - startTime) / 1000;
        Log.i(TAG, "[Stage 5] Delivered shader " + detection.shaderName + " to GPU in " + elapsedUs + " µs");
        return refinedSource;
    }
}
