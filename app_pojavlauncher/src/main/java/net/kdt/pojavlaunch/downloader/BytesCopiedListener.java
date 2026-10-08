package net.kdt.pojavlaunch.downloader;

public interface BytesCopiedListener {
    void onBytesCopied(int nbytes);

    /**
     * Called when the current attempt is abandoned and the file is about to be restarted from
     * zero (for example a mirror that returned the wrong bytes falling back to the official URL),
     * so the bytes already counted for this attempt can be un-counted. Default is a no-op.
     */
    default void onRestart() { }
}
