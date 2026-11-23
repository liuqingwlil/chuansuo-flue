package com.chuansuo.app;

import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;
import com.chuansuo.database.LibraryRepository;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class RouteEditorTest {
    public static class EditorWithoutNativeMap extends RouteActivity {
        @Override protected boolean shouldShowMap() { return false; }
    }
    @Before public void before() {
        RuntimeEnvironment.getApplication().deleteDatabase("Library.db");
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.getApplication())
                .edit().putString("setting_map_key", "configured-key-for-editor-test").commit();
    }
    @Test public void coordinateEditorSavesPlaceToRepository() {
        Intent intent = RouteActivity.newPlace(RuntimeEnvironment.getApplication(), "原地点", 116, 39, 55);
        try (ActivityController<EditorWithoutNativeMap> c = Robolectric.buildActivity(EditorWithoutNativeMap.class, intent).setup()) {
            RouteActivity a = c.get(); ((EditText) a.findViewById(R.id.route_name)).setText("测试收藏");
            a.findViewById(R.id.route_save).performClick();
            try (LibraryRepository db = new LibraryRepository(a)) { assertEquals("测试收藏", db.list(false, -1, "").get(0).name); }
        }
    }
    @Test public void invalidCoordinateDoesNotCloseInputDialogOrAddNode() {
        try (ActivityController<EditorWithoutNativeMap> c = Robolectric.buildActivity(EditorWithoutNativeMap.class).setup()) {
            RouteActivity a = c.get(); a.findViewById(R.id.route_add_point).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
            ((EditText) dialog.findViewById(R.id.point_latitude)).setText("91"); dialog.getButton(-1).performClick();
            assertTrue(dialog.isShowing());
            assertEquals(0, ((android.widget.ListView) a.findViewById(R.id.route_points)).getCount());
            ((EditText) dialog.findViewById(R.id.point_latitude)).setText("39"); dialog.getButton(-1).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertFalse(dialog.isShowing());
            assertEquals(1, ((android.widget.ListView) a.findViewById(R.id.route_points)).getCount());
        }
    }
    @Test public void draftSurvivesActivityRecreation() {
        Intent intent = RouteActivity.newPlace(RuntimeEnvironment.getApplication(), "地点", 116, 39, 55);
        Bundle saved = new Bundle();
        try (ActivityController<EditorWithoutNativeMap> c = Robolectric.buildActivity(EditorWithoutNativeMap.class, intent).setup()) {
            ((EditText) c.get().findViewById(R.id.route_name)).setText("未保存草稿"); c.saveInstanceState(saved);
        }
        try (ActivityController<EditorWithoutNativeMap> c = Robolectric.buildActivity(EditorWithoutNativeMap.class, intent).setup(saved)) {
            assertEquals("未保存草稿", ((EditText) c.get().findViewById(R.id.route_name)).getText().toString());
        }
    }
}
