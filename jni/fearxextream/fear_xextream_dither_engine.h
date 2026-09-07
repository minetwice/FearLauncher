#ifndef FEAR_XEXTREAM_DITHER_ENGINE_H
#define FEAR_XEXTREAM_DITHER_ENGINE_H

#include "fear_xextream_core.h"
#include <string>

namespace FearXextream {

    class DitherEngine {
    public:
        static DitherEngine& getInstance();
        void injectAntiFlickerLayer(std::string& shaderSource, GLenum shaderType);
        void injectColorEnhancerLayer(std::string& shaderSource, GLenum shaderType);
        void configureColorPrecision();

    private:
        DitherEngine() = default;
    };

}

#endif // FEAR_XEXTREAM_DITHER_ENGINE_H
