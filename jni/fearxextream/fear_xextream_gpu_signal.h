#ifndef FEAR_XEXTREAM_GPU_SIGNAL_H
#define FEAR_XEXTREAM_GPU_SIGNAL_H

#include "fear_xextream_core.h"

namespace FearXextream {

    class GPUSignalOptimizer {
    public:
        static GPUSignalOptimizer& getInstance();
        void optimizeGPUSignals();
        void unlockTileMemoryPass();
        void configureAdrenoLRZ();
        void configureMaliTileBuffer();
        void enforceFramePacing();

    private:
        GPUSignalOptimizer() = default;
    };

}

#endif // FEAR_XEXTREAM_GPU_SIGNAL_H
