#!/bin/bash
set -e

# ==============================================================================
# "MH DRIVE" AUTOMATED DEPLOYMENT & COMPILATION SCRIPT
# ==============================================================================

echo "[MH DRIVE] Initializing build workspace..."

# 1. Setup Meson cross-build configuration for Android NDK
meson setup jni/build jni/ \
  --buildtype=release \
  -Dstrip=true \
  --default-library=shared \
  -Doptimization=3

# 2. Compile shared libraries using Meson compile
echo "[MH DRIVE] Executing native compilation engine..."
meson compile -C jni/build

# 3. Extract compiled library fragments and package natively
echo "[MH DRIVE] Arranging compiled dynamic binary libraries..."
mkdir -p app_pojavlauncher/src/main/jniLibs/arm64-v8a
cp jni/build/libmh_drive_gl_wrapper.so app_pojavlauncher/src/main/jniLibs/arm64-v8a/
cp jni/build/libmh_drive_vulkan_mesa.so app_pojavlauncher/src/main/jniLibs/arm64-v8a/

echo "[MH DRIVE] Execution phase completed cleanly and packed beautifully."
