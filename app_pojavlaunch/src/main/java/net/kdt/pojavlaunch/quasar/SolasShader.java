package net.kdt.pojavlaunch.quasar;

public class SolasShader {
    private static final String VERTEX_SHADER = "#version 300 es
precision highp float;
precision highp int;
" +
        "uniform mat4 u_ProjectionMatrix;
uniform mat4 u_ViewMatrix;
uniform mat4 u_ModelMatrix;
uniform mat4 u_ShadowMatrix;
" +
        "in vec3 a_Position;
in vec3 a_Normal;
in vec2 a_TexCoord;
out vec3 v_Position;
out vec3 v_Normal;
out vec2 v_TexCoord;
out vec3 v_WorldPosition;
out vec4 v_ShadowCoord;
" +
        "void main() {
" +
        "v_TexCoord = a_TexCoord;
v_WorldPosition = vec3(u_ModelMatrix * vec4(a_Position, 1.0));
v_Position = vec3(u_ViewMatrix * u_ModelMatrix * vec4(a_Position, 1.0));
v_Normal = mat3(transpose(inverse(u_ModelMatrix))) * a_Normal;
v_ShadowCoord = u_ShadowMatrix * vec4(v_WorldPosition, 1.0);
gl_Position = u_ProjectionMatrix * u_ViewMatrix * u_ModelMatrix * vec4(a_Position, 1.0);
" +
        "}
";
    
    private static final String FRAGMENT_SHADER = "#version 300 es
precision highp float;
precision highp int;
" +
        "uniform vec3 u_CameraPosition;
uniform vec3 u_LightDirection;
uniform vec3 u_LightColor;
uniform float u_LightIntensity;
uniform vec3 u_AmbientColor;
" +
        "uniform sampler2D u_ShadowMap;
uniform float u_ShadowBias;
uniform vec2 u_ShadowMapSize;
" +
        "uniform sampler2D u_DiffuseMap;
uniform sampler2D u_NormalMap;
uniform sampler2D u_SpecularMap;
" +
        "in vec3 v_Position;
in vec3 v_Normal;
in vec2 v_TexCoord;
in vec3 v_WorldPosition;
in vec4 v_ShadowCoord;
out vec4 fragColor;
" +
        "float calculateShadow(vec4 sc) {
" +
        "vec2 stc = sc.xy / sc.w * 0.5 + 0.5;
if (stc.x < 0.0 || stc.x > 1.0 || stc.y < 0.0 || stc.y > 1.0) return 1.0;
float depth = sc.z - u_ShadowBias;
float mapDepth = texture(u_ShadowMap, stc).r;
return step(depth, mapDepth);
" +
        "}
" +
        "vec3 calculateLighting(vec3 n, vec3 vd, vec3 albedo) {
" +
        "vec3 ld = normalize(u_LightDirection);
float NdotL = max(dot(n, ld), 0.0);
vec3 diffuse = albedo * NdotL * u_LightColor * u_LightIntensity;
vec3 hv = normalize(ld + vd);
float NdotH = max(dot(n, hv), 0.0);
vec3 specular = vec3(pow(NdotH, 32.0)) * u_LightColor * u_LightIntensity;
vec3 ambient = albedo * 0.1 * u_AmbientColor;
return diffuse + specular + ambient;
" +
        "}
" +
        "void main() {
" +
        "vec3 albedo = texture(u_DiffuseMap, v_TexCoord).rgb;
vec3 normal = normalize(v_Normal);
float specular = texture(u_SpecularMap, v_TexCoord).r;
vec3 viewDir = normalize(u_CameraPosition - v_WorldPosition);
vec3 lighting = calculateLighting(normal, viewDir, albedo);
float shadow = calculateShadow(v_ShadowCoord);
lighting *= shadow;
lighting += specular * 0.5;
fragColor = vec4(lighting, 1.0);
" +
        "}
";
    
    private final ShaderInfo shaderInfo;
    private final ShaderProcessor processor;
    private String vertexSource;
    private String fragmentSource;
    
    public SolasShader() {
        this.shaderInfo = new ShaderInfo("Solas", ShaderInfo.ShaderType.FRAGMENT);
        this.processor = ShaderProcessor.getInstance();
        initializeShaders();
    }
    
    private void initializeShaders() {
        this.vertexSource = VERTEX_SHADER;
        this.fragmentSource = FRAGMENT_SHADER;
        processShaders();
    }
    
    private void processShaders() {
        ShaderInfo vi = new ShaderInfo("Solas_Vertex", ShaderInfo.ShaderType.VERTEX);
        ShaderInfo fi = new ShaderInfo("Solas_Fragment", ShaderInfo.ShaderType.FRAGMENT);
        this.vertexSource = processor.processShader(vertexSource, vi);
        this.fragmentSource = processor.processShader(fragmentSource, fi);
    }
    
    public String getVertexSource() { return vertexSource; }
    public String getFragmentSource() { return fragmentSource; }
    public ShaderInfo getShaderInfo() { return shaderInfo; }
    public void reload() { processShaders(); }
    public String getName() { return "Solas"; }
    public boolean isCompatible() {
        GpuCapabilities caps = processor.getCapabilities();
        return caps != null && caps.getVersion().contains("ES");
    }
}