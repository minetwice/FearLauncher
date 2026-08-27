package net.kdt.pojavlaunch.quasar.backend;

import android.util.Log;

import net.kdt.pojavlaunch.quasar.capability.CapabilityTable;

/** Quasar always selects QuasarCore — never LTW / Zink / Turnip. */
public class BackendSelector {
    private static final String TAG = "Quasar-BackendSelector";

    public static RenderBackend select(CapabilityTable table) {
        Log.i(TAG, "Selecting QuasarCore (first-party FearCore translator)");
        return new QuasarCoreBackend(table);
    }
}
