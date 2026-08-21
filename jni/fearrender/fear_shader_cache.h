#ifndef FEAR_SHADER_CACHE_H
#define FEAR_SHADER_CACHE_H

#include <string>
#include <GLES3/gl32.h>

void initShaderCacheSystem(const std::string& cacheDir, int launcherVersion);
std::string getShaderSourceHash(const std::string& source);
bool loadProgramBinaryFromCache(GLuint program, const std::string& hash, bool isGLES);
void saveProgramBinaryToCache(GLuint program, const std::string& hash, bool isGLES);

#endif // FEAR_SHADER_CACHE_H
