package net.kdt.pojavlaunch.quasar;

import java.util.ArrayList;
import java.util.List;

public class Stage4ShaderRefiner {
    private final MaliPolyfillInjector polyfillInjector;
    private final MaliShaderFixes maliFixes;
    private final PerformanceOptimizer performanceOptimizer;
    
    public Stage4ShaderRefiner() {
        this.polyfillInjector = new MaliPolyfillInjector();
        this.maliFixes = polyfillInjector.getMaliFixes();
        this.performanceOptimizer = new PerformanceOptimizer();
    }
    
    public String refine(String shaderSource, ShaderInfo shaderInfo) {
        if (shaderSource == null || shaderSource.isEmpty()) return shaderSource;
        String refined = shaderSource;
        refined = polyfillInjector.injectPolyfills(refined, shaderInfo);
        refined = maliFixes.applyAllFixes(refined);
        refined = performanceOptimizer.optimize(refined, shaderInfo);
        refined = ensureGlesCompatibility(refined);
        refined = validateShader(refined, shaderInfo);
        return refined;
    }
    
    private String ensureGlesCompatibility(String shaderSource) {
        String processed = shaderSource;
        if (!processed.contains("#version")) {
            processed = "#version 300 es
" + processed;
        } else if (!processed.contains("es")) {
            processed = processed.replace("#version 330", "#version 300 es");
            processed = processed.replace("#version 400", "#version 300 es");
            processed = processed.replace("#version 410", "#version 310 es");
            processed = processed.replace("#version 420", "#version 310 es");
            processed = processed.replace("#version 430", "#version 320 es");
        }
        if (!processed.contains("precision")) {
            int versionEnd = processed.indexOf("
");
            if (versionEnd > 0) {
                processed = processed.substring(0, versionEnd + 1) + "precision highp float;
precision highp int;
" + processed.substring(versionEnd + 1);
            }
        }
        processed = processed.replace("sampler2DShadow", "sampler2D");
        return processed;
    }
    
    private String validateShader(String shaderSource, ShaderInfo shaderInfo) {
        String validated = shaderSource;
        if (!validated.contains("void main()") && !validated.contains("main(")) {
            if (shaderInfo.getType() == ShaderInfo.ShaderType.COMPUTE) {
                validated += "
void main() {}
";
            }
        }
        if (shaderInfo.getType() == ShaderInfo.ShaderType.FRAGMENT) {
            if (!validated.contains("fragColor") && !validated.contains("gl_FragColor")) {
                validated = validated.replace("void main()", "out vec4 fragColor;
void main()");
            }
        }
        if (shaderInfo.getType() == ShaderInfo.ShaderType.VERTEX) {
            if (!validated.contains("gl_Position")) {
                validated = validated.replace("void main()", "out vec4 gl_Position;
void main()");
            }
        }
        return validated;
    }
    
    public List<String> refineBatch(List<String> shaderSources, List<ShaderInfo> shaderInfos) {
        List<String> refined = new ArrayList<>();
        for (int i = 0; i < shaderSources.size(); i++) {
            refined.add(refine(shaderSources.get(i), shaderInfos.get(i)));
        }
        return refined;
    }
    
    public MaliPolyfillInjector getPolyfillInjector() { return polyfillInjector; }
    public MaliShaderFixes getMaliFixes() { return maliFixes; }
    public PerformanceOptimizer getPerformanceOptimizer() { return performanceOptimizer; }
}