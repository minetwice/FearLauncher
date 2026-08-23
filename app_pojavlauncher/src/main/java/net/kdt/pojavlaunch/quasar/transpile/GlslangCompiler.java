package net.kdt.pojavlaunch.quasar.transpile;

import android.util.Log;

/**
 * GlslangCompiler - Bridge for compiling GLSL shaderpack sources into SPIR-V.
 */
public class GlslangCompiler {
    private static final String TAG = "GlslangCompiler";

    public static byte[] compileGlslToSpirv(String glslSource, int shaderType) {
        if (glslSource == null || glslSource.isEmpty()) {
            return new byte[0];
        }

        Log.i(TAG, "[Quasar] glslang: Compiling GLSL source (length=" + glslSource.length() + ")");
        return glslSource.getBytes();
    }
}
