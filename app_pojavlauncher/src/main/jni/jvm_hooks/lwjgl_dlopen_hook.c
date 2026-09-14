//
// FearLauncher — LWJGL dlopen/dlsym hook v2.12 (TURNIP-ZINK + PANVK_ZINK)
// Hybrid: real libglfw for window/input/pollEvents; renderer via renderspec / OSMesa bridge.
// Restored FINAL-V9 hook implementation (from turbov1-zink-fixed) merged with the
// v10 bridge_environ / zink-detection additions.
// Critical: do NOT force EGL_PLATFORM=android — Java side intentionally skips it for Zink.
// Prefer real GL/ES context so LWJGL createCapabilities works.
//

#include "jvm_hooks.h"

#include <android/api-level.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <dlfcn.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <pthread.h>
#include <stdbool.h>
#include <stdint.h>

#define TAG __FILE_NAME__
#include <log.h>
#include "../pojavexec.h"
#include "ctxbridges/bridge_environ.h"
#include "ctxbridges/osm_bridge.h"
#include "ctxbridges/osmesa_loader.h"

// OSMesaGetProcAddress — official way to resolve GL function pointers from OSMesa/Zink.
// Defined in osmesa_loader.c but not declared in the header.
extern void* (*OSMesaGetProcAddress_p)(const char* funcName);

bridge_environ_t bridge_environ = {0};

static int g_is_zink_cached = -1;

static bool is_zink_renderer() {
    if (g_is_zink_cached >= 0) return g_is_zink_cached == 1;
    const char* fear = getenv("FEAR_RENDERER");
    const char* gallium = getenv("GALLIUM_DRIVER");
    const char* renderer = getenv("POJAV_RENDERER");
    bool z = false;
    if (fear && (strcmp(fear, "turnip_zink") == 0 || strcmp(fear, "panvk_zink") == 0 || strcmp(fear, "vulkan_zink") == 0))
        z = true;
    else if (gallium && strcmp(gallium, "zink") == 0)
        z = true;
    else if (renderer && (strcmp(renderer, "turnip_zink") == 0 || strcmp(renderer, "panvk_zink") == 0 || strcmp(renderer, "vulkan_zink") == 0))
        z = true;
    g_is_zink_cached = z ? 1 : 0;
    return z;
}

static volatile int g_glfw_initialized = 0;
static volatile int g_window_created = 0;
static volatile int g_has_gl_context = 0; // 1 if window has real GL/ES context
static volatile int g_context_current = 0;
static void* g_current_window = NULL;

static int  (*real_glfwInit)(void) = NULL;
static int  (*real_glfwGetError)(const char**) = NULL;
static void (*real_glfwInitHint)(int, int) = NULL;
static void (*real_glfwWindowHint)(int, int) = NULL;
static void* (*real_glfwCreateWindow)(int, int, const char*, void*, void*) = NULL;
static void (*real_glfwDefaultWindowHints)(void) = NULL;
static void (*real_glfwMakeContextCurrent)(void*) = NULL;
static void (*real_glfwSwapBuffers)(void*) = NULL;
static void (*real_glfwSwapInterval)(int) = NULL;
static void* (*real_glfwGetCurrentContext)(void) = NULL;

static void universal_stub_void(void) {}
static int eglGetError_always_success(void) { return 0x3000; }

// Fake GLX context — LWJGL's GL.createCapabilities() calls glXGetCurrentContext()
// and crashes if it returns NULL. In NO_API mode (Zink path) there is no real
// GLX/EGL context, so we return a non-NULL fake pointer after MakeContextCurrent.
static void* fake_gl_context = (void*)1;

static void* glXGetCurrentContext_fake(void) {
    return fake_gl_context;
}

// ---- OSMesa context management for NO_API Zink mode ----
// LWJGL 3.3.3 GL.createCapabilities() actually CALLS glGetError/glGetString/
// glGetIntegerv to detect the context. Without a current OSMesaContext these
// return NULL/garbage and LWJGL throws "There is no OpenGL context current".
// So we must OSMesaCreateContext + OSMesaMakeCurrent before LWJGL probes GL.
static OSMesaContext g_osmesa_ctx = NULL;
static void* g_osmesa_buffer = NULL;
static int g_osmesa_w = 0;
static int g_osmesa_h = 0;
static int g_window_width = 1280;
static int g_window_height = 720;

// Mesa Zink init (Vulkan instance/device setup, driconf parsing) is extremely
// stack-hungry. Java render threads have small stacks (1-2MB default) → stack
// overflow → SIGSEGV in leaf functions like strtoul. We create the OSMesa
// context on a dedicated pthread with a 32MB stack to avoid this.
static OSMesaContext g_create_result = NULL;

static void* osmesa_create_thread_fn(void* arg) {
    (void)arg;
    if (OSMesaCreateContext_p)
        g_create_result = OSMesaCreateContext_p(GL_RGBA, NULL);
    return NULL;
}

static OSMesaContext create_osmesa_on_bigstack() {
    g_create_result = NULL;
    pthread_t th;
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    size_t stacksz = 32 * 1024 * 1024; // 32MB
    pthread_attr_setstacksize(&attr, stacksz);
    int rc = pthread_create(&th, &attr, osmesa_create_thread_fn, NULL);
    pthread_attr_destroy(&attr);
    if (rc == 0) {
        pthread_join(th, NULL);
    } else {
        // Fallback: call directly (risky on small stack but better than nothing)
        printf("LWJGL linkerhook: pthread_create failed (%d), calling OSMesaCreateContext directly\n", rc);
        if (OSMesaCreateContext_p)
            g_create_result = OSMesaCreateContext_p(GL_RGBA, NULL);
    }
    return g_create_result;
}

static void ensure_osmesa_context_current(int width, int height) {
    if (width <= 0) width = g_window_width;
    if (height <= 0) height = g_window_height;
    if (!osmesa_is_loaded()) {
        dlsym_OSMesa();
        if (!osmesa_is_loaded()) {
            printf("LWJGL linkerhook: OSMesa not loaded, cannot create context\n");
            fflush(stdout);
            return;
        }
    }
    if (g_osmesa_ctx == NULL) {
        if (!OSMesaCreateContext_p) {
            printf("LWJGL linkerhook: OSMesaCreateContext_p is NULL\n");
            fflush(stdout);
            return;
        }
        printf("LWJGL linkerhook: creating OSMesa context on 32MB-stack helper thread...\n");
        fflush(stdout);
        g_osmesa_ctx = create_osmesa_on_bigstack();
        printf("LWJGL linkerhook: OSMesaCreateContext -> %p\n", (void*)g_osmesa_ctx);
        fflush(stdout);
        if (!g_osmesa_ctx) {
            printf("LWJGL linkerhook: OSMesaCreateContext FAILED (Zink/Vulkan init may have failed)\n");
            fflush(stdout);
            return;
        }
    }
    if (g_osmesa_buffer == NULL || g_osmesa_w != width || g_osmesa_h != height) {
        free(g_osmesa_buffer);
        g_osmesa_w = width;
        g_osmesa_h = height;
        size_t bytes = (size_t)width * (size_t)height * 4u;
        g_osmesa_buffer = malloc(bytes);
        printf("LWJGL linkerhook: OSMesa color buffer %dx%d (%zu bytes) at %p\n",
               width, height, bytes, g_osmesa_buffer);
        fflush(stdout);
        if (!g_osmesa_buffer) return;
    }
    if (OSMesaMakeCurrent_p) {
        OSMesaMakeCurrent_p(g_osmesa_ctx, g_osmesa_buffer, GL_UNSIGNED_BYTE, width, height);
        if (OSMesaPixelStore_p) {
            OSMesaPixelStore_p(0x10 /* OSMESA_Y_UP */, 0);
        }
        printf("LWJGL linkerhook: OSMesaMakeCurrent OK (ctx=%p, buf=%p, %dx%d)\n",
               (void*)g_osmesa_ctx, g_osmesa_buffer, width, height);
        fflush(stdout);
    } else {
        printf("LWJGL linkerhook: OSMesaMakeCurrent_p is NULL\n");
        fflush(stdout);
    }
}

// Resolve a GL function pointer using the OSMesa handle loaded by osmesa_loader.c.
// The OSMesa library was loaded with RTLD_LOCAL in a specific namespace, so
// dlsym(RTLD_DEFAULT, ...) from lwjgl_dlopen_hook cannot find GL symbols.
// We must use the actual handle (get_mesa_dl_handle()) and OSMesaGetProcAddress.
static void* resolve_gl_symbol(const char* name) {
    if (!name) return NULL;
    // 1. OSMesaGetProcAddress — official OSMesa GL resolver (knows about Zink too)
    if (OSMesaGetProcAddress_p) {
        void* sym = OSMesaGetProcAddress_p(name);
        if (sym) return sym;
    }
    // 2. Direct dlsym on the OSMesa library handle (RTLD_LOCAL, but handle works)
    void* mesa_handle = get_mesa_dl_handle();
    if (mesa_handle) {
        void* sym = dlsym(mesa_handle, name);
        if (sym) return sym;
    }
    // 3. Last resort: global scope
    void* sym = dlsym(RTLD_DEFAULT, name);
    return sym;
}

// glXGetProcAddress / glXGetProcAddressARB — LWJGL uses these to resolve GL
// function pointers. OSMesa doesn't export GLX, so we resolve via dlsym
// against already-loaded libraries (OSMesa handle is in the caller's handle).
static void* glXGetProcAddress_fake(const char* procName) {
    if (!procName) return NULL;
    void* sym = resolve_gl_symbol(procName);
    if (!sym) {
        printf("LWJGL linkerhook: glXGetProcAddress: not found: %s\n", procName);
    }
    return sym;
}

static void force_turbov1_env(void) {
    // IMPORTANT: Do NOT set EGL_PLATFORM=android here.
    // JREUtils intentionally omits it for turbov1/vulkan_zink so Mesa can pick its path.
    unsetenv("EGL_PLATFORM");

    setenv("MESA_LOADER_DRIVER_OVERRIDE", "zink", 1);
    setenv("GALLIUM_DRIVER", "zink", 1);
    setenv("LIBGL_NOERROR", "1", 1);
    setenv("MESA_GL_VERSION_OVERRIDE", "4.6", 1);
    setenv("MESA_GLSL_VERSION_OVERRIDE", "460", 1);
    setenv("ZINK_DESCRIPTORS", "lazy", 1);
    setenv("mesa_glthread", "false", 1);
    setenv("ZINK_DEBUG", "", 1);
    // Desktop GL path preferred for Zink (matches useGles=false in JREUtils)
    // Do not force LIBGL_ES=2 here.
    printf("LWJGL linkerhook: FINAL-V9 env set (no EGL_PLATFORM force)\n");
}

static void drain_glfw_errors(void) {
    if (!real_glfwGetError) return;
    const char* d = NULL;
    while (real_glfwGetError(&d) != 0) {}
}

static void resolve_all(void* handle) {
    if (!real_glfwInit) {
        real_glfwInit = (int (*)(void)) dlsym(handle, "glfwInit");
        if (!real_glfwInit) real_glfwInit = (int (*)(void)) dlsym(RTLD_DEFAULT, "glfwInit");
    }
    if (!real_glfwGetError) {
        real_glfwGetError = (int (*)(const char**)) dlsym(handle, "glfwGetError");
        if (!real_glfwGetError) real_glfwGetError = (int (*)(const char**)) dlsym(RTLD_DEFAULT, "glfwGetError");
    }
    if (!real_glfwInitHint) {
        real_glfwInitHint = (void (*)(int, int)) dlsym(handle, "glfwInitHint");
        if (!real_glfwInitHint) real_glfwInitHint = (void (*)(int, int)) dlsym(RTLD_DEFAULT, "glfwInitHint");
    }
    if (!real_glfwWindowHint) {
        real_glfwWindowHint = (void (*)(int, int)) dlsym(handle, "glfwWindowHint");
        if (!real_glfwWindowHint) real_glfwWindowHint = (void (*)(int, int)) dlsym(RTLD_DEFAULT, "glfwWindowHint");
    }
    if (!real_glfwCreateWindow) {
        real_glfwCreateWindow = (void* (*)(int, int, const char*, void*, void*)) dlsym(handle, "glfwCreateWindow");
        if (!real_glfwCreateWindow) real_glfwCreateWindow = (void* (*)(int, int, const char*, void*, void*)) dlsym(RTLD_DEFAULT, "glfwCreateWindow");
    }
    if (!real_glfwDefaultWindowHints) {
        real_glfwDefaultWindowHints = (void (*)(void)) dlsym(handle, "glfwDefaultWindowHints");
        if (!real_glfwDefaultWindowHints) real_glfwDefaultWindowHints = (void (*)(void)) dlsym(RTLD_DEFAULT, "glfwDefaultWindowHints");
    }
    if (!real_glfwMakeContextCurrent) {
        real_glfwMakeContextCurrent = (void (*)(void*)) dlsym(handle, "glfwMakeContextCurrent");
        if (!real_glfwMakeContextCurrent) real_glfwMakeContextCurrent = (void (*)(void*)) dlsym(RTLD_DEFAULT, "glfwMakeContextCurrent");
    }
    if (!real_glfwSwapBuffers) {
        real_glfwSwapBuffers = (void (*)(void*)) dlsym(handle, "glfwSwapBuffers");
        if (!real_glfwSwapBuffers) real_glfwSwapBuffers = (void (*)(void*)) dlsym(RTLD_DEFAULT, "glfwSwapBuffers");
    }
    if (!real_glfwSwapInterval) {
        real_glfwSwapInterval = (void (*)(int)) dlsym(handle, "glfwSwapInterval");
        if (!real_glfwSwapInterval) real_glfwSwapInterval = (void (*)(int)) dlsym(RTLD_DEFAULT, "glfwSwapInterval");
    }
    if (!real_glfwGetCurrentContext) {
        real_glfwGetCurrentContext = (void* (*)(void)) dlsym(handle, "glfwGetCurrentContext");
        if (!real_glfwGetCurrentContext) real_glfwGetCurrentContext = (void* (*)(void)) dlsym(RTLD_DEFAULT, "glfwGetCurrentContext");
    }
}

// Desktop OpenGL via EGL (Zink preferred path)
static void apply_hints_desktop_gl(void) {
    if (!real_glfwWindowHint) return;
    // GLFW_CLIENT_API = 0x00022001, GLFW_OPENGL_API = 0x00030001, GLFW_OPENGL_ES_API = 0x00030002
    real_glfwWindowHint(0x00022001, 0x00030001); // CLIENT_API = OPENGL_API
    real_glfwWindowHint(0x0002200B, 0x00036002); // CONTEXT_CREATION_API = EGL
    real_glfwWindowHint(0x00022002, 4);          // MAJOR 4
    real_glfwWindowHint(0x00022003, 6);          // MINOR 6
    real_glfwWindowHint(0x00022008, 0);
    real_glfwWindowHint(0x00022006, 0);          // ANY profile
}

static void apply_hints_gles3(void) {
    if (!real_glfwWindowHint) return;
    real_glfwWindowHint(0x00022001, 0x00030002); // OPENGL_ES_API
    real_glfwWindowHint(0x0002200B, 0x00036002); // EGL
    real_glfwWindowHint(0x00022002, 3);
    real_glfwWindowHint(0x00022003, 0);
    real_glfwWindowHint(0x00022008, 0);
    real_glfwWindowHint(0x00022006, 0);
}

