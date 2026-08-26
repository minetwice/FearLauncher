package net.kdt.pojavlaunch.quasar;

/**
 * Astra shader implementation for Mali GPU compatibility.
 * Provides celestial/sky effects that work on ARM Mali GPUs.
 */
public class AstraShader {
    
    /**
     * Fragment shader for Astra sky effects.
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
        "uniform vec3 u_sunPosition;\n" +
        "uniform vec3 u_moonPosition;\n" +
        "uniform float u_timeOfDay;\n" +
        "uniform float u_starIntensity;\n" +
        "\n" +
        "float hash(float n) { return fract(sin(n) * 43758.5453); }\n" +
        "\n" +
        "float noise(vec2 x) {\n" +
        "    vec2 p = floor(x);\n" +
        "    vec2 f = fract(x);\n" +
        "    f = f * f * (3.0 - 2.0 * f);\n" +
        "    float n = p.x + p.y * 57.0;\n" +
        "    return mix(mix(hash(n + 0.0), hash(n + 1.0), f.x),\n" +
        "               mix(hash(n + 57.0), hash(n + 58.0), f.x), f.y);\n" +
        "}\n" +
        "\n" +
        "void main() {\n" +
        "    vec2 uv = v_texCoord;\n" +
        "    \n" +
        "    vec3 sky = mix(vec3(0.1, 0.2, 0.4), vec3(0.0, 0.0, 0.0), uv.y);\n" +
        "    \n" +
        "    float sunDist = length(uv - u_sunPosition.xy);\n" +
        "    vec3 sunColor = vec3(1.0, 0.8, 0.6) * exp(-sunDist * sunDist * 10.0);\n" +
        "    \n" +
        "    float moonDist = length(uv - u_moonPosition.xy);\n" +
        "    vec3 moonColor = vec3(0.8, 0.8, 1.0) * exp(-moonDist * moonDist * 8.0);\n" +
        "    \n" +
        "    float stars = noise(uv * 100.0) * u_starIntensity;\n" +
        "    stars = smoothstep(0.0, 0.5, stars);\n" +
        "    vec3 starColor = vec3(1.0) * stars * 0.3;\n" +
        "    \n" +
        "    vec3 color = sky + sunColor + moonColor + starColor;\n" +
        "    \n" +
        "    fragColor = vec4(color, 1.0);\n" +
        "}\n";
    
    /**
     * Vertex shader for Astra shader.
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
     * Mali-specific adjustments for Astra shader.
     */
    public static String getMaliCompatibleAstraShader(String shaderSource) {
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
        
        adjusted = adjusted.replace("noise3(", "noise(");
        adjusted = adjusted.replace("pnoise(", "noise(");
        
        return adjusted;
    }
}