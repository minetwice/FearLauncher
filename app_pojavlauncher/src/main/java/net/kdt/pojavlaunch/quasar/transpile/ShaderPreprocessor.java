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

        // 1. Remove 'noperspective' keyword (reserved in GLES)
        source = source.replaceAll("\\bnoperspective\\b", " ");

        // 2. Replace ARB texture LOD extension with EXT version for GLES
        source = source.replaceAll(
            "#extension\\s+GL_ARB_shader_texture_lod\\s*:",
            "#extension GL_EXT_shader_texture_lod :"
        );

        // 3. Replace texture2DLod and textureCubeLod with textureLod
        source = source.replaceAll("\\btexture2DLod\\s*\\(", "textureLod(");
        source = source.replaceAll("\\btextureCubeLod\\s*\\(", "textureLod(");

        // 4. Disable NV shader noperspective interpolation directive or suppress warning safely after #version
        if (!source.contains("GL_NV_shader_noperspective_interpolation")) {
            source = insertAfterVersionLine(source, "#extension GL_NV_shader_noperspective_interpolation : disable");
        } else {
            source = source.replaceAll(
                "#extension\\s+GL_NV_shader_noperspective_interpolation\\s*:\\s*(enable|require)",
                "#extension GL_NV_shader_noperspective_interpolation : disable"
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
