#include "gles/main.hpp"
#include "utils/log.hpp"

void GLES::GLESWrapper::init() {
    LOGI("GLES 1.0 entrypoint!");

    GLES::registerTranslatedFunctions();
    GLES::registerBrandingOverride();
    GLES::registerNoErrorOverride();
    GLES::registerDrawOverride();
}
