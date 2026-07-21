#ifndef FEAR_SHADER_CACHE_H
#define FEAR_SHADER_CACHE_H
#include <string>
// Manages binary precompiled shader caches
class ShaderCache {
public:
    static bool hasCache(std::string hash) {
        return false;
    }
};
#endif
