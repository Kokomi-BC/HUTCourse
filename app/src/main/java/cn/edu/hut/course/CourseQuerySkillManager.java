package cn.edu.hut.course;

import android.content.Context;
import android.content.SharedPreferences;

import cn.edu.hut.course.data.CourseStorageManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class CourseQuerySkillManager {

    private static final String PREF_COURSE_STORAGE = "course_storage";
    private static final String KEY_SEMESTER_START_DATE = "semester_start_date";

    private CourseQuerySkillManager() {
    }

    public static String queryTodayRemaining(Context context) {
        List<Course> allCourses = CourseStorageManager.loadCourses(context);
        if (allCourses.isEmpty()) {
            return "查询结果：暂无课程数据";
        }

        Calendar now = Calendar.getInstance();
        int week = getWeekForDate(context, now);
        int dayOfWeek = toMondayFirstDay(now);
        int currentSeconds = now.get(Calendar.HOUR_OF_DAY) * 3600 + now.get(Calendar.MINUTE) * 60 + now.get(Calendar.SECOND);

        List<Course> result = new ArrayList<>();
        for (Course c : allCourses) {
            if (!isNormalCourse(c)) continue;
            if (c.dayOfWeek != dayOfWeek) continue;
            if (c.weeks == null || !c.weeks.contains(week)) continue;
            int startSeconds = getStartSeconds(c.startSection);
            if (startSeconds > currentSeconds) {
                result.add(c);
            }
        }

        result.sort(Comparator.comparingInt(a -> a.startSection));
        return formatCourseList("今日剩余课程", week, dayOfWeek, result);
    }

    public static String queryCoursesByDate(Context context, String dateStr) {
        String input = dateStr == null ? "" : dateStr.trim();
        if (input.isEmpty()) {
            return "查询失败：日期为空，格式应为 yyyy-MM-dd";
        }

        Calendar target = parseDate(input);
        if (target == null) {
            return "查询失败：日期格式错误，格式应为 yyyy-MM-dd";
        }

        List<Course> allCourses = CourseStorageManager.loadCourses(context);
        if (allCourses.isEmpty()) {
            return "查询结果：暂无课程数据";
        }

        int week = getWeekForDate(context, target);
        int dayOfWeek = toMondayFirstDay(target);

        List<Course> result = new ArrayList<>();
        for (Course c : allCourses) {
            if (!isNormalCourse(c)) continue;
            if (c.dayOfWeek != dayOfWeek) continue;
            if (c.weeks == null || !c.weeks.contains(week)) continue;
            result.add(c);
        }
        result.sort(Comparator.comparingInt(a -> a.startSection));

        String title = "日期课程(" + input + ")";
        return formatCourseList(title, week, dayOfWeek, result);
    }

    public static String searchByTeacher(Context context, String teacherKeyword) {
        String keyword = teacherKeyword == null ? "" : teacherKeyword.trim();
        if (keyword.isEmpty()) {
            return "查询失败：教师名称为空";
        }

        List<Course> allCourses = CourseStorageManager.loadCourses(context);
        if (allCourses.isEmpty()) {
            return "查询结果：暂无课程数据";
        }

        List<Course> result = new ArrayList<>();
        for (Course c : allCourses) {
            if (!isNormalCourse(c)) continue;
            String teacher = c.teacher == null ? "" : c.teacher;
            if (teacher.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT))) {
                result.add(c);
            }
        }

        sortByCourseTime(result);
        return formatSearchList("教师查询", "教师关键词=" + keyword, result);
    }

    public static String searchByCourseName(Context context, String courseNameKeyword) {
        String keyword = courseNameKeyword == null ? "" : courseNameKeyword.trim();
        if (keyword.isEmpty()) {
            return "查询失败：课程名称为空";
        }

        List<Course> allCourses = CourseStorageManager.loadCourses(context);
        if (allCourses.isEmpty()) {
            return "查询结果：暂无课程数据";
        }

        List<Course> result = new ArrayList<>();
        for (Course c : allCourses) {
            if (!isNormalCourse(c)) continue;
            String name = c.name == null ? "" : c.name;
            if (name.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT))) {
                result.add(c);
            }
        }

        sortByCourseTime(result);
        return formatSearchList("课程名查询", "课程关键词=" + keyword, result);
    }

    private static void sortByCourseTime(List<Course> result) {
        result.sort((a, b) -> {
            int byDay = Integer.compare(a.dayOfWeek, b.dayOfWeek);
            if (byDay != 0) return byDay;
            int bySection = Integer.compare(a.startSection, b.startSection);
            if (bySection != 0) return bySection;
            return safe(a.name).compareToIgnoreCase(safe(b.name));
        });
    }

    private static String formatCourseList(String title, int week, int dayOfWeek, List<Course> courses) {
        String dayText = toDayText(dayOfWeek);
        if (courses.isEmpty()) {
            return title + "：无课程（第" + week + "周，周" + dayText + "）";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(title).append("（第").append(week).append("周，周").append(dayText).append("）:\n");
        for (int i = 0; i < courses.size(); i++) {
            Course c = courses.get(i);
            sb.append(i + 1)
                    .append(". ")
                    .append(buildCourseDisplay(c));
            if (i < courses.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private static String formatSearchList(String title, String condition, List<Course> courses) {
        if (courses.isEmpty()) {
            return title + "：无匹配结果（" + condition + "）";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(title).append("（").append(condition).append("）:\n");
        for (int i = 0; i < courses.size(); i++) {
            Course c = courses.get(i);
            sb.append(i + 1)
                    .append(". ")
                    .append(buildCourseDisplay(c));
            if (i < courses.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private static String buildCourseDisplay(Course c) {
        StringBuilder sb = new StringBuilder();
        sb.append(safe(c.name));
        if (c.isExperimental) {
            sb.append("[实验]");
        }
        sb.append(" | 时间 ").append(buildTimeText(c));
        sb.append(" | 地点 ").append(sanitizeLocation(c.location));
        if (!safe(c.teacher).isEmpty()) {
            sb.append(" | 教师 ").append(c.teacher.trim());
        }
        return sb.toString();
    }

    private static String buildTimeText(Course c) {
        String direct = safe(c.timeStr).trim();
        if (!direct.isEmpty()) {
            return direct;
        }

        int startSeconds = getStartSeconds(c.startSection);
        int slotSpan = Math.max(1, (c.sectionSpan + 1) / 2);
        int endSeconds = startSeconds + slotSpan * 100 * 60;
        return formatClock(startSeconds) + "-" + formatClock(endSeconds) + "(第" + c.startSection + "节起)";
    }

    private static int getStartSeconds(int startSection) {
        switch (startSection) {
            case 1:
                return 8 * 3600;
            case 3:
                return 10 * 3600;
            case 5:
                return 14 * 3600;
            case 7:
                return 16 * 3600;
            case 9:
                return 19 * 3600;
            case 11:
                return 21 * 3600;
            default:
                int slot = Math.max(0, (startSection - 1) / 2);
                int[] starts = {8 * 3600, 10 * 3600, 14 * 3600, 16 * 3600, 19 * 3600, 21 * 3600};
                return slot < starts.length ? starts[slot] : starts[starts.length - 1];
        }
    }

    private static String formatClock(int totalSeconds) {
        int hour = totalSeconds / 3600;
        int minute = (totalSeconds % 3600) / 60;
        return String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
    }

    private static boolean isNormalCourse(Course c) {
        return c != null && !c.isRemark && c.dayOfWeek >= 1 && c.dayOfWeek <= 7;
    }

    private static int toMondayFirstDay(Calendar cal) {
        int raw = cal.get(Calendar.DAY_OF_WEEK);
        return raw == Calendar.SUNDAY ? 7 : raw - 1;
    }

    private static Calendar parseDate(String date) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            sdf.setLenient(false);
            Date parsed = sdf.parse(date);
            if (parsed == null) return null;
            Calendar cal = Calendar.getInstance();
            cal.setTime(parsed);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            return cal;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int getWeekForDate(Context context, Calendar targetDate) {
        Calendar termStart = getSemesterStartMonday(context);
        Calendar target = (Calendar) targetDate.clone();
        target.set(Calendar.HOUR_OF_DAY, 0);
        target.set(Calendar.MINUTE, 0);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        long diff = target.getTimeInMillis() - termStart.getTimeInMillis();
        return (int) (diff / (7L * 24 * 60 * 60 * 1000)) + 1;
    }

    private static Calendar getSemesterStartMonday(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_COURSE_STORAGE, Context.MODE_PRIVATE);
        long semesterStartDateMs = prefs.getLong(KEY_SEMESTER_START_DATE, 0);

        Calendar start = Calendar.getInstance();
        if (semesterStartDateMs != 0) {
            start.setTimeInMillis(semesterStartDateMs);
        }
        while (start.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            start.add(Calendar.DAY_OF_MONTH, -1);
        }
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        return start;
    }

    private static String sanitizeLocation(String raw) {
        String location = safe(raw).trim();
        while (location.startsWith("@") || location.startsWith("＠")) {
            location = location.substring(1).trim();
        }
        return location.isEmpty() ? "未定" : location;
    }

    private static String toDayText(int dayOfWeek) {
        switch (dayOfWeek) {
            case 1:
                return "一";
            case 2:
                return "二";
            case 3:
                return "三";
            case 4:
                return "四";
            case 5:
                return "五";
            case 6:
                return "六";
            case 7:
                return "日";
            default:
                return "?";
        }
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }
}
