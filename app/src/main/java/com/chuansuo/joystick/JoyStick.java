package com.chuansuo.joystick;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.PixelFormat;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.SearchView;

import androidx.preference.PreferenceManager;

import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.MapPoi;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.model.LatLng;
import com.chuansuo.utils.PlaceSearchController;
import com.chuansuo.database.DataBaseHistoryLocation;
import com.chuansuo.app.HistoryActivity;
import com.chuansuo.app.MainActivity;
import com.chuansuo.app.R;
import com.chuansuo.utils.GoUtils;
import com.chuansuo.utils.MapUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JoyStick extends View {
    private static final int DivGo = 1000;    /* 移动的时间间隔，单位 ms */
    private static final int OVERLAY_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

    private final Context mContext;
    private WindowManager.LayoutParams mWindowParamCurrent;
    private WindowManager mWindowManager;
    private ImageButton mBubble;
    private View mVisibleWindow;
    private int overlayX;
    private int overlayY;
    private final LayoutInflater inflater;
    private final Button[] speedButtons = new Button[3];
    private JoyStickClickListener mListener;

    // 移动
    private View mJoystickLayout;
    private GoUtils.TimeCount mTimer;
    private boolean isMove;
    private double mSpeed = 1.2;        /* 默认的速度，单位 m/s */
    private double mAltitude = 55.0;
    private double mAngle = 0;
    private double mR = 0;
    private double disLng = 0;
    private double disLat = 0;
    private final SharedPreferences sharedPreferences;
    /* 历史记录悬浮窗相关 */
    private FrameLayout mHistoryLayout;
    private final List<Map<String, Object>> mAllRecord = new ArrayList<> ();
    private TextView noRecordText;
    private ListView mRecordListView;
    /* 地图悬浮窗相关 */
    private FrameLayout mMapLayout;
    private MapView mMapView;
    private BaiduMap mBaiduMap;
    private LatLng mCurMapLngLat;
    private LatLng mMarkMapLngLat;
    private PlaceSearchController mPlaceSearch;
    private ListView mSearchList;
    private LinearLayout mSearchLayout;

    public JoyStick(Context context) {
        this(context, null);
    }

    public JoyStick(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.mContext = context;

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);

        initWindowManager();

        inflater = LayoutInflater.from(mContext);
        initJoyStickView();
        initJoyStickMapView();
        initHistoryView();
    }

    public JoyStick(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public void setCurrentPosition(double lng, double lat, double alt) {
        double[] lngLat = MapUtils.wgs2bd09(lng, lat);
        mCurMapLngLat = new LatLng(lngLat[1], lngLat[0]);
        mAltitude = alt;

        resetBaiduMap();
    }

    public void show() {
        if (!isControlVisible()) showWindow(mBubble);
    }

    private void showWindow(View window) {
        if (window == mVisibleWindow) return;
        detachWindow();
        mWindowParamCurrent.flags = OVERLAY_FLAGS;
        sizeOverlayWindow(window);
        if (window == mMapLayout) resetBaiduMap();
        mWindowManager.addView(window, mWindowParamCurrent);
        mVisibleWindow = window;
    }

    public boolean isControlVisible() {
        return mVisibleWindow != null;
    }

    public void hide() {
        detachWindow();
    }

    private void detachWindow() {
        View window = mVisibleWindow;
        if (window == null) return;
        mVisibleWindow = null;

        if (window == mMapLayout) {
            mPlaceSearch.cancel();
            resetSearch(window, R.id.joystick_map_searchView, R.id.joystick_map_tips);
            mSearchLayout.setVisibility(GONE);
        } else if (window == mHistoryLayout) {
            resetSearch(window, R.id.joystick_his_searchView, R.id.joystick_his_tips);
        }
        mWindowManager.removeViewImmediate(window);
    }

    private void resetSearch(View window, int searchId, int tipsId) {
        SearchView search = window.findViewById(searchId);
        search.clearFocus();
        search.onActionViewCollapsed();
        window.findViewById(tipsId).setVisibility(VISIBLE);
    }

    public void stopMovement() {
        mTimer.cancel();
        isMove = false;
    }

    public void destroy() {
        hide();
        mPlaceSearch.destroy();
        mTimer.cancel();
        mBaiduMap.setMyLocationEnabled(false);
        mMapView.onDestroy();
    }

    public void setListener(JoyStickClickListener mListener) {
        this.mListener = mListener;
    }

    @SuppressLint("RtlHardcoded") // Overlay dragging uses physical screen coordinates.
    private void initWindowManager() {
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mWindowParamCurrent = new WindowManager.LayoutParams();
        mWindowParamCurrent.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        mWindowParamCurrent.format = PixelFormat.RGBA_8888;
        mWindowParamCurrent.flags = OVERLAY_FLAGS;
        mWindowParamCurrent.gravity = Gravity.LEFT | Gravity.TOP;
        mWindowParamCurrent.width = WindowManager.LayoutParams.WRAP_CONTENT;
        mWindowParamCurrent.height = WindowManager.LayoutParams.WRAP_CONTENT;
        overlayX = dp(12);
        overlayY = dp(130);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void sizeOverlayWindow(View window) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int width;
        int height;
        if (window == mBubble) {
            int bubbleSize = getResources().getDimensionPixelSize(R.dimen.joystick_bubble_size);
            width = Math.min(bubbleSize, screenWidth);
            height = Math.min(bubbleSize, screenHeight);
        } else if (window == mJoystickLayout) {
            width = Math.min(dp(244), screenWidth);
            window.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(screenHeight, MeasureSpec.AT_MOST));
            height = window.getMeasuredHeight();
        } else {
            width = Math.min(dp(300), screenWidth);
            height = Math.min(dp(window == mMapLayout ? 480 : 450), screenHeight);
        }
        mWindowParamCurrent.width = width;
        mWindowParamCurrent.height = height;
        mWindowParamCurrent.x = clampPosition(overlayX, screenWidth, width);
        mWindowParamCurrent.y = clampPosition(overlayY, screenHeight, height);
    }

    private int clampPosition(int position, int screenSize, int windowSize) {
        return Math.max(0, Math.min(position, screenSize - windowSize));
    }

    private void setSearchFocusable(View window, boolean focusable) {
        if (mVisibleWindow != window) return;
        mWindowParamCurrent.flags = focusable
                ? OVERLAY_FLAGS & ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE : OVERLAY_FLAGS;
        mWindowManager.updateViewLayout(window, mWindowParamCurrent);
    }

    @SuppressLint({"InflateParams", "ClickableViewAccessibility"})
    private void initJoyStickView() {
        /* 移动计时器 */
        mTimer = new GoUtils.TimeCount(DivGo, DivGo);
        mTimer.setListener(new GoUtils.TimeCount.TimeCountListener() {
            @Override
            public void onTick(long millisUntilFinished) {

            }

            @Override
            public void onFinish() {
                // 注意：这里的 x y 与 圆中角度的对应问题（以 X 轴正向为 0 度）且转换为 km
                disLng = mSpeed * (double)(DivGo / 1000) * mR * Math.cos(mAngle * 2 * Math.PI / 360) / 1000;// 注意安卓中的三角函数使用的是弧度
                disLat = mSpeed * (double)(DivGo / 1000) * mR * Math.sin(mAngle * 2 * Math.PI / 360) / 1000;// 注意安卓中的三角函数使用的是弧度
                mListener.onMoveInfo(mSpeed, disLng, disLat, 90.0F-mAngle);
                mTimer.start();
            }
        });
        mJoystickLayout = inflater.inflate(R.layout.joystick, null);
        mBubble = (ImageButton) inflater.inflate(R.layout.joystick_bubble, null);
        mBubble.setOnTouchListener(new JoyStickOnTouchListener(mBubble));
        mBubble.setOnClickListener(view -> showWindow(mJoystickLayout));

        View dragHandle = mJoystickLayout.findViewById(R.id.joystick_drag);
        dragHandle.setOnTouchListener(new JoyStickOnTouchListener(mJoystickLayout));
        mJoystickLayout.findViewById(R.id.joystick_collapse).setOnClickListener(view -> showWindow(mBubble));

        /* 位置按钮点击事件处理 */
        ImageButton btnPosition = mJoystickLayout.findViewById(R.id.joystick_position);
        btnPosition.setOnClickListener(v -> showWindow(mMapLayout));

        /* 历史按钮点击事件处理 */
        ImageButton btnHistory = mJoystickLayout.findViewById(R.id.joystick_history);
        btnHistory.setOnClickListener(v -> showWindow(mHistoryLayout));

        speedButtons[0] = mJoystickLayout.findViewById(R.id.joystick_walk);
        speedButtons[1] = mJoystickLayout.findViewById(R.id.joystick_run);
        speedButtons[2] = mJoystickLayout.findViewById(R.id.joystick_bike);
        speedButtons[0].setOnClickListener(view -> selectSpeed(0, "setting_walk", R.string.setting_walk_default, 1.2));
        speedButtons[1].setOnClickListener(view -> selectSpeed(1, "setting_run", R.string.setting_run_default, 3.6));
        speedButtons[2].setOnClickListener(view -> selectSpeed(2, "setting_bike", R.string.setting_bike_default, 10.0));
        selectSpeed(0, "setting_walk", R.string.setting_walk_default, 1.2);

        /* 方向键点击处理 */
        RockerView rckView = mJoystickLayout.findViewById(R.id.joystick_rocker);
        rckView.setListener(this::processDirection);

        /* 方向键点击处理 */
        ButtonView btnView = mJoystickLayout.findViewById(R.id.joystick_button);
        btnView.setListener(this::processDirection);

        /* 这里用来决定摇杆类型 */
        if (sharedPreferences.getString("setting_joystick_type", "0").equals("0")) {
            rckView.setVisibility(VISIBLE);
            btnView.setVisibility(GONE);
        } else {
            rckView.setVisibility(GONE);
            btnView.setVisibility(VISIBLE);
        }
    }

    private void selectSpeed(int choice, String key, int defaultValue, double fallbackSpeed) {
        for (int index = 0; index < speedButtons.length; index++) {
            Button button = speedButtons[index];
            boolean selected = index == choice;
            button.setSelected(selected);
            button.setTextColor(getResources().getColor(selected ? R.color.colorPrimary : R.color.ui_ink, mContext.getTheme()));
            button.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
        try {
            mSpeed = Double.parseDouble(sharedPreferences.getString(key, getResources().getString(defaultValue)));
            if (!Double.isFinite(mSpeed) || mSpeed <= 0) mSpeed = fallbackSpeed;
        } catch (NumberFormatException e) {
            mSpeed = fallbackSpeed;
        }
    }

    private void processDirection(boolean auto, double angle, double r) {
        if (r <= 0) {
            mTimer.cancel();
            isMove = false;
            mListener.onMoveInfo(0, 0, 0, 0);
        } else {
            mAngle = angle;
            mR = r;
            if (auto) {
                if (!isMove) {
                    mTimer.start();
                    isMove = true;
                }
            } else {
                mTimer.cancel();
                isMove = false;
                // 注意：这里的 x y 与 圆中角度的对应问题（以 X 轴正向为 0 度）且转换为 km
                disLng = mSpeed * (double)(DivGo / 1000) * mR * Math.cos(mAngle * 2 * Math.PI / 360) / 1000;// 注意安卓中的三角函数使用的是弧度
                disLat = mSpeed * (double)(DivGo / 1000) * mR * Math.sin(mAngle * 2 * Math.PI / 360) / 1000;// 注意安卓中的三角函数使用的是弧度
                mListener.onMoveInfo(mSpeed, disLng, disLat, 90.0F-mAngle);
            }
        }
    }

    private class JoyStickOnTouchListener implements OnTouchListener {
        private final View overlayWindow;
        private final int touchSlop = ViewConfiguration.get(mContext).getScaledTouchSlop();
        private float x;
        private float y;
        private boolean dragging;
        private boolean touchCancelled;

        JoyStickOnTouchListener(View overlayWindow) {
            this.overlayWindow = overlayWindow;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    x = event.getRawX();
                    y = event.getRawY();
                    dragging = false;
                    touchCancelled = false;
                    view.setPressed(true);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (touchCancelled || mVisibleWindow != overlayWindow
                            || (!dragging && !exceedsTouchSlop(event))) break;
                    dragging = true;
                    view.setPressed(false);
                    float nowX = event.getRawX();
                    float nowY = event.getRawY();
                    int screenWidth = getResources().getDisplayMetrics().widthPixels;
                    int screenHeight = getResources().getDisplayMetrics().heightPixels;
                    overlayX = clampPosition(mWindowParamCurrent.x + Math.round(nowX - x),
                            screenWidth, mWindowParamCurrent.width);
                    overlayY = clampPosition(mWindowParamCurrent.y + Math.round(nowY - y),
                            screenHeight, mWindowParamCurrent.height);
                    mWindowParamCurrent.x = overlayX;
                    mWindowParamCurrent.y = overlayY;
                    x = nowX;
                    y = nowY;
                    mWindowManager.updateViewLayout(overlayWindow, mWindowParamCurrent);
                    break;
                case MotionEvent.ACTION_UP:
                    view.setPressed(false);
                    if (!touchCancelled && !dragging && !exceedsTouchSlop(event) && mVisibleWindow == overlayWindow) {
                        view.performClick();
                    }
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_CANCEL:
                    touchCancelled = true;
                    view.setPressed(false);
                    break;
                default:
                    break;
            }
            return true;
        }

        private boolean exceedsTouchSlop(MotionEvent event) {
            return Math.hypot(event.getRawX() - x, event.getRawY() - y) > touchSlop;
        }
    }

    public interface JoyStickClickListener {
        void onMoveInfo(double speed, double disLng, double disLat, double angle);
        void onPositionInfo(double lng, double lat, double alt);
    }


    @SuppressLint({"InflateParams", "ClickableViewAccessibility"})
    private void initJoyStickMapView() {
        mMapLayout = (FrameLayout)inflater.inflate(R.layout.joystick_map, null);
        mMapLayout.setOnTouchListener(new JoyStickOnTouchListener(mMapLayout));

        mSearchList = mMapLayout.findViewById(R.id.map_search_list_view);
        mSearchLayout = mMapLayout.findViewById(R.id.map_search_linear);
        mPlaceSearch = new PlaceSearchController(mContext, () -> MainActivity.mCurrentCity,
                new PlaceSearchController.Listener() {
                    @Override public void onResults(List<Map<String, Object>> data) {
                        SimpleAdapter adapter = new SimpleAdapter(mContext, data, R.layout.search_poi_item,
                                new String[]{MainActivity.POI_NAME, MainActivity.POI_ADDRESS,
                                        MainActivity.POI_LONGITUDE, MainActivity.POI_LATITUDE},
                                new int[]{R.id.poi_name, R.id.poi_address, R.id.poi_longitude, R.id.poi_latitude});
                        mSearchList.setAdapter(adapter);
                        mSearchLayout.setVisibility(data.isEmpty() ? GONE : VISIBLE);
                    }
                    @Override public void onError(String message) {
                        GoUtils.DisplayToast(mContext, message);
                    }
                });
        mSearchList.setOnItemClickListener((parent, view, position, id) -> {
            mPlaceSearch.cancel();
            mSearchLayout.setVisibility(View.GONE);

            String lng = ((TextView) view.findViewById(R.id.poi_longitude)).getText().toString();
            String lat = ((TextView) view.findViewById(R.id.poi_latitude)).getText().toString();
            markBaiduMap(new LatLng(Double.parseDouble(lat), Double.parseDouble(lng)));
        });

        TextView tips = mMapLayout.findViewById(R.id.joystick_map_tips);
        SearchView mSearchView = mMapLayout.findViewById(R.id.joystick_map_searchView);
        mSearchView.setOnSearchClickListener(v -> {
            tips.setVisibility(GONE);

            // 特殊处理：这里让搜索框获取焦点，以显示输入法
            setSearchFocusable(mMapLayout, true);
        });
        mSearchView.setOnCloseListener(() -> {
            mPlaceSearch.cancel();
            tips.setVisibility(VISIBLE);
            mSearchLayout.setVisibility(GONE);

            // 关闭时清除焦点
            setSearchFocusable(mMapLayout, false);

            return false;       /* 这里必须返回false，否则需要自行处理搜索框的折叠 */
        });
        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                mPlaceSearch.search(query, true);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (newText != null && newText.length() > 0) {
                    try {
                        mPlaceSearch.search(newText, false);
                    } catch (Exception e) {
                        GoUtils.DisplayToast(mContext,getResources().getString(R.string.app_error_search));
                        e.printStackTrace();
                    }
                } else {
                    mPlaceSearch.cancel();
                    mSearchLayout.setVisibility(GONE);
                }

                return true;
            }
        });

        ImageButton btnGo = mMapLayout.findViewById(R.id.btnGo);
        btnGo.setOnClickListener(v -> {
            // 关闭时清除焦点
            setSearchFocusable(mMapLayout, false);

            tips.setVisibility(VISIBLE);
            mSearchView.clearFocus();
            mSearchView.onActionViewCollapsed();

            if (mMarkMapLngLat == null) {
                GoUtils.DisplayToast(mContext, getResources().getString(R.string.app_error_location));
            } else {
                if (mCurMapLngLat != mMarkMapLngLat) {
                    mCurMapLngLat = mMarkMapLngLat;
                    mMarkMapLngLat = null;

                    double[] lngLat = MapUtils.bd2wgs(mCurMapLngLat.longitude, mCurMapLngLat.latitude);
                    mListener.onPositionInfo(lngLat[0], lngLat[1], mAltitude);

                    resetBaiduMap();

                    GoUtils.DisplayToast(mContext, getResources().getString(R.string.app_location_ok));
                }
            }
        });
        btnGo.setColorFilter(getResources().getColor(R.color.colorAccent, mContext.getTheme()));

        ImageButton btnClose = mMapLayout.findViewById(R.id.map_close);
        btnClose.setOnClickListener(v -> showWindow(mJoystickLayout));

        ImageButton btnBack = mMapLayout.findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> resetBaiduMap());
        btnBack.setColorFilter(getResources().getColor(R.color.colorAccent, mContext.getTheme()));

        initBaiduMap();
    }

    private void initBaiduMap() {
        mMapView = mMapLayout.findViewById(R.id.map_joystick);
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
                markBaiduMap(point);
            }

            /**
             * 单击地图中的POI点
             */
            @Override
            public void onMapPoiClick(MapPoi poi) {
                markBaiduMap(poi.getPosition());
            }
        });

        mBaiduMap.setOnMapLongClickListener(new BaiduMap.OnMapLongClickListener() {
            /**
             * 长按地图
             */
            @Override
            public void onMapLongClick(LatLng point) {
                markBaiduMap(point);
            }
        });

        mBaiduMap.setOnMapDoubleClickListener(new BaiduMap.OnMapDoubleClickListener() {
            /**
             * 双击地图
             */
            @Override
            public void onMapDoubleClick(LatLng point) {
                markBaiduMap(point);
            }
        });
    }

    private void resetBaiduMap() {
        mBaiduMap.clear();

        MyLocationData locData = new MyLocationData.Builder()
                .latitude(mCurMapLngLat.latitude)
                .longitude(mCurMapLngLat.longitude)
                .build();
        mBaiduMap.setMyLocationData(locData);

        MapStatus.Builder builder = new MapStatus.Builder();
        builder.target(mCurMapLngLat).zoom(18.0f);
        mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()));
    }

    private void markBaiduMap(LatLng latLng) {
        mMarkMapLngLat = latLng;

        MarkerOptions ooA = new MarkerOptions().position(latLng).icon(MainActivity.mMapIndicator);
        mBaiduMap.clear();
        mBaiduMap.addOverlay(ooA);

        MapStatus.Builder builder = new MapStatus.Builder();
        builder.target(latLng).zoom(18.0f);
        mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()));
    }


    @SuppressLint({"InflateParams", "ClickableViewAccessibility"})
    private void initHistoryView() {
        mHistoryLayout = (FrameLayout)inflater.inflate(R.layout.joystick_history, null);
        mHistoryLayout.setOnTouchListener(new JoyStickOnTouchListener(mHistoryLayout));

        TextView tips = mHistoryLayout.findViewById(R.id.joystick_his_tips);
        SearchView mSearchView = mHistoryLayout.findViewById(R.id.joystick_his_searchView);
        mSearchView.setOnSearchClickListener(v -> {
            tips.setVisibility(GONE);

            // 特殊处理：这里让搜索框获取焦点，以显示输入法
            setSearchFocusable(mHistoryLayout, true);
        });
        mSearchView.setOnCloseListener(() -> {
            tips.setVisibility(VISIBLE);

            // 关闭时清除焦点
            setSearchFocusable(mHistoryLayout, false);

            return false;       /* 这里必须返回false，否则需要自行处理搜索框的折叠 */
        });
        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {// 当点击搜索按钮时触发该方法
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {// 当搜索内容改变时触发该方法
                if (TextUtils.isEmpty(newText)) {
                    showHistory(mAllRecord);
                } else {
                    List<Map<String, Object>> searchRet = new ArrayList<>();
                    for (int i = 0; i < mAllRecord.size(); i++){
                        if (mAllRecord.get(i).toString().indexOf(newText) > 0){
                            searchRet.add(mAllRecord.get(i));
                        }
                    }

                    if (searchRet.size() > 0) {
                        showHistory(searchRet);
                    } else {
                        GoUtils.DisplayToast(mContext, getResources().getString(R.string.app_search_null));
                        showHistory(mAllRecord);
                    }
                }

                return false;
            }
        });

        noRecordText = mHistoryLayout.findViewById(R.id.joystick_his_record_no_textview);
        mRecordListView = mHistoryLayout.findViewById(R.id.joystick_his_record_list_view);
        mRecordListView.setOnItemClickListener((adapterView, view, i, l) -> {
            // 关闭时清除焦点
            setSearchFocusable(mHistoryLayout, false);

            mSearchView.clearFocus();
            mSearchView.onActionViewCollapsed();
            tips.setVisibility(VISIBLE);

            // wgs84坐标
            String wgs84LatLng = (String) ((TextView) view.findViewById(R.id.WGSLatLngText)).getText();
            wgs84LatLng = wgs84LatLng.substring(wgs84LatLng.indexOf('[') + 1, wgs84LatLng.indexOf(']'));
            String[] wgs84latLngStr = wgs84LatLng.split(" ");
            String wgs84Longitude = wgs84latLngStr[0].substring(wgs84latLngStr[0].indexOf(':') + 1);
            String wgs84Latitude = wgs84latLngStr[1].substring(wgs84latLngStr[1].indexOf(':') + 1);

            mListener.onPositionInfo(Double.parseDouble(wgs84Longitude), Double.parseDouble(wgs84Latitude), mAltitude);

            // 注意这里在选择位置之后需要刷新地图
            String bdLatLng = (String) ((TextView) view.findViewById(R.id.BDLatLngText)).getText();
            bdLatLng = bdLatLng.substring(bdLatLng.indexOf('[') + 1, bdLatLng.indexOf(']'));
            String[] bdLatLngStr = bdLatLng.split(" ");
            String bdLongitude = bdLatLngStr[0].substring(bdLatLngStr[0].indexOf(':') + 1);
            String bdLatitude = bdLatLngStr[1].substring(bdLatLngStr[1].indexOf(':') + 1);
            mCurMapLngLat = new LatLng(Double.parseDouble(bdLatitude), Double.parseDouble(bdLongitude));

            GoUtils.DisplayToast(mContext, getResources().getString(R.string.app_location_ok));
        });

        fetchAllRecord();

        showHistory(mAllRecord);

        ImageButton btnClose = mHistoryLayout.findViewById(R.id.joystick_his_close);
        btnClose.setOnClickListener(v -> showWindow(mJoystickLayout));
    }

    private void fetchAllRecord() {
        SQLiteDatabase mHistoryLocationDB;

        try {
            DataBaseHistoryLocation hisLocDBHelper = new DataBaseHistoryLocation(mContext.getApplicationContext());
            mHistoryLocationDB = hisLocDBHelper.getWritableDatabase();

            Cursor cursor = mHistoryLocationDB.query(DataBaseHistoryLocation.TABLE_NAME, null,
                    DataBaseHistoryLocation.DB_COLUMN_ID + " > ?", new String[] {"0"},
                    null, null, DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP + " DESC", null);

            while (cursor.moveToNext()) {
                Map<String, Object> item = new HashMap<>();
                int ID = cursor.getInt(0);
                String Location = cursor.getString(1);
                String Longitude = cursor.getString(2);
                String Latitude = cursor.getString(3);
                long TimeStamp = cursor.getInt(4);
                String BD09Longitude = cursor.getString(5);
                String BD09Latitude = cursor.getString(6);
                Log.d("TB", ID + "\t" + Location + "\t" + Longitude + "\t" + Latitude + "\t" + TimeStamp + "\t" + BD09Longitude + "\t" + BD09Latitude);
                BigDecimal bigDecimalLongitude = BigDecimal.valueOf(Double.parseDouble(Longitude));
                BigDecimal bigDecimalLatitude = BigDecimal.valueOf(Double.parseDouble(Latitude));
                BigDecimal bigDecimalBDLongitude = BigDecimal.valueOf(Double.parseDouble(BD09Longitude));
                BigDecimal bigDecimalBDLatitude = BigDecimal.valueOf(Double.parseDouble(BD09Latitude));
                double doubleLongitude = bigDecimalLongitude.setScale(11, RoundingMode.HALF_UP).doubleValue();
                double doubleLatitude = bigDecimalLatitude.setScale(11, RoundingMode.HALF_UP).doubleValue();
                double doubleBDLongitude = bigDecimalBDLongitude.setScale(11, RoundingMode.HALF_UP).doubleValue();
                double doubleBDLatitude = bigDecimalBDLatitude.setScale(11, RoundingMode.HALF_UP).doubleValue();
                item.put(HistoryActivity.KEY_ID, Integer.toString(ID));
                item.put(HistoryActivity.KEY_LOCATION, Location);
                item.put(HistoryActivity.KEY_TIME, GoUtils.timeStamp2Date(Long.toString(TimeStamp)));
                item.put(HistoryActivity.KEY_LNG_LAT_WGS, "[经度:" + doubleLongitude + " 纬度:" + doubleLatitude + "]");
                item.put(HistoryActivity.KEY_LNG_LAT_CUSTOM, "[经度:" + doubleBDLongitude + " 纬度:" + doubleBDLatitude + "]");
                mAllRecord.add(item);
            }
            cursor.close();
            mHistoryLocationDB.close();
        } catch (Exception e) {
            Log.e("JOYSTICK", "ERROR - fetchAllRecord");
        }
    }

    private void showHistory(List<Map<String, Object>> list) {
        if (list.size() == 0) {
            mRecordListView.setVisibility(View.GONE);
            noRecordText.setVisibility(View.VISIBLE);
        } else {
            noRecordText.setVisibility(View.GONE);
            mRecordListView.setVisibility(View.VISIBLE);

            try {
                SimpleAdapter simAdapt = new SimpleAdapter(
                        mContext,
                        list,
                        R.layout.history_item,
                        new String[]{HistoryActivity.KEY_ID, HistoryActivity.KEY_LOCATION, HistoryActivity.KEY_TIME, HistoryActivity.KEY_LNG_LAT_WGS, HistoryActivity.KEY_LNG_LAT_CUSTOM}, // 与下面数组元素要一一对应
                        new int[]{R.id.LocationID, R.id.LocationText, R.id.TimeText, R.id.WGSLatLngText, R.id.BDLatLngText});
                mRecordListView.setAdapter(simAdapt);
            } catch (Exception e) {
                Log.e("JOYSTICK", "ERROR - showHistory");
            }
        }
    }
}
