#include "fear_plugin_loader.h"
#include <android/log.h>
#include <dlfcn.h>
#include <string.h>
#include <string>
#include <atomic>
#include <unordered_set>
#include <mutex>

#define TAG "FearRender"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Plugin function typedefs
typedef int     (*fear_plugin_init_fn)(void* userdata);
typedef void    (*fear_plugin_shutdown_fn)(void);
typedef void*   (*fear_plugin_get_proc_fn)(const char* name);
typedef const char* (*fear_plugin_get_name_fn)(void);
typedef const char* (*fear_plugin_get_version_fn)(void);

// Plugin state
static void* g_pluginHandle = nullptr;
static std::atomic<bool> g_pluginLoaded(false);

static fear_plugin_init_fn          g_pluginInit = nullptr;
static fear_plugin_shutdown_fn      g_pluginShutdown = nullptr;
static fear_plugin_get_proc_fn      g_pluginGetProc = nullptr;
static fear_plugin_get_name_fn      g_pluginGetName = nullptr;
static fear_plugin_get_version_fn   g_pluginGetVersion = nullptr;

static std::mutex g_overrideMutex;
static std::unordered_set<std::string> g_overriddenFunctions;
static std::string g_pluginName;
static std::string g_pluginVersion;

int fear_plugin_load(const char* path) {
    if (!path || path[0] == '\0') {
        LOGE("[FearPlugin] Load called with null/empty path");
        return -1;
    }

    if (g_pluginLoaded.load()) {
        LOGW("[FearPlugin] A plugin is already loaded, unloading first");
        fear_plugin_unload();
    }

    LOGI("[FearPlugin] Attempting to load renderer plugin: %s", path);

    // Use RTLD_GLOBAL so plugin symbols are visible to the rest of the process
    // and RTLD_NOW to catch missing symbols at load time
    g_pluginHandle = dlopen(path, RTLD_GLOBAL | RTLD_NOW);
    if (!g_pluginHandle) {
        const char* err = dlerror();
        LOGE("[FearPlugin] dlopen failed for '%s': %s", path, err ? err : "unknown");
        return -2;
    }

    // Resolve required symbols
    g_pluginInit = (fear_plugin_init_fn)dlsym(g_pluginHandle, "fear_plugin_init");
    g_pluginShutdown = (fear_plugin_shutdown_fn)dlsym(g_pluginHandle, "fear_plugin_shutdown");
    g_pluginGetProc = (fear_plugin_get_proc_fn)dlsym(g_pluginHandle, "fear_plugin_get_proc");
    g_pluginGetName = (fear_plugin_get_name_fn)dlsym(g_pluginHandle, "fear_plugin_get_name");
    g_pluginGetVersion = (fear_plugin_get_version_fn)dlsym(g_pluginHandle, "fear_plugin_get_version");

    // fear_plugin_get_proc is mandatory
    if (!g_pluginGetProc) {
        LOGE("[FearPlugin] Plugin missing required symbol: fear_plugin_get_proc");
        dlclose(g_pluginHandle);
        g_pluginHandle = nullptr;
        return -3;
    }

    // Call optional init
    if (g_pluginInit) {
        int initResult = g_pluginInit(nullptr);
        if (initResult != 0) {
            LOGE("[FearPlugin] Plugin fear_plugin_init() returned %d", initResult);
            dlclose(g_pluginHandle);
            g_pluginHandle = nullptr;
            g_pluginGetProc = nullptr;
            g_pluginInit = nullptr;
            g_pluginShutdown = nullptr;
            g_pluginGetName = nullptr;
            g_pluginGetVersion = nullptr;
            return -4;
        }
    }

    // Get metadata
    if (g_pluginGetName) {
        const char* name = g_pluginGetName();
        g_pluginName = name ? name : "unknown";
    } else {
        g_pluginName = "unknown";
    }

    if (g_pluginGetVersion) {
        const char* ver = g_pluginGetVersion();
        g_pluginVersion = ver ? ver : "unknown";
    } else {
        g_pluginVersion = "unknown";
    }

    g_pluginLoaded.store(true);
    LOGI("[FearPlugin] Renderer plugin loaded: %s v%s", g_pluginName.c_str(), g_pluginVersion.c_str());

    // Probe common GL functions to count overrides
    {
        std::lock_guard<std::mutex> lock(g_overrideMutex);
        g_overriddenFunctions.clear();
    }
    static const char* probeFunctions[] = {
        "glDrawArrays", "glDrawElements", "glDrawArraysInstanced",
        "glDrawElementsInstanced", "glDrawArraysIndirect", "glDrawElementsIndirect",
        "glMultiDrawArrays", "glMultiDrawElements", "glMultiDrawArraysIndirect",
        "glMultiDrawElementsIndirect",
        "glBindTexture", "glBindTextureUnit", "glBindSampler",
        "glActiveTexture",
        "glTexImage2D", "glTexImage3D", "glTexSubImage2D", "glTexSubImage3D",
        "glTexStorage2D", "glTexStorage3D", "glTexStorage2DMultisample",
        "glCompressedTexImage2D", "glCompressedTexImage3D",
        "glCopyTexImage2D", "glCopyTexSubImage2D",
        "glBindFramebuffer", "glBindRenderbuffer",
        "glFramebufferTexture2D", "glFramebufferTextureLayer",
        "glRenderbufferStorage", "glRenderbufferStorageMultisample",
        "glBlitFramebuffer",
        "glGenFramebuffers", "glGenRenderbuffers", "glGenTextures", "glGenBuffers",
        "glDeleteFramebuffers", "glDeleteRenderbuffers", "glDeleteTextures", "glDeleteBuffers",
        "glBindBuffer", "glBindBufferBase", "glBindBufferRange",
        "glBufferData", "glBufferSubData", "glBufferStorage",
        "glMapBuffer", "glMapBufferRange", "glUnmapBuffer",
        "glCreateShader", "glShaderSource", "glCompileShader", "glLinkProgram",
        "glAttachShader", "glDetachShader", "glDeleteShader", "glDeleteProgram",
        "glUseProgram", "glCreateProgram",
        "glEnable", "glDisable", "glEnablei", "glDisablei",
        "glClearColor", "glClear", "glClearBufferfi", "glClearBufferfv", "glClearBufferiv",
        "glViewport", "glScissor", "glScissorArrayv",
        "glDepthFunc", "glDepthMask", "glDepthRangef",
        "glStencilFunc", "glStencilOp", "glStencilMask",
        "glBlendFunc", "glBlendFuncSeparate", "glBlendEquation", "glBlendEquationSeparate",
        "glBlendColor",
        "glCullFace", "glFrontFace", "glPolygonOffset",
        "glPixelStorei",
        "glReadPixels", "glReadBuffer",
        "glDrawBuffers",
        "glUniform1i", "glUniform1f", "glUniform2f", "glUniform3f", "glUniform4f",
        "glUniformMatrix4fv", "glUniformMatrix3fv",
        "glUniformBlockBinding",
        "glVertexAttribPointer", "glVertexAttribIPointer",
        "glEnableVertexAttribArray", "glDisableVertexAttribArray",
        "glGenVertexArrays", "glBindVertexArray", "glDeleteVertexArrays",
        "glVertexAttribDivisor",
        "glFenceSync", "glClientWaitSync", "glDeleteSync", "glWaitSync",
        "glFlush", "glFinish",
        "glMemoryBarrier", "glTextureBarrier",
        "glBindImageTexture",
        "glDispatchCompute", "glDispatchComputeIndirect",
        "glGetString", "glGetStringi", "glGetIntegerv", "glGetFloatv", "glGetBooleanv",
        "eglGetProcAddress", "eglCreateContext", "eglMakeCurrent",
        "eglSwapBuffers", "eglChooseConfig", "eglGetConfigAttrib",
        "glfwCreateWindow", "glfwMakeContextCurrent", "glfwSwapBuffers",
        "glfwSwapInterval", "glfwWindowShouldClose", "glfwPollEvents",
        nullptr
    };

    int overrideCount = 0;
    for (int i = 0; probeFunctions[i] != nullptr; i++) {
        void* fn = g_pluginGetProc(probeFunctions[i]);
        if (fn) {
            std::lock_guard<std::mutex> lock(g_overrideMutex);
            g_overriddenFunctions.insert(probeFunctions[i]);
            overrideCount++;
        }
    }
    LOGI("[FearPlugin] Plugin overrides %d GL/EGL functions", overrideCount);

    return 0;
}

void fear_plugin_unload(void) {
    if (!g_pluginLoaded.load()) return;

    LOGI("[FearPlugin] Unloading renderer plugin: %s", g_pluginName.c_str());

    if (g_pluginShutdown) {
        g_pluginShutdown();
    }

    if (g_pluginHandle) {
        dlclose(g_pluginHandle);
        g_pluginHandle = nullptr;
    }

    g_pluginInit = nullptr;
    g_pluginShutdown = nullptr;
    g_pluginGetProc = nullptr;
    g_pluginGetName = nullptr;
    g_pluginGetVersion = nullptr;
    g_pluginName.clear();
    g_pluginVersion.clear();

    {
        std::lock_guard<std::mutex> lock(g_overrideMutex);
        g_overriddenFunctions.clear();
    }

    g_pluginLoaded.store(false);
    LOGI("[FearPlugin] Plugin unloaded");
}

int fear_plugin_is_loaded(void) {
    return g_pluginLoaded.load() ? 1 : 0;
}

const char* fear_plugin_get_name(void) {
    if (!g_pluginLoaded.load()) return nullptr;
    return g_pluginName.c_str();
}

const char* fear_plugin_get_version(void) {
    if (!g_pluginLoaded.load()) return nullptr;
    return g_pluginVersion.c_str();
}

void* fear_plugin_get_proc(const char* name) {
    if (!g_pluginLoaded.load() || !g_pluginGetProc || !name) return nullptr;
    void* fn = g_pluginGetProc(name);
    return fn;
}

int fear_plugin_get_override_count(void) {
    std::lock_guard<std::mutex> lock(g_overrideMutex);
    return (int)g_overriddenFunctions.size();
}
