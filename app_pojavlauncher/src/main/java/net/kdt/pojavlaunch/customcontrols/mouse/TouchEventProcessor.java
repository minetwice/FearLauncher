package net.kdt.pojavlaunch.customcontrols.mouse;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import git.artdeell.dnbootstrap.glfw.GLFW;

public abstract class TouchEventProcessor {
    private final View mHostView;
    private boolean fearUiDumped = false;

    public TouchEventProcessor(View hostView) {
        mHostView = hostView;
    }

    /**
     * FEARWIRE-INPUTROT (v10.23): the path a finger actually takes.
     *
     * <p>The engine presents without pre-rotation, so the compositor turns the image a quarter
     * turn, and Minecraft maps the pointer against a window that was never turned. A finger is
     * TOOL_TYPE_FINGER, which falls through MinecraftGLSurface.onTouchEvent's loop, and arrives
     * here instead - so this is where the pointer has to be turned back.
     *
     * <p>The default is 270. Under case 90 the arithmetic is nx = ny, ny = 1 - nx, which is the
     * counter-clockwise turn; the recorded line confirms it - screen (1753,37) becomes
     * (0.0303,0.3216) and that is exactly what was sent. The turn the compositor needs is the
     * clockwise one, which is case 270.
     */
    protected void sendTouchCoordinates(float x, float y) {
        if (!fearUiDumped) {
            fearUiDumped = true;
            fearDumpUi();
        }
        float nx = x / mHostView.getWidth();
        float ny = y / mHostView.getHeight();
        int rot = fearIsMonster() ? fearRot("fear_input.txt", 270) : 0;
        if (rot != 0) {
            float ox = nx, oy = ny;
            switch (rot) {
                case 90:
                    nx = oy;
                    ny = 1f - ox;
                    break;
                case 180:
                    nx = 1f - ox;
                    ny = 1f - oy;
                    break;
                case 270:
                    nx = 1f - oy;
                    ny = ox;
                    break;
                default:
                    break;
            }
        }
        /* Written on every event, not only when the rotation ran. A skipped rotation and a
           missing pointer event both left an empty file, which made two runs impossible to
           tell apart. */
        fearMouseLog(rot, x, y, nx, ny);
        GLFW.cursorX = nx;
        GLFW.cursorY = ny;
        GLFW.sendMousePos();
    }

    /**
     * FEARWIRE-UIDUMP (v10.25): every view on screen, with the box it occupies.
     *
     * <p>Bounds are in screen pixels - the same space fear_mouse.txt records the pointer in -
     * so a click and a box can be lined up directly, with no arithmetic in between. Only the
     * first event of a launch triggers it: the tree does not move while the game runs, and a
     * dump per touch would bury the pointer log it is meant to be read beside.
     */
    private void fearDumpUi() {
        try {
            View root = mHostView.getRootView();
            StringBuilder b = new StringBuilder();
            b.append("=== FearUI === root=").append(root.getClass().getName())
                    .append(" size=").append(root.getWidth()).append("x").append(root.getHeight())
                    .append("\n");
            fearWalk(root, 0, b);
            b.append("================\n");
            String text = b.toString();
            try (java.io.FileWriter w = new java.io.FileWriter(
                    new java.io.File(Tools.DIR_GAME_HOME, "fear_ui.txt"), false)) {
                w.write(text);
            }
            System.out.println(text);
        } catch (Throwable t) {
            System.out.println("[FearUI] failed: " + t);
        }
    }

