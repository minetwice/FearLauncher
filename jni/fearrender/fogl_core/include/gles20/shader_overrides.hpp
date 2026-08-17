#pragma once

#include <GLES2/gl2.h>


void OV_glShaderSource(GLuint shader, GLsizei count, const GLchar* const* string, const GLint* length);
void OV_glCompileShader(GLuint shader);
void OV_glLinkProgram(GLuint program);
void OV_glUseProgram(GLuint program);
void OV_glDeleteProgram(GLuint program);
