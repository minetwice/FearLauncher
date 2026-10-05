package net.kdt.pojavlaunch.recorder;

import android.content.Context;
import android.util.Log;

import net.kdt.pojavlaunch.plugins.LibraryPlugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Re-encodes a recording with FFmpeg - the "render" step. The capture is whatever the
 * screen actually produced; this pass decides the output resolution, frame rate and
 * bitrate, can raise the frame rate by generating in-between frames, and can keep only a
 * trimmed window of the source.
 *
 * FFmpeg comes from an FFmpeg plugin app, the same binary the launcher already points the
 * game at through FEAR_FFMPEG_PATH. It is shipped as libffmpeg.so so the platform extracts
 * it into the plugin's native library directory, which is the one place on Android a
 * binary may be executed from. Both the launcher's own plugin and the upstream
 * PojavLauncher one are accepted.
 */
public final class FfmpegExporter {

    private static final String TAG = "FfmpegExporter";

    /** What the user asked for, resolved to concrete numbers. */
    public static final class Options {
        public final int height;        // 0 = keep source
        public final int fps;           // 0 = keep source
        public final int bitrate;       // bits per second
        public final boolean interpolate;
        /** Trim window in milliseconds; 0 means "from the start" / "to the end". */
        public final long trimStartMs;
        public final long trimEndMs;

        public Options(int height, int fps, int bitrate, boolean interpolate) {
            this(height, fps, bitrate, interpolate, 0L, 0L);
        }

        public Options(int height, int fps, int bitrate, boolean interpolate,
                       long trimStartMs, long trimEndMs) {
            this.height = height;
            this.fps = fps;
            this.bitrate = bitrate;
            this.interpolate = interpolate;
            this.trimStartMs = trimStartMs;
            this.trimEndMs = trimEndMs;
        }

        /** Same settings, restricted to a trimmed window of the source. */
        public Options withTrim(long startMs, long endMs) {
            return new Options(height, fps, bitrate, interpolate, startMs, endMs);
        }

        public boolean isTrimmed() {
            return trimStartMs > 0 || trimEndMs > 0;
        }

        public boolean isCopy() {
            return height == 0 && fps == 0 && !interpolate;
        }
    }

    public interface Listener {
        void onProgress(int percent);
        void onFinished(boolean success, File output, String message);
    }

    private FfmpegExporter() {}

    /** The plugin's ffmpeg, or null when no ffmpeg plugin is installed. */
    public static File locate(Context context) {
        for (String id : new String[]{
                LibraryPlugin.ID_FFMPEG_PLUGIN,
                LibraryPlugin.ID_FFMPEG_PLUGIN_UPSTREAM}) {
            LibraryPlugin plugin = LibraryPlugin.discoverPlugin(context, id);
            if (plugin == null) continue;
            File binary = new File(plugin.resolveAbsolutePath("libffmpeg.so"));
            if (binary.isFile()) return binary;
        }
        return null;
    }

    /**
     * Runs the render on a background thread. The output is written next to the input as
     * "<name>_export.mp4" so the original is never touched.
     */
    public static void export(Context context, File input, Options options, File outputDir,
                              Listener listener) {
        new Thread(() -> run(context, input, options, outputDir, listener), "fear-ffmpeg").start();
    }

    private static void run(Context context, File input, Options options, File outputDir,
                            Listener listener) {
        File ffmpeg = locate(context);
        if (ffmpeg == null) {
            listener.onFinished(false, null,
                    "FFmpeg plugin not found. Install the FFmpeg app to export.");
            return;
        }

        //noinspection ResultOfMethodCallIgnored
        outputDir.mkdirs();
        String base = input.getName().replaceAll("\\.mp4$", "");
        File output = new File(outputDir, base + "_export.mp4");

        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpeg.getAbsolutePath());
        cmd.add("-y");
        cmd.add("-hide_banner");
        // A trimmed render decodes only the wanted window, which is also faster than
        // decoding the whole capture and throwing the rest away.
        if (options.trimStartMs > 0) {
            cmd.add("-ss");
            cmd.add(String.format(Locale.US, "%.3f", options.trimStartMs / 1000.0));
        }
        cmd.add("-i");
        cmd.add(input.getAbsolutePath());
        if (options.trimEndMs > 0) {
            cmd.add("-to");
            cmd.add(String.format(Locale.US, "%.3f",
                    Math.max(0, options.trimEndMs - options.trimStartMs) / 1000.0));
        }

