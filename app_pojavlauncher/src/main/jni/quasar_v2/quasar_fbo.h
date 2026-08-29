/*
 * QuasarV2 - FBO Manager Header
 */

#ifndef QUASAR_FBO_H
#define QUASAR_FBO_H

#include <GLES3/gl32.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    GLuint main_fbo;
    GLuint main_color_tex;
    GLuint main_depth_rbo;
    GLsizei width;
    GLsizei height;
    int initialized;
} quasar_fbo_manager_t;

void quasar_fbo_init(quasar_fbo_manager_t *mgr, GLsizei w, GLsizei h);
void quasar_fbo_resize(quasar_fbo_manager_t *mgr, GLsizei w, GLsizei h);
void quasar_fbo_bind_main(quasar_fbo_manager_t *mgr);
void quasar_fbo_blit_to_screen(quasar_fbo_manager_t *mgr);
void quasar_fbo_cleanup(quasar_fbo_manager_t *mgr);
quasar_fbo_manager_t* quasar_fbo_get_instance(void);

#ifdef __cplusplus
}
#endif

#endif /* QUASAR_FBO_H */
