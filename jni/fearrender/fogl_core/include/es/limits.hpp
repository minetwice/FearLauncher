#pragma once

#include "utils/log.hpp"

#include <GLES3/gl32.h>
#include <GLES2/gl2ext.h>
#include <EGL/egl.h>

#define GETVAL(type, name, dest) glGet##type##v(name, dest)

namespace ESLimits {
    inline GLint MAX_TEXTURE_SIZE = 4096;
    inline GLfloat MAX_TEXTURE_MAX_ANISOTROPY_EXT = 16.0f;

    inline void init() {
        try {
            if (eglGetCurrentContext() == EGL_NO_CONTEXT) {
                LOGI("[FearRender] ESLimits::init deferred (no context yet)");
                return;
            }
            GETVAL(Integer, GL_MAX_TEXTURE_SIZE, &MAX_TEXTURE_SIZE);
            GETVAL(Float, GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT, &MAX_TEXTURE_MAX_ANISOTROPY_EXT);

            LOGI("GL_MAX_TEXTURE_SIZE: %d", MAX_TEXTURE_SIZE);
            LOGI("GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT: %f", MAX_TEXTURE_MAX_ANISOTROPY_EXT);
        } catch (...) {
            LOGW("[FearRender] ESLimits::init exception caught, using defaults");
        }
    }
}
