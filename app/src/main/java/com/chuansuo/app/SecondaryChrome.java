package com.chuansuo.app;

import android.app.Activity;
import android.view.Window;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

final class SecondaryChrome {
    private SecondaryChrome() {}

    static void applySystemBars(Activity activity, Window window) {
        int backgroundColor = activity.getColor(R.color.ui_canvas);
        window.setStatusBarColor(backgroundColor);
        window.setNavigationBarColor(backgroundColor);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true);
        controller.setAppearanceLightNavigationBars(true);
    }
}
