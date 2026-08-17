#include "egl/egl.hpp"
#include "es/utils.hpp"
#include "gles20/ext/main.hpp"
#include "main.hpp"

void GLES20Ext::register_EXT_blend_func_extended() {
    if (!ESUtils::isExtensionSupported("GL_EXT_blend_func_extended")) return;
    LOGI("EXT_blend_func_extended is present and so used.");

    ESUtils::fakeExtensions.insert("ARB_blend_func_extended");

    REGISTEREXT(glBindFragDataLocation, "EXT");
    REGISTEREXT(glBindFragDataLocationIndexed, "EXT");
}