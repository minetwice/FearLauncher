package net.kdt.pojavlaunch.downloader;

import java.io.IOException;

public abstract class DownloaderTask implements Runnable {
    protected final TaskMetadata mMetadata;
    protected final Downloader mDownloader;

    protected DownloaderTask(TaskMetadata mMetadata, Downloader mHostDownloader) {
        this.mMetadata = mMetadata;
        this.mDownloader = mHostDownloader;
    }

    @Override
    public final void run() {
        try {
            performTask();
        }catch (IOException e) {
            mDownloader.taskException(e);
        }catch (Throwable t) {
            // A RuntimeException (or any other unchecked error) must never silently kill a worker
            // thread: if it did, the file counter would never advance and the whole download would
            // spin forever, leaving the launcher permanently "busy".
            mDownloader.taskException(new IOException("Unhandled error in download task", t));
        }
    }

    protected abstract void performTask() throws IOException;
}
