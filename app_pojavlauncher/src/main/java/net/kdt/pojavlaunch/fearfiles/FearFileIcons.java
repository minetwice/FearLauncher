package net.kdt.pojavlaunch.fearfiles;

import android.graphics.drawable.Drawable;
import android.webkit.MimeTypeMap;

import com.fearlauncher.fear.R;

import java.io.File;
import java.util.Locale;

/** Picks the glyph for a file, and reads the app icon for anything installable. */
public final class FearFileIcons {

    private FearFileIcons() {}

    public static int glyphFor(File file) {
        if (file.isDirectory()) return R.drawable.ic_file_folder;
        String name = file.getName().toLowerCase(Locale.ROOT);
        String ext = extensionOf(name);
        switch (ext) {
            case "jar": case "litemod":
                return R.drawable.ic_file_jar;
            case "zip": case "mrpack": case "mcpack": case "mcaddon":
                return R.drawable.ic_file_zip;
            case "png": case "jpg": case "jpeg": case "gif": case "webp": case "bmp":
                return R.drawable.ic_file_image;
            case "txt": case "log": case "json": case "cfg": case "toml": case "yml":
            case "yaml": case "properties": case "md": case "lang":
                return R.drawable.ic_file_text;
            case "ogg": case "mp3": case "wav": case "m4a": case "flac":
                return R.drawable.ic_file_audio;
            case "mp4": case "mkv": case "webm": case "avi": case "mov":
                return R.drawable.ic_file_video;
            case "apk": case "apks": case "xapk":
                return R.drawable.ic_file_apk;
            case "fsh": case "vsh": case "glsl": case "frag": case "vert": case "spv":
                return R.drawable.ic_file_shader;
            default:
                // resource packs and shader packs are usually just zips with another suffix
                if (name.endsWith(".zip.disabled")) return R.drawable.ic_file_zip;
                return R.drawable.ic_file_unknown;
        }
    }

    /** A real app icon for an APK, or null when there is none to show. */
    public static Drawable appIconFor(android.content.Context context, File file) {
        if (context == null) return null;
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".apk")) return null;
        try {
            android.content.pm.PackageManager pm = context.getPackageManager();
            android.content.pm.PackageInfo info =
                    pm.getPackageArchiveInfo(file.getAbsolutePath(), 0);
            if (info == null || info.applicationInfo == null) return null;
            // The icon lives at a path inside the archive, so it has to be pointed there
            // before the manager can decode it.
            info.applicationInfo.sourceDir = file.getAbsolutePath();
            info.applicationInfo.publicSourceDir = file.getAbsolutePath();
            return pm.getApplicationIcon(info.applicationInfo);
        } catch (Throwable t) {
            return null;
        }
    }

    /** True when this is a zip-family file we can look inside. */
    public static boolean isArchive(File file) {
        String ext = extensionOf(file.getName().toLowerCase(Locale.ROOT));
        return ext.equals("zip") || ext.equals("jar") || ext.equals("mrpack")
                || ext.equals("mcpack") || ext.equals("mcaddon");
    }

    public static String mimeFor(File file) {
        String ext = extensionOf(file.getName().toLowerCase(Locale.ROOT));
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return mime != null ? mime : "*/*";
    }

    private static String extensionOf(String lowerName) {
        int dot = lowerName.lastIndexOf('.');
        return dot >= 0 && dot < lowerName.length() - 1 ? lowerName.substring(dot + 1) : "";
    }
}
