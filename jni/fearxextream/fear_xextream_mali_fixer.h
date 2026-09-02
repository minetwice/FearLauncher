#ifndef FEAR_XEXTREAM_MALI_FIXER_H
#define FEAR_XEXTREAM_MALI_FIXER_H

#include <string>
#include <GLES3/gl32.h>

namespace FearXextream {

    class MaliShaderFixer {
    public:
        // Inject Mali GPU specific workarounds for Bliss & Complementary shaderpacks
        static void applyMaliWorkarounds(std::string& shaderSource);

        // Fix Mali tile-buffer color space & depth precision
        static void configureMaliPipelineEnv();
    };

}

#endif // FEAR_XEXTREAM_MALI_FIXER_H
