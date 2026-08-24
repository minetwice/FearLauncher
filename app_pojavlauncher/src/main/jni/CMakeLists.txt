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
# FearRender submodule already provides glslang + SPIRV-Cross via FetchContent.
# We just compile the C API wrapper (spirv_cross_c.cpp) directly and link
# against FearRender's existing spirv-cross-glsl target.

if(TARGET glslang AND TARGET spirv-cross-glsl)
    # Find SPIRV-Cross headers from FearRender's FetchContent
    get_target_property(SPIRV_CROSS_SRC_DIR spirv-cross-glsl SOURCE_DIR)
    set(SPIRV_CROSS_INCLUDE_DIR "${SPIRV_CROSS_SRC_DIR}")

    # Find glslang headers
    get_target_property(GLSLANG_SRC_DIR glslang SOURCE_DIR)
    set(GLSLANG_INCLUDE_DIR "${GLSLANG_SRC_DIR}")

    add_library(quasar_shader SHARED
        quasar/quasar_shader_bridge.cpp
    )
    target_include_directories(quasar_shader PRIVATE
        ${GLSLANG_INCLUDE_DIR}
        ${SPIRV_CROSS_INCLUDE_DIR}
    )
    target_link_libraries(quasar_shader PRIVATE
        glslang
        SPIRV
        glslang-default-resource-limits
        spirv-cross-glsl
        log
    )
    target_compile_definitions(quasar_shader PRIVATE SPIRV_CROSS_C_API_GLSL=1)
    message(STATUS "Quasar shader transpilation library: ENABLED (using FearRender glslang+SPIRV-Cross)")
else()
    message(WARNING "Quasar shader transpilation: DISABLED (glslang or spirv-cross-glsl not found)")
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
