package com.chuansuo.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.preference.PreferenceManager;

import com.baidu.mapapi.search.core.PoiInfo;
import com.baidu.mapapi.search.core.SearchResult;
import com.baidu.mapapi.search.poi.OnGetPoiSearchResultListener;
import com.baidu.mapapi.search.poi.PoiCitySearchOption;
import com.baidu.mapapi.search.poi.PoiDetailResult;
import com.baidu.mapapi.search.poi.PoiDetailSearchResult;
import com.baidu.mapapi.search.poi.PoiIndoorResult;
import com.baidu.mapapi.search.poi.PoiResult;
import com.baidu.mapapi.search.poi.PoiSearch;
import com.baidu.mapapi.search.sug.SuggestionResult;
import com.baidu.mapapi.search.sug.SuggestionSearch;
import com.baidu.mapapi.search.sug.SuggestionSearchOption;
import com.chuansuo.app.R;
import com.elvishew.xlog.XLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Shared by the main map and floating map; all callbacks are delivered on main. */
public final class PlaceSearchController {
    public interface Listener {
        void onResults(List<Map<String, Object>> results);
        void onError(String message);
    }

    private final Context context;
    private final Supplier<String> locatedCity;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SuggestionSearch suggestions;
    private PoiSearch poi;
    private int generation;
    private boolean destroyed;

    public PlaceSearchController(Context context, Supplier<String> locatedCity, Listener listener) {
        this.context = context.getApplicationContext();
        this.locatedCity = locatedCity;
        this.listener = listener;
    }

    public void search(String input, boolean submitted) {
        cancel();
        final String query = input == null ? "" : input.trim();
        if (query.isEmpty()) {
            listener.onResults(Collections.emptyList());
            return;
        }
        final int requestId = generation;
        handler.postDelayed(() -> {
            if (!isCurrent(requestId)) return;
            String key = MapSettings.apiKey(context);
            if (key == null || key.trim().isEmpty()) {
                fail(requestId, context.getString(R.string.search_key_missing));
                return;
            }
            String configuredCity = PreferenceManager.getDefaultSharedPreferences(context)
                    .getString("setting_search_city", "");
            String city = SearchPolicy.resolveCity(configuredCity, locatedCity.get());
            try {
                if (submitted) searchPoi(query, city, requestId, true);
                else searchSuggestions(query, city, requestId);
            } catch (RuntimeException e) {
                XLog.e("Place search request failed: " + e.getClass().getSimpleName());
                fail(requestId, context.getString(R.string.app_error_search));
            }
        }, submitted ? 0 : 350);
    }

    private void searchSuggestions(String query, String city, int id) {
        suggestions = SuggestionSearch.newInstance();
        suggestions.setOnGetSuggestionResultListener(result -> handler.post(() -> {
            if (!isCurrent(id)) return;
            if (result == null) {
                fail(id, context.getString(R.string.app_error_search));
                return;
            }
            if (result.error != SearchResult.ERRORNO.NO_ERROR
                    && result.error != SearchResult.ERRORNO.RESULT_NOT_FOUND) {
                fail(id, errorMessage(result.error));
                return;
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            if (result.getAllSuggestions() != null) {
                for (SuggestionResult.SuggestionInfo item : result.getAllSuggestions()) {
                    if (item.pt != null) {
                        rows.add(row(item.key, safe(item.city) + " " + safe(item.district),
                                item.pt.longitude, item.pt.latitude));
                    }
                }
            }
            if (!rows.isEmpty()) listener.onResults(rows);
            else {
                try {
                    searchPoi(query, city, id, false);
                } catch (RuntimeException e) {
                    XLog.e("POI fallback failed: " + e.getClass().getSimpleName());
                    fail(id, context.getString(R.string.app_error_search));
                }
            }
        }));
        if (!suggestions.requestSuggestion(new SuggestionSearchOption()
                .city(city).citylimit(false).keyword(query))) {
            fail(id, context.getString(R.string.search_request_invalid));
        }
    }

    private void searchPoi(String query, String city, int id, boolean submitted) {
        poi = PoiSearch.newInstance();
        poi.setOnGetPoiSearchResultListener(new OnGetPoiSearchResultListener() {
            @Override public void onGetPoiResult(PoiResult result) {
                handler.post(() -> {
                    if (!isCurrent(id)) return;
                    if (result == null) {
                        fail(id, context.getString(R.string.app_error_search));
                        return;
                    }
                    if (result.error != SearchResult.ERRORNO.NO_ERROR
                            && result.error != SearchResult.ERRORNO.RESULT_NOT_FOUND
                            && result.error != SearchResult.ERRORNO.AMBIGUOUS_KEYWORD) {
                        fail(id, errorMessage(result.error));
                        return;
                    }
                    List<Map<String, Object>> rows = new ArrayList<>();
                    if (result.getAllPoi() != null) {
                        for (PoiInfo item : result.getAllPoi()) {
                            if (item.location != null) rows.add(row(item.name, item.address,
                                    item.location.longitude, item.location.latitude));
                        }
                    }
                    listener.onResults(rows);
                    if (rows.isEmpty() && submitted) {
                        String message = context.getString(R.string.app_search_null);
                        if (result.getSuggestCityList() != null
                                && !result.getSuggestCityList().isEmpty()) {
                            message = context.getString(R.string.search_choose_city);
                        }
                        listener.onError(message);
                    }
                });
            }
            @Override public void onGetPoiDetailResult(PoiDetailResult result) { }
            @Override public void onGetPoiDetailResult(PoiDetailSearchResult result) { }
            @Override public void onGetPoiIndoorResult(PoiIndoorResult result) { }
        });
        if (!poi.searchInCity(new PoiCitySearchOption().city(city).keyword(query)
                .cityLimit(false).pageNum(0).pageCapacity(20))) {
            fail(id, context.getString(R.string.search_request_invalid));
        }
    }

    private String errorMessage(SearchResult.ERRORNO error) {
        XLog.e("Baidu place search error: " + error);
        if (error == SearchResult.ERRORNO.KEY_ERROR
                || error == SearchResult.ERRORNO.PERMISSION_UNFINISHED
                || error == SearchResult.ERRORNO.NO_ADVANCED_PERMISSION) {
            return context.getString(R.string.search_key_invalid);
        }
        if (error == SearchResult.ERRORNO.NETWORK_ERROR
                || error == SearchResult.ERRORNO.NETWORK_TIME_OUT) {
            return context.getString(R.string.search_network_error);
        }
        return context.getString(R.string.app_error_search) + " (" + error + ")";
    }

    private void fail(int id, String message) {
        if (!isCurrent(id)) return;
        listener.onResults(Collections.emptyList());
        listener.onError(message);
    }

    private static Map<String, Object> row(String name, String address, double lng, double lat) {
        Map<String, Object> item = new HashMap<>();
        item.put("POI_NAME", safe(name));
        item.put("POI_ADDRESS", safe(address));
        item.put("POI_LONGITUDE", Double.toString(lng));
        item.put("POI_LATITUDE", Double.toString(lat));
        return item;
    }

    private static String safe(String text) { return text == null ? "" : text; }
    private boolean isCurrent(int id) { return !destroyed && id == generation; }

    public void cancel() {
        generation++;
        handler.removeCallbacksAndMessages(null);
        if (suggestions != null) { suggestions.destroy(); suggestions = null; }
        if (poi != null) { poi.destroy(); poi = null; }
    }

    public void destroy() {
        destroyed = true;
        cancel();
    }
}
