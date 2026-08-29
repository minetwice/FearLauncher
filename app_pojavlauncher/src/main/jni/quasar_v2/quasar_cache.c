/*
 * QuasarV2 - Shader & Binary Cache Implementation
 */

#include "quasar_cache.h"
#include <stdlib.h>
#include <string.h>

#define TAG "Quasar-Cache"
#include <log.h>

#define MAX_CACHE_ENTRIES 512

static quasar_cache_entry_t g_cache[MAX_CACHE_ENTRIES];
static size_t g_cache_count = 0;

void quasar_cache_init(const char* cache_dir) {
    g_cache_count = 0;
    memset(g_cache, 0, sizeof(g_cache));
    LOGI("QuasarV2: Shader L2 RAM cache initialized");
}

GLuint quasar_cache_get_program(uint32_t hash) {
    for (size_t i = 0; i < g_cache_count; i++) {
        if (g_cache[i].hash == hash) {
            return g_cache[i].program;
        }
    }
    return 0;
}

void quasar_cache_put_program(uint32_t hash, GLuint program) {
    if (g_cache_count < MAX_CACHE_ENTRIES) {
        g_cache[g_cache_count].hash = hash;
        g_cache[g_cache_count].program = program;
        g_cache_count++;
    }
}

void quasar_cache_flush(void) {
    g_cache_count = 0;
}
