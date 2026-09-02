#include "fear_xextream_state_tracker.h"
#include <android/log.h>

#define LOG_TAG "FearXextreamTracker"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace FearXextream {

    StateTracker& StateTracker::getInstance() {
        static StateTracker instance;
        return instance;
    }

    void StateTracker::setBlendState(bool enabled, GLenum srcRGB, GLenum dstRGB, GLenum srcAlpha, GLenum dstAlpha) {
        std::lock_guard<std::mutex> lock(m_mutex);
        if (m_blendState.enabled != enabled ||
            m_blendState.srcRGB != srcRGB || m_blendState.dstRGB != dstRGB ||
            m_blendState.srcAlpha != srcAlpha || m_blendState.dstAlpha != dstAlpha) {

            m_blendState.enabled = enabled;
            m_blendState.srcRGB = srcRGB;
            m_blendState.dstRGB = dstRGB;
            m_blendState.srcAlpha = srcAlpha;
            m_blendState.dstAlpha = dstAlpha;

            if (enabled) {
                glEnable(GL_BLEND);
                glBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
            } else {
                glDisable(GL_BLEND);
            }
        }
    }

    void StateTracker::setDepthState(bool testEnabled, bool writeMask, GLenum func) {
        std::lock_guard<std::mutex> lock(m_mutex);
        if (m_depthState.testEnabled != testEnabled) {
            m_depthState.testEnabled = testEnabled;
            if (testEnabled) glEnable(GL_DEPTH_TEST);
            else glDisable(GL_DEPTH_TEST);
        }

        if (m_depthState.writeMask != writeMask) {
            m_depthState.writeMask = writeMask;
            glDepthMask(writeMask ? GL_TRUE : GL_FALSE);
        }

        if (m_depthState.func != func) {
            m_depthState.func = func;
            glDepthFunc(func);
        }
    }

    void StateTracker::setRasterizerState(bool cullEnabled, GLenum cullFace, GLenum frontFace) {
        std::lock_guard<std::mutex> lock(m_mutex);
        if (m_rasterizerState.cullEnabled != cullEnabled) {
            m_rasterizerState.cullEnabled = cullEnabled;
            if (cullEnabled) glEnable(GL_CULL_FACE);
            else glDisable(GL_CULL_FACE);
        }

        if (m_rasterizerState.cullFace != cullFace) {
            m_rasterizerState.cullFace = cullFace;
            glCullFace(cullFace);
        }

        if (m_rasterizerState.frontFace != frontFace) {
            m_rasterizerState.frontFace = frontFace;
            glFrontFace(frontFace);
        }
    }

    void StateTracker::bindTexture(GLenum target, GLuint texture) {
        std::lock_guard<std::mutex> lock(m_mutex);
        if (target == GL_TEXTURE_2D && m_boundTexture2D != texture) {
            m_boundTexture2D = texture;
            glBindTexture(target, texture);
        }
    }

    void StateTracker::bindFramebuffer(GLenum target, GLuint framebuffer) {
        std::lock_guard<std::mutex> lock(m_mutex);
        if (m_boundFramebuffer != framebuffer) {
            m_boundFramebuffer = framebuffer;
            glBindFramebuffer(target, framebuffer);
        }
    }

    void StateTracker::resetStats() {
        m_drawCalls.store(0, std::memory_order_relaxed);
    }

}
