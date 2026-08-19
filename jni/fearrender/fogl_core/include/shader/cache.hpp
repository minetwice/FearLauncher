#pragma once

#include "utils/env.hpp"
#include "utils/log.hpp"
#include "utils/fast_map.hpp"

#include <filesystem>
#include <fstream>
#include <functional>
#include <sstream>
#include <string>
#include <sys/stat.h>
#include <unordered_map>
#include <utility>

inline bool ENABLE_SHADER_CACHE = getEnvironmentVar("FOGLE_ENABLE_SHADER_CACHE", "1") == "1";
inline const std::string SHADER_CACHE_DIRECTORY = getEnvironmentVar("MESA_GLSL_CACHE_DIR") + "/converted";
inline const size_t SHADER_RETENTION_DAYS = std::stoull(getEnvironmentVar("FOGLE_CACHE_RETENTION_DAY", "30"));

// key is hash, value is path
inline FAST_MAP_BI(size_t, std::string) shaderCache;

inline const time_t thresholdInSeconds = SHADER_RETENTION_DAYS * 24 * 60 * 60;

namespace ShaderConverter::Cache {
    inline bool isShaderInCache(size_t hashKey) {
        if (!ENABLE_SHADER_CACHE) return false;
        if (!hashKey) return false;
        return shaderCache.find(hashKey) != shaderCache.end();
    }

    inline void putShaderInCache(size_t key, std::string& source) {
        if (!ENABLE_SHADER_CACHE) return;
        if (isShaderInCache(key)) {
            LOGW("Key already in cache, skipping...");
            return;
        }

        std::string filePath = SHADER_CACHE_DIRECTORY + "/" + std::to_string(key);

        std::ofstream newFile(filePath, std::fstream::out | std::fstream::trunc);
        newFile << source << std::endl;

        newFile.flush();
        newFile.close();

        shaderCache.insert({ key, filePath });

        if (getEnvironmentVar("LIBGL_VGPU_DUMP") == "1") {
            LOGI("Shader with hash=%zu was saved to cache.", key);
        }
    }

    inline std::string getCachedShaderSource(size_t key) {
        if (!ENABLE_SHADER_CACHE) return "";
        if (!isShaderInCache(key)) return "";

        std::ifstream file(shaderCache.at(key));
        std::stringstream buffer;
        buffer << file.rdbuf();

        return buffer.str();
    }

    inline bool invalidateShaderCache(size_t key) {
        if (!ENABLE_SHADER_CACHE) return false;
        if (!isShaderInCache(key)) return false;

        std::string filename = shaderCache.at(key);
        shaderCache.erase(key);

        if (std::filesystem::exists(filename)) {
            std::filesystem::remove(filename);

            LOGI("Invalidated shader with key %zu", key);
            return true;
        }

        return false;
    }

    template<typename T>
    inline size_t getHash(T key) {
        if (!ENABLE_SHADER_CACHE) return 0;
        return std::hash<T>{}(key);
    }

    inline void init() {
        if (!ENABLE_SHADER_CACHE) {
            LOGI("Shader cache is disabled! Skipping initialization...");
            return;
        }

        size_t deletedOldCacheFiles = 0;
        LOGI("Indexing shader caches!");
        LOGI("Deleting cache files older than %zu days...", SHADER_RETENTION_DAYS);

        try {
            if (!std::filesystem::is_directory(SHADER_CACHE_DIRECTORY)) {
                LOGI("Cache path is not a directory! Deleting!");
                std::filesystem::remove(SHADER_CACHE_DIRECTORY);
            }

            if(!std::filesystem::exists(SHADER_CACHE_DIRECTORY)) {
                LOGI("Cache directory doesn't exist! Creating...");
                std::filesystem::create_directory(SHADER_CACHE_DIRECTORY);
            }

            for (const auto& entry : std::filesystem::directory_iterator { SHADER_CACHE_DIRECTORY }) {
                if (!std::filesystem::is_regular_file(entry.status())) continue;

                try {
                    std::pair<size_t, std::string> shaderCacheEntry = std::make_pair(
                        std::stoull(entry.path().filename().string()),
                        entry.path().string()
                    );

                    struct stat fileStat;
                    if (stat(shaderCacheEntry.second.c_str(), &fileStat) != 0) {
                        LOGW("Failed to stat() cache file! Assuming invalid and deleting!");
                        std::filesystem::remove(entry.path());

                        deletedOldCacheFiles++;
                        continue;
                    }

                    const time_t now = time(nullptr);
                    const double lastAccessTime = difftime(now, fileStat.st_atime);

                    if (lastAccessTime > thresholdInSeconds) {
                        if (std::filesystem::remove(entry.path())) {
                            deletedOldCacheFiles++;
                        } else {
                            LOGW("Failed to remove old shader cache file! Ignoring...");
                        }

                        continue;
                    }

                    shaderCache.insert({ shaderCacheEntry.first, shaderCacheEntry.second });
                } catch (const std::exception& e) {
                    LOGW("Error when indexing a cache file! Skipping...");
                    LOGW("%s", e.what());
                }
            }

            LOGI("Deleted %zu old cache files.", deletedOldCacheFiles);

            if (deletedOldCacheFiles > 0) {
                LOGI("Shader cache size (old) : %zu", shaderCache.size() + deletedOldCacheFiles);
                LOGI("Shader cache size (new) : %zu", shaderCache.size());
            } else {
                LOGI("Shader cache size : %zu", shaderCache.size());
            }
        } catch (const std::exception& e) {
            LOGW("Failed to initialize cache index!");
            LOGW("Reason: %s", e.what());

            LOGW("Shader cache will be disabled!");
            ENABLE_SHADER_CACHE = false;
        }
    }
}