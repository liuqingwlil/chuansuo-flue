package com.chuansuo.app;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import com.chuansuo.database.LibraryRepository;
import com.chuansuo.gpx.GpxCodec;
import com.chuansuo.model.LibraryItem;
import com.chuansuo.service.ServiceGo;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LibraryActivity extends AppCompatActivity {
    private static final int IMPORT = 81, EXPORT = 82;
    private static final int ALL_ENTRIES = -1, PLACES = 0, ROUTES = 1;
    private LibraryRepository repository;
    private LibraryEditorUi ui;
    private int selectedType = ALL_ENTRIES;
    private long selectedGroup = -1;
    private Button groupFilter;
    private final Button[] tabs = new Button[3];
    private EditText search;
    private ListView list;
    private TextView empty;
    private TextView status;
    private View playback;
    private Button togglePlayback, playbackSpeed;
    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private final Runnable statusTick = new Runnable() {
        @Override public void run() {
            updatePlayback();
            statusHandler.postDelayed(this, 1000);
        }
    };
    private ProgressBar progress;
    private List<LibraryRepository.Group> groups;
    private List<LibraryItem> items = new ArrayList<>();
    private long[] exportIds;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("收藏与线路");
        repository = new LibraryRepository(this);
        ui = new LibraryEditorUi(this);
        ui.prepareWindow(this);
        LinearLayout root = ui.column();
        root.setBackgroundColor(ui.canvas);
        root.setFocusableInTouchMode(true);
        ui.pad(root, 12, 0, 12, 8);
        setContentView(root);

        LinearLayout toolbar = ui.toolbar("收藏", this::finish);
        toolbar.addView(ui.iconButton(R.drawable.ic_more, "收藏选项", this::libraryMenu));
        root.addView(toolbar);

        search = new EditText(this);
        search.setSingleLine();
        search.setHint("搜索名称");
        ui.field(search);
        ui.decorateIcon(search, R.drawable.ic_search, ui.muted, 20);
        ui.add(root, search, 8);

        LinearLayout filters = ui.row();
        String[] tabNames = {"全部", "地点", "线路"};
        for (int index = 0; index < tabs.length; index++) {
            int type = index - 1;
            tabs[index] = ui.button(tabNames[index], false, 0, () -> setType(type));
            filters.addView(tabs[index], ui.fillWeight());
        }
        ui.add(root, filters, 8);
        root.addView(ui.divider());

        groupFilter = ui.button("全部分组", false, R.drawable.ic_folder, this::filterGroups);
        groupFilter.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        ui.add(root, groupFilter, 8);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(-1, ui.dp(4)));

        list = new ListView(this);
        ui.list(list);
        ui.pad(list, 0, 8, 0, 8);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        list.setOnItemClickListener((p, v, at, id) -> {
            LibraryRow row = (LibraryRow) p.getItemAtPosition(at);
            if (row.item != null) itemMenu(row.item);
        });
        empty = ui.text("暂无条目。可以新建或导入 GPX，也可以从主地图和历史位置保存收藏。", 15, ui.muted, false);
        empty.setGravity(Gravity.CENTER);
        ui.pad(empty, 24, 12, 24, 12);
        root.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1));

        playback = createPlayback();
        playback.setVisibility(View.GONE);
        ui.add(root, playback, 8);
        LinearLayout actions = ui.row();
        actions.addView(ui.button("新建地点", true, R.drawable.ic_position, () -> openEditor(false)), ui.fillWeight());
        LinearLayout.LayoutParams routeButton = ui.fillWeight();
        routeButton.setMarginStart(ui.dp(8));
        actions.addView(ui.button("新建线路", true, R.drawable.ic_route, () -> openEditor(true)), routeButton);
        ui.add(root, actions, 8);

        if (state != null) {
            selectedType = state.getInt("type", ALL_ENTRIES);
            search.setText(state.getString("search", ""));
            exportIds = state.getLongArray("export");
        }
        updateTabs();
        refreshGroups(state == null ? -1 : state.getLong("group", -1));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int f) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { refreshItems(); }
            @Override public void afterTextChanged(Editable e) { }
        });
        root.requestFocus();
    }
    @Override protected void onResume() { super.onResume(); refreshGroups(selectedGroup); statusHandler.post(statusTick); }
    @Override protected void onPause() { statusHandler.removeCallbacksAndMessages(null); super.onPause(); }
    @Override public boolean onSupportNavigateUp() { finish(); return true; }
    @Override protected void onDestroy() { io.shutdownNow(); repository.close(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putInt("type", selectedType);
        state.putLong("group", selectedGroup); state.putString("search", search.getText().toString()); state.putLongArray("export", exportIds);
    }

    private void openEditor(boolean route) {
        startActivity(new Intent(this, RouteActivity.class).putExtra("route", route).putExtra("group", selectedGroup));
    }

    private void libraryMenu() {
        new AlertDialog.Builder(this).setItems(new String[]{"管理分组", "导入 GPX", "导出当前列表"}, (d, at) -> {
            if (at == 0) groupMenu();
            else if (at == 1) startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), IMPORT);
            else export(items);
        }).show();
    }

    private View createPlayback() {
        LinearLayout panel = ui.column();
        panel.setBackground(ui.shape(ui.surface, 14, true));
        ui.pad(panel, 12, 10, 12, 10);
        TextView title = ui.text("模拟控制", 17, ui.ink, true);
        ui.decorateIcon(title, R.drawable.ic_route, ui.accent, 20);
        panel.addView(title);
        status = ui.text("", 14, ui.muted, false);
        ui.add(panel, status, 4);
        LinearLayout controls = ui.row();
        togglePlayback = ui.button("暂停", false, R.drawable.ic_pause,
                () -> ServiceGo.control(this, ServiceGo.ACTION_TOGGLE, 0));
        controls.addView(togglePlayback, ui.fillWeight());
        LinearLayout.LayoutParams speedButton = ui.fillWeight();
        speedButton.setMarginStart(ui.dp(6));
        playbackSpeed = ui.button("运行速度", false, 0, this::changeSpeed);
        controls.addView(playbackSpeed, speedButton);
        LinearLayout.LayoutParams stopButton = ui.fillWeight();
        stopButton.setMarginStart(ui.dp(6));
        controls.addView(ui.button("停止", true, R.drawable.ic_stop, () -> ServiceGo.control(this, ServiceGo.ACTION_STOP, 0)), stopButton);
        ui.add(panel, controls, 8);
        return panel;
    }

    private void updatePlayback() {
        boolean running = ServiceGo.isRunning();
        playback.setVisibility(running ? View.VISIBLE : View.GONE);
        if (!running) return;
        String currentStatus = ServiceGo.status();
        if (!TextUtils.equals(status.getText(), currentStatus)) status.setText(currentStatus);

        boolean canControlRoute = ServiceGo.hasActiveRoute() && !ServiceGo.isActiveRouteFinished();
        togglePlayback.setVisibility(canControlRoute ? View.VISIBLE : View.GONE);
        playbackSpeed.setVisibility(canControlRoute ? View.VISIBLE : View.GONE);
        if (canControlRoute) {
            boolean paused = ServiceGo.isActiveRoutePaused();
            String action = paused ? "继续" : "暂停";
            if (!TextUtils.equals(togglePlayback.getText(), action)) {
                togglePlayback.setText(action);
                ui.decorateIcon(togglePlayback, paused ? R.drawable.ic_play : R.drawable.ic_pause, ui.accent, 18);
            }
        }
    }

    private void setType(int type) {
        if (selectedType == type) return;
        selectedType = type;
        updateTabs();
        refreshItems();
    }

    private void updateTabs() {
        for (int index = 0; index < tabs.length; index++) {
            ui.tab(tabs[index], index - 1 == selectedType);
        }
    }

    private void filterGroups() {
        List<String> options = new ArrayList<>();
        options.add("全部分组");
        int selectedIndex = 0;
        for (LibraryRepository.Group group : groups) {
            if (group.id == selectedGroup) selectedIndex = options.size();
            options.add(group.name);
        }
        new AlertDialog.Builder(this).setTitle("按分组筛选")
                .setSingleChoiceItems(options.toArray(new String[0]), selectedIndex, (dialog, index) -> {
                    refreshGroups(index == 0 ? -1 : groups.get(index - 1).id);
                    dialog.dismiss();
                }).setNegativeButton("关闭", null).show();
    }

    private void refreshGroups(long selected) {
        groups = repository.groups();
        selectedGroup = -1;
        groupFilter.setText("全部分组");
        for (LibraryRepository.Group group : groups) {
            if (group.id == selected) {
                selectedGroup = group.id;
                groupFilter.setText(group.name);
                break;
            }
        }
        refreshItems();
    }

    private void refreshItems() {
        String query = search.getText().toString();
        items = new ArrayList<>();
        if (selectedType != ROUTES) items.addAll(repository.list(false, selectedGroup, query));
        if (selectedType != PLACES) items.addAll(repository.list(true, selectedGroup, query));
        Map<Long, List<LibraryItem>> byGroup = new LinkedHashMap<>();
        for (LibraryItem item : items) byGroup.computeIfAbsent(item.groupId, id -> new ArrayList<>()).add(item);
        List<LibraryRow> rows = new ArrayList<>();
        for (LibraryRepository.Group group : groups) {
            List<LibraryItem> entries = byGroup.get(group.id);
            if (entries == null) continue;
            rows.add(new LibraryRow(group.name + "（" + entries.size() + "）", null));
            for (LibraryItem item : entries) rows.add(new LibraryRow(item.name, item));
        }
        list.setAdapter(new LibraryRows(rows));
        empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        list.setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private static final class LibraryRow {
        final String title;
        final LibraryItem item;

        LibraryRow(String title, LibraryItem item) {
            this.title = title;
            this.item = item;
        }
    }

    private final class LibraryRows extends BaseAdapter {
        private final List<LibraryRow> rows;

        LibraryRows(List<LibraryRow> rows) { this.rows = rows; }

        @Override public int getCount() { return rows.size(); }
        @Override public LibraryRow getItem(int position) { return rows.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public boolean areAllItemsEnabled() { return false; }
        @Override public boolean isEnabled(int position) { return getItem(position).item != null; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int position) { return isEnabled(position) ? 1 : 0; }

        @Override public View getView(int position, View recycled, ViewGroup parent) {
            LibraryRow item = getItem(position);
            if (item.item == null) {
                TextView header = recycled == null ? ui.text("", 15, ui.ink, true) : (TextView) recycled;
                if (recycled == null) {
                    ui.pad(header, 14, 12, 14, 12);
                    ui.decorateIcon(header, R.drawable.ic_folder, ui.ink, 20);
                    header.setBackground(ui.shape(ui.soft, 12, false));
                    ViewCompat.setAccessibilityHeading(header, true);
                }
                header.setText(item.title);
                return header;
            }
            ItemRow row = recycled == null ? new ItemRow() : (ItemRow) recycled;
            row.bind(item.item);
            return row;
        }
    }

    private final class ItemRow extends LinearLayout {
        private final TextView title = ui.text("", 16, ui.ink, true);
        private final TextView detail = ui.text("", 13, ui.muted, false);

        ItemRow() {
            super(LibraryActivity.this);
            setOrientation(VERTICAL);
            setBackgroundColor(ui.surface);
            LinearLayout content = ui.row();
            ui.pad(content, 14, 10, 10, 10);
            content.setMinimumHeight(ui.dp(60));
            LinearLayout text = ui.column();
            text.addView(title);
            text.addView(detail);
            content.addView(text, ui.fillWeight());
            ImageView menu = new ImageView(LibraryActivity.this);
            menu.setImageDrawable(ui.icon(R.drawable.ic_more, ui.muted, 20));
            menu.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            content.addView(menu, new LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)));
            addView(content);
            addView(ui.divider());
        }

        void bind(LibraryItem item) {
            title.setText(item.pinned ? item.name + " · 已置顶" : item.name);
            ui.decorateIcon(title, item.route ? R.drawable.ic_route : R.drawable.ic_position, ui.pin, 20);
            detail.setText(item.route
                    ? String.format(Locale.CHINA, "%d 个节点 · %.0f 米 · %.1f 米/秒", item.points.size(), item.lengthMeters(), item.speed)
                    : String.format(Locale.US, "%.6f, %.6f", item.points.get(0).longitude, item.points.get(0).latitude));
            ui.pad(detail, 28, 1, 0, 0);
        }
    }
    private void itemMenu(LibraryItem item) {
        List<String> actions = new ArrayList<>(); actions.add("预览 / 编辑"); if (item.route) actions.add("启动线路移动");
        actions.add("导出 GPX"); actions.add("移动到分组"); actions.add(item.pinned ? "取消置顶" : "置顶"); actions.add("上移"); actions.add("下移"); actions.add("删除");
        new AlertDialog.Builder(this).setTitle(item.name).setItems(actions.toArray(new String[0]), (d, at) -> {
            String action = actions.get(at);
            switch (action) {
                case "预览 / 编辑": startActivity(new Intent(this, RouteActivity.class).putExtra("id", item.id)); break;
                case "启动线路移动": ServiceGo.startRoute(this, item.id); break;
                case "导出 GPX": export(java.util.Collections.singletonList(item)); break;
                case "移动到分组": chooseGroup(item); break;
                case "置顶": case "取消置顶": item.pinned = !item.pinned; repository.save(item); refreshItems(); break;
                case "上移": repository.moveItem(item.id, -1); refreshItems(); break;
                case "下移": repository.moveItem(item.id, 1); refreshItems(); break;
                case "删除": new AlertDialog.Builder(this).setMessage("删除此条目？").setPositiveButton("删除", (a, b) -> { repository.delete(item.id); refreshItems(); }).setNegativeButton("取消", null).show(); break;
            }
        }).show();
    }
    private void chooseGroup(LibraryItem item) {
        List<LibraryRepository.Group> all = repository.groups(); String[] labels = all.stream().map(g -> g.name).toArray(String[]::new);
        new AlertDialog.Builder(this).setTitle("移动到分组").setItems(labels, (d, at) -> { item.groupId = all.get(at).id; repository.save(item); refreshItems(); }).show();
    }
    private void groupMenu() {
        List<LibraryRepository.Group> all = repository.groups(); List<String> labels = new ArrayList<>(); labels.add("新建分组");
        for (int i = 1; i < all.size(); i++) labels.add(all.get(i).name);
        new AlertDialog.Builder(this).setTitle("管理分组").setItems(labels.toArray(new String[0]), (d, at) -> {
            if (at == 0) editGroup(0, "");
            else {
                LibraryRepository.Group g = all.get(at);
                new AlertDialog.Builder(this).setTitle(g.name).setItems(new String[]{"重命名", "上移", "下移", "删除分组"}, (a, choice) -> {
                    if (choice == 0) editGroup(g.id, g.name);
                    else if (choice < 3) { repository.moveGroup(g.id, choice == 1 ? -1 : 1); refreshGroups(g.id); }
                    else new AlertDialog.Builder(this).setMessage("删除分组后，条目保留在未分组中。").setPositiveButton("删除分组", (b, c) -> { repository.deleteGroup(g.id); refreshGroups(0); }).setNegativeButton("取消", null).show();
                }).show();
            }
        }).show();
    }
    private void editGroup(long id, String name) {
        EditText input = new EditText(this); input.setSingleLine(); input.setText(name);
        ui.field(input);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(id == 0 ? "新建分组" : "重命名分组").setView(input).setPositiveButton("保存", null).setNegativeButton("取消", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> {
            try { long saved = repository.saveGroup(id, input.getText().toString()); refreshGroups(saved); dialog.dismiss(); }
            catch (Exception e) { message("名称无效或分组已存在"); }
        })); dialog.show();
    }
    private void export(List<LibraryItem> selected) {
        if (selected.isEmpty()) { message("请先添加条目"); return; }
        exportIds = selected.stream().mapToLong(item -> item.id).toArray();
        startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/gpx+xml").putExtra(Intent.EXTRA_TITLE, getString(R.string.app_name) + ".gpx"), EXPORT);
    }
    private void changeSpeed() {
        EditText value = new EditText(this); value.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL); value.setText("1.2");
        ui.field(value);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("本次运行速度（米/秒）").setView(value).setPositiveButton("设置", null).setNegativeButton("取消", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> {
            try {
                double speed = Double.parseDouble(value.getText().toString());
                if (!Double.isFinite(speed) || speed <= 0 || speed > 100) throw new IllegalArgumentException();
                ServiceGo.control(this, ServiceGo.ACTION_SPEED, speed); dialog.dismiss();
            } catch (Exception e) { message("速度需大于 0 且不超过 100 米/秒"); }
        })); dialog.show();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data); if (result != Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData(); progress.setVisibility(View.VISIBLE);
        final long groupId = Math.max(0, selectedGroup); final long[] selectedIds = exportIds;
        io.execute(() -> {
            try {
                if (request == IMPORT) {
                    List<LibraryItem> parsed;
                    try (InputStream stream = getContentResolver().openInputStream(uri)) { parsed = GpxCodec.read(stream); }
                    String fileName = displayName(uri);
                    runOnUiThread(() -> { if (!isDestroyed()) { progress.setVisibility(View.GONE); previewImport(parsed, groupId, fileName); } });
                } else if (request == EXPORT && selectedIds != null) {
                    List<LibraryItem> selected = new ArrayList<>();
                    try (LibraryRepository db = new LibraryRepository(getApplicationContext())) {
                        for (long id : selectedIds) { LibraryItem item = db.get(id); if (item != null) selected.add(item); }
                    }
                    if (selected.isEmpty()) throw new IllegalArgumentException("待导出的条目已不存在");
                    try (OutputStream stream = getContentResolver().openOutputStream(uri, "wt")) { GpxCodec.write(stream, selected); }
                    runOnUiThread(() -> { if (!isDestroyed()) { progress.setVisibility(View.GONE); message("GPX 已导出"); } });
                }
            } catch (Exception e) { runOnUiThread(() -> { if (!isDestroyed()) { progress.setVisibility(View.GONE); message(e.getMessage()); } }); }
        });
    }
    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getString(0);
        } catch (RuntimeException ignored) {
            // Document providers may expose a stream without display metadata.
        }
        return "GPX 文件";
    }

    private void previewImport(List<LibraryItem> parsed, long groupId, String fileName) {
        LinearLayout panel = ui.column();
        panel.setBackground(ui.shape(ui.surface, 24, false));
        ui.pad(panel, 20, 16, 20, 16);
        TextView heading = ui.text("导入预览", 22, ui.ink, true);
        ViewCompat.setAccessibilityHeading(heading, true);
        panel.addView(heading);
        TextView file = ui.text(fileName, 16, ui.ink, true);
        ui.decorateIcon(file, R.drawable.ic_leading_in, ui.accent, 24);
        ui.add(panel, file, 12);
        ui.add(panel, ui.text("GPX · WGS84 · " + parsed.size() + " 个条目", 13, ui.muted, false), 4);
        ui.add(panel, ui.divider(), 12);

        ListView choices = new ListView(this);
        choices.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        ui.list(choices);
        choices.setDivider(new ColorDrawable(ui.line));
        choices.setDividerHeight(ui.dp(1));
        choices.setAdapter(new ArrayAdapter<LibraryItem>(this, android.R.layout.simple_list_item_multiple_choice, parsed) {
            @Override public View getView(int position, View recycled, ViewGroup parent) {
                CheckedTextView label = (CheckedTextView) super.getView(position, recycled, parent);
                LibraryItem item = getItem(position);
                if (item == null) return label;
                String description = item.route
                        ? String.format(Locale.CHINA, "%.0f 米 · %d 个节点", item.lengthMeters(), item.points.size())
                        : String.format(Locale.US, "经纬度 %.6f, %.6f", item.points.get(0).longitude, item.points.get(0).latitude);
                label.setText(item.name + "\n" + description);
                label.setTextSize(15);
                label.setTextColor(ui.ink);
                label.setCheckMarkTintList(ColorStateList.valueOf(ui.accent));
                label.setMinHeight(ui.dp(58));
                ui.pad(label, 4, 6, 8, 6);
                ui.decorateIcon(label, item.route ? R.drawable.ic_route : R.drawable.ic_position, ui.accent, 22);
                return label;
            }
        });
        for (int index = 0; index < parsed.size(); index++) choices.setItemChecked(index, true);
        int halfScreen = getResources().getConfiguration().screenHeightDp / 2;
        int listHeight = Math.max(58, Math.min(Math.min(320, halfScreen), parsed.size() * 60));
        panel.addView(choices, new LinearLayout.LayoutParams(-1, ui.dp(listHeight)));

        AlertDialog preview = new AlertDialog.Builder(this).setView(panel).create();
        LinearLayout actions = ui.row();
        actions.addView(ui.button("取消", false, 0, preview::dismiss), ui.fillWeight());
        LinearLayout.LayoutParams importButton = new LinearLayout.LayoutParams(0, -2, 2);
        importButton.setMarginStart(ui.dp(8));
        actions.addView(ui.button("导入所选", true, 0, () -> {
            List<LibraryItem> selected = new ArrayList<>();
            for (int index = 0; index < parsed.size(); index++) {
                if (choices.isItemChecked(index)) selected.add(parsed.get(index));
            }
            preview.dismiss();
            importItems(selected, groupId);
        }), importButton);
        ui.add(panel, actions, 12);
        preview.show();
        Window window = preview.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void importItems(List<LibraryItem> selected, long groupId) {
        if (selected.isEmpty()) return;
        progress.setVisibility(View.VISIBLE);
        io.execute(() -> {
            String result;
            try (LibraryRepository db = new LibraryRepository(getApplicationContext())) { db.importItems(selected, groupId); result = "已导入 " + selected.size() + " 个条目"; }
            catch (Exception e) { result = "导入未完成，未写入任何条目：" + e.getMessage(); }
            final String text = result;
            runOnUiThread(() -> { if (!isDestroyed()) { progress.setVisibility(View.GONE); refreshItems(); message(text); } });
        });
    }
    private void message(String text) { Toast.makeText(this, text == null ? "操作失败" : text, Toast.LENGTH_LONG).show(); }
}
