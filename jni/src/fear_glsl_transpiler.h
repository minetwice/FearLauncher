#ifndef FEAR_GLSL_TRANSPILER_H
#define FEAR_GLSL_TRANSPILER_H

#include <string>
#include <GLES3/gl32.h>

std::string FearTranspileGLSL(
    const char* src,
    GLenum type,
    int esVersion,
    bool* ok
);

#endif // FEAR_GLSL_TRANSPILER_H
