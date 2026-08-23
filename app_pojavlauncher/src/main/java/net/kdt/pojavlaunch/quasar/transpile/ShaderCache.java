package net.kdt.pojavlaunch.quasar.transpile;

import android.util.Log;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * ShaderCache - Persistent disk cache for transpiled shaders keyed by source hash + capability profile.
 */
public class ShaderCache {
    private static final String TAG = "ShaderCache";
    private final File mCacheDir;

    public ShaderCache(File baseCacheDir) {
        mCacheDir = new File(baseCacheDir, "quasar_shader_cache");
        if (!mCacheDir.exists()) {
            mCacheDir.mkdirs();
        }
    }

    public static String calculateSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }

    public String getCachedShader(String shaderHash) {
        File cachedFile = new File(mCacheDir, shaderHash + ".glsl");
        if (!cachedFile.exists()) return null;

        try (BufferedReader reader = new BufferedReader(new FileReader(cachedFile))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            Log.i(TAG, "[Quasar] Cache HIT for shader hash: " + shaderHash);
            return sb.toString();
        } catch (IOException e) {
            Log.w(TAG, "[Quasar] Failed to read cached shader: " + e.getMessage());
            return null;
        }
    }

    public void putCachedShader(String shaderHash, String compiledSource) {
        File cachedFile = new File(mCacheDir, shaderHash + ".glsl");
        try (FileWriter writer = new FileWriter(cachedFile)) {
            writer.write(compiledSource);
            Log.i(TAG, "[Quasar] Cache STORED for shader hash: " + shaderHash);
        } catch (IOException e) {
            Log.w(TAG, "[Quasar] Failed to write shader to cache: " + e.getMessage());
        }
    }
}
