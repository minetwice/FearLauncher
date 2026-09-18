//
// Created by maks on 09.04.2026.
// FearLauncher: configureRenderspec + mark Zink/OSMesa renderer for bridge.
//

#include "utils.h"
#include "pojavexec.h"
#include "driver_helper/nsbypass.h"
#include "ctxbridges/bridge_environ.h"
#include "ctxbridges/osmesa_loader.h"
#include <jni.h>
#include <stdio.h>
#include <dlfcn.h>
#include <stdlib.h>
#include <string.h>

static JavaVM* dalivk;
static jclass class_CallbackBridge;
static jmethodID method_openLink;

static pojavexec_renderspec_t renderspec = {0};

void openLink(const char* link) {
    JNIEnv *attachedEnv = get_attached_env(dalivk);
    (*attachedEnv)->CallStaticVoidMethod(attachedEnv, class_CallbackBridge, method_openLink, (*attachedEnv)->NewStringUTF(attachedEnv, link));
}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_CallbackBridge_minibridgeInit(JNIEnv *env, jclass clazz) {
    (*env)->GetJavaVM(env, &dalivk);
    class_CallbackBridge = (*env)->NewGlobalRef(env, clazz);
    method_openLink = (*env)->GetStaticMethodID(env, clazz, "openLink", "(Ljava/lang/String;)V");
}

static void* egl_acquire_ns(const char* name) {
    return linker_ns_dlopen(name, RTLD_LOCAL | RTLD_NOW);
}

static void* egl_acquire_default(const char* name) {
    return dlopen(name, RTLD_NOW);
}

JNIEXPORT jboolean JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_configureRenderspec(JNIEnv *env, jclass clazz,
                                                            jstring eglPath, jboolean use_loader_bypass,
                                                            jboolean use_gles,
                                                            jint gles_version) {
    if(eglPath != NULL) {
        const char* egl_path = (*env)->GetStringUTFChars(env, eglPath, NULL);
        if (renderspec.egl_path) free((void*)renderspec.egl_path);
        renderspec.egl_path = strdup(egl_path);
        (*env)->ReleaseStringUTFChars(env, eglPath, egl_path);
        if(!renderspec.egl_path) return false;
        if(use_loader_bypass) {
            const char* native_dir = getenv("POJAV_NATIVEDIR");
            if(!native_dir) return false;
            if(!linker_ns_load(native_dir)) {
                printf("linker_ns_load failed\n");
                return false;
            }
            renderspec.egl_acquire = egl_acquire_ns;
        } else {
            renderspec.egl_acquire = egl_acquire_default;
        }

        void* egl_handle = renderspec.egl_acquire(renderspec.egl_path);
        if(!egl_handle) {
            printf("Failed to load EGL: %s\n", dlerror());
            return false;
        }
        printf("Loaded EGL %s (in namespace: %i)\n", renderspec.egl_path, use_loader_bypass);
    }

    renderspec.force_gles_context = use_gles;
    renderspec.override_major_version = gles_version;

    /* Zink / OSMesa path — critical: bridge must know we are not GL4ES/EGL-GLES */
    if (!use_gles) {
        bridge_environ.config_renderer = RENDERER_VK_ZINK;
        printf("configureRenderspec: config_renderer=RENDERER_VK_ZINK\n");
        /* Eager OSMesa symbol load so context path is ready before glfwCreateWindow */
        dlsym_OSMesa();
        if (osmesa_is_loaded()) {
            printf("configureRenderspec: OSMesa symbols ready\n");
        } else {
            printf("configureRenderspec: OSMesa symbols NOT ready (LIB_MESA_NAME=%s)\n",
                   getenv("LIB_MESA_NAME") ? getenv("LIB_MESA_NAME") : "null");
        }
        /* Ensure system eglGetProcAddress is in the process for any leftover EGL probes */
        void* sys = dlopen("/system/lib64/libEGL.so", RTLD_NOW | RTLD_GLOBAL);
        if (!sys) sys = dlopen("libEGL.so", RTLD_NOW | RTLD_GLOBAL);
        if (sys) printf("configureRenderspec: system libEGL preloaded GLOBAL\n");
    } else {
        bridge_environ.config_renderer = RENDERER_GL4ES;
        printf("configureRenderspec: config_renderer=RENDERER_GL4ES\n");
    }
    return true;
}

const pojavexec_renderspec_t* pojavexec_getRenderSpec() {
    return &renderspec;
}
