LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := fear_render
LOCAL_SRC_FILES := src/fear_main.cpp \
                   src/fear_hooks.cpp \
                   src/fear_backend.cpp \
                   src/fear_shader.cpp \
                   src/fear_memory.cpp

LOCAL_LDLIBS    := -llog -landroid -ldl

LOCAL_CPPFLAGS  := -std=c++17 -Wall -Wextra -O3 -fPIC -Wno-unused-parameter

include $(BUILD_SHARED_LIBRARY)
