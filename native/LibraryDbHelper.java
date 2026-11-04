package com.shorts.app.v2;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

public class LibraryDbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "shorts_library.db";
    private static final int DB_VERSION = 1;

    public LibraryDbHelper(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE videos (id TEXT PRIMARY KEY, folder_uri TEXT NOT NULL, uri TEXT NOT NULL, name TEXT, size INTEGER, modified INTEGER, thumbnail TEXT, added_at INTEGER)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }

    public synchronized JSONArray getVideos(String folderUri) {
        JSONArray result = new JSONArray();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query("videos", null, "folder_uri=?", new String[]{folderUri}, null, null, "added_at ASC")) {
            while (c.moveToNext()) {
                JSONObject item = new JSONObject();
                item.put("id", c.getString(c.getColumnIndexOrThrow("id")));
                item.put("uri", c.getString(c.getColumnIndexOrThrow("uri")));
                item.put("name", c.getString(c.getColumnIndexOrThrow("name")));
                item.put("size", c.getLong(c.getColumnIndexOrThrow("size")));
                item.put("modified", c.getLong(c.getColumnIndexOrThrow("modified")));
                String thumb = c.getString(c.getColumnIndexOrThrow("thumbnail"));
                if (thumb != null && !thumb.isEmpty()) item.put("thumbnail", thumb);
                result.put(item);
            }
        } catch (Exception ignored) { }
        return result;
    }

    public synchronized JSONObject getVideo(String id) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query("videos", null, "id=?", new String[]{id}, null, null, null)) {
            if (!c.moveToFirst()) return null;
            JSONObject item = new JSONObject();
            item.put("id", c.getString(c.getColumnIndexOrThrow("id")));
            item.put("uri", c.getString(c.getColumnIndexOrThrow("uri")));
            item.put("name", c.getString(c.getColumnIndexOrThrow("name")));
            item.put("size", c.getLong(c.getColumnIndexOrThrow("size")));
            item.put("modified", c.getLong(c.getColumnIndexOrThrow("modified")));
            String thumb = c.getString(c.getColumnIndexOrThrow("thumbnail"));
            if (thumb != null && !thumb.isEmpty()) item.put("thumbnail", thumb);
            return item;
        } catch (Exception e) { return null; }
    }

    public synchronized void putVideo(String folderUri, JSONObject item) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("id", item.optString("id"));
        values.put("folder_uri", folderUri);
        values.put("uri", item.optString("uri"));
        values.put("name", item.optString("name"));
        values.put("size", item.optLong("size"));
        values.put("modified", item.optLong("modified"));
        values.put("thumbnail", item.optString("thumbnail", ""));
        values.put("added_at", System.currentTimeMillis());
        db.insertWithOnConflict("videos", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized void deleteMissing(String folderUri, java.util.Set<String> found) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            try (Cursor c = db.query("videos", new String[]{"id"}, "folder_uri=?", new String[]{folderUri}, null, null, null)) {
                while (c.moveToNext()) {
                    String id = c.getString(0);
                    if (!found.contains(id)) db.delete("videos", "id=?", new String[]{id});
                }
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
}
