package net.kdt.pojavlaunch.fearfiles;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.Tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A file manager that lives over the game.
 *
 * Two roots - the launcher's own home and the shared Downloads folder - because those
 * are the two places a mod or a pack actually arrives in. Folders open on tap, archives
 * open as a listing of their entries, and a long press starts picking files so they can
 * be copied, renamed or deleted.
 */
public class FearFileManagerFragment extends DialogFragment implements FearFileAdapter.Listener {

    public static final String TAG = "FearFileManagerFragment";

    private FearFileAdapter mAdapter;
    private RecyclerView mList;
    private TextView mPathView;
    private TextView mEmptyView;
    private View mSelectionBar;
    private TextView mSelectionCount;
    private View mPasteButton;

    private File mCurrentDir;
    private File mRoot;
    private final List<File> mClipboard = new ArrayList<>();

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        Window window = dialog.getWindow();
        if (window != null) {
            window.requestFeature(Window.FEATURE_NO_TITLE);
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_fear_file_manager, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mPathView = view.findViewById(R.id.fm_path);
        mEmptyView = view.findViewById(R.id.fm_empty);
        mSelectionBar = view.findViewById(R.id.fm_selection_bar);
        mSelectionCount = view.findViewById(R.id.fm_selection_count);
        mPasteButton = view.findViewById(R.id.fm_paste);
        mList = view.findViewById(R.id.fm_list);
        mList.setLayoutManager(new LinearLayoutManager(requireContext()));

        mAdapter = new FearFileAdapter(this);
        mList.setAdapter(mAdapter);

        View close = view.findViewById(R.id.fm_close);
        if (close != null) close.setOnClickListener(v -> dismiss());

        View up = view.findViewById(R.id.fm_up);
        if (up != null) up.setOnClickListener(v -> navigateUp());

        View launcherRoot = view.findViewById(R.id.fm_root_launcher);
        if (launcherRoot != null) launcherRoot.setOnClickListener(v -> openRoot(launcherRoot()));

        View downloadsRoot = view.findViewById(R.id.fm_root_downloads);
        if (downloadsRoot != null) downloadsRoot.setOnClickListener(v -> openRoot(downloadsRoot()));

        View storageRoot = view.findViewById(R.id.fm_root_storage);
        if (storageRoot != null) storageRoot.setOnClickListener(v -> {
            if (ensureAllFilesAccess()) openRoot(storageRoot());
        });

        View copy = view.findViewById(R.id.fm_copy);
        if (copy != null) copy.setOnClickListener(v -> {
            mClipboard.clear();
            mClipboard.addAll(mAdapter.selectedFiles());
            if (mClipboard.isEmpty()) return;
            toast(mClipboard.size() + " ready to paste");
            mAdapter.clearSelection();
            updatePaste();
        });

        View rename = view.findViewById(R.id.fm_rename);
        if (rename != null) rename.setOnClickListener(v -> promptRename());

        View delete = view.findViewById(R.id.fm_delete);
        if (delete != null) delete.setOnClickListener(v -> confirmDelete());

        if (mPasteButton != null) mPasteButton.setOnClickListener(v -> pasteHere());

        View cancel = view.findViewById(R.id.fm_cancel_selection);
        if (cancel != null) cancel.setOnClickListener(v -> mAdapter.clearSelection());

        openRoot(launcherRoot());
    }

    private File launcherRoot() {
        File home = new File(Tools.DIR_GAME_HOME);
        return home.isDirectory() ? home : new File(Tools.DIR_DATA);
    }

    /** The whole of the shared storage - /storage/emulated/0. */
    private File storageRoot() {
        try {
            File root = Environment.getExternalStorageDirectory();
            if (root != null && root.isDirectory()) return root;
        } catch (Throwable ignored) { }
        return launcherRoot();
    }

