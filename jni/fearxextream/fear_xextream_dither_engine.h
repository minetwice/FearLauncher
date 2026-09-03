#ifndef FEAR_XEXTREAM_DITHER_ENGINE_H
#define FEAR_XEXTREAM_DITHER_ENGINE_H

#include <string>
#include <GLES3/gl32.h>

namespace FearXextream {

    class DitherEngine {
    public:
        static DitherEngine& getInstance();

        // Inject temporal dithering, gamma correction, and flickering suppression into shaders
        void injectAntiFlickerLayer(std::string& shaderSource, GLenum shaderType);

        // Configure sRGB framebuffer quantization to prevent color banding on Mali GPUs
        void configureColorPrecision();
    };

}

#endif // FEAR_XEXTREAM_DITHER_ENGINE_H
