#include "egl/egl.hpp"
#include "es/state_tracking.hpp"
#include "es/utils.hpp"
#include "gles20/ext/main.hpp"
#include "gles20/ext/ext_buffer_storage.hpp"
#include "main.hpp"

#include <GLES2/gl2ext.h>

inline PFNGLBUFFERSTORAGEEXTPROC glBufferStorageEXT = nullptr;

void GLES20Ext::register_EXT_buffer_storage() {
    if (!ESUtils::isExtensionSupported("GL_EXT_buffer_storage")) return;
    LOGI("EXT_buffer_storage is present and so used.");

    ESUtils::fakeExtensions.insert("GL_ARB_buffer_storage");

    glBufferStorageEXT = reinterpret_cast<PFNGLBUFFERSTORAGEEXTPROC>(GETFUNCEXT(glBufferStorage, "EXT"));
    REGISTER(glBufferStorage);
}

void glBufferStorage(GLenum target, GLsizeiptr size, const void* data, GLbitfield flags) {
    glBufferStorageEXT(target, size, data, flags);

    trackedStates->boundBuffers[target].size = size;
}