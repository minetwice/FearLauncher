package net.kdt.pojavlaunch;

import static net.kdt.pojavlaunch.MainActivity.touchCharInput;
import static net.kdt.pojavlaunch.utils.MCOptionUtils.getMcScale;
import static net.kdt.pojavlaunch.CallbackBridge.sendMouseButton;
import static net.kdt.pojavlaunch.CallbackBridge.windowHeight;
import static net.kdt.pojavlaunch.CallbackBridge.windowWidth;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.RequiresApi;

import net.kdt.pojavlaunch.customcontrols.ControlLayout;
import net.kdt.pojavlaunch.customcontrols.gamepad.DefaultDataProvider;
import net.kdt.pojavlaunch.customcontrols.gamepad.Gamepad;
import net.kdt.pojavlaunch.customcontrols.gamepad.DirectGamepad;
import net.kdt.pojavlaunch.customcontrols.mouse.AndroidPointerCapture;
import net.kdt.pojavlaunch.customcontrols.mouse.InGUIEventProcessor;
import net.kdt.pojavlaunch.customcontrols.mouse.InGameEventProcessor;
import net.kdt.pojavlaunch.customcontrols.mouse.TouchEventProcessor;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.render.SurfaceProvider;
import net.kdt.pojavlaunch.render.SurfaceViewSurfaceProvider;
import net.kdt.pojavlaunch.render.TextureViewSurfaceProvider;
import net.kdt.pojavlaunch.utils.MCOptionUtils;

import fr.spse.gamepad_remapper.GamepadHandler;
import fr.spse.gamepad_remapper.RemapperManager;
import fr.spse.gamepad_remapper.RemapperView;
import git.artdeell.dnbootstrap.glfw.GLFW;
import git.artdeell.dnbootstrap.glfw.GamepadEnableHandler;
import git.artdeell.dnbootstrap.glfw.GrabListener;

/**
 * Class dealing with showing minecraft surface and taking inputs to dispatch them to minecraft
 */
public class MinecraftGLSurface extends View implements GrabListener, GamepadEnableHandler, SurfaceProvider.SurfaceCallback {
    /* Gamepad object for gamepad inputs, instantiated on need */
    private GamepadHandler mGamepadHandler;
    /* The RemapperView.Builder object allows you to set which buttons to remap */
    private final RemapperManager mInputManager = new RemapperManager(getContext(), new RemapperView.Builder(null)
            .remapA(true)
            .remapB(true)
            .remapX(true)
            .remapY(true)

            .remapLeftJoystick(true)
            .remapRightJoystick(true)
            .remapStart(true)
            .remapSelect(true)
            .remapLeftShoulder(true)
            .remapRightShoulder(true)
            .remapLeftTrigger(true)
            .remapRightTrigger(true)
            .remapDpad(true));

    /* Sensitivity, adjusted according to screen size */
    private final double mSensitivityFactor = (1.4 * (1080f/ Tools.getDisplayMetrics((Activity) getContext()).heightPixels));

    private final SurfaceProvider mSurfaceProvider = LauncherPreferences.PREF_USE_ALTERNATE_SURFACE ? new SurfaceViewSurfaceProvider() : new TextureViewSurfaceProvider();
    private boolean mRefreshOnly = true;
    /* Surface ready listener, used by the activity to launch minecraft */
    SurfaceReadyListener mSurfaceReadyListener = null;
    final Object mSurfaceReadyListenerLock = new Object();
    /* View holding the surface, either a SurfaceView or a TextureView */
    View mSurface;

    private final InGameEventProcessor mIngameProcessor = new InGameEventProcessor(this, mSensitivityFactor);
    private final InGUIEventProcessor mInGUIProcessor = new InGUIEventProcessor(this);
    private TouchEventProcessor mCurrentTouchProcessor = mInGUIProcessor;
    private AndroidPointerCapture mPointerCapture;
    private View mTouchpad;
    private boolean mLastGrabState = false;

    public MinecraftGLSurface(Context context) {
        this(context, null);
    }

    public MinecraftGLSurface(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        setFocusable(true);
        GLFW.setGamepadEnableHandler(this);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void setUpPointerCapture() {
        if(mPointerCapture != null) mPointerCapture.detach();
        mPointerCapture = new AndroidPointerCapture(mTouchpad, this);
    }

    /** Initialize the view and all its settings
     * @param isAlreadyRunning set to true to tell the view that the game is already running
     *                         (only updates the window without calling the start listener)
     * @param touchpad the optional cursor-emulating touchpad, used for touch event processing
     *                 when the cursor is not grabbed
     */
    public void start(boolean isAlreadyRunning, View touchpad) {
        mTouchpad = touchpad;
        if (Tools.isAndroid8OrHigher()) setUpPointerCapture();
        mInGUIProcessor.setAbstractTouchpad(touchpad);
        mRefreshOnly = isAlreadyRunning;
        mSurface = mSurfaceProvider.create(getContext(), this);
        ((ViewGroup) getParent()).addView(mSurface);
        // Hand the surface to the engine: it is the only thing that can resize the
        // render target while the game is running, which is how the governor holds the
        // frame rate near its target.
        net.kdt.pojavlaunch.engine.FearEngine.attachSurface(this);
    }

    /**
     * The touch event for both grabbed an non-grabbed mouse state on the touch screen
     * Does not cover the virtual mouse touchpad
     */
    @Override
    @SuppressWarnings("accessibility")
    public boolean onTouchEvent(MotionEvent e) {
        // Kinda need to send this back to the layout
        if(((ControlLayout)getParent()).getModifiable()) return false;

        // Looking for a mouse to handle, won't have an effect if no mouse exists.
        for (int i = 0; i < e.getPointerCount(); i++) {
            int toolType = e.getToolType(i);
            if(toolType == MotionEvent.TOOL_TYPE_MOUSE) {
                if(Tools.isAndroid8OrHigher() &&
                        mPointerCapture != null) {
                    mPointerCapture.handleAutomaticCapture();
                    return true;
                }
            }else if(toolType != MotionEvent.TOOL_TYPE_STYLUS) continue;

            // Mouse found
            // Avoid going through the JNI each time.
            if(GLFW.isGrabbing()) return false;
            fearCursor(e.getX(i), e.getY(i));
            GLFW.sendMousePos();
            return true; //mouse event handled successfully
        }
        if (mIngameProcessor == null || mInGUIProcessor == null) return true;
        return mCurrentTouchProcessor.processTouchEvent(e);
    }

    private void createGamepad(InputDevice inputDevice) {
        if(GLFW.gamepadButtonBuffer != null) {
            mGamepadHandler = new DirectGamepad();
            // Only send this if there was a gamepad event, to avoid forcing users without gamepads through
            // Controlify calibration
            GLFW.nativeNotifyGamepadConnected();
        }else {
            mGamepadHandler = new Gamepad(inputDevice, DefaultDataProvider.INSTANCE, mTouchpad);
        }
    }

    /**
     * The event for mouse/joystick movements
     */
    @SuppressLint("NewApi")
    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        int mouseCursorIndex = -1;

        if(Gamepad.isGamepadEvent(event)){
            if(mGamepadHandler == null) createGamepad(event.getDevice());

            mInputManager.handleMotionEventInput(getContext(), event, mGamepadHandler);
            return true;
        }

        for(int i = 0; i < event.getPointerCount(); i++) {
            if(event.getToolType(i) != MotionEvent.TOOL_TYPE_MOUSE && event.getToolType(i) != MotionEvent.TOOL_TYPE_STYLUS ) continue;
            // Mouse found
            mouseCursorIndex = i;
            break;
        }
        if(mouseCursorIndex == -1) return false; // we cant consoom that, theres no mice!

        // Make sure we grabbed the mouse if necessary
        // Avoid going through the JNI each time.
        updateGrabState(GLFW.isGrabbing());

        switch(event.getActionMasked()) {
            case MotionEvent.ACTION_HOVER_MOVE:
                fearCursor(event.getX(mouseCursorIndex), event.getY(mouseCursorIndex));
                GLFW.sendMousePos();
                return true;
            case MotionEvent.ACTION_SCROLL:
                CallbackBridge.sendScroll(event.getAxisValue(MotionEvent.AXIS_HSCROLL), event.getAxisValue(MotionEvent.AXIS_VSCROLL));
                return true;
            case MotionEvent.ACTION_BUTTON_PRESS:
                return sendMouseButtonUnconverted(event.getActionButton(),true);
            case MotionEvent.ACTION_BUTTON_RELEASE:
                return sendMouseButtonUnconverted(event.getActionButton(),false);
            default:
                return false;
        }
    }

