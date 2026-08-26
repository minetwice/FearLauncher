package net.kdt.pojavlaunch.quasar;

/**
 * Complementary shader implementation for Mali GPU compatibility.
 * This shader provides complementary color blending that works on ARM Mali GPUs
 * which lack certain GLSL extensions.
 */
public class ComplementaryShader {
    
    /**
     * Fragment shader source for complementary color effect.
     * Optimized for OpenGL ES 3.0+ and Mali GPUs.
     */
    public static final String FRAGMENT_SHADER = 
        "#version 300 es\n" +
        "precision highp float;\n" +
        "\n" +
        "uniform sampler2D u_texture;\n" +
        "in vec2 v_texCoord;\n" +
        "out vec4 fragColor;\n" +
        "\n" +
        "uniform vec2 texelSize;\n" +
        "\n" +
        "void main() {\n" +
        "    vec4 left = texture(u_texture, v_texCoord + vec2(-texelSize.x, 0.0));\n" +
        "    vec4 right = texture(u_texture, v_texCoord + vec2(texelSize.x, 0.0));\n" +
        "    vec4 top = texture(u_texture, v_texCoord + vec2(0.0, texelSize.y));\n" +
        "    vec4 bottom = texture(u_texture, v_texCoord + vec2(0.0, -texelSize.y));\n" +
        "    \n" +
        "    float horizontalEdge = abs(length(right.rgb - left.rgb));\n" +
        "    float verticalEdge = abs(length(top.rgb - bottom.rgb));\n" +
        "    \n" +
        "    vec3 color = texture(u_texture, v_texCoord).rgb;\n" +
        "    \n" +
        "    vec3 complementary = 1.0 - color;\n" +
        "    \n" +
        "    float edgeFactor = max(horizontalEdge, verticalEdge);\n" +
        "    vec3 finalColor = mix(color, complementary, edgeFactor * 0.5);\n" +
        "    \n" +
        "    fragColor = vec4(finalColor, 1.0);\n" +
        "}\n";
    
    /**
     * Vertex shader source for complementary shader.
     */
    public static final String VERTEX_SHADER = 
        "#version 300 es\n" +
        "precision highp float;\n" +
        "\n" +
        "in vec2 a_position;\n" +
        "in vec2 a_texCoord;\n" +
        "out vec2 v_texCoord;\n" +
        "\n" +
        "uniform mat4 u_projection;\n" +
        "uniform mat4 u_modelView;\n" +
        "\n" +
        "void main() {\n" +
        "    v_texCoord = a_texCoord;\n" +
        "    gl_Position = u_projection * u_modelView * vec4(a_position, 0.0, 1.0);\n" +
        "}\n";
    
    /**
     * Gets the complementary color of a given RGB color.
     * This is a CPU-side implementation for reference.
     */
    public static float[] getComplementaryColor(float[] rgb) {
        float[] complementary = new float[3];
        for (int i = 0; i < 3; i++) {
            complementary[i] = 1.0f - rgb[i];
        }
        return complementary;
    }
    
    /**
     * Mali-specific adjustments for shader compatibility.
     */
    public static String getMaliCompatibleShader(String shaderSource) {
        String adjusted = shaderSource
            .replace("texture2D(", "texture(")
            .replace("gl_FragColor", "fragColor")
            .replace("varying ", "in ")
            .replace("attribute ", "in ")
            .replace("#version 120", "#version 300 es")
            .replace("#version 130", "#version 300 es")
            .replace("#version 140", "#version 300 es")
            .replace("#version 150", "#version 300 es");
        
        if (!adjusted.contains("precision")) {
            adjusted = "precision highp float;\n" + adjusted;
        }
        
        return adjusted;
    }
}