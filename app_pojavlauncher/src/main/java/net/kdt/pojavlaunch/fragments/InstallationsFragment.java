package net.kdt.pojavlaunch.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.instances.Instances;

public class InstallationsFragment extends Fragment {
    public static final String TAG = "InstallationsFragment";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_installations, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.findViewById(R.id.execute_jar_button).setOnClickListener(v ->
                Tools.launchModInstaller(requireContext(), null));

        view.findViewById(R.id.custom_controls_button).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), CustomControlsActivity.class)));

        view.findViewById(R.id.mods_button).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), SearchModFragment.class, SearchModFragment.TAG, null));

        view.findViewById(R.id.share_logs_button).setOnClickListener(v ->
                Tools.shareLog(requireContext()));

        view.findViewById(R.id.open_directory_button).setOnClickListener(v -> {
            if (Instances.loadSelectedInstance() != null) {
                Tools.openPath(requireContext(), Instances.loadSelectedInstance().getGameDirectory(), false);
            } else {
                Tools.openPath(requireContext(), Tools.DIR_GAME_HOME, false);
            }
        });
    }
}