    /** The event for keyboard/ gamepad button inputs */
    public boolean processKeyEvent(KeyEvent event) {
        //Log.i("KeyEvent", event.toString());

        //Filtering useless events by order of probability
        int eventKeycode = event.getKeyCode();
        if(eventKeycode == KeyEvent.KEYCODE_UNKNOWN) return true;
        if(eventKeycode == KeyEvent.KEYCODE_VOLUME_DOWN) return false;
        if(eventKeycode == KeyEvent.KEYCODE_VOLUME_UP) return false;
        if(event.getRepeatCount() != 0) return true;
        int action = event.getAction();
        if(action == KeyEvent.ACTION_MULTIPLE) return true;
        // Ignore the cancelled up events. They occur when the user switches layouts.
        // In accordance with https://developer.android.com/reference/android/view/KeyEvent#FLAG_CANCELED
        if(action == KeyEvent.ACTION_UP &&
                (event.getFlags() & KeyEvent.FLAG_CANCELED) != 0) return true;

        //Sometimes, key events comes from SOME keys of the software keyboard
        //Even weirder, is is unknown why a key or another is selected to trigger a keyEvent
        if((event.getFlags() & KeyEvent.FLAG_SOFT_KEYBOARD) == KeyEvent.FLAG_SOFT_KEYBOARD){
            if(eventKeycode == KeyEvent.KEYCODE_ENTER) return true; //We already listen to it.
            touchCharInput.dispatchKeyEvent(event);
            return true;
        }

        //Sometimes, key events may come from the mouse
        if(event.getDevice() != null
                && ( (event.getSource() & InputDevice.SOURCE_MOUSE_RELATIVE) == InputDevice.SOURCE_MOUSE_RELATIVE
                ||   (event.getSource() & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE)  ){

            if(eventKeycode == KeyEvent.KEYCODE_BACK){
                sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT, event.getAction() == KeyEvent.ACTION_DOWN);
                return true;
            }
        }

        if(Gamepad.isGamepadEvent(event)){
            if(mGamepadHandler == null) createGamepad(event.getDevice());

            mInputManager.handleKeyEventInput(getContext(), event, mGamepadHandler);
            return true;
        }

        CallbackBridge.setModifiers(event);
        char codepoint = action == KeyEvent.ACTION_DOWN ? (char) event.getUnicodeChar(event.getMetaState()) : 0;
        GLFW.sendRawKeyEvent(eventKeycode, action == KeyEvent.ACTION_DOWN ? 1 : 0, CallbackBridge.getCurrentMods(), codepoint);

        // Some events will be generated an infinite number of times when no consumed
        return (event.getFlags() & KeyEvent.FLAG_FALLBACK) == KeyEvent.FLAG_FALLBACK;
    }

    /** Convert the mouse button, then send it
     * @return Whether the event was processed
     */
    public static boolean sendMouseButtonUnconverted(int button, boolean status) {
        int glfwButton = -256;
        switch (button) {
            case MotionEvent.BUTTON_PRIMARY:
                glfwButton = LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT;
                break;
            case MotionEvent.BUTTON_TERTIARY:
                glfwButton = LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_MIDDLE;
                break;
            case MotionEvent.BUTTON_SECONDARY:
                glfwButton = LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT;
                break;
        }
        if(glfwButton == -256) return false;
        sendMouseButton(glfwButton, status);
        return true;
    }

    /** Called when the size need to be set at any point during the surface lifecycle **/
    public void refreshSize(){
        refreshSize(false);
    }

