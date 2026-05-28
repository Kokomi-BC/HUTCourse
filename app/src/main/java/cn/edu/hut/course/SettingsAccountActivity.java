package cn.edu.hut.course;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.datepicker.MaterialDatePicker;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import cn.edu.hut.course.data.CourseStorageManager;
import cn.edu.hut.course.data.ExamStorageManager;

import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;

public class SettingsAccountActivity extends AppCompatActivity {

    private static final String PREF_COURSE_STORAGE = "course_storage";
    private static final String PREF_COURSE_COLORS = "course_colors";
    private static final String TARGET_URL = "http://jwxt.hut.edu.cn/jsxsd/xskb/xskb_list.do?viweType=0";
    private static final String BASE_URL = "http://jwxt.hut.edu.cn";
    private static final String LOGIN_URL = BASE_URL + "/jsxsd/sso.jsp";
    private static final String SUCCESS_URL = BASE_URL + "/jsxsd/framework/xsMainV.htmlx";
    private static final String LOGIN_SUCCESS_PATH = "/jsxsd/framework/xsMainV.htmlx";
    private static final String CAS_LOGIN_PREFIX = "https://mycas.hut.edu.cn/cas";
    private static final String EXTRACT_SUMMARY_DEFAULT = "从教务同步最新数据";
    private static final long CLICK_GUARD_MS = 700L;

