package net.kdt.pojavlaunch.quasar;

public class AstraShader {
    private static final String VERTEX_SHADER = "#version 300 es
#extension GL_OES_standard_derivatives : enable
precision highp float;
precision highp int;
" +
        "uniform mat4 u_ProjectionMatrix;
uniform mat4 u_ViewMatrix;
uniform mat4 u_ModelMatrix;
uniform mat4 u_ShadowMatrix;
uniform vec3 u_CameraPosition;
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
#extension GL_OES_standard_derivatives : enable
precision highp float;
precision highp int;
" +
        "uniform mat4 u_ProjectionMatrix;
uniform mat4 u_ViewMatrix;
uniform vec3 u_CameraPosition;
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
shadow /= 9.0;
shadow = smoothstep(0.0, 0.5, shadow);
return shadow;
" +
        "}
" +
        "float fresnelEffect(vec3 n, vec3 vd, float p) {
" +
        "float bias = 0.05; float scale = 1.0 - bias; float factor = 1.0 - dot(n, vd);
return pow(factor / scale, p);
" +
        "}
" +
        "vec3 calculateLighting(vec3 pos, vec3 n, vec3 vd, vec3 albedo, float metallic, float roughness, float ao) {
" +
        "vec3 ld = normalize(u_LightDirection);
float NdotL = max(dot(n, ld), 0.0);
vec3 hv = normalize(ld + vd);
float NdotH = max(dot(n, hv), 0.0);
float spec = pow(NdotH, roughness * 128.0 + 1.0);
float fresnel = fresnelEffect(n, vd, 5.0);
vec3 diffuse = albedo * NdotL * u_LightColor * u_LightIntensity;
vec3 specular = vec3(spec) * fresnel * u_LightColor * u_LightIntensity;
vec3 ambient = albedo * ao * u_AmbientColor;
return diffuse + specular + ambient;
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
vec3 lighting = calculateLighting(v_WorldPosition, normal, viewDir, albedo, metallic, roughness, ao);
float shadow = calculateShadow(v_ShadowCoord);
lighting *= shadow;
fragColor = vec4(pow(lighting, vec3(1.0/2.2)), 1.0);
" +
        "}
";
    
    private final ShaderInfo shaderInfo;
    private final ShaderProcessor processor;
    private String vertexSource;
    private String fragmentSource;
    
    public AstraShader() {
        this.shaderInfo = new ShaderInfo("Astra", ShaderInfo.ShaderType.FRAGMENT);
        this.processor = ShaderProcessor.getInstance();
        initializeShaders();
    }
    
    private void initializeShaders() {
        this.vertexSource = VERTEX_SHADER;
        this.fragmentSource = FRAGMENT_SHADER;
        processShaders();
    }
    
    private void processShaders() {
        ShaderInfo vi = new ShaderInfo("Astra_Vertex", ShaderInfo.ShaderType.VERTEX);
        ShaderInfo fi = new ShaderInfo("Astra_Fragment", ShaderInfo.ShaderType.FRAGMENT);
        this.vertexSource = processor.processShader(vertexSource, vi);
        this.fragmentSource = processor.processShader(fragmentSource, fi);
    }
    
    public String getVertexSource() { return vertexSource; }
    public String getFragmentSource() { return fragmentSource; }
    public ShaderInfo getShaderInfo() { return shaderInfo; }
    public void reload() { processShaders(); }
    public String getName() { return "Astra"; }
    public boolean isCompatible() {
        GpuCapabilities caps = processor.getCapabilities();
        return caps != null && (caps.getVersion().contains("ES 3.1") || caps.getVersion().contains("ES 3.2"));
    }
}