TURNIP SETUP GUIDE - Fix Color Glitches and Enable Shaders on Mali/Adreno

================================================================================

PROBLEM SUMMARY:
- Red/Green color glitches appearing
- Complementary and Astra shaders not working
- Using LTW (OpenGL translator) which has limitations
- Mali-G615 GPU with OpenGL ES 3.2

SOLUTION:
Replace LTW with Turnip (Vulkan) renderer for better compatibility and performance.

================================================================================
STEP 1: ENABLE TURNIP IN YOUR APPLICATION
================================================================================

Add this code at the START of your application initialization:

    TurnipConfig.initialize();

This will:
1. Set quasar.renderer to "turnip"
2. Enable Turnip for Mali and Adreno GPUs
3. Enable color space fixes
4. Enable shader caching
5. Force iris.enableShaders to true

================================================================================
STEP 2: REPLACE LTW WITH TURNIP PIPELINE
================================================================================

Replace your current QuasarPipeline usage:

OLD (LTW):
    QuasarPipeline pipeline = new QuasarPipeline();
    String processed = pipeline.processShader(source, info);

NEW (Turnip):
    TurnipQuasarPipeline pipeline = TurnipConfig.createPipeline();
    String processed = pipeline.processShader(source, info);

================================================================================
STEP 3: FORCE TURNIP (OPTIONAL - FOR TESTING)
================================================================================

If shaders still dont work, force Turnip to be used:

    TurnipConfig.FORCE_TURNIP = true;
    TurnipConfig.initialize();

Or programmatically:

    TurnipIntegration turnip = new TurnipIntegration();
    turnip.forceEnableTurnip(true);

================================================================================
STEP 4: ENABLE SHADERS IN IRIS
================================================================================

Make sure these properties are set:

    System.setProperty("iris.enableShaders", "true");
    System.setProperty("iris.shaders.enabled", "true");

Or edit iris.properties file:

    enableShaders=true

================================================================================
STEP 5: FIX COLOR GLITCHES
================================================================================

The ColorSpaceFixer automatically handles:

1. sRGB to Linear conversion for textures
2. Linear to sRGB conversion for output
3. Proper handling of normal maps (linear space)
4. Proper handling of shadow maps (linear space)

If you still see color issues:

    ColorSpaceFixer fixer = new ColorSpaceFixer();
    String fixedShader = fixer.fixColorSpace(shaderSource, shaderInfo);

================================================================================
STEP 6: USE TURNIP-OPTIMIZED SHADERS
================================================================================

Instead of creating shaders directly:

OLD:
    ComplementaryShader shader = new ComplementaryShader();

NEW:
    TurnipQuasarPipeline pipeline = TurnipConfig.createPipeline();
    ComplementaryShader shader = pipeline.createComplementaryShader();

This ensures shaders are processed through the Turnip-optimized pipeline.

================================================================================
STEP 7: VERIFY TURNIP IS WORKING
================================================================================

Check the logs for:

    [TurnipConfig] Turnip configuration initialized
    [TurnipConfig] Mali support: true
    [TurnipIntegration] Turnip enabled for Mali GPU

If you see these messages, Turnip is active.

================================================================================
STEP 8: TROUBLESHOOTING
================================================================================

PROBLEM: Color glitches still present
SOLUTION:
1. Verify ENABLE_COLOR_SPACE_FIXES is true in TurnipConfig
2. Check if shader is processed through Turnip pipeline
3. Add debug logging to see which pipeline is used

PROBLEM: Shaders not loading
SOLUTION:
1. Check if iris.enableShaders is true
2. Verify Turnip is enabled for your GPU
3. Try FORCE_TURNIP = true
4. Check shader compilation logs

PROBLEM: Performance issues
SOLUTION:
1. Enable shader caching: ENABLE_SHADER_CACHE = true
2. Adjust SHADER_CACHE_MAX_SIZE
3. Check GPU driver version

