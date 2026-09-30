#include <jni.h>
#include <assert.h>
#include <string.h>
#include <stdio.h>
#include <dlfcn.h>
#include "native_hooks.h"
#include <stdlib.h>
#include <stdint.h> /* FEARWIRE-HOLYZINK-ROTATE2 */

static JavaVM* dalvikJavaVMPtr;

static JavaVM* runtimeJavaVMPtr;
static JNIEnv* runtimeJNIEnvPtr_GRAPHICS;
static JNIEnv* runtimeJNIEnvPtr_INPUT;
jclass class_CTCScreen;
jmethodID method_GetRGB;

jclass class_CTCAndroidInput;
jmethodID method_ReceiveInput;

jclass class_MainActivity;
jmethodID method_OpenLink;
jmethodID method_OpenPath;
jclass class_JavaGUILauncherActivity;
jmethodID method_QuerySystemClipboard;
jmethodID method_PutClipboardData;

jclass class_Frame;
jclass class_Rectangle;
jclass class_CTCClipboard = NULL;
jmethodID constructor_Rectangle;
jmethodID method_GetFrames;
jmethodID method_GetBounds;
jmethodID method_SetBounds;
jmethodID method_SystemClipboardDataReceived = NULL;

jfieldID field_x;
jfieldID field_y;

typedef void (*install_global_egl_hook_fn)(bytehook_hook_all_t);

/* FEARWIRE-HOLYZINK-ROTATE2: rewrite 90/270 buffer transforms to identity
   (ROT_90=0x10, ROT_270=0x30 - both match & 0x10) so zink's unrotated
   landscape output is displayed upright instead of sideways. */
static int32_t (*real_holy_setBuffersTransform_p)(void*, int32_t);
static int32_t hooked_holy_setBuffersTransform_impl(void* window, int32_t transform) {
    const char* fearRenderer = getenv("FEAR_RENDERER");
    /* FEARWIRE-HOLYZINK-ROTATE8: force ROTATE_90 - the producer transform
       is what neutralizes the WSI's preTransform rotation. */
    if (fearRenderer && strcmp(fearRenderer, "holy_zink_kopper") == 0 && transform != 1) {
        printf("FEARWIRE-ROTATE: ANativeWindow_setBuffersTransform(%d) -> 1 (ROTATE8)\n", transform);
        transform = 1;
    }
    if (real_holy_setBuffersTransform_p == NULL) {
        real_holy_setBuffersTransform_p = (int32_t (*)(void*, int32_t)) dlsym(RTLD_DEFAULT, "ANativeWindow_setBuffersTransform");
        /* FEARWIRE-HOLYZINK-ROTATE4: isolated namespace - dlopen fallback */
        if (real_holy_setBuffersTransform_p == NULL) {
            void* fearNatWin = dlopen("libnativewindow.so", RTLD_NOW);
            if (fearNatWin == NULL) fearNatWin = dlopen("libandroid.so", RTLD_NOW);
            if (fearNatWin != NULL)
                real_holy_setBuffersTransform_p = (int32_t (*)(void*, int32_t)) dlsym(fearNatWin, "ANativeWindow_setBuffersTransform");
        }
        printf("FEARWIRE-ROTATE4: awt setBuffersTransform resolved=%p\n", (void*) real_holy_setBuffersTransform_p);
    }
    if (real_holy_setBuffersTransform_p == NULL) return 0;
    return real_holy_setBuffersTransform_p(window, transform);
}

/* FEARWIRE-HOLYZINK-ROTATE3-guard: mojo's glfw resets the buffer geometry to
   the app-landscape default with (0,0); keep the portrait geometry we forced. */
