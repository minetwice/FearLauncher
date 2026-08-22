package net.kdt.pojavlaunch.prefs.screens;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;
import androidx.preference.SwitchPreferenceCompat;

import git.artdeell.mojo.R;

import net.kdt.pojavlaunch.plugins.LibraryPlugin;
import net.kdt.pojavlaunch.prefs.CustomSeekBarPreference;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.RendererCompatUtil;
import net.kdt.pojavlaunch.utils.RenderPluginManager;

import android.content.Intent;
import android.net.Uri;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Fragment for any settings video related
 */
public class LauncherPreferenceVideoFragment extends LauncherPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_video);
        int resolution = (int) (LauncherPreferences.PREF_SCALE_FACTOR * 100);

        CustomSeekBarPreference resolutionSeekbar = requirePreference("resolutionRatio",
                CustomSeekBarPreference.class);
        resolutionSeekbar.setSuffix(" %");

        // #724 bug fix
        if (resolution < 25) {
            resolutionSeekbar.setValue(100);
        } else {
            resolutionSeekbar.setValue(resolution);
        }

        // Sustained performance is only available since Nougat
        SwitchPreference sustainedPerfSwitch = requirePreference("sustainedPerformance",
                SwitchPreference.class);
        sustainedPerfSwitch.setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N);
        sustainedPerfSwitch.setChecked(LauncherPreferences.PREF_SUSTAINED_PERFORMANCE);

        requirePreference("alternate_surface", SwitchPreferenceCompat.class).setChecked(LauncherPreferences.PREF_USE_ALTERNATE_SURFACE);
        requirePreference("force_vsync", SwitchPreferenceCompat.class).setChecked(LauncherPreferences.PREF_FORCE_VSYNC);

        // Show ANGLE switch only if AnglePlugin is available
        LibraryPlugin angle = LibraryPlugin.discoverPlugin(getContext(), LibraryPlugin.ID_ANGLE_PLUGIN);
        SwitchPreferenceCompat angleSwitch = requirePreference("use_angle", SwitchPreferenceCompat.class);
        angleSwitch.setVisible(angle != null);
        angleSwitch.setChecked(LauncherPreferences.PREF_USE_ANGLE);

        ListPreference rendererListPreference = requirePreference("renderer",
                ListPreference.class);
        RendererCompatUtil.RenderersList renderersList = RendererCompatUtil.getCompatibleRenderers(getContext());
        rendererListPreference.setEntries(renderersList.rendererDisplayNames);
        rendererListPreference.setEntryValues(renderersList.rendererIds.toArray(new String[0]));

        // Custom renderer plugin path preference
        Preference customRendererPref = findPreference("customRendererPath");
        if (customRendererPref != null) {
            // Show/hide based on whether custom_inject renderer is selected
            customRendererPref.setVisible("custom_inject".equals(LauncherPreferences.PREF_RENDERER));

            // Display current path if set
            String currentPath = LauncherPreferences.PREF_CUSTOM_RENDERER_PATH;
            if (currentPath != null && !currentPath.isEmpty()) {
                customRendererPref.setSummary("Selected: " + new File(currentPath).getName());
            }

            customRendererPref.setOnPreferenceClickListener(preference -> {
                openPluginFileChooser();
                return true;
            });
        }

        computeVisibility();
    }

    // File picker for custom renderer .so plugin
    private void openPluginFileChooser() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        // Try to filter for .so files
        String[] mimeTypes = {"application/octet-stream", "*/*"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);

        try {
            pluginPickerLauncher.launch(new String[]{"*/*"});
        } catch (Exception e) {
            // Fallback: use ACTION_GET_CONTENT
            Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
            fallback.setType("*/*");
            try {
                pluginPickerLauncher.launch(new String[]{"*/*"});
            } catch (Exception e2) {
                android.util.Log.e("VideoPrefs", "No file picker available", e2);
            }
        }
    }

    private final ActivityResultLauncher<String[]> pluginPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments()) {
                @Override
                public void onActivityResult(java.util.List<android.net.Uri> uris) {
                    if (uris == null || uris.isEmpty()) return;

                    android.net.Uri uri = uris.get(0);
                    if (uri == null) return;

                    // Copy the .so file to app's internal storage
                    try {
                        File pluginDir = new File(requireContext().getFilesDir(), "render_plugins");
                        if (!pluginDir.exists()) pluginDir.mkdirs();

                        String fileName = "custom_renderer.so";
                        // Try to get original filename
                        android.database.Cursor cursor = requireContext().getContentResolver()
                                .query(uri, null, null, null, null);
                        if (cursor != null) {
                            int nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                            if (nameIdx >= 0 && cursor.moveToFirst()) {
                                String displayName = cursor.getString(nameIdx);
                                if (displayName != null && displayName.endsWith(".so")) {
                                    fileName = displayName;
                                }
                            }
                            cursor.close();
                        }

                        File pluginFile = new File(pluginDir, fileName);
                        try (InputStream is = requireContext().getContentResolver().openInputStream(uri);
                             FileOutputStream fos = new FileOutputStream(pluginFile)) {
                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            while ((bytesRead = is.read(buffer)) != -1) {
                                fos.write(buffer, 0, bytesRead);
                            }
                        }

                        // Set executable permission
                        pluginFile.setReadable(true, false);
                        pluginFile.setExecutable(true, false);

                        String pluginPath = pluginFile.getAbsolutePath();
                        LauncherPreferences.DEFAULT_PREF.edit()
                                .putString("customRendererPath", pluginPath).apply();
                        LauncherPreferences.PREF_CUSTOM_RENDERER_PATH = pluginPath;

                        // Update UI
                        Preference customRendererPref = findPreference("customRendererPath");
                        if (customRendererPref != null) {
                            customRendererPref.setSummary("Selected: " + fileName);
                        }

                        android.util.Log.i("VideoPrefs", "Custom renderer plugin saved: " + pluginPath);
                    } catch (Exception e) {
                        android.util.Log.e("VideoPrefs", "Failed to copy plugin file", e);
                    }
                }
            };

    @Override
    public void onResume() {
        super.onResume();
        Activity activity = getActivity();
        if(activity != null) {
            requirePreference("ignoreNotch").setVisible(LauncherPreferences.hasNotch(activity));
        }
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        if ("renderer".equals(preference.getKey())) {
            ListPreference lp = (ListPreference) preference;
            CharSequence[] entries = lp.getEntries();
            int selectedIndex = lp.findIndexOfValue(lp.getValue());

            ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(getContext(), R.layout.item_renderer_select, android.R.id.text1, entries) {
                @NonNull
                @Override
                public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                    View v = super.getView(position, convertView, parent);
                    View check = v.findViewById(R.id.item_check);
                    if (check != null) {
                        check.setVisibility(position == selectedIndex ? View.VISIBLE : View.INVISIBLE);
                    }
                    return v;
                }
            };

            new AlertDialog.Builder(requireContext(), R.style.FearAlertDialogTheme)
                    .setTitle(lp.getDialogTitle() != null ? lp.getDialogTitle() : lp.getTitle())
                    .setAdapter(adapter, (dialog, which) -> {
                        String value = lp.getEntryValues()[which].toString();
                        if (lp.callChangeListener(value)) {
                            lp.setValue(value);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } else {
            super.onDisplayPreferenceDialog(preference);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String s) {
        super.onSharedPreferenceChanged(p, s);
        computeVisibility();
    }

    private void computeVisibility(){
        requirePreference("force_vsync", SwitchPreferenceCompat.class)
                .setVisible(LauncherPreferences.PREF_USE_ALTERNATE_SURFACE);

        // Show custom renderer path only when custom_inject is selected
        Preference customRendererPref = findPreference("customRendererPath");
        if (customRendererPref != null) {
            customRendererPref.setVisible("custom_inject".equals(LauncherPreferences.PREF_RENDERER));
        }
    }
}
