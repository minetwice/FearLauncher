#include "es/ffpe/lists.hpp"
#include "es/ffpe/states.hpp"
#include "es/ffpe/immediate.hpp"
#include "gles/ffp/main.hpp"
#include "glm/ext/vector_float2.hpp"
#include "main.hpp"

#include <GLES3/gl32.h>

void glTexCoord2f(GLfloat s, GLfloat t);

void FFP::registerTexCoordFunctions() {
    REGISTER(glTexCoord2f);
}

void glTexCoord2f(GLfloat s, GLfloat t) {
    if (!FFPE::Rendering::ImmediateMode::isActive()) {
        FFPE::List::addCommand<glTexCoord2f>(s, t);
        return;
    }

    FFPE::States::VertexData::set(
        glm::vec2(s, t),
        FFPE::States::VertexData::texCoord
    );
}