static int (*real_holy_setBuffersGeometry_p)(void*, int, int, int);
static int (*real_holy_getWinWidth_p)(void*);
static int (*real_holy_getWinHeight_p)(void*);
static int hooked_holy_setBuffersGeometry_impl(void* window, int width, int height, int format) {
    const char* fearRenderer = getenv("FEAR_RENDERER");
    if (fearRenderer && strcmp(fearRenderer, "holy_zink_kopper") == 0
        && width == 0 && height == 0 && window != NULL) {
        if (real_holy_getWinWidth_p == NULL) {
            real_holy_getWinWidth_p = (int (*)(void*)) dlsym(RTLD_DEFAULT, "ANativeWindow_getWidth");
            real_holy_getWinHeight_p = (int (*)(void*)) dlsym(RTLD_DEFAULT, "ANativeWindow_getHeight");
            if (real_holy_getWinWidth_p == NULL || real_holy_getWinHeight_p == NULL) {
                void* fearNatWinG = dlopen("libnativewindow.so", RTLD_NOW);
                if (fearNatWinG == NULL) fearNatWinG = dlopen("libandroid.so", RTLD_NOW);
                if (fearNatWinG != NULL) {
                    if (real_holy_getWinWidth_p == NULL)
                        real_holy_getWinWidth_p = (int (*)(void*)) dlsym(fearNatWinG, "ANativeWindow_getWidth");
                    if (real_holy_getWinHeight_p == NULL)
                        real_holy_getWinHeight_p = (int (*)(void*)) dlsym(fearNatWinG, "ANativeWindow_getHeight");
                }
            }
        }
        if (real_holy_getWinWidth_p != NULL && real_holy_getWinHeight_p != NULL) {
            width = real_holy_getWinWidth_p(window);
            height = real_holy_getWinHeight_p(window);
            printf("FEARWIRE-ROTATE3: kept geometry reset -> %dx%d\n", width, height);
        }
    }
    if (real_holy_setBuffersGeometry_p == NULL)
        real_holy_setBuffersGeometry_p = (int (*)(void*, int, int, int)) dlsym(RTLD_DEFAULT, "ANativeWindow_setBuffersGeometry");
        if (real_holy_setBuffersGeometry_p == NULL) {
            void* fearNatWinH = dlopen("libnativewindow.so", RTLD_NOW);
            if (fearNatWinH == NULL) fearNatWinH = dlopen("libandroid.so", RTLD_NOW);
            if (fearNatWinH != NULL)
                real_holy_setBuffersGeometry_p = (int (*)(void*, int, int, int)) dlsym(fearNatWinH, "ANativeWindow_setBuffersGeometry");
        }
    if (real_holy_setBuffersGeometry_p == NULL) return -1;
    return real_holy_setBuffersGeometry_p(window, width, height, format);
}

jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    /* FEARWIRE-HOLYZINK-ROTATE2: install the transform intercept directly via
       bytehook - libpojavexec_awt always loads and links it, unlike the
       exithook/linkerhook chain which is dead in this fork's game process. */
    bytehook_hook_all(NULL, "ANativeWindow_setBuffersTransform",
                      (void*) hooked_holy_setBuffersTransform_impl, NULL, NULL);
    bytehook_hook_all(NULL, "ANativeWindow_setBuffersGeometry",
                      (void*) hooked_holy_setBuffersGeometry_impl, NULL, NULL); /* ROTATE3-guard */

    // Install global EGL hook first - get bytehook_hook_all from exithook
    void* exithook_handle = dlopen("libexithook.so", RTLD_LAZY);
    if(exithook_handle) {
        bytehook_hook_all_t bytehook_hook_all_p = (bytehook_hook_all_t)dlsym(exithook_handle, "bytehook_hook_all");
        if(bytehook_hook_all_p) {
            install_global_egl_hook_fn hook_fn = (install_global_egl_hook_fn) dlsym(RTLD_DEFAULT, "install_global_egl_hook");
            if(!hook_fn) {
                void* linkerhook_handle = dlopen("liblinkerhook.so", RTLD_LAZY);
                if(linkerhook_handle) {
                    hook_fn = (install_global_egl_hook_fn) dlsym(linkerhook_handle, "install_global_egl_hook");
                }
            }
            if(hook_fn) {
                hook_fn(bytehook_hook_all_p);
            }
        }
    }
    
    if (dalvikJavaVMPtr == NULL) {
        //Save dalvik global JavaVM pointer
        dalvikJavaVMPtr = vm;
        JNIEnv *env = NULL;
        (*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_4);
        class_MainActivity = (*env)->NewGlobalRef(env,(*env)->FindClass(env, "net/kdt/pojavlaunch/CallbackBridge"));
        method_OpenLink= (*env)->GetStaticMethodID(env, class_MainActivity, "openLink", "(Ljava/lang/String;)V");
        method_OpenPath= (*env)->GetStaticMethodID(env, class_MainActivity, "openLink", "(Ljava/lang/String;)V");
        class_JavaGUILauncherActivity = (*env)->NewGlobalRef(env, (*env)->FindClass(env, "net/kdt/pojavlaunch/JavaGUILauncherActivity"));
        method_QuerySystemClipboard = (*env)->GetStaticMethodID(env, class_JavaGUILauncherActivity, "querySystemClipboard", "()V");
        method_PutClipboardData = (*env)->GetStaticMethodID(env, class_JavaGUILauncherActivity, "putClipboardData", "(Ljava/lang/String;Ljava/lang/String;)V");
    } else if (dalvikJavaVMPtr != vm) {
        runtimeJavaVMPtr = vm;
    }

    return JNI_VERSION_1_4;
}

JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_AWTInputBridge_nativeSendData(JNIEnv* env, jclass clazz, jint type, jint i1, jint i2, jint i3, jint i4) {
    if (runtimeJNIEnvPtr_INPUT == NULL) {
        if (runtimeJavaVMPtr == NULL) {
            return;
        } else {
            (*runtimeJavaVMPtr)->AttachCurrentThreadAsDaemon(runtimeJavaVMPtr, &runtimeJNIEnvPtr_INPUT, NULL);
        }
    }

    if (method_ReceiveInput == NULL) {
        class_CTCAndroidInput = (*runtimeJNIEnvPtr_INPUT)->FindClass(runtimeJNIEnvPtr_INPUT, "net/java/openjdk/cacio/ctc/CTCAndroidInput");
        if ((*runtimeJNIEnvPtr_INPUT)->ExceptionCheck(runtimeJNIEnvPtr_INPUT) == JNI_TRUE) {
            (*runtimeJNIEnvPtr_INPUT)->ExceptionClear(runtimeJNIEnvPtr_INPUT);
            class_CTCAndroidInput = (*runtimeJNIEnvPtr_INPUT)->FindClass(runtimeJNIEnvPtr_INPUT, "com/github/caciocavallosilano/cacio/ctc/CTCAndroidInput");
        }
        assert(class_CTCAndroidInput != NULL);
        method_ReceiveInput = (*runtimeJNIEnvPtr_INPUT)->GetStaticMethodID(runtimeJNIEnvPtr_INPUT, class_CTCAndroidInput, "receiveData", "(IIIII)V");
        assert(method_ReceiveInput != NULL);
    }
    (*runtimeJNIEnvPtr_INPUT)->CallStaticVoidMethod(
        runtimeJNIEnvPtr_INPUT,
        class_CTCAndroidInput,
        method_ReceiveInput,
        type, i1, i2, i3, i4
    );
}

// TODO: check for memory leaks
// int printed = 0;
int threadAttached = 0;
JNIEXPORT jboolean JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_renderAWTScreenFrame(JNIEnv* env, jclass clazz, jobject targetBuffer) {
    if (runtimeJNIEnvPtr_GRAPHICS == NULL) {
        if (runtimeJavaVMPtr == NULL) {
            return JNI_FALSE;
        } else {
            (*runtimeJavaVMPtr)->AttachCurrentThreadAsDaemon(runtimeJavaVMPtr, &runtimeJNIEnvPtr_GRAPHICS, NULL);
        }
    }
    jintArray jreRgbArray;
  
    if (method_GetRGB == NULL) {
        class_CTCScreen = (*runtimeJNIEnvPtr_GRAPHICS)->FindClass(runtimeJNIEnvPtr_GRAPHICS, "net/java/openjdk/cacio/ctc/CTCScreen");
        if ((*runtimeJNIEnvPtr_GRAPHICS)->ExceptionCheck(runtimeJNIEnvPtr_GRAPHICS) == JNI_TRUE) {
            (*runtimeJNIEnvPtr_GRAPHICS)->ExceptionClear(runtimeJNIEnvPtr_GRAPHICS);
            class_CTCScreen = (*runtimeJNIEnvPtr_GRAPHICS)->FindClass(runtimeJNIEnvPtr_GRAPHICS, "com/github/caciocavallosilano/cacio/ctc/CTCScreen");
        }
        assert(class_CTCScreen != NULL);
        method_GetRGB = (*runtimeJNIEnvPtr_GRAPHICS)->GetStaticMethodID(runtimeJNIEnvPtr_GRAPHICS, class_CTCScreen, "getCurrentScreenRGB", "()[I");
        assert(method_GetRGB != NULL);
    }
    jreRgbArray = (jintArray) (*runtimeJNIEnvPtr_GRAPHICS)->CallStaticObjectMethod(
        runtimeJNIEnvPtr_GRAPHICS,
        class_CTCScreen,
        method_GetRGB
    );
    if (jreRgbArray == NULL) {
        return JNI_FALSE;
    }

    jint arrayLength = (*runtimeJNIEnvPtr_GRAPHICS)->GetArrayLength(runtimeJNIEnvPtr_GRAPHICS, jreRgbArray);

    void* prim_src = (*runtimeJNIEnvPtr_GRAPHICS)->GetPrimitiveArrayCritical(runtimeJNIEnvPtr_GRAPHICS, jreRgbArray, NULL);
    void* prim_dst = (*env)->GetDirectBufferAddress(env, targetBuffer);
    if(prim_src == NULL) {
        return JNI_FALSE;
    }
    memcpy(prim_dst, prim_src, arrayLength * sizeof(jint));
    (*runtimeJNIEnvPtr_GRAPHICS)->ReleasePrimitiveArrayCritical(runtimeJNIEnvPtr_GRAPHICS, jreRgbArray, prim_src, 0);
    
    return JNI_TRUE;
}

