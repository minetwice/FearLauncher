#ifndef TURBO_V1_PERF_H
#define TURBO_V1_PERF_H

#include "turbo_v1_core.h"
#include <atomic>
#include <chrono>
#include <cstdint>

namespace turbo_v1 {

// ============================================================================
// Indus2.0 Performance Governor
// Smart CPU/GPU resource management for 200+ FPS target
// ============================================================================

struct PerfState {
    // Frame timing
    std::atomic<int64_t>  frame_start_ns{0};
    std::atomic<int64_t>  frame_end_ns{0};
    std::atomic<int64_t>  frame_delta_ns{0};       // last frame time in ns
    std::atomic<double>   fps{0.0};                 // smoothed FPS
    std::atomic<int>      target_fps{240};          // target FPS ceiling
    std::atomic<int>      min_fps{30};              // below this we degrade quality
    std::atomic<int>      adaptive_resolution_scale{100}; // 100 = full res, 50 = half
    std::atomic<bool>     frame_skip_enabled{true};
    std::atomic<bool>     vsync_forced_off{true};
    std::atomic<bool>     gpu_boost_active{false};

    // CPU affinity
    std::atomic<int>      big_core_mask{0};         // bitmask of big cores
    std::atomic<int>      render_thread_cpu{-1};     // pinned CPU core for render thread
    std::atomic<int>      cpu_count{0};

    // Draw call batching
    std::atomic<int>      batched_draw_calls{0};
    std::atomic<int>      total_draw_calls{0};
    std::atomic<int>      skipped_draw_calls{0};

    // Entity rendering stats
    std::atomic<int>      entities_drawn{0};
    std::atomic<int>      entities_batched{0};
    std::atomic<int>      entities_culled{0};
};

// Lifecycle
void perf_init();
void perf_shutdown();

// Frame pacing — call at start and end of each frame
void perf_frame_begin();
void perf_frame_end();

// Adaptive resolution scaling based on current FPS
int  perf_get_resolution_scale();   // returns 50-100
void perf_set_target_fps(int fps);

// CPU pinning
void perf_pin_render_thread();
void perf_pin_worker_threads(int count);

// GPU frequency boost hint (writes to /sys/class/kgsl or equivalent)
void perf_gpu_boost(bool enable);

// Get the global perf state (for logging / JNI queries)
PerfState& perf_get_state();

// Frame skip decision: returns true if this frame should be rendered, false to skip
bool perf_should_render_frame();

} // namespace turbo_v1

#endif // TURBO_V1_PERF_H
