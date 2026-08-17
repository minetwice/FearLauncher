#include "gles20/ext/main.hpp"
#include "utils/log.hpp"

void GLES20Ext::GLES20ExtWrapper::init() {
    LOGI("GLES 2.0 extension entrypoint!");

    GLES20Ext::register_EXT_buffer_storage();
    GLES20Ext::register_OES_mapbuffer();
    GLES20Ext::register_EXT_blend_func_extended();
    GLES20Ext::register_ARB_compute_shader();
    GLES20Ext::register_ARB_timer_query();
    GLES20Ext::register_EXT_framebuffer_object();
}