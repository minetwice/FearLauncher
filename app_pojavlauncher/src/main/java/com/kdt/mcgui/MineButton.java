package com.kdt.mcgui;

import android.content.*;
import android.graphics.*;
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

	public void init() {
		setTypeface(ResourcesCompat.getFont(getContext(), R.font.noto_sans_bold));

		// If background is not set, set premium_button_bg
		if (getBackground() == null) {
			setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.premium_button_bg, null));
		}

		// Dynamically skin button glow/stroke border matching the selected theme color directly from the Color Wheel (Step 1)
		try {
			SharedPreferences prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(getContext());
			int primaryColor = prefs.getInt("launcher_theme_color_argb", 0xFF00F0FF);
			if (getBackground() != null) {
				getBackground().setColorFilter(new android.graphics.PorterDuffColorFilter(primaryColor, android.graphics.PorterDuff.Mode.SRC_ATOP));
			}
		} catch (Exception e) {
			// fallback silently
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

		// All buttons now feature premium white text for dark glass/blue neon contrast
		setTextColor(Color.WHITE);
		setAllCaps(true);
		setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen._13ssp));

		// On click TouchListener animations (No stretching/looping)
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
