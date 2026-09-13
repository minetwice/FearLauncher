//
// Ported from ZalithLauncher (ctxbridges/common.h)
// Basic render window types shared by all bridges.
//

#ifndef POJAVLAUNCHER_COMMON_H
#define POJAVLAUNCHER_COMMON_H

#define STATE_RENDERER_ALIVE 0
#define STATE_RENDERER_NEW_WINDOW 1

typedef struct {
    char       state;
    struct ANativeWindow *nativeSurface;
    struct ANativeWindow *newNativeSurface;
} basic_render_window_t;

#endif //POJAVLAUNCHER_COMMON_H
