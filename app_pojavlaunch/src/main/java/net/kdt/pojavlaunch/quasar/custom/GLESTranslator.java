package net.kdt.pojavlaunch.quasar.custom;

import java.util.*;

/**
 * OpenGL to GLES 3.2 Translator
 * Converts PC OpenGL calls to Android GLES equivalents
 */
public class GLESTranslator {
    private final GPUCapabilities capabilities;
    private final Map<String, String> functionMappings;
    private final Map<String, String> extensionMappings;
    private final Map<String, String> constantMappings;
    
    public GLESTranslator() {
        this.capabilities = new GPUCapabilities();
        this.functionMappings = new HashMap<>();
        this.extensionMappings = new HashMap<>();
        this.constantMappings = new HashMap<>();
        initializeMappings();
    }
    
    private void initializeMappings() {
        // Function mappings: PC OpenGL -> GLES
        functionMappings.put("glBegin", "// glBegin removed - use glDrawArrays");
        functionMappings.put("glEnd", "// glEnd removed");
        functionMappings.put("glVertex2f", "glVertexAttrib2f");
        functionMappings.put("glVertex3f", "glVertexAttrib3f");
        functionMappings.put("glNormal3f", "glVertexAttrib3f");
        functionMappings.put("glTexCoord2f", "glVertexAttrib2f");
        functionMappings.put("glColor3f", "glVertexAttrib3f");
        functionMappings.put("glColor4f", "glVertexAttrib4f");
        functionMappings.put("glMaterialfv", "// glMaterialfv removed");
        functionMappings.put("glLightfv", "// glLightfv removed");
        functionMappings.put("glLoadIdentity", "// glLoadIdentity removed");
        functionMappings.put("glMatrixMode", "// glMatrixMode removed");
        functionMappings.put("glOrtho", "// glOrtho removed");
        functionMappings.put("glFrustum", "// glFrustum removed");
        functionMappings.put("glTranslatef", "// glTranslatef removed");
        functionMappings.put("glRotatef", "// glRotatef removed");
        functionMappings.put("glScalef", "// glScalef removed");
        
        // Extension mappings
        extensionMappings.put("GL_ARB_shader_objects", "");
        extensionMappings.put("GL_ARB_vertex_shader", "");
        extensionMappings.put("GL_ARB_fragment_shader", "");
        extensionMappings.put("GL_ARB_shading_language_100", "");
        extensionMappings.put("GL_NV_shader_noperspective_interpolation", "");
        extensionMappings.put("GL_EXT_geometry_shader4", "");
        extensionMappings.put("GL_ARB_compute_shader", "");
        extensionMappings.put("GL_ARB_texture_storage", "GL_EXT_texture_storage");
        extensionMappings.put("GL_ARB_buffer_storage", "GL_EXT_buffer_storage");
        
        // Constant mappings
        constantMappings.put("GL_QUADS", "GL_TRIANGLE_STRIP");
        constantMappings.put("GL_QUAD_STRIP", "GL_TRIANGLE_STRIP");
        constantMappings.put("GL_POLYGON", "GL_TRIANGLE_FAN");
    }
    
    public void setup() {
        capabilities.detect();
        System.out.println("[GLESTranslator] Setup complete");
        System.out.println("[GLESTranslator] GPU: " + capabilities.getRenderer());
        System.out.println("[GLESTranslator] Version: " + capabilities.getVersion());
    }
    
    /**
     * Translate OpenGL code to GLES
     */
    public String translate(String glCode) {
        if (glCode == null || glCode.isEmpty()) {
            return glCode;
        }
        
        String translated = glCode;
        
        // Translate function calls
        for (Map.Entry<String, String> entry : functionMappings.entrySet()) {
            translated = translated.replaceAll("\b" + entry.getKey() + "\b", entry.getValue());
        }
        
        // Translate constants
        for (Map.Entry<String, String> entry : constantMappings.entrySet()) {
            translated = translated.replaceAll("\b" + entry.getKey() + "\b", entry.getValue());
        }
        
        // Translate extensions
        for (Map.Entry<String, String> entry : extensionMappings.entrySet()) {
            String ext = entry.getKey();
            String replacement = entry.getValue();
            if (replacement.isEmpty()) {
                // Remove unsupported extension
                translated = translated.replace("#extension " + ext, "// Removed: #extension " + ext);
            } else {
                // Replace with supported extension
                translated = translated.replace("#extension " + ext, "#extension " + replacement);
            }
        }
        
        // Ensure GLES version
        translated = ensureGlesVersion(translated);
        
        // Ensure precision
        translated = ensurePrecision(translated);
        
        return translated;
    }
    
    private String ensureGlesVersion(String source) {
        if (!source.contains("#version")) {
            return "#version 300 es
" + source;
        }
        
        if (source.contains("#version 330") || source.contains("#version 4")) {
            return source.replaceAll("#version\s+\d+", "#version 300 es");
        }
        
        return source;
    }
    
    private String ensurePrecision(String source) {
        if (!source.contains("precision")) {
            int versionEnd = source.indexOf("
");
            if (versionEnd > 0) {
                return source.substring(0, versionEnd + 1) + 
                       "precision highp float;
" +
                       "precision highp int;
" +
                       source.substring(versionEnd + 1);
            }
        }
        return source;
    }
    
    /**
     * Translate a shader
     */
    public String translateShader(String shaderSource) {
        return translate(shaderSource);
    }
    
    /**
     * Get capabilities
     */
    public GPUCapabilities getCapabilities() {
        return capabilities;
    }
}
