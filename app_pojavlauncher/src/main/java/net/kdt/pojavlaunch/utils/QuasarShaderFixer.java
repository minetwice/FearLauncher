package net.kdt.pojavlaunch.utils;

import android.util.Log;

/**
 * Quasar Shader Fixer - Provides shader compatibility fixes for Mali GPUs
 * 
 * Mali GPUs (ARM) have missing extensions and driver limitations that prevent
 * certain GLSL shaders from working correctly. This class provides automatic
 * fixes for common shader issues on Mali GPUs.
 * 
 * Issues addressed:
 * - Missing GL_EXT_shader_framebuffer_fetch
 * - Complementary color blending
 * - Solas/Solus shader patterns
 * - Precision qualifiers
 * - Unsupported GLSL functions
 */
public class QuasarShaderFixer {

    private static final String TAG = "QuasarShaderFixer";
    private static Boolean sIsMali = null;
    private static Boolean sHasFramebufferFetch = null;
    
    // Mali GPU patterns that need special handling
    private static final String[] MALI_GPU_PATTERNS = {
        "Mali-T", "Mali-G", "Mali-B", "Immortalis"
    };

    // Extensions that are typically missing on Mali
    private static final String[] MISSING_EXTENSIONS = {
        "GL_EXT_shader_framebuffer_fetch",
        "GL_ARB_shader_image_load_store",
        "GL_NV_shader_framebuffer_fetch"
    };

