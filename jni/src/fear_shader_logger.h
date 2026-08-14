#ifndef FEAR_SHADER_LOGGER_H
#define FEAR_SHADER_LOGGER_H

#include <android/log.h>
#include <stdio.h>
#include <stdlib.h>
#include <string>
#include <mutex>
#include <unistd.h>
#include <sys/stat.h>
#include <stdarg.h>

#define TAG "FearEngine"

enum FearLogLevel {
    LOG_LEVEL_DEBUG,
    LOG_LEVEL_INFO,
    LOG_LEVEL_WARNING,
    LOG_LEVEL_ERROR
};

inline std::string get_log_file_path() {
    const char* tmp = getenv("TMPDIR");
    if (!tmp) {
        return "/data/data/git.artdeell.mojo/files/fear_engine.log";
    }
    std::string tmp_str(tmp);
    size_t last_slash = tmp_str.find_last_of('/');
    if (last_slash != std::string::npos) {
        std::string base = tmp_str.substr(0, last_slash);
        return base + "/files/fear_engine.log";
    }
    return tmp_str + "/../files/fear_engine.log";
}

inline void FearLog(FearLogLevel level, const char* format, ...) {
    va_list args;
    va_start(args, format);

    // Format message
    char buf[4096];
    vsnprintf(buf, sizeof(buf), format, args);
    va_end(args);

    int android_priority = ANDROID_LOG_INFO;
    const char* level_str = "INFO";
    switch (level) {
        case LOG_LEVEL_DEBUG:
            android_priority = ANDROID_LOG_DEBUG;
            level_str = "DEBUG";
            break;
        case LOG_LEVEL_INFO:
            android_priority = ANDROID_LOG_INFO;
            level_str = "INFO";
            break;
        case LOG_LEVEL_WARNING:
            android_priority = ANDROID_LOG_WARN;
            level_str = "WARNING";
            break;
        case LOG_LEVEL_ERROR:
            android_priority = ANDROID_LOG_ERROR;
            level_str = "ERROR";
            break;
    }

    // 1. Android Logcat
    __android_log_print(android_priority, TAG, "[%s] %s", level_str, buf);

    // 2. File logger with rotation (max 5MB)
    static std::mutex log_file_mutex;
    std::lock_guard<std::mutex> lock(log_file_mutex);

    std::string path = get_log_file_path();
    struct stat st;
    if (stat(path.c_str(), &st) == 0) {
        if (st.st_size > 5 * 1024 * 1024) { // 5MB limit
            // Rotate log
            std::string old_path = path + ".old";
            rename(path.c_str(), old_path.c_str());
        }
    }

    FILE* f = fopen(path.c_str(), "a");
    if (f) {
        fprintf(f, "[FearEngine][%s] %s\n", level_str, buf);
        fclose(f);
    }
}

#define LOG_ERROR(fmt, ...) FearLog(LOG_LEVEL_ERROR, fmt, ##__VA_ARGS__)
#define LOG_WARNING(fmt, ...) FearLog(LOG_LEVEL_WARNING, fmt, ##__VA_ARGS__)
#define LOG_INFO(fmt, ...) FearLog(LOG_LEVEL_INFO, fmt, ##__VA_ARGS__)
#define LOG_DEBUG(fmt, ...) FearLog(LOG_LEVEL_DEBUG, fmt, ##__VA_ARGS__)

#endif // FEAR_SHADER_LOGGER_H
