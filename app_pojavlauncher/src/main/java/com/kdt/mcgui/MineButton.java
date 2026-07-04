package com.kdt.mcgui;

import android.content.*;
import android.graphics.*;
import android.util.*;
import android.view.MotionEvent;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.core.content.res.ResourcesCompat;

import git.artdeell.mojo.R;

public class MineButton extends androidx.appcompat.widget.AppCompatButton {
	
	public MineButton(Context ctx) {
		this(ctx, null);
	}
	
	public MineButton(Context ctx, AttributeSet attrs) {
		super(ctx, attrs);
		init();
	}

	public void init() {
		setTypeface(ResourcesCompat.getFont(getContext(), R.font.noto_sans_bold));
		setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.premium_button_bg, null));
		setTextColor(Color.WHITE);
		setAllCaps(true);
		setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen._13ssp));

		// Start loop animation
		startAnimation(AnimationUtils.loadAnimation(getContext(), R.anim.button_loop));

		setOnTouchListener((v, event) -> {
			switch (event.getAction()) {
				case MotionEvent.ACTION_DOWN:
					v.clearAnimation();
					Animation pressAnim = AnimationUtils.loadAnimation(getContext(), R.anim.button_press);
					v.startAnimation(pressAnim);
					break;
				case MotionEvent.ACTION_UP:
				case MotionEvent.ACTION_CANCEL:
					Animation releaseAnim = AnimationUtils.loadAnimation(getContext(), R.anim.button_release);
					releaseAnim.setAnimationListener(new Animation.AnimationListener() {
						@Override public void onAnimationStart(Animation animation) {}
						@Override public void onAnimationRepeat(Animation animation) {}
						@Override public void onAnimationEnd(Animation animation) {
							v.startAnimation(AnimationUtils.loadAnimation(getContext(), R.anim.button_loop));
						}
					});
					v.startAnimation(releaseAnim);
					break;
			}
			return false;
		});
	}

}
