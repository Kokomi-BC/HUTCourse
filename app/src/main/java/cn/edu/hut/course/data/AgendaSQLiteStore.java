package cn.edu.hut.course.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import cn.edu.hut.course.Agenda;

import java.util.ArrayList;
import java.util.List;

public final class AgendaSQLiteStore {

    private static final String DB_NAME = "agenda_store.db";
    private static final int DB_VERSION = 3;

    private static final String TABLE_AGENDAS = "agendas";

    private static final String COL_ID = "id";
    private static final String COL_TITLE = "title";
    private static final String COL_DESCRIPTION = "description";
    private static final String COL_LOCATION = "location";
    private static final String COL_DATE = "date_value";
    private static final String COL_START_MINUTE = "start_minute";
    private static final String COL_END_MINUTE = "end_minute";
    private static final String COL_PRIORITY = "priority";
    private static final String COL_RENDER_COLOR = "render_color";
    private static final String COL_REPEAT_RULE = "repeat_rule";
    private static final String COL_MONTHLY_STRATEGY = "monthly_strategy";
    private static final String COL_CREATED_AT = "created_at";
    private static final String COL_UPDATED_AT = "updated_at";

    private AgendaSQLiteStore() {
    }

    public static synchronized long insertAgenda(Context context, Agenda agenda) {
        if (agenda == null) {
            return -1L;
        }
        DbHelper helper = new DbHelper(context.getApplicationContext());
        SQLiteDatabase db = helper.getWritableDatabase();
        try {
            ContentValues values = toValues(agenda, false);
            return db.insert(TABLE_AGENDAS, null, values);
        } finally {
            db.close();
        }
    }

    public static synchronized boolean updateAgenda(Context context, Agenda agenda) {
        if (agenda == null || agenda.id <= 0) {
            return false;
        }
        DbHelper helper = new DbHelper(context.getApplicationContext());
        SQLiteDatabase db = helper.getWritableDatabase();
        try {
            ContentValues values = toValues(agenda, true);
            int rows = db.update(TABLE_AGENDAS, values, COL_ID + "=?", new String[]{String.valueOf(agenda.id)});
            return rows > 0;
        } finally {
            db.close();
        }
    }

    public static synchronized boolean deleteAgendaById(Context context, long agendaId) {
        if (agendaId <= 0) {
            return false;
        }
        DbHelper helper = new DbHelper(context.getApplicationContext());
        SQLiteDatabase db = helper.getWritableDatabase();
        try {
            int rows = db.delete(TABLE_AGENDAS, COL_ID + "=?", new String[]{String.valueOf(agendaId)});
            return rows > 0;
        } finally {
            db.close();
        }
    }

    public static synchronized Agenda readAgendaById(Context context, long agendaId) {
        if (agendaId <= 0) {
            return null;
        }
        DbHelper helper = new DbHelper(context.getApplicationContext());
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_AGENDAS, null, COL_ID + "=?", new String[]{String.valueOf(agendaId)}, null, null, null);
            if (cursor.moveToFirst()) {
                return readAgenda(cursor);
            }
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            db.close();
        }
    }

    public static synchronized List<Agenda> readAllAgendas(Context context) {
        DbHelper helper = new DbHelper(context.getApplicationContext());
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor cursor = null;
        List<Agenda> result = new ArrayList<>();
        try {
            cursor = db.query(TABLE_AGENDAS, null, null, null, null, null,
                    COL_DATE + " ASC, " + COL_START_MINUTE + " ASC, " + COL_ID + " ASC");
            while (cursor.moveToNext()) {
                result.add(readAgenda(cursor));
            }
            return result;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            db.close();
        }
    }

    private static ContentValues toValues(Agenda agenda, boolean forUpdate) {
        ContentValues values = new ContentValues();
        values.put(COL_TITLE, safe(agenda.title));
        values.put(COL_DESCRIPTION, safe(agenda.description));
        values.put(COL_LOCATION, safe(agenda.location));
        values.put(COL_DATE, safe(agenda.date));
        values.put(COL_START_MINUTE, agenda.startMinute);
        values.put(COL_END_MINUTE, agenda.endMinute);
        values.put(COL_PRIORITY, agenda.priority);
        values.put(COL_RENDER_COLOR, agenda.renderColor);
        values.put(COL_REPEAT_RULE, safe(agenda.repeatRule));
        values.put(COL_MONTHLY_STRATEGY, safe(agenda.monthlyStrategy));
        values.put(COL_UPDATED_AT, agenda.updatedAt);
        if (!forUpdate) {
            values.put(COL_CREATED_AT, agenda.createdAt);
        }
        return values;
    }

    private static Agenda readAgenda(Cursor cursor) {
        Agenda agenda = new Agenda();
        agenda.id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID));
        agenda.title = safe(cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE)));
        agenda.description = safe(cursor.getString(cursor.getColumnIndexOrThrow(COL_DESCRIPTION)));
        agenda.location = safe(cursor.getString(cursor.getColumnIndexOrThrow(COL_LOCATION)));
        agenda.date = safe(cursor.getString(cursor.getColumnIndexOrThrow(COL_DATE)));
        agenda.startMinute = cursor.getInt(cursor.getColumnIndexOrThrow(COL_START_MINUTE));
        agenda.endMinute = cursor.getInt(cursor.getColumnIndexOrThrow(COL_END_MINUTE));
        agenda.priority = cursor.getInt(cursor.getColumnIndexOrThrow(COL_PRIORITY));
        int renderColorIndex = cursor.getColumnIndex(COL_RENDER_COLOR);
        agenda.renderColor = renderColorIndex >= 0 ? cursor.getInt(renderColorIndex) : 0;
        agenda.repeatRule = safe(cursor.getString(cursor.getColumnIndexOrThrow(COL_REPEAT_RULE)));
        agenda.monthlyStrategy = safe(cursor.getString(cursor.getColumnIndexOrThrow(COL_MONTHLY_STRATEGY)));
        agenda.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT));
        agenda.updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_UPDATED_AT));
        return agenda;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class DbHelper extends SQLiteOpenHelper {

        DbHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_AGENDAS + " ("
                    + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + COL_TITLE + " TEXT NOT NULL,"
                    + COL_DESCRIPTION + " TEXT,"
                    + COL_LOCATION + " TEXT,"
                    + COL_DATE + " TEXT NOT NULL,"
                    + COL_START_MINUTE + " INTEGER NOT NULL,"
                    + COL_END_MINUTE + " INTEGER NOT NULL,"
                    + COL_PRIORITY + " INTEGER NOT NULL,"
                    + COL_RENDER_COLOR + " INTEGER DEFAULT 0,"
                    + COL_REPEAT_RULE + " TEXT NOT NULL,"
                    + COL_MONTHLY_STRATEGY + " TEXT NOT NULL,"
                    + COL_CREATED_AT + " INTEGER,"
                    + COL_UPDATED_AT + " INTEGER"
                    + ")");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 2) {
                try {
                    db.execSQL("ALTER TABLE " + TABLE_AGENDAS + " ADD COLUMN " + COL_LOCATION + " TEXT DEFAULT ''");
                } catch (Exception ignored) {
                }
            }
            if (oldVersion < 3) {
                try {
                    db.execSQL("ALTER TABLE " + TABLE_AGENDAS + " ADD COLUMN " + COL_RENDER_COLOR + " INTEGER DEFAULT 0");
                } catch (Exception ignored) {
                }
            }
        }
    }
}
