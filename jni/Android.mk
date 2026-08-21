LOCAL_PATH := $(call my-dir)

# ═══════════════════════════════════════════════
# FEAR RENDER ENGINE MODULE
# ═══════════════════════════════════════════════
include $(CLEAR_VARS)
LOCAL_MODULE    := fear_render
LOCAL_SRC_FILES := src/fear_main.cpp \
                   src/fear_egl.cpp \
                   src/fear_gl_guards.cpp \
                   src/fear_formats.cpp \
                   src/fear_shader.cpp
LOCAL_LDLIBS    := -llog -landroid -ldl
LOCAL_CPPFLAGS  := -std=c++17 -Wall -Wextra -O3 -fPIC -Wno-unused-parameter
include $(BUILD_SHARED_LIBRARY)


# ═══════════════════════════════════════════════
# MH DRIVE GL WRAPPER MODULE (With glMemoryBarrier fix)
# ═══════════════════════════════════════════════
include $(CLEAR_VARS)
LOCAL_MODULE    := mh_drive_gl_wrapper
LOCAL_SRC_FILES := src/mh_drive_gl_wrapper.cpp
LOCAL_LDLIBS    := -llog -landroid -ldl
LOCAL_CPPFLAGS  := -std=c++17 -Wall -Wextra -O3 -fPIC -Wno-unused-parameter
include $(BUILD_SHARED_LIBRARY)


# ═══════════════════════════════════════════════
# MH DRIVE VULKAN MESA MODULE (With feature spoofing)
# ═══════════════════════════════════════════════
include $(CLEAR_VARS)
LOCAL_MODULE    := mh_drive_vulkan_mesa
LOCAL_SRC_FILES := src/zink_device.c
LOCAL_LDLIBS    := -llog -landroid -ldl
LOCAL_CPPFLAGS  := -std=c++17 -Wall -Wextra -O3 -fPIC -Wno-unused-parameter
include $(BUILD_SHARED_LIBRARY)
