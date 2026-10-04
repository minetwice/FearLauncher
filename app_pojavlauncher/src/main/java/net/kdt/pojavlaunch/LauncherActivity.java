package net.kdt.pojavlaunch;

import static android.content.res.Configuration.ORIENTATION_PORTRAIT;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.system.Os;
import android.view.Menu;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.navigation.NavigationView;
import com.kdt.mcgui.ProgressLayout;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.authenticator.accounts.Accounts;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.extra.ExtraListener;
import net.kdt.pojavlaunch.fragments.InstallationsFragment;
import net.kdt.pojavlaunch.fragments.FearCrashFragment;
import net.kdt.pojavlaunch.fragments.MainMenuFragment;
import net.kdt.pojavlaunch.fragments.MicrosoftLoginFragment;
import net.kdt.pojavlaunch.fragments.SelectAuthFragment;
import net.kdt.pojavlaunch.fragments.SearchModFragment;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.InstanceInstaller;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.lifecycle.ContextAwareDoneListener;
import net.kdt.pojavlaunch.lifecycle.ContextExecutor;
import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.IconCacheJanitor;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener;
import net.kdt.pojavlaunch.services.ProgressServiceKeeper;
import net.kdt.pojavlaunch.tasks.AsyncMinecraftDownloader;
import net.kdt.pojavlaunch.tasks.AsyncVersionList;
import net.kdt.pojavlaunch.tasks.MinecraftDownloader;
import net.kdt.pojavlaunch.utils.NotificationUtils;

public class LauncherActivity extends BaseActivity {

    private boolean mPendingCrashReport = false;
    public static final String SETTING_FRAGMENT_TAG = "SETTINGS_FRAGMENT";
    public static final String INSTALLATIONS_FRAGMENT_TAG = "INSTALLATIONS_FRAGMENT";

    private FragmentContainerView mFragmentView;
    private ProgressLayout mProgressLayout;
    private ProgressServiceKeeper mProgressServiceKeeper;
    private NotificationManager mNotificationManager;
    private DrawerLayout mDrawerLayout;
    private View mFearGlobalWash;

    // ---- global download bar: lives on the top layer of the activity, so it
    // shows over every screen whenever anything is downloading ----
    private View mFearDlWrap;
    private com.kdt.mcgui.FearBarView mFearDlBar;
    private android.widget.TextView mFearDlText;
    private net.kdt.pojavlaunch.progresskeeper.ProgressListener mFearDlListener;
    private static final String[] FEAR_DL_KEYS = {
            com.kdt.mcgui.ProgressLayout.DOWNLOAD_MINECRAFT,
            com.kdt.mcgui.ProgressLayout.UNPACK_RUNTIME,
            com.kdt.mcgui.ProgressLayout.INSTALL_MODPACK,
            com.kdt.mcgui.ProgressLayout.AUTHENTICATE,
            com.kdt.mcgui.ProgressLayout.DOWNLOAD_VERSION_LIST,
            com.kdt.mcgui.ProgressLayout.INSTANCE_INSTALL,
    };

    private long mLastButtonTint = 0L;

    /** The wash rides the shared FearTheme cycle so every screen shifts together. */
    private final net.kdt.pojavlaunch.utils.FearTheme.Listener mFearWashListener = colour -> {
        if (mFearGlobalWash != null) {
            mFearGlobalWash.setBackgroundColor((colour & 0x00FFFFFF) | 0x14000000);
        }
        // Recolour every button on the current screen off the same cycle. MineButton
        // already paints its background through a PorterDuff filter, so we drive the
        // same filter. Throttled hard - walking every view to set a colour filter is
        // real UI-thread work, and the shift is slow enough that 5fps is plenty.
        long now = android.os.SystemClock.uptimeMillis();
        if (now - mLastButtonTint > 200) {
            mLastButtonTint = now;
            android.content.SharedPreferences prefs =
                    android.preference.PreferenceManager.getDefaultSharedPreferences(this);
            prefs.edit().putInt("launcher_theme_color_v2", colour).apply();
            tintFearButtons(findViewById(R.id.container_fragment), colour);
        }
    };

