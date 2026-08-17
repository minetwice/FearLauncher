#include "gles32/main.hpp"
#include "utils/log.hpp"

void GLES32::GLES32Wrapper::init() {
    LOGI("GLES 3.2 entrypoint!");

    GLES32::registerBaseVertexFunction();
    GLES32::registerFramebufferOverrides();
    GLES32::registerTextureOverrides();
}