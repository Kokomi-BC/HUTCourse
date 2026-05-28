package cn.edu.hut.course;

import android.content.Context;
import android.text.TextUtils;

import org.jsoup.Jsoup;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import cn.edu.hut.course.data.CourseStorageManager;
import cn.edu.hut.course.data.ExamStorageManager;

public final class ExamScraper {

    private static final String EXAM_API_URL = CourseScraper.BASE_URL + "/jsxsd/xsks/xsksap_list";
    private static final String EXAM_REFERER = CourseScraper.BASE_URL + "/jsxsd/xsks/xsksap_query";

    private ExamScraper() {
    }

    /**
     * 根据当前日期动态计算教务系统 xnxqid（学年学期ID）。
     * 格式：YYYY-YYYY-X，第1学期 9月~次年2月，第2学期 3月~8月。
     * 经 Playwright 实测，教务系统使用的正是此字面量格式，如 "2025-2026-2"。
     */
    private static String resolveCurrentXnxqid() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int year = cal.get(java.util.Calendar.YEAR);
        int month = cal.get(java.util.Calendar.MONTH) + 1; // 1-12
        if (month >= 9) {
            return year + "-" + (year + 1) + "-1";
        } else if (month <= 2) {
            return (year - 1) + "-" + year + "-1";
        } else {
            return (year - 1) + "-" + year + "-2";
        }
    }

    /**
     * 调用教务考试安排 API（LayUI 表格 AJAX 接口）。
     * 使用 HttpURLConnection（与 CourseScraper.fetch 一致），避免 Jsoup cookie 解析差异。
     */
    public static List<Exam> fetchExamSchedule(Context context, String cookie) throws Exception {
        if (TextUtils.isEmpty(cookie)) {
            android.util.Log.w("ExamScraper", "No cookie");
            return new ArrayList<>();
        }

        String xnxqid = resolveCurrentXnxqid();
        String query = "pageNum=1&pageSize=20&xnxqid=" + URLEncoder.encode(xnxqid, "UTF-8") + "&xqlb=";
        String fullUrl = EXAM_API_URL + "?" + query;
        android.util.Log.d("ExamScraper", "Requesting: " + fullUrl);

        HttpURLConnection conn = (HttpURLConnection) new URL(fullUrl).openConnection();
        conn.setRequestProperty("Cookie", cookie);
        conn.setRequestProperty("Accept", "application/json, text/javascript, */*; q=0.01");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setRequestProperty("Referer", EXAM_REFERER);
        conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);

        int code = conn.getResponseCode();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(
                        code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream(),
                        StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        String response = sb.toString();
        android.util.Log.d("ExamScraper", "HTTP " + code
                + " bodyLen=" + response.length()
                + " preview=" + (response.length() > 200 ? response.substring(0, 200) : response));
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

            // 尝试多种常见的 LayUI table 返回格式
            org.json.JSONArray data = root.optJSONArray("data");
            if (data == null) {
                data = root.optJSONArray("rows");
            }
            if (data == null) {
                data = root.optJSONArray("list");
            }
            if (data == null) {
                // 尝试 {"data": {"rows": [...]}}
                org.json.JSONObject dataObj = root.optJSONObject("data");
                if (dataObj != null) {
                    data = dataObj.optJSONArray("rows");
                    if (data == null) {
                        data = dataObj.optJSONArray("list");
                    }
                }
            }
            if (data == null || data.length() == 0) {
                android.util.Log.w("ExamScraper", "No recognizable data array. root keys: " + root.keys().toString()
                        + " raw_preview=" + (json.length() > 300 ? json.substring(0, 300) : json));
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
