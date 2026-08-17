#include "es/utils.hpp"
#include "gles20/ext/main.hpp"
#include "utils/log.hpp"

void GLES20Ext::register_EXT_framebuffer_object() {
    LOGI("ES 3 is present and so GL_EXT_framebuffer_object exposed.");

    ESUtils::fakeExtensions.insert("GL_EXT_framebuffer_object");
}
