cmake_minimum_required(VERSION 3.22.1)
project(fear_render C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)

find_program(PYTHON_EXECUTABLE NAMES python3 python)
if(PYTHON_EXECUTABLE)
    set(PYTHON_EXECUTABLE "${PYTHON_EXECUTABLE}" CACHE FILEPATH "" FORCE)
    set(Python_EXECUTABLE "${PYTHON_EXECUTABLE}" CACHE FILEPATH "" FORCE)
    set(Python3_EXECUTABLE "${PYTHON_EXECUTABLE}" CACHE FILEPATH "" FORCE)
endif()

add_compile_options(-Wall -Wextra -O2 -fPIC -Wno-unused-parameter -Wno-missing-field-initializers -DFMT_HEADER_ONLY=1)

# Disable unnecessary executables/tests in dependencies to speed up build
set(SPIRV_CROSS_ENABLE_TESTS OFF CACHE BOOL "" FORCE)
set(SPIRV_CROSS_ENABLE_HLSL OFF CACHE BOOL "" FORCE)
set(SPIRV_CROSS_ENABLE_MSL OFF CACHE BOOL "" FORCE)
set(SPIRV_CROSS_CLI OFF CACHE BOOL "" FORCE)
set(SPIRV_CROSS_ENABLE_C_API OFF CACHE BOOL "" FORCE)
set(SPIRV_CROSS_ENABLE_REFLECT OFF CACHE BOOL "" FORCE)

set(SHADERC_SKIP_EXAMPLES ON CACHE BOOL "" FORCE)
set(SHADERC_SKIP_TESTS ON CACHE BOOL "" FORCE)
set(SHADERC_SKIP_COPYRIGHT_CHECK ON CACHE BOOL "" FORCE)
set(SHADERC_ENABLE_GLSLC OFF CACHE BOOL "" FORCE)
set(SHADERC_ENABLE_EXAMPLES OFF CACHE BOOL "" FORCE)
set(GLSLANG_TESTS OFF CACHE BOOL "" FORCE)
set(GLSLANG_ENABLE_INSTALL OFF CACHE BOOL "" FORCE)
set(SPIRV_SKIP_TESTS ON CACHE BOOL "" FORCE)
set(SPIRV_SKIP_EXECUTABLES ON CACHE BOOL "" FORCE)
set(SPIRV_TOOLS_BUILD_STATIC ON CACHE BOOL "" FORCE)

# Check if full local vendor tree exists
if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/fearrender/fogl_core/deps/spirv-cross/CMakeLists.txt" AND
   EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/fearrender/fogl_core/deps/shaderc/third_party/spirv-tools/CMakeLists.txt")
    add_subdirectory(fearrender/fogl_core/deps/spirv-cross EXCLUDE_FROM_ALL)
    add_subdirectory(fearrender/fogl_core/deps/shaderc EXCLUDE_FROM_ALL)
else()
    FetchContent_Declare(
        spirv_headers
        GIT_REPOSITORY https://github.com/KhronosGroup/SPIRV-Headers.git
        GIT_TAG        vulkan-sdk-1.3.268.0
    )
    FetchContent_GetProperties(spirv_headers)
    if(NOT spirv_headers_POPULATED)
        FetchContent_Populate(spirv_headers)
        set(SHADERC_SPIRV_HEADERS_DIR "${spirv_headers_SOURCE_DIR}" CACHE STRING "" FORCE)
    endif()

    FetchContent_Declare(
        spirv_tools
        GIT_REPOSITORY https://github.com/KhronosGroup/SPIRV-Tools.git
        GIT_TAG        vulkan-sdk-1.3.268.0
    )
    FetchContent_GetProperties(spirv_tools)
    if(NOT spirv_tools_POPULATED)
        FetchContent_Populate(spirv_tools)
        set(SHADERC_SPIRV_TOOLS_DIR "${spirv_tools_SOURCE_DIR}" CACHE STRING "" FORCE)
    endif()

    FetchContent_Declare(
        glslang
        GIT_REPOSITORY https://github.com/KhronosGroup/glslang.git
        GIT_TAG        vulkan-sdk-1.3.268.0
    )
    FetchContent_GetProperties(glslang)
    if(NOT glslang_POPULATED)
        FetchContent_Populate(glslang)
        set(SHADERC_GLSLANG_DIR "${glslang_SOURCE_DIR}" CACHE STRING "" FORCE)
    endif()

    FetchContent_Declare(
        spirv_cross
        GIT_REPOSITORY https://github.com/KhronosGroup/SPIRV-Cross.git
        GIT_TAG        vulkan-sdk-1.3.268.0
    )

    FetchContent_Declare(
        shaderc
        GIT_REPOSITORY https://github.com/google/shaderc.git
        GIT_TAG        v2024.0
    )

    FetchContent_MakeAvailable(spirv_cross shaderc)
endif()

file(GLOB_RECURSE FOGL_SOURCES
    "fearrender/fogl_core/src/*.cpp"
)

add_library(fearrender SHARED
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
    ${FOGL_SOURCES}
)

set_target_properties(fearrender PROPERTIES
    OUTPUT_NAME "GLFear"
    CXX_VISIBILITY_PRESET default
    C_VISIBILITY_PRESET default
)

target_include_directories(fearrender PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/src
    ${CMAKE_CURRENT_SOURCE_DIR}/fearrender
    ${CMAKE_CURRENT_SOURCE_DIR}/fearrender/fogl_core/include
    ${CMAKE_CURRENT_SOURCE_DIR}/fearrender/fogl_core/build_info
    ${CMAKE_CURRENT_SOURCE_DIR}/fearrender/fogl_core/deps/fmt/include
    ${CMAKE_CURRENT_SOURCE_DIR}/fearrender/fogl_core/deps/frozen/include
    ${CMAKE_CURRENT_SOURCE_DIR}/fearrender/fogl_core/deps/glm
    ${CMAKE_CURRENT_SOURCE_DIR}/fearrender/fogl_core/deps/shaderc/libshaderc/include
    ${CMAKE_CURRENT_SOURCE_DIR}/fearrender/fogl_core/deps/spirv-cross
)

if(spirv_cross_SOURCE_DIR)
    target_include_directories(fearrender PRIVATE ${spirv_cross_SOURCE_DIR})
endif()

if(shaderc_SOURCE_DIR)
    target_include_directories(fearrender PRIVATE ${shaderc_SOURCE_DIR}/libshaderc/include)
endif()

find_library(log-lib log)
find_library(android-lib android)
find_library(egl-lib EGL)
find_library(glesv3-lib GLESv3)

target_link_libraries(fearrender PRIVATE
    ${log-lib}
    ${android-lib}
    ${egl-lib}
    ${glesv3-lib}
    dl
    shaderc
    spirv-cross-core
    spirv-cross-glsl
)

# FearXextream Engine Native Library
add_library(fearxextream SHARED
    fearxextream/fear_xextream_core.cpp
    fearxextream/fear_xextream_shader_transpiler.cpp
    fearxextream/fear_xextream_state_tracker.cpp
    fearxextream/fear_xextream_texture_translator.cpp
    fearxextream/fear_xextream_mali_fixer.cpp
)

set_target_properties(fearxextream PROPERTIES
    OUTPUT_NAME "FearXextream"
    CXX_VISIBILITY_PRESET default
    C_VISIBILITY_PRESET default
)

target_include_directories(fearxextream PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/fearxextream
)

target_link_libraries(fearxextream PRIVATE
    ${log-lib}
    ${android-lib}
    ${egl-lib}
    ${glesv3-lib}
    dl
)
