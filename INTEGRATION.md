# fear_mali_compat — FearLauncher integration

This bundle wires the `VkLayer_fear_mali_compat` Vulkan layer into FearLauncher
so the `vulkan_zink` (Fear Render) renderer can initialise on ARM Mali GPUs,
where the vendor Vulkan driver is missing the features Zink needs
(`logicOp`, `fillModeNonSolid`, `shaderClipDistance`, `alphaToOne`).

## What's in here

```
app_pojavlauncher/src/main/jni/mali_compat/fear_mali_compat.c        (new)
app_pojavlauncher/src/main/jni/mali_compat/VkLayer_fear_mali_compat.json (new)
app_pojavlauncher/src/main/jni/CMakeLists.txt                       (edited)
app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/JREUtils.java (edited)
mali-compat-integration.patch                                       (both edits as a unified diff)
```

## Apply

Either copy the files in place, or apply the patch from the repo root:

```sh
git apply mali-compat-integration.patch
```

## How it works

1. **Build** — `CMakeLists.txt` gains a `VkLayer_fear_mali_compat` shared-library
   target. Gradle builds it with the app's NDK toolchain and packages
   `libVkLayer_fear_mali_compat.so` into `lib/arm64-v8a/`.

2. **Enable** — in `JREUtils.setupRendererEnv()`, when the `vulkan_zink` renderer
   is selected and the GPU is ARM Mali (`GLInfoUtils.getGlInfo().isArm()`), the
   new `setupMaliCompatLayer()` helper:
   - writes `VkLayer_fear_mali_compat.json` into a writable dir
     (`<game home>/vk_layers/`) with an absolute `library_path` pointing at the
     `.so` in the native library directory;
   - sets `FEAR_MALI_COMPAT=1`, `VK_LAYER_PATH`, `VK_INSTANCE_LAYERS` and
     `VK_LOADER_LAYERS_ENABLE`.

3. **Run** — Zink queries device features, the layer reports the missing ones as
   available, and strips them again before `vkCreateDevice` reaches the Mali
   driver. Device creation succeeds and the incorrect-rendering class caused by
   missing features goes away.

## Tuning

`FEAR_MALI_COMPAT_FEATURES` (comma list, default all): `fillModeNonSolid`,
`logicOp`, `shaderClipDistance`, `alphaToOne`, or `none`. Can be set per-user in
`custom_env.txt`. If a device behaves worse, start with `fillModeNonSolid`.

## Verify

```
adb logcat -s FearMaliCompat
```

Expect `instance created, compat layer active`,
`reporting <feat> = VK_TRUE (driver lacks it)` and
`device created with stripped feature mask ...`.

## Caveats

- Test on the target device. Android's Vulkan loader can be strict about
  app-shipped layers; if `VK_LAYER_PATH` / `VK_INSTANCE_LAYERS` are ignored on
  your Android version, the layer will not activate and Zink falls back to its
  current (glitchy) behaviour. The `FearMaliCompat` logcat tag tells you which
  case you are in.
- This fixes device-creation / feature-gap problems. It does not make the Mali
  vendor driver a conformant OpenGL 4.6 stack.

License: MIT.
