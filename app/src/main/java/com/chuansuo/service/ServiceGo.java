package com.chuansuo.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.Manifest;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.widget.Toast;
import com.chuansuo.database.LibraryRepository;
import com.chuansuo.model.Coordinate;
import com.chuansuo.model.LibraryItem;
import com.chuansuo.simulation.RoutePlayer;
import com.chuansuo.simulation.SimulationEngine;
import com.chuansuo.utils.GoUtils;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.location.provider.ProviderProperties;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.elvishew.xlog.XLog;
import com.chuansuo.app.MainActivity;
import com.chuansuo.app.LibraryActivity;
import com.chuansuo.app.R;
import com.chuansuo.joystick.JoyStick;

public class ServiceGo extends Service {
    // 定位相关变量
    public static final double DEFAULT_LAT = 36.667662;
    public static final double DEFAULT_LNG = 117.027707;
    public static final double DEFAULT_ALT = 55.0D;
    public static final float DEFAULT_BEA = 0.0F;
    private double mCurLat = DEFAULT_LAT;
    private double mCurLng = DEFAULT_LNG;
    private double mCurAlt = DEFAULT_ALT;
    private float mCurBea = DEFAULT_BEA;
    private double mSpeed = 1.2;        /* 默认的速度，单位 m/s */
    private static final int HANDLER_MSG_ID = 0;
    private static final String SERVICE_GO_HANDLER_NAME = "ServiceGoLocation";
    private LocationManager mLocManager;
    private HandlerThread mLocHandlerThread;
    private Handler mLocHandler;
    private volatile boolean isStop = true;
    private static volatile ServiceGo active;
    private volatile boolean running;
    private final SimulationEngine engine = new SimulationEngine();
    private final Handler ui = new Handler(android.os.Looper.getMainLooper());
    private String routeName = "";
    private long lastNotification;
    public static final String ACTION_ROUTE = "com.chuansuo.START_ROUTE";
    public static final String ACTION_TOGGLE = "com.chuansuo.TOGGLE_ROUTE";
    public static final String ACTION_STOP = "com.chuansuo.STOP_SIMULATION";
    public static final String ACTION_SPEED = "com.chuansuo.ROUTE_SPEED";

    public static boolean isRunning() {
        ServiceGo service = active;
        return service != null && service.running;
    }

    public static boolean hasActiveRoute() {
        return activeRoute() != null;
    }

    public static boolean isActiveRoutePaused() {
        RoutePlayer route = activeRoute();
        return route != null && route.state() == RoutePlayer.State.PAUSED;
    }

    public static boolean isActiveRouteFinished() {
        RoutePlayer route = activeRoute();
        return route != null && route.state() == RoutePlayer.State.FINISHED;
    }

    public static Double activeRouteProgress() {
        RoutePlayer route = activeRoute();
        return route == null ? null : route.progress();
    }

    public static Coordinate currentPosition() {
        ServiceGo service = active;
        return service == null || !service.running ? null : service.engine.currentPosition();
    }

    private static RoutePlayer activeRoute() {
        ServiceGo service = active;
        return service == null || !service.running ? null : service.engine.route();
    }

