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
    private Logger.eventLogListener mLogListener;
    private ToggleButton mLogToggle;
    private DefocusableScrollView mScrollView;
    private TextView mLogTextView;
    // FEAR-LOGCOMPACT: pending log text, flushed to the TextView at most every
    // 100ms. Appending per line floods the UI thread and the fullScroll() per
    // line is what made the game stutter while the log was open.
    private final StringBuilder mPendingLog = new StringBuilder();
    private boolean mFlushScheduled = false;
    private static final int FEAR_MAX_LOG_LINES = 1000;

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
                    if(isChecked) {
                        Logger.setLogListener(mLogListener);
                    }else{
                        mLogTextView.setText("");
                        synchronized (mPendingLog) { mPendingLog.setLength(0); } // FEAR-LOGCOMPACT: drop pending batch
                        Logger.setLogListener(null); // Makes the JNI code be able to skip expensive logger callbacks
                        // NOTE: was tested by rapidly smashing the log on/off button, no sync issues found :)
                    }
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

        // Listen to logs
        // FEAR-LOGCOMPACT: buffer incoming lines and flush in one TextView
        // update every 100ms - one layout pass instead of one per log line -
        // and cap the buffer at 1000 lines so it can never grow unbounded
        // (fixes the old "TODO clamp the max text so it doesn't go oob").
        mLogListener = text -> {
            if(mLogTextView.getVisibility() != VISIBLE) return;
            synchronized (mPendingLog) {
                mPendingLog.append(text).append('\n');
            }
            if(mFlushScheduled) return;
            mFlushScheduled = true;
            postDelayed(() -> {
                mFlushScheduled = false;
                String chunk;
                synchronized (mPendingLog) {
                    chunk = mPendingLog.toString();
                    mPendingLog.setLength(0);
                }
                if (chunk.isEmpty()) return;
                mLogTextView.append(chunk);
                int lineCount = mLogTextView.getLineCount();
                if (lineCount > FEAR_MAX_LOG_LINES + 200 && mLogTextView.getLayout() != null) {
                    int cut = mLogTextView.getLayout().getLineStart(lineCount - FEAR_MAX_LOG_LINES);
                    if (cut > 0) mLogTextView.getEditableText().delete(0, cut);
                }
                if(mScrollView.isKeepFocusing()) mScrollView.fullScroll(View.FOCUS_DOWN);
            }, 100);
        };
    }

}
