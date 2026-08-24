/*
 * quasar_shader_bridge.cpp - JNI bridge for Quasar shader transpilation
 *
 * Provides two native methods:
 * 1. GlslangCompiler.nativeCompileToSPIRV(stage, sourceCode, fileName) -> int[]
 * 2. SpirvCrossTranspiler.nativeTranspileToGLSL(spirv, glslVersion, isGLES) -> String
 *
 * Build: requires glslang + SPIRV-Cross as static libraries (see CMakeLists.txt)
 */

#include <jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>

#include <glslang/Include/glslang_c_interface.h>
#include <glslang/Public/resource_limits_c.h>
#include <spirv_cross_c.h>

#define LOG_TAG "Quasar-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

/* ---- glslang stage mapping ----
 * Java constants in GlslangCompiler.java:
 *   STAGE_VERTEX=0, STAGE_GEOMETRY=1, STAGE_TESSCTRL=2,
 *   STAGE_TESSEVAL=3, STAGE_FRAGMENT=4, STAGE_COMPUTE=5
 * glslang_stage_t enum order:
 *   VERTEX=0, TESSCONTROL=1, TESSEVALUATION=2, GEOMETRY=3,
 *   FRAGMENT=4, COMPUTE=5, ...
 * Note: Java order differs from glslang for geometry/tess!
 */
static glslang_stage_t map_stage(jint jStage) {
    switch(jStage) {
        case 0: return GLSLANG_STAGE_VERTEX;
        case 1: return GLSLANG_STAGE_GEOMETRY;
        case 2: return GLSLANG_STAGE_TESSCONTROL;
        case 3: return GLSLANG_STAGE_TESSEVALUATION;
        case 4: return GLSLANG_STAGE_FRAGMENT;
        case 5: return GLSLANG_STAGE_COMPUTE;
        default: return GLSLANG_STAGE_VERTEX;
    }
}

/* ---- Thread safety: glslang is NOT re-entrant ---- */
static pthread_mutex_t g_glslang_mutex = PTHREAD_MUTEX_INITIALIZER;
static int g_glslang_initialized = 0;

static void ensure_glslang_init() {
    pthread_mutex_lock(&g_glslang_mutex);
    if (!g_glslang_initialized) {
        int ok = glslang_initialize_process();
        if (ok) {
            g_glslang_initialized = 1;
            LOGI("glslang initialized successfully");
        } else {
            LOGE("glslang_initialize_process() FAILED");
        }
    }
    pthread_mutex_unlock(&g_glslang_mutex);
}

/* ===== JNI_OnLoad / JNI_OnUnload ===== */
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    LOGI("quasar_shader JNI_OnLoad");
    ensure_glslang_init();
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    if (g_glslang_initialized) {
        glslang_finalize_process();
        g_glslang_initialized = 0;
        LOGI("glslang finalized");
    }
}

/* ============================================================
 * GlslangCompiler.nativeInitialize() -> void
 * ============================================================ */
JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_quasar_transpile_GlslangCompiler_nativeInitialize(
        JNIEnv *env, jclass cls)
{
    ensure_glslang_init();
}

/* ============================================================
 * GlslangCompiler.nativeCompileToSPIRV(int stage, String sourceCode, String fileName) -> int[]
 * ============================================================ */
