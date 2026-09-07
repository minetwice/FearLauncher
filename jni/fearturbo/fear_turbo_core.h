#ifndef FEAR_TURBO_CORE_H
#define FEAR_TURBO_CORE_H

#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <unordered_map>
#include <mutex>
#include <cstdint>

#define LOG_TAG "FearTurbo"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

namespace fear_turbo {

// ============================================================================
// Fear Turbo Engine — Core Types and Structures
// ============================================================================

struct TurboContext {
    EGLDisplay egl_display;
    EGLSurface egl_surface;
    EGLContext egl_context;
    EGLConfig  egl_config;
    int32_t    window_width;
    int32_t    window_height;
    int32_t    gl_version_major;
    int32_t    gl_version_minor;
    bool       initialized;
    bool       use_shadow_buffers;
    bool       shader_cache_enabled;
    std::string cache_path;
};

// OpenGL feature detection (what the game expects vs what GLES provides)
struct FeatureMap {
    bool has_gl_buffer_storage;     // GL_ARB_buffer_storage
    bool has_gl_direct_state_access; // GL_ARB_direct_state_access
    bool has_gl_map_persistent;     // GL_ARB_map_persistent
    bool has_gl_map_coherent;       // GL_ARB_map_coherent
    bool has_gl_shader_image_load_store; // GL_ARB_shader_image_load_store
    bool has_gl_compute_shader;     // GL_ARB_compute_shader
    bool has_gl_multi_draw_indirect; // GL_ARB_multi_draw_indirect
    bool has_gl_texture_cube_map_array; // GL_ARB_texture_cube_map_array
    bool has_gl_shader_storage_buffer_object; // GL_ARB_shader_storage_buffer_object
    bool has_gl_uniform_buffer_object; // GL_ARB_uniform_buffer_object
};

// Initialize the Fear Turbo engine
bool init(TurboContext& ctx, const std::string& cache_path);

// Shutdown
void shutdown(TurboContext& ctx);

// Get the fake feature map (we report all desktop GL features as available)
const FeatureMap& get_feature_map();

// Report fake GL version string (e.g. "4.6.0 FearTurbo 1.0")
const char* get_gl_version_string();
const char* get_gl_renderer_string();
const char* get_gl_vendor_string();

} // namespace fear_turbo

#endif // FEAR_TURBO_CORE_H
