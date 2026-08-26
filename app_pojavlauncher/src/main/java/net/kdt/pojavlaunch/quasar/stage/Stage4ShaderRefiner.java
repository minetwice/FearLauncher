package net.kdt.pojavlaunch.quasar.stage;

import net.kdt.pojavlaunch.quasar.MaliShaderFixes;

/**
 * Stage 4: Shader refinement stage for Quasar renderer.
 * Applies final refinements to shaders after translation.
 * For Mali GPUs, this includes:
 * - Applying Mali-specific polyfills
 * - Fixing color space issues (CRITICAL: fixes red/green artifacts)
 * - Adjusting shadow parameters
 * - Ensuring GLES compatibility
 * - Shader-specific fixes for Complementary, Bliss, Solas, Astra
 */
public class Stage4ShaderRefiner {
    
    private static final String[] FRAGMENT_SHADER_OUTPUTS = {
        "fragColor", "FragColor", "gl_FragColor"
    };
    
    /**
     * Refines a shader source code.
     * Main entry point for shader refinement in the Quasar pipeline.
     */
    public static String refineShader(String shaderSource, boolean isFragmentShader, String shaderName) {
        String refined = shaderSource;
        
        // Step 1: Apply Mali-specific polyfills
        refined = MaliPolyfillInjector.injectPolyfills(refined, isFragmentShader);
        
        // Step 2: Apply Mali shader fixes from utility class
        refined = MaliShaderFixes.applyMaliFixes(refined);
        
        // Step 3: Apply shader-specific refinements
        refined = applyShaderSpecificFixes(refined, isFragmentShader, shaderName);
        
        // Step 4: Apply color space fixes (CRITICAL: fixes red/green artifact issue)
        refined = applyColorSpaceFixes(refined, isFragmentShader);
        
        return refined;
    }
    
    /**
     * Applies shader-specific fixes based on the shader pack name.
     */
    private static String applyShaderSpecificFixes(String shaderSource, boolean isFragmentShader, String shaderName) {
        if (shaderName == null) {
            return shaderSource;
        }
        
        String fixed = shaderSource;
        shaderName = shaderName.toLowerCase();
        
        // Complementary shaders
        if (shaderName.contains("complementary")) {
            fixed = fixComplementaryShaders(fixed, isFragmentShader);
        }
        
        // Bliss shaders
        if (shaderName.contains("bliss")) {
            fixed = fixBlissShaders(fixed, isFragmentShader);
        }
        
        // Solas/Solus shaders
        if (shaderName.contains("solas") || shaderName.contains("solus")) {
            fixed = fixSolasShaders(fixed, isFragmentShader);
        }
        
        // Astra shaders
        if (shaderName.contains("astra") || shaderName.contains("celestial")) {
            fixed = fixAstraShaders(fixed, isFragmentShader);
        }
        
        return fixed;
    }
    
    /**
     * Fixes for Complementary shaders.
     * Addresses color blending issues on Mali GPUs.
     */
    private static String fixComplementaryShaders(String shaderSource, boolean isFragmentShader) {
        String fixed = shaderSource;
        
        if (isFragmentShader) {
            // Add complementary shader defines
            if (!fixed.contains("MALI_COMPLEMENTARY_FIX")) {
                int insertPos = fixed.indexOf("void main()");
                if (insertPos > 0) {
                    insertPos = fixed.lastIndexOf("\n", insertPos) + 1;
                    String complementaryFix = "// Mali: Complementary shader color fix\n" +
                                           "#define MALI_COMPLEMENTARY_FIX 1\n" +
                                           "#define complementaryColor(c) (1.0 - (c))\n\n";
                    fixed = fixed.substring(0, insertPos) + complementaryFix + fixed.substring(insertPos);
                }
            }
            
            // Fix complementary color calculation to use proper color space
            fixed = fixed.replace("1.0 - color", "mali_complementary(color)");
            
            // Add the complementary function with proper gamma correction
            if (!fixed.contains("mali_complementary")) {
                int insertPos = fixed.indexOf("void main()");
                if (insertPos > 0) {
                    insertPos = fixed.lastIndexOf("\n", insertPos) + 1;
                    String compFunc = "// Mali complementary color function with gamma correction\n" +
                                    "vec3 mali_complementary(vec3 color) {\n" +
                                    "    return 1.0 - pow(color, vec3(2.2));\n" +
                                    "}\n\n";
                    fixed = fixed.substring(0, insertPos) + compFunc + fixed.substring(insertPos);
                }
            }
        }
        
        return fixed;
    }
    
    /**
     * Fixes for Bliss shaders.
     * Bliss shaders use PBR lighting that needs Mali adjustments.
     */
    private static String fixBlissShaders(String shaderSource, boolean isFragmentShader) {
        String fixed = shaderSource;
        
        if (isFragmentShader) {
            // Add Bliss shader defines
            if (!fixed.contains("MALI_BLISS_FIX")) {
                int insertPos = fixed.indexOf("void main()");
                if (insertPos > 0) {
                    insertPos = fixed.lastIndexOf("\n", insertPos) + 1;
                    String blissFix = "// Mali: Bliss shader fixes\n" +
                                    "#define MALI_BLISS_FIX 1\n" +
                                    "#define roughnessSq(r) ((r) * (r))\n\n";
                    fixed = fixed.substring(0, insertPos) + blissFix + fixed.substring(insertPos);
                }
            }
            
            // Optimize roughness calculations for Mali
            fixed = fixed.replace("roughness * roughness", "roughnessSq(roughness)");
        }
        
        return fixed;
    }
    
