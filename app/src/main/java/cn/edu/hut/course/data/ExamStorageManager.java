package cn.edu.hut.course.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import cn.edu.hut.course.Exam;

public final class ExamStorageManager {

    private static final String PREFS_NAME = "exam_prefs";

    private ExamStorageManager() {
    }

    private static String keyForTable(long tableId) {
        return "exam_list_" + tableId;
    }

    public static synchronized void saveExams(Context context, List<Exam> exams) {
        long tableId = CourseStorageManager.getActiveTableId(context);
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        JSONArray arr = new JSONArray();
        for (Exam e : exams) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("courseName", e.courseName != null ? e.courseName : "");
                obj.put("courseCode", e.courseCode != null ? e.courseCode : "");
                obj.put("teacher", e.teacher != null ? e.teacher : "");
                obj.put("examDate", e.examDate != null ? e.examDate : "");
                obj.put("startTime", e.startTime != null ? e.startTime : "");
                obj.put("endTime", e.endTime != null ? e.endTime : "");
                obj.put("location", e.location != null ? e.location : "");
                obj.put("campus", e.campus != null ? e.campus : "");
                obj.put("sessionInfo", e.sessionInfo != null ? e.sessionInfo : "");
                obj.put("seatNumber", e.seatNumber != null ? e.seatNumber : "");
                obj.put("ticketNumber", e.ticketNumber != null ? e.ticketNumber : "");
                arr.put(obj);
            } catch (Exception ignored) {
            }
        }
        prefs.edit().putString(keyForTable(tableId), arr.toString()).apply();
    }

    public static synchronized List<Exam> loadExams(Context context) {
        long tableId = CourseStorageManager.getActiveTableId(context);
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(keyForTable(tableId), "[]");
        List<Exam> result = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                Exam e = new Exam();
                e.courseName = obj.optString("courseName", "");
                e.courseCode = obj.optString("courseCode", "");
                e.teacher = obj.optString("teacher", "");
                e.examDate = obj.optString("examDate", "");
                e.startTime = obj.optString("startTime", "");
                e.endTime = obj.optString("endTime", "");
                e.location = obj.optString("location", "");
                e.campus = obj.optString("campus", "");
                e.sessionInfo = obj.optString("sessionInfo", "");
                e.seatNumber = obj.optString("seatNumber", "");
                e.ticketNumber = obj.optString("ticketNumber", "");
                result.add(e);
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    public static synchronized void deleteExamsForTable(Context context, long tableId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(keyForTable(tableId)).apply();
    }

    public static synchronized boolean hasExams(Context context) {
        return !loadExams(context).isEmpty();
    }
}
