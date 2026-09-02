/*
 * QuasarV2 - Shader & Binary Cache Header
 */

#ifndef QUASAR_CACHE_H
#define QUASAR_CACHE_H

#include <GLES3/gl32.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    uint32_t hash;
    GLuint program;
} quasar_cache_entry_t;

void quasar_cache_init(const char* cache_dir);
GLuint quasar_cache_get_program(uint32_t hash);
void quasar_cache_put_program(uint32_t hash, GLuint program);
void quasar_cache_flush(void);

#ifdef __cplusplus
}
#endif

#endif /* QUASAR_CACHE_H */