    private ActivityResultLauncher<Intent> browserLauncher;
    private TextView tvExtractSummary;
    private TextView tvClearSummary;
    private TextView tvLogoutSummary;
    private long lastActionClickAt = 0L;
    private boolean extractInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiStyleHelper.hideStatusBar(this);
        setContentView(R.layout.activity_settings_account);
        applyPageVisualStyle();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        UiStyleHelper.styleGlassToolbar(toolbar, this);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_rounded_24);
        toolbar.setNavigationOnClickListener(v -> finish());

        TextView tvStartDateSummary = findViewById(R.id.tvStartDateSummary);
        TextView tvEndDateSummary = findViewById(R.id.tvEndDateSummary);
        tvExtractSummary = findViewById(R.id.tvExtractSummary);
        tvClearSummary = findViewById(R.id.tvClearSummary);
        tvLogoutSummary = findViewById(R.id.tvLogoutSummary);
        resetExtractSummary();
        updateActionStatusSummaries();
        updateStartDateSummary(tvStartDateSummary);
        updateEndDateSummary(tvEndDateSummary);
        ensureTableSwitcherCard();

        findViewById(R.id.itemSetStartDate).setOnClickListener(v -> {
            if (!shouldHandleActionClick()) return;
            showMaterialDatePicker(tvStartDateSummary);
        });

        findViewById(R.id.itemSetEndDate).setOnClickListener(v -> {
            if (!shouldHandleActionClick()) return;
            showEndDatePicker(tvEndDateSummary);
        });

        browserLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Intent data = result.getData();
                boolean loginSuccess = data.getBooleanExtra("login_success", false);
                if (loginSuccess) {
                    Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show();
                    String newCookie = data.getStringExtra("cookie");
                    long activeId = CourseStorageManager.getActiveTableId(this);
                    CourseStorageManager.saveCookieForTable(this, activeId, newCookie);
                    // 通知主页刷新数据
                    Intent out = new Intent();
                    out.putExtra("action", "extract_after_login");
                    out.putExtra("cookie", newCookie);
                    setResult(RESULT_OK, out);
                    // 不关闭当前界面，留在账号与同步页面
                    updateActionStatusSummaries();
                }
            }
        });

        findViewById(R.id.btnOpenJwxt).setOnClickListener(v -> {
            if (!shouldHandleActionClick()) return;
            launchBrowserForLogin();
        });

        findViewById(R.id.btnExtractFromJwxt).setOnClickListener(v -> {
            if (!shouldHandleActionClick()) return;
            extractCourseWithPreLogin();
        });

        findViewById(R.id.btnClearCurrent).setOnClickListener(v ->
            {
                if (!shouldHandleActionClick()) return;
                showConfirmActionDialog("清空当前课表", "仅清空当前课表的课程与日程，不退出登录，是否继续？", this::clearLocalScheduleOnly);
            });

        findViewById(R.id.btnLogout).setOnClickListener(v ->
            {
                if (!shouldHandleActionClick()) return;
                showConfirmActionDialog("退出登录", "将清除当前课表的教务系统登录状态，其他课表不受影响，是否继续？", this::logoutJwxtSession);
            });
    }

    private boolean shouldHandleActionClick() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastActionClickAt < CLICK_GUARD_MS) {
            return false;
        }
        lastActionClickAt = now;
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyPageVisualStyle();
        updateActionStatusSummaries();
        ensureTableSwitcherCard();
        if (!extractInProgress) {
            resetExtractSummary();
        }
    }

    private void updateActionStatusSummaries() {
        int savedCount = getSavedCourseCount();
        if (tvClearSummary != null) {
            if (savedCount > 0) {
                tvClearSummary.setText("当前课表已保存" + savedCount + "门课程");
            } else {
                tvClearSummary.setText("当前课表暂无数据");
            }
        }
        if (tvLogoutSummary != null) {
            long activeId = CourseStorageManager.getActiveTableId(this);
            String savedCookie = CourseStorageManager.getCookieForTable(this, activeId);
            tvLogoutSummary.setText((savedCookie != null && !savedCookie.isEmpty()) ? "状态：已登录" : "状态：未登录");
        }
    }

    private int getSavedCourseCount() {
        return CourseStorageManager.countNonRemarkCourses(this);
    }

    private boolean hasLocalLoginCookie() {
        long activeId = CourseStorageManager.getActiveTableId(this);
        String savedCookie = CourseStorageManager.getCookieForTable(this, activeId);
        return savedCookie != null && !savedCookie.trim().isEmpty();
    }

    private void setExtractSummary(String text) {
        runOnUiThread(() -> {
            if (tvExtractSummary != null) {
                tvExtractSummary.setText(text);
            }
        });
    }

    private void resetExtractSummary() {
        setExtractSummary(EXTRACT_SUMMARY_DEFAULT);
    }

    private void showNotLoggedInHint() {
        runOnUiThread(() -> Toast.makeText(SettingsAccountActivity.this, "请登录教务系统", Toast.LENGTH_SHORT).show());
        setExtractSummary("未登录，请先登录教务");
    }

    private void applyPageVisualStyle() {
        View root = findViewById(R.id.rootSettingsAccount);
        if (root != null) {
            UiStyleHelper.applySecondaryPageBackground(root, this);
        }
        UiStyleHelper.applyGlassCards(findViewById(android.R.id.content), this);
    }

    private void syncCookieToWebView() {
        long activeId = CourseStorageManager.getActiveTableId(this);
        String savedCookie = CourseStorageManager.getCookieForTable(this, activeId);
        CookieManager cookieManager = CookieManager.getInstance();
        // 不能在这里 removeAllCookies()，否则会清空 CAS 的 TGC (单点登录凭证)！
        // 只将当前课表记录的 JSESSIONID 同步到 jwxt 即可
        if (savedCookie != null && !savedCookie.trim().isEmpty()) {
            String[] cookies = savedCookie.split(";");
            for (String c : cookies) {
                if (!c.trim().isEmpty()) {
                    cookieManager.setCookie(BASE_URL, c.trim());
                }
            }
        } else {
            // 新表无 cookie：直接清除 session cookies，防止残留旧表登录态
            cookieManager.removeSessionCookies(null);
        }
        cookieManager.flush();
    }

    private void launchBrowserForLogin() {
        syncCookieToWebView();
        Intent intent = new Intent(this, BrowserActivity.class);
        intent.putExtra("url", CourseScraper.LOGIN_URL);
        intent.putExtra("autoCloseOnLoginSuccess", true);
        browserLauncher.launch(intent);
    }

    private void extractCourseWithPreLogin() {
        if (extractInProgress) {
            Toast.makeText(this, "正在处理中，请稍候", Toast.LENGTH_SHORT).show();
            setExtractSummary("正在处理中，请稍候...");
            return;
        }
        syncCookieToWebView();
        extractInProgress = true;

        long activeId = CourseStorageManager.getActiveTableId(this);
        String savedCookie = CourseStorageManager.getCookieForTable(this, activeId);

        if (savedCookie != null && !savedCookie.trim().isEmpty()) {
            extractCourseWithFallback(savedCookie, true);
        } else {
            Toast.makeText(this, "正在校验教务登录...", Toast.LENGTH_SHORT).show();
            setExtractSummary("正在校验登录状态...");
            checkLoginByWebView((cookie, success) -> {
                if (success && cookie != null && !cookie.isEmpty()) {
                    extractCourseWithFallback(cookie, true);
                } else {
                    showNotLoggedInHint();
                    extractInProgress = false;
                }
            });
        }
    }

    private interface LoginCheckCallback {
        void onResult(String cookie, boolean success);
    }

    private void checkLoginByWebView(LoginCheckCallback callback) {
        WebView webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        final boolean[] finished = {false};
        final Runnable[] timeoutHolder = new Runnable[1];

        Runnable cleanup = () -> {
            if (isFinishing() || isDestroyed()) return;
            webView.setWebViewClient(null);
            webView.setWebChromeClient(null);
            webView.stopLoading();
            webView.post(() -> {
                try { webView.destroy(); } catch (Exception ignored) {}
            });
        };

        Runnable completeFail = () -> {
            if (finished[0]) return;
            finished[0] = true;
            if (timeoutHolder[0] != null) {
                webView.removeCallbacks(timeoutHolder[0]);
            }
            cleanup.run();
            callback.onResult(null, false);
        };

        webView.setWebViewClient(new WebViewClient() {
            private void tryComplete(String url) {
                if (finished[0]) return;
                if (url != null && url.contains(LOGIN_SUCCESS_PATH)) {
                    finished[0] = true;
                    if (timeoutHolder[0] != null) {
                        webView.removeCallbacks(timeoutHolder[0]);
                    }
                    CookieManager.getInstance().flush();
                    String cookie = CookieManager.getInstance().getCookie(url);
                    if (cookie == null || cookie.isEmpty()) {
                        cookie = CookieManager.getInstance().getCookie(BASE_URL);
                    }
                    cleanup.run();
                    callback.onResult(cookie, cookie != null && !cookie.isEmpty());
                }
            }

            private void tryFailIfStillAtLogin(String url) {
                if (finished[0]) return;
                if (isNotLoggedInUrl(url)) {
                    finished[0] = true;
                    if (timeoutHolder[0] != null) {
                        webView.removeCallbacks(timeoutHolder[0]);
                    }
                    cleanup.run();
                    callback.onResult(null, false);
                }
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                tryComplete(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                tryComplete(url);
                tryFailIfStillAtLogin(url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }
        });

        timeoutHolder[0] = completeFail;
        webView.postDelayed(timeoutHolder[0], 8000L); // 给隐藏 WebView 足够的时间去经历各种 SSO 重定向
        webView.loadUrl(LOGIN_URL);
    }

    private boolean isNotLoggedInUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.startsWith(CAS_LOGIN_PREFIX)
                || lower.contains("mycas.hut.edu.cn/cas")
                || lower.contains("/cas/login")
                || lower.contains("jsxsd/sso.jsp");
    }

    private void showConfirmActionDialog(String title, String message, Runnable onConfirm) {
        new
                MaterialAlertDialogBuilder(new androidx.appcompat.view.ContextThemeWrapper(this, R.style.Theme_MyApplication_Dialog))
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("取消", null)
                .setPositiveButton("确认", (dialog, which) -> {
                    if (onConfirm != null) onConfirm.run();
                })
                .show();
    }

    private void clearLocalScheduleOnly() {
        CourseStorageManager.clearCourses(this);
        // Only clear current table's colors
        long tableId = CourseStorageManager.getActiveTableId(this);
        SharedPreferences colorPrefs = getSharedPreferences(PREF_COURSE_COLORS, MODE_PRIVATE);
        SharedPreferences.Editor editor = colorPrefs.edit();
        for (String key : colorPrefs.getAll().keySet()) {
            if (key.startsWith(tableId + "_")) {
                editor.remove(key);
            }
        }
        editor.apply();
        clearAppCacheDirs();
        Intent result = new Intent();
        result.putExtra("action", "reload_courses");
        setResult(RESULT_OK, result);
        Toast.makeText(this, "已清空当前课表（保留登录状态）", Toast.LENGTH_SHORT).show();
        updateActionStatusSummaries();
    }

    private void logoutJwxtSession() {
        // Only clear current table's stored cookie
        long activeId = CourseStorageManager.getActiveTableId(this);
        CourseStorageManager.clearCookieForTable(this, activeId);
        // Also clear system CookieManager to force re-login for this table
        CookieManager manager = CookieManager.getInstance();
        manager.removeSessionCookies(null);
        manager.removeAllCookies(null);
        manager.flush();
        Intent result = new Intent();
        result.putExtra("action", "reload_courses");
        setResult(RESULT_OK, result);
        Toast.makeText(this, "已退出当前课表登录", Toast.LENGTH_SHORT).show();
        updateActionStatusSummaries();
    }

    private void clearAppCacheDirs() {
        deleteRecursively(getCacheDir());
        java.io.File externalCache = getExternalCacheDir();
        if (externalCache != null) {
            deleteRecursively(externalCache);
        }
    }

    private void deleteRecursively(java.io.File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            java.io.File[] children = file.listFiles();
            if (children != null) {
                for (java.io.File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    private void extractCourseWithFallback(String passedCookie, boolean allowSilentRetry) {
        String cookie = passedCookie;
        if (cookie == null || cookie.isEmpty()) {
            // Try per-table stored cookie first
            long activeId = CourseStorageManager.getActiveTableId(this);
            cookie = CourseStorageManager.getCookieForTable(this, activeId);
        }

        if (cookie == null || cookie.isEmpty()) {
            showNotLoggedInHint();
            extractInProgress = false;
            return;
        }

        Toast.makeText(this, "正在刷新数据...", Toast.LENGTH_SHORT).show();
        setExtractSummary("刷新数据中...");
        final String finalCookie = cookie;
        CourseScraper.extractAllTables(finalCookie, new CourseScraper.ScrapeCallback() {
            @Override
            public void onSuccess(List<Course> courses) {
                int courseCount = 0;
                for (Course c : courses) {
                    if (c != null && !c.isRemark) {
                        courseCount++;
                    }
                }

                if (courseCount == 0) {
                    if (allowSilentRetry) {
                        setExtractSummary("登录状态疑似失效，正在重新校验...");
                        attemptSilentLoginAndRetry();
                    } else {
                        showNotLoggedInHint();
                        extractInProgress = false;
                    }
                    return;
                }

                final int finalCourseCount = courseCount;
                saveCoursesToLocal(courses);
                // Save cookie per table
                long activeId = CourseStorageManager.getActiveTableId(SettingsAccountActivity.this);
                CourseStorageManager.saveCookieForTable(SettingsAccountActivity.this, activeId, finalCookie);
                // 同时刷新个人信息
                new Thread(() -> {
                    try {
                        CourseScraper.StudentProfile profile = CourseScraper.scrapeStudentProfile(finalCookie);
                        CourseStorageManager.saveProfileForTable(SettingsAccountActivity.this, activeId,
                                profile.name, profile.studentId, profile.className, profile.college);
                    } catch (Exception ignored) {}
                }).start();
                 // 同步考试安排（在 onSuccess 回调线程内同步执行，确保拿到结果后再刷新 UI）
                int fetchedExamCount = 0;
                try {
                    List<Exam> exams = ExamScraper.fetchExamSchedule(SettingsAccountActivity.this, finalCookie);
                    if (!exams.isEmpty()) {
                        ExamStorageManager.saveExams(SettingsAccountActivity.this, exams);
                        fetchedExamCount = exams.size();
                    }
                } catch (Exception e) {
                    android.util.Log.w("SettingsAccount", "Exam sync failed", e);
                }

                final int finalExamCount = fetchedExamCount;
                runOnUiThread(() -> {
                    Intent result = new Intent();
                    result.putExtra("action", "reload_courses");
                    setResult(RESULT_OK, result);
                    String toast = "刷新成功：课程 " + finalCourseCount + " 门";
                    if (finalExamCount > 0) {
                        toast += "，考试 " + finalExamCount + " 门";
                    }
                    Toast.makeText(SettingsAccountActivity.this, toast, Toast.LENGTH_SHORT).show();
                    setExtractSummary(toast);
                    extractInProgress = false;
                });
            }

            @Override
            public void onError(String msg) {
                if (allowSilentRetry) {
                    setExtractSummary("刷新失败，正在重新校验登录...");
                    attemptSilentLoginAndRetry();
                } else {
                    runOnUiThread(() -> Toast.makeText(SettingsAccountActivity.this, "刷新失败: " + msg, Toast.LENGTH_SHORT).show());
                    setExtractSummary("刷新失败，请重试");
                    extractInProgress = false;
                }
            }
        });
    }

    private void attemptSilentLoginAndRetry() {
        runOnUiThread(() -> {
            Toast.makeText(SettingsAccountActivity.this, "正在尝试自动校验登录...", Toast.LENGTH_SHORT).show();
            setExtractSummary("正在重新校验登录...");
            checkLoginByWebView((refreshedCookie, success) -> {
                if (success && refreshedCookie != null && !refreshedCookie.isEmpty()) {
                    extractCourseWithFallback(refreshedCookie, false);
                } else {
                    showNotLoggedInHint();
                    extractInProgress = false;
                }
            });
        });
    }

    private String trySilentLoginAndGetCookie() {
        HttpURLConnection conn = null;
        String cookie = null;
        try {
            long activeId = CourseStorageManager.getActiveTableId(this);
            cookie = CourseStorageManager.getCookieForTable(this, activeId);
            if (cookie == null || cookie.isEmpty()) {
                return null;
            }

            URL loginUrl = new URL(LOGIN_URL);
            conn = (HttpURLConnection) loginUrl.openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Cookie", cookie);
            conn.connect();

            String updatedCookie = cookie;
            Map<String, List<String>> headers = conn.getHeaderFields();
            List<String> setCookies = headers.get("Set-Cookie");
            if (setCookies != null) {
                for (String one : setCookies) {
                    String[] parts = one.split(";");
                    if (parts.length > 0) {
                        if (!updatedCookie.contains(parts[0])) {
                            updatedCookie = updatedCookie + "; " + parts[0];
                        }
                    }
                }
            }
            cookie = updatedCookie;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }

        HttpURLConnection verifyConn = null;
        try {
            if (cookie == null || cookie.isEmpty()) {
                return null;
            }

            verifyConn = (HttpURLConnection) new URL(SUCCESS_URL).openConnection();
            verifyConn.setInstanceFollowRedirects(true);
            verifyConn.setConnectTimeout(8000);
            verifyConn.setReadTimeout(8000);
            verifyConn.setRequestProperty("User-Agent", "Mozilla/5.0");
            verifyConn.setRequestProperty("Cookie", cookie);
            int code = verifyConn.getResponseCode();
            String finalUrl = verifyConn.getURL() != null ? verifyConn.getURL().toString() : "";
            String location = verifyConn.getHeaderField("Location");

            if (finalUrl.contains(LOGIN_SUCCESS_PATH)) {
                return cookie;
            }
            if (location != null && location.contains(LOGIN_SUCCESS_PATH)) {
                return cookie;
            }
            if (code == HttpURLConnection.HTTP_OK) {
                String body = readBodyPreview(verifyConn);
                if (!body.contains("登录") && !body.contains("sso.jsp") && body.contains("xsMain")) {
                    return cookie;
                }
            }
            return null;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (verifyConn != null) {
                verifyConn.disconnect();
            }
        }
    }

    private String readBodyPreview(HttpURLConnection conn) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            int lines = 0;
            while ((line = br.readLine()) != null && lines < 20) {
                sb.append(line);
                lines++;
            }
            return sb.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private void saveCoursesToLocal(List<Course> courses) {
        CourseStorageManager.saveCourses(this, courses);
    }

    private void updateStartDateSummary(TextView tvStartDateSummary) {
        SharedPreferences prefs = getSharedPreferences(PREF_COURSE_STORAGE, MODE_PRIVATE);
        long semesterStartDateMs = prefs.getLong("semester_start_date", 0);
        if (semesterStartDateMs == 0) {
            tvStartDateSummary.setText("自动");
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            tvStartDateSummary.setText(sdf.format(semesterStartDateMs));
        }
    }

    private void showMaterialDatePicker(TextView tvStartDateSummary) {
        SharedPreferences prefs = getSharedPreferences(PREF_COURSE_STORAGE, MODE_PRIVATE);
        long semesterStartDateMs = prefs.getLong("semester_start_date", 0);
        long defaultSelection = semesterStartDateMs == 0 ? MaterialDatePicker.todayInUtcMilliseconds() : semesterStartDateMs;

        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("选择开学日期")
                .setSelection(defaultSelection)
                .build();

        picker.addOnPositiveButtonClickListener(selection -> {
            Calendar selected = Calendar.getInstance();
            selected.setTimeInMillis(selection);
            selected.set(Calendar.HOUR_OF_DAY, 0);
            selected.set(Calendar.MINUTE, 0);
            selected.set(Calendar.SECOND, 0);
            selected.set(Calendar.MILLISECOND, 0);

            prefs.edit().putLong("semester_start_date", selected.getTimeInMillis()).apply();
            updateStartDateSummary(tvStartDateSummary);

            Intent i = new Intent();
            i.putExtra("action", "refresh_current_week");
            setResult(RESULT_OK, i);
        });

        picker.show(getSupportFragmentManager(), "semester_date_picker");
    }
    private void updateEndDateSummary(TextView tvEndDateSummary) {
        SharedPreferences prefs = getSharedPreferences(PREF_COURSE_STORAGE, MODE_PRIVATE);
        long endMs = prefs.getLong("semester_end_date", 0);
        if (endMs == 0) {
            tvEndDateSummary.setText("未设置（自动推算20周）");
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            tvEndDateSummary.setText(sdf.format(endMs));
        }
    }

    private void showEndDatePicker(TextView tvEndDateSummary) {
        SharedPreferences prefs = getSharedPreferences(PREF_COURSE_STORAGE, MODE_PRIVATE);
        long currentMs = prefs.getLong("semester_end_date", 0);
        long startMs = prefs.getLong("semester_start_date", 0);
        long defaultSel;
        if (currentMs != 0) {
            defaultSel = currentMs;
        } else if (startMs != 0) {
            Calendar estEnd = Calendar.getInstance();
            estEnd.setTimeInMillis(startMs);
            estEnd.add(Calendar.WEEK_OF_YEAR, 20);
            defaultSel = estEnd.getTimeInMillis();
        } else {
            defaultSel = MaterialDatePicker.todayInUtcMilliseconds();
        }

        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("选择放假日期")
                .setSelection(defaultSel)
                .build();

        picker.addOnPositiveButtonClickListener(selection -> {
            Calendar selected = Calendar.getInstance();
            selected.setTimeInMillis(selection);
            selected.set(Calendar.HOUR_OF_DAY, 0);
            selected.set(Calendar.MINUTE, 0);
            selected.set(Calendar.SECOND, 0);
            selected.set(Calendar.MILLISECOND, 0);

            prefs.edit().putLong("semester_end_date", selected.getTimeInMillis()).apply();
            updateEndDateSummary(tvEndDateSummary);

            Intent i = new Intent();
            i.putExtra("action", "refresh_current_week");
            setResult(RESULT_OK, i);
        });

        picker.show(getSupportFragmentManager(), "semester_end_date_picker");
    }
    // ==================== Table Switcher ====================

    private void ensureTableSwitcherCard() {
        LinearLayout contentList = null;
        // Find the ScrollView inside the CoordinatorLayout
        View root = findViewById(R.id.rootSettingsAccount);
        if (root instanceof androidx.coordinatorlayout.widget.CoordinatorLayout) {
            for (int i = 0; i < ((android.view.ViewGroup) root).getChildCount(); i++) {
                View child = ((android.view.ViewGroup) root).getChildAt(i);
                if (child instanceof ScrollView && ((ScrollView) child).getChildCount() > 0
                        && ((ScrollView) child).getChildAt(0) instanceof LinearLayout) {
                    contentList = (LinearLayout) ((ScrollView) child).getChildAt(0);
                    break;
                }
            }
        }
        if (contentList == null) return;

        // Remove old switcher card if exists
        View oldCard = contentList.findViewWithTag("table_switcher_card");
        if (oldCard != null) {
            contentList.removeView(oldCard);
        }

        List<CourseTable> tables = CourseStorageManager.readAllCourseTables(this);
        long activeId = CourseStorageManager.getActiveTableId(this);
        String activeName = "未命名课表";
        for (CourseTable t : tables) {
            if (t.id == activeId) {
                activeName = (t.name != null && !t.name.trim().isEmpty()) ? t.name.trim() : "未命名课表";
                break;
            }
        }

        com.google.android.material.card.MaterialCardView card = new com.google.android.material.card.MaterialCardView(this);
        card.setTag("table_switcher_card");
        card.setRadius(dp(24));
        card.setCardElevation(0f);
        card.setClickable(true);
        card.setFocusable(true);
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        card.setForeground(getResources().getDrawable(tv.resourceId, getTheme()));

        LinearLayout cardContent = new LinearLayout(this);
        cardContent.setOrientation(LinearLayout.VERTICAL);
        cardContent.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView cardTitle = new TextView(this);
        cardTitle.setText("切换课表");
        cardTitle.setTextSize(16);
        cardTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        cardContent.addView(cardTitle);

        TextView cardSummary = new TextView(this);
        cardSummary.setText("当前：" + activeName + "  ›");
        cardSummary.setTextSize(12);
        cardSummary.setPadding(0, dp(6), 0, 0);
        cardContent.addView(cardSummary);

        card.addView(cardContent);
        card.setOnClickListener(v -> showTableSwitcherSheet());

        // Insert at the top of the content list
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(lp);
        contentList.addView(card, 0);
    }

    private void showTableSwitcherSheet() {
        if (isFinishing() || isDestroyed()) return;

        List<CourseTable> tables = CourseStorageManager.readAllCourseTables(this);
        long activeId = CourseStorageManager.getActiveTableId(this);

        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp(8), 0, dp(16));

        TextView title = new TextView(this);
        title.setText("切换课表");
        title.setTextSize(18);
        title.setPadding(dp(20), dp(12), dp(20), dp(8));
        root.addView(title);

        for (CourseTable t : tables) {
            boolean isActive = t.id == activeId;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(dp(20), dp(10), dp(12), dp(10));
            row.setClickable(true);
            row.setFocusable(true);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            TextView ind = new TextView(this);
            ind.setText(isActive ? "● " : "○ ");
            ind.setTextSize(14);
            ind.setTextColor(isActive ? getResources().getColor(android.R.color.holo_blue_dark, getTheme()) : 0xFF5F6368);
            row.addView(ind);

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvName = new TextView(this);
            tvName.setText(t.name != null && !t.name.trim().isEmpty() ? t.name.trim() : "未命名课表");
            tvName.setTextSize(15);
            info.addView(tvName);

            if (!t.profileName.isEmpty()) {
                TextView tvProfile = new TextView(this);
                tvProfile.setText(t.profileName);
                tvProfile.setTextSize(12);
                info.addView(tvProfile);
            }

            row.addView(info);
            final long tableId = t.id;
            row.setOnClickListener(v -> {
                sheet.dismiss();
                if (tableId != activeId) {
                    CourseStorageManager.setActiveTableId(SettingsAccountActivity.this, tableId);
                    syncCookieToWebView();
                    Toast.makeText(SettingsAccountActivity.this, "已切换到：" + (t.name != null && !t.name.trim().isEmpty() ? t.name.trim() : "未命名课表"), Toast.LENGTH_SHORT).show();
                    updateActionStatusSummaries();
                    ensureTableSwitcherCard();
                }
            });
            root.addView(row);
        }

        sheet.setContentView(root);
        sheet.show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}

