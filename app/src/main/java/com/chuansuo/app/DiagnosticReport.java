package com.chuansuo.app;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.concurrent.TimeUnit;

final class DiagnosticReport {
    private DiagnosticReport() { }

    static File create(Context context) throws IOException {
        File directory = new File(context.getCacheDir(), "feedback");
        Files.createDirectories(directory.toPath());
        long oldest = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1);
        File[] oldReports = directory.listFiles(file ->
                file.getName().startsWith("ChuanSuo-log-") && file.lastModified() < oldest);
        if (oldReports != null) {
            for (File old : oldReports) Files.deleteIfExists(old.toPath());
        }

        File report = File.createTempFile("ChuanSuo-log-", ".txt", directory);
        try (OutputStream output = Files.newOutputStream(report.toPath())) {
            String header = "ChuanSuo " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")\n"
                    + "Android API " + Build.VERSION.SDK_INT + "\n\n";
            output.write(header.getBytes(StandardCharsets.UTF_8));

            File log = ChuanSuoApplication.logFile(context);
            long copied = 0;
            if (ChuanSuoApplication.isLoggingEnabled(context)) {
                try {
                    copied = Files.copy(log.toPath(), output);
                } catch (NoSuchFileException ignored) {
                    // No events yet, or the active file was rotated while exporting.
                }
            }
            if (copied == 0) {
                output.write(context.getString(R.string.log_no_events).getBytes(StandardCharsets.UTF_8));
            }
        }
        return report;
    }
}
