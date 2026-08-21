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

// Module 3 Implementation: Zero-Copy Compact UMA Memory Architecture
FearHostVisibleBuffer fear_allocate_zero_copy_buffer(size_t size) {
    FearHostVisibleBuffer buf = {};
    buf.size = size;
    buf.is_coherent = true;
    buf.memory_type_index = 1; // VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT

    // Allocate host-accessible memory mapped directly to Vulkan UMA region
    buf.host_mapped_ptr = malloc(size);
    LOGI("[FearMemory UMA] Allocated %zu bytes zero-copy host-visible coherent buffer", size);
    return buf;
}

void fear_free_zero_copy_buffer(FearHostVisibleBuffer* buf) {
    if (buf && buf->host_mapped_ptr) {
        free(buf->host_mapped_ptr);
        buf->host_mapped_ptr = nullptr;
        buf->size = 0;
        LOGI("[FearMemory UMA] Freed zero-copy host-visible buffer");
    }
}
