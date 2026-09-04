package net.kdt.pojavlaunch.customcontrols.mouse;

import android.view.MotionEvent;

public class PointerTracker {
    private boolean mColdStart = true;
    private int mTrackedPointerId;
    private int mPointerCount;
    private float mLastX, mLastY;
    private final float[] mMotionVector = new float[2];

    public void startTracking(MotionEvent motionEvent) {
        mColdStart = false;
        mTrackedPointerId = motionEvent.getPointerId(0);
        mPointerCount = motionEvent.getPointerCount();
        mLastX = motionEvent.getX();
        mLastY = motionEvent.getY();
    }

    public void cancelTracking() {
        mColdStart = true;
    }

    public int trackEvent(MotionEvent motionEvent) {
        int trackedPointerIndex = motionEvent.findPointerIndex(mTrackedPointerId);
        int pointerCount = motionEvent.getPointerCount();
        if(trackedPointerIndex == -1 || mPointerCount != pointerCount || mColdStart) {
            startTracking(motionEvent);
            trackedPointerIndex = 0;
        }

        // Low-latency high-rate historical batch motion interpolation for 120Hz/144Hz smooth mouse movement
        float deltaX = 0.0f;
        float deltaY = 0.0f;
        int historySize = motionEvent.getHistorySize();
        if (historySize > 0) {
            for (int h = 0; h < historySize; h++) {
                float histX = motionEvent.getHistoricalX(trackedPointerIndex, h);
                float histY = motionEvent.getHistoricalY(trackedPointerIndex, h);
                deltaX += (histX - mLastX);
                deltaY += (histY - mLastY);
                mLastX = histX;
                mLastY = histY;
            }
        }

        float trackedX = motionEvent.getX(trackedPointerIndex);
        float trackedY = motionEvent.getY(trackedPointerIndex);
        deltaX += (trackedX - mLastX);
        deltaY += (trackedY - mLastY);

        mMotionVector[0] = deltaX;
        mMotionVector[1] = deltaY;
        mLastX = trackedX;
        mLastY = trackedY;
        return trackedPointerIndex;
    }

    public float[] getMotionVector() {
        return mMotionVector;
    }
}
