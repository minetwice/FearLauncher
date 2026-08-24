cmake_minimum_required(VERSION 3.22.1)

project("FearLauncher")

add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../../../jni ${CMAKE_CURRENT_BINARY_DIR}/fearrender_build)

add_library(pojavexec SHARED
 affinity.c
 jvm_hooks/emui_iterator_fix_hook.c
 jvm_hooks/java_exec_hooks.c
 jvm_hooks/lwjgl_dlopen_hook.c
 jre_launcher/jre_launcher.c
 jre_launcher/native_library_hook.c
 jre_launcher/elf_hinter.c
 jre_launcher/abort_wait.c
 driver_helper/nsbypass.c
 driver_helper/fake_dlfcn.c
 driver_helper/func_locator.c
 utils.c
 stdio_is.c
 minibridge.c
 vulkan_loader.c

)

target_include_directories(pojavexec PRIVATE .)
target_link_libraries(pojavexec PRIVATE log)
if(CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
 target_sources(pojavexec PRIVATE driver_helper/arm64_func_locator.c)
 target_compile_definitions(pojavexec PRIVATE USE_ARM64_LOCATOR)
 target_compile_definitions(pojavexec PRIVATE ENABLE_TURNIP_LOADER)

 add_library(linkerhook SHARED
 driver_helper/hook.c
 )
 target_link_options(linkerhook PRIVATE "-Wl,-z,global")
endif()

# Quasar native library - Vulkan capability probe
add_library(quasar_probe SHARED
 quasar/quasar_capability_probe.c
)
target_include_directories(quasar_probe PRIVATE .)
target_link_libraries(quasar_probe PRIVATE log dl)

# ===== Quasar Shader Transpilation Library =====
# Requires glslang and SPIRV-Cross sources in third_party/
# CI clones them automatically; for local builds run:
#   git clone --depth 1 https://github.com/KhronosGroup/glslang.git third_party/glslang
#   git clone --depth 1 https://github.com/KhronosGroup/SPIRV-Cross.git third_party/SPIRV-Cross

set(QUASAR_THIRDPARTY_DIR "${CMAKE_CURRENT_SOURCE_DIR}/third_party")
set(QUASAR_HAS_GLSLANG OFF)
set(QUASAR_HAS_SPIRVCROSS OFF)

if(EXISTS "${QUASAR_THIRDPARTY_DIR}/glslang/CMakeLists.txt")
    set(QUASAR_HAS_GLSLANG ON)
endif()
if(EXISTS "${QUASAR_THIRDPARTY_DIR}/SPIRV-Cross/CMakeLists.txt")
    set(QUASAR_HAS_SPIRVCROSS ON)
endif()

if(QUASAR_HAS_GLSLANG AND QUASAR_HAS_SPIRVCROSS)

    # --- glslang: build as static library, NO SPIRV-Tools dependency ---
    set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
    set(ENABLE_SPIRV ON CACHE BOOL "" FORCE)
    set(ENABLE_OPT OFF CACHE BOOL "" FORCE)
    set(ENABLE_HLSL OFF CACHE BOOL "" FORCE)
    set(ENABLE_GLSLANG_BINARIES OFF CACHE BOOL "" FORCE)
    set(GLSLANG_TESTS OFF CACHE BOOL "" FORCE)
    set(GLSLANG_ENABLE_INSTALL OFF CACHE BOOL "" FORCE)
    set(ENABLE_RTTI OFF CACHE BOOL "" FORCE)
    set(ENABLE_EXCEPTIONS OFF CACHE BOOL "" FORCE)
    set(ENABLE_PCH OFF CACHE BOOL "" FORCE)
    set(BUILD_EXTERNAL OFF CACHE BOOL "" FORCE)
    add_subdirectory(${QUASAR_THIRDPARTY_DIR}/glslang ${CMAKE_CURRENT_BINARY_DIR}/glslang_build)

    # --- SPIRV-Cross: build as static library with GLSL backend + C API ---
    set(SPIRV_CROSS_STATIC ON CACHE BOOL "" FORCE)
    set(SPIRV_CROSS_SHARED OFF CACHE BOOL "" FORCE)
    set(SPIRV_CROSS_CLI OFF CACHE BOOL "" FORCE)
    set(SPIRV_CROSS_ENABLE_TESTS OFF CACHE BOOL "" FORCE)
    set(SPIRV_CROSS_ENABLE_GLSL ON CACHE BOOL "" FORCE)
    set(SPIRV_CROSS_ENABLE_HLSL OFF CACHE BOOL "" FORCE)
    set(SPIRV_CROSS_ENABLE_MSL OFF CACHE BOOL "" FORCE)
    set(SPIRV_CROSS_ENABLE_CPP OFF CACHE BOOL "" FORCE)
    set(SPIRV_CROSS_ENABLE_REFLECT OFF CACHE BOOL "" FORCE)
    set(SPIRV_CROSS_ENABLE_C_API ON CACHE BOOL "" FORCE)
    set(SPIRV_CROSS_FORCE_PIC ON CACHE BOOL "" FORCE)
    set(SPIRV_CROSS_SKIP_INSTALL ON CACHE BOOL "" FORCE)
    add_subdirectory(${QUASAR_THIRDPARTY_DIR}/SPIRV-Cross ${CMAKE_CURRENT_BINARY_DIR}/spirvcross_build)

    # --- Quasar shader JNI bridge library ---
    add_library(quasar_shader SHARED
        quasar/quasar_shader_bridge.cpp
    )
    target_include_directories(quasar_shader PRIVATE
        ${QUASAR_THIRDPARTY_DIR}/glslang
        ${QUASAR_THIRDPARTY_DIR}/SPIRV-Cross
    )
    target_link_libraries(quasar_shader PRIVATE
        glslang
        SPIRV
        glslang-default-resource-limits
        spirv-cross-c
        log
    )
    message(STATUS "Quasar shader transpilation library: ENABLED (glslang + SPIRV-Cross)")
else()
    message(WARNING "Quasar shader transpilation: DISABLED (glslang/SPIRV-Cross not found in third_party/)")
endif()

add_library(pojavexec_awt SHARED
 awt_bridge.c
)

add_library(awt_headless SHARED 
 awt_xawt/dummy.c
)

add_library(awt_xawt SHARED
 awt_xawt/xawt_fake.c
)
target_link_libraries(awt_xawt PRIVATE awt_headless)
