package net.kdt.pojavlaunch.quasar.core;

import android.content.Context;
import android.util.Log;

import net.kdt.pojavlaunch.quasar.capability.CapabilityTable;
import net.kdt.pojavlaunch.quasar.gl.ModernGLCompat;
import net.kdt.pojavlaunch.quasar.shield.ShaderShield;

/**
 * QuasarCore — first-party desktop OpenGL → Android GLES translator.
 * Not LTW. Not Turnip/Zink.
 * libFearCore.so + fear_render + ShaderShield + TranslatorTable.
 */
public final class QuasarCore {
    private static final String TAG = "QuasarCore";
    public static final String GL_LIB = "libFearCore.so";
    public static final String RENDER_LIB = "fear_render";

    private static volatile boolean ready = false;
    private static volatile CapabilityTable caps;

    private QuasarCore() {}

    public static synchronized void boot(Context ctx, CapabilityTable table) {
        if (ready) return;
        caps = table;
        Log.i(TAG, "Booting QuasarCore A–Z translator (FearCore + fear_render + ShaderShield)");
        try {
            System.loadLibrary(RENDER_LIB);
            Log.i(TAG, "Loaded " + RENDER_LIB);
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "fear_render not loaded: " + e.getMessage());
        }
        try {
            ModernGLCompat.activate(table);
        } catch (Throwable t) {
            Log.w(TAG, "ModernGLCompat: " + t.getMessage());
        }
        ShaderShield.setMode(ShaderShield.Mode.EXTREME);
        ready = true;
        Log.i(TAG, "QuasarCore ready | " + TranslatorTable.summary());
    }

    public static boolean isReady() { return ready; }
    public static CapabilityTable caps() { return caps; }

    public static String status() {
        return "QuasarCore ready=" + ready + " gl=" + GL_LIB
                + " interceptor=" + RENDER_LIB
                + " shield=" + ShaderShield.stats()
                + " features=" + TranslatorTable.size();
    }
}
