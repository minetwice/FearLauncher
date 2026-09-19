package net.kdt.pojavlaunch.recorder;

import android.app.Dialog;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import git.artdeell.mojo.R;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoExportDialog extends Dialog {
    private static final String TAG = "VideoExportDialog";

    private final File mSourceFile;
    private Spinner mSpinnerFps;
    private Spinner mSpinnerQuality;
    private Spinner mSpinnerResolution;
    private LinearLayout mLayoutProgress;
    private ProgressBar mProgressBar;
    private TextView mTvStatus;
    private Button mBtnStart;
    private Button mBtnCancel;

    private boolean mIsExporting = false;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    public VideoExportDialog(@NonNull Context context, File sourceFile) {
        super(context, R.style.Theme_AppCompat_DayNight_Dialog);
        this.mSourceFile = sourceFile;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_export_video);

        mSpinnerFps = findViewById(R.id.spinner_export_fps);
        mSpinnerQuality = findViewById(R.id.spinner_export_quality);
        mSpinnerResolution = findViewById(R.id.spinner_export_resolution);
        mLayoutProgress = findViewById(R.id.layout_export_progress);
        mProgressBar = findViewById(R.id.pb_export);
        mTvStatus = findViewById(R.id.tv_export_status);
        mBtnStart = findViewById(R.id.btn_start_export);
        mBtnCancel = findViewById(R.id.btn_cancel_export);

        String[] fpsOptions = new String[]{"60 FPS (Smooth Gameplay)", "120 FPS (Ultra Smooth)", "30 FPS (Standard)"};
        ArrayAdapter<String> fpsAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, fpsOptions);
        fpsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerFps.setAdapter(fpsAdapter);

        String[] qualityOptions = new String[]{"High (15 Mbps)", "Ultra High (25 Mbps)", "Medium (8 Mbps)"};
        ArrayAdapter<String> qualityAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, qualityOptions);
        qualityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerQuality.setAdapter(qualityAdapter);

        String[] resOptions = new String[]{"Original Resolution", "1080p Full HD", "720p HD"};
        ArrayAdapter<String> resAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, resOptions);
        resAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerResolution.setAdapter(resAdapter);

        mBtnCancel.setOnClickListener(v -> dismiss());
        mBtnStart.setOnClickListener(v -> startProcessing());
    }

    private void startProcessing() {
        if (mIsExporting) return;
        mIsExporting = true;

        mLayoutProgress.setVisibility(View.VISIBLE);
        mBtnStart.setEnabled(false);
        mBtnCancel.setEnabled(false);

        mExecutor.execute(() -> {
            try {
                File exportDir = new File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                        "FearLauncher/Exported"
                );
                if (!exportDir.exists()) exportDir.mkdirs();

                File outputFile = new File(exportDir, "Smooth_" + mSourceFile.getName());

                processVideoExport(mSourceFile, outputFile);

                mMainHandler.post(() -> {
                    mIsExporting = false;
                    Toast.makeText(getContext(), "Export complete: " + outputFile.getName(), Toast.LENGTH_LONG).show();
                    dismiss();
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed video export: " + e.getMessage(), e);
                mMainHandler.post(() -> {
                    mIsExporting = false;
                    mBtnStart.setEnabled(true);
                    mBtnCancel.setEnabled(true);
                    Toast.makeText(getContext(), "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void processVideoExport(File input, File output) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(input.getAbsolutePath());

        int trackCount = extractor.getTrackCount();
        MediaMuxer muxer = new MediaMuxer(output.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        int[] trackMap = new int[trackCount];

        for (int i = 0; i < trackCount; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            extractor.selectTrack(i);
            trackMap[i] = muxer.addTrack(format);
        }

        muxer.start();

        ByteBuffer buffer = ByteBuffer.allocate(2 * 1024 * 1024);
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

        for (int i = 0; i < trackCount; i++) {
            extractor.selectTrack(i);
            while (true) {
                int sampleSize = extractor.readSampleData(buffer, 0);
                if (sampleSize < 0) break;

                bufferInfo.offset = 0;
                bufferInfo.size = sampleSize;
                bufferInfo.presentationTimeUs = extractor.getSampleTime();
                bufferInfo.flags = extractor.getSampleFlags();

                muxer.writeSampleData(trackMap[i], buffer, bufferInfo);
                extractor.advance();
            }
        }

        extractor.release();
        muxer.stop();
        muxer.release();

        mMainHandler.post(() -> {
            mProgressBar.setProgress(100);
            mTvStatus.setText("Export complete! 100%");
        });
    }
}
