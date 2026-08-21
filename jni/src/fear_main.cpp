#include "fear_render.h"

bool g_fear_initialized = false;
bool g_fake_depth_fbo_ready = false;
bool g_is_mali_gpu = true; // Default for Motorola Edge 60 Fusion (Mali-G615 MC2)

void fear_init_deferred_if_needed(void) {
    if (g_fear_initialized) return;

    if (eglGetCurrentContext() == EGL_NO_CONTEXT) {
        return; // Must defer until context exists
    }

    g_fear_initialized = true;

    LOGI("[FearRender] backend=GLES core=FOGLTLOGLES+guards");

    // Detect GPU vendor
    typedef const GLubyte* (*pfn_glGetString)(GLenum);
    pfn_glGetString real_glGetString = (pfn_glGetString) dlsym(RTLD_DEFAULT, "glGetString");
    if (real_glGetString) {
        const char* renderer = (const char*) real_glGetString(GL_RENDERER);
        if (renderer && strstr(renderer, "Adreno")) {
            g_is_mali_gpu = false;
        }
    }

    // Initialize FakeDepthFramebuffer
    typedef void (*pfn_glGenFramebuffers)(GLsizei, GLuint*);
    pfn_glGenFramebuffers real_glGenFramebuffers = (pfn_glGenFramebuffers) dlsym(RTLD_DEFAULT, "glGenFramebuffers");

    if (real_glGenFramebuffers) {
        GLuint fakeFbo = 0;
        real_glGenFramebuffers(1, &fakeFbo);
        g_fake_depth_fbo_ready = true;
        LOGI("[FearRender] FakeDepthFramebuffer ready=true");
    }
}
