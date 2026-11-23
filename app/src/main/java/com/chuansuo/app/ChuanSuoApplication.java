package com.chuansuo.app;

import android.app.Application;
import android.content.Context;
import androidx.preference.PreferenceManager;
import com.chuansuo.utils.MapSettings;

import com.baidu.location.LocationClient;
import com.baidu.mapapi.CoordType;
import com.baidu.mapapi.SDKInitializer;

import com.elvishew.xlog.LogConfiguration;
import com.elvishew.xlog.LogLevel;
import com.elvishew.xlog.XLog;
import com.elvishew.xlog.printer.ConsolePrinter;
import com.elvishew.xlog.printer.Printer;
import com.elvishew.xlog.printer.file.FilePrinter;
import com.elvishew.xlog.printer.file.backup.FileSizeBackupStrategy;
import com.elvishew.xlog.printer.file.clean.FileLastModifiedCleanStrategy;
import com.elvishew.xlog.printer.file.naming.ChangelessFileNameGenerator;

import java.io.File;

public class ChuanSuoApplication extends Application {
    public static final String APP_NAME = "ChuanSuo";
    private static final String LOG_FILE_NAME = APP_NAME + ".log";
    private static final long MAX_TIME = 1000 * 60 * 60 * 24 * 3; // 3 days

    @Override
    public void onCreate() {
        super.onCreate();

        initXlog();

        // 百度地图 7.5 开始，要求必须同意隐私政策，默认为false
        SDKInitializer.setAgreePrivacy(this, true);
        // 百度定位 7.5 开始，要求必须同意隐私政策，默认为false(官方说可以统一为以上接口，但实际测试并不行，定位还是需要单独设置)
        LocationClient.setAgreePrivacy(true);
        String mapKey = MapSettings.apiKey(this);
        if (mapKey != null && !mapKey.trim().isEmpty()) {
            SDKInitializer.setApiKey(mapKey.trim());
            LocationClient.setKey(mapKey.trim());
        }
        // 在使用 SDK 各组间之前初始化 context 信息，传入 ApplicationContext
        SDKInitializer.initialize(this);

        SDKInitializer.setCoordType(CoordType.BD09LL);
    }

    static File logFile(Context context) {
        return new File(new File(context.getFilesDir(), "support-logs"), LOG_FILE_NAME);
    }

    static boolean isLoggingEnabled(Context context) {
        return !BuildConfig.DEBUG || !PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean("setting_log_off", false);
    }

    private void initXlog() {
        if (!isLoggingEnabled(this)) {
            XLog.init(LogLevel.NONE);
            return;
        }
        File file = logFile(this);
        File directory = file.getParentFile();
        if (!directory.isDirectory() && !directory.mkdirs()) {
            XLog.init(LogLevel.NONE);
            return;
        }

        LogConfiguration config = new LogConfiguration.Builder()
                .logLevel(BuildConfig.DEBUG ? LogLevel.ALL : LogLevel.WARN)
                .tag(APP_NAME)
                .build();
        Printer filePrinter = new FilePrinter.Builder(directory.getAbsolutePath())
                .fileNameGenerator(new ChangelessFileNameGenerator(file.getName()))
                .backupStrategy(new FileSizeBackupStrategy(256 * 1024))
                .cleanStrategy(new FileLastModifiedCleanStrategy(MAX_TIME))
                .build();
        if (BuildConfig.DEBUG) {
            XLog.init(config, new ConsolePrinter(), filePrinter);
        } else {
            XLog.init(config, filePrinter);
        }
    }
}
