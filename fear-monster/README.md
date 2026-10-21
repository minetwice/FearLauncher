# Fear Monster engine

The engine that renders Minecraft straight to Vulkan, with no OpenGL translation
layer in the way. It is delivered as a launcher function, not a mod: the launcher
picks the build that matches the Minecraft version being started and injects it, so
nobody matches versions by hand or even needs to know the engine exists.

## Why it is version-specific

The engine hooks Minecraft's own classes, and those change between Minecraft
versions. One build cannot serve every version - this is how the engine is made, not
a limitation of the packaging. That is exactly why it belongs in the launcher: the
launcher knows the version, so it can pick the right build itself.

## What exists today

Everything below is in the tree and green on CI. None of it is reachable yet, which
is deliberate - see "Activation" below.

| Piece | Where | State |
|---|---|---|
| Settings switch, default on | `pref_video.xml`, `LauncherPreferences` | device-verified |
| Decision + injection point | `engine/FearMonster.java` | in, gated |
| Engine built from source | `.github/workflows/fear-monster-engine.yml` | builds, artifact uploaded |
| Direct-Vulkan render case | `JREUtils.loadGraphicsLibrary` | in, unreachable |
| Direct-Vulkan env profile | `JREUtils.setEnviroimentForGame` | in, unreachable |
| Vulkan loader shim | `jni/fear_monster_vk.c`, CMake `fearmonster_vk` | built, never loaded |
| No-GL render spec | `JREUtils` fear_monster case returns the loader directly | in |
| Window accessor | `jni/ctxbridges/osm_bridge.c` -> `nativeFearMonsterWindow` | in, uncalled |

## Activation: what is still missing

Activating the path means registering a renderer id and wiring the pieces together.
Doing that today would crash on selection, for two concrete reasons:

1. **LWJGL is still pointed at a GL library.** `GameRunner` sets
   `-Dorg.lwjgl.opengl.libname` from whatever `loadGraphicsLibrary` returned. On this
   path that is the Vulkan loader, so LWJGL would try to open OpenGL from it and
   fail. The Vulkan path must not ask LWJGL for OpenGL at all.
2. **The window never reaches the engine.** `nativeFearMonsterWindow` exists but
   nothing calls it, and the engine needs that window to build a `VkSurfaceKHR`.

So activation is three edits, in this order:

1. Skip the `org.lwjgl.opengl.libname` argument on this path.
2. Call `nativeFearMonsterWindow()` and hand the result to the engine at the point
   where it asks for a window.
3. Register the renderer id and let `FearMonster.shouldInject` see it, so the engine
   jar is injected.

Then the first real device test: does the device expose a Vulkan device, and does the
engine produce a frame.

## Overlay

Files under `overlay/` replace the same path inside the upstream checkout. Every
overlay file must say what it changes and why, so the diff against upstream stays
reviewable. Currently the overlay carries no source changes - it exists so the first
one has somewhere to go.

## Upstream

Upstream: https://github.com/xCollateral/VulkanMod (branch `dev`).
Pin the exact commit in the workflow before shipping anything built here.

## What is deliberately untouched

Nothing here changes how Zink, Quasar, GLES2 or LTW behave. The engine is a separate
path; with it off, or on a Minecraft version with no build, the session runs on the
renderer exactly as it does today. Every piece above is either unreachable (no
renderer id selects it) or inert (nothing loads or calls it).
