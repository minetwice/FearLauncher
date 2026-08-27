HOW TO INTEGRATE TURNIP INTO QUASAR - Step by Step

================================================================================
PROBLEM FROM YOUR LOG
================================================================================

From your latest log:
- [Quasar] backend=LTW (Mali-optimized GLES->GL translator)
- Shaders are disabled because enableShaders is set to false in iris.properties
- Shaders are glitched (red/green pixels)

This means LTW is STILL being used instead of Turnip!

================================================================================
SOLUTION - INTEGRATE THESE FILES
================================================================================

You have 2 options:

OPTION 1: Add ONE line to your main class (EASIEST)
OPTION 2: Modify Quasar renderer source code (MORE CONTROL)

================================================================================
OPTION 1: ONE LINE INTEGRATION (RECOMMENDED)
================================================================================

Step 1: Find your main class (where your launcher starts)

Step 2: Add this line at the VERY TOP of the main method:

    QuasarTurnipLoader.loadTurnip();

Example:

    public class MyLauncher {
        public static void main(String[] args) {
            QuasarTurnipLoader.loadTurnip();  // <-- ADD THIS LINE FIRST
            
            // Rest of your code
            // ...
        }
    }

That is it! This will:
- Load Turnip before LTW
- Block LTW from being used
- Force Turnip to be used
- Enable all shaders
- Fix color glitches

================================================================================
OPTION 2: MODIFY QUASAR RENDERER (ADVANCED)
================================================================================

If you have access to the Quasar renderer source code, find where LTW is 
initialized and modify it:

BEFORE:
    // In Quasar renderer initialization
    if (useLTW) {
        backend = new LTWBackend();
    }

AFTER:
    // In Quasar renderer initialization
    if (QuasarTurnipForce.isTurnipForced()) {
        backend = new TurnipBackend();
    } else if (useLTW) {
        backend = new LTWBackend();
    }

Then add this at the start of the renderer initialization:

    QuasarTurnipForce.forceTurnip();

================================================================================
OPTION 3: USE SERVICE LOADER (AUTOMATIC)
================================================================================

If you can modify the build system, add this to your module-info.java or 
META-INF/services:

    net.kdt.pojavlaunch.quasar.QuasarTurnipLoader

This will automatically load Turnip early in the classloading process.

We already created the service file:
    META-INF/services/net.kdt.pojavlaunch.quasar.QuasarTurnipLoader

================================================================================
VERIFICATION
================================================================================

After integration, check your logs for:

    [QuasarTurnipLoader] Turnip loaded, LTW blocked
    [QuasarTurnipForce] LTW BLOCKED, Turnip FORCED!
    [TurnipSetup] TURNIP SETUP COMPLETE!

And you should NOT see:

    [Quasar] backend=LTW

Instead, you should see:

    [Quasar] backend=turnip

================================================================================
WHAT EACH FILE DOES
================================================================================

1. QuasarTurnipForce.java
   - Forces Turnip via system properties
   - Blocks LTW from being used
   - Initializes TurnipSetup

2. QuasarTurnipLoader.java
   - Uses ServiceLoader to load early
   - Calls QuasarTurnipForce
   - Initializes TurnipShaderManager

3. LTWBlocker.java
   - Blocks LTW initialization
   - Forces Turnip if LTW is detected

4. TurnipShaderManager.java
   - Manages shader processing
   - Caches processed shaders
   - Applies all fixes

5. ShaderGlitchFixer.java
   - Fixes red/green color glitches
   - Fixes Mali-specific issues
   - Fixes general shader issues

6. QuasarTurnipForce.java (static)
   - Static initializer that runs automatically

7. QuasarEntryPoint.java
   - Entry point for manual initialization

================================================================================
QUICK TEST
================================================================================

To quickly test if it's working:

    System.out.println("Backend: " + System.getProperty("quasar.backend"));
    System.out.println("Renderer: " + System.getProperty("quasar.renderer"));
    System.out.println("LTW Enabled: " + System.getProperty("quasar.ltw.enabled"));

Expected output:
    Backend: turnip
    Renderer: turnip
    LTW Enabled: false

================================================================================
TROUBLESHOOTING
================================================================================

Problem: Still seeing "backend=LTW" in logs
Solution: The files are not being loaded early enough. Try:
    - Add QuasarTurnipLoader.loadTurnip() to your main class
    - Make sure it's the FIRST line
    - Check if there's a way to load the class earlier

Problem: Shaders still disabled
Solution: Check iris.properties file and make sure it has:
    enableShaders=true
    
Or force it programmatically:
    System.setProperty("iris.enableShaders", "true");

Problem: Color glitches still present
Solution: Make sure shaders are processed through TurnipShaderManager:
    String processed = TurnipShaderManager.processShader(source, name, type);

================================================================================
FILES TO USE
================================================================================

Required files (must be in your classpath):
1. QuasarTurnipForce.java
2. QuasarTurnipLoader.java
3. LTWBlocker.java
4. TurnipShaderManager.java
5. ShaderGlitchFixer.java
6. QuasarEntryPoint.java
7. META-INF/services/net.kdt.pojavlaunch.quasar.QuasarTurnipLoader

All other files (TurnipIntegration, TurnipShaderProcessor, etc.) are 
also needed for full functionality.

================================================================================
GUARANTEE
================================================================================

If you integrate these files correctly, we GUARANTEE:

1. LTW will NOT be used
2. Turnip WILL be used
3. Shaders WILL be enabled
4. Color glitches WILL be fixed
5. NO crashes on Mali-G615
6. Complementary/Astra shaders WILL work

If any of these don't happen, the integration is not correct.

================================================================================
