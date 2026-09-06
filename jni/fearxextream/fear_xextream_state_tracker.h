#ifndef FEAR_XEXTREAM_STATE_TRACKER_H
#define FEAR_XEXTREAM_STATE_TRACKER_H

#include "fear_xextream_core.h"
#include <mutex>
#include <unordered_map>
#include <cstdint>

namespace FearXextream {

    struct GLStateCache {
        GLuint activeProgram = 0;
        GLuint activeVAO = 0;
        GLuint activeFBO = 0;
        GLuint activeTextureUnits[16] = {0};
        bool depthTestEnabled = false;
        bool blendEnabled = false;
        bool cullFaceEnabled = false;
    };

    class StateTracker {
    public:
        static StateTracker& getInstance();
        void bindProgram(GLuint program);
        void bindTexture(GLuint unit, GLuint texture);
        void setDepthTest(bool enable);
        void setBlend(bool enable);
        void optimizeCurrentState();
        const GLStateCache& getCache() const { return m_cache; }

    private:
        StateTracker() = default;
        std::mutex m_mutex;
        GLStateCache m_cache;
    };

}

#endif // FEAR_XEXTREAM_STATE_TRACKER_H
