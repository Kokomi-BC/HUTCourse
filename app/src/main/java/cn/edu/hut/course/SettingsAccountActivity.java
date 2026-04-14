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
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.datepicker.MaterialDatePicker;
import androidx.appcompat.app.AlertDialog;

import cn.edu.hut.course.data.CourseStorageManager;

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
    private static final String EXTRACT_SUMMARY_DEFAULT = "从教务同步最新课表";
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
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        TextView tvStartDateSummary = findViewById(R.id.tvStartDateSummary);
        tvExtractSummary = findViewById(R.id.tvExtractSummary);
        tvClearSummary = findViewById(R.id.tvClearSummary);
        tvLogoutSummary = findViewById(R.id.tvLogoutSummary);
        resetExtractSummary();
        updateActionStatusSummaries();
        updateStartDateSummary(tvStartDateSummary);
        findViewById(R.id.itemSetStartDate).setOnClickListener(v -> {
            if (!shouldHandleActionClick()) return;
            showMaterialDatePicker(tvStartDateSummary);
        });

        browserLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Intent data = result.getData();
                boolean loginSuccess = data.getBooleanExtra("login_success", false);
                if (loginSuccess) {
                    Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show();
                    Intent out = new Intent();
                    out.putExtra("action", "extract_after_login");
                    out.putExtra("cookie", data.getStringExtra("cookie"));
                    setResult(RESULT_OK, out);
                    finish();
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
                showConfirmActionDialog("清除当前课表", "仅清除本地课表，不退出登录，是否继续？", this::clearLocalScheduleOnly);
            });

        findViewById(R.id.btnLogout).setOnClickListener(v ->
            {
                if (!shouldHandleActionClick()) return;
                showConfirmActionDialog("退出登录", "将清除教务系统登录状态，是否继续？", this::logoutJwxtSession);
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
        if (!extractInProgress) {
            resetExtractSummary();
        }
    }

    private void updateActionStatusSummaries() {
        int savedCount = getSavedCourseCount();
        if (tvClearSummary != null) {
            if (savedCount > 0) {
                tvClearSummary.setText("已保存" + savedCount + "门课程，可清除");
            } else {
                tvClearSummary.setText("暂无本地课表");
            }
        }
        if (tvLogoutSummary != null) {
            tvLogoutSummary.setText(hasLocalLoginCookie() ? "当前状态：已登录" : "当前状态：未登录");
        }
    }

    private int getSavedCourseCount() {
        return CourseStorageManager.countNonRemarkCourses(this);
    }

    private boolean hasLocalLoginCookie() {
        String cookie = CookieManager.getInstance().getCookie(TARGET_URL);
        if (cookie == null || cookie.isEmpty()) {
            cookie = CookieManager.getInstance().getCookie(BASE_URL);
        }
        return cookie != null && !cookie.trim().isEmpty();
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
        runOnUiThread(() -> Toast.makeText(SettingsAccountActivity.this, "未登录或者登录信息失效", Toast.LENGTH_SHORT).show());
        setExtractSummary("未登录，请先登录教务");
    }

    private void applyPageVisualStyle() {
        View root = findViewById(R.id.rootSettingsAccount);
        if (root != null) {
            UiStyleHelper.applySecondaryPageBackground(root, this);
        }
        UiStyleHelper.applyGlassCards(findViewById(android.R.id.content), this);
    }

    private void launchBrowserForLogin() {
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
        extractInProgress = true;
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

        Runnable completeFail = () -> {
            if (finished[0]) return;
            finished[0] = true;
            if (timeoutHolder[0] != null) {
                webView.removeCallbacks(timeoutHolder[0]);
            }
            try {
                webView.stopLoading();
                webView.destroy();
            } catch (Exception ignored) {}
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
                    try {
                        webView.stopLoading();
                        webView.destroy();
                    } catch (Exception ignored) {}
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
                    try {
                        webView.stopLoading();
                        webView.destroy();
                    } catch (Exception ignored) {}
                    callback.onResult(null, false);
                }
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                tryComplete(url);
                tryFailIfStillAtLogin(url);
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
        webView.postDelayed(timeoutHolder[0], 3000L);
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
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(new androidx.appcompat.view.ContextThemeWrapper(this, com.google.android.material.R.style.Theme_Material3_DayNight_Dialog_Alert))
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
        getSharedPreferences(PREF_COURSE_COLORS, MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
        clearAppCacheDirs();
        Intent result = new Intent();
        result.putExtra("action", "reload_courses");
        setResult(RESULT_OK, result);
        Toast.makeText(this, "已清除本地课表（保留登录状态）", Toast.LENGTH_SHORT).show();
        updateActionStatusSummaries();
    }

    private void logoutJwxtSession() {
        CookieManager manager = CookieManager.getInstance();
        manager.removeSessionCookies(null);
        manager.removeAllCookies(null);
        manager.flush();
        Intent result = new Intent();
        result.putExtra("action", "reload_courses");
        setResult(RESULT_OK, result);
        Toast.makeText(this, "已退出登录", Toast.LENGTH_SHORT).show();
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
            cookie = CookieManager.getInstance().getCookie(TARGET_URL);
            if (cookie == null || cookie.isEmpty()) {
                cookie = CookieManager.getInstance().getCookie(BASE_URL);
            }
        }

        if (cookie == null || cookie.isEmpty()) {
            showNotLoggedInHint();
            extractInProgress = false;
            return;
        }

        Toast.makeText(this, "正在刷新课表...", Toast.LENGTH_SHORT).show();
        setExtractSummary("刷新课表中...");
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
                runOnUiThread(() -> {
                    Intent result = new Intent();
                    result.putExtra("action", "reload_courses");
                    setResult(RESULT_OK, result);
                    Toast.makeText(SettingsAccountActivity.this, "刷新成功，共" + finalCourseCount + "门课", Toast.LENGTH_SHORT).show();
                    setExtractSummary("刷新成功，共" + finalCourseCount + "门课");
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
        runOnUiThread(() -> Toast.makeText(SettingsAccountActivity.this, "正在尝试自动校验登录...", Toast.LENGTH_SHORT).show());
        setExtractSummary("正在重新校验登录...");
        new Thread(() -> {
            String refreshedCookie = trySilentLoginAndGetCookie();
            if (refreshedCookie != null && !refreshedCookie.isEmpty()) {
                runOnUiThread(() -> extractCourseWithFallback(refreshedCookie, false));
            } else {
                showNotLoggedInHint();
                extractInProgress = false;
            }
        }).start();
    }

    private String trySilentLoginAndGetCookie() {
        HttpURLConnection conn = null;
        try {
            String cookie = CookieManager.getInstance().getCookie(BASE_URL);
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

            Map<String, List<String>> headers = conn.getHeaderFields();
            List<String> setCookies = headers.get("Set-Cookie");
            if (setCookies != null) {
                for (String one : setCookies) {
                    CookieManager.getInstance().setCookie(BASE_URL, one);
                }
                CookieManager.getInstance().flush();
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }

        HttpURLConnection verifyConn = null;
        try {
            String newCookie = CookieManager.getInstance().getCookie(BASE_URL);
            if (newCookie == null || newCookie.isEmpty()) {
                return null;
            }

            verifyConn = (HttpURLConnection) new URL(SUCCESS_URL).openConnection();
            verifyConn.setInstanceFollowRedirects(true);
            verifyConn.setConnectTimeout(8000);
            verifyConn.setReadTimeout(8000);
            verifyConn.setRequestProperty("User-Agent", "Mozilla/5.0");
            verifyConn.setRequestProperty("Cookie", newCookie);
            int code = verifyConn.getResponseCode();
            String finalUrl = verifyConn.getURL() != null ? verifyConn.getURL().toString() : "";
            String location = verifyConn.getHeaderField("Location");

            if (finalUrl.contains(LOGIN_SUCCESS_PATH)) {
                return newCookie;
            }
            if (location != null && location.contains(LOGIN_SUCCESS_PATH)) {
                return newCookie;
            }
            if (code == HttpURLConnection.HTTP_OK) {
                String body = readBodyPreview(verifyConn);
                if (!body.contains("登录") && !body.contains("sso.jsp") && body.contains("xsMain")) {
                    return newCookie;
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
            tvStartDateSummary.setText("当前自动");
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
}
