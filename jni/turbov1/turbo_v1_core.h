#ifndef TURBO_V1_CORE_H
#define TURBO_V1_CORE_H

#include <EGL/egl.h>
#include <GLES3/gl32.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <unordered_map>
#include <mutex>
#include <cstdint>

#define LOG_TAG "TurboV1"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

namespace turbo_v1 {

// ============================================================================
// TurboV1 Engine — Core Architecture & Feature Matrix
// NextGen OpenGL ES 3.2 Translation & High-FPS Shader Engine
// ============================================================================

struct Context {
    EGLDisplay egl_display;
    EGLSurface egl_surface;
    EGLContext egl_context;
    int32_t    window_width;
    int32_t    window_height;
    bool       initialized;
    bool       shader_cache_enabled;
    bool       color_vibrance_enabled;
    std::string cache_path;
};

struct Features {
    bool has_buffer_storage;            // GL_ARB_buffer_storage
    bool has_direct_state_access;       // GL_ARB_direct_state_access
    bool has_shader_image_load_store;   // GL_ARB_shader_image_load_store
    bool has_compute_shader;            // GL_ARB_compute_shader
    bool has_multi_draw_indirect;       // GL_ARB_multi_draw_indirect
    bool has_texture_cube_map_array;    // GL_ARB_texture_cube_map_array
    bool has_shader_storage_buffer;     // GL_ARB_shader_storage_buffer_object
    bool has_uniform_buffer_object;     // GL_ARB_uniform_buffer_object
    bool has_tessellation_shader;       // GL_ARB_tessellation_shader
    bool has_geometry_shader;           // GL_ARB_geometry_shader4
    bool has_bindless_texture;          // GL_ARB_bindless_texture
};

// Engine Lifecycle
bool init(Context& ctx, const std::string& cache_path);
void shutdown(Context& ctx);

const Features& get_features();

// Strings
const char* get_version_string();
const char* get_renderer_string();
const char* get_vendor_string();

} // namespace turbo_v1

#endif // TURBO_V1_CORE_H
