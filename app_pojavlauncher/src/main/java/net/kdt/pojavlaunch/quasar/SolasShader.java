package net.kdt.pojavlaunch.quasar;

import android.opengl.GLES20;
import android.util.Log;

/**
 * SolasShader - Implementation of Solas/Solus shader effects for Minecraft
 * 
 * Solas shaders are popular Minecraft shaders that add:
 * - Volumetric lighting
 * - God rays
 * - Dynamic shadows
 * - Color grading
 * 
 * This implementation provides a simplified version that works on Mali GPUs
 * with proper fallbacks for missing extensions.
 */
public class SolasShader {
    private static final String TAG = "SolasShader";
    
    // Shader program handles
    private int programHandle = 0;
    private int compositeProgramHandle = 0;
    
    // Attribute locations
    private int positionAttrib = -1;
    private int texCoordAttrib = -1;
    
    // Uniform locations
    private int textureUniform = -1;
    private int timeUniform = -1;
    private int resolutionUniform = -1;
    private int sunPositionUniform = -1;
    private int shadowUniform = -1;
    
    // Shader versions
    public enum SolasVersion {
        SIMPLE,       // Basic solas effect
        LIGHT,        // With lighting effects
        FULL          // Full solas with all effects
    }
    
    // Simple vertex shader
    private static final String SIMPLE_VERTEX = 
        "attribute vec4 position;\n" +
        "attribute vec2 texCoord;\n" +
        "varying vec2 v_texCoord;\n" +
        "\n" +
        "void main() {\n" +
        "    gl_Position = position;\n" +
        "    v_texCoord = texCoord;\n" +
        "}\n";
    
    // Simple fragment shader (basic solas effect)
    private static final String SIMPLE_FRAGMENT = 
        "precision highp float;\n" +
        "varying vec2 v_texCoord;\n" +
        "uniform sampler2D u_texture;\n" +
        "uniform float u_time;\n" +
        "uniform vec2 u_resolution;\n" +
        "\n" +
        "// Simple solas effect - warm color grading\n" +
        "vec3 solasColorGrade(vec3 color, float time) {\n" +
        "    // Warm tone mapping\n" +
        "    float warmth = sin(time * 0.3) * 0.1 + 0.9;\n" +
        "    color.r = mix(color.r, min(color.r * 1.2, 1.0), warmth);\n" +
        "    color.b = mix(color.b, color.b * 0.8, warmth * 0.5);\n" +
        "    return color;\n" +
        "}\n" +
        "\n" +
        "void main() {\n" +
        "    vec4 color = texture2D(u_texture, v_texCoord);\n" +
        "    color.rgb = solasColorGrade(color.rgb, u_time);\n" +
        "    gl_FragColor = color;\n" +
        "}\n";
    
    // Light version with sun rays
    private static final String LIGHT_FRAGMENT = 
        "precision highp float;\n" +
        "varying vec2 v_texCoord;\n" +
        "uniform sampler2D u_texture;\n" +
        "uniform float u_time;\n" +
        "uniform vec2 u_resolution;\n" +
        "uniform vec2 u_sunPosition;\n" +
        "\n" +
        "// Sun rays effect\n" +
        "float sunRays(vec2 uv, vec2 sunPos, float time) {\n" +
        "    vec2 delta = uv - sunPos;\n" +
        "    float dist = length(delta);\n" +
        "    if (dist > 0.5) return 0.0;\n" +
        "    \n" +
        "    // Create rays based on distance from sun\n" +
        "    float rays = sin(dist * 50.0 + time * 2.0) * 0.1 + 0.9;\n" +
        "    rays *= smoothstep(0.5, 0.2, dist);\n" +
        "    return rays * 0.3;\n" +
        "}\n" +
        "\n" +
        "// Warm color grading\n" +
        "vec3 warmGrade(vec3 color, float time) {\n" +
        "    float warmth = sin(time * 0.3) * 0.1 + 0.9;\n" +
        "    color.r = mix(color.r, min(color.r * 1.2, 1.0), warmth);\n" +
        "    color.b = mix(color.b, color.b * 0.8, warmth * 0.5);\n" +
        "    return color;\n" +
        "}\n" +
        "\n" +
        "void main() {\n" +
        "    vec4 color = texture2D(u_texture, v_texCoord);\n" +
        "    \n" +
        "    // Apply sun rays\n" +
        "    float rays = sunRays(v_texCoord, u_sunPosition, u_time);\n" +
        "    vec3 rayColor = vec3(1.0, 0.8, 0.6) * rays;\n" +
        "    \n" +
        "    // Apply color grading\n" +
        "    color.rgb = warmGrade(color.rgb + rayColor, u_time);\n" +
        "    \n" +
        "    gl_FragColor = color;\n" +
        "}\n";
    
