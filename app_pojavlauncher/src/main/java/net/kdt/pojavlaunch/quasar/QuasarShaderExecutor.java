package net.kdt.pojavlaunch.quasar;

import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLES31;
import android.util.Log;

import net.kdt.pojavlaunch.quasar.transpile.ShaderPreprocessor;

import java.util.HashMap;
import java.util.Map;

/**
 * QuasarShaderExecutor - Universal shader execution system for Mali/Adreno GPUs
 * 
 * This class provides:
 * - Automatic shader fixing for Mali GPU limitations
 * - Support for complementary colors, solas, and custom shaders
 * - Runtime shader compilation and linking
 * - Error handling and fallback mechanisms
 */
public class QuasarShaderExecutor {
    private static final String TAG = "QuasarShaderExecutor";
    
    // Shader program cache
    private static final Map<String, Integer> shaderProgramCache = new HashMap<>();
    
    // Built-in shader templates for common use cases
    public static class BuiltinShaders {
        // Complementary color shader (inverts colors)
        public static final String COMPLEMENTARY_FRAGMENT = 
            "precision highp float;\n" +
            "varying vec2 v_texCoord;\n" +
            "uniform sampler2D u_texture;\n" +
            "void main() {\n" +
            "    vec4 color = texture2D(u_texture, v_texCoord);\n" +
            "    gl_FragColor = vec4(1.0 - color.rgb, color.a);\n" +
            "}\n";
        
        // Solas shader (simplified version for mobile)
        public static final String SOLAS_FRAGMENT = 
            "precision highp float;\n" +
            "varying vec2 v_texCoord;\n" +
            "uniform sampler2D u_texture;\n" +
            "uniform float u_time;\n" +
            "void main() {\n" +
            "    vec2 uv = v_texCoord;\n" +
            "    vec4 texColor = texture2D(u_texture, uv);\n" +
            "    // Simple solas effect - add time-based color shift\n" +
            "    float timeFactor = sin(u_time * 0.5) * 0.1 + 0.9;\n" +
            "    gl_FragColor = vec4(texColor.rgb * timeFactor, texColor.a);\n" +
            "}\n";
        
        // Pass-through shader (default)
        public static final String PASSTHROUGH_FRAGMENT = 
            "precision highp float;\n" +
            "varying vec2 v_texCoord;\n" +
            "uniform sampler2D u_texture;\n" +
            "void main() {\n" +
            "    gl_FragColor = texture2D(u_texture, v_texCoord);\n" +
            "}\n";
        
        // Simple vertex shader
        public static final String SIMPLE_VERTEX = 
            "attribute vec4 position;\n" +
            "attribute vec2 texCoord;\n" +
            "varying vec2 v_texCoord;\n" +
            "void main() {\n" +
            "    gl_Position = position;\n" +
            "    v_texCoord = texCoord;\n" +
            "}\n";
    }

    /**
     * Compile a shader with automatic Mali GPU fixes
     * 
     * @param shaderCode The GLSL shader source code
     * @param shaderType GLES20.GL_VERTEX_SHADER or GLES20.GL_FRAGMENT_SHADER
     * @return The compiled shader handle, or 0 if compilation failed
     */
    public static int compileShader(String shaderCode, int shaderType) {
        if (shaderCode == null || shaderCode.isEmpty()) {
            Log.e(TAG, "Shader code is null or empty");
            return 0;
        }

        // Apply Quasar shader preprocessor fixes for Mali/Adreno
        String fixedShader = ShaderPreprocessor.fix(shaderCode, shaderType);
        
        int shader = GLES20.glCreateShader(shaderType);
        if (shader == 0) {
            Log.e(TAG, "Failed to create shader object");
            return 0;
        }

        GLES20.glShaderSource(shader, fixedShader);
        GLES20.glCompileShader(shader);

        // Check compilation status
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);

        if (compiled[0] == 0) {
            String error = GLES20.glGetShaderInfoLog(shader);
            Log.e(TAG, "Shader compilation failed: " + error);
            Log.e(TAG, "Fixed shader code:\n" + fixedShader);
            GLES20.glDeleteShader(shader);
            
            // Try with more aggressive fixes
            String aggressiveFix = applyAggressiveFixes(shaderCode, shaderType, error);
            if (!aggressiveFix.equals(shaderCode)) {
                return compileShader(aggressiveFix, shaderType);
            }
            return 0;
        }

