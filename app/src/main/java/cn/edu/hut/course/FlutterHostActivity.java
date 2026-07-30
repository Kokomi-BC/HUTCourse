package cn.edu.hut.course;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.edu.hut.course.ai.AiPromptCenter;
import cn.edu.hut.course.data.AgendaStorageManager;
import cn.edu.hut.course.data.CourseStorageManager;
import cn.edu.hut.course.data.ExamStorageManager;
import cn.edu.hut.course.data.WeatherSQLiteStore;
import cn.edu.hut.course.glass.GlassBottomBarFactory;
import cn.edu.hut.course.glass.LifecycleInjector;
import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodChannel;

/**
 * FlutterHostActivity - 承载 Flutter UI 的宿主 Activity。
 * 负责通过 MethodChannel 向 Flutter 提供数据并处理导航请求。
 */
public class FlutterHostActivity extends FlutterActivity {

    private static final String CHANNEL = "cn.edu.hut.course/native";
    private MethodChannel _channel;

    private void _sendChunk(String text) {
        if (_channel != null) {
            _channel.invokeMethod("aiChunk", text != null ? text : "");
        }
    }

    private void _sendDone() {
        if (_channel != null) {
            _channel.invokeMethod("aiDone", null);
        }
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // DecorView 此时已初始化，注入 Compose 所需的 ViewTreeOwners
        LifecycleInjector.inject(this);
    }

    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        super.configureFlutterEngine(flutterEngine);
        Log.d("FlutterHost", "configureFlutterEngine called, engine=" + flutterEngine);

        _channel = new MethodChannel(
                flutterEngine.getDartExecutor().getBinaryMessenger(),
                CHANNEL);

        // 注册玻璃底栏 PlatformView（使用 AndroidLiquidGlass backdrop 库）
        flutterEngine
                .getPlatformViewsController()
                .getRegistry()
                .registerViewFactory(
                        "cn.edu.hut.course/glass_bottom_bar",
                        new GlassBottomBarFactory(index -> {
                            _channel.invokeMethod("tabSelected", index);
                            return kotlin.Unit.INSTANCE;
                        }));

