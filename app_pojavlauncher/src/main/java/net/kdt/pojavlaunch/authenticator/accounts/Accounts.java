package net.kdt.pojavlaunch.authenticator.accounts;

import android.util.Log;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.AuthType;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.utils.JSONUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Accounts {
    private static final String TAG = "Accounts";
    private static final String PROFILE_PREF_FILE = "selected_account_file";

    public final List<MinecraftAccount> accounts;
    public final int selectionIndex;

    private Accounts(List<MinecraftAccount> accounts, int selectionIndex) {
        this.accounts = accounts;
        this.selectionIndex = selectionIndex;
    }

    public static Accounts load() throws IOException {
        File accountsDir = new File(Tools.DIR_ACCOUNT_NEW);
        synchronized (Accounts.class) {
            FileUtils.ensureDirectory(accountsDir);
        }
        File[] accountFiles = accountsDir.listFiles();
        if (accountFiles == null) throw new IOException("Failed to create account directory");

        String selectedAccount = getSelectedAccount();
        ArrayList<MinecraftAccount> accounts = new ArrayList<>(accountFiles.length);
        int selectedAccountIdx = 0;

        for (File accFile : accountFiles) {
            MinecraftAccount account = loadAccount(accFile);
            if (account == null) continue;
            accounts.add(account);
            if (accFile.getName().equals(selectedAccount)) {
                selectedAccountIdx = accounts.size() - 1;
            }
        }
        accounts.trimToSize();
        return new Accounts(Collections.unmodifiableList(accounts), selectedAccountIdx);
    }

    private static MinecraftAccount loadAccount(File source) {
        // FIX 1: Don't try to load if file doesn't exist or name is empty
        if (source == null || !source.exists() || source.getName().isEmpty()) {
            return null;
        }

        MinecraftAccount acc;
        try {
            acc = JSONUtils.readFromFile(source, MinecraftAccount.class);
        } catch (Exception e) {
            Log.w(TAG, "Failed to load account from: " + source.getName(), e);
            return null;
        }
        if (acc == null) return null;
        acc.mSaveLocation = source;

        if (acc.accessToken == null)  acc.accessToken  = "0";
        if (acc.profileId == null)    acc.profileId    = "00000000-0000-0000-0000-000000000000";
        if (acc.username == null)     acc.username     = "0";
        if (acc.refreshToken == null) acc.refreshToken = "0";
        if (acc.authType == null) {
            acc.authType = acc.isMicrosoft ? AuthType.MICROSOFT : AuthType.LOCAL;
        }
        return acc;
    }

    private static String getSelectedAccount() {
        return LauncherPreferences.DEFAULT_PREF.getString(PROFILE_PREF_FILE, "");
    }

    /**
     * FIX 2: getCurrent() now safely returns null if no account is selected
     * or selected account file doesn't exist anymore.
     */
    public static MinecraftAccount getCurrent() {
        String selectedAccount = getSelectedAccount();

        // Guard: empty string means nothing is selected
        if (selectedAccount == null || selectedAccount.isEmpty()) {
            Log.d(TAG, "getCurrent: no account selected");
            return null;
        }

        File accountFile = new File(Tools.DIR_ACCOUNT_NEW, selectedAccount);

        // Guard: file was deleted or never created
        if (!accountFile.exists()) {
            Log.w(TAG, "getCurrent: selected account file missing: " + selectedAccount);
            // FIX 3: Clear stale preference so next call doesn't repeat this
            clearCurrentSelection();
            return null;
        }

        return loadAccount(accountFile);
    }

    /**
     * FIX 4: New helper — clears the saved selection (e.g. if account was deleted)
     */
    public static void clearCurrentSelection() {
        LauncherPreferences.DEFAULT_PREF
                .edit()
                .remove(PROFILE_PREF_FILE)
                .apply();
        Log.d(TAG, "clearCurrentSelection: cleared stale account selection");
    }

    /**
     * FIX 5: New helper — checks if ANY account is available and selected
     */
    public static boolean hasCurrentAccount() {
        return getCurrent() != null;
    }

    private static File pickAccountPath() {
        File profilePath;
        do {
            String profileName = UUID.randomUUID().toString();
            profilePath = new File(Tools.DIR_ACCOUNT_NEW, profileName);
        } while (profilePath.exists());
        return profilePath;
    }

    public static MinecraftAccount create(Setter setter) throws IOException {
        MinecraftAccount minecraftAccount = new MinecraftAccount();
        setter.writeAccount(minecraftAccount);
        minecraftAccount.mSaveLocation = pickAccountPath();
        minecraftAccount.save();
        Log.d(TAG, "create: new account saved at " + minecraftAccount.mSaveLocation.getName());
        return minecraftAccount;
    }

    /**
     * FIX 6: setCurrent now also verifies the file actually exists before saving
     */
    public static void setCurrent(MinecraftAccount minecraftAccount) {
        if (minecraftAccount == null || minecraftAccount.mSaveLocation == null) {
            Log.e(TAG, "setCurrent: tried to set null account!");
            return;
        }
        if (!minecraftAccount.mSaveLocation.exists()) {
            Log.e(TAG, "setCurrent: account file doesn't exist: "
                    + minecraftAccount.mSaveLocation.getName());
            return;
        }
        LauncherPreferences.DEFAULT_PREF
                .edit()
                .putString(PROFILE_PREF_FILE, minecraftAccount.mSaveLocation.getName())
                .apply();
        Log.d(TAG, "setCurrent: saved account -> " + minecraftAccount.mSaveLocation.getName());
    }

    public static void delete(MinecraftAccount minecraftAccount) {
        if (minecraftAccount == null || minecraftAccount.mSaveLocation == null) return;

        // FIX 7: If deleting the currently selected account, clear the selection too
        String currentSelected = getSelectedAccount();
        if (minecraftAccount.mSaveLocation.getName().equals(currentSelected)) {
            clearCurrentSelection();
            Log.d(TAG, "delete: cleared selection because current account was deleted");
        }

        boolean deleted = minecraftAccount.mSaveLocation.delete();
        Log.d(TAG, "delete: " + minecraftAccount.mSaveLocation.getName()
                + " deleted=" + deleted);
    }

    public interface Setter {
        void writeAccount(MinecraftAccount minecraftAccount) throws IOException;
    }
}
