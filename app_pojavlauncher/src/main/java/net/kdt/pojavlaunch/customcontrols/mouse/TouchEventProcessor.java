package net.kdt.pojavlaunch.customcontrols.mouse;

import android.view.MotionEvent;
import android.view.View;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import git.artdeell.dnbootstrap.glfw.GLFW;

public abstract class TouchEventProcessor {
    private final View mHostView;
    public TouchEventProcessor(View hostView) {
        mHostView = hostView;
    }

    /**
     * FEARWIRE-INPUTROT (v10.23): the path a finger actually takes.
     *
     * <p>This is where the pointer was going wrong. The engine presents without pre-rotation,
     * so the compositor turns the image a quarter turn, and Minecraft maps the pointer against
     * a window that was never turned. The rotation was added on MinecraftGLSurface.onTouchEvent
     * first, but that code is only reached for TOOL_TYPE_MOUSE and TOOL_TYPE_STYLUS: a finger
     * falls through the loop and arrives here instead.
     *
     * <p>So the pointer is turned back here. The angle comes from fear_input.txt in the game dir
     * (0/90/180/270, default 90), only for fear_monster.
     */
    protected void sendTouchCoordinates(float x, float y) {
        float nx = x / mHostView.getWidth();
        float ny = y / mHostView.getHeight();
        int rot = fearIsMonster() ? fearInputRot() : 0;
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
           missing pointer event both left an empty file, which made the last two runs
           impossible to tell apart. */
        fearMouseLog(rot, x, y, nx, ny);
        GLFW.cursorX = nx;
        GLFW.cursorY = ny;
        GLFW.sendMousePos();
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

    /** The pointer rotation to apply, from fear_input.txt. Default 90. */
    private int fearInputRot() {
        try {
            java.io.File f = new java.io.File(Tools.DIR_GAME_HOME, "fear_input.txt");
            if (f.isFile()) {
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                try (java.io.FileInputStream fis = new java.io.FileInputStream(f)) {
                    byte[] buf = new byte[16];
                    int n;
                    while ((n = fis.read(buf)) > 0) bos.write(buf, 0, n);
                }
                String v = bos.toString().trim();
                if ("0".equals(v)) return 0;
                if ("180".equals(v)) return 180;
                if ("270".equals(v)) return 270;
            }
        } catch (Throwable ignored) {}
        return 90;
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

    /** Relative movement for a grabbed mouse - no origin, so no rotation applies. */
    protected void applyMoveVector(float x, float y) {
        GLFW.cursorX += x * LauncherPreferences.PREF_MOUSESPEED / mHostView.getWidth();
        GLFW.cursorY += y * LauncherPreferences.PREF_MOUSESPEED / mHostView.getHeight();
        GLFW.sendMousePos();
    }

    abstract public boolean processTouchEvent(MotionEvent motionEvent);
    abstract public void cancelPendingActions();
}
