#ifndef FEAR_XEXTREAM_GPU_SIGNAL_H
#define FEAR_XEXTREAM_GPU_SIGNAL_H

namespace FearXextream {

    class GPUSignalOptimizer {
    public:
        static GPUSignalOptimizer& getInstance();

        // Maximize GPU accessibility (100% throughput) and tune driver execution passes
        void optimizeGPUSignals();

        // Configure high-bandwidth tile memory pass for ARM Mali / Qualcomm Adreno
        void unlockTileMemoryPass();
    };

}

#endif // FEAR_XEXTREAM_GPU_SIGNAL_H