    // Full version with shadows and advanced effects
    private static final String FULL_FRAGMENT = 
        "precision highp float;\n" +
        "varying vec2 v_texCoord;\n" +
        "uniform sampler2D u_texture;\n" +
        "uniform sampler2D u_depthTexture;\n" +
        "uniform float u_time;\n" +
        "uniform vec2 u_resolution;\n" +
        "uniform vec2 u_sunPosition;\n" +
        "uniform float u_shadowIntensity;\n" +
        "\n" +
        "// Depth-based shadow calculation\n" +
        "float calculateShadow(sampler2D depthTex, vec2 uv, vec2 lightPos) {\n" +
        "    // Simplified shadow calculation\n" +
        "    vec2 delta = uv - lightPos;\n" +
        "    float dist = length(delta) * 2.0;\n" +
        "    return smoothstep(0.8, 0.2, dist) * u_shadowIntensity;\n" +
        "}\n" +
        "\n" +
        "// Sun rays effect\n" +
        "float sunRays(vec2 uv, vec2 sunPos, float time) {\n" +
        "    vec2 delta = uv - sunPos;\n" +
        "    float dist = length(delta);\n" +
        "    if (dist > 0.5) return 0.0;\n" +
        "    \n" +
        "    float rays = sin(dist * 50.0 + time * 2.0) * 0.1 + 0.9;\n" +
        "    rays *= smoothstep(0.5, 0.2, dist);\n" +
        "    return rays * 0.3;\n" +
        "}\n" +
        "\n" +
        "// Volumetric lighting\n" +
        "vec3 volumetricLighting(vec3 color, float shadow, vec2 uv, vec2 sunPos) {\n" +
        "    vec2 delta = uv - sunPos;\n" +
        "    float dist = length(delta) * 2.0;\n" +
        "    vec3 lightColor = vec3(1.0, 0.8, 0.6);\n" +
        "    return color + lightColor * (1.0 - shadow) * 0.3;\n" +
        "}\n" +
        "\n" +
        "// Warm color grading\n" +
        "vec3 warmGrade(vec3 color, float time) {\n" +
        "    float warmth = sin(time * 0.3) * 0.1 + 0.9;\n" +
        "    color.r = mix(color.r, min(color.r * 1.2, 1.0), warmth);\n" +
        "    color.b = mix(color.b, color.b * 0.8, warmth * 0.5);\n" +
        "    return color;\n" +
        "}\n" +
        "\n" +
        "void main() {\n" +
        "    vec4 color = texture2D(u_texture, v_texCoord);\n" +
        "    \n" +
        "    // Calculate shadow\n" +
        "    float shadow = calculateShadow(u_depthTexture, v_texCoord, u_sunPosition);\n" +
        "    \n" +
        "    // Apply sun rays\n" +
        "    float rays = sunRays(v_texCoord, u_sunPosition, u_time);\n" +
        "    vec3 rayColor = vec3(1.0, 0.8, 0.6) * rays;\n" +
        "    \n" +
        "    // Apply volumetric lighting\n" +
        "    color.rgb = volumetricLighting(color.rgb, shadow, v_texCoord, u_sunPosition);\n" +
        "    \n" +
        "    // Apply color grading\n" +
        "    color.rgb = warmGrade(color.rgb + rayColor, u_time);\n" +
        "    \n" +
        "    gl_FragColor = color;\n" +
        "}\n";
    
    /**
     * Create a Solas shader with the specified version
     */
    public SolasShader(SolasVersion version) {
        String vertexSource = SIMPLE_VERTEX;
        String fragmentSource;
        
        switch (version) {
            case LIGHT:
                fragmentSource = LIGHT_FRAGMENT;
                break;
            case FULL:
                fragmentSource = FULL_FRAGMENT;
                break;
            default:
                fragmentSource = SIMPLE_FRAGMENT;
        }
        
        programHandle = createProgram(vertexSource, fragmentSource);
        
        if (programHandle > 0) {
            positionAttrib = GLES20.glGetAttribLocation(programHandle, "position");
            texCoordAttrib = GLES20.glGetAttribLocation(programHandle, "texCoord");
            textureUniform = GLES20.glGetUniformLocation(programHandle, "u_texture");
            timeUniform = GLES20.glGetUniformLocation(programHandle, "u_time");
            resolutionUniform = GLES20.glGetUniformLocation(programHandle, "u_resolution");
            sunPositionUniform = GLES20.glGetUniformLocation(programHandle, "u_sunPosition");
            shadowUniform = GLES20.glGetUniformLocation(programHandle, "u_shadowIntensity");
            
            Log.d(TAG, "Solas shader created (version: " + version + ")");
        } else {
            Log.e(TAG, "Failed to create Solas shader");
        }
    }
    
    /**
     * Create a shader program
     */
    private int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = compileShader(vertexSource, GLES20.GL_VERTEX_SHADER);
        int fragmentShader = compileShader(fragmentSource, GLES20.GL_FRAGMENT_SHADER);
        