JNIEXPORT jintArray JNICALL
Java_net_kdt_pojavlaunch_quasar_transpile_GlslangCompiler_nativeCompileToSPIRV(
        JNIEnv *env, jclass cls, jint jStage, jstring jSourceCode, jstring jFileName)
{
    ensure_glslang_init();
    if (!g_glslang_initialized) {
        LOGE("glslang not initialized, cannot compile");
        return NULL;
    }

    const char *sourceCode = (*env)->GetStringUTFChars(env, jSourceCode, NULL);
    const char *fileName = jFileName ? (*env)->GetStringUTFChars(env, jFileName, NULL) : "shader.glsl";
    if (!sourceCode) {
        LOGE("Failed to get source string");
        return NULL;
    }

    glslang_stage_t stage = map_stage(jStage);
    jintArray result = NULL;

    pthread_mutex_lock(&g_glslang_mutex);

    glslang_input_t input;
    memset(&input, 0, sizeof(input));
    input.language = GLSLANG_SOURCE_GLSL;
    input.stage = stage;
    input.client = GLSLANG_CLIENT_VULKAN;
    input.client_version = GLSLANG_TARGET_VULKAN_1_1;
    input.target_language = GLSLANG_TARGET_SPV;
    input.target_language_version = GLSLANG_TARGET_SPV_1_0;
    input.code = sourceCode;
    input.default_version = 110;
    input.default_profile = GLSLANG_NO_PROFILE;
    input.force_default_version_and_profile = 0;
    input.forward_compatible = 0;
    input.messages = GLSLANG_MSG_DEFAULT_BIT | GLSLANG_MSG_SPV_RULES_BIT | GLSLANG_MSG_VULKAN_RULES_BIT;
    input.resource = glslang_default_resource();

    glslang_shader_t *shader = glslang_shader_create(&input);
    if (!shader) {
        LOGE("glslang_shader_create failed for %s", fileName);
        goto cleanup_strings;
    }

    if (!glslang_shader_preprocess(shader, &input)) {
        LOGE("glslang_shader_preprocess failed for %s: %s", fileName,
             glslang_shader_get_info_log(shader));
        goto cleanup_shader;
    }
    if (!glslang_shader_parse(shader, &input)) {
        LOGE("glslang_shader_parse failed for %s: %s", fileName,
             glslang_shader_get_info_log(shader));
        goto cleanup_shader;
    }

    glslang_program_t *program = glslang_program_create();
    if (!program) {
        LOGE("glslang_program_create failed");
        goto cleanup_shader;
    }
    glslang_program_add_shader(program, shader);

    if (!glslang_program_link(program, GLSLANG_MSG_SPV_RULES_BIT | GLSLANG_MSG_VULKAN_RULES_BIT)) {
        LOGE("glslang_program_link failed for %s: %s", fileName,
             glslang_program_get_info_log(program));
        goto cleanup_program;
    }

    glslang_program_SPIRV_generate(program, stage);

    /* Check for SPIR-V generation errors */
    const char *spv_msg = glslang_program_SPIRV_get_messages(program);
    if (spv_msg) {
        LOGD("glslang SPIRV messages for %s: %s", fileName, spv_msg);
    }

    size_t word_count = glslang_program_SPIRV_get_size(program);
    if (word_count == 0) {
        LOGE("SPIR-V generation produced 0 words for %s", fileName);
        goto cleanup_program;
    }

    LOGD("Compiled %s -> %zu SPIR-V words", fileName, word_count);

    result = (*env)->NewIntArray(env, (jsize)word_count);
    if (result) {
        const unsigned int *spv = glslang_program_SPIRV_get_ptr(program);
        (*env)->SetIntArrayRegion(env, result, 0, (jsize)word_count, (const jint *)spv);
    }

cleanup_program:
    glslang_program_delete(program);
cleanup_shader:
    glslang_shader_delete(shader);
cleanup_strings:
    pthread_mutex_unlock(&g_glslang_mutex);
    (*env)->ReleaseStringUTFChars(env, jSourceCode, sourceCode);
    if (jFileName && fileName) (*env)->ReleaseStringUTFChars(env, jFileName, fileName);
    return result;
}

/* ============================================================
 * SpirvCrossTranspiler.nativeTranspileToGLSL(int[] spirv, int glslVersion, boolean isGLES) -> String
 * ============================================================ */
