package net.kdt.pojavlaunch.recorder;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fearlauncher.fear.R;

import java.util.List;

/** Lists the recordings; the selected row is highlighted so the preview clearly matches. */
public class RecordingsAdapter extends RecyclerView.Adapter<RecordingsAdapter.Holder> {

    public interface OnPick {
        void onPick(RecordingEntry entry);
    }

    private final List<RecordingEntry> mEntries;
    private final OnPick mListener;
    private int mSelected = -1;

    public RecordingsAdapter(List<RecordingEntry> entries, OnPick listener) {
        mEntries = entries;
        mListener = listener;
    }

    public RecordingEntry selected() {
        return mSelected >= 0 && mSelected < mEntries.size() ? mEntries.get(mSelected) : null;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recording, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        RecordingEntry entry = mEntries.get(position);
        holder.name.setText(entry.displayName());
        holder.meta.setText(entry.readableDuration() + "  ·  " + entry.readableSize());
        holder.itemView.setSelected(position == mSelected);
        holder.itemView.setOnClickListener(v -> {
            int previous = mSelected;
            mSelected = holder.getAdapterPosition();
            if (previous >= 0) notifyItemChanged(previous);
            notifyItemChanged(mSelected);
            if (mListener != null) mListener.onPick(entry);
        });
    }

    @Override
    public int getItemCount() {
        return mEntries.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView meta;

        Holder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.recording_name);
            meta = itemView.findViewById(R.id.recording_meta);
        }
    }
}
