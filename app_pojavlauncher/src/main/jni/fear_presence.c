/*
 * Fear Monster presence: the call that actually publishes the status on Discord.
 *
 * The Discord Social SDK exposes presence in C, not in Java. Its Java layer
 * (DiscordRpcClient, NativeCalls) covers the RPC transport and audio; the rich-presence
 * call lives behind cdiscord.h, which ships inside the same AAR as a Prefab package. So
 * the wrapper is C, and CMake links it against the package.
 *
 * This launcher uses Rich Presence WITHOUT authentication - no account linking, no token,
 * just the Discord app installed on the phone. Discord's documentation for that route says
 * to set the application id, build the activity and call UpdateRichPresence.
 *
 * An activity has four parts that matter here: its name, its type, its details and its
 * state. The name is the line that reads "Playing ...", so it has to be set - without it
 * Discord falls back to a generic label, which is what "Game" was.
 *
 * The callbacks are pumped from a thread of this file's own making, attached to the JVM
 * with AttachCurrentThread, and kept off the main thread because RunCallbacks blocks.
 *
 * Guarded by FEAR_PRESENCE_HAVE_SDK, which CMake defines only when the package was
 * found. The JNI entry points exist either way, so the Java side gets a clean false and a
 * log line rather than UnsatisfiedLinkError, and the build still succeeds.
 */
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#include <unistd.h>
#include <android/log.h>

#define LOG_TAG "FearPresence"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)

#ifdef FEAR_PRESENCE_HAVE_SDK
#include "cdiscord.h"
#endif

/* Kept here rather than in Java so the strings handed to the SDK stay alive for as long
 * as the SDK may hold on to them. */
static char g_name[64] = "Fear Launcher";
static char g_details[128] = "Powered by fear";
static char g_state[256] = "";
static pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;

static void copy_into(JNIEnv* env, jstring src, char* dst, size_t cap) {
    if (src == NULL || dst == NULL || cap == 0) return;
    const char* utf = (*env)->GetStringUTFChars(env, src, NULL);
    if (utf == NULL) return;
    strncpy(dst, utf, cap - 1);
    dst[cap - 1] = '\0';
    (*env)->ReleaseStringUTFChars(env, src, utf);
}

#ifdef FEAR_PRESENCE_HAVE_SDK

static Discord_Client* g_client = NULL;
static Discord_Client_Status g_status = Discord_Client_Status_Disconnected;
static JavaVM* g_vm = NULL;
static pthread_t g_pump;
static volatile int g_pump_running = 0;

/* The SDK takes strings as a buffer plus a length, not a C string. */
static Discord_String str_ref(char* s) {
    Discord_String out;
    out.ptr = (uint8_t*) s;
    out.size = s ? strlen(s) : 0;
    return out;
}

/*
 * Drives the SDK's callbacks. The thread is attached to the JVM first, because those
 * callbacks make JNI calls back into the app and an unattached thread has no environment.
 * It is deliberately not the main thread: RunCallbacks blocks while it works, and on the
 * main thread that stops the launcher responding.
 */
static void* pump_thread(void* unused) {
    (void) unused;
    JNIEnv* env = NULL;
    if (g_vm != NULL) {
        (*g_vm)->AttachCurrentThread(g_vm, &env, NULL);
    }
    while (g_pump_running) {
        Discord_RunCallbacks();
        usleep(100 * 1000);
    }
    if (g_vm != NULL) {
        (*g_vm)->DetachCurrentThread(g_vm);
    }
    return NULL;
}

/*
 * Sends the activity. Deliberately not gated on the client's status: this launcher uses the
 * unauthenticated RPC route, where the SDK talks to the installed Discord app directly and
 * the authenticated Ready state is not what drives it.
 */
static void publish_locked(void) {
    if (g_client == NULL) return;
    Discord_Activity* activity = (Discord_Activity*) Discord_Alloc(sizeof(Discord_Activity));
    if (activity == NULL) {
        LOGW("could not allocate an activity");
        return;
    }
    Discord_Activity_Init(activity);
    /* The name is the first line. Without it Discord shows a generic label - "Game".
     * Note the signature: SetName takes the string by value, while SetDetails and SetState
     * take a pointer. */
    Discord_String name = str_ref(g_name);
    Discord_Activity_SetName(activity, name);
    Discord_Activity_SetType(activity, Discord_ActivityTypes_Playing);
    Discord_String details = str_ref(g_details);
    Discord_String state = str_ref(g_state);
    Discord_Activity_SetDetails(activity, &details);
    Discord_Activity_SetState(activity, &state);
    Discord_Client_UpdateRichPresence(g_client, activity, NULL, NULL, NULL);
    LOGI("presence sent to the SDK: name=\"%s\" details=\"%s\" state=\"%s\" (client status %d)",
         g_name, g_details, g_state, (int) g_status);
}

