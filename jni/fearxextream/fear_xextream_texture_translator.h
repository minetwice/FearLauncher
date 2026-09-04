#ifndef FEAR_XEXTREAM_TEXTURE_TRANSLATOR_H
#define FEAR_XEXTREAM_TEXTURE_TRANSLATOR_H

#include <GLES3/gl32.h>
#include <cstdint>

namespace FearXextream {

    struct TextureFormatMapping {
        GLenum desktopInternalFormat;
        GLenum mobileInternalFormat;
        GLenum format;
        GLenum type;
        bool requiresSRGBConversion;
        bool isFloatFormat;
    };

    class TextureTranslator {
    public:
        static TextureTranslator& getInstance();

        // Translate Desktop OpenGL texture internal formats to GLES 3.2 / Vulkan compatible formats
        TextureFormatMapping translateFormat(GLenum desktopFormat, GLenum srcFormat, GLenum srcType);

        // Adjust texture sampler parameters for Shaderpack compatibility
        void applySamplerFixes(GLenum target, GLint minFilter, GLint magFilter, GLint wrapS, GLint wrapT);

        // Convert sRGB color vectors to Linear color space in C++ for CPU-generated textures
        static void convertSRGBToLinear(uint8_t* pixels, size_t pixelCount, int channels);

        // Expert Mali GPU Texture Format & Swizzle Fix to prevent BGRA/RGBA color channel swap (green skies)
        void applyMaliTextureSwizzleFix(GLenum target, GLenum format, GLenum internalFormat);
    };

}

#endif // FEAR_XEXTREAM_TEXTURE_TRANSLATOR_H
