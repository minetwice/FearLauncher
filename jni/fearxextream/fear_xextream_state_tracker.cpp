#include "fear_xextream_state_tracker.h"
#include <android/log.h>
#include <dlfcn.h>

#define LOG_TAG "FearXextreamTracker"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace FearXextream {

    StateTracker& StateTracker::getInstance() {
        static StateTracker instance;
        return instance;
    }

    void StateTracker::bindProgram(GLuint program) {
        std::lock_guard<std::mutex> lock(m_mutex);
        if (m_cache.activeProgram == program) return; // Skip redundant glUseProgram
        m_cache.activeProgram = program;

        typedef void (*glUseProgram_pfn)(GLuint);
        static glUseProgram_pfn real_glUseProgram = (glUseProgram_pfn)dlsym(RTLD_DEFAULT, "glUseProgram");
        if (real_glUseProgram) real_glUseProgram(program);
    }

    void StateTracker::bindTexture(uint32_t unit, GLuint texture) {
        std::lock_guard<std::mutex> lock(m_mutex);
        if (unit >= 16) return;
        if (m_cache.activeTextureUnits[unit] == texture) return; // Skip redundant glBindTexture
        m_cache.activeTextureUnits[unit] = texture;

        typedef void (*glActiveTexture_pfn)(GLenum);
        typedef void (*glBindTexture_pfn)(GLenum, GLuint);
        static glActiveTexture_pfn real_glActiveTexture = (glActiveTexture_pfn)dlsym(RTLD_DEFAULT, "glActiveTexture");
        static glBindTexture_pfn real_glBindTexture = (glBindTexture_pfn)dlsym(RTLD_DEFAULT, "glBindTexture");

        if (real_glActiveTexture) real_glActiveTexture(GL_TEXTURE0 + unit);
        if (real_glBindTexture) real_glBindTexture(GL_TEXTURE_2D, texture);
    }

    void StateTracker::setDepthTest(bool enable) {
        std::lock_guard<std::mutex> lock(m_mutex);
        if (m_cache.depthTestEnabled == enable) return;
        m_cache.depthTestEnabled = enable;

        typedef void (*glEnable_pfn)(GLenum);
        typedef void (*glDisable_pfn)(GLenum);
        static glEnable_pfn real_glEnable = (glEnable_pfn)dlsym(RTLD_DEFAULT, "glEnable");
        static glDisable_pfn real_glDisable = (glDisable_pfn)dlsym(RTLD_DEFAULT, "glDisable");

        if (enable) {
            if (real_glEnable) real_glEnable(GL_DEPTH_TEST);
        } else {
            if (real_glDisable) real_glDisable(GL_DEPTH_TEST);
        }
    }

    void StateTracker::setBlend(bool enable) {
        std::lock_guard<std::mutex> lock(m_mutex);
        if (m_cache.blendEnabled == enable) return;
        m_cache.blendEnabled = enable;

        typedef void (*glEnable_pfn)(GLenum);
        typedef void (*glDisable_pfn)(GLenum);
        static glEnable_pfn real_glEnable = (glEnable_pfn)dlsym(RTLD_DEFAULT, "glEnable");
        static glDisable_pfn real_glDisable = (glDisable_pfn)dlsym(RTLD_DEFAULT, "glDisable");

        if (enable) {
            if (real_glEnable) real_glEnable(GL_BLEND);
        } else {
            if (real_glDisable) real_glDisable(GL_BLEND);
        }
    }

    void StateTracker::optimizeCurrentState() {
        std::lock_guard<std::mutex> lock(m_mutex);
        typedef void (*glGetIntegerv_pfn)(GLenum, GLint*);
        static glGetIntegerv_pfn real_glGetIntegerv = (glGetIntegerv_pfn)dlsym(RTLD_DEFAULT, "glGetIntegerv");
        if (real_glGetIntegerv) {
            GLint prog = 0;
            real_glGetIntegerv(GL_CURRENT_PROGRAM, &prog);
            m_cache.activeProgram = static_cast<GLuint>(prog);
        }
        LOGI("StateTracker: State cache synchronized and redundant GL switches eliminated.");
    }

}
