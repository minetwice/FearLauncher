package net.kdt.pojavlaunch.quasar;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShaderPreprocessor {
    private static final Map<String, String> EXTENSION_MAP = new HashMap<>();
    private static final Map<String, String> VERSION_MAP = new HashMap<>();
    private static final Pattern INCLUDE_PATTERN = Pattern.compile("#include\s+["<]([^">]+)[">]");
    private static final Pattern DEFINE_PATTERN = Pattern.compile("#define\s+(\w+)\s+(.+)");
    private static final Pattern PRAGMA_PATTERN = Pattern.compile("#pragma\s+(.+)");
    private final Map<String, String> includeCache = new HashMap<>();
    
    static {
        EXTENSION_MAP.put("GL_ARB_shader_texture_lod", "GL_EXT_shader_texture_lod");
        EXTENSION_MAP.put("GL_NV_shader_noperspective_interpolation", "");
        EXTENSION_MAP.put("GL_EXT_geometry_shader4", "");
        VERSION_MAP.put("330", "300 es");
        VERSION_MAP.put("400", "300 es");
        VERSION_MAP.put("410", "310 es");
        VERSION_MAP.put("420", "310 es");
        VERSION_MAP.put("430", "320 es");
    }
    
    private final ShaderProcessor processor;
    private final GpuCapabilities capabilities;
    
    public ShaderPreprocessor() {
        this.processor = ShaderProcessor.getInstance();
        this.capabilities = processor.getCapabilities();
    }
    
    public String preprocess(String shaderSource, ShaderInfo shaderInfo) {
        if (shaderSource == null || shaderSource.isEmpty()) return shaderSource;
        String processed = shaderSource;
        processed = processIncludes(processed);
        processed = normalizeVersion(processed);
        processed = replaceExtensions(processed);
        processed = processDirectives(processed);
        processed = addPrecisionQualifiers(processed);
        return processed;
    }
    
    private String processIncludes(String shaderSource) {
        String processed = shaderSource;
        Matcher matcher = INCLUDE_PATTERN.matcher(processed);
        while (matcher.find()) {
            String includeFile = matcher.group(1);
            String includeContent = getIncludeContent(includeFile);
            if (includeContent != null) {
                includeContent = processIncludes(includeContent);
                processed = processed.replace(matcher.group(0), includeContent);
                matcher = INCLUDE_PATTERN.matcher(processed);
            }
        }
        return processed;
    }
    
    private String getIncludeContent(String includeFile) {
        return includeCache.get(includeFile);
    }
    
    private String normalizeVersion(String shaderSource) {
        String processed = shaderSource;
        String versionPattern = "#version\s+(\d+)\s*([^\n]*)";
        Pattern pattern = Pattern.compile(versionPattern);
        Matcher matcher = pattern.matcher(processed);
        if (matcher.find()) {
            String version = matcher.group(1);
            String profile = matcher.group(2).trim();
            if (profile.contains("es") || profile.contains("ES")) return processed;
            String esVersion = VERSION_MAP.get(version);
            if (esVersion != null) {
                processed = processed.replace(matcher.group(0), "#version " + esVersion);
            } else {
                processed = processed.replace(matcher.group(0), "#version 300 es");
            }
        } else {
            processed = "#version 300 es
" + processed;
        }
        return processed;
    }
    
    private String replaceExtensions(String shaderSource) {
        String processed = shaderSource;
        for (Map.Entry<String, String> entry : EXTENSION_MAP.entrySet()) {
            String ext = entry.getKey();
            String replacement = entry.getValue();
            if (!replacement.isEmpty()) {
                processed = processed.replace("#extension " + ext + " : enable", "#extension " + replacement + " : enable");
            } else {
                processed = processed.replace("#extension " + ext + " : enable
", "");
            }
        }
        return processed;
    }
    
    private String processDirectives(String shaderSource) {
        String processed = shaderSource;
        Matcher matcher = DEFINE_PATTERN.matcher(processed);
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = matcher.group(2);
            processed = processed.replaceAll("\b" + name + "\b", value);
        }
        processed = PRAGMA_PATTERN.matcher(processed).replaceAll("");
        return processed;
    }
    
    private String addPrecisionQualifiers(String shaderSource) {
        String processed = shaderSource;
        if (processed.contains("precision")) return processed;
        int versionIndex = processed.indexOf("#version");
        if (versionIndex >= 0) {
            int newlineIndex = processed.indexOf('
', versionIndex);
            if (newlineIndex >= 0) {
                return processed.substring(0, newlineIndex + 1) + "
precision highp float;
precision highp int;
" + processed.substring(newlineIndex + 1);
            }
        }
        return processed;
    }
    
    public GpuCapabilities getCapabilities() { return capabilities; }
    public void clearIncludeCache() { includeCache.clear(); }
}