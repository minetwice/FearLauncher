#include "fear_render_engine.h"
#include "fear_shader_logger.h"
#include "fear_shader_translator.h"
#include "fear_shader_cache.h"
#include <string>
#include <unordered_map>
#include <mutex>
#include <algorithm>
#include <cctype>

static std::mutex g_strategyMutex;
static std::unordered_map<std::string, int> g_shaderWinningLevels;

static void replaceAllLocal(std::string& s, const std::string& from, const std::string& to) {
    if (from.empty()) return;
    size_t pos = 0;
    while ((pos = s.find(from, pos)) != std::string::npos) {
        s.replace(pos, from.length(), to);
        pos += to.length();
    }
}

static std::string stripHeavyFeatures(const std::string& src) {
    std::string out = src;
    const char* disables[] = {
        "SSR_ENABLED", "VOLUMETRIC_CLOUDS", "VOLUMETRIC_FOG", "MOTION_BLUR",
        "TAA_ENABLED", "RAYTRACED_AO", "PATH_TRACING", "SSGI_ENABLED",
        "REFRACTION_ENABLED", "CAUSTICS_ENABLED", "BLOOM_ENABLED"
    };
    for (const char* d : disables) {
        std::string def = std::string("#define ") + d;
        replaceAllLocal(out, def + " 1", def + " 0");
        replaceAllLocal(out, def, std::string("#define ") + d + " 0 // fear-stripped");
    }
    return out;
}

static std::string forceEs300(const std::string& src) {
    std::string out = src;
    replaceAllLocal(out, "#version 460 core", "#version 300 es");
    replaceAllLocal(out, "#version 450 core", "#version 300 es");
    replaceAllLocal(out, "#version 440 core", "#version 300 es");
    replaceAllLocal(out, "#version 430 core", "#version 300 es");
    replaceAllLocal(out, "#version 420 core", "#version 300 es");
    replaceAllLocal(out, "#version 410 core", "#version 300 es");
    replaceAllLocal(out, "#version 400 core", "#version 300 es");
    replaceAllLocal(out, "#version 330 core", "#version 300 es");
    replaceAllLocal(out, "#version 150", "#version 300 es");
    replaceAllLocal(out, "#version 140", "#version 300 es");
    replaceAllLocal(out, "#version 130", "#version 300 es");
    replaceAllLocal(out, "#version 120", "#version 300 es");
    replaceAllLocal(out, "noperspective ", "smooth ");
    replaceAllLocal(out, "noperspective\t", "smooth ");

    if (out.find("#version") == std::string::npos) {
        out = "#version 300 es\n" + out;
    }
    if (out.find("precision ") == std::string::npos) {
        size_t pos = out.find('\n');
        if (pos != std::string::npos) {
            out.insert(pos + 1, "precision mediump float;\nprecision mediump int;\n");
        }
    }
    return out;
}

std::string executeStrategyL1ToL8(
    const char* sourceCode,
    GLenum shaderType,
    int* winningLevel,
    bool* compilationSuccess
) {
    if (!sourceCode) {
        if (winningLevel) *winningLevel = 8;
        if (compilationSuccess) *compilationSuccess = false;
        return "";
    }

    std::string original(sourceCode);
    std::string trans;
    bool success = false;

    trans = FearTranslateGLSL(sourceCode, shaderType, &success);
    if (success && !trans.empty()) {
        if (winningLevel) *winningLevel = 1;
        if (compilationSuccess) *compilationSuccess = true;
        LOG_INFO("[FearRender] shader via L1 full translator");
        return trans;
    }

    trans = forceEs300(original);
    if (!trans.empty()) {
        if (winningLevel) *winningLevel = 4;
        if (compilationSuccess) *compilationSuccess = true;
        LOG_INFO("[FearRender] shader via L4 ES300 normalize");
        return trans;
    }

    trans = "#version 300 es\nprecision mediump float;\nprecision mediump int;\n" + original;
    if (winningLevel) *winningLevel = 5;
    if (compilationSuccess) *compilationSuccess = true;
    LOG_INFO("[FearRender] shader via L5 minimal");
    return trans;
}

std::string executeStrategyFeatureStrip(const char* sourceCode) {
    if (!sourceCode) return "";
    return forceEs300(stripHeavyFeatures(sourceCode));
}

extern "C" {

int getFearRenderStrategyLevel(const char* shaderHash) {
    std::lock_guard<std::mutex> lock(g_strategyMutex);
    if (!shaderHash) return 1;
    auto it = g_shaderWinningLevels.find(shaderHash);
    if (it != g_shaderWinningLevels.end()) {
        return it->second;
    }
    return 1;
}

void fear_setShaderWinningLevel(const char* shaderHash, int level) {
    if (!shaderHash) return;
    std::lock_guard<std::mutex> lock(g_strategyMutex);
    g_shaderWinningLevels[shaderHash] = level;
}

}
