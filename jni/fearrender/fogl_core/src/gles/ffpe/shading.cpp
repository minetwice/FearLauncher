#include "es/ffpe/lists.hpp"
#include "es/ffpe/states.hpp"
#include "gles/ffp/enums.hpp"
#include "gles/ffp/main.hpp"
#include "main.hpp"

#include <GLES3/gl32.h>

void glShadeModel(GLenum mode);

void FFP::registerShadingFunction() {
    REGISTER(glShadeModel);
}

void glShadeModel(GLenum mode) {
    switch (mode) {
        case GL_FLAT:
        case GL_SMOOTH:
            break;
        default: return;
    }

    if (FFPE::List::addCommand<glShadeModel>(mode)) return;

    FFPE::States::ShadeModel::type = mode;
    FFPE::States::Manager::invalidateCurrentState();
}