    /** Sidebar entries keep their own flat look - the colour cycle skips them. */
    private boolean isSidebarEntry(View v) {
        int id = v.getId();
        return id == R.id.tray_installations_btn || id == R.id.tray_jar_btn
                || id == R.id.tray_controls_btn || id == R.id.tray_more_btn
                || id == R.id.tray_manager_btn || id == R.id.tray_logs_btn;
    }

    private void tintFearButtons(View root, int colour) {
        if (root == null) return;
        if (!isSidebarEntry(root)
                && (root instanceof com.kdt.mcgui.MineButton
                    || root instanceof com.kdt.mcgui.LauncherMenuButton)) {
            android.graphics.drawable.Drawable bg = root.getBackground();
            if (bg != null) {
                bg.setColorFilter(new android.graphics.PorterDuffColorFilter(
                        colour, android.graphics.PorterDuff.Mode.SRC_ATOP));
            }
        }
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                tintFearButtons(group.getChildAt(i), colour);
            }
        }
    }
    private NavigationView mNavigationView;
    private static ActivityResultLauncher<String> mRequestPermissionLauncher;

    private final ExtraListener<Boolean> mSelectAuthMethod = (key, value) -> {
        FragmentManager manager = getSupportFragmentManager();
        if (!value || manager.isStateSaved()) return false;
        Fragment fragment = manager.findFragmentById(mFragmentView.getId());
        if (!(fragment instanceof MainMenuFragment)) return false;
        Tools.swapFragment(this, SelectAuthFragment.class, SelectAuthFragment.TAG, null);
        return false;
    };

    private final ExtraListener<Boolean> mLaunchGameListener = (key, value) -> {
        if (mProgressLayout != null && mProgressLayout.hasProcesses()) {
            Toast.makeText(this, R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
            return false;
        }
        Instance selectedInstance = Instances.loadSelectedInstance();
        if (selectedInstance == null) {
            Toast.makeText(this, R.string.no_instance, Toast.LENGTH_LONG).show();
            return false;
        }
        if (selectedInstance.installer != null) {
            selectedInstance.installer.start();
            return false;
        }
        if (!Tools.isValidString(selectedInstance.versionId)) {
            Toast.makeText(this, R.string.error_no_version, Toast.LENGTH_LONG).show();
            return false;
        }
        if (Accounts.getCurrent() == null) {
            Toast.makeText(this, R.string.no_saved_accounts, Toast.LENGTH_LONG).show();
            ExtraCore.setValue(ExtraConstants.SELECT_AUTH_METHOD, true);
            return false;
        }
        String normalizedVersionId = AsyncMinecraftDownloader.normalizeVersionId(selectedInstance.versionId);
        JMinecraftVersionList.Version mcVersion = AsyncMinecraftDownloader.getListedVersion(normalizedVersionId);
        new MinecraftDownloader().start(
                this.getAssets(),
                mcVersion,
                normalizedVersionId,
                new ContextAwareDoneListener(this, normalizedVersionId)
        );
        return false;
    };

    private final TaskCountListener mDoubleLaunchPreventionListener = taskCount -> {
        if (taskCount > 0) {
            Tools.runOnUiThread(() ->
                    mNotificationManager.cancel(NotificationUtils.NOTIFICATION_ID_GAME_START)
            );
        }
        return false;
    };

    @Override
    protected boolean shouldIgnoreNotch() {
        return getResources().getConfiguration().orientation == ORIENTATION_PORTRAIT;
    }

    @Override
    public boolean setFullscreen() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // A leftover flag means the last run died before it could clean up.
        mPendingCrashReport = net.kdt.pojavlaunch.utils.FearCrashGuard.detect(this) != null;
        setContentView(R.layout.activity_pojav_launcher);

        try {
            Os.setenv("POJAV_NATIVEDIR", Tools.NATIVE_LIB_DIR, true);
            Os.setenv("TMPDIR", Tools.DIR_CACHE.getAbsolutePath(), true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        IconCacheJanitor.runJanitor();
        getWindow().setBackgroundDrawable(null);
        bindViews();
        setupDrawer();
        SoundManager.init(this);
        SoundManager.startMusic(this);

        if (savedInstanceState == null) {
            // The last run died, so lead with the crash board instead of the menu.
            androidx.fragment.app.Fragment first = mPendingCrashReport
                    ? new FearCrashFragment()
                    : new MainMenuFragment();
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.container_fragment, first)
                    .commit();
            if (mNavigationView != null) {
                mNavigationView.setCheckedItem(R.id.nav_dashboard);
            }
        }

        mRequestPermissionLauncher = this.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isAllowed -> {
                    if (!isAllowed) {
                        Tools.runOnUiThread(() ->
                                Toast.makeText(this, R.string.notification_permission_toast, Toast.LENGTH_LONG).show()
                        );
                    }
                }
        );
        checkNotificationPermission();

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        ProgressKeeper.addTaskCountListener(mDoubleLaunchPreventionListener);
        mProgressServiceKeeper = new ProgressServiceKeeper(this);
        ProgressKeeper.addTaskCountListener(mProgressServiceKeeper);
        if (mProgressLayout != null) ProgressKeeper.addTaskCountListener(mProgressLayout);

        ExtraCore.addExtraListener(ExtraConstants.SELECT_AUTH_METHOD, mSelectAuthMethod);
        ExtraCore.addExtraListener(ExtraConstants.LAUNCH_GAME, mLaunchGameListener);

        // Local login listener
        ExtraCore.addExtraListener(ExtraConstants.MOJANG_LOGIN_TODO, (key, value) -> {
            if (value instanceof String[]) {
                String[] loginData = (String[]) value;
                String username = loginData[0];
                Tools.runOnUiThread(() -> {
                    Toast.makeText(this, "Account saved: " + username, Toast.LENGTH_SHORT).show();
                    ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
                });
            }
            return false;
        });

        new AsyncVersionList().getVersionList(versions ->
                ExtraCore.setValue(ExtraConstants.RELEASE_TABLE, versions)
        );

        // The on-screen progress/download bar view was removed; the progress
        // keys below still drive everything else, so only guard the view.
        if (mProgressLayout != null) {
            mProgressLayout.observe(ProgressLayout.DOWNLOAD_MINECRAFT);
            mProgressLayout.observe(ProgressLayout.UNPACK_RUNTIME);
            mProgressLayout.observe(ProgressLayout.INSTALL_MODPACK);
            mProgressLayout.observe(ProgressLayout.AUTHENTICATE);
            mProgressLayout.observe(ProgressLayout.DOWNLOAD_VERSION_LIST);
            mProgressLayout.observe(ProgressLayout.INSTANCE_INSTALL);
        }

        // Auto-reload listener
        ProgressKeeper.addTaskCountListener(tc -> {
            if (tc == 0) {
                Tools.runOnUiThread(() -> {
                    // Refresh dash components
                    ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
                    ExtraCore.setValue(ExtraConstants.REFRESH_VERSION_SPINNER, "REFRESH");

                    // Specific logic for instance list refresh if needed
                    Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.container_fragment);
                    if (fragment instanceof MainMenuFragment) {
                        ((MainMenuFragment) fragment).refreshAccountUI();
                    }
                });
            }
            return false;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        ContextExecutor.setActivity(this);
        InstanceInstaller.postInstallCheck(this);
        SoundManager.startMusic(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ContextExecutor.clearActivity();
        SoundManager.stopMusic();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mProgressLayout != null) {
            mProgressLayout.cleanUpObservers();
            ProgressKeeper.removeTaskCountListener(mProgressLayout);
        }
        ProgressKeeper.removeTaskCountListener(mProgressServiceKeeper);
        net.kdt.pojavlaunch.utils.FearTheme.unregister(mFearWashListener);
        if (mFearDlListener != null) {
            for (String key : FEAR_DL_KEYS)
                net.kdt.pojavlaunch.progresskeeper.ProgressKeeper.removeListener(key, mFearDlListener);
            mFearDlListener = null;
        }
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.SELECT_AUTH_METHOD, mSelectAuthMethod);
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.LAUNCH_GAME, mLaunchGameListener);
    }

    @Override
    public void onBackPressed() {
        if (mDrawerLayout != null && mNavigationView != null && mDrawerLayout.isDrawerOpen(mNavigationView)) {
            mDrawerLayout.closeDrawer(mNavigationView);
            return;
        }
        MicrosoftLoginFragment fragment = (MicrosoftLoginFragment) getVisibleFragment(MicrosoftLoginFragment.TAG);
        if (fragment != null && fragment.canGoBack()) {
            fragment.goBack();
            return;
        }
        super.onBackPressed();
    }

    @SuppressWarnings("SameParameterValue")
    private Fragment getVisibleFragment(String tag) {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(tag);
        if (fragment != null && fragment.isVisible()) {
            return fragment;
        }
        return null;
    }

    public void askForPermission(int minApi, final String permission) {
        if (Build.VERSION.SDK_INT < minApi) return;
        mRequestPermissionLauncher.launch(permission);
    }

    public boolean checkForPermission(int minApi, final String permission) {
        return Build.VERSION.SDK_INT < minApi ||
                ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_DENIED;
    }

    public boolean checkForPermissionRationale(int minApi, final String permission) {
        return checkForPermission(minApi, permission) ||
                ActivityCompat.shouldShowRequestPermissionRationale(this, permission);
    }

    private void checkNotificationPermission() {
        if (LauncherPreferences.PREF_SKIP_NOTIFICATION_PERMISSION_CHECK ||
                checkForPermission(33, Manifest.permission.POST_NOTIFICATIONS)) {
            return;
        }
        showNotificationPermissionReasoning();
    }

    private void showNotificationPermissionReasoning() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.notification_permission_dialog_title)
                .setMessage(R.string.notification_permission_dialog_text)
                .setPositiveButton(android.R.string.ok, (d, w) ->
                        askForPermission(33, Manifest.permission.POST_NOTIFICATIONS))
                .setNegativeButton(android.R.string.cancel, (d, w) -> handleNoNotificationPermission())
                .show();
    }

    private void handleNoNotificationPermission() {
        LauncherPreferences.PREF_SKIP_NOTIFICATION_PERMISSION_CHECK = true;
        LauncherPreferences.DEFAULT_PREF.edit()
                .putBoolean(LauncherPreferences.PREF_KEY_SKIP_NOTIFICATION_CHECK, true)
                .apply();
    }

    public ProgressLayout getProgressLayout() {
        return mProgressLayout;
    }

    private void bindViews() {
        mFragmentView = findViewById(R.id.container_fragment);
        // Both views were removed from the layouts - the old progress/download
        // bar and the slide-out sidebar. The fields stay so every guarded call
        // site keeps compiling; they are simply null now.
        mProgressLayout = null;
        mDrawerLayout = findViewById(R.id.drawer_layout);
        mNavigationView = null;
        mFearGlobalWash = findViewById(R.id.fear_global_wash);
        net.kdt.pojavlaunch.utils.FearTheme.register(mFearWashListener);
        bindGlobalDownloadBar();
        // The slide-out navigation sidebar was removed - the hamburger tray in
        // the home fragment is the only menu now, so kill the edge-swipe.
        if (mDrawerLayout != null) {
            mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        }
    }

    /**
     * The dragon-head bar, hoisted to the activity so it floats above every
     * screen. It listens to all the download progress keys and follows the real
     * transfer, and hides itself once no task is left.
     */
    private void bindGlobalDownloadBar() {
        mFearDlWrap = findViewById(R.id.fear_dl_wrap);
        mFearDlBar = findViewById(R.id.fear_dl_bar);
        mFearDlText = findViewById(R.id.fear_dl_text);
        if (mFearDlWrap == null || mFearDlBar == null || mFearDlListener != null) return;

        mFearDlListener = new net.kdt.pojavlaunch.progresskeeper.ProgressListener() {
            private void apply(int percent, Object[] va) {
                final int p = Math.max(0, Math.min(100, percent));
                String msg = p + "%";
                if (va != null && va.length >= 2 && va[0] instanceof Number && va[1] instanceof Number) {
                    msg = p + "%      "
                            + String.format(java.util.Locale.US, "%.1f", ((Number) va[0]).doubleValue())
                            + " / " + String.format(java.util.Locale.US, "%.1f", ((Number) va[1]).doubleValue())
                            + " MB";
                    if (va.length >= 3 && va[2] instanceof Number) {
                        msg += "      " + String.format(java.util.Locale.US, "%.1f",
                                ((Number) va[2]).doubleValue()) + " MB/s";
                    }
                }
                final String fmsg = msg;
                mFearDlWrap.post(() -> {
                    mFearDlWrap.setVisibility(View.VISIBLE);
                    if (mFearDlText != null) mFearDlText.setText(fmsg);
                    mFearDlBar.setProgress(p);
                });
            }

            @Override public void onProgressStarted() {
                mFearDlWrap.post(() -> mFearDlWrap.setVisibility(View.VISIBLE));
            }

            @Override public void onProgressUpdated(int progress, int resid, Object... va) {
                apply(progress, va);
            }

            @Override public void onProgressEnded() {
                mFearDlWrap.post(() -> {
                    if (net.kdt.pojavlaunch.progresskeeper.ProgressKeeper.getTaskCount() == 0) {
                        mFearDlWrap.setVisibility(View.GONE);
                    }
                });
            }
        };
        for (String key : FEAR_DL_KEYS) {
            net.kdt.pojavlaunch.progresskeeper.ProgressKeeper.addListener(key, mFearDlListener);
        }
    }

    private void setupDrawer() {
        ImageButton hamburgerButton = findViewById(R.id.hamburger_button);
        if (hamburgerButton != null) {
            hamburgerButton.setOnClickListener(v -> {
                if (mDrawerLayout != null && mNavigationView != null) {
                    mDrawerLayout.openDrawer(mNavigationView);
                }
            });
        }

        if (mNavigationView != null) {
            Menu menu = mNavigationView.getMenu();
            menu.clear();

            // ✅ Add items WITHOUT "Account"
            menu.add(0, R.id.nav_dashboard, 0, "Home")
                    .setIcon(R.drawable.ic_px_home);
            menu.add(0, R.id.nav_installations, 1, "Installations")
                    .setIcon(R.drawable.ic_px_java);
            menu.add(0, R.id.nav_mods, 2, "Mods")
                    .setIcon(R.drawable.ic_px_file_dl);
            // "Account" item REMOVED – no more nav_account
            menu.add(0, R.id.nav_skins, 3, "Skins")
                    .setIcon(R.drawable.ic_px_edit);
            menu.add(0, R.id.nav_settings, 4, "Settings")
                    .setIcon(R.drawable.ic_px_sliders);

            menu.findItem(R.id.nav_dashboard).setChecked(true);

            mNavigationView.setNavigationItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_dashboard) {
                    getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.container_fragment, new MainMenuFragment())
                            .commit();
                } else if (id == R.id.nav_settings) {
                    Tools.swapFragment(this, LauncherPreferenceFragment.class, SETTING_FRAGMENT_TAG, null);
                } else if (id == R.id.nav_installations) {
                    getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.container_fragment, new InstallationsFragment())
                            .commit();
                } else if (id == R.id.nav_mods) {
                    Tools.swapFragment(this, SearchModFragment.class, SearchModFragment.TAG, null);
                } else if (id == R.id.nav_skins) {
                    Toast.makeText(this, "Skins (Coming soon)", Toast.LENGTH_SHORT).show();
                }
                // No nav_account handling

                if (mDrawerLayout != null) {
                    mDrawerLayout.closeDrawer(mNavigationView);
                }
                return true;
            });
        }
    }
}
