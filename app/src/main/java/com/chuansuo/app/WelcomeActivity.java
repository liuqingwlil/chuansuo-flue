package com.chuansuo.app;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

import com.chuansuo.utils.GoUtils;

import java.util.ArrayList;
import java.util.List;

public class WelcomeActivity extends AppCompatActivity {
    private static final String KEY_ACCEPT_AGREEMENT = "KEY_ACCEPT_AGREEMENT";
    private static final String KEY_ACCEPT_PRIVACY = "KEY_ACCEPT_PRIVACY";
    private static final int SDK_PERMISSION_REQUEST = 127;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE
    };

    private SharedPreferences preferences;
    private CheckBox checkBox;
    private boolean agreementAccepted;
    private boolean privacyAccepted;
    private boolean renderingConsent;
    private boolean permissionsGranted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);
        SecondaryChrome.applySystemBars(this, getWindow());

        PreferenceManager.setDefaultValues(this, R.xml.preferences_main, false);

        TextView credit = findViewById(R.id.welcome_credit);
        credit.setText(getString(R.string.welcome_credit, getString(R.string.app_author)));
        findViewById(R.id.startButton).setOnClickListener(view -> startMainActivity());
        findViewById(R.id.welcome_agreement).setOnClickListener(view -> showPolicy(true));
        findViewById(R.id.welcome_privacy).setOnClickListener(view -> showPolicy(false));

        preferences = getSharedPreferences(KEY_ACCEPT_AGREEMENT, MODE_PRIVATE);
        agreementAccepted = preferences.getBoolean(KEY_ACCEPT_AGREEMENT, false);
        privacyAccepted = preferences.getBoolean(KEY_ACCEPT_PRIVACY, false);
        checkBox = findViewById(R.id.check_agreement);
        renderConsent();
        checkBox.setOnCheckedChangeListener((button, checked) -> {
            if (renderingConsent) return;
            if (checked && !hasConsent()) {
                GoUtils.DisplayToast(this, getString(R.string.app_error_read));
                renderConsent();
            } else if (!checked) {
                agreementAccepted = false;
                privacyAccepted = false;
                saveConsent();
            }
        });
        if (hasConsent()) checkDefaultPermissions();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != SDK_PERMISSION_REQUEST) return;

        permissionsGranted = grantResults.length == permissions.length && grantResults.length > 0;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) permissionsGranted = false;
        }
        if (!permissionsGranted) {
            GoUtils.DisplayToast(this, getString(R.string.app_error_permission));
        }
    }

    private void checkDefaultPermissions() {
        List<String> missingPermissions = new ArrayList<>();
        for (String permission : REQUIRED_PERMISSIONS) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }
        permissionsGranted = missingPermissions.isEmpty();
        if (!permissionsGranted) {
            requestPermissions(missingPermissions.toArray(new String[0]), SDK_PERMISSION_REQUEST);
        }
    }

    private void startMainActivity() {
        if (!hasConsent()) {
            GoUtils.DisplayToast(this, getString(R.string.app_error_agreement));
            return;
        }
        if (!GoUtils.isNetworkAvailable(this)) {
            GoUtils.DisplayToast(this, getString(R.string.app_error_network));
            return;
        }
        if (!GoUtils.isGpsOpened(this)) {
            GoUtils.DisplayToast(this, getString(R.string.app_error_gps));
            return;
        }
        if (!permissionsGranted) {
            checkDefaultPermissions();
            return;
        }
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private boolean hasConsent() {
        return agreementAccepted && privacyAccepted;
    }

    private void renderConsent() {
        renderingConsent = true;
        checkBox.setChecked(hasConsent());
        renderingConsent = false;
    }

    private void saveConsent() {
        preferences.edit()
                .putBoolean(KEY_ACCEPT_AGREEMENT, agreementAccepted)
                .putBoolean(KEY_ACCEPT_PRIVACY, privacyAccepted)
                .apply();
    }

    private void showPolicy(boolean agreement) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Material_Light_NoActionBar);
        dialog.setContentView(R.layout.user_agreement);

        Toolbar toolbar = dialog.findViewById(R.id.policy_toolbar);
        toolbar.setTitle(agreement ? R.string.app_agreement : R.string.app_privacy);
        toolbar.setNavigationOnClickListener(view -> dialog.dismiss());
        TextView content = dialog.findViewById(R.id.tv_content);
        content.setText(agreement ? R.string.app_agreement_content : R.string.app_privacy_content);
        dialog.findViewById(R.id.tv_cancel).setOnClickListener(view -> setPolicyResult(dialog, agreement, false));
        dialog.findViewById(R.id.tv_agree).setOnClickListener(view -> setPolicyResult(dialog, agreement, true));

        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawableResource(R.color.ui_canvas);
            SecondaryChrome.applySystemBars(this, window);
        }
    }

    private void setPolicyResult(Dialog dialog, boolean agreement, boolean accepted) {
        if (agreement) {
            agreementAccepted = accepted;
        } else {
            privacyAccepted = accepted;
        }
        saveConsent();
        renderConsent();
        dialog.dismiss();
        if (hasConsent()) checkDefaultPermissions();
    }

}
