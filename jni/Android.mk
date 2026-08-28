LOCAL_PATH := $(call my-dir)

# ═══════════════════════════════════════════════
# QUASAR CORE — pure custom GLES3 translator
# No LTW / gl4es / MobileGlues / Mesa
# ═══════════════════════════════════════════════
include $(CLEAR_VARS)
LOCAL_MODULE    := fear_render
LOCAL_SRC_FILES := src/fear_main.cpp \
                   src/fear_backend.cpp \
                   src/fear_memory.cpp \
                   src/quasar_core.cpp
LOCAL_C_INCLUDES := $(LOCAL_PATH)/src
LOCAL_LDLIBS    := -llog -landroid -ldl -lEGL -lGLESv3
LOCAL_CPPFLAGS  := -std=c++17 -Wall -O2 -fPIC -Wno-unused-parameter -DQUASAR_PURE=1
LOCAL_CFLAGS    := -O2 -fPIC
include $(BUILD_SHARED_LIBRARY)

# MH Drive wrappers kept for other renderers
include $(CLEAR_VARS)
LOCAL_MODULE    := mh_drive_gl_wrapper
LOCAL_SRC_FILES := src/mh_drive_gl_wrapper.cpp
LOCAL_LDLIBS    := -llog -landroid -ldl
LOCAL_CPPFLAGS  := -std=c++17 -O2 -fPIC -Wno-unused-parameter
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE    := mh_drive_vulkan_mesa
LOCAL_SRC_FILES := src/zink_device.c
LOCAL_LDLIBS    := -llog -landroid -ldl
LOCAL_CFLAGS    := -O2 -fPIC -Wno-unused-parameter
include $(BUILD_SHARED_LIBRARY)
