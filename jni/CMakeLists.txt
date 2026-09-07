cmake_minimum_required(VERSION 3.10)
project(fear_turbo C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Fear Turbo Engine Native Library
add_library(fear_turbo SHARED
    fearturbo/fear_turbo_core.cpp
    fearturbo/fear_turbo_buffer_shadow.cpp
    fearturbo/fear_turbo_gl_translator.cpp
    fearturbo/fear_turbo_shader_ast.cpp
    fearturbo/fear_turbo_shader_codegen.cpp
    fearturbo/fear_turbo_shader_parser.cpp
    fearturbo/fear_turbo_hooks.cpp
    fearturbo/fear_turbo_egl.cpp
)

set_target_properties(fear_turbo PROPERTIES
    OUTPUT_NAME "FearTurbo"
    CXX_VISIBILITY_PRESET default
    VISIBILITY_INLINES_HIDDEN OFF
)

target_include_directories(fear_turbo PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/fearturbo
)

target_compile_options(fear_turbo PRIVATE -fvisibility=default)

target_link_libraries(fear_turbo PRIVATE
    EGL
    GLESv3
    log
    android
    dl
)
