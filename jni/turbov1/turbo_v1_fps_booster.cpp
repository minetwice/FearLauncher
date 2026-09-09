#include "turbo_v1_fps_booster.h"
#include "turbo_v1_core.h"
#include <android/log.h>
#include <chrono>
#include <thread>
#include <string>
#include <algorithm>

namespace turbo_v1 {

// ============================================================================
// TurboV2 NextGen FPS Booster & Quality Enhancer Implementation
// Extreme Performance for 200+ FPS Cross & Ultra Smoothness
// ============================================================================

static FPSBooster g_fps_booster_instance;

FPSBooster& FPSBooster::get_instance() {
    return g_fps_booster_instance;
}

FPSBooster& get_fps_booster() {
    return FPSBooster::get_instance();
}

FPSBooster::FPSBooster()
    : m_frame_start_time(0),
      m_frame_end_time(0),
      m_last_fps_update_time(0),
      m_frame_count(0),
      m_current_fps(0.0f),
      m_frame_time_ms(0.0f),
      m_target_fps(240),
      m_max_fps(0),  // 0 = unlimited
      m_fps_unlocked(true),
      m_frame_pacing_enabled(false), // Disable pacing overhead for maximum 200+ FPS
      m_entity_optimization_enabled(true),
      m_vsync_enabled(false),
      m_current_gpu(GPUVendor::UNKNOWN)
{
    // Initialize entity optimization defaults for extreme speed
    m_entity_opt.frustum_culling_enabled = true;
    m_entity_opt.occlusion_culling_enabled = true;
    m_entity_opt.lod_enabled = true;
    m_entity_opt.batch_rendering_enabled = true;
    m_entity_opt.max_visible_entities = 10000;
    m_entity_opt.cull_distance_multiplier = 2.0f;
    
    // Initialize performance metrics
    m_metrics.average_fps = 0.0f;
    m_metrics.min_fps = 999.0f;
    m_metrics.max_fps = 0.0f;
    m_metrics.total_frames = 0;
    m_metrics.gpu_utilization = 0.0f;
    
    LOGI("TurboV2 FPS Booster: Engine initialized with 200+ FPS target");
}

FPSBooster::~FPSBooster() {
    LOGI("TurboV2 FPS Booster: Destructor called");
}

void FPSBooster::initialize(int target_fps) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_target_fps = target_fps;
    m_max_fps = 0;  // Unlimited for maximum performance
    
    detect_and_optimize_gpu();
    
    LOGI("TurboV2 FPS Booster: Initialized with target FPS: %d", target_fps);
    LOGI("TurboV2 FPS Booster: FPS Unlocked: %s", m_fps_unlocked ? "YES" : "NO");
    LOGI("TurboV2 FPS Booster: VSync: %s", m_vsync_enabled ? "ENABLED" : "DISABLED");
    LOGI("TurboV2 FPS Booster: Entity Optimization: %s", m_entity_optimization_enabled ? "ENABLED" : "DISABLED");
}

void FPSBooster::begin_frame() {
    std::lock_guard<std::mutex> lock(m_mutex);
    
    // Get current time in nanoseconds
    auto now = std::chrono::high_resolution_clock::now();
    auto duration = now.time_since_epoch();
    m_frame_start_time = std::chrono::duration_cast<std::chrono::nanoseconds>(duration).count();
    
    if (m_entity_optimization_enabled) {
        optimize_entity_rendering();
    }
}

void FPSBooster::end_frame() {
    std::lock_guard<std::mutex> lock(m_mutex);
    
    auto now = std::chrono::high_resolution_clock::now();
    auto duration = now.time_since_epoch();
    m_frame_end_time = std::chrono::duration_cast<std::chrono::nanoseconds>(duration).count();
    
    float frame_time_ns = static_cast<float>(m_frame_end_time - m_frame_start_time);
    m_frame_time_ms = frame_time_ns / 1000000.0f;
    
    m_frame_count++;
    m_metrics.total_frames++;
    
    // Update FPS statistics every second
    if (m_frame_end_time - m_last_fps_update_time >= 1000000000LL) {
        m_current_fps = static_cast<float>(m_frame_count) * 1000000000.0f / 
                       static_cast<float>(m_frame_end_time - m_last_fps_update_time);
        
        m_metrics.average_fps = (m_metrics.average_fps * 0.9f) + (m_current_fps * 0.1f);
        m_metrics.min_fps = std::min(m_metrics.min_fps, m_current_fps);
        m_metrics.max_fps = std::max(m_metrics.max_fps, m_current_fps);
        
        if (m_current_fps >= 150.0f) {
            LOGI("TurboV2 FPS Booster: Current FPS: %.1f (Max: %.1f, Avg: %.1f)",
                m_current_fps, m_metrics.max_fps, m_metrics.average_fps);
        }
        
        m_frame_count = 0;
        m_last_fps_update_time = m_frame_end_time;
    }
    
    // Flush pipeline non-blocking for unlocked extreme FPS
    if (m_fps_unlocked && !m_vsync_enabled) {
        glFlush();
    }
}

float FPSBooster::get_current_fps() const {
    std::lock_guard<std::mutex> lock(m_mutex);
    return m_current_fps;
}

float FPSBooster::get_frame_time_ms() const {
    std::lock_guard<std::mutex> lock(m_mutex);
    return m_frame_time_ms;
}

