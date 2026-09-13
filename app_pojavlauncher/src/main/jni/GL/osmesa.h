/*
 * Minimal OSMesa interface header for FearLauncher.
 * Based on Mesa's include/GL/osmesa.h (MIT licensed).
 *
 * The Android NDK does not ship GL/gl.h, so we define the minimal
 * set of GL types and constants that the OSMesa bridge needs.
 */
#ifndef FEARLAUNCHER_OSMESA_H
#define FEARLAUNCHER_OSMESA_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* --- Minimal GL type definitions (normally in GL/gl.h) --- */
typedef unsigned int GLenum;
typedef unsigned char GLboolean;
typedef unsigned int GLbitfield;
typedef signed char GLbyte;
typedef short GLshort;
typedef int GLint;
typedef int GLsizei;
typedef unsigned char GLubyte;
typedef unsigned short GLushort;
typedef unsigned int GLuint;
typedef float GLfloat;
typedef float GLclampf;
typedef double GLdouble;
typedef double GLclampd;
typedef void GLvoid;

/* GL types used by OSMesa function signatures */
#define GL_UNSIGNED_BYTE 0x1401
#define GL_RGBA 0x1908

/* --- OSMesa types and constants --- */
typedef struct osmesa_context *OSMesaContext;

#define OSMESA_RGBA 0x1908
#define OSMESA_BGRA 0x1
#define OSMESA_ARGB 0x2
#define OSMESA_RGB  0x1907
#define OSMESA_BGR  0x4
#define OSMESA_RGBA_FLOAT 0x8110

#define OSMESA_ROW_LENGTH 0x10
#define OSMESA_Y_UP 0x11

#define OSMESA_MAX_WIDTH  0x20
#define OSMESA_MAX_HEIGHT 0x21

#ifdef __cplusplus
}
#endif

#endif /* FEARLAUNCHER_OSMESA_H */
