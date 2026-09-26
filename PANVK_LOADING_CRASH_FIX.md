# PanVK loading-screen crash fix

Branch: `fix/panvk-loading-crash` (copy of `Mega-checkpoint`)

## Root cause
`GameRunner` set LWJGL OpenGL lib to `libGL.so` for `panvk_zink`.
Turnip/Zink already use `libmh_drive_vulkan_mesa.so`. PanVK did not, so the first real GL calls on the Mojang loading/atlas screen hit the wrong GL implementation and crashed/froze.

## Changes
1. `GameRunner.java`: include `panvk_zink` in `-Dorg.lwjgl.opengl.libname=libmh_drive_vulkan_mesa.so`.
2. `JREUtils.java`: `setUseTurnip(false)` on PanVK so Turnip does not steal `libmjlvlk.so`.
3. `JREUtils.java`: do not `preloadVulkan()` (Turnip preload) for `panvk_zink`.
4. Point `VK_DRIVER_FILES` at bundled `libvulkan_panfrost.so` when present.

## How to test
```
git fetch origin
git checkout fix/panvk-loading-crash
./gradlew :app_pojavlauncher:assembleDebug
```
Use renderer `panvk_zink`, Iris shaders off (already forced), `mipmapLevels=0`.
