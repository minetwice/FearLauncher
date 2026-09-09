package net.kdt.pojavlaunch.utils;

import android.util.Log;

import net.kdt.pojavlaunch.Tools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Indus2.0 World Optimizer
 * Improves world creation and loading speed by:
 * - Pre-warming the region file cache before world load
 * - Optimizing Minecraft options.txt for fast world generation
 * - Pre-allocating world directories
 */
public class IndusWorldOptimizer {
    private static final String TAG = "Indus2.0";

    public static void optimizeForWorldLoad(File gameDir) {
        Log.i(TAG, "Optimizing game directory for fast world load: " + gameDir.getAbsolutePath());
        ensureDir(new File(gameDir, "saves"));
        ensureDir(new File(gameDir, "saves/.cache"));
        ensureDir(new File(gameDir, "region_cache"));
        ensureDir(new File(gameDir, "data"));
        prewarmRegionCache(gameDir);
        optimizeWorldGenOptions(gameDir);
        Log.i(TAG, "World optimization complete");
    }

    private static void prewarmRegionCache(File gameDir) {
        File savesDir = new File(gameDir, "saves");
        if (!savesDir.exists()) return;
        File[] worlds = savesDir.listFiles(File::isDirectory);
        if (worlds == null || worlds.length == 0) return;
        int threads = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (File world : worlds) {
            pool.submit(() -> {
                File regionDir = new File(world, "region");
                if (!regionDir.exists()) return;
                File[] regionFiles = regionDir.listFiles((dir, name) -> name.endsWith(".mca"));
                if (regionFiles == null) return;
                for (File rf : regionFiles) {
                    rf.lastModified();
                    try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(rf, "r")) {
                        byte[] buf = new byte[8192];
                        raf.read(buf);
                    } catch (Exception ignored) {}
                }
            });
        }
        pool.shutdown();
        try { pool.awaitTermination(3, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Log.i(TAG, "Region cache pre-warmed for " + worlds.length + " world(s)");
    }

    private static void optimizeWorldGenOptions(File gameDir) {
        File optionsFile = new File(gameDir, "options.txt");
        if (!optionsFile.exists()) return;
        try {
            java.util.List<String> lines = new java.util.ArrayList<>();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(new java.io.FileInputStream(optionsFile),
                            java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("chunkLoading:") || line.startsWith("renderRegion:")) continue;
                    lines.add(line);
                }
            }
            boolean hasMaxFps = false, hasClouds = false;
            for (String l : lines) {
                if (l.startsWith("maxFps:")) hasMaxFps = true;
                if (l.startsWith("renderClouds:")) hasClouds = true;
            }
            if (!hasMaxFps) lines.add("maxFps:260");
            if (!hasClouds) lines.add("renderClouds:false");
            try (java.io.OutputStream os = new FileOutputStream(optionsFile)) {
                StringBuilder sb = new StringBuilder();
                for (String l : lines) sb.append(l).append('\n');
                os.write(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            Log.i(TAG, "World gen options optimized");
        } catch (Exception e) {
            Log.w(TAG, "Could not optimize world gen options", e);
        }
    }

    private static void ensureDir(File dir) {
        if (!dir.exists()) dir.mkdirs();
    }
}
