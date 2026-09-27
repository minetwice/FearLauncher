#include <jni.h>
#include <sys/types.h>
#include <stdbool.h>
#include <unistd.h>
#include <pthread.h>
#include <stdio.h>
#include <fcntl.h>
#include <string.h>
#include <errno.h>
#include <stdlib.h>
#include <time.h>

#include "stdio_is.h"

//
// Created by maks on 17.02.21.
// MC25: log pipeline made non-blocking for game threads.
//
// Old code: every log line was written straight to latestlog.txt (shared
// storage = FUSE) with an fdatasync PER LINE, on the CALLING thread (the
// pipe reader thread for stdout, and any Java thread for appendToLog).
// Under the atlas-phase load a single FUSE write/fdatasync can stall in
// the kernel for a very long time (D state). That wedges the whole game:
// the pipe reader blocks -> the stdout pipe (64KB) fills -> the render
// thread's next printf blocks -> GPU goes idle, screen freezes, ANR.
// This is exactly the observed freeze (panvk_wd_dump Thread-2 state=D).
//
// Fix: game threads only ever append to a bounded in-memory ring (mutex +
// two memcpy, no I/O). ONE writer thread drains the ring to the file and
// calls fdatasync at most once per 3 seconds. If storage stalls, log
// lines are DROPPED (counted) instead of the game hanging.

static volatile jobject exitTrap_ctx;
static volatile jclass exitTrap_exitClass;
static volatile jmethodID exitTrap_staticMethod;
static JavaVM *exitTrap_jvm;

static int pfd[2];
static pthread_t logger;
static jmethodID logger_onEventLogged;
static volatile jobject logListener = NULL;
static int latestlog_fd = -1;

/* ---- MC25: bounded log ring ---- */
#define LOGQ_CAP (256 * 1024)
static pthread_mutex_t logq_lock = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t logq_cond = PTHREAD_COND_INITIALIZER;
static char logq_buf[LOGQ_CAP];
static size_t logq_head = 0;   /* read position */
static size_t logq_used = 0;  /* bytes waiting */
static unsigned long long logq_dropped = 0;
static volatile int logq_writer_started = 0;

static void logq_push(const char *data, size_t len) {
    if (len == 0 || len >= LOGQ_CAP) return;
    pthread_mutex_lock(&logq_lock);
    if (LOGQ_CAP - logq_used <= len) {
        logq_dropped++;
        pthread_mutex_unlock(&logq_lock);
        return;
    }
    size_t tail = (logq_head + logq_used) % LOGQ_CAP;
    size_t first = LOGQ_CAP - tail;
    if (first > len) first = len;
    memcpy(logq_buf + tail, data, first);
    if (len > first) memcpy(logq_buf, data + first, len - first);
    logq_used += len;
    pthread_cond_signal(&logq_cond);
    pthread_mutex_unlock(&logq_lock);
}

/* Drains up to outcap bytes; returns bytes drained. */
static size_t logq_drain(char *out, size_t outcap) {
    size_t done = 0;
    pthread_mutex_lock(&logq_lock);
    size_t n = logq_used < outcap ? logq_used : outcap;
    if (n > 0) {
        size_t first = LOGQ_CAP - logq_head;
        if (first > n) first = n;
        memcpy(out, logq_buf + logq_head, first);
        if (n > first) memcpy(out + first, logq_buf, n - first);
        logq_head = (logq_head + n) % LOGQ_CAP;
        logq_used -= n;
        done = n;
    }
    pthread_mutex_unlock(&logq_lock);
    return done;
}

static void *logq_writer_thread(void *param) {
    JNIEnv *env;
    JavaVM *dvm = (JavaVM *) param;
    (*dvm)->AttachCurrentThread(dvm, &env, NULL);
    char tmp[8192];
    char note[96];
    struct timespec last_sync;
    clock_gettime(CLOCK_MONOTONIC, &last_sync);
    for (;;) {
        pthread_mutex_lock(&logq_lock);
        while (logq_used == 0)
            pthread_cond_wait(&logq_cond, &logq_lock);
        pthread_mutex_unlock(&logq_lock);

        unsigned long long dropped;
        pthread_mutex_lock(&logq_lock);
        dropped = logq_dropped;
        logq_dropped = 0;
        pthread_mutex_unlock(&logq_lock);
        if (dropped && latestlog_fd != -1) {
            int nn = snprintf(note, sizeof(note),
                              "[MC25] log queue overflow, %llu line(s) dropped\n",
                              dropped);
            if (nn > 0) write(latestlog_fd, note, (size_t)nn);
        }

        size_t n = logq_drain(tmp, sizeof(tmp));
        if (n > 0 && latestlog_fd != -1)
            write(latestlog_fd, tmp, n);

        struct timespec now;
        clock_gettime(CLOCK_MONOTONIC, &now);
        if (now.tv_sec - last_sync.tv_sec >= 3) {
            if (latestlog_fd != -1) fdatasync(latestlog_fd);
            last_sync = now;
        }
    }
    /* unreachable */
    (*dvm)->DetachCurrentThread(dvm);
    return NULL;
}

static bool recordBuffer(char *buf, ssize_t len) {
    if (strstr(buf, "Session ID is")) return false;
    /* MC25: never touch the file from the caller's thread */
    logq_push(buf, (size_t)len);
    return true;
}

static void *logger_thread(void *param) {
    JNIEnv *env;
    jstring writeString;
    JavaVM *dvm = (JavaVM *) param;
    (*dvm)->AttachCurrentThread(dvm, &env, NULL);
    ssize_t rsize;
    char buf[2050];
    while ((rsize = read(pfd[0], buf, sizeof(buf) - 1)) > 0) {
        bool shouldRecordString = recordBuffer(buf, rsize); //queued for latestlog
        if (buf[rsize - 1] == '\n') {
            rsize = rsize - 1; //truncate
        }
        buf[rsize] = 0x00;
        if (shouldRecordString && logListener != NULL) {
            writeString = (*env)->NewStringUTF(env, buf); //send to app without newline
            (*env)->CallVoidMethod(env, logListener, logger_onEventLogged, writeString);
            (*env)->DeleteLocalRef(env, writeString);
        }
    }
    (*dvm)->DetachCurrentThread(dvm);
    return NULL;
}


JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_Logger_begin(JNIEnv *env, __attribute((unused)) jclass clazz, jstring logPath) {
    if (latestlog_fd != -1) {
        int localfd = latestlog_fd;
        latestlog_fd = -1;
        close(localfd);
    }
    if (logger_onEventLogged == NULL) {
        jclass eventLogListener = (*env)->FindClass(env, "net/kdt/pojavlaunch/Logger$eventLogListener");
        logger_onEventLogged = (*env)->GetMethodID(env, eventLogListener, "onEventLogged", "(Ljava/lang/String;)V");
    }
    jclass ioeClass = (*env)->FindClass(env, "java/io/IOException");

    setvbuf(stdout, 0, _IOLBF, 0); // make stdout line-buffered
    setvbuf(stderr, 0, _IONBF, 0); // make stderr unbuffered

    /* create the pipe and redirect stdout and stderr */
    pipe(pfd);
    dup2(pfd[1], 1);
    dup2(pfd[1], 2);

    /* open latestlog.txt for writing */
    const char *logFilePath = (*env)->GetStringUTFChars(env, logPath, NULL);
    latestlog_fd = open(logFilePath, O_WRONLY | O_TRUNC);
    if (latestlog_fd == -1) {
        latestlog_fd = 0;
        (*env)->ThrowNew(env, ioeClass, strerror(errno));
        return;
    }
    (*env)->ReleaseStringUTFChars(env, logPath, logFilePath);

    JavaVM *vm = NULL;
    (*env)->GetJavaVM(env, &vm);

    /* spawn the logging thread */
    int result = pthread_create(&logger, 0, logger_thread, vm);
    if (result != 0) {
        close(latestlog_fd);
        (*env)->ThrowNew(env, ioeClass, strerror(result));
        return;
    }
    pthread_detach(logger);

    /* MC25: spawn the single file-writer thread (once) */
    if (!logq_writer_started) {
        pthread_t writer;
        if (pthread_create(&writer, 0, logq_writer_thread, vm) == 0) {
            pthread_detach(writer);
            logq_writer_started = 1;
        }
    }
}

JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_Logger_appendToLog(JNIEnv *env, __attribute((unused)) jclass clazz, jstring text) {
    jsize appendStringLength = (*env)->GetStringUTFLength(env, text);
    char newChars[appendStringLength + 2];
    (*env)->GetStringUTFRegion(env, text, 0, (*env)->GetStringLength(env, text), newChars);
    newChars[appendStringLength] = '\n';
    newChars[appendStringLength + 1] = 0;
    if (recordBuffer(newChars, appendStringLength + 1) && logListener != NULL) {
        (*env)->CallVoidMethod(env, logListener, logger_onEventLogged, text);
    }
}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_Logger_setLogListener(JNIEnv *env, __attribute((unused)) jclass clazz, jobject log_listener) {
    jobject logListenerLocal = logListener;
    if (log_listener == NULL) {
        logListener = NULL;
    } else {
        logListener = (*env)->NewGlobalRef(env, log_listener);
    }
    if (logListenerLocal != NULL) (*env)->DeleteGlobalRef(env, logListenerLocal);
}
