package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import net.kdt.pojavlaunch.Tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * FearDiag - watches a game session and writes down everything that could be making it
 * stutter, overheat or throttle, and what to build next.
 *
 * The launcher runs the game's Java VM inside its own process, so the runtime numbers read
 * here ARE the game's: heap in use, garbage collections, live threads, and the CPU time of
 * every thread it owns. On top of that it samples the CPU core frequencies and the thermal
 * zones straight from sysfs (which is what actually shows a device throttling itself),
 * asks Android for the thermal status and the battery temperature, and tails the game's own
 * log for the lines that name a cause - "Can't keep up", a mixin that failed to apply, an
 * out-of-memory - as well as the renderer's own per-frame marker, which gives the frame rate.
 *
 * Everything lands in <gameDir>/fear_lag_report.txt when the session ends, and a partial
 * copy is written every 30 seconds so a crash or a kill still leaves usable evidence. The
 * report leads with a verdict - what actually looked like the problem - and ends with the
 * features that would fix it, so the file can be sent off and acted on.
 */
public final class LagWatch {

    private static final String TAG = "LagWatch";
    private static final long SAMPLE_INTERVAL_MS = 2000L;
    private static final long PARTIAL_WRITE_EVERY_MS = 30000L;
    private static final String REPORT_NAME = "fear_lag_report.txt";

    /** Log lines that name a known cause of stutter, with the label to file them under. */
    private static final String[][] SIGNATURES = {
            {"Can't keep up", "server tick behind real time (tick rate)"},
            {"OutOfMemoryError", "ran out of heap"},
            {"Full GC", "long garbage collection pause"},
            {"Mixin apply failed", "a mod failed to apply a mixin"},
            {"mixin", "a mod failed to apply a mixin"},
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
            {"Could not load", "a pack or resource failed to load"},
            {"not valid", "a resource pack was rejected"},
    };

    /** Line shapes that are known to be printed once per presented frame. */
    private static final String[] FRAME_HINTS = {
            "osmdiaG[swap]", "swap]", "[swap", "frametime", "frame time", "fps:", "present"
    };

    private static volatile boolean sRunning;
    private static Thread sThread;
    private static File sReport;
    private static File sMirror;
    private static String sRenderer = "?";

    private LagWatch() {}

    /** Starts watching. Safe to call more than once; a second call is ignored. */
    public static synchronized void start(Context context, File gameDir) {
        start(context, gameDir, "?");
    }

