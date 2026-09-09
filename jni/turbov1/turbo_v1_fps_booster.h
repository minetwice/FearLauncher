#ifndef TURBO_V1_FPS_BOOSTER_H
#define TURBO_V1_FPS_BOOSTER_H

#include <GLES3/gl32.h>
#include <EGL/egl.h>
#include <cstdint>
#include <mutex>
#include <atomic>

namespace turbo_v1 {

// ============================================================================
// TurboV2 NextGen FPS Booster & Quality Enhancer System
// Achieves 200+ FPS with extreme smoothness & vibrant visual enhancement
// ============================================================================

class FPSBooster {
public:
    static FPSBooster& get_instance();
    
    FPSBooster();
    ~FPSBooster();

    // Initialize FPS booster with target FPS
    void initialize(int target_fps = 240);
    
    // Call before frame render
    void begin_frame();
    
    // Call after frame render
    void end_frame();
    
    // Get current FPS
    float get_current_fps() const;
    
    // Get frame time in milliseconds
    float get_frame_time_ms() const;
    
    // Enable/disable FPS unlocker
    void set_fps_unlocked(bool unlocked);
    
    // Enable/disable frame pacing
    void set_frame_pacing_enabled(bool enabled);
    
    // Enable/disable entity culling optimization
    void set_entity_optimization_enabled(bool enabled);
    
    // Enable/disable vsync
    void set_vsync_enabled(bool enabled);
    
    // Set maximum FPS limit (0 = unlimited)
    void set_max_fps(int max_fps);
    
    // Force high performance GPU mode
    void force_high_performance_mode();
    
    // Optimize for specific GPU
    void optimize_for_gpu(const char* gpu_name);
    
    // Entity render optimization
    void optimize_entity_rendering();
    
private:
    void detect_and_optimize_gpu();

    // Prevent copying
    FPSBooster(const FPSBooster&) = delete;
    FPSBooster& operator=(const FPSBooster&) = delete;
    
    // Frame timing
    int64_t m_frame_start_time;
    int64_t m_frame_end_time;
    int64_t m_last_fps_update_time;
    int m_frame_count;
    float m_current_fps;
    float m_frame_time_ms;
    
    // Settings
    int m_target_fps;
    int m_max_fps;
    bool m_fps_unlocked;
    bool m_frame_pacing_enabled;
    bool m_entity_optimization_enabled;
    bool m_vsync_enabled;
    
    // GPU specific optimizations
    enum class GPUVendor {
        UNKNOWN,
        NVIDIA,
        AMD,
        INTEL,
        QUALCOMM,
        ARM,
        MALI,
        POWERVR
    };
    
    GPUVendor m_current_gpu;
    
    // Entity optimization state
    struct EntityOptimization {
        bool frustum_culling_enabled;
        bool occlusion_culling_enabled;
        bool lod_enabled;
        bool batch_rendering_enabled;
        int max_visible_entities;
        float cull_distance_multiplier;
    } m_entity_opt;
    
    // Performance metrics
    struct PerformanceMetrics {
        float average_fps;
        float min_fps;
        float max_fps;
        int64_t total_frames;
        float gpu_utilization;
    } m_metrics;
    
    mutable std::mutex m_mutex;
};

// Global FPS booster instance access
FPSBooster& get_fps_booster();

// FPS booster hook functions
extern "C" {
    void turbo_v1_fps_booster_init(int target_fps);
    void turbo_v1_fps_booster_begin_frame();
    void turbo_v1_fps_booster_end_frame();
    void turbo_v1_fps_booster_set_unlocked(bool unlocked);
    void turbo_v1_fps_booster_set_vsync(bool enabled);
    void turbo_v1_fps_booster_set_max_fps(int max_fps);
}

} // namespace turbo_v1

#endif // TURBO_V1_FPS_BOOSTER_H
