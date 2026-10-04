package net.kdt.pojavlaunch.skins;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.accounts.MinecraftAccount;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the player's own skin on screen, in the launcher and in the world.
 *
 * Two things have to line up for that. The local skin server needs a skin file it can
 * actually read, and a resource pack has to shadow the default player textures so the
 * skin shows on the player model itself. Both are resolved here, and both are redone on
 * every launch, because an instance can change under us between runs.
 */
public final class FearSkinSync {

    private static final String TAG = "FearSkinSync";

    public static final String PREF_SKIN_PATH = "active_skin_path";
    public static final String PREF_SKIN_ALEX = "active_skin_is_alex";

    private static final String PACK_NAME = "FEAR_Skin_Pack";
    private static final String CRAFTYN_BASE = "https://craftynmc.onrender.com/skins/";

    /** Render's free tier sleeps, and a cold start can take the best part of a minute. */
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final int ATTEMPTS = 3;

    private FearSkinSync() {}

    // ---- what skin are we actually showing ---------------------------------

    /**
     * The skin file to use, or null when there is nothing readable.
     *
     * A preference pointing at a file that has since gone is treated as no preference at
     * all, and the cached Craftyn skin is used instead - that is the case that used to
     * end up on Steve.
     */
    public static File resolveSkinFile(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String path = prefs.getString(PREF_SKIN_PATH, null);
        if (path != null && !path.equals("steve") && !path.equals("alex")) {
            File file = new File(path);
            if (file.isFile() && file.length() > 0) return file;
        }
        return cachedCraftynSkin();
    }