    /** Starts watching, remembering which renderer the session is running. */
    public static synchronized void start(Context context, File gameDir, String renderer) {
        if (sRunning || gameDir == null) return;
        sRunning = true;
        sRenderer = renderer == null ? "?" : renderer;
        final Context app = context.getApplicationContext();
        sReport = new File(gameDir, REPORT_NAME);
        // A second copy next to latestlog.txt, which is the file the launcher already
        // knows how to share, so the report is easy to send off.
        sMirror = new File(Tools.DIR_GAME_HOME, REPORT_NAME);
        sThread = new Thread(() -> loop(app, gameDir), "fear-diag");
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

    // ---- the sampler ------------------------------------------------------------------

    /** One pass over the machine. Everything here is best-effort. */
    private static final class Sample {
        long wallMs;
        long heapUsedMb, heapTotalMb, heapMaxMb;
        int threads;
        long gcRuns, gcMs;
        double cpuPct = -1;
        List<String> topThreads = new ArrayList<>();
        int[] coreFreqKhz = new int[0];
        int[] coreMaxKhz = new int[0];
        double thermalMaxC = Double.NaN;
        String thermalName = "?";
        double batteryC = Double.NaN;
        int thermalStatus = -1;
        double fps = -1;
        String frameMarker = "";
    }

    private static void loop(Context context, File gameDir) {
        List<Sample> samples = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, String> firstExample = new LinkedHashMap<>();

        List<LogTail> tails = new ArrayList<>();
        tails.add(new LogTail(new File(gameDir, "logs/latestlog.txt")));
        tails.add(new LogTail(new File(gameDir, "latestlog.txt")));
        if (Tools.DIR_GAME_HOME != null) {
            tails.add(new LogTail(new File(Tools.DIR_GAME_HOME, "latestlog.txt")));
        }

        long lastCpuJiffies = -1L;
        long lastWall = 0L;
        Map<Integer, Long> lastThreadJiffies = new LinkedHashMap<>();
        long lastPartial = 0L;
        String started = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());

        while (sRunning) {
            Sample s = new Sample();
            try {
                s.wallMs = System.currentTimeMillis();

                Runtime rt = Runtime.getRuntime();
                s.heapUsedMb = (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L);
                s.heapTotalMb = rt.totalMemory() / (1024L * 1024L);
                s.heapMaxMb = rt.maxMemory() / (1024L * 1024L);
                // Thread count straight from /proc: Thread.getAllStackTraces() suspends every
                // thread to walk the stacks, which is far too expensive to run twice a second.
                s.threads = readThreadCount();

                // Android ships no java.lang.management, so the collector is read from the
                // ART runtime instead: GC runs / GC time for this process, which is the one
                // running the game.
                if (Build.VERSION.SDK_INT >= 23) {
                    try {
                        String runs = android.os.Debug.getRuntimeStat("art.gc.gc-count");
                        String time = android.os.Debug.getRuntimeStat("art.gc.gc-time");
                        if (runs != null) s.gcRuns = Long.parseLong(runs.trim());
                        if (time != null) s.gcMs = Long.parseLong(time.trim());
                    } catch (Throwable ignored) { }
                }

                long cpuJiffies = readCpuJiffies();
                if (lastCpuJiffies >= 0 && lastWall > 0 && cpuJiffies >= 0) {
                    long dj = cpuJiffies - lastCpuJiffies;
                    long dt = s.wallMs - lastWall;
                    if (dt > 0) s.cpuPct = 100.0 * dj / (dt * 10.0); // 100 jiffies/s per core
                }
                lastCpuJiffies = cpuJiffies;
                lastWall = s.wallMs;

                s.topThreads = readTopThreads(lastThreadJiffies, s.wallMs);
                s.coreFreqKhz = readCoreFreq("scaling_cur_freq");
                s.coreMaxKhz = readCoreFreq("cpuinfo_max_freq");
                String[] thermal = readThermal();
                if (thermal != null) {
                    s.thermalMaxC = Double.parseDouble(thermal[0]);
                    s.thermalName = thermal[1];
                }
                s.batteryC = batteryTemp(context);
                s.thermalStatus = thermalStatus(context);

                // Frame rate: the renderer prints one line per presented frame. We count
                // the most common line shape this window, preferring a shape that looks
                // like a known frame marker, and divide by the window length.
                FrameTally tally = new FrameTally();
                for (LogTail t : tails) t.read(counts, firstExample, tally);
                if (tally.total > 0 && lastWall > 0) {
                    s.fps = tally.fpsFor(SAMPLE_INTERVAL_MS / 1000.0);
                    s.frameMarker = tally.bestShape();
                }

                samples.add(s);
            } catch (Throwable t) {
                Log.w(TAG, "sample failed", t);
            }

            long now = System.currentTimeMillis();
            if (now - lastPartial >= PARTIAL_WRITE_EVERY_MS) {
                lastPartial = now;
                writeReport(gameDir, started, samples, counts, firstExample, false);
            }

            try {
                Thread.sleep(SAMPLE_INTERVAL_MS);
            } catch (InterruptedException e) {
                break;
            }
        }

        writeReport(gameDir, started, samples, counts, firstExample, true);
    }

    /** A log file we keep reading from where we left off. */
    private static final class LogTail {
        private final File file;
        private long offset = -1L;

        LogTail(File file) { this.file = file; }

