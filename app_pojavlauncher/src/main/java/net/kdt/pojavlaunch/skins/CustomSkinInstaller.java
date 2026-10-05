package net.kdt.pojavlaunch.skins;

import android.content.Context;
import android.util.Log;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Installs CustomSkinLoader into the game so the account's skin and cape show on ANY server
 * (cracked / offline-mode included), without disturbing the rest of the mod pack.
 *
 * Why the config is deliberately conservative: a resource pack can only change the
 * *default* skin, so on a server that sends its own skin it does nothing. CustomSkinLoader
 * loads the skin client-side instead - but its HD/transparent-skin and skull handling patch
 * the same texture pipeline that Entity Texture Features / Entity Model Features use, and
 * on this pack that made EMF throw
 *   "ArrayIndexOutOfBoundsException: Index -1 out of bounds for length 513"
 * for every entity model and hang the game on the loading screen. Turning those options off
 * keeps CustomSkinLoader to the plain skin/cape path, which is all we need.
 *
 * Escape hatch: if a file named DISABLED exists in <gameDir>/CustomSkinLoader/, the launcher
 * removes the mod and its config again instead of installing them.
 */
public final class CustomSkinInstaller {

    private static final String TAG = "CustomSkinInstaller";

    private static final String MOD_FILE_NAME = "CustomSkinLoader_Universal-15.1.jar";
    private static final String MOD_URL =
            "https://github.com/xfl03/MCCustomSkinLoader/releases/download/v15.1/" + MOD_FILE_NAME;
    /** Expected SHA-256 of the jar; a mismatch aborts the install rather than ship a bad mod. */
    private static final String MOD_SHA256 =
            "c8769a38b3cc1da2a7b2f5827d1a53a24cd1ef6d0784b1eb545569f5882a2ffb";

    private static final String API_ROOT = "https://craftynmc.onrender.com/csl/";

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 60_000;

    private CustomSkinInstaller() {}

    /** Idempotent and safe to call on every launch. */
    public static void ensureInstalled(Context context) {
        if (context == null) return;

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
                if (new File(base, "CustomSkinLoader/DISABLED").isFile()) {
                    removeFrom(base);
                } else {
                    installInto(base);
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not prepare CustomSkinLoader in " + base, e);
            }
        }
    }

    private static void installInto(File base) throws Exception {
        File mods = new File(base, "mods");
        File modFile = new File(mods, MOD_FILE_NAME);

        boolean needsInstall = !modFile.isFile()
                || modFile.length() == 0
                || !MOD_SHA256.equalsIgnoreCase(sha256(modFile));
        if (needsInstall) {
            if (!mods.isDirectory() && !mods.mkdirs()) return;
            byte[] bytes = download(MOD_URL);
            if (bytes == null || bytes.length == 0) {
                Log.w(TAG, "CustomSkinLoader download failed; leaving mods as they are");
                return;
            }
            try (FileOutputStream out = new FileOutputStream(modFile)) {
                out.write(bytes);
            }
            Log.i(TAG, "Installed " + MOD_FILE_NAME);
        }

        writeConfig(base);
    }

    /**
     * Writes the CustomSkinLoader config: CraftynMC first (an offline account resolves to our
     * skin and cape), Mojang as the fallback for everyone else, and the mod-pack-conflicting
     * features turned off.
     */
    private static void writeConfig(File base) throws Exception {
        File dir = new File(base, "CustomSkinLoader");
        if (!dir.isDirectory() && !dir.mkdirs()) return;
        File file = new File(dir, "CustomSkinLoader.json");
        String json = "{\n"
                + "  \"enable\": true,\n"
                + "  \"enableCape\": true,\n"
                + "  \"enableTransparentSkin\": false,\n"
                + "  \"forceLoadAllTextures\": false,\n"
                + "  \"enableSkull\": false,\n"
                + "  \"enableDynamicSkull\": false,\n"
                + "  \"enableUpdateSkull\": false,\n"
                + "  \"enableLocalProfileCache\": false,\n"
                + "  \"loadlist\": [\n"
                + "    {\n"
                + "      \"name\": \"LocalSkin\",\n"
                + "      \"type\": \"Legacy\",\n"
                + "      \"root\": \"LocalSkin/\"\n"
                + "    },\n"
                + "    {\n"
                + "      \"name\": \"CraftynMC\",\n"
                + "      \"type\": \"CustomSkinAPI\",\n"
                + "      \"root\": \"" + API_ROOT + "\"\n"
                + "    },\n"
                + "    {\n"
                + "      \"name\": \"Mojang\",\n"
                + "      \"type\": \"MojangAPI\"\n"
                + "    }\n"
                + "  ]\n"
                + "}\n";
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void removeFrom(File base) {
        delete(new File(base, "mods/" + MOD_FILE_NAME));
        delete(new File(base, "CustomSkinLoader/CustomSkinLoader.json"));
        delete(new File(base, "CustomSkinLoader/Core/CustomSkinLoader-Common.jar"));
    }

    private static void delete(File file) {
        try {
            if (file.isFile() && !file.delete()) Log.w(TAG, "Could not delete " + file);
        } catch (Throwable ignored) { }
    }

    private static byte[] download(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            if (conn.getResponseCode() != 200) return null;
            try (InputStream in = conn.getInputStream();
                 ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int read;
                while ((read = in.read(buf)) != -1) bos.write(buf, 0, read);
                return bos.toByteArray();
            }
        } catch (Exception e) {
            Log.w(TAG, "Download failed for " + url, e);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String sha256(File file) {
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) != -1) md.update(buf, 0, read);
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