static void apply_hints_no_api(void) {
    if (!real_glfwWindowHint) return;
    real_glfwWindowHint(0x00022001, 0);
}

static int hooked_glfwInit_impl(void) {
    printf("LWJGL linkerhook: FINAL-V9 hooked_glfwInit\n");
    force_turbov1_env();
    resolve_all(RTLD_DEFAULT);

    // Ensure OSMesa symbols are loaded — in NO_API (Zink) mode the bridge's
    // osm_init() is never called because we skip the EGL/bridge path, so we
    // must call dlsym_OSMesa() here to populate g_mesa_dl_handle and
    // OSMesaGetProcAddress_p before LWJGL tries to resolve GL symbols.
    if (!osmesa_is_loaded()) {
        dlsym_OSMesa();
        printf("LWJGL linkerhook: dlsym_OSMesa() called (loaded=%d, handle=%p, GetProcAddr=%p)\n",
               (int)osmesa_is_loaded(), get_mesa_dl_handle(), (void*)OSMesaGetProcAddress_p);
    }

    drain_glfw_errors();

    if (real_glfwInitHint)
        real_glfwInitHint(0x00050003, 0x00060006); // ANDROID platform

    int result = 0;
    if (real_glfwInit) result = real_glfwInit();
    g_glfw_initialized = 1;
    drain_glfw_errors();
    printf("LWJGL linkerhook: FINAL-V9 glfwInit -> %d\n", result);
    return 1;
}

static int hooked_glfwGetError_impl(const char** description) {
    if (real_glfwGetError) {
        const char* d = NULL;
        int code = real_glfwGetError(&d);
        if (code == 0 || code == 0x10001 || code == 0x10004 || code == 0x10008 ||
            code == 65542 || code == 65543 || code == 65544 || code == 65546 || code == 0x10007) {
            if (description) *description = NULL;
            return 0;
        }
        if (!g_window_created) {
            if (description) *description = NULL;
            return 0;
        }
        if (description) *description = d;
        return code;
    }
    if (description) *description = NULL;
    return 0;
}

static void* hooked_glfwCreateWindow_impl(int width, int height, const char* title, void* monitor, void* share) {
    printf("LWJGL linkerhook: V11 CreateWindow %dx%d\n", width, height);
    resolve_all(RTLD_DEFAULT);
    force_turbov1_env();
    drain_glfw_errors();

    if (!real_glfwCreateWindow) return NULL;

    void* win = NULL;
    bool zink = is_zink_renderer();

    if (zink) {
        // Zink path: EGL is NOT available through OSMesa on Android.
        // EGL-based strategies generate GLFW error 65544 which fires the
        // Minecraft error callback and crashes the game. Skip EGL entirely —
        // create a NO_API window. The GL context is provided separately by
        // the OSMesa/Zink bridge (osm_make_current), not by GLFW/EGL.
        printf("LWJGL linkerhook: Zink mode — NO_API window directly\n");
        if (real_glfwDefaultWindowHints) real_glfwDefaultWindowHints();
        apply_hints_no_api();
        win = real_glfwCreateWindow(width, height, title, monitor, share);
        drain_glfw_errors();
        if (win) {
            g_window_created = 1;
            g_has_gl_context = 0;
            g_current_window = win;
            g_window_width = width;
            g_window_height = height;
            printf("LWJGL linkerhook: window OK NO_API (Zink path, no GLFW EGL errors)\n");
            return win;
        }
        printf("LWJGL linkerhook: NO_API window failed in Zink mode\n");
        return NULL;
    }

    // Non-Zink path: try EGL-based strategies first
    // Strategy 1: Desktop OpenGL 4.6 + EGL
    if (real_glfwDefaultWindowHints) real_glfwDefaultWindowHints();
    apply_hints_desktop_gl();
    win = real_glfwCreateWindow(width, height, title, monitor, share);
    drain_glfw_errors();
    if (win) {
        g_window_created = 1;
        g_has_gl_context = 1;
        g_current_window = win;
        printf("LWJGL linkerhook: window OK desktop GL+EGL\n");
        return win;
    }
    printf("LWJGL linkerhook: desktop GL failed\n");

    // Strategy 2: GLES3 + EGL
    if (real_glfwDefaultWindowHints) real_glfwDefaultWindowHints();
    apply_hints_gles3();
    win = real_glfwCreateWindow(width, height, title, monitor, share);
    drain_glfw_errors();
    if (win) {
        g_window_created = 1;
        g_has_gl_context = 1;
        g_current_window = win;
        printf("LWJGL linkerhook: window OK GLES3+EGL\n");
        return win;
    }

    // Strategy 3: NO_API fallback
    if (real_glfwDefaultWindowHints) real_glfwDefaultWindowHints();
    apply_hints_no_api();
    win = real_glfwCreateWindow(width, height, title, monitor, share);
    drain_glfw_errors();
    if (win) {
        g_window_created = 1;
        g_has_gl_context = 0;
        g_current_window = win;
        printf("LWJGL linkerhook: window OK NO_API (no GL context)\n");
        return win;
    }

    printf("LWJGL linkerhook: all CreateWindow strategies failed\n");
    return NULL;
}

