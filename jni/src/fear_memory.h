#ifndef FEAR_MEMORY_H
#define FEAR_MEMORY_H

#include <stddef.h>
#include <stdint.h>

void allocate_and_recycle_ring_buffers(size_t request_size);

// Module 3: Zero-Copy Compact Memory Architecture API
struct FearHostVisibleBuffer {
    void* host_mapped_ptr;
    size_t size;
    uint32_t memory_type_index;
    bool is_coherent;
};

FearHostVisibleBuffer fear_allocate_zero_copy_buffer(size_t size);
void fear_free_zero_copy_buffer(FearHostVisibleBuffer* buf);

#endif // FEAR_MEMORY_H