    private void fearWalk(View v, int depth, StringBuilder b) {
        if (v == null || depth > 24) return;
        try {
            if (v.getVisibility() == View.VISIBLE) {
                int[] loc = new int[2];
                v.getLocationOnScreen(loc);
                b.append("[FearUI] ");
                for (int i = 0; i < depth; i++) b.append("  ");
                b.append(v.getClass().getSimpleName());
                if (v.getId() != View.NO_ID) {
                    try {
                        b.append(" id=").append(v.getResources().getResourceEntryName(v.getId()));
                    } catch (Throwable ignored) {
                        b.append(" id=").append(v.getId());
                    }
                }
                b.append(" (").append(loc[0]).append(",").append(loc[1]).append(")-(")
                        .append(loc[0] + v.getWidth()).append(",").append(loc[1] + v.getHeight())
                        .append(") clickable=").append(v.isClickable());
                if (v instanceof TextView) {
                    CharSequence t = ((TextView) v).getText();
                    if (t != null && t.length() > 0) {
                        String s = t.toString().replace('\n', ' ');
                        if (s.length() > 40) s = s.substring(0, 40);
                        b.append(" text=\"").append(s).append("\"");
                    }
                }
                b.append("\n");
            }
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) fearWalk(g.getChildAt(i), depth + 1, b);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * True when this launch is the Fear Monster engine.
     *
     * <p>FEAR_RENDERER is read first: onSurfaceAvailable publishes the renderer into the
     * environment before the window is set up, and the native side gates on the same value.
     * The instance lookup is the fallback, but it can throw from this thread - and when it did,
     * the catch below turned that into "not the engine" and the rotation was skipped without a
     * trace, which is what the empty tracker file was reporting.
     */
    private boolean fearIsMonster() {
        try {
            if ("fear_monster".equals(System.getenv("FEAR_RENDERER"))) return true;
        } catch (Throwable ignored) {}
        try {
            net.kdt.pojavlaunch.instances.Instance sel =
                    net.kdt.pojavlaunch.instances.Instances.loadSelectedInstance();
            String r = sel != null ? sel.getLaunchRenderer()
                    : LauncherPreferences.PREF_RENDERER;
            return "fear_monster".equals(r);
        } catch (Throwable t) {
            return false;
        }
    }

    /** A rotation angle from a file in the game dir, or the default. */
    private int fearRot(String name, int def) {
        try {
            java.io.File f = new java.io.File(Tools.DIR_GAME_HOME, name);
            if (f.isFile()) {
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                try (java.io.FileInputStream fis = new java.io.FileInputStream(f)) {
                    byte[] buf = new byte[16];
                    int n;
                    while ((n = fis.read(buf)) > 0) bos.write(buf, 0, n);
                }
                String v = bos.toString().trim();
                if ("0".equals(v)) return 0;
                if ("90".equals(v)) return 90;
                if ("180".equals(v)) return 180;
                if ("270".equals(v)) return 270;
            }
        } catch (Throwable ignored) {}
        return def;
    }

    /** One line per pointer event: where it was, and where it went. */
    private void fearMouseLog(int rot, float px, float py, float nx, float ny) {
        try {
            java.io.File f = new java.io.File(Tools.DIR_GAME_HOME, "fear_mouse.txt");
            if (f.length() > 256 * 1024) return;
            String line = String.format(java.util.Locale.US,
                    "view=%dx%d screen=(%.0f,%.0f) rot=%d sent=(%.4f,%.4f) monster=%s%n",
                    mHostView.getWidth(), mHostView.getHeight(), px, py, rot, nx, ny,
                    fearIsMonster());
            try (java.io.FileWriter w = new java.io.FileWriter(f, true)) {
                w.write(line);
            }
        } catch (Throwable ignored) {}
    }

    protected void applyMoveVector(float[] vector) {
        applyMoveVector(vector[0], vector[1]);
    }

    /**
     * FEARWIRE-MOVEROT (v10.29): relative movement, with an angle of its own.
     *
     * <p>Positions land correctly with the pointer turned by 270. The camera did not: swiping
     * up moved it sideways. One angle was being asked to do two jobs, and they are not the
     * same quantity - a position is where the finger is on the panel and has to be mapped into
     * the window the UI was laid out in, while a movement is a direction the game applies to
     * the camera in its own, turned, space.
     *
     * <p>So this reads fear_move.txt rather than fear_input.txt, defaulting to 90. If that is
     * wrong the other three are one launch each, with no rebuild.
     */
    protected void applyMoveVector(float x, float y) {
        if (fearIsMonster()) {
            switch (fearRot("fear_move.txt", 90)) {
                case 90: {
                    float t = x;
                    x = y;
                    y = -t;
                    break;
                }
                case 180: {
                    x = -x;
                    y = -y;
                    break;
                }
                case 270: {
                    float t = x;
                    x = -y;
                    y = t;
                    break;
                }
                default:
                    break;
            }
        }
        GLFW.cursorX += x * LauncherPreferences.PREF_MOUSESPEED / mHostView.getWidth();
        GLFW.cursorY += y * LauncherPreferences.PREF_MOUSESPEED / mHostView.getHeight();
        GLFW.sendMousePos();
    }

    abstract public boolean processTouchEvent(MotionEvent motionEvent);
    abstract public void cancelPendingActions();
}
