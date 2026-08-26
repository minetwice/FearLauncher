package net.kdt.pojavlaunch.quasar.transpile;

import java.util.regex.Pattern;

/**
 * ShaderPreprocessor provides pre-compilation modifications for GLSL source code
 * before it is submitted to glShaderSource on Android (GLES 3.2 / LTW / Mali-G615 MC2).
 *
 * It fixes:
 * 1. 'noperspective' interpolation qualifier errors (reserved in GLES 3.2).
 * 2. GL_ARB_shader_texture_lod -> GL_EXT_shader_texture_lod extension replacement for Solas Shaders.
 * 3. Legacy texture2DLod / textureCubeLod function calls -> textureLod.
 * 4. Silencing GL_NV_shader_noperspective_interpolation extension warnings.
 */
public class ShaderPreprocessor {

    public static String fix(String shaderSource) {
        if (shaderSource == null || shaderSource.isEmpty()) {
            return shaderSource;
        }

        String source = shaderSource;

        // 1. Sanitize unsupported extension directives into single-line comments
        source = source.replaceAll(
            "(?m)^\\s*#\\s*extension\\s+(GL_NV_shader_noperspective_interpolation|GL_ARB_shader_texture_lod|GL_EXT_gpu_shader4|GL_ARB_gpu_shader5|GL_ARB_draw_instanced|GL_ARB_explicit_attrib_location|GL_ARB_shader_draw_parameters|GL_ARB_shading_language_420pack|GL_ARB_bindless_texture)\\b.*",
            "// extension directive commented for GLES compatibility"
        );

        // 2. Remove 'noperspective' interpolation qualifier (reserved keyword in GLES 3.2)
        source = source.replaceAll("\\bnoperspective\\b", "             ");

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

        // 4. Precision statement normalization for fragment shaders
        if ((source.contains("gl_FragColor") || source.contains("gl_FragData") || source.contains("out vec4") || source.contains("Fragment")) && !source.contains("precision ")) {
            source = insertAfterVersionLine(source,
                "#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\nprecision highp int;\n#endif"
            );
        }

        return source;
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
