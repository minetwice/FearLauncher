package net.kdt.pojavlaunch.quasar.backend;

import android.util.Log;

import net.kdt.pojavlaunch.quasar.capability.CapabilityTable;
import net.kdt.pojavlaunch.quasar.core.TranslatorTable;

public class QuasarCoreBackend implements RenderBackend {
    private static final String TAG = "Quasar-CoreBackend";
    private final CapabilityTable caps;
    private boolean ok;

    public QuasarCoreBackend(CapabilityTable caps) { this.caps = caps; }

    @Override public String getBackendName() { return "QuasarCore (FearCore)"; }

    @Override
    public void init() {
        Log.i(TAG, "Init " + getBackendName() + " | " + TranslatorTable.summary());
        ok = true;
    }

    @Override
    public boolean supportsFeature(String feature) {
        if (!ok) return false;
        TranslatorTable.Entry e = TranslatorTable.all().get(feature);
        if (e == null) return caps != null && caps.isFeatureSupported(feature);
        return e.kind != TranslatorTable.Kind.STUB;
    }

    @Override public void cleanup() { ok = false; }
}
