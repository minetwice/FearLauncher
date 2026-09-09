#ifndef TURBO_V1_SMOOTHNESS_MANAGER_H
#define TURBO_V1_SMOOTHNESS_MANAGER_H

#include <GLES3/gl32.h>
#include <cstdint>
#include <mutex>
#include <vector>
#include <chrono>
#include <queue>

namespace turbo_v1 {

// ============================================================================
// TurboV1 Smoothness Manager - Ultimate Smoothness System
// Eliminates stutter, lag spikes, and input delay for 200+ FPS butter-smooth gameplay
// ============================================================================

class SmoothnessManager {
public:
    static SmoothnessManager& get_instance();
    
    // Initialize the smoothness manager
    void initialize();
    
    // Enable/disable the smoothness system
    void set_enabled(bool enabled);
    bool is_enabled() const;
    
    // Frame pacing control
    void set_frame_pacing_enabled(bool enabled);
    void set_target_frame_time(float ms);  // Target frame time in milliseconds
    void set_max_frame_time(float ms);    // Maximum allowed frame time
    
    // Stutter remover
    void set_stutter_remover_enabled(bool enabled);
    void set_stutter_threshold(float ms);   // Threshold for stutter detection
    void set_stutter_compensation(float value); // How much to compensate
    
    // Input lag reduction
    void set_input_lag_reduction_enabled(bool enabled);
    void set_input_prediction_enabled(bool enabled);
    void set_input_buffer_size(int frames); // Input buffer size for prediction
    
    // Triple buffering for smooth rendering
    void set_triple_buffering_enabled(bool enabled);
    
    // Call at the beginning of each frame
    void begin_frame();
    
    // Call at the end of each frame
    void end_frame();
    
    // Record frame time
    void record_frame_time(float ms);
    
    // Get current smoothness metrics
    float get_current_fps() const;
    float get_average_frame_time() const;
    float get_frame_time_std_dev() const;  // Standard deviation (lower = smoother)
    float get_stutter_score() const;       // 0 = perfect, 1 = bad stutter
    
    // Wait for optimal frame timing (for frame pacing)
    void wait_for_optimal_timing();
    
    // Reset to defaults
    void reset_to_defaults();
    
    // Get settings summary
    std::string get_settings_summary() const;
    
private:
    SmoothnessManager();
    ~SmoothnessManager();
    
    // Prevent copying
    SmoothnessManager(const SmoothnessManager&) = delete;
    SmoothnessManager& operator=(const SmoothnessManager&) = delete;
    
    // Settings
    struct Settings {
        bool enabled;
        
        // Frame pacing
        bool frame_pacing_enabled;
        float target_frame_time_ms;
        float max_frame_time_ms;
        
        // Stutter remover
        bool stutter_remover_enabled;
        float stutter_threshold_ms;
        float stutter_compensation;
        
        // Input lag reduction
        bool input_lag_reduction_enabled;
        bool input_prediction_enabled;
        int input_buffer_size;
        
        // Triple buffering
        bool triple_buffering_enabled;
    } m_settings;
    
    // Frame timing history
    struct FrameTiming {
        int64_t timestamp_ns;
        float frame_time_ms;
        float predicted_next_frame_ms;
    };
    
    std::vector<FrameTiming> m_frame_history;
    static const int MAX_FRAME_HISTORY = 100;
    
    // Statistics
    struct Statistics {
        float current_fps;
        float average_frame_time_ms;
        float frame_time_std_dev;
        float stutter_score;
        int64_t total_frames;
        int64_t stutter_events;
    } m_stats;
    
    // Input buffer for prediction
    struct InputState {
        int64_t timestamp_ns;
        float mouse_x;
        float mouse_y;
        bool buttons[8];
        float axis[4];
    };
    
    std::queue<InputState> m_input_buffer;
    
    // Timing
    int64_t m_last_frame_time_ns;
    int64_t m_frame_start_time_ns;
    int64_t m_next_frame_target_ns;
    
    std::mutex m_mutex;
    
    // Helper methods
    void update_statistics();
    float calculate_std_dev() const;
    float calculate_stutter_score() const;
};

// Global smoothness manager instance
SmoothnessManager& get_smoothness_manager();

// C interface for JNI
extern "C" {
    void turbo_v1_smoothness_init();
    void turbo_v1_smoothness_set_enabled(bool enabled);
    void turbo_v1_smoothness_set_frame_pacing_enabled(bool enabled);
    void turbo_v1_smoothness_set_target_frame_time(float ms);
    void turbo_v1_smoothness_set_stutter_remover_enabled(bool enabled);
    void turbo_v1_smoothness_set_input_lag_reduction_enabled(bool enabled);
    void turbo_v1_smoothness_begin_frame();
    void turbo_v1_smoothness_end_frame();
    void turbo_v1_smoothness_reset();
    float turbo_v1_smoothness_get_stutter_score();
}

} // namespace turbo_v1

#endif // TURBO_V1_SMOOTHNESS_MANAGER_H
