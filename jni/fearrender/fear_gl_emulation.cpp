#include "fear_gl_emulation.h"
#include "es/ffpe/matrices.hpp"
#include "es/ffpe/immediate.hpp"

void initGLFixedFunctionEmulation() {
    // FFPE initialization handled during FOGLTLOGLES::init()
}

void pushMatrix(int mode) {
    FFPE::Rendering::Matrices::pushMatrix(mode);
}

void popMatrix(int mode) {
    FFPE::Rendering::Matrices::popMatrix(mode);
}

void beginImmediateMode(GLenum mode) {
    FFPE::Rendering::ImmediateMode::begin(mode);
}

void endImmediateMode() {
    FFPE::Rendering::ImmediateMode::end();
}
