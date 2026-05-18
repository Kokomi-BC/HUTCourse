package cn.edu.hut.course.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import cn.edu.hut.course.Course;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

public final class CourseSQLiteStore {

    private static final String DB_NAME = "course_store.db";
    private static final int DB_VERSION = 2; // Updated to v2 for multi-table support
    private static final String TABLE_COURSES = "courses";
    private static final String TABLE_COURSE_TABLES = "course_tables"; // New table

    // Course fields
    private static final String COL_TABLE_ID = "table_id"; // Foreign key
    private static final String COL_NAME = "name";
    private static final String COL_TEACHER = "teacher";
    private static final String COL_LOCATION = "location";
    private static final String COL_DAY_OF_WEEK = "day_of_week";
    private static final String COL_START_SECTION = "start_section";
    private static final String COL_SECTION_SPAN = "section_span";
    private static final String COL_TIME_STR = "time_str";
    private static final String COL_TYPE_CLASS = "type_class";
    private static final String COL_IS_EXPERIMENTAL = "is_experimental";
    private static final String COL_IS_REMARK = "is_remark";
    private static final String COL_WEEKS_JSON = "weeks_json";

    // CourseTable fields
    private static final String COL_TABLE_NAME = "name";
    private static final String COL_TABLE_CREATE_TIME = "create_time";

    private CourseSQLiteStore() {
    }

    public static synchronized long insertCourseTable(Context context, cn.edu.hut.course.CourseTable table) {
        DbHelper helper = new DbHelper(context.getApplicationContext());
        SQLiteDatabase db = helper.getWritableDatabase();
        try {
            ContentValues values = new ContentValues();
            values.put(COL_TABLE_NAME, safe(table.name));
            values.put(COL_TABLE_CREATE_TIME, table.createTime);
            return db.insert(TABLE_COURSE_TABLES, null, values);
        } finally {
            db.close();
        }
    }

    public static synchronized void updateCourseTable(Context context, cn.edu.hut.course.CourseTable table) {
        DbHelper helper = new DbHelper(context.getApplicationContext());
        SQLiteDatabase db = helper.getWritableDatabase();
        try {
            ContentValues values = new ContentValues();
            values.put(COL_TABLE_NAME, safe(table.name));
            values.put(COL_TABLE_CREATE_TIME, table.createTime);
            db.update(TABLE_COURSE_TABLES, values, "id=?", new String[]{String.valueOf(table.id)});
        } finally {
            db.close();
        }
    }

