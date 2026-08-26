package net.kdt.pojavlaunch.quasar.gl;

/**
 * Resolved mobile GL profile for the current device.
 * Maps what Iris/desktop packs expect (GL 3.3–4.6) onto ES 3.0/3.1/3.2.
 */
public final class GLVersionProfile {

    public final int esMajor;
    public final int esMinor;
    public final int esVersionCode;
    public final String shadingLanguage;
    public final boolean aep;
    public final String vendor;

    public GLVersionProfile(int esMajor, int esMinor, boolean aep, String vendor) {
        this.esMajor = esMajor;
        this.esMinor = esMinor;
        this.esVersionCode = esMajor * 10 + esMinor;
        this.aep = aep;
        this.vendor = vendor != null ? vendor : "unknown";
        if (esVersionCode >= 32) {
            this.shadingLanguage = "320 es";
        } else if (esVersionCode >= 31) {
            this.shadingLanguage = "310 es";
        } else {
            this.shadingLanguage = "300 es";
        }
    }

    public static GLVersionProfile fromGlesVersion(int glesVersion, String vendor) {
        int major = 3;
        int minor = 0;
        if (glesVersion >= 32) minor = 2;
        else if (glesVersion >= 31) minor = 1;
        else if (glesVersion >= 30) minor = 0;
        boolean aep = minor >= 2;
        return new GLVersionProfile(major, minor, aep, vendor);
    }

    public boolean atLeast(int code) {
        return esVersionCode >= code;
    }

    public String toDesktopCompatString() {
        if (esVersionCode >= 32) return "OpenGL ES 3.2 (compat ~ GL 4.0/4.3 subset)";
        if (esVersionCode >= 31) return "OpenGL ES 3.1 (compat ~ GL 4.3 compute subset)";
        return "OpenGL ES 3.0 (compat ~ GL 3.3 subset)";
    }

    @Override
    public String toString() {
        return "GLVersionProfile{ES " + esMajor + "." + esMinor
                + " GLSL " + shadingLanguage
                + " aep=" + aep
                + " vendor=" + vendor + "}";
    }
}
