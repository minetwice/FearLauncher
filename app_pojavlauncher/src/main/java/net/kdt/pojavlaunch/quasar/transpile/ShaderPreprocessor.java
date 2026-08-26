package net.kdt.pojavlaunch.quasar.transpile;

import java.util.regex.Pattern;

/**
 * ShaderPreprocessor provides pre-compilation modifications for GLSL source code
 * before it is submitted to glShaderSource on Android (GLES 3.2 / LTW / Mali-G615 MC2).
 *
 * Enhanced to handle Mali GPU limitations for Complementary & Solas shaders:
 * 1. Replace GL_ARB_shader_texture_lod with GL_EXT_shader_texture_lod
 * 2. Handle noperspective interpolation qualifier
 * 3. Add Mali-specific polyfills
 * 4. Ensure required GLES extensions are enabled
 */
public class ShaderPreprocessor {

    public static String fix(String shaderSource) {
        if (shaderSource == null || shaderSource.isEmpty()) {
            return shaderSource;
        }

        String source = shaderSource;

        // 1. Replace desktop extensions with GLES equivalents
        // Replace GL_ARB_shader_texture_lod with GL_EXT_shader_texture_lod (supported on Mali & Adreno)
        source = source.replaceAll(
            "(?m)^\\s*#\\s*extension\\s+GL_ARB_shader_texture_lod\\s*:.*",
            "#extension GL_EXT_shader_texture_lod : require"
        );

        // Comment out extensions that have no GLES equivalent
        source = source.replaceAll(
            "(?m)^\\s*#\\s*extension\\s+(GL_NV_shader_noperspective_interpolation|GL_EXT_gpu_shader4|GL_ARB_gpu_shader5|GL_ARB_draw_instanced|GL_ARB_explicit_attrib_location|GL_ARB_shader_draw_parameters|GL_ARB_shading_language_420pack|GL_ARB_bindless_texture)\\b.*",
            "// extension directive commented for GLES compatibility"
        );

        // 2. Remove noperspective interpolation qualifier (reserved keyword in GLES 3.2)
        // Replace with smooth which is the opposite behavior but safer
        source = source.replaceAll("\\bnoperspective\\b", "smooth");

        // 3. Desktop GLSL -> GLES function renames
        source = source.replaceAll("\\btexture2DProjLod\\b", "textureProjLod  ");
        source = source.replaceAll("\\btexture2DLod\\b", "textureLod  ");
        source = source.replaceAll("\\btexture3DLod\\b", "textureLod  ");
        source = source.replaceAll("\\btextureCubeLod\\b", "textureLod    ");
        source = source.replaceAll("\\btexture2DGradARB\\b", "textureGrad   ");
        source = source.replaceAll("\\btexture2DGrad\\b", "textureGrad  ");
        source = source.replaceAll("\\btexture2DProj\\b", "textureProj ");
        source = source.replaceAll("\\btexture2D\\b", "texture  ");
        source = source.replaceAll("\\btextureCube\\b", "texture   ");
        source = source.replaceAll("\\btexture3D\\b", "texture  ");
        source = source.replaceAll("\\btexture1D\\b", "texture  ");
        source = source.replaceAll("\\bshadow2DProj\\b", "textureProj ");
        source = source.replaceAll("\\bshadow2D\\b", "texture ");

        // 4. Add Mali/Adreno compatibility extensions
        // These are commonly supported on mobile GPUs
        if (!source.contains("#extension GL_EXT_shader_texture_lod")) {
            source = insertExtension(source, "#extension GL_EXT_shader_texture_lod : require");
        }
        if (!source.contains("#extension GL_OES_standard_derivatives")) {
            source = insertExtension(source, "#extension GL_OES_standard_derivatives : enable");
        }
        if (!source.contains("#extension GL_EXT_draw_buffers")) {
            source = insertExtension(source, "#extension GL_EXT_draw_buffers : enable");
        }
        if (!source.contains("#extension GL_EXT_frag_depth")) {
            source = insertExtension(source, "#extension GL_EXT_frag_depth : enable");
        }

        // 5. Precision statement normalization for fragment shaders
        if ((source.contains("gl_FragColor") || source.contains("gl_FragData") || 
             source.contains("out vec4") || source.contains("Fragment")) && 
            !source.contains("precision ")) {
            source = insertAfterVersionLine(source,
                "#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\nprecision highp int;\n#endif");
        }

        // 6. Mali-specific fixes for Complementary & Solas shaders
        // Replace gl_ClipDistance with emulation for Mali (which lacks GL_EXT_clip_cull_distance)
        source = source.replaceAll("\\bgl_ClipDistance\\b", "quasar_gl_ClipDistance");
        
        // Add emulation for gl_ClipDistance if it is used
        if (source.contains("quasar_gl_ClipDistance")) {
            String clipEmulation = "// Mali GPU gl_ClipDistance emulation\n" +
                "#ifdef MC_GL_VENDOR_MALI\n" +
                "vec4 quasar_gl_ClipDistance;\n" +
                "#endif\n";
            source = insertAfterVersionLine(source, clipEmulation);
        }

        return source;
    }

    private static String insertExtension(String code, String extension) {
        int versionIndex = code.indexOf("#version");
        if (versionIndex != -1) {
            int lineEnd = code.indexOf("\n", versionIndex);
            if (lineEnd != -1) {
                return code.substring(0, lineEnd + 1) + extension + "\n" + code.substring(lineEnd + 1);
            }
        }
        return extension + "\n" + code;
    }

    private static String insertAfterVersionLine(String code, String insertText) {
        int versionIndex = code.indexOf("#version");
        if (versionIndex != -1) {
            int lineEnd = code.indexOf("\n", versionIndex);
            if (lineEnd != -1) {
                return code.substring(0, lineEnd + 1) + insertText + "\n" + code.substring(lineEnd + 1);
            }
        }
        return insertText + "\n" + code;
    }
}
