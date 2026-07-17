package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.runOnUiThread;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.math.MathUtils;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.kdt.mcgui.ProgressLayout;

import git.artdeell.mojo.R;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.ModItemAdapter;
import net.kdt.pojavlaunch.modloaders.modpacks.api.CommonApi;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModpackApi;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.profiles.VersionSelectorDialog;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class SearchModFragment extends Fragment implements ModItemAdapter.SearchResultCallback {

    public static final String TAG = "SearchModFragment";
    private View mOverlay;

    private EditText mSearchEditText;
    private ImageButton mFilterButton;
    private RecyclerView mRecyclerview;
    private ModItemAdapter mModItemAdapter;
    private ProgressBar mSearchProgressBar;
    private TextView mStatusTextView;
    private ColorStateList mDefaultTextColor;
    private ModpackApi modpackApi;

    private final SearchFilters mSearchFilters;

    private Button mImportButton;
    private TaskCountListener mTaskCountListener;

    ActivityResultLauncher<String> mImportLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri == null) return;
                Context context = getContext();
                ContentResolver contentResolver = getContext().getContentResolver();
                PojavApplication.sExecutorService.execute(() -> {
                    performLocalInstall(uri, context, contentResolver);
                });
            });

    public void performLocalInstall(Uri uri, Context context, ContentResolver contentResolver) {
            String fileName = Tools.getFileName(context, uri);
            if (fileName == null) return;
            File outFile = new File(Tools.DIR_CACHE, fileName + ".cf");
            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, R.string.multirt_progress_caching);
            try (InputStream inputStream = contentResolver.openInputStream(uri);
                 OutputStream outputStream = new FileOutputStream(outFile)) {
                if (inputStream == null) return;
                IOUtils.copy(inputStream, outputStream);
                outputStream.flush();
            } catch (IOException e) {
                Tools.showErrorRemote("Error", e);
                ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
                return;
            }
            try {
                modpackApi.installLocalModpack(fileName, outFile, null);
            } catch (IOException e) {
                Tools.showErrorRemote("Error", e);
            } finally {
                outFile.delete();
                ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
            }
    }

    public SearchModFragment(){
        super(R.layout.fragment_mod_search);
        mSearchFilters = new SearchFilters();
        mSearchFilters.isModpack = true;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        modpackApi = new CommonApi(context.getString(R.string.curseforge_api_key));
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // You can only access resources after attaching to current context
        mModItemAdapter = new ModItemAdapter(getResources(), modpackApi, this);
        ProgressKeeper.addTaskCountListener(mModItemAdapter);

        mOverlay = view.findViewById(R.id.search_mod_overlay);
        mSearchEditText = view.findViewById(R.id.search_mod_edittext);
        mSearchProgressBar = view.findViewById(R.id.search_mod_progressbar);
        mRecyclerview = view.findViewById(R.id.search_mod_list);
        mStatusTextView = view.findViewById(R.id.search_mod_status_text);
        mFilterButton = view.findViewById(R.id.search_mod_filter);

        // Map Segment Control Tabs dynamically
        com.kdt.mcgui.MineButton btnModpacks = view.findViewById(R.id.tab_modpacks);
        com.kdt.mcgui.MineButton btnMods = view.findViewById(R.id.tab_mods);
        com.kdt.mcgui.MineButton btnRes = view.findViewById(R.id.tab_resourcepacks);
        com.kdt.mcgui.MineButton btnShaders = view.findViewById(R.id.tab_shaders);

        if (btnModpacks != null && btnMods != null && btnRes != null && btnShaders != null) {
            btnModpacks.setOnClickListener(v -> {
                btnModpacks.setBackgroundResource(R.drawable.premium_button_bg);
                btnModpacks.setTextColor(Color.BLACK);
                btnMods.setBackgroundResource(R.drawable.premium_glass_black_bg);
                btnMods.setTextColor(Color.WHITE);
                btnRes.setBackgroundResource(R.drawable.premium_glass_black_bg);
                btnRes.setTextColor(Color.WHITE);
                btnShaders.setBackgroundResource(R.drawable.premium_glass_black_bg);
                btnShaders.setTextColor(Color.WHITE);

                mSearchFilters.isModpack = true;
                mSearchFilters.isMod = false;
                mSearchFilters.isResourcePack = false;
                mSearchFilters.isShaderPack = false;
                searchMods(mSearchEditText.getText().toString());
            });

            btnMods.setOnClickListener(v -> {
                btnModpacks.setBackgroundResource(R.drawable.premium_glass_black_bg);
                btnModpacks.setTextColor(Color.WHITE);
                btnMods.setBackgroundResource(R.drawable.premium_button_bg);
                btnMods.setTextColor(Color.BLACK);
                btnRes.setBackgroundResource(R.drawable.premium_glass_black_bg);
                btnRes.setTextColor(Color.WHITE);
                btnShaders.setBackgroundResource(R.drawable.premium_glass_black_bg);
                btnShaders.setTextColor(Color.WHITE);

                mSearchFilters.isModpack = false;
                mSearchFilters.isMod = true;
                mSearchFilters.isResourcePack = false;
                mSearchFilters.isShaderPack = false;
                searchMods(mSearchEditText.getText().toString());
            });

            btnRes.setOnClickListener(v -> {
                btnModpacks.setBackgroundResource(R.drawable.premium_glass_black_bg);
                btnModpacks.setTextColor(Color.WHITE);
                btnMods.setBackgroundResource(R.drawable.premium_glass_black_bg);
                btnMods.setTextColor(Color.WHITE);
                btnRes.setBackgroundResource(R.drawable.premium_button_bg);
                btnRes.setTextColor(Color.BLACK);
                btnShaders.setBackgroundResource(R.drawable.premium_glass_black_bg);
                btnShaders.setTextColor(Color.WHITE);

                mSearchFilters.isModpack = false;
                mSearchFilters.isMod = false;
                mSearchFilters.isResourcePack = true;
                mSearchFilters.isShaderPack = false;
                searchMods(mSearchEditText.getText().toString());
            });

            btnShaders.setOnClickListener(v -> {
                btnModpacks.setBackgroundResource(R.drawable.premium_glass_black_bg);
                btnModpacks.setTextColor(Color.WHITE);
                btnMods.setBackgroundResource(R.drawable.premium_glass_black_bg);
                btnMods.setTextColor(Color.WHITE);
                btnRes.setBackgroundResource(R.drawable.premium_glass_black_bg);
                btnRes.setTextColor(Color.WHITE);
                btnShaders.setBackgroundResource(R.drawable.premium_button_bg);
                btnShaders.setTextColor(Color.BLACK);

                mSearchFilters.isModpack = false;
                mSearchFilters.isMod = false;
                mSearchFilters.isResourcePack = false;
                mSearchFilters.isShaderPack = true;
                searchMods(mSearchEditText.getText().toString());
            });
        }

        mDefaultTextColor = mStatusTextView.getTextColors();

        int spanCount = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE ? 4 : 2;
        GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), spanCount);
        gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return mModItemAdapter.getItemViewType(position) == 1 ? spanCount : 1; // 1 is VIEW_TYPE_LOADING
            }
        });
        mRecyclerview.setLayoutManager(gridLayoutManager);
        mRecyclerview.setAdapter(mModItemAdapter);

        mSearchEditText.setOnEditorActionListener((v, actionId, event) -> {
            searchMods(mSearchEditText.getText().toString());
            mSearchEditText.clearFocus();
            return false;
        });

        mFilterButton.setOnClickListener(v -> displayFilterDialog());
        mImportButton = view.findViewById(R.id.mineButton_import_local_modpack);
        mImportButton.setOnClickListener(v -> {
            mImportLauncher.launch("*/*");
        });
        mTaskCountListener = taskCount -> {
            runOnUiThread(() -> mImportButton.setEnabled(taskCount == 0));
            return false;
        };
        ProgressKeeper.addTaskCountListener(mTaskCountListener);

        searchMods(null);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ProgressKeeper.removeTaskCountListener(mModItemAdapter);
        if (mTaskCountListener != null) { ProgressKeeper.removeTaskCountListener(mTaskCountListener); }
    }

    @Override
    public void onSearchFinished() {
        mSearchProgressBar.setVisibility(View.GONE);
        mStatusTextView.setVisibility(View.GONE);
    }

    @Override
    public void onSearchError(int error) {
        mSearchProgressBar.setVisibility(View.GONE);
        mStatusTextView.setVisibility(View.VISIBLE);
        switch (error) {
            case ERROR_INTERNAL:
                mStatusTextView.setTextColor(Color.RED);
                mStatusTextView.setText(R.string.search_modpack_error);
                break;
            case ERROR_NO_RESULTS:
                mStatusTextView.setTextColor(mDefaultTextColor);
                mStatusTextView.setText(R.string.search_modpack_no_result);
                break;
        }
    }

    private void searchMods(String name) {
        mSearchProgressBar.setVisibility(View.VISIBLE);
        mSearchFilters.name = name == null ? "" : name;
        mModItemAdapter.performSearchQuery(mSearchFilters);
    }

    private void displayFilterDialog() {
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(R.layout.dialog_mod_filters)
                .create();

        // setup the view behavior
        dialog.setOnShowListener(dialogInterface -> {
            TextView mSelectedVersion = dialog.findViewById(R.id.search_mod_selected_mc_version_textview);
            Button mSelectVersionButton = dialog.findViewById(R.id.search_mod_mc_version_button);
            Button mApplyButton = dialog.findViewById(R.id.search_mod_apply_filters);

            assert mSelectVersionButton != null;
            assert mSelectedVersion != null;
            assert mApplyButton != null;

            // Setup the expendable list behavior
            mSelectVersionButton.setOnClickListener(v -> VersionSelectorDialog.open(v.getContext(), true, (id, snapshot)-> mSelectedVersion.setText(id)));

            // Apply visually all the current settings
            mSelectedVersion.setText(mSearchFilters.mcVersion);

            // Apply the new settings
            mApplyButton.setOnClickListener(v -> {
                mSearchFilters.mcVersion = mSelectedVersion.getText().toString();
                searchMods(mSearchEditText.getText().toString());
                dialogInterface.dismiss();
            });
        });

        dialog.show();
    }
}