JNIEXPORT jstring JNICALL
Java_net_kdt_pojavlaunch_quasar_transpile_SpirvCrossTranspiler_nativeTranspileToGLSL(
        JNIEnv *env, jclass cls, jintArray jSpirv, jint jGlslVersion, jboolean jIsGLES)
{
    if (!jSpirv) {
        LOGE("nativeTranspileToGLSL: null SPIR-V input");
        return NULL;
    }

    jsize word_count = (*env)->GetArrayLength(env, jSpirv);
    if (word_count == 0) {
        LOGE("nativeTranspileToGLSL: empty SPIR-V input");
        return NULL;
    }

    jint *spv = (*env)->GetIntArrayElements(env, jSpirv, NULL);
    if (!spv) {
        LOGE("nativeTranspileToGLSL: failed to get SPIR-V array elements");
        return NULL;
    }

    spvc_context ctx = NULL;
    spvc_parsed_ir ir = NULL;
    spvc_compiler compiler = NULL;
    spvc_compiler_options opts = NULL;
    const char *source = NULL;
    jstring result = NULL;

    spvc_result res;

    res = spvc_context_create(&ctx);
    if (res != SPVC_SUCCESS) {
        LOGE("spvc_context_create failed: %d", res);
        goto release_spv;
    }

    res = spvc_context_parse_spirv(ctx, (const SpvId *)spv, (size_t)word_count, &ir);
    if (res != SPVC_SUCCESS) {
        LOGE("spvc_context_parse_spirv failed: %d - %s", res,
             spvc_context_get_last_error_string(ctx));
        goto destroy_ctx;
    }

    res = spvc_context_create_compiler(ctx, SPVC_BACKEND_GLSL, ir,
                                       SPVC_CAPTURE_MODE_TAKE_OWNERSHIP, &compiler);
    if (res != SPVC_SUCCESS) {
        LOGE("spvc_context_create_compiler failed: %d - %s", res,
             spvc_context_get_last_error_string(ctx));
        goto destroy_ctx;
    }

    res = spvc_compiler_create_compiler_options(compiler, &opts);
    if (res != SPVC_SUCCESS) {
        LOGE("spvc_compiler_create_compiler_options failed: %d", res);
        goto destroy_ctx;
    }

    /* Set GLSL version (default 300 for GLES, 330 for desktop) */
    unsigned int glsl_ver = jGlslVersion > 0 ? (unsigned int)jGlslVersion : 300;
    spvc_compiler_options_set_uint(opts, SPVC_COMPILER_OPTION_GLSL_VERSION, glsl_ver);

    /* Set ES mode */
    spvc_compiler_options_set_bool(opts, SPVC_COMPILER_OPTION_GLSL_ES,
                                    jIsGLES ? SPVC_TRUE : SPVC_FALSE);

    /* Enable Vulkan semantics (layout qualifiers compatible with Zink/Mesa) */
    spvc_compiler_options_set_bool(opts, SPVC_COMPILER_OPTION_GLSL_VULKAN_SEMANTICS, SPVC_TRUE);

    /* Use highp default for ES */
    if (jIsGLES) {
        spvc_compiler_options_set_bool(opts, SPVC_COMPILER_OPTION_GLSL_ES_DEFAULT_FLOAT_PRECISION_HIGHP, SPVC_TRUE);
        spvc_compiler_options_set_bool(opts, SPVC_COMPILER_OPTION_GLSL_ES_DEFAULT_INT_PRECISION_HIGHP, SPVC_TRUE);
    }

    res = spvc_compiler_install_compiler_options(compiler, opts);
    if (res != SPVC_SUCCESS) {
        LOGE("spvc_compiler_install_compiler_options failed: %d", res);
        goto destroy_ctx;
    }

    res = spvc_compiler_compile(compiler, &source);
    if (res != SPVC_SUCCESS) {
        LOGE("spvc_compiler_compile failed: %d - %s", res,
             spvc_context_get_last_error_string(ctx));
        goto destroy_ctx;
    }

    if (source) {
        LOGD("SPIRV-Cross: transpiled %d words -> %zu chars GLSL", word_count, strlen(source));
        /* Copy the string BEFORE destroying context */
        result = (*env)->NewStringUTF(env, source);
    }

destroy_ctx:
    /* Context owns everything (ir, compiler, opts, source string) */
    spvc_context_destroy(ctx);
release_spv:
    (*env)->ReleaseIntArrayElements(env, jSpirv, spv, JNI_ABORT);
    return result;
}

/* ============================================================
 * SpirvCrossTranspiler.nativeReflect(int[] spirv) -> String (JSON)
 * ============================================================ */
JNIEXPORT jstring JNICALL
Java_net_kdt_pojavlaunch_quasar_transpile_SpirvCrossTranspiler_nativeReflect(
        JNIEnv *env, jclass cls, jintArray jSpirv)
{
    if (!jSpirv) return NULL;

    jsize word_count = (*env)->GetArrayLength(env, jSpirv);
    if (word_count == 0) return NULL;

    jint *spv = (*env)->GetIntArrayElements(env, jSpirv, NULL);
    if (!spv) return NULL;

    spvc_context ctx = NULL;
    spvc_parsed_ir ir = NULL;
    spvc_compiler compiler = NULL;
    const char *json = NULL;
    jstring result = NULL;

    if (spvc_context_create(&ctx) != SPVC_SUCCESS) goto release;
    if (spvc_context_parse_spirv(ctx, (const SpvId *)spv, (size_t)word_count, &ir) != SPVC_SUCCESS) goto destroy;
    if (spvc_context_create_compiler(ctx, SPVC_BACKEND_NONE, ir,
                                     SPVC_CAPTURE_MODE_TAKE_OWNERSHIP, &compiler) != SPVC_SUCCESS) goto destroy;

    /* For now, return a simple JSON with word count */
    char buf[256];
    snprintf(buf, sizeof(buf), "{\"wordCount\": %d}", word_count);
    result = (*env)->NewStringUTF(env, buf);

destroy:
    spvc_context_destroy(ctx);
release:
    (*env)->ReleaseIntArrayElements(env, jSpirv, spv, JNI_ABORT);
    return result;
}
