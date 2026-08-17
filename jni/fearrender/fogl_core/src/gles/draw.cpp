#include "es/ffpe/draw.hpp"
#include "es/ffpe/lists.hpp"
#include "gles/draw_overrides.hpp"
#include "gles/main.hpp"
#include "main.hpp"
#include "utils/log.hpp"

#include <GLES3/gl32.h>

void GLES::registerDrawOverride() {
    REGISTEROV(glDrawArrays);
}

void OV_glDrawArrays(GLenum mode, GLint first, GLsizei count) {
    if (count <= 0) return;
    if (FFPE::List::addCommand<OV_glDrawArrays>(mode, first, count)) return;
    if (debugEnabled) LOGI("OV_glDrawArrays : mode=%u count=%i", mode, count);

    GLDebugGroup gldg("Draw Arrays");
    switch (mode) {
        case GL_QUADS:
            FFPE::Rendering::Arrays::Quads::drawQuads(count);
            break;

        default:
            FFPE::Rendering::Arrays::drawArrays(mode, first, count);
    }
}