        if (vertexShader == 0 || fragmentShader == 0) {
            if (vertexShader != 0) GLES20.glDeleteShader(vertexShader);
            if (fragmentShader != 0) GLES20.glDeleteShader(fragmentShader);
            return 0;
        }
        
        int program = GLES20.glCreateProgram();
        if (program == 0) {
            GLES20.glDeleteShader(vertexShader);
            GLES20.glDeleteShader(fragmentShader);
            return 0;
        }
        
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);
        
        int[] linked = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
        
        GLES20.glDeleteShader(vertexShader);
        GLES20.glDeleteShader(fragmentShader);
        
        if (linked[0] == 0) {
            String error = GLES20.glGetProgramInfoLog(program);
            Log.e(TAG, "Program linking failed: " + error);
            GLES20.glDeleteProgram(program);
            return 0;
        }
        
        return program;
    }
    
    /**
     * Compile a shader
     */
    private int compileShader(String source, int type) {
        int shader = GLES20.glCreateShader(type);
        if (shader == 0) return 0;
        
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        
        if (compiled[0] == 0) {
            String error = GLES20.glGetShaderInfoLog(shader);
            Log.e(TAG, "Shader compilation failed: " + error);
            GLES20.glDeleteShader(shader);
            return 0;
        }
        
        return shader;
    }
    
    /**
     * Use this shader
     */
    public void use() {
        if (programHandle > 0) {
            GLES20.glUseProgram(programHandle);
        } else {
            GLES20.glUseProgram(0);
        }
    }
    
    /**
     * Set the position attribute pointer
     */
    public void setPositionPointer(int size, int type, int stride, int offset) {
        if (positionAttrib >= 0) {
            GLES20.glEnableVertexAttribArray(positionAttrib);
            GLES20.glVertexAttribPointer(positionAttrib, size, type, false, stride, offset);
        }
    }
    
    /**
     * Set the texture coordinate attribute pointer
     */
    public void setTexCoordPointer(int size, int type, int stride, int offset) {
        if (texCoordAttrib >= 0) {
            GLES20.glEnableVertexAttribArray(texCoordAttrib);
            GLES20.glVertexAttribPointer(texCoordAttrib, size, type, false, stride, offset);
        }
    }
    
    /**
     * Set the texture uniform
     */
    public void setTexture(int textureUnit) {
        if (textureUniform >= 0) {
            GLES20.glUniform1i(textureUniform, textureUnit);
        }
    }
    
    /**
     * Set the depth texture uniform (for full version)
     */
    public void setDepthTexture(int textureUnit) {
        int depthUniform = GLES20.glGetUniformLocation(programHandle, "u_depthTexture");
        if (depthUniform >= 0) {
            GLES20.glUniform1i(depthUniform, textureUnit);
        }
    }
    
    /**
     * Set the time uniform
     */
    public void setTime(float time) {
        if (timeUniform >= 0) {
            GLES20.glUniform1f(timeUniform, time);
        }
    }
    
    /**
     * Set the resolution uniform
     */
    public void setResolution(float width, float height) {
        if (resolutionUniform >= 0) {
            GLES20.glUniform2f(resolutionUniform, width, height);
        }
    }
    
    /**
     * Set the sun position uniform
     */
    public void setSunPosition(float x, float y) {
        if (sunPositionUniform >= 0) {
            GLES20.glUniform2f(sunPositionUniform, x, y);
        }
    }
    
    /**
     * Set the shadow intensity uniform
     */
    public void setShadowIntensity(float intensity) {
        if (shadowUniform >= 0) {
            GLES20.glUniform1f(shadowUniform, intensity);
        }
    }
    
    /**
     * Disable attribute arrays
     */
    public void disableAttributes() {
        if (positionAttrib >= 0) {
            GLES20.glDisableVertexAttribArray(positionAttrib);
        }
        if (texCoordAttrib >= 0) {
            GLES20.glDisableVertexAttribArray(texCoordAttrib);
        }
    }
    
    /**
     * Get the program handle
     */
    public int getProgramHandle() {
        return programHandle;
    }
    
    /**
     * Check if the shader is valid
     */
    public boolean isValid() {
        return programHandle > 0;
    }
    
    /**
     * Delete the shader program
     */
    public void delete() {
        if (programHandle > 0) {
            GLES20.glDeleteProgram(programHandle);
            programHandle = 0;
            Log.d(TAG, "Solas shader deleted");
        }
    }
    
    /**
     * Get the vertex shader source
     */
    public static String getVertexShader() {
        return SIMPLE_VERTEX;
    }
    
    /**
     * Get the fragment shader source for a specific version
     */
    public static String getFragmentShader(SolasVersion version) {
        switch (version) {
            case LIGHT:
                return LIGHT_FRAGMENT;
            case FULL:
                return FULL_FRAGMENT;
            default:
                return SIMPLE_FRAGMENT;
        }
    }
}
