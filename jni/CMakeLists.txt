cmake_minimum_required(VERSION 3.22.1)
project(fear_turbo C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_compile_options(-Wall -Wextra -O2 -fPIC -Wno-unused-parameter -Wno-missing-field-initializers)

# ============================================================================
# Fear Turbo — Custom OpenGL-to-OpenGL ES Translation Engine
# ============================================================================
# This is a from-scratch renderer that:
# 1. Translates desktop OpenGL calls to OpenGL ES 3.x calls
# 2. Parses and converts GLSL shaders (OpenGL desktop) to GLSL ES
# 3. Uses an AST-based shader parser for accurate translation
# 4. Provides shadow buffers for glMapBufferRange (Mali GPU fix)
# ============================================================================

add_library(fear_turbo SHARED
    fearturbo/fear_turbo_core.cpp
    fearturbo/fear_turbo_gl_translator.cpp
    fearturbo/fear_turbo_shader_parser.cpp
    fearturbo/fear_turbo_shader_ast.cpp
    fearturbo/fear_turbo_shader_codegen.cpp
    fearturbo/fear_turbo_buffer_shadow.cpp
    fearturbo/fear_turbo_hooks.cpp
)

set_target_properties(fear_turbo PROPERTIES
    OUTPUT_NAME "FearTurbo"
    CXX_VISIBILITY_PRESET default
    C_VISIBILITY_PRESET default
)

target_include_directories(fear_turbo PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/fearturbo
)

find_library(log-lib log)
find_library(android-lib android)
find_library(egl-lib EGL)
find_library(glesv3-lib GLESv3)

target_link_libraries(fear_turbo PRIVATE
    ${log-lib}
    ${android-lib}
    ${egl-lib}
    ${glesv3-lib}
    dl
)
