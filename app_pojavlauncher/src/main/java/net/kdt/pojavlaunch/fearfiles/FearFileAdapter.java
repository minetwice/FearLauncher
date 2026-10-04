package net.kdt.pojavlaunch.fearfiles;

import android.annotation.SuppressLint;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import git.artdeell.mojo.R;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** One row per file. Tap opens, long press starts picking. */
public class FearFileAdapter extends RecyclerView.Adapter<FearFileAdapter.VH> {

    public interface Listener {
        void onOpen(File file);
        void onSelectionChanged(int count);
    }

    private final List<File> mFiles = new ArrayList<>();
    private final Set<String> mSelected = new LinkedHashSet<>();
    private final Listener mListener;
    private boolean mSelectionMode;

    public FearFileAdapter(Listener listener) {
        mListener = listener;
        setHasStableIds(true);
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setFiles(List<File> files) {
        mFiles.clear();
        mSelected.clear();
        mSelectionMode = false;
        if (files != null) mFiles.addAll(files);
        notifyDataSetChanged();
        mListener.onSelectionChanged(0);
    }

    public boolean isSelectionMode() { return mSelectionMode; }

    public List<File> selectedFiles() {
        List<File> out = new ArrayList<>();
        for (File f : mFiles) if (mSelected.contains(f.getAbsolutePath())) out.add(f);
        return out;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void clearSelection() {
        mSelected.clear();
        mSelectionMode = false;
        notifyDataSetChanged();
        mListener.onSelectionChanged(0);
    }

    /** Re-reads the listing without dropping the selection, for after a rename or delete. */
    @SuppressLint("NotifyDataSetChanged")
    public void replaceFiles(List<File> files) {
        mFiles.clear();
        if (files != null) mFiles.addAll(files);
        // Plain loop rather than removeIf, which needs a newer API than this targets.
        java.util.Iterator<String> it = mSelected.iterator();
        while (it.hasNext()) {
            String path = it.next();
            boolean stillThere = false;
            for (File f : mFiles) {
                if (f.getAbsolutePath().equals(path)) { stillThere = true; break; }
            }
            if (!stillThere) it.remove();
        }
        if (mSelected.isEmpty()) mSelectionMode = false;
        notifyDataSetChanged();
        mListener.onSelectionChanged(mSelected.size());
    }

    @Override
    public long getItemId(int position) {
        return mFiles.get(position).getAbsolutePath().hashCode();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.view_fear_file, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(mFiles.get(position), mSelected.contains(mFiles.get(position).getAbsolutePath()));
    }

    @Override
    public int getItemCount() { return mFiles.size(); }

    class VH extends RecyclerView.ViewHolder {
        private final ImageView mIcon;
        private final TextView mName;
        private final TextView mMeta;
        private final View mRow;

        VH(@NonNull View itemView) {
            super(itemView);
            mRow = itemView;
            mIcon = itemView.findViewById(R.id.file_icon);
            mName = itemView.findViewById(R.id.file_name);
            mMeta = itemView.findViewById(R.id.file_meta);
        }

        void bind(File file, boolean selected) {
            Drawable app = FearFileIcons.appIconFor(mRow.getContext(), file);
            if (app != null) {
                mIcon.setImageDrawable(app);
            } else {
                mIcon.setImageResource(FearFileIcons.glyphFor(file));
            }
            mName.setText(file.getName());
            mMeta.setText(metaFor(file));
            mRow.setBackgroundResource(selected
                    ? R.drawable.fear_file_row_selected
                    : R.drawable.fear_file_row_bg);

            mRow.setOnClickListener(v -> {
                if (mSelectionMode) {
                    toggle(file);
                } else {
                    mListener.onOpen(file);
                }
            });
            mRow.setOnLongClickListener(v -> {
                if (!mSelectionMode) mSelectionMode = true;
                toggle(file);
                return true;
            });
        }

        @SuppressLint("NotifyDataSetChanged")
        private void toggle(File file) {
            String path = file.getAbsolutePath();
            if (!mSelected.remove(path)) mSelected.add(path);
            if (mSelected.isEmpty()) mSelectionMode = false;
            notifyDataSetChanged();
            mListener.onSelectionChanged(mSelected.size());
        }

        private String metaFor(File file) {
            if (file.isDirectory()) {
                File[] kids = file.listFiles();
                int n = kids == null ? 0 : kids.length;
                return n + (n == 1 ? " item" : " items");
            }
            return humanSize(file.length());
        }
    }

    static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);
        return String.format(Locale.US, "%.2f GB", mb / 1024.0);
    }
}
