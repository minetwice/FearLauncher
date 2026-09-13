//
// Simplified bridge environment for FearLauncher.
// Ported from ZalithLauncher environ/ — only the fields the OSMesa bridge needs.
// FearLauncher's input/event system lives in dnbglfw, NOT here.
//

#ifndef FEARLAUNCHER_BRIDGE_ENVIRON_H
#define FEARLAUNCHER_BRIDGE_ENVIRON_H

#include <stdbool.h>
#include <ctxbridges/common.h>

#define RENDERER_GL4ES 1
#define RENDERER_VK_ZINK 2

typedef struct {
    struct ANativeWindow* pojavWindow;   // set via setupBridgeWindow(Surface)
    basic_render_window_t* mainWindowBundle;
    int config_renderer;                 // RENDERER_GL4ES or RENDERER_VK_ZINK
    bool force_vsync;
    int savedWidth, savedHeight;
} bridge_environ_t;

extern bridge_environ_t bridge_environ;

#endif //FEARLAUNCHER_BRIDGE_ENVIRON_H