    /**
     * Check if the current device has a Mali GPU
     */
    public static boolean isMaliGPU() {
        if (sIsMali != null) {
            return sIsMali;
        }
        
        try {
            GLInfo glInfo = GLInfoUtils.getGlInfo();
            String renderer = glInfo.renderer;
            String vendor = glInfo.vendor;
            
            if (renderer != null && vendor != null) {
                for (String pattern : MALI_GPU_PATTERNS) {
                    if (renderer.contains(pattern) && vendor.equals("ARM")) {
                        sIsMali = true;
                        Log.d(TAG, "Detected Mali GPU: " + renderer);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error detecting Mali GPU", e);
        }
        
        sIsMali = false;
        return false;
    }

    /**
     * Check if framebuffer fetch is supported
     */
    public static boolean hasFramebufferFetch() {
        if (sHasFramebufferFetch != null) {
            return sHasFramebufferFetch;
        }
        
        // Mali GPUs typically don't support framebuffer fetch
        sHasFramebufferFetch = !isMaliGPU();
        return sHasFramebufferFetch;
    }

    /**
     * Reset cached GPU information (call this after context changes)
     */
    public static void resetCache() {
        sIsMali = null;
        sHasFramebufferFetch = null;
    }

    /**
     * Fix GLSL shaders for Mali GPUs
     * 
     * @param shaderCode The original shader code
     * @param shaderType GLES20.GL_VERTEX_SHADER or GLES20.GL_FRAGMENT_SHADER
     * @return Fixed shader code safe for Mali GPUs
     */
    public static String fixShaderForMali(String shaderCode, int shaderType) {
        if (!isMaliGPU()) {
            return shaderCode; // No fixes needed for non-Mali
        }

        String fixedShader = shaderCode;
        
        Log.d(TAG, "Applying Mali GPU shader fixes");

        // Fix 1: Replace gl_FragColor with custom variable
        // Mali doesn't support writing to gl_FragColor directly in some cases
        fixedShader = fixedShader.replaceAll(
            "\\bgl_FragColor\\b",
            "quasar_FragColor"
        );

        // Fix 2: Add fallback for complementary colors
        // Mali doesn't support certain blending equations
        fixedShader = fixedShader.replaceAll(
            "gl_Color\\s*=\\s*vec4\\(1\\.0\\s*-\\s*gl_Color\\.rgb,\\s*gl_Color\\.a\\)",
            "gl_Color = vec4(quasar_Complementary(gl_Color.rgb), gl_Color.a)"
        );

        // Fix 3: Replace solas/solus shader patterns
        fixedShader = fixedShader.replaceAll(
            "\\bsolas_\\w+",
            "quasar_SolasReplacement"
        );

        // Fix 4: Replace solus patterns
        fixedShader = fixedShader.replaceAll(
            "\\bsolus_\\w+",
            "quasar_SolusReplacement"
        );

        // Fix 5: Add precision qualifier if missing (Mali needs explicit precision)
        if (!fixedShader.contains("precision")) {
            if (shaderType == 0x8B30) { // GLES20.GL_FRAGMENT_SHADER
                fixedShader = "precision highp float;\nprecision highp int;\n" + fixedShader;
            } else {
                fixedShader = "precision highp float;\n" + fixedShader;
            }
        }

        // Fix 6: Replace unsupported GLSL functions
        fixedShader = fixedShader.replaceAll(
            "\\btexture2DLod\\b",
            "texture2D"
        );

        // Fix 7: Replace texture3D with texture2D for Mali (simplified)
        fixedShader = fixedShader.replaceAll(
            "\\btexture3D\\b",
            "texture2D"
        );

        // Fix 8: Replace textureCube with texture2D
        fixedShader = fixedShader.replaceAll(
            "\\btextureCube\\b",
            "texture2D"
        );

        // Fix 9: Add Mali-compatible extensions
        fixedShader = addMaliExtensions(fixedShader);

        // Fix 10: Ensure version directive is present
        if (!fixedShader.contains("#version")) {
            fixedShader = getOptimalShaderVersion() + fixedShader;
        }

        return fixedShader;
    }

    /**
     * Add Mali-compatible GLSL extensions
     */
    private static String addMaliExtensions(String shader) {
        // Add Mali-compatible extensions
        String extensions = 
            "#extension GL_OES_standard_derivatives : enable\n" +
            "#extension GL_EXT_shader_texture_lod : enable\n" +
            "#extension GL_OES_texture_npot : enable\n" +
            "#extension GL_OES_texture_float : enable\n" +
            "#extension GL_OES_texture_half_float : enable\n\n";

        if (!shader.contains("#extension")) {
            return extensions + shader;
        }
        return shader;
    }

    /**
     * Get the optimal GLSL version for the current GPU
     */
    public static String getOptimalShaderVersion() {
        if (isMaliGPU()) {
            // Mali GPUs work best with GLSL ES 3.0 or 3.1
            return "#version 310 es\n";
        }
        return "#version 300 es\n";
    }

    /**
     * Generate fallback functions for Mali GPUs
     */
    public static String getMaliFallbackFunctions() {
        return
            "// Quasar Mali GPU Fallback Functions\n" +
            "vec3 quasar_Complementary(vec3 color) {\n" +
            "    return vec3(1.0 - color.r, 1.0 - color.g, 1.0 - color.b);\n" +
            "}\n\n" +
            
            "vec4 quasar_SolasReplacement(vec2 uv) {\n" +
            "    // Fallback for solas/solus shaders\n" +
            "    // Try to use the first available texture unit\n" +
            "    return texture2D(u_Texture0, uv);\n" +
            "}\n\n" +
            
            "vec4 quasar_SolusReplacement(vec2 uv) {\n" +
            "    // Alternative fallback\n" +
            "    return texture2D(u_Texture, uv);\n" +
            "}\n\n" +
            
            "vec4 quasar_FragColor;\n";
    }

    /**
     * Check if a shader uses unsupported features on Mali
     */
    public static boolean needsFallback(String shaderCode) {
        if (!isMaliGPU()) {
            return false;
        }

        for (String ext : MISSING_EXTENSIONS) {
            if (shaderCode.contains(ext)) {
                return true;
            }
        }

        // Check for complementary patterns
        if (shaderCode.matches(".*1\\.0\\s*-\\s*.*Color.*")) {
            return true;
        }

        // Check for solas patterns
        if (shaderCode.matches(".*solas_.*") || 
            shaderCode.matches(".*solus_.*")) {
            return true;
        }

        // Check for framebuffer fetch
        if (shaderCode.contains("gl_FragColor") || 
            shaderCode.contains("gl_LastFragColor")) {
            return true;
        }

        return false;
    }

    /**
     * Apply aggressive fallbacks based on compilation error
     */
    public static String applyAggressiveFallbacks(String shaderCode, String error) {
        String fixed = shaderCode;

        if (error.contains("framebuffer fetch")) {
            fixed = fixed.replaceAll("gl_FragColor", "quasar_FragColor");
            fixed = getMaliFallbackFunctions() + fixed;
        }

        if (error.contains("complementary") || error.contains("1.0 -")) {
            fixed = fixed.replaceAll("1\\.0\\s*-\\s*gl_Color", "quasar_Complementary(gl_Color");
            // Close the function call
            fixed = fixed.replaceAll("quasar_Complementary(gl_Color\\.rgb)", 
                "quasar_Complementary(gl_Color.rgb)");
        }

        if (error.contains("solas") || error.contains("solus")) {
            fixed = fixed.replaceAll("solas_", "quasar_SolasReplacement(");
            fixed = fixed.replaceAll("solus_", "quasar_SolusReplacement(");
        }

        if (error.contains("texture2DLod")) {
            fixed = fixed.replaceAll("texture2DLod", "texture2D");
        }

        if (error.contains("precision")) {
            fixed = "precision highp float;\nprecision highp int;\n" + fixed;
        }

        return fixed;
    }

    /**
     * Initialize Quasar shader fixer (call this on OpenGL context creation)
     */
    public static void initialize() {
        resetCache();
        boolean isMali = isMaliGPU();
        boolean hasFbFetch = hasFramebufferFetch();
        
        Log.d(TAG, "Quasar Shader Fixer initialized - Mali GPU: " + isMali + 
              ", Framebuffer Fetch: " + hasFbFetch);
    }

    /**
     * Get GPU information as a string for debugging
     */
    public static String getGpuInfo() {
        try {
            GLInfo glInfo = GLInfoUtils.getGlInfo();
            return "Vendor: " + glInfo.vendor + ", Renderer: " + glInfo.renderer + 
                   ", GLES Version: " + glInfo.glesMajorVersion + 
                   ", Is Mali: " + isMaliGPU();
        } catch (Exception e) {
            return "Error getting GPU info: " + e.getMessage();
        }
    }
}
