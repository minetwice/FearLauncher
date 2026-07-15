#ifndef FEAR_HOOKS_H
#define FEAR_HOOKS_H

#include <EGL/egl.h>
#include <GLES3/gl32.h>

#ifdef __cplusplus
extern "C" {
#endif

// Initialize the hooks and load the underlying libltw.so wrapper
void init_fear_hooks();

// Dynamic interception function mapping
void* fear_glGetProcAddress(const char* procname);

// Spoofed glGetString and glGetStringi
const GLubyte* fear_glGetString(GLenum name);
const GLubyte* fear_glGetStringi(GLenum name, GLuint index);

#ifdef __cplusplus
}
#endif

#endif // FEAR_HOOKS_H
