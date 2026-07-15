LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := fear_render
LOCAL_SRC_FILES := \
    ../src/fear_hooks.cpp \
    ../src/fear_backend.cpp \
    ../src/fear_shader.cpp \
    ../src/fear_memory.cpp \
    ../src/fear_main.cpp

LOCAL_LDLIBS := -llog -landroid -lEGL -lGLESv3
LOCAL_CFLAGS := -std=c++17 -Wall -O3 -fexceptions -frtti

include $(BUILD_SHARED_LIBRARY)
