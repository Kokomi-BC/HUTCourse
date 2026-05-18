package cn.edu.hut.course.data;

import android.content.Context;
import android.content.SharedPreferences;

import cn.edu.hut.course.Course;
import cn.edu.hut.course.CourseTable;

import java.util.ArrayList;
import java.util.List;

public final class CourseStorageManager {

    public static final String PREF_COURSE_STORAGE = "course_storage";
    public static final String KEY_COURSES_JSON_PREFIX = "courses_json_";
    public static final String KEY_ACTIVE_TABLE_ID = "active_table_id";

    // Profile keys per table
    private static final String KEY_PROFILE_NAME_PREFIX = "profile_name_";
    private static final String KEY_PROFILE_STUDENT_ID_PREFIX = "profile_student_id_";
    private static final String KEY_PROFILE_CLASS_PREFIX = "profile_class_";
    private static final String KEY_PROFILE_COLLEGE_PREFIX = "profile_college_";

    // Cookie key per table
    private static final String KEY_COOKIE_PREFIX = "cookie_";

    private CourseStorageManager() {
    }

    public static synchronized long getActiveTableId(Context context) {
        return prefs(context).getLong(KEY_ACTIVE_TABLE_ID, 1L);
    }

    public static synchronized void setActiveTableId(Context context, long tableId) {
        prefs(context).edit().putLong(KEY_ACTIVE_TABLE_ID, tableId).apply();
    }

    private static String getActiveCoursesJsonKey(Context context) {
        return KEY_COURSES_JSON_PREFIX + getActiveTableId(context);
    }
    
    private static String getCoursesJsonKey(long tableId) {
        return KEY_COURSES_JSON_PREFIX + tableId;
    }

    public static synchronized void saveCourses(Context context, List<Course> courses) {
        try {
            String json = CourseJsonCodec.toJson(courses);
            saveCoursesJson(context, json);
        } catch (Exception ignored) {
        }
    }

    public static synchronized void saveCoursesJson(Context context, String json) throws Exception {
        if (readAllCourseTables(context).isEmpty()) {
            CourseTable newTable = new CourseTable();
            newTable.name = "默认课表";
            long newId = insertCourseTable(context, newTable);
            if (newId != -1) {
                setActiveTableId(context, newId);
            }
        }
        long tableId = getActiveTableId(context);
        List<Course> courses = CourseJsonCodec.fromJson(json);
        String normalized = CourseJsonCodec.toJson(courses);
        prefs(context).edit().putString(getCoursesJsonKey(tableId), normalized).apply();
        CourseSQLiteStore.overwriteCourses(context, tableId, courses);
    }

    public static synchronized List<Course> loadCourses(Context context) {
        long tableId = getActiveTableId(context);
        String json = prefs(context).getString(getCoursesJsonKey(tableId), "");
        if (json != null && !json.trim().isEmpty()) {
            try {
                return CourseJsonCodec.fromJson(json);
            } catch (Exception ignored) {
            }
        }

        List<Course> fromDb = CourseSQLiteStore.readAllCourses(context, tableId);
        if (!fromDb.isEmpty()) {
            try {
                prefs(context).edit().putString(getCoursesJsonKey(tableId), CourseJsonCodec.toJson(fromDb)).apply();
            } catch (Exception ignored) {
            }
            return fromDb;
        }
        return new ArrayList<>();
    }

    public static synchronized String loadCoursesJson(Context context) {
        long tableId = getActiveTableId(context);
        return loadCoursesJsonForTable(context, tableId);
    }

    public static synchronized String loadCoursesJsonForTable(Context context, long tableId) {
        String json = prefs(context).getString(getCoursesJsonKey(tableId), "");
        if (json != null && !json.trim().isEmpty()) {
            return json;
        }
        List<Course> fromDb = CourseSQLiteStore.readAllCourses(context, tableId);
        try {
            String fallback = CourseJsonCodec.toJson(fromDb);
            prefs(context).edit().putString(getCoursesJsonKey(tableId), fallback).apply();
            return fallback;
        } catch (Exception ignored) {
            return "[]";
        }
    }

    public static synchronized int countNonRemarkCourses(Context context) {
        int count = 0;
        for (Course c : loadCourses(context)) {
            if (c != null && !c.isRemark) {
                count++;
            }
        }
        return count;
    }

    public static synchronized int countNonRemarkCoursesForTable(Context context, long tableId) {
        int count = 0;
        List<Course> courses = loadCoursesForTable(context, tableId);
        for (Course c : courses) {
            if (c != null && !c.isRemark) {
                count++;
            }
        }
        return count;
    }

    public static synchronized List<Course> loadCoursesForTable(Context context, long tableId) {
        String json = prefs(context).getString(getCoursesJsonKey(tableId), "");
        if (json != null && !json.trim().isEmpty()) {
            try {
                return CourseJsonCodec.fromJson(json);
            } catch (Exception ignored) {
            }
        }
        List<Course> fromDb = CourseSQLiteStore.readAllCourses(context, tableId);
        if (!fromDb.isEmpty()) {
            try {
                prefs(context).edit().putString(getCoursesJsonKey(tableId), CourseJsonCodec.toJson(fromDb)).apply();
            } catch (Exception ignored) {
            }
            return fromDb;
        }
        return new ArrayList<>();
    }

    public static synchronized void overwriteCoursesForTable(Context context, long tableId, List<Course> courses) {
        try {
            String json = CourseJsonCodec.toJson(courses);
            prefs(context).edit().putString(getCoursesJsonKey(tableId), json).apply();
            CourseSQLiteStore.overwriteCourses(context, tableId, courses);
        } catch (Exception ignored) {
        }
    }

    public static synchronized void clearCourses(Context context) {
        long tableId = getActiveTableId(context);
        prefs(context).edit().remove(getCoursesJsonKey(tableId)).apply();
        CourseSQLiteStore.overwriteCourses(context, tableId, new ArrayList<>());
    }
    
    public static synchronized long insertCourseTable(Context context, CourseTable table) {
        return CourseSQLiteStore.insertCourseTable(context, table);
    }
    
    public static synchronized void updateCourseTable(Context context, CourseTable table) {
        CourseSQLiteStore.updateCourseTable(context, table);
    }
    
    public static synchronized void deleteCourseTable(Context context, long tableId) {
        prefs(context).edit().remove(getCoursesJsonKey(tableId)).apply();
        CourseSQLiteStore.deleteCourseTable(context, tableId);
        AgendaSQLiteStore.deleteAgendasByTable(context, tableId);
        if (getActiveTableId(context) == tableId) {
            setActiveTableId(context, 1L);
        }
    }
    
    public static synchronized List<CourseTable> readAllCourseTables(Context context) {
        List<CourseTable> tables = CourseSQLiteStore.readAllCourseTables(context);
        // Populate profile fields from SharedPreferences
        SharedPreferences p = prefs(context);
        for (CourseTable t : tables) {
            t.profileName = p.getString(KEY_PROFILE_NAME_PREFIX + t.id, "");
            t.profileStudentId = p.getString(KEY_PROFILE_STUDENT_ID_PREFIX + t.id, "");
            t.profileClassName = p.getString(KEY_PROFILE_CLASS_PREFIX + t.id, "");
            t.profileCollege = p.getString(KEY_PROFILE_COLLEGE_PREFIX + t.id, "");
        }
        return tables;
    }

    // ==================== Profile per table ====================

    public static synchronized void saveProfileForTable(Context context, long tableId,
                                                         String name, String studentId,
                                                         String className, String college) {
        prefs(context).edit()
                .putString(KEY_PROFILE_NAME_PREFIX + tableId, name != null ? name : "")
                .putString(KEY_PROFILE_STUDENT_ID_PREFIX + tableId, studentId != null ? studentId : "")
                .putString(KEY_PROFILE_CLASS_PREFIX + tableId, className != null ? className : "")
                .putString(KEY_PROFILE_COLLEGE_PREFIX + tableId, college != null ? college : "")
                .apply();
    }

    public static String getProfileName(Context context, long tableId) {
        return prefs(context).getString(KEY_PROFILE_NAME_PREFIX + tableId, "");
    }

    public static String getProfileStudentId(Context context, long tableId) {
        return prefs(context).getString(KEY_PROFILE_STUDENT_ID_PREFIX + tableId, "");
    }

    public static String getProfileClassName(Context context, long tableId) {
        return prefs(context).getString(KEY_PROFILE_CLASS_PREFIX + tableId, "");
    }

    public static String getProfileCollege(Context context, long tableId) {
        return prefs(context).getString(KEY_PROFILE_COLLEGE_PREFIX + tableId, "");
    }

    // ==================== Cookie per table ====================

    public static synchronized void saveCookieForTable(Context context, long tableId, String cookie) {
        prefs(context).edit().putString(KEY_COOKIE_PREFIX + tableId, cookie != null ? cookie : "").apply();
    }

    public static synchronized String getCookieForTable(Context context, long tableId) {
        return prefs(context).getString(KEY_COOKIE_PREFIX + tableId, "");
    }

    public static synchronized void clearCookieForTable(Context context, long tableId) {
        prefs(context).edit().remove(KEY_COOKIE_PREFIX + tableId).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF_COURSE_STORAGE, Context.MODE_PRIVATE);
    }
}
