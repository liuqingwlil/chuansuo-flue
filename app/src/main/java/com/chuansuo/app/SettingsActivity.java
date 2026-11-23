package com.chuansuo.app;

import android.os.Bundle;
import android.view.Window;

import androidx.appcompat.widget.Toolbar;

public class SettingsActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_settings);
        SecondaryChrome.applySystemBars(this, getWindow());

        Toolbar toolbar = findViewById(R.id.settings_toolbar);
        toolbar.setNavigationOnClickListener(view -> finish());

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new FragmentSettings())
                    .commit();
        }
    }
}
