package net.kdt.pojavlaunch.skins;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import androidx.preference.PreferenceManager;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.accounts.Accounts;
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
    public static final String PREF_CAPE_PATH = "active_cape_path";

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
            // The cape is stored in this same folder, and it is a 64x32 texture rather
            // than a skin - but it is newer than the skin, so without this it wins the
            // "newest skin" test and gets written into the skin slots. That is what made
            // the player render as a cape and look like they had no skin at all.
            if (f.getName().endsWith("_cape.png")) continue;
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
                // The pack is written WITHOUT the skin. A resource pack can only replace
                // the DEFAULT player texture, so writing ours into those slots made every
                // player on a server who had no skin of their own wear ours - the whole
                // server looked identical. Passing null deletes those overrides again.
                // Per-player skins come from the profile (authlib-injector plus the local
                // skin server) and from CustomSkinLoader instead, both of which are
                // resolved per player.
                writePack(base, null, slim);
                writeLocalSkin(base, context, skin);
            } catch (Exception e) {
                Log.w(TAG, "Could not write the skin pack into " + base, e);
            }
        }
    }

    /**
     * Mirrors the account's skin and cape into CustomSkinLoader's own local folders.
     *
     * The mod reads LocalSkin/skins/&lt;username&gt;.png and LocalSkin/capes/&lt;username&gt;.png
     * before any network source, and the load list points at that folder with a Legacy
     * entry. This is what gives the cape a source at all: without these files the mod has
     * nothing to load a cape from, however many servers the list offers.
     */
    private static void writeLocalSkin(File base, Context context, File skin) {
        try {
            MinecraftAccount account = Accounts.getCurrent();
            String username = account != null ? account.username : null;
            if (username == null || username.isEmpty()) return;

            File skinsDir = new File(base, "CustomSkinLoader/LocalSkin/skins");
            File capesDir = new File(base, "CustomSkinLoader/LocalSkin/capes");
            //noinspection ResultOfMethodCallIgnored
            skinsDir.mkdirs();
            //noinspection ResultOfMethodCallIgnored
            capesDir.mkdirs();

            if (skin != null) {
                copy(skin, new File(skinsDir, username + ".png"));
            }

            File cape = resolveCapeFile(context);
            File capeTarget = new File(capesDir, username + ".png");
            if (cape != null) {
                copy(cape, capeTarget);
            } else if (capeTarget.isFile()) {
                // No cape on the account: drop any older one rather than keep showing it.
                //noinspection ResultOfMethodCallIgnored
                capeTarget.delete();
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not mirror the skin into CustomSkinLoader's local folder", e);
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
        // 1.19.4+ / 1.21.9+: the game resolves a player with no skin to one of NINE
        // default skins (steve, alex, ari, efe, kai, makena, noor, sunny, zuri), chosen
        // by UUID, and per model (wide/slim). Overwriting only steve.png/alex.png is not
        // enough - for most UUIDs the resolved default is one of the others, so the pack
        // was silently ignored and the player kept the vanilla default skin. Write our
        // skin into every default slot so it shows whichever one is resolved.
        String[] defaultSkins = {
            "steve", "alex", "ari", "efe", "kai", "makena", "noor", "sunny", "zuri"
        };
        for (String defaultSkin : defaultSkins) {
            targets.add(new File(wideDir, defaultSkin + ".png"));
            targets.add(new File(slimDir, defaultSkin + ".png"));
        }

        for (File target : targets) {
            if (skin == null) {
                if (target.exists()) //noinspection ResultOfMethodCallIgnored
                    target.delete();
            } else {
                copy(skin, target);
            }
        }

        // Minecraft 1.21.9+ refuses a pack whose declared max format is newer than
        // 64 unless min_format/max_format are present, and then REMOVES it from the
        // enabled list ("Pack declares support for version newer than 64, but is
        // missing mandatory fields min_format and max_format"). That is why the
        // skin pack silently stopped applying. Keep the legacy fields for older
        // versions and add the new range fields for modern ones.
        writeString(new File(packDir, "pack.mcmeta"),
                "{\n  \"pack\": {\n    \"pack_format\": 46,\n"
                        + "    \"supported_formats\": [15, 99],\n"
                        + "    \"min_format\": 15,\n"
                        + "    \"max_format\": 99,\n"
                        + "    \"description\": \"FEAR Skin Pack - synced from your account\"\n  }\n}\n");

        writePackIcon(packDir);
        enablePackInOptions(base);
    }

    /**
     * Adds the pack to the game's enabled list.
     *
     * A missing options.txt used to end the whole thing, which is the normal state on a
     * fresh instance - so the file is created instead. The list itself is spliced rather
     * than string-replaced: dropping the entry in front of "]" leaves a trailing comma on
     * an empty list, and a list the game cannot parse is a list it throws away.
     */
    private static void enablePackInOptions(File base) {
        File options = new File(base, "options.txt");
        try {
            String content = options.isFile() ? readString(options) : null;
            if (content == null) content = "";

            String entry = "\"file/" + PACK_NAME + "\"";
            String key = "resourcePacks:[";
            int at = content.indexOf(key);

            if (at >= 0) {
                int close = content.indexOf(']', at);
                if (close < 0) return;
                // Take any previous entry out first. Skipping the whole method when the name
                // was already present is what left installs that had the pack in the wrong
                // slot stuck there for good.
                String inner = trimCommas(content.substring(at + key.length(), close)
                        .replace(entry, ""));
                // Appended at the END, because in options.txt the LAST pack in the list has
                // the highest priority - it is where the player's own packs sit. In front of
                // vanilla the pack loaded and was then overridden by it, so it never showed.
                String spliced = inner.isEmpty() ? entry : inner + "," + entry;
                content = content.substring(0, at + key.length()) + spliced + content.substring(close);
            } else {
                if (!content.isEmpty() && !content.endsWith("\n")) content += "\n";
                content += key + entry + "]\n";
            }
            writeString(options, content);
        } catch (Exception e) {
            Log.w(TAG, "Could not enable the skin pack in " + options, e);
        }
    }

    /** Tidies the commas an entry leaves behind when it is pulled out of the list. */
    private static String trimCommas(String value) {
        String out = value.trim();
        while (out.startsWith(",")) out = out.substring(1).trim();
        while (out.endsWith(",")) out = out.substring(0, out.length() - 1).trim();
        while (out.contains(",,")) out = out.replace(",,", ",");
        return out;
    }

    /** A small icon so the pack reads as a pack rather than a blank slot in the list. */
    private static void writePackIcon(File packDir) {
        try {
            int size = 128;
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(
                    size, size, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);
            android.graphics.Paint paint = new android.graphics.Paint(
                    android.graphics.Paint.ANTI_ALIAS_FLAG);
            canvas.drawColor(0xFF000000);
            paint.setColor(0xFFFF2B3A);
            android.graphics.Path wedge = new android.graphics.Path();
            wedge.moveTo(0, size);
            wedge.lineTo(size, 0);
            wedge.lineTo(size, size);
            wedge.close();
            canvas.drawPath(wedge, paint);
            paint.setColor(0xFFFFFFFF);
            canvas.drawRect(size * 0.16f, size * 0.28f, size * 0.54f, size * 0.38f, paint);
            canvas.drawRect(size * 0.16f, size * 0.45f, size * 0.46f, size * 0.55f, paint);
            canvas.drawRect(size * 0.16f, size * 0.62f, size * 0.38f, size * 0.72f, paint);
            try (FileOutputStream out = new FileOutputStream(new File(packDir, "pack.png"))) {
                bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
            }
            bmp.recycle();
        } catch (Throwable ignored) {
            // An icon is a nicety; the pack works without one.
        }
    }

    // ---- launch time --------------------------------------------------------

    /** How long a launch waits for a fresh skin before falling back to the cached one. */
    private static final long PRELAUNCH_SKIN_WAIT_MS = 8_000L;

    /**
     * Called just before the game starts.
     *
     * The skin chosen on the website is fetched HERE, before the game reads it, so
     * the launch the player is watching already shows it. It used to be fetched in
     * the background, which meant the new skin only appeared one launch later - so a
     * skin picked on the website looked like it had not applied at all.
     *
     * The fetch is bounded: if CraftynMC is cold (its free tier sleeps) the launch
     * falls back to the cached skin after PRELAUNCH_SKIN_WAIT_MS and the fetch keeps
     * running for the next launch, so a slow server can never stall the game.
     */
    public static void prepareForLaunch(Context context, MinecraftAccount account) {
        if (context == null) return;
        try {
            // Any account with a name gets looked up, not just our own auth type. The
            // backend resolves the UUID first and then the username, so whoever is logged
            // in sees their own skin and cape, and a name it does not know simply finds
            // nothing - which is the same result as not asking at all.
            if (account != null && account.username != null) {
                final Context app = context.getApplicationContext();
                final String username = account.username;
                final String profileId = account.profileId;
                Thread fetch = new Thread(
                        () -> {
                            downloadCraftynSkin(app, username, profileId);
                            // The cape rides along with the skin, so the menu character is
                            // dressed the moment the game comes up.
                            downloadCraftynCape(app, username, profileId);
                        }, "fear-skin-prelaunch");
                fetch.setDaemon(true);
                fetch.start();
                fetch.join(PRELAUNCH_SKIN_WAIT_MS);
            }
            File skin = resolveSkinFile(context);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            if (skin != null) {
                // Re-derive the model from the file every launch: the resolved path can point
                // at a skin imported after the last run, and a stale model here is exactly
                // what puts a slim skin on the wide arms.
                prefs.edit()
                        .putString(PREF_SKIN_PATH, skin.getAbsolutePath())
                        .putBoolean(PREF_SKIN_ALEX, detectSlim(skin))
                        .apply();
            }
            syncPack(context);
            // Keep CustomSkinLoader in the instance so the skin shows on any server. Its
            // config is deliberately conservative to avoid clashing with Entity Model
            // Features (see CustomSkinInstaller for details).
            CustomSkinInstaller.ensureInstalled(context);
        } catch (Exception e) {
            Log.w(TAG, "Could not prepare the skin for launch", e);
        }
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
                // Rewriting identical bytes makes CustomSkinLoader and the game re-upload the
                // texture for nothing - the on-device lag report showed hundreds of such
                // uploads from this path. If the file already holds exactly these bytes, leave
                // it (and the pack) alone.
                boolean unchanged = bytesEqual(target, bytes);
                if (!unchanged) {
                    try (FileOutputStream out = new FileOutputStream(target)) {
                        out.write(bytes);
                    }
                }
                // Store the model the artwork was drawn for, not just the file. A slim skin
                // left on the classic model stretches the arms and looks broken, and the
                // preference is what both the menu character and the game's profile read.
                // Read it off the file on disk either way, so the model is corrected even when
                // the bytes did not change, and only write the preference when it is actually
                // missing or different so a no-op refresh stays a no-op.
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                boolean slim = detectSlim(target);
                SharedPreferences.Editor editor = null;
                if (!target.getAbsolutePath().equals(prefs.getString(PREF_SKIN_PATH, null))) {
                    editor = prefs.edit().putString(PREF_SKIN_PATH, target.getAbsolutePath());
                }
                if (slim != prefs.getBoolean(PREF_SKIN_ALEX, false)) {
                    editor = editor == null ? prefs.edit() : editor;
                    editor.putBoolean(PREF_SKIN_ALEX, slim);
                }
                if (editor != null) editor.apply();
                if (unchanged) {
                    Log.i(TAG, "Skin for " + username + " is unchanged; not rewriting it or re-applying the pack");
                } else {
                    Log.i(TAG, "Skin refreshed for " + username);
                }
                return true;
            } catch (Exception e) {
                Log.w(TAG, "Could not store the skin for " + username, e);
            }
        }
        Log.w(TAG, "No skin available for " + username + "; keeping whatever was there");
        return false;
    }

    /**
     * Fetches the account's cape from CraftynMC, the same way the skin is fetched.
     *
     * Returns the stored file, or null when the account has no cape - which is a normal
     * answer, not an error, so a missing cape simply leaves the character without one.
     */
    public static File downloadCraftynCape(Context context, String username, String uuid) {
        if (context == null || username == null) return null;
        String dashed = dashUuid(uuid);
        String undashed = uuid != null ? uuid.replace("-", "").toLowerCase() : null;

        String[] candidates = { dashed != null ? CRAFTYN_BASE + dashed + "_cape.png" : null,
                                undashed != null && !undashed.isEmpty() ? CRAFTYN_BASE + undashed + "_cape.png" : null,
                                CRAFTYN_BASE + username + "_cape.png" };

        for (String candidate : candidates) {
            if (candidate == null) continue;
            byte[] bytes = fetchWithRetries(candidate);
            if (bytes == null || bytes.length == 0) continue;
            try {
                File dir = new File(Tools.DIR_GAME_HOME, "skins");
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
                // Deliberately NOT "craftynmc_*": cachedCraftynSkin() treats anything
                // with that prefix as a skin, and the cape must never be taken for one.
                File target = new File(dir, "craftyncape_" + username + ".png");
                // Clear the old, colliding name from the build that had the bug.
                File stale = new File(dir, "craftynmc_" + username + "_cape.png");
                if (stale.isFile()) {
                    //noinspection ResultOfMethodCallIgnored
                    stale.delete();
                }
                try (FileOutputStream out = new FileOutputStream(target)) {
                    out.write(bytes);
                }
                PreferenceManager.getDefaultSharedPreferences(context).edit()
                        .putString(PREF_CAPE_PATH, target.getAbsolutePath())
                        .apply();
                Log.i(TAG, "Cape refreshed for " + username);
                return target;
            } catch (Exception e) {
                Log.w(TAG, "Could not store the cape for " + username, e);
            }
        }
        Log.i(TAG, "No cape for " + username + "; leaving the character capless");
        return null;
    }

    /**
     * Works out whether a skin was drawn for the slim (Alex) arms or the classic (Steve)
     * ones, because the atlas does not record it.
     *
     * The classic arm is four pixels wide and the slim arm three, so the outer column of
     * each arm face is painted in a classic skin and left fully transparent in a slim one.
     * On a 64x64 atlas those unused columns are x 47 and x 55 on the right arm (its front
     * and back faces) and x 39 and x 47 on the left arm, over rows y 20..31 and y 52..63.
     * Reading them is the accepted test. A legacy 64x32 file predates slim entirely, so it
     * is always classic.
     *
     * It is a heuristic, not metadata: an artist who deliberately erased the outer arm
     * column will read as slim, which is why the viewer still offers the two models by hand.
     */
    public static boolean detectSlim(File skinFile) {
        if (skinFile == null || !skinFile.isFile() || skinFile.length() == 0) return false;
        Bitmap bmp = null;
        try {
            bmp = BitmapFactory.decodeFile(skinFile.getAbsolutePath());
            if (bmp == null) return false;
            int w = bmp.getWidth();
            int h = bmp.getHeight();
            if (w < 64 || h < 64) return false;
            int scale = Math.max(1, w / 64);

            // Right arm: the block spans x 40..55, with its front face at x 44..47 and its
            // back face at x 52..55. A classic arm is four pixels wide and paints all four
            // columns of each face; a slim arm is three, so it leaves the outer column of
            // each face transparent - x 47 (front) and x 55 (back).
            int[] rightUnused = { 47, 55 };
            for (int x : rightUnused) {
                for (int y = 20; y < 32; y++) {
                    if (isOpaque(bmp, x * scale, y * scale)) return false;
                }
            }
            // Left arm: the block spans x 32..47, front face x 36..39 (slim leaves x 39
            // unused) and back face x 44..47 (slim leaves x 47 unused).
            int[] leftUnused = { 39, 47 };
            for (int x : leftUnused) {
                for (int y = 52; y < 64; y++) {
                    if (isOpaque(bmp, x * scale, y * scale)) return false;
                }
            }
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Could not read the skin model", e);
            return false;
        } finally {
            if (bmp != null) bmp.recycle();
        }
    }

    private static boolean isOpaque(Bitmap bmp, int x, int y) {
        if (x < 0 || y < 0 || x >= bmp.getWidth() || y >= bmp.getHeight()) return false;
        return ((bmp.getPixel(x, y) >>> 24) != 0);
    }

    /** The cape file to draw in the launcher, or null when there is none on disk. */
    public static File resolveCapeFile(Context context) {
        if (context == null) return null;
        String path = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREF_CAPE_PATH, null);
        if (path == null) return null;
        File file = new File(path);
        return (file.isFile() && file.length() > 0) ? file : null;
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

    /** True when the file already holds exactly these bytes, so a rewrite would be a no-op. */
    private static boolean bytesEqual(File file, byte[] bytes) {
        try {
            if (!file.isFile() || file.length() != bytes.length) return false;
            try (InputStream in = new FileInputStream(file)) {
                return java.util.Arrays.equals(readAll(in), bytes);
            }
        } catch (Exception e) {
            return false;
        }
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
