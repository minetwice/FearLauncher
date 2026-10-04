package com.kdt;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import net.kdt.pojavlaunch.Logger;
import git.artdeell.mojo.R;

/**
 * A class able to display logs to the user.
 * It has support for the Logger class
 */
public class LoggerView extends ConstraintLayout {
    private ToggleButton mLogToggle;
    private DefocusableScrollView mScrollView;
    private TextView mLogTextView;
    private static final int FEAR_MAX_LOG_LINES = 1000;
    // FEAR-LOGTAIL: the console follows latestlog.txt directly (see tailLogFile).
    private Thread mTailThread;
    private long mTailOffset = 0;
    private volatile boolean mTailRunning = true;

    public LoggerView(@NonNull Context context) {
        this(context, null);
    }

    public LoggerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        // Triggers the log view shown state by default when viewing it
        mLogToggle.setChecked(visibility == VISIBLE);
    }

    /**
     * Inflate the layout, and add component behaviors
     */
    private void init(){
        inflate(getContext(), R.layout.view_logger, this);
        mLogTextView = findViewById(R.id.content_log_view);
        mLogTextView.setTypeface(Typeface.MONOSPACE);
        // FEAR-LOGCOMPACT: small console-style text, takes far less screen space
        mLogTextView.setTextSize(9.5f);
        mLogTextView.setLineSpacing(0f, 1f);
        //TODO clamp the max text so it doesn't go oob (handled by FEAR-LOGCOMPACT below)
        mLogTextView.setMaxLines(Integer.MAX_VALUE);
        mLogTextView.setEllipsize(null);
        // FEAR: start VISIBLE - the console used to start hidden with its output
        // toggle off, which meant Logger.setLogListener(null) was in force and
        // nothing was captured at all while the game was launching.
        mLogTextView.setVisibility(VISIBLE);

        // Toggle log visibility
        mLogToggle = findViewById(R.id.content_log_toggle_log);
        mLogToggle.setOnCheckedChangeListener(
                (compoundButton, isChecked) -> {
                    mLogTextView.setVisibility(isChecked ? VISIBLE : GONE);
                    if(!isChecked) mLogTextView.setText("");
                });
        mLogToggle.setChecked(true);   // capture from the very first launch line

        // Remove the loggerView from the user View
        ImageButton cancelButton = findViewById(R.id.log_view_cancel);
        cancelButton.setOnClickListener(view -> LoggerView.this.setVisibility(GONE));

        // Share to McLo.gs Feature Implementation (Copper Launcher premium function)
        com.kdt.mcgui.MineButton shareBtn = findViewById(R.id.btn_share_mclogs);
        if (shareBtn != null) {
            shareBtn.setOnClickListener(view -> {
                final String logText = mLogTextView.getText().toString();
                if (logText.trim().isEmpty()) {
                    android.widget.Toast.makeText(getContext(), "Log content is empty!", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                shareBtn.setEnabled(false);
                shareBtn.setText("UPLOADING...");
                new Thread(() -> {
                    try {
                        java.net.URL url = new java.net.URL("https://api.mclo.gs/1/log");
                        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setDoOutput(true);
                        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                        String postData = "content=" + java.net.URLEncoder.encode(logText, "UTF-8");
                        try (java.io.OutputStream os = conn.getOutputStream()) {
                            os.write(postData.getBytes("UTF-8"));
                        }

                        int responseCode = conn.getResponseCode();
                        if (responseCode == 200) {
                            try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                                StringBuilder response = new StringBuilder();
                                String line;
                                while ((line = br.readLine()) != null) {
                                    response.append(line);
                                }
                                // Parse {"success":true,"url":"https://mclo.gs/xxxxx"}
                                String resStr = response.toString();
                                int urlIdx = resStr.indexOf("\"url\":\"");
                                if (urlIdx != -1) {
                                    final String sharedUrl = resStr.substring(urlIdx + 7, resStr.indexOf("\"", urlIdx + 7));
                                    post(() -> {
                                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                                        android.content.ClipData clip = android.content.ClipData.newPlainText("Copied Log Link", sharedUrl);
                                        clipboard.setPrimaryClip(clip);
                                        android.widget.Toast.makeText(getContext(), "Log URL Copied: " + sharedUrl, android.widget.Toast.LENGTH_LONG).show();
                                        shareBtn.setEnabled(true);
                                        shareBtn.setText("SHARE LOGS (MCLO.GS)");
                                    });
                                    return;
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    post(() -> {
                        android.widget.Toast.makeText(getContext(), "Failed to upload log to mclo.gs!", android.widget.Toast.LENGTH_SHORT).show();
                        shareBtn.setEnabled(true);
                        shareBtn.setText("SHARE LOGS (MCLO.GS)");
                    });
                }).start();
            });
        }

        // Set the scroll view
        mScrollView = findViewById(R.id.content_log_scroll);
        mScrollView.setKeepFocusing(true);

        //Set up the autoscroll switch
        ToggleButton autoscrollToggle = findViewById(R.id.content_log_toggle_autoscroll);
        autoscrollToggle.setOnCheckedChangeListener(
                (compoundButton, isChecked) -> {
                    if(isChecked) mScrollView.fullScroll(View.FOCUS_DOWN);
                    mScrollView.setKeepFocusing(isChecked);
                }
        );
        autoscrollToggle.setChecked(true);

        // FEAR-LOGTAIL: follow latestlog.txt directly.
        //
        // The native stdout reader writes the game's own output to latestlog.txt
        // but deliberately never calls the Java log listener (see stdio_is.c, MC27),
        // so the old listener-driven console showed only the launcher's own lines
        // and then sat still while the game logged. Reading the file itself means
        // every line - game stdout included - reaches the view, and it keeps
        // following the file as it grows.
        mTailThread = new Thread(this::tailLogFile, "fear-log-tail");
        mTailThread.setDaemon(true);
        mTailThread.start();
    }

    /** Polls latestlog.txt and appends whatever is new to the console. */
    private void tailLogFile() {
        while (mTailRunning) {
            try {
                Thread.sleep(250);
                String home = net.kdt.pojavlaunch.Tools.DIR_GAME_HOME;
                if (home == null) continue;
                java.io.File log = new java.io.File(home, "latestlog.txt");
                if (!log.isFile()) continue;
                long len = log.length();
                if (len < mTailOffset) mTailOffset = 0; // truncated by Logger.begin
                if (len <= mTailOffset) continue;
                long start = mTailOffset;
                String complete;
                long consumed;
                try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(log, "r")) {
                    raf.seek(start);
                    byte[] buf = new byte[(int) Math.min(len - start, 65536)];
                    int read = raf.read(buf);
                    if (read <= 0) continue;
                    String raw = new String(buf, 0, read, "UTF-8");
                    int lastNewline = raw.lastIndexOf('\n');
                    if (lastNewline < 0) continue; // wait for a whole line
                    complete = raw.substring(0, lastNewline + 1);
                    consumed = complete.getBytes("UTF-8").length;
                }
                mTailOffset = start + consumed;
                if (mLogTextView.getVisibility() == VISIBLE) {
                    final String chunk = complete;
                    post(() -> appendLogChunk(chunk));
                }
            } catch (InterruptedException e) {
                return;
            } catch (Throwable ignored) { }
        }
    }

    /** Appends text on the UI thread, caps the buffer and follows the bottom. */
    private void appendLogChunk(String chunk) {
        mLogTextView.append(chunk);
        int lineCount = mLogTextView.getLineCount();
        if (lineCount > FEAR_MAX_LOG_LINES + 200 && mLogTextView.getLayout() != null) {
            int cut = mLogTextView.getLayout().getLineStart(lineCount - FEAR_MAX_LOG_LINES);
            if (cut > 0) mLogTextView.getEditableText().delete(0, cut);
        }
        if (mScrollView != null && mScrollView.isKeepFocusing()) {
            mScrollView.post(() -> mScrollView.fullScroll(View.FOCUS_DOWN));
        }
    }

}
