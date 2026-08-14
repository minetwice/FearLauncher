#ifndef FEAR_SHADER_CACHE_H
#define FEAR_SHADER_CACHE_H

#include <string>
#include <GLES3/gl32.h>

// Initialize cache system (creates directory, handles cache invalidation on version update)
void initShaderCacheSystem(const std::string& cacheDir, int launcherVersion);

// Generate SHA-256 hash from source string
std::string getShaderSourceHash(const std::string& source);

// Load precompiled program binary if cached
bool loadProgramBinaryFromCache(GLuint program, const std::string& programHash);

// Save linked program binary to cache
void saveProgramBinaryToCache(GLuint program, const std::string& programHash);

// Clear the cache directory
void clearShaderCacheDir();

#endif // FEAR_SHADER_CACHE_H