/* Logged so the launcher's log can show whether the client ever left disconnected. */
static void on_status_changed(Discord_Client_Status status, Discord_Client_Error error,
                              int32_t errorDetail, void* userData) {
    (void) error; (void) errorDetail; (void) userData;
    pthread_mutex_lock(&g_lock);
    g_status = status;
    LOGI("Discord status changed: %d (0 disconnected, 1 connecting, 2 connected, 3 ready)",
         (int) status);
    if (status == Discord_Client_Status_Ready) publish_locked();
    pthread_mutex_unlock(&g_lock);
}

#endif /* FEAR_PRESENCE_HAVE_SDK */

JNIEXPORT jboolean JNICALL
Java_net_kdt_pojavlaunch_presence_FearPresence_nativeStart(JNIEnv* env, jclass clazz, jstring appId) {
#ifdef FEAR_PRESENCE_HAVE_SDK
    pthread_mutex_lock(&g_lock);
    if (g_client != NULL) {
        pthread_mutex_unlock(&g_lock);
        return JNI_TRUE;
    }
    if (g_vm == NULL) {
        (*env)->GetJavaVM(env, &g_vm);
    }
    const char* id = appId ? (*env)->GetStringUTFChars(env, appId, NULL) : NULL;
    uint64_t app = id ? (uint64_t) strtoull(id, NULL, 10) : 0;
    if (id) (*env)->ReleaseStringUTFChars(env, appId, id);
    if (app == 0) {
        pthread_mutex_unlock(&g_lock);
        LOGW("no application id; presence stays off");
        return JNI_FALSE;
    }
    g_client = (Discord_Client*) Discord_Alloc(sizeof(Discord_Client));
    if (g_client == NULL) {
        pthread_mutex_unlock(&g_lock);
        LOGW("could not allocate the Discord client");
        return JNI_FALSE;
    }
    Discord_Client_Init(g_client);
    Discord_Client_SetApplicationId(g_client, app);
    Discord_Client_SetStatusChangedCallback(g_client, on_status_changed, NULL, NULL);
    Discord_Client_Connect(g_client);
    g_pump_running = 1;
    pthread_create(&g_pump, NULL, pump_thread, NULL);
    pthread_mutex_unlock(&g_lock);
    LOGI("Discord RPC client started for application %llu", (unsigned long long) app);
    return JNI_TRUE;
#else
    (void) env; (void) clazz; (void) appId;
    LOGW("Discord Social SDK is not linked into this build; presence stays off");
    return JNI_FALSE;
#endif
}

/*
 * Runs the SDK's pending callbacks once. Kept for completeness; the pump thread is what
 * actually drives them, because doing this on the main looper stops the launcher
 * responding while RunCallbacks works.
 */
JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_presence_FearPresence_nativePump(JNIEnv* env, jclass clazz) {
#ifdef FEAR_PRESENCE_HAVE_SDK
    (void) env; (void) clazz;
    if (g_client == NULL) return;
    Discord_RunCallbacks();
#else
    (void) env; (void) clazz;
#endif
}

/*
 * The current status as a number, so the Java side can put it in the launcher's log. -1
 * means the SDK is not in this build at all, which is a different problem from "not
 * connected yet".
 */
JNIEXPORT jint JNICALL
Java_net_kdt_pojavlaunch_presence_FearPresence_nativeStatus(JNIEnv* env, jclass clazz) {
#ifdef FEAR_PRESENCE_HAVE_SDK
    (void) env; (void) clazz;
    jint out;
    pthread_mutex_lock(&g_lock);
    out = (jint) g_status;
    pthread_mutex_unlock(&g_lock);
    return out;
#else
    (void) env; (void) clazz;
    return -1;
#endif
}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_presence_FearPresence_nativeSetPlaying(JNIEnv* env, jclass clazz,
                                                               jstring details, jstring state) {
#ifdef FEAR_PRESENCE_HAVE_SDK
    pthread_mutex_lock(&g_lock);
    copy_into(env, details, g_details, sizeof(g_details));
    copy_into(env, state, g_state, sizeof(g_state));
    publish_locked();
    pthread_mutex_unlock(&g_lock);
#else
    (void) env; (void) clazz; (void) details; (void) state;
#endif
}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_presence_FearPresence_nativeClear(JNIEnv* env, jclass clazz) {
#ifdef FEAR_PRESENCE_HAVE_SDK
    pthread_mutex_lock(&g_lock);
    if (g_client != NULL) {
        Discord_Activity* activity = (Discord_Activity*) Discord_Alloc(sizeof(Discord_Activity));
        if (activity != NULL) {
            /* An activity with nothing in it is how a status is taken back down. */
            Discord_Activity_Init(activity);
            Discord_Activity_SetType(activity, Discord_ActivityTypes_Playing);
            Discord_Client_UpdateRichPresence(g_client, activity, NULL, NULL, NULL);
        }
        LOGI("presence cleared");
    }
    pthread_mutex_unlock(&g_lock);
#else
    (void) env; (void) clazz;
#endif
}
