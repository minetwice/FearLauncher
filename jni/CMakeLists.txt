cmake_minimum_required(VERSION 3.10)
project(fear_turbo C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

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
    C_VISIBILITY_PRESET default
    VISIBILITY_INLINES_HIDDEN OFF
)

target_include_directories(fear_turbo PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/fearturbo
)

target_compile_options(fear_turbo PRIVATE
    -fvisibility=default
    -Wno-unused-parameter
    -Wno-unused-variable
    -Wno-missing-field-initializers
)

find_library(log-lib log)
find_library(android-lib android)
find_library(egl-lib EGL)
find_library(glesv3-lib GLESv3)
find_library(dl-lib dl)

target_link_libraries(fear_turbo PRIVATE
    ${egl-lib}
    ${glesv3-lib}
    ${log-lib}
    ${android-lib}
    ${dl-lib}
)
