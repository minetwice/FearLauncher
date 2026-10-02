package com.kdt.mcgui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.VideoView;

/**
 * FEAR intro: a VideoView that keeps the video's real aspect ratio instead of
 * stretching it to fill the screen. Give it match_parent in both directions and
 * set the aspect once the media is prepared - it measures itself to fit inside
 * whatever space it is given, so a 16:9 clip letterboxes cleanly on any screen.
 */
public class AspectVideoView extends VideoView {
    private float mAspect = 0f;

    public AspectVideoView(Context context) { super(context); }
    public AspectVideoView(Context context, AttributeSet attrs) { super(context, attrs); }
    public AspectVideoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /** @param aspect width / height of the video */
    public void setAspect(float aspect) {
        if (aspect > 0f && aspect != mAspect) {
            mAspect = aspect;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (mAspect > 0f && width > 0 && height > 0) {
            if (width / (float) height > mAspect) {
                width = (int) (height * mAspect);   // pillarbox
            } else {
                height = (int) (width / mAspect);   // letterbox
            }
        }
        setMeasuredDimension(width, height);
    }
}
