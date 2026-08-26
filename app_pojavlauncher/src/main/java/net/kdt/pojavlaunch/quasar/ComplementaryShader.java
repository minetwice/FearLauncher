package net.kdt.pojavlaunch.quasar;

import android.opengl.GLES20;
import android.util.Log;

/**
 * ComplementaryShader - Specialized shader for complementary color effects
 * 
 * This shader inverts the colors of the rendered scene, which is useful for:
 * - Complementary color shaders in Minecraft
 * - Negative/film effects
 * - Color inversion post-processing
 * 
 * Works on Mali GPUs with proper fallbacks
 */
public class ComplementaryShader {
    private static final String TAG = "ComplementaryShader";
    
    // Shader program handle
    private int programHandle = 0;
    
    // Attribute and uniform locations
    private int positionAttrib = -1;
    private int texCoordAttrib = -1;
    private int textureUniform = -1;
    
    // Vertex shader source
    private static final String VERTEX_SHADER = 
        "attribute vec4 position;\n" +
        "attribute vec2 texCoord;\n" +
        "varying vec2 v_texCoord;\n" +
        "\n" +
        "void main() {\n" +
        "    gl_Position = position;\n" +
        "    v_texCoord = texCoord;\n" +
        "}\n";
    
    // Fragment shader source - Complementary colors
    private static final String FRAGMENT_SHADER = 
        "precision highp float;\n" +
        "varying vec2 v_texCoord;\n" +
        "uniform sampler2D u_texture;\n" +
        "\n" +
        "void main() {\n" +
        "    vec4 color = texture2D(u_texture, v_texCoord);\n" +
        "    // Invert RGB channels, keep alpha\n" +
        "    gl_FragColor = vec4(1.0 - color.r, 1.0 - color.g, 1.0 - color.b, color.a);\n" +
        "}\n";
    
    // Fragment shader with time-based animation
    private static final String FRAGMENT_SHADER_ANIMATED = 
        "precision highp float;\n" +
        "varying vec2 v_texCoord;\n" +
        "uniform sampler2D u_texture;\n" +
        "uniform float u_time;\n" +
        "\n" +
        "void main() {\n" +
        "    vec4 color = texture2D(u_texture, v_texCoord);\n" +
        "    // Animated complementary effect\n" +
        "    float effect = sin(u_time * 2.0) * 0.5 + 0.5;\n" +
        "    vec3 inverted = 1.0 - color.rgb;\n" +
        "    gl_FragColor = vec4(mix(color.rgb, inverted, effect), color.a);\n" +
        "}\n";
    
    // Fragment shader with edge detection
    private static final String FRAGMENT_SHADER_EDGE = 
        "precision highp float;\n" +
        "varying vec2 v_texCoord;\n" +
        "uniform sampler2D u_texture;\n" +
        "uniform vec2 u_textureSize;\n" +
        "\n" +
        "void main() {\n" +
        "    vec2 texelSize = 1.0 / u_textureSize;\n" +
        "    vec4 color = texture2D(u_texture, v_texCoord);\n" +
        "    \n" +
        "    // Sample neighboring pixels\n" +
        "    vec4 left = texture2D(u_texture, v_texCoord + vec2(-texelSize.x, 0.0));
" +
        "    vec4 right = texture2D(u_texture, v_texCoord + vec2(texelSize.x, 0.0));
" +
        "    vec4 top = texture2D(u_texture, v_texCoord + vec2(0.0, texelSize.y));
" +
        "    vec4 bottom = texture2D(u_texture, v_texCoord + vec2(0.0, -texelSize.y));
" +
        "    \n" +
        "    // Edge detection\n" +
        "    float edge = abs(length(left.rgb - right.rgb)) + \n" +
        "                 abs(length(top.rgb - bottom.rgb));
" +
        "    \n" +
        "    // Invert colors with edge enhancement\n" +
        "    vec3 inverted = 1.0 - color.rgb;\n" +
        "    gl_FragColor = vec4(inverted + edge * 0.5, color.a);\n" +
        "}\n";
    
    /**
     * Create a complementary shader
     */
    public ComplementaryShader() {
        programHandle = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if (programHandle > 0) {
            positionAttrib = GLES20.glGetAttribLocation(programHandle, "position");
            texCoordAttrib = GLES20.glGetAttribLocation(programHandle, "texCoord");
            textureUniform = GLES20.glGetUniformLocation(programHandle, "u_texture");
            Log.d(TAG, "Complementary shader created successfully");
        } else {
            Log.e(TAG, "Failed to create complementary shader");
        }
    }
    
    /**
     * Create a complementary shader with animation support
     */
    public ComplementaryShader(boolean animated) {
        if (animated) {
            programHandle = createProgram(VERTEX_SHADER, FRAGMENT_SHADER_ANIMATED);
        } else {
            programHandle = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        }
        
        if (programHandle > 0) {
            positionAttrib = GLES20.glGetAttribLocation(programHandle, "position");
            texCoordAttrib = GLES20.glGetAttribLocation(programHandle, "texCoord");
            textureUniform = GLES20.glGetUniformLocation(programHandle, "u_texture");
            Log.d(TAG, "Complementary shader created (animated: " + animated + ")");
        } else {
            Log.e(TAG, "Failed to create complementary shader");
        }
    }
    
    /**
     * Create a complementary shader with edge detection
     */
    public ComplementaryShader(boolean animated, boolean edgeDetection) {
        if (edgeDetection) {
            programHandle = createProgram(VERTEX_SHADER, FRAGMENT_SHADER_EDGE);
        } else if (animated) {
            programHandle = createProgram(VERTEX_SHADER, FRAGMENT_SHADER_ANIMATED);
        } else {
            programHandle = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        }
        
        if (programHandle > 0) {
            positionAttrib = GLES20.glGetAttribLocation(programHandle, "position");
            texCoordAttrib = GLES20.glGetAttribLocation(programHandle, "texCoord");
            textureUniform = GLES20.glGetUniformLocation(programHandle, "u_texture");
            Log.d(TAG, "Complementary shader created (animated: " + animated + 
                  ", edgeDetection: " + edgeDetection + ")");
        } else {
            Log.e(TAG, "Failed to create complementary shader");
        }
    }
    
    /**
     * Create a shader program from vertex and fragment sources
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
     * Set the time uniform (for animated version)
     */
    public void setTime(float time) {
        int timeUniform = GLES20.glGetUniformLocation(programHandle, "u_time");
        if (timeUniform >= 0) {
            GLES20.glUniform1f(timeUniform, time);
        }
    }
    
    /**
     * Set the texture size uniform (for edge detection version)
     */
    public void setTextureSize(float width, float height) {
        int textureSizeUniform = GLES20.glGetUniformLocation(programHandle, "u_textureSize");
        if (textureSizeUniform >= 0) {
            GLES20.glUniform2f(textureSizeUniform, width, height);
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
            Log.d(TAG, "Complementary shader deleted");
        }
    }
    
    /**
     * Get the vertex shader source
     */
    public static String getVertexShader() {
        return VERTEX_SHADER;
    }
    
    /**
     * Get the fragment shader source
     */
    public static String getFragmentShader() {
        return FRAGMENT_SHADER;
    }
    
    /**
     * Get the animated fragment shader source
     */
    public static String getFragmentShaderAnimated() {
        return FRAGMENT_SHADER_ANIMATED;
    }
    
    /**
     * Get the edge detection fragment shader source
     */
    public static String getFragmentShaderEdge() {
        return FRAGMENT_SHADER_EDGE;
    }
}
