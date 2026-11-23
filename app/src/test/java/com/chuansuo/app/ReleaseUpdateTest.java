package com.chuansuo.app;

import android.app.Application;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class ReleaseUpdateTest {
    private static final String REPOSITORY = "https://github.com/example/chuansuo";
    private static final String APK = "ChuanSuo_1.10.0_arm64-v8a_release_signed.apk";
    private static final String DOWNLOAD = REPOSITORY + "/releases/download/v1.10.0/" + APK;

    @Test
    public void versionsAreComparedNumericallyWithoutUsingTheTitle() {
        assertTrue(ReleaseUpdate.compareVersions("v1.10.0", "1.9.9") > 0);
        assertEquals(0, ReleaseUpdate.compareVersions("v1.0.0", "1.0.0"));
        assertTrue(ReleaseUpdate.compareVersions("v1.0.0", "1.0.1") < 0);
    }

    @Test
    public void selectsOnlyAnUploadedStandardApkForTheRelease() throws Exception {
        JSONArray assets = new JSONArray()
                .put(asset("source.zip", DOWNLOAD, "uploaded"))
                .put(asset(APK, DOWNLOAD, "new"))
                .put(asset(APK, "https://example.org/not-an-update.apk", "uploaded"));
        JSONObject release = new JSONObject().put("tag_name", "v1.10.0").put("assets", assets);
        assertEquals("", ReleaseUpdate.standardDownloadUrl(release, REPOSITORY));
        assets.put(asset(APK, DOWNLOAD, "uploaded"));
        assertEquals(DOWNLOAD, ReleaseUpdate.standardDownloadUrl(release, REPOSITORY));
    }

    private JSONObject asset(String name, String url, String state) throws Exception {
        return new JSONObject().put("name", name).put("browser_download_url", url).put("state", state);
    }
}
