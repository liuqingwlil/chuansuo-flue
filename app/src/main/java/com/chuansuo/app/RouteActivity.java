package com.chuansuo.app;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.Editable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import com.baidu.mapapi.map.*;
import com.baidu.mapapi.model.LatLng;
import com.chuansuo.database.LibraryRepository;
import com.chuansuo.model.Coordinate;
import com.chuansuo.model.LibraryItem;
import com.chuansuo.service.ServiceGo;
import com.chuansuo.utils.MapSettings;
import com.chuansuo.utils.MapUtils;
import com.chuansuo.utils.PlaceSearchController;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RouteActivity extends AppCompatActivity {
    private LibraryRepository repository;
    private LibraryItem item;
    private LibraryEditorUi ui;
    private EditText name, speed, keyword;
    private CheckBox loop;
    private ListView nodes;
    private TextView summary, distance, duration;
    private Button groupSelector;
    private MapView mapView;
    private BaiduMap map;
    private BitmapDescriptor marker;
    private PlaceSearchController search;
    private boolean dirty;

    public static Intent newPlace(Context context, String name, double lng, double lat, double alt) {
        return new Intent(context, RouteActivity.class).putExtra("route", false).putExtra("name", name)
                .putExtra("lng", lng).putExtra("lat", lat).putExtra("alt", alt);
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        repository = new LibraryRepository(this);
        item = repository.get(getIntent().getLongExtra("id", 0));
        if (item == null) {
            item = new LibraryItem(getIntent().getStringExtra("name"), getIntent().getBooleanExtra("route", true));
            if (item.name == null) item.name = item.route ? "新线路" : "新地点";
            item.groupId = Math.max(0, getIntent().getLongExtra("group", 0));
            if (getIntent().hasExtra("lng")) item.points.add(new Coordinate(getIntent().getDoubleExtra("lng", 0), getIntent().getDoubleExtra("lat", 0), getIntent().getDoubleExtra("alt", 55)));
        }
        if (state != null) restore(state.getString("draft"));
        setTitle(item.route ? "编辑线路" : "编辑地点");
        ui = new LibraryEditorUi(this);
        ui.prepareWindow(this);
        createEditor();
        search = new PlaceSearchController(this, () -> MainActivity.mCurrentCity, new PlaceSearchController.Listener() {
            @Override public void onResults(List<Map<String, Object>> rows) {
                if (rows.isEmpty() || isFinishing()) return;
                String[] labels = rows.stream().map(row -> row.get("POI_NAME") + "\n" + row.get("POI_ADDRESS")).toArray(String[]::new);
                new AlertDialog.Builder(RouteActivity.this).setTitle("选择节点").setItems(labels, (d, at) -> {
                    Map<String, Object> row = rows.get(at);
                    addBaidu(new LatLng(Double.parseDouble(row.get("POI_LATITUDE").toString()), Double.parseDouble(row.get("POI_LONGITUDE").toString())), row.get("POI_NAME").toString());
                }).show();
            }
            @Override public void onError(String text) { message(text); }
        });
        if (state != null) speed.setText(state.getString("speedText", Double.toString(item.speed)));
        refresh(); dirty = state != null && state.getBoolean("dirty");
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int f) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { dirty = true; }
            @Override public void afterTextChanged(Editable value) { updateSummary(); }
        };
        name.addTextChangedListener(watcher); speed.addTextChangedListener(watcher);
        loop.setOnCheckedChangeListener((view, checked) -> dirty = true);
    }

    private void createEditor() {
        LinearLayout root = ui.column();
        root.setBackgroundColor(ui.canvas);
        root.setFocusableInTouchMode(true);
        ui.pad(root, 12, 0, 12, 8);
        setContentView(root);

        LinearLayout toolbar = ui.toolbar(getTitle(), this::onBackPressed);
        if (item.route) {
            Button save = ui.button("保存", false, 0, () -> save(true));
            save.setId(R.id.route_save);
            toolbar.addView(save);
        }
        root.addView(toolbar);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setFillViewport(false);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout form = ui.column();
        ui.pad(form, 2, 8, 2, 12);
        scroll.addView(form);
        String nameLabel = item.route ? "线路名称" : "地点名称";
        TextView label = ui.text(nameLabel, 15, ui.ink, true);
        label.setLabelFor(R.id.route_name);
        form.addView(label);
        name = field(form, nameLabel, item.name);
        name.setId(R.id.route_name);
        if (item.route) addRouteSummary(form);

        LinearLayout mapTools = ui.column();
        if (item.route) {
            mapTools.setVisibility(View.GONE);
            Button mapToggle = ui.button("地图与地点搜索", false, R.drawable.ic_map, () -> {
                boolean open = mapTools.getVisibility() != View.VISIBLE;
                mapTools.setVisibility(open ? View.VISIBLE : View.GONE);
                if (open && mapView != null) refreshMap();
            });
            ui.add(form, mapToggle, 8);
        }
        addMapTools(mapTools);
        ui.add(form, mapTools, 4);

        LinearLayout actions = ui.row();
        Button add = ui.button("输入坐标", false, R.drawable.ic_input, () -> editPoint(-1));
        add.setId(R.id.route_add_point);
        actions.addView(add, ui.fillWeight());
        LinearLayout.LayoutParams saved = ui.fillWeight();
        saved.setMarginStart(ui.dp(8));
        actions.addView(ui.button("从收藏添加", false, 0, this::chooseSavedPlace), saved);
        ui.add(form, actions, 8);

        nodes = new ListView(this);
        nodes.setId(R.id.route_points);
        nodes.setBackground(ui.shape(ui.surface, 14, true));
        nodes.setClipToOutline(true);
        ui.list(nodes);
        nodes.setOnItemClickListener((p, v, at, id) -> pointMenu(at));
        summary = ui.text("", 13, ui.muted, false);
        ui.add(form, summary, 8);

        if (item.route) {
            addGroupSelector(form);
            root.addView(nodes, new LinearLayout.LayoutParams(-1, 0, 1.35f));
        } else {
            int pointHeight = Math.round(108 * Math.max(1, getResources().getConfiguration().fontScale));
            form.addView(nodes, new LinearLayout.LayoutParams(-1, ui.dp(pointHeight)));
            ui.add(form, ui.text("收藏分组", 17, ui.ink, true), 16);
            addGroupSelector(form);
        }
        addEditorFooter(root);
        root.requestFocus();
    }

    private void addRouteSummary(LinearLayout form) {
        LinearLayout card = ui.row();
        card.setBaselineAligned(false);
        card.setBackground(ui.shape(ui.surface, 14, true));
        ui.pad(card, 12, 8, 12, 8);
        distance = addStatistic(card, "总距离", R.drawable.ic_route);
        View divider = new View(this);
        divider.setBackgroundColor(ui.line);
        card.addView(divider, new LinearLayout.LayoutParams(ui.dp(1), ui.dp(40)));
        duration = addStatistic(card, "预计时长", R.drawable.ic_date);
        ui.add(form, card, 12);
    }

    private TextView addStatistic(LinearLayout card, String title, int icon) {
        LinearLayout statistic = ui.column();
        ui.pad(statistic, 8, 0, 4, 0);
        TextView label = ui.text(title, 13, ui.muted, false);
        ui.decorateIcon(label, icon, ui.accent, 16);
        statistic.addView(label);
        TextView value = ui.text("", 19, ui.ink, true);
        statistic.addView(value);
        card.addView(statistic, ui.fillWeight());
        return value;
    }

    private void addMapTools(LinearLayout parent) {
        if (shouldShowMap()) {
            FrameLayout mapFrame = new FrameLayout(this) {
                @Override public boolean dispatchTouchEvent(MotionEvent event) {
                    int action = event.getActionMasked();
                    if (action == MotionEvent.ACTION_DOWN) getParent().requestDisallowInterceptTouchEvent(true);
                    else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                        getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    return super.dispatchTouchEvent(event);
                }
            };
            mapFrame.setBackground(ui.shape(ui.soft, 14, true));
            mapFrame.setClipToOutline(true);
            mapView = new MapView(this);
            mapFrame.addView(mapView, new FrameLayout.LayoutParams(-1, -1));
            int mapHeight = Math.max(144, Math.min(240, getResources().getConfiguration().screenHeightDp / 3));
            LinearLayout.LayoutParams frame = new LinearLayout.LayoutParams(-1, ui.dp(mapHeight));
            frame.topMargin = ui.dp(8);
            parent.addView(mapFrame, frame);
            map = mapView.getMap();
            marker = BitmapDescriptorFactory.fromResource(R.drawable.icon_gcoding);
            map.setOnMapClickListener(new BaiduMap.OnMapClickListener() {
                @Override public void onMapClick(LatLng p) { addBaidu(p, "地图选点"); }
                @Override public void onMapPoiClick(MapPoi p) { addBaidu(p.getPosition(), p.getName()); }
            });
        } else {
            TextView hint = ui.text("填写地图 Key 后可在地图上选点。现在可以输入 GPS 坐标或使用收藏地点。", 14, ui.muted, false);
            hint.setBackground(ui.shape(ui.soft, 14, false));
            ui.pad(hint, 12, 12, 12, 12);
            ui.add(parent, hint, 4);
        }
        LinearLayout query = ui.row();
        keyword = new EditText(this);
        keyword.setSingleLine();
        keyword.setHint("搜索地点并添加");
        ui.field(keyword);
        ui.decorateIcon(keyword, R.drawable.ic_search, ui.muted, 20);
        query.addView(keyword, ui.fillWeight());
        LinearLayout.LayoutParams searchButton = new LinearLayout.LayoutParams(-2, -2);
        searchButton.setMarginStart(ui.dp(8));
        query.addView(ui.button("搜索", false, 0, () -> search.search(keyword.getText().toString(), true)), searchButton);
        ui.add(parent, query, 8);
    }

    private void addGroupSelector(LinearLayout form) {
        groupSelector = ui.button("选择分组", false, R.drawable.ic_folder, this::chooseGroup);
        groupSelector.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        ui.add(form, groupSelector, 8);
        updateGroupLabel();
    }

    private void updateGroupLabel() {
        String name = repository.groups().stream().filter(group -> group.id == item.groupId)
                .map(group -> group.name).findFirst().orElse("未分组");
        groupSelector.setText(item.route ? "保存分组 · " + name : name);
    }

    private void addEditorFooter(LinearLayout root) {
        root.addView(ui.divider());
        LinearLayout bottom = ui.row();
        ui.pad(bottom, 0, 8, 0, 0);
        speed = new EditText(this);
        speed.setHint("米/秒");
        speed.setId(R.id.route_speed);
        speed.setSingleLine();
        speed.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        speed.setText(Double.toString(item.speed));
        ui.field(speed);
        loop = new CheckBox(this);
        loop.setText("循环");
        loop.setTextColor(ui.ink);
        loop.setButtonTintList(ColorStateList.valueOf(ui.accent));
        loop.setChecked(item.loop);
        loop.setMinHeight(ui.dp(48));

        if (item.route) {
            LinearLayout rate = ui.column();
            TextView label = ui.text("速度（米/秒）", 12, ui.muted, false);
            label.setLabelFor(R.id.route_speed);
            rate.addView(label);
            rate.addView(speed);
            bottom.addView(rate, ui.fillWeight());
            bottom.addView(loop);
            bottom.addView(ui.button("开始播放", true, R.drawable.ic_play, () -> {
                if (save(false)) ServiceGo.startRoute(this, item.id);
            }), new LinearLayout.LayoutParams(0, -2, 1.45f));
        } else {
            Button save = ui.button("保存地点", true, 0, () -> save(true));
            save.setId(R.id.route_save);
            bottom.addView(save, ui.fillWeight());
            LinearLayout.LayoutParams preview = ui.fillWeight();
            preview.setMarginStart(ui.dp(8));
            bottom.addView(ui.button("模拟此位置", false, 0, () -> {
                if (save(false)) ServiceGo.startPlace(this, item.points.get(0));
            }), preview);
        }
        root.addView(bottom);
    }
    protected boolean shouldShowMap() { return !MapSettings.apiKey(this).isEmpty(); }
    private void addBaidu(LatLng p, String title) {
        double[] wgs = MapUtils.bd2wgs(p.longitude, p.latitude);
        add(new Coordinate(wgs[0], wgs[1], 55, null, lastSegment(), title));
    }
    private int lastSegment() { return item.points.isEmpty() ? 0 : item.points.get(item.points.size() - 1).segment; }
    private void add(Coordinate p) {
        if (item.points.size() >= 10000) { message("最多支持 10000 个节点"); return; }
        if (!item.route) item.points.clear(); item.points.add(p); dirty = true; refresh();
    }
    private void chooseSavedPlace() {
        List<LibraryItem> places = repository.list(false, -1, "");
        if (places.isEmpty()) { message("还没有收藏地点"); return; }
        new AlertDialog.Builder(this).setTitle("选择收藏地点").setItems(places.stream().map(p -> p.name).toArray(String[]::new), (d, at) -> {
            Coordinate p = places.get(at).points.get(0); add(new Coordinate(p.longitude, p.latitude, p.altitude, p.timeMillis, lastSegment(), places.get(at).name));
        }).show();
    }
    private void editPoint(int at) {
        Coordinate p = at < 0 ? new Coordinate(116.397, 39.908, 55) : item.points.get(at);
        LinearLayout view = ui.column();
        ui.pad(view, 20, 8, 20, 8);
        EditText lng = numeric(view, "经度（WGS84）", p.longitude, R.id.point_longitude);
        EditText lat = numeric(view, "纬度（WGS84）", p.latitude, R.id.point_latitude);
        EditText alt = numeric(view, "海拔（米）", p.altitude, View.generateViewId());
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(at < 0 ? "添加 GPS 坐标" : "编辑节点")
                .setView(view).setPositiveButton("确定", null).setNegativeButton("取消", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> {
            try {
                Coordinate point = new Coordinate(Double.parseDouble(lng.getText().toString()), Double.parseDouble(lat.getText().toString()), Double.parseDouble(alt.getText().toString()),
                        at < 0 ? null : p.timeMillis, at < 0 ? lastSegment() : p.segment, p.name);
                if (at < 0) add(point); else { item.points.set(at, point); dirty = true; refresh(); }
                dialog.dismiss();
            } catch (Exception e) { message("请填写有效经纬度和海拔"); }
        })); dialog.show();
    }
    private void pointMenu(int at) {
        String[] actions = item.route ? new String[]{"编辑坐标", "上移", "下移", "删除", "从此节点新建分段", "连接前一分段"} : new String[]{"编辑坐标", "删除"};
        new AlertDialog.Builder(this).setTitle("节点 " + (at + 1)).setItems(actions, (d, choice) -> {
            String action = actions[choice];
            if ("编辑坐标".equals(action)) { editPoint(at); return; }
            if ("删除".equals(action)) item.points.remove(at);
            else if ("上移".equals(action) && at > 0) Collections.swap(item.points, at, at - 1);
            else if ("下移".equals(action) && at + 1 < item.points.size()) Collections.swap(item.points, at, at + 1);
            else if (action.contains("分段") && at > 0) {
                int old = item.points.get(at).segment;
                int replacement = action.startsWith("连接") ? item.points.get(at - 1).segment
                        : item.points.stream().mapToInt(point -> point.segment).max().orElse(0) + 1;
                for (int i = at; i < item.points.size() && item.points.get(i).segment == old; i++) {
                    Coordinate p = item.points.get(i); item.points.set(i, new Coordinate(p.longitude, p.latitude, p.altitude, p.timeMillis, replacement, p.name));
                }
            }
            dirty = true; refresh();
        }).show();
    }
    private void chooseGroup() {
        List<LibraryRepository.Group> groups = repository.groups();
        new AlertDialog.Builder(this).setTitle("保存到分组")
                .setItems(groups.stream().map(g -> g.name).toArray(String[]::new), (d, at) -> {
                    item.groupId = groups.get(at).id;
                    dirty = true;
                    updateGroupLabel();
                }).show();
    }
    private boolean save(boolean close) {
        try {
            item.name = name.getText().toString(); item.speed = Double.parseDouble(speed.getText().toString()); item.loop = loop.isChecked(); repository.save(item);
            dirty = false; message("已保存"); if (close) finish(); return true;
        } catch (Exception e) { message(e.getMessage()); return false; }
    }
    private void refresh() {
        int position = nodes.getFirstVisiblePosition();
        View first = nodes.getChildAt(0);
        int offset = first == null ? 0 : first.getTop();
        nodes.setAdapter(new PointRows());
        if (!item.points.isEmpty()) nodes.setSelectionFromTop(Math.min(position, item.points.size() - 1), offset);
        nodes.setVisibility(item.route || !item.points.isEmpty() ? View.VISIBLE : View.GONE);
        updateSummary();
        refreshMap();
    }

    private final class PointRows extends BaseAdapter {
        private final int[] segmentNumbers;
        private final int segmentCount;

        PointRows() {
            segmentNumbers = new int[item.points.size()];
            int segment = 0;
            for (int index = 0; index < segmentNumbers.length; index++) {
                if (index == 0 || item.points.get(index).segment != item.points.get(index - 1).segment) segment++;
                segmentNumbers[index] = segment;
            }
            segmentCount = segment;
        }

        @Override public int getCount() { return segmentNumbers.length; }
        @Override public Coordinate getItem(int position) { return item.points.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View recycled, ViewGroup parent) {
            PointRow row = recycled == null ? new PointRow() : (PointRow) recycled;
            String heading = item.route && (position == 0 || segmentNumbers[position] != segmentNumbers[position - 1])
                    ? "第 " + segmentNumbers[position] + " 段（共 " + segmentCount + " 段）" : "";
            row.bind(getItem(position), position + 1, heading);
            return row;
        }
    }

    private final class PointRow extends LinearLayout {
        private final TextView header = ui.text("", 15, ui.ink, true);
        private final TextView index = ui.text("", 13, ui.accent, true);
        private final TextView title = ui.text("", 16, ui.ink, true);
        private final TextView detail = ui.text("", 13, ui.muted, false);

        PointRow() {
            super(RouteActivity.this);
            setOrientation(VERTICAL);
            ui.pad(header, 14, 10, 14, 10);
            header.setBackgroundColor(ui.soft);
            ViewCompat.setAccessibilityHeading(header, true);
            addView(header);
            LinearLayout row = ui.row();
            ui.pad(row, 12, 8, 10, 8);
            row.setMinimumHeight(ui.dp(54));
            index.setGravity(Gravity.CENTER);
            index.setBackground(ui.shape(ui.soft, 14, false));
            LinearLayout.LayoutParams indexLayout = new LinearLayout.LayoutParams(ui.dp(28), ui.dp(28));
            indexLayout.setMarginEnd(ui.dp(10));
            row.addView(index, indexLayout);
            LinearLayout labels = ui.column();
            ui.decorateIcon(title, R.drawable.ic_position, ui.pin, 18);
            labels.addView(title);
            labels.addView(detail);
            ui.pad(detail, 26, 1, 0, 0);
            row.addView(labels, ui.fillWeight());
            ImageView menu = new ImageView(RouteActivity.this);
            menu.setImageDrawable(ui.icon(R.drawable.ic_more, ui.muted, 20));
            menu.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(menu, new LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)));
            addView(row);
            addView(ui.divider());
        }

        void bind(Coordinate point, int number, String heading) {
            header.setText(heading);
            header.setVisibility(heading.isEmpty() ? View.GONE : View.VISIBLE);
            index.setText(Integer.toString(number));
            index.setVisibility(item.route ? View.VISIBLE : View.GONE);
            String fallback = item.route ? "节点 " + number : "当前位置";
            title.setText(point.name.isEmpty() ? fallback : point.name);
            detail.setText(item.route
                    ? String.format(Locale.US, "%.6f, %.6f · %.1f 米", point.longitude, point.latitude, point.altitude)
                    : String.format(Locale.US, "纬度 %.6f · 经度 %.6f\n海拔 %.1f 米", point.latitude, point.longitude, point.altitude));
        }
    }

    private void refreshMap() {
        if (map != null) {
            map.clear(); List<LatLng> segment = new ArrayList<>(); int previous = -1;
            int index = 0, markerStep = Math.max(1, (item.points.size() + 199) / 200);
            for (Coordinate p : item.points) {
                double[] bd = MapUtils.wgs2bd09(p.longitude, p.latitude); LatLng point = new LatLng(bd[1], bd[0]);
                if (index % markerStep == 0 || index == item.points.size() - 1) map.addOverlay(new MarkerOptions().position(point).icon(marker));
                index++;
                if (p.segment != previous) { drawLine(segment); segment.clear(); previous = p.segment; }
                if (segment.isEmpty() || !point.equals(segment.get(segment.size() - 1))) segment.add(point);
            }
            drawLine(segment);
            if (!item.points.isEmpty()) { Coordinate p = item.points.get(0); double[] bd = MapUtils.wgs2bd09(p.longitude, p.latitude); map.setMapStatus(MapStatusUpdateFactory.newLatLngZoom(new LatLng(bd[1], bd[0]), 15)); }
        }
    }
    private void updateSummary() {
        if (!item.route) {
            summary.setText(item.points.isEmpty() ? "选择一个收藏地点" : "坐标 · WGS84");
            return;
        }
        double rate;
        try { rate = Double.parseDouble(speed.getText().toString()); } catch (NumberFormatException e) { rate = item.speed; }
        double length = item.lengthMeters();
        distance.setText(length < 1000
                ? String.format(Locale.CHINA, "%.0f 米", length)
                : String.format(Locale.CHINA, "%.2f 公里", length / 1000));
        duration.setText(Double.isFinite(rate) && rate > 0
                ? String.format(Locale.CHINA, "约 %.1f 分钟", length / rate / 60) : "—");
        summary.setText(item.points.isEmpty() ? "添加节点以创建线路" : item.points.size() + " 个节点");
    }
    private void drawLine(List<LatLng> points) {
        if (item.route && points.size() >= 2) {
            map.addOverlay(new PolylineOptions().points(new ArrayList<>(points)).width(8).color(ui.accent));
        }
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); boolean changed = hasChanges(); item.name = name.getText().toString(); item.loop = loop.isChecked();
        try { double value = Double.parseDouble(speed.getText().toString()); if (Double.isFinite(value) && value > 0 && value <= 100) item.speed = value; } catch (Exception ignored) { }
        state.putString("draft", draft()); state.putBoolean("dirty", changed); state.putString("speedText", speed.getText().toString());
    }
    private String draft() {
        try {
            JSONObject o = new JSONObject(); o.put("id", item.id); o.put("name", item.name); o.put("route", item.route); o.put("group", item.groupId); o.put("speed", item.speed); o.put("loop", item.loop); o.put("pinned", item.pinned);
            JSONArray points = new JSONArray(); for (Coordinate p : item.points) points.put(new JSONArray().put(p.longitude).put(p.latitude).put(p.altitude).put(p.timeMillis == null ? JSONObject.NULL : p.timeMillis).put(p.segment).put(p.name));
            o.put("points", points); return o.toString();
        } catch (Exception e) { return ""; }
    }
    private void restore(String value) {
        try {
            JSONObject o = new JSONObject(value); item = new LibraryItem(o.getString("name"), o.getBoolean("route")); item.id = o.getLong("id"); item.groupId = o.getLong("group"); item.speed = o.getDouble("speed"); item.loop = o.getBoolean("loop"); item.pinned = o.getBoolean("pinned");
            JSONArray points = o.getJSONArray("points"); for (int i = 0; i < points.length(); i++) { JSONArray p = points.getJSONArray(i); item.points.add(new Coordinate(p.getDouble(0), p.getDouble(1), p.getDouble(2), p.isNull(3) ? null : p.getLong(3), p.getInt(4), p.getString(5))); }
        } catch (Exception e) { message("无法恢复编辑草稿"); }
    }
    private boolean hasChanges() { return dirty || !name.getText().toString().equals(item.name) || !speed.getText().toString().equals(Double.toString(item.speed)) || loop.isChecked() != item.loop; }
    @Override public boolean onSupportNavigateUp() { onBackPressed(); return true; }
    @Override public void onBackPressed() {
        if (!hasChanges()) { super.onBackPressed(); return; }
        new AlertDialog.Builder(this).setMessage("保存修改后退出？").setPositiveButton("保存", (d, at) -> save(true)).setNegativeButton("放弃", (d, at) -> finish()).setNeutralButton("继续编辑", null).show();
    }
    @Override protected void onResume() { super.onResume(); if (mapView != null) mapView.onResume(); }
    @Override protected void onPause() { if (mapView != null) mapView.onPause(); super.onPause(); }
    @Override protected void onDestroy() { if (search != null) search.destroy(); if (mapView != null) mapView.onDestroy(); if (marker != null) marker.recycle(); repository.close(); super.onDestroy(); }
    private EditText field(LinearLayout root, String hint, String value) {
        EditText field = new EditText(this);
        field.setSingleLine();
        field.setHint(hint);
        field.setText(value);
        ui.field(field);
        ui.add(root, field, 4);
        return field;
    }

    private EditText numeric(LinearLayout root, String label, double value, int id) {
        TextView description = ui.text(label, 14, ui.muted, true);
        description.setLabelFor(id);
        ui.add(root, description, 8);
        EditText field = field(root, label, Double.toString(value));
        field.setId(id);
        field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        return field;
    }
    private void message(String value) { Toast.makeText(this, value == null ? "保存失败" : value, Toast.LENGTH_LONG).show(); }
}
