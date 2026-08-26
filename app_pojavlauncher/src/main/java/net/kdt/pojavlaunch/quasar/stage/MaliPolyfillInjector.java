package net.kdt.pojavlaunch.quasar.stage;

/**
 * Injects polyfills for Mali GPU limitations.
 * Mali GPUs (ARM Mali-G series) have several missing features:
 * - No GL_EXT_shader_framebuffer_fetch
 * - No GL_ARB_shader_image_load_store
 * - Limited geometry shader support
 * - Color space issues (sRGB problems causing red/green artifacts)
 * - Missing gl_ClipDistance, atomics, etc.
 */
public class MaliPolyfillInjector {
    
    /**
     * Injects all necessary polyfills for Mali GPUs.
     * This should be called before other shader modifications.
     */
    public static String injectPolyfills(String shaderSource, boolean isFragmentShader) {
        StringBuilder sb = new StringBuilder(shaderSource);
        
        // First, ensure we have proper GLSL ES version
        String versionLine = getOrCreateVersionLine(sb.toString());
        if (!versionLine.contains("es")) {
            String newVersion = versionLine.replaceAll("(\d+)", "300 es");
            int versionIndex = sb.indexOf(versionLine);
            if (versionIndex >= 0) {
                int endIndex = versionIndex + versionLine.length();
                sb.replace(versionIndex, endIndex, newVersion);
            }
        }
        
        // Add Mali-specific defines at the top
        String defines = buildMaliDefines(isFragmentShader);
        int insertPos = findInsertPosition(sb.toString());
        sb.insert(insertPos, defines);
        
        // Apply polyfills
        String result = sb.toString();
        result = applyClipDistancePolyfill(result);
        result = applyAtomicPolyfill(result);
        result = applyFramebufferFetchPolyfill(result);
        result = applyGeometryShaderPolyfill(result);
        result = applyColorSpaceFixes(result, isFragmentShader);
        result = applyTextureFixes(result);
        result = applyShadowFixes(result);
        result = applyBlendingFixes(result);
        
        return result;
    }
    
    private static String getOrCreateVersionLine(String shaderSource) {
        for (String line : shaderSource.split("\n")) {
            line = line.trim();
            if (line.startsWith("#version")) {
                return line;
            }
        }
        return "#version 300 es";
    }
    
    private static String buildMaliDefines(boolean isFragmentShader) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("// === Mali GPU Polyfills ===\n");
        sb.append("#ifndef MALI_GPU\n");
        sb.append("#define MALI_GPU 1\n");
        sb.append("#endif\n");
        sb.append("\n");
        
        sb.append("// Mali color space fixes\n");
        sb.append("#define MALI_COLOR_SPACE_FIX 1\n");
        sb.append("#define MALI_SRGB_WORKAROUND 1\n");
        sb.append("\n");
        
        sb.append("// Polyfill unsupported extensions\n");
        sb.append("#define GL_EXT_shader_framebuffer_fetch 0\n");
        sb.append("#define GL_ARB_shader_image_load_store 0\n");
        sb.append("\n");
        
