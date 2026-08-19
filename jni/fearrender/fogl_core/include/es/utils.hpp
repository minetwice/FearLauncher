#pragma once

#include "gles/ffp/enums.hpp"
#include "utils/log.hpp"
#include "utils/types.hpp"

#include <atomic>
#include <GLES3/gl31.h>
#include <EGL/egl.h>
#include <unordered_set>
#include <tuple>
#include <utility>
#include <stdexcept>
#include <string>

inline std::atomic_bool esUtilsInitialized = false;
inline std::atomic_bool g_versionPending = false;

namespace ESUtils {

inline void initExtensionsES3();

inline std::pair<int, int> version = std::make_pair(3, 2); // major, minor default
inline int shadingVersion = 320; // (major * 100) + (minor * 10)

inline std::unordered_set<std::string> realExtensions;
inline std::unordered_set<std::string> fakeExtensions;

inline bool isAngle = false;
inline std::tuple<int, int, int> angleVersion = std::make_tuple(0, 0, 0);

inline void performDeferredInit() {
    if (!g_versionPending.exchange(false)) return;

    try {
        str versionStr = reinterpret_cast<str>(glGetString(GL_VERSION));
        if (!versionStr) {
            LOGW("[FearRender] glGetString(GL_VERSION) returned null during deferred init, assuming GLES 3.2");
            version = std::make_pair(3, 2);
            shadingVersion = 320;
            LOGI("[FearRender] GLES version detected: OpenGL ES 3.2");
            return;
        }

        int major = 3, minor = 2;
        if (sscanf(versionStr, "OpenGL ES %d.%d", &major, &minor) != 2) {
            LOGW("[FearRender] Failed to parse version string '%s', assuming GLES 3.2", versionStr);
            major = 3; minor = 2;
        }

        version = std::make_pair(major, minor);
        shadingVersion = (major * 100) + (minor * 10);
        LOGI("[FearRender] GLES version detected: OpenGL ES %d.%d", major, minor);

        int angleMajor = 0, angleMinor = 0, anglePatch = 0;
        if (sscanf(versionStr, "(ANGLE %d.%d.%d", &angleMajor, &angleMinor, &anglePatch) == 3) {
            isAngle = true;
            angleVersion = std::make_tuple(angleMajor, angleMinor, anglePatch);
        }

        try {
            initExtensionsES3();
        } catch (...) {
            LOGW("[FearRender] Extension init skipped/failed, continuing");
        }
    } catch (const std::exception& e) {
        LOGW("[FearRender] Error during GL queries: %s, defaulting to GLES 3.2", e.what());
        version = std::make_pair(3, 2);
        shadingVersion = 320;
        LOGI("[FearRender] GLES version detected: OpenGL ES 3.2");
    } catch (...) {
        LOGW("[FearRender] Unknown error during GL queries, defaulting to GLES 3.2");
        version = std::make_pair(3, 2);
        shadingVersion = 320;
        LOGI("[FearRender] GLES version detected: OpenGL ES 3.2");
    }
}

inline void init() {
    if (esUtilsInitialized.exchange(true)) return;

    EGLContext ctx = eglGetCurrentContext();
    if (ctx == EGL_NO_CONTEXT) {
        g_versionPending = true;
        version = std::make_pair(3, 2);
        shadingVersion = 320;
        LOGI("[FearRender] GL version detection deferred (no context yet)");
        return;
    }

    g_versionPending = true;
    performDeferredInit();
}

inline bool isExtensionSupported(std::string name) {
    if (g_versionPending.load() && eglGetCurrentContext() != EGL_NO_CONTEXT) {
        performDeferredInit();
    }
    return realExtensions.find(name) != realExtensions.end();
}

inline void initExtensionsES3() {
    GLint extensionCount = 0;
    glGetIntegerv(GL_NUM_EXTENSIONS, &extensionCount);

    for (GLint i = 0; i < extensionCount; ++i) {
        str extension = reinterpret_cast<str>(glGetStringi(GL_EXTENSIONS, i));
        if (extension) ESUtils::realExtensions.insert(std::string(extension));
    }
    fakeExtensions = realExtensions;
}

inline GLenum getComponentTypeFromFormat(GLint format) {
    switch (format) {
        case GL_R32F:
        case GL_RG32F:
        case GL_RGB32F:
        case GL_RGBA32F:
        case GL_R16F:
        case GL_RG16F:
        case GL_RGB16F:
        case GL_RGBA16F:
            return GL_FLOAT;

        case GL_R8I:
        case GL_R16I:
        case GL_R32I:
        case GL_RG8I:
        case GL_RG16I:
        case GL_RG32I:
        case GL_RGB8I:
        case GL_RGB16I:
        case GL_RGB32I:
        case GL_RGBA8I:
        case GL_RGBA16I:
        case GL_RGBA32I:
            return GL_INT;

        case GL_R8UI:
        case GL_R16UI:
        case GL_R32UI:
        case GL_RG8UI:
        case GL_RG16UI:
        case GL_RG32UI:
        case GL_RGB8UI:
        case GL_RGB16UI:
        case GL_RGB32UI:
        case GL_RGBA8UI:
        case GL_RGBA16UI:
        case GL_RGBA32UI:
            return GL_UNSIGNED_INT;

        case GL_R8:
        case GL_RG8:
        case GL_RGB8:
        case GL_RGBA8:
        case 0x822a:
        case 0x822c:
        case 0x8050:
        case 0x805b:
        case GL_RGB10_A2:
            return GL_UNSIGNED_NORMALIZED;

        case GL_R8_SNORM:
        case GL_RG8_SNORM:
        case GL_RGB8_SNORM:
        case GL_RGBA8_SNORM:
            return GL_SIGNED_NORMALIZED;

        case GL_DEPTH_COMPONENT16:
        case GL_DEPTH_COMPONENT24:
        case GL_DEPTH_COMPONENT32F:
            return GL_FLOAT;

        default:
            return GL_UNSIGNED_NORMALIZED;
    }
}

inline bool isSRGBFormat(GLint format) {
    switch (format) {
        case GL_SRGB:
        case GL_SRGB8:
        case GL_SRGB8_ALPHA8:
        case 0x8c48:
        case 0x8c49:
            return true;
        default:
            return false;
    }
}

namespace TypeTraits {

template <typename T>
struct GLTypeEnum;

template<GLenum T>
struct GLPrimitive;

#define GL_TYPE_ENUM(podt, glt) template<> struct GLTypeEnum<podt> { static constexpr GLenum value = glt; }
#define GL_PRIMITIVE(glt, podt) template<> struct GLPrimitive<glt> { using type = podt; }

GL_TYPE_ENUM(GLubyte, GL_UNSIGNED_BYTE);
GL_TYPE_ENUM(GLuint, GL_UNSIGNED_INT);
GL_TYPE_ENUM(GLshort, GL_SHORT);
GL_TYPE_ENUM(GLint, GL_INT);
GL_TYPE_ENUM(GLfloat, GL_FLOAT);
GL_TYPE_ENUM(GLdouble, GL_DOUBLE);

GL_PRIMITIVE(GL_UNSIGNED_BYTE, GLubyte);
GL_PRIMITIVE(GL_UNSIGNED_INT, GLuint);
GL_PRIMITIVE(GL_SHORT, GLshort);
GL_PRIMITIVE(GL_INT, GLint);
GL_PRIMITIVE(GL_FLOAT, GLfloat);
GL_PRIMITIVE(GL_DOUBLE, GLdouble);

inline GLsizei getTypeSize(GLenum type) {
    switch (type) {
        case GL_UNSIGNED_BYTE: return sizeof(GLubyte);
        case GL_UNSIGNED_INT: return sizeof(GLuint);
        case GL_SHORT: return sizeof(GLshort);
        case GL_INT: return sizeof(GLint);
        case GL_FLOAT: return sizeof(GLfloat);
        case GL_DOUBLE: return sizeof(GLdouble);
        default:
            LOGE("Unhandled type! (type=%u)", type);
            return sizeof(GLuint);
    }
}

template<typename Func>
void typeToPrimitive(GLenum type, const Func&& func) {
    switch (type) {
        case GL_UNSIGNED_BYTE:
            func.template operator()<GLPrimitive<GL_UNSIGNED_BYTE>::type>();
            break;

        case GL_UNSIGNED_INT:
            func.template operator()<GLPrimitive<GL_UNSIGNED_INT>::type>();
            break;

        case GL_SHORT:
            func.template operator()<GLPrimitive<GL_SHORT>::type>();
            break;

        case GL_INT:
            func.template operator()<GLPrimitive<GL_INT>::type>();
            break;

        case GL_FLOAT:
            func.template operator()<GLPrimitive<GL_FLOAT>::type>();
            break;

        case GL_DOUBLE:
            func.template operator()<GLPrimitive<GL_DOUBLE>::type>();
            break;

        default:
            LOGE("Unhandled type! (type=%u)", type);
            func.template operator()<GLPrimitive<GL_UNSIGNED_INT>::type>();
            break;
    }
}

template<typename T>
inline const T* asTypedArray(const void* array) {
    return static_cast<const T*>(array);
}

}

}
