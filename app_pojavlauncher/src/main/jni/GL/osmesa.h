/*
 * Minimal OSMesa interface header for FearLauncher.
 * Based on Mesa's include/GL/osmesa.h (MIT licensed).
 * Only the types and constants used by the ctxbridges are defined here.
 */
#ifndef FEARLAUNCHER_OSMESA_H
#define FEARLAUNCHER_OSMESA_H

#include <GL/gl.h>

#ifdef __cplusplus
extern "C" {
#endif

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
