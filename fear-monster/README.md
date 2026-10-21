# Fear Monster engine

The engine that renders Minecraft straight to Vulkan, with no OpenGL translation
layer in the way. It is delivered as a launcher function, not a mod: the launcher
picks the build that matches the Minecraft version being started and injects it, so
nobody matches versions by hand or even needs to know the engine exists.

## Why it is version-specific

The engine hooks Minecraft's own classes, and those change between Minecraft
versions. One build cannot serve every version - this is how the engine is made,
not a limitation of the packaging. That is exactly why it belongs in the launcher:
the launcher knows the version, so it can pick the right build itself.

## How it is built

`.github/workflows/fear-monster-engine.yml` clones the upstream engine at a pinned
commit, applies everything under `overlay/` on top, builds it with Gradle, and
uploads the resulting jar as a build artifact. Nothing in this workflow is wired
into the app build, so a failure here cannot break the launcher release - it only
tells us whether the engine builds.

## Upstream

Upstream: https://github.com/xCollateral/VulkanMod (branch `dev`).
Pin the exact commit in the workflow before shipping anything built here.

## Overlay

Files under `overlay/` replace the same path inside the upstream checkout. Every
overlay file must say what it changes and why, so the diff against upstream stays
reviewable. Currently the overlay carries no source changes - it exists so the
first one has somewhere to go.

## What is deliberately untouched

Nothing here changes how Zink, Quasar, GLES2 or LTW behave. The engine is a
separate path; with it off, or on a Minecraft version with no build, the session
runs on the renderer exactly as it does today.
