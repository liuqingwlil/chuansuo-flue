package com.chuansuo.app;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.chuansuo.utils.GoUtils;

import java.util.Objects;

public class FragmentSettings extends PreferenceFragmentCompat {

    // Set a non-empty decimal EditTextPreference
    private void setupDecimalEditTextPreference(EditTextPreference preference) {
        if (preference != null) {
            preference.setSummaryProvider((Preference.SummaryProvider<EditTextPreference>) EditTextPreference::getText);
            preference.setOnBindEditTextListener(editText -> {
                editText.setInputType(InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_CLASS_NUMBER);
                editText.setSelection(editText.length());
            });
            preference.setOnPreferenceChangeListener((pref, newValue) -> {
                if (newValue.toString().trim().isEmpty()) {
                    GoUtils.DisplayToast(this.getContext(), getResources().getString(R.string.app_error_input_null));
                    return false;
                }
                return true;
            });
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences_main);
        if (BuildConfig.DEBUG) {
            addPreferencesFromResource(R.xml.preferences_developer);
            setupDeveloperPreferences();
        }

        ListPreference pfJoystick = findPreference("setting_joystick_type");
        if (pfJoystick != null) {
            // 使用自定义 SummaryProvider
            pfJoystick.setSummaryProvider((Preference.SummaryProvider<ListPreference>) preference -> Objects.requireNonNull(preference.getEntry()));
            pfJoystick.setOnPreferenceChangeListener((preference, newValue) -> !newValue.toString().trim().isEmpty());
        }

        EditTextPreference pfWalk = findPreference("setting_walk");
        setupDecimalEditTextPreference(pfWalk);

        EditTextPreference pfRun = findPreference("setting_run");
        setupDecimalEditTextPreference(pfRun);

        EditTextPreference pfBike = findPreference("setting_bike");
        setupDecimalEditTextPreference(pfBike);

        EditTextPreference pfAltitude = findPreference("setting_altitude");
        setupDecimalEditTextPreference(pfAltitude);

        EditTextPreference pfLatOffset = findPreference("setting_lat_max_offset");
        setupDecimalEditTextPreference(pfLatOffset);

        EditTextPreference pfLonOffset = findPreference("setting_lon_max_offset");
        setupDecimalEditTextPreference(pfLonOffset);

        EditTextPreference pfPosHisValid = findPreference("setting_history_expiration");
        setupDecimalEditTextPreference(pfPosHisValid);

        EditTextPreference city = findPreference("setting_search_city");
        if (city != null) city.setSummaryProvider(preference -> {
            String value = ((EditTextPreference) preference).getText();
            return value == null || value.trim().isEmpty()
                    ? getString(R.string.setting_search_city_summary) : value;
        });

        // 设置版本号
        String verName;
        verName = GoUtils.getVersionName(FragmentSettings.this.getContext());
        Preference pfVersion = findPreference("setting_version");
        if (pfVersion != null) {
            pfVersion.setSummary(verName);
        }

        Preference projectUrl = findPreference("setting_project_url");
        if (projectUrl != null) {
            String url = ProjectIdentity.homeUrl();
            if (url.isEmpty()) {
                projectUrl.setSummary(R.string.setting_project_url_unavailable);
                projectUrl.setSelectable(false);
            } else {
                projectUrl.setSummary(url);
                projectUrl.setOnPreferenceClickListener(preference -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (ActivityNotFoundException exception) {
                        GoUtils.DisplayToast(requireContext(), getString(R.string.setting_project_no_browser));
                    }
                    return true;
                });
            }
        }
    }

    private void setupDeveloperPreferences() {
        EditTextPreference mapKey = findPreference("setting_map_key");
        if (mapKey != null) {
            mapKey.setOnBindEditTextListener(edit -> edit.setInputType(
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD));
            mapKey.setOnPreferenceChangeListener((preference, value) -> {
                GoUtils.DisplayToast(getContext(), getString(R.string.setting_map_restart));
                return true;
            });
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setDivider(new ColorDrawable(requireContext().getColor(R.color.ui_line)));
        setDividerHeight(1);
        getListView().setClipToPadding(false);
        float density = requireContext().getResources().getDisplayMetrics().density;
        getListView().setPadding(0, 0, 0, Math.round(20 * density));
    }
}
