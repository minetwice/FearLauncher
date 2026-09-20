package net.kdt.pojavlauncher.recorder;

import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import git.artdeell.mojo.R;

/**
 * The Export Studio board: pick upscale (up to 4K), fps smoothness,
 * audio per-source mute, noise reduction / voice boost and the save
 * destination, then re-master the recording through RecordingExporter.
 */
public final class ExportBoardDialog {

    private static ExportBoardDialog sCurrent;

    private final Context mContext;
    private final Dialog mDialog;
    private final RecordingStore.Entry mEntry;

    private final int[] mResolution = {0, 0};  // 0,0 = same as source
    private final int[] mFps = {0};            // 0 = same
    private boolean mMuteInternal = false;
    private boolean mMuteMic = false;
    private boolean mNoiseReduction = false;
    private boolean mVoiceBoost = false;
    private boolean mSaveToDownloads = true;

    private Button mResSame, mRes1080, mRes1440, mRes4k;
    private Button mFpsSame, mFps30, mFps60;
    private Button mAudioInternal, mAudioMic, mNrBtn, mBoostBtn;
    private Button mDestDownloads, mDestPick;
    private ProgressBar mProgress;
    private ProgressBar mSpinner;
    private TextView mStatus;
    private Button mExportBtn;
    private RecordingExporter mExporter;
    private android.net.Uri mOutUri;
    private long mExportStartMs;

    private ExportBoardDialog(Context context, RecordingStore.Entry entry) {
        mContext = context;
        mEntry = entry;
        mDialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        mDialog.setContentView(R.layout.dialog_recorder_export);

        View close = mDialog.findViewById(R.id.rec_export_close);
        mResSame = mDialog.findViewById(R.id.rec_res_same);
        mRes1080 = mDialog.findViewById(R.id.rec_res_1080);
        mRes1440 = mDialog.findViewById(R.id.rec_res_1440);
        mRes4k = mDialog.findViewById(R.id.rec_res_4k);
        mFpsSame = mDialog.findViewById(R.id.rec_fps_same);
        mFps30 = mDialog.findViewById(R.id.rec_fps_30);
        mFps60 = mDialog.findViewById(R.id.rec_fps_60);
        mAudioInternal = mDialog.findViewById(R.id.rec_audio_internal);
        mAudioMic = mDialog.findViewById(R.id.rec_audio_mic);
        mNrBtn = mDialog.findViewById(R.id.rec_audio_nr);
        mBoostBtn = mDialog.findViewById(R.id.rec_audio_boost);
        mDestDownloads = mDialog.findViewById(R.id.rec_dest_downloads);
        mDestPick = mDialog.findViewById(R.id.rec_dest_pick);
        mProgress = mDialog.findViewById(R.id.rec_export_progress);
        mSpinner = mDialog.findViewById(R.id.rec_export_spinner);
        mStatus = mDialog.findViewById(R.id.rec_export_status);
        mExportBtn = mDialog.findViewById(R.id.rec_export_btn);
        Button cancelBtn = mDialog.findViewById(R.id.rec_export_cancel);

        mProgress.setVisibility(View.GONE);
        mSpinner.setVisibility(View.GONE);

        mResSame.setOnClickListener(v -> setResolution(0, 0));
        mRes1080.setOnClickListener(v -> setResolution(1920, 1080));
        mRes1440.setOnClickListener(v -> setResolution(2560, 1440));
        mRes4k.setOnClickListener(v -> setResolution(3840, 2160));
        mFpsSame.setOnClickListener(v -> { mFps[0] = 0; refreshChips(); });
        mFps30.setOnClickListener(v -> { mFps[0] = 30; refreshChips(); });
        mFps60.setOnClickListener(v -> { mFps[0] = 60; refreshChips(); });
        mAudioInternal.setOnClickListener(v -> { mMuteInternal = !mMuteInternal; refreshChips(); });
        mAudioMic.setOnClickListener(v -> { mMuteMic = !mMuteMic; refreshChips(); });
        mNrBtn.setOnClickListener(v -> { mNoiseReduction = !mNoiseReduction; refreshChips(); });
        mBoostBtn.setOnClickListener(v -> { mVoiceBoost = !mVoiceBoost; refreshChips(); });
        mDestDownloads.setOnClickListener(v -> { mSaveToDownloads = true; refreshChips(); });
        mDestPick.setOnClickListener(v -> { mSaveToDownloads = false; refreshChips(); });

        if (Build.VERSION.SDK_INT < 29) {
            mSaveToDownloads = false;
            mDestDownloads.setVisibility(View.GONE);
        }

        mExportBtn.setOnClickListener(v -> {
            v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
            net.kdt.pojavlaunch.SoundManager.playClick();
            startExport();
        });
        cancelBtn.setOnClickListener(v -> {
            if (mExporter != null) {
                mExporter.cancel();
                mStatus.setText("Cancelling…");
            } else {
                mDialog.dismiss();
            }
        });
        close.setOnClickListener(v -> {
            if (mExporter != null) mExporter.cancel();
            mDialog.dismiss();
        });

        mDialog.setOnDismissListener(d -> {
            if (sCurrent == ExportBoardDialog.this) sCurrent = null;
        });

        refreshChips();
    }

    public static void show(Context context, RecordingStore.Entry entry) {
        ExportBoardDialog board = new ExportBoardDialog(context, entry);
        sCurrent = board;
        board.mDialog.show();
    }

    static void onPicked(Uri uri) {
        ExportBoardDialog board = sCurrent;
        if (board != null && uri != null) {
            board.beginExport(uri);
        }
    }

    private void setResolution(int w, int h) {
        mResolution[0] = w;
        mResolution[1] = h;
        refreshChips();
    }