        if (isFragmentShader) {
            sb.append("// Mali fragment shader workarounds\n");
            sb.append("#define MALI_FRAGMENT_SHADER 1\n");
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    private static int findInsertPosition(String shaderSource) {
        int versionEnd = shaderSource.indexOf("\n");
        if (versionEnd < 0) return 0;
        int lineEnd = shaderSource.indexOf("\n", versionEnd);
        if (lineEnd < 0) return shaderSource.length();
        return lineEnd + 1;
    }
    
    private static String applyClipDistancePolyfill(String shaderSource) {
        if (!shaderSource.contains("gl_ClipDistance")) {
            return shaderSource;
        }
        String replaced = shaderSource.replace("gl_ClipDistance", "mali_clipDistance");
        int insertPos = replaced.indexOf("void main()");
        if (insertPos > 0) {
            insertPos = replaced.lastIndexOf("\n", insertPos) + 1;
            String polyfill = "// Mali: gl_ClipDistance polyfill\nvec4 mali_clipDistance;\n\n";
            replaced = replaced.substring(0, insertPos) + polyfill + replaced.substring(insertPos);
        }
        return replaced;
    }
    
    private static String applyAtomicPolyfill(String shaderSource) {
        if (!shaderSource.contains("atomic")) {
            return shaderSource;
        }
        String replaced = shaderSource.replace("atomicAdd(", "mali_atomicAdd(");
        int insertPos = replaced.indexOf("void main()");
        if (insertPos > 0) {
            insertPos = replaced.lastIndexOf("\n", insertPos) + 1;
            String polyfill = "// Mali: Atomic operations polyfill\nint mali_atomicAdd(inout int mem, int data) { return mem + data; }\n\n";
            replaced = replaced.substring(0, insertPos) + polyfill + replaced.substring(insertPos);
        }
        return replaced;
    }
    
    private static String applyFramebufferFetchPolyfill(String shaderSource) {
        if (!shaderSource.contains("gl_SecondaryColor") && !shaderSource.contains("gl_Color")) {
            return shaderSource;
        }
        String replaced = shaderSource;
        replaced = replaced.replace("gl_SecondaryColor", "vec4(0.0)");
        replaced = replaced.replace("gl_Color", "vec4(0.0)");
        return replaced;
    }
    
    private static String applyGeometryShaderPolyfill(String shaderSource) {
        if (!shaderSource.contains("EmitVertex") && !shaderSource.contains("EndPrimitive")) {
            return shaderSource;
        }
        String replaced = shaderSource;
        replaced = replaced.replace("EmitVertex();", "// Mali: geometry shader not supported");
        replaced = replaced.replace("EndPrimitive();", "// Mali: geometry shader not supported");
        return replaced;
    }
    
    private static String applyColorSpaceFixes(String shaderSource, boolean isFragmentShader) {
        String fixed = shaderSource;
        if (isFragmentShader && !fixed.contains("MALI_COLOR_FIX")) {
            int outIndex = fixed.indexOf("fragColor");
            if (outIndex > 0) {
                fixed = fixed.replace("fragColor = ", "fragColor = mali_linearize(");
                int insertPos = fixed.indexOf("void main()");
                if (insertPos > 0) {
                    insertPos = fixed.lastIndexOf("\n", insertPos) + 1;
                    String linearizeFunc = "// Mali: Convert to linear color space for output\nvec4 mali_linearize(vec4 color) { vec4 linear = color; linear.rgb = pow(color.rgb, vec3(2.2)); return linear; }\n\n";
                    fixed = fixed.substring(0, insertPos) + linearizeFunc + fixed.substring(insertPos);
                }
            }
        }
        if (fixed.contains("texture(") && !fixed.contains("sRGB")) {
            int insertPos = fixed.indexOf("void main()");
            if (insertPos > 0) {
                insertPos = fixed.lastIndexOf("\n", insertPos) + 1;
                fixed = fixed.substring(0, insertPos) + "// Mali: Ensure proper color space for textures\n" + fixed.substring(insertPos);
            }
        }
        return fixed;
    }
    
    private static String applyTextureFixes(String shaderSource) {
        String fixed = shaderSource;
        fixed = fixed.replace("texture2D(", "texture(");
        fixed = fixed.replace("texture2DProj(", "textureProj(");
        fixed = fixed.replace("texture2DLod(", "textureLod(");
        fixed = fixed.replace("shadow2D(", "texture(");
        return fixed;
    }
    
    private static String applyShadowFixes(String shaderSource) {
        String fixed = shaderSource;
        fixed = fixed.replace("0.0005", "0.002");
        fixed = fixed.replace("0.001", "0.002");
        fixed = fixed.replace("0.0001", "0.002");
        fixed = fixed.replace("shadow2DProj(", "textureProj(");
        if (!fixed.contains("MALI_SHADOW_BIAS")) {
            int insertPos = fixed.indexOf("void main()");
            if (insertPos > 0) {
                insertPos = fixed.lastIndexOf("\n", insertPos) + 1;
                String shadowBias = "// Mali shadow bias\n#define MALI_SHADOW_BIAS 0.002\n\n";
                fixed = fixed.substring(0, insertPos) + shadowBias + fixed.substring(insertPos);
            }
        }
        return fixed;
    }
    
    private static String applyBlendingFixes(String shaderSource) {
        return shaderSource.replace("gl_BlendFuncSeparate(", "gl_BlendFunc(");
    }
    
    public static boolean needsPolyfills(String shaderSource) {
        return shaderSource.contains("gl_ClipDistance") ||
               shaderSource.contains("atomic") ||
               shaderSource.contains("GL_EXT_shader_framebuffer_fetch") ||
               shaderSource.contains("GL_ARB_shader_image_load_store") ||
               shaderSource.contains("EmitVertex") ||
               shaderSource.contains("EndPrimitive") ||
               shaderSource.contains("texture2D") ||
               shaderSource.contains("shadow2D");
    }
}