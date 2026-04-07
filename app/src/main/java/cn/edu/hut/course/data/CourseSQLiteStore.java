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
    private static final int DB_VERSION = 1;
    private static final String TABLE_COURSES = "courses";

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

    private CourseSQLiteStore() {
    }

    public static synchronized void overwriteCourses(Context context, List<Course> courses) {
        DbHelper helper = new DbHelper(context.getApplicationContext());
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE_COURSES, null, null);
            if (courses != null) {
                for (Course c : courses) {
                    if (c == null) {
                        continue;
                    }
                    ContentValues values = new ContentValues();
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

    public static synchronized List<Course> readAllCourses(Context context) {
        DbHelper helper = new DbHelper(context.getApplicationContext());
        SQLiteDatabase db = helper.getReadableDatabase();
        List<Course> result = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_COURSES, null, null, null, null, null, "id ASC");
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
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_COURSES + " ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
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
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_COURSES);
            onCreate(db);
        }
    }
}
