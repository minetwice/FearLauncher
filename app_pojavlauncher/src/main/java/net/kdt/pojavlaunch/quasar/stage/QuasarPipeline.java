package net.kdt.pojavlaunch.quasar.stage;

import android.content.Context;
import android.util.Log;

import net.kdt.pojavlaunch.quasar.capability.CapabilityTable;
import net.kdt.pojavlaunch.quasar.transpile.ShaderCache;

/**
 * QuasarPipeline orchestrates the 5-Stage Shader Translator Net:
 *
 * Stage 1: Fast Shader Detector (Identifies triggered shader e.g., deferred1.vsh, stage type, compute/gbuffer/deferred).
 * Stage 2: GPU Detector & Capability Inspection (Shows Mali vs Adreno GPU, Vulkan API, and GLES feature matrix).
 * Stage 3: Desktop GLSL -> GLES/Vulkan Transpiler Net (Strips GL_NV_shader_noperspective_interpolation, noperspective keywords, texture/MRT conversion).
 * Stage 4: GPU Refining Net (Mali/Adreno color & precision highp stabilization, compute invocation fixes).
 * Stage 5: Final Output Delivery & Shader Cache Net (Caches & delivers refined GLSL to GPU).
 */
public class QuasarPipeline {
    private static final String TAG = "Quasar-Pipeline";

    private final Stage2GpuDetector gpuDetector;
    private final ShaderCache shaderCache;
    private String shaderpackHash = "default_shaderpack";

    public QuasarPipeline(Context context) {
        this.gpuDetector = new Stage2GpuDetector(context);
        this.shaderCache = new ShaderCache(context);
    }

    public QuasarPipeline(CapabilityTable capabilityTable, ShaderCache shaderCache) {
        this.gpuDetector = new Stage2GpuDetector(capabilityTable);
        this.shaderCache = shaderCache;
    }

    public void setShaderpackHash(String hash) {
        if (hash != null && !hash.isEmpty()) {
            this.shaderpackHash = hash;
        }
    }

    /**
     * Process a shader source through all 5 stages of the Quasar Net.
     *
     * @param originalSource The original desktop GLSL source code
     * @param shaderStage The GL shader stage (0=Vertex, 4=Fragment, 5=Compute, etc.)
     * @param shaderName The file name or identifier (e.g., "deferred1.vsh")
     * @return The 5-stage refined GLSL source ready for Mali/Adreno GPUs
     */
    public String processShader(String originalSource, int shaderStage, String shaderName) {
        if (originalSource == null || originalSource.isEmpty()) {
            return originalSource;
        }

        long startTime = System.nanoTime();

        // Stage 1: Fast Detector
        Stage1FastDetector.DetectionResult detection = Stage1FastDetector.detect(shaderName, shaderStage, originalSource);

        CapabilityTable capability = gpuDetector.getCapabilityTable();

        // Fast Cache Check before full 5-stage translation
        if (shaderCache != null) {
            String profileKey = capability != null ? capability.getProfileKey() : "default";
            String cacheKey = shaderCache.computeKey(shaderpackHash, profileKey, detection.shaderName, originalSource);
            if (shaderCache.contains(cacheKey)) {
                String cached = shaderCache.get(cacheKey);
                if (cached != null && !cached.isEmpty()) {
                    Log.d(TAG, "[Quasar Pipeline] FAST CACHE HIT for " + shaderName);
                    return cached;
                }
            }
        }

        // Stage 2: GPU Capability (already probed & logged by gpuDetector)

        // Stage 3: OpenGL -> GLES/Vulkan Translation Net
        String translatedSource = Stage3ShaderTranslator.translate(originalSource, detection, capability);

        // Stage 4: GPU Refinement Net (Mali/Adreno precision & color fix)
        String refinedSource = Stage4ShaderRefiner.refine(translatedSource, detection, capability);

        // Stage 5: Final Output Delivery & Caching Net
        String finalSource = Stage5OutputDelivery.deliver(originalSource, detection, capability, refinedSource, shaderCache, shaderpackHash);

        long totalElapsedUs = (System.nanoTime() - startTime) / 1000;
        Log.i(TAG, "[Quasar Pipeline] 5-Stage Net processed " + shaderName + " (" + detection.stageName + ") in " + totalElapsedUs + " µs");

        return finalSource;
    }

    public Stage2GpuDetector getGpuDetector() {
        return gpuDetector;
    }
}
