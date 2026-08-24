package net.kdt.pojavlaunch.quasar.transpile;

import android.util.Log;

/**
 * GlslangCompiler bridges to the glslang C library via JNI to compile
 * desktop GLSL (#version 120-460) into SPIR-V bytecode.
 *
 * Pipeline: Desktop GLSL -> SPIR-V (via glslang)
 *
 * The native library libquasar_shader.so is loaded at class init time.
 * It links against static glslang + SPIRV-Cross libraries.
 */
public class GlslangCompiler {
    private static final String TAG = "Quasar-GlslangCompiler";
    private static boolean initialized = false;
    private static boolean nativeAvailable = false;

    // Shader stages (matching glslang_stage_t)
    public static final int STAGE_VERTEX = 0;
    public static final int STAGE_GEOMETRY = 1;
    public static final int STAGE_TESSELLATION_CONTROL = 2;
    public static final int STAGE_TESSELLATION_EVALUATION = 3;
    public static final int STAGE_FRAGMENT = 4;
    public static final int STAGE_COMPUTE = 5;

    static {
        try {
            System.loadLibrary("quasar_shader");
            nativeAvailable = true;
            Log.i(TAG, "libquasar_shader.so loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            nativeAvailable = false;
            Log.w(TAG, "libquasar_shader.so not available - shader transpilation disabled", e);
        }
    }

    /**
     * Initialize the glslang library. Must be called once before any compilation.
     */
    public static synchronized void initialize() {
        if (initialized) {
            Log.w(TAG, "Glslang already initialized");
            return;
        }
        if (!nativeAvailable) {
            Log.e(TAG, "Native library not available");
            return;
        }
        Log.i(TAG, "Initializing glslang compiler...");
        try {
            nativeInitialize();
            initialized = true;
            Log.i(TAG, "Glslang initialized successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to initialize glslang native library", e);
        }
    }

    /**
     * Compile GLSL source code to SPIR-V bytecode.
     *
     * @param stage The shader stage (STAGE_VERTEX, STAGE_FRAGMENT, etc.)
     * @param sourceCode The GLSL source code string
     * @param fileName The source file name (for error messages)
     * @return The SPIR-V bytecode as an int[] array, or null if compilation failed
     */
    public static int[] compileToSPIRV(int stage, String sourceCode, String fileName) {
        if (!nativeAvailable) {
            Log.e(TAG, "Native library not available");
            return null;
        }
        if (!initialized) {
            initialize();
            if (!initialized) {
                Log.e(TAG, "Glslang not initialized");
                return null;
            }
        }
        Log.d(TAG, "Compiling " + fileName + " (stage=" + stage + ") to SPIR-V...");
        try {
            int[] spirv = nativeCompileToSPIRV(stage, sourceCode, fileName);
            if (spirv != null) {
                Log.d(TAG, "Compiled " + fileName + " -> " + spirv.length + " SPIR-V words");
            } else {
                Log.e(TAG, "Compilation failed for " + fileName);
            }
            return spirv;
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native method not available", e);
            return null;
        }
    }

    public static boolean isAvailable() {
        return nativeAvailable;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    // --- Native methods ---
    private static native void nativeInitialize();
    private static native int[] nativeCompileToSPIRV(int stage, String sourceCode, String fileName);
}
