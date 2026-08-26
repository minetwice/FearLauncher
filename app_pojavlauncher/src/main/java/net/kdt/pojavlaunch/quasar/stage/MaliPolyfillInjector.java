package net.kdt.pojavlaunch.quasar.stage;

/**
 * MaliPolyfillInjector provides version line and polyfill transformations for Mali GPUs.
 */
public class MaliPolyfillInjector {

    public static String injectPolyfills(String glsl) {
        if (glsl == null || glsl.isEmpty()) return glsl;

        if (glsl.contains("#version")) {
            int verIndex = glsl.indexOf("#version");
            int lineEnd = glsl.indexOf("\n", verIndex);
            if (lineEnd != -1) {
                String versionLine = glsl.substring(verIndex, lineEnd);
                String newVersion = versionLine.replaceAll("(\\d+)", "320 es");
                glsl = glsl.substring(0, verIndex) + newVersion + glsl.substring(lineEnd);
            }
        }
        return glsl;
    }
}
