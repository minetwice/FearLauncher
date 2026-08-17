#include "es/ffpe/lists.hpp"
#include "es/ffpe/states.hpp"
#include "es/state_tracking.hpp"
#include "gles/ffp/enums.hpp"
#include "gles/ffp/main.hpp"
#include "main.hpp"

#include <GLES3/gl32.h>

void glAlphaFunc(GLenum op, GLclampf threshold);

void FFP::registerAlphaTestFunctions() {
    REGISTER(glAlphaFunc);
}

void glAlphaFunc(GLenum op, GLclampf threshold) {
    if (!trackedStates->isCapabilityEnabled(GL_ALPHA_TEST)) return;
    if (FFPE::List::addCommand<glAlphaFunc>(op, threshold)) return;

    FFPE::States::AlphaTest::op = op;
    FFPE::States::AlphaTest::threshold = threshold;

    FFPE::States::Manager::invalidateCurrentState();
}
