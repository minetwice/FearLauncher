package net.kdt.pojavlaunch.quasar.gl;

import android.util.Log;

import net.kdt.pojavlaunch.quasar.capability.CapabilityTable;
import net.kdt.pojavlaunch.quasar.gl.OpenGLFeatureCatalog.Entry;
import net.kdt.pojavlaunch.quasar.gl.OpenGLFeatureCatalog.Support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Bridges every catalogued desktop OpenGL feature onto the live Android device. */
public final class MobileFeatureBridge {
    private static final String TAG = "Quasar-GLBridge";

    public enum Outcome { PASSTHROUGH, JOIN, EMULATE, STRIP, UNKNOWN }

    public static final class Resolution {
        public final String feature;
        public final Outcome outcome;
        public final String mobileExtension;
        public final String reason;

        Resolution(String feature, Outcome outcome, String mobileExtension, String reason) {
            this.feature = feature;
            this.outcome = outcome;
            this.mobileExtension = mobileExtension;
            this.reason = reason;
        }

        @Override
        public String toString() {
            return feature + "→" + outcome
                    + (mobileExtension != null ? "(" + mobileExtension + ")" : "")
                    + " [" + reason + "]";
        }
    }

    private final GLVersionProfile profile;
    private final Set<String> glesExtLower;
    private final CapabilityTable caps;
    private final Map<String, Resolution> resolved = new HashMap<>();

    public MobileFeatureBridge(CapabilityTable caps, GLVersionProfile profile) {
        this.caps = caps;
        this.profile = profile != null ? profile
                : GLVersionProfile.fromGlesVersion(30, caps != null ? caps.getGpuVendor() : "unknown");
        this.glesExtLower = new HashSet<>();
        if (caps != null && caps.getGlesExtensions() != null) {
            for (String e : caps.getGlesExtensions()) {
                if (e != null) glesExtLower.add(e.toLowerCase(Locale.ROOT));
            }
        }
        resolveAll();
    }

    private void resolveAll() {
        for (Entry e : OpenGLFeatureCatalog.all().values()) {
            resolved.put(e.desktopName, resolveOne(e));
        }
        Log.i(TAG, "Resolved " + resolved.size() + " desktop GL features for " + profile);
    }

    private Resolution resolveOne(Entry e) {
        switch (e.support) {
            case NATIVE_ES30:
                if (profile.atLeast(30)) return ok(e, Outcome.PASSTHROUGH, null, "native ES 3.0+");
                return ok(e, Outcome.STRIP, null, "needs ES 3.0");
            case NATIVE_ES31:
                if (profile.atLeast(31)) return ok(e, Outcome.PASSTHROUGH, null, "native ES 3.1+");
                if (caps != null) {
                    if ("compute_shader".equals(e.desktopName) && caps.hasComputeShaders())
                        return ok(e, Outcome.PASSTHROUGH, null, "caps.compute");
                    if ("shader_image_load_store".equals(e.desktopName) && caps.hasImageLoadStore())
                        return ok(e, Outcome.PASSTHROUGH, null, "caps.image");
                    if ("shader_storage_buffer".equals(e.desktopName) && caps.hasSSBO())
                        return ok(e, Outcome.PASSTHROUGH, null, "caps.ssbo");
                }
                return ok(e, Outcome.STRIP, null, "needs ES 3.1");
            case NATIVE_ES32:
                if (profile.atLeast(32) || profile.aep) return ok(e, Outcome.PASSTHROUGH, null, "native ES 3.2/AEP");
                if (caps != null) {
                    if ("geometry_shader".equals(e.desktopName) && caps.hasGeometryShaders())
                        return ok(e, Outcome.PASSTHROUGH, null, "caps.geom");
                    if ("tessellation_shader".equals(e.desktopName) && caps.hasTessellation())
                        return ok(e, Outcome.PASSTHROUGH, null, "caps.tess");
                }
                if (e.mobileExt != null && hasExt(e.mobileExt))
                    return ok(e, Outcome.JOIN, e.mobileExt, "extension present");
                return ok(e, Outcome.STRIP, null, "needs ES 3.2");
            case JOIN:
                if (e.mobileExt != null && hasExt(e.mobileExt))
                    return ok(e, Outcome.JOIN, e.mobileExt, "mobile ext present");
                if (e.minEsVersion > 0 && profile.atLeast(e.minEsVersion))
                    return ok(e, Outcome.PASSTHROUGH, null, "core at ES " + e.minEsVersion);
                return ok(e, Outcome.STRIP, null, "join target missing → strip");
            case EMULATE:
                return ok(e, Outcome.EMULATE, null, e.note != null ? e.note : "emulate");
            case STRIP:
            default:
                return ok(e, Outcome.STRIP, null, e.note != null ? e.note : "strip");
        }
    }

    private static Resolution ok(Entry e, Outcome o, String mob, String reason) {
        return new Resolution(e.desktopName, o, mob, reason);
    }

    private boolean hasExt(String name) {
        if (name == null) return false;
        return glesExtLower.contains(name.toLowerCase(Locale.ROOT));
    }

    public Resolution get(String feature) {
        Resolution r = resolved.get(feature);
        if (r != null) return r;
        return new Resolution(feature, Outcome.UNKNOWN, null, "not in catalog");
    }

    public Outcome outcome(String feature) { return get(feature).outcome; }

    public boolean shouldStripExtension(String glExtensionName) {
        if (glExtensionName == null) return false;
        String want = glExtensionName.trim();
        for (Entry e : OpenGLFeatureCatalog.all().values()) {
            if (want.equals(e.desktopExt)) {
                Outcome o = outcome(e.desktopName);
                return o == Outcome.STRIP || o == Outcome.EMULATE;
            }
        }
        String u = want.toUpperCase(Locale.ROOT);
        if (u.startsWith("GL_ARB_") || u.startsWith("GL_NV_") || u.startsWith("GL_AMD_")) return true;
        return false;
    }

    public String joinExtensionOrNull(String glExtensionName) {
        if (glExtensionName == null) return null;
        for (Entry e : OpenGLFeatureCatalog.all().values()) {
            if (glExtensionName.equals(e.desktopExt)) {
                Resolution r = get(e.desktopName);
                if (r.outcome == Outcome.JOIN) return r.mobileExtension;
            }
        }
        return null;
    }

    public List<Resolution> allResolutions() {
        return Collections.unmodifiableList(new ArrayList<>(resolved.values()));
    }

    public GLVersionProfile getProfile() { return profile; }

    public String summary() {
        int pass = 0, join = 0, emu = 0, strip = 0;
        for (Resolution r : resolved.values()) {
            switch (r.outcome) {
                case PASSTHROUGH: pass++; break;
                case JOIN: join++; break;
                case EMULATE: emu++; break;
                case STRIP: strip++; break;
                default: break;
            }
        }
        return "GLBridge " + profile + " pass=" + pass + " join=" + join
                + " emu=" + emu + " strip=" + strip;
    }
}
