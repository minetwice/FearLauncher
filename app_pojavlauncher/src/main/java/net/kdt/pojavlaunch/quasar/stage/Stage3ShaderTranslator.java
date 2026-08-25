package net.kdt.pojavlaunch.quasar.stage;

import android.util.Log;

import net.kdt.pojavlaunch.quasar.capability.CapabilityTable;
import net.kdt.pojavlaunch.quasar.transpile.ShaderPreprocessor;

/**
 * Stage 3: Desktop OpenGL to GLES/Vulkan Transpiler Net.
 *
 * Translates desktop GLSL (#version 120 - 460) to GLSL ES 3.00/3.10/3.20/Vulkan GLSL.
 * Strips desktop extensions (e.g. GL_NV_shader_noperspective_interpolation),
 * replaces unsupported keywords (e.g. noperspective -> smooth), converts texture sampler functions,
 * and translates MRT / gl_FragData constructs for Complementary & AstraLex shaders.
 */
public class Stage3ShaderTranslator {
    private static final String TAG = "Quasar-Stage3Translator";

    public static String translate(String sourceCode, Stage1FastDetector.DetectionResult detection, CapabilityTable capability) {
        if (sourceCode == null || sourceCode.isEmpty()) {
            return sourceCode;
        }

        Log.d(TAG, "[Stage 3] Translating shader for Android target: " + detection.shaderName);

        // Preprocess GLSL (fixes noperspective, GL_ARB_shader_texture_lod, textureLod, and NV extension warnings)
        String glsl = ShaderPreprocessor.fix(sourceCode);

        // Clean up remaining unsupported extensions
        glsl = removeLinesContaining(glsl, "#extension GL_ARB_");
        glsl = removeLinesContaining(glsl, "#extension GL_EXT_gpu_shader4");

        // Texture sampling functions conversion (desktop -> modern GLES/GLSL)
        glsl = glsl.replace("texture2D(", "texture(");
        glsl = glsl.replace("texture2DProj(", "textureProj(");
        glsl = glsl.replace("texture2DGrad(", "textureGrad(");
        glsl = glsl.replace("textureCube(", "texture(");
        glsl = glsl.replace("texture3D(", "texture(");
        glsl = glsl.replace("texture1D(", "texture(");
        glsl = glsl.replace("shadow2D(", "texture(");
        glsl = glsl.replace("shadow2DProj(", "textureProj(");

        // 4. Version directive adjustment
        boolean isCompute = detection.isCompute;
        String targetVersion = isCompute ? "#version 310 es" : "#version 300 es";

        if (glsl.contains("#version")) {
            int verIndex = glsl.indexOf("#version");
            int lineEnd = glsl.indexOf("\n", verIndex);
            if (lineEnd != -1) {
                glsl = glsl.substring(0, verIndex) + targetVersion + glsl.substring(lineEnd);
            }
        } else {
            glsl = targetVersion + "\n" + glsl;
        }

        // 5. Shader stage attribute/varying/MRT conversion
        if (detection.shaderStage == 0) { // Vertex
            glsl = glsl.replaceAll("\\battribute\\b", "in");
            glsl = glsl.replaceAll("\\bvarying\\b", "out");
        } else if (detection.shaderStage == 4) { // Fragment
            glsl = glsl.replaceAll("\\bvarying\\b", "in");

            // Handle Multiple Render Targets (gl_FragData[0..7]) for Complementary & AstraLex
            for (int i = 0; i < 8; i++) {
                String fragDataStr = "gl_FragData[" + i + "]";
                if (glsl.contains(fragDataStr)) {
                    String targetOut = "quasar_FragData" + i;
                    String decl = "layout(location = " + i + ") out vec4 " + targetOut + ";";
                    if (!glsl.contains(targetOut)) {
                        glsl = insertAfterVersion(glsl, targetVersion, decl);
                    }
                    glsl = glsl.replace(fragDataStr, targetOut);
                }
            }

            if (glsl.contains("gl_FragColor") && !glsl.contains("quasar_FragData0")) {
                if (!glsl.contains("out vec4 FragColor")) {
                    glsl = insertAfterVersion(glsl, targetVersion, "layout(location = 0) out vec4 FragColor;");
                }
                glsl = glsl.replace("gl_FragColor", "FragColor");
            }
        }

        Log.d(TAG, "[Stage 3] Translation complete for " + detection.shaderName);
        return glsl;
    }

    private static String removeLinesContaining(String code, String substring) {
        int pos = 0;
        StringBuilder sb = new StringBuilder(code);
        while ((pos = sb.indexOf(substring, pos)) != -1) {
            int lineStart = sb.lastIndexOf("\n", pos);
            if (lineStart == -1) lineStart = 0;
            else lineStart += 1;

            int lineEnd = sb.indexOf("\n", pos);
            if (lineEnd == -1) lineEnd = sb.length();

            sb.delete(lineStart, lineEnd < sb.length() ? lineEnd + 1 : lineEnd);
            pos = lineStart;
        }
        return sb.toString();
    }

    private static String insertAfterVersion(String code, String versionStr, String insertText) {
        int pos = code.indexOf(versionStr);
        if (pos == -1) return insertText + "\n" + code;
        int lineEnd = code.indexOf("\n", pos);
        if (lineEnd == -1) return code + "\n" + insertText;
        return code.substring(0, lineEnd + 1) + insertText + "\n" + code.substring(lineEnd + 1);
    }
}
