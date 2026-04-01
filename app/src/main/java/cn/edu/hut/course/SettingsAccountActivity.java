package cn.edu.hut.course;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.datepicker.MaterialDatePicker;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;

public class SettingsAccountActivity extends AppCompatActivity {

    private static final String PREF_COURSE_STORAGE = "course_storage";
    private static final String KEY_COURSES_JSON = "courses_json";
    private static final String TARGET_URL = "http://jwxt.hut.edu.cn/jsxsd/xskb/xskb_list.do?viweType=0";
    private static final String BASE_URL = "http://jwxt.hut.edu.cn";
    private static final String LOGIN_URL = BASE_URL + "/jsxsd/sso.jsp";
    private static final String SUCCESS_URL = BASE_URL + "/jsxsd/framework/xsMainV.htmlx";
    private static final String LOGIN_SUCCESS_PATH = "/jsxsd/framework/xsMainV.htmlx";

    private ActivityResultLauncher<Intent> browserLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_account);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        TextView tvStartDateSummary = findViewById(R.id.tvStartDateSummary);
        updateStartDateSummary(tvStartDateSummary);
        findViewById(R.id.itemSetStartDate).setOnClickListener(v -> showMaterialDatePicker(tvStartDateSummary));

        browserLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Intent data = result.getData();
                boolean loginSuccess = data.getBooleanExtra("login_success", false);
                if (loginSuccess) {
                    Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show();
                }
            }
        });

        findViewById(R.id.btnOpenJwxt).setOnClickListener(v -> {
            launchBrowserForLogin();
        });

        findViewById(R.id.btnExtractFromJwxt).setOnClickListener(v -> extractCourseWithFallback(null, true));

        findViewById(R.id.btnClearCurrent).setOnClickListener(v -> {
            Intent i = new Intent();
            i.putExtra("action", "clear_schedule_only");
            setResult(RESULT_OK, i);
            finish();
        });

        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            Intent i = new Intent();
            i.putExtra("action", "logout");
            setResult(RESULT_OK, i);
            finish();
        });
    }

    private void launchBrowserForLogin() {
        Intent intent = new Intent(this, BrowserActivity.class);
        intent.putExtra("url", CourseScraper.LOGIN_URL);
        intent.putExtra("autoCloseOnLoginSuccess", true);
        browserLauncher.launch(intent);
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
            Toast.makeText(this, "未登录或者登录信息失效", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "正在抓取课表...", Toast.LENGTH_SHORT).show();
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
                        attemptSilentLoginAndRetry();
                    } else {
                        runOnUiThread(() -> Toast.makeText(SettingsAccountActivity.this, "未登录或者登录信息失效", Toast.LENGTH_SHORT).show());
                    }
                    return;
                }

                final int finalCourseCount = courseCount;
                saveCoursesToLocal(courses);
                runOnUiThread(() -> {
                    Intent result = new Intent();
                    result.putExtra("action", "reload_courses");
                    setResult(RESULT_OK, result);
                    Toast.makeText(SettingsAccountActivity.this, "抓取成功，共" + finalCourseCount + "门课", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String msg) {
                if (allowSilentRetry) {
                    attemptSilentLoginAndRetry();
                } else {
                    runOnUiThread(() -> Toast.makeText(SettingsAccountActivity.this, "抓取失败: " + msg, Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private void attemptSilentLoginAndRetry() {
        Toast.makeText(this, "正在尝试自动校验登录...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String refreshedCookie = trySilentLoginAndGetCookie();
            if (refreshedCookie != null && !refreshedCookie.isEmpty()) {
                runOnUiThread(() -> extractCourseWithFallback(refreshedCookie, false));
            } else {
                runOnUiThread(() -> Toast.makeText(SettingsAccountActivity.this, "未登录或者登录信息失效", Toast.LENGTH_SHORT).show());
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
            verifyConn.setInstanceFollowRedirects(false);
            verifyConn.setConnectTimeout(8000);
            verifyConn.setReadTimeout(8000);
            verifyConn.setRequestProperty("User-Agent", "Mozilla/5.0");
            verifyConn.setRequestProperty("Cookie", newCookie);
            int code = verifyConn.getResponseCode();
            String location = verifyConn.getHeaderField("Location");

            if (code == HttpURLConnection.HTTP_OK) {
                return newCookie;
            }
            if (location != null && location.contains(LOGIN_SUCCESS_PATH)) {
                return newCookie;
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

    private void saveCoursesToLocal(List<Course> courses) {
        try {
            JSONArray arr = new JSONArray();
            for (Course c : courses) {
                JSONObject o = new JSONObject();
                o.put("name", c.name);
                o.put("teacher", c.teacher);
                o.put("dayOfWeek", c.dayOfWeek);
                o.put("startSection", c.startSection);
                o.put("location", c.location);
                o.put("isExperimental", c.isExperimental);
                o.put("isRemark", c.isRemark);
                JSONArray w = new JSONArray();
                if (c.weeks != null) {
                    for (int week : c.weeks) {
                        w.put(week);
                    }
                }
                o.put("weeks", w);
                arr.put(o);
            }
            getSharedPreferences(PREF_COURSE_STORAGE, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_COURSES_JSON, arr.toString())
                    .apply();
        } catch (Exception ignored) {
        }
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
