#include "turbo_v1_core.h"
#include "turbo_v1_perf.h"
#include "turbo_v1_entity.h"
#include <jni.h>

namespace turbo_v1 {

static Context g_ctx;
static Features g_features;

bool init(Context& ctx, const std::string& cache_path) {
    LOGI("Indus2.0: Initializing Epic FPS Booster Engine (cache: %s)", cache_path.c_str());
    ctx.cache_path = cache_path;
    ctx.initialized = true;
    ctx.shader_cache_enabled = true;
    ctx.color_vibrance_enabled = true;
    ctx.egl_display = EGL_NO_DISPLAY;
    ctx.egl_surface = EGL_NO_SURFACE;
    ctx.egl_context = EGL_NO_CONTEXT;
    ctx.window_width = 0;
    ctx.window_height = 0;
    g_features.has_buffer_storage = true;
    g_features.has_direct_state_access = true;
    g_features.has_shader_image_load_store = true;
    g_features.has_compute_shader = true;
    g_features.has_multi_draw_indirect = true;
    g_features.has_texture_cube_map_array = true;
    g_features.has_shader_storage_buffer = true;
    g_features.has_uniform_buffer_object = true;
    g_features.has_tessellation_shader = true;
    g_features.has_geometry_shader = true;
    g_features.has_bindless_texture = true;
    g_ctx = ctx;
    perf_init();
    EntityManager::instance().init();
    LOGI("Indus2.0: Epic FPS Booster Engine ready — targeting 200+ FPS with smart resource management");
    return true;
}

void shutdown(Context& ctx) {
    LOGI("Indus2.0: Shutting down Epic FPS Booster Engine");
    EntityManager::instance().shutdown();
    perf_shutdown();
    ctx.initialized = false;
}

const Features& get_features() { return g_features; }
const char* get_version_string() { return "4.6.0 Indus2.0 TurboV1 Epic FPS Booster 2.0"; }
const char* get_renderer_string() { return "Indus2.0 TurboV1 Epic FPS Booster Engine"; }
const char* get_vendor_string() { return "NVIDIA Corporation"; }

} // namespace turbo_v1

extern "C" {

JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_initTurboV1Engine(JNIEnv* env, jclass cls, jstring cachePath) {
    const char* path = env->GetStringUTFChars(cachePath, nullptr);
    turbo_v1::Context ctx;
    turbo_v1::init(ctx, path ? path : "");
    if (path) env->ReleaseStringUTFChars(cachePath, path);
}

JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_turboV1FrameBegin(JNIEnv* env, jclass cls) { turbo_v1::perf_frame_begin(); }
JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_turboV1FrameEnd(JNIEnv* env, jclass cls) { turbo_v1::perf_frame_end(); }
JNIEXPORT double JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_turboV1GetFPS(JNIEnv* env, jclass cls) { return turbo_v1::perf_get_state().fps.load(); }
JNIEXPORT jint JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_turboV1GetResolutionScale(JNIEnv* env, jclass cls) { return turbo_v1::perf_get_resolution_scale(); }
JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_turboV1SetTargetFPS(JNIEnv* env, jclass cls, jint fps) { turbo_v1::perf_set_target_fps(fps); }
JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_turboV1PinRenderThread(JNIEnv* env, jclass cls) { turbo_v1::perf_pin_render_thread(); }
JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_turboV1GPUBoost(JNIEnv* env, jclass cls, jboolean enable) { turbo_v1::perf_gpu_boost(enable); }
JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_turboV1SubmitEntityBatch(JNIEnv* env, jclass cls) { turbo_v1::EntityManager::instance().submit_batched_draws(); }
JNIEXPORT jint JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_turboV1GetEntitiesDrawn(JNIEnv* env, jclass cls) { return turbo_v1::EntityManager::instance().get_drawn_count(); }
JNIEXPORT jint JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_turboV1GetEntitiesBatched(JNIEnv* env, jclass cls) { return turbo_v1::EntityManager::instance().get_batched_count(); }
JNIEXPORT jint JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_turboV1GetEntitiesCulled(JNIEnv* env, jclass cls) { return turbo_v1::EntityManager::instance().get_culled_count(); }

} // extern "C"
