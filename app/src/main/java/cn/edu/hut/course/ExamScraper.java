package cn.edu.hut.course;

import android.content.Context;
import android.text.TextUtils;

import org.jsoup.Jsoup;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.edu.hut.course.data.CourseStorageManager;
import cn.edu.hut.course.data.ExamStorageManager;

public final class ExamScraper {

    private static final String EXAM_API_URL = CourseScraper.BASE_URL + "/jsxsd/xsks/xsksap_list";
    private static final String EXAM_REFERER = CourseScraper.BASE_URL + "/jsxsd/xsks/xsksap_query";

    private ExamScraper() {
    }

    public static List<Exam> fetchExamSchedule(Context context, String cookie) throws IOException {
        if (TextUtils.isEmpty(cookie)) {
            android.util.Log.w("ExamScraper", "No cookie");
            return new ArrayList<>();
        }

        Map<String, String> cookies = new HashMap<>();
        for (String pair : cookie.split(";")) {
            String[] kv = pair.trim().split("=", 2);
            if (kv.length == 2) {
                cookies.put(kv[0].trim(), kv[1].trim());
            }
        }

        // LayUI 表格 AJAX 接口：GET 带查询参数
        String response = Jsoup.connect(EXAM_API_URL)
                .cookies(cookies)
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", EXAM_REFERER)
                .header("X-Requested-With", "XMLHttpRequest")
                .data("pageNum", "1")
                .data("pageSize", "20")
                .data("xnxqid", "2025-2026-2")
                .data("xqlb", "")
                .timeout(15000)
                .ignoreContentType(true)
                .get()
                .text();

        android.util.Log.d("ExamScraper", "Resp len=" + response.length() + " preview=" +
                (response.length() > 200 ? response.substring(0, 200) : response));
        return parseExamResponse(response);
    }

    public static List<Exam> parseExamResponse(String html) {
        List<Exam> exams = new ArrayList<>();
        if (TextUtils.isEmpty(html)) return exams;

        // 先尝试 JSON 解析
        List<Exam> jsonExams = tryParseJsonResponse(html);
        if (!jsonExams.isEmpty()) return jsonExams;

        // 回退到 HTML 表格解析
        return tryParseHtmlTable(html);
    }

    private static List<Exam> tryParseHtmlTable(String html) {
        List<Exam> exams = new ArrayList<>();
        try {
            org.jsoup.nodes.Document doc = Jsoup.parse(html);
            org.jsoup.nodes.Element table = doc.select("table#dataTable").first();
            if (table == null) {
                table = doc.select("table").first();
            }
            if (table == null) return exams;

            org.jsoup.select.Elements rows = table.select("tr");
            for (int i = 1; i < rows.size(); i++) {
                org.jsoup.select.Elements cells = rows.get(i).select("td");
                if (cells.size() < 7) continue;

                String timeStr = cells.size() > 3 ? cells.get(3).text().trim() : "";
                String datePart = "";
                String startTime = "";
                String endTime = "";
                if (timeStr.contains(" ")) {
                    String[] parts = timeStr.split(" ");
                    datePart = parts[0].trim();
                    if (parts.length > 1 && parts[1].contains("~")) {
                        String[] timeParts = parts[1].split("~");
                        startTime = timeParts[0].trim();
                        endTime = timeParts.length > 1 ? timeParts[1].trim() : "";
                    }
                }

                Exam exam = new Exam();
                exam.courseName = cells.size() > 6 ? cells.get(6).text().trim() : "";
                exam.courseCode = cells.size() > 5 ? cells.get(5).text().trim() : "";
                exam.teacher = cells.size() > 7 ? cells.get(7).text().trim() : "";
                exam.examDate = datePart;
                exam.startTime = startTime;
                exam.endTime = endTime;
                exam.location = cells.size() > 4 ? cells.get(4).text().trim() : "";
                exam.campus = cells.size() > 2 ? cells.get(2).text().trim() : "";
                exam.sessionInfo = cells.size() > 10 ? cells.get(10).text().trim() : "";
                exams.add(exam);
            }
            android.util.Log.d("ExamScraper", "HTML parsed " + exams.size() + " exams");
        } catch (Exception e) {
            android.util.Log.e("ExamScraper", "HTML parse error", e);
        }
        return exams;
    }

    private static List<Exam> tryParseJsonResponse(String json) {
        List<Exam> exams = new ArrayList<>();
        try {
            android.util.Log.d("ExamScraper", "Raw exam response: " + (json.length() > 500 ? json.substring(0, 500) : json));
            org.json.JSONObject root = new org.json.JSONObject(json);
            org.json.JSONArray data = root.optJSONArray("data");
            if (data == null || data.length() == 0) {
                android.util.Log.w("ExamScraper", "No data array, root keys: " + root.keys());
                return exams;
            }
            for (int i = 0; i < data.length(); i++) {
                org.json.JSONObject item = data.getJSONObject(i);
                // 教务系统返回的字段名（拼音缩写）
                String timeStr = item.optString("kssj", "");       // 考试时间

                Exam exam = new Exam();
                exam.courseName = item.optString("kskcmc", "");    // 课程名称
                exam.courseCode = item.optString("kch", "");       // 课程编号
                exam.teacher = item.optString("jsxm", "");         // 授课教师
                exam.location = item.optString("js_mc", "");       // 考场
                exam.campus = optFirst(item, "ksxq", "xqmc");      // 校区
                exam.sessionInfo = item.optString("ksccmc", "");   // 考试场次

                // 解析时间：格式 "2026-05-29 16:00~17:40"
                String datePart = "";
                String startTime = "";
                String endTime = "";
                if (timeStr.contains(" ")) {
                    String[] parts = timeStr.split(" ");
                    datePart = parts[0].trim();
                    if (parts.length > 1 && parts[1].contains("~")) {
                        String[] timeParts = parts[1].split("~");
                        startTime = timeParts[0].trim();
                        endTime = timeParts.length > 1 ? timeParts[1].trim() : "";
                    }
                }
                exam.examDate = datePart;
                exam.startTime = startTime;
                exam.endTime = endTime;
                exams.add(exam);
            }
            android.util.Log.d("ExamScraper", "Parsed " + exams.size() + " exams");
        } catch (Exception e) {
            android.util.Log.e("ExamScraper", "Parse error", e);
        }
        return exams;
    }

    private static String optFirst(org.json.JSONObject obj, String... keys) {
        for (String key : keys) {
            String val = obj.optString(key, "");
            if (!val.isEmpty() && !"null".equals(val)) return val;
        }
        return "";
    }

    /**
     * 从教务系统同步考试安排并保存到本地存储
     */
    public static boolean syncFromServer(Context context, String cookie) {
        try {
            List<Exam> exams = fetchExamSchedule(context, cookie);
            if (!exams.isEmpty()) {
                ExamStorageManager.saveExams(context, exams);
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
