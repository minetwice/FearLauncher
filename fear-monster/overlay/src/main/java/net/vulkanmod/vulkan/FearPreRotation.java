package net.vulkanmod.vulkan;

import org.joml.Matrix4f;

/**
 * FEAR: Vulkan pre-rotation, off by default.
 *
 * <p>Off because the extent is now the window's size rather than the surface's, and with that
 * in place the image already arrives upright - the compositor's turn and the window's shape
 * agree. Rotating the projection on top of that would be a second turn.
 *
 * <p>fear_prerotate.txt in the game dir turns it on: 90, 180 or 270. The launcher's pointer
 * reads the same file, so the two cannot disagree.
 */
public final class FearPreRotation {
    private FearPreRotation() {}

    private static volatile int sAngle = -1;

    private static int angle() {
        int a = sAngle;
        if (a >= 0) return a;
        int v = 0;
        try {
            String dir = System.getenv("FEAR_GAME_DIR");
            if (dir != null && !dir.isEmpty()) {
                java.io.File f = new java.io.File(dir, "fear_prerotate.txt");
                if (f.isFile()) {
                    String s = new String(java.nio.file.Files.readAllBytes(f.toPath()),
                            java.nio.charset.StandardCharsets.UTF_8).trim();
                    if ("0".equals(s)) v = 0;
                    else if ("90".equals(s)) v = 90;
                    else if ("180".equals(s)) v = 180;
                    else if ("270".equals(s)) v = 270;
                }
            }
        } catch (Throwable ignored) {}
        sAngle = v;
        return v;
    }

    public static boolean enabled() { return angle() != 0; }

    public static Matrix4f apply(Matrix4f proj) {
        int a = angle();
        if (a == 0 || proj == null) return proj;
        try {
            Matrix4f r = new Matrix4f().identity();
            r.rotateZ((float) Math.toRadians(a));
            return r.mul(proj, new Matrix4f());
        } catch (Throwable t) {
            return proj;
        }
    }
}
