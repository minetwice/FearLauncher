//
// Ported from ZalithLauncher (ctxbridges/swap_interval_no_egl.c)
// Sets swap interval directly on ANativeWindow without EGL.
//
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <android/log.h>
#include <android/native_window.h>

typedef struct android_native_base_t {
    int magic;
    int version;
    void* reserved[4];
    void (*incRef)(struct android_native_base_t* base);
    void (*decRef)(struct android_native_base_t* base);
} android_native_base_t;

#define ANDROID_NATIVE_MAKE_CONSTANT(a,b,c,d) \
    (((unsigned)(a)<<24)|((unsigned)(b)<<16)|((unsigned)(c)<<8)|(unsigned)(d))
#define ANDROID_NATIVE_WINDOW_MAGIC \
    ANDROID_NATIVE_MAKE_CONSTANT('_','w','n','d')

struct ANativeWindowBuffer;

struct ANativeWindow_real {
    struct android_native_base_t common;
    const uint32_t flags;
    const int   minSwapInterval;
    const int   maxSwapInterval;
    const float xdpi;
    const float ydpi;
    intptr_t    oem[4];
    int (*setSwapInterval)(struct ANativeWindow_real* window, int interval);
    int (*dequeueBuffer_DEPRECATED)(struct ANativeWindow_real* window, struct ANativeWindowBuffer** buffer);
    int (*lockBuffer_DEPRECATED)(struct ANativeWindow_real* window, struct ANativeWindowBuffer* buffer);
    int (*queueBuffer_DEPRECATED)(struct ANativeWindow_real* window, struct ANativeWindowBuffer* buffer);
    int (*query)(const struct ANativeWindow_real* window, int what, int* value);
    int (*perform)(struct ANativeWindow_real* window, int operation, ... );
    int (*cancelBuffer_DEPRECATED)(struct ANativeWindow_real* window, struct ANativeWindowBuffer* buffer);
    int (*dequeueBuffer)(struct ANativeWindow_real* window, struct ANativeWindowBuffer** buffer, int* fenceFd);
    int (*queueBuffer)(struct ANativeWindow_real* window, struct ANativeWindowBuffer* buffer, int fenceFd);
    int (*cancelBuffer)(struct ANativeWindow_real* window, struct ANativeWindowBuffer* buffer, int fenceFd);
};

void setNativeWindowSwapInterval(struct ANativeWindow* nativeWindow, int swapInterval) {
    struct ANativeWindow_real* window = (struct ANativeWindow_real*) nativeWindow;
    if(!window) return;
    if(window->common.magic != ANDROID_NATIVE_WINDOW_MAGIC) {
        __android_log_print(ANDROID_LOG_ERROR, "SwapInterval", "ANativeWindow magic mismatch!");
        return;
    }
    if(window->setSwapInterval == NULL) {
        __android_log_print(ANDROID_LOG_WARN, "SwapInterval", "setSwapInterval is NULL");
        return;
    }
    window->setSwapInterval(window, swapInterval);
}
