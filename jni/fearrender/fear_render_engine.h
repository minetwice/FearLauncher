#ifndef FEAR_RENDER_ENGINE_H
#define FEAR_RENDER_ENGINE_H

#include <GLES3/gl32.h>
#include <string>

// Fear Render 3.0 "Shader Guarantee Engine" API
extern "C" {

void initFearRenderEngine(const char* cacheDir, int launcherVersion);
void destroyFearRenderEngine();
const char* getFearRenderVersion();
int getFearRenderStrategyLevel(const char* shaderHash);

std::string executeStrategyL1ToL8(
    const char* sourceCode,
    GLenum shaderType,
    int* winningLevel,
    bool* compilationSuccess
);

// GL Interception Wrappers for Fear Render 3.0
GLuint fear_glCreateShader(GLenum type);
void fear_glShaderSource(GLuint shader, GLsizei count, const GLchar* const* string, const GLint* length);
void fear_glCompileShader(GLuint shader);
void fear_glAttachShader(GLuint program, GLuint shader);
void fear_glDetachShader(GLuint program, GLuint shader);
void fear_glLinkProgram(GLuint program);
void fear_glDeleteShader(GLuint shader);
void fear_glDeleteProgram(GLuint program);

void fear_glTexImage2D(GLenum target, GLint level, GLint internalformat, GLsizei width, GLsizei height, GLint border, GLenum format, GLenum type, const void* pixels);
void fear_glTexImage3D(GLenum target, GLint level, GLint internalformat, GLsizei width, GLsizei height, GLsizei depth, GLint border, GLenum format, GLenum type, const void* pixels);
void fear_glRenderbufferStorage(GLenum target, GLenum internalformat, GLsizei width, GLsizei height);
void fear_glFramebufferTexture2D(GLenum target, GLenum attachment, GLenum textarget, GLuint texture, GLint level);

void* fear_eglGetProcAddress(const char* procname);

}

#endif // FEAR_RENDER_ENGINE_H
