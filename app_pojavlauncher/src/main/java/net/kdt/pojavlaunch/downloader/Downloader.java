package net.kdt.pojavlaunch.downloader;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.mirrors.DownloadMirror;
import net.kdt.pojavlaunch.tasks.SpeedCalculator;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.utils.HashUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import android.util.Log;

import com.fearlauncher.fear.R;

public class Downloader {
    private static final double ONE_MEGABYTE = (1024d * 1024d);
    /**
     * If no byte is transferred and no file completes for this long, the download is declared
     * stalled and aborted instead of spinning forever. A single socket read already gives up
     * after {@link DownloadUtils#READ_TIMEOUT_MS}, so this is only a last-resort backstop.
     */
    private static final long STALL_TIMEOUT_MS = 120_000L;
    /** Upper bound for the metadata-completion phase (HEAD/sha1 lookups); a backstop only. */
    private static final long METADATA_TIMEOUT_MS = 300_000L;
    /**
     * The bulk-download phase occupies 0..this percentage of the shared progress bar when the
     * caller runs post-download steps (client-JAR copy, natives extraction) afterwards. Subclasses
     * that do so (e.g. {@code MinecraftDownloader}) set {@link #mDownloadProgressBand} to this;
     * for everyone else the bar simply runs to 100%.
     */
    public static final int DOWNLOAD_PROGRESS_BAND = 90;
    private static final ThreadLocal<byte[]> sThreadLocalBuffer = new ThreadLocal<>();
    private final String mProgressKey;
    private final AtomicReference<IOException> mThreadException = new AtomicReference<>();
    private final AtomicInteger mDownloadedFileCounter = new AtomicInteger();
    /** Files that failed permanently; a non-zero value is a terminal condition for the loop. */
    private final AtomicInteger mFailedFileCounter = new AtomicInteger();
    private final AtomicLong mDownloadedSizeCounter = new AtomicLong();
    private final AtomicLong mInternetUsageCounter = new AtomicLong();
    private final AtomicBoolean mUseSizeProgress = new AtomicBoolean(true);
    private final SpeedCalculator mSpeedCalculator = new SpeedCalculator();
    /** Files not yet completed, keyed by absolute path, so a stall can name the culprit(s). */
    private final ConcurrentHashMap<String, TaskMetadata> mPendingFiles = new ConcurrentHashMap<>();
    /** Percentage at which the download loop ends its share of the bar (100 = it owns the bar). */
    protected int mDownloadProgressBand = 100;
    private ExecutorService mDownloadService;
    private ExecutorService mVerifyService;

    public Downloader(String mProgressKey) {
        this.mProgressKey = mProgressKey;
    }

    protected void runDownloads(ArrayList<? extends TaskMetadata> downloads) throws IOException, InterruptedException {
        insertMetadata(downloads);
        performDownloads(downloads);
    }

    private void performDownloads(ArrayList<? extends TaskMetadata> metadata) throws IOException, InterruptedException {
        mThreadException.set(null);
        mDownloadedFileCounter.set(0);
        mFailedFileCounter.set(0);
        mDownloadedSizeCounter.set(0);
        mPendingFiles.clear();
        // Scale the worker count with the device within sane bounds: at least 4 so a single slow
        // file never serialises the whole install, at most 8 so we do not swamp a mobile link.
        int downloadThreads = Math.max(4, Math.min(8, Runtime.getRuntime().availableProcessors()));
        mDownloadService = Executors.newFixedThreadPool(downloadThreads, r -> {
            Thread thread = new Thread(r);
            thread.setPriority(Thread.NORM_PRIORITY + 2);
            thread.setName("download thread");
            return thread;
        });
        int verifyThreads = Math.max(4, Math.min(8, Runtime.getRuntime().availableProcessors()));
        mVerifyService = Executors.newFixedThreadPool(verifyThreads, r -> {
            Thread thread = new Thread(r);
            thread.setPriority(Thread.NORM_PRIORITY + 1);
            thread.setName("verify thread");
            return thread;
        });
        long totalSize = 0;
        int totalCount = metadata.size();
        boolean sizeCounter = mUseSizeProgress.get();
        for(TaskMetadata element : metadata) {
            totalSize += element.size;
            if(element.path != null) mPendingFiles.put(element.path.getAbsolutePath(), element);
            mVerifyService.submit(new CheckFileOnDiskTask(element, this));
        }
        double totalMegabytes = totalSize / ONE_MEGABYTE;
        long lastProgressAt = System.currentTimeMillis();
        long lastBytes = mInternetUsageCounter.get();
        long lastFiles = mDownloadedFileCounter.get();
        // Completion accounting is total: every scheduled file ends either completed (counter
        // advanced) or failed (failure counter advanced, and the phase aborts). A file that can
        // never be produced therefore ends the loop instead of leaving it spinning just under 100%.
        while(mDownloadedFileCounter.get() + mFailedFileCounter.get() < totalCount) {
            IOException exception = mThreadException.get();
            if(exception != null) throw exception;
            long now = System.currentTimeMillis();
            long bytes = mInternetUsageCounter.get();
            long files = mDownloadedFileCounter.get();
            if(bytes != lastBytes || files != lastFiles) {
                lastBytes = bytes;
                lastFiles = files;
                lastProgressAt = now;
            } else if(now - lastProgressAt > STALL_TIMEOUT_MS) {
                mDownloadService.shutdownNow();
                mVerifyService.shutdownNow();
                throw new IOException("Download stalled: no data for " + (STALL_TIMEOUT_MS / 1000)
                        + "s (" + files + "/" + totalCount + " files finished). "
                        + "Still waiting for: " + describePendingFiles()
                        + ". Check your connection or download source and try again.");
            }
            if(sizeCounter) reportSizeProgress(totalMegabytes);
            else reportCountProgress(R.string.newerdl_downloading_files_count, totalCount);
            Thread.sleep(33);
        }
        // A file failed: abort loudly and name it, rather than reporting success.
        IOException failure = mThreadException.get();
        if(failure != null) throw failure;
        if(mFailedFileCounter.get() > 0) {
            throw new IOException("Download failed: " + mFailedFileCounter.get()
                    + " file(s) could not be downloaded. Please retry.");
        }
        // The file phase is complete. If the caller declared post-processing steps (band < 100),
        // pin the bar to the top of the download band and label it, so it cannot sit at "almost
        // done" while those run.
        if(mDownloadProgressBand < 100) {
            ProgressLayout.setProgress(mProgressKey, mDownloadProgressBand, R.string.newdl_finalizing_game_files);
        }
        mDownloadService.shutdown();
        mVerifyService.shutdown();
        if(!mDownloadService.awaitTermination(100, TimeUnit.MILLISECONDS) ||
                !mVerifyService.awaitTermination(100, TimeUnit.MILLISECONDS)) {
            throw new RuntimeException("BUG! The file counter is wrong. Maybe. Send this to artDev.");
        }
    }

    private void insertMetadata(ArrayList<? extends TaskMetadata> metadata) throws IOException, InterruptedException {
        mThreadException.set(null);
        mDownloadedFileCounter.set(0);
        ArrayList<TaskMetadata> reducedList = new ArrayList<>();
        for(TaskMetadata element : metadata) {
            if(!CompleteMetadataTask.shouldCompleteMetadata(element)) continue;
            reducedList.add(element);
        }
        if(reducedList.isEmpty()) return;
        int threads = Math.max(8, Runtime.getRuntime().availableProcessors() * 2);
        try (ExecutorService executorService = Executors.newFixedThreadPool(threads)) {
            for(TaskMetadata element : reducedList) executorService.submit(new CompleteMetadataTask(element, this));
            executorService.shutdown();
            long metadataDeadline = System.currentTimeMillis() + METADATA_TIMEOUT_MS;
            while (!executorService.awaitTermination(33, TimeUnit.MILLISECONDS)) {
                IOException exception = mThreadException.get();
                if(exception != null) throw exception;
                if(System.currentTimeMillis() > metadataDeadline) {
                    executorService.shutdownNow();
                    throw new IOException("Metadata download stalled for more than "
                            + (METADATA_TIMEOUT_MS / 1000) + "s; aborting. Please retry.");
                }
                reportCountProgress(R.string.newerdl_inserting_metadata_count, reducedList.size());
            }
        }
    }

    private double getSpeed() {
        return mSpeedCalculator.feed(mInternetUsageCounter.get()) / ONE_MEGABYTE;
    }

    private void reportCountProgress(int resource, int total) {
        int downloadedCount = mDownloadedFileCounter.get();
        int progress = total <= 0 ? mDownloadProgressBand
                : (int) ((downloadedCount / (float)total) * mDownloadProgressBand);
        ProgressLayout.setProgress(mProgressKey, progress, resource,
                downloadedCount, total, getSpeed()
        );
    }

    private void reportSizeProgress(double totalMegabytes) {
        double downloadedMegabytes = mDownloadedSizeCounter.get() / ONE_MEGABYTE;
        double ratio = totalMegabytes <= 0 ? 0d : downloadedMegabytes / totalMegabytes;
        int progress = (int) Math.min(mDownloadProgressBand, ratio * mDownloadProgressBand);
        ProgressLayout.setProgress(mProgressKey, progress, R.string.newerdl_downloading_files_size,
                downloadedMegabytes, totalMegabytes, getSpeed()
        );
    }

    /** Human-readable list of the files still outstanding, used to name a stall. */
    private String describePendingFiles() {
        if(mPendingFiles.isEmpty()) return "(no outstanding files)";
        StringBuilder builder = new StringBuilder();
        int shown = 0;
        for(TaskMetadata pending : mPendingFiles.values()) {
            if(shown > 0) builder.append(", ");
            builder.append(pending.path == null ? "?" : pending.path.getName());
            if(++shown >= 5) break;
        }
        int remaining = mPendingFiles.size() - shown;
        if(remaining > 0) builder.append(", ... (").append(remaining).append(" more)");
        return builder.toString();
    }

    protected void taskException(IOException e) {
        mThreadException.compareAndSet(null, e);
    }

    protected void disableSizeCounter() {
        mUseSizeProgress.lazySet(false);
    }

    protected void submitFileForDownload(TaskMetadata taskMetadata) {
        mDownloadService.submit(new DownloadFileTask(taskMetadata, this));
    }

    protected void submitFileForRecheck(TaskMetadata taskMetadata) {
        mVerifyService.submit(new CheckFileOnDiskTask(taskMetadata, this, true));
    }

    protected void fileComplete() {
        mDownloadedFileCounter.getAndIncrement();
    }

    /** Mark a specific file as complete: also drops it from the outstanding set. */
    protected void fileComplete(TaskMetadata taskMetadata) {
        if(taskMetadata != null && taskMetadata.path != null) {
            mPendingFiles.remove(taskMetadata.path.getAbsolutePath());
        }
        mDownloadedFileCounter.getAndIncrement();
    }

    /**
     * Record a file that cannot be produced and abort the phase, naming it. Every failed task ends
     * here, so a verification failure can never leave the completion counter short: the loop either
     * advances the counter or terminates on this failure, and the reported error names the file.
     */
    protected void fileFailed(TaskMetadata taskMetadata, IOException cause) {
        if(taskMetadata != null && taskMetadata.path != null) {
            mPendingFiles.remove(taskMetadata.path.getAbsolutePath());
        }
        mFailedFileCounter.getAndIncrement();
        taskException(new IOException("Failed to download "
                + (taskMetadata == null || taskMetadata.path == null ? "(unknown file)" : taskMetadata.path.getName())
                + (taskMetadata == null || taskMetadata.url == null ? "" : " from " + taskMetadata.url), cause));
    }

    protected void addSize(long bytes) {
        mDownloadedSizeCounter.getAndAdd(bytes);
    }

    private void copy(InputStream inputStream, OutputStream outputStream, BytesCopiedListener listener) throws IOException {
        byte[] buffer = getBuffer();
        int readLen;
        while((readLen = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, readLen);
            if(listener != null) listener.onBytesCopied(readLen);
            mInternetUsageCounter.getAndAdd(readLen);
        }
    }

    private static HttpURLConnection openConnection(URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", DownloadUtils.USER_AGENT);
        connection.setRequestProperty("Connection", "keep-alive");
        connection.setDoInput(true);
        connection.setDoOutput(false);
        return connection;
    }

    protected void downloadToStream(HttpURLConnection connection, OutputStream outputStream, BytesCopiedListener listener) throws IOException {
        InputStream inputStream = connection.getInputStream();
        copy(inputStream, outputStream, listener);
    }

    protected String downloadString(URL url) throws IOException {
        HttpURLConnection connection = openConnection(url);
        int length = connection.getContentLength();
        if(length < 0) length = 32;
        try(ByteArrayOutputStream outputStream = new ByteArrayOutputStream(length)) {
            downloadToStream(connection, outputStream, null);
            return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        }finally {
            connection.disconnect();
        }
    }

    protected void downloadFile(File file, URL url, BytesCopiedListener listener) throws IOException {
        HttpURLConnection connection = openConnection(url);
        try(FileOutputStream outputStream = new FileOutputStream(file)) {
            downloadToStream(connection, outputStream, listener);
        }finally {
            connection.disconnect();
        }
    }

    /**
     * Resolve a task's URL through the configured mirror, or return the original URL when no
     * mirror applies (source set to "default", or a host the mirror does not serve).
     */
    protected URL mirrorUrl(TaskMetadata taskMetadata) {
        if(taskMetadata.url == null) return null;
        // Only Mojang's own file hosts are served by the mirror; anything else (CurseForge,
        // Modrinth, custom Maven repositories, ...) must keep its original URL. This also protects
        // the modpack installers, which reuse DOWNLOAD_CLASS_METADATA for non-Mojang CDN files.
        if(!isMojangHost(taskMetadata.url.getHost())) return taskMetadata.url;
        try {
            String mapped = DownloadMirror.getMirrorMapping(taskMetadata.mirrorType, taskMetadata.url.toString());
            if(mapped == null || mapped.equals(taskMetadata.url.toString())) return taskMetadata.url;
            return new URL(mapped);
        } catch (Exception e) {
            return taskMetadata.url;
        }
    }

    /** Hosts served by the configured mirror (Mojang's own file hosts). */
    private static boolean isMojangHost(String host) {
        return host != null && (host.endsWith("mojang.com") || host.endsWith("minecraft.net"));
    }

    /**
     * Whether a locally downloaded file already matches everything we know about it (size and/or
     * SHA-1). Used to validate a mirror's response before trusting it: a mirror that returns the
     * wrong bytes must fall back to the official source instead of failing the whole install.
     */
    private boolean matchesExpectedContent(TaskMetadata taskMetadata) {
        File file = taskMetadata.path;
        if(file == null || !file.exists()) return false;
        if(taskMetadata.size > 0 && file.length() != taskMetadata.size) return false;
        if(taskMetadata.sha1Hash != null) {
            try {
                return HashUtils.compareSHA1(file, taskMetadata.sha1Hash);
            }catch (IOException e) {
                Log.w("Downloader", "Could not verify " + file.getName()
                        + " against " + taskMetadata.sha1Hash, e);
                return false;
            }
        }
        return true;
    }

    /**
     * Download a bulk file (game JAR, library or asset), preferring the configured mirror and
     * falling back to the official Mojang URL if the mirror fails OR returns content that does not
     * match the known size/SHA-1. The official fallback is treated as success, so a bad or missing
     * mirror entry can never break an install. Any partial file is kept so the next attempt can
     * resume with a Range request.
     */
    protected void downloadFileMirrored(TaskMetadata taskMetadata, BytesCopiedListener listener) throws IOException {
        URL mirror = mirrorUrl(taskMetadata);
        if(mirror == null || mirror.toString().equals(taskMetadata.url.toString())) {
            downloadFile(taskMetadata.path, taskMetadata.url, listener);
            return;
        }
        try {
            downloadFile(taskMetadata.path, mirror, listener);
            if(matchesExpectedContent(taskMetadata)) return;
            Log.w("Downloader", "Mirror " + mirror
                    + " returned unexpected content; retrying from " + taskMetadata.url);
        }catch (IOException mirrorError) {
            Log.w("Downloader", "Mirror download failed for " + mirror
                    + "; falling back to " + taskMetadata.url, mirrorError);
        }
        downloadFile(taskMetadata.path, taskMetadata.url, listener);
    }

    /**
     * Resume a bulk file from the mirror when possible, otherwise from the official URL. Mirrors
     * support Range requests just like Mojang, so a partial download can always be resumed. If the
     * mirror produces content that does not verify, the suspect partial file is dropped and the
     * caller restarts it (which will validate the mirror and fall back if needed).
     * @return true if the server honoured the Range request and appended a valid remainder
     */
    protected boolean tryContinueDownloadMirrored(TaskMetadata taskMetadata, BytesCopiedListener listener) throws IOException {
        URL mirror = mirrorUrl(taskMetadata);
        if(mirror != null && !mirror.toString().equals(taskMetadata.url.toString())) {
            try {
                if(tryContinueDownload(taskMetadata.path, taskMetadata.size, mirror, listener)) {
                    if(matchesExpectedContent(taskMetadata)) return true;
                    Log.w("Downloader", "Mirror " + mirror
                            + " produced unexpected content; restarting from " + taskMetadata.url);
                    if(!taskMetadata.path.delete()) {
                        Log.w("Downloader", "Could not delete partial file " + taskMetadata.path);
                    }
                    return false;
                }
            }catch (IOException mirrorError) {
                Log.w("Downloader", "Mirror resume failed for " + mirror
                        + "; trying " + taskMetadata.url, mirrorError);
            }
        }
        return tryContinueDownload(taskMetadata.path, taskMetadata.size, taskMetadata.url, listener);
    }

    /** Learn a file's size from the mirror first, falling back to the official URL. */
    protected long getFileContentLengthMirrored(TaskMetadata taskMetadata) {
        URL mirror = mirrorUrl(taskMetadata);
        if(mirror != null && !mirror.toString().equals(taskMetadata.url.toString())) {
            try {
                long length = getFileContentLength(mirror);
                if(length > 0) return length;
            }catch (IOException ignored) {
                // fall through to the official source
            }
        }
        if(taskMetadata.url == null) return -1;
        try {
            return getFileContentLength(taskMetadata.url);
        }catch (IOException ignored) {
            return -1;
        }
    }

    /** Fetch a small metadata sidecar (e.g. a library ".sha1") from the mirror, else the official URL. */
    protected String downloadStringMirrored(TaskMetadata taskMetadata, String suffix) throws IOException {
        URL mirror = mirrorUrl(taskMetadata);
        if(mirror != null && !mirror.toString().equals(taskMetadata.url.toString())) {
            try {
                return downloadString(new URL(mirror.toString() + suffix));
            }catch (IOException mirrorError) {
                Log.w("Downloader", "Mirror metadata fetch failed for " + mirror + suffix, mirrorError);
            }
        }
        return downloadString(new URL(taskMetadata.url.toString() + suffix));
    }

    protected boolean tryContinueDownload(File file, long wantedLength, URL url, BytesCopiedListener listener) throws IOException {
        HttpURLConnection connection = openConnection(url);
        String range = String.format(Locale.ENGLISH, "bytes=%d-%d", file.length(), wantedLength - 1);
        connection.setRequestProperty("Range", range);
        try {
            connection.connect();
            int responseCode = connection.getResponseCode();
            if(responseCode != 206) {
                return false;
            }
            try(FileOutputStream outputStream = new FileOutputStream(file, true)) {
                downloadToStream(connection, outputStream, listener);
                return true;
            }
        }finally {
            connection.disconnect();
        }
    }

    protected long getFileContentLength(URL url) throws IOException {
        HttpURLConnection connection = openConnection(url);
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        connection.setRequestMethod("HEAD");
        try {
            connection.connect();
            int response = connection.getResponseCode();
            if(response == 200 || response == 206) {
                long len = connection.getContentLengthLong();
                if(len > 0) return len;
            }
        } catch (IOException ignored) {
            // Fallback gracefully if HEAD is blocked or times out
        } finally {
            connection.disconnect();
        }
        return -1;
    }

    public static byte[] getBuffer() {
        byte[] buffer = sThreadLocalBuffer.get();
        if(buffer == null) {
            buffer = new byte[262144]; // 256KB High-throughput thread-local buffer
            sThreadLocalBuffer.set(buffer);
        }
        return buffer;
    }
}
