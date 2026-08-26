package net.kdt.pojavlaunch.quasar;

/**
 * Fixes color space issues causing red/green glitches
 * 
 * Common causes on Mali GPUs:
 * 1. Incorrect sRGB to linear conversion
 * 2. Missing color space declarations
 * 3. Texture format mismatches
 * 4. Framebuffer color space issues
 * 5. Shader output color space issues
 */
public class ColorSpaceFixer {
    
    private final TurnipIntegration turnipIntegration;
    private boolean forceLinear = false;
    private boolean forceSrgb = false;
    
    public ColorSpaceFixer() {
        this.turnipIntegration = new TurnipIntegration();
    }
    
    /**
     * Fixes color space issues in a shader
     */
    public String fixColorSpace(String shaderSource, ShaderInfo shaderInfo) {
        if (shaderSource == null) return shaderSource;
        
        String fixed = shaderSource;
        
        // Detect if this is a fragment shader
        boolean isFragment = shaderInfo.getType() == ShaderInfo.ShaderType.FRAGMENT;
        
        // Step 1: Add color space conversion functions
        fixed = addColorSpaceFunctions(fixed);
        
        // Step 2: Fix texture sampling
        fixed = fixTextureColorSpace(fixed, isFragment);
        
        // Step 3: Fix output color space
        fixed = fixOutputColorSpace(fixed, isFragment);
        
        // Step 4: Fix input color space
        fixed = fixInputColorSpace(fixed, isFragment);
        
        // Step 5: Add color space defines
        fixed = addColorSpaceDefines(fixed);
        
        // Step 6: Turnip-specific fixes
        if (turnipIntegration.isTurnipEnabled()) {
            fixed = turnipIntegration.fixColorSpaceForVulkan(fixed);
        }
        
        return fixed;
    }
    
    /**
     * Adds color space conversion functions
     */
    private String addColorSpaceFunctions(String source) {
        if (source.contains("srgbToLinear") && source.contains("linearToSrgb")) {
            return source;
        }
        
        String functions = "
// Color Space Conversion Functions
" +
            "vec3 srgbToLinear(vec3 srgb) {
" +
            "vec3 linear;
" +
            "for (int i = 0; i < 3; i++) {
" +
            "if (srgb[i] <= 0.04045) {
" +
            "linear[i] = srgb[i] / 12.92;
" +
            "} else {
" +
            "linear[i] = pow((srgb[i] + 0.055) / 1.055, 2.4);
" +
            "}
" +
            "}
" +
            "return linear;
" +
            "}

" +
            "vec3 linearToSrgb(vec3 linear) {
" +
            "vec3 srgb;
" +
            "for (int i = 0; i < 3; i++) {
" +
            "if (linear[i] <= 0.0031308) {
" +
            "srgb[i] = linear[i] * 12.92;
" +
            "} else {
" +
            "srgb[i] = 1.055 * pow(linear[i], 1.0 / 2.4) - 0.055;
" +
            "}
" +
            "}
" +
            "return srgb;
" +
            "}

" +
            "vec4 srgbToLinear(vec4 srgb) {
" +
            "return vec4(srgbToLinear(srgb.rgb), srgb.a);
" +
            "}

" +
            "vec4 linearToSrgb(vec4 linear) {
" +
            "return vec4(linearToSrgb(linear.rgb), linear.a);
" +
            "}
";
        
        // Find a good place to insert (after version and before main code)
        int insertPos = findInsertPosition(source);
        return source.substring(0, insertPos) + functions + source.substring(insertPos);
    }
    
