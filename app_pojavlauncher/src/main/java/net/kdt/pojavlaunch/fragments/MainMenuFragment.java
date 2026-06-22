package net.kdt.pojavlaunch.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;

import com.kdt.mcgui.mcVersionSpinner;

public class MainMenuFragment extends Fragment {

    public static final String TAG = "MainMenuFragment";
    private mcVersionSpinner mVersionSpinner;

    public MainMenuFragment() {
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // Play button
        Button playButton = view.findViewById(R.id.play_button);

        // Menu buttons
        View customControlButton = view.findViewById(R.id.custom_control_button);
        View shareLogsButton = view.findViewById(R.id.share_logs_button);
        View newsButton = view.findViewById(R.id.news_button);
        View socialMediaButton = view.findViewById(R.id.social_media_button);
        View installJarButton = view.findViewById(R.id.install_jar_button);
        View openFilesButton = view.findViewById(R.id.open_files_button);

        // Bottom bar
        ImageButton editProfileButton = view.findViewById(R.id.edit_profile_button);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);

        // Hamburger menu — ID is hamburger_menu_icon in layout (ImageView)
        ImageView hamburgerMenuButton = view.findViewById(R.id.hamburger_menu_icon);

        // --- Click Listeners ---

        playButton.setOnClickListener(v -> {
            if (mVersionSpinner.getProfileAdapter() == null ||
                    mVersionSpinner.getProfileAdapter().getCount() == 0) {
                Toast.makeText(requireContext(), R.string.no_instance, Toast.LENGTH_LONG).show();
                return;
            }
            ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
        });

        customControlButton.setOnClickListener(v ->
                Tools.swapFragment(requireActivity(),
                        ControlButtonFragment.class,
                        ControlButtonFragment.TAG, null));

        shareLogsButton.setOnClickListener(v ->
                Tools.shareLog(requireContext()));

        editProfileButton.setOnClickListener(v ->
                Tools.swapFragment(requireActivity(),
                        ProfileEditorFragment.class,
                        ProfileEditorFragment.TAG, null));

        hamburgerMenuButton.setOnClickListener(v ->
                Tools.swapFragment(requireActivity(),
                        SettingsFragment.class,
                        SettingsFragment.TAG, null));

        newsButton.setOnClickListener(v -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://minecraft.wiki"));
            startActivity(browserIntent);
        });

        socialMediaButton.setOnClickListener(v -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse(getString(R.string.social_media_invite)));
            startActivity(browserIntent);
        });

        installJarButton.setOnClickListener(v -> {
            if (ExtraCore.getValue(ExtraConstants.LAUNCH_GAME) == Boolean.TRUE) {
                Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
                return;
            }
            if (mVersionSpinner.getProfileAdapter() == null ||
                    mVersionSpinner.getProfileAdapter().getCount() == 0) {
                Toast.makeText(requireContext(), R.string.no_instance, Toast.LENGTH_LONG).show();
                return;
            }
            Tools.installJarFile(requireActivity());
        });

        openFilesButton.setOnClickListener(v -> {
            boolean opened = Tools.openGameDirectory(requireContext());
            if (!opened) {
                Toast.makeText(requireContext(), R.string.gamedir_open_failed, Toast.LENGTH_LONG).show();
            }
        });

        mVersionSpinner.setOnClickListener(v ->
                Tools.swapFragment(requireActivity(),
                        ProfileEditorFragment.class,
                        ProfileEditorFragment.TAG, null));
    }
}
