package net.kdt.pojavlaunch.recorder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

import com.fearlauncher.fear.R;

/**
 * The little control pill that floats over the game while a recording is running: pause or
 * resume, stop, and two toggles for the microphone and the device's own audio.
 *
 * It lives inside the game activity's own view tree rather than as a system overlay, so it
 * needs no "draw over other apps" permission and disappears cleanly with the game. State
 * comes from the recorder's state broadcast, so the buttons always reflect reality even if
 * the recording was started from somewhere else.
 */
public class RecordingOverlay {

    private final Context mContext;
    private final ViewGroup mParent;
    private final LinearLayout mBar;
    // Not final: these are filled in by buildBar(), which the constructor calls.
    private ImageView mPauseButton;
    private ImageView mMicButton;
    private ImageView mSpeakerButton;

    private boolean mPaused;
    private boolean mMicOn;
    private boolean mInternalOn;
    private boolean mRegistered;

    private final BroadcastReceiver mStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(ScreenRecorderService.EXTRA_STATE,
                    ScreenRecorderService.STATE_RECORDING);
            mMicOn = intent.getBooleanExtra("mic", mMicOn);
            mInternalOn = intent.getBooleanExtra("internal", mInternalOn);
            if (state == ScreenRecorderService.STATE_STOPPED) {
                hide();
            } else {
                mPaused = state == ScreenRecorderService.STATE_PAUSED;
                show();
                refreshButtons();
            }
        }
    };

    public RecordingOverlay(Context context, ViewGroup parent) {
        mContext = context;
        mParent = parent;
        mBar = buildBar();
    }

    /** Adds the pill to the game view and starts listening for state. */
    public void attach() {
        if (mBar.getParent() == null) {
            mParent.addView(mBar);
        }
        if (!mRegistered) {
            IntentFilter filter = new IntentFilter(ScreenRecorderService.ACTION_STATE);
            if (Build.VERSION.SDK_INT >= 33) {
                mContext.registerReceiver(mStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                mContext.registerReceiver(mStateReceiver, filter);
            }
            mRegistered = true;
        }
    }

    public void detach() {
        if (mRegistered) {
            try {
                mContext.unregisterReceiver(mStateReceiver);
            } catch (Exception ignored) { }
            mRegistered = false;
        }
        if (mBar.getParent() != null) mParent.removeView(mBar);
    }

    public void show() {
        mBar.setVisibility(View.VISIBLE);
    }

    public void hide() {
        mBar.setVisibility(View.GONE);
    }

    // ---- ui -----------------------------------------------------------------

    private LinearLayout buildBar() {
        LinearLayout bar = new LinearLayout(mContext);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(ContextCompat.getDrawable(mContext, R.drawable.bg_rec_bar));

        int pad = dp(8);
        bar.setPadding(pad, pad, pad, pad);

        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        barParams.topMargin = dp(10);
        bar.setLayoutParams(barParams);
        bar.setVisibility(View.GONE);

        mPauseButton = addButton(bar, R.drawable.ic_rec_pause, R.string.recorder_pause,
                v -> send(ScreenRecorderService.ACTION_PAUSE));
        addButton(bar, R.drawable.ic_rec_stop, R.string.recorder_stop,
                v -> {
                    send(ScreenRecorderService.ACTION_STOP);
                    hide();
                });
        mMicButton = addButton(bar, R.drawable.ic_rec_mic, R.string.recorder_mic,
                v -> send(ScreenRecorderService.ACTION_TOGGLE_MIC));
        mSpeakerButton = addButton(bar, R.drawable.ic_rec_speaker, R.string.recorder_speaker,
                v -> send(ScreenRecorderService.ACTION_TOGGLE_INTERNAL));

        refreshButtons();
        return bar;
    }

    private ImageView addButton(LinearLayout bar, int iconRes, int contentDescRes,
                                View.OnClickListener listener) {
        ImageView button = new ImageView(mContext);
        int size = dp(38);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.setMargins(dp(4), 0, dp(4), 0);
        button.setLayoutParams(params);
        button.setImageResource(iconRes);
        button.setContentDescription(mContext.getString(contentDescRes));
        button.setBackground(ContextCompat.getDrawable(mContext, R.drawable.bg_rec_button));
        button.setPadding(dp(9), dp(9), dp(9), dp(9));
        button.setClickable(true);
        button.setOnClickListener(listener);
        bar.addView(button);
        return button;
    }

    private void refreshButtons() {
        mPauseButton.setImageResource(mPaused ? R.drawable.ic_rec_play : R.drawable.ic_rec_pause);
        mPauseButton.setOnClickListener(mPaused
                ? v -> send(ScreenRecorderService.ACTION_RESUME)
                : v -> send(ScreenRecorderService.ACTION_PAUSE));
        tintActive(mMicButton, mMicOn);
        tintActive(mSpeakerButton, mInternalOn);
    }

    /** Lit-up red when the source is capturing, dim when muted. */
    private void tintActive(ImageView button, boolean active) {
        Drawable background = active
                ? ContextCompat.getDrawable(mContext, R.drawable.bg_rec_button_active)
                : ContextCompat.getDrawable(mContext, R.drawable.bg_rec_button);
        button.setBackground(background);
        Drawable icon = button.getDrawable();
        if (icon != null) {
            icon.mutate().setColorFilter(active ? 0xFFFFFFFF : 0xB3FFFFFF,
                    PorterDuff.Mode.SRC_IN);
        }
    }

    private void send(String action) {
        Intent intent = new Intent(mContext, ScreenRecorderService.class).setAction(action);
        mContext.startService(intent);
        if (ScreenRecorderService.ACTION_PAUSE.equals(action)) mPaused = true;
        if (ScreenRecorderService.ACTION_RESUME.equals(action)) mPaused = false;
        refreshButtons();
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                mContext.getResources().getDisplayMetrics());
    }
}
