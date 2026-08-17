#pragma once

#include "utils/types.hpp"
#include "utils/defines.hpp"

#include <GLES3/gl32.h>

PUBLIC_API FunctionPtr glXGetProcAddress(const GLchar* pn);

void initDebug();
