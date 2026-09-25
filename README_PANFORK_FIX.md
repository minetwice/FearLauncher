# Panfork Black Screen Fix

## Issue Description
The Minecraft Java launcher for Android was experiencing a **black screen issue** when using the **Panfork renderer**. The game would launch, but the title screen would not render, and only a black screen would appear. However, the title screen music would play in the background, and random clicks would register as button presses.

---

## Root Cause
The issue was caused by **Panfork's failure to initialize a valid OpenGL context** for rendering. The error `65538: Cannot query extension without a current OpenGL or OpenGL ES context` in the logs confirmed this.

---

## Solution
To fix this issue, the following changes were implemented:

### 1. **Added PanforkManager**
- A new class (`PanforkManager.java`) was created to handle Panfork renderer initialization and GPU-specific workarounds.
- It detects the GPU model and applies necessary environment variables for Panfork.
- It also checks if Panfork is available and usable for the current GPU.

### 2. **Added PanforkWorkarounds**
- A new class (`PanforkWorkarounds.java`) was created to provide GPU-specific workarounds for Panfork.
- It includes workarounds for Mali GPUs, such as the **Mali-G615**, which may not fully support Panfork.

### 3. **Added PanforkGLSurface**
- A new class (`PanforkGLSurface.java`) was created to handle Panfork-specific surface creation and OpenGL context validation.
- It validates the OpenGL context creation process and logs errors for debugging.

### 4. **Added PanforkRendererWrapper**
- A new class (`PanforkRendererWrapper.java`) was created to provide a unified interface for Panfork renderer initialization and fallback handling.
- It attempts to initialize Panfork and falls back to **Zink** if Panfork fails.

### 5. **Updated MinecraftGLSurface**
- The `MinecraftGLSurface.java` file was updated to integrate the Panfork renderer with fallback support.
- It now checks if Panfork is enabled and initialized, and falls back to Zink if Panfork fails.

### 6. **Updated PojavApplication**
- The `PojavApplication.java` file was updated to initialize the `PanforkManager` early in the application lifecycle.

---

## Changes Summary

### New Files
- `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/gpu/PanforkManager.java`
- `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/gpu/PanforkWorkarounds.java`
- `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/gpu/PanforkGLSurface.java`
- `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/gpu/PanforkRendererWrapper.java`

### Modified Files
- `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/MinecraftGLSurface.java`
- `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/PojavApplication.java`

---

## How It Works
1. **Initialization**: The `PanforkManager` is initialized early in the application lifecycle (`PojavApplication.onCreate()`).
2. **GPU Detection**: The `PanforkManager` detects the GPU model and applies necessary environment variables for Panfork.
3. **Renderer Selection**: The `PanforkRendererWrapper` attempts to initialize Panfork and falls back to Zink if Panfork fails.
4. **Surface Creation**: The `MinecraftGLSurface` uses the appropriate renderer based on the initialization result.
5. **Fallback Handling**: If Panfork fails to initialize, the launcher falls back to Zink, ensuring the game still launches and renders correctly.

---

## Testing
To test this fix:
1. Build the launcher with the changes in the `panfork-blackscreen-fix` branch.
2. Run the launcher on a device with **ARM Mali-G615 MC2** GPU.
3. Select the **Panfork renderer** in the launcher settings.
4. Launch the game and verify that the title screen renders correctly.

If Panfork fails to initialize, the launcher will automatically fall back to Zink, and the game should still launch and render correctly.

---

## Expected Outcome
- The game launches and the title screen renders correctly when using the Panfork renderer.
- If Panfork fails to initialize, the launcher falls back to Zink, ensuring the game still works.
- Logs provide clear debugging information if issues persist.

---

## Notes
- This fix ensures compatibility with **Mali GPUs** (e.g., Mali-G615) that may not fully support Panfork.
- The fallback mechanism ensures that the game still launches even if Panfork fails, providing a better user experience.
- The changes are backward-compatible and do not affect other renderers (e.g., Zink, Krypton).