    private static File cachedCraftynSkin() {
        File dir = new File(Tools.DIR_GAME_HOME, "skins");
        File best = null;
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (!f.isFile() || !f.getName().startsWith("craftynmc_")) continue;
            if (f.length() == 0) continue;
            if (best == null || f.lastModified() > best.lastModified()) best = f;
        }
        return best;
    }

    /** True when the skin is a slim (Alex) model rather than the wide one. */
    public static boolean isSlimModel(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(PREF_SKIN_ALEX, false);
    }

    // ---- the resource pack --------------------------------------------------

    /**
     * Writes the skin into a resource pack for the game home and the selected instance.
     *
     * The texture paths matter: since 1.19.4 the default player textures live under
     * textures/entity/player/wide and /slim, and the older flat paths stopped being read.
     * Both are written so the pack works on either. The format range matters too - a pack
     * whose format is outside the game's range is simply refused, which is what a
     * hardcoded 15 did on anything modern.
     */
    public static void syncPack(Context context) {
        if (context == null) return;
        File skin = resolveSkinFile(context);
        boolean slim = isSlimModel(context);

        List<File> bases = new ArrayList<>();
        bases.add(new File(Tools.DIR_GAME_HOME));
        try {
            Instance instance = Instances.loadSelectedInstance();
            if (instance != null) {
                File dir = instance.getGameDirectory();
                if (dir != null && !dir.equals(new File(Tools.DIR_GAME_HOME))) bases.add(dir);
            }
        } catch (Throwable ignored) { }

        for (File base : bases) {
            try {
                writePack(base, skin, slim);
            } catch (Exception e) {
                Log.w(TAG, "Could not write the skin pack into " + base, e);
            }
        }
    }

    private static void writePack(File base, File skin, boolean slim) throws IOException {
        File packDir = new File(base, "resourcepacks/" + PACK_NAME);
        File assets = new File(packDir, "assets/minecraft/textures/entity");
        File wideDir = new File(assets, "player/wide");
        File slimDir = new File(assets, "player/slim");
        //noinspection ResultOfMethodCallIgnored
        wideDir.mkdirs();
        //noinspection ResultOfMethodCallIgnored
        slimDir.mkdirs();

        // Both models get the skin, so it shows whichever the account is set to.
        List<File> targets = new ArrayList<>();
        targets.add(new File(assets, "steve.png"));      // pre-1.19.4
        targets.add(new File(assets, "alex.png"));       // pre-1.19.4
        targets.add(new File(wideDir, "steve.png"));     // 1.19.4+
        targets.add(new File(slimDir, "alex.png"));      // 1.19.4+

        for (File target : targets) {
            if (skin == null) {
                if (target.exists()) //noinspection ResultOfMethodCallIgnored
                    target.delete();
            } else {
                copy(skin, target);
            }
        }

        writeString(new File(packDir, "pack.mcmeta"),
                "{\n  \"pack\": {\n    \"pack_format\": 46,\n"
                        + "    \"supported_formats\": [15, 99],\n"
                        + "    \"description\": \"FEAR Skin Pack - synced from your account\"\n  }\n}\n");

        enablePackInOptions(base);
    }

    /** Adds the pack to the enabled list, and repairs a list that no longer has it. */
    private static void enablePackInOptions(File base) {
        File options = new File(base, "options.txt");
        if (!options.isFile()) return;
        try {
            String content = readString(options);
            if (content == null) return;
            if (content.contains(PACK_NAME)) return;
            if (content.contains("resourcePacks:[")) {
                content = content.replace("resourcePacks:[",
                        "resourcePacks:[\"file/" + PACK_NAME + "\",");
            } else {
                content = content + "\nresourcePacks:[\"file/" + PACK_NAME + "\"]\n";
            }
            writeString(options, content);
        } catch (Exception e) {
            Log.w(TAG, "Could not enable the skin pack in " + options, e);
        }
    }

    // ---- launch time --------------------------------------------------------

    /**
     * Called just before the game starts. Cheap and local: it decides which skin file to
     * use, points the preference at it so the local skin server picks it up, and rewrites
     * the pack. Any network work happens afterwards, off this path.
     */
    public static void prepareForLaunch(Context context, MinecraftAccount account) {
        if (context == null) return;
        try {
            File skin = resolveSkinFile(context);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            if (skin != null) {
                prefs.edit().putString(PREF_SKIN_PATH, skin.getAbsolutePath()).apply();
            }
            syncPack(context);
        } catch (Exception e) {
            Log.w(TAG, "Could not prepare the skin for launch", e);
        }
        if (account != null) refreshInBackground(context, account);
    }

    /** Fire-and-forget: pulls a fresh copy for the next launch. */
    private static void refreshInBackground(Context context, MinecraftAccount account) {
        if (account.authType != net.kdt.pojavlaunch.authenticator.AuthType.CRAFTYN_MC) return;
        if (account.username == null) return;
        final Context app = context.getApplicationContext();
        new Thread(() -> downloadCraftynSkin(app, account.username, account.profileId)).start();
    }

    // ---- downloading --------------------------------------------------------

    /**
     * Fetches the account's skin from CraftynMC.
     *
     * The service sleeps when idle, so a single five second attempt fails often enough to
     * look random. This retries, waits properly for the body, and - most importantly -
     * leaves the previous skin alone when it cannot fetch a new one, so a bad connection
     * cannot drop the player back to Steve.
     */
    public static boolean downloadCraftynSkin(Context context, String username, String uuid) {
        if (context == null || username == null) return false;
        String dashed = dashUuid(uuid);
        String undashed = uuid != null ? uuid.replace("-", "").toLowerCase() : null;

        String[] candidates = { dashed != null ? CRAFTYN_BASE + dashed + ".png" : null,
                                undashed != null && !undashed.isEmpty() ? CRAFTYN_BASE + undashed + ".png" : null,
                                CRAFTYN_BASE + username + ".png" };

        for (String candidate : candidates) {
            if (candidate == null) continue;
            byte[] bytes = fetchWithRetries(candidate);
            if (bytes == null || bytes.length == 0) continue;
            try {
                File dir = new File(Tools.DIR_GAME_HOME, "skins");
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
                File target = new File(dir, "craftynmc_" + username + ".png");
                try (FileOutputStream out = new FileOutputStream(target)) {
                    out.write(bytes);
                }
                PreferenceManager.getDefaultSharedPreferences(context).edit()
                        .putString(PREF_SKIN_PATH, target.getAbsolutePath())
                        .apply();
                Log.i(TAG, "Skin refreshed for " + username);
                return true;
            } catch (Exception e) {
                Log.w(TAG, "Could not store the skin for " + username, e);
            }
        }
        Log.w(TAG, "No skin available for " + username + "; keeping whatever was there");
        return false;
    }

    private static byte[] fetchWithRetries(String url) {
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                conn.setInstanceFollowRedirects(true);
                if (conn.getResponseCode() != 200) continue;
                try (InputStream in = conn.getInputStream()) {
                    return readAll(in);
                }
            } catch (Exception e) {
                Log.w(TAG, "Skin fetch attempt " + attempt + " failed for " + url, e);
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        return null;
    }

    private static String dashUuid(String uuid) {
        if (uuid == null) return null;
        if (uuid.contains("-")) return uuid;
        if (uuid.length() != 32) return uuid;
        return uuid.substring(0, 8) + "-" + uuid.substring(8, 12) + "-" + uuid.substring(12, 16)
                + "-" + uuid.substring(16, 20) + "-" + uuid.substring(20, 32);
    }

    // ---- small helpers ------------------------------------------------------

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) bos.write(buffer, 0, read);
        return bos.toByteArray();
    }

    private static void copy(File source, File target) throws IOException {
        try (InputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }
    }

    private static void writeString(File file, String content) throws IOException {
        if (content == null) return;
        //noinspection ResultOfMethodCallIgnored
        file.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String readString(File file) {
        if (!file.isFile()) return null;
        try (InputStream in = new FileInputStream(file)) {
            return new String(readAll(in), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
