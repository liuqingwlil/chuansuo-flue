package com.chuansuo.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigInteger;
import java.util.regex.Pattern;

final class ReleaseUpdate {
    private static final Pattern VERSION = Pattern.compile("v?[0-9]+\\.[0-9]+\\.[0-9]+");

    private ReleaseUpdate() { }

    static int compareVersions(String first, String second) {
        String[] left = versionParts(first);
        String[] right = versionParts(second);
        for (int i = 0; i < left.length; i++) {
            int order = new BigInteger(left[i]).compareTo(new BigInteger(right[i]));
            if (order != 0) return order;
        }
        return 0;
    }

    private static String[] versionParts(String tag) {
        if (!VERSION.matcher(tag).matches()) throw new IllegalArgumentException("Invalid release version");
        return tag.replaceFirst("^v", "").split("\\.");
    }

    static String standardDownloadUrl(JSONObject release, String repositoryUrl) {
        String tag = release.optString("tag_name");
        JSONArray assets = release.optJSONArray("assets");
        if (!VERSION.matcher(tag).matches() || assets == null || repositoryUrl.isEmpty()) return "";

        String version = tag.replaceFirst("^v", "");
        String name = "ChuanSuo_" + version + "_arm64-v8a_release_signed.apk";
        String downloadUrl = repositoryUrl + "/releases/download/" + tag + "/" + name;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset != null && name.equals(asset.optString("name"))
                    && "uploaded".equals(asset.optString("state"))
                    && downloadUrl.equals(asset.optString("browser_download_url"))) {
                return downloadUrl;
            }
        }
        return "";
    }
}