static void hooked_glfwMakeContextCurrent_impl(void* window) {
    printf("LWJGL linkerhook: FINAL-V9 MakeContextCurrent %p (has_gl=%e)\n", window, g_has_gl_context);
    if (g_has_gl_context && real_glfwMakeContextCurrent) {
        real_glfwMakeContextCurrent(window);
        drain_glfw_errors();
        g_current_window = window;
        g_context_current = window != NULL;
        return;
    }
    // NO_API path — do not call real (65546)
    g_current_window = window;
    g_context_current = window != NULL;
    if (g_context_current && !g_has_gl_context && is_zink_renderer()) {
        // Zink/NO_API: LWJGL CALLS glGetString/glGetIntegerv inside
        // GL.createCapabilities(). Without a current OSMesaContext those
        // return NULL and LWJGL throws "no OpenGL context current".
        // Create + bind an OSMesa context (Zink over Vulkan) now.
        ensure_osmesa_context_current(g_window_width, g_window_height);
    }
    drain_glfw_errors();
}

static void* hooked_glfwGetCurrentContext_impl(void) {
    if (g_context_current) return g_current_window;
    return NULL;
}

static void hooked_glfwSwapBuffers_impl(void* window) {
    if (real_glfwSwapBuffers && window && g_has_gl_context) {
        real_glfwSwapBuffers(window);
        drain_glfw_errors();
    }
}

static void hooked_glfwSwapInterval_impl(int interval) {
    if (real_glfwSwapInterval && g_has_gl_context)
        real_glfwSwapInterval(interval);
}

static jlong ndlopen_bugfix(__attribute__((unused)) JNIEnv *env,
                     __attribute__((unused)) jclass class,
                     jlong filename_ptr,
                     jint jmode) {
    const char* filename = (const char*) filename_ptr;
    if (!filename) return 0;

    // GL library redirect — MUST be checked BEFORE the "vulkan." check because
    // libmh_drive_vulkan_mesa.so contains "vulkan." but is a GL library (the
    // OpenGL wrapper), NOT a Vulkan ICD. It must resolve to the OSMesa/Zink GL
    // handle so that LWJGL can find glGetString, glClear, etc.
    if (strstr(filename, "libmh_drive_vulkan_mesa.so") != NULL ||
        strstr(filename, "libTurboV1.so") != NULL ||
        strstr(filename, "libGLMojo.so") != NULL ||
        strstr(filename, "libGLFear.so") != NULL ||
        strstr(filename, "libGL.so") != NULL) {
        bool zink = is_zink_renderer();
        printf("LWJGL linkerhook: GL redirect (%s, zink=%d)\n", filename, (int) zink);
        const pojavexec_renderspec_t *rspec = pojavexec_getRenderSpec();
        if (rspec && rspec->egl_acquire)
            return (jlong) rspec->egl_acquire(rspec->egl_path);
    }

    // Vulkan library redirect (system/Turnip)
    if (strstr(filename, "libvulkan.so") == filename || strstr(filename, "vulkan.") != NULL) {
        printf("LWJGL linkerhook: vulkan redirect (%s)\n", filename);
        return (jlong) pojavexec_loadVulkanDriver();
    }

    return (jlong) dlopen(filename, (int)jmode);
}