    public static synchronized void deleteCourseTable(Context context, long tableId) {
        DbHelper helper = new DbHelper(context.getApplicationContext());
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE_COURSES, "table_id=?", new String[]{String.valueOf(tableId)});
            db.delete(TABLE_COURSE_TABLES, "id=?", new String[]{String.valueOf(tableId)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    public static synchronized List<cn.edu.hut.course.CourseTable> readAllCourseTables(Context context) {
        DbHelper helper = new DbHelper(context.getApplicationContext());
        SQLiteDatabase db = helper.getReadableDatabase();
        List<cn.edu.hut.course.CourseTable> list = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_COURSE_TABLES, null, null, null, null, null, "id ASC");
            while (cursor.moveToNext()) {
                cn.edu.hut.course.CourseTable t = new cn.edu.hut.course.CourseTable();
                t.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
                t.name = cursor.getString(cursor.getColumnIndexOrThrow(COL_TABLE_NAME));
                t.createTime = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TABLE_CREATE_TIME));
                list.add(t);
            }
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
        return list;
    }

    // Now uses tableId to overwrite courses for a specific table
    public static synchronized void overwriteCourses(Context context, long tableId, List<Course> courses) {
        DbHelper helper = new DbHelper(context.getApplicationContext());
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE_COURSES, "table_id=?", new String[]{String.valueOf(tableId)});
            if (courses != null) {
                for (Course c : courses) {
                    if (c == null) {
                        continue;
                    }
                    ContentValues values = new ContentValues();
                    values.put(COL_TABLE_ID, tableId);
                    values.put(COL_NAME, safe(c.name));
                    values.put(COL_TEACHER, safe(c.teacher));
                    values.put(COL_LOCATION, safe(c.location));
                    values.put(COL_DAY_OF_WEEK, c.dayOfWeek);
                    values.put(COL_START_SECTION, c.startSection);
                    values.put(COL_SECTION_SPAN, c.sectionSpan);
                    values.put(COL_TIME_STR, safe(c.timeStr));
                    values.put(COL_TYPE_CLASS, safe(c.typeClass));
                    values.put(COL_IS_EXPERIMENTAL, c.isExperimental ? 1 : 0);
                    values.put(COL_IS_REMARK, c.isRemark ? 1 : 0);
                    values.put(COL_WEEKS_JSON, toWeeksJson(c.weeks));
                    db.insert(TABLE_COURSES, null, values);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    public static synchronized List<Course> readAllCourses(Context context, long tableId) {
        DbHelper helper = new DbHelper(context.getApplicationContext());
        SQLiteDatabase db = helper.getReadableDatabase();
        List<Course> result = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_COURSES, null, "table_id=?", new String[]{String.valueOf(tableId)}, null, null, "id ASC");
            while (cursor.moveToNext()) {
                Course c = new Course();
                c.name = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME));
                c.teacher = cursor.getString(cursor.getColumnIndexOrThrow(COL_TEACHER));
                c.location = cursor.getString(cursor.getColumnIndexOrThrow(COL_LOCATION));
                c.dayOfWeek = cursor.getInt(cursor.getColumnIndexOrThrow(COL_DAY_OF_WEEK));
                c.startSection = cursor.getInt(cursor.getColumnIndexOrThrow(COL_START_SECTION));
                c.sectionSpan = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SECTION_SPAN));
                c.timeStr = cursor.getString(cursor.getColumnIndexOrThrow(COL_TIME_STR));
                c.typeClass = cursor.getString(cursor.getColumnIndexOrThrow(COL_TYPE_CLASS));
                c.isExperimental = cursor.getInt(cursor.getColumnIndexOrThrow(COL_IS_EXPERIMENTAL)) == 1;
                c.isRemark = cursor.getInt(cursor.getColumnIndexOrThrow(COL_IS_REMARK)) == 1;
                c.weeks = parseWeeksJson(cursor.getString(cursor.getColumnIndexOrThrow(COL_WEEKS_JSON)));
                result.add(c);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            db.close();
        }
        return result;
    }

    private static String toWeeksJson(List<Integer> weeks) {
        JSONArray arr = new JSONArray();
        if (weeks != null) {
            for (Integer week : weeks) {
                if (week != null) {
                    arr.put(week);
                }
            }
        }
        return arr.toString();
    }

    private static List<Integer> parseWeeksJson(String json) {
        List<Integer> weeks = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json == null ? "[]" : json);
            for (int i = 0; i < arr.length(); i++) {
                weeks.add(arr.optInt(i));
            }
        } catch (Exception ignored) {
        }
        return weeks;
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
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_COURSE_TABLES + " ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + COL_TABLE_NAME + " TEXT NOT NULL,"
                    + COL_TABLE_CREATE_TIME + " INTEGER"
                    + ")");

            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_COURSES + " ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + COL_TABLE_ID + " INTEGER NOT NULL DEFAULT 1,"
                    + COL_NAME + " TEXT NOT NULL,"
                    + COL_TEACHER + " TEXT,"
                    + COL_LOCATION + " TEXT,"
                    + COL_DAY_OF_WEEK + " INTEGER,"
                    + COL_START_SECTION + " INTEGER,"
                    + COL_SECTION_SPAN + " INTEGER,"
                    + COL_TIME_STR + " TEXT,"
                    + COL_TYPE_CLASS + " TEXT,"
                    + COL_IS_EXPERIMENTAL + " INTEGER,"
                    + COL_IS_REMARK + " INTEGER,"
                    + COL_WEEKS_JSON + " TEXT"
                    + ")");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 2) {
                // V1 -> V2 Migration
                db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_COURSE_TABLES + " ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + COL_TABLE_NAME + " TEXT NOT NULL,"
                        + COL_TABLE_CREATE_TIME + " INTEGER"
                        + ")");

                db.execSQL("ALTER TABLE " + TABLE_COURSES + " ADD COLUMN " + COL_TABLE_ID + " INTEGER DEFAULT 1");

                Cursor c = null;
                boolean hasOldCourses = false;
                try {
                    c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_COURSES, null);
                    if (c != null && c.moveToFirst() && c.getInt(0) > 0) {
                        hasOldCourses = true;
                    }
                } finally {
                    if (c != null) c.close();
                }

                if (hasOldCourses) {
                    String timeStr = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
                    db.execSQL("INSERT INTO " + TABLE_COURSE_TABLES + " (id, " + COL_TABLE_NAME + ", " + COL_TABLE_CREATE_TIME + ") VALUES (1, '" + timeStr + "', " + System.currentTimeMillis() + ")");
                }
            } else {
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_COURSES);
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_COURSE_TABLES);
                onCreate(db);
            }
        }
    }
}