void FPSBooster::set_fps_unlocked(bool unlocked) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_fps_unlocked = unlocked;
    LOGI("TurboV2 FPS Booster: FPS Unlocked set to: %s", unlocked ? "YES" : "NO");
}

void FPSBooster::set_frame_pacing_enabled(bool enabled) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_frame_pacing_enabled = enabled;
    LOGI("TurboV2 FPS Booster: Frame Pacing set to: %s", enabled ? "ENABLED" : "DISABLED");
}

void FPSBooster::set_entity_optimization_enabled(bool enabled) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_entity_optimization_enabled = enabled;
    LOGI("TurboV2 FPS Booster: Entity Optimization set to: %s", enabled ? "ENABLED" : "DISABLED");
}

void FPSBooster::set_vsync_enabled(bool enabled) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_vsync_enabled = enabled;
    LOGI("TurboV2 FPS Booster: VSync set to: %s", enabled ? "ENABLED" : "DISABLED");
}

void FPSBooster::set_max_fps(int max_fps) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_max_fps = max_fps;
    LOGI("TurboV2 FPS Booster: Max FPS set to: %d", max_fps);
}

void FPSBooster::detect_and_optimize_gpu() {
    const char* gpu_env = std::getenv("GPU_RENDERER");
    if (gpu_env) {
        optimize_for_gpu(gpu_env);
        return;
    }
    optimize_for_gpu("Mali");
}

void FPSBooster::force_high_performance_mode() {
    LOGI("TurboV2 FPS Booster: Forcing EXTREME HIGH PERFORMANCE mode");
    
    set_fps_unlocked(true);
    set_vsync_enabled(false);
    set_entity_optimization_enabled(true);
    set_frame_pacing_enabled(false);
    set_max_fps(0);  // Unlimited
    
    m_entity_opt.frustum_culling_enabled = true;
    m_entity_opt.occlusion_culling_enabled = true;
    m_entity_opt.lod_enabled = true;
    m_entity_opt.batch_rendering_enabled = true;
    m_entity_opt.max_visible_entities = 10000;
    m_entity_opt.cull_distance_multiplier = 2.5f;
    
    LOGI("TurboV2 FPS Booster: EXTREME HIGH PERFORMANCE mode ENABLED!");
}

void FPSBooster::optimize_for_gpu(const char* gpu_name) {
    std::string gpu_lower;
    if (gpu_name) {
        gpu_lower = std::string(gpu_name);
        std::transform(gpu_lower.begin(), gpu_lower.end(), gpu_lower.begin(), ::tolower);
    }
    
    if (gpu_lower.find("mali") != std::string::npos) {
        m_current_gpu = GPUVendor::MALI;
        LOGI("TurboV2 FPS Booster: Optimizing for Mali GPU");
        m_entity_opt.batch_rendering_enabled = true;
        m_entity_opt.max_visible_entities = 8000;
    } else if (gpu_lower.find("adreno") != std::string::npos) {
        m_current_gpu = GPUVendor::QUALCOMM;
        LOGI("TurboV2 FPS Booster: Optimizing for Adreno GPU");
        m_entity_opt.occlusion_culling_enabled = true;
        m_entity_opt.lod_enabled = true;
    } else if (gpu_lower.find("powervr") != std::string::npos) {
        m_current_gpu = GPUVendor::POWERVR;
        LOGI("TurboV2 FPS Booster: Optimizing for PowerVR GPU");
        m_entity_opt.batch_rendering_enabled = true;
        m_entity_opt.max_visible_entities = 6000;
    } else if (gpu_lower.find("nvidia") != std::string::npos) {
        m_current_gpu = GPUVendor::NVIDIA;
        LOGI("TurboV2 FPS Booster: Optimizing for NVIDIA GPU");
        m_entity_opt.frustum_culling_enabled = true;
        m_entity_opt.occlusion_culling_enabled = true;
        m_entity_opt.lod_enabled = true;
        m_entity_opt.batch_rendering_enabled = true;
        m_entity_opt.max_visible_entities = 10000;
        m_entity_opt.cull_distance_multiplier = 2.5f;
    } else {
        m_current_gpu = GPUVendor::UNKNOWN;
        LOGI("TurboV2 FPS Booster: Default optimizations active");
    }
}

void FPSBooster::optimize_entity_rendering() {
    if (m_entity_optimization_enabled) {
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glFrontFace(GL_CCW);
    }
}

// ============================================================================
// C Interface for JNI and Hooking
// ============================================================================

extern "C" {

void turbo_v1_fps_booster_init(int target_fps) {
    get_fps_booster().initialize(target_fps);
}

void turbo_v1_fps_booster_begin_frame() {
    get_fps_booster().begin_frame();
}

void turbo_v1_fps_booster_end_frame() {
    get_fps_booster().end_frame();
}

void turbo_v1_fps_booster_set_unlocked(bool unlocked) {
    get_fps_booster().set_fps_unlocked(unlocked);
}

void turbo_v1_fps_booster_set_vsync(bool enabled) {
    get_fps_booster().set_vsync_enabled(enabled);
}

void turbo_v1_fps_booster_set_max_fps(int max_fps) {
    get_fps_booster().set_max_fps(max_fps);
}

} // extern "C"

} // namespace turbo_v1
