cmake_minimum_required(VERSION 3.10)
project(turbov1 C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Aggressive optimization flags for maximum FPS
set(CMAKE_CXX_FLAGS_RELEASE "-O3 -ffast-math -funroll-loops -fomit-frame-pointer -DNDEBUG")
set(CMAKE_CXX_FLAGS_RELWITHDEBINFO "-O3 -ffast-math -funroll-loops -g")

# Indus2.0 TurboV1 Epic FPS Booster Engine Native Library
add_library(turbov1 SHARED
    turbov1/turbo_v1_core.cpp
    turbov1/turbo_v1_buffer.cpp
    turbov1/turbo_v1_gl_translator.cpp
    turbov1/turbo_v1_shader_transpiler.cpp
    turbov1/turbo_v1_hooks.cpp
    turbov1/turbo_v1_perf.cpp
    turbov1/turbo_v1_entity.cpp
)

set_target_properties(turbov1 PROPERTIES
    OUTPUT_NAME "TurboV1"
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON
)

target_include_directories(turbov1 PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/turbov1
)

target_link_libraries(turbov1 PRIVATE
    EGL
    GLESv3
    log
    android
    dl
)
