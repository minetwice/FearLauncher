//
// Created by maks on 09.04.2026.
//

#include "utils.h"
#include "pojavexec.h"
#include "driver_helper/nsbypass.h"
#include <jni.h>
#include <stdio.h>
#include <dlfcn.h>
#include <stdlib.h>
#include <string.h>
#include <bytehook.h>

#define TAG __FILE_NAME__
#include <log.h>

static JavaVM* dalivk;
static jclass class_CallbackBridge;
static jmethodID method_openLink;

static pojavexec_renderspec_t renderspec = {0};

// ByteHook state
static void* bytehook_handle = NULL;
static bytehook_hook_all_t bytehook_hook_all_p = NULL;

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
        renderspec.egl_path = strdup(egl_path);
        (*env)->ReleaseStringUTFChars(env, eglPath, egl_path);
        if(!renderspec.egl_path) return false;
        if(use_loader_bypass) {
            const char* native_dir = getenv("POJAV_NATIVEDIR");
            if(!native_dir) return false;
            if(!linker_ns_load(native_dir)) {
                printf("linker_ns_load failed
");
                return false;
            }
            renderspec.egl_acquire = egl_acquire_ns;
        } else {
            renderspec.egl_acquire = egl_acquire_default;
        }

        void* egl_handle = renderspec.egl_acquire(renderspec.egl_path);
        if(!egl_handle) {
            printf("Failed to load EGL: %s
", dlerror());
            return false;
        }
        printf("Loaded EGL %s (in namespace: %i)
", renderspec.egl_path, use_loader_bypass);
    }

    renderspec.force_gles_context = use_gles;
    renderspec.override_major_version = gles_version;
    return true;
}

const pojavexec_renderspec_t* pojavexec_getRenderSpec() {
    return &renderspec;
}


// Import the hook from lwjgl_dlopen_hook.c
extern void* eglGetProcAddress_hook(const char* procname);

// Initialize ByteHook and install EGL hook
void install_global_egl_hook() {
    if (bytehook_handle != NULL) {
        // Already initialized
        return;
    }

    bytehook_handle = dlopen("libbytehook.so", RTLD_NOW);
    if (bytehook_handle == NULL) {
        LOGE("Failed to load libbytehook.so: %s", dlerror());
        return;
    }

    int (*bytehook_init_p)(int mode, bool debug);

    bytehook_hook_all_p = (bytehook_hook_all_t) dlsym(bytehook_handle, "bytehook_hook_all");
    bytehook_init_p = (int (*)(int, bool)) dlsym(bytehook_handle, "bytehook_init");

    if (bytehook_hook_all_p == NULL || bytehook_init_p == NULL) {
        LOGE("Failed to get bytehook symbols: %s", dlerror());
        dlclose(bytehook_handle);
        bytehook_handle = NULL;
        return;
    }

    int bhook_status = bytehook_init_p(BYTEHOOK_MODE_AUTOMATIC, false);
    if (bhook_status != BYTEHOOK_STATUS_CODE_OK) {
        LOGE("bytehook_init failed (%i)", bhook_status);
        dlclose(bytehook_handle);
        bytehook_handle = NULL;
        return;
    }

    // Hook eglGetProcAddress in all relevant libraries
    bytehook_hook_all_p(NULL, "eglGetProcAddress", (void*)eglGetProcAddress_hook, NULL, NULL);
    LOGI("Successfully hooked eglGetProcAddress with bytehook");
}

// JNI_OnLoad is called when the library is loaded
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    install_global_egl_hook();
    return JNI_VERSION_1_6;
}