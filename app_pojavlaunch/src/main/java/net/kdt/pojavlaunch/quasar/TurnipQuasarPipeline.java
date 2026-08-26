package net.kdt.pojavlaunch.quasar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Quasar Pipeline with Turnip (Vulkan) support
 * This is the main pipeline that replaces LTW with Turnip for Mali and Adreno GPUs
 */
public class TurnipQuasarPipeline {
    
    private final TurnipIntegration turnipIntegration;
    private final TurnipShaderProcessor turnipProcessor;
    private final QuasarPipeline fallbackPipeline;
    private final ColorSpaceFixer colorSpaceFixer;
    
    // Configuration
    private boolean useTurnip = true;
    private boolean useFallback = true;
    
    public TurnipQuasarPipeline() {
        this.turnipIntegration = new TurnipIntegration();
        this.turnipProcessor = new TurnipShaderProcessor();
        this.fallbackPipeline = new QuasarPipeline();
        this.colorSpaceFixer = new ColorSpaceFixer();
    }
    
    /**
     * Processes a shader through the Turnip-optimized pipeline
     */
    public String processShader(String shaderSource, ShaderInfo shaderInfo) {
        if (shaderSource == null || shaderSource.isEmpty()) {
            return shaderSource;
        }
        
        // Check if we should use Turnip
        if (useTurnip && turnipIntegration.isTurnipEnabled()) {
            return turnipProcessor.processForTurnip(shaderSource, shaderInfo);
        }
        
        // Fall back to standard pipeline
        if (useFallback) {
            return fallbackPipeline.processShader(shaderSource, shaderInfo);
        }
        
        return shaderSource;
    }
    
    /**
     * Processes a shader with name and type
     */
    public String processShader(String shaderSource, String shaderName, ShaderInfo.ShaderType type) {
        ShaderInfo info = new ShaderInfo(shaderName, type);
        return processShader(shaderSource, info);
    }
    
    /**
     * Processes vertex and fragment shaders together
     */
    public ShaderPair processShaderPair(String vertexSource, String fragmentSource, String shaderName) {
        ShaderInfo vertexInfo = new ShaderInfo(shaderName + "_Vertex", ShaderInfo.ShaderType.VERTEX);
        ShaderInfo fragmentInfo = new ShaderInfo(shaderName + "_Fragment", ShaderInfo.ShaderType.FRAGMENT);
        
        String processedVertex = processShader(vertexSource, vertexInfo);
        String processedFragment = processShader(fragmentSource, fragmentInfo);
        
        return new ShaderPair(processedVertex, processedFragment);
    }
    
    /**
     * Creates a Complementary shader optimized for Turnip
     */
    public ComplementaryShader createComplementaryShader() {
        if (useTurnip && turnipIntegration.isTurnipEnabled()) {
            return turnipProcessor.createComplementaryForTurnip();
        }
        return new ComplementaryShader();
    }
    
    /**
     * Creates an Astra shader optimized for Turnip
     */
    public AstraShader createAstraShader() {
        if (useTurnip && turnipIntegration.isTurnipEnabled()) {
            return turnipProcessor.createAstraForTurnip();
        }
        return new AstraShader();
    }
    
    /**
     * Creates a Solas shader optimized for Turnip
     */
    public SolasShader createSolasShader() {
        SolasShader shader = new SolasShader();
        if (useTurnip && turnipIntegration.isTurnipEnabled()) {
            ShaderInfo vertexInfo = new ShaderInfo("Solas_Vertex", ShaderInfo.ShaderType.VERTEX);
            ShaderInfo fragmentInfo = new ShaderInfo("Solas_Fragment", ShaderInfo.ShaderType.FRAGMENT);
            
            String vertex = turnipProcessor.processForTurnip(shader.getVertexSource(), vertexInfo);
            String fragment = turnipProcessor.processForTurnip(shader.getFragmentSource(), fragmentInfo);
            
            return new SolasShader() {
                @Override
                public String getVertexSource() { return vertex; }
                @Override
                public String getFragmentSource() { return fragment; }
            };
        }
        return shader;
    }
    
    /**
     * Enables or disables Turnip
     */
    public void setUseTurnip(boolean use) {
        this.useTurnip = use;
    }
    
    /**
     * Enables or disables fallback pipeline
     */
    public void setUseFallback(boolean use) {
        this.useFallback = use;
    }
    
    /**
     * Checks if Turnip is being used
     */
    public boolean isUsingTurnip() {
        return useTurnip && turnipIntegration.isTurnipEnabled();
    }
    
    /**
     * Checks if current GPU is Mali
     */
    public boolean isMaliGpu() {
        return turnipIntegration.isMaliGpu();
    }
    
    /**
     * Checks if current GPU is Adreno
     */
    public boolean isAdrenoGpu() {
        return turnipIntegration.isAdrenoGpu();
    }
    
    /**
     * Gets Turnip integration
     */
    public TurnipIntegration getTurnipIntegration() {
        return turnipIntegration;
    }
    
    /**
     * Gets Turnip shader processor
     */
    public TurnipShaderProcessor getTurnipProcessor() {
        return turnipProcessor;
    }
    
    /**
     * Gets fallback pipeline
     */
    public QuasarPipeline getFallbackPipeline() {
        return fallbackPipeline;
    }
    
    /**
     * Gets color space fixer
     */
    public ColorSpaceFixer getColorSpaceFixer() {
        return colorSpaceFixer;
    }
    
    /**
     * Shader pair container
     */
    public static class ShaderPair {
        private final String vertexSource;
        private final String fragmentSource;
        public ShaderPair(String vertexSource, String fragmentSource) {
            this.vertexSource = vertexSource;
            this.fragmentSource = fragmentSource;
        }
        public String getVertexSource() { return vertexSource; }
        public String getFragmentSource() { return fragmentSource; }
    }
}
