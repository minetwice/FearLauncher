#include <jni.h>
#include <assert.h>
#include <string.h>
#include <stdio.h>
#include "driver_helper/hook.h"

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
jmethodID method_SystemClipboardDataReceived = NULL;

jfieldID field_x;
jfieldID field_y;

jint JNI_OnLoad(JavaVM* vm, void* reserved) {
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

    if (type == 0) {
        // Mouse event
        (*runtimeJNIEnvPtr_INPUT)->CallStaticVoidMethod(runtimeJNIEnvPtr_INPUT, class_CTCAndroidInput, method_ReceiveInput, 0, i1, i2, i3, i4);
    } else if (type == 1) {
        // Key event
        (*runtimeJNIEnvPtr_INPUT)->CallStaticVoidMethod(runtimeJNIEnvPtr_INPUT, class_CTCAndroidInput, method_ReceiveInput, 1, i1, i2, i3, i4);
    } else if (type == 2) {
        // Scroll event
        (*runtimeJNIEnvPtr_INPUT)->CallStaticVoidMethod(runtimeJNIEnvPtr_INPUT, class_CTCAndroidInput, method_ReceiveInput, 2, i1, i2, i3, i4);
    }
}

JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_AWTInputBridge_nativeSendImage(JNIEnv* env, jclass clazz, jobject buffer, jint width, jint height, jint stride) {
    if (runtimeJNIEnvPtr_GRAPHICS == NULL) {
        if (runtimeJavaVMPtr == NULL) {
            return;
        } else {
            (*runtimeJavaVMPtr)->AttachCurrentThreadAsDaemon(runtimeJavaVMPtr, &runtimeJNIEnvPtr_GRAPHICS, NULL);
        }
    }

    jint* elements = (*env)->GetIntArrayElements(env, buffer, NULL);
    (*runtimeJNIEnvPtr_GRAPHICS)->CallStaticVoidMethod(runtimeJNIEnvPtr_GRAPHICS, class_CTCScreen, method_GetRGB, elements, width, height, stride);
    (*env)->ReleaseIntArrayElements(env, buffer, elements, JNI_ABORT);
}

JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_AWTInputBridge_nativeInit(JNIEnv* env, jclass clazz, jclass ctcScreenClass, jclass ctcAndroidInputClass) {
    class_CTCScreen = (*env)->NewGlobalRef(env, ctcScreenClass);
    class_CTCAndroidInput = (*env)->NewGlobalRef(env, ctcAndroidInputClass);

    method_GetRGB = (*env)->GetStaticMethodID(env, class_CTCScreen, "getRGB", "([IIII)V");
    method_ReceiveInput = (*env)->GetStaticMethodID(env, class_CTCAndroidInput, "receiveInput", "(IIIII)V");
    method_SystemClipboardDataReceived = (*env)->GetStaticMethodID(env, class_CTCAndroidInput, "systemClipboardDataReceived", "(Ljava/lang/String;)V");
}

JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_AWTInputBridge_nativeSendClipboardData(JNIEnv* env, jclass clazz, jstring data) {
    const char* clipboardData = (*env)->GetStringUTFChars(env, data, NULL);
    if (method_SystemClipboardDataReceived != NULL && runtimeJNIEnvPtr_INPUT != NULL) {
        jstring jclipboardData = (*env)->NewStringUTF(env, clipboardData);
        (*runtimeJNIEnvPtr_INPUT)->CallStaticVoidMethod(runtimeJNIEnvPtr_INPUT, class_CTCAndroidInput, method_SystemClipboardDataReceived, jclipboardData);
        (*env)->DeleteLocalRef(env, jclipboardData);
    }
    (*env)->ReleaseStringUTFChars(env, data, clipboardData);
}
