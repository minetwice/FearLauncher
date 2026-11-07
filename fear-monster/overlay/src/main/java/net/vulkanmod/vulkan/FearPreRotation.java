package net.vulkanmod.vulkan;

import org.joml.Matrix4f;

/**
 * FEAR: Vulkan pre-rotation, the other half of the IDENTITY change.
 *
 * <p>The engine presents with preTransform at IDENTITY so that the Android compositor does the
 * turning, exactly as it does for the GL renderers. That works, but the game's own coordinates
 * are then a quarter turn away from the panel: the pointer has to be turned by hand, and the
 * camera axes come out swapped.
 *
 * <p>This removes the need for any of that. preTransform goes back to currentTransform, the
 * swapchain goes back to the surface's identity size, and the projection matrix is turned here
 * - which is the third of the three things Google's pre-rotation guidance asks for, and the one
 * VulkanMod was missing.
 *
 * <p>Everything is behind fear_prerotate.txt in the game dir, default OFF, so this cannot break
 * a launch that works today. With the file absent the matrix is returned untouched.
 */
public final class FearPreRotation {
    private FearPreRotation() {}

    private static volatile int sAngle = -1;

    /** 0/90/180/270 from fear_prerotate.txt, or 0 when the file is absent. */
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
                    if ("90".equals(s)) v = 90;
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
