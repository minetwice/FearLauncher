#include "es/utils.hpp"
#include "gles20/ext/main.hpp"
#include "utils/log.hpp"

void GLES20Ext::register_ARB_compute_shader() {
    LOGI("Not exposing GL_ARB_compute_shader.");

    // ESUtils::fakeExtensions.insert("GL_ARB_compute_shader");
}
