package net.kdt.pojavlaunch.quasar;

public class ComplementaryShader {
    private static final String VERSION_DECLARATION = "#version 300 es
#extension GL_OES_standard_derivatives : enable
precision highp float;
precision highp int;
";
    private static final String COMMON_UNIFORMS = "uniform mat4 u_ProjectionMatrix;
uniform mat4 u_ViewMatrix;
uniform mat4 u_ModelMatrix;
uniform vec3 u_CameraPosition;
uniform float u_Time;
uniform vec2 u_Resolution;
uniform vec3 u_LightDirection;
uniform vec3 u_LightColor;
uniform float u_LightIntensity;
uniform vec3 u_AmbientColor;
";
    private static final String SHADOW_UNIFORMS = "uniform mat4 u_ShadowMatrix;
uniform sampler2D u_ShadowMap;
uniform float u_ShadowBias;
uniform vec2 u_ShadowMapSize;
";
    private static final String MATERIAL_UNIFORMS = "uniform vec3 u_DiffuseColor;
uniform vec3 u_SpecularColor;
uniform float u_SpecularPower;
uniform float u_Metallic;
uniform float u_Roughness;
";
    
    private static final String VERTEX_SHADER = VERSION_DECLARATION + COMMON_UNIFORMS + SHADOW_UNIFORMS + 
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
    
    private static final String FRAGMENT_SHADER = VERSION_DECLARATION + COMMON_UNIFORMS + SHADOW_UNIFORMS + MATERIAL_UNIFORMS +
        "uniform sampler2D u_DiffuseMap;
uniform sampler2D u_NormalMap;
uniform sampler2D u_SpecularMap;
uniform sampler2D u_RoughnessMap;
uniform sampler2D u_AOMap;
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
float shadow = 0.0;
vec2 ts = 1.0 / u_ShadowMapSize;
for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) {
vec2 offset = vec2(x, y) * ts;
if (depth <= texture(u_ShadowMap, stc + offset).r) shadow += 1.0;
}
return shadow / 9.0;
" +
        "}
" +
        "vec3 calculateLighting(vec3 n, vec3 vd, vec3 ld, vec3 albedo, float metallic, float roughness, float ao) {
" +
        "float NdotL = max(dot(n, ld), 0.0);
vec3 h = normalize(ld + vd);
float NdotH = max(dot(n, h), 0.0);
float a = roughness * roughness;
float D = a * a / (3.14159265 * (NdotH * NdotH * (a * a - 1.0) + 1.0) * (NdotH * NdotH * (a * a - 1.0) + 1.0));
vec3 F = vec3(0.04);
F = mix(F, vec3(1.0), pow(1.0 - max(dot(h, vd), 0.0), 5.0));
vec3 kS = F;
vec3 kD = vec3(1.0) - kS;
kD *= 1.0 - metallic;
vec3 color = (kD * albedo / 3.14159265 + NdotL * D * F) * NdotL * u_LightColor * u_LightIntensity;
color += albedo * 0.03 * ao * u_AmbientColor;
return color;
" +
        "}
" +
        "void main() {
" +
        "vec3 albedo = texture(u_DiffuseMap, v_TexCoord).rgb;
vec3 normal = normalize(v_Normal);
float metallic = texture(u_SpecularMap, v_TexCoord).r;
float roughness = texture(u_RoughnessMap, v_TexCoord).r;
float ao = texture(u_AOMap, v_TexCoord).r;
vec3 viewDir = normalize(u_CameraPosition - v_WorldPosition);
vec3 lightDir = normalize(u_LightDirection);
float shadow = calculateShadow(v_ShadowCoord);
vec3 lighting = calculateLighting(normal, viewDir, lightDir, albedo, metallic, roughness, ao);
lighting *= shadow;
fragColor = vec4(lighting, 1.0);
" +
        "}
";
    
    private final ShaderInfo shaderInfo;
    private final ShaderProcessor processor;
    private String vertexSource;
    private String fragmentSource;
    
    public ComplementaryShader() {
        this.shaderInfo = new ShaderInfo("Complementary", ShaderInfo.ShaderType.FRAGMENT);
        this.processor = ShaderProcessor.getInstance();
        initializeShaders();
    }
    
    private void initializeShaders() {
        this.vertexSource = VERTEX_SHADER;
        this.fragmentSource = FRAGMENT_SHADER;
        processShaders();
    }
    
    private void processShaders() {
        ShaderInfo vi = new ShaderInfo("Complementary_Vertex", ShaderInfo.ShaderType.VERTEX);
        ShaderInfo fi = new ShaderInfo("Complementary_Fragment", ShaderInfo.ShaderType.FRAGMENT);
        this.vertexSource = processor.processShader(vertexSource, vi);
        this.fragmentSource = processor.processShader(fragmentSource, fi);
    }
    
    public String getVertexSource() { return vertexSource; }
    public String getFragmentSource() { return fragmentSource; }
    public ShaderInfo getShaderInfo() { return shaderInfo; }
    public void reload() { processShaders(); }
    public String getName() { return "Complementary"; }
    public boolean isCompatible() {
        GpuCapabilities caps = processor.getCapabilities();
        return caps != null && (caps.getVersion().contains("ES 3.0") || caps.getVersion().contains("ES 3.1") || caps.getVersion().contains("ES 3.2"));
    }
}