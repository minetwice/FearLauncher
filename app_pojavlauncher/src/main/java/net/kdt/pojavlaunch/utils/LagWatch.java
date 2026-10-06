package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Watches a game session and writes down everything that could be making it stutter.
 *
 * The launcher runs the game's Java VM inside its own process, so the runtime numbers read
 * here ARE the game's: heap in use, garbage collections, live threads. On top of that it
 * samples the process CPU time, asks Android for the thermal status, and tails the game's
 * own log for the lines that name a cause - "Can't keep up", a mixin that failed to apply,
 * an out-of-memory, a chunk that would not load.
 *
 * Everything lands in <gameDir>/fear_lag_report.txt when the session ends. The report is
 * meant to be read and shared, so it leads with a verdict - the handful of things that
 * actually looked like the problem - and keeps the raw samples underneath as evidence.
 */
public final class LagWatch {

    private static final String TAG = "LagWatch";
    private static final long SAMPLE_INTERVAL_MS = 3000L;
    private static final String REPORT_NAME = "fear_lag_report.txt";

    /** Log lines that name a known cause of stutter, with the label to file them under. */
    private static final String[][] SIGNATURES = {
            {"Can't keep up", "server tick behind real time (tick rate)"},
            {"Running ", "chunk / world generation on the main thread"},
            {"OutOfMemoryError", "ran out of heap"},
            {"Full GC", "long garbage collection pause"},
            {"mixin", "a mod failed to apply a mixin"},
            {"Mixin apply failed", "a mod failed to apply a mixin"},
            {"Failed to load", "a resource or class would not load"},
            {"Exception", "an exception was thrown"},
            {"Unable to", "something the game needed was unavailable"},
            {"Sodium", "Sodium reported something"},
            {"Iris", "Iris / shaders reported something"},
            {"shader", "shader compilation or use"},
            {"texture", "texture upload"},
            {"Timed out", "a network or disk operation timed out"},
            {"GL_INVALID", "an OpenGL call was rejected"},
            {"GL_OUT_OF_MEMORY", "the GL driver ran out of memory"},
            {"throttl", "the device is thermally throttling"},
    };

    private static volatile boolean sRunning;
    private static Thread sThread;
    private static File sReport;

    private LagWatch() {}

    /** Starts watching. Safe to call more than once; a second call is ignored. */
    public static synchronized void start(Context context, File gameDir) {
        if (sRunning || gameDir == null) return;
        sRunning = true;
        final Context app = context.getApplicationContext();
        final File log = new File(gameDir, "logs/latestlog.txt");
        sReport = new File(gameDir, REPORT_NAME);
        sThread = new Thread(() -> loop(app, gameDir, log), "fear-lagwatch");
        sThread.setDaemon(true);
        sThread.start();
        Log.i(TAG, "Watching this session; the report will be " + sReport.getAbsolutePath());
    }

    public static void stop() {
        sRunning = false;
        Thread t = sThread;
        sThread = null;
        if (t != null) {
            try {
                t.join(4000);
            } catch (InterruptedException ignored) { }
        }
    }

    /** The report from the last finished session, or null if there has never been one. */
    public static File reportFile(File gameDir) {
        if (gameDir == null) return null;
        File f = new File(gameDir, REPORT_NAME);
        return f.isFile() ? f : null;
    }

    // ---- the sampler ----------------------------------------------------------------

    private static void loop(Context context, File gameDir, File logFile) {
        List<String> samples = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, String> firstExample = new LinkedHashMap<>();
        long logOffset = logFile.isFile() ? logFile.length() : 0L;
        long lastCpuJiffies = -1L;
        long lastSampleWall = 0L;

        String started = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());

        while (sRunning) {
            try {
                // Heap and threads: this is the game's own runtime, not the launcher's.
                Runtime rt = Runtime.getRuntime();
                long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L);
                long totalMb = rt.totalMemory() / (1024L * 1024L);
                long maxMb = rt.maxMemory() / (1024L * 1024L);
                int threads = Thread.getAllStackTraces().size();
                long gcCount = 0L, gcMillis = 0L;
                // Android ships no java.lang.management, so the game's collector is read from
                // the ART runtime instead: the same "GC runs / GC time for this process"
                // numbers, and this process is the one running the game.
                if (Build.VERSION.SDK_INT >= 23) {
                    try {
                        String gcRuns = android.os.Debug.getRuntimeStat("art.gc.gc-count");
                        String gcTime = android.os.Debug.getRuntimeStat("art.gc.gc-time");
                        if (gcRuns != null) gcCount = Long.parseLong(gcRuns.trim());
                        if (gcTime != null) gcMillis = Long.parseLong(gcTime.trim());
                    } catch (Throwable ignored) { }
                }

                // CPU: our own process, so /proc/self is readable.
                long cpuJiffies = readCpuJiffies();
                long now = System.currentTimeMillis();
                String cpu = "n/a";
                if (lastCpuJiffies >= 0 && lastSampleWall > 0 && cpuJiffies >= 0) {
                    long dj = cpuJiffies - lastCpuJiffies;
                    long dt = now - lastSampleWall;
                    if (dt > 0) cpu = String.format(Locale.US, "%.0f%%",
                            100.0 * dj / (dt * 10.0)); // 100 jiffies per second per core
                }
                lastCpuJiffies = cpuJiffies;
                lastSampleWall = now;

                int thermal = thermalStatus(context);

                samples.add(String.format(Locale.US,
                        "%s  heap %d/%d MB (max %d)  threads %d  gc %d runs / %d ms  cpu %s  thermal %d",
                        new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()),
                        usedMb, totalMb, maxMb, threads, gcCount, gcMillis, cpu, thermal));

                // Anything the game itself complained about since the last pass.
                if (logFile.isFile()) {
                    long size = logFile.length();
                    if (size < logOffset) logOffset = 0L; // log rotated
                    if (size > logOffset) {
                        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
                            reader.skip(logOffset);
                            String line;
                            while ((line = reader.readLine()) != null) note(line, counts, firstExample);
                        }
                        logOffset = size;
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "sample failed", t);
            }

            try {
                Thread.sleep(SAMPLE_INTERVAL_MS);
            } catch (InterruptedException e) {
                break;
            }
        }

        try {
            writeReport(gameDir, started, samples, counts, firstExample);
        } catch (Throwable t) {
            Log.w(TAG, "could not write the report", t);
        }
    }

    /** Files one log line under every signature it matches. */
    private static void note(String line, Map<String, Integer> counts, Map<String, String> first) {
        String lower = line.toLowerCase(Locale.US);
        for (String[] sig : SIGNATURES) {
            if (lower.contains(sig[0].toLowerCase(Locale.US))) {
                String label = sig[1];
                Integer c = counts.get(label);
                counts.put(label, c == null ? 1 : c + 1);
                if (!first.containsKey(label)) {
                    first.put(label, line.length() > 200 ? line.substring(0, 200) : line);
                }
            }
        }
    }

    private static long readCpuJiffies() {
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/self/stat"))) {
            String stat = r.readLine();
            if (stat == null) return -1L;
            int close = stat.lastIndexOf(')');
            if (close < 0) return -1L;
            String[] rest = stat.substring(close + 2).split(" ");
            // utime and stime are fields 14 and 15 of the whole line, i.e. 12 and 13 here.
            return Long.parseLong(rest[11]) + Long.parseLong(rest[12]);
        } catch (Throwable t) {
            return -1L;
        }
    }

    private static int thermalStatus(Context context) {
        if (Build.VERSION.SDK_INT < 29) return -1;
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return pm == null ? -1 : pm.getCurrentThermalStatus();
        } catch (Throwable t) {
            return -1;
        }
    }

    // ---- the report -----------------------------------------------------------------

    private static void writeReport(File gameDir, String started, List<String> samples,
                                    Map<String, Integer> counts, Map<String, String> first)
            throws Exception {
        File out = new File(gameDir, REPORT_NAME);
        StringBuilder sb = new StringBuilder();
        sb.append("FEAR LAUNCHER - LAG REPORT\n");
        sb.append("==========================\n");
        sb.append("Session started : ").append(started).append('\n');
        sb.append("Device          : ").append(Build.MANUFACTURER).append(' ')
                .append(Build.MODEL).append(" (Android ").append(Build.VERSION.RELEASE).append(")\n");
        sb.append("CPU cores       : ").append(Runtime.getRuntime().availableProcessors()).append('\n');
        sb.append("Java max heap   : ").append(Runtime.getRuntime().maxMemory() / (1024L * 1024L)).append(" MB\n");
        sb.append("Samples         : ").append(samples.size()).append('\n');
        sb.append('\n');

        sb.append("LIKELY CAUSES\n");
        sb.append("-------------\n");
        if (counts.isEmpty()) {
            sb.append("Nothing in the game log matched a known cause of stutter.\n");
        } else {
            List<Map.Entry<String, Integer>> ordered = new ArrayList<>(counts.entrySet());
            ordered.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
            for (Map.Entry<String, Integer> e : ordered) {
                sb.append(String.format(Locale.US, "  %-45s %d time(s)%n", e.getKey(), e.getValue()));
                String example = first.get(e.getKey());
                if (example != null) sb.append("      e.g. ").append(example.trim()).append('\n');
            }
        }
        sb.append('\n');

        sb.append("SAMPLES\n");
        sb.append("-------\n");
        for (String s : samples) sb.append(s).append('\n');

        sb.append('\n');
        sb.append("HOW TO READ THIS\n");
        sb.append("----------------\n");
        sb.append("thermal 0 is cool, 1..2 warm, 3+ is the device throttling itself - a rising\n");
        sb.append("number across the session means the frame rate is falling for thermal reasons,\n");
        sb.append("not because of a setting. 'gc N runs / M ms' growing quickly means the heap is\n");
        sb.append("too small or something is allocating hard. 'heap' near 'max' the whole time is\n");
        sb.append("the same story. A high cpu figure with a low frame rate means the game is busy\n");
        sb.append("off the render thread - world generation, or a mod on the main thread.\n");

        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        }
        Log.i(TAG, "Report written to " + out.getAbsolutePath());
    }
}
