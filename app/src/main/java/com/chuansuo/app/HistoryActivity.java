package com.chuansuo.app;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.view.Gravity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.chuansuo.database.DataBaseHistoryLocation;
import com.chuansuo.utils.GoUtils;
import com.chuansuo.utils.HistoryPolicy;

public class HistoryActivity extends BaseActivity {
    public static final String KEY_ID = "KEY_ID";
    public static final String KEY_LOCATION = "KEY_LOCATION";
    public static final String KEY_TIME = "KEY_TIME";
    public static final String KEY_LNG_LAT_WGS = "KEY_LNG_LAT_WGS";
    public static final String KEY_LNG_LAT_CUSTOM = "KEY_LNG_LAT_CUSTOM";

    private static final String KEY_DAY = "KEY_DAY";
    private static final String KEY_PRECISE_WGS = "KEY_PRECISE_WGS";
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA);

    private ListView mRecordListView;
    private Toolbar toolbar;
    private SearchView searchView;
    private TextView noRecordText;
    private LinearLayout mSearchLayout;
    private SQLiteDatabase mHistoryLocationDB;
    private List<Map<String, Object>> mAllRecord = new ArrayList<>();
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_history);
        SecondaryChrome.applySystemBars(this, getWindow());

        toolbar = findViewById(R.id.history_toolbar);
        toolbar.setNavigationOnClickListener(view -> finish());
        toolbar.inflateMenu(R.menu.menu_history);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_history_search) {
                setSearchVisible(mSearchLayout.getVisibility() != View.VISIBLE);
                return true;
            }
            if (item.getItemId() == R.id.action_delete) {
                confirmClearHistory();
                return true;
            }
            return false;
        });

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        initLocationDataBase();
        initSearchView();
        initRecordListView();
    }

    @Override
    protected void onDestroy() {
        if (mHistoryLocationDB != null) mHistoryLocationDB.close();
        super.onDestroy();
    }

    private void confirmClearHistory() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.history_clear)
                .setMessage(R.string.history_clear_message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (deleteRecord(-1)) {
                        GoUtils.DisplayToast(this, getString(R.string.history_delete_ok));
                        updateRecordList();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void initLocationDataBase() {
        try {
            DataBaseHistoryLocation hisLocDBHelper = new DataBaseHistoryLocation(getApplicationContext());
            mHistoryLocationDB = hisLocDBHelper.getWritableDatabase();
        } catch (Exception e) {
            Log.e("HistoryActivity", "ERROR - initLocationDataBase");
        }

        recordArchive();
    }

    //sqlite 操作 查询所有记录
    private List<Map<String, Object>> fetchAllRecord() {
        List<Map<String, Object>> data = new ArrayList<>();

        try (Cursor cursor = mHistoryLocationDB.query(DataBaseHistoryLocation.TABLE_NAME, null,
                DataBaseHistoryLocation.DB_COLUMN_ID + " > ?", new String[]{"0"},
                null, null, DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP + " DESC", null)) {
            while (cursor.moveToNext()) {
                Map<String, Object> item = new HashMap<>();
                int ID = cursor.getInt(0);
                String Location = cursor.getString(1);
                String Longitude = cursor.getString(2);
                String Latitude = cursor.getString(3);
                long TimeStamp = cursor.getLong(4);
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
                item.put(KEY_ID, Integer.toString(ID));
                item.put(KEY_LOCATION, Location);
                item.put(KEY_TIME, GoUtils.timeStamp2Date(Long.toString(TimeStamp)));
                item.put(KEY_DAY, Instant.ofEpochSecond(TimeStamp).atZone(ZoneId.systemDefault()).format(DAY_FORMAT));
                item.put(KEY_LNG_LAT_WGS, getString(R.string.history_coordinates, doubleLongitude, doubleLatitude));
                item.put(KEY_PRECISE_WGS, doubleLongitude + " " + doubleLatitude);
                item.put(KEY_LNG_LAT_CUSTOM, "[经度:" + doubleBDLongitude + " 纬度:" + doubleBDLatitude + "]");
                data.add(item);
            }
        } catch (Exception e) {
            data.clear();
            Log.e("HistoryActivity", "ERROR - fetchAllRecord");
        }

        return data;
    }

    private void recordArchive() {
        long cutoff = HistoryPolicy.cutoffSeconds(
                sharedPreferences.getString(HistoryPolicy.EXPIRATION_KEY,
                        getString(R.string.history_expiration)), System.currentTimeMillis() / 1000);

        try {
            mHistoryLocationDB.delete(DataBaseHistoryLocation.TABLE_NAME,
                    DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP + " < ?", new String[] {Long.toString(cutoff)});
        } catch (Exception e) {
            Log.e("HistoryActivity", "ERROR - recordArchive");
        }
    }

    private boolean deleteRecord(int ID) {
        boolean deleteRet = true;

        try {
            if (ID <= -1) {
                mHistoryLocationDB.delete(DataBaseHistoryLocation.TABLE_NAME,null, null);
            } else {
                mHistoryLocationDB.delete(DataBaseHistoryLocation.TABLE_NAME,
                        DataBaseHistoryLocation.DB_COLUMN_ID + " = ?", new String[] {Integer.toString(ID)});
            }
        } catch (Exception e) {
            deleteRet = false;
            Log.e("HistoryActivity", "ERROR - deleteRecord");
        }

        return deleteRet;
    }

    private void initSearchView() {
        mSearchLayout = findViewById(R.id.search_linear);
        searchView = findViewById(R.id.searchView);
        searchView.setSubmitButtonEnabled(false);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String query) {
                showRecords(query);
                return true;
            }
        });
    }

    private void setSearchVisible(boolean visible) {
        mSearchLayout.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            searchView.requestFocus();
        } else {
            searchView.setQuery("", false);
            InputMethodManager inputMethod = getSystemService(InputMethodManager.class);
            if (inputMethod != null) inputMethod.hideSoftInputFromWindow(searchView.getWindowToken(), 0);
            searchView.clearFocus();
        }
    }

    private void showDeleteDialog(String locID) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.history_delete);
        builder.setMessage(R.string.history_delete_message);
        builder.setPositiveButton(android.R.string.ok, (dialog, whichButton) -> {
            boolean deleteRet = deleteRecord(Integer.parseInt(locID));
            if (deleteRet) {
                GoUtils.DisplayToast(HistoryActivity.this, getResources().getString(R.string.history_delete_ok));
                updateRecordList();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);

        builder.show();
    }

    private void showInputDialog(String locID, String name) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(name);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("名称");
        builder.setView(input);
        builder.setPositiveButton("确认", (dialog, whichButton) -> {
            String userInput = input.getText().toString();
            DataBaseHistoryLocation.updateHistoryLocation(mHistoryLocationDB, locID, userInput);
            updateRecordList();
        });
        builder.setNegativeButton(android.R.string.cancel, null);

        builder.show();
    }

    private String[] randomOffset(String longitude, String latitude) {
        String max_offset_default = getResources().getString(R.string.setting_random_offset_default);
        double lon_max_offset = Double.parseDouble(Objects.requireNonNull(sharedPreferences.getString("setting_lon_max_offset", max_offset_default)));
        double lat_max_offset = Double.parseDouble(Objects.requireNonNull(sharedPreferences.getString("setting_lat_max_offset", max_offset_default)));
        double lon = Double.parseDouble(longitude);
        double lat = Double.parseDouble(latitude);

        double randomLonOffset = (Math.random() * 2 - 1) * lon_max_offset;  // Longitude offset (meters)
        double randomLatOffset = (Math.random() * 2 - 1) * lat_max_offset;  // Latitude offset (meters)

        lon += randomLonOffset / 111320;    // (meters -> longitude)
        lat += randomLatOffset / 110574;    // (meters -> latitude)

        String offsetMessage = String.format(Locale.US, "经度偏移: %.2f米\n纬度偏移: %.2f米", randomLonOffset, randomLatOffset);
        GoUtils.DisplayToast(this, offsetMessage);

        return new String[]{String.valueOf(lon), String.valueOf(lat)};
    }

    private void initRecordListView() {
        noRecordText = findViewById(R.id.record_no_textview);
        mRecordListView = findViewById(R.id.record_list_view);
        mRecordListView.setOnItemClickListener((adapterView, view, i, l) -> {
            String bd09Longitude;
            String bd09Latitude;
            String name;
            name = ((TextView) view.findViewById(R.id.LocationText)).getText().toString();
            String bd09LatLng = ((TextView) view.findViewById(R.id.BDLatLngText)).getText().toString();
            bd09LatLng = bd09LatLng.substring(bd09LatLng.indexOf('[') + 1, bd09LatLng.indexOf(']'));
            String[] latLngStr = bd09LatLng.split(" ");
            bd09Longitude = latLngStr[0].substring(latLngStr[0].indexOf(':') + 1);
            bd09Latitude = latLngStr[1].substring(latLngStr[1].indexOf(':') + 1);

            // Random offset
            if(sharedPreferences.getBoolean("setting_random_offset", false)) {
                String[] offsetResult = randomOffset(bd09Longitude, bd09Latitude);
                bd09Longitude = offsetResult[0];
                bd09Latitude = offsetResult[1];
            }

            if (!MainActivity.showLocation(name, bd09Longitude, bd09Latitude)) {
                GoUtils.DisplayToast(this, getResources().getString(R.string.history_error_location));
            }
            this.finish();
        });

        mRecordListView.setOnItemLongClickListener((parent, view, position, id) -> {
            PopupMenu popupMenu = new PopupMenu(HistoryActivity.this, view);
            popupMenu.setGravity(Gravity.END | Gravity.BOTTOM);
            popupMenu.getMenu().add("保存为收藏");
            popupMenu.getMenu().add("编辑");
            popupMenu.getMenu().add("删除");

            popupMenu.setOnMenuItemClickListener(item -> {
                String locID = ((TextView) view.findViewById(R.id.LocationID)).getText().toString();
                String name = ((TextView) view.findViewById(R.id.LocationText)).getText().toString();
                switch (item.getTitle().toString()) {
                    case "保存为收藏":
                        try (Cursor c = mHistoryLocationDB.query(DataBaseHistoryLocation.TABLE_NAME, null,
                                DataBaseHistoryLocation.DB_COLUMN_ID + "=?", new String[]{locID}, null, null, null)) {
                            if (c.moveToFirst()) startActivity(RouteActivity.newPlace(this, name,
                                    Double.parseDouble(c.getString(c.getColumnIndexOrThrow(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_WGS84))),
                                    Double.parseDouble(c.getString(c.getColumnIndexOrThrow(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_WGS84))), 55));
                        }
                        return true;
                    case "编辑":
                        showInputDialog(locID, name);
                        return true;
                    case "删除":
                        showDeleteDialog(locID);
                        return true;
                    default:
                        return false;
                }
            });

            popupMenu.show();
            return true;
        });

        updateRecordList();
    }

    private void updateRecordList() {
        mAllRecord = fetchAllRecord();
        boolean hasRecords = !mAllRecord.isEmpty();
        toolbar.getMenu().findItem(R.id.action_history_search).setEnabled(hasRecords);
        toolbar.getMenu().findItem(R.id.action_delete).setEnabled(hasRecords);
        if (!hasRecords && mSearchLayout.getVisibility() == View.VISIBLE) setSearchVisible(false);
        showRecords(searchView.getQuery().toString());
    }

    private void showRecords(String query) {
        if (mRecordListView == null) return;
        String term = query.trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> records = new ArrayList<>();
        Map<String, Integer> countByDay = new HashMap<>();
        for (Map<String, Object> record : mAllRecord) {
            if (!term.isEmpty() && !record.toString().toLowerCase(Locale.ROOT).contains(term)) continue;
            records.add(record);
            countByDay.merge((String) record.get(KEY_DAY), 1, Integer::sum);
        }

        mRecordListView.setVisibility(records.isEmpty() ? View.GONE : View.VISIBLE);
        noRecordText.setVisibility(records.isEmpty() ? View.VISIBLE : View.GONE);
        noRecordText.setText(mAllRecord.isEmpty() ? R.string.history_idle : R.string.history_error_search);
        mRecordListView.setAdapter(new SimpleAdapter(this, records, R.layout.history_record_row,
                new String[]{KEY_ID, KEY_LOCATION, KEY_TIME, KEY_LNG_LAT_WGS, KEY_LNG_LAT_CUSTOM},
                new int[]{R.id.LocationID, R.id.LocationText, R.id.TimeText, R.id.WGSLatLngText, R.id.BDLatLngText}) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View row = super.getView(position, convertView, parent);
                TextView section = row.findViewById(R.id.history_day);
                String day = (String) records.get(position).get(KEY_DAY);
                boolean startsDay = position == 0 || !day.equals(records.get(position - 1).get(KEY_DAY));
                section.setVisibility(startsDay ? View.VISIBLE : View.GONE);
                if (startsDay) section.setText(getString(R.string.history_day_count, day, countByDay.get(day)));
                return row;
            }
        });
    }
}
