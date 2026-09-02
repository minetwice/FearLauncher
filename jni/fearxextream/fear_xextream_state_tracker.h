#ifndef FEAR_XEXTREAM_STATE_TRACKER_H
#define FEAR_XEXTREAM_STATE_TRACKER_H

#include <atomic>
#include <cstdint>
#include <vector>
#include <mutex>
#include <GLES3/gl32.h>

namespace FearXextream {

    struct alignas(16) GLBlendState {
        bool enabled = false;
        GLenum srcRGB = GL_ONE;
        GLenum dstRGB = GL_ZERO;
        GLenum srcAlpha = GL_ONE;
        GLenum dstAlpha = GL_ZERO;
    };

    struct alignas(16) GLDepthState {
        bool testEnabled = false;
        bool writeMask = true;
        GLenum func = GL_LESS;
    };

    struct alignas(16) GLRasterizerState {
        bool cullEnabled = false;
        GLenum cullFace = GL_BACK;
        GLenum frontFace = GL_CCW;
        bool polygonOffsetFill = false;
        float factor = 0.0f;
        float units = 0.0f;
    };

    class StateTracker {
    public:
        static StateTracker& getInstance();

        void setBlendState(bool enabled, GLenum srcRGB, GLenum dstRGB, GLenum srcAlpha, GLenum dstAlpha);
        void setDepthState(bool testEnabled, bool writeMask, GLenum func);
        void setRasterizerState(bool cullEnabled, GLenum cullFace, GLenum frontFace);

        void bindTexture(GLenum target, GLuint texture);
        void bindFramebuffer(GLenum target, GLuint framebuffer);

        uint64_t getDrawCallCount() const { return m_drawCalls.load(std::memory_order_relaxed); }
        void incrementDrawCall() { m_drawCalls.fetch_add(1, std::memory_order_relaxed); }

        void resetStats();

    private:
        StateTracker() = default;

        GLBlendState m_blendState;
        GLDepthState m_depthState;
        GLRasterizerState m_rasterizerState;

        GLuint m_boundTexture2D = 0;
        GLuint m_boundFramebuffer = 0;

        std::atomic<uint64_t> m_drawCalls{0};
        mutable std::mutex m_mutex;
    };

}

#endif // FEAR_XEXTREAM_STATE_TRACKER_H
