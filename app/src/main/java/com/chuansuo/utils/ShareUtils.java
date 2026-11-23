package com.chuansuo.utils;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import java.io.File;

public class ShareUtils {
    /**
     * 返回uri
     */
    public static Uri getUriFromFile(Context context, File file) {
        String authority = context.getPackageName().concat(".fileProvider");
        return FileProvider.getUriForFile(context, authority, file);
    }

    public static void shareFile(Context context, File file, String mimeType, String title) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Uri uri = getUriFromFile(context, file);
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.setClipData(ClipData.newRawUri(title, uri));
        share.setType(mimeType);
        context.startActivity(Intent.createChooser(share, title));
    }

    public static void shareText(Context context, String title, String text) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("application/plain");
        share.putExtra(Intent.EXTRA_TEXT, text);
        share.putExtra(Intent.EXTRA_SUBJECT, title);
        context.startActivity(Intent.createChooser(share, title));
    }
}
