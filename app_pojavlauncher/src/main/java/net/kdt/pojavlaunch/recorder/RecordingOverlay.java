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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
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
 *
 * The whole pill can be dragged anywhere on screen, and the chevron folds it down to a
 * small circle so it can be parked out of the way of the on-screen keyboard.
 */
public class RecordingOverlay {

    private final Context mContext;
    private final ViewGroup mParent;
    private final FrameLayout mRoot;
    private final LinearLayout mBar;
    private final ImageView mCollapsed;
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
        mCollapsed = buildCollapsed();
        mRoot = new FrameLayout(context);
        mRoot.addView(mBar);
        mRoot.addView(mCollapsed);
        mRoot.setVisibility(View.GONE);
        // Never take focus: the game's own text field must keep the keyboard and its
        // action button, and a focusable overlay would fight it for them.
        mRoot.setFocusable(false);
        mRoot.setFocusableInTouchMode(false);
    }

    /** Adds the pill to the game view and starts listening for state. */
    public void attach() {
        if (mRoot.getParent() == null) {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            params.topMargin = dp(10);
            mParent.addView(mRoot, params);
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
        if (mRoot.getParent() != null) mParent.removeView(mRoot);
    }

    public void show() {
        mRoot.setVisibility(View.VISIBLE);
    }

    public void hide() {
        mRoot.setVisibility(View.GONE);
    }

    // ---- ui -----------------------------------------------------------------

    private LinearLayout buildBar() {
        LinearLayout bar = new LinearLayout(mContext);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(ContextCompat.getDrawable(mContext, R.drawable.bg_rec_bar));
        int pad = dp(8);
        bar.setPadding(pad, pad, pad, pad);

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
        // The fold-away arrow sits last, so it reads as "shrink me".
        addButton(bar, R.drawable.ic_fear_chevron, R.string.recorder_collapse, v -> collapse());

        refreshButtons();
        return bar;
    }

    /** The folded-down form: one small circle that re-opens the pill when tapped. */
    private ImageView buildCollapsed() {
        ImageView circle = new ImageView(mContext);
        int size = dp(44);
        circle.setLayoutParams(new FrameLayout.LayoutParams(size, size));
        circle.setImageResource(R.drawable.ic_px_record);
        circle.setContentDescription(mContext.getString(R.string.recorder_expand));
        circle.setBackground(ContextCompat.getDrawable(mContext, R.drawable.bg_rec_circle));
        circle.setPadding(dp(11), dp(11), dp(11), dp(11));
        circle.setVisibility(View.GONE);
        installDrag(circle, this::expand);
        return circle;
    }

    private void collapse() {
        mBar.setVisibility(View.GONE);
        mCollapsed.setVisibility(View.VISIBLE);
    }

    private void expand() {
        mCollapsed.setVisibility(View.GONE);
        mBar.setVisibility(View.VISIBLE);
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
        // Drag wins over click: a small movement moves the pill, a clean tap presses the
        // button. A plain click listener would swallow the drag.
        installDrag(button, () -> listener.onClick(button));
        bar.addView(button);
        return button;
    }

    /**
     * Makes a view draggable. A tap with no movement fires the tap action; anything past
     * the touch slop drags the whole pill instead, so the buttons stay usable while the
     * pill can be parked wherever it is not in the way.
     */
    private void installDrag(final View handle, final Runnable onTap) {
        final int slop = ViewConfiguration.get(mContext).getScaledTouchSlop();
        handle.setOnTouchListener(new View.OnTouchListener() {
            private float downRawX, downRawY, startX, startY;
            private boolean dragging;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        startX = mRoot.getX();
                        startY = mRoot.getY();
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downRawX;
                        float dy = event.getRawY() - downRawY;
                        if (!dragging && Math.hypot(dx, dy) > slop) dragging = true;
                        if (dragging) moveTo(startX + dx, startY + dy);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (!dragging && onTap != null) onTap.run();
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    /** Places the pill, keeping it fully inside the game view. */
    private void moveTo(float x, float y) {
        float maxX = Math.max(0, mParent.getWidth() - mRoot.getWidth());
        float maxY = Math.max(0, mParent.getHeight() - mRoot.getHeight());
        mRoot.setX(Math.max(0f, Math.min(x, maxX)));
        mRoot.setY(Math.max(0f, Math.min(y, maxY)));
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
