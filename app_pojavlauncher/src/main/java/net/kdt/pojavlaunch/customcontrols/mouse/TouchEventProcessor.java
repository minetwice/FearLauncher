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
     * The pointer, turned by whatever the engine turned the image by.
     *
     * <p>Both read fear_prerotate.txt, so they cannot drift apart. The default is 0: the engine's
     * swapchain now follows the window rather than the surface, which leaves the compositor
     * nothing to add and the projection nothing to undo, so an unturned pointer is the right one.
     */
    protected void sendTouchCoordinates(float x, float y) {
        if (!fearUiDumped) {
            fearUiDumped = true;
            fearDumpUi();
        }
        float nx = x / mHostView.getWidth();
        float ny = y / mHostView.getHeight();
        int rot = fearIsMonster() ? fearAngle() : 0;
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
        fearMouseLog(rot, x, y, nx, ny);
        GLFW.cursorX = nx;
        GLFW.cursorY = ny;
        GLFW.sendMousePos();
    }

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

    /** The angle the engine turned the image by. Default 0 - see sendTouchCoordinates. */
    private int fearAngle() {
        return fearRot("fear_prerotate.txt", 0);
    }

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

    /** Relative movement, turned by the same angle as the image. */
    protected void applyMoveVector(float x, float y) {
        if (fearIsMonster()) {
            switch (fearAngle()) {
                case 90: { float t = x; x = y; y = -t; break; }
                case 180: { x = -x; y = -y; break; }
                case 270: { float t = x; x = -y; y = t; break; }
                default: break;
            }
        }
        GLFW.cursorX += x * LauncherPreferences.PREF_MOUSESPEED / mHostView.getWidth();
        GLFW.cursorY += y * LauncherPreferences.PREF_MOUSESPEED / mHostView.getHeight();
        GLFW.sendMousePos();
    }

    abstract public boolean processTouchEvent(MotionEvent motionEvent);
    abstract public void cancelPendingActions();
}
