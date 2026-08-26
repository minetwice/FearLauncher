package net.kdt.pojavlaunch.quasar.transpile;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Rewrites Iris/OptiFine shaderpack files on disk so Mali/Adreno GLES drivers
 * never see desktop-only keywords/extensions (esp. noperspective).
 *
 * Native glShaderSource hooks can be bypassed when LTW owns the GL entry points;
 * this path is independent of that and is guaranteed to run before Iris compiles.
 */
public final class ShaderPackSanitizer {
    private static final String TAG = "Quasar-PackSanitize";

    private ShaderPackSanitizer() {}

    /**
     * Sanitize every .zip shaderpack under the given shaderpacks directory.
     * Safe to call multiple times — already-patched packs are left alone when
     * no noperspective / bad extensions remain.
     */
    public static void sanitizeDirectory(File shaderpacksDir) {
        if (shaderpacksDir == null || !shaderpacksDir.isDirectory()) {
            return;
        }
        File[] files = shaderpacksDir.listFiles();
        if (files == null) return;

        int touched = 0;
        for (File f : files) {
            if (f == null || !f.isFile()) continue;
            String name = f.getName().toLowerCase(Locale.ROOT);
            if (!name.endsWith(".zip")) continue;
            try {
                if (sanitizeZip(f)) {
                    touched++;
                    Log.i(TAG, "Sanitized shaderpack: " + f.getName());
                }
            } catch (Throwable t) {
                Log.w(TAG, "Failed to sanitize " + f.getName() + ": " + t.getMessage());
            }
        }
        if (touched > 0) {
            Log.i(TAG, "Sanitized " + touched + " shaderpack(s) for Mali/Adreno");
        }
    }

    /** @return true if the zip was modified */
    public static boolean sanitizeZip(File zipFile) throws IOException {
        File tmp = new File(zipFile.getParentFile(), zipFile.getName() + ".quasar-tmp");
        boolean modified = false;

        try (ZipFile zin = new ZipFile(zipFile);
             ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(tmp))) {

            Enumeration<? extends ZipEntry> entries = zin.entries();
            while (entries.hasMoreElements()) {
                ZipEntry in = entries.nextElement();
                String entryName = in.getName();
                ZipEntry out = new ZipEntry(entryName);
                zout.putNextEntry(out);

                if (!in.isDirectory() && isShaderSource(entryName)) {
                    byte[] raw = readAll(zin.getInputStream(in));
                    String src = new String(raw, StandardCharsets.UTF_8);
                    String fixed = ShaderPreprocessor.preprocessForMobile(src, entryName);
                    if (!fixed.equals(src)) {
                        modified = true;
                        zout.write(fixed.getBytes(StandardCharsets.UTF_8));
                    } else {
                        zout.write(raw);
                    }
                } else if (!in.isDirectory()) {
                    copy(zin.getInputStream(in), zout);
                }
                zout.closeEntry();
            }
        }

        if (modified) {
            if (!zipFile.delete() || !tmp.renameTo(zipFile)) {
                try (FileInputStream in = new FileInputStream(tmp);
                     FileOutputStream out = new FileOutputStream(zipFile)) {
                    copy(in, out);
                }
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
            return true;
        }
        //noinspection ResultOfMethodCallIgnored
        tmp.delete();
        return false;
    }

    private static boolean isShaderSource(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".vsh") || n.endsWith(".fsh") || n.endsWith(".gsh")
                || n.endsWith(".csh") || n.endsWith(".glsl") || n.endsWith(".vert")
                || n.endsWith(".frag") || n.endsWith(".comp");
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        copy(in, bos);
        return bos.toByteArray();
    }

    private static void copy(InputStream in, java.io.OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) {
            out.write(buf, 0, n);
        }
    }
}
