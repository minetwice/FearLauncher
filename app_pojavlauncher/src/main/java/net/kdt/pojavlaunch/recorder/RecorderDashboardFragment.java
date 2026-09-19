package net.kdt.pojavlaunch.recorder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import git.artdeell.mojo.R;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RecorderDashboardFragment extends Fragment {
    public static final String TAG = "RecorderDashboardFragment";

    private RecyclerView mRecyclerView;
    private View mTvEmpty;
    private Button mBtnRecord;
    private VideoAdapter mAdapter;
    private final List<File> mVideoFiles = new ArrayList<>();

    private final ExecutorService mExecutor = Executors.newFixedThreadPool(2);
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private androidx.activity.result.ActivityResultLauncher<Intent> mMediaProjectionLauncher;

    private final BroadcastReceiver mRefreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadVideos();
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMediaProjectionLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Context context = getContext();
                    if (context == null) return;
                    if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                        Intent recorderIntent = new Intent(context, RecorderService.class);
                        recorderIntent.setAction(RecorderService.ACTION_START);
                        recorderIntent.putExtra(RecorderService.EXTRA_RESULT_CODE, result.getResultCode());
                        recorderIntent.putExtra(RecorderService.EXTRA_DATA, result.getData());
                        recorderIntent.putExtra(RecorderService.EXTRA_FPS, 60);
                        recorderIntent.putExtra(RecorderService.EXTRA_BITRATE, 12_000_000);
                        recorderIntent.putExtra(RecorderService.EXTRA_ENABLE_AUDIO, true);
                        recorderIntent.putExtra(RecorderService.EXTRA_NOISE_REDUCTION, true);
                        androidx.core.content.ContextCompat.startForegroundService(context, recorderIntent);
                        updateRecordButtonState();
                    } else {
                        Toast.makeText(context, "Screen recording permission denied", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_recorder_dashboard, container, false);

        mRecyclerView = view.findViewById(R.id.rv_recordings);
        mTvEmpty = view.findViewById(R.id.tv_empty_recordings);
        mBtnRecord = view.findViewById(R.id.btn_record_new);

        mRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        mAdapter = new VideoAdapter();
        mRecyclerView.setAdapter(mAdapter);

        mBtnRecord.setOnClickListener(v -> toggleRecording());

        updateRecordButtonState();
        loadVideos();
        return view;
    }

    private void updateRecordButtonState() {
        if (mBtnRecord == null) return;
        if (RecorderService.isRecording()) {
            mBtnRecord.setText("■ STOP RECORDING");
        } else {
            mBtnRecord.setText("● START RECORDING");
        }
    }

    private void toggleRecording() {
        Context context = getContext();
        if (context == null) return;

        if (RecorderService.isRecording()) {
            Intent stopIntent = new Intent(context, RecorderService.class);
            stopIntent.setAction(RecorderService.ACTION_STOP);
            context.startService(stopIntent);
            updateRecordButtonState();
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(context)) {
                Toast.makeText(context, "Please grant Overlay permission to display recording controls", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + context.getPackageName()));
                startActivity(intent);
                return;
            }
            android.media.projection.MediaProjectionManager projectionManager =
                    (android.media.projection.MediaProjectionManager) context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            if (projectionManager != null) {
                mMediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent());
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getContext() != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getContext().registerReceiver(mRefreshReceiver, new IntentFilter("net.kdt.pojavlaunch.recorder.REFRESH_DASHBOARD"), Context.RECEIVER_EXPORTED);
            } else {
                getContext().registerReceiver(mRefreshReceiver, new IntentFilter("net.kdt.pojavlaunch.recorder.REFRESH_DASHBOARD"));
            }
        }
        loadVideos();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (getContext() != null) {
            try {
                getContext().unregisterReceiver(mRefreshReceiver);
            } catch (Exception ignored) {}
        }
    }

    private void loadVideos() {
        mExecutor.execute(() -> {
            List<File> files = new ArrayList<>();

            File folder = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "FearLauncher"
            );
            if (folder.exists() && folder.isDirectory()) {
                File[] fileList = folder.listFiles();
                if (fileList != null) {
                    for (File f : fileList) {
                        if (f.isFile() && f.getName().endsWith(".mp4")) {
                            files.add(f);
                        }
                    }
                }
            }

            File exportFolder = new File(folder, "Exported");
            if (exportFolder.exists() && exportFolder.isDirectory()) {
                File[] exportedList = exportFolder.listFiles();
                if (exportedList != null) {
                    for (File f : exportedList) {
                        if (f.isFile() && f.getName().endsWith(".mp4")) {
                            files.add(f);
                        }
                    }
                }
            }

            Collections.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

            mMainHandler.post(() -> {
                mVideoFiles.clear();
                mVideoFiles.addAll(files);

                if (mVideoFiles.isEmpty()) {
                    if (mTvEmpty != null) mTvEmpty.setVisibility(View.VISIBLE);
                    mRecyclerView.setVisibility(View.GONE);
                } else {
                    if (mTvEmpty != null) mTvEmpty.setVisibility(View.GONE);
                    mRecyclerView.setVisibility(View.VISIBLE);
                    mAdapter.notifyDataSetChanged();
                }
            });
        });
    }

    private class VideoAdapter extends RecyclerView.Adapter<VideoViewHolder> {
        @NonNull
        @Override
        public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recorder_video, parent, false);
            return new VideoViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
            File file = mVideoFiles.get(position);
            holder.tvName.setText(file.getName());

            String sizeMb = String.format(Locale.US, "%.1f MB", file.length() / (1024.0 * 1024.0));
            String dateStr = new SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(new Date(file.lastModified()));
            holder.tvDetails.setText(dateStr + " | " + sizeMb);

            mExecutor.execute(() -> {
                Bitmap thumbnail = null;
                try {
                    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                    retriever.setDataSource(file.getAbsolutePath());
                    thumbnail = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    retriever.release();
                } catch (Exception ignored) {}

                final Bitmap bmp = thumbnail;
                mMainHandler.post(() -> {
                    if (bmp != null) {
                        holder.ivThumbnail.setImageBitmap(bmp);
                    } else {
                        holder.ivThumbnail.setImageResource(R.drawable.ic_px_image);
                    }
                });
            });

            holder.btnPlay.setOnClickListener(v -> {
                Context context = getContext();
                if (context == null) return;
                try {
                    Uri videoUri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file);
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(videoUri, "video/mp4");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    context.startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(context, "No video player application found", Toast.LENGTH_SHORT).show();
                }
            });

            holder.btnExport.setOnClickListener(v -> {
                if (getContext() != null) {
                    VideoExportDialog dialog = new VideoExportDialog(getContext(), file);
                    dialog.show();
                }
            });
        }

        @Override
        public int getItemCount() {
            return mVideoFiles.size();
        }
    }

    private static class VideoViewHolder extends RecyclerView.ViewHolder {
        ImageView ivThumbnail;
        TextView tvName;
        TextView tvDetails;
        Button btnPlay;
        Button btnExport;

        public VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumbnail = itemView.findViewById(R.id.iv_video_thumbnail);
            tvName = itemView.findViewById(R.id.tv_video_name);
            tvDetails = itemView.findViewById(R.id.tv_video_details);
            btnPlay = itemView.findViewById(R.id.btn_play_video);
            btnExport = itemView.findViewById(R.id.btn_export_video);
        }
    }
}