JNIEXPORT void JNICALL Java_net_java_openjdk_cacio_ctc_CTCClipboard_nQuerySystemClipboard(JNIEnv *env, jclass clazz) {
    JNIEnv *dalvikEnv;char detachable = 0;
    if((*dalvikJavaVMPtr)->GetEnv(dalvikJavaVMPtr, (void **) &dalvikEnv, JNI_VERSION_1_6) == JNI_EDETACHED) {
        (*dalvikJavaVMPtr)->AttachCurrentThread(dalvikJavaVMPtr, &dalvikEnv, NULL);
        detachable = 1;
    }
    if(method_SystemClipboardDataReceived == NULL) {
        class_CTCClipboard = (*env)->NewGlobalRef(env, clazz);
        method_SystemClipboardDataReceived = (*env)->GetStaticMethodID(env, clazz, "systemClipboardDataReceived", "(Ljava/lang/String;Ljava/lang/String;)V");
    }
    (*dalvikEnv)->CallStaticVoidMethod(dalvikEnv, class_JavaGUILauncherActivity, method_QuerySystemClipboard);
    if(detachable) (*dalvikJavaVMPtr)->DetachCurrentThread(dalvikJavaVMPtr);
}

JNIEXPORT void JNICALL Java_net_java_openjdk_cacio_ctc_CTCClipboard_nPutClipboardData(JNIEnv* env, jclass clazz, jstring clipboardData, jstring clipboardDataMime) {
    JNIEnv *dalvikEnv;char detachable = 0;
    if((*dalvikJavaVMPtr)->GetEnv(dalvikJavaVMPtr, (void **) &dalvikEnv, JNI_VERSION_1_6) == JNI_EDETACHED) {
        (*dalvikJavaVMPtr)->AttachCurrentThread(dalvikJavaVMPtr, &dalvikEnv, NULL);
        detachable = 1;
    }

    const char* dataChars = (*env)->GetStringUTFChars(env, clipboardData, NULL);
    const char* mimeChars = (*env)->GetStringUTFChars(env, clipboardDataMime, NULL);
    (*dalvikEnv)->CallStaticVoidMethod(dalvikEnv, class_JavaGUILauncherActivity, method_PutClipboardData,
                                       (*dalvikEnv)->NewStringUTF(dalvikEnv, dataChars),
                                       (*dalvikEnv)->NewStringUTF(dalvikEnv, mimeChars));
    (*env)->ReleaseStringUTFChars(env, clipboardData, dataChars);
    (*env)->ReleaseStringUTFChars(env, clipboardDataMime, mimeChars);
    if(detachable) (*dalvikJavaVMPtr)->DetachCurrentThread(dalvikJavaVMPtr);
}

JNIEXPORT void JNICALL Java_com_github_caciocavallosilano_cacio_ctc_CTCClipboard_nQuerySystemClipboard(JNIEnv *env, jclass clazz) {
    Java_net_java_openjdk_cacio_ctc_CTCClipboard_nQuerySystemClipboard(env, clazz);
}

