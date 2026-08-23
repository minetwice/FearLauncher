#ifndef FEAR_SHADER_LOGGER_H
#define FEAR_SHADER_LOGGER_H

#include "fogl_core/include/utils/log.hpp"

#ifndef LOG_INFO
#define LOG_INFO(...) do { LOGI(__VA_ARGS__); } while(0)
#endif

#ifndef LOG_WARNING
#define LOG_WARNING(...) do { LOGW(__VA_ARGS__); } while(0)
#endif

#ifndef LOG_ERROR
#define LOG_ERROR(...) do { LOGE(__VA_ARGS__); } while(0)
#endif

#endif // FEAR_SHADER_LOGGER_H
