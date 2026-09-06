#include "fear_xextream_gpu_signal.h"
#include <android/log.h>
#include <GLES3/gl32.h>
#include <dlfcn.h>
#include <cstdlib>

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "FearXextreamSignal"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace FearXextream {

    GPUSignalOptimizer& GPUSignalOptimizer::getInstance() {
        static GPUSignalOptimizer instance;
        return instance;
    }

    void GPUSignalOptimizer::optimizeGPUSignals() {
        LOGI("GPUSignalOptimizer: unlocking GPU pipeline throughput...");
        unlockTileMemoryPass();

        GPUArchitecture arch = ContextTracker::getInstance().getGPUArchitecture();
        if (arch == GPUArchitecture::QUALCOMM_ADRENO) {
            configureAdrenoLRZ();
        } else if (arch == GPUArchitecture::ARM_MALI) {
            configureMaliTileBuffer();
        }

        enforceFramePacing();
    }

    void GPUSignalOptimizer::unlockTileMemoryPass() {
        typedef void (*glHint_pfn)(GLenum, GLenum);
        static glHint_pfn real_glHint = (glHint_pfn)dlsym(RTLD_DEFAULT, "glHint");
        if (real_glHint) {
            real_glHint(GL_GENERATE_MIPMAP_HINT, GL_FASTEST);
#ifdef GL_FRAGMENT_SHADER_DERIVATIVE_HINT
            real_glHint(GL_FRAGMENT_SHADER_DERIVATIVE_HINT, GL_FASTEST);
#endif
        }

        setenv("LIBGL_SHADER_DEFAULT_PRECISION", "mediump", 0);
        setenv("LIBGL_BATCH", "1", 0);
        setenv("LIBGL_USEVBO", "1", 0);
        setenv("LIBGL_BEGINEND", "1", 0);
        setenv("LIBGL_RECYCLEFBO", "1", 0);
        setenv("LIBGL_NOERROR", "1", 0);

        LOGI("GPUSignalOptimizer: tile-memory + batching hints applied.");
    }

    void GPUSignalOptimizer::configureAdrenoLRZ() {
        typedef void (*glDepthFunc_pfn)(GLenum);
        typedef void (*glDepthMask_pfn)(GLboolean);
        typedef void (*glEnable_pfn)(GLenum);
        static glDepthFunc_pfn real_glDepthFunc = (glDepthFunc_pfn)dlsym(RTLD_DEFAULT, "glDepthFunc");
        static glDepthMask_pfn real_glDepthMask = (glDepthMask_pfn)dlsym(RTLD_DEFAULT, "glDepthMask");
        static glEnable_pfn real_glEnable = (glEnable_pfn)dlsym(RTLD_DEFAULT, "glEnable");

        if (real_glDepthFunc) real_glDepthFunc(GL_LEQUAL);
        if (real_glDepthMask) real_glDepthMask(GL_TRUE);
        if (real_glEnable) real_glEnable(GL_DEPTH_TEST);

        setenv("ADRENO_SCENARIO", "gaming", 0);
        setenv("LIBGL_AVOID16BITS", "1", 0);

        LOGI("GPUSignalOptimizer: Adreno LRZ / Early-Z path active.");
    }

    void GPUSignalOptimizer::configureMaliTileBuffer() {
        typedef void (*glDepthMask_pfn)(GLboolean);
        typedef void (*glDepthFunc_pfn)(GLenum);
        typedef void (*glEnable_pfn)(GLenum);
        static glDepthMask_pfn real_glDepthMask = (glDepthMask_pfn)dlsym(RTLD_DEFAULT, "glDepthMask");
        static glDepthFunc_pfn real_glDepthFunc = (glDepthFunc_pfn)dlsym(RTLD_DEFAULT, "glDepthFunc");
        static glEnable_pfn real_glEnable = (glEnable_pfn)dlsym(RTLD_DEFAULT, "glEnable");

        if (real_glDepthMask) real_glDepthMask(GL_TRUE);
        if (real_glDepthFunc) real_glDepthFunc(GL_LEQUAL);
        if (real_glEnable) real_glEnable(GL_DEPTH_TEST);

        setenv("mali_debug", "nocluster", 0);
        setenv("pan_shader_compile_threads", "4", 0);
        setenv("LIBGL_AVOID16BITS", "1", 0);

        LOGI("GPUSignalOptimizer: Mali tile-buffer / HSR path active.");
    }

    void GPUSignalOptimizer::enforceFramePacing() {
        setenv("vblank_mode", "0", 0);
        setenv("MESA_VK_WSI_PRESENT_MODE", "immediate", 0);
        setenv("LIBGL_VSYNC", "0", 0);
        setenv("FORCE_VSYNC", "false", 0);
        setenv("POJAV_VSYNC_IN_ZINK", "0", 0);
        setenv("FEAR_XEXTREAM_PRESENT_IMMEDIATE", "1", 0);

        LOGI("GPUSignalOptimizer: vsync-off frame pacing applied for max FPS.");
    }

}
