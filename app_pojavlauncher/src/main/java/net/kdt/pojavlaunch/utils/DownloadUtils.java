package net.kdt.pojavlaunch.utils;

import android.util.Log;

import androidx.annotation.Nullable;

import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.concurrent.Callable;

import net.kdt.pojavlaunch.*;
import org.apache.commons.io.*;

@SuppressWarnings("IOStreamConstructor")
public class DownloadUtils {
    public static final String USER_AGENT = Tools.APP_NAME;

    /** How long we wait for the TCP connection to be established. */
    public static final int CONNECT_TIMEOUT_MS = 8000;
    /** How long a single socket read may stall before we give up on it. */
    public static final int READ_TIMEOUT_MS = 30000;
    /** How many times a whole download is attempted before it is reported as failed. */
    public static final int MAX_DOWNLOAD_ATTEMPTS = 4;
    /** Base delay between two attempts; grows linearly with the attempt number. */
    private static final long RETRY_BACKOFF_MS = 700L;
    /** HTTP 416: the server rejected the resume range because the local file is already complete. */
    private static final int HTTP_RANGE_NOT_SATISFIABLE = 416;

    public static void download(String url, OutputStream os) throws IOException {
        download(new URL(url), os);
    }

    public static void download(URL url, OutputStream os) throws IOException {
        InputStream is = null;
        try {
            // System.out.println("Connecting: " + url.toString());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Connection", "keep-alive");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setDoInput(true);
            conn.connect();
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("Server returned HTTP " + conn.getResponseCode()
                        + ": " + conn.getResponseMessage());
            }
            is = conn.getInputStream();
            IOUtils.copy(is, os);
        } catch (IOException e) {
            throw new IOException("Unable to download from " + url, e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static String downloadString(String url) throws IOException {
        IOException lastError = null;
        for (int attempt = 1; attempt <= MAX_DOWNLOAD_ATTEMPTS; attempt++) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try {
                download(url, bos);
                return new String(bos.toByteArray(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                lastError = e;
                Log.w("DownloadUtils", "Download attempt " + attempt + "/" + MAX_DOWNLOAD_ATTEMPTS
                        + " failed for " + url, e);
                if (attempt < MAX_DOWNLOAD_ATTEMPTS) sleepQuietly(RETRY_BACKOFF_MS * attempt);
            } finally {
                try { bos.close(); } catch (IOException ignored) { }
            }
        }
        throw new IOException("Failed to download " + url + " after "
                + MAX_DOWNLOAD_ATTEMPTS + " attempts", lastError);
    }

    public static void downloadFile(String url, File out) throws IOException {
        FileUtils.ensureParentDirectory(out);
        IOException lastError = null;
        for (int attempt = 1; attempt <= MAX_DOWNLOAD_ATTEMPTS; attempt++) {
            try {
                downloadFileOnce(url, out);
                return;
            } catch (IOException e) {
                lastError = e;
                Log.w("DownloadUtils", "Download attempt " + attempt + "/" + MAX_DOWNLOAD_ATTEMPTS
                        + " failed for " + url, e);
                // Keep the partial file: the next attempt resumes it with a Range request instead
                // of re-downloading a large file from scratch.
                if (attempt < MAX_DOWNLOAD_ATTEMPTS) sleepQuietly(RETRY_BACKOFF_MS * attempt);
            }
        }
        throw new IOException("Failed to download " + url + " after "
                + MAX_DOWNLOAD_ATTEMPTS + " attempts", lastError);
    }

    /**
     * One attempt at a file download. When a partial file already exists it is resumed with an
     * open-ended Range request and appended to; when the server ignores the range (HTTP 200) the
     * file is rewritten from zero, and when it rejects it (HTTP 416) the stale partial is truncated
     * and a retry is signalled. The partial is never deleted, so a slow or flaky link keeps making
     * progress across attempts.
     */
    private static void downloadFileOnce(String url, File out) throws IOException {
        long alreadyDownloaded = out.exists() ? out.length() : 0L;
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Connection", "keep-alive");
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setDoInput(true);
        if (alreadyDownloaded > 0L) conn.setRequestProperty("Range", "bytes=" + alreadyDownloaded + "-");
        InputStream is = null;
        try {
            conn.connect();
            int responseCode = conn.getResponseCode();
            if (responseCode == HTTP_RANGE_NOT_SATISFIABLE) {
                truncateQuietly(out);
                throw new IOException("Server rejected the resume range; restarting " + url);
            }
            boolean resumed = responseCode == HttpURLConnection.HTTP_PARTIAL;
            if (responseCode != HttpURLConnection.HTTP_OK && !resumed) {
                throw new IOException("Server returned HTTP " + responseCode
                        + ": " + conn.getResponseMessage());
            }
            is = conn.getInputStream();
            try (FileOutputStream os = new FileOutputStream(out, resumed)) {
                IOUtils.copy(is, os);
            }
        } catch (IOException e) {
            throw new IOException("Unable to download from " + url, e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            conn.disconnect();
        }
    }

    public static void downloadFileMonitored(String urlInput, File outputFile, @Nullable byte[] buffer,
                                             Tools.DownloaderFeedback monitor) throws IOException {
        FileUtils.ensureParentDirectory(outputFile);
        IOException lastError = null;
        for (int attempt = 1; attempt <= MAX_DOWNLOAD_ATTEMPTS; attempt++) {
            try {
                downloadFileMonitoredOnce(urlInput, outputFile, buffer, monitor);
                return;
            } catch (IOException e) {
                lastError = e;
                Log.w("DownloadUtils", "Download attempt " + attempt + "/" + MAX_DOWNLOAD_ATTEMPTS
                        + " failed for " + urlInput, e);
                // Keep the partial file: the next attempt resumes it with a Range request instead
                // of re-downloading a large file from scratch.
                if (attempt < MAX_DOWNLOAD_ATTEMPTS) sleepQuietly(RETRY_BACKOFF_MS * attempt);
            }
        }
        throw new IOException("Failed to download " + urlInput + " after "
                + MAX_DOWNLOAD_ATTEMPTS + " attempts", lastError);
    }

    private static void downloadFileMonitoredOnce(String urlInput, File outputFile, @Nullable byte[] buffer,
                                                  Tools.DownloaderFeedback monitor) throws IOException {
        long alreadyDownloaded = outputFile.exists() ? outputFile.length() : 0L;
        HttpURLConnection conn = (HttpURLConnection) new URL(urlInput).openConnection();
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Connection", "keep-alive");
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        if (alreadyDownloaded > 0L) conn.setRequestProperty("Range", "bytes=" + alreadyDownloaded + "-");
        try {
            int responseCode = conn.getResponseCode();
            if (responseCode == HTTP_RANGE_NOT_SATISFIABLE) {
                truncateQuietly(outputFile);
                throw new IOException("Server rejected the resume range; restarting " + urlInput);
            }
            boolean resumed = responseCode == HttpURLConnection.HTTP_PARTIAL;
            if (responseCode != HttpURLConnection.HTTP_OK && !resumed) {
                throw new IOException("Server returned HTTP " + responseCode
                        + ": " + conn.getResponseMessage());
            }
            InputStream readStr = conn.getInputStream();
            try (FileOutputStream fos = new FileOutputStream(outputFile, resumed)) {
                int current;
                long overall = resumed ? alreadyDownloaded : 0L;
                int remaining = conn.getContentLength();
                long total = remaining < 0 ? -1L : overall + remaining;

                if (buffer == null) buffer = new byte[262144];

                while ((current = readStr.read(buffer)) != -1) {
                    overall += current;
                    fos.write(buffer, 0, current);
                    long max = total < 0 ? overall : total;
                    monitor.updateProgress((int) Math.min(overall, Integer.MAX_VALUE),
                            (int) Math.min(max, Integer.MAX_VALUE));
                }
            } finally {
                try { readStr.close(); } catch (Exception ignored) { }
            }
        } finally {
            conn.disconnect();
        }
    }

    /** Truncate a stale partial file (used when the server rejects our resume range). */
    private static void truncateQuietly(File file) {
        try (FileOutputStream ignored = new FileOutputStream(file)) {
            // Opening without append truncates the file.
        } catch (IOException e) {
            Log.w("DownloadUtils", "Could not truncate " + file, e);
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static <T> T downloadStringCached(String url, String cacheName, ParseCallback<T> parseCallback) throws IOException, ParseException{
        File cacheDestination = new File(Tools.DIR_CACHE, "string_cache/"+cacheName);
        if(cacheDestination.isFile() &&
                cacheDestination.canRead() &&
                System.currentTimeMillis() < (cacheDestination.lastModified() + 86400000)) {
            try {
                String cachedString = Tools.read(new FileInputStream(cacheDestination));
                return parseCallback.process(cachedString);
            }catch(IOException e) {
                Log.i("DownloadUtils", "Failed to read the cached file", e);
            }catch (ParseException e) {
                Log.i("DownloadUtils", "Failed to parse the cached file", e);
            }
        }
        String urlContent = DownloadUtils.downloadString(url);
        // if we download the file and fail parsing it, we will yeet outta there
        // and not cache the unparseable sting. We will return this after trying to save the downloaded
        // string into cache
        T parseResult = parseCallback.process(urlContent);

        boolean tryWriteCache;
        if(cacheDestination.exists()) {
            tryWriteCache = cacheDestination.canWrite();
        } else {
            tryWriteCache = FileUtils.ensureParentDirectorySilently(cacheDestination);
        }

        if(tryWriteCache) try {
            Tools.write(cacheDestination, urlContent);
        }catch(IOException e) {
            Log.i("DownloadUtils", "Failed to cache the string", e);
        }
        return parseResult;
    }

    private static <T> T downloadFile(Callable<T> downloadFunction) throws IOException{
        try {
            return downloadFunction.call();
        } catch (IOException e){
            throw e;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean verifyFile(File file, String sha1) throws IOException {
        return file.exists() && HashUtils.compareSHA1(file, sha1);
    }

    public static <T> T ensureSha1(File outputFile, @Nullable String sha1, Callable<T> downloadFunction) throws IOException {
        // Skip if needed
        if(sha1 == null) {
            // If the file exists and we don't know it's SHA1, don't try to redownload it.
            if(outputFile.exists()) return null;
            else return downloadFile(downloadFunction);
        }

        int attempts = 0;
        boolean fileOkay = verifyFile(outputFile, sha1);
        T result = null;
        while (attempts < 5 && !fileOkay){
            attempts++;
            downloadFile(downloadFunction);
            fileOkay = verifyFile(outputFile, sha1);
        }
        if(!fileOkay) throw new SHA1VerificationException("SHA1 verifcation failed after 5 download attempts");
        return result;
    }

    /**
     * Get the content length for a given URL.
     * @param url the URL to get the length for
     * @return the length in bytes or -1 if not available
     */
    public static long getContentLength(String url) {
        try {
            HttpURLConnection urlConnection = (HttpURLConnection) new URL(url).openConnection();
            urlConnection.setRequestMethod("HEAD");
            urlConnection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            urlConnection.setReadTimeout(CONNECT_TIMEOUT_MS);
            urlConnection.setDoInput(false);
            urlConnection.setDoOutput(false);
            urlConnection.connect();
            int responseCode = urlConnection.getResponseCode();
            if(responseCode >= 200 && responseCode <= 299) return urlConnection.getContentLength();
        }catch (IOException e) {
            Log.w("DownloadUtils", "Failed to get content length", e);
        }
        return -1;
    }

    public interface ParseCallback<T> {
        T process(String input) throws ParseException;
    }
    public static class ParseException extends Exception {
        public ParseException(Exception e) {
            super(e);
        }
    }

    public static class SHA1VerificationException extends IOException {
        public SHA1VerificationException(String message) {
            super(message);
        }
    }
}