    public static String status() {
        ServiceGo service = active;
        if (service == null || !service.running) return "模拟服务未启动";
        RoutePlayer player = service.engine.route();
        if (player == null) return service.engine.mode() == SimulationEngine.Mode.JOYSTICK ? "摇杆移动运行中" : "固定位置模拟运行中";
        String state = player.state() == RoutePlayer.State.RUNNING ? "移动中" : player.state() == RoutePlayer.State.PAUSED ? "已暂停" : "已到达终点";
        return String.format(java.util.Locale.CHINA, "%s · %s · %.1f%% · %.1f 米/秒", service.routeName, state, player.progress() * 100, player.speed());
    }
    private static boolean checkPermissions(Context context) {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "请先授予定位权限", Toast.LENGTH_LONG).show(); return false;
        }
        if (!GoUtils.isGpsOpened(context)) { GoUtils.showEnableGpsDialog(context); return false; }
        if (!Settings.canDrawOverlays(context)) { GoUtils.showEnableFloatWindowDialog(context); return false; }
        // Do not probe/remove an active test provider while changing simulation mode.
        if (!isRunning() && !GoUtils.isAllowMockLocation(context)) { GoUtils.showEnableMockLocationDialog(context); return false; }
        return true;
    }
    public static void startRoute(Context context, long id) {
        if (checkPermissions(context)) context.startForegroundService(new Intent(context, ServiceGo.class).setAction(ACTION_ROUTE).putExtra("route_id", id));
    }
    public static void startPlace(Context context, Coordinate point) {
        if (checkPermissions(context)) context.startForegroundService(new Intent(context, ServiceGo.class)
                .putExtra(MainActivity.LNG_MSG_ID, point.longitude).putExtra(MainActivity.LAT_MSG_ID, point.latitude).putExtra(MainActivity.ALT_MSG_ID, point.altitude));
    }
    public static void control(Context context, String action, double speed) {
        if (!isRunning()) { Toast.makeText(context, "模拟服务尚未启动", Toast.LENGTH_SHORT).show(); return; }
        if (!ACTION_STOP.equals(action) && !hasActiveRoute()) {
            Toast.makeText(context, "当前没有运行中的线路", Toast.LENGTH_SHORT).show(); return;
        }
        context.startService(new Intent(context, ServiceGo.class).setAction(action).putExtra("speed", speed));
    }
    // 通知栏消息
    private static final int SERVICE_GO_NOTE_ID = 1;
    private static final String SERVICE_GO_NOTE_ACTION_JOYSTICK_SHOW = "ShowJoyStick";
    private static final String SERVICE_GO_NOTE_ACTION_JOYSTICK_HIDE = "HideJoyStick";
    private static final String SERVICE_GO_NOTE_CHANNEL_ID = "SERVICE_GO_NOTE";
    private static final String SERVICE_GO_NOTE_CHANNEL_NAME = "模拟定位";
    private NoteActionReceiver mActReceiver;
    // 摇杆相关
    private JoyStick mJoyStick;

    private final ServiceGoBinder mBinder = new ServiceGoBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mLocManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        active = this;

        initGoLocation();

        initNotification();

        initJoyStick();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSimulation(); return START_NOT_STICKY; }
        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) { stopSimulation(); return START_NOT_STICKY; }
        if (!running && (ACTION_TOGGLE.equals(action) || ACTION_SPEED.equals(action))) { stopSimulation(); return START_NOT_STICKY; }
        long now = SystemClock.elapsedRealtime();
        if (ACTION_TOGGLE.equals(action)) {
            RoutePlayer player = engine.route();
            if (player != null && player.state() == RoutePlayer.State.PAUSED) engine.resume(now); else engine.pause(now);
            updateNotification(); return START_NOT_STICKY;
        }
        if (ACTION_SPEED.equals(action)) {
            try { engine.setRouteSpeed(intent.getDoubleExtra("speed", 1.2), now); }
            catch (IllegalArgumentException e) { Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show(); }
            updateNotification(); return START_NOT_STICKY;
        }
        try {
            mJoyStick.stopMovement();
            if (ACTION_ROUTE.equals(action)) {
                try (LibraryRepository db = new LibraryRepository(this)) {
                    LibraryItem route = db.get(intent.getLongExtra("route_id", 0));
                    if (route == null || !route.route) throw new IllegalArgumentException("线路不存在");
                    route.validate(); routeName = route.name;
                    engine.startRoute(new RoutePlayer(route.points, route.speed, route.loop, now));
                }
            } else {
                engine.setPosition(new Coordinate(intent.getDoubleExtra(MainActivity.LNG_MSG_ID, DEFAULT_LNG),
                        intent.getDoubleExtra(MainActivity.LAT_MSG_ID, DEFAULT_LAT), intent.getDoubleExtra(MainActivity.ALT_MSG_ID, DEFAULT_ALT)));
            }
            if (!running) { removeTestProviderNetwork(); addTestProviderNetwork(); removeTestProviderGPS(); addTestProviderGPS(); }
            running = true; isStop = false;
            Coordinate point = engine.tick(now);
            mJoyStick.setCurrentPosition(point.longitude, point.latitude, point.altitude); mJoyStick.show();
            mLocHandler.removeMessages(HANDLER_MSG_ID); mLocHandler.sendEmptyMessage(HANDLER_MSG_ID);
            updateNotification();
        } catch (Exception e) {
            Toast.makeText(this, "无法启动：" + e.getMessage(), Toast.LENGTH_LONG).show();
            if (!running) stopSimulation();
        }
        return START_NOT_STICKY;
    }

    private void stopSimulation() {
        isStop = true; running = false; engine.stopRoute();
        mLocHandler.removeMessages(HANDLER_MSG_ID); mJoyStick.stopMovement(); mJoyStick.hide();
        ui.removeCallbacksAndMessages(null);
        removeTestProviderNetwork(); removeTestProviderGPS();
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
    }

    @Override
    public void onDestroy() {
        isStop = true;
        running = false;
        if (active == this) active = null;
        ui.removeCallbacksAndMessages(null);
        mLocHandler.removeMessages(HANDLER_MSG_ID);
        mLocHandlerThread.quit();

        mJoyStick.destroy();

        removeTestProviderNetwork();
        removeTestProviderGPS();

        unregisterReceiver(mActReceiver);
        stopForeground(STOP_FOREGROUND_REMOVE);

        super.onDestroy();
    }

    private void initNotification() {
        mActReceiver = new NoteActionReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(getPackageName() + "." + SERVICE_GO_NOTE_ACTION_JOYSTICK_SHOW);
        filter.addAction(getPackageName() + "." + SERVICE_GO_NOTE_ACTION_JOYSTICK_HIDE);
        registerReceiver(mActReceiver, filter);

        NotificationChannel mChannel = new NotificationChannel(SERVICE_GO_NOTE_CHANNEL_ID, SERVICE_GO_NOTE_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            notificationManager.createNotificationChannel(mChannel);
        }

    }

    private void updateNotification() {
        boolean routeActive = hasActiveRoute();
        boolean joystickVisible = mBinder.isJoystickVisible();
        Intent clickIntent = new Intent(this, routeActive ? LibraryActivity.class : MainActivity.class);
        PendingIntent click = PendingIntent.getActivity(this, 1, clickIntent, PendingIntent.FLAG_IMMUTABLE);
        PendingIntent show = PendingIntent.getBroadcast(this, 2, new Intent(getPackageName() + "." + SERVICE_GO_NOTE_ACTION_JOYSTICK_SHOW).setPackage(getPackageName()), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent hide = PendingIntent.getBroadcast(this, 3, new Intent(getPackageName() + "." + SERVICE_GO_NOTE_ACTION_JOYSTICK_HIDE).setPackage(getPackageName()), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent pause = PendingIntent.getService(this, 4, new Intent(this, ServiceGo.class).setAction(ACTION_TOGGLE), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 5, new Intent(this, ServiceGo.class).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(this, SERVICE_GO_NOTE_CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name)).setContentText(status()).setContentIntent(click)
                .setSmallIcon(R.drawable.ic_simulation_notification).setOnlyAlertOnce(true).setOngoing(true)
                .addAction(R.drawable.ic_joystick, joystickVisible ? "隐藏摇杆" : "显示摇杆",
                        joystickVisible ? hide : show);
        if (routeActive && !isActiveRouteFinished()) {
            boolean paused = isActiveRoutePaused();
            notification.addAction(paused ? R.drawable.ic_play : R.drawable.ic_pause,
                    paused ? "继续" : "暂停", pause);
        }
        notification.addAction(R.drawable.ic_stop, "停止", stop);
        if (running) startForeground(SERVICE_GO_NOTE_ID, notification.build());
    }

    private void initJoyStick() {
        mJoyStick = new JoyStick(this);
        mJoyStick.setListener(new JoyStick.JoyStickClickListener() {
            @Override
            public void onMoveInfo(double speed, double disLng, double disLat, double angle) {
                engine.move(speed, disLng, disLat, angle);
            }

            @Override
            public void onPositionInfo(double lng, double lat, double alt) {
                mJoyStick.stopMovement();
                engine.setPosition(new Coordinate(lng, lat, alt));
            }
        });
    }

    private void initGoLocation() {
        // 创建 HandlerThread 实例，第一个参数是线程的名字
        mLocHandlerThread = new HandlerThread(SERVICE_GO_HANDLER_NAME, Process.THREAD_PRIORITY_FOREGROUND);
        // 启动 HandlerThread 线程
        mLocHandlerThread.start();
        // Handler 对象与 HandlerThread 的 Looper 对象的绑定
        mLocHandler = new Handler(mLocHandlerThread.getLooper()) {
            // 这里的Handler对象可以看作是绑定在HandlerThread子线程中，所以handlerMessage里的操作是在子线程中运行的
            @Override
            public void handleMessage(@NonNull Message msg) {
                if (!isStop) {
                    long now = SystemClock.elapsedRealtime();
                    Coordinate point = engine.tick(now);
                    mCurLng = point.longitude; mCurLat = point.latitude; mCurAlt = point.altitude;
                    mCurBea = engine.bearing(); mSpeed = engine.speed();
                    setLocationNetwork(); setLocationGPS();
                    if (now - lastNotification >= 1000) {
                        lastNotification = now;
                        ui.post(() -> { if (running) updateNotification(); });
                    }
                    sendEmptyMessageDelayed(HANDLER_MSG_ID, 100);
                }
            }
        };

    }

    private void removeTestProviderGPS() {
        try {
            if (mLocManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                mLocManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false);
                mLocManager.removeTestProvider(LocationManager.GPS_PROVIDER);
            }
        } catch (Exception e) {
            XLog.e("SERVICEGO: ERROR - removeTestProviderGPS");
        }
    }

    // 注意下面临时添加 @SuppressLint("wrongconstant") 以处理 addTestProvider 参数值的 lint 错误
    @SuppressLint("wrongconstant")
    private void addTestProviderGPS() {
        try {
            // 注意，由于 android api 问题，下面的参数会提示错误(以下参数是通过相关API获取的真实GPS参数，不是随便写的)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mLocManager.addTestProvider(LocationManager.GPS_PROVIDER, false, true, false,
                        false, true, true, true, ProviderProperties.POWER_USAGE_HIGH, ProviderProperties.ACCURACY_FINE);
            } else {
                mLocManager.addTestProvider(LocationManager.GPS_PROVIDER, false, true, false,
                        false, true, true, true, Criteria.POWER_HIGH, Criteria.ACCURACY_FINE);
            }
            if (!mLocManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                mLocManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);
            }
        } catch (Exception e) {
            XLog.e("SERVICEGO: ERROR - addTestProviderGPS");
        }
    }

    private void setLocationGPS() {
        try {
            // 尽可能模拟真实的 GPS 数据
            Location loc = new Location(LocationManager.GPS_PROVIDER);
            loc.setAccuracy(Criteria.ACCURACY_FINE);    // 设定此位置的估计水平精度，以米为单位。
            loc.setAltitude(mCurAlt);                     // 设置高度，在 WGS 84 参考坐标系中的米
            loc.setBearing(mCurBea);                       // 方向（度）
            loc.setLatitude(mCurLat);                   // 纬度（度）
            loc.setLongitude(mCurLng);                  // 经度（度）
            loc.setTime(System.currentTimeMillis());    // 本地时间
            loc.setSpeed((float) mSpeed);
            loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            Bundle bundle = new Bundle();
            bundle.putInt("satellites", 7);
            loc.setExtras(bundle);

            mLocManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, loc);
        } catch (Exception e) {
            XLog.e("SERVICEGO: ERROR - setLocationGPS");
        }
    }

    private void removeTestProviderNetwork() {
        try {
            if (mLocManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                mLocManager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, false);
                mLocManager.removeTestProvider(LocationManager.NETWORK_PROVIDER);
            }
        } catch (Exception e) {
            XLog.e("SERVICEGO: ERROR - removeTestProviderNetwork");
        }
    }

    // 注意下面临时添加 @SuppressLint("wrongconstant") 以处理 addTestProvider 参数值的 lint 错误
    @SuppressLint("wrongconstant")
    private void addTestProviderNetwork() {
        try {
            // 注意，由于 android api 问题，下面的参数会提示错误(以下参数是通过相关API获取的真实NETWORK参数，不是随便写的)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mLocManager.addTestProvider(LocationManager.NETWORK_PROVIDER, true, false,
                        true, true, true, true,
                        true, ProviderProperties.POWER_USAGE_LOW, ProviderProperties.ACCURACY_COARSE);
            } else {
                mLocManager.addTestProvider(LocationManager.NETWORK_PROVIDER, true, false,
                        true, true, true, true,
                        true, Criteria.POWER_LOW, Criteria.ACCURACY_COARSE);
            }
            if (!mLocManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                mLocManager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, true);
            }
        } catch (SecurityException e) {
            XLog.e("SERVICEGO: ERROR - addTestProviderNetwork");
        }
    }

    private void setLocationNetwork() {
        try {
            // 尽可能模拟真实的 NETWORK 数据
            Location loc = new Location(LocationManager.NETWORK_PROVIDER);
            loc.setAccuracy(Criteria.ACCURACY_COARSE);  // 设定此位置的估计水平精度，以米为单位。
            loc.setAltitude(mCurAlt);                     // 设置高度，在 WGS 84 参考坐标系中的米
            loc.setBearing(mCurBea);                       // 方向（度）
            loc.setLatitude(mCurLat);                   // 纬度（度）
            loc.setLongitude(mCurLng);                  // 经度（度）
            loc.setTime(System.currentTimeMillis());    // 本地时间
            loc.setSpeed((float) mSpeed);
            loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());

            mLocManager.setTestProviderLocation(LocationManager.NETWORK_PROVIDER, loc);
        } catch (Exception e) {
            XLog.e("SERVICEGO: ERROR - setLocationNetwork");
        }
    }

    public class NoteActionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null) {
                if (action.equals(getPackageName() + "." + SERVICE_GO_NOTE_ACTION_JOYSTICK_SHOW)) {
                    mBinder.setJoystickVisible(true);
                }

                if (action.equals(getPackageName() + "." + SERVICE_GO_NOTE_ACTION_JOYSTICK_HIDE)) {
                    mBinder.setJoystickVisible(false);
                }
            }
        }
    }

    public class ServiceGoBinder extends Binder {
        public boolean isJoystickVisible() {
            return running && mJoyStick.isControlVisible();
        }

        public void setJoystickVisible(boolean visible) {
            if (!running) return;
            if (visible) {
                mJoyStick.show();
            } else {
                mJoyStick.hide();
            }
            updateNotification();
        }

        public void setPosition(double lng, double lat, double alt) {
            mJoyStick.stopMovement();
            engine.setPosition(new Coordinate(lng, lat, alt));
            mJoyStick.setCurrentPosition(lng, lat, alt);
            updateNotification();
        }
    }
}
