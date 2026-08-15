cmake_minimum_required(VERSION 3.22.1)
project(fear_render)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)

# FetchContent for glslang (Khronos Reference GLSL Compiler)
FetchContent_Declare(
    glslang
    GIT_REPOSITORY https://github.com/KhronosGroup/glslang.git
    GIT_TAG        14.0.0
)

# FetchContent for SPIRV-Cross
FetchContent_Declare(
    spirv_cross
    GIT_REPOSITORY https://github.com/KhronosGroup/SPIRV-Cross.git
    GIT_TAG        sdk-1.3.268.0
)

add_compile_options(-Wall -Wextra -O3 -fPIC -Wno-unused-parameter)

add_library(fear_render SHARED
    src/fear_main.cpp
    src/fear_hooks.cpp
    src/fear_backend.cpp
    src/fear_memory.cpp
    src/fear_shader_translator.cpp
    src/fear_shader_interceptor.cpp
    src/fear_shader_cache.cpp
    src/fear_glsl_transpiler.cpp
    src/fear_gl_emulation.cpp
    fearrender/fear_render_engine.cpp
    fearrender/fear_shader_strategy.cpp
)

target_include_directories(fear_render PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/src
    ${CMAKE_CURRENT_SOURCE_DIR}/fearrender
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(fear_render PRIVATE
    ${log-lib}
    ${android-lib}
    dl
)