PROBLEM: Complementary shader crashes
SOLUTION:
1. Use Turnip-optimized version: pipeline.createComplementaryShader()
2. Check if all required extensions are polyfilled
3. Enable debug logging to see compilation errors

================================================================================
COMMON FIXES FOR MALI GPUS
================================================================================

Mali GPUs have specific issues that are automatically fixed:

1. Green/Red Pixels:
   - CAUSE: Incorrect sRGB to linear conversion
   - FIX: ColorSpaceFixer adds proper conversion functions

2. Grid Lines:
   - CAUSE: Texture filtering issues
   - FIX: Turnip uses proper Vulkan texture filtering

3. Shadow Problems:
   - CAUSE: Depth bias issues
   - FIX: Shadow bias is automatically increased for Mali

4. Extension Issues:
   - CAUSE: Missing GL_NV_shader_noperspective_interpolation
   - FIX: TurnipShaderProcessor replaces with smooth interpolation

================================================================================
CONFIGURATION OPTIONS
================================================================================

Edit TurnipConfig.java to customize:

ENABLE_TURNIP_FOR_MALI = true    // Enable Turnip for Mali GPUs
ENABLE_TURNIP_FOR_ADRENO = true  // Enable Turnip for Adreno GPUs
FORCE_TURNIP = false             // Force Turnip on all devices
ENABLE_COLOR_SPACE_FIXES = true  // Fix color glitches
ENABLE_SHADER_CACHE = true       // Cache processed shaders
SHADER_CACHE_MAX_SIZE = 500      // Maximum cached shaders
ENABLE_DEBUG_LOGGING = true      // Log initialization info
ENABLE_COMPLEMENTARY_SHADER = true // Enable Complementary shader
ENABLE_ASTRA_SHADER = true       // Enable Astra shader
ENABLE_SOLAS_SHADER = true        // Enable Solas shader

================================================================================
QUICK START - MINIMAL CODE
================================================================================

Add these 2 lines at the start of your app:

    TurnipConfig.initialize();
    TurnipQuasarPipeline pipeline = TurnipConfig.createPipeline();

Then use pipeline to process all shaders.

That is it! This will:
- Enable Turnip for Mali/Adreno
- Fix color glitches
- Enable shaders
- Optimize performance

================================================================================
ADVANCED USAGE
================================================================================

For more control:

    TurnipIntegration turnip = new TurnipIntegration();
    if (turnip.isMaliGpu() || turnip.isAdrenoGpu()) {
        // Use Turnip pipeline
        TurnipQuasarPipeline pipeline = new TurnipQuasarPipeline();
        pipeline.setUseTurnip(true);
        
        // Process shaders
        String vertex = pipeline.processShader(vsSource, vInfo);
        String fragment = pipeline.processShader(fsSource, fInfo);
        
        // Or use optimized shaders
        ComplementaryShader comp = pipeline.createComplementaryShader();
        AstraShader astra = pipeline.createAstraShader();
    }

================================================================================
FILES INVOLVED
================================================================================

Core Files:
- TurnipIntegration.java     - Main Turnip support
- TurnipShaderProcessor.java - Shader processing for Turnip
- TurnipQuasarPipeline.java  - Main pipeline with Turnip
- ColorSpaceFixer.java       - Fixes color glitches
- TurnipConfig.java          - Configuration

Existing Files (Updated):
- ShaderProcessor.java      - Now works with Turnip
- QuasarPipeline.java       - Now has Turnip support
- All shader implementations  - Now have Turnip-optimized versions

================================================================================
VERIFICATION CHECKLIST
================================================================================

[ ] TurnipConfig.initialize() called at startup
[ ] Using TurnipQuasarPipeline instead of QuasarPipeline
[ ] iris.enableShaders is set to true
[ ] Turnip is enabled for your GPU (check logs)
[ ] Color space fixes are enabled
[ ] Shader caching is enabled
[ ] Using pipeline.createComplementaryShader() etc.

If all checked, color glitches should be gone and shaders should work!

================================================================================
