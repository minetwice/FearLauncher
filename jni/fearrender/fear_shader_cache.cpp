#include "fear_shader_cache.h"
#include "fear_shader_logger.h"
#include <fstream>
#include <sstream>
#include <functional>
#include <sys/stat.h>
#include <cstring>
#include <vector>

static std::string g_cacheDir = "";
static int g_launcherVersion = 1;

void initShaderCacheSystem(const std::string& cacheDir, int launcherVersion) {
    g_cacheDir = cacheDir;
    g_launcherVersion = launcherVersion;

    std::string shaderCacheDir = cacheDir + "/fear_shaders";
    mkdir(shaderCacheDir.c_str(), 0777);
}

std::string getShaderSourceHash(const std::string& source) {
    std::hash<std::string> hasher;
    size_t h1 = hasher(source);
    std::hash<size_t> hasher2;
    size_t h2 = hasher2(h1);

    std::stringstream ss;
    ss << std::hex << h1 << h2;
    return ss.str();
}

bool loadProgramBinaryFromCache(GLuint program, const std::string& hash, bool isGLES) {
    if (g_cacheDir.empty()) return false;

    std::string filepath = g_cacheDir + "/fear_shaders/" + hash + ".bin";
    std::ifstream file(filepath, std::ios::binary);
    if (!file.is_open()) return false;

    GLenum binaryFormat = 0;
    GLsizei binaryLength = 0;
    file.read(reinterpret_cast<char*>(&binaryFormat), sizeof(GLenum));
    file.read(reinterpret_cast<char*>(&binaryLength), sizeof(GLsizei));

    if (binaryLength <= 0 || binaryLength > 64 * 1024 * 1024) {
        return false;
    }

    std::vector<char> binaryData(binaryLength);
    file.read(binaryData.data(), binaryLength);
    file.close();

    glProgramBinary(program, binaryFormat, binaryData.data(), binaryLength);

    GLint linkStatus = GL_FALSE;
    glGetProgramiv(program, GL_LINK_STATUS, &linkStatus);

    if (linkStatus == GL_TRUE) {
        LOG_INFO("[FearRender] Program binary loaded from cache: %s", hash.c_str());
        return true;
    }

    return false;
}

void saveProgramBinaryToCache(GLuint program, const std::string& hash, bool isGLES) {
    if (g_cacheDir.empty()) return;

    GLint binaryLength = 0;
    glGetProgramiv(program, GL_PROGRAM_BINARY_LENGTH, &binaryLength);
    if (binaryLength <= 0) return;

    std::vector<char> binaryData(binaryLength);
    GLenum binaryFormat = 0;
    glGetProgramBinary(program, binaryLength, nullptr, &binaryFormat, binaryData.data());

    std::string filepath = g_cacheDir + "/fear_shaders/" + hash + ".bin";
    std::ofstream file(filepath, std::ios::binary);
    if (!file.is_open()) return;

    file.write(reinterpret_cast<const char*>(&binaryFormat), sizeof(GLenum));
    file.write(reinterpret_cast<const char*>(&binaryLength), sizeof(GLsizei));
    file.write(binaryData.data(), binaryLength);
    file.close();

    LOG_INFO("[FearRender] Program binary saved to cache: %s", hash.c_str());
}
