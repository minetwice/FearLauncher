package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceVideoFragment;

public class CustomSettingsFragment extends Fragment {

    public static final String TAG = "CustomSettingsFragment";

    private RecyclerView recyclerView;
    private SettingsAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_custom_settings, container, false);

        Toolbar toolbar = view.findViewById(R.id.settings_toolbar);
        toolbar.setNavigationOnClickListener(v -> getParentFragmentManager().popBackStack());

        recyclerView = view.findViewById(R.id.settings_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        List<SettingItem> items = buildSettingsList();
        adapter = new SettingsAdapter(items);
        recyclerView.setAdapter(adapter);

        return view;
    }

    private List<SettingItem> buildSettingsList() {
        List<SettingItem> list = new ArrayList<>();

        // Category: Video
        list.add(new SettingItem.Category("Video & Performance"));
        list.add(new SettingItem.Normal(
                "Video",
                "Resolution, renderer, VSync",
                R.drawable.ic_px_image,
                () -> Tools.swapFragment(requireActivity(), LauncherPreferenceVideoFragment.class, "video", null)
        ));

        // Category: Controls
        list.add(new SettingItem.Category("Game Controls"));
        list.add(new SettingItem.Normal(
                "Controls",
                "Gestures, buttons, mouse",
                R.drawable.ic_px_gamepad,
                () -> Tools.swapFragment(requireActivity(), LauncherPreferenceControlFragment.class, "controls", null)
        ));

        // Category: Java
        list.add(new SettingItem.Category("Java Engine"));
        list.add(new SettingItem.Normal(
                "Java",
                "Runtime, memory, JVM args",
                R.drawable.ic_px_java,
                () -> Tools.swapFragment(requireActivity(), LauncherPreferenceJavaFragment.class, "java", null)
        ));

        // Category: Tools
        list.add(new SettingItem.Category("Advanced Tools"));
        list.add(new SettingItem.Normal(
                "Tools",
                "Download source, file checks",
                R.drawable.ic_px_alt_sliders,
                () -> Tools.swapFragment(requireActivity(), LauncherPreferenceMiscellaneousFragment.class, "misc", null)
        ));

        // Category: Experiments
        list.add(new SettingItem.Category("Experimental Labs"));
        list.add(new SettingItem.Normal(
                "Experiments",
                "Bleeding-edge features",
                R.drawable.ic_px_experiment,
                () -> Tools.swapFragment(requireActivity(), LauncherPreferenceExperimentalFragment.class, "experiment", null)
        ));

        // Switch: Force English
        list.add(new SettingItem.SwitchItem(
                "Force English",
                "See original strings, restart required",
                R.drawable.ic_px_translate,
                LauncherPreferences.DEFAULT_PREF.getBoolean("force_english", false),
                (checked) -> LauncherPreferences.DEFAULT_PREF.edit().putBoolean("force_english", checked).apply()
        ));

        // Slider: Memory allocation (example)
        int currentMem = LauncherPreferences.DEFAULT_PREF.getInt("allocation", 1024);
        list.add(new SettingItem.SliderItem(
                "Memory Allocation",
                "Controls how much RAM Minecraft uses",
                R.drawable.ic_px_ram,
                currentMem,
                256, 4096, 64,
                (progress) -> {
                    LauncherPreferences.DEFAULT_PREF.edit().putInt("allocation", progress).apply();
                },
                (progress) -> progress + " MB"
        ));

        // Slider: Resolution (example)
        int currentRes = LauncherPreferences.DEFAULT_PREF.getInt("resolutionRatio", 100);
        list.add(new SettingItem.SliderItem(
                "Resolution Scaler",
                "Decrease resolution for performance",
                R.drawable.ic_px_resolution,
                currentRes,
                25, 100, 5,
                (progress) -> {
                    LauncherPreferences.DEFAULT_PREF.edit().putInt("resolutionRatio", progress).apply();
                },
                (progress) -> progress + "%"
        ));

        // Notification permission (just a normal item with click)
        list.add(new SettingItem.Normal(
                "Notification Permission",
                "Request notification permission",
                R.drawable.ic_px_bell,
                () -> {
                    if (getActivity() instanceof LauncherActivity) {
                        ((LauncherActivity) getActivity()).askForPermission(33, Manifest.permission.POST_NOTIFICATIONS);
                    }
                }
        ));

        return list;
    }

    // ================== ADAPTER & VIEW HOLDER ==================

    private class SettingsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int TYPE_CATEGORY = 0;
        private static final int TYPE_NORMAL = 1;
        private static final int TYPE_SWITCH = 2;
        private static final int TYPE_SLIDER = 3;

        private List<SettingItem> items;

        SettingsAdapter(List<SettingItem> items) {
            this.items = items;
        }

        @Override
        public int getItemViewType(int position) {
            SettingItem item = items.get(position);
            if (item instanceof SettingItem.Category) return TYPE_CATEGORY;
            if (item instanceof SettingItem.SwitchItem) return TYPE_SWITCH;
            if (item instanceof SettingItem.SliderItem) return TYPE_SLIDER;
            return TYPE_NORMAL;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_CATEGORY) {
                View v = inflater.inflate(android.R.layout.simple_list_item_1, parent, false);
                // We'll customize in onBind
                return new CategoryViewHolder(v);
            } else if (viewType == TYPE_SWITCH) {
                View v = inflater.inflate(R.layout.item_setting_switch, parent, false);
                return new SwitchViewHolder(v);
            } else if (viewType == TYPE_SLIDER) {
                View v = inflater.inflate(R.layout.item_setting_slider, parent, false);
                return new SliderViewHolder(v);
            } else {
                View v = inflater.inflate(R.layout.item_setting_normal, parent, false);
                return new NormalViewHolder(v);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            SettingItem item = items.get(position);
            if (holder instanceof CategoryViewHolder) {
                CategoryViewHolder vh = (CategoryViewHolder) holder;
                vh.textView.setText(((SettingItem.Category) item).title);
                vh.textView.setTextColor(getResources().getColor(R.color.silver_light));
                vh.textView.setShadowLayer(4, 0, 2, 0x80000000);
            } else if (holder instanceof NormalViewHolder) {
                NormalViewHolder vh = (NormalViewHolder) holder;
                SettingItem.Normal normal = (SettingItem.Normal) item;
                vh.icon.setImageResource(normal.iconRes);
                vh.title.setText(normal.title);
                vh.summary.setText(normal.summary);
                vh.itemView.setOnClickListener(v -> normal.onClick.run());
            } else if (holder instanceof SwitchViewHolder) {
                SwitchViewHolder vh = (SwitchViewHolder) holder;
                SettingItem.SwitchItem switchItem = (SettingItem.SwitchItem) item;
                vh.icon.setImageResource(switchItem.iconRes);
                vh.title.setText(switchItem.title);
                vh.summary.setText(switchItem.summary);
                vh.switchView.setChecked(switchItem.checked);
                vh.switchView.setOnCheckedChangeListener(null); // clear previous
                vh.switchView.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    switchItem.onToggle.accept(isChecked);
                });
            } else if (holder instanceof SliderViewHolder) {
                SliderViewHolder vh = (SliderViewHolder) holder;
                SettingItem.SliderItem sliderItem = (SettingItem.SliderItem) item;
                vh.icon.setImageResource(sliderItem.iconRes);
                vh.title.setText(sliderItem.title);
                vh.summary.setText(sliderItem.summary);
                vh.seekBar.setMax(sliderItem.max);
                vh.seekBar.setProgress(sliderItem.currentValue);
                vh.valueText.setText(sliderItem.formatter.format(sliderItem.currentValue));
                // Animate when seekbar changes
                vh.seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (fromUser) {
                            // Animate the text update
                            vh.valueText.animate().alpha(0.3f).setDuration(100).withEndAction(() -> {
                                vh.valueText.setText(sliderItem.formatter.format(progress));
                                vh.valueText.animate().alpha(1f).setDuration(100).start();
                            });
                            sliderItem.onValueChanged.accept(progress);
                            // You could also animate the seekbar thumb if needed
                        }
                    }
                    @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                    @Override public void onStopTrackingTouch(SeekBar seekBar) {}
                });
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        // ---------- ViewHolders ----------
        class CategoryViewHolder extends RecyclerView.ViewHolder {
            TextView textView;
            CategoryViewHolder(View v) {
                super(v);
                textView = (TextView) v;
                textView.setPadding(32, 16, 16, 16);
                textView.setTextSize(18);
                textView.setTypeface(null, android.graphics.Typeface.BOLD);
            }
        }

        class NormalViewHolder extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView title, summary;
            NormalViewHolder(View v) {
                super(v);
                icon = v.findViewById(R.id.item_icon);
                title = v.findViewById(R.id.item_title);
                summary = v.findViewById(R.id.item_summary);
            }
        }

        class SwitchViewHolder extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView title, summary;
            SwitchCompat switchView;
            SwitchViewHolder(View v) {
                super(v);
                icon = v.findViewById(R.id.item_icon);
                title = v.findViewById(R.id.item_title);
                summary = v.findViewById(R.id.item_summary);
                switchView = v.findViewById(R.id.item_switch);
            }
        }

        class SliderViewHolder extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView title, summary, valueText;
            SeekBar seekBar;
            SliderViewHolder(View v) {
                super(v);
                icon = v.findViewById(R.id.item_icon);
                title = v.findViewById(R.id.item_title);
                summary = v.findViewById(R.id.item_summary);
                valueText = v.findViewById(R.id.item_value);
                seekBar = v.findViewById(R.id.item_seekbar);
            }
        }
    }

    // ---------- Data classes ----------
    abstract static class SettingItem {}
    static class Category extends SettingItem {
        String title;
        Category(String title) { this.title = title; }
    }
    static class Normal extends SettingItem {
        String title, summary;
        int iconRes;
        Runnable onClick;
        Normal(String title, String summary, int iconRes, Runnable onClick) {
            this.title = title; this.summary = summary; this.iconRes = iconRes; this.onClick = onClick;
        }
    }
    static class SwitchItem extends SettingItem {
        String title, summary;
        int iconRes;
        boolean checked;
        java.util.function.Consumer<Boolean> onToggle;
        SwitchItem(String title, String summary, int iconRes, boolean checked, java.util.function.Consumer<Boolean> onToggle) {
            this.title = title; this.summary = summary; this.iconRes = iconRes; this.checked = checked; this.onToggle = onToggle;
        }
    }
    static class SliderItem extends SettingItem {
        String title, summary;
        int iconRes, currentValue, min, max, step;
        java.util.function.Consumer<Integer> onValueChanged;
        java.util.function.Function<Integer, String> formatter;
        SliderItem(String title, String summary, int iconRes, int currentValue, int min, int max, int step,
                   java.util.function.Consumer<Integer> onValueChanged,
                   java.util.function.Function<Integer, String> formatter) {
            this.title = title; this.summary = summary; this.iconRes = iconRes;
            this.currentValue = currentValue; this.min = min; this.max = max; this.step = step;
            this.onValueChanged = onValueChanged; this.formatter = formatter;
        }
    }
}
