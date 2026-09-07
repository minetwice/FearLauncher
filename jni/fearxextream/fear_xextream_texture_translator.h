#ifndef FEAR_XEXTREAM_TEXTURE_TRANSLATOR_H
#define FEAR_XEXTREAM_TEXTURE_TRANSLATOR_H

#include "fear_xextream_core.h"

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
        TextureFormatMapping translateFormat(GLenum desktopFormat, GLenum srcFormat, GLenum srcType);
        void applySamplerFixes(GLenum target, GLint minFilter, GLint magFilter, GLint wrapS, GLint wrapT);
        void convertSRGBToLinear(uint8_t* pixels, size_t pixelCount, int channels);
        void applyMaliTextureSwizzleFix(GLenum target, GLenum format, GLenum internalFormat);
        void prepareUnpackAlignment();

    private:
        TextureTranslator() = default;
    };

}

#endif // FEAR_XEXTREAM_TEXTURE_TRANSLATOR_H
