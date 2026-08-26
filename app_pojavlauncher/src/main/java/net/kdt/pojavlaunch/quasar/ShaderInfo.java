package net.kdt.pojavlaunch.quasar;

import java.util.HashMap;
import java.util.Map;

public class ShaderInfo {
    public enum ShaderType { VERTEX, FRAGMENT, GEOMETRY, COMPUTE, UNKNOWN }
    private final String name;
    private final ShaderType type;
    private final Map<String, Object> properties;
    private GpuCapabilities capabilities;
    
    public ShaderInfo(String name, ShaderType type) {
        this.name = name;
        this.type = type;
        this.properties = new HashMap<>();
    }
    
    public String getName() { return name; }
    public ShaderType getType() { return type; }
    public GpuCapabilities getCapabilities() { return capabilities; }
    public void setCapabilities(GpuCapabilities capabilities) { this.capabilities = capabilities; }
    public Object getProperty(String key) { return properties.get(key); }
    public void setProperty(String key, Object value) { properties.put(key, value); }
    public boolean hasProperty(String key) { return properties.containsKey(key); }
    public boolean isFragmentShader() { return type == ShaderType.FRAGMENT; }
    public boolean isVertexShader() { return type == ShaderType.VERTEX; }
    @Override public String toString() { return "ShaderInfo{name='" + name + "', type=" + type + "}"; }
}