#include "fear_xextream_texture_translator.h"
#include <cmath>
#include <android/log.h>

#define LOG_TAG "FearXextreamTexture"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace FearXextream {

    TextureTranslator& TextureTranslator::getInstance() {
        static TextureTranslator instance;
        return instance;
    }

    TextureFormatMapping TextureTranslator::translateFormat(GLenum desktopFormat, GLenum srcFormat, GLenum srcType) {
        TextureFormatMapping mapping;
        mapping.desktopInternalFormat = desktopFormat;
        mapping.format = srcFormat;
        mapping.type = srcType;
        mapping.requiresSRGBConversion = false;
        mapping.isFloatFormat = false;

        switch (desktopFormat) {
            // High-precision floating point G-Buffer formats for shaders (colortex0-15, normals, depth, specular)
            case GL_RGBA16F:
                mapping.mobileInternalFormat = GL_RGBA16F;
                mapping.format = GL_RGBA;
                mapping.type = GL_HALF_FLOAT;
                mapping.isFloatFormat = true;
                break;
            case GL_RGBA32F:
                mapping.mobileInternalFormat = GL_RGBA32F;
                mapping.format = GL_RGBA;
                mapping.type = GL_FLOAT;
                mapping.isFloatFormat = true;
                break;
            case GL_R11F_G11F_B10F:
                mapping.mobileInternalFormat = GL_R11F_G11F_B10F;
                mapping.format = GL_RGB;
                mapping.type = GL_UNSIGNED_INT_10F_11F_11F_REV;
                mapping.isFloatFormat = true;
                break;

            // sRGB Color Space Formats for realistic Minecraft block textures and entity shading
            case GL_SRGB:
            case GL_SRGB8:
                mapping.mobileInternalFormat = GL_SRGB8;
                mapping.format = GL_RGB;
                mapping.type = GL_UNSIGNED_BYTE;
                mapping.requiresSRGBConversion = true;
                break;

            case GL_SRGB8_ALPHA8:
                mapping.mobileInternalFormat = GL_SRGB8_ALPHA8;
                mapping.format = GL_RGBA;
                mapping.type = GL_UNSIGNED_BYTE;
                mapping.requiresSRGBConversion = true;
                break;

            // High Precision Depth & Shadow Maps (Mali/Adreno 24-bit / 32-bit float shadow maps)
            case GL_DEPTH_COMPONENT:
            case GL_DEPTH_COMPONENT16:
            case GL_DEPTH_COMPONENT24:
                mapping.mobileInternalFormat = GL_DEPTH_COMPONENT24;
                mapping.format = GL_DEPTH_COMPONENT;
                mapping.type = GL_UNSIGNED_INT;
                break;
            case GL_DEPTH_COMPONENT32F:
                mapping.mobileInternalFormat = GL_DEPTH_COMPONENT32F;
                mapping.format = GL_DEPTH_COMPONENT;
                mapping.type = GL_FLOAT;
                mapping.isFloatFormat = true;
                break;

            // Standard RGBA Fallbacks
            case GL_RGBA:
            case GL_RGBA8:
            default:
                mapping.mobileInternalFormat = GL_RGBA8;
                mapping.format = GL_RGBA;
                mapping.type = GL_UNSIGNED_BYTE;
                break;
        }

        return mapping;
    }

    void TextureTranslator::applySamplerFixes(GLenum target, GLint minFilter, GLint magFilter, GLint wrapS, GLint wrapT) {
        // Enforce CLAMP_TO_EDGE for non-power-of-two (NPOT) samplers to fix texture edge bleeding
        if (wrapS == GL_REPEAT) wrapS = GL_CLAMP_TO_EDGE;
        if (wrapT == GL_REPEAT) wrapT = GL_CLAMP_TO_EDGE;

        glTexParameteri(target, GL_TEXTURE_WRAP_S, wrapS);
        glTexParameteri(target, GL_TEXTURE_WRAP_T, wrapT);
        glTexParameteri(target, GL_TEXTURE_MIN_FILTER, minFilter);
        glTexParameteri(target, GL_TEXTURE_MAG_FILTER, magFilter);
    }

    void TextureTranslator::convertSRGBToLinear(uint8_t* pixels, size_t pixelCount, int channels) {
        if (!pixels) return;
        for (size_t i = 0; i < pixelCount * channels; i += channels) {
            for (int c = 0; c < 3 && c < channels; ++c) {
                float srgb = pixels[i + c] / 255.0f;
                float linear = (srgb <= 0.04045f) ? (srgb / 12.92f) : std::pow((srgb + 0.055f) / 1.055f, 2.4f);
                pixels[i + c] = static_cast<uint8_t>(std::round(linear * 255.0f));
            }
        }
    }

    void TextureTranslator::prepareUnpackAlignment() {
        // Force 1-byte pixel unpack alignment to prevent Mali memory stride corruptions
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    }

    void TextureTranslator::applyMaliTextureSwizzleFix(GLenum target, GLenum format, GLenum internalFormat) {
        prepareUnpackAlignment();

        #ifndef GL_TEXTURE_SWIZZLE_R
        #define GL_TEXTURE_SWIZZLE_R 0x8E42
        #define GL_TEXTURE_SWIZZLE_G 0x8E43
        #define GL_TEXTURE_SWIZZLE_B 0x8E44
        #define GL_TEXTURE_SWIZZLE_A 0x8E45
        #endif

        // Mali GPU BGRA / BGR blue-red channel swizzle fix
        if (format == 0x80E1 /* GL_BGRA_EXT */ || format == 0x80E0 /* GL_BGR_EXT */) {
            glTexParameteri(target, GL_TEXTURE_SWIZZLE_R, GL_BLUE);
            glTexParameteri(target, GL_TEXTURE_SWIZZLE_G, GL_GREEN);
            glTexParameteri(target, GL_TEXTURE_SWIZZLE_B, GL_RED);
            glTexParameteri(target, GL_TEXTURE_SWIZZLE_A, GL_ALPHA);
        } else {
            // Reset to identity swizzle to prevent color glitches on normal RGBA textures
            glTexParameteri(target, GL_TEXTURE_SWIZZLE_R, GL_RED);
            glTexParameteri(target, GL_TEXTURE_SWIZZLE_G, GL_GREEN);
            glTexParameteri(target, GL_TEXTURE_SWIZZLE_B, GL_BLUE);
            glTexParameteri(target, GL_TEXTURE_SWIZZLE_A, GL_ALPHA);
        }
    }

}
