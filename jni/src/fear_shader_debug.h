#ifndef FEAR_SHADER_DEBUG_H
#define FEAR_SHADER_DEBUG_H
#include <android/log.h>
// Diagnostics and verbose error loggers
#define FEAR_LOGI(...) __android_log_print(ANDROID_LOG_INFO, "FEAR_SHADER", __VA_ARGS__)
#endif
