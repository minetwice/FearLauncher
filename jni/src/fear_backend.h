#ifndef FEAR_BACKEND_H
#define FEAR_BACKEND_H

#include <stddef.h>

void detect_hardware_and_select_backend();

// Module 4: Hardware Specific Workarounds API
struct FearGPUWorkarounds {
    bool is_adreno;
    bool is_mali;
    size_t min_uniform_buffer_offset_alignment;
    bool bypass_spirv_validation_fail;
};

FearGPUWorkarounds fear_get_gpu_workarounds();

#endif // FEAR_BACKEND_H
