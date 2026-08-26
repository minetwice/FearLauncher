package net.kdt.pojavlaunch.quasar;

public class TurnipConfig {
    public static final boolean ENABLE_TURNIP_FOR_MALI = true;
    public static final boolean ENABLE_TURNIP_FOR_ADRENO = true;
    public static final boolean FORCE_TURNIP = false;
    public static final boolean ENABLE_COLOR_SPACE_FIXES = true;
    public static final boolean ENABLE_MALI_FIXES = true;
    public static final boolean ENABLE_ADRENO_FIXES = true;
    public static final boolean ENABLE_SHADER_CACHE = true;
    public static final boolean ENABLE_DEBUG_LOGGING = true;
    public static final int SHADER_CACHE_MAX_SIZE = 500;
    public static final boolean ENABLE_COMPLEMENTARY_SHADER = true;
    public static final boolean ENABLE_ASTRA_SHADER = true;
    public static final boolean ENABLE_SOLAS_SHADER = true;
    public static final int SHADOW_QUALITY = 2;
    public static final boolean USE_SRGB_FRAMEBUFFER = true;
    
    public static void initialize() {
        System.setProperty("quasar.renderer", "turnip");
        System.setProperty("quasar.enable_turnip", String.valueOf(ENABLE_TURNIP_FOR_MALI || ENABLE_TURNIP_FOR_ADRENO));
        System.setProperty("quasar.force_turnip", String.valueOf(FORCE_TURNIP));
        System.setProperty("quasar.enable_color_fixes", String.valueOf(ENABLE_COLOR_SPACE_FIXES));
        System.setProperty("quasar.enable_shader_cache", String.valueOf(ENABLE_SHADER_CACHE));
        System.setProperty("quasar.shader_cache_size", String.valueOf(SHADER_CACHE_MAX_SIZE));
        System.setProperty("iris.enableShaders", "true");
        System.setProperty("iris.shaders.enabled", "true");
        logConfiguration();
    }
    
    private static void logConfiguration() {
        if (ENABLE_DEBUG_LOGGING) {
            System.out.println("[TurnipConfig] Turnip configuration initialized");
            System.out.println("[TurnipConfig] Mali support: " + ENABLE_TURNIP_FOR_MALI);
            System.out.println("[TurnipConfig] Adreno support: " + ENABLE_TURNIP_FOR_ADRENO);
            System.out.println("[TurnipConfig] Color fixes: " + ENABLE_COLOR_SPACE_FIXES);
            System.out.println("[TurnipConfig] Shader cache: " + ENABLE_SHADER_CACHE);
        }
    }
    
    public static TurnipQuasarPipeline createPipeline() {
        TurnipQuasarPipeline pipeline = new TurnipQuasarPipeline();
        pipeline.setUseTurnip(ENABLE_TURNIP_FOR_MALI || ENABLE_TURNIP_FOR_ADRENO || FORCE_TURNIP);
        pipeline.setUseFallback(true);
        return pipeline;
    }
    
    public static TurnipShaderProcessor createShaderProcessor() {
        TurnipShaderProcessor processor = new TurnipShaderProcessor();
        if (FORCE_TURNIP) {
            processor.forceTurnip(true);
        }
        return processor;
    }
    
    public static boolean shouldUseTurnip() {
        String renderer = System.getProperty("gl.renderer", "");
        String vendor = System.getProperty("gl.vendor", "");
        boolean isMali = renderer.contains("Mali") || vendor.contains("ARM");
        boolean isAdreno = renderer.contains("Adreno") || vendor.contains("QUALCOMM");
        return (isMali && ENABLE_TURNIP_FOR_MALI) || (isAdreno && ENABLE_TURNIP_FOR_ADRENO) || FORCE_TURNIP;
    }
}
