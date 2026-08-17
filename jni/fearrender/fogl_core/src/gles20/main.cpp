#include "gles20/main.hpp"
#include "utils/log.hpp"

void GLES20::GLES20Wrapper::init() {
    LOGI("GLES 2.0 entrypoint!");

    GLES20::registerTrackingFunctions();
    GLES20::registerShaderOverrides();
    GLES20::registerTextureOverrides();
    GLES20::registerMultiDrawEmulation();
    GLES20::registerRenderbufferWorkaround();
}