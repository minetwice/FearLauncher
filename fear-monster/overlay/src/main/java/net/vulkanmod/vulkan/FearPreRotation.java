package net.vulkanmod.vulkan;

import org.joml.Matrix4f;

/**
 * FEAR: Vulkan pre-rotation.
 *
 * <p>preTransform is at currentTransform and the swapchain is at the surface's identity size, so
 * the compositor no longer turns the image. The turning happens here instead, on the projection -
 * the third of the three things Google's pre-rotation guidance asks for and the one VulkanMod
 * never did. Two hooks feed it: GameRenderer's world projection and ProjectionMatrix2's GUI one.
 *
 * <p>The default is 180. 90 was the first guess and it came out upside down on the device -
 * landscape right, screen inverted - so the panel's transform is a half turn rather than a
 * quarter. 90 and 270 turn it a quarter the other way, 0 turns the whole thing off.
 *
 * <p>fear_prerotate.txt in the game dir overrides the angle, so it can be checked without a
 * rebuild.
 */
public final class FearPreRotation {
    private FearPreRotation() {}

    private static volatile int sAngle = -1;

    private static int angle() {
        int a = sAngle;
        if (a >= 0) return a;
        int v = 180;
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

    /** Turn the finished projection so the image lands upright on the panel. */
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