        Log.d(TAG, "Shader compiled successfully (type: " + (shaderType == GLES20.GL_VERTEX_SHADER ? "vertex" : "fragment") + ")");
        return shader;
    }

    /**
     * Create a shader program from vertex and fragment shaders
     * 
     * @param vertexShaderCode Vertex shader source
     * @param fragmentShaderCode Fragment shader source
     * @return The linked program handle, or 0 if failed
     */
    public static int createProgram(String vertexShaderCode, String fragmentShaderCode) {
        return createProgram(vertexShaderCode, fragmentShaderCode, null);
    }

    /**
     * Create a shader program with geometry shader support (if available)
     * 
     * @param vertexShaderCode Vertex shader source
     * @param fragmentShaderCode Fragment shader source
     * @param geometryShaderCode Geometry shader source (can be null)
     * @return The linked program handle, or 0 if failed
     */
    public static int createProgram(String vertexShaderCode, String fragmentShaderCode, String geometryShaderCode) {
        int vertexShader = compileShader(vertexShaderCode, GLES20.GL_VERTEX_SHADER);
        int fragmentShader = compileShader(fragmentShaderCode, GLES20.GL_FRAGMENT_SHADER);
        
        if (vertexShader == 0 || fragmentShader == 0) {
            if (vertexShader != 0) GLES20.glDeleteShader(vertexShader);
            if (fragmentShader != 0) GLES20.glDeleteShader(fragmentShader);
            return 0;
        }

        int program = GLES20.glCreateProgram();
        if (program == 0) {
            GLES20.glDeleteShader(vertexShader);
            GLES20.glDeleteShader(fragmentShader);
            Log.e(TAG, "Failed to create program object");
            return 0;
        }

        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);

        // Attach geometry shader if provided
        if (geometryShaderCode != null && !geometryShaderCode.isEmpty()) {
            int geometryShader = compileShader(geometryShaderCode, GLES31.GL_GEOMETRY_SHADER);
            if (geometryShader != 0) {
                GLES20.glAttachShader(program, geometryShader);
            }
        }

        GLES20.glLinkProgram(program);

        // Check linking status
        int[] linked = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);

        if (linked[0] == 0) {
            String error = GLES20.glGetProgramInfoLog(program);
            Log.e(TAG, "Program linking failed: " + error);
            GLES20.glDeleteProgram(program);
            program = 0;
        }

        // Cleanup shaders
        GLES20.glDeleteShader(vertexShader);
        GLES20.glDeleteShader(fragmentShader);

        if (program != 0) {
            Log.d(TAG, "Shader program created and linked successfully");
        }
        
        return program;
    }

    /**
     * Create a shader program from cached or built-in shaders
     * 
     * @param programName Unique name for the program
     * @param vertexShaderCode Vertex shader source
     * @param fragmentShaderCode Fragment shader source
     * @return The program handle (cached or newly created)
     */
    public static int getOrCreateProgram(String programName, String vertexShaderCode, String fragmentShaderCode) {
        Integer cached = shaderProgramCache.get(programName);
        if (cached != null && cached > 0) {
            return cached;
        }
        
        int program = createProgram(vertexShaderCode, fragmentShaderCode);
        if (program > 0) {
            shaderProgramCache.put(programName, program);
        }
        return program;
    }

    /**
     * Load and compile a complementary color shader
     * 
     * @return Program handle for complementary shader, or 0 if failed
     */
    public static int createComplementaryShader() {
        return getOrCreateProgram(
            "quasar_complementary",
            BuiltinShaders.SIMPLE_VERTEX,
            BuiltinShaders.COMPLEMENTARY_FRAGMENT
        );
    }

    /**
     * Load and compile a solas shader
     * 
     * @return Program handle for solas shader, or 0 if failed
     */
    public static int createSolasShader() {
        return getOrCreateProgram(
            "quasar_solas",
            BuiltinShaders.SIMPLE_VERTEX,
            BuiltinShaders.SOLAS_FRAGMENT
        );
    }

    /**
     * Load and compile a pass-through shader (default)
     * 
     * @return Program handle for pass-through shader, or 0 if failed
     */
    public static int createPassthroughShader() {
        return getOrCreateProgram(
            "quasar_passthrough",
            BuiltinShaders.SIMPLE_VERTEX,
            BuiltinShaders.PASSTHROUGH_FRAGMENT
        );
    }

    /**
     * Use a shader program
     * 
     * @param programHandle The program handle to use
     */
    public static void useProgram(int programHandle) {
        if (programHandle > 0) {
            GLES20.glUseProgram(programHandle);
        } else {
            GLES20.glUseProgram(0);
            Log.w(TAG, "Attempted to use invalid program handle");
        }
    }

    /**
     * Delete a shader program and remove from cache
     * 
     * @param programName The name of the program to delete
     */
    public static void deleteProgram(String programName) {
        Integer program = shaderProgramCache.remove(programName);
        if (program != null && program > 0) {
            GLES20.glDeleteProgram(program);
            Log.d(TAG, "Deleted shader program: " + programName);
        }
    }

    /**
     * Clear all cached shader programs
     */
    public static void clearCache() {
        for (Integer program : shaderProgramCache.values()) {
            if (program > 0) {
                GLES20.glDeleteProgram(program);
            }
        }
        shaderProgramCache.clear();
        Log.d(TAG, "Cleared all shader program cache");
    }

    /**
     * Apply aggressive fixes based on compilation errors
     */
    private static String applyAggressiveFixes(String shaderCode, int shaderType, String error) {
        String fixed = shaderCode;
        
        if (error.contains("noperspective")) {
            fixed = fixed.replaceAll("\\bnoperspective\\b", "smooth");
        }
        
        if (error.contains("GL_ARB_shader_texture_lod")) {
            fixed = fixed.replaceAll(
                "#extension\\s+GL_ARB_shader_texture_lod",
                "#extension GL_EXT_shader_texture_lod"
            );
        }
        
        if (error.contains("texture2DLod")) {
            fixed = fixed.replaceAll("texture2DLod", "textureLod");
        }
        
        if (error.contains("gl_FragColor") && !fixed.contains("out vec4")) {
            fixed = "out vec4 gl_FragColor;\n" + fixed;
        }
        
        if (error.contains("precision") && shaderType == GLES20.GL_FRAGMENT_SHADER) {
            fixed = "precision highp float;\n" + fixed;
        }
        
        return fixed;
    }

    /**
     * Get the location of an attribute in a shader program
     */
    public static int getAttribLocation(int programHandle, String name) {
        if (programHandle > 0) {
            return GLES20.glGetAttribLocation(programHandle, name);
        }
        return -1;
    }

    /**
     * Get the location of a uniform in a shader program
     */
    public static int getUniformLocation(int programHandle, String name) {
        if (programHandle > 0) {
            return GLES20.glGetUniformLocation(programHandle, name);
        }
        return -1;
    }

    /**
     * Set a float uniform value
     */
    public static void setUniform1f(int programHandle, String name, float value) {
        int location = getUniformLocation(programHandle, name);
        if (location >= 0) {
            GLES20.glUniform1f(location, value);
        }
    }

    /**
     * Set a vec2 uniform value
     */
    public static void setUniform2f(int programHandle, String name, float x, float y) {
        int location = getUniformLocation(programHandle, name);
        if (location >= 0) {
            GLES20.glUniform2f(location, x, y);
        }
    }

    /**
     * Set a vec3 uniform value
     */
    public static void setUniform3f(int programHandle, String name, float x, float y, float z) {
        int location = getUniformLocation(programHandle, name);
        if (location >= 0) {
            GLES20.glUniform3f(location, x, y, z);
        }
    }

    /**
     * Set a vec4 uniform value
     */
    public static void setUniform4f(int programHandle, String name, float x, float y, float z, float w) {
        int location = getUniformLocation(programHandle, name);
        if (location >= 0) {
            GLES20.glUniform4f(location, x, y, z, w);
        }
    }

    /**
     * Set an integer uniform value
     */
    public static void setUniform1i(int programHandle, String name, int value) {
        int location = getUniformLocation(programHandle, name);
        if (location >= 0) {
            GLES20.glUniform1i(location, value);
        }
    }

    /**
     * Set a sampler uniform value (texture unit)
     */
    public static void setSamplerUniform(int programHandle, String name, int textureUnit) {
        setUniform1i(programHandle, name, textureUnit);
    }

    /**
     * Check if a shader program is valid
     */
    public static boolean isValidProgram(int programHandle) {
        if (programHandle <= 0) return false;
        
        int[] status = new int[1];
        GLES20.glGetProgramiv(programHandle, GLES20.GL_LINK_STATUS, status, 0);
        return status[0] != 0;
    }

    /**
     * Get the current active shader program
     */
    public static int getCurrentProgram() {
        int[] current = new int[1];
        GLES20.glGetIntegerv(GLES20.GL_CURRENT_PROGRAM, current, 0);
        return current[0];
    }
}
