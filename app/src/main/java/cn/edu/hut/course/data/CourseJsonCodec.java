package cn.edu.hut.course.data;

import cn.edu.hut.course.Course;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class CourseJsonCodec {

    private CourseJsonCodec() {
    }

    public static String toJson(List<Course> courses) throws Exception {
        JSONArray arr = new JSONArray();
        if (courses != null) {
            for (Course c : courses) {
                if (c == null) {
                    continue;
                }
                JSONObject o = new JSONObject();
                o.put("name", safe(c.name));
                o.put("teacher", safe(c.teacher));
                o.put("location", safe(c.location));
                o.put("dayOfWeek", c.dayOfWeek);
                o.put("startSection", c.startSection);
                o.put("sectionSpan", c.sectionSpan);
                o.put("timeStr", safe(c.timeStr));
                o.put("typeClass", safe(c.typeClass));
                o.put("isExperimental", c.isExperimental);
                o.put("isRemark", c.isRemark);

                JSONArray weeks = new JSONArray();
                if (c.weeks != null) {
                    for (Integer week : c.weeks) {
                        if (week != null) {
                            weeks.put(week);
                        }
                    }
                }
                o.put("weeks", weeks);
                arr.put(o);
            }
        }
        return arr.toString();
    }

    public static List<Course> fromJson(String json) throws Exception {
        String safeJson = (json == null || json.trim().isEmpty()) ? "[]" : json;
        JSONArray arr = new JSONArray(safeJson);
        List<Course> courses = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            Course c = new Course();
            c.name = o.optString("name", "");
            c.teacher = o.optString("teacher", "");
            c.location = o.optString("location", "");
            c.dayOfWeek = o.optInt("dayOfWeek", 1);
            c.startSection = o.optInt("startSection", 1);
            c.sectionSpan = o.optInt("sectionSpan", 2);
            c.timeStr = o.optString("timeStr", "");
            c.typeClass = o.optString("typeClass", "");
            c.isExperimental = o.optBoolean("isExperimental", false);
            c.isRemark = o.optBoolean("isRemark", false);

            JSONArray w = o.optJSONArray("weeks");
            c.weeks = new ArrayList<>();
            if (w != null) {
                for (int k = 0; k < w.length(); k++) {
                    c.weeks.add(w.optInt(k));
                }
            }
            courses.add(c);
        }
        return courses;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
