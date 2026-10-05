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
 * Installs CustomSkinLoader into the game, so the account's skin is forced on ANY
 * server (cracked / offline-mode included) instead of relying on the server to send it.
 *
 * A resource pack can only change the *default* skin, so on a server that sends its own
 * skin (or resolves one for every player) it does nothing - that is why the skin showed in
 * singleplayer but not in multiplayer. CustomSkinLoader loads the skin client-side from a
 * skin API and overrides whatever the server sent, which is exactly what we want.
 *
 * This drops the mod jar into the instance's mods folder and writes an ExtraList entry
 * pointing at this network's CustomSkinAPI (https://craftynmc.onrender.com/csl/).
 * ExtraList is additive: Mojang and the other built-in sources keep working, and our entry
 * is only reached when they have nothing - which is the case for an offline account.
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
                installInto(base);
            } catch (Exception e) {
                Log.w(TAG, "Could not install CustomSkinLoader into " + base, e);
            }
        }
    }

    private static void installInto(File base) throws Exception {
        File mods = new File(base, "mods");
        File modFile = new File(mods, MOD_FILE_NAME);

        // Re-download only when the file is missing or does not match the expected hash,
        // so a good jar is never fetched twice.
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

        writeExtraList(base);
    }

    /** Adds this network to CustomSkinLoader's load list without replacing the defaults. */
    private static void writeExtraList(File base) throws Exception {
        File dir = new File(base, "CustomSkinLoader/ExtraList");
        if (!dir.isDirectory() && !dir.mkdirs()) return;
        File file = new File(dir, "craftynmc.json");
        String json = "[\n  {\n    \"name\": \"CraftynMC\",\n    \"type\": \"CustomSkinAPI\",\n"
                + "    \"root\": \"" + API_ROOT + "\"\n  }\n]\n";
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
        }
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
