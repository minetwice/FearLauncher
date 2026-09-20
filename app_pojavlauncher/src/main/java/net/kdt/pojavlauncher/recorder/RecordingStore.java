package net.kdt.pojavlauncher.recorder;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lists the recordings made by RecorderService:
 * - API 29+: MediaStore query over Movies/FearLauncher
 * - older devices: the app-private Recordings folder
 */
public final class RecordingStore {

    public static class Entry {
        public String name;
        public Uri uri;          // MediaStore uri (API 29+), otherwise null
        public String path;      // legacy file path, otherwise null
        public long durationMs;
        public long sizeBytes;
        public long dateTaken;
    }

    private RecordingStore() {
    }

    public static List<Entry> list(Context context) {
        List<Entry> out = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                Uri collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
                try (Cursor c = context.getContentResolver().query(
                        collection,
                        new String[]{MediaStore.Video.Media._ID,
                                MediaStore.Video.Media.DISPLAY_NAME,
                                MediaStore.Video.Media.DURATION,
                                MediaStore.Video.Media.SIZE,
                                MediaStore.Video.Media.DATE_ADDED},
                        MediaStore.Video.Media.RELATIVE_PATH + " LIKE ?",
                        new String[]{"Movies/FearLauncher%"}, null)) {
                    if (c != null) {
                        while (c.moveToNext()) {
                            Entry e = new Entry();
                            long id = c.getLong(0);
                            e.name = c.getString(1);
                            e.durationMs = c.getLong(2);
                            e.sizeBytes = c.getLong(3);
                            e.dateTaken = c.getLong(4) * 1000L;
                            e.uri = ContentUris.withAppendedId(collection, id);
                            out.add(e);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        try {
            File dir = new File(context.getExternalFilesDir(null), "Recordings");
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (!f.getName().endsWith(".mp4")) continue;
                    Entry e = new Entry();
                    e.name = f.getName();
                    e.path = f.getAbsolutePath();
                    e.sizeBytes = f.length();
                    e.dateTaken = f.lastModified();
                    out.add(e);
                }
            }
        } catch (Exception ignored) {
        }
        Collections.sort(out, (a, b) -> Long.compare(b.dateTaken, a.dateTaken));
        return out;
    }

    /** Points a MediaExtractor at whatever storage the entry lives in. */
    public static void setDataSource(Context context, android.media.MediaExtractor extractor, Entry e)
            throws Exception {
        if (e.uri != null) {
            extractor.setDataSource(context, e.uri, null);
        } else if (e.path != null) {
            extractor.setDataSource(e.path);
        } else {
            throw new Exception("Recording has no data source");
        }
    }
}
