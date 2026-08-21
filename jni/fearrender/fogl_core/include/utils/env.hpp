#pragma once

#include <string>

inline std::string getEnvironmentVar(std::string const& key, std::string const& fallback = "") {
    char* val = getenv( key.c_str() );
    return val == NULL ? fallback : std::string(val);
}
