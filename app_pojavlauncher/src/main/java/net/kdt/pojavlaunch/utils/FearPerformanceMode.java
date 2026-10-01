package net.kdt.pojavlaunch.utils;

import android.util.Log;

import java.io.File;

/**
 * FEARPATCH performance mode.
 *
 * A non-root, renderer-agnostic tune of the vanilla options.txt. These are the
 * settings that actually decide frame time in Minecraft, and on a weak phone
 * they are worth far more than any native layer. Everything here is
 * best-effort: unknown keys are ignored by the game, and a failure to write
 * options.txt must never stop a launch.
 *
 * The values are chosen per device tier so a strong phone keeps a good looking
 * world while a weak one gets a real frame rate.
 */
public class FearPerformanceMode {
    private static final String TAG = "FearPerfMode";

    /** @return true if the options were applied */
    public static boolean apply(File gamedir) {
        try {
            MCOptionUtils.load(gamedir.getAbsolutePath());

            int cores = Runtime.getRuntime().availableProcessors();
            boolean strong = cores >= 8;

            // Never cap the frame rate and never sync it to the panel.
            set("maxFps", "260");
            set("enableVsync", "false");

            // The single biggest lever in the game.
            set("renderDistance", strong ? "8" : "6");
            set("simulationDistance", strong ? "8" : "6");

            // Cheap wins that cost almost nothing visually.
            set("graphicsMode", "0");          // fast graphics
            set("fancyGraphics", "false");
            set("ao", "false");                // no smooth lighting
            set("entityShadows", "false");
            set("renderClouds", "false");
            set("cloudStatus", "false");
            set("particles", "2");             // minimal
            set("bobView", "false");
            set("entityDistanceScaling", "0.5");
            set("mipmapLevels", "2");
            set("improvedTransparency", "false");

            MCOptionUtils.save();
            Log.i(TAG, "performance mode applied (strong=" + strong + ")");
            return true;
        } catch (Throwable t) {
            // Never let a settings tune break a launch.
            Log.w(TAG, "performance mode failed, ignoring", t);
            return false;
        }
    }

    private static void set(String key, String value) {
        MCOptionUtils.set(key, value);
    }
}
