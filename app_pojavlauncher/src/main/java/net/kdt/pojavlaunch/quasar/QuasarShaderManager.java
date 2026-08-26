package net.kdt.pojavlaunch.quasar;

import android.opengl.GLES20;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * QuasarShaderManager - Central shader management system for Quasar renderer
 * 
 * This class manages:
 * - Loading shaders from assets
 * - Caching compiled shaders
 * - Handling shader variations for different GPU capabilities
 * - Automatic fallback to simpler shaders when needed
 */
public class QuasarShaderManager {
    private static final String TAG = "QuasarShaderManager";
    
    // Shader cache: name -> program handle
    private final Map<String, Integer> shaderCache = new HashMap<>();
    
    // Shader source cache: name -> source code
    private final Map<String, String> shaderSourceCache = new HashMap<>();
    
    // GPU capability flags
    private boolean supportsGLES31 = false;
    private boolean isMaliGPU = false;
    private boolean isAdrenoGPU = false;
    
    // Singleton instance
    private static QuasarShaderManager instance;
    
    private QuasarShaderManager() {
        // Initialize GPU detection
        detectGPUCapabilities();
    }
    
    /**
     * Get the singleton instance
     */
    public static synchronized QuasarShaderManager getInstance() {
        if (instance == null) {
            instance = new QuasarShaderManager();
        }
        return instance;
    }
    
    /**
     * Detect GPU capabilities
     */
    private void detectGPUCapabilities() {
        try {
            String renderer = GLES20.glGetString(GLES20.GL_RENDERER);
            String vendor = GLES20.glGetString(GLES20.GL_VENDOR);
            String version = GLES20.glGetString(GLES20.GL_VERSION);
            
            if (renderer != null && vendor != null) {
                isMaliGPU = (renderer.contains("Mali") || renderer.contains("Immortalis")) 
                           && vendor.equals("ARM");
                isAdrenoGPU = renderer.contains("Adreno") && vendor.equals("Qualcomm");
                
                // Check for GLES 3.1 support
                supportsGLES31 = version != null && version.contains("OpenGL ES 3.1");
            }
            
            Log.d(TAG, "GPU Detected - Mali: " + isMaliGPU + ", Adreno: " + isAdrenoGPU + 
                  ", GLES 3.1: " + supportsGLES31);
        } catch (Exception e) {
            Log.w(TAG, "Failed to detect GPU capabilities", e);
        }
    }
    
    /**
     * Load a shader from a string
     * 
     * @param name Unique name for the shader
     * @param vertexSource Vertex shader source code
     * @param fragmentSource Fragment shader source code
     * @return true if loaded successfully
     */
    public boolean loadShader(String name, String vertexSource, String fragmentSource) {
        shaderSourceCache.put(name + ".vert", vertexSource);
        shaderSourceCache.put(name + ".frag", fragmentSource);
        
        int program = QuasarShaderExecutor.createProgram(vertexSource, fragmentSource);
        if (program > 0) {
            shaderCache.put(name, program);
            Log.d(TAG, "Loaded shader: " + name);
            return true;
        }
        
        Log.e(TAG, "Failed to load shader: " + name);
        return false;
    }
    
    /**
     * Load a shader from an InputStream
     * 
     * @param name Unique name for the shader
     * @param vertexStream Vertex shader input stream
     * @param fragmentStream Fragment shader input stream
     * @return true if loaded successfully
     */
    public boolean loadShader(String name, InputStream vertexStream, InputStream fragmentStream) {
        try {
            String vertexSource = readStream(vertexStream);
            String fragmentSource = readStream(fragmentStream);
            return loadShader(name, vertexSource, fragmentSource);
        } catch (IOException e) {
            Log.e(TAG, "Failed to read shader files", e);
            return false;
        }
    }
    
    /**
     * Get a shader program by name
     * 
     * @param name The shader name
     * @return The program handle, or 0 if not found
     */
    public int getShader(String name) {
        Integer program = shaderCache.get(name);
        return program != null ? program : 0;
    }
    
    /**
     * Use a shader by name
     * 
     * @param name The shader name
     */
    public void useShader(String name) {
        int program = getShader(name);
        QuasarShaderExecutor.useProgram(program);
    }
    
    /**
     * Create and use a complementary color shader
     */
    public int createAndUseComplementaryShader() {
        int program = QuasarShaderExecutor.createComplementaryShader();
        if (program > 0) {
            shaderCache.put("complementary", program);
            QuasarShaderExecutor.useProgram(program);
        }
        return program;
    }
    
