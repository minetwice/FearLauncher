cmake_minimum_required(VERSION 3.22.1)

project("FearLauncher")

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

# Quasar Helper Library for Mali GPU shader fixes
add_library(quasar SHARED
        quasar_helper.c
)

target_include_directories(quasar PRIVATE .)
target_link_libraries(quasar PRIVATE log GLESv3)

# fear_mali_compat - Vulkan layer that makes Mesa's Zink work on ARM Mali GPUs.
# The layer reports the Zink-required features the Mali vendor driver is missing
# and strips them again before device creation. See mali_compat/README.md.
add_library(VkLayer_fear_mali_compat SHARED
        mali_compat/fear_mali_compat.c
)
target_include_directories(VkLayer_fear_mali_compat PRIVATE .)
target_compile_definitions(VkLayer_fear_mali_compat PRIVATE VK_NO_PROTOTYPES)
target_link_libraries(VkLayer_fear_mali_compat PRIVATE log)
set_target_properties(VkLayer_fear_mali_compat PROPERTIES
        C_VISIBILITY_PRESET hidden
        PREFIX "lib"
)