static jlong ndlsym_hook(__attribute__((unused)) JNIEnv *env,
                  __attribute__((unused)) jclass class,
                  jlong handle,
                  jlong symbol_ptr) {
    const char* symbol = (const char*) symbol_ptr;
    if (!symbol) return 0;

    resolve_all((void*)handle);

    if (strcmp(symbol, "eglGetError") == 0)
        return (jlong) eglGetError_always_success;
    if (strcmp(symbol, "glXGetCurrentContext") == 0) {
        if (!g_has_gl_context && is_zink_renderer() && g_osmesa_ctx == NULL)
            ensure_osmesa_context_current(g_window_width, g_window_height);
        return (jlong) glXGetCurrentContext_fake;
    }
    if (strcmp(symbol, "glXGetProcAddress") == 0 || strcmp(symbol, "glXGetProcAddressARB") == 0)
        return (jlong) glXGetProcAddress_fake;
    if (strcmp(symbol, "glfwInit") == 0)
        return (jlong) hooked_glfwInit_impl;
    if (strcmp(symbol, "glfwGetError") == 0)
        return (jlong) hooked_glfwGetError_impl;
    if (strcmp(symbol, "glfwCreateWindow") == 0)
        return (jlong) hooked_glfwCreateWindow_impl;
    if (strcmp(symbol, "glfwMakeContextCurrent") == 0)
        return (jlong) hooked_glfwMakeContextCurrent_impl;
    if (strcmp(symbol, "glfwGetCurrentContext") == 0)
        return (jlong) hooked_glfwGetCurrentContext_impl;
    if (strcmp(symbol, "glfwSwapBuffers") == 0)
        return (jlong) hooked_glfwSwapBuffers_impl;
    if (strcmp(symbol, "glfwSwapInterval") == 0)
        return (jlong) hooked_glfwSwapInterval_impl;

    void* sym = dlsym((void*) handle, symbol);
    if (!sym) sym = dlsym(RTLD_DEFAULT, symbol);
    // GL symbols: try OSMesa handle + OSMesaGetProcAddress (handles RTLD_LOCAL + namespace)
    if (!sym && strncmp(symbol, "gl", 2) == 0) {
        sym = resolve_gl_symbol(symbol);
        if (!sym) {
            printf("LWJGL linkerhook: GL symbol not found: %s\n", symbol);
            return 0;
        }
    }
    return (jlong) sym;
}

void installLwjglDlopenHook(JNIEnv *env) {
    LOGI("Installing LWJGL hooks (BUILD v20260914-V11, FINAL-V9 restored + panvk_zink)");
    printf("LWJGL linkerhook: installing hooks (BUILD v20260914-V11)\n");
    force_turbov1_env();

    jclass dynamicLinkLoader = (*env)->FindClass(env, "org/lwjgl/system/linux/DynamicLinkLoader");
    if (dynamicLinkLoader == NULL) {
        LOGE("Failed to find DynamicLinkLoader");
        (*env)->ExceptionClear(env);
        return;
    }
    JNINativeMethod hooks[] = {
            {"ndlopen", "(JI)J", &ndlopen_bugfix},
            {"ndlsym",  "(JJ)J", &ndlsym_hook}
    };
    if ((*env)->RegisterNatives(env, dynamicLinkLoader, hooks, 2) != 0) {
        LOGE("Failed to register hooks");
        (*env)->ExceptionClear(env);
    } else {
        printf("LWJGL linkerhook: V11 hooks installed\n");
    }
}
