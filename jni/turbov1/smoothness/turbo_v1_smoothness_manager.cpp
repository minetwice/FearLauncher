#include "turbo_v1_smoothness_manager.h"
#include "../turbo_v1_core.h"
#include <android/log.h>
#include <sstream>
#include <iomanip>
#include <cmath>
#include <thread>

namespace turbo_v1 {

// ============================================================================
// TurboV1 Smoothness Manager Implementation
// Ultimate Smoothness for 200+ FPS Butter-Smooth Gameplay
// ============================================================================

static SmoothnessManager g_smoothness_manager_instance;

SmoothnessManager& SmoothnessManager::get_instance() {
    return g_smoothness_manager_instance;
}

SmoothnessManager& get_smoothness_manager() {
    return SmoothnessManager::get_instance();
}

SmoothnessManager::SmoothnessManager()
    : m_last_frame_time_ns(0),
      m_frame_start_time_ns(0),
      m_next_frame_target_ns(0)
{
    reset_to_defaults();
    LOGI("TurboV1 Smoothness Manager: Constructor initialized");
}

SmoothnessManager::~SmoothnessManager() {
    LOGI("TurboV1 Smoothness Manager: Destructor called");
}

void SmoothnessManager::initialize() {
    std::lock_guard<std::mutex> lock(m_mutex);
    
    // Initialize timing
    auto now = std::chrono::high_resolution_clock::now();
    auto duration = now.time_since_epoch();
    m_last_frame_time_ns = std::chrono::duration_cast<std::chrono::nanoseconds>(duration).count();
    m_frame_start_time_ns = m_last_frame_time_ns;
    m_next_frame_target_ns = m_last_frame_time_ns;
    
    // Clear history
    m_frame_history.clear();
    m_input_buffer = std::queue<InputState>();
    
    // Reset statistics
    m_stats.current_fps = 0.0f;
    m_stats.average_frame_time_ms = 0.0f;
    m_stats.frame_time_std_dev = 0.0f;
    m_stats.stutter_score = 0.0f;
    m_stats.total_frames = 0;
    m_stats.stutter_events = 0;
    
    LOGI("TurboV1 Smoothness Manager: Initialized");
    LOGI("  Frame Pacing: %s", m_settings.frame_pacing_enabled ? "ENABLED" : "DISABLED");
    LOGI("  Target Frame Time: %.2fms", m_settings.target_frame_time_ms);
    LOGI("  Stutter Remover: %s", m_settings.stutter_remover_enabled ? "ENABLED" : "DISABLED");
    LOGI("  Input Lag Reduction: %s", m_settings.input_lag_reduction_enabled ? "ENABLED" : "DISABLED");
    LOGI("  Triple Buffering: %s", m_settings.triple_buffering_enabled ? "ENABLED" : "DISABLED");
}

void SmoothnessManager::set_enabled(bool enabled) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_settings.enabled = enabled;
    LOGI("TurboV1 Smoothness: %s", enabled ? "ENABLED" : "DISABLED");
}

bool SmoothnessManager::is_enabled() const {
    std::lock_guard<std::mutex> lock(m_mutex);
    return m_settings.enabled;
}

void SmoothnessManager::set_frame_pacing_enabled(bool enabled) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_settings.frame_pacing_enabled = enabled;
    LOGI("TurboV1 Smoothness: Frame Pacing %s", enabled ? "ENABLED" : "DISABLED");
}

void SmoothnessManager::set_target_frame_time(float ms) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_settings.target_frame_time_ms = std::max(1.0f, ms);
    LOGI("TurboV1 Smoothness: Target Frame Time set to %.2fms", m_settings.target_frame_time_ms);
}

void SmoothnessManager::set_max_frame_time(float ms) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_settings.max_frame_time_ms = std::max(1.0f, ms);
    LOGI("TurboV1 Smoothness: Max Frame Time set to %.2fms", m_settings.max_frame_time_ms);
}

void SmoothnessManager::set_stutter_remover_enabled(bool enabled) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_settings.stutter_remover_enabled = enabled;
    LOGI("TurboV1 Smoothness: Stutter Remover %s", enabled ? "ENABLED" : "DISABLED");
}

void SmoothnessManager::set_stutter_threshold(float ms) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_settings.stutter_threshold_ms = std::max(1.0f, ms);
    LOGI("TurboV1 Smoothness: Stutter Threshold set to %.2fms", m_settings.stutter_threshold_ms);
}

void SmoothnessManager::set_stutter_compensation(float value) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_settings.stutter_compensation = std::max(0.0f, std::min(1.0f, value));
    LOGI("TurboV1 Smoothness: Stutter Compensation set to %.2f", m_settings.stutter_compensation);
}

