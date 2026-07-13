package com.kdt.mcgui;

import android.content.*;
import android.graphics.*;
import android.animation.AnimatorInflater;
import android.util.*;
import android.view.MotionEvent;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.core.content.res.ResourcesCompat;
import android.graphics.drawable.Drawable;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.SoundManager;

public class MineButton extends androidx.appcompat.widget.AppCompatButton {
	
	public MineButton(Context ctx) {
		this(ctx, null);
	}
	
	public MineButton(Context ctx, AttributeSet attrs) {
		super(ctx, attrs);
		init();
	}

	private boolean mIsGlassBlack = false;

	public void init() {
		setTypeface(ResourcesCompat.getFont(getContext(), R.font.noto_sans_bold));

		// If background is not set, set premium_button_bg
		if (getBackground() == null) {
			setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.premium_button_bg, null));
		} else {
			// We can check if it is premium_glass_black_bg
			try {
				// Some layouts use premium_glass_black_bg as button background which has black/translucent base
				// We want to keep white text on premium_glass_black_bg and black text on premium_button_bg
				mIsGlassBlack = true;
			} catch (Exception e) {}
		}

		// Scale the icons to prevent them from becoming "Big" and taking up the whole button
		Drawable[] drawables = getCompoundDrawables();
		int iconSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 28, getResources().getDisplayMetrics());
		for (Drawable d : drawables) {
			if (d != null) {
				d.setBounds(0, 0, iconSize, iconSize);
			}
		}
		setCompoundDrawables(drawables[0], drawables[1], drawables[2], drawables[3]);

		// If the background is glass black/translucent, keep text color white; otherwise, set to black for high contrast against the silver/white button!
		if (mIsGlassBlack) {
			setTextColor(Color.WHITE);
		} else {
			setTextColor(Color.BLACK);
		}
		setAllCaps(true);
		setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen._13ssp));

		// Loop Animation
		android.animation.Animator pulse = AnimatorInflater.loadAnimator(getContext(), R.animator.button_loop_pulse);
		pulse.setTarget(this);
		pulse.start();

		setOnTouchListener((v, event) -> {
			switch (event.getAction()) {
				case MotionEvent.ACTION_DOWN:
					Animation pressAnim = AnimationUtils.loadAnimation(getContext(), R.anim.button_press);
					v.startAnimation(pressAnim);
					SoundManager.playClick();
					break;
				case MotionEvent.ACTION_UP:
				case MotionEvent.ACTION_CANCEL:
					Animation releaseAnim = AnimationUtils.loadAnimation(getContext(), R.anim.button_release);
					v.startAnimation(releaseAnim);
					break;
			}
			return false;
		});
	}

}
