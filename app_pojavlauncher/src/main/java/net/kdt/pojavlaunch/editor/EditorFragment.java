package net.kdt.pojavlaunch.editor;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.fearlauncher.fear.R;

import net.kdt.pojavlaunch.recorder.FfmpegExporter;

import java.io.File;
import java.util.Locale;

/**
 * The editing screen: preview on top, a clip timeline with trim handles below it, and a
 * row of tool tabs along the bottom - the same shape as the editing tools people already
 * use, so the recording can be trimmed and rendered without leaving the launcher.
 *
 * Trimming is what this first pass delivers; the tool tabs describe what each will do as
 * they are filled in.
 */
public class EditorFragment extends Fragment {

    public static final String TAG = "EDITOR_FRAGMENT";
    private static final String ARG_PATH = "path";

    private static final String[] TOOL_LABELS =
            {"VIDEO", "TEXT", "EFFECTS", "CAPTIONS", "STICKERS", "FORMAT"};
    private static final int[] TOOL_ICONS = {
            R.drawable.ic_px_record, R.drawable.ic_px_edit, R.drawable.ic_px_image_renderer,
            R.drawable.ic_px_book, R.drawable.ic_px_zap, R.drawable.ic_px_image};
    private static final String[] TOOL_HINTS = {
            "Trim, split and reorder the clip. Drag the red handles on the timeline to keep only the part you want.",
            "Add text over the video. Coming next.",
            "Apply effects and filters. Coming next.",
            "Add captions. Coming next.",
            "Place stickers. Coming next.",
            "Change the output resolution and frame rate.",
    };

    private static final String[] EXPORT_PRESETS = {
            "1080p  ·  60fps  ·  smooth",
            "1080p  ·  30fps",
            "720p  ·  60fps  ·  smooth",
            "720p  ·  30fps",
            "480p  ·  30fps",
    };

    private VideoView mVideo;
    private ImageView mPlay;
    private TextView mTime;
    private TextView mHint;
    private TimelineView mTimeline;
    private LinearLayout mTools;

    private File mFile;
    private int mDurationMs;
    private int mSelectedTool = 0;

    private final Handler mTicker = new Handler(Looper.getMainLooper());
    private final Runnable mProgressTask = new Runnable() {
        @Override
        public void run() {
            if (mVideo != null && mVideo.isPlaying()) {
                int position = mVideo.getCurrentPosition();
                mTime.setText(timeLabel(position));
                if (mDurationMs > 0) mTimeline.setProgress(position / (float) mDurationMs);
                mTicker.postDelayed(this, 200);
            }
        }
    };

    public static EditorFragment forFile(String absolutePath) {
        EditorFragment fragment = new EditorFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PATH, absolutePath);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_editor, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mVideo = view.findViewById(R.id.editor_video);
        mPlay = view.findViewById(R.id.editor_play);
        mTime = view.findViewById(R.id.editor_time);
        mHint = view.findViewById(R.id.editor_tool_hint);
        mTimeline = view.findViewById(R.id.editor_timeline);
        mTools = view.findViewById(R.id.editor_tools);

        Bundle args = getArguments();
        if (args == null || args.getString(ARG_PATH) == null) {
            Toast.makeText(getContext(), "No clip to edit", Toast.LENGTH_SHORT).show();
            return;
        }
        mFile = new File(args.getString(ARG_PATH));

        mPlay.setOnClickListener(v -> togglePlayback());
        view.findViewById(R.id.editor_back).setOnClickListener(v ->
                getParentFragmentManager()
                        .beginTransaction()
                        .replace(R.id.container_fragment,
                                new net.kdt.pojavlaunch.fragments.RecordingsFragment())
                        .commit());
        view.findViewById(R.id.editor_render).setOnClickListener(v -> chooseRenderPreset());

        mTimeline.setListener(new TimelineView.Listener() {
            @Override
            public void onScrubTo(float fraction) {
                if (mDurationMs > 0) mVideo.seekTo((int) (fraction * mDurationMs));
            }

            @Override
            public void onTrimChanged(float startFraction, float endFraction) {
                mHint.setText(String.format(Locale.US, "Keep %.0f%% – %.0f%% of the clip",
                        startFraction * 100, endFraction * 100));
            }
        });

