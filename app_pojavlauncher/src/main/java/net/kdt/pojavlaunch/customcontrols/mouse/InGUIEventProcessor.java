package net.kdt.pojavlaunch.customcontrols.mouse;

import android.view.MotionEvent;
import android.view.View;

import net.kdt.pojavlaunch.LwjglGlfwKeycode;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import net.kdt.pojavlaunch.CallbackBridge;

public class InGUIEventProcessor extends TouchEventProcessor {
    public static final float FINGER_SCROLL_THRESHOLD = Tools.dpToPx(6);
    public static final float FINGER_STILL_THRESHOLD = Tools.dpToPx(5);

    private final PointerTracker mTracker = new PointerTracker();
    private final TapDetector mSingleTapDetector;
    private View mTouchpad;
    private boolean mIsMouseDown = false;
    private float mStartX, mStartY;
    private final Scroller mScroller = new Scroller(FINGER_SCROLL_THRESHOLD);

    public InGUIEventProcessor(View hostView) {
        super(hostView);
        mSingleTapDetector = new TapDetector(1, TapDetector.DETECTION_METHOD_BOTH);
    }

    @Override
    public boolean processTouchEvent(MotionEvent motionEvent) {
        boolean singleTap = mSingleTapDetector.onTouchEvent(motionEvent);

        switch (motionEvent.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mTracker.startTracking(motionEvent);
                /* FEAR: the cursor moves to the finger on every press, touchpad or not.
                   It used to be inside the touchpadDisplayed() guard, so with the virtual mouse
                   showing the cursor stayed wherever it had been left - and the tap that follows on
                   ACTION_UP clicks at the cursor's old position, not where the finger went. Large
                   buttons survived because the cursor was often already over them; a one-line text
                   field never was, which is why the server address box could not be selected and
                   why the video settings options would not change.
                   This is also why fear_mouse.txt held nothing but its header: the only call to
                   sendTouchCoordinates on press was behind that guard. */
                sendTouchCoordinates(motionEvent.getX(), motionEvent.getY());
                if(!touchpadDisplayed()) {
                    // disabled gestures means no scrolling possible, send gesture early
                    if (LauncherPreferences.PREF_DISABLE_GESTURES) enableMouse();
                    else setGestureStart(motionEvent);
                }
                break;

            case MotionEvent.ACTION_MOVE:
                int pointerCount = motionEvent.getPointerCount();
                int pointerIndex = mTracker.trackEvent(motionEvent);
                if(pointerCount == 1 || LauncherPreferences.PREF_DISABLE_GESTURES) {
                    if(touchpadDisplayed()) {
                        applyMoveVector(mTracker.getMotionVector());
                    } else {
                        float mainPointerX = motionEvent.getX(pointerIndex);
                        float mainPointerY = motionEvent.getY(pointerIndex);
                        sendTouchCoordinates(mainPointerX, mainPointerY);

                        if(!mIsMouseDown) {
                            if(!hasGestureStarted()) setGestureStart(motionEvent);
                            if(!LeftClickGesture.isFingerStill(mStartX, mStartY, mainPointerX, mainPointerY, FINGER_STILL_THRESHOLD))
                                enableMouse();
                        }

                    }
                } else mScroller.performScroll(mTracker.getMotionVector());
                break;

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                mScroller.resetScrollOvershoot();
                mTracker.cancelTracking();

                /* FEAR: click on every release, not only when the tap detector agrees.
                   Three things had to line up before a click was sent: gestures enabled or the touchpad
                   showing, the mouse button not already held, and mSingleTapDetector calling it a tap.
                   With the virtual mouse on, a press also starts a gesture - so the release was being
                   read as the end of a drag rather than a tap, and nothing was clicked. The cursor had
                   already been moved to the finger on press, so a click here lands exactly where the
                   finger was: big buttons happened to work before because the cursor was often already
                   over them, and a one-line text field never was. */
                /* FEAR: release first, then click.
                   The click was guarded by !mIsMouseDown, and a press that turns into a small drag sets
                   that flag - so a tap that moved by a pixel or two arrived here with the button already
                   held and no click was sent. That is why the cursor follows the finger (the press moves
                   it) while nothing on a GUI screen responds (the release never clicked). */
                if(mIsMouseDown) disableMouse();
                CallbackBridge.performClick(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT);
                resetGesture();
        }


        return true;
    }

    private boolean touchpadDisplayed() {
        return mTouchpad != null && mTouchpad.getVisibility() == View.VISIBLE;
    }

    public void setAbstractTouchpad(View touchpad) {
        mTouchpad = touchpad;
    }

    private void enableMouse() {
        CallbackBridge.sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT, true);
        mIsMouseDown = true;
    }

    private void disableMouse() {
        CallbackBridge.sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT, false);
        mIsMouseDown = false;
    }

    private void setGestureStart(MotionEvent event) {
        mStartX = event.getX();
        mStartY = event.getY();
    }

    private void resetGesture() {
        mStartX = mStartY = -1;
    }

    private boolean hasGestureStarted() {
        return mStartX != -1 || mStartY != -1;
    }

    @Override
    public void cancelPendingActions() {
        mScroller.resetScrollOvershoot();
        if(mIsMouseDown) disableMouse();
    }
}
