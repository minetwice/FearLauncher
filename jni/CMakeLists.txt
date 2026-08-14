cmake_minimum_required(VERSION 3.22.1)
project(fear_render)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_compile_options(-Wall -Wextra -O3 -fPIC -Wno-unused-parameter)

add_library(fear_render SHARED
    src/fear_main.cpp
    src/fear_hooks.cpp
    src/fear_backend.cpp
    src/fear_memory.cpp
    src/fear_shader_translator.cpp
    src/fear_shader_interceptor.cpp
    src/fear_shader_cache.cpp
)

target_include_directories(fear_render PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/src
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(fear_render PRIVATE
    ${log-lib}
    ${android-lib}
    dl
)
