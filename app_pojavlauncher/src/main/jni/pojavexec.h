//
// Created by maks on 08.05.2026.
//

#ifndef POJAVLAUNCHER_POJAVEXEC_H
#define POJAVLAUNCHER_POJAVEXEC_H

typedef void* (*acquire_egl_handle_t)(const char*);

typedef struct {
    acquire_egl_handle_t egl_acquire;
    const char* egl_path;
    int force_gles_context;
    int override_major_version;
    /* FEARWIRE-DISPSPEC: the runtime's prebuilt libglfw.so continues its
       mojoexec_renderspec ABI here (disp_width/height/hz) for the fake
       monitor video mode; without these fields it read past the struct. */
    int disp_width;
    int disp_height;
    float disp_hz;
} pojavexec_renderspec_t;

void pojavexec_setDisplayParams(int width, int height, float hz);
void* pojavexec_loadVulkanDriver();
const pojavexec_renderspec_t* pojavexec_getRenderSpec();

#endif //POJAVLAUNCHER_POJAVEXEC_H