        String filters = buildFilters(options);
        if (!filters.isEmpty()) {
            cmd.add("-vf");
            cmd.add(filters);
        }

        cmd.add("-c:v");
        cmd.add("libx264");
        cmd.add("-preset");
        cmd.add("veryfast");
        if (options.bitrate > 0) {
            cmd.add("-b:v");
            cmd.add(String.valueOf(options.bitrate));
        }
        cmd.add("-pix_fmt");
        cmd.add("yuv420p");
        cmd.add("-c:a");
        cmd.add("aac");
        cmd.add("-b:a");
        cmd.add("128k");
        cmd.add("-movflags");
        cmd.add("+faststart");
        cmd.add(output.getAbsolutePath());

        long durationMs = RecordingEntry.load(context).stream()
                .filter(e -> e.file.equals(input)).findFirst()
                .map(e -> e.durationMs).orElse(0L);

        try {
            ProcessBuilder builder = new ProcessBuilder(cmd).redirectErrorStream(true);
            Process process = builder.start();
            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int percent = parseProgress(line, durationMs);
                    if (percent >= 0) listener.onProgress(percent);
                }
            }
            int exit = process.waitFor();
            boolean ok = exit == 0 && output.isFile() && output.length() > 0;
            listener.onFinished(ok, ok ? output : null,
                    ok ? null : "FFmpeg exited with code " + exit);
        } catch (Exception e) {
            Log.e(TAG, "Export failed", e);
            listener.onFinished(false, null, e.getMessage());
        }
    }

    /**
     * The filter chain. Frame interpolation is the piece that makes a low frame rate clip
     * play smoothly: minterpolate synthesises the frames in between by estimating motion,
     * which is why it is slower and can show artefacts on fast motion.
     */
    private static String buildFilters(Options options) {
        StringBuilder sb = new StringBuilder();
        if (options.height > 0) {
            // -2 keeps the width even and preserves the aspect ratio.
            sb.append("scale=-2:").append(options.height);
        }
        if (options.fps > 0) {
            if (sb.length() > 0) sb.append(',');
            if (options.interpolate) {
                sb.append("minterpolate=fps=").append(options.fps)
                        .append(":mi_mode=mci:mc_mode=aobmc:me_mode=bidir:vsbmc=1");
            } else {
                sb.append("fps=").append(options.fps);
            }
        }
        return sb.toString();
    }

    /** Pulls the "time=HH:MM:SS.cc" field out of an ffmpeg progress line. */
    private static int parseProgress(String line, long durationMs) {
        int at = line.indexOf("time=");
        if (at < 0 || durationMs <= 0) return -1;
        String time = line.substring(at + 5).trim();
        int space = time.indexOf(' ');
        if (space > 0) time = time.substring(0, space);
        String[] parts = time.split(":");
        if (parts.length != 3) return -1;
        try {
            long seconds = (long) (Double.parseDouble(parts[0]) * 3600)
                    + (long) (Double.parseDouble(parts[1]) * 60)
                    + (long) Double.parseDouble(parts[2]);
            long percent = (seconds * 1000L) * 100L / durationMs;
            return (int) Math.max(0, Math.min(99, percent));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** A short label for the progress notification. */
    public static String describe(Options options) {
        StringBuilder sb = new StringBuilder();
        if (options.height > 0) sb.append(options.height).append("p");
        else sb.append("source");
        if (options.fps > 0) {
            sb.append(" · ").append(options.fps).append("fps");
            if (options.interpolate) sb.append(" (smooth)");
        }
        return String.format(Locale.US, "%s", sb);
    }
}
