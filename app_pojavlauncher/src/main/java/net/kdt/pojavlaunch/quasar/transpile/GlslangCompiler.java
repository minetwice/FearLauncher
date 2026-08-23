package net.kdt.pojavlaunch.quasar.transpile;

import android.util.Log;

/**
 * GlslangCompiler bridges to the glslang C library via JNI to compile
 * desktop GLSL (#version 120-460) into SPIR-V bytecode.
 *
 * Pipeline: Desktop GLSL -> SPIR-V (via glslang)
 *
 * The C API of glslang is used (glslang_c_interface.h) which provides:
 * - glslang_initialize_process() — call once at startup
 * - glslang_shader_create() / glslang_shader_preprocess() / glslang_shader_parse()
 * - glslang_program_create() / glslang_program_add_shader() / glslang_program_link()
 * - glslang_program_SPIRV_generate() — generates SPIR-V bytecode
 * - glslang_program_SPIRV_get_ptr() / glslang_program_SPIRV_get_size() — retrieve SPIR-V
 *
 * TODO: Implement JNI bridge to libglslang.so. The native library must be built
 * as part of the Quasar native module (separate from FearLauncher's existing
 * pojavexec native library).
 *
 * Native build:
 * - Clone glslang and SPIRV-Tools sources
 * - Build with NDK CMake using android.toolchain.cmake
 * - Produce libglslang.so for arm64-v8a and armeabi-v7a
 */
public class GlslangCompiler {
    private static final String TAG = "Quasar-GlslangCompiler";
    private static boolean initialized = false;

    // Shader stages (matching glslang_stage_t)
    public static final int STAGE_VERTEX = 0;
    public static final int STAGE_FRAGMENT = 4;
    public static final int STAGE_GEOMETRY = 1;
    public static final int STAGE_TESSELLATION_CONTROL = 2;
    public static final int STAGE_TESSELLATION_EVALUATION = 3;
    public static final int STAGE_COMPUTE = 5;

    /**
     * Initialize the glslang library. Must be called once before any compilation.
     */
    public static synchronized void initialize() {
        if (initialized) {
            Log.w(TAG, "Glslang already initialized");
            return;
        }
        Log.i(TAG, "Initializing glslang compiler...");
        try {
            nativeInitialize();
            initialized = true;
            Log.i(TAG, "Glslang initialized successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load glslang native library", e);
            throw new RuntimeException("Glslang native library not available", e);
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
        if (!initialized) {
            Log.e(TAG, "Glslang not initialized — call initialize() first");
            return null;
        }
        Log.d(TAG, "Compiling " + fileName + " (stage=" + stage + ") to SPIR-V...");

        try {
            int[] spirv = nativeCompileToSPIRV(stage, sourceCode, fileName);
            if (spirv == null) {
                Log.e(TAG, "Compilation failed for " + fileName);
            } else {
                Log.d(TAG, "Compiled " + fileName + " to " + spirv.length + " SPIR-V words");
            }
            return spirv;
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native method not available", e);
            return null;
        }
    }

    /**
     * Shutdown the glslang library and release resources.
     */
    public static synchronized void shutdown() {
        if (!initialized) return;
        Log.i(TAG, "Shutting down glslang...");
        try {
            nativeShutdown();
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "Native shutdown failed", e);
        }
        initialized = false;
    }

    // --- Native methods (implemented in libquasar_glslang.so) ---

    private static native void nativeInitialize();
    private static native int[] nativeCompileToSPIRV(int stage, String sourceCode, String fileName);
    private static native void nativeShutdown();
}
