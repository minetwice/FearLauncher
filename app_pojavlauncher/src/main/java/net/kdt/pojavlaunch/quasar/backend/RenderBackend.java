package net.kdt.pojavlaunch.quasar.backend;

/**
 * Interface for Quasar render backends.
 * Each backend implements this to provide rendering services.
 */
public interface RenderBackend {
    /**
     * Get the name of this backend (e.g. "Zink", "GL4ES").
     */
    String getBackendName();

    /**
     * Initialize the backend. Called once after selection.
     */
    void init();

    /**
     * Check if this backend supports a given GL feature.
     * @param feature The feature name (e.g. "geometry_shader", "compute_shader")
     * @return true if supported
     */
    boolean supportsFeature(String feature);

    /**
     * Clean up backend resources.
     */
    void cleanup();
}
