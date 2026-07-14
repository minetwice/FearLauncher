package com.kdt.mcgui;

import android.content.Context;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ProgressBar;

import androidx.annotation.StringRes;
import androidx.core.content.res.ResourcesCompat;

import git.artdeell.mojo.R;

public class TextProgressBar extends ProgressBar {

    private int mTextPadding = 0;
    public TextProgressBar(Context context) {super(context, null, android.R.attr.progressBarStyleHorizontal); init();}

    public TextProgressBar(Context context, AttributeSet attrs) {super(context, attrs, android.R.attr.progressBarStyleHorizontal); init();}
    public TextProgressBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, android.R.attr.progressBarStyleHorizontal);
        init();
    }
    public TextProgressBar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, android.R.attr.progressBarStyleHorizontal, defStyleRes);
        init();
    }

    private Paint mTextPaint;
    private String mText = "";

    private void init(){
        setProgressDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.view_text_progressbar, null));
        setProgress(35);
        mTextPaint = new Paint();
        mTextPaint.setColor(Color.WHITE);
        mTextPaint.setFlags(Paint.FAKE_BOLD_TEXT_FLAG);
        mTextPaint.setAntiAlias(true);
    }

    private float mAnimatedProgress = 0f;

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Ensure progress is animated smoothly and percentage text updates dynamically in real-time
        int targetProgress = getProgress();
        if (Math.abs(mAnimatedProgress - targetProgress) > 0.1f) {
            mAnimatedProgress += (targetProgress - mAnimatedProgress) * 0.25f;
            postInvalidateDelayed(16);
        } else {
            mAnimatedProgress = targetProgress;
        }

        mTextPaint.setTextSize((float) ((getHeight() - getPaddingBottom() - getPaddingTop()) * 0.55));

        // Dynamically append current progress percentage for proper status reporting
        String displayMessage = mText;
        if (displayMessage == null) displayMessage = "";
        if (!displayMessage.contains("%") && getMax() > 0) {
            displayMessage = displayMessage + " (" + (int)((mAnimatedProgress / getMax()) * 100) + "%)";
        }

        int xPos = (int) Math.max(Math.min((mAnimatedProgress * getWidth() / getMax()) + mTextPadding, getWidth() - mTextPaint.measureText(displayMessage) - mTextPadding), mTextPadding);
        int yPos = (int) ((getHeight() / 2) - ((mTextPaint.descent() + mTextPaint.ascent()) / 2));

        canvas.drawText(displayMessage, xPos, yPos, mTextPaint);
    }


    public final void setText(@StringRes int resid) {
        setText(getContext().getResources().getText(resid).toString());
    }

    public final void setText(String text){
        mText = text;
        invalidate();
    }

    public final void setTextPadding(int padding){
        mTextPadding = padding;
    }
}
