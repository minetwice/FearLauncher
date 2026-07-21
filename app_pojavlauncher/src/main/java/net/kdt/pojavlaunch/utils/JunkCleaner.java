package net.kdt.pojavlaunch.utils;

import android.os.Handler;
import android.os.Looper;
import net.kdt.pojavlaunch.Tools;
import java.io.File;

public class JunkCleaner {
    private static Handler sCleanerHandler;
    private static Runnable sCleanerRunnable;

    public static void start() {
        if (sCleanerHandler == null) {
            sCleanerHandler = new Handler(Looper.getMainLooper());
        }
        if (sCleanerRunnable != null) {
            sCleanerHandler.removeCallbacks(sCleanerRunnable);
        }

        sCleanerRunnable = new Runnable() {
            @Override
            public void run() {
                // Run on Pojav's background executor to avoid blocking the UI thread
                Tools.sExecutorService.submit(() -> {
                    try {
                        // 1. Run Lightweight Java VM Garbage Collector
                        System.gc();
                        Runtime.getRuntime().gc();

                        // 2. Clear Temporary files inside Pojav DIR_DATA / temp directory
                        File tempDir = new File(Tools.DIR_DATA, "temp");
                        if (tempDir.exists() && tempDir.isDirectory()) {
                            File[] files = tempDir.listFiles();
                            if (files != null) {
                                for (File f : files) {
                                    // Remove if modified more than 10 seconds ago
                                    if (System.currentTimeMillis() - f.lastModified() > 10000) {
                                        f.delete();
                                    }
                                }
                            }
                        }

                        // 3. Clear Finalization queues
                        System.runFinalization();
                    } catch (Exception e) {
                        // Silent suppress
                    }
                });

                // Post delay of exactly 1 second
                sCleanerHandler.postDelayed(this, 1000);
            }
        };

        sCleanerHandler.postDelayed(sCleanerRunnable, 1000);
    }

    public static void stop() {
        if (sCleanerHandler != null && sCleanerRunnable != null) {
            sCleanerHandler.removeCallbacks(sCleanerRunnable);
        }
    }
}
