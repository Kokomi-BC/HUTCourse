package cn.edu.hut.course;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import cn.edu.hut.course.data.CourseStorageManager;

import org.json.JSONArray;

public class SettingsDataActivity extends AppCompatActivity {

    private static final String PREF_COURSE_STORAGE = "course_storage";
    private static final String BASE_URL = "http://jwxt.hut.edu.cn";
    private static final String TARGET_URL = BASE_URL + "/jsxsd/xskb/xskb_list.do?viweType=0";

    private TextView tvExportTableSummary;
    private TextView tvImportTableSummary;
    private TextView tvExportCookieSummary;
    private TextView tvImportCookieSummary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiStyleHelper.hideStatusBar(this);
        setContentView(R.layout.activity_settings_data);
        applyPageVisualStyle();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        UiStyleHelper.styleGlassToolbar(toolbar, this);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_rounded_24);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvExportTableSummary = findViewById(R.id.tvExportTableSummary);
        tvImportTableSummary = findViewById(R.id.tvImportTableSummary);
        tvExportCookieSummary = findViewById(R.id.tvExportCookieSummary);
        tvImportCookieSummary = findViewById(R.id.tvImportCookieSummary);

        findViewById(R.id.btnExportTable).setOnClickListener(v -> {
            exportTableToClipboard();
        });

        findViewById(R.id.btnImportTable).setOnClickListener(v -> {
            importTableFromClipboard();
        });

        findViewById(R.id.btnExportCookie).setOnClickListener(v -> {
            exportCookieToClipboard();
        });

        findViewById(R.id.btnImportCookie).setOnClickListener(v -> {
            importCookieFromClipboard();
        });

        refreshStatusSummary();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyPageVisualStyle();
        refreshStatusSummary();
    }

    private void applyPageVisualStyle() {
        View root = findViewById(R.id.rootSettingsData);
        if (root != null) {
            UiStyleHelper.applySecondaryPageBackground(root, this);
        }
        UiStyleHelper.applyGlassCards(findViewById(android.R.id.content), this);
    }

    private void refreshStatusSummary() {
        int count = getSavedCourseCount();
        if (tvExportTableSummary != null) {
            tvExportTableSummary.setText(count > 0 ? "当前可导出" + count + "门课程" : "当前无课表可导出");
        }
        if (tvImportTableSummary != null) {
            tvImportTableSummary.setText("从剪贴板读取课表数据");
        }
        boolean hasCookie = hasCookie();
        if (tvExportCookieSummary != null) {
            tvExportCookieSummary.setText(hasCookie ? "当前有登录Cookie，可导出" : "当前无可用Cookie");
        }
        if (tvImportCookieSummary != null) {
            tvImportCookieSummary.setText("从剪贴板导入Cookie");
        }
    }

    private int getSavedCourseCount() {
        return CourseStorageManager.countNonRemarkCourses(this);
    }

    private boolean hasCookie() {
        String cookie = CookieManager.getInstance().getCookie(TARGET_URL);
        if (cookie == null || cookie.isEmpty()) {
            cookie = CookieManager.getInstance().getCookie(BASE_URL);
        }
        return cookie != null && !cookie.trim().isEmpty();
    }

    private ClipboardManager clipboard() {
        return (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
    }

    private void exportTableToClipboard() {
        String json = CourseStorageManager.loadCoursesJson(this);
        if (json == null || json.trim().isEmpty() || "[]".equals(json.trim())) {
            Toast.makeText(this, "当前无课表可导出", Toast.LENGTH_SHORT).show();
            if (tvExportTableSummary != null) tvExportTableSummary.setText("导出失败：暂无课表数据");
            return;
        }
        clipboard().setPrimaryClip(ClipData.newPlainText("CourseTable", json));
        Toast.makeText(this, "课表已导出至剪贴板", Toast.LENGTH_SHORT).show();
        if (tvExportTableSummary != null) tvExportTableSummary.setText("导出成功：已复制到剪贴板");
    }

    private void importTableFromClipboard() {
        try {
            ClipboardManager cm = clipboard();
            if (!cm.hasPrimaryClip() || cm.getPrimaryClip() == null || cm.getPrimaryClip().getItemCount() <= 0) {
                Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show();
                if (tvImportTableSummary != null) tvImportTableSummary.setText("导入失败：剪贴板为空");
                return;
            }
            CharSequence text = cm.getPrimaryClip().getItemAt(0).getText();
            if (text == null) {
                Toast.makeText(this, "剪贴板无文本数据", Toast.LENGTH_SHORT).show();
                if (tvImportTableSummary != null) tvImportTableSummary.setText("导入失败：剪贴板无文本");
                return;
            }
            String json = text.toString();
            new JSONArray(json);

                CourseStorageManager.saveCoursesJson(this, json);

            Intent i = new Intent();
            i.putExtra("action", "reload_courses");
            setResult(RESULT_OK, i);

            Toast.makeText(this, "导入成功", Toast.LENGTH_SHORT).show();
            if (tvImportTableSummary != null) tvImportTableSummary.setText("导入成功：课表已更新");
            refreshStatusSummary();
        } catch (Exception e) {
            Toast.makeText(this, "导入失败，数据格式可能不正确", Toast.LENGTH_SHORT).show();
            if (tvImportTableSummary != null) tvImportTableSummary.setText("导入失败：数据格式不正确");
        }
    }

    private void exportCookieToClipboard() {
        String cookie = CookieManager.getInstance().getCookie(TARGET_URL);
        if (cookie == null || cookie.isEmpty()) {
            cookie = CookieManager.getInstance().getCookie(BASE_URL);
        }
        if (cookie == null || cookie.trim().isEmpty()) {
            Toast.makeText(this, "暂无Cookie", Toast.LENGTH_SHORT).show();
            if (tvExportCookieSummary != null) tvExportCookieSummary.setText("导出失败：暂无Cookie");
            return;
        }
        clipboard().setPrimaryClip(ClipData.newPlainText("Cookie", cookie));
        Toast.makeText(this, "Cookie已导出至剪贴板", Toast.LENGTH_SHORT).show();
        if (tvExportCookieSummary != null) tvExportCookieSummary.setText("导出成功：已复制到剪贴板");
    }

    private void importCookieFromClipboard() {
        try {
            ClipboardManager cm = clipboard();
            if (!cm.hasPrimaryClip() || cm.getPrimaryClip() == null || cm.getPrimaryClip().getItemCount() <= 0) {
                Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show();
                if (tvImportCookieSummary != null) tvImportCookieSummary.setText("导入失败：剪贴板为空");
                return;
            }
            CharSequence text = cm.getPrimaryClip().getItemAt(0).getText();
            if (text == null) {
                Toast.makeText(this, "剪贴板无文本数据", Toast.LENGTH_SHORT).show();
                if (tvImportCookieSummary != null) tvImportCookieSummary.setText("导入失败：剪贴板无文本");
                return;
            }
            String cookie = text.toString();
            CookieManager.getInstance().setCookie(BASE_URL, cookie);
            CookieManager.getInstance().flush();

            Intent i = new Intent();
            i.putExtra("action", "reload_courses");
            setResult(RESULT_OK, i);

            Toast.makeText(this, "Cookie导入成功", Toast.LENGTH_SHORT).show();
            if (tvImportCookieSummary != null) tvImportCookieSummary.setText("导入成功：登录信息已更新");
            refreshStatusSummary();
        } catch (Exception e) {
            Toast.makeText(this, "导入失败", Toast.LENGTH_SHORT).show();
            if (tvImportCookieSummary != null) tvImportCookieSummary.setText("导入失败");
        }
    }
}
