package net.kdt.pojavlaunch.modloaders.modpacks;

import androidx.annotation.NonNull;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModpackApi;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Works out, for each search result, whether it runs on the version this instance is
 * on - and what range of versions it does cover.
 *
 * Search results carry no version data, only the detail endpoint has it, so this asks
 * for details a handful at a time and writes the answers back onto the items. Nothing
 * here is fatal: an item whose details fail to arrive is simply left unmarked.
 */
public final class ModVersionEnricher {

    /** How many detail requests may be in flight at once. */
    private static final int CONCURRENCY = 4;
    /** A page is enriched up to this many items; the rest keep whatever they had. */
    private static final int MAX_ITEMS = 40;

    public interface Callback {
        /** Always on the UI thread, and always called exactly once. */
        void onEnriched(ModItem[] items);
    }

    private ModVersionEnricher() {}

    public static void enrich(ModpackApi api, ModItem[] items, String targetVersion, Callback callback) {
        if (items == null || items.length == 0) {
            callback.onEnriched(items);
            return;
        }
        int count = Math.min(items.length, MAX_ITEMS);
        final AtomicInteger remaining = new AtomicInteger(count);
        final AtomicInteger slot = new AtomicInteger(0);
        for (int worker = 0; worker < Math.min(CONCURRENCY, count); worker++) {
            PojavApplication.sExecutorService.execute(() -> {
                while (true) {
                    int index = slot.getAndIncrement();
                    if (index >= count) break;
                    try {
                        ModDetail detail = api.getModDetails(items[index]);
                        if (detail != null) apply(items[index], detail, targetVersion);
                    } catch (Throwable ignored) { }
                    if (remaining.decrementAndGet() == 0) {
                        Tools.runOnUiThread(() -> callback.onEnriched(items));
                    }
                }
            });
        }
    }

    private static void apply(ModItem item, ModDetail detail, String targetVersion) {
        String[] versions = detail.mcVersionNames;
        if (versions == null || versions.length == 0) return;

        boolean matches = false;
        String lowest = null, highest = null;
        for (String v : versions) {
            if (v == null || v.isEmpty()) continue;
            if (targetVersion != null && v.equalsIgnoreCase(targetVersion)) matches = true;
            if (lowest == null || compareVersions(v, lowest) < 0) lowest = v;
            if (highest == null || compareVersions(v, highest) > 0) highest = v;
        }
        if (lowest == null) return;

        item.recommended = matches;
        item.versionRange = lowest.equals(highest) ? lowest : lowest + " \u2013 " + highest;
    }

    /** Dotted release numbers, with anything trailing compared as text. */
    static int compareVersions(@NonNull String a, @NonNull String b) {
        String[] pa = a.split("[.\\-]");
        String[] pb = b.split("[.\\-]");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            String sa = i < pa.length ? pa[i] : "0";
            String sb = i < pb.length ? pb[i] : "0";
            Integer na = asInt(sa), nb = asInt(sb);
            int c;
            if (na != null && nb != null) c = Integer.compare(na, nb);
            else c = sa.compareToIgnoreCase(sb);
            if (c != 0) return c;
        }
        return 0;
    }

    private static Integer asInt(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }
}