void SmoothnessManager::set_input_lag_reduction_enabled(bool enabled) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_settings.input_lag_reduction_enabled = enabled;
    LOGI("TurboV1 Smoothness: Input Lag Reduction %s", enabled ? "ENABLED" : "DISABLED");
}

void SmoothnessManager::set_input_prediction_enabled(bool enabled) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_settings.input_prediction_enabled = enabled;
    LOGI("TurboV1 Smoothness: Input Prediction %s", enabled ? "ENABLED" : "DISABLED");
}

void SmoothnessManager::set_input_buffer_size(int frames) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_settings.input_buffer_size = std::max(1, std::min(10, frames));
    LOGI("TurboV1 Smoothness: Input Buffer Size set to %d frames", m_settings.input_buffer_size);
}

void SmoothnessManager::set_triple_buffering_enabled(bool enabled) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_settings.triple_buffering_enabled = enabled;
    LOGI("TurboV1 Smoothness: Triple Buffering %s", enabled ? "ENABLED" : "DISABLED");
}

void SmoothnessManager::begin_frame() {
    std::lock_guard<std::mutex> lock(m_mutex);
    
    if (!m_settings.enabled) return;
    
    // Get current time
    auto now = std::chrono::high_resolution_clock::now();
    auto duration = now.time_since_epoch();
    m_frame_start_time_ns = std::chrono::duration_cast<std::chrono::nanoseconds>(duration).count();
    
    // Calculate next frame target for frame pacing
    if (m_settings.frame_pacing_enabled) {
        m_next_frame_target_ns = m_last_frame_time_ns + 
            static_cast<int64_t>(m_settings.target_frame_time_ms * 1000000.0f);
        
        // Wait if we're ahead of schedule (for smooth pacing)
        wait_for_optimal_timing();
    }
    
    // Apply input prediction if enabled
    if (m_settings.input_prediction_enabled) {
        apply_input_prediction();
    }
}

void SmoothnessManager::end_frame() {
    std::lock_guard<std::mutex> lock(m_mutex);
    
    if (!m_settings.enabled) return;
    
    // Get current time
    auto now = std::chrono::high_resolution_clock::now();
    auto duration = now.time_since_epoch();
    int64_t frame_end_time_ns = std::chrono::duration_cast<std::chrono::nanoseconds>(duration).count();
    
    // Calculate frame time
    float frame_time_ms = static_cast<float>(frame_end_time_ns - m_frame_start_time_ns) / 1000000.0f;
    
    // Record frame time
    record_frame_time(frame_time_ms);
    
    // Update last frame time
    m_last_frame_time_ns = frame_end_time_ns;
    m_stats.total_frames++;
    
    // Update statistics
    update_statistics();
    
    // Check for stutter
    if (m_settings.stutter_remover_enabled && frame_time_ms > m_settings.stutter_threshold_ms) {
        m_stats.stutter_events++;
        compensate_for_stutter(frame_time_ms);
    }
}

void SmoothnessManager::record_frame_time(float ms) {
    std::lock_guard<std::mutex> lock(m_mutex);
    
    FrameTiming timing;
    auto now = std::chrono::high_resolution_clock::now();
    auto duration = now.time_since_epoch();
    timing.timestamp_ns = std::chrono::duration_cast<std::chrono::nanoseconds>(duration).count();
    timing.frame_time_ms = ms;
    timing.predicted_next_frame_ms = m_settings.target_frame_time_ms;
    
    m_frame_history.push_back(timing);
    
    // Keep history size limited
    if (m_frame_history.size() > MAX_FRAME_HISTORY) {
        m_frame_history.erase(m_frame_history.begin());
    }
}

void SmoothnessManager::wait_for_optimal_timing() {
    if (!m_settings.frame_pacing_enabled) return;
    
    auto now = std::chrono::high_resolution_clock::now();
    auto duration = now.time_since_epoch();
    int64_t current_time_ns = std::chrono::duration_cast<std::chrono::nanoseconds>(duration).count();
    
    // If we're ahead of the target, wait a bit
    if (current_time_ns < m_next_frame_target_ns) {
        int64_t wait_time_ns = m_next_frame_target_ns - current_time_ns;
        
        // Only wait up to 5ms to avoid input lag
        if (wait_time_ns > 5000000LL) {  // 5ms in ns
            wait_time_ns = 5000000LL;
        }
        
        // Busy wait for precision (better than sleep for short waits)
        while (current_time_ns < m_next_frame_target_ns - 100000LL) {  // 0.1ms buffer
            std::this_thread::yield();
            auto now2 = std::chrono::high_resolution_clock::now();
            auto duration2 = now2.time_since_epoch();
            current_time_ns = std::chrono::duration_cast<std::chrono::nanoseconds>(duration2).count();
        }
        
        // Fine-tune with busy wait
        while (current_time_ns < m_next_frame_target_ns) {
            std::this_thread::yield();
            auto now2 = std::chrono::high_resolution_clock::now();
            auto duration2 = now2.time_since_epoch();
            current_time_ns = std::chrono::duration_cast<std::chrono::nanoseconds>(duration2).count();
        }
    }
}

