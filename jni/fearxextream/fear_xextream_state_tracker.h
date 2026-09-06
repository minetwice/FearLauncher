#ifndef FEAR_XEXTREAM_STATE_TRACKER_H
#define FEAR_XEXTREAM_STATE_TRACKER_H

#include "fear_xextream_core.h"
#include <mutex>
#include <atomic>
#include <unordered_map>

namespace FearXextream {

    struct GLStateCache {
        GLuint activeProgram = 0;
        GLuint activeVAO = 0;
        GLuint activeFBO = 0;
        GLuint activeRBO = 0;
        GLuint activeArrayBuffer = 0;
        GLuint activeElementBuffer = 0;
        GLuint activeTextureUnits[32] = {0};
        GLenum activeTextureTarget[32] = {0};
        int activeTextureUnit = 0;
        bool depthTestEnabled = false;
        bool depthMask = true;
        bool blendEnabled = false;
        bool cullFaceEnabled = false;
        bool scissorEnabled = false;
        bool stencilEnabled = false;
        GLenum depthFunc = GL_LESS;
        GLint viewport[4] = {0, 0, 0, 0};
        GLint scissor[4] = {0, 0, 0, 0};
        GLboolean colorMask[4] = {GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE};
        uint64_t redundantSkipCount = 0;
        uint64_t stateChangeCount = 0;
    };

    class StateTracker {
    public:
        static StateTracker& getInstance();
        void bindProgram(GLuint program);
        void bindVAO(GLuint vao);
        void bindFBO(GLenum target, GLuint fbo);
        void bindBuffer(GLenum target, GLuint buffer);
        void bindTexture(uint32_t unit, GLuint texture, GLenum target = GL_TEXTURE_2D);
        void setActiveTextureUnit(int unit);
        void setDepthTest(bool enable);
        void setDepthMask(bool enable);
        void setDepthFunc(GLenum func);
        void setBlend(bool enable);
        void setCullFace(bool enable);
        void setScissor(bool enable);
        void setViewport(GLint x, GLint y, GLsizei w, GLsizei h);
        void setColorMask(GLboolean r, GLboolean g, GLboolean b, GLboolean a);
        void optimizeCurrentState();
        void resetCache();
        const GLStateCache& getCache() const { return m_cache; }
        uint64_t getRedundantSkips() const { return m_cache.redundantSkipCount; }

    private:
        StateTracker() = default;
        std::mutex m_mutex;
        GLStateCache m_cache;
        bool m_enabled = true;
    };

}

#endif // FEAR_XEXTREAM_STATE_TRACKER_H