        void read(Map<String, Integer> counts, Map<String, String> first, FrameTally tally) {
            try {
                if (!file.isFile()) return;
                long size = file.length();
                if (offset < 0 || size < offset) offset = 0L; // first pass, or the log rotated
                if (size <= offset) return;
                try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                    raf.seek(offset);
                    String line;
                    while ((line = raf.readLine()) != null) {
                        note(line, counts, first);
                        tally.add(line);
                    }
                }
                offset = size;
            } catch (Throwable ignored) { }
        }
    }

    /** Counts how often each line shape repeats, to find the renderer's frame marker. */
    private static final class FrameTally {
        private final Map<String, Integer> shapes = new LinkedHashMap<>();
        int total;

        void add(String line) {
            if (line == null || line.isEmpty()) return;
            total++;
            String shape = shape(line);
            Integer c = shapes.get(shape);
            shapes.put(shape, c == null ? 1 : c + 1);
        }

        private static String shape(String line) {
            // Collapse every run of digits / hex to '#' so lines that differ only in
            // their numbers (a frame counter, a coordinate) count as one shape.
            return line.replaceAll("[0-9a-fA-F]+", "#").trim();
        }

        private boolean looksLikeFrame(String shape) {
            String lower = shape.toLowerCase(Locale.US);
            for (String hint : FRAME_HINTS) {
                if (lower.contains(hint.toLowerCase(Locale.US))) return true;
            }
            return false;
        }

        String bestShape() {
            String best = null;
            int bestCount = 0;
            for (Map.Entry<String, Integer> e : shapes.entrySet()) {
                boolean hint = looksLikeFrame(e.getKey());
                // A hinted shape wins over a slightly more frequent unhinted one.
                int score = e.getValue() * (hint ? 4 : 1);
                if (score > bestCount) {
                    bestCount = score;
                    best = e.getKey();
                }
            }
            return best == null ? "" : best;
        }

        double fpsFor(double seconds) {
            String best = bestShape();
            Integer c = shapes.get(best);
            if (c == null || seconds <= 0) return -1;
            return c / seconds;
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

    // ---- the readers ------------------------------------------------------------------

    private static volatile long lastThreadSampleMs = 0L;

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

    /** The busiest threads in our own process, as "name 12%" strings. */
    private static List<String> readTopThreads(Map<Integer, Long> last, long wallMs) {
        List<String> out = new ArrayList<>();
        List<double[]> ranked = new ArrayList<>();
        List<String> names = new ArrayList<>();
        try {
            File taskDir = new File("/proc/self/task");
            File[] tasks = taskDir.listFiles();
            if (tasks == null) return out;
            long dt = Math.max(1, wallMs - lastThreadSampleMs);
            lastThreadSampleMs = wallMs;
            for (File task : tasks) {
                int tid;
                try {
                    tid = Integer.parseInt(task.getName());
                } catch (Throwable ignored2) {
                    continue;
                }
                long jiffies = readTaskJiffies(task);
                if (jiffies < 0) continue;
                Long prev = last.get(tid);
                last.put(tid, jiffies);
                if (prev == null) continue;
                double pct = 100.0 * (jiffies - prev) / (dt * 10.0);
                if (pct <= 0.5) continue;
                ranked.add(new double[]{pct});
                names.add(readTaskName(task) + " (" + tid + ")");
            }
        } catch (Throwable ignored) { }
        // Sort by usage, keep the top few.
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) idx.add(i);
        idx.sort((a, b) -> Double.compare(ranked.get(b)[0], ranked.get(a)[0]));
        for (int i = 0; i < idx.size() && i < 6; i++) {
            int k = idx.get(i);
            out.add(String.format(Locale.US, "%s %.0f%%", names.get(k), ranked.get(k)[0]));
        }
        return out;
    }

    private static int readThreadCount() {
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/self/status"))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("Threads:")) {
                    return Integer.parseInt(line.substring(8).trim());
                }
            }
        } catch (Throwable ignored) { }
        return -1;
    }

    private static long readTaskJiffies(File task) {
        try (BufferedReader r = new BufferedReader(new FileReader(new File(task, "stat")))) {
            String stat = r.readLine();
            if (stat == null) return -1L;
            int close = stat.lastIndexOf(')');
            if (close < 0) return -1L;
            String[] rest = stat.substring(close + 2).split(" ");
            return Long.parseLong(rest[11]) + Long.parseLong(rest[12]);
        } catch (Throwable t) {
            return -1L;
        }
    }

    private static String readTaskName(File task) {
        try (BufferedReader r = new BufferedReader(new FileReader(new File(task, "comm")))) {
            String n = r.readLine();
            return n == null ? "?" : n.trim();
        } catch (Throwable t) {
            return "?";
        }
    }

    /** Per-core frequency in kHz, from /sys/devices/system/cpu/cpuN/cpufreq/<which>. */
    private static int[] readCoreFreq(String which) {
        int cores = Runtime.getRuntime().availableProcessors();
        int[] out = new int[cores];
        int found = 0;
        for (int i = 0; i < cores; i++) {
            out[i] = readInt("/sys/devices/system/cpu/cpu" + i + "/cpufreq/" + which);
            if (out[i] > 0) found++;
        }
        if (found == 0) return new int[0];
        return out;
    }

    /** The hottest thermal zone as {temperatureC, zoneName}, or null when none is readable. */
    private static String[] readThermal() {
        double max = Double.NEGATIVE_INFINITY;
        String name = "?";
        try {
            File dir = new File("/sys/class/thermal");
            File[] zones = dir.listFiles();
            if (zones == null) return null;
            for (File zone : zones) {
                if (!zone.getName().startsWith("thermal_zone")) continue;
                int milli = readInt(new File(zone, "temp").getAbsolutePath());
                if (milli <= 0) continue;
                double c = milli / 1000.0;
                if (c > max) {
                    max = c;
                    String t = readString(new File(zone, "type").getAbsolutePath());
                    name = t == null ? zone.getName() : t.trim();
                }
            }
        } catch (Throwable ignored) { }
        if (max == Double.NEGATIVE_INFINITY) return null;
        return new String[]{String.format(Locale.US, "%.1f", max), name};
    }

    private static double batteryTemp(Context context) {
        // Prefer the battery's own sysfs node, fall back to the sticky battery intent.
        int tenths = readInt("/sys/class/power_supply/battery/temp");
        if (tenths > 0) return tenths / 10.0;
        try {
            Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (intent == null) return Double.NaN;
            int t = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
            if (t == Integer.MIN_VALUE) return Double.NaN;
            return t / 10.0;
        } catch (Throwable t) {
            return Double.NaN;
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

    private static int readInt(String path) {
        try {
            String s = readString(path);
            if (s == null) return -1;
            return Integer.parseInt(s.trim());
        } catch (Throwable t) {
            return -1;
        }
    }

    private static String readString(String path) {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            return r.readLine();
        } catch (Throwable t) {
            return null;
        }
    }

    // ---- the report -------------------------------------------------------------------

    private static void writeReport(File gameDir, String started, List<Sample> samples,
                                    Map<String, Integer> counts, Map<String, String> first,
                                    boolean finished) {
        try {
            StringBuilder sb = new StringBuilder();
            SimpleDateFormat hms = new SimpleDateFormat("HH:mm:ss", Locale.US);

            sb.append("FEAR LAUNCHER - SESSION DIAGNOSTIC\n");
            sb.append("=================================\n");
            sb.append("Session started : ").append(started).append('\n');
            sb.append("Report written  : ").append(hms.format(new Date()))
                    .append(finished ? " (session end)\n" : " (in progress)\n");
            sb.append("Device          : ").append(Build.MANUFACTURER).append(' ')
                    .append(Build.MODEL).append(" (Android ").append(Build.VERSION.RELEASE).append(")\n");
            sb.append("Renderer        : ").append(sRenderer).append('\n');
            sb.append("CPU cores       : ").append(Runtime.getRuntime().availableProcessors()).append('\n');
            sb.append("Java max heap   : ").append(Runtime.getRuntime().maxMemory() / (1024L * 1024L)).append(" MB\n");
            sb.append("Samples         : ").append(samples.size()).append('\n');
            sb.append('\n');

            Analysis a = analyse(samples, counts);

            // -- what actually looked wrong --
            sb.append("VERDICT\n");
            sb.append("-------\n");
            if (a.findings.isEmpty()) {
                sb.append("Nothing stood out: no thermal climb, no throttling, no GC storm, no\n");
                sb.append("single thread hogging the CPU. If it still felt laggy, the log section\n");
                sb.append("below is where the cause will be.\n");
            } else {
                for (String f : a.findings) sb.append("  - ").append(f).append('\n');
            }
            sb.append('\n');

            // -- measured numbers --
            sb.append("MEASURED\n");
            sb.append("--------\n");
            sb.append(String.format(Locale.US, "  frame rate   : %.1f avg, %.1f min, %.1f max\n",
                    a.fpsAvg, a.fpsMin, a.fpsMax));
            sb.append(String.format(Locale.US, "  process CPU  : %.0f%% avg, %.0f%% peak\n", a.cpuAvg, a.cpuMax));
            sb.append(String.format(Locale.US, "  thermal      : %.1fC avg, %.1fC peak (%s)\n",
                    a.tempAvg, a.tempMax, a.tempName));
            sb.append(String.format(Locale.US, "  battery      : %.1fC avg, %.1fC peak\n", a.battAvg, a.battMax));
            sb.append("  CPU cores    : ").append(a.coreSummary).append('\n');
            sb.append(String.format(Locale.US, "  throttling   : %s\n", a.throttleNote));
            sb.append(String.format(Locale.US, "  GC           : %d runs / %d ms over the session\n",
                    a.gcRuns, a.gcMs));
            sb.append(String.format(Locale.US, "  heap         : %.0f MB avg used of %d MB max\n", a.heapAvg, a.heapMax));
            if (!a.frameMarker.isEmpty()) {
                sb.append("  frame marker : ").append(a.frameMarker).append('\n');
            }
            sb.append('\n');

            // -- what to build next --
            sb.append("RECOMMENDED FEATURES (what to add to FearLauncher)\n");
            sb.append("-------------------------------------------------\n");
            if (a.recommendations.isEmpty()) {
                sb.append("  Nothing urgent - the session was clean. Send this file anyway; the\n");
                sb.append("  samples below still show how the device behaves over time.\n");
            } else {
                for (String r : a.recommendations) sb.append("  - ").append(r).append('\n');
            }
            sb.append('\n');

            // -- what the game itself complained about --
            sb.append("GAME LOG: LIKELY CAUSES\n");
            sb.append("-----------------------\n");
            if (counts.isEmpty()) {
                sb.append("Nothing in the game log matched a known cause of stutter.\n");
            } else {
                List<Map.Entry<String, Integer>> ordered = new ArrayList<>(counts.entrySet());
                ordered.sort((x, y) -> Integer.compare(y.getValue(), x.getValue()));
                for (Map.Entry<String, Integer> e : ordered) {
                    sb.append(String.format(Locale.US, "  %-45s %d time(s)%n", e.getKey(), e.getValue()));
                    String example = first.get(e.getKey());
                    if (example != null) sb.append("      e.g. ").append(example.trim()).append('\n');
                }
            }
            sb.append('\n');

            // -- raw evidence --
            sb.append("SAMPLES\n");
            sb.append("-------\n");
            sb.append("  time      fps    cpu   heapMB  thr  gc(runs/ms)   hottestC  cores(kHz)\n");
            for (Sample s : samples) sb.append(formatSample(s, hms)).append('\n');

            sb.append('\n');
            sb.append("HOW TO READ THIS\n");
            sb.append("----------------\n");
            sb.append("thermal 0 is cool, 1..2 warm, 3+ is the device throttling itself - a rising\n");
            sb.append("number across the session means the frame rate is falling for thermal reasons,\n");
            sb.append("not because of a setting. 'gc N runs / M ms' growing quickly means the heap is\n");
            sb.append("too small or something is allocating hard. 'heap' near 'max' the whole time is\n");
            sb.append("the same story. A high cpu figure with a low frame rate means the game is busy\n");
            sb.append("off the render thread - world generation, or a mod on the main thread.\n");

            String text = sb.toString();
            writeBoth(text);
            Log.i(TAG, "Report written to " + (sReport == null ? "?" : sReport.getAbsolutePath()));
        } catch (Throwable t) {
            Log.w(TAG, "could not write the report", t);
        }
    }

    private static void writeBoth(String text) {
        for (File f : new File[]{sReport, sMirror}) {
            if (f == null) continue;
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(text.getBytes(StandardCharsets.UTF_8));
            } catch (Throwable ignored) { }
        }
    }

    private static String formatSample(Sample s, SimpleDateFormat hms) {
        StringBuilder core = new StringBuilder();
        if (s.coreFreqKhz.length > 0) {
            int max = 0, min = Integer.MAX_VALUE;
            for (int f : s.coreFreqKhz) {
                if (f <= 0) continue;
                max = Math.max(max, f);
                min = Math.min(min, f);
            }
            core.append(min / 1000).append('-').append(max / 1000);
        } else {
            core.append("n/a");
        }
        return String.format(Locale.US, "  %-8s  %-5s  %-5s  %-6d  %-3d  %d/%d  %-8s  %s",
                hms.format(new Date(s.wallMs)),
                s.fps < 0 ? "-" : String.format(Locale.US, "%.0f", s.fps),
                s.cpuPct < 0 ? "-" : String.format(Locale.US, "%.0f%%", s.cpuPct),
                s.heapUsedMb, s.threads, s.gcRuns, s.gcMs,
                Double.isNaN(s.thermalMaxC) ? "-" : String.format(Locale.US, "%.1f", s.thermalMaxC),
                core);
    }

    // ---- analysis ---------------------------------------------------------------------

    private static final class Analysis {
        double fpsAvg = -1, fpsMin = -1, fpsMax = -1;
        double cpuAvg = -1, cpuMax = -1;
        double tempAvg = Double.NaN, tempMax = Double.NaN;
        String tempName = "?";
        double battAvg = Double.NaN, battMax = Double.NaN;
        long gcRuns, gcMs;
        double heapAvg = -1;
        long heapMax;
        String coreSummary = "not readable on this device";
        String throttleNote = "none seen";
        String frameMarker = "";
        final List<String> findings = new ArrayList<>();
        final List<String> recommendations = new ArrayList<>();
    }

    private static Analysis analyse(List<Sample> samples, Map<String, Integer> counts) {
        Analysis a = new Analysis();
        if (samples.isEmpty()) return a;

        double fpsSum = 0; int fpsN = 0;
        double cpuSum = 0; int cpuN = 0;
        double tSum = 0; int tN = 0;
        double bSum = 0; int bN = 0;
        double heapSum = 0;
        double maxFps = 0, minFps = Double.MAX_VALUE;
        int coresAtMax = 0, coresAtMaxN = 0;
        double firstTemp = Double.NaN, lastTemp = Double.NaN;
        int throttleSamples = 0;

        for (Sample s : samples) {
            if (s.fps >= 0) { fpsSum += s.fps; fpsN++; maxFps = Math.max(maxFps, s.fps); minFps = Math.min(minFps, s.fps); }
            if (s.cpuPct >= 0) { cpuSum += s.cpuPct; cpuN++; a.cpuMax = Math.max(a.cpuMax, s.cpuPct); }
            if (!Double.isNaN(s.thermalMaxC)) {
                tSum += s.thermalMaxC; tN++;
                a.tempMax = Double.isNaN(a.tempMax) ? s.thermalMaxC : Math.max(a.tempMax, s.thermalMaxC);
                if (Double.isNaN(firstTemp)) firstTemp = s.thermalMaxC;
                lastTemp = s.thermalMaxC;
                a.tempName = s.thermalName;
            }
            if (!Double.isNaN(s.batteryC)) {
                bSum += s.batteryC; bN++;
                a.battMax = Double.isNaN(a.battMax) ? s.batteryC : Math.max(a.battMax, s.batteryC);
            }
            if (s.gcRuns > 0) { a.gcRuns = s.gcRuns; a.gcMs = s.gcMs; }
            heapSum += s.heapUsedMb;
            a.heapMax = Math.max(a.heapMax, s.heapMaxMb);

            // Throttling: big cores pinned well below their own maximum while the process
            // is busy. That is the signature of a governor pulling the clocks down.
            if (s.coreFreqKhz.length > 0 && s.coreMaxKhz.length == s.coreFreqKhz.length) {
                int maxCur = 0, maxCap = 0;
                for (int i = 0; i < s.coreFreqKhz.length; i++) {
                    if (s.coreFreqKhz[i] > maxCur) maxCur = s.coreFreqKhz[i];
                    if (s.coreMaxKhz[i] > maxCap) maxCap = s.coreMaxKhz[i];
                }
                if (maxCap > 0) {
                    coresAtMaxN++;
                    if (maxCur >= maxCap * 0.95) coresAtMax++;
                    if (s.cpuPct > 40 && maxCur < maxCap * 0.6) throttleSamples++;
                }
            }
            if (s.frameMarker != null && !s.frameMarker.isEmpty()) a.frameMarker = s.frameMarker;
        }

        a.fpsAvg = fpsN > 0 ? fpsSum / fpsN : -1;
        a.fpsMin = fpsN > 0 ? minFps : -1;
        a.fpsMax = fpsN > 0 ? maxFps : -1;
        a.cpuAvg = cpuN > 0 ? cpuSum / cpuN : -1;
        a.tempAvg = tN > 0 ? tSum / tN : Double.NaN;
        a.battAvg = bN > 0 ? bSum / bN : Double.NaN;
        a.heapAvg = heapSum / samples.size();
        if (coresAtMaxN > 0) {
            a.coreSummary = String.format(Locale.US, "peak clocks hit in %d of %d samples",
                    coresAtMax, coresAtMaxN);
        }

        // -- findings --
        if (throttleSamples > samples.size() * 0.15) {
            a.throttleNote = String.format(Locale.US, "clocks pulled down in %d of %d samples",
                    throttleSamples, samples.size());
            a.findings.add("Thermal throttling: the CPU cores were held well below their maximum "
                    + "while the game was busy (" + a.throttleNote + "). This is the device protecting "
                    + "itself, and it is the most common reason a session starts smooth and turns laggy.");
            a.recommendations.add("Adaptive frame cap + resolution scaling: drop the render resolution "
                    + "(or enable FSR1 upscaling in FearV1) as the thermal status climbs, so the frame rate "
                    + "stays steady instead of falling off a cliff.");
            a.recommendations.add("A \"cool mode\" preset that lowers render distance and disables heavy "
                    + "shader effects automatically once the battery passes ~40C.");
        } else if (throttleSamples > 0) {
            a.throttleNote = String.format(Locale.US, "brief (in %d samples)", throttleSamples);
        }

        if (!Double.isNaN(a.tempMax) && a.tempMax >= 70) {
            a.findings.add(String.format(Locale.US, "Heat: a thermal zone reached %.1fC (%s).",
                    a.tempMax, a.tempName));
        }
        if (!Double.isNaN(a.battMax) && a.battMax >= 42) {
            a.findings.add(String.format(Locale.US, "Battery temperature reached %.1fC, which is where "
                    + "most phones start cutting performance.", a.battMax));
            a.recommendations.add("A charge-aware profile: many devices throttle hard while charging. "
                    + "Warn the user, and cap the frame rate automatically when plugged in and hot.");
        }
        if (!Double.isNaN(firstTemp) && !Double.isNaN(lastTemp) && lastTemp - firstTemp >= 8) {
            a.findings.add(String.format(Locale.US, "The device heated up %.1fC during the session "
                    + "(%.1fC -> %.1fC) - expect the frame rate to fall as it does.",
                    lastTemp - firstTemp, firstTemp, lastTemp));
        }

        if (fpsN > 0 && a.fpsMin >= 0 && a.fpsAvg > 0 && a.fpsMin < a.fpsAvg * 0.5) {
            a.findings.add(String.format(Locale.US, "Frame rate is unstable: it dips to %.0f from an "
                    + "average of %.0f. That is stutter, and it is what 'laggy' feels like even when the "
                    + "average looks fine.", a.fpsMin, a.fpsAvg));
            a.recommendations.add("Frame-time graph in the in-game overlay, so a stutter can be pinned to "
                    + "the moment it happens (a chunk load, a shader compile, a GC pause).");
        }

        if (a.gcRuns > 0 && samples.size() > 1 && a.gcMs > 0) {
            double gcMsPerSample = (double) a.gcMs / samples.size();
            if (gcMsPerSample > 60) {
                a.findings.add(String.format(Locale.US, "Garbage collection is heavy: %d runs, %d ms "
                        + "total (about %.0f ms per sample). GC pauses are felt as stutter.",
                        a.gcRuns, a.gcMs, gcMsPerSample));
                a.recommendations.add("Heap/GC tuning: pick a heap size and a collector for the device "
                        + "instead of the default, and surface the GC pause time in the overlay.");
            }
        }
        if (a.heapMax > 0 && a.heapAvg > a.heapMax * 0.85) {
            a.findings.add(String.format(Locale.US, "Heap is nearly full the whole time (%.0f MB of %d MB).",
                    a.heapAvg, a.heapMax));
            a.recommendations.add("A memory-pressure warning before launch, and an automatic bump of the "
                    + "allocated RAM when the chosen heap is clearly too small for the modpack.");
        }

        // A single thread eating most of the CPU means the game is bottlenecked off the
        // render thread - world generation, or a mod doing work on the main thread.
        for (Sample s : samples) {
            if (s.cpuPct > 60 && !s.topThreads.isEmpty()) {
                String top = s.topThreads.get(0);
                try {
                    double pct = Double.parseDouble(top.substring(top.lastIndexOf(' ') + 1).replace("%", ""));
                    if (pct > s.cpuPct * 0.7 && pct > 50) {
                        a.findings.add("One thread is doing most of the work: " + top
                                + " while the whole process sat at " + String.format(Locale.US, "%.0f%%", s.cpuPct)
                                + ". The game is bottlenecked off the render thread.");
                        a.recommendations.add("Thread-level pinning and priority tuning for the render thread "
                                + "specifically, so world generation and mods cannot starve it.");
                        break;
                    }
                } catch (Throwable ignored) { }
            }
        }

        if (fpsN == 0) {
            a.findings.add("The frame rate could not be measured: no per-frame marker was found in the "
                    + "game log for this renderer.");
            a.recommendations.add("A native frame counter shared with the launcher, so the frame rate is "
                    + "known for every renderer and does not depend on what the game happens to log.");
        }

        for (String key : counts.keySet()) {
            if (key.contains("heap") || key.contains("mixin") || key.contains("throttl")) {
                a.recommendations.add("Investigate the log matches for \"" + key
                        + "\" - see the LIKELY CAUSES section below.");
            }
        }

        return a;
    }
}