    /**
     * Fixes texture sampling color space
     */
    private String fixTextureColorSpace(String source, boolean isFragment) {
        String fixed = source;
        
        // Check if textures are being sampled in sRGB space
        // On Mali GPUs, textures are often stored in sRGB but sampled as linear
        
        // Fix 1: Ensure diffuse textures are converted from sRGB
        fixed = fixed.replaceAll(
            "texture(([^,]+),\s*([^)]+))\.rgb",
            "srgbToLinear(textureLod($1, $2, 0.0)).rgb"
        );
        
        fixed = fixed.replaceAll(
            "texture(([^,]+),\s*([^)]+))\.rgba",
            "srgbToLinear(textureLod($1, $2, 0.0)).rgba"
        );
        
        // Fix 2: Normal maps should NOT be converted (they're linear)
        if (fixed.contains("u_NormalMap") || fixed.contains("normalMap")) {
            fixed = fixed.replaceAll(
                "texture(([a-zA-Z_]*[Nn]ormal[Mm]ap[^,]*),\s*([^)]+))",
                "textureLod($1, $2, 0.0)"
            );
        }
        
        // Fix 3: Shadow maps are linear
        if (fixed.contains("u_ShadowMap") || fixed.contains("shadowMap")) {
            fixed = fixed.replaceAll(
                "texture(([a-zA-Z_]*[Ss]hadow[Mm]ap[^,]*),\s*([^)]+))",
                "textureLod($1, $2, 0.0)"
            );
        }
        
        return fixed;
    }
    
    /**
     * Fixes output color space
     */
    private String fixOutputColorSpace(String source, boolean isFragment) {
        if (!isFragment) return source;
        
        String fixed = source;
        
        // Fragment shader output should be in sRGB for display
        // Fix: fragColor = vec4(color, alpha) -> fragColor = vec4(linearToSrgb(color), alpha)
        fixed = fixed.replaceAll(
            "fragColor\s*=\s*vec4(([^,]+),\s*([^)]+))",
            "fragColor = vec4(linearToSrgb($1), $2)"
        );
        
        // Also handle gl_FragColor
        fixed = fixed.replaceAll(
            "gl_FragColor\s*=\s*vec4(([^,]+),\s*([^)]+))",
            "gl_FragColor = vec4(linearToSrgb($1), $2)"
        );
        
        return fixed;
    }
    
    /**
     * Fixes input color space
     */
    private String fixInputColorSpace(String source, boolean isFragment) {
        String fixed = source;
        
        // Vertex shader inputs are typically linear
        // No conversion needed
        
        return fixed;
    }
    
    /**
     * Adds color space defines
     */
    private String addColorSpaceDefines(String source) {
        if (source.contains("#define COLOR_SPACE")) return source;
        
        int versionIndex = source.indexOf("#version");
        if (versionIndex >= 0) {
            int newlineIndex = source.indexOf('
', versionIndex);
            if (newlineIndex >= 0) {
                String defines = "
" +
                    "#define COLOR_SPACE_LINEAR 1
" +
                    "#define COLOR_SPACE_SRGB 1
" +
                    "#define USE_SRGB_CONVERSION 1
";
                return source.substring(0, newlineIndex + 1) + defines + source.substring(newlineIndex + 1);
            }
        }
        return source;
    }
    
    /**
     * Finds a good position to insert code
     */
    private int findInsertPosition(String source) {
        // Try to find after all preprocessor directives
        int lastDirective = Math.max(
            source.lastIndexOf("#version"),
            Math.max(
                source.lastIndexOf("#extension"),
                source.lastIndexOf("#define")
            )
        );
        
        if (lastDirective >= 0) {
            int newline = source.indexOf('
', lastDirective);
            if (newline >= 0) {
                return newline + 1;
            }
        }
        
        return 0;
    }
    
    /**
     * Forces linear color space
     */
    public void forceLinearColorSpace(boolean force) {
        this.forceLinear = force;
    }
    
    /**
     * Forces sRGB color space
     */
    public void forceSrgbColorSpace(boolean force) {
        this.forceSrgb = force;
    }
    
    /**
     * Gets Turnip integration
     */
    public TurnipIntegration getTurnipIntegration() {
        return turnipIntegration;
    }
}
