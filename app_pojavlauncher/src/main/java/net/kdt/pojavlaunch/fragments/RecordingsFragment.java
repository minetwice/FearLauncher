package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fearlauncher.fear.R;

import net.kdt.pojavlaunch.recorder.RecordingEntry;
import net.kdt.pojavlaunch.recorder.RecordingsAdapter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;

/**
 * The Dashboard: the recordings this device has captured, a preview player, and an export
 * action. Styled as a dark studio panel - a header, a preview stage, a transport strip and
 * a list - so it reads like the editing tools people already know.
 */
public class RecordingsFragment extends Fragment {

    public static final String TAG = "RECORDINGS_FRAGMENT";

    private VideoView mVideo;
    private TextView mPlaceholder;
    private ImageView mPlay;
    private SeekBar mSeek;
    private TextView mTime;
    private TextView mTotal;
    private TextView mEmpty;

    private RecordingsAdapter mAdapter;
    private RecordingEntry mSelected;

    private final Handler mTicker = new Handler(Looper.getMainLooper());
    private final Runnable mProgressTask = new Runnable() {
        @Override
        public void run() {
            if (mVideo != null && mVideo.isPlaying()) {
                int position = mVideo.getCurrentPosition();
                mSeek.setProgress(position);
                mTime.setText(format(position));
                mTicker.postDelayed(this, 400);
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_recordings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mVideo = view.findViewById(R.id.preview_video);
        mPlaceholder = view.findViewById(R.id.preview_placeholder);
        mPlay = view.findViewById(R.id.preview_play);
        mSeek = view.findViewById(R.id.preview_seek);
        mTime = view.findViewById(R.id.preview_time);
        mTotal = view.findViewById(R.id.preview_total);
        mEmpty = view.findViewById(R.id.recordings_empty);

        mPlay.setOnClickListener(v -> togglePlayback());
        mSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mVideo != null) {
                    mVideo.seekTo(progress);
                    mTime.setText(format(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        view.findViewById(R.id.export_button).setOnClickListener(v -> exportSelected());

        RecyclerView list = view.findViewById(R.id.recordings_list);
        list.setLayoutManager(new LinearLayoutManager(getContext()));

        List<RecordingEntry> entries = RecordingEntry.load(requireContext());
        mAdapter = new RecordingsAdapter(entries, this::onRecordingPicked);
        list.setAdapter(mAdapter);

        boolean isEmpty = entries.isEmpty();
        mEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        list.setVisibility(isEmpty ? View.GONE : View.VISIBLE);

        if (!isEmpty) onRecordingPicked(entries.get(0));
    }

    // ---- preview ------------------------------------------------------------

    private void onRecordingPicked(RecordingEntry entry) {
        mSelected = entry;
        if (mVideo == null) return;
        mPlaceholder.setVisibility(View.GONE);
        mVideo.setVisibility(View.VISIBLE);

        // The default controller is disabled - the transport strip below is ours.
        mVideo.setMediaController(null);
        mVideo.setVideoPath(entry.file.getAbsolutePath());
        mVideo.setOnPreparedListener(mp -> {
            mp.setLooping(false);
            int duration = mVideo.getDuration();
            mSeek.setMax(Math.max(duration, 1));
            mTotal.setText(format(duration));
            mTime.setText(format(0));
        });
        mVideo.setOnCompletionListener(mp -> {
            mPlay.setImageResource(R.drawable.ic_rec_play);
            mSeek.setProgress(0);
            mTime.setText(format(0));
        });
        mVideo.seekTo(1);
    }

    private void togglePlayback() {
        if (mVideo == null || mSelected == null) return;
        if (mVideo.isPlaying()) {
            mVideo.pause();
            mPlay.setImageResource(R.drawable.ic_rec_play);
        } else {
            mVideo.start();
            mPlay.setImageResource(R.drawable.ic_rec_pause);
            mTicker.post(mProgressTask);
        }
    }

    // ---- export -------------------------------------------------------------

    /**
     * Saves the selected recording somewhere the user can reach it. This is the plain copy
     * today; the re-encode step (pick fps / quality and render) lands on top of this same
     * entry point.
     */
    private void exportSelected() {
        if (mSelected == null) {
            Toast.makeText(getContext(), R.string.recordings_pick, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File outDir = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "FearRecorder");
            //noinspection ResultOfMethodCallIgnored
            outDir.mkdirs();
            File target = new File(outDir, mSelected.file.getName());
            copy(mSelected.file, target);
            Toast.makeText(getContext(),
                    getString(R.string.recordings_exported) + ": " + target.getAbsolutePath(),
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(getContext(), "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static void copy(File source, File target) throws Exception {
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }
    }

    private static String format(int millis) {
        int totalSeconds = Math.max(millis, 0) / 1000;
        return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mVideo != null && mVideo.isPlaying()) {
            mVideo.pause();
            mPlay.setImageResource(R.drawable.ic_rec_play);
        }
        mTicker.removeCallbacks(mProgressTask);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mTicker.removeCallbacks(mProgressTask);
        if (mVideo != null) {
            mVideo.stopPlayback();
            mVideo = null;
        }
    }
}
