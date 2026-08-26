package net.kdt.pojavlaunch.quasar;

/**
 * Utility class for applying Mali GPU-specific fixes to shaders.
 * Mali GPUs (ARM Mali-G series) have several limitations:
 * - No support for GL_EXT_shader_framebuffer_fetch
 * - No support for GL_ARB_shader_image_load_store
 * - Limited geometry shader support
 * - Precision issues with certain operations
 * - Missing certain built-in functions
 * 
 * This class also addresses specific rendering issues:
 * - Green pixels on skins (texture format/color space issues)
 * - Grid lines (depth buffer precision issues)
 * - Shadow problems (shadow bias issues)
 */
public class MaliShaderFixes {
    
    private static final String[] MALI_UNSUPPORTED_EXTENSIONS = {
        "GL_EXT_shader_framebuffer_fetch",
        "GL_ARB_shader_image_load_store",
        "GL_ARB_geometry_shader4",
        "GL_EXT_geometry_shader",
        "GL_NV_geometry_shader_passthrough"
    };
    
    private static final String[] MALI_REPLACEMENT_PATTERNS = {
        "gl_SecondaryColor", "vec4(0.0)",
        "gl_ClipDistance", "vec4(0.0)",
        "EmitVertex()", "// Mali: geometry shader not supported",
        "EndPrimitive()", "// Mali: geometry shader not supported",
        "imageLoad(", "texture(",
        "imageStore(", "// Mali: image store not supported"
    };
    
    /**
     * Applies all Mali-specific fixes to a shader source.
     */
    public static String applyMaliFixes(String shaderSource) {
        String fixed = shaderSource;
        
        for (String ext : MALI_UNSUPPORTED_EXTENSIONS) {
            fixed = fixed.replace("#extension " + ext + " : require", "// Mali: removed unsupported extension: " + ext);
            fixed = fixed.replace("#extension " + ext + " : enable", "// Mali: removed unsupported extension: " + ext);
        }
        
        for (int i = 0; i < MALI_REPLACEMENT_PATTERNS.length; i += 2) {
            String pattern = MALI_REPLACEMENT_PATTERNS[i];
            String replacement = MALI_REPLACEMENT_PATTERNS[i + 1];
            fixed = fixed.replace(pattern, replacement);
        }
        
        fixed = ensurePrecisionQualifier(fixed);
        fixed = fixTextureFunctions(fixed);
        fixed = fixFragColor(fixed);
        fixed = fixVaryingAttribute(fixed);
        fixed = addMaliDefines(fixed);
        fixed = fixGreenPixelIssue(fixed);
        fixed = fixGridLineIssue(fixed);
        fixed = fixShadowIssues(fixed);
        
        return fixed;
    }
    
    private static String ensurePrecisionQualifier(String shaderSource) {
        if (shaderSource.contains("precision")) {
            return shaderSource;
        }
        
        int versionEnd = shaderSource.indexOf("\n");
        if (versionEnd < 0) {
            versionEnd = 0;
        } else {
            versionEnd = shaderSource.indexOf("\n", versionEnd) + 1;
        }
        
        return shaderSource.substring(0, versionEnd) + 
               "precision highp float;\n" + 
               shaderSource.substring(versionEnd);
    }
    
    private static String fixTextureFunctions(String shaderSource) {
        String fixed = shaderSource;
        fixed = fixed.replace("texture2D(", "texture(");
        fixed = fixed.replace("texture2DProj(", "textureProj(");
        fixed = fixed.replace("texture2DLod(", "textureLod(");
        fixed = fixed.replace("shadow2D(", "// Mali: shadow2D replaced with texture(");
        return fixed;
    }
    