    /** Same as refreshSize, but allows you to force an immediate size update **/
    public void refreshSize(boolean immediate) {
        if(isInLayout() && !immediate) {
            post(this::refreshSize);
            return;
        }
        // Use the width and height of the View instead of display dimensions to avoid
        // getting squiched/stretched due to inconsistencies between the layout and
        // screen dimensions.
        int newWidth = Tools.getDisplayFriendlyRes(getWidth(), LauncherPreferences.PREF_SCALE_FACTOR);
        int newHeight = Tools.getDisplayFriendlyRes(getHeight(), LauncherPreferences.PREF_SCALE_FACTOR);
        if (newHeight < 1 || newWidth < 1) {
            Log.e("MGLSurface", String.format("Impossible resolution : %dx%d", newWidth, newHeight));
            return;
        }
        windowWidth = newWidth;
        windowHeight = newHeight;
        if(mSurface == null){
            Log.w("MGLSurface", "Attempt to refresh size on null surface");
            return;
        }
        mSurfaceProvider.updateSize();
    }

    /**
     * FEARWIRE-INPUTROT (v10.21): puts a touch where the picture is.
     *
     * <p>The engine presents without pre-rotation now, so the Android compositor rotates the
     * image a quarter turn. The picture is therefore upright, but Minecraft still believes it
     * is drawing into a window that has not been turned, and it maps the pointer against that
     * window. A tap lands where the UI is not - the buttons look live and do nothing, which
     * is exactly what a ghost looks like.
     *
     * <p>So the pointer is turned back before it is handed over. The angle is read from
     * fear_input.txt in the game dir (0/90/180/270, default 90) so it can be tried on the
     * device without a rebuild, the same way holy_rotate.txt and fear_swap.txt are.
     *
     * <p>Every other renderer is untouched: their pointer goes through unchanged, exactly as
     * it always has.
     */
    private void fearCursor(float px, float py) {
        float nx = px / getWidth();
        float ny = py / getHeight();
        if (!"fear_monster".equals(fearRNow())) {
            GLFW.cursorX = nx;
            GLFW.cursorY = ny;
            return;
        }
        switch (fearInputRot()) {
            case 90:
                GLFW.cursorX = ny;
                GLFW.cursorY = 1f - nx;
                break;
            case 180:
                GLFW.cursorX = 1f - nx;
                GLFW.cursorY = 1f - ny;
                break;
            case 270:
                GLFW.cursorX = 1f - ny;
                GLFW.cursorY = nx;
                break;
            default:
                GLFW.cursorX = nx;
                GLFW.cursorY = ny;
                break;
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

    /** The renderer this launch will use, or "" when it cannot be worked out. */
    private String fearRNow() {
        try {
            net.kdt.pojavlaunch.instances.Instance sel =
                    net.kdt.pojavlaunch.instances.Instances.loadSelectedInstance();
            String r = sel != null ? sel.getLaunchRenderer()
                    : net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_RENDERER;
            return r == null ? "" : r;
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * FEARWIRE-DIAG (v10.17): writes down everything the rotation depends on, in one place.
     *
     * <p>The rotation has been guessed at three times and missed three times, because none
     * of the values it depends on were ever visible from a device. Everything that could
     * move the UI is printed here - to latestlog, and to fear_diag.txt in the game
     * directory so it survives a log that gets rotated away. Nothing in here changes
     * behaviour: it only reports.
     */
    private void fearDiag(boolean holyRotate, boolean swap) {
        try {
            java.io.File t = new java.io.File(Tools.DIR_GAME_HOME, "holy_rotate.txt");
            StringBuilder b = new StringBuilder();
            b.append("=== FearDiag ===\n");
            b.append("renderer=").append(fearRNow()).append("\n");
            b.append("gameDir=").append(Tools.DIR_GAME_HOME).append("\n");
            b.append("surface=").append(windowWidth).append("x").append(windowHeight).append("\n");
            b.append("holyRotate=").append(holyRotate).append("\n");
            b.append("dimsSwapped=").append(swap).append("\n");
            b.append("overrideWidth=").append(fearSwapValue(swap, true)).append("\n");
            b.append("overrideHeight=").append(fearSwapValue(swap, false)).append("\n");
            b.append("FEAR_ROTATE_T=").append(String.valueOf(System.getenv("FEAR_ROTATE_T"))).append("\n");
            b.append("sFearRotateDir=").append(net.kdt.pojavlaunch.utils.JREUtils.sFearRotateDir).append("\n");
            b.append("FEAR_RENDERER=").append(String.valueOf(System.getenv("FEAR_RENDERER"))).append("\n");
            b.append("holy_rotate.txt=").append(t.getAbsolutePath())
                    .append(" exists=").append(t.isFile()).append("\n");
            java.io.File sw = new java.io.File(Tools.DIR_GAME_HOME, "fear_swap.txt");
            b.append("fear_swap.txt=").append(sw.getAbsolutePath())
                    .append(" exists=").append(sw.isFile()).append("\n");
            java.io.File ir = new java.io.File(Tools.DIR_GAME_HOME, "fear_input.txt");
            b.append("fear_input.txt=").append(ir.getAbsolutePath())
                    .append(" exists=").append(ir.isFile())
                    .append(" rot=").append(fearInputRot()).append("\n");
            b.append("================");
            String text = b.toString();
            System.out.println(text);
            try (java.io.FileWriter w = new java.io.FileWriter(
                    new java.io.File(Tools.DIR_GAME_HOME, "fear_diag.txt"), false)) {
                w.write(text + "\n");
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            System.out.println("[FearDiag] failed: " + t);
        }
    }

    /** The value that was written for overrideWidth/overrideHeight, for the log. */
    private int fearSwapValue(boolean swap, boolean width) {
        if (swap) return width ? windowHeight : windowWidth;
        return width ? windowWidth : windowHeight;
    }

    private void realStart(){
        // Initial size set. Request immedate refresh, otherwise the initial width and height for the game
        // may be broken/unknown.
        refreshSize(true);

        //Load Minecraft options:
        MCOptionUtils.set("fullscreen", "false"); /* FEARWIRE: "off" is not a boolean MC parses (error in latestlog) */
        /* FEARWIRE-HOLYZINK-ROTATE3: holy zink renders portrait (the compositor
           rotates it upright), so give MC swapped dims and enable the dnbglfw
           input remap. */
        boolean holyZink = false;
        try {
            net.kdt.pojavlaunch.instances.Instance sel =
                    net.kdt.pojavlaunch.instances.Instances.loadSelectedInstance();
            /* FEARWIRE-HOLYZINK-ROTATE7: same renderer resolution as the
               launcher itself (getLaunchRenderer falls back to PREF_RENDERER
               when instance.renderer is empty). */
            String fearR = sel != null ? sel.getLaunchRenderer()
                    : net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_RENDERER;
            holyZink = "holy_zink_kopper".equals(fearR) || "fear_monster".equals(fearR);
        } catch (Throwable ignored) {}
        /* FEARWIRE-HOLYZINK-ROTATE9: fear_monster needs this too, and it was the one
           renderer left out. Its surface is landscape (2584x1220) but the ANativeWindow
           reports the display's natural, portrait size, so Minecraft laid the UI out for a
           tall window and everything came out rotated a quarter turn:
           the title screen, its buttons, the whole HUD. holyRotate is exactly the switch
           for that - it hands MC the swapped dimensions and enables the dnbglfw input
           remap - and it was being computed and then thrown away. */
        GLFW.holyRotate = holyZink;
        /* FEARWIRE-HARDROTATE (v10.19): the swap is OFF for fear_monster now, and this is
           the one combination the diagnostic has not been through. It has shown, on a
           device, that the UI is a quarter turn out in all three states tried so far:
           no swap with sFearRotateDir=90, swap with 90, and swap with 0. The first had the
           bridge rotating it, the others had this swap rotating it - two different causes,
           each enough on its own. With the bridge's rotation off (v10.18) and this off too,
           nothing should be turning it.
           And it is tunable, because rebuilding to test each combination has cost enough
           launches: fear_swap.txt in the game dir, containing 0 or 1, overrides this. */
        boolean fearSwap = false;
        if ("fear_monster".equals(fearRNow())) {
            try {
                java.io.File f = new java.io.File(Tools.DIR_GAME_HOME, "fear_swap.txt");
                if (f.isFile()) {
                    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                    try (java.io.FileInputStream fis = new java.io.FileInputStream(f)) {
                        byte[] buf = new byte[16];
                        int n;
                        while ((n = fis.read(buf)) > 0) bos.write(buf, 0, n);
                    }
                    String v = bos.toString().trim();
                    if (!v.isEmpty()) fearSwap = "1".equals(v);
                    System.out.println("FEARWIRE v10.19: fear_swap.txt -> fearSwap=" + fearSwap);
                }
            } catch (Throwable ignored) {}
        }
        MCOptionUtils.set("overrideWidth", String.valueOf(fearSwap ? windowHeight : windowWidth));
        MCOptionUtils.set("overrideHeight", String.valueOf(fearSwap ? windowWidth : windowHeight));
        MCOptionUtils.save();
        getMcScale();
        fearDiag(holyZink, fearSwap);

        new Thread(() -> {
            try {
                // Wait until the listener is attached
                synchronized(mSurfaceReadyListenerLock) {
                    if(mSurfaceReadyListener == null) mSurfaceReadyListenerLock.wait();
                }

                mSurfaceReadyListener.isReady();
            } catch (Throwable e) {
                Tools.showError(getContext(), e, true);
            }
        }, "JVM Main thread").start();
    }

    @Override
    public void onGrabState(boolean isGrabbing) {
        post(()->updateGrabState(isGrabbing));
    }

    private TouchEventProcessor pickEventProcessor(boolean isGrabbing) {
        return isGrabbing ? mIngameProcessor : mInGUIProcessor;
    }

    private void updateGrabState(boolean isGrabbing) {
        if(mLastGrabState != isGrabbing) {
            mCurrentTouchProcessor.cancelPendingActions();
            mCurrentTouchProcessor = pickEventProcessor(isGrabbing);
            mLastGrabState = isGrabbing;
        }
    }

    @Override
    public void onSurfaceAvailable(Surface surface) {
        /* FEARWIRE-HOLYZINK-ROTATE5: publish the selected renderer into the
           process env BEFORE the native surface setup - setupRendererEnv's
           envMap is applied later (at game launch), so every env-gated native
           rotation fix silently skipped during window setup. */
        String fearRenderer5 = "(unset)";
        try {
            net.kdt.pojavlaunch.instances.Instance sel =
                    net.kdt.pojavlaunch.instances.Instances.loadSelectedInstance();
            /* FEARWIRE-HOLYZINK-ROTATE7: use getLaunchRenderer() - the exact
               method the launcher itself uses. instance.renderer can be empty
               while the real renderer comes from the global PREF_RENDERER. */
            fearRenderer5 = sel != null ? sel.getLaunchRenderer()
                    : net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_RENDERER;
        } catch (Throwable t) { fearRenderer5 = "ERR " + t; }
        if (fearRenderer5 != null && !fearRenderer5.isEmpty() && !fearRenderer5.startsWith("ERR")) {
            try { android.system.Os.setenv("FEAR_RENDERER", fearRenderer5, true); } catch (Throwable ignored) {}
        }
        System.out.println("FEARWIRE v10.11: onSurfaceAvailable FEAR_RENDERER=" + fearRenderer5);
        /* FEARWIRE-HOLYZINK-ROTATE8: holy_rotate.txt in the game dir can
           override the counter-rotation (0/1/3/4) without a rebuild. */
        try {
            java.io.File fearTFile = new java.io.File(Tools.DIR_GAME_HOME, "holy_rotate.txt");
            if (fearTFile.isFile()) {
                java.io.ByteArrayOutputStream fearBos = new java.io.ByteArrayOutputStream();
                try (java.io.FileInputStream fearFis = new java.io.FileInputStream(fearTFile)) {
                    byte[] buf = new byte[16];
                    int n;
                    while ((n = fearFis.read(buf)) > 0) fearBos.write(buf, 0, n);
                }
                String t = fearBos.toString().trim();
                if (!t.isEmpty()) {
                    android.system.Os.setenv("FEAR_ROTATE_T", t, true);
                    net.kdt.pojavlaunch.utils.JREUtils.sFearRotateDir = "4".equals(t) ? 270 : "3".equals(t) ? 180 : 90;
                }
                System.out.println("FEARWIRE v10.12: holy_rotate.txt -> FEAR_ROTATE_T=" + t);
        /* FEARWIRE-ZINK-MALI (v10.14): holy_zink.txt in the game dir - one
           KEY=VALUE per line (# comments allowed) - applies env overrides for
           the zink stack WITHOUT a rebuild (e.g. ZINK_MALI_NOBINDLESS=0 to
           re-enable bindless for performance once textures are correct). */
        try {
            java.io.File fearEnvFile = new java.io.File(Tools.DIR_GAME_HOME, "holy_zink.txt");
            if (fearEnvFile.isFile()) {
                java.io.BufferedReader fearBr = new java.io.BufferedReader(new java.io.FileReader(fearEnvFile));
                String fearLn;
                while ((fearLn = fearBr.readLine()) != null) {
                    fearLn = fearLn.trim();
                    int fearEq = fearLn.indexOf('=');
                    if (fearLn.isEmpty() || fearLn.startsWith("#") || fearEq <= 0) continue;
                    try { android.system.Os.setenv(fearLn.substring(0, fearEq), fearLn.substring(fearEq + 1), true); } catch (Throwable ignored) {}
                }
                fearBr.close();
                System.out.println("FEARWIRE v10.14: holy_zink.txt env overrides applied");
            }
        } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        /* FEARWIRE-FRAMEDUMP (v10.16): publish the game dir to native so the
           osm_bridge frame dump (FEAR_DUMP_FRAME=<seconds> via holy_zink.txt)
           can write holy_frame.raw next to latestlog for offline analysis. */
        try { android.system.Os.setenv("FEAR_GAME_DIR", Tools.DIR_GAME_HOME, true); } catch (Throwable ignored) {}
        /* FEARWIRE-HARDROTATE (v10.20): fear_monster is handled exactly like
           holy_zink_kopper now, because holy_zink_kopper is the one that works.
           That means: holyRotate on, the dimensions left alone, and sFearRotateDir left to
           holy_rotate.txt rather than forced.
           v10.18 forced it to 0 and that was the mistake - it overrode holy_rotate.txt, so
           the one setting that makes holy_zink_kopper work could never reach fear_monster,
           and every combination tried after that was really the same combination.
           v10.19's swap stays off for the same reason: holy_zink_kopper does not swap. */
        GLFW.nativeSurfaceCreated(surface);
        // Pass the Surface to the OSMesa bridge in pojavexec (for zink renderers)
        try {
            net.kdt.pojavlaunch.utils.JREUtils.setupBridgeWindow(surface);
        } catch (Throwable t) {
            android.util.Log.w("MinecraftGLSurface", "Bridge window setup failed", t);
        }
        if(mRefreshOnly) return;
        realStart();
        mRefreshOnly = true;
    }

    @Override
    public void onSurfaceResized() {
        GLFW.nativeSurfaceUpdated();
    }

    @Override
    public void onSurfaceDestroyed() {
        GLFW.nativeSurfaceDestroyed();
    }

    @Override
    public void onEnableGamepad() {
        post(()->{
            if(mGamepadHandler != null && mGamepadHandler instanceof Gamepad) {
                ((Gamepad)mGamepadHandler).removeSelf();
            }
            // Force gamepad recreation on next event
            mGamepadHandler = null;
        });
    }

    /** A small interface called when the listener is ready for the first time */
    public interface SurfaceReadyListener {
        void isReady();
    }

    public void setSurfaceReadyListener(SurfaceReadyListener listener){
        synchronized (mSurfaceReadyListenerLock) {
            mSurfaceReadyListener = listener;
            mSurfaceReadyListenerLock.notifyAll();
        }
    }
}
