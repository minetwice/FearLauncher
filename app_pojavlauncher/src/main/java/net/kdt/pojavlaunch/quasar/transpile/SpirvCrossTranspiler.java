package net.kdt.pojavlaunch.quasar.transpile;

import android.util.Log;

/**
 * SpirvCrossTranspiler bridges to the SPIRV-Cross C library via JNI to
 * cross-compile SPIR-V bytecode into GLES-compatible GLSL.
 *
 * Pipeline: SPIR-V -> GLSL ES (via SPIRV-Cross)
 *
 * The native library libquasar_shader.so is shared with GlslangCompiler.
 */
public class SpirvCrossTranspiler {
    private static final String TAG = "Quasar-SpirvCross";

    // Target GLSL versions
    public static final int GLSL_VERSION_300 = 300;
    public static final int GLSL_VERSION_310 = 310;
    public static final int GLSL_VERSION_320 = 320;
    public static final int GLSL_VERSION_330 = 330;
    public static final int GLSL_VERSION_460 = 460;

    private static boolean nativeAvailable = false;

    static {
        try {
            System.loadLibrary("quasar_shader");
            nativeAvailable = true;
            Log.i(TAG, "libquasar_shader.so loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            nativeAvailable = false;
            Log.w(TAG, "libquasar_shader.so not available - SPIRV-Cross disabled", e);
        }
    }

    /**
     * Cross-compile SPIR-V bytecode to GLSL.
     *
     * @param spirv The SPIR-V bytecode as int[] array
     * @param glslVersion Target GLSL version (e.g. 300 for GLES, 330 for desktop)
     * @param isGLES If true, output GLSL ES; if false, output desktop GLSL
     * @return The compiled GLSL source code, or null if compilation failed
     */
    public static String transpileToGLSL(int[] spirv, int glslVersion, boolean isGLES) {
        if (!nativeAvailable) {
            Log.e(TAG, "Native library not available");
            return null;
        }
        if (spirv == null || spirv.length == 0) {
            Log.e(TAG, "Invalid SPIR-V input (null or empty)");
            return null;
        }

        Log.d(TAG, "Cross-compiling SPIR-V (" + spirv.length + " words) to GLSL "
                + glslVersion + (isGLES ? " ES" : " desktop"));

        try {
            String result = nativeTranspileToGLSL(spirv, glslVersion, isGLES);
            if (result == null) {
                Log.e(TAG, "SPIRV-Cross compilation failed");
            } else {
                Log.d(TAG, "Cross-compiled to " + result.length() + " chars of GLSL");
            }
            return result;
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native method not available", e);
            return null;
        }
    }

    /**
     * Convenience method: transpile to GLSL ES 3.00 (most common for Mali GLES 3)
     */
    public static String transpileToGLES300(int[] spirv) {
        return transpileToGLSL(spirv, GLSL_VERSION_300, true);
    }

    /**
     * Convenience method: transpile to GLSL ES 3.10
     */
    public static String transpileToGLES310(int[] spirv) {
        return transpileToGLSL(spirv, GLSL_VERSION_310, true);
    }

    /**
     * Convenience method: transpile to desktop GLSL 3.30
     */
    public static String transpileToDesktop330(int[] spirv) {
        return transpileToGLSL(spirv, GLSL_VERSION_330, false);
    }

    /**
     * Convenience method: transpile to desktop GLSL 4.60
     */
    public static String transpileToDesktop460(int[] spirv) {
        return transpileToGLSL(spirv, GLSL_VERSION_460, false);
    }

    /**
     * Get reflection information about the SPIR-V binary.
     *
     * @param spirv The SPIR-V bytecode
     * @return JSON string describing the shader resources, or null on failure
     */
    public static String reflect(int[] spirv) {
        if (!nativeAvailable) return null;
        if (spirv == null || spirv.length == 0) return null;
        try {
            return nativeReflect(spirv);
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native method not available", e);
            return null;
        }
    }

    public static boolean isAvailable() {
        return nativeAvailable;
    }

    // --- Native methods ---
    private static native String nativeTranspileToGLSL(int[] spirv, int glslVersion, boolean isGLES);
    private static native String nativeReflect(int[] spirv);
}