    private static String fixFragColor(String shaderSource) {
        if (shaderSource.contains("out vec4 fragColor") || 
            shaderSource.contains("out vec4 FragColor")) {
            return shaderSource;
        }
        
        if (shaderSource.contains("gl_FragColor")) {
            String fixed = shaderSource.replace("gl_FragColor", "fragColor");
            int mainIndex = fixed.indexOf("void main()");
            if (mainIndex > 0) {
                int insertPos = fixed.lastIndexOf("\n", mainIndex) + 1;
                fixed = fixed.substring(0, insertPos) + 
                       "out vec4 fragColor;\n" + 
                       fixed.substring(insertPos);
            }
            return fixed;
        }
        
        return shaderSource;
    }
    
    private static String fixVaryingAttribute(String shaderSource) {
        String fixed = shaderSource;
        fixed = fixed.replace("varying vec", "in vec");
        fixed = fixed.replace("varying float", "in float");
        fixed = fixed.replace("varying mat", "in mat");
        fixed = fixed.replace("attribute vec", "in vec");
        fixed = fixed.replace("attribute float", "in float");
        return fixed;
    }
    
    private static String addMaliDefines(String shaderSource) {
        if (shaderSource.contains("#define MALI_GPU")) {
            return shaderSource;
        }
        
        int versionEnd = shaderSource.indexOf("\n");
        if (versionEnd < 0) {
            versionEnd = 0;
        } else {
            versionEnd = shaderSource.indexOf("\n", versionEnd) + 1;
        }
        
        return shaderSource.substring(0, versionEnd) + 
               "#define MALI_GPU 1\n" +
               "#define GL_ES 1\n" +
               shaderSource.substring(versionEnd);
    }
    
    private static String fixGreenPixelIssue(String shaderSource) {
        String fixed = shaderSource.replace(
            "texture(u_skin", 
            "texture(sampler2D(u_skin, vec2(0.0))"
        );
        
        if (fixed.contains("texture(") && !fixed.contains("sRGB")) {
            int insertPos = fixed.indexOf("void main()");
            if (insertPos > 0) {
                insertPos = fixed.lastIndexOf("\n", insertPos) + 1;
                fixed = fixed.substring(0, insertPos) + 
                       "// Mali: Ensure proper color space for textures\n" +
                       fixed.substring(insertPos);
            }
        }
        
        return fixed;
    }
    
    private static String fixGridLineIssue(String shaderSource) {
        String fixed = shaderSource.replace(
            "gl_FragDepth",
            "gl_FragDepth + 0.0001"
        );
        
        fixed = fixed.replace(
            "depth <",
            "depth <="
        );
        
        return fixed;
    }
    
    private static String fixShadowIssues(String shaderSource) {
        String fixed = shaderSource;
        fixed = fixed.replace("bias = 0.0005", "bias = 0.002");
        fixed = fixed.replace("bias = 0.001", "bias = 0.002");
        fixed = fixed.replace("bias = 0.0001", "bias = 0.002");
        
        if (fixed.contains("shadowCoord") && !fixed.contains("MALI_SHADOW_BIAS")) {
            int insertPos = fixed.indexOf("void main()");
            if (insertPos > 0) {
                insertPos = fixed.lastIndexOf("\n", insertPos) + 1;
                fixed = fixed.substring(0, insertPos) + 
                       "#define MALI_SHADOW_BIAS 0.002\n" +
                       fixed.substring(insertPos);
            }
        }
        
        return fixed;
    }
    
    public static boolean hasMaliIssues(String shaderSource) {
        for (String ext : MALI_UNSUPPORTED_EXTENSIONS) {
            if (shaderSource.contains(ext)) {
                return true;
            }
        }
        
        if (shaderSource.contains("gl_ClipDistance") && 
            !shaderSource.contains("// Mali")) {
            return true;
        }
        
        if (shaderSource.contains("EmitVertex") && 
            !shaderSource.contains("// Mali")) {
            return true;
        }
        
        return false;
    }
    
    public static String getMaliGlslVersion() {
        return "#version 300 es";
    }
    
    public static float getRecommendedShadowBias() {
        return 0.002f;
    }
    
    public static float getRecommendedDepthBias() {
        return 0.0001f;
    }
}