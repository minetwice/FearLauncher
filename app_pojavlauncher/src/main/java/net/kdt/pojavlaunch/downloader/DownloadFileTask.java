package net.kdt.pojavlaunch.downloader;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

public class DownloadFileTask extends DownloaderTask implements BytesCopiedListener {
    /**
     * How many times a single file is attempted before it is reported as failed. Every attempt
     * after the first resumes from the partial file when the server honours a Range request, so an
     * attempt makes forward progress instead of starting over; the budget only bounds a link that
     * is permanently broken (each attempt is itself bounded by the socket read timeout and the
     * whole-download stall watchdog).
     */
    private static final int MAX_DOWNLOAD_ATTEMPTS = 8;
    /** Upper bound for the pause between two attempts, so a flaky link gets time to recover. */
    private static final long MAX_RETRY_BACKOFF_MS = 4000L;

    private final AtomicLong mBytesDownloaded = new AtomicLong();
    DownloadFileTask(TaskMetadata mMetadata, Downloader mHostDownloader) {
        super(mMetadata, mHostDownloader);
    }

    @Override
    protected void performTask() throws IOException {
        tryDownload();
        mDownloader.submitFileForRecheck(mMetadata);
    }

    /**
     * Single retry owner for one file. It loops (never recurses) a bounded number of times; each
     * iteration either finishes the file, resumes it with a Range request, or - when the server
     * refuses the range - restarts it from zero. The partial file is kept across attempts so a
     * retry can always resume.
     */
    private void tryDownload() throws IOException {
        IOException lastError = null;
        for (int attempt = 0; attempt < MAX_DOWNLOAD_ATTEMPTS; attempt++) {
            if (attempt > 0) sleepQuietly(Math.min(MAX_RETRY_BACKOFF_MS, 500L * attempt));
            // Whatever we counted for this file so far is about to be re-counted from disk, so
            // un-count it first. This keeps the shared progress counter equal to the bytes that are
            // actually on disk even when an attempt restarts or is abandoned part-way through.
            mDownloader.addSize(-mBytesDownloaded.get());
            mBytesDownloaded.set(0);
            try {
                long alreadyDownloaded = mMetadata.path.exists() ? mMetadata.path.length() : 0L;
                if (alreadyDownloaded <= 0L) {
                    // Nothing usable on disk: download from zero. This validates the mirror and
                    // falls back to the official URL when the mirror is bad or missing.
                    mDownloader.downloadFileMirrored(mMetadata, this);
                } else {
                    // A partial file exists: count it and ask the server to send only the rest.
                    mBytesDownloaded.set(alreadyDownloaded);
                    mDownloader.addSize(alreadyDownloaded);
                    if (!mDownloader.tryContinueDownloadMirrored(mMetadata, this)) {
                        // The server refused the Range request, so this partial cannot be resumed.
                        // Un-count it and let the next line download from zero; the fresh
                        // FileOutputStream truncates the stale partial, so nothing is left behind.
                        mDownloader.addSize(-mBytesDownloaded.get());
                        mBytesDownloaded.set(0);
                        mDownloader.downloadFileMirrored(mMetadata, this);
                    }
                }
                return;
            } catch (IOException e) {
                // Keep the partial file so the next attempt resumes instead of restarting.
                lastError = e;
            }
        }
        throw new IOException("Failed to download file "
                + mMetadata.path.getName() + " (" + mMetadata.url + ")", lastError);
    }

    @Override
    public void onBytesCopied(int nbytes) {
        mBytesDownloaded.getAndAdd(nbytes);
        mDownloader.addSize(nbytes);
    }

    /**
     * The downloader is abandoning the current attempt and restarting this file from zero (for
     * example a mirror that returned the wrong bytes falling back to the official URL). Un-count
     * what this file contributed so the restart is not double-counted.
     */
    @Override
    public void onRestart() {
        mDownloader.addSize(-mBytesDownloaded.get());
        mBytesDownloaded.set(0);
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
