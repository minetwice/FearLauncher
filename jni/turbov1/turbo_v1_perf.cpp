#include "turbo_v1_perf.h"
#include <pthread.h>
#include <sched.h>
#include <unistd.h>
#include <cstdio>
#include <cstring>
#include <algorithm>

namespace turbo_v1 {

static PerfState g_perf;

// ============================================================================
// Init / Shutdown
// ============================================================================

void perf_init() {
    LOGI("Indus2.0: Performance Governor initializing (200+ FPS target)");

    g_perf.fps.store(60.0);
    g_perf.target_fps.store(240);
    g_perf.min_fps.store(30);
    g_perf.adaptive_resolution_scale.store(100);
    g_perf.frame_skip_enabled.store(true);
    g_perf.vsync_forced_off.store(true);
    g_perf.gpu_boost_active.store(false);

    // Detect CPU count
    int cpu_count = (int)sysconf(_SC_NPROCESSORS_ONLN);
    if (cpu_count <= 0) cpu_count = 8;
    g_perf.cpu_count.store(cpu_count);

    // Detect big cores: on typical SoCs, the last N/2 cores are the "big" cores
    // On big.LITTLE: cores 0..(n_big-1) are LITTLE, rest are big
    // We use a heuristic: the upper half of cores are performance cores
    int big_mask = 0;
    int mid = cpu_count / 2;
    for (int i = mid; i < cpu_count; i++) {
        big_mask |= (1 << i);
    }
    g_perf.big_core_mask.store(big_mask);

    // Pin the last big core for the render thread
    g_perf.render_thread_cpu.store(cpu_count - 1);

    LOGI("Indus2.0: CPU count=%d, big core mask=0x%x, render thread pinned to core %d",
         cpu_count, big_mask, cpu_count - 1);

    // Try to activate GPU boost immediately
    perf_gpu_boost(true);
}

void perf_shutdown() {
    perf_gpu_boost(false);
    LOGI("Indus2.0: Performance Governor shut down");
}

// ============================================================================
// Frame Pacing
// ============================================================================

static inline int64_t now_ns() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000000000LL + ts.tv_nsec;
}

void perf_frame_begin() {
    int64_t now = now_ns();
    g_perf.frame_start_ns.store(now);
}

void perf_frame_end() {
    int64_t now = now_ns();
    int64_t start = g_perf.frame_start_ns.load();
    int64_t delta = now - start;
    if (delta <= 0) delta = 16666667; // fallback 60fps
    g_perf.frame_delta_ns.store(delta);
    g_perf.frame_end_ns.store(now);

    // Smoothed FPS using exponential moving average
    double instant_fps = 1000000000.0 / (double)delta;
    double current = g_perf.fps.load();
    double smoothed = current * 0.9 + instant_fps * 0.1;
    g_perf.fps.store(smoothed);

    // Adaptive resolution scaling
    int current_scale = g_perf.adaptive_resolution_scale.load();
    int min_fps = g_perf.min_fps.load();
    int target = g_perf.target_fps.load();

    if (smoothed < min_fps && current_scale > 50) {
        // FPS too low — reduce resolution to gain frames
        int new_scale = std::max(50, current_scale - 5);
        g_perf.adaptive_resolution_scale.store(new_scale);
        LOGD("Indus2.0: FPS=%.1f below min=%d, lowering resolution scale to %d%%",
             smoothed, min_fps, new_scale);
    } else if (smoothed > target * 0.95 && current_scale < 100) {
        // FPS is high enough — restore resolution
        int new_scale = std::min(100, current_scale + 2);
        g_perf.adaptive_resolution_scale.store(new_scale);
    }
}

int perf_get_resolution_scale() {
    return g_perf.adaptive_resolution_scale.load();
}

void perf_set_target_fps(int fps) {
    if (fps < 30) fps = 30;
    if (fps > 300) fps = 300;
    g_perf.target_fps.store(fps);
    LOGI("Indus2.0: Target FPS set to %d", fps);
}

bool perf_should_render_frame() {
    if (!g_perf.frame_skip_enabled.load()) return true;

    double fps = g_perf.fps.load();
    int target = g_perf.target_fps.load();

    // If we're well above target, render every frame
    if (fps >= target * 0.95) return true;

    // If we're below target, don't skip (we need every frame)
    // Skip logic is for when GPU is overcommitted: skip alternate frames
    // to reduce draw call pressure while maintaining responsiveness
    static thread_local int frame_counter = 0;
    frame_counter++;

    if (fps < g_perf.min_fps.load()) {
        // Critical: render every other frame to reduce GPU load
        return (frame_counter & 1) == 0;
    }

    return true;
}