        buildTools();

        mVideo.setMediaController(null);
        mVideo.setVideoPath(mFile.getAbsolutePath());
        mVideo.setOnPreparedListener(mp -> {
            mp.setLooping(false);
            mDurationMs = mVideo.getDuration();
            mTime.setText(timeLabel(0));
        });
        mVideo.setOnCompletionListener(mp -> {
            mPlay.setImageResource(R.drawable.ic_rec_play);
            mTicker.removeCallbacks(mProgressTask);
        });
        mVideo.seekTo(1);
    }

    // ---- tools --------------------------------------------------------------

    private void buildTools() {
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < TOOL_LABELS.length; i++) {
            View item = inflater.inflate(R.layout.item_editor_tool, mTools, false);
            ((ImageView) item.findViewById(R.id.tool_icon)).setImageResource(TOOL_ICONS[i]);
            ((TextView) item.findViewById(R.id.tool_label)).setText(TOOL_LABELS[i]);
            final int index = i;
            item.setOnClickListener(v -> selectTool(index));
            mTools.addView(item);
        }
        selectTool(0);
    }

    private void selectTool(int index) {
        mSelectedTool = index;
        mHint.setText(TOOL_HINTS[index]);
        for (int i = 0; i < mTools.getChildCount(); i++) {
            mTools.getChildAt(i).setBackgroundResource(
                    i == index ? R.drawable.bg_editor_tool_active : R.drawable.bg_editor_tool);
        }
    }

    // ---- playback -----------------------------------------------------------

    private void togglePlayback() {
        if (mVideo == null || mFile == null) return;
        if (mVideo.isPlaying()) {
            mVideo.pause();
            mPlay.setImageResource(R.drawable.ic_rec_play);
            mTicker.removeCallbacks(mProgressTask);
        } else {
            mVideo.start();
            mPlay.setImageResource(R.drawable.ic_rec_pause);
            mTicker.post(mProgressTask);
        }
    }

    // ---- render -------------------------------------------------------------

    /** Renders the trimmed clip, reusing the same presets the dashboard offers. */
    private void chooseRenderPreset() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.editor_render)
                .setItems(EXPORT_PRESETS, (dialog, which) -> render(preset(which)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static FfmpegExporter.Options preset(int which) {
        switch (which) {
            case 0: return new FfmpegExporter.Options(1080, 60, 12_000_000, true);
            case 1: return new FfmpegExporter.Options(1080, 30, 12_000_000, false);
            case 2: return new FfmpegExporter.Options(720, 60, 8_000_000, true);
            case 3: return new FfmpegExporter.Options(720, 30, 8_000_000, false);
            default: return new FfmpegExporter.Options(480, 30, 4_000_000, false);
        }
    }

    private void render(FfmpegExporter.Options options) {
        if (mDurationMs <= 0) {
            Toast.makeText(getContext(), "Clip not ready yet", Toast.LENGTH_SHORT).show();
            return;
        }
        // The timeline's fractions become real times here, so the render keeps only the
        // trimmed part rather than the whole capture.
        long startMs = (long) (mTimeline.getTrimStart() * mDurationMs);
        long endMs = (long) (mTimeline.getTrimEnd() * mDurationMs);
        FfmpegExporter.Options trimmed = options.withTrim(startMs, endMs);

        File outDir = new File(requireContext().getExternalFilesDir(null), "exports");
        // Listener has two callbacks, so this is an anonymous class rather than a lambda.
        FfmpegExporter.export(requireContext(), mFile, trimmed, outDir,
                new FfmpegExporter.Listener() {
                    @Override
                    public void onProgress(int percent) {
                        // Progress is not surfaced on this screen yet.
                    }

                    @Override
                    public void onFinished(boolean success, File output, String message) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            if (success) {
                                Toast.makeText(getContext(),
                                        getString(R.string.editor_rendered) + ": " + output.getName(),
                                        Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(getContext(),
                                        message != null ? message : "Render failed",
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                });
    }

    private String timeLabel(int positionMs) {
        return String.format(Locale.US, "%s / %s", clock(positionMs), clock(mDurationMs));
    }

    private static String clock(int millis) {
        int seconds = Math.max(millis, 0) / 1000;
        return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60);
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
