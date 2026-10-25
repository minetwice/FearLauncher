package net.vulkanmod.vulkan;

/**
 * FEAR: master log, part 2 - Minecraft's own widgets, which nothing on the launcher side can
 * see.
 *
 * <p>The title screen buttons are not Android views. They are drawn by the game, inside the
 * engine, so the only place that can enumerate them is in here.
 *
 * <p>Everything is read by reflection. The first attempt at this named Minecraft's fields and
 * methods directly and the engine build went red; the second attempt wrapped the wrong method
 * and went red for a different reason. Both were compile errors in this file, and both were
 * avoidable by reading the log first.
 *
 * <p>Every checked exception the reflection and the file write can throw is caught inside
 * dump() itself, which is where the compiler requires it - a catch in the caller does not
 * count for a checked exception.
 */
public final class FearUiLog {
    private static int tick = 0;
    private static int failures = 0;

    private FearUiLog() {}

    /** Called once per frame from Renderer.beginFrame. Does its work once a second. */
    public static void tick() {
        try {
            if ((tick++ % 60) != 0) return;
            if (failures > 8) return;
            dump();
        } catch (Throwable t) {
            failures++;
        }
    }

    private static void dump() {
        try {
            String dir = System.getenv("FEAR_GAME_DIR");
            if (dir == null || dir.isEmpty()) return;

            Class<?> mcClass = Class.forName("net.minecraft.client.MinecraftClient");
            Object mc = mcClass.getMethod("getInstance").invoke(null);
            if (mc == null) return;

            Object screen = get(mc, "getCurrentScreen", "getScreen");
            if (screen == null) screen = field(mc, "currentScreen", "screen");
            if (screen == null) return;

            Object win = get(mc, "getWindow");
            Object kids = get(screen, "children", "getChildren");

            StringBuilder b = new StringBuilder();
            b.append("=== FearMC === screen=").append(screen.getClass().getSimpleName());
            b.append(" gui=").append(get(win, "getScaleFactor", "getGuiScale"));
            b.append(" scaled=").append(get(win, "getScaledWidth", "getWidth"));
            b.append("x").append(get(win, "getScaledHeight", "getHeight"));
            b.append(" framebuffer=").append(get(win, "getFramebufferWidth"));
            b.append("x").append(get(win, "getFramebufferHeight"));
            b.append("\n");

            if (kids instanceof Iterable) {
                for (Object w : (Iterable<?>) kids) {
                    if (w == null) continue;
                    Object x = get(w, "getX");
                    Object y = get(w, "getY");
                    Object ww = get(w, "getWidth");
                    Object hh = get(w, "getHeight");
                    Object msg = get(w, "getMessage");
                    String text = "";
                    if (msg != null) {
                        Object s = get(msg, "getString");
                        if (s != null) text = s.toString();
                    }
                    b.append("[FearMC] ").append(w.getClass().getSimpleName())
                            .append(" \"").append(text).append("\" (")
                            .append(x).append(",").append(y).append(")-(")
                            .append(sum(x, ww)).append(",").append(sum(y, hh))
                            .append(") active=").append(get(w, "isActive", "active"))
                            .append("\n");
                }
            }

            b.append("================\n");
            java.nio.file.Files.write(java.nio.file.Paths.get(dir, "fear_mc.txt"),
                    b.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Throwable t) {
            failures++;
        }
    }

    /** x + w, as an int, or 0 when either is missing. */
    private static Object sum(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            return ((Number) a).intValue() + ((Number) b).intValue();
        }
        return "?";
    }

    /** The first of these no-argument methods that exists, or null. */
    private static Object get(Object target, String... names) {
        if (target == null) return null;
        for (String n : names) {
            try {
                java.lang.reflect.Method m = target.getClass().getMethod(n);
                m.setAccessible(true);
                return m.invoke(target);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /** The first of these public fields that exists, or null. */
    private static Object field(Object target, String... names) {
        if (target == null) return null;
        for (String n : names) {
            try {
                java.lang.reflect.Field f = target.getClass().getField(n);
                f.setAccessible(true);
                return f.get(target);
            } catch (Throwable ignored) {}
        }
        return null;
    }
}
