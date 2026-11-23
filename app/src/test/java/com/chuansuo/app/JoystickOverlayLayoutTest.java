package com.chuansuo.app;

import android.app.Application;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class JoystickOverlayLayoutTest {
    @Test
    public void plainInflaterLoadsCompactBubbleAndToolbarIcons() {
        ContextThemeWrapper context = new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.AppTheme);
        LayoutInflater inflater = LayoutInflater.from(context);
        ImageButton bubble = (ImageButton) inflater.inflate(R.layout.joystick_bubble, new FrameLayout(context), false);
        int expectedSize = Math.round(48 * context.getResources().getDisplayMetrics().density);
        assertEquals(expectedSize, bubble.getLayoutParams().width);
        assertEquals(expectedSize, bubble.getLayoutParams().height);
        assertEquals(View.VISIBLE, bubble.getVisibility());
        assertNotNull(bubble.getDrawable());

        View panel = inflater.inflate(R.layout.joystick, null);
        assertVisibleIcon(panel, R.id.joystick_history);
        assertVisibleIcon(panel, R.id.joystick_position);
        assertVisibleIcon(panel, R.id.joystick_collapse);
    }

    private static void assertVisibleIcon(View panel, int id) {
        ImageButton button = panel.findViewById(id);
        assertEquals(View.VISIBLE, button.getVisibility());
        assertNotNull(button.getDrawable());
    }
}
