package net.kdt.pojavlaunch.utils;

import android.util.Log;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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

    // ---- Entity Texture Features ---------------------------------------------------------

    /** Launcher preference marking the ETF config as already neutralised (apply once). */
    private static final String KEY_ETF_APPLIED = "fear_etf_texture_churn_applied";

    /**
     * The Entity Texture Features options that make it regenerate entity textures per frame,
     * each with its cheapest value. Only a key that is ALREADY present in the config file is
     * touched, so a name we are not sure about is never added. The key names are ETF's own
     * config field names ({@code enableCustomTextures}, {@code enableEmissiveTextures}, ...),
     * which are exactly the ones the mod writes into {@code config/entity_texture_features.json}.
     */
    private static final String[][] ETF_HEAVY_KEYS = {
            // random / custom entity texture generation
            {"enableCustomTextures", "false"},
            {"enableCustomBlockEntities", "false"},
            // emissive texture generation
            {"enableEmissiveTextures", "false"},
            {"enableEmissiveBlockEntities", "false"},
            {"enableEnchantedTextures", "false"},
            {"alwaysCheckVanillaEmissiveSuffix", "false"},
            // per-frame animation and player-skin texture regeneration
            {"enableBlinking", "false"},
            {"enableArmorAndTrims", "false"},
            {"skinFeaturesEnabled", "false"},
            {"skinFeaturesEnableTransparency", "false"},
            {"skinFeaturesEnableFullTransparency", "false"},
            {"tryETFTransparencyForAllSkins", "false"},
            {"enableEnemyTeamPlayersSkinFeatures", "false"},
            {"use3DSkinLayerPatch", "false"},
            {"enableFullBodyWardenTextures", "false"},
    };

    /**
     * FEARPATCH: stops Entity Texture Features from regenerating entity textures every frame.
     *
     * <p>ETF's random/custom and emissive texture features rebuild and re-upload entity textures
     * during play; each upload forces a GPU sync, and the lag reports showed hundreds of them per
     * session. This sets those options to their cheapest values in the instance's own ETF config.
     *
     * <p>It touches ONLY a config that already exists - it never creates one - and only the
     * specific heavy keys, each only when the key is already present. Every other key is left
     * exactly as it was, and the file is written back as pretty-printed JSON in the same shape
     * the mod uses. A preference records that it has run, so the file is rewritten once and not
     * on every launch. Everything is best-effort: a failure here must never stop a launch.</p>
     *
     * @return true if the config existed and was processed
     */
    public static boolean applyEntityTextureFeatures(File gamedir) {
        try {
            if (gamedir == null) return false;
            // Same instance directory options.txt is read from, plus ETF's own config subfolder.
            File config = new File(new File(gamedir, "config"), "entity_texture_features.json");
            if (!config.isFile()) {
                // Never create it - a user without ETF gets nothing.
                Log.i(TAG, "no config/entity_texture_features.json here, ETF left alone");
                return false;
            }
            // Apply once: rewriting the file on every launch would fight the mod and the user.
            if (LauncherPreferences.DEFAULT_PREF != null
                    && LauncherPreferences.DEFAULT_PREF.getBoolean(KEY_ETF_APPLIED, false)) {
                return false;
            }

            JsonObject root = JsonParser.parseString(Tools.read(config)).getAsJsonObject();
            List<String> changed = new ArrayList<>();
            for (String[] entry : ETF_HEAVY_KEYS) {
                String key = entry[0];
                if (!root.has(key)) continue; // only touch keys already in the file
                JsonElement value = root.get(key);
                if (value == null || !value.isJsonPrimitive()
                        || !value.getAsJsonPrimitive().isBoolean()) continue;
                boolean cheapest = Boolean.parseBoolean(entry[1]);
                if (value.getAsBoolean() == cheapest) continue; // already cheapest
                root.addProperty(key, cheapest);
                changed.add(key + "=" + cheapest);
            }

            if (changed.isEmpty()) {
                Log.i(TAG, "ETF config already at the cheapest texture values");
            } else {
                Tools.write(config, Tools.GLOBAL_GSON.toJson(root));
                Log.i(TAG, "ETF texture churn reduced, set " + changed);
            }
            if (LauncherPreferences.DEFAULT_PREF != null) {
                LauncherPreferences.DEFAULT_PREF.edit().putBoolean(KEY_ETF_APPLIED, true).apply();
            }
            return true;
        } catch (Throwable t) {
            // Never let a config tune break a launch.
            Log.w(TAG, "ETF config tune failed, ignoring", t);
            return false;
        }
    }
}