    /**
     * Broad storage on Android 11 and up needs the all-files permission, and it is only
     * grantable from a settings screen - there is no runtime dialog for it.
     */
    private boolean ensureAllFilesAccess() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return true;
        try {
            if (Environment.isExternalStorageManager()) return true;
            toast("Allow file access, then come back");
            android.content.Intent intent = new android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(android.net.Uri.parse("package:" + requireContext().getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            try {
                startActivity(new android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            } catch (Exception ignored) { }
        }
        return false;
    }

    private File downloadsRoot() {
        try {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (dir != null && dir.isDirectory()) return dir;
        } catch (Throwable ignored) { }
        return launcherRoot();
    }

    private void openRoot(File root) {
        mRoot = root;
        load(root);
    }

    private void navigateUp() {
        if (mCurrentDir == null) return;
        File parent = mCurrentDir.getParentFile();
        if (parent == null || !parent.canRead()) return;
        // Never climb out of the root the user picked.
        if (mRoot != null && !parent.getAbsolutePath().startsWith(mRoot.getAbsolutePath())) {
            load(mRoot);
            return;
        }
        load(parent);
    }

    private void load(File dir) {
        mCurrentDir = dir;
        List<File> entries = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files != null) entries.addAll(Arrays.asList(files));
        entries.sort(Comparator
                .comparing((File f) -> !f.isDirectory())
                .thenComparing((File f) -> f.getName().toLowerCase()));
        mAdapter.setFiles(entries);
        mPathView.setText(shortPath(dir));
        mEmptyView.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
        // A null listing on shared storage almost always means the all-files permission
        // is missing, and an empty-looking folder would hide that.
        if (files == null && dir.canRead() == false) {
            mEmptyView.setText("Not readable - grant file access");
        } else {
            mEmptyView.setText("Nothing in here");
        }
        updatePaste();
    }

    /** Re-reads the folder while keeping the selection, for use after a change. */
    private void refresh() {
        if (mCurrentDir == null) return;
        List<File> entries = new ArrayList<>();
        File[] files = mCurrentDir.listFiles();
        if (files != null) entries.addAll(Arrays.asList(files));
        entries.sort(Comparator
                .comparing((File f) -> !f.isDirectory())
                .thenComparing((File f) -> f.getName().toLowerCase()));
        mAdapter.replaceFiles(entries);
        mEmptyView.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
        updatePaste();
    }

    private String shortPath(File dir) {
        if (mRoot != null) {
            String base = mRoot.getAbsolutePath();
            String full = dir.getAbsolutePath();
            if (full.startsWith(base)) {
                String tail = full.substring(base.length());
                return mRoot.getName() + (tail.isEmpty() ? "" : tail);
            }
        }
        return dir.getAbsolutePath();
    }

    // ---- adapter callbacks -------------------------------------------------

    @Override
    public void onOpen(File file) {
        if (file.isDirectory()) {
            if (file.canRead()) load(file);
            else toast("Cannot open that folder");
            return;
        }
        if (FearFileIcons.isArchive(file)) {
            showArchive(file);
        } else {
            toast(file.getName());
        }
    }

    @Override
    public void onSelectionChanged(int count) {
        boolean picking = count > 0;
        mSelectionBar.setVisibility(picking ? View.VISIBLE : View.GONE);
        mSelectionCount.setText(count == 1 ? "1 SELECTED" : count + " SELECTED");
    }

    // ---- actions -----------------------------------------------------------

    private void promptRename() {
        List<File> picked = mAdapter.selectedFiles();
        if (picked.size() != 1) {
            toast("Pick exactly one file to rename");
            return;
        }
        final File source = picked.get(0);
        EditText input = new EditText(requireContext());
        input.setText(source.getName());
        input.setSelection(input.getText().length());
        new AlertDialog.Builder(requireContext())
                .setTitle("RENAME")
                .setView(input)
                .setPositiveButton("RENAME", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty() || name.contains("/")) return;
                    File target = new File(source.getParentFile(), name);
                    if (!source.renameTo(target)) toast("Could not rename that");
                    mAdapter.clearSelection();
                    refresh();
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private void confirmDelete() {
        final List<File> picked = mAdapter.selectedFiles();
        if (picked.isEmpty()) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("DELETE")
                .setMessage(picked.size() == 1
                        ? "Delete " + picked.get(0).getName() + "?"
                        : "Delete " + picked.size() + " items?")
                .setPositiveButton("DELETE", (d, w) -> {
                    for (File f : picked) deleteRecursively(f);
                    mAdapter.clearSelection();
                    refresh();
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private void pasteHere() {
        if (mClipboard.isEmpty() || mCurrentDir == null) return;
        int copied = 0;
        for (File source : mClipboard) {
            try {
                copyRecursively(source, new File(mCurrentDir, source.getName()));
                copied++;
            } catch (Exception ignored) { }
        }
        toast(copied + (copied == 1 ? " item pasted" : " items pasted"));
        mClipboard.clear();
        refresh();
    }

    private void updatePaste() {
        if (mPasteButton == null) return;
        boolean ready = !mClipboard.isEmpty();
        mPasteButton.setVisibility(ready ? View.VISIBLE : View.GONE);
    }

    // ---- archives ----------------------------------------------------------

    private void showArchive(File archive) {
        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new FileInputStream(archive))) {
            ZipEntry entry;
            int guard = 0;
            while ((entry = in.getNextEntry()) != null && guard++ < 2000) {
                if (!entry.isDirectory()) names.add(entry.getName());
            }
        } catch (Exception e) {
            toast("Could not read that archive");
            return;
        }
        if (names.isEmpty()) {
            toast("That archive is empty");
            return;
        }
        String[] rows = names.toArray(new String[0]);
        new AlertDialog.Builder(requireContext())
                .setTitle(archive.getName())
                .setItems(rows, null)
                .setPositiveButton("CLOSE", null)
                .show();
    }

    // ---- file helpers ------------------------------------------------------

    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] kids = file.listFiles();
            if (kids != null) for (File k : kids) deleteRecursively(k);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private static void copyRecursively(File source, File target) throws Exception {
        if (source.isDirectory()) {
            if (!target.exists() && !target.mkdirs()) throw new Exception("mkdir failed");
            File[] kids = source.listFiles();
            if (kids != null) {
                for (File k : kids) copyRecursively(k, new File(target, k.getName()));
            }
            return;
        }
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
        }
    }

    private void toast(String message) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onResume() {
        super.onResume();
        if (mAdapter != null) mAdapter.notifyDataSetChanged();
    }
}
