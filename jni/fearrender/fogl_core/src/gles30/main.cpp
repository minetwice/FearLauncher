#include "gles30/main.hpp"
#include "utils/log.hpp"

void GLES30::GLES30Wrapper::init() {
    LOGI("GLES 3.0 entrypoint!");

    GLES30::registerTranslatedFunctions();
    GLES30::registerTextureOverrides();
    GLES30::registerMultiDrawEmulation();
    GLES30::registerVertexAttribFunctions();
}