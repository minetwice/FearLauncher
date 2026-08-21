LOCAL_PATH := $(call my-dir)

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