// ============================================================================
// CPU Pinning
// ============================================================================

void perf_pin_render_thread() {
    int cpu = g_perf.render_thread_cpu.load();
    if (cpu < 0 || cpu >= g_perf.cpu_count.load()) return;

    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);
    CPU_SET(cpu, &cpuset);

    int result = sched_setaffinity(0, sizeof(cpu_set_t), &cpuset);
    if (result == 0) {
        // Also set high priority for the render thread
        struct sched_param sp;
        sp.sched_priority = sched_get_priority_max(SCHED_FIFO);
        if (sp.sched_priority > 0) {
            pthread_setschedparam(pthread_self(), SCHED_FIFO, &sp);
        }
        LOGI("Indus2.0: Render thread pinned to core %d with SCHED_FIFO", cpu);
    } else {
        LOGW("Indus2.0: Failed to pin render thread to core %d (errno=%d)", cpu, errno);
        // Fallback: try to set nice value
        nice(-10);
    }
}

void perf_pin_worker_threads(int count) {
    int big_mask = g_perf.big_core_mask.load();
    if (big_mask == 0) return;

    // Distribute worker threads across big cores
    static std::atomic<int> next_core{0};
    int cpu_count = g_perf.cpu_count.load();
    int big_cores[16];
    int big_count = 0;

    for (int i = 0; i < cpu_count && big_count < 16; i++) {
        if (big_mask & (1 << i)) {
            big_cores[big_count++] = i;
        }
    }
    if (big_count == 0) return;

    int target = big_cores[next_core.fetch_add(1) % big_count];
    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);
    CPU_SET(target, &cpuset);
    sched_setaffinity(0, sizeof(cpu_set_t), &cpuset);
}

// ============================================================================
// GPU Frequency Boost
// ============================================================================

static void write_sysfs(const char* path, const char* value) {
    FILE* f = fopen(path, "w");
    if (f) {
        fprintf(f, "%s", value);
        fclose(f);
    }
}

void perf_gpu_boost(bool enable) {
    g_perf.gpu_boost_active.store(enable);

    // Adreno (KGSL) GPU frequency boost
    if (enable) {
        // Set GPU to max frequency
        write_sysfs("/sys/class/kgsl/kgsl-3d0/devfreq/governor", "performance");
        write_sysfs("/sys/class/kgsl/kgsl-3d0/max_pwrlevel", "0");
        write_sysfs("/sys/class/kgsl/kgsl-3d0/min_pwrlevel", "0");
        write_sysfs("/sys/class/kgsl/kgsl-3d0/bus_split", "0");
        write_sysfs("/sys/class/kgsl/kgsl-3d0/force_bus_on", "1");
        write_sysfs("/sys/class/kgsl/kgsl-3d0/force_clk_on", "1");
        write_sysfs("/sys/class/kgsl/kgsl-3d0/idle_timer", "10000");

        // Mali GPU
        write_sysfs("/sys/devices/platform/13800000.mali/devfreq/governor", "performance");

        // devfreq global
        write_sysfs("/sys/class/devfreq/governor", "performance");

        // CPU boost: all big cores online
        int cpu_count = g_perf.cpu_count.load();
        int mid = cpu_count / 2;
        for (int i = mid; i < cpu_count; i++) {
            char path[256];
            snprintf(path, sizeof(path), "/sys/devices/system/cpu/cpu%d/online", i);
            write_sysfs(path, "1");
        }

        // CPU governor to performance for big cores
        for (int i = mid; i < cpu_count; i++) {
            char path[256];
            snprintf(path, sizeof(path),
                     "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_governor", i);
            write_sysfs(path, "performance");
        }

        LOGI("Indus2.0: GPU + CPU boost activated (Adreno/Mali performance mode)");
    } else {
        write_sysfs("/sys/class/kgsl/kgsl-3d0/devfreq/governor", "msm-adreno-tz");
        int cpu_count = g_perf.cpu_count.load();
        int mid = cpu_count / 2;
        for (int i = mid; i < cpu_count; i++) {
            char path[256];
            snprintf(path, sizeof(path),
                     "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_governor", i);
            write_sysfs(path, "schedutil");
        }
        LOGI("Indus2.0: GPU + CPU boost deactivated");
    }
}

PerfState& perf_get_state() {
    return g_perf;
}

} // namespace turbo_v1
