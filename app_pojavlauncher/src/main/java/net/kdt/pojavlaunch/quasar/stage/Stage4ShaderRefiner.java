package net.kdt.pojavlaunch.quasar.stage;

import android.util.Log;

import net.kdt.pojavlaunch.quasar.capability.CapabilityTable;

/**
 * Stage 4: GPU Refinement Net (Precision & Color Stability for Mali/Adreno GPUs).
 *
 * Refines shaders specifically for Mali (ARM) and Adreno (Qualcomm) GPUs:
 * - Injects highp precision defaults for floats, ints, samplers, and images to prevent rendering artifacts/black screens on Mali GPUs.
 * - Fixes compute shader invocation coordinates (gl_FragCoord -> gl_GlobalInvocationID).
 * - Ensures local workgroup size layouts for compute passes.
 * - Injects mobile definitions (#define MC_ANDROID, #define QUASAR_MOBILE, etc.).
 */
public class Stage4ShaderRefiner {
    private static final String TAG = "Quasar-Stage4Refiner";

    public static String refine(String sourceCode, Stage1FastDetector.DetectionResult detection, CapabilityTable capability) {
        if (sourceCode == null || sourceCode.isEmpty()) {
            return sourceCode;
        }

        Log.d(TAG, "[Stage 4] Refining shader for GPU stability: " + detection.shaderName);

        String glsl = sourceCode;
        String vendor = capability != null ? capability.getGpuVendor().toLowerCase() : "unknown";

        // 1. Mobile GPU Macros
        StringBuilder macros = new StringBuilder();
        macros.append("\n#define MC_ANDROID 1\n#define QUASAR_MOBILE 1\n");
        if (vendor.contains("mali")) {
            macros.append("#define MC_GL_VENDOR_MALI 1\n");
        } else if (vendor.contains("adreno")) {
            macros.append("#define MC_GL_VENDOR_ADRENO 1\n");
        }

        int versionIndex = glsl.indexOf("#version");
        if (versionIndex != -1) {
            int lineEnd = glsl.indexOf("\n", versionIndex);
            if (lineEnd != -1) {
                glsl = glsl.substring(0, lineEnd + 1) + macros.toString() + glsl.substring(lineEnd + 1);
            }
        }

        // 2. Precision Qualifier Injection for Mali & Adreno Color Stability
        if (!glsl.contains("precision ")) {
            String precisionBlock = "precision highp float;\nprecision highp int;\n" +
                    "precision highp sampler2D;\nprecision highp sampler2DArray;\n" +
                    "precision highp sampler3D;\nprecision highp samplerCube;\n" +
                    "precision highp sampler2DShadow;\nprecision highp sampler2DArrayShadow;\n";
            if (detection.isCompute) {
                precisionBlock += "precision highp image2D;\nprecision highp uimage2D;\nprecision highp iimage2D;\n";
            }
            glsl = insertAfterHeader(glsl, precisionBlock);
        } else {
            // Upgrade mediump/lowp float to highp float for color and lighting stability on Mali GPUs
            glsl = glsl.replace("precision mediump float;", "precision highp float;");
            glsl = glsl.replace("precision lowp float;", "precision highp float;");
        }

        // 3. Compute Shader Refinement
        if (detection.isCompute) {
            if (glsl.contains("gl_FragCoord.xy")) {
                glsl = glsl.replace("gl_FragCoord.xy", "vec2(gl_GlobalInvocationID.xy)");
            }
            if (glsl.contains("gl_FragCoord")) {
                glsl = glsl.replace("gl_FragCoord", "vec4(gl_GlobalInvocationID.xy, 0.0, 1.0)");
            }
            if (!glsl.contains("layout(local_size_")) {
                String layoutStr = "\n#ifndef QUASAR_COMPUTE_LAYOUT\n#define QUASAR_COMPUTE_LAYOUT\nlayout(local_size_x = 1, local_size_y = 1, local_size_z = 1) in;\n#endif\n";
                int mainPos = glsl.indexOf("void main");
                if (mainPos != -1) {
                    glsl = glsl.substring(0, mainPos) + layoutStr + glsl.substring(mainPos);
                } else {
                    glsl += layoutStr;
                }
            }
        }

        Log.d(TAG, "[Stage 4] GPU refinement completed for " + detection.shaderName);
        return glsl;
    }

    private static String insertAfterHeader(String code, String textToInsert) {
        int pos = code.indexOf("#version");
        if (pos == -1) return textToInsert + "\n" + code;
        int lineEnd = code.indexOf("\n", pos);
        if (lineEnd == -1) return code + "\n" + textToInsert;
        return code.substring(0, lineEnd + 1) + textToInsert + "\n" + code.substring(lineEnd + 1);
    }
}
