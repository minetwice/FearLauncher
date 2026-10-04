package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.preference.PreferenceManager;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Catches the launch that never came back.
 *
 * The game runs in this process, so a hard crash - a SIGSEGV inside the GL driver,
 * say - kills the process outright and nothing downstream ever runs. So instead of
 * trying to react to the crash, a flag is set when the game starts and cleared when
 * the game activity finishes normally. A flag still set on the next launcher start
 * means the previous run died, and whatever the game left behind is read back here.
 */
public final class FearCrashGuard {

    private static final String PREF_PENDING = "fear_launch_pending";
    private static final String PREF_STARTED_AT = "fear_launch_started_at";

    /** How long a run must have lasted before a leftover flag is believed. */
    private static final long MIN_RUN_MS = 20_000L;

    private FearCrashGuard() {}

    public static void markLaunching(Context context) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(PREF_PENDING, true)
                .putLong(PREF_STARTED_AT, System.currentTimeMillis())
                .apply();
    }

    public static void markCleanExit(Context context) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(PREF_PENDING, false)
                .apply();
    }

    /** One line of the crash, as the game wrote it. */
    public static final class Report {
        public String title = "The game stopped unexpectedly";
        public String detail = "";
        /** The mod file blamed, if one could be worked out. Never null when set. */
        public File culprit;
        /**
         * True when the frame names the vendor GL driver. That crash is inside Mali's own
         * library, so nothing the launcher passes it can avoid it - the only way past is
         * to run on a renderer that does not hand the draws to that driver.
         */
        public boolean vendorDriverCrash;
    }

    /** Forgets the pending launch. Call once the board has been dealt with. */
    public static void clear(Context context) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(PREF_PENDING, false)
                .apply();
    }

    /**
     * Returns a report if the last launch died, otherwise null. Reading does not clear
     * the flag - the board reads it too, and only clears once it is dismissed.
     */
    public static Report detect(Context context) {
        android.content.SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context);
        if (!prefs.getBoolean(PREF_PENDING, false)) return null;

        long startedAt = prefs.getLong(PREF_STARTED_AT, 0L);
        if (startedAt == 0L || System.currentTimeMillis() - startedAt < MIN_RUN_MS) {
            clear(context);
            return null;
        }

        Report report = new Report();
        File crashReport = newest(new File(Tools.DIR_HOME_CRASH));
        File instanceReport = null;
        try {
            Instance instance = Instances.loadSelectedInstance();
            if (instance != null) {
                instanceReport = newest(new File(instance.getGameDirectory(), "crash-reports"));
            }
        } catch (Exception ignored) { }
        if (instanceReport != null && (crashReport == null
                || instanceReport.lastModified() > crashReport.lastModified())) {
            crashReport = instanceReport;
        }

        if (crashReport != null && crashReport.lastModified() > startedAt) {
            readCrashReport(crashReport, report);
        } else {
            readHsErr(newestHsErr(), report);
        }
        return report;
    }

    private static void readCrashReport(File file, Report report) {
        report.title = "The game crashed";
        StringBuilder mods = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            String line;
            boolean inSuspected = false;
            while ((line = r.readLine()) != null) {
                String t = line.trim();
                if (t.startsWith("Description:")) {
                    report.detail = t.substring("Description:".length()).trim();
                } else if (t.startsWith("Suspected Mods:") || t.startsWith("Suspected mods:")) {
                    inSuspected = true;
                    String rest = t.substring(t.indexOf(':') + 1).trim();
                    if (!rest.isEmpty()) mods.append(rest).append(' ');
                } else if (inSuspected) {
                    if (t.isEmpty()) break;
                    mods.append(t).append(' ');
                }
            }
        } catch (Exception ignored) { }
        String suspected = mods.toString().trim();
        if (!suspected.isEmpty()) {
            report.detail = (report.detail.isEmpty() ? "" : report.detail + "  \u2014  ")
                    + "Suspected: " + suspected;
        }
        report.culprit = findModFile(suspected);
    }

    private static void readHsErr(File file, Report report) {
        if (file == null) return;
        report.title = "The graphics driver crashed";
        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            String line;
            int seen = 0;
            while ((line = r.readLine()) != null && seen < 40) {
                seen++;
                String t = line.trim();
                if (t.startsWith("#  SIG") || t.startsWith("# SIG")) {
                    report.detail = t.replaceFirst("^#\\s*", "");
                } else if (t.contains("Problematic frame:")) {
                    String next = r.readLine();
                    if (next != null) {
                        String frame = next.replaceFirst("^#\\s*", "").trim();
                        report.detail = (report.detail.isEmpty() ? "" : report.detail + "  \u2014  ")
                                + frame;
                        String lower = frame.toLowerCase();
                        report.vendorDriverCrash = lower.contains("libgles_mali")
                                || lower.contains("libgles_adreno")
                                || lower.contains("libgles_qualcomm")
                                || lower.contains("vulkan.mali");
                    }
                    break;
                }
            }
        } catch (Exception ignored) { }
    }

    private static File newestHsErr() {
        File dir = new File(Tools.DIR_GAME_HOME);
        File best = null;
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (!f.isFile()) continue;
            String n = f.getName();
            if (n.startsWith("hs_err_pid") && n.endsWith(".log")) {
                if (best == null || f.lastModified() > best.lastModified()) best = f;
            }
        }
        return best;
    }

    private static File newest(File dir) {
        if (dir == null || !dir.isDirectory()) return null;
        File best = null;
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (!f.isFile() || !f.getName().startsWith("crash-")) continue;
            if (best == null || f.lastModified() > best.lastModified()) best = f;
        }
        return best;
    }

    /** Best-effort: match a suspected mod id against the instance's mods folder. */
    private static File findModFile(String suspected) {
        if (suspected == null || suspected.isEmpty()) return null;
        List<File> mods = new ArrayList<>();
        try {
            Instance instance = Instances.loadSelectedInstance();
            if (instance != null) {
                File dir = new File(instance.getGameDirectory(), "mods");
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) if (f.isFile()) mods.add(f);
                }
            }
        } catch (Exception ignored) { }
        String lower = suspected.toLowerCase();
        for (File f : mods) {
            String name = f.getName().toLowerCase();
            String stem = name.endsWith(".jar") ? name.substring(0, name.length() - 4) : name;
            // the crash report names mods as "id version", so match on the id part
            for (String token : lower.split("[\\s,]+")) {
                if (token.length() < 3) continue;
                if (stem.contains(token)) return f;
            }
        }
        return null;
    }
}
