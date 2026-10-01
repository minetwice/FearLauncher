//
// FEAR NATIVE PERFORMANCE ENGINE
//
// Non-root, in-process tuning that runs alongside the game. It deliberately
// does NOT touch the renderer: it only asks the kernel for better CPU
// placement of the game's hot threads, and it logs what it found so the
// result is visible in latestlog.txt.
//
// Why this can help at all: Android's scheduler happily lets Minecraft's
// "Render thread" / "Client thread" bounce between the little and the big
// cluster, and it lets the JVM's JIT compiler threads run on little cores too.
// Pinning the hot threads onto the performance cluster removes that migration
// jitter. The gain is a steadier frame time (better 1% lows), not a new FPS
// ceiling.
//
// Everything here is best-effort: if a sysfs node or a permission is missing
// we log it and carry on. Nothing in this file may abort the game.
//
#define _GNU_SOURCE

#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <dirent.h>
#include <sched.h>
#include <errno.h>
#include <linux/limits.h>
#include <sys/mman.h>
#include <android/log.h>

#define LOG_TAG "FearPerf"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

#define FEAR_MAX_CPUS 32

static int  g_cpu_count = 0;
static int  g_perf_count = 0;
static cpu_set_t g_perf_set;
static int  g_have_perf = 0;

// Build a CPU set of the "performance" cluster: every core whose maximum
// frequency is at least 80% of the fastest core's maximum frequency.
static void fear_build_perf_set(void) {
    if (g_have_perf) return;

    char path[PATH_MAX];
    char buf[64];
    unsigned long freq[FEAR_MAX_CPUS];
    unsigned long maxf = 0;
    int n = 0;

    memset(freq, 0, sizeof(freq));
    for (int i = 0; i < FEAR_MAX_CPUS; i++) {
        snprintf(path, sizeof(path),
                 "/sys/devices/system/cpu/cpu%d/cpufreq/cpuinfo_max_freq", i);
        FILE *f = fopen(path, "r");
        if (!f) break;
        if (fgets(buf, sizeof(buf), f)) {
            freq[i] = strtoul(buf, NULL, 10);
            if (freq[i] > maxf) maxf = freq[i];
        }
        fclose(f);
        n++;
    }

    if (n == 0) {
        // No cpufreq nodes (locked kernels): fall back to all online cores.
        g_cpu_count = (int)sysconf(_SC_NPROCESSORS_ONLN);
        if (g_cpu_count <= 0 || g_cpu_count > FEAR_MAX_CPUS) g_cpu_count = 1;
        CPU_ZERO(&g_perf_set);
        for (int i = 0; i < g_cpu_count; i++) CPU_SET(i, &g_perf_set);
        g_perf_count = g_cpu_count;
        g_have_perf = 1;
        LOGI("no cpufreq nodes; using all %d cores", g_cpu_count);
        return;
    }

    g_cpu_count = n;
    CPU_ZERO(&g_perf_set);
    for (int i = 0; i < n; i++) {
        if (maxf && freq[i] * 100 >= maxf * 80) {
            CPU_SET(i, &g_perf_set);
            g_perf_count++;
        }
    }
    if (g_perf_count == 0) {
        for (int i = 0; i < n; i++) CPU_SET(i, &g_perf_set);
        g_perf_count = n;
    }
    g_have_perf = 1;
    LOGI("topology: %d cores, %d performance cores (max %lu kHz)",
         g_cpu_count, g_perf_count, maxf);
}

// Pin one thread (by tid) onto the performance cluster.
static int fear_pin_tid(int tid) {
    cpu_set_t set;
    memcpy(&set, &g_perf_set, sizeof(set));
    return sched_setaffinity(tid, sizeof(set), &set) == 0;
}

// Threads worth pinning. /proc/<tid>/comm is truncated to 15 chars.
static int fear_name_is_hot(const char *name) {
    static const char *hot[] = {
        "Render thread", "Client thread", "main",
        "C2 CompilerThre", "C1 CompilerThre", "C2 CompilerThrea"
    };
    for (unsigned i = 0; i < sizeof(hot) / sizeof(hot[0]); i++) {
        size_t l = strlen(hot[i]);
        if (strncmp(name, hot[i], l) == 0) return 1;
    }
    return 0;
}

static int fear_pin_hot_threads(void) {
    fear_build_perf_set();

    DIR *d = opendir("/proc/self/task");
    if (!d) return 0;

    struct dirent *e;
    int pinned = 0;
    while ((e = readdir(d)) != NULL) {
        if (e->d_name[0] < '0' || e->d_name[0] > '9') continue;
        int tid = atoi(e->d_name);

        char p[PATH_MAX];
        char comm[64];
        memset(comm, 0, sizeof(comm));
        snprintf(p, sizeof(p), "/proc/self/task/%d/comm", tid);
        FILE *f = fopen(p, "r");
        if (!f) continue;
        if (fgets(comm, sizeof(comm), f)) {
            size_t l = strlen(comm);
            while (l && (comm[l - 1] == '\n' || comm[l - 1] == '\r')) comm[--l] = 0;
        }
        fclose(f);

        if (fear_name_is_hot(comm) && fear_pin_tid(tid)) {
            pinned++;
            LOGI("pinned tid %d (%s) -> %d performance cores", tid, comm, g_perf_count);
        }
    }
    closedir(d);
    return pinned;
}

// Best-effort transparent huge pages for this process' anonymous mappings
// (this includes the JVM heap). madvise is advisory: if the kernel does not
// support THP we simply get nothing back, so there is no downside.
static int fear_madvise_hugepages(void) {
#ifdef MADV_HUGEPAGE
    FILE *f = fopen("/proc/self/maps", "r");
    if (!f) return 0;
    char line[512];
    int n = 0;
    while (fgets(line, sizeof(line), f)) {
        unsigned long s, e;
        char perms[8];
        if (sscanf(line, "%lx-%lx %7s", &s, &e, perms) != 3) continue;
        if (perms[0] != 'r' || perms[1] != 'w') continue;
        size_t len = (size_t)(e - s);
        if (len < (2u << 20)) continue;   // only mappings >= 2 MB
        if (madvise((void *)s, len, MADV_HUGEPAGE) == 0) n++;
    }
    fclose(f);
    return n;
#else
    return 0;
#endif
}

// One-shot: build the topology view and pin whatever hot threads already exist.
JNIEXPORT jint JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_nativeFearPerfStart(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    LOGI("=== Fear native perf engine start ===");
    fear_build_perf_set();
    int hp = fear_madvise_hugepages();
    LOGI("hugepage hint applied to %d anonymous regions", hp);
    int n = fear_pin_hot_threads();
    LOGI("=== Fear native perf engine: %d hot threads pinned to %d performance cores ===",
         n, g_perf_count);
    return n;
}

// Repeatable: called by the launcher's watcher while the game starts up, so
// threads that appear later (Render thread, Client thread) get pinned too.
JNIEXPORT jint JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_nativeFearPinGameThreads(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    return fear_pin_hot_threads();
}
