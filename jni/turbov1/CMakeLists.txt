# TurboV1 - NextGen High-FPS Render Engine
# Extreme Performance for 200+ FPS with Entity Optimization

cmake_minimum_required(VERSION 3.22.1)

project(turbo_v1 LANGUAGES CXX C)

# Include directories
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/../
)

# TurboV1 Core Library (Static for better linking)
add_library(turbo_v1_core STATIC
    turbo_v1_core.cpp
    turbo_v1_core.h
    turbo_v1_gl_translator.cpp
    turbo_v1_gl_translator.h
    turbo_v1_buffer.cpp
    turbo_v1_buffer.h
    turbo_v1_shader_transpiler.cpp
    turbo_v1_shader_transpiler.h
    turbo_v1_hooks.cpp
    turbo_v1_hooks.h
    # NEW: FPS Booster System
    turbo_v1_fps_booster.cpp
    turbo_v1_fps_booster.h
    # NEW: Quality Enhancer System
    quality_enhancer/turbo_v1_quality_enhancer.cpp
    quality_enhancer/turbo_v1_quality_enhancer.h
    # NEW: Smoothness Manager System
    smoothness/turbo_v1_smoothness_manager.cpp
    smoothness/turbo_v1_smoothness_manager.h
)

# Link dependencies
target_link_libraries(turbo_v1_core PRIVATE
    GLESv3
    EGL
    log
    android
)

# Compiler flags for maximum performance
if(CMAKE_CXX_COMPILER_ID STREQUAL "Clang")
    target_compile_options(turbo_v1_core PRIVATE
        -O3
        -march=armv8-a+fp16
        -mtune=generic
        -funsafe-math-optimizations
        -ffast-math
        -fno-math-errno
        -fno-signed-zeros
        -fno-trapping-math
        -finline-functions
        -finline-hint-functions
        -fomit-frame-pointer
        -funroll-loops
        -fno-exceptions
        -fno-rtti
    )
endif()

# Enable C++17 features
target_compile_features(turbo_v1_core PRIVATE cxx_std_17)

# Export include directory for users of this library
target_include_directories(turbo_v1_core PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}
)
