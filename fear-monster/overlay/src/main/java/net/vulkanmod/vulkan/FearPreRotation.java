package net.vulkanmod.vulkan;

import org.joml.Matrix4f;

/**
 * FEAR: Vulkan pre-rotation.
 *
 * <p>preTransform is back at currentTransform and the swapchain is at the surface's identity
 * size, which together mean the compositor no longer turns the image. The turning happens here
 * instead, on the projection, which is the third of the three things Google's pre-rotation
 * guidance asks for and the one VulkanMod never did.
 *
 * <p>This is what makes fear_monster behave like the GL renderers: the game draws in its own
 * landscape coordinates, the result is presented upright, and nothing on the pointer side has
 * to be turned by hand. No camera axes to swap, no fear_input.txt, no fear_move.txt.
 *
 * <p>fear_prerotate.txt in the game dir still overrides the angle (0/90/180/270) so it can be
 * checked on the device without a rebuild; 0 turns the whole thing off again.
 */
public final class FearPreRotation {
    private FearPreRotation() {}

    private static volatile int sAngle = -1;

    /** 0/90/180/270 from fear_prerotate.txt. Default 90 - the panel is landscape. */
    private static int angle() {
        int a = sAngle;
        if (a >= 0) return a;
        int v = 90;
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

    /**
     * Turn the projection so the drawn image lands upright on a panel the compositor has
     * already turned. Applied after the game's own matrix, so it rotates the finished result
     * rather than the camera.
     */
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
