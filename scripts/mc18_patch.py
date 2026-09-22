#!/usr/bin/env python3
"""MC18: patch Mesa 25.1.4 zink for Mali GPUs (FearLauncher core glitch fix).

Run from inside the mesa/ source tree (after the Vera-Firefly mesa-build-fix
overlay, which is where the VULKAN_PTR zink_screen.c comes from).

1. zink_screen.c: enable the `inconsistent_interpolation` driver workaround for
   ARM_PROPRIETARY (and PanVK) — Mali GPUs interpolate varyings inconsistently
   between the two triangles of a quad, which makes texture coordinates
   shift/slide across block surfaces in Minecraft.
2. 00-mesa-defaults.conf: add a "Minecraft (Java)" driconf profile with
   vs_position_always_invariant / zero_invalidated_buffers /
   glsl_correct_derivatives_after_discard (fixes block texture shifting and
   flickering on Mali hardware).
"""
import io
import os
import sys


def die(msg):
    sys.stderr.write("MC18 FATAL: " + msg + "\n")
    sys.exit(1)


# ---------- Patch 1: zink_screen.c ----------
p = "src/gallium/drivers/zink/zink_screen.c"
if not os.path.isfile(p):
    die(p + " not found")

t = io.open(p, encoding="utf-8").read()

OLD = """      /* this has bad perf on AMD */
      screen->info.have_KHR_push_descriptor = false;
      /* Interpolation is not consistent between two triangles of a rectangle. */
      screen->driver_workarounds.inconsistent_interpolation = true;
      break;
   default:
      break;
   }
"""
NEW = """      /* this has bad perf on AMD */
      screen->info.have_KHR_push_descriptor = false;
      /* Interpolation is not consistent between two triangles of a rectangle. */
      screen->driver_workarounds.inconsistent_interpolation = true;
      break;
   case VK_DRIVER_ID_INTEL_OPEN_SOURCE_MESA:
   case VK_DRIVER_ID_MESA_TURNIP:
   case VK_DRIVER_ID_ARM_PROPRIETARY:
   case VK_DRIVER_ID_MESA_PANVK:
      /* MC18: Mali GPUs (proprietary and PanVK) interpolate varyings
       * inconsistently between the two triangles of a quad, which makes
       * texture coordinates shift/slide across block surfaces. */
      screen->driver_workarounds.inconsistent_interpolation = true;
      break;
   default:
      break;
   }
"""

if "MC18: Mali GPUs" in t:
    print("MC18: zink_screen.c already patched, skipping")
elif t.count(OLD) == 1:
    t = t.replace(OLD, NEW)
    io.open(p, "w", encoding="utf-8", newline="").write(t)
    print("MC18: zink_screen.c patched (inconsistent_interpolation for Mali)")
else:
    die("inconsistent_interpolation anchor found %d times (expected 1)" % t.count(OLD))

# ---------- Patch 2: 00-mesa-defaults.conf ----------
p2 = "src/util/00-mesa-defaults.conf"
if not os.path.isfile(p2):
    die(p2 + " not found")

t2 = io.open(p2, encoding="utf-8").read()

BLOCK = """
        <!-- MC18: Minecraft (Java) on Mali GPUs - fixes block texture shifting/flickering -->
        <application name="Minecraft (Java)" executable_regexp="java.*">
            <option name="vs_position_always_invariant" value="true" />
            <option name="zero_invalidated_buffers" value="true" />
            <option name="glsl_correct_derivatives_after_discard" value="true" />
        </application>
"""

if "MC18: Minecraft" in t2:
    print("MC18: 00-mesa-defaults.conf already patched, skipping")
else:
    inserted = False
    for anchor in (
        "        <!-- Vulkan workarounds: -->",
        "    </device>",
        "</device>",
        "</driinfo>",
    ):
        if anchor in t2:
            t2 = t2.replace(anchor, BLOCK + "\n" + anchor, 1)
            inserted = True
            print("MC18: 00-mesa-defaults.conf patched (anchor: %r)" % anchor[:30])
            break
    if not inserted:
        die("no usable anchor found in 00-mesa-defaults.conf")
    io.open(p2, "w", encoding="utf-8", newline="").write(t2)

print("MC18: all patches applied OK")