JNIEXPORT void JNICALL Java_com_github_caciocavallosilano_cacio_ctc_CTCClipboard_nPutClipboardData(JNIEnv* env, jclass clazz, jstring clipboardData, jstring clipboardDataMime) {
    Java_net_java_openjdk_cacio_ctc_CTCClipboard_nPutClipboardData(env, clazz, clipboardData, clipboardDataMime);
}

JNIEXPORT void JNICALL Java_net_java_openjdk_cacio_ctc_CTCDesktopPeer_openFile(JNIEnv *env, jclass clazz, jstring filePath) {
    JNIEnv *dalvikEnv;char detachable = 0;
    if((*dalvikJavaVMPtr)->GetEnv(dalvikJavaVMPtr, (void **) &dalvikEnv, JNI_VERSION_1_6) == JNI_EDETACHED) {
        (*dalvikJavaVMPtr)->AttachCurrentThread(dalvikJavaVMPtr, &dalvikEnv, NULL);
        detachable = 1;
    }
    const char* stringChars = (*env)->GetStringUTFChars(env, filePath, NULL);
    (*dalvikEnv)->CallStaticVoidMethod(dalvikEnv, class_MainActivity, method_OpenPath, (*dalvikEnv)->NewStringUTF(dalvikEnv, stringChars));
    (*env)->ReleaseStringUTFChars(env, filePath, stringChars);
    if(detachable) (*dalvikJavaVMPtr)->DetachCurrentThread(dalvikJavaVMPtr);
}

JNIEXPORT void JNICALL Java_net_java_openjdk_cacio_ctc_CTCDesktopPeer_openUri(JNIEnv *env, jclass clazz, jstring uri) {
    JNIEnv *dalvikEnv;char detachable = 0;
    if((*dalvikJavaVMPtr)->GetEnv(dalvikJavaVMPtr, (void **) &dalvikEnv, JNI_VERSION_1_6) == JNI_EDETACHED) {
        (*dalvikJavaVMPtr)->AttachCurrentThread(dalvikJavaVMPtr, &dalvikEnv, NULL);
        detachable = 1;
    }
    const char* stringChars = (*env)->GetStringUTFChars(env, uri, NULL);
    (*dalvikEnv)->CallStaticVoidMethod(dalvikEnv, class_MainActivity, method_OpenLink, (*dalvikEnv)->NewStringUTF(dalvikEnv, stringChars));
    (*env)->ReleaseStringUTFChars(env, uri, stringChars);
    if(detachable) (*dalvikJavaVMPtr)->DetachCurrentThread(dalvikJavaVMPtr);
}

JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_AWTInputBridge_nativeClipboardReceived(JNIEnv *env, jclass clazz, jstring clipboardData, jstring clipboardDataMime) {
    if(method_SystemClipboardDataReceived == NULL || class_CTCClipboard == NULL) return;
    if (runtimeJNIEnvPtr_INPUT == NULL) {
        if (runtimeJavaVMPtr == NULL) {
            return;
        } else {
            (*runtimeJavaVMPtr)->AttachCurrentThreadAsDaemon(runtimeJavaVMPtr, &runtimeJNIEnvPtr_INPUT, NULL);
        }
    }
    const char* dataChars = clipboardData != NULL ? (*env)->GetStringUTFChars(env, clipboardData, NULL) : NULL;
    const char* mimeChars = clipboardDataMime != NULL ? (*env)->GetStringUTFChars(env, clipboardDataMime, NULL) : NULL;
    (*runtimeJNIEnvPtr_INPUT)->CallStaticVoidMethod(runtimeJNIEnvPtr_INPUT, class_CTCClipboard, method_SystemClipboardDataReceived,
                                                    clipboardData != NULL ? (*runtimeJNIEnvPtr_INPUT)->NewStringUTF(runtimeJNIEnvPtr_INPUT, dataChars) : NULL,
                                                    clipboardDataMime != NULL ? (*runtimeJNIEnvPtr_INPUT)->NewStringUTF(runtimeJNIEnvPtr_INPUT, mimeChars) : NULL);
    if(dataChars != NULL) (*env)->ReleaseStringUTFChars(env, clipboardData, dataChars);
    if(mimeChars != NULL) (*env)->ReleaseStringUTFChars(env, clipboardDataMime, mimeChars);
}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_AWTInputBridge_nativeMoveWindow(JNIEnv *env, jclass clazz, jint xoff, jint yoff) {
    if (runtimeJNIEnvPtr_INPUT == NULL) {
        if (runtimeJavaVMPtr == NULL) {
            return;
        } else {
            (*runtimeJavaVMPtr)->AttachCurrentThreadAsDaemon(runtimeJavaVMPtr, &runtimeJNIEnvPtr_INPUT, NULL);
        }
    }
    if(field_y == NULL) {
        class_Frame = (*runtimeJNIEnvPtr_INPUT)->FindClass(runtimeJNIEnvPtr_INPUT, "java/awt/Frame");
        method_GetFrames = (*runtimeJNIEnvPtr_INPUT)->GetStaticMethodID(runtimeJNIEnvPtr_INPUT, class_Frame, "getFrames", "()[Ljava/awt/Frame;");
        method_GetBounds = (*runtimeJNIEnvPtr_INPUT)->GetMethodID(runtimeJNIEnvPtr_INPUT, class_Frame, "getBounds", "(Ljava/awt/Rectangle;)Ljava/awt/Rectangle;");
        method_SetBounds = (*runtimeJNIEnvPtr_INPUT)->GetMethodID(runtimeJNIEnvPtr_INPUT, class_Frame, "setBounds", "(Ljava/awt/Rectangle;)V");
        class_Rectangle = (*runtimeJNIEnvPtr_INPUT)->FindClass(runtimeJNIEnvPtr_INPUT, "java/awt/Rectangle");
        constructor_Rectangle = (*runtimeJNIEnvPtr_INPUT)->GetMethodID(runtimeJNIEnvPtr_INPUT, class_Rectangle, "<init>", "()V");
        field_x = (*runtimeJNIEnvPtr_INPUT)->GetFieldID(runtimeJNIEnvPtr_INPUT, class_Rectangle, "x", "I");
        field_y = (*runtimeJNIEnvPtr_INPUT)->GetFieldID(runtimeJNIEnvPtr_INPUT, class_Rectangle, "y", "I");
    }
    jobject rectangle = (*runtimeJNIEnvPtr_INPUT)->NewObject(runtimeJNIEnvPtr_INPUT, class_Rectangle, constructor_Rectangle);
    jobjectArray frames = (*runtimeJNIEnvPtr_INPUT)->CallStaticObjectMethod(runtimeJNIEnvPtr_INPUT, class_Frame, method_GetFrames);
    for(jsize i = 0; i < (*runtimeJNIEnvPtr_INPUT)->GetArrayLength(runtimeJNIEnvPtr_INPUT, frames); i++) {
        jobject frame = (*runtimeJNIEnvPtr_INPUT)->GetObjectArrayElement(runtimeJNIEnvPtr_INPUT, frames, i);
        (*runtimeJNIEnvPtr_INPUT)->CallObjectMethod(runtimeJNIEnvPtr_INPUT, frame, method_GetBounds, rectangle);
        (*runtimeJNIEnvPtr_INPUT)->SetIntField(runtimeJNIEnvPtr_INPUT, rectangle,  field_x, (*runtimeJNIEnvPtr_INPUT)->GetIntField(runtimeJNIEnvPtr_INPUT, rectangle, field_x) + xoff);
        (*runtimeJNIEnvPtr_INPUT)->SetIntField(runtimeJNIEnvPtr_INPUT, rectangle,  field_y, (*runtimeJNIEnvPtr_INPUT)->GetIntField(runtimeJNIEnvPtr_INPUT, rectangle, field_y) + yoff);
        (*runtimeJNIEnvPtr_INPUT)->CallVoidMethod(runtimeJNIEnvPtr_INPUT, frame, method_SetBounds, rectangle);
        (*runtimeJNIEnvPtr_INPUT)->DeleteLocalRef(runtimeJNIEnvPtr_INPUT, frame);
    }
    (*runtimeJNIEnvPtr_INPUT)->DeleteLocalRef(runtimeJNIEnvPtr_INPUT, rectangle);
    (*runtimeJNIEnvPtr_INPUT)->DeleteLocalRef(runtimeJNIEnvPtr_INPUT, frames);
}
