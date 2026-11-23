package com.chuansuo.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.chuansuo.model.Coordinate;
import com.chuansuo.model.LibraryItem;
import java.util.ArrayList;
import java.util.List;

/** Independent library: history expiration never touches saved places/routes. */
public final class LibraryRepository extends SQLiteOpenHelper {
    public static final class Group {
        public final long id;
        public final String name;
        Group(long id, String name) { this.id = id; this.name = name; }
        @Override public String toString() { return name; }
    }

    public LibraryRepository(Context context) { super(context, "Library.db", null, 1); }
    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE groups_ (_id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE, rank INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE items (_id INTEGER PRIMARY KEY, kind INTEGER NOT NULL, name TEXT NOT NULL, "
                + "group_id INTEGER REFERENCES groups_(_id) ON DELETE SET NULL, pinned INTEGER NOT NULL DEFAULT 0, "
                + "speed REAL NOT NULL, loop INTEGER NOT NULL DEFAULT 0, rank INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE points (item_id INTEGER NOT NULL REFERENCES items(_id) ON DELETE CASCADE, "
                + "sequence INTEGER NOT NULL, lng REAL NOT NULL, lat REAL NOT NULL, alt REAL NOT NULL, "
                + "time INTEGER, segment INTEGER NOT NULL, name TEXT NOT NULL, PRIMARY KEY(item_id,sequence))");
        db.execSQL("CREATE INDEX items_group ON items(group_id,kind,pinned,rank)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("缺少资料库升级步骤，原数据已保留");
    }

    public List<Group> groups() {
        List<Group> result = new ArrayList<>();
        result.add(new Group(0, "未分组"));
        try (Cursor c = getReadableDatabase().rawQuery("SELECT _id,name FROM groups_ ORDER BY rank,_id", null)) {
            while (c.moveToNext()) result.add(new Group(c.getLong(0), c.getString(1)));
        }
        return result;
    }
    public long saveGroup(long id, String name) {
        if (name == null || name.trim().isEmpty() || name.trim().length() > 100) throw new IllegalArgumentException("分组名称需为 1～100 个字符");
        ContentValues v = new ContentValues(); v.put("name", name.trim());
        SQLiteDatabase db = getWritableDatabase();
        if (id == 0) { v.put("rank", System.currentTimeMillis()); return db.insertOrThrow("groups_", null, v); }
        if (db.update("groups_", v, "_id=?", args(id)) != 1) throw new IllegalArgumentException("分组不存在");
        return id;
    }
    public void deleteGroup(long id) { if (id != 0) getWritableDatabase().delete("groups_", "_id=?", args(id)); }
    public void moveGroup(long id, int delta) {
        List<Group> list = groups(); int at = -1;
        for (int i = 1; i < list.size(); i++) if (list.get(i).id == id) at = i;
        if (at < 1 || at + delta < 1 || at + delta >= list.size()) return;
        Group swap = list.remove(at); list.add(at + delta, swap);
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            for (int i = 1; i < list.size(); i++) {
                ContentValues v = new ContentValues(); v.put("rank", i);
                db.update("groups_", v, "_id=?", args(list.get(i).id));
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public long save(LibraryItem item) {
        item.validate(); SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try { long id = saveInternal(db, item); db.setTransactionSuccessful(); item.id = id; return id; }
        finally { db.endTransaction(); }
    }
    public void importItems(List<LibraryItem> items, long group) {
        if (items.size() > 1000) throw new IllegalArgumentException("一次最多导入 1000 个条目");
        for (LibraryItem item : items) item.validate();
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            for (LibraryItem source : items) {
                LibraryItem item = new LibraryItem(source.name, source.route);
                item.points.addAll(source.points); item.speed = source.speed; item.loop = source.loop;
                item.groupId = group;
                saveInternal(db, item);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    private long saveInternal(SQLiteDatabase db, LibraryItem item) {
        ContentValues v = new ContentValues();
        v.put("kind", item.route ? 1 : 0); v.put("name", item.name.trim());
        if (item.groupId == 0) v.putNull("group_id"); else v.put("group_id", item.groupId);
        v.put("pinned", item.pinned ? 1 : 0); v.put("speed", item.speed); v.put("loop", item.loop ? 1 : 0);
        long id = item.id;
        if (id == 0) { v.put("rank", System.currentTimeMillis()); id = db.insertOrThrow("items", null, v); }
        else if (db.update("items", v, "_id=?", args(id)) != 1) throw new IllegalArgumentException("条目不存在");
        db.delete("points", "item_id=?", args(id));
        for (int i = 0; i < item.points.size(); i++) {
            Coordinate point = item.points.get(i); ContentValues p = new ContentValues();
            p.put("item_id", id); p.put("sequence", i); p.put("lng", point.longitude);
            p.put("lat", point.latitude); p.put("alt", point.altitude);
            if (point.timeMillis == null) p.putNull("time"); else p.put("time", point.timeMillis);
            p.put("segment", point.segment); p.put("name", point.name);
            db.insertOrThrow("points", null, p);
        }
        return id;
    }
    public LibraryItem get(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT _id,kind,name,group_id,pinned,speed,loop FROM items WHERE _id=?", args(id))) {
            return c.moveToFirst() ? read(c) : null;
        }
    }
    public List<LibraryItem> list(boolean routes, long group, String keyword) {
        String where = "kind=?"; List<String> values = new ArrayList<>(); values.add(routes ? "1" : "0");
        if (group >= 0) { where += group == 0 ? " AND group_id IS NULL" : " AND group_id=?"; if (group > 0) values.add(Long.toString(group)); }
        if (keyword != null && !keyword.trim().isEmpty()) {
            where += " AND instr(lower(name),lower(?))>0"; values.add(keyword.trim());
        }
        List<LibraryItem> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT _id,kind,name,group_id,pinned,speed,loop FROM items WHERE "
                + where + " ORDER BY pinned DESC,rank,_id", values.toArray(new String[0]))) {
            while (c.moveToNext()) result.add(read(c));
        }
        return result;
    }
    private LibraryItem read(Cursor c) {
        LibraryItem item = new LibraryItem(c.getString(2), c.getInt(1) == 1);
        item.id = c.getLong(0); item.groupId = c.isNull(3) ? 0 : c.getLong(3);
        item.pinned = c.getInt(4) != 0; item.speed = c.getDouble(5); item.loop = c.getInt(6) != 0;
        try (Cursor p = getReadableDatabase().rawQuery("SELECT lng,lat,alt,time,segment,name FROM points WHERE item_id=? ORDER BY sequence", args(item.id))) {
            while (p.moveToNext()) item.points.add(new Coordinate(p.getDouble(0), p.getDouble(1), p.getDouble(2),
                    p.isNull(3) ? null : p.getLong(3), p.getInt(4), p.getString(5)));
        }
        return item;
    }
    public void delete(long id) { getWritableDatabase().delete("items", "_id=?", args(id)); }
    public void moveItem(long id, int delta) {
        LibraryItem item = get(id); if (item == null) return;
        List<LibraryItem> list = list(item.route, item.groupId, "");
        list.removeIf(row -> row.pinned != item.pinned);
        int at = -1; for (int i = 0; i < list.size(); i++) if (list.get(i).id == id) at = i;
        if (at < 0 || at + delta < 0 || at + delta >= list.size()) return;
        LibraryItem swap = list.remove(at); list.add(at + delta, swap);
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            for (int i = 0; i < list.size(); i++) { ContentValues v = new ContentValues(); v.put("rank", i); db.update("items", v, "_id=?", args(list.get(i).id)); }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    private static String[] args(long id) { return new String[]{Long.toString(id)}; }
}
