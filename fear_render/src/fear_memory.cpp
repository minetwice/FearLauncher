#include "fear_memory.h"
#include <GLES3/gl32.h>
#include <android/log.h>
#include <vector>
#include <mutex>
#include <stdlib.h>
#include <string.h>

#define LOG_TAG "FEAR_MEMORY_MANAGER"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::vector<FearBufferAllocation> s_tracked_allocations;
static std::mutex s_alloc_mutex;

FearBufferAllocation FearMemoryManager::allocateBuffer(size_t size, bool persistent) {
    std::lock_guard<std::mutex> lock(s_alloc_mutex);

    FearBufferAllocation alloc = {0, size, nullptr, persistent};

    glGenBuffers(1, &alloc.buffer_id);
    glBindBuffer(GL_ARRAY_BUFFER, alloc.buffer_id);

    if (persistent) {
        glBufferData(GL_ARRAY_BUFFER, size, nullptr, GL_DYNAMIC_DRAW);
        alloc.mapped_ptr = malloc(size);
        if (alloc.mapped_ptr) {
            memset(alloc.mapped_ptr, 0, size);
        } else {
            LOGE("Failed to allocate host backing memory for persistent buffer emulation of size: %zu", size);
        }
    } else {
        glBufferData(GL_ARRAY_BUFFER, size, nullptr, GL_STATIC_DRAW);
    }

    glBindBuffer(GL_ARRAY_BUFFER, 0);
    s_tracked_allocations.push_back(alloc);

    LOGI("Allocated Fear Buffer: ID=%u, Size=%zu, PersistentEmulated=%d", alloc.buffer_id, size, persistent);
    return alloc;
}

void FearMemoryManager::freeBuffer(const FearBufferAllocation& allocation) {
    std::lock_guard<std::mutex> lock(s_alloc_mutex);

    if (allocation.buffer_id != 0) {
        glDeleteBuffers(1, &allocation.buffer_id);
    }

    if (allocation.mapped_ptr) {
        free(allocation.mapped_ptr);
    }

    for (auto it = s_tracked_allocations.begin(); it != s_tracked_allocations.end(); ++it) {
        if (it->buffer_id == allocation.buffer_id) {
            s_tracked_allocations.erase(it);
            break;
        }
    }
    LOGI("Freed Fear Buffer: ID=%u", allocation.buffer_id);
}

void FearMemoryManager::flushPersistentMemory() {
    std::lock_guard<std::mutex> lock(s_alloc_mutex);

    for (const auto& alloc : s_tracked_allocations) {
        if (alloc.is_persistent && alloc.mapped_ptr && alloc.buffer_id != 0) {
            glBindBuffer(GL_ARRAY_BUFFER, alloc.buffer_id);
            glBufferSubData(GL_ARRAY_BUFFER, 0, alloc.size, alloc.mapped_ptr);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
        }
    }
}
