package com.chuansuo.app;

import android.app.Application;
import android.app.Dialog;
import android.widget.CheckBox;

import androidx.preference.Preference;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class ProjectPresentationTest {
    @Test
    public void redesignedOnboardingRequiresBothPolicies() {
        try (ActivityController<WelcomeActivity> controller = Robolectric.buildActivity(WelcomeActivity.class).setup()) {
            WelcomeActivity activity = controller.get();
            CheckBox consent = activity.findViewById(R.id.check_agreement);
            assertFalse(consent.isChecked());

            activity.findViewById(R.id.welcome_agreement).performClick();
            Dialog agreement = ShadowDialog.getLatestDialog();
            agreement.findViewById(R.id.tv_agree).performClick();
            assertFalse(consent.isChecked());

            activity.findViewById(R.id.welcome_privacy).performClick();
            Dialog privacy = ShadowDialog.getLatestDialog();
            privacy.findViewById(R.id.tv_agree).performClick();
            assertTrue(consent.isChecked());
        }
    }

    @Test
    public void aboutShowsProjectNameCreditAndConfiguredHome() {
        try (ActivityController<SettingsActivity> controller = Robolectric.buildActivity(SettingsActivity.class).setup()) {
            SettingsActivity activity = controller.get();
            activity.getSupportFragmentManager().executePendingTransactions();
            FragmentSettings settings = (FragmentSettings) activity.getSupportFragmentManager().findFragmentById(R.id.settings);
            assertNotNull(settings);

            assertEquals("穿梭", settings.findPreference("setting_project_name").getSummary());
            assertEquals("YH", settings.findPreference("setting_author").getSummary());

            Preference projectHome = settings.findPreference("setting_project_url");
            assertNotNull(projectHome);
            String configuredUrl = ProjectIdentity.homeUrl();
            if (configuredUrl.isEmpty()) {
                assertFalse(projectHome.isSelectable());
            } else {
                assertEquals(configuredUrl, projectHome.getSummary());
                assertNotNull(projectHome.getOnPreferenceClickListener());
            }
        }
    }
}
