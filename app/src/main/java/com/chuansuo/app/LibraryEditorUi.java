package com.chuansuo.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;

final class LibraryEditorUi {
    private final Context context;
    final int accent, canvas, surface, ink, muted, line, soft, pin, onPrimary;

    LibraryEditorUi(Context context) {
        this.context = context;
        accent = context.getColor(R.color.colorAccent);
        canvas = context.getColor(R.color.ui_canvas);
        surface = context.getColor(R.color.ui_surface);
        ink = context.getColor(R.color.ui_ink);
        muted = context.getColor(R.color.ui_muted);
        line = context.getColor(R.color.ui_line);
        soft = context.getColor(R.color.ui_soft);
        pin = context.getColor(R.color.ui_pin);
        onPrimary = context.getColor(R.color.ui_on_primary);
    }

    void prepareWindow(AppCompatActivity activity) {
        if (activity.getSupportActionBar() != null) activity.getSupportActionBar().hide();
        SecondaryChrome.applySystemBars(activity, activity.getWindow());
    }

    int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    void pad(View view, int start, int top, int end, int bottom) {
        view.setPaddingRelative(dp(start), dp(top), dp(end), dp(bottom));
    }

    LinearLayout column() {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    LinearLayout row() {
        LinearLayout view = new LinearLayout(context);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    LinearLayout.LayoutParams fillWeight() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
    }

    void add(LinearLayout parent, View view, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(topMargin);
        parent.addView(view, params);
    }

    GradientDrawable shape(int fill, int radius, boolean bordered) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radius));
        if (bordered) shape.setStroke(dp(1), line);
        return shape;
    }

    View divider() {
        View divider = new View(context);
        divider.setBackgroundColor(line);
        divider.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(1)));
        return divider;
    }

    TextView text(CharSequence value, int size, int color, boolean bold) {
        TextView label = new TextView(context);
        label.setText(value);
        label.setTextSize(size);
        label.setTextColor(color);
        if (bold) label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return label;
    }

    Drawable icon(int resource, int color, int size) {
        Drawable icon = context.getDrawable(resource).mutate();
        icon.setTint(color);
        icon.setBounds(0, 0, dp(size), dp(size));
        return icon;
    }

    void decorateIcon(TextView view, int resource, int color, int size) {
        view.setCompoundDrawablesRelative(icon(resource, color, size), null, null, null);
        view.setCompoundDrawablePadding(dp(8));
    }

    void tab(Button button, boolean current) {
        button.setSelected(current);
        button.setTextColor(current ? accent : muted);
        button.setTypeface(Typeface.DEFAULT, current ? Typeface.BOLD : Typeface.NORMAL);
        button.setBackground(ripple(current ? soft : canvas, 12));
    }

    void field(EditText view) {
        view.setMinHeight(dp(48));
        view.setTextSize(16);
        view.setTextColor(ink);
        view.setHintTextColor(muted);
        view.setBackgroundTintList(null);
        view.setBackground(shape(surface, 12, true));
        pad(view, 12, 6, 12, 6);
    }

    Button button(CharSequence label, boolean primary, int icon, Runnable action) {
        Button button = new Button(context);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTextColor(primary ? onPrimary : accent);
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        pad(button, 10, 0, 10, 0);
        button.setStateListAnimator(null);
        button.setBackgroundTintList(null);
        button.setDefaultFocusHighlightEnabled(false);
        button.setBackground(ripple(primary ? accent : soft, 12));
        if (icon != 0) decorateIcon(button, icon, primary ? onPrimary : accent, 18);
        button.setOnFocusChangeListener((v, focused) -> {
            int flags = button.getPaintFlags() & ~Paint.UNDERLINE_TEXT_FLAG;
            button.setPaintFlags(focused && !v.isInTouchMode() ? flags | Paint.UNDERLINE_TEXT_FLAG : flags);
        });
        button.setOnClickListener(v -> action.run());
        return button;
    }

    ImageButton iconButton(int resource, String description, Runnable action) {
        ImageButton button = new ImageButton(context);
        button.setImageDrawable(icon(resource, ink, 24));
        button.setContentDescription(description);
        button.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        pad(button, 12, 12, 12, 12);
        button.setBackgroundTintList(null);
        button.setDefaultFocusHighlightEnabled(false);
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_focused}, ripple(soft, 12));
        background.addState(new int[0], ripple(android.graphics.Color.TRANSPARENT, 12));
        button.setBackground(background);
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        button.setOnClickListener(v -> action.run());
        return button;
    }

    LinearLayout toolbar(CharSequence title, Runnable back) {
        LinearLayout toolbar = row();
        toolbar.addView(iconButton(R.drawable.ic_back, "返回", back));
        TextView heading = text(title, 22, ink, true);
        ViewCompat.setAccessibilityHeading(heading, true);
        toolbar.addView(heading, fillWeight());
        return toolbar;
    }

    void list(ListView list) {
        list.setDivider(null);
        list.setClipToPadding(false);
        list.setCacheColorHint(android.graphics.Color.TRANSPARENT);
        list.setDrawSelectorOnTop(true);
        StateListDrawable selector = new StateListDrawable();
        selector.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(0x0A173733));
        selector.addState(new int[]{android.R.attr.state_focused}, new ColorDrawable(0x22173733));
        selector.addState(new int[0], new ColorDrawable(android.graphics.Color.TRANSPARENT));
        list.setSelector(selector);
    }

    private RippleDrawable ripple(int fill, int radius) {
        return new RippleDrawable(ColorStateList.valueOf(0x19173733),
                shape(fill, radius, false), shape(android.graphics.Color.WHITE, radius, false));
    }
}