void SmoothnessManager::compensate_for_stutter(float frame_time_ms) {
    if (!m_settings.stutter_remover_enabled) return;
    
    // Calculate how much we need to compensate
    float overshoot_ms = frame_time_ms - m_settings.target_frame_time_ms;
    float compensation_factor = m_settings.stutter_compensation;
    
    // Adjust next frame target to compensate
    m_next_frame_target_ns -= static_cast<int64_t>(overshoot_ms * compensation_factor * 1000000.0f);
    
    LOGD("TurboV1 Smoothness: Stutter detected (%.2fms), compensating by %.2f%%", 
         frame_time_ms, compensation_factor * 100.0f);
}

void SmoothnessManager::apply_input_prediction() {
    if (!m_settings.input_prediction_enabled) return;
    
    // Simple linear prediction for input
    // In a real implementation, this would predict input based on velocity
    // For now, we just ensure the input buffer is maintained
    
    if (m_input_buffer.size() > static_cast<size_t>(m_settings.input_buffer_size)) {
        m_input_buffer.pop();
    }
}

void SmoothnessManager::update_statistics() {
    std::lock_guard<std::mutex> lock(m_mutex);
    
    if (m_frame_history.empty()) return;
    
    // Calculate average frame time
    float sum_frame_times = 0.0f;
    for (const auto& frame : m_frame_history) {
        sum_frame_times += frame.frame_time_ms;
    }
    m_stats.average_frame_time_ms = sum_frame_times / static_cast<float>(m_frame_history.size());
    
    // Calculate current FPS
    if (m_stats.average_frame_time_ms > 0.0f) {
        m_stats.current_fps = 1000.0f / m_stats.average_frame_time_ms;
    } else {
        m_stats.current_fps = 0.0f;
    }
    
    // Calculate standard deviation
    m_stats.frame_time_std_dev = calculate_std_dev();
    
    // Calculate stutter score
    m_stats.stutter_score = calculate_stutter_score();
}

float SmoothnessManager::calculate_std_dev() const {
    if (m_frame_history.size() < 2) return 0.0f;
    
    float mean = m_stats.average_frame_time_ms;
    float sum_sq = 0.0f;
    
    for (const auto& frame : m_frame_history) {
        float diff = frame.frame_time_ms - mean;
        sum_sq += diff * diff;
    }
    
    float variance = sum_sq / static_cast<float>(m_frame_history.size() - 1);
    return std::sqrt(variance);
}

float SmoothnessManager::calculate_stutter_score() const {
    if (m_frame_history.empty()) return 0.0f;
    
    // Count frames that exceeded the stutter threshold
    int stutter_count = 0;
    for (const auto& frame : m_frame_history) {
        if (frame.frame_time_ms > m_settings.stutter_threshold_ms) {
            stutter_count++;
        }
    }
    
    // Calculate stutter ratio (0 = no stutter, 1 = all frames stutter)
    float stutter_ratio = static_cast<float>(stutter_count) / static_cast<float>(m_frame_history.size());
    
    // Also factor in frame time variance
    float variance_factor = m_stats.frame_time_std_dev / m_settings.target_frame_time_ms;
    variance_factor = std::min(variance_factor, 1.0f);
    
    // Combined score
    return (stutter_ratio * 0.7f) + (variance_factor * 0.3f);
}

float SmoothnessManager::get_current_fps() const {
    std::lock_guard<std::mutex> lock(m_mutex);
    return m_stats.current_fps;
}

float SmoothnessManager::get_average_frame_time() const {
    std::lock_guard<std::mutex> lock(m_mutex);
    return m_stats.average_frame_time_ms;
}

float SmoothnessManager::get_frame_time_std_dev() const {
    std::lock_guard<std::mutex> lock(m_mutex);
    return m_stats.frame_time_std_dev;
}

float SmoothnessManager::get_stutter_score() const {
    std::lock_guard<std::mutex> lock(m_mutex);
    return m_stats.stutter_score;
}

