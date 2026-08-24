package net.kdt.pojavlaunch.quasar.stage;

import android.util.Log;

/**
 * Stage 1: Fast Shader & Trigger Detector.
 *
 * Instantly detects which shader file was triggered (e.g., deferred1.vsh, gbuffers_terrain.fsh),
 * determines the shader stage (Vertex, Fragment, Compute, Geometry), and extracts metadata.
 */
public class Stage1FastDetector {
    private static final String TAG = "Quasar-Stage1Detector";

    public static class DetectionResult {
        public final String shaderName;
        public final int shaderStage; // 0=Vertex, 1=Geometry, 4=Fragment, 5=Compute
        public final String stageName;
        public final boolean isCompute;
        public final boolean isDeferred;
        public final boolean isGBuffer;

        public DetectionResult(String shaderName, int shaderStage) {
            this.shaderName = shaderName != null ? shaderName : "unknown_shader";
            this.shaderStage = shaderStage;
            this.isCompute = shaderStage == 5 || this.shaderName.contains("composite") || this.shaderName.contains("compute");
            this.isDeferred = this.shaderName.contains("deferred");
            this.isGBuffer = this.shaderName.contains("gbuffer");

            switch (shaderStage) {
                case 0: this.stageName = "VERTEX"; break;
                case 1: this.stageName = "GEOMETRY"; break;
                case 4: this.stageName = "FRAGMENT"; break;
                case 5: this.stageName = "COMPUTE"; break;
                default: this.stageName = "UNKNOWN"; break;
            }
        }

        @Override
        public String toString() {
            return "DetectionResult{name='" + shaderName + '\'' +
                    ", stage=" + stageName +
                    ", deferred=" + isDeferred +
                    ", gbuffer=" + isGBuffer + '}';
        }
    }

    public static DetectionResult detect(String shaderName, int shaderStage, String sourceCode) {
        long startTime = System.nanoTime();

        int finalStage = shaderStage;
        if (sourceCode != null) {
            if (sourceCode.contains("layout(local_size_") || sourceCode.contains("gl_GlobalInvocationID")) {
                finalStage = 5; // Compute
            }
        }

        DetectionResult result = new DetectionResult(shaderName, finalStage);
        long elapsedUs = (System.nanoTime() - startTime) / 1000;
        Log.d(TAG, "[Stage 1] Triggered shader detected in " + elapsedUs + " µs: " + result);
        return result;
    }
}
