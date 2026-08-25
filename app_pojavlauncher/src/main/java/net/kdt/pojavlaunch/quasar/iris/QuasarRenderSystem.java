package net.kdt.pojavlaunch.quasar.iris;

import android.util.Log;

import net.kdt.pojavlaunch.quasar.QuasarRenderer;
import net.kdt.pojavlaunch.quasar.capability.CapabilityTable;
import net.kdt.pojavlaunch.quasar.transpile.GlslangCompiler;
import net.kdt.pojavlaunch.quasar.transpile.ShaderPreprocessor;
import net.kdt.pojavlaunch.quasar.transpile.SpirvCrossTranspiler;

/**
 * QuasarRenderSystem bridges Quasar into Iris's existing IrisRenderSystem interface.
 *
 * Iris (https://github.com/IrisShaders/Iris) abstracts GL calls through
 * IrisRenderSystem in common/src/main/java/net/irisshaders/iris/gl/IrisRenderSystem.java.
 * IrisRenderSystem is a static utility class with a DSAAccess interface that has
 * three implementations:
 * - DSACore (OpenGL 4.5 direct state access)
 * - DSAARB (ARB_direct_state_access extension)
 * - DSAUnsupported (fallback, binds before operating)
 *
 * Quasar's approach:
 * Rather than forking Iris wholesale, Quasar implements a new backend
 * implementation of DSAAccess that routes calls through the Quasar pipeline.
 * This allows Iris's shader-loading and rendering logic to call into Quasar
 * without modification.
 *
 * Shader path (Mali/Adreno fix):
 * 1. ShaderPreprocessor strips desktop-only extensions (esp. NV noperspective)
 *    and replaces the noperspective keyword so Mali GLES accepts the source.
 * 2. GlslangCompiler: GLSL -> SPIR-V
 * 3. SpirvCrossTranspiler: SPIR-V -> GLES GLSL (300/310 ES)
 */
public class QuasarRenderSystem {
    private static final String TAG = "Quasar-RenderSystem";
    private static boolean initialized = false;

    /**
     * Initialize the Quasar render system bridge.
     * This sets up the Iris DSAAccess implementation to route through Quasar.
     */
    public static void init() {
        if (initialized) {
            Log.w(TAG, "QuasarRenderSystem already initialized");
            return;
        }
        Log.i(TAG, "Initializing QuasarRenderSystem (Iris bridge)...");

        // TODO: Implement DSAAccess bridge
        // The plan:
        // 1. Detect Iris is loaded (check for net.irisshaders.iris.Iris class)
        // 2. Use reflection to access IrisRenderSystem.dsaState field
        // 3. Replace the DSAAccess implementation with a QuasarDSAAccess instance
        //    that routes calls through the active Quasar backend
        // 4. Hook ShaderCreator.createShader() to intercept GLSL source and
        //    route it through the Quasar transpilation pipeline before
        //    passing the transpiled source to glShaderSource()

        Log.w(TAG, "QuasarRenderSystem is a stub — Iris integration not yet implemented");
        initialized = true;
    }

    /**
     * Check if Iris is available on the classpath.
     * @return true if Iris classes are loaded
     */
    public static boolean isIrisAvailable() {
        try {
            Class.forName("net.irisshaders.iris.Iris");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Transpile a shader source string for the current device.
     *
     * When Iris tries to compile a shader (ShaderCreator -> glShaderSource),
     * Quasar intercepts the GLSL source and runs:
     * 1. ShaderPreprocessor (Mali/Adreno extension + keyword fixes)
     * 2. GlslangCompiler: GLSL -> SPIR-V
     * 3. SpirvCrossTranspiler: SPIR-V -> target GLSL ES
     *
     * @param shaderSource The original desktop GLSL source
     * @param shaderStage The shader stage (vertex, fragment, etc.)
     * @param shaderName The shader name (for logging)
     * @return The transpiled GLSL source, or the preprocessed original if SPIR-V path fails
     */
    public static String transpileShader(String shaderSource, int shaderStage, String shaderName) {
        Log.d(TAG, "Transpiling shader: " + shaderName + " (stage=" + shaderStage + ")");

        CapabilityTable caps = null;
        try {
            if (QuasarRenderer.getInstance().isInitialized()) {
                caps = QuasarRenderer.getInstance().getCapabilityTable();
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not read CapabilityTable, using mobile-safe defaults", e);
        }

        // Step 0: Preprocess for Mali / GLES (strip NV noperspective etc.)
        String preprocessed = ShaderPreprocessor.preprocess(shaderSource, caps, shaderName);

        // Step 1: Compile GLSL -> SPIR-V
        int[] spirv = GlslangCompiler.compileToSPIRV(shaderStage, preprocessed, shaderName);
        if (spirv == null) {
            Log.w(TAG, "GLSL->SPIR-V failed for " + shaderName + ", using preprocessed source");
            return preprocessed;
        }

        // Step 2: Cross-compile SPIR-V -> target GLSL ES for Android
        int targetVersion = SpirvCrossTranspiler.GLSL_VERSION_300;
        boolean isGLES = true;
        if (caps != null) {
            int gles = caps.getGlesVersion();
            if (gles >= 320) {
                targetVersion = SpirvCrossTranspiler.GLSL_VERSION_320;
            } else if (gles >= 310) {
                targetVersion = SpirvCrossTranspiler.GLSL_VERSION_310;
            }
            // Prefer GLES path on mobile; only use desktop if explicitly desktop-like
            String vendor = caps.getGpuVendor();
            if ("amd".equalsIgnoreCase(vendor) || "nvidia".equalsIgnoreCase(vendor)
                    || "intel".equalsIgnoreCase(vendor)) {
                isGLES = false;
                targetVersion = SpirvCrossTranspiler.GLSL_VERSION_330;
            }
        }

        String transpiled = SpirvCrossTranspiler.transpileToGLSL(spirv, targetVersion, isGLES);
        if (transpiled == null) {
            Log.w(TAG, "SPIR-V->GLSL failed for " + shaderName + ", using preprocessed source");
            return preprocessed;
        }

        Log.d(TAG, "Successfully transpiled " + shaderName
                + " (" + shaderSource.length() + " -> " + transpiled.length() + " chars, gles=" + isGLES + ")");
        return transpiled;
    }

    /**
     * Shutdown the Quasar render system bridge.
     */
    public static void shutdown() {
        if (!initialized) return;
        Log.i(TAG, "Shutting down QuasarRenderSystem...");
        initialized = false;
    }
}