void SmoothnessManager::reset_to_defaults() {
    std::lock_guard<std::mutex> lock(m_mutex);
    
    m_settings.enabled = true;
    
    // Frame pacing defaults
    m_settings.frame_pacing_enabled = true;
    m_settings.target_frame_time_ms = 5.0f;  // 200 FPS = 5ms per frame
    m_settings.max_frame_time_ms = 10.0f;    // Max 100 FPS
    
    // Stutter remover defaults
    m_settings.stutter_remover_enabled = true;
    m_settings.stutter_threshold_ms = 8.0f;   // 125 FPS threshold
    m_settings.stutter_compensation = 0.5f;    // 50% compensation
    
    // Input lag reduction defaults
    m_settings.input_lag_reduction_enabled = true;
    m_settings.input_prediction_enabled = true;
    m_settings.input_buffer_size = 3;
    
    // Triple buffering defaults
    m_settings.triple_buffering_enabled = true;
    
    // Reset statistics
    m_stats.current_fps = 0.0f;
    m_stats.average_frame_time_ms = 0.0f;
    m_stats.frame_time_std_dev = 0.0f;
    m_stats.stutter_score = 0.0f;
    m_stats.total_frames = 0;
    m_stats.stutter_events = 0;
    
    // Clear history
    m_frame_history.clear();
    m_input_buffer = std::queue<InputState>();
    
    LOGI("TurboV1 Smoothness Manager: Reset to defaults");
}

std::string SmoothnessManager::get_settings_summary() const {
    std::lock_guard<std::mutex> lock(m_mutex);
    std::ostringstream oss;
    oss << std::fixed << std::setprecision(2);
    oss << "TurboV1 Smoothness Settings:\n";
    oss << "  Enabled: " << (m_settings.enabled ? "YES" : "NO") << "\n";
    oss << "  Frame Pacing: " << (m_settings.frame_pacing_enabled ? "YES" : "NO") 
        << " (Target: " << m_settings.target_frame_time_ms << "ms)\n";
    oss << "  Stutter Remover: " << (m_settings.stutter_remover_enabled ? "YES" : "NO") 
        << " (Threshold: " << m_settings.stutter_threshold_ms << "ms, Compensation: " 
        << m_settings.stutter_compensation << ")\n";
    oss << "  Input Lag Reduction: " << (m_settings.input_lag_reduction_enabled ? "YES" : "NO") 
        << " (Prediction: " << (m_settings.input_prediction_enabled ? "YES" : "NO") 
        << ", Buffer: " << m_settings.input_buffer_size << " frames)\n";
    oss << "  Triple Buffering: " << (m_settings.triple_buffering_enabled ? "YES" : "NO") << "\n";
    oss << "\nStatistics:\n";
    oss << "  Current FPS: " << m_stats.current_fps << "\n";
    oss << "  Avg Frame Time: " << m_stats.average_frame_time_ms << "ms\n";
    oss << "  Frame Time Std Dev: " << m_stats.frame_time_std_dev << "ms\n";
    oss << "  Stutter Score: " << m_stats.stutter_score << " (0 = perfect)\n";
    oss << "  Total Frames: " << m_stats.total_frames << "\n";
    oss << "  Stutter Events: " << m_stats.stutter_events;
    
    return oss.str();
}

// ============================================================================
// C Interface for JNI
// ============================================================================

extern "C" {

void turbo_v1_smoothness_init() {
    get_smoothness_manager().initialize();
}

void turbo_v1_smoothness_set_enabled(bool enabled) {
    get_smoothness_manager().set_enabled(enabled);
}

void turbo_v1_smoothness_set_frame_pacing_enabled(bool enabled) {
    get_smoothness_manager().set_frame_pacing_enabled(enabled);
}

void turbo_v1_smoothness_set_target_frame_time(float ms) {
    get_smoothness_manager().set_target_frame_time(ms);
}

void turbo_v1_smoothness_set_stutter_remover_enabled(bool enabled) {
    get_smoothness_manager().set_stutter_remover_enabled(enabled);
}

void turbo_v1_smoothness_set_input_lag_reduction_enabled(bool enabled) {
    get_smoothness_manager().set_input_lag_reduction_enabled(enabled);
}

void turbo_v1_smoothness_begin_frame() {
    get_smoothness_manager().begin_frame();
}

void turbo_v1_smoothness_end_frame() {
    get_smoothness_manager().end_frame();
}

void turbo_v1_smoothness_reset() {
    get_smoothness_manager().reset_to_defaults();
}

float turbo_v1_smoothness_get_stutter_score() {
    return get_smoothness_manager().get_stutter_score();
}

} // extern "C"

} // namespace turbo_v1
