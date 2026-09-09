# Indus2.0 — Epic FPS Booster Engine

A fork of FearLauncher (PojavLauncher-based Minecraft Java Edition launcher for Android) with aggressive performance optimizations targeting **200+ FPS** gameplay.

## What Changed

### 1. Project Renamed to Indus2.0
- `settings.gradle` → `rootProject.name='Indus2.0'`
- Renderer display name → `Indus2.0 TurboV1 - Epic FPS Booster Engine`
- All log tags updated to `Indus2.0`

### 2. Smart CPU/GPU Resource Management (`turbo_v1_perf.h/cpp`)
New native performance governor that manages hardware resources for maximum FPS:

- **CPU core pinning**: Automatically detects big cores on big.LITTLE SoCs and pins the render thread to the fastest core with `SCHED_FIFO` real-time scheduling
- **GPU frequency boost**: Activates Adreno (KGSL) and Mali GPU performance governors via sysfs — forces max frequency, disables bus splitting, forces clock/bus on
- **Adaptive resolution scaling**: Monitors real-time FPS and dynamically lowers render resolution (down to 50%) when FPS drops below the minimum threshold, restoring it when FPS recovers
- **Frame skip logic**: When GPU is overcommitted, intelligently skips alternate frames to reduce draw call pressure while maintaining input responsiveness
- **Frame pacing**: Precise frame timing using `CLOCK_MONOTONIC` nanosecond timestamps with exponential moving average FPS calculation

### 3. Entity Batch Renderer (`turbo_v1_entity.h/cpp`)
New entity rendering system that dramatically reduces draw calls when many entities are on screen:

- **Frustum culling**: Extracts 6 frustum planes from the projection-view matrix and culls entities whose bounding boxes are outside the view
- **Instanced draw batching**: Groups entities by texture and submits batched `glDrawElementsInstanced` calls instead of individual draws
- **Occlusion-aware**: Tracks visible/culled/batched entity counts per frame
- **Dynamic instance buffer**: Grows automatically as entity count increases

### 4. Enhanced Shader Transpiler (`turbo_v1_shader_transpiler.cpp`)
More aggressive GLSL optimization for mobile GPUs:

- **Optimized precision hints**: `mediump` for sampler2D and sampler3D (big perf win on Mali/Adreno), `highp` only where needed
- **Dead code stripping**: Removes comment-only lines to reduce compilation time and instruction count
- **Double normalize elimination**: Detects and simplifies `normalize(normalize(x))` → `normalize(x)` (common in fog/lighting)
- **Extended safe math guards**: Added `sqrt`, `asin`, `acos`, `atan` clamping to prevent NaN/Inf crashes
- **Early Z layout hints**: Added `layout(early_fragment_tests)` optimization hint
- **Larger shader cache**: Cache size increased from 4096 to 8192 entries for complex shader packs

### 5. Download Speed (`Downloader.java`)
- Thread pool increased from `max(8, cores*2)` to `max(16, cores*4)` — 2x more parallel downloads
- Buffer size increased from 64KB to 256KB — 4x larger I/O buffer
- Read timeout increased from 15s to 30s for large files
- Added `Accept-Encoding: identity` and `Cache-Control: no-cache` headers

### 6. Launching Speed (`GameRunner.java`)
- **Pre-launch performance optimization**: `IndusPerformanceManager.applyPerformanceOptimizations()` runs before JVM start — activates GPU/CPU boost, trims caches, runs GC
- **Optimized JVM arguments**: G1GC with gaming-tuned settings (50ms max GC pause, 20% new size, string deduplication, tiered compilation, large pages)
- **World directory optimization**: Pre-creates save/region directories before launch

### 7. World Creation/Load Speed (`IndusWorldOptimizer.java`)
- **Region cache pre-warming**: Reads the first 8KB of all `.mca` region files in parallel before launch so they're in the page cache
- **World gen options optimization**: Sets `maxFps:260` and `renderClouds:false` for faster world generation
- **Directory pre-allocation**: Creates `saves/`, `region_cache/`, `data/` directories upfront

### 8. Build Optimizations (`CMakeLists.txt`)
- Added `-O3 -ffast-math -funroll-loops -fomit-frame-pointer` optimization flags
- Added new source files to the build

## New Files

| File | Description |
|------|-------------|
| `jni/turbov1/turbo_v1_perf.h` | Performance governor header |
| `jni/turbov1/turbo_v1_perf.cpp` | Performance governor implementation |
| `jni/turbov1/turbo_v1_entity.h` | Entity batch renderer header |
| `jni/turbov1/turbo_v1_entity.cpp` | Entity batch renderer implementation |
| `app_pojavlauncher/.../IndusPerformanceManager.java` | Java-side CPU/GPU management + JVM tuning |
| `app_pojavlauncher/.../IndusWorldOptimizer.java` | World load optimization |

## Modified Files

| File | Changes |
|------|---------|
| `settings.gradle` | Project name → Indus2.0 |
| `app_pojavlauncher/.../headings_array.xml` | Renderer display name |
| `jni/CMakeLists.txt` | New sources + optimization flags |
| `jni/turbov1/turbo_v1_core.cpp` | Integrated perf + entity init, new JNI entry points |
| `jni/turbov1/turbo_v1_hooks.cpp` | Added perf + entity hook exports |
| `jni/turbov1/turbo_v1_shader_transpiler.cpp` | Aggressive shader optimizations |
| `app_pojavlauncher/.../JREUtils.java` | New JNI declarations, env var consolidation |
| `app_pojavlauncher/.../GameRunner.java` | Pre-launch optimization, optimized JVM args |
| `app_pojavlauncher/.../Downloader.java` | Faster parallel downloads |

## How to Build

This is an Android project. Build with Gradle:
```
./gradlew :app_pojavlauncher:assembleFullDebug
```

The native TurboV1 library is built via CMake (configured in `jni/CMakeLists.txt`).
