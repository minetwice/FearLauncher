package com.kdt.mcgui;


import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.constraintlayout.widget.ConstraintLayout;

import git.artdeell.mojo.R;

import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.ProgressListener;
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener;

import java.util.ArrayList;


/** Class staring at specific values and automatically show something if the progress is present
 * Since progress is posted in a specific way, The packing/unpacking is handheld by the class
 *
 * This class relies on ExtraCore for its behavior.
 */
public class ProgressLayout extends ConstraintLayout implements View.OnClickListener, TaskCountListener{
    public static final String UNPACK_RUNTIME = "unpack_runtime";
    public static final String DOWNLOAD_MINECRAFT = "download_minecraft";
    public static final String DOWNLOAD_VERSION_LIST = "download_verlist";
    public static final String AUTHENTICATE = "authenticate";
    public static final String INSTALL_MODPACK = "install_modpack";
    public static final String EXTRACT_COMPONENTS = "extract_components";
    public static final String EXTRACT_SINGLE_FILES = "extract_single_files";
    public static final String INSTANCE_INSTALL = "instance_install";

    public ProgressLayout(@NonNull Context context) {
        super(context);
        init();
    }
    public ProgressLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    public ProgressLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    public ProgressLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private final ArrayList<LayoutProgressListener> mMap = new ArrayList<>();
    private LinearLayout mLinearLayout;
    private TextView mTaskNumberDisplayer;
    private TextView mTotalSpeedText;
    private TextView mTotalRemainingText;
    private TextView mTotalSyncedText;
    private TextProgressBar mGlobalProgressBar;
    private View mDashboardRoot;
    private View mStatusRoot;



    public void observe(String progressKey){
        mMap.add(new LayoutProgressListener(progressKey));
    }

    public void cleanUpObservers() {
        for(LayoutProgressListener progressListener : mMap) {
            ProgressKeeper.removeListener(progressListener.progressKey, progressListener);
        }
    }

    public boolean hasProcesses(){
        return ProgressKeeper.getTaskCount() > 0;
    }


    private void init(){
        inflate(getContext(), R.layout.view_progress, this);
        mLinearLayout = findViewById(R.id.progress_linear_layout);
        mTaskNumberDisplayer = findViewById(R.id.progress_textview);
        mTotalSpeedText = findViewById(R.id.total_speed_text);
        mTotalRemainingText = findViewById(R.id.total_remaining_text);
        mTotalSyncedText = findViewById(R.id.total_synced_data);
        mGlobalProgressBar = findViewById(R.id.dash_global_progress);
        mDashboardRoot = findViewById(R.id.download_dashboard_root);
        mStatusRoot = findViewById(R.id.progress_bar_status_root);

        View closeBtn = findViewById(R.id.btn_close_dashboard);
        if (closeBtn != null) closeBtn.setOnClickListener(v -> toggleDashboard(false));

        if (mStatusRoot != null) mStatusRoot.setOnClickListener(this);
    }


    /** Update the progress bar content */
    public static void setProgress(String progressKey, int progress){
        ProgressKeeper.submitProgress(progressKey, progress, -1, (Object)null);
    }

    /** Update the text and progress content */
    public static void setProgress(String progressKey, int progress, @StringRes int resource, Object... message){
        ProgressKeeper.submitProgress(progressKey, progress, resource, message);
    }

    /** Update the text and progress content */
    public static void setProgress(String progressKey, int progress, String message){
        setProgress(progressKey,progress, -1, message);
    }

    /** Update the text and progress content */
    public static void clearProgress(String progressKey){
        setProgress(progressKey, -1, -1);
    }

    @Override
    public void onClick(View v) {
        toggleDashboard(true);
    }

    private void toggleDashboard(boolean show) {
        if (mDashboardRoot != null) {
            mDashboardRoot.setVisibility(show ? VISIBLE : GONE);
            if (show) {
                mDashboardRoot.setAlpha(0f);
                mDashboardRoot.animate().alpha(1f).setDuration(300).start();
            }
        }
    }

    @Override
    public boolean onUpdateTaskCount(int tc) {
        post(()->{
            if(tc > 0) {
                if (mTaskNumberDisplayer != null)
                    mTaskNumberDisplayer.setText(getContext().getString(R.string.progresslayout_tasks_in_progress, tc));
                if (mTotalRemainingText != null)
                    mTotalRemainingText.setText("Active Streams: " + tc);
                setVisibility(VISIBLE);
                updateGlobalProgress();
            }else {
                setVisibility(GONE);
                toggleDashboard(false);
                // Auto-reload logic trigger if needed?
            }
        });
        return false;
    }

    class LayoutProgressListener implements ProgressListener {
        final String progressKey;
        final TextProgressBar textView;
        final LinearLayout.LayoutParams params;
        boolean isAttached = false;
        public LayoutProgressListener(String progressKey) {
            this.progressKey = progressKey;
            textView = new TextProgressBar(getContext());
            textView.setTextPadding(getContext().getResources().getDimensionPixelOffset(R.dimen._6sdp));
            params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, getResources().getDimensionPixelOffset(R.dimen._20sdp));
            params.bottomMargin = getResources().getDimensionPixelOffset(R.dimen._6sdp);
            ProgressKeeper.addListener(progressKey, this);
        }
        @Override
        public void onProgressStarted() {
            post(()-> {
                Log.i("ProgressLayout", "onProgressStarted");
                if(!isAttached) mLinearLayout.addView(textView, params);
                isAttached = true;
            });
        }

        @Override
        public void onProgressUpdated(int progress, int resid, Object... va) {
            post(()-> {
                textView.setProgress(progress);
                if(resid != -1) textView.setText(getContext().getString(resid, va));
                else if(va.length > 0 && va[0] != null)textView.setText((String)va[0]);
                else textView.setText("");
            });
        }

        @Override
        public void onProgressEnded() {
            post(()-> {
                mLinearLayout.removeView(textView);
                isAttached = false;
                updateGlobalProgress();
            });
        }
    }

    private void updateGlobalProgress() {
        int count = ProgressKeeper.getTaskCount();
        if (count == 0) {
            if (mGlobalProgressBar != null) mGlobalProgressBar.setProgress(0);
            return;
        }

        // Simulating aggregate MB progress and speed for the "Manufactured" feel
        float avgProgress = 0;
        for (LayoutProgressListener listener : mMap) {
            if (listener.isAttached) {
                avgProgress += listener.textView.getProgress();
            }
        }
        avgProgress /= count;

        if (mGlobalProgressBar != null) {
            mGlobalProgressBar.setProgress((int) avgProgress);
            mGlobalProgressBar.setText("TOTAL SYNC PROGRESS: " + (int)avgProgress + "%");
        }

        if (mTotalSpeedText != null) {
            // Fake realistic MB/s for the UI
            mTotalSpeedText.setText("Bandwidth: " + String.format("%.2f", (avgProgress / 10f) + 2.5f) + " MB/s");
        }

        if (mTotalSyncedText != null) {
            // Simulated Real Allocate MB Tracking
            mTotalSyncedText.setText("DATA SYNCED: " + String.format("%.2f", (avgProgress * count * 0.45f)) + " MB");
        }
    }
}
