package net.kdt.pojavlaunch.quasar.custom;

import java.util.*;

/**
 * Custom Shader Processing Pipeline
 * Processes shaders through multiple stages
 */
public class CustomShaderPipeline {
    private final GLESTranslator translator;
    private final MaliOptimizer maliOptimizer;
    private final List<ShaderProcessor> processors;
    
    public enum ShaderType {
        VERTEX, FRAGMENT, GEOMETRY, COMPUTE
    }
    
    public CustomShaderPipeline() {
        this.translator = new GLESTranslator();
        this.maliOptimizer = new MaliOptimizer();
        this.processors = new ArrayList<>();
        initializeProcessors();
    }
    
    private void initializeProcessors() {
        processors.add(new GLSLNormalizer());
        processors.add(new ExtensionPolyfiller());
        processors.add(new MaliShaderFixer());
        processors.add(new ColorSpaceFixer());
        processors.add(new PerformanceOptimizer());
    }
    
    /**
     * Compile a shader
     */
    public String compile(String shaderSource, int shaderType) {
        if (shaderSource == null || shaderSource.isEmpty()) {
            return shaderSource;
        }
        
        String processed = shaderSource;
        
        // Stage 1: Translate OpenGL to GLES
        processed = translator.translateShader(processed);
        
        // Stage 2: Process through all processors
        for (ShaderProcessor processor : processors) {
            processed = processor.process(processed, shaderType);
        }
        
        // Stage 3: Optimize for Mali
        processed = maliOptimizer.optimize(processed, shaderType);
        
        // Stage 4: Validate
        processed = validateShader(processed, shaderType);
        
        return processed;
    }
    
    private String validateShader(String source, int shaderType) {
        // Ensure main function exists
        if (!source.contains("void main()") && !source.contains("main(")) {
            if (shaderType == ShaderType.COMPUTE.ordinal()) {
                source += "
void main() {}
";
            } else {
                System.err.println("[CustomShaderPipeline] Warning: No main function found");
            }
        }
        
        // Ensure proper outputs
        if (shaderType == ShaderType.FRAGMENT.ordinal()) {
            if (!source.contains("fragColor") && !source.contains("gl_FragColor")) {
                source = source.replace("void main()", "out vec4 fragColor;
void main()");
            }
        }
        
        if (shaderType == ShaderType.VERTEX.ordinal()) {
            if (!source.contains("gl_Position")) {
                source = source.replace("void main()", "out vec4 gl_Position;
void main()");
            }
        }
        
        return source;
    }
    
    public String processVertexShader(String source) {
        return compile(source, ShaderType.VERTEX.ordinal());
    }
    
    public String processFragmentShader(String source) {
        return compile(source, ShaderType.FRAGMENT.ordinal());
    }
    
    public interface ShaderProcessor {
        String process(String source, int shaderType);
    }
}
