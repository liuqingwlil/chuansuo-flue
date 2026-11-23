package com.chuansuo.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;

import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapPoi;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.search.core.SearchResult;
import com.baidu.mapapi.search.geocode.GeoCodeResult;
import com.baidu.mapapi.search.geocode.GeoCoder;
import com.baidu.mapapi.search.geocode.OnGetGeoCoderResultListener;
import com.baidu.mapapi.search.geocode.ReverseGeoCodeOption;
import com.baidu.mapapi.search.geocode.ReverseGeoCodeResult;
import com.chuansuo.utils.PlaceSearchController;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.chuansuo.model.Coordinate;
import com.chuansuo.service.ServiceGo;
import com.chuansuo.database.DataBaseHistoryLocation;
import com.chuansuo.database.DataBaseHistorySearch;
import com.chuansuo.utils.ShareUtils;
import com.chuansuo.utils.GoUtils;
import com.chuansuo.utils.MapUtils;
import com.chuansuo.utils.MapSettings;

import com.elvishew.xlog.XLog;

import io.noties.markwon.Markwon;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class MainActivity extends BaseActivity implements SensorEventListener {
    /* 对外 */
    public static final String LAT_MSG_ID = "LAT_VALUE";
    public static final String LNG_MSG_ID = "LNG_VALUE";
    public static final String ALT_MSG_ID = "ALT_VALUE";

    public static final String POI_NAME = "POI_NAME";
    public static final String POI_ADDRESS = "POI_ADDRESS";
    public static final String POI_LONGITUDE = "POI_LONGITUDE";
    public static final String POI_LATITUDE = "POI_LATITUDE";

    private OkHttpClient mOkHttpClient;
    private SharedPreferences sharedPreferences;

    /*============================== 主界面地图 相关 ==============================*/
    /************** 地图 *****************/
    public final static BitmapDescriptor mMapIndicator = BitmapDescriptorFactory.fromResource(R.drawable.icon_gcoding);
    public static String mCurrentCity = null;
    private MapView mMapView;
    private static BaiduMap mBaiduMap = null;
    private static LatLng mMarkLatLngMap;
    private static String mMarkName;
    private static String mMarkAddress;
    private GeoCoder mGeoCoder;
    private final java.util.Set<GeoCoder> historyGeocoders = new java.util.HashSet<>();
    private SensorManager mSensorManager;
    private Sensor mSensorAccelerometer;
    private Sensor mSensorMagnetic;
    private float[] mAccValues = new float[3];//加速度传感器数据
    private float[] mMagValues = new float[3];//地磁传感器数据
    private final float[] mR = new float[9];//旋转矩阵，用来保存磁场和加速度的数据
    private final float[] mDirectionValues = new float[3];//模拟方向传感器的数据（原始数据为弧度）
    /************** 定位 *****************/
    private LocationClient mLocClient = null;
    private double mCurrentLat = 0.0;       // 当前位置的百度纬度
    private double mCurrentLon = 0.0;       // 当前位置的百度经度
    private float mCurrentDirection = 0.0f;
    private boolean isFirstLoc = true; // 是否首次定位
    private boolean isServiceBound;
    private ServiceGo.ServiceGoBinder mServiceBinder;
    private ServiceConnection mConnection;
    private Button mButtonStart;
    private Button mJoystickButton;
    private Button mPauseButton;
    private Button mUsePoint;
    private TextView mDockTitle;
    private TextView mDockStatus;
    private TextView mDockCoordinates;
    private View mPointDetail;
    private TextView mPointName;
    private TextView mPointAddress;
    private ProgressBar mRouteProgress;
    private Boolean renderedRunning;
    private boolean renderedPending;
    private TextView mPointGps;
    private TextView mPointBaidu;
    private boolean hasCurrentLocation;
    private boolean satelliteMap;
    private long startPendingUntil;
    private final Handler simulationUi = new Handler(Looper.getMainLooper());
    private final Runnable refreshSimulation = new Runnable() {
        @Override public void run() {
            updateSimulationDock();
            simulationUi.postDelayed(this, 500);
        }
    };
    /*============================== 历史记录 相关 ==============================*/
    private SQLiteDatabase mLocationHistoryDB;
    private SQLiteDatabase mSearchHistoryDB;
    /*============================== SearchView 相关 ==============================*/
    private SearchView searchView;
    private View mapBody;
    private View searchTrigger;
    private TextView searchMessage;
    private boolean searching;
    private ListView mSearchList;
    private LinearLayout mSearchLayout;
    private ListView mSearchHistoryList;
    private LinearLayout mHistoryLayout;
    private MenuItem searchItem;
    private PlaceSearchController mPlaceSearch;
    /*============================== 更新 相关 ==============================*/
    private Call updateCheck;
    private boolean updateRequestedByUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        toolbar.setNavigationIcon(R.drawable.ic_menu);
        toolbar.setNavigationContentDescription(R.string.nav_drawer_open);
        toolbar.setNavigationOnClickListener(view -> drawer.openDrawer(GravityCompat.START));
        setTitle(R.string.map_simulation_title);
        SecondaryChrome.applySystemBars(this, getWindow());

        mapBody = findViewById(R.id.map_body);
        searchTrigger = findViewById(R.id.map_search_trigger);
        searchMessage = findViewById(R.id.map_search_message);
        searchTrigger.setOnClickListener(v -> {
            if (searchItem != null) searchItem.expandActionView();
        });

        XLog.i("MainActivity: onCreate");

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mOkHttpClient = new OkHttpClient();

        initNavigationView();

        initMap();

        initMapLocation();

        initMapButton();
        initSimulationDock();

        mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mServiceBinder = (ServiceGo.ServiceGoBinder) service;
                updateSimulationDock();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                mServiceBinder = null;
            }
        };

        initStoreHistory();

        initSearchView();

        checkUpdateVersion(false);
    }

    @Override
    protected void onPause() {
        XLog.i("MainActivity: onPause");
        simulationUi.removeCallbacks(refreshSimulation);
        mMapView.onPause();
        if (mSensorManager != null) mSensorManager.unregisterListener(this);
        super.onPause();
    }

    @Override
    protected void onResume() {
        XLog.i("MainActivity: onResume");
        mMapView.onResume();
        if (mSensorManager != null) {
            if (mSensorAccelerometer != null) {
                mSensorManager.registerListener(this, mSensorAccelerometer, SensorManager.SENSOR_DELAY_UI);
            }
            if (mSensorMagnetic != null) {
                mSensorManager.registerListener(this, mSensorMagnetic, SensorManager.SENSOR_DELAY_UI);
            }
        }
        refreshSimulation.run();
        super.onResume();
    }

    @Override
    protected void onStop() {
        XLog.i("MainActivity: onStop");
        //取消注册传感器监听
        if (mSensorManager != null) mSensorManager.unregisterListener(this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        XLog.i("MainActivity: onDestroy");

        if (isServiceBound) {
            unbindService(mConnection); isServiceBound = false;
        }
        if (updateCheck != null) updateCheck.cancel();

        if (mSensorManager != null) mSensorManager.unregisterListener(this);

        // 退出时销毁定位
        if (mLocClient != null) mLocClient.stop();
        // 关闭定位图层
        mBaiduMap.setMyLocationEnabled(false);
        mMapView.onDestroy();

        //poi search destroy
        mPlaceSearch.destroy();

        if (mGeoCoder != null) mGeoCoder.destroy();
        for (GeoCoder coder : historyGeocoders) coder.destroy();
        historyGeocoders.clear();

        //close db
        mLocationHistoryDB.close();
        mSearchHistoryDB.close();

        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else if (searching) {
            searchItem.collapseActionView();
        } else {
            moveTaskToBack(false);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        searchItem = menu.findItem(R.id.action_search);
        searchView = (SearchView) searchItem.getActionView();
        searchView.setQueryHint(getString(R.string.map_search_here));
        searchView.setBackgroundResource(R.drawable.map_field);
        searchView.setMaxWidth(Integer.MAX_VALUE);
        searchView.setSubmitButtonEnabled(false);
        View searchPlate = searchView.findViewById(androidx.appcompat.R.id.search_plate);
        searchPlate.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        EditText searchField = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
        searchField.setTextColor(getColor(R.color.ui_ink));
        searchField.setHintTextColor(getColor(R.color.ui_muted));

        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override public boolean onMenuItemActionCollapse(MenuItem item) {
                searching = false;
                mPlaceSearch.cancel();
                mSearchLayout.setVisibility(View.GONE);
                mHistoryLayout.setVisibility(View.GONE);
                searchMessage.setVisibility(View.GONE);
                mapBody.setVisibility(View.VISIBLE);
                searchTrigger.setVisibility(View.VISIBLE);
                return true;
            }

            @Override public boolean onMenuItemActionExpand(MenuItem item) {
                searching = true;
                mapBody.setVisibility(View.GONE);
                searchTrigger.setVisibility(View.GONE);
                showSearchHistory();
                return true;
            }
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                try {
                    mPlaceSearch.search(query, true);
                    //搜索历史 插表参数
                    ContentValues contentValues = new ContentValues();
                    contentValues.put(DataBaseHistorySearch.DB_COLUMN_KEY, query);
                    contentValues.put(DataBaseHistorySearch.DB_COLUMN_DESCRIPTION, "搜索关键字");
                    contentValues.put(DataBaseHistorySearch.DB_COLUMN_IS_LOCATION, DataBaseHistorySearch.DB_SEARCH_TYPE_KEY);
                    contentValues.put(DataBaseHistorySearch.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);

                    DataBaseHistorySearch.saveHistorySearch(mSearchHistoryDB, contentValues);
                    mSearchLayout.setVisibility(View.VISIBLE);
                    searchMessage.setVisibility(View.GONE);
                    searchView.clearFocus();
                } catch (Exception e) {
                    GoUtils.DisplayToast(MainActivity.this, getResources().getString(R.string.app_error_search));
                    XLog.d(getResources().getString(R.string.app_error_search));
                }

                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (!searching) return true;
                if (newText == null || newText.trim().isEmpty()) {
                    mPlaceSearch.cancel();
                    showSearchHistory();
                } else {
                    mHistoryLayout.setVisibility(View.GONE);
                    mSearchLayout.setVisibility(View.VISIBLE);
                    searchMessage.setVisibility(View.GONE);
                    try {
                        mPlaceSearch.search(newText, false);
                    } catch (RuntimeException e) {
                        showSearchError(getString(R.string.app_error_search));
                    }
                }

                return true;
            }
        });

        ImageView closeButton = searchView.findViewById(androidx.appcompat.R.id.search_close_btn);
        closeButton.setImageResource(R.drawable.ic_close);
        closeButton.setOnClickListener(v -> {
            searchView.setQuery("", false);
            showSearchHistory();
        });
        return true;
    }

    private void showSearchHistory() {
        List<Map<String, Object>> data = getSearchHistory();
        mSearchLayout.setVisibility(View.GONE);
        mHistoryLayout.setVisibility(View.VISIBLE);
        mSearchHistoryList.setAdapter(new SimpleAdapter(this, data, R.layout.search_item,
                new String[]{DataBaseHistorySearch.DB_COLUMN_KEY, DataBaseHistorySearch.DB_COLUMN_DESCRIPTION,
                        DataBaseHistorySearch.DB_COLUMN_TIMESTAMP, DataBaseHistorySearch.DB_COLUMN_IS_LOCATION,
                        DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM, DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM},
                new int[]{R.id.search_key, R.id.search_description, R.id.search_timestamp, R.id.search_isLoc,
                        R.id.search_longitude, R.id.search_latitude}));
        searchMessage.setText(R.string.map_search_history_empty);
        searchMessage.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showSearchError(String message) {
        if (!searching) return;
        mSearchLayout.setVisibility(View.VISIBLE);
        mHistoryLayout.setVisibility(View.GONE);
        mSearchList.setAdapter(null);
        searchMessage.setText(message);
        searchMessage.setVisibility(View.VISIBLE);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if(sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
            mAccValues = sensorEvent.values;
        }
        else if(sensorEvent.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD){
            mMagValues = sensorEvent.values;
        }

        SensorManager.getRotationMatrix(mR, null, mAccValues, mMagValues);
        SensorManager.getOrientation(mR, mDirectionValues);
        mCurrentDirection = (float) Math.toDegrees(mDirectionValues[0]);    // 弧度转角度
        if (mCurrentDirection < 0) {    // 由 -180 ~ + 180 转为 0 ~ 360
            mCurrentDirection += 360;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    /*============================== NavigationView 相关 ==============================*/
    private void initNavigationView() {
        /*============================== NavigationView 相关 ==============================*/
        NavigationView mNavigationView = findViewById(R.id.nav_view);
        mNavigationView.getMenu().findItem(R.id.nav_dev).setVisible(BuildConfig.DEBUG);
        mNavigationView.getMenu().findItem(R.id.nav_update).setVisible(!BuildConfig.DEBUG);
        mNavigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_library) {
                startActivity(new Intent(this, LibraryActivity.class));
            } else if (id == R.id.nav_route) {
                Intent editor = new Intent(this, RouteActivity.class).putExtra("route", true);
                if (mMarkLatLngMap != null) {
                    double[] point = MapUtils.bd2wgs(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);
                    editor.putExtra("lng", point[0]).putExtra("lat", point[1]);
                }
                startActivity(editor);
            } else if (id == R.id.nav_history) {
                Intent intent = new Intent(MainActivity.this, HistoryActivity.class);

                startActivity(intent);
            } else if (id == R.id.nav_settings) {
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
            } else if (BuildConfig.DEBUG && id == R.id.nav_dev) {
                if (!GoUtils.isDeveloperOptionsEnabled(this)) {
                    GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_dev));
                } else {
                    try {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                        startActivity(intent);
                    } catch (Exception e) {
                        GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_dev));
                    }
                }
            } else if (id == R.id.nav_update) {
                checkUpdateVersion(true);
            } else if (id == R.id.nav_feedback) {
                shareDiagnosticLog();
            } else if (id == R.id.nav_contact || id == R.id.nav_project_home) {
                openProjectUrl(id == R.id.nav_contact ? ProjectIdentity.issuesUrl() : ProjectIdentity.homeUrl());
            }

            DrawerLayout drawer = findViewById(R.id.drawer_layout);
            drawer.closeDrawer(GravityCompat.START);

            return true;
        });

        // 直接获取第 0 个头部视图
        View headerView = mNavigationView.getHeaderView(0);
        TextView app_version = headerView.findViewById(R.id.app_version);
        app_version.setText(getString(R.string.map_nav_signature, getString(R.string.app_author), GoUtils.getVersionName(this)));
    }

    private void shareDiagnosticLog() {
        try {
            ShareUtils.shareFile(this, DiagnosticReport.create(this), "text/plain",
                    getString(R.string.nav_menu_feedback));
        } catch (IOException | IllegalArgumentException | android.content.ActivityNotFoundException e) {
            GoUtils.DisplayToast(this, getString(R.string.log_share_failed));
        }
    }

    private void openProjectUrl(String url) {
        if (url.isEmpty()) {
            GoUtils.DisplayToast(this, getString(R.string.update_not_configured));
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (android.content.ActivityNotFoundException e) {
            GoUtils.DisplayToast(this, getString(R.string.setting_project_no_browser));
        }
    }

    /*============================== 主界面地图 相关 ==============================*/
    private void initMap() {
        // 地图初始化
        mMapView = findViewById(R.id.bdMapView);
        mMapView.showZoomControls(false);
        mBaiduMap = mMapView.getMap();
        mBaiduMap.setMapType(BaiduMap.MAP_TYPE_NORMAL);
        mBaiduMap.setMyLocationEnabled(true);
        mBaiduMap.setOnMapTouchListener(event -> {

        });
        mBaiduMap.setOnMapClickListener(new BaiduMap.OnMapClickListener() {
            /**
             * 单击地图
             */
            @Override
            public void onMapClick(LatLng point) {
                mMarkLatLngMap = point;
                mMarkName = null;
                mMarkAddress = null;
                markMap();
            }
            /**
             * 单击地图中的POI点
             */
            @Override
            public void onMapPoiClick(MapPoi poi) {
                mMarkLatLngMap = poi.getPosition();
                mMarkName = poi.getName();
                mMarkAddress = null;
                markMap();
            }
        });
        mBaiduMap.setOnMapLongClickListener(new BaiduMap.OnMapLongClickListener() {
            /**
             * 长按地图
             */
            @Override
            public void onMapLongClick(LatLng point) {
                mMarkLatLngMap = point;
                mMarkName = null;
                mMarkAddress = null;
                markMap();
                mGeoCoder.reverseGeoCode(new ReverseGeoCodeOption().location(point));
            }
        });
        mBaiduMap.setOnMapDoubleClickListener(new BaiduMap.OnMapDoubleClickListener() {
            /**
             * 双击地图
             */
            @Override
            public void onMapDoubleClick(LatLng point) {
                mBaiduMap.animateMapStatus(MapStatusUpdateFactory.zoomIn());
            }
        });

        mGeoCoder = GeoCoder.newInstance();
        mGeoCoder.setOnGetGeoCodeResultListener(new OnGetGeoCoderResultListener() {
            @Override
            public void onGetGeoCodeResult(GeoCodeResult geoCodeResult) {
            }

            @Override
            public void onGetReverseGeoCodeResult(ReverseGeoCodeResult reverseGeoCodeResult) {
                if (reverseGeoCodeResult == null || reverseGeoCodeResult.error != SearchResult.ERRORNO.NO_ERROR) {
                    XLog.i("逆地理位置失败!");
                    return;
                }
                LatLng result = reverseGeoCodeResult.getLocation();
                LatLng selected = mMarkLatLngMap;
                if (selected != null && result != null
                        && Math.abs(selected.latitude - result.latitude) < 0.0001
                        && Math.abs(selected.longitude - result.longitude) < 0.0001) {
                    mMarkName = reverseGeoCodeResult.getAddress();
                    updateSimulationDock();
                }
            }
        });

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);// 获取传感器管理服务
        if (mSensorManager != null) {
            mSensorAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (mSensorAccelerometer != null) {
                mSensorManager.registerListener(this, mSensorAccelerometer, SensorManager.SENSOR_DELAY_UI);
            }
            mSensorMagnetic = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            if (mSensorMagnetic != null) {
                mSensorManager.registerListener(this, mSensorMagnetic, SensorManager.SENSOR_DELAY_UI);
            }
        }
    }

    //开启地图的定位图层
    private void initMapLocation() {
        try {
            // 定位初始化
            mLocClient = new LocationClient(this);
            mLocClient.registerLocationListener(new BDAbstractLocationListener() {
                @Override
                public void onReceiveLocation(BDLocation bdLocation) {
                    if (bdLocation == null || mMapView == null) {// mapview 销毁后不在处理新接收的位置
                        return;
                    }

                    mCurrentCity = bdLocation.getCity();
                    hasCurrentLocation = true;
                    mCurrentLat = bdLocation.getLatitude();
                    mCurrentLon = bdLocation.getLongitude();
                    MyLocationData locData = new MyLocationData.Builder()
                            .accuracy(bdLocation.getRadius())
                            .direction(mCurrentDirection)// 此处设置开发者获取到的方向信息，顺时针0-360
                            .latitude(bdLocation.getLatitude())
                            .longitude(bdLocation.getLongitude()).build();
                    mBaiduMap.setMyLocationData(locData);
                    MyLocationConfiguration configuration = new MyLocationConfiguration(MyLocationConfiguration.LocationMode.NORMAL, true, null);
                    mBaiduMap.setMyLocationConfiguration(configuration);

                    /* 如果出现错误，则需要重新请求位置 */
                    int err = bdLocation.getLocType();
                    if (err == BDLocation.TypeCriteriaException || err == BDLocation.TypeNetWorkException) {
                        mLocClient.requestLocation();   /* 请求位置 */
                    } else {
                        if (isFirstLoc) {
                            isFirstLoc = false;
                            // 这里记录百度地图返回的位置
                            LatLng current = new LatLng(bdLocation.getLatitude(), bdLocation.getLongitude());
                            MapStatus.Builder builder = new MapStatus.Builder();
                            builder.target(current).zoom(18.0f);
                            mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()));

                            XLog.i("Initial Baidu location received");
                        }
                    }
                }
                /**
                 * 错误的状态码
                 * <a><a href="http://lbsyun.baidu.com/index.php?title=android-locsdk/guide/addition-func/error-code">...</a></a>
                 * <p>
                 * 回调定位诊断信息，开发者可以根据相关信息解决定位遇到的一些问题
                 *
                 * @param locType      当前定位类型
                 * @param diagnosticType  诊断类型（1~9）
                 * @param diagnosticMessage 具体的诊断信息释义
                 */
                @Override
                public void onLocDiagnosticMessage(int locType, int diagnosticType, String diagnosticMessage) {
                    XLog.w("Baidu location diagnostic: " + locType + "-" + diagnosticType);
                }
            });
            LocationClientOption locationOption = getLocationClientOption();
            //需将配置好的LocationClientOption对象，通过setLocOption方法传递给LocationClient对象使用
            mLocClient.setLocOption(locationOption);
            //开始定位
            mLocClient.start();
        } catch (Exception e) {
            XLog.e("ERROR: initMapLocation");
        }
    }

    @NonNull
    private static LocationClientOption getLocationClientOption() {
        LocationClientOption locationOption = new LocationClientOption();
        //可选，默认高精度，设置定位模式，高精度，低功耗，仅设备
        locationOption.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);
        //可选，默认gcj02，设置返回的定位结果坐标系，如果配合百度地图使用，建议设置为bd09ll;
        locationOption.setCoorType("bd09ll");
        //可选，默认0，即仅定位一次，设置发起连续定位请求的间隔需要大于等于1000ms才是有效的
        locationOption.setScanSpan(1000);
        //可选，设置是否需要地址信息，默认不需要
        locationOption.setIsNeedAddress(true);
        //可选，设置是否需要设备方向结果
        locationOption.setNeedDeviceDirect(false);
        //可选，默认false，设置是否当gps有效时按照1S1次频率输出GPS结果
        locationOption.setLocationNotify(true);
        //可选，默认true，定位SDK内部是一个SERVICE，并放到了独立进程，设置是否在stop的时候杀死这个进程，默认不杀死
        locationOption.setIgnoreKillProcess(true);
        //可选，默认false，设置是否需要位置语义化结果，可以在BDLocation.getLocationDescribe里得到，结果类似于“在北京天安门附近”
        locationOption.setIsNeedLocationDescribe(false);
        //可选，默认false，设置是否需要POI结果，可以在BDLocation.getPoiList里得到
        locationOption.setIsNeedLocationPoiList(false);
        //可选，默认false，设置是否开启Gps定位
        //locationOption.setOpenGps(true);
        locationOption.setOpenGnss(true);
        //可选，默认false，设置定位时是否需要海拔信息，默认不需要，除基础定位版本都可用
        locationOption.setIsNeedAltitude(false);
        return locationOption;
    }

    //地图上各按键的监听
    private void initMapButton() {
        View mapLayers = findViewById(R.id.map_layers);
        mapLayers.setOnClickListener(v -> {
            PopupMenu choices = new PopupMenu(this, mapLayers);
            choices.getMenu().add(0, 0, 0, R.string.map_pic_normal).setCheckable(true).setChecked(!satelliteMap);
            choices.getMenu().add(0, 1, 1, R.string.map_pic_sate).setCheckable(true).setChecked(satelliteMap);
            choices.setOnMenuItemClickListener(item -> {
                satelliteMap = item.getItemId() == 1;
                mBaiduMap.setMapType(satelliteMap ? BaiduMap.MAP_TYPE_SATELLITE : BaiduMap.MAP_TYPE_NORMAL);
                return true;
            });
            choices.show();
        });
        findViewById(R.id.cur_position).setOnClickListener(v -> resetMap());
        findViewById(R.id.zoom_in).setOnClickListener(v -> mBaiduMap.animateMapStatus(MapStatusUpdateFactory.zoomIn()));
        findViewById(R.id.zoom_out).setOnClickListener(v -> mBaiduMap.animateMapStatus(MapStatusUpdateFactory.zoomOut()));
        findViewById(R.id.input_pos).setOnClickListener(v -> showCoordinateInput());
    }

    private BottomSheetDialog showMapSheet(View content) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(content);
        dialog.setOnShowListener(ignored -> {
            View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) sheet.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            dialog.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        });
        dialog.show();
        return dialog;
    }

    private void showCoordinateInput() {
        View content = getLayoutInflater().inflate(R.layout.location_input, null);
        EditText longitude = content.findViewById(R.id.joystick_longitude);
        EditText latitude = content.findViewById(R.id.joystick_latitude);
        RadioButton baidu = content.findViewById(R.id.pos_type_bd);
        Button confirm = content.findViewById(R.id.input_position_ok);
        Button cancel = content.findViewById(R.id.input_position_cancel);
        if (mMarkLatLngMap != null) {
            longitude.setText(String.format(Locale.ROOT, "%.6f", mMarkLatLngMap.longitude));
            latitude.setText(String.format(Locale.ROOT, "%.6f", mMarkLatLngMap.latitude));
        }
        BottomSheetDialog dialog = showMapSheet(content);
        confirm.setOnClickListener(v -> {
            String lngText = longitude.getText().toString().trim();
            String latText = latitude.getText().toString().trim();
            if (lngText.isEmpty() || latText.isEmpty()) {
                GoUtils.DisplayToast(this, getString(R.string.app_error_input));
                return;
            }
            double lng;
            double lat;
            try {
                lng = Double.parseDouble(lngText);
                lat = Double.parseDouble(latText);
            } catch (NumberFormatException e) {
                GoUtils.DisplayToast(this, getString(R.string.app_error_input));
                return;
            }
            if (!Double.isFinite(lng) || Math.abs(lng) > 180.0) {
                longitude.setError(getString(R.string.app_error_longitude));
                return;
            }
            if (!Double.isFinite(lat) || Math.abs(lat) > 90.0) {
                latitude.setError(getString(R.string.app_error_latitude));
                return;
            }
            if (baidu.isChecked()) {
                mMarkLatLngMap = new LatLng(lat, lng);
            } else {
                double[] bd = MapUtils.wgs2bd09(lng, lat);
                mMarkLatLngMap = new LatLng(bd[1], bd[0]);
            }
            mMarkName = getString(R.string.map_manual_position);
            mMarkAddress = null;
            markMap();
            mBaiduMap.setMapStatus(MapStatusUpdateFactory.newLatLng(mMarkLatLngMap));
            dialog.dismiss();
        });
        cancel.setOnClickListener(v -> dialog.dismiss());
        underlineOnFocus(confirm);
        underlineOnFocus(cancel);
    }

    //标定选择的位置
    private void markMap() {
        if (mMarkLatLngMap != null) {
            MarkerOptions ooA = new MarkerOptions().position(mMarkLatLngMap).icon(mMapIndicator);
            mBaiduMap.clear();
            mBaiduMap.addOverlay(ooA);
        }
        updateSimulationDock();
    }

    private void resetMap() {
        mBaiduMap.clear();
        mMarkLatLngMap = null;
        mMarkName = null;
        mMarkAddress = null;
        updateSimulationDock();
        if (!hasCurrentLocation) return;

        MyLocationData locData = new MyLocationData.Builder()
                .latitude(mCurrentLat)
                .longitude(mCurrentLon)
                .direction(mCurrentDirection)
                .build();
        mBaiduMap.setMyLocationData(locData);

        MapStatus.Builder builder = new MapStatus.Builder();
        builder.target(new LatLng(mCurrentLat, mCurrentLon)).zoom(18.0f);
        mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()));
    }

    // 在地图上显示位置
    public static boolean showLocation(String name, String bd09Longitude, String bd09Latitude) {
        boolean ret = true;

        try {
            if (!bd09Longitude.isEmpty() && !bd09Latitude.isEmpty()) {
                mMarkName = name;
                mMarkAddress = null;
                mMarkLatLngMap = new LatLng(Double.parseDouble(bd09Latitude), Double.parseDouble(bd09Longitude));
                MarkerOptions ooA = new MarkerOptions().position(mMarkLatLngMap).icon(mMapIndicator);
                mBaiduMap.clear();
                mBaiduMap.addOverlay(ooA);
                MapStatusUpdate mapstatusupdate = MapStatusUpdateFactory.newLatLng(mMarkLatLngMap);
                mBaiduMap.setMapStatus(mapstatusupdate);
            }
        } catch (Exception e) {
            ret = false;
            XLog.e("ERROR: showHistoryLocation");
        }

        return ret;
    }

    private void initSimulationDock() {
        mButtonStart = findViewById(R.id.faBtnStart);
        mJoystickButton = findViewById(R.id.map_joystick_toggle);
        mPauseButton = findViewById(R.id.map_pause_route);
        mUsePoint = findViewById(R.id.poi_fly);
        mDockTitle = findViewById(R.id.map_state_title);
        mDockStatus = findViewById(R.id.map_simulation_status);
        mDockCoordinates = findViewById(R.id.map_simulation_coordinates);
        mPointDetail = findViewById(R.id.map_point_detail);
        mPointName = findViewById(R.id.poi_address);
        mPointAddress = findViewById(R.id.poi_subtitle);
        mRouteProgress = findViewById(R.id.map_route_progress);
        mPointGps = findViewById(R.id.poi_coordinates_gps);
        mPointBaidu = findViewById(R.id.poi_coordinates_baidu);

        mButtonStart.setOnClickListener(v -> {
            if (ServiceGo.isRunning()) {
                startPendingUntil = 0;
                ServiceGo.control(this, ServiceGo.ACTION_STOP, 0);
                if (isServiceBound) {
                    unbindService(mConnection);
                    isServiceBound = false;
                    mServiceBinder = null;
                }
            } else {
                startOrMoveToSelectedPoint(v);
            }
        });
        mUsePoint.setOnClickListener(this::startOrMoveToSelectedPoint);
        mJoystickButton.setOnClickListener(v -> {
            if (mServiceBinder == null) {
                GoUtils.DisplayToast(this, "模拟服务正在连接，请稍后再试");
                return;
            }
            mServiceBinder.setJoystickVisible(!mServiceBinder.isJoystickVisible());
            updateSimulationDock();
        });
        mPauseButton.setOnClickListener(v -> ServiceGo.control(this, ServiceGo.ACTION_TOGGLE, 0));

        Button save = findViewById(R.id.poi_save);
        Button copy = findViewById(R.id.poi_copy);
        Button share = findViewById(R.id.poi_share);
        save.setOnClickListener(v -> {
            if (mMarkLatLngMap == null) return;
            double[] point = MapUtils.bd2wgs(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);
            startActivity(RouteActivity.newPlace(this, TextUtils.isEmpty(mMarkName) ? "收藏地点" : mMarkName,
                    point[0], point[1], 55));
        });
        copy.setOnClickListener(v -> {
            if (mMarkLatLngMap == null) return;
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.map_input_title), selectedCoordinateText()));
            GoUtils.DisplayToast(this, getString(R.string.app_location_copy));
        });
        share.setOnClickListener(v -> {
            if (mMarkLatLngMap != null) ShareUtils.shareText(this, getString(R.string.map_share), selectedCoordinateText());
        });
        underlineOnFocus(mButtonStart);
        underlineOnFocus(mJoystickButton);
        underlineOnFocus(mPauseButton);
        underlineOnFocus(mUsePoint);
        underlineOnFocus(findViewById(R.id.input_pos));
        underlineOnFocus(save);
        underlineOnFocus(copy);
        underlineOnFocus(share);
        updateSimulationDock();
    }

    private void underlineOnFocus(TextView button) {
        button.setOnFocusChangeListener((view, focused) -> {
            int flags = button.getPaintFlags();
            int underline = android.graphics.Paint.UNDERLINE_TEXT_FLAG;
            button.setPaintFlags(focused ? flags | underline : flags & ~underline);
        });
    }

    private String selectedCoordinateText() {
        return String.format(Locale.ROOT, "%.6f,%.6f", mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);
    }

    private void updateSimulationDock() {
        if (mButtonStart == null) return;
        boolean running = ServiceGo.isRunning();
        if (running) startPendingUntil = 0;
        boolean pending = SystemClock.elapsedRealtime() < startPendingUntil;
        boolean route = ServiceGo.hasActiveRoute();
        boolean paused = route && ServiceGo.isActiveRoutePaused();
        boolean finished = route && ServiceGo.isActiveRouteFinished();
        boolean selected = mMarkLatLngMap != null;

        if (running && !isServiceBound && mConnection != null) {
            isServiceBound = bindService(new Intent(this, ServiceGo.class), mConnection, 0);
        } else if (!running && !pending && isServiceBound) {
            unbindService(mConnection);
            isServiceBound = false;
            mServiceBinder = null;
        }
        if (renderedRunning == null || running != renderedRunning || pending != renderedPending) {
            renderedRunning = running;
            renderedPending = pending;
            mButtonStart.setEnabled(!pending);
            mButtonStart.setText(running ? R.string.map_stop_simulation : R.string.map_start_simulation);
            mButtonStart.setCompoundDrawablesRelativeWithIntrinsicBounds(running ? R.drawable.ic_stop : R.drawable.ic_play, 0, 0, 0);
            mButtonStart.setCompoundDrawablePadding(Math.round(6 * getResources().getDisplayMetrics().density));
            mButtonStart.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(
                    getColor(pending ? R.color.ui_ink : R.color.ui_on_primary)));
            mButtonStart.setTextColor(getColor(pending ? R.color.ui_ink : R.color.ui_on_primary));
        }
        int dockTitle = R.string.map_choose_place;
        if (selected) dockTitle = R.string.map_point_title;
        if (running) dockTitle = R.string.map_simulating;
        if (route) dockTitle = R.string.map_route_playing;
        if (paused) dockTitle = R.string.map_route_paused;
        if (finished) dockTitle = R.string.map_route_finished;
        updateText(mDockTitle, getString(dockTitle));
        if (pending) {
            updateText(mDockStatus, getString(R.string.map_simulation_starting));
        } else if (running) {
            updateText(mDockStatus, ServiceGo.status());
        } else {
            updateText(mDockStatus, getString(hasCurrentLocation ? R.string.map_current_location : R.string.map_pick_instruction));
        }
        mDockStatus.setVisibility(!running && selected && !pending ? View.GONE : View.VISIBLE);
        Coordinate current = ServiceGo.currentPosition();
        if (running && current != null) {
            updateText(mDockCoordinates, getString(R.string.map_gps_coordinates, current.latitude, current.longitude));
            mDockCoordinates.setVisibility(View.VISIBLE);
        } else if (!selected && hasCurrentLocation) {
            double[] coordinates = MapUtils.bd2wgs(mCurrentLon, mCurrentLat);
            updateText(mDockCoordinates, getString(R.string.map_gps_coordinates, coordinates[1], coordinates[0]));
            mDockCoordinates.setVisibility(View.VISIBLE);
        } else {
            mDockCoordinates.setVisibility(View.GONE);
        }
        boolean joystickVisible = mServiceBinder != null && mServiceBinder.isJoystickVisible();
        updateText(mJoystickButton, getString(joystickVisible ? R.string.map_joystick_on : R.string.map_joystick));
        mJoystickButton.setVisibility(running && !route ? View.VISIBLE : View.GONE);
        updateText(mPauseButton, getString(paused ? R.string.map_resume : R.string.map_pause));
        mPauseButton.setVisibility(route && !finished ? View.VISIBLE : View.GONE);
        Double routeProgress = ServiceGo.activeRouteProgress();
        mRouteProgress.setVisibility(routeProgress == null ? View.GONE : View.VISIBLE);
        if (routeProgress != null) mRouteProgress.setProgress((int) Math.round(routeProgress * 100));
        mPointDetail.setVisibility(selected ? View.VISIBLE : View.GONE);
        mUsePoint.setVisibility(selected && running ? View.VISIBLE : View.GONE);
        if (selected) {
            double[] gps = MapUtils.bd2wgs(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);
            updateText(mPointName, TextUtils.isEmpty(mMarkName) ? getString(R.string.map_selected_position) : mMarkName);
            mPointAddress.setVisibility(TextUtils.isEmpty(mMarkAddress) || TextUtils.equals(mMarkName, mMarkAddress)
                    ? View.GONE : View.VISIBLE);
            updateText(mPointAddress, mMarkAddress);
            updateText(mPointGps, getString(R.string.map_gps_coordinates, gps[1], gps[0]));
            updateText(mPointBaidu, getString(R.string.map_baidu_coordinates, mMarkLatLngMap.latitude, mMarkLatLngMap.longitude));
        }
    }

    private void updateText(TextView textView, CharSequence text) {
        CharSequence value = text == null ? "" : text;
        if (!TextUtils.equals(textView.getText(), value)) textView.setText(value);
    }

    private void startOrMoveToSelectedPoint(View anchor) {
        if (mMarkLatLngMap == null) {
            Snackbar.make(anchor, R.string.map_pick_instruction, Snackbar.LENGTH_LONG).show();
            return;
        }
        if (!GoUtils.isNetworkAvailable(this)) {
            GoUtils.DisplayToast(this, getString(R.string.app_error_network));
            return;
        }
        boolean running = ServiceGo.isRunning();
        boolean location = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean gps = GoUtils.isGpsOpened(this);
        boolean overlay = Settings.canDrawOverlays(this);
        boolean mock = running || GoUtils.isAllowMockLocation(this);
        if (!location || !gps || !overlay || !mock) {
            showSimulationPermissions(location, gps, overlay, mock);
            return;
        }

        double[] point = MapUtils.bd2wgs(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);
        double altitude;
        try {
            altitude = Double.parseDouble(sharedPreferences.getString("setting_altitude", "55.0"));
        } catch (NumberFormatException e) {
            altitude = 55;
        }
        if (running) {
            if (mServiceBinder == null) {
                GoUtils.DisplayToast(this, "模拟服务正在连接，请稍后再试");
                return;
            }
            mServiceBinder.setPosition(point[0], point[1], altitude);
            Snackbar.make(anchor, "已传送到新位置", Snackbar.LENGTH_LONG).show();
        } else {
            Intent service = new Intent(this, ServiceGo.class)
                    .putExtra(LNG_MSG_ID, point[0]).putExtra(LAT_MSG_ID, point[1]).putExtra(ALT_MSG_ID, altitude);
            if (!isServiceBound) isServiceBound = bindService(service, mConnection, BIND_AUTO_CREATE);
            startPendingUntil = SystemClock.elapsedRealtime() + 1500;
            startForegroundService(service);
        }
        recordCurrentLocation(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);
        mMarkLatLngMap = null;
        mMarkName = null;
        mMarkAddress = null;
        updateSimulationDock();
        if (GoUtils.isWifiEnabled(this)) GoUtils.showDisableWifiDialog(this);
    }

    private void showSimulationPermissions(boolean location, boolean gps, boolean overlay, boolean mock) {
        View content = getLayoutInflater().inflate(R.layout.map_permission_sheet, null);
        LinearLayout rows = content.findViewById(R.id.map_permission_rows);
        BottomSheetDialog dialog = showMapSheet(content);

        Runnable developerSettings = () -> openSetting(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, null);
        Runnable requestLocation = () -> requestPermissions(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 93);
        Runnable overlaySettings = () -> openSetting(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        Runnable gpsSettings = () -> openSetting(Settings.ACTION_LOCATION_SOURCE_SETTINGS, null);
        addPermissionRow(rows, dialog, R.drawable.ic_menu_dev, R.string.map_permission_developer,
                R.string.map_permission_developer_detail, mock, developerSettings);
        addPermissionRow(rows, dialog, R.drawable.ic_position, R.string.map_permission_location,
                R.string.map_permission_location_detail, location, requestLocation);
        addPermissionRow(rows, dialog, R.drawable.ic_layers, R.string.map_permission_overlay,
                R.string.map_permission_overlay_detail, overlay, overlaySettings);
        addPermissionRow(rows, dialog, R.drawable.ic_home_position, R.string.map_permission_gps,
                R.string.map_permission_gps_detail, gps, gpsSettings);

        Button primary = content.findViewById(R.id.map_permission_primary);
        underlineOnFocus(primary);
        primary.setOnClickListener(v -> {
            dialog.dismiss();
            if (!location) requestLocation.run();
            else if (!gps) gpsSettings.run();
            else if (!overlay) overlaySettings.run();
            else developerSettings.run();
        });
    }

    private void addPermissionRow(LinearLayout rows, BottomSheetDialog dialog, int icon, int label,
                                  int detail, boolean done, Runnable launchSetting) {
        View row = getLayoutInflater().inflate(R.layout.map_permission_row, rows, false);
        ImageView image = row.findViewById(R.id.map_permission_icon);
        TextView title = row.findViewById(R.id.map_permission_label);
        TextView subtitle = row.findViewById(R.id.map_permission_detail);
        TextView status = row.findViewById(R.id.map_permission_status);
        image.setImageResource(icon);
        title.setText(label);
        subtitle.setText(detail);
        status.setText(done ? R.string.map_permission_done : R.string.map_permission_pending);
        if (!done) {
            row.setFocusable(true);
            row.setOnClickListener(v -> {
                dialog.dismiss();
                launchSetting.run();
            });
        }
        rows.addView(row);
    }

    private void openSetting(String action, Uri data) {
        try {
            startActivity(new Intent(action, data));
        } catch (android.content.ActivityNotFoundException e) {
            GoUtils.DisplayToast(this, getString(R.string.map_permission_unavailable));
        }
    }

    /*============================== 历史记录 相关 ==============================*/
    private void initStoreHistory() {
        try {
            // 定位历史
            DataBaseHistoryLocation dbLocation = new DataBaseHistoryLocation(getApplicationContext());
            mLocationHistoryDB = dbLocation.getWritableDatabase();
            // 搜索历史
            DataBaseHistorySearch dbHistory = new DataBaseHistorySearch(getApplicationContext());
            mSearchHistoryDB = dbHistory.getWritableDatabase();
        } catch (Exception e) {
            XLog.e("ERROR: sqlite init error");
        }
    }

    //获取查询历史
    private List<Map<String, Object>> getSearchHistory() {
        List<Map<String, Object>> data = new ArrayList<>();

        try {
            Cursor cursor = mSearchHistoryDB.query(DataBaseHistorySearch.TABLE_NAME, null,
                    DataBaseHistorySearch.DB_COLUMN_ID + " > ?", new String[] {"0"},
                    null, null, DataBaseHistorySearch.DB_COLUMN_TIMESTAMP + " DESC", null);

            while (cursor.moveToNext()) {
                Map<String, Object> searchHistoryItem = new HashMap<>();
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_KEY, cursor.getString(1));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_DESCRIPTION, cursor.getString(2));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_TIMESTAMP, "" + cursor.getInt(3));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_IS_LOCATION, "" + cursor.getInt(4));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM, cursor.getString(7));
                searchHistoryItem.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM, cursor.getString(8));
                data.add(searchHistoryItem);
            }
            cursor.close();
        } catch (Exception e) {
            XLog.e("ERROR: getSearchHistory");
        }

        return data;
    }

    // Persist coordinates immediately; resolve their label with the same Android SDK key.
    private void recordCurrentLocation(double lng, double lat) {
        double[] wgs = MapUtils.bd2wgs(lng, lat);
        String longitude = Double.toString(wgs[0]);
        String latitude = Double.toString(wgs[1]);
        String label = mMarkName == null || mMarkName.trim().isEmpty()
                ? getString(R.string.history_location_default_name) : mMarkName;
        ContentValues values = new ContentValues();
        values.put(DataBaseHistoryLocation.DB_COLUMN_LOCATION, label);
        values.put(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_WGS84, longitude);
        values.put(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_WGS84, latitude);
        values.put(DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);
        values.put(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_CUSTOM, Double.toString(lng));
        values.put(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_CUSTOM, Double.toString(lat));
        DataBaseHistoryLocation.saveHistoryLocation(mLocationHistoryDB, values);
        if (MapSettings.apiKey(this).isEmpty()) return;

        GeoCoder coder = GeoCoder.newInstance();
        historyGeocoders.add(coder);
        coder.setOnGetGeoCodeResultListener(new OnGetGeoCoderResultListener() {
            @Override public void onGetGeoCodeResult(GeoCodeResult result) { }
            @Override public void onGetReverseGeoCodeResult(ReverseGeoCodeResult result) {
                historyGeocoders.remove(coder);
                try {
                    if (!isDestroyed() && mLocationHistoryDB.isOpen() && result != null
                            && result.error == SearchResult.ERRORNO.NO_ERROR
                            && result.getAddress() != null && !result.getAddress().trim().isEmpty()) {
                        ContentValues address = new ContentValues();
                        address.put(DataBaseHistoryLocation.DB_COLUMN_LOCATION, result.getAddress());
                        mLocationHistoryDB.update(DataBaseHistoryLocation.TABLE_NAME, address,
                                DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_WGS84 + "=? AND "
                                        + DataBaseHistoryLocation.DB_COLUMN_LATITUDE_WGS84 + "=?",
                                new String[]{longitude, latitude});
                    }
                } finally { coder.destroy(); }
            }
        });
        try {
            if (!coder.reverseGeoCode(new ReverseGeoCodeOption().location(new LatLng(lat, lng)))) {
                historyGeocoders.remove(coder); coder.destroy();
            }
        } catch (RuntimeException error) {
            historyGeocoders.remove(coder); coder.destroy();
            XLog.e("History address lookup unavailable");
        }
    }

    /*============================== SearchView 相关 ==============================*/
    private void initSearchView() {
        mSearchLayout = findViewById(R.id.search_linear);
        mHistoryLayout = findViewById(R.id.search_history_linear);

        mSearchList = findViewById(R.id.search_list_view);
        mSearchList.setOnItemClickListener((parent, view, position, id) -> {
            mPlaceSearch.cancel();
            String lng = ((TextView) view.findViewById(R.id.poi_longitude)).getText().toString();
            String lat = ((TextView) view.findViewById(R.id.poi_latitude)).getText().toString();
            mMarkName = ((TextView) view.findViewById(R.id.poi_name)).getText().toString();
            mMarkAddress = ((TextView) view.findViewById(R.id.poi_address)).getText().toString();
            mMarkLatLngMap = new LatLng(Double.parseDouble(lat), Double.parseDouble(lng));
            MapStatusUpdate mapstatusupdate = MapStatusUpdateFactory.newLatLng(mMarkLatLngMap);
            mBaiduMap.setMapStatus(mapstatusupdate);

            markMap();

            double[] latLng = MapUtils.bd2wgs(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);

            // mSearchList.setVisibility(View.GONE);
            //搜索历史 插表参数
            ContentValues contentValues = new ContentValues();
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_KEY, mMarkName);
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_DESCRIPTION, ((TextView) view.findViewById(R.id.poi_address)).getText().toString());
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_IS_LOCATION, DataBaseHistorySearch.DB_SEARCH_TYPE_RESULT);
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM, lng);
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM, lat);
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_WGS84, String.valueOf(latLng[0]));
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_WGS84, String.valueOf(latLng[1]));
            contentValues.put(DataBaseHistorySearch.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);

            DataBaseHistorySearch.saveHistorySearch(mSearchHistoryDB, contentValues);
            mSearchLayout.setVisibility(View.GONE);
            searchItem.collapseActionView();
        });
        //搜索历史列表的点击监听
        mSearchHistoryList = findViewById(R.id.search_history_list_view);
        mSearchHistoryList.setOnItemClickListener((parent, view, position, id) -> {
            String searchDescription = ((TextView) view.findViewById(R.id.search_description)).getText().toString();
            String searchKey = ((TextView) view.findViewById(R.id.search_key)).getText().toString();
            String searchIsLoc = ((TextView) view.findViewById(R.id.search_isLoc)).getText().toString();

            //如果是定位搜索
            if (searchIsLoc.equals("1")) {
                String lng = ((TextView) view.findViewById(R.id.search_longitude)).getText().toString();
                String lat = ((TextView) view.findViewById(R.id.search_latitude)).getText().toString();
                mMarkName = searchKey;
                mMarkAddress = searchDescription;
                mMarkLatLngMap = new LatLng(Double.parseDouble(lat), Double.parseDouble(lng));
                MapStatusUpdate mapstatusupdate = MapStatusUpdateFactory.newLatLng(mMarkLatLngMap);
                mBaiduMap.setMapStatus(mapstatusupdate);

                markMap();

                double[] latLng = MapUtils.bd2wgs(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude);

                //设置列表不可见
                mHistoryLayout.setVisibility(View.GONE);
                searchItem.collapseActionView();
                //更新表
                ContentValues contentValues = new ContentValues();
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_KEY, searchKey);
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_DESCRIPTION, searchDescription);
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_IS_LOCATION, DataBaseHistorySearch.DB_SEARCH_TYPE_RESULT);
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM, lng);
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM, lat);
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_WGS84, String.valueOf(latLng[0]));
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_WGS84, String.valueOf(latLng[1]));
                contentValues.put(DataBaseHistorySearch.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);

                DataBaseHistorySearch.saveHistorySearch(mSearchHistoryDB, contentValues);
            } else if (searchIsLoc.equals("0")) { //如果仅仅是搜索
                try {
                    searchView.setQuery(searchKey, true);
                } catch (Exception e) {
                    GoUtils.DisplayToast(this, getResources().getString(R.string.app_error_search));
                    XLog.e(getResources().getString(R.string.app_error_search));
                }
            } else {
                XLog.e(getResources().getString(R.string.app_error_param));
            }
        });
        mSearchHistoryList.setOnItemLongClickListener((parent, view, position, id) -> {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("警告")//这里是表头的内容
                    .setMessage("确定要删除该项搜索记录吗?")//这里是中间显示的具体信息
                    .setPositiveButton("确定",(dialog, which) -> {
                        String searchKey = ((TextView) view.findViewById(R.id.search_key)).getText().toString();

                        try {
                            mSearchHistoryDB.delete(DataBaseHistorySearch.TABLE_NAME, DataBaseHistorySearch.DB_COLUMN_KEY + " = ?", new String[] {searchKey});
                            showSearchHistory();
                        } catch (Exception e) {
                            XLog.e("ERROR: delete database error");
                            GoUtils.DisplayToast(MainActivity.this,getResources().getString(R.string.history_delete_error));
                        }
                    })
                    .setNegativeButton("取消",
                            (dialog, which) -> {
                            })
                    .show();
            return true;
        });
        mPlaceSearch = new PlaceSearchController(this, () -> mCurrentCity,
                new PlaceSearchController.Listener() {
                    @Override public void onResults(List<Map<String, Object>> data) {
                        SimpleAdapter adapter = new SimpleAdapter(MainActivity.this, data,
                                R.layout.search_poi_item,
                                new String[]{POI_NAME, POI_ADDRESS, POI_LONGITUDE, POI_LATITUDE},
                                new int[]{R.id.poi_name, R.id.poi_address, R.id.poi_longitude, R.id.poi_latitude});
                        if (!searching) return;
                        mSearchList.setAdapter(adapter);
                        mSearchLayout.setVisibility(View.VISIBLE);
                        searchMessage.setText(R.string.map_search_empty);
                        searchMessage.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                    @Override public void onError(String message) {
                        showSearchError(message);
                    }
                });
    }

    private void checkUpdateVersion(boolean manual) {
        if (BuildConfig.DEBUG) return;
        String endpoint = ProjectIdentity.latestReleaseApiUrl();
        if (endpoint.isEmpty()) {
            if (manual) GoUtils.DisplayToast(this, getString(R.string.update_not_configured));
            return;
        }
        updateRequestedByUser |= manual;
        if (updateCheck != null) return;

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(endpoint).header("Accept", "application/vnd.github+json").build();
        updateCheck = mOkHttpClient.newCall(request);
        updateCheck.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (!call.isCanceled()) runOnUiThread(() -> finishUpdateCheck(null));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                JSONObject release = null;
                try (Response result = response) {
                    ResponseBody body = result.body();
                    if (result.isSuccessful() && body != null) release = new JSONObject(body.string());
                } catch (IOException | JSONException e) {
                    XLog.w("Update response could not be read");
                }
                JSONObject latest = release;
                runOnUiThread(() -> finishUpdateCheck(latest));
            }
        });
    }

    private void finishUpdateCheck(JSONObject release) {
        updateCheck = null;
        boolean manual = updateRequestedByUser;
        updateRequestedByUser = false;
        if (isFinishing() || isDestroyed()) return;
        if (release == null) {
            if (manual) GoUtils.DisplayToast(this, getString(R.string.update_unavailable));
            return;
        }

        try {
            String tag = release.getString("tag_name");
            if (ReleaseUpdate.compareVersions(tag, BuildConfig.VERSION_NAME) <= 0) {
                if (manual) GoUtils.DisplayToast(this, getString(R.string.update_last));
                return;
            }
            String downloadUrl = ReleaseUpdate.standardDownloadUrl(release, ProjectIdentity.homeUrl());
            if (downloadUrl.isEmpty()) {
                if (manual) GoUtils.DisplayToast(this, getString(R.string.update_no_apk));
                return;
            }
            showUpdateDialog(release, tag, downloadUrl);
        } catch (JSONException | IllegalArgumentException e) {
            if (manual) GoUtils.DisplayToast(this, getString(R.string.update_unavailable));
        }
    }

    private void showUpdateDialog(JSONObject release, String tag, String downloadUrl) {
        View content = getLayoutInflater().inflate(R.layout.update, null);
        ((TextView) content.findViewById(R.id.update_title)).setText(release.optString("name", tag));
        ((TextView) content.findViewById(R.id.update_time)).setText(release.optString("published_at"));
        ((TextView) content.findViewById(R.id.update_commit)).setText(release.optString("target_commitish"));
        Markwon.create(this).setMarkdown(content.findViewById(R.id.update_content), release.optString("body"));

        AlertDialog dialog = new AlertDialog.Builder(this).setView(content).create();
        content.findViewById(R.id.update_ignore).setOnClickListener(v -> dialog.dismiss());
        content.findViewById(R.id.update_agree).setOnClickListener(v -> {
            dialog.dismiss();
            openProjectUrl(downloadUrl);
        });
        dialog.show();
    }
}