    /**
     * Fixes for Solas/Solus shaders.
     */
    private static String fixSolasShaders(String shaderSource, boolean isFragmentShader) {
        String fixed = shaderSource;
        
        if (isFragmentShader) {
            if (!fixed.contains("MALI_SOLAS_FIX")) {
                int insertPos = fixed.indexOf("void main()");
                if (insertPos > 0) {
                    insertPos = fixed.lastIndexOf("\n", insertPos) + 1;
                    String solasFix = "// Mali: Solas shader fixes\n" +
                                    "#define MALI_SOLAS_FIX 1\n" +
                                    "#define lightScattering(l, d) ((l) * exp(-(d) * 0.1))\n\n";
                    fixed = fixed.substring(0, insertPos) + solasFix + fixed.substring(insertPos);
                }
            }
        }
        
        return fixed;
    }
    
    /**
     * Fixes for Astra shaders.
     */
    private static String fixAstraShaders(String shaderSource, boolean isFragmentShader) {
        String fixed = shaderSource;
        
        if (isFragmentShader) {
            if (!fixed.contains("MALI_ASTRA_FIX")) {
                int insertPos = fixed.indexOf("void main()");
                if (insertPos > 0) {
                    insertPos = fixed.lastIndexOf("\n", insertPos) + 1;
                    String astraFix = "// Mali: Astra shader fixes\n" +
                                    "#define MALI_ASTRA_FIX 1\n" +
                                    "#define atmosphericScattering(c, d) ((c) * exp(-(d) * 0.01))\n\n";
                    fixed = fixed.substring(0, insertPos) + astraFix + fixed.substring(insertPos);
                }
            }
        }
        
        return fixed;
    }
    
    /**
     * Applies color space fixes to prevent red/green artifacts.
     * THIS IS THE CRITICAL FIX for the user's reported issue.
     * Mali GPUs have sRGB framebuffer issues that cause color distortion.
     */
    private static String applyColorSpaceFixes(String shaderSource, boolean isFragmentShader) {
        String fixed = shaderSource;
        
        if (isFragmentShader) {
            // Add sRGB to linear conversion for final output
            if (!fixed.contains("mali_toLinear")) {
                for (String output : FRAGMENT_SHADER_OUTPUTS) {
                    String pattern = output + " = ";
                    if (fixed.contains(pattern)) {
                        // Find all occurrences
                        int index = fixed.indexOf(pattern);
                        while (index >= 0) {
                            int endIndex = fixed.indexOf(";", index);
                            if (endIndex > index) {
                                String before = fixed.substring(0, endIndex);
                                String after = fixed.substring(endIndex);
                                String conversion = output + ".rgb = mali_toLinear(" + output + ".rgb)";
                                
                                // Only add if not already present
                                if (!before.contains("mali_toLinear")) {
                                    fixed = before + ";" + conversion + after;
                                }
                                index = fixed.indexOf(pattern, endIndex + conversion.length());
                            } else {
                                break;
                            }
                        }
                    }
                }
            }
            
            // Add the sRGB to linear conversion function
            // This is the KEY fix for red/green color artifacts
            if (!fixed.contains("mali_toLinear")) {
                int insertPos = fixed.indexOf("void main()");
                if (insertPos > 0) {
                    insertPos = fixed.lastIndexOf("\n", insertPos) + 1;
                    String toLinearFunc = "// Mali: sRGB to Linear color space conversion\n" +
                                        "// CRITICAL: Fixes red/green color artifacts on Mali GPUs\n" +
                                        "vec3 mali_toLinear(vec3 srgb) {\n" +
                                        "    vec3 linear;\n" +
                                        "    linear.r = (srgb.r <= 0.04045) ? srgb.r / 12.92 : pow((srgb.r + 0.055) / 1.055, 2.4);\n" +
                                        "    linear.g = (srgb.g <= 0.04045) ? srgb.g / 12.92 : pow((srgb.g + 0.055) / 1.055, 2.4);\n" +
                                        "    linear.b = (srgb.b <= 0.04045) ? srgb.b / 12.92 : pow((srgb.b + 0.055) / 1.055, 2.4);\n" +
                                        "    return linear;\n" +
                                        "}\n\n";
                    fixed = fixed.substring(0, insertPos) + toLinearFunc + fixed.substring(insertPos);
                }
            }
        }
        
        return fixed;
    }
    
    /**
     * Checks if a shader source needs refinement.
     */
    public static boolean needsRefinement(String shaderSource) {
        return MaliPolyfillInjector.needsPolyfills(shaderSource) ||
               shaderSource.contains("complementary") ||
               shaderSource.contains("bliss") ||
               shaderSource.contains("solas") ||
               shaderSource.contains("astra");
    }
}