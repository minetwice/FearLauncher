#ifndef FEAR_GL_EMULATION_H
#define FEAR_GL_EMULATION_H

#include <GLES3/gl32.h>

void initGLFixedFunctionEmulation();
void pushMatrix(int mode);
void popMatrix(int mode);
void beginImmediateMode(GLenum mode);
void endImmediateMode();

#endif // FEAR_GL_EMULATION_H
