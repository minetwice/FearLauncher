package net.kdt.pojavlaunch.quasar;

/**
 * Fixes shader glitches (red/green pixels) on Mali GPUs
 * 
 * This applies all necessary fixes to shaders before they are compiled.
 */
public class ShaderGlitchFixer {
    
    private static final TurnipIntegration turnipIntegration = new TurnipIntegration();
    private static final ColorSpaceFixer colorSpaceFixer = new ColorSpaceFixer();
    
    /**
     * Fix all glitches in a shader
     */
    public static String fixShader(String shaderSource, String shaderName, int shaderType) {
        if (shaderSource == null || shaderSource.isEmpty()) {
            return shaderSource;
        }
        
        String fixed = shaderSource;
        
        // Step 1: Apply Turnip fixes
        if (turnipIntegration.isTurnipEnabled()) {
            fixed = turnipIntegration.fixShaderForTurnip(fixed);
        }
        
        // Step 2: Fix color space (main cause of red/green glitches)
        ShaderInfo.ShaderType type = getShaderType(shaderType);
        ShaderInfo info = new ShaderInfo(shaderName, type);
        fixed = colorSpaceFixer.fixColorSpace(fixed, info);
        
        // Step 3: Apply Mali-specific fixes
        fixed = fixMaliGlitches(fixed);
        
        // Step 4: Apply general fixes
        fixed = fixGeneralGlitches(fixed);
        
        return fixed;
    }
    
    /**
     * Fix Mali-specific glitches
     */
    private static String fixMaliGlitches(String source) {
        String fixed = source;
        
        // Fix 1: Green pixels from incorrect color space
        fixed = fixed.replaceAll("texture(([^,]+),s*([^)]+))\.rgb", 
                "srgbToLinear(textureLod($1, $2, 0.0)).rgb");
        
        // Fix 2: Grid lines from texture filtering
        if (!fixed.contains("GL_OES_standard_derivatives")) {
            int versionIndex = fixed.indexOf("#version");
            if (versionIndex >= 0) {
                int newline = fixed.indexOf('
', versionIndex);
                if (newline >= 0) {
                    fixed = fixed.substring(0, newline + 1) + 
                           "#extension GL_OES_standard_derivatives : enable
" +
                           fixed.substring(newline + 1);
                }
            }
        }
        
        // Fix 3: Shadow problems
        fixed = fixed.replaceAll("u_ShadowBias\s*=\s*([0-9.]+)", 
                "u_ShadowBias = $1 * 4.0");
        
        // Fix 4: Precision issues
        fixed = fixed.replace("precision mediump float", "precision highp float");
        
        return fixed;
    }
    
    /**
     * Fix general glitches
     */
    private static String fixGeneralGlitches(String source) {
        String fixed = source;
        
        // Ensure proper version
        if (!fixed.contains("#version")) {
            fixed = "#version 300 es
" + fixed;
        }
        
        // Ensure precision
        if (!fixed.contains("precision")) {
            int versionEnd = fixed.indexOf("
");
            if (versionEnd > 0) {
                fixed = fixed.substring(0, versionEnd + 1) + 
                       "precision highp float;
" +
                       "precision highp int;
" +
                       fixed.substring(versionEnd + 1);
            }
        }
        
        return fixed;
    }
    
    /**
     * Convert numeric type to ShaderInfo.ShaderType
     */
    private static ShaderInfo.ShaderType getShaderType(int type) {
        switch (type) {
            case 0: return ShaderInfo.ShaderType.VERTEX;
            case 1: return ShaderInfo.ShaderType.FRAGMENT;
            case 2: return ShaderInfo.ShaderType.GEOMETRY;
            case 3: return ShaderInfo.ShaderType.COMPUTE;
            default: return ShaderInfo.ShaderType.UNKNOWN;
        }
    }
    
    /**
     * Process a shader through the full pipeline
     */
    public static String processShader(String shaderSource, String shaderName, int shaderType) {
        return TurnipSetup.processShader(shaderSource, shaderName, shaderType);
    }
}
