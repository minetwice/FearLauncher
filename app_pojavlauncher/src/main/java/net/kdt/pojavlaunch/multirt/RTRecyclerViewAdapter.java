package net.kdt.pojavlaunch.multirt;

import static net.kdt.pojavlaunch.PojavApplication.sExecutorService;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.Architecture;
import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.kdt.pojavlaunch.NewJREUtil;

public class RTRecyclerViewAdapter extends RecyclerView.Adapter<RTRecyclerViewAdapter.RTViewHolder> {

    private boolean mIsDeleting = false;
    private final List<Runtime> mRuntimes = new ArrayList<>();

    public RTRecyclerViewAdapter() {
        refreshRuntimes();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void refreshRuntimes() {
        mRuntimes.clear();
        List<Runtime> installed = MultiRTUtils.getRuntimes();
        mRuntimes.addAll(installed);
        for (NewJREUtil.InternalRuntime internal : NewJREUtil.InternalRuntime.values()) {
            boolean found = false;
            for (Runtime r : installed) {
                if (r.name.equals(internal.name)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                mRuntimes.add(new Runtime(internal.name));
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RTViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View recyclableView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_multirt_runtime,parent,false);
        return new RTViewHolder(recyclableView);
    }

    @Override
    public void onBindViewHolder(@NonNull RTViewHolder holder, int position) {
        holder.bindRuntime(mRuntimes.get(position),position);
    }

    @Override
    public int getItemCount() {
        return mRuntimes.size();
    }

    public boolean isDefaultRuntime(Runtime rt) {
        return LauncherPreferences.PREF_DEFAULT_RUNTIME.equals(rt.name);
    }

    @SuppressLint("NotifyDataSetChanged") //not a problem, given the typical size of the list
    public void setDefault(Runtime rt){
        LauncherPreferences.PREF_DEFAULT_RUNTIME = rt.name;
        LauncherPreferences.DEFAULT_PREF.edit().putString("defaultRuntime",LauncherPreferences.PREF_DEFAULT_RUNTIME).apply();
        notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged") //not a problem, given the typical size of the list
    public void setIsEditing(boolean isEditing) {
        mIsDeleting = isEditing;
        notifyDataSetChanged();
    }

    public boolean getIsEditing(){
        return mIsDeleting;
    }


    public class RTViewHolder extends RecyclerView.ViewHolder {
        final TextView mJavaVersionTextView;
        final TextView mFullJavaVersionTextView;
        final ColorStateList mDefaultColors;
        final Button mSetDefaultButton;
        final ImageButton mDeleteButton;
        final Context mContext;
        Runtime mCurrentRuntime;
        int mCurrentPosition;

        public RTViewHolder(View itemView) {
            super(itemView);
            mJavaVersionTextView = itemView.findViewById(R.id.multirt_view_java_version);
            mFullJavaVersionTextView = itemView.findViewById(R.id.multirt_view_java_version_full);
            mSetDefaultButton = itemView.findViewById(R.id.multirt_view_setdefaultbtn);
            mDeleteButton = itemView.findViewById(R.id.multirt_view_removebtn);

            mDefaultColors =  mFullJavaVersionTextView.getTextColors();
            mContext = itemView.getContext();

            setupOnClickListeners();
        }

        @SuppressLint("NotifyDataSetChanged") // same as all the other ones
        private void setupOnClickListeners(){
            mSetDefaultButton.setOnClickListener(v -> {
                if(mCurrentRuntime != null) {
                    setDefault(mCurrentRuntime);
                    RTRecyclerViewAdapter.this.notifyDataSetChanged();
                }
            });

            mDeleteButton.setOnClickListener(v -> {
                if (mCurrentRuntime == null) return;

                if(MultiRTUtils.getRuntimes().size() < 2) {
                    new AlertDialog.Builder(mContext)
                            .setTitle(R.string.global_error)
                            .setMessage(R.string.multirt_config_removeerror_last)
                            .setPositiveButton(android.R.string.ok,(adapter, which)->adapter.dismiss())
                            .show();
                    return;
                }

                sExecutorService.execute(() -> {
                    try {
                        MultiRTUtils.removeRuntimeNamed(mCurrentRuntime.name);
                        mDeleteButton.post(() -> {
                            if(getBindingAdapter() != null)
                                getBindingAdapter().notifyDataSetChanged();
                        });

                    } catch (IOException e) {
                        Tools.showError(itemView.getContext(), e);
                    }
                });

            });
        }

        private NewJREUtil.InternalRuntime getInternalRuntime(Runtime runtime) {
            for(NewJREUtil.InternalRuntime internal : NewJREUtil.InternalRuntime.values()) {
                if(internal.name.equals(runtime.name)) return internal;
            }
            return null;
        }

        public void bindRuntime(Runtime runtime, int pos) {
            mCurrentRuntime = runtime;
            mCurrentPosition = pos;
            if(runtime.versionString != null && Tools.DEVICE_ARCHITECTURE == Architecture.archAsInt(runtime.arch)) {
                mJavaVersionTextView.setText(runtime.name
                        .replace(".tar.xz", "")
                        .replace("-", " "));
                mFullJavaVersionTextView.setText(runtime.versionString);
                mFullJavaVersionTextView.setTextColor(mDefaultColors);

                updateButtonsVisibility();

                boolean defaultRuntime = isDefaultRuntime(runtime);
                mSetDefaultButton.setEnabled(!defaultRuntime);
                mSetDefaultButton.setText(defaultRuntime ? R.string.multirt_config_setdefault_already:R.string.multirt_config_setdefault);
                return;
            }

            // Problematic runtime moment, force propose deletion
            NewJREUtil.InternalRuntime internal = getInternalRuntime(runtime);
            if (runtime.versionString == null && internal != null) {
                mJavaVersionTextView.setText(runtime.name.replace("Internal-", "Java "));
                mFullJavaVersionTextView.setText(R.string.global_waiting);
                mFullJavaVersionTextView.setTextColor(mDefaultColors);
                mDeleteButton.setVisibility(View.GONE);
                mSetDefaultButton.setVisibility(View.VISIBLE);
                mSetDefaultButton.setText(mContext.getString(R.string.mcl_launch_downloading, internal.majorVersion + ""));
                mSetDefaultButton.setEnabled(true);
                mSetDefaultButton.setOnClickListener(v -> {
                    mSetDefaultButton.setEnabled(false);
                    mSetDefaultButton.setText(R.string.global_waiting);
                    sExecutorService.execute(() -> {
                        try {
                            NewJREUtil.checkAllInternalRuntimes(mContext.getAssets());
                            Tools.runOnUiThread(RTRecyclerViewAdapter.this::refreshRuntimes);
                        } catch (Exception e) {
                            Tools.showError(mContext, e);
                            Tools.runOnUiThread(() -> {
                                mSetDefaultButton.setEnabled(true);
                                mSetDefaultButton.setText(mContext.getString(R.string.mcl_launch_downloading, internal.majorVersion + ""));
                            });
                        }
                    });
                });
                return;
            }

            mDeleteButton.setVisibility(View.VISIBLE);
            if(runtime.versionString == null){
                mFullJavaVersionTextView.setText(R.string.multirt_runtime_corrupt);
            }else{
                mFullJavaVersionTextView.setText(mContext.getString(R.string.multirt_runtime_incompatiblearch, runtime.arch));
            }
            mJavaVersionTextView.setText(runtime.name);
            mFullJavaVersionTextView.setTextColor(Color.RED);
            mSetDefaultButton.setVisibility(View.GONE);
        }

        private void updateButtonsVisibility(){
            mSetDefaultButton.setVisibility(mIsDeleting ? View.GONE : View.VISIBLE);
            mDeleteButton.setVisibility(mIsDeleting ? View.VISIBLE : View.GONE);
        }
    }
}
