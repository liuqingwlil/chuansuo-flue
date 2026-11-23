package com.chuansuo.app;

import android.app.Application;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.chuansuo.database.LibraryRepository;
import com.chuansuo.model.Coordinate;
import com.chuansuo.model.LibraryItem;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.Arrays;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class LibraryRepositoryTest {
    private Context context;
    private LibraryRepository db;
    @Before public void before() { context = RuntimeEnvironment.getApplication(); context.deleteDatabase("Library.db"); db = new LibraryRepository(context); }
    @After public void after() { db.close(); }
    private LibraryItem place(String name) { LibraryItem item = new LibraryItem(name, false); item.points.add(new Coordinate(116, 39, 55)); return item; }
    private LibraryItem route(String name) { LibraryItem item = new LibraryItem(name, true); item.points.add(new Coordinate(116, 39, 55, 1700000000000L, 0, "start")); item.points.add(new Coordinate(116.01, 39.01, 65, null, 0, "end")); return item; }
    @Test public void savesAndReopensRouteWithMetadataAndPointOrder() {
        LibraryItem item = route("线路"); item.groupId = db.saveGroup(0, "工作"); item.speed = 3.6; item.loop = true; item.pinned = true; db.save(item);
        db.close(); db = new LibraryRepository(context); LibraryItem saved = db.get(item.id);
        assertEquals(item.name, saved.name); assertTrue(saved.route); assertTrue(saved.loop); assertTrue(saved.pinned); assertEquals(3.6, saved.speed, 0);
        assertEquals(116.01, saved.points.get(1).longitude, 0); assertEquals(Long.valueOf(1700000000000L), saved.points.get(0).timeMillis); assertEquals("end", saved.points.get(1).name);
    }
    @Test public void deletingGroupPreservesItemsAsUngrouped() {
        LibraryItem item = place("地点"); item.groupId = db.saveGroup(0, "临时"); db.save(item); db.deleteGroup(item.groupId);
        assertNotNull(db.get(item.id)); assertEquals(0, db.get(item.id).groupId); assertEquals(1, db.list(false, 0, "").size());
    }
    @Test public void deletingRouteCascadesOnlyItsPoints() {
        LibraryItem a = route("A"), b = place("B"); db.save(a); db.save(b); db.delete(a.id);
        try (Cursor c = db.getReadableDatabase().rawQuery("SELECT count(*) FROM points", null)) { c.moveToFirst(); assertEquals(1, c.getInt(0)); }
        assertNotNull(db.get(b.id));
    }
    @Test public void updateReplacesOldPointsRatherThanAppending() {
        LibraryItem item = route("线路"); db.save(item); item.points.remove(1); item.points.add(new Coordinate(117, 40, 0)); db.save(item);
        assertEquals(2, db.get(item.id).points.size()); assertEquals(117, db.get(item.id).points.get(1).longitude, 0);
    }
    @Test public void importValidatesBeforeWritingAnyRows() {
        LibraryItem invalid = route("无效"); invalid.points.clear();
        assertThrows(IllegalArgumentException.class, () -> db.importItems(Arrays.asList(place("正常"), invalid), 0));
        assertTrue(db.list(false, -1, "").isEmpty());
    }
    @Test public void failedForeignKeyImportRollsBackAllRows() {
        assertThrows(Exception.class, () -> db.importItems(Arrays.asList(place("A"), place("B")), 99999));
        assertTrue(db.list(false, -1, "").isEmpty());
    }
    @Test public void pinsAndFiltersWithinGroups() {
        long group = db.saveGroup(0, "工作"); LibraryItem a = place("甲"), b = place("乙"); a.groupId = group; b.groupId = group; db.save(a); db.save(b);
        b.pinned = true; db.save(b); assertEquals(b.id, db.list(false, group, "").get(0).id);
        assertEquals(1, db.list(false, group, "甲").size()); assertTrue(db.list(false, 0, "").isEmpty());
    }
    @Test public void reordersGroupsAndItems() {
        long a = db.saveGroup(0, "A"), b = db.saveGroup(0, "B"); db.moveGroup(b, -1); assertEquals(b, db.groups().get(1).id);
        db.saveGroup(a, "C"); assertEquals("C", db.groups().get(2).name);
        LibraryItem x = place("X"), y = place("Y"); db.save(x); db.save(y); db.moveItem(y.id, -1); assertEquals(y.id, db.list(false, 0, "").get(0).id);
    }
    @Test public void savedLibraryIsIndependentFromHistoryCleanup() {
        LibraryItem item = place("长期收藏"); db.save(item);
        try (SQLiteDatabase history = context.openOrCreateDatabase("HistoryLocation.db", 0, null)) { history.execSQL("CREATE TABLE IF NOT EXISTS history (_id INTEGER)"); history.execSQL("DELETE FROM history"); }
        assertEquals("长期收藏", db.get(item.id).name);
    }
    @Test public void duplicateGroupNameDoesNotDestroyExistingGroup() {
        long id = db.saveGroup(0, "常用"); assertThrows(Exception.class, () -> db.saveGroup(0, "常用")); assertEquals(id, db.groups().get(1).id);
    }
}
