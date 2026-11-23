package com.chuansuo.app;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.net.Uri;

import com.chuansuo.utils.ShareUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class DiagnosticReportTest {
    @Test
    public void newInstallCanExportAReportBeforeAnyLogEvents() throws Exception {
        try (ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = controller.get();
            File report = DiagnosticReport.create(activity);
            String content = new String(Files.readAllBytes(report.toPath()), StandardCharsets.UTF_8);
            assertTrue(content.contains(BuildConfig.VERSION_NAME));
            assertTrue(content.contains(activity.getString(R.string.log_no_events)));
        }
    }

    @Test
    public void sharesAnAppLogCopyWithTemporaryReadAccess() throws Exception {
        try (ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = controller.get();
            File log = ChuanSuoApplication.logFile(activity);
            Files.createDirectories(log.toPath().getParent());
            Files.write(log.toPath(), "Baidu location diagnostic: 1-2".getBytes(StandardCharsets.UTF_8));

            File report = DiagnosticReport.create(activity);
            assertTrue(new String(Files.readAllBytes(report.toPath()), StandardCharsets.UTF_8)
                    .contains("Baidu location diagnostic: 1-2"));
            ShareUtils.shareFile(activity, report, "text/plain", "发送日志");
            Intent chooser = shadowOf(activity).getNextStartedActivity();
            assertEquals(Intent.ACTION_CHOOSER, chooser.getAction());

            Intent target = chooser.getParcelableExtra(Intent.EXTRA_INTENT);
            Uri attachment = target.getParcelableExtra(Intent.EXTRA_STREAM);
            assertEquals("content", attachment.getScheme());
            assertEquals("text/plain", target.getType());
            assertEquals(attachment, target.getClipData().getItemAt(0).getUri());
            assertTrue((target.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
            try (InputStream input = activity.getContentResolver().openInputStream(attachment)) {
                assertEquals('C', input.read());
            }
        }
    }
}
