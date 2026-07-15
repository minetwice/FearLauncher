#include "fear_memory.h"
#include <android/log.h>
#include <stdlib.h>

#define TAG "FEAR_RENDERER"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

static void* ring_buffer_ptr = nullptr;
static size_t ring_buffer_allocated = 0;

void allocate_and_recycle_ring_buffers(size_t request_size) {
    if (ring_buffer_allocated < request_size) {
        if (ring_buffer_ptr) {
            free(ring_buffer_ptr);
        }
        ring_buffer_ptr = malloc(request_size);
        ring_buffer_allocated = request_size;
        LOGI("Allocated %zu bytes in persistent ring-buffer for fast memory mappings.", request_size);
    }
}
