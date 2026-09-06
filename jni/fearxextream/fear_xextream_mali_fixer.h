#ifndef FEAR_XEXTREAM_MALI_FIXER_H
#define FEAR_XEXTREAM_MALI_FIXER_H

#include <string>

namespace FearXextream {

    class MaliShaderFixer {
    public:
        static void applyMaliWorkarounds(std::string& shaderSource);
        static void fixBlissShaderColorGlitches(std::string& shaderSource);
        static void configureMaliPipelineEnv();
    private:
        static void injectSafeMathWrappers(std::string& shaderSource);
        static void fixDerivativesAndDiscards(std::string& shaderSource);
        static void fixAtmosphereColorGrading(std::string& shaderSource);
    };

}

#endif // FEAR_XEXTREAM_MALI_FIXER_H
