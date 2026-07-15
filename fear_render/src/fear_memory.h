#ifndef FEAR_MEMORY_H
#define FEAR_MEMORY_H

#include <stddef.h>
#include <stdint.h>

struct FearBufferAllocation {
    uint32_t buffer_id;
    size_t size;
    void* mapped_ptr;
    bool is_persistent;
};

class FearMemoryManager {
public:
    static FearMemoryManager& getInstance() {
        static FearMemoryManager instance;
        return instance;
    }

    FearBufferAllocation allocateBuffer(size_t size, bool persistent);
    void freeBuffer(const FearBufferAllocation& allocation);
    void flushPersistentMemory();

private:
    FearMemoryManager() {}
};

#endif // FEAR_MEMORY_H
