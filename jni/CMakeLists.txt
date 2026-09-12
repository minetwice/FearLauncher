cmake_minimum_required(VERSION 3.22.1)

# Avoid redefining project() when this file is pulled in via add_subdirectory()
if(CMAKE_SOURCE_DIR STREQUAL CMAKE_CURRENT_SOURCE_DIR)
    project(turbov1 C CXX)
endif()

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# TurboV1 Engine Native Library
add_library(turbov1 SHARED
    turbov1/turbo_v1_core.cpp
    turbov1/turbo_v1_buffer.cpp
    turbov1/turbo_v1_gl_translator.cpp
    turbov1/turbo_v1_shader_transpiler.cpp
    turbov1/turbo_v1_vulkan.cpp
    turbov1/turbo_v1_hooks.cpp
)

set_target_properties(turbov1 PROPERTIES
    OUTPUT_NAME "TurboV1"
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON
)

target_include_directories(turbov1 PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/turbov1
)

# Explicitly resolve Android NDK system libraries (more reliable than bare names)
find_library(log-lib log)
find_library(android-lib android)
find_library(egl-lib EGL)
find_library(glesv3-lib GLESv3)
find_library(dl-lib dl)

target_link_libraries(turbov1 PRIVATE
    ${log-lib}
    ${android-lib}
    ${egl-lib}
    ${glesv3-lib}
    ${dl-lib}
)

# Vulkan headers come from the NDK sysroot. Functions are resolved at runtime via dlsym.
# No need to link libvulkan.so at build time.
