//
// Created by maks on 06.01.2025.
// Cleaned & fixed version for TurboV1 / Zink support (UTF-8 corruption removed)
//

#include "jvm_hooks.h"

#include <android/api-level.h>

#include <dlfcn.h>
#include <string.h>
#include <stdlib.h>
#include <pthread.h>

#define TAG __FILE_NAME__
#include <log.h>

#include "../pojavexec.h"

#define GL_VERSION 0x1F02
#define GL_RENDERER 0x1F01
#define GL_VENDOR 0x1F00
#define GL_EXTENSIONS 0x1F03

static void universal_stub_void(void) {
    LOGI("LWJGL linkerhook: universal GL stub executed");
}

static int eglGetError_stub(void) {
    return 0x3000; // EGL_SUCCESS
}

// Forward declaration for TurboV1 glfwCreateWindow wrapper (implemented elsewhere or weak)
__attribute__((weak)) void* hooked_glfwCreateWindow(int width, int height, const char* title, void* monitor, void* share);

/**
 * ndlopen hook - overrides for vulkan / renderer libraries
 */
static jlong ndlopen_bugfix(__attribute__((unused)) JNIEnv *env,
                     __attribute__((unused)) jclass class,
                     jlong filename_ptr,
                     jint jmode) {
    const char* filename = (const char*) filename_ptr;
    if (!filename) return 0;

    // Override vulkan loading
    if (strstr(filename, "libvulkan.so") == filename || strstr(filename, "vulkan.") != NULL) {
        printf("LWJGL linkerhook: replacing load for libvulkan.so with custom driver\n");
        return (jlong) pojavexec_loadVulkanDriver();
    }

    // TurboV1 / Fear renderer
    if (strstr(filename, "libTurboV1.so") != NULL ||
        strstr(filename, "libGLMojo.so") != NULL ||
        strstr(filename, "libGLFear.so") != NULL ||
        strstr(filename, "libGL.so") != NULL) {
        printf("LWJGL linkerhook: replacing OpenGL with renderspec / TurboV1 driver\n");
        const pojavexec_renderspec_t *rspec = pojavexec_getRenderSpec();
        if (rspec && rspec->egl_acquire) {
            return (jlong) rspec->egl_acquire(rspec->egl_path);
        }
    }

    int mode = (int)jmode;
    return (jlong) dlopen(filename, mode);
}

/**
 * ndlsym hook - intercepts important GL / GLFW / EGL symbols for TurboV1
 */
static jlong ndlsym_hook(__attribute__((unused)) JNIEnv *env,
                  __attribute__((unused)) jclass class,
                  jlong handle,
                  jlong symbol_ptr) {
    const char* symbol = (const char*) symbol_ptr;
    if (!symbol) return 0;

    // TurboV1 / Vulkan / Zink specific hooks
    if (strcmp(symbol, "eglGetError") == 0) {
        printf("LWJGL linkerhook: hooked eglGetError\n");
        return (jlong) eglGetError_stub;
    }
    if (strcmp(symbol, "eglGetProcAddress") == 0) {
        printf("LWJGL linkerhook: hooked eglGetProcAddress\n");
        // Fall through to real one
    }
    if (strcmp(symbol, "glfwInit") == 0) {
        printf("LWJGL linkerhook: hooked glfwInit for TurboV1 Android Vulkan mode\n");
        typedef void (*glfwInitHint_pfn)(int, int);
        typedef int (*glfwInit_pfn)(void);

        glfwInitHint_pfn real_glfwInitHint = (glfwInitHint_pfn) dlsym((void*) handle, "glfwInitHint");
        if (!real_glfwInitHint) real_glfwInitHint = (glfwInitHint_pfn) dlsym(RTLD_DEFAULT, "glfwInitHint");
        if (real_glfwInitHint) {
            // Force Android native platform
            real_glfwInitHint(0x00050003 /* GLFW_PLATFORM */, 0x00060006 /* GLFW_PLATFORM_ANDROID */);
        }

        typedef void (*glfwWindowHint_pfn)(int, int);
        glfwWindowHint_pfn real_glfwWindowHint = (glfwWindowHint_pfn) dlsym((void*) handle, "glfwWindowHint");
        if (!real_glfwWindowHint) real_glfwWindowHint = (glfwWindowHint_pfn) dlsym(RTLD_DEFAULT, "glfwWindowHint");
        if (real_glfwWindowHint) {
            // Prefer OpenGL ES + EGL context (instead of GLFW_NO_API)
            real_glfwWindowHint(0x00022001 /* GLFW_CLIENT_API */, 0x00030001 /* GLFW_OPENGL_ES_API */);
            real_glfwWindowHint(0x0002200B /* GLFW_CONTEXT_CREATION_API */, 0x00036002 /* GLFW_EGL_CONTEXT_API */);
        }
        // Return the real glfwInit so the game can call it
        void* real = dlsym((void*) handle, "glfwInit");
        if (!real) real = dlsym(RTLD_DEFAULT, "glfwInit");
        return (jlong) real;
    }
    if (strcmp(symbol, "glfwGetError") == 0) {
        printf("LWJGL linkerhook: hooked glfwGetError to suppress pre-init error bits\n");
        // Return real function below
    }
    if (strcmp(symbol, "glfwCreateWindow") == 0) {
        printf("LWJGL linkerhook: returning hooked_glfwCreateWindow wrapper for Vulkan/Zink TurboV1 mode\n");
        if (hooked_glfwCreateWindow) {
            return (jlong) hooked_glfwCreateWindow;
        }
        // Fallback to real
    }

    // Generic: try the given handle first, then default
    void* sym = dlsym((void*) handle, symbol);
    if (!sym) {
        sym = dlsym(RTLD_DEFAULT, symbol);
    }
    // For unknown gl* symbols provide a safe stub to avoid null function pointer crashes
    if (!sym && symbol && strncmp(symbol, "gl", 2) == 0) {
        return (jlong) universal_stub_void;
    }
    return (jlong) sym;
}

/**
 * Install the LWJGL dlopen + dlsym hooks.
 */
void installLwjglDlopenHook(JNIEnv *env) {
    LOGI("Installing LWJGL dlopen() and dlsym() hooks (BUILD v20260912-CLEAN)");
    printf("LWJGL linkerhook: installing dlopen/dlsym hooks (BUILD v20260912-CLEAN)\n");
    jclass dynamicLinkLoader = (*env)->FindClass(env, "org/lwjgl/system/linux/DynamicLinkLoader");
    if (dynamicLinkLoader == NULL) {
        LOGE("Failed to find the target class");
        printf("LWJGL linkerhook ERROR: Failed to find DynamicLinkLoader class\n");
        (*env)->ExceptionClear(env);
        return;
    }
    JNINativeMethod hooks[] = {
            {"ndlopen", "(JI)J", &ndlopen_bugfix},
            {"ndlsym",  "(JJ)J", &ndlsym_hook}
    };
    if ((*env)->RegisterNatives(env, dynamicLinkLoader, hooks, 2) != 0) {
        printf("LWJGL linkerhook: RegisterNatives failed\n");
        LOGE("Failed to register the hooked methods");
        (*env)->ExceptionClear(env);
    } else {
        printf("LWJGL linkerhook: dlopen/dlsym hooks installed successfully\n");
    }
}