    private void refreshChips() {
        mResSame.setBackgroundResource(mResolution[0] == 0
                ? R.drawable.premium_button_bg : R.drawable.premium_glass_black_bg);
        mRes1080.setBackgroundResource(mResolution[0] == 1920
                ? R.drawable.premium_button_bg : R.drawable.premium_glass_black_bg);
        mRes1440.setBackgroundResource(mResolution[0] == 2560
                ? R.drawable.premium_button_bg : R.drawable.premium_glass_black_bg);
        mRes4k.setBackgroundResource(mResolution[0] == 3840
                ? R.drawable.premium_button_bg : R.drawable.premium_glass_black_bg);

        mFpsSame.setBackgroundResource(mFps[0] == 0
                ? R.drawable.premium_button_bg : R.drawable.premium_glass_black_bg);
        mFps30.setBackgroundResource(mFps[0] == 30
                ? R.drawable.premium_button_bg : R.drawable.premium_glass_black_bg);
        mFps60.setBackgroundResource(mFps[0] == 60
                ? R.drawable.premium_button_bg : R.drawable.premium_glass_black_bg);

        mAudioInternal.setBackgroundResource(!mMuteInternal
                ? R.drawable.premium_button_bg : R.drawable.premium_glass_black_bg);
        mAudioMic.setBackgroundResource(!mMuteMic
                ? R.drawable.premium_button_bg : R.drawable.premium_glass_black_bg);
        mNrBtn.setBackgroundResource(mNoiseReduction
                ? R.drawable.premium_button_bg : R.drawable.premium_glass_black_bg);
        mBoostBtn.setBackgroundResource(mVoiceBoost
                ? R.drawable.premium_button_bg : R.drawable.premium_glass_black_bg);

        mDestDownloads.setBackgroundResource(mSaveToDownloads
                ? R.drawable.premium_button_bg : R.drawable.premium_glass_black_bg);
        mDestPick.setBackgroundResource(!mSaveToDownloads
                ? R.drawable.premium_button_bg : R.drawable.premium_glass_black_bg);
    }

    private void lockUi(boolean locked) {
        mExportBtn.setEnabled(!locked);
        mExportBtn.setAlpha(locked ? 0.5f : 1f);
        mProgress.setVisibility(locked ? View.VISIBLE : View.GONE);
        mSpinner.setVisibility(locked ? View.VISIBLE : View.GONE);
    }

    private String suggestName() {
        String base = mEntry.name != null ? mEntry.name : "recording";
        if (base.endsWith(".mp4")) base = base.substring(0, base.length() - 4);
        return base + "_export.mp4";
    }

    private void startExport() {
        if (mSaveToDownloads && Build.VERSION.SDK_INT >= 29) {
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, suggestName());
                values.put(MediaStore.Downloads.MIME_TYPE, "video/mp4");
                values.put(MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/FearLauncher");
                Uri uri = mContext.getContentResolver()
                        .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new Exception("Could not create file in Downloads");
                beginExport(uri);
            } catch (Exception e) {
                Toast.makeText(mContext, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else {
            ExportPickActivity.pick(mContext, suggestName(), ExportBoardDialog::onPicked);
        }
    }

    private void beginExport(Uri outUri) {
        mOutUri = outUri;
        try {
            android.os.ParcelFileDescriptor pfd =
                    mContext.getContentResolver().openFileDescriptor(outUri, "rw");
            if (pfd == null) throw new Exception("Cannot open output file");
            runExport(pfd);
        } catch (Exception e) {
            Toast.makeText(mContext, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void runExport(android.os.ParcelFileDescriptor pfd) {
        lockUi(true);
        mExportStartMs = System.currentTimeMillis();
        mStatus.setText("Preparing…");
        RecordingExporter.Options options = new RecordingExporter.Options();
        options.targetWidth = mResolution[0];
        options.targetHeight = mResolution[1];
        options.targetFps = mFps[0];
        options.muteInternal = mMuteInternal;
        options.muteMic = mMuteMic;
        options.noiseReduction = mNoiseReduction;
        options.voiceBoost = mVoiceBoost;

        mExporter = new RecordingExporter(new RecordingExporter.Listener() {
            private final Handler handler = new Handler(Looper.getMainLooper());

            @Override
            public void onProgress(int percent, String stage) {
                handler.post(() -> {
                    mProgress.setProgress(percent);
                    long elapsed = System.currentTimeMillis() - mExportStartMs;
                    String eta = formatEta(percent > 0 ? elapsed * (100 - percent) / percent : -1L);
                    mStatus.setText(stage + "  " + percent + "%"
                            + (eta != null ? "  ·  " + eta + " left" : ""));
                });
            }

            @Override
            public void onDone() {
                handler.post(() -> {
                    Toast.makeText(mContext, "Export saved!", Toast.LENGTH_LONG).show();
                    mDialog.dismiss();
                });
            }

            @Override
            public void onError(String message) {
                handler.post(() -> {
                    // remove the broken/partial output file so no 0-byte junk is left behind
                    try {
                        if (mOutUri != null) mContext.getContentResolver().delete(mOutUri, null, null);
                    } catch (Exception ignored) {
                    }
                    mOutUri = null;
                    lockUi(false);
                    mStatus.setText(message);
                    Toast.makeText(mContext, "Export failed: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
        new Thread(() -> mExporter.export(mContext, mEntry, options, pfd), "FearExport").start();
    }

    private static String formatEta(long ms) {
        if (ms < 0) return null;
        long s = (ms + 999) / 1000;
        if (s < 60) return s + "s";
        return (s / 60) + "m " + (s % 60) + "s";
    }
}
