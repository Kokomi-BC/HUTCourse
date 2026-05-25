package cn.edu.hut.course;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import cn.edu.hut.course.data.CourseStorageManager;

public class FirstTimeSetupActivity extends AppCompatActivity {

    private static final String PREF_COURSE_STORAGE = "course_storage";
    private static final String PREF_COURSE_COLORS = "course_colors";
    private static final String KEY_SEMESTER_START_DATE = "semester_start_date";
    private static final String KEY_SEMESTER_END_DATE = "semester_end_date";
    private static final String KEY_SHOW_GRID_LINES = "show_grid_lines";
    private static final String KEY_TIMETABLE_THEME_COLOR = "timetable_theme_color";
    private static final String KEY_TIMETABLE_FONT_SCALE = "timetable_font_scale";
    private static final String KEY_FIRST_LAUNCH_COMPLETED = "first_launch_completed";

    private static final String BASE_URL = "http://jwxt.hut.edu.cn";
    private static final String LOGIN_URL = BASE_URL + "/jsxsd/sso.jsp";
    private static final String LOGIN_SUCCESS_PATH = "/jsxsd/framework/xsMainV.htmlx";
    private static final String TARGET_URL = BASE_URL + "/jsxsd/xskb/xskb_list.do?viweType=0";
    private static final int TIMETABLE_FONT_PERCENT_MIN = 85;
    private static final int TIMETABLE_FONT_PERCENT_MAX = 130;

    private TextView tvLoginStatus;
    private TextView tvFetchStatus;
    private TextView tvSemesterDateSummary;
    private TextView tvSemesterEndDateSummary;
    private TextView tvFontPercent;
    private TextView tvFontPreview;
    private SeekBar seekFontSize;
    private LinearLayout layoutThemePaletteRow;
    private MaterialSwitch switchGridLines;
    private MaterialButton btnEnter;

    private ActivityResultLauncher<Intent> browserLauncher;
    private boolean fetchInProgress = false;
    private long lastActionClickAt = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiStyleHelper.hideStatusBar(this);
        setContentView(R.layout.activity_first_time_setup);
        applyPageVisualStyle();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        UiStyleHelper.styleGlassToolbar(toolbar, this);

        tvLoginStatus = findViewById(R.id.tvLoginStatus);
        tvFetchStatus = findViewById(R.id.tvFetchStatus);
        tvSemesterDateSummary = findViewById(R.id.tvSemesterDateSummary);
        tvSemesterEndDateSummary = findViewById(R.id.tvSemesterEndDateSummary);
        tvFontPercent = findViewById(R.id.tvFontPercent);
        tvFontPreview = findViewById(R.id.tvFontPreview);
        seekFontSize = findViewById(R.id.seekFontSize);
        layoutThemePaletteRow = findViewById(R.id.layoutThemePaletteRow);
        switchGridLines = findViewById(R.id.switchGridLines);
        btnEnter = findViewById(R.id.btnEnter);

        browserLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        boolean loginSuccess = result.getData().getBooleanExtra("login_success", false);
                        if (loginSuccess) {
                            Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show();
                            updateLoginStatus();
                        }
                    }
                });

        // 教务系统登录
        findViewById(R.id.cardLoginJwxt).setOnClickListener(v -> {
            if (!shouldHandleClick()) return;
            launchBrowserForLogin();
        });

        // 刷新数据
        findViewById(R.id.cardFetchSchedule).setOnClickListener(v -> {
            if (!shouldHandleClick()) return;
            fetchCourseFromJwxt();
        });

        // 开学日期
        findViewById(R.id.cardSemesterDate).setOnClickListener(v -> {
            if (!shouldHandleClick()) return;
            showSemesterDatePicker();
        });

        // 放假日期
        findViewById(R.id.cardSemesterEndDate).setOnClickListener(v -> {
            if (!shouldHandleClick()) return;
            showSemesterEndDatePicker();
        });

        // 主题色
        findViewById(R.id.cardThemeColor).setOnClickListener(v -> {
            if (!shouldHandleClick()) return;
            showThemeHexColorPicker();
        });

        // 大模型配置
        findViewById(R.id.cardAiSettings).setOnClickListener(v -> {
            if (!shouldHandleClick()) return;
            startActivity(new Intent(this, SettingsAiActivity.class));
        });

        // 进入课表
        btnEnter.setOnClickListener(v -> {
            markFirstLaunchCompleted();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        // 初始化控件
        setupGridLinesSwitch();
        setupFontSizeSeekBar();
        updateAllStatuses();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyPageVisualStyle();
        updateAllStatuses();
    }

    private boolean shouldHandleClick() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastActionClickAt < 500L) return false;
        lastActionClickAt = now;
        return true;
    }

    // ==================== 视觉样式 ====================

    private void applyPageVisualStyle() {
        View root = findViewById(R.id.rootFirstTimeSetup);
        if (root != null) {
            UiStyleHelper.applySecondaryPageBackground(root, this);
        }
        UiStyleHelper.applyGlassCards(findViewById(android.R.id.content), this);

        SharedPreferences prefs = getSharedPreferences(PREF_COURSE_STORAGE, MODE_PRIVATE);
        int themeColor = prefs.getInt(KEY_TIMETABLE_THEME_COLOR, ColorPaletteProvider.defaultThemeColor());
        if (btnEnter != null) {
            btnEnter.setBackgroundTintList(android.content.res.ColorStateList.valueOf(themeColor));
            btnEnter.setTextColor(ColorUtils.calculateLuminance(themeColor) < 0.5 ? Color.WHITE : Color.BLACK);
        }
    }

    // ==================== 状态刷新 ====================

    private void updateAllStatuses() {
        updateLoginStatus();
        updateFetchStatus();
        updateSemesterDateSummary();
        updateSemesterEndDateSummary();
        renderThemePaletteRow();
        refreshFontSizeControls();
    }

    private void updateLoginStatus() {
        if (tvLoginStatus == null) return;
        boolean loggedIn = hasLoginCookie();
        tvLoginStatus.setText(loggedIn ? "状态：已登录" : "打开浏览器完成统一认证登录");
    }

    private void updateFetchStatus() {
        if (tvFetchStatus == null) return;
        int count = getSavedCourseCount();
        if (count > 0) {
            tvFetchStatus.setText("已保存 " + count + " 门课程，可重新刷新");
        } else {
            tvFetchStatus.setText("从教务系统同步最新课表");
        }
    }

    private int getSavedCourseCount() {
        return CourseStorageManager.countNonRemarkCourses(this);
    }

    private boolean hasLoginCookie() {
        String cookie = CookieManager.getInstance().getCookie(TARGET_URL);
        if (cookie == null || cookie.isEmpty()) {
            cookie = CookieManager.getInstance().getCookie(BASE_URL);
        }
        return cookie != null && !cookie.trim().isEmpty();
    }

    // ==================== 教务系统 ====================

    private void launchBrowserForLogin() {
        Intent intent = new Intent(this, BrowserActivity.class);
        intent.putExtra("url", CourseScraper.LOGIN_URL);
        intent.putExtra("autoCloseOnLoginSuccess", true);
        browserLauncher.launch(intent);
    }

    private void fetchCourseFromJwxt() {
        if (fetchInProgress) {
            Toast.makeText(this, "正在处理中，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        fetchInProgress = true;
        tvFetchStatus.setText("正在校验登录状态...");
        Toast.makeText(this, "正在校验教务登录...", Toast.LENGTH_SHORT).show();

        checkLoginStatus((cookie, success) -> {
            if (success && cookie != null && !cookie.isEmpty()) {
                doExtractCourses(cookie, true);
            } else {
                Toast.makeText(this, "未登录或登录已失效，请先登录教务系统", Toast.LENGTH_SHORT).show();
                tvFetchStatus.setText("未登录，请先登录教务系统");
                fetchInProgress = false;
            }
        });
    }

    private void checkLoginStatus(LoginCheckCallback callback) {
        WebView webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        final boolean[] finished = {false};
        final Runnable[] timeoutHolder = new Runnable[1];

        Runnable fail = () -> {
            if (finished[0]) return;
            finished[0] = true;
            if (timeoutHolder[0] != null) webView.removeCallbacks(timeoutHolder[0]);
            try { webView.stopLoading(); webView.destroy(); } catch (Exception ignored) {}
            callback.onResult(null, false);
        };

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                checkUrl(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                checkUrl(url);
            }

            private void checkUrl(String url) {
                if (finished[0]) return;
                if (url != null && url.contains(LOGIN_SUCCESS_PATH)) {
                    finished[0] = true;
                    if (timeoutHolder[0] != null) webView.removeCallbacks(timeoutHolder[0]);
                    CookieManager.getInstance().flush();
                    String c = CookieManager.getInstance().getCookie(url);
                    if (c == null || c.isEmpty()) c = CookieManager.getInstance().getCookie(BASE_URL);
                    try { webView.stopLoading(); webView.destroy(); } catch (Exception ignored) {}
                    callback.onResult(c, c != null && !c.isEmpty());
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }
        });

        timeoutHolder[0] = fail;
        webView.postDelayed(timeoutHolder[0], 3000L);
        webView.loadUrl(LOGIN_URL);
    }

    private interface LoginCheckCallback {
        void onResult(String cookie, boolean success);
    }

    private void doExtractCourses(String cookie, boolean allowSilentRetry) {
        Toast.makeText(this, "正在刷新数据...", Toast.LENGTH_SHORT).show();
        tvFetchStatus.setText("刷新数据中...");

        CourseScraper.extractAllTables(cookie, new CourseScraper.ScrapeCallback() {
            @Override
            public void onSuccess(List<Course> courses) {
                int count = 0;
                for (Course c : courses) {
                    if (c != null && !c.isRemark) count++;
                }
                if (count == 0) {
                    if (allowSilentRetry) {
                        tvFetchStatus.setText("登录疑似失效，重新校验...");
                        attemptSilentLoginAndRetry();
                    } else {
                        Toast.makeText(FirstTimeSetupActivity.this, "未获取到课程", Toast.LENGTH_SHORT).show();
                        tvFetchStatus.setText("未获取到课程，请重试");
                        fetchInProgress = false;
                    }
                    return;
                }
                saveCourses(courses);
                // 同时刷新个人信息
                final String profileCookie = cookie;
                new Thread(() -> {
                    try {
                        CourseScraper.StudentProfile profile = CourseScraper.scrapeStudentProfile(profileCookie);
                        long activeId = CourseStorageManager.getActiveTableId(FirstTimeSetupActivity.this);
                        CourseStorageManager.saveProfileForTable(FirstTimeSetupActivity.this, activeId,
                                profile.name, profile.studentId, profile.className, profile.college);
                    } catch (Exception ignored) {
                        // 个人信息刷新失败不影响课表刷新结果
                    }
                }).start();
                final int finalCount = count;
                runOnUiThread(() -> {
                    Toast.makeText(FirstTimeSetupActivity.this, "刷新成功，共 " + finalCount + " 门课", Toast.LENGTH_SHORT).show();
                    tvFetchStatus.setText("刷新成功，共 " + finalCount + " 门课");
                    fetchInProgress = false;
                });
            }

            @Override
            public void onError(String msg) {
                if (allowSilentRetry) {
                    tvFetchStatus.setText("刷新失败，重新校验...");
                    attemptSilentLoginAndRetry();
                } else {
                    runOnUiThread(() -> Toast.makeText(FirstTimeSetupActivity.this, "刷新失败: " + msg, Toast.LENGTH_SHORT).show());
                    tvFetchStatus.setText("刷新失败，请重试");
                    fetchInProgress = false;
                }
            }
        });
    }

    private void attemptSilentLoginAndRetry() {
        new Thread(() -> {
            String refreshedCookie = trySilentLogin();
            if (refreshedCookie != null && !refreshedCookie.isEmpty()) {
                runOnUiThread(() -> doExtractCourses(refreshedCookie, false));
            } else {
                runOnUiThread(() -> {
                    Toast.makeText(FirstTimeSetupActivity.this, "登录校验失败，请重新登录教务系统", Toast.LENGTH_SHORT).show();
                    tvFetchStatus.setText("登录失效，请重新登录");
                    fetchInProgress = false;
                });
            }
        }).start();
    }

    private String trySilentLogin() {
        HttpURLConnection conn = null;
        try {
            String cookie = CookieManager.getInstance().getCookie(BASE_URL);
            if (cookie == null || cookie.isEmpty()) return null;

            URL loginUrl = new URL(LOGIN_URL);
            conn = (HttpURLConnection) loginUrl.openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Cookie", cookie);
            conn.connect();

            java.util.Map<String, java.util.List<String>> headers = conn.getHeaderFields();
            java.util.List<String> setCookies = headers.get("Set-Cookie");
            if (setCookies != null) {
                for (String one : setCookies) {
                    CookieManager.getInstance().setCookie(BASE_URL, one);
                }
                CookieManager.getInstance().flush();
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }

        HttpURLConnection verifyConn = null;
        try {
            String newCookie = CookieManager.getInstance().getCookie(BASE_URL);
            if (newCookie == null || newCookie.isEmpty()) return null;

            verifyConn = (HttpURLConnection) new URL(BASE_URL + "/jsxsd/framework/xsMainV.htmlx").openConnection();
            verifyConn.setInstanceFollowRedirects(true);
            verifyConn.setConnectTimeout(8000);
            verifyConn.setReadTimeout(8000);
            verifyConn.setRequestProperty("User-Agent", "Mozilla/5.0");
            verifyConn.setRequestProperty("Cookie", newCookie);
            verifyConn.connect();
            String finalUrl = verifyConn.getURL() != null ? verifyConn.getURL().toString() : "";
            if (finalUrl.contains(LOGIN_SUCCESS_PATH)) return newCookie;
        } catch (Exception ignored) {
        } finally {
            if (verifyConn != null) verifyConn.disconnect();
        }
        return null;
    }

    private void saveCourses(List<Course> courses) {
        CourseStorageManager.saveCourses(this, courses);
    }

    // ==================== 开学日期 ====================

    private void updateSemesterDateSummary() {
        if (tvSemesterDateSummary == null) return;
        SharedPreferences prefs = getSharedPreferences(PREF_COURSE_STORAGE, MODE_PRIVATE);
        long ms = prefs.getLong(KEY_SEMESTER_START_DATE, 0);
        if (ms == 0) {
            tvSemesterDateSummary.setText("自动（周一）");
        } else {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
            tvSemesterDateSummary.setText(fmt.format(ms));
        }
    }

    private void showSemesterDatePicker() {
        SharedPreferences prefs = getSharedPreferences(PREF_COURSE_STORAGE, MODE_PRIVATE);
        long currentMs = prefs.getLong(KEY_SEMESTER_START_DATE, 0);
        long defaultSel = currentMs == 0 ? MaterialDatePicker.todayInUtcMilliseconds() : currentMs;

        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("选择开学日期")
                .setSelection(defaultSel)
                .build();
        picker.addOnPositiveButtonClickListener(selection -> {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(selection);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long oldStart = prefs.getLong(KEY_SEMESTER_START_DATE, 0);
            prefs.edit().putLong(KEY_SEMESTER_START_DATE, cal.getTimeInMillis()).apply();
            updateSemesterDateSummary();
            if (oldStart != 0 && oldStart != cal.getTimeInMillis()) {
                Toast.makeText(this, "开学日期已更新，返回课表后将自动适配新学期的周数范围", Toast.LENGTH_LONG).show();
            }
        });
        picker.show(getSupportFragmentManager(), "semester_date_picker");
    }

    // ==================== 放假日期 ====================

    private void updateSemesterEndDateSummary() {
        if (tvSemesterEndDateSummary == null) return;
        SharedPreferences prefs = getSharedPreferences(PREF_COURSE_STORAGE, MODE_PRIVATE);
        long ms = prefs.getLong(KEY_SEMESTER_END_DATE, 0);
        if (ms == 0) {
            tvSemesterEndDateSummary.setText("未设置（自动推算20周）");
        } else {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
            tvSemesterEndDateSummary.setText(fmt.format(ms));
        }
    }

    private void showSemesterEndDatePicker() {
        SharedPreferences prefs = getSharedPreferences(PREF_COURSE_STORAGE, MODE_PRIVATE);
        long currentMs = prefs.getLong(KEY_SEMESTER_END_DATE, 0);
        long startMs = prefs.getLong(KEY_SEMESTER_START_DATE, 0);
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
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(selection);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            prefs.edit().putLong(KEY_SEMESTER_END_DATE, cal.getTimeInMillis()).apply();
            updateSemesterEndDateSummary();
        });
        picker.show(getSupportFragmentManager(), "semester_end_date_picker");
    }

    // ==================== 课表主题色 ====================

    private void renderThemePaletteRow() {
        if (layoutThemePaletteRow == null) return;
        layoutThemePaletteRow.removeAllViews();
        int current = getCurrentThemeColor();
        int[] palette = ColorPaletteProvider.vibrantLightPalette();
        int accent = UiStyleHelper.resolveOnSurfaceColor(this);

        for (int color : palette) {
            boolean selected = current == color;
            addPaletteDot(layoutThemePaletteRow, color, selected, null, accent, v -> setThemeColor(color));
        }
        addPaletteDot(layoutThemePaletteRow,
                UiStyleHelper.resolveGlassCardColor(this),
                false, "+", accent,
                v -> showThemeHexColorPicker());
    }

    private int getCurrentThemeColor() {
        return getSharedPreferences(PREF_COURSE_STORAGE, MODE_PRIVATE)
                .getInt(KEY_TIMETABLE_THEME_COLOR, ColorPaletteProvider.defaultThemeColor());
    }

    private void setThemeColor(int color) {
        getSharedPreferences(PREF_COURSE_STORAGE, MODE_PRIVATE)
                .edit().putInt(KEY_TIMETABLE_THEME_COLOR, color).apply();
        renderThemePaletteRow();
        // 只更新按钮颜色，不调 applyPageVisualStyle 否则 applyGlassCards 会把色卡颜色覆盖为半透明
        if (btnEnter != null) {
            btnEnter.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
            btnEnter.setTextColor(ColorUtils.calculateLuminance(color) < 0.5 ? Color.WHITE : Color.BLACK);
        }
    }

    private void addPaletteDot(LinearLayout parent, int color, boolean selected,
                                String label, int strokeColor, View.OnClickListener listener) {
        int size = dp(40);
        int strokeWidth = dp(2);

        MaterialCardView dot = new MaterialCardView(this);
        dot.setRadius(size / 2f);
        dot.setCardElevation(0f);
        dot.setCardBackgroundColor(color);
        if (selected) {
            dot.setStrokeWidth(strokeWidth);
            dot.setStrokeColor(strokeColor);
        } else {
            dot.setStrokeWidth(0);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMargins(0, 0, dp(10), 0);
        dot.setLayoutParams(lp);
        dot.setClickable(true);
        dot.setFocusable(true);
        dot.setOnClickListener(listener);

        if (!TextUtils.isEmpty(label)) {
            TextView labelView = new TextView(this);
            labelView.setText(label);
            labelView.setTextColor(UiStyleHelper.resolveOnSurfaceColor(this));
            labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
            labelView.setGravity(android.view.Gravity.CENTER);
            dot.addView(labelView, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT));
        }

        parent.addView(dot);
    }

    private void showThemeHexColorPicker() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(
                new androidx.appcompat.view.ContextThemeWrapper(this,
                        com.google.android.material.R.style.Theme_Material3_DayNight_Dialog_Alert));
        builder.setTitle("自定义主题色");

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("#FFD166");
        input.setText(String.format("#%06X", 0xFFFFFF & getCurrentThemeColor()));
        builder.setView(input);

        builder.setNegativeButton("取消", null);
        builder.setPositiveButton("确定", (dialog, which) -> {
            try {
                int color = Color.parseColor(input.getText().toString().trim());
                setThemeColor(color);
            } catch (Exception e) {
                Toast.makeText(this, "颜色格式无效，请输入 #RRGGBB", Toast.LENGTH_SHORT).show();
            }
        });
        builder.show();
    }

    // ==================== 网格线 ====================

    private void setupGridLinesSwitch() {
        if (switchGridLines == null) return;
        SharedPreferences prefs = getSharedPreferences(PREF_COURSE_STORAGE, MODE_PRIVATE);
        switchGridLines.setChecked(prefs.getBoolean(KEY_SHOW_GRID_LINES, true));
        switchGridLines.setOnCheckedChangeListener((btn, checked) -> {
            prefs.edit().putBoolean(KEY_SHOW_GRID_LINES, checked).apply();
        });
    }

    // ==================== 字号 ====================

    private void setupFontSizeSeekBar() {
        if (seekFontSize == null) return;
        seekFontSize.setMax(TIMETABLE_FONT_PERCENT_MAX - TIMETABLE_FONT_PERCENT_MIN);
        refreshFontSizeControls();

        seekFontSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int percent = TIMETABLE_FONT_PERCENT_MIN + progress;
                updateFontPercentLabel(percent);
                if (fromUser) {
                    setFontPercent(percent);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void refreshFontSizeControls() {
        int current = getCurrentFontPercent();
        if (seekFontSize != null) {
            seekFontSize.setProgress(current - TIMETABLE_FONT_PERCENT_MIN);
        }
        updateFontPercentLabel(current);
    }

    private int getCurrentFontPercent() {
        float scale = getSharedPreferences(PREF_COURSE_STORAGE, MODE_PRIVATE)
                .getFloat(KEY_TIMETABLE_FONT_SCALE, 1.0f);
        if (Float.isNaN(scale) || Float.isInfinite(scale)) scale = 1.0f;
        int percent = Math.round(scale * 100f);
        return Math.max(TIMETABLE_FONT_PERCENT_MIN, Math.min(TIMETABLE_FONT_PERCENT_MAX, percent));
    }

    private void setFontPercent(int percent) {
        int clamped = Math.max(TIMETABLE_FONT_PERCENT_MIN, Math.min(TIMETABLE_FONT_PERCENT_MAX, percent));
        getSharedPreferences(PREF_COURSE_STORAGE, MODE_PRIVATE)
                .edit().putFloat(KEY_TIMETABLE_FONT_SCALE, clamped / 100f).apply();
        updateFontPercentLabel(clamped);
    }

    private void updateFontPercentLabel(int percent) {
        if (tvFontPercent != null) {
            tvFontPercent.setText(percent + "%");
            int onSurface = UiStyleHelper.resolveOnSurfaceColor(this);
            tvFontPercent.setTextColor(onSurface);
            tvFontPercent.setBackground(makeRoundedSolid(ColorUtils.setAlphaComponent(onSurface, 28), 10));
        }
        if (tvFontPreview != null) {
            // 模拟课表单元格内课程名称字号（与 MainActivity 的 ~9sp * scale 一致）
            float scale = percent / 100f;
            tvFontPreview.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f * scale);
        }
    }

    private android.graphics.drawable.GradientDrawable makeRoundedSolid(int color, int radiusDp) {
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        gd.setCornerRadius(dp(radiusDp));
        gd.setColor(color);
        return gd;
    }

    // ==================== 首次启动标记 ====================

    private void markFirstLaunchCompleted() {
        getSharedPreferences(PREF_COURSE_STORAGE, MODE_PRIVATE)
                .edit().putBoolean(KEY_FIRST_LAUNCH_COMPLETED, true).apply();
    }

    public static boolean isFirstLaunchCompleted(android.content.Context context) {
        return context.getSharedPreferences(PREF_COURSE_STORAGE, android.content.Context.MODE_PRIVATE)
                .getBoolean(KEY_FIRST_LAUNCH_COMPLETED, false);
    }

    // ==================== 工具方法 ====================

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
