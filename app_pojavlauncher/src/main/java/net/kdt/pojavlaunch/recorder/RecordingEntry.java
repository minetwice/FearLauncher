package net.kdt.pojavlaunch.recorder;

import android.content.Context;
import android.media.MediaMetadataRetriever;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** One finished recording on disk, with the bits the Dashboard needs to list it. */
public final class RecordingEntry {

    public final File file;
    public final long sizeBytes;
    public final long durationMs;
    public final long lastModified;

    private RecordingEntry(File file, long sizeBytes, long durationMs, long lastModified) {
        this.file = file;
        this.sizeBytes = sizeBytes;
        this.durationMs = durationMs;
        this.lastModified = lastModified;
    }

    public String displayName() {
        String name = file.getName();
        return name.toLowerCase().endsWith(".mp4") ? name.substring(0, name.length() - 4) : name;
    }

    public String readableSize() {
        double mb = sizeBytes / (1024.0 * 1024.0);
        return mb >= 1024 ? String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0)
                          : String.format(java.util.Locale.US, "%.1f MB", mb);
    }

    public String readableDuration() {
        long totalSeconds = durationMs / 1000L;
        long minutes = TimeUnit.SECONDS.toMinutes(totalSeconds);
        long seconds = totalSeconds - TimeUnit.MINUTES.toSeconds(minutes);
        return String.format(java.util.Locale.US, "%d:%02d", minutes, seconds);
    }

    /** All recordings in the recorder's output folder, newest first. */
    public static List<RecordingEntry> load(Context context) {
        List<RecordingEntry> entries = new ArrayList<>();
        File dir = new File(context.getExternalFilesDir(null), "recordings");
        File[] files = dir.listFiles();
        if (files == null) return entries;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        for (File file : files) {
            if (!file.isFile() || !file.getName().toLowerCase().endsWith(".mp4")) continue;
            entries.add(new RecordingEntry(file, file.length(), readDuration(file), file.lastModified()));
        }
        return entries;
    }

    /**
     * Reads the clip length. A file that cannot be read reports 0 rather than throwing, so
     * a half-written recording cannot take the whole list down with it.
     */
    private static long readDuration(File file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            String value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return value == null ? 0L : Long.parseLong(value);
        } catch (Exception e) {
            return 0L;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) { }
        }
    }
}
