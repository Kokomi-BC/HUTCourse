package cn.edu.hut.course.data;

import android.content.Context;
import android.content.SharedPreferences;

import cn.edu.hut.course.Course;

import java.util.ArrayList;
import java.util.List;

public final class CourseStorageManager {

    public static final String PREF_COURSE_STORAGE = "course_storage";
    public static final String KEY_COURSES_JSON = "courses_json";

    private CourseStorageManager() {
    }

    public static synchronized void saveCourses(Context context, List<Course> courses) {
        try {
            String json = CourseJsonCodec.toJson(courses);
            saveCoursesJson(context, json);
        } catch (Exception ignored) {
        }
    }

    public static synchronized void saveCoursesJson(Context context, String json) throws Exception {
        List<Course> courses = CourseJsonCodec.fromJson(json);
        String normalized = CourseJsonCodec.toJson(courses);
        prefs(context).edit().putString(KEY_COURSES_JSON, normalized).apply();
        CourseSQLiteStore.overwriteCourses(context, courses);
    }

    public static synchronized List<Course> loadCourses(Context context) {
        String json = prefs(context).getString(KEY_COURSES_JSON, "");
        if (json != null && !json.trim().isEmpty()) {
            try {
                return CourseJsonCodec.fromJson(json);
            } catch (Exception ignored) {
            }
        }

        List<Course> fromDb = CourseSQLiteStore.readAllCourses(context);
        if (!fromDb.isEmpty()) {
            try {
                prefs(context).edit().putString(KEY_COURSES_JSON, CourseJsonCodec.toJson(fromDb)).apply();
            } catch (Exception ignored) {
            }
            return fromDb;
        }
        return new ArrayList<>();
    }

    public static synchronized String loadCoursesJson(Context context) {
        String json = prefs(context).getString(KEY_COURSES_JSON, "");
        if (json != null && !json.trim().isEmpty()) {
            return json;
        }
        List<Course> fromDb = CourseSQLiteStore.readAllCourses(context);
        try {
            String fallback = CourseJsonCodec.toJson(fromDb);
            prefs(context).edit().putString(KEY_COURSES_JSON, fallback).apply();
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

    public static synchronized void clearCourses(Context context) {
        prefs(context).edit().remove(KEY_COURSES_JSON).apply();
        CourseSQLiteStore.overwriteCourses(context, new ArrayList<>());
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF_COURSE_STORAGE, Context.MODE_PRIVATE);
    }
}
