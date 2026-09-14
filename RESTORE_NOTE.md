# Restore Note

PanVK completely removed.

Original working Mesa Zink (turnip_zink) setup restored from commit 0ee5223 and clean versions.

Key files:
- JREUtils.java (full, no PanVK)
- GameRunner.java (full, no PanVK)
- lwjgl_dlopen_hook.c (v2.12 TURNIP-ZINK full 615 lines)

Build should now use only turnip_zink / vulkan_zink / opengles.

Texture bugs to be fixed later as discussed.
