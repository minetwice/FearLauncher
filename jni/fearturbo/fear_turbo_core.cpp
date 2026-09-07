#include "fear_turbo_core.h"

namespace fear_turbo {

static TurboContext g_ctx;
static FeatureMap g_features;

bool init(TurboContext& ctx, const std::string& cache_path) {
    LOGI("FearTurbo: Initializing engine (cache: %s)", cache_path.c_str());
    
    ctx.cache_path = cache_path;
    ctx.initialized = false;
    ctx.use_shadow_buffers = true;
    ctx.shader_cache_enabled = true;
    ctx.egl_display = EGL_NO_DISPLAY;
    ctx.egl_surface = EGL_NO_SURFACE;
    ctx.egl_context = EGL_NO_CONTEXT;
    ctx.window_width = 0;
    ctx.window_height = 0;
    ctx.gl_version_major = 3;
    ctx.gl_version_minor = 2;

    // Set up feature map — we report ALL desktop GL features as available
    // so Minecraft/Sodium/Iris think they're running on a full GL 4.6 driver
    g_features.has_gl_buffer_storage = true;
    g_features.has_gl_direct_state_access = true;
    g_features.has_gl_map_persistent = true;
    g_features.has_gl_map_coherent = true;
    g_features.has_gl_shader_image_load_store = true;
    g_features.has_gl_compute_shader = true;
    g_features.has_gl_multi_draw_indirect = true;
    g_features.has_gl_texture_cube_map_array = true;
    g_features.has_gl_shader_storage_buffer_object = true;
    g_features.has_gl_uniform_buffer_object = true;

    // Query actual GLES version
    const char* version = (const char*)glGetString(GL_VERSION);
    if (version) {
        LOGI("FearTurbo: Native GLES version: %s", version);
    }

    ctx.initialized = true;
    g_ctx = ctx;
    
    LOGI("FearTurbo: Engine initialized successfully");
    return true;
}

void shutdown(TurboContext& ctx) {
    LOGI("FearTurbo: Shutting down engine");
    ctx.initialized = false;
}

const FeatureMap& get_feature_map() {
    return g_features;
}

const char* get_gl_version_string() {
    return "4.6.0 FearTurbo 1.0";
}

const char* get_gl_renderer_string() {
    return "FearTurbo GL ES Translation Engine";
}

const char* get_gl_vendor_string() {
    return "FearLauncher";
}

} // namespace fear_turbo

// JNI entry point
extern "C" {
#include <jni.h>

JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_initFearTurboEngine(JNIEnv* env, jclass cls, jstring cachePath) {
    const char* path = env->GetStringUTFChars(cachePath, nullptr);
    fear_turbo::TurboContext ctx;
    fear_turbo::init(ctx, path);
    env->ReleaseStringUTFChars(cachePath, path);
}

} // extern "C"
