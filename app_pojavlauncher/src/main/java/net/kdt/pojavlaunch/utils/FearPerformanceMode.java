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

    /** The game's maximum frame-rate setting. Vanilla's slider tops out at 260. */
    public static final String MAX_FPS = "260";

    /** @return true if the options were applied */
    public static boolean apply(File gamedir) {
        try {
            MCOptionUtils.load(gamedir.getAbsolutePath());

            int cores = Runtime.getRuntime().availableProcessors();
            boolean strong = cores >= 8;

            // Never cap the frame rate and never sync it to the panel.
            set("maxFps", MAX_FPS);
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
            set("biomeBlendRadius", "0");         // no per-face biome colour blending

            // The menu background blur re-renders the world into a smaller buffer and
            // filters it every frame. Through a GL translation layer on a phone that is
            // brutal, and it is what makes the pointer lag behind the finger in menus.
            // 0 turns it off, and the menus then draw flat and stay responsive.
            set("menuBackgroundBlurriness", "0");

            MCOptionUtils.save();
            Log.i(TAG, "performance mode applied (strong=" + strong + ")");
            return true;
        } catch (Throwable t) {
            // Never let a settings tune break a launch.
            Log.w(TAG, "performance mode failed, ignoring", t);
            return false;
        }
    }

    /**
     * FEARPATCH: writes ONLY the two keys that decide whether the frame rate is capped
     * at all, and does so on EVERY launch.
     *
     * <p>The full {@link #apply(File)} tune is gated behind performance mode, so a player
     * who never turns that on - or the Smooth PvP profile - keeps the game's vanilla
     * {@code maxFps} (120) and {@code enableVsync} (true). That is how a session ends up
     * pinned just under a 60 Hz panel with no cap the player ever chose. These two keys are
     * therefore always written; render distance, simulation distance and every other value
     * stay gated behind performance mode.</p>
     *
     * @return true if the options were applied
     */
    public static boolean applyUncappedFrameRate(File gamedir) {
        try {
            MCOptionUtils.load(gamedir.getAbsolutePath());
            // Never cap the frame rate and never sync it to the panel.
            set("maxFps", MAX_FPS);
            set("enableVsync", "false");
            MCOptionUtils.save();
            Log.i(TAG, "uncapped frame-rate defaults applied");
            return true;
        } catch (Throwable t) {
            // Never let a settings tune break a launch.
            Log.w(TAG, "uncapped frame-rate defaults failed, ignoring", t);
            return false;
        }
    }

    private static void set(String key, String value) {
        MCOptionUtils.set(key, value);
    }
}