    /**
     * Create and use a solas shader
     */
    public int createAndUseSolasShader() {
        int program = QuasarShaderExecutor.createSolasShader();
        if (program > 0) {
            shaderCache.put("solas", program);
            QuasarShaderExecutor.useProgram(program);
        }
        return program;
    }
    
    /**
     * Create and use a pass-through shader
     */
    public int createAndUsePassthroughShader() {
        int program = QuasarShaderExecutor.createPassthroughShader();
        if (program > 0) {
            shaderCache.put("passthrough", program);
            QuasarShaderExecutor.useProgram(program);
        }
        return program;
    }
    
    /**
     * Apply shader fixes for Mali GPU
     * 
     * @param shaderCode The original shader code
     * @return Fixed shader code for Mali
     */
    public String applyMaliFixes(String shaderCode) {
        if (!isMaliGPU) {
            return shaderCode;
        }
        
        String fixed = shaderCode;
        
        // Fix 1: Replace noperspective with smooth
        fixed = fixed.replaceAll("\\bnoperspective\\b", "smooth");
        
        // Fix 2: Disable unsupported extensions
        fixed = fixed.replaceAll(
            "#extension\\s+GL_NV_shader_noperspective_interpolation",
            "#extension GL_NV_shader_noperspective_interpolation : disable"
        );
        
        // Fix 3: Replace ARB extensions with EXT
        fixed = fixed.replaceAll(
            "#extension\\s+GL_ARB_shader_texture_lod",
            "#extension GL_EXT_shader_texture_lod : enable"
        );
        
        // Fix 4: Replace texture2DLod with textureLod
        fixed = fixed.replaceAll("texture2DLod", "textureLod");
        
        // Fix 5: Add precision if missing
        if (!fixed.contains("precision")) {
            fixed = "precision highp float;\n" + fixed;
        }
        
        // Fix 6: Add output location for fragment shaders
        if (fixed.contains("out vec4") && !fixed.contains("layout(location")) {
            fixed = fixed.replace("out vec4 fragColor;", "layout(location = 0) out vec4 fragColor;");
        }
        
        // Fix 7: Safe division for Mali GPUs
        fixed = fixed.replaceAll(
            "(\\w+)\\s*/\\s*gl_FragCoord\\.w",
            "$1 / max(gl_FragCoord.w, 0.001)"
        );
        
        return fixed;
    }
    
    /**
     * Check if Mali GPU
     */
    public boolean isMaliGPU() {
        return isMaliGPU;
    }
    
    /**
     * Check if Adreno GPU
     */
    public boolean isAdrenoGPU() {
        return isAdrenoGPU;
    }
    
    /**
     * Check if GLES 3.1 is supported
     */
    public boolean supportsGLES31() {
        return supportsGLES31;
    }
    
    /**
     * Get GPU information as a string
     */
    public String getGPUInfo() {
        try {
            String renderer = GLES20.glGetString(GLES20.GL_RENDERER);
            String vendor = GLES20.glGetString(GLES20.GL_VENDOR);
            String version = GLES20.glGetString(GLES20.GL_VERSION);
            return "Renderer: " + renderer + ", Vendor: " + vendor + ", Version: " + version;
        } catch (Exception e) {
            return "Unknown GPU";
        }
    }
    
    /**
     * Clear all cached shaders
     */
    public void clearCache() {
        shaderCache.clear();
        shaderSourceCache.clear();
        QuasarShaderExecutor.clearCache();
        Log.d(TAG, "Cleared all shader caches");
    }
    
    /**
     * Read a stream into a string
     */
    private String readStream(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder stringBuilder = new StringBuilder();
        String line;
        
        while ((line = reader.readLine()) != null) {
            stringBuilder.append(line).append("\n");
        }
        
        reader.close();
        return stringBuilder.toString();
    }
    
    /**
     * Check if a shader exists in the cache
     */
    public boolean hasShader(String name) {
        return shaderCache.containsKey(name);
    }
    
    /**
     * Remove a shader from the cache
     */
    public void removeShader(String name) {
        Integer program = shaderCache.remove(name);
        if (program != null && program > 0) {
            GLES20.glDeleteProgram(program);
            Log.d(TAG, "Removed shader: " + name);
        }
    }
    
    /**
     * Get the number of cached shaders
     */
    public int getShaderCount() {
        return shaderCache.size();
    }
    
    /**
     * Reset the singleton instance (for testing)
     */
    public static void resetInstance() {
        if (instance != null) {
            instance.clearCache();
            instance = null;
        }
    }
}
