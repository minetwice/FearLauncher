#include "es/utils.hpp"
#include "gles20/ext/main.hpp"
#include "utils/log.hpp"

#include <GLES3/gl32.h>

void GLES20Ext::register_ARB_timer_query() {
    LOGI("ES 3 is present and so GL_ARB_timer_query exposed.");
    ESUtils::fakeExtensions.insert("GL_ARB_timer_query");
}