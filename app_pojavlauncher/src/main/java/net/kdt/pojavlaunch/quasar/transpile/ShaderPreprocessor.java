package net.kdt.pojavlaunch.quasar.transpile;

import android.opengl.GLES20;

/**
 * ShaderPreprocessor provides pre-compilation modifications for GLSL source code
 * before it is submitted to glShaderSource on Android (GLES 3.2 / LTW / Mali-G615 MC2).
 *
 * It resolves:
 * 1. Complementary Shaders compilation errors (GL_NV_shader_noperspective_interpolation, noperspective keyword).
 * 2. Visual glitches (green patches, TV wave scanline effects) on Mali GPUs via safe division.
 * 3. Texture LOD extension mapping (GL_ARB_shader_texture_lod -> GL_EXT_shader_texture_lod).
 * 4. GLES fragment output declarations and precision injection.
 */
public class ShaderPreprocessor {

    public static String fix(String source, int shaderType) {
        if (source == null || source.isEmpty()) {
            return source;
        }

        String result = source;

        // FIX 1: noperspective -> smooth (GLES 3.2 defaults to smooth interpolation)
        result = result.replaceAll("\\bnoperspective\\b", "smooth");

        // FIX 2: ARB texture LOD -> EXT extension for GLES
        result = result.replaceAll(
            "#extension\\s+GL_ARB_shader_texture_lod\\s*:\\s*\\w+",
            "#extension GL_EXT_shader_texture_lod : enable"
        );

        // FIX 3: Legacy texture2DLod / textureCubeLod -> textureLod
        result = result.replaceAll("\\btexture2DLod\\s*\\(", "textureLod(");
        result = result.replaceAll("\\btextureCubeLod\\s*\\(", "textureLod(");

        // FIX 4: Disable unsupported NV extension safely after #version directive
        if (!result.contains("GL_NV_shader_noperspective_interpolation")) {
            result = insertAfterVersionLine(result, "#extension GL_NV_shader_noperspective_interpolation : disable");
        } else {
            result = result.replaceAll(
                "#extension\\s+GL_NV_shader_noperspective_interpolation\\s*:\\s*(enable|require)",
                "#extension GL_NV_shader_noperspective_interpolation : disable"
            );
        }

        // FIX 5: GLES fragment shader precision & layout output
        if (shaderType == GLES20.GL_FRAGMENT_SHADER) {
            if (!result.contains("precision highp float;")) {
                result = insertAfterVersionLine(result, "precision highp float;");
            }
            if (result.contains("out vec4 fragColor;") && !result.contains("layout(location = 0)")) {
                result = result.replace(
                    "out vec4 fragColor;",
                    "layout(location = 0) out vec4 fragColor;"
                );
            }
        }

        // FIX 6: Safe division for scanline / TV wave / green patch visual artifacts on Mali GPUs
        result = result.replaceAll(
            "(\\w+)\\s*/\\s*gl_FragCoord\\.w",
            "$1 / max(gl_FragCoord.w, 0.001)"
        );

        // FIX 7: texture2D/Cube -> texture (for GLES modern versions)
        if (!result.contains("#version 120") && !result.contains("#version 130")) {
            result = result.replaceAll("\\btexture2D\\s*\\(", "texture(");
            result = result.replaceAll("\\btextureCube\\s*\\(", "texture(");
        }

        return result;
    }

    public static String fix(String source) {
        return fix(source, GLES20.GL_FRAGMENT_SHADER);
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
