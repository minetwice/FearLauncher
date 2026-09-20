package net.kdt.pojavlauncher.recorder;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;

import git.artdeell.mojo.R;

/**
 * MineButton variant with compact text for the recorder dialogs.
 * MineButton hardcodes 13ssp text in its constructor (ignoring the XML
 * android:textSize), which overflows small chips out of the screen.
 * This subclass restores a compact size right after inflation.
 */
public class SmallButton extends com.kdt.mcgui.MineButton {

    public SmallButton(Context context) {
        super(context);
        compact();
    }

    public SmallButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        compact();
    }

    public SmallButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        compact();
    }

    private void compact() {
        setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen._8ssp));
    }
}
