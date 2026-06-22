package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.mcVersionSpinner;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.utils.FileUtils;

import java.io.File;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private mcVersionSpinner mVersionSpinner;

    public MainMenuFragment() {
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // सिर्फ उन्हीं Views को Find करो जो Layout में मौजूद हैं
        Button playButton = view.findViewById(R.id.play_button);
        Button customControlButton = view.findViewById(R.id.custom_control_button);
        Button shareLogsButton = view.findViewById(R.id.share_logs_button);
        ImageButton editProfileButton = view.findViewById(R.id.edit_profile_button);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);

        // Play Button
        if (playButton != null) {
            playButton.setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));
        }

        // Custom Controls
        if (customControlButton != null) {
            customControlButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        }

        // Share Logs
        if (shareLogsButton != null) {
            shareLogsButton.setOnClickListener((v) -> Tools.shareLog(requireContext()));
        }

        // Edit Profile
        if (editProfileButton != null) {
            editProfileButton.setOnClickListener(v -> {
                if (mVersionSpinner != null) {
                    mVersionSpinner.openProfileEditor(requireActivity());
                }
            });
        }

        // Hamburger Menu Button (Popup)
        ImageButton hamburgerMenuButton = view.findViewById(R.id.hamburger_menu_button);
        if (hamburgerMenuButton != null) {
            hamburgerMenuButton.setOnClickListener(v -> {
                // Simple Popup Menu
                android.widget.PopupMenu popup = new android.widget.PopupMenu(requireContext(), v);
                popup.getMenu().add(0, 1, 0, "Execute .jar");
                popup.getMenu().add(0, 2, 1, "Open Game Directory");
                popup.setOnMenuItemClickListener(item -> {
                    if (item.getItemId() == 1) {
                        // Execute JAR
                        if (ProgressKeeper.getTaskCount() == 0) {
                            new net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension("jar")
                                .launch(null);
                        } else {
                            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
                        }
                        return true;
                    } else if (item.getItemId() == 2) {
                        // Open Game Directory
                        Instance instance = Instances.loadSelectedInstance();
                        if (instance == null) {
                            Toast.makeText(requireContext(), R.string.no_instance, Toast.LENGTH_LONG).show();
                            return true;
                        }
                        File gameDirectory = instance.getGameDirectory();
                        if (FileUtils.ensureDirectorySilently(gameDirectory)) {
                            Tools.openPath(requireContext(), gameDirectory, false);
                        } else {
                            Toast.makeText(requireContext(), R.string.gamedir_open_failed, Toast.LENGTH_LONG).show();
                        }
                        return true;
                    }
                    return false;
                });
                popup.show();
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
    }
}