        _channel.setMethodCallHandler((call, result) -> {
                    Log.d("FlutterHost", "MethodChannel call: " + call.method);
                    switch (call.method) {
                        case "getCourses": {
                            List<Map<String, Object>> courses = getCoursesList();
                            Log.d("FlutterHost", "getCourses: returning " + courses.size() + " courses");
                            result.success(courses);
                            break;
                        }
                        case "getCoursesJson": {
                            // 直接返回原始 JSON，绕过 Map 序列化排查问题
                            String json = CourseStorageManager.loadCoursesJson(FlutterHostActivity.this);
                            Log.d("FlutterHost", "getCoursesJson: raw JSON length=" + (json != null ? json.length() : 0));
                            result.success(json != null ? json : "[]");
                            break;
                        }
                        case "getCourseCount": {
                            int count = CourseStorageManager.countNonRemarkCourses(FlutterHostActivity.this);
                            Log.d("FlutterHost", "getCourseCount: " + count);
                            result.success(count);
                            break;
                        }
                        case "getTableId": {
                            long tableId = CourseStorageManager.getActiveTableId(FlutterHostActivity.this);
                            Log.d("FlutterHost", "getTableId: " + tableId);
                            result.success((int) tableId);
                            break;
                        }
                        case "getTodayCourses":
                            result.success(getTodayCoursesMap());
                            break;
                        case "getCurrentWeek":
                            result.success(getCurrentWeekNum());
                            break;
                        case "getProfile":
                            result.success(getProfileMap());
                            break;
                        case "getThemeColor":
                            result.success(getThemeColorInt());
                            break;
                        case "getAgendaItems":
                            result.success(getAgendaItemsList());
                            break;
                        case "getAgendaItemsJson":
                            result.success(getAgendaItemsJson());
                            break;
                        case "openSettings":
                            openSettingsPage();
                            result.success(null);
                            break;
                        case "openAccountSettings":
                            startActivity(new Intent(this, SettingsAccountActivity.class));
                            result.success(null);
                            break;
                        case "openDisplaySettings":
                            startActivity(new Intent(this, SettingsDisplayActivity.class));
                            result.success(null);
                            break;
                        case "openDataSettings":
                            startActivity(new Intent(this, SettingsDataActivity.class));
                            result.success(null);
                            break;
                        case "openAiSettings":
                            startActivity(new Intent(this, SettingsAiActivity.class));
                            result.success(null);
                            break;
                        case "openExam":
                            openExamPage();
                            result.success(null);
                            break;
                        case "openAgenda":
                            startActivity(new Intent(this, AgendaOverviewActivity.class));
                            result.success(null);
                            break;
                        case "sendAiMessage": {
                            String msg = call.argument("message");
                            if (msg == null || msg.trim().isEmpty()) {
                                result.success("请输入消息内容");
                                break;
                            }
                            final String userMessage = msg;
                            new Thread(() -> {
                                try {
                                    AiConfigStore.AiModelConfig config =
                                            AiConfigStore.getTopPriorityModel(FlutterHostActivity.this);
                                    if (config == null
                                            || config.apiKey == null || config.apiKey.isEmpty()
                                            || config.modelName == null || config.modelName.isEmpty()) {
                                        runOnUiThread(() -> result.success("请先在设置中配置 AI 模型"));
                                        return;
                                    }
                                    String systemPrompt = AiPromptCenter.buildSystemPrompt();
                                    String reply = AiGateway.chat(
                                            config.provider,
                                            config.baseUrl,
                                            config.apiKey,
                                            config.modelName,
                                            systemPrompt,
                                            userMessage);
                                    runOnUiThread(() -> result.success(reply));
                                } catch (Exception e) {
                                    Log.e("FlutterHost", "sendAiMessage error", e);
                                    runOnUiThread(() -> result.success("AI 请求失败: " + e.getMessage()));
                                }
                            }).start();
                            break;
                        }
                        case "startAiStream": {
                            String msg = call.argument("message");
                            if (msg == null || msg.trim().isEmpty()) {
                                result.success(false);
                                break;
                            }
                            final String userMessage = msg;
                            result.success(true); // 立即确认启动
                            new Thread(() -> {
                                try {
                                    AiConfigStore.AiModelConfig config =
                                            AiConfigStore.getTopPriorityModel(FlutterHostActivity.this);
                                    if (config == null
                                            || config.apiKey == null || config.apiKey.isEmpty()
                                            || config.modelName == null || config.modelName.isEmpty()) {
                                        runOnUiThread(() -> _sendChunk("请先在设置中配置 AI 模型"));
                                        runOnUiThread(() -> _sendDone());
                                        return;
                                    }
                                    String systemPrompt = AiPromptCenter.buildSystemPrompt();
                                    AiGateway.chat(
                                            config.provider,
                                            config.baseUrl,
                                            config.apiKey,
                                            config.modelName,
                                            systemPrompt,
                                            userMessage,
                                            (List<String>) null,  // imagePaths
                                            null,                  // cacheHint
                                            currentText -> runOnUiThread(() -> _sendChunk(currentText)));
                                    runOnUiThread(() -> _sendDone());
                                } catch (Exception e) {
                                    Log.e("FlutterHost", "startAiStream error", e);
                                    runOnUiThread(() -> _sendChunk("AI 请求失败: " + e.getMessage()));
                                    runOnUiThread(() -> _sendDone());
                                }
                            }).start();
                            break;
                        }
                        case "getWeather":
                            result.success(getWeatherMap());
                            break;
                        case "getWeekDates":
                            int week = call.argument("week");
                            result.success(getWeekDatesList(week));
                            break;
                        case "loadChatHistory":
                            result.success(getChatHistory());
                            break;
                        case "saveChatMessage": {
                            String sessionId = call.argument("sessionId");
                            String role = call.argument("role");
                            String content = call.argument("content");
                            result.success(saveChatMessage(sessionId, role, content));
                            break;
                        }
                        case "loadSessionMessages": {
                            String sessionId = call.argument("sessionId");
                            result.success(loadSessionMessages(sessionId));
                            break;
                        }
                        case "deleteSession": {
                            String sessionId = call.argument("sessionId");
                            result.success(deleteSession(sessionId));
                            break;
                        }
                        case "setThemeColor": {
                            int color = call.argument("color");
                            getSharedPreferences("course_storage", MODE_PRIVATE)
                                    .edit()
                                    .putInt("timetable_theme_color", color)
                                    .apply();
                            result.success(true);
                            break;
                        }
                        case "getAiConfig": {
                            result.success(getAiConfigMap());
                            break;
                        }
                        case "getCurrentActualWeek": {
                            result.success(getCurrentWeekNum());
                            break;
                        }
                        default:
                            result.notImplemented();
                    }
                });
    }

    private List<Map<String, Object>> getCoursesList() {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            List<Course> courses = CourseStorageManager.loadCourses(this);
            Log.d("FlutterHost", "getCoursesList: loaded " + courses.size() + " courses from storage");
            for (Course c : courses) {
                if (c == null || c.isRemark) continue;
                Map<String, Object> map = new HashMap<>();
                map.put("name", c.name != null ? c.name : "");
                map.put("teacher", c.teacher != null ? c.teacher : "");
                map.put("location", c.location != null ? c.location : "");
                map.put("dayOfWeek", c.dayOfWeek);
                map.put("startSection", c.startSection);
                map.put("endSection", c.startSection + c.sectionSpan - 1);
                map.put("weeks", c.weeks != null ? new ArrayList<>(c.weeks) : new ArrayList<>());
                map.put("isExperimental", c.isExperimental);
                list.add(map);
            }
            Log.d("FlutterHost", "getCoursesList: returning " + list.size() + " non-remark courses");
        } catch (Exception e) {
            Log.e("FlutterHost", "getCoursesList error", e);
        }
        return list;
    }

    private Map<String, Object> getTodayCoursesMap() {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> courses = new ArrayList<>();
        try {
            Calendar now = Calendar.getInstance();
            int todayDay = now.get(Calendar.DAY_OF_WEEK);
            int dayOfWeek = (todayDay == Calendar.SUNDAY) ? 7 : todayDay - 1;
            int currentHour = now.get(Calendar.HOUR_OF_DAY);
            int currentMinute = now.get(Calendar.MINUTE);
            int currentSeconds = currentHour * 3600 + currentMinute * 60;

            // 五大节时间范围（秒）
            int[] slotStart = {8*3600, 10*3600, 14*3600, 16*3600, 19*3600};
            int[] slotEnd   = {9*3600+40*60, 11*3600+40*60, 15*3600+40*60, 17*3600+40*60, 20*3600+40*60};
            String[] slotLabels = {"第一大节", "第二大节", "第三大节", "第四大节", "第五大节"};

            List<Course> allCourses = CourseStorageManager.loadCourses(this);
            int currentWeek = getCurrentWeekNum();

            // 先收集当天所有课程
            List<Map<String, Object>> raw = new ArrayList<>();
            for (Course c : allCourses) {
                if (c == null || c.isRemark || c.dayOfWeek != dayOfWeek) continue;
                if (c.weeks == null || !c.weeks.contains(currentWeek)) continue;

                Map<String, Object> courseMap = new HashMap<>();
                courseMap.put("name", c.name != null ? c.name : "");
                courseMap.put("teacher", c.teacher != null ? c.teacher : "");
                courseMap.put("location", c.location != null ? c.location : "");
                courseMap.put("startSection", c.startSection);
                courseMap.put("endSection", c.startSection + c.sectionSpan - 1);

                int slotIndex = Math.max(0, Math.min(4, (c.startSection - 1) / 2));
                courseMap.put("timeSlot", slotLabels[slotIndex]);
                courseMap.put("slotIndex", slotIndex);

                // 判断是否已完成/进行中/下一节
                boolean isFinished = currentSeconds > slotEnd[slotIndex];
                boolean isCurrent = currentSeconds >= slotStart[slotIndex] && currentSeconds <= slotEnd[slotIndex];
                courseMap.put("isFinished", isFinished);
                courseMap.put("isCurrent", isCurrent);
                courseMap.put("isNext", false); // 稍后计算

                raw.add(courseMap);
            }

            // 按节次排序
            raw.sort((a, b) -> {
                int sa = (int) a.get("slotIndex");
                int sb = (int) b.get("slotIndex");
                return Integer.compare(sa, sb);
            });

            // 计算 isNext: 第一门还没开始（且未结束）的课
            boolean foundNext = false;
            for (Map<String, Object> item : raw) {
                int si = (int) item.get("slotIndex");
                boolean finished = (boolean) item.get("isFinished");
                if (!finished && !foundNext) {
                    item.put("isNext", true);
                    foundNext = true;
                }
            }

            courses.addAll(raw);
        } catch (Exception e) { }

        result.put("courses", courses);
        return result;
    }

    private int getCurrentWeekNum() {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("course_storage", MODE_PRIVATE);
            long semesterStart = prefs.getLong("semester_start_date", 0);
            if (semesterStart > 0) {
                Calendar start = Calendar.getInstance();
                start.setTimeInMillis(semesterStart);
                while (start.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                    start.add(Calendar.DAY_OF_MONTH, -1);
                }
                start.set(Calendar.HOUR_OF_DAY, 0);
                start.set(Calendar.MINUTE, 0);
                start.set(Calendar.SECOND, 0);
                start.set(Calendar.MILLISECOND, 0);

                Calendar now = Calendar.getInstance();
                long diff = now.getTimeInMillis() - start.getTimeInMillis();
                return (int) (diff / (7L * 24L * 60L * 60L * 1000L)) + 1;
            }
        } catch (Exception e) { }
        return 1;
    }

    private Map<String, Object> getProfileMap() {
        Map<String, Object> profile = new HashMap<>();
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("course_storage", MODE_PRIVATE);
            profile.put("name", prefs.getString("profile_name", "--"));
            profile.put("studentId", prefs.getString("profile_student_id", "--"));
            profile.put("className", prefs.getString("profile_class", "--"));
            profile.put("college", prefs.getString("profile_college", "--"));

            List<Exam> exams = ExamStorageManager.loadExams(this);
            if (exams.isEmpty()) {
                profile.put("examSummary", "暂无考试安排");
            } else {
                String today = AgendaStorageManager.formatDate(Calendar.getInstance());
                int remaining = 0;
                for (Exam e : exams) {
                    if (e.examDate != null && e.examDate.compareTo(today) >= 0) {
                        remaining++;
                    }
                }
                profile.put("examSummary", "共 " + exams.size() + " 门考试，剩余 " + remaining + " 门");
            }
        } catch (Exception e) {
            profile.put("name", "--");
            profile.put("examSummary", "暂无考试安排");
        }
        return profile;
    }

    private int getThemeColorInt() {
        try {
            return getSharedPreferences("course_storage", MODE_PRIVATE)
                    .getInt("timetable_theme_color", 0xFF667eea);
        } catch (Exception e) {
            return 0xFF667eea;
        }
    }

    private List<Map<String, Object>> getAgendaItemsList() {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            List<Agenda> agendas = AgendaStorageManager.loadAllAgendas(this);
            for (Agenda a : agendas) {
                if (a == null) continue;
                Map<String, Object> map = new HashMap<>();
                map.put("id", a.id);
                map.put("title", a.title != null ? a.title : "");
                map.put("description", a.description != null ? a.description : "");
                map.put("date", a.date != null ? a.date : "");
                map.put("startMinute", a.startMinute);
                map.put("endMinute", a.endMinute);
                map.put("priority", a.priority);
                map.put("repeatRule", a.repeatRule != null ? a.repeatRule : "");
                map.put("location", a.location != null ? a.location : "");
                map.put("renderColor", a.renderColor);
                map.put("readOnly", a.readOnly);
                map.put("monthlyStrategy", a.monthlyStrategy != null ? a.monthlyStrategy : "skip");
                list.add(map);
            }
        } catch (Exception e) { }
        return list;
    }

    private String getAgendaItemsJson() {
        try {
            List<Agenda> agendas = AgendaStorageManager.loadAllAgendas(this);
            org.json.JSONArray arr = new org.json.JSONArray();
            for (Agenda a : agendas) {
                if (a == null) continue;
                org.json.JSONObject obj = new org.json.JSONObject();
                obj.put("id", a.id);
                obj.put("title", a.title != null ? a.title : "");
                obj.put("description", a.description != null ? a.description : "");
                obj.put("date", a.date != null ? a.date : "");
                obj.put("startMinute", a.startMinute);
                obj.put("endMinute", a.endMinute);
                obj.put("priority", a.priority);
                obj.put("repeatRule", a.repeatRule != null ? a.repeatRule : "");
                obj.put("location", a.location != null ? a.location : "");
                obj.put("renderColor", a.renderColor);
                obj.put("readOnly", a.readOnly);
                obj.put("monthlyStrategy", a.monthlyStrategy != null ? a.monthlyStrategy : "skip");
                arr.put(obj);
            }
            return arr.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    private void openSettingsPage() {
        try {
            startActivity(new Intent(this, SettingsHomeActivity.class));
        } catch (Exception e) { }
    }

    private void openExamPage() {
        try {
            startActivity(new Intent(this, ExamActivity.class));
        } catch (Exception e) { }
    }

    private List<String> getWeekDatesList(int week) {
        List<String> dates = new ArrayList<>();
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("course_storage", MODE_PRIVATE);
            long semesterStart = prefs.getLong("semester_start_date", 0);
            if (semesterStart > 0) {
                Calendar start = Calendar.getInstance();
                start.setTimeInMillis(semesterStart);
                // 找到学期第一周的周一
                while (start.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                    start.add(Calendar.DAY_OF_MONTH, -1);
                }
                start.set(Calendar.HOUR_OF_DAY, 0);
                start.set(Calendar.MINUTE, 0);
                start.set(Calendar.SECOND, 0);
                start.set(Calendar.MILLISECOND, 0);
                // 跳到目标周
                start.add(Calendar.WEEK_OF_YEAR, week - 1);
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
                for (int i = 0; i < 7; i++) {
                    dates.add(sdf.format(start.getTime()));
                    start.add(Calendar.DAY_OF_MONTH, 1);
                }
            }
        } catch (Exception e) { }
        return dates;
    }

    private Map<String, Object> getWeatherMap() {
        Map<String, Object> result = new HashMap<>();
        try {
            TianyuanWeatherManager.WeatherSnapshot snapshot =
                    TianyuanWeatherManager.buildFromDb(this);
            if (snapshot != null && snapshot.success && !snapshot.forecasts.isEmpty()) {
                // Today's weather (first forecast)
                TianyuanWeatherManager.DayForecast today = snapshot.forecasts.get(0);
                result.put("todayWeather", today.weather != null ? today.weather : "");
                result.put("todayTemp", today.temperature != null ? today.temperature : "");
                result.put("todayWind", today.wind != null ? today.wind : "");
                result.put("todayHumidity", today.humidity != null ? today.humidity : "");
                result.put("feelsLike", today.feelsLike != null ? today.feelsLike : "");
                result.put("area", snapshot.area != null ? snapshot.area : "");

                // 7-day forecast list
                List<Map<String, Object>> forecastList = new ArrayList<>();
                for (TianyuanWeatherManager.DayForecast f : snapshot.forecasts) {
                    Map<String, Object> fMap = new HashMap<>();
                    fMap.put("date", f.date != null ? f.date : "");
                    fMap.put("dayLabel", f.dayLabel != null ? f.dayLabel : "");
                    fMap.put("weather", f.weather != null ? f.weather : "");
                    fMap.put("temperature", f.temperature != null ? f.temperature : "");
                    fMap.put("wind", f.wind != null ? f.wind : "");
                    forecastList.add(fMap);
                }
                result.put("forecasts", forecastList);
            } else {
                result.put("todayWeather", "");
                result.put("todayTemp", "");
                result.put("todayWind", "");
                result.put("todayHumidity", "");
                result.put("feelsLike", "");
                result.put("area", "");
                result.put("forecasts", new ArrayList<>());
            }
        } catch (Exception e) {
            result.put("todayWeather", "");
            result.put("todayTemp", "");
            result.put("todayWind", "");
            result.put("todayHumidity", "");
            result.put("feelsLike", "");
            result.put("area", "");
            result.put("forecasts", new ArrayList<>());
        }
        return result;
    }

    // ==================== Chat History Persistence ====================

    private static final String HISTORY_PREFS = "ai_chat_history";
    private static final String HISTORY_KEY = "history_json";
    private static final int MAX_SESSIONS = 30;

    private org.json.JSONArray loadHistoryJson() {
        try {
            String json = getSharedPreferences(HISTORY_PREFS, MODE_PRIVATE)
                    .getString(HISTORY_KEY, null);
            if (json != null && !json.isEmpty()) {
                return new org.json.JSONArray(json);
            }
        } catch (Exception e) {
            Log.e("FlutterHost", "loadHistoryJson error", e);
        }
        return new org.json.JSONArray();
    }

    private void saveHistoryJson(org.json.JSONArray arr) {
        getSharedPreferences(HISTORY_PREFS, MODE_PRIVATE)
                .edit()
                .putString(HISTORY_KEY, arr.toString())
                .apply();
    }

    private boolean saveChatMessage(String sessionId, String role, String content) {
        if (sessionId == null || sessionId.isEmpty()) return false;
        if (role == null || content == null) return false;
        try {
            org.json.JSONArray arr = loadHistoryJson();
            org.json.JSONObject target = null;
            int targetIdx = -1;
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject s = arr.getJSONObject(i);
                if (sessionId.equals(s.optString("id", ""))) {
                    target = s;
                    targetIdx = i;
                    break;
                }
            }
            if (target == null) {
                // 创建新 session
                target = new org.json.JSONObject();
                target.put("id", sessionId);
                target.put("title", "user".equals(role) && content.length() > 0
                        ? (content.length() > 30 ? content.substring(0, 30) + "..." : content)
                        : "新对话");
                target.put("titleFromAi", false);
                target.put("messages", new org.json.JSONArray());
                target.put("updatedAt", System.currentTimeMillis());
            }
            // 追加消息
            org.json.JSONArray msgs = target.getJSONArray("messages");
            org.json.JSONObject msg = new org.json.JSONObject();
            msg.put("role", role);
            msg.put("content", content);
            msg.put("timestamp", System.currentTimeMillis());
            msgs.put(msg);
            // 更新时间戳
            target.put("updatedAt", System.currentTimeMillis());
            // 如果是新 session（之前没找到），插入到最前面
            if (targetIdx < 0) {
                org.json.JSONArray newArr = new org.json.JSONArray();
                newArr.put(target);
                for (int i = 0; i < arr.length(); i++) {
                    newArr.put(arr.get(i));
                }
                arr = newArr;
            }
            // 限制最大会话数
            while (arr.length() > MAX_SESSIONS) {
                arr.remove(arr.length() - 1);
            }
            saveHistoryJson(arr);
            return true;
        } catch (Exception e) {
            Log.e("FlutterHost", "saveChatMessage error", e);
            return false;
        }
    }

    private List<Map<String, Object>> loadSessionMessages(String sessionId) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (sessionId == null || sessionId.isEmpty()) return list;
        try {
            org.json.JSONArray arr = loadHistoryJson();
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject s = arr.getJSONObject(i);
                if (sessionId.equals(s.optString("id", ""))) {
                    org.json.JSONArray msgs = s.optJSONArray("messages");
                    if (msgs != null) {
                        for (int j = 0; j < msgs.length(); j++) {
                            org.json.JSONObject m = msgs.getJSONObject(j);
                            Map<String, Object> map = new HashMap<>();
                            map.put("role", m.optString("role", ""));
                            map.put("content", m.optString("content", ""));
                            list.add(map);
                        }
                    }
                    break;
                }
            }
        } catch (Exception e) {
            Log.e("FlutterHost", "loadSessionMessages error", e);
        }
        return list;
    }

    private boolean deleteSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) return false;
        try {
            org.json.JSONArray arr = loadHistoryJson();
            org.json.JSONArray newArr = new org.json.JSONArray();
            boolean found = false;
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject s = arr.getJSONObject(i);
                if (sessionId.equals(s.optString("id", ""))) {
                    found = true;
                } else {
                    newArr.put(s);
                }
            }
            if (found) {
                saveHistoryJson(newArr);
            }
            return found;
        } catch (Exception e) {
            Log.e("FlutterHost", "deleteSession error", e);
            return false;
        }
    }

    private List<Map<String, Object>> getChatHistory() {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            org.json.JSONArray arr = loadHistoryJson();
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject session = arr.getJSONObject(i);
                String id = session.optString("id", "");
                String title = session.optString("title", "");
                long updatedAt = session.optLong("updatedAt", 0);
                // 如果 title 为空或是默认值，尝试从第一条消息提取
                if (title.isEmpty() || "新对话".equals(title)) {
                    org.json.JSONArray msgs = session.optJSONArray("messages");
                    if (msgs != null && msgs.length() > 0) {
                        org.json.JSONObject first = msgs.getJSONObject(0);
                        String content = first.optString("content", "");
                        title = content.length() > 30 ? content.substring(0, 30) + "..." : content;
                    }
                }
                if (title.isEmpty()) title = "新对话";
                // 格式化日期
                String dateStr = "";
                if (updatedAt > 0) {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                            "yyyy-MM-dd HH:mm", java.util.Locale.getDefault());
                    dateStr = sdf.format(new java.util.Date(updatedAt));
                }
                Map<String, Object> item = new HashMap<>();
                item.put("title", title);
                item.put("date", dateStr);
                item.put("id", id);
                list.add(item);
            }
        } catch (Exception e) {
            Log.e("FlutterHost", "getChatHistory error", e);
        }
        return list;
    }

    private Map<String, Object> getAiConfigMap() {
        Map<String, Object> map = new HashMap<>();
        try {
            AiConfigStore.AiModelConfig config = AiConfigStore.getTopPriorityModel(this);
            if (config != null) {
                map.put("modelName", config.modelName != null ? config.modelName : "");
                map.put("provider", config.provider != null ? config.provider : "");
                map.put("baseUrl", config.baseUrl != null ? config.baseUrl : "");
                map.put("apiKey", config.apiKey != null ? config.apiKey : "");
                map.put("displayName", config.displayName != null ? config.displayName : "");
                map.put("multimodal", config.multimodal);
            }
        } catch (Exception e) { }
        return map;
    }
}