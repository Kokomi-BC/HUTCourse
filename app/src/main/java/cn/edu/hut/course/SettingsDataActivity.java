package cn.edu.hut.course;

import android.content.DialogInterface;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import cn.edu.hut.course.data.AgendaStorageManager;
import cn.edu.hut.course.data.CourseJsonCodec;
import cn.edu.hut.course.data.CourseStorageManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SettingsDataActivity extends AppCompatActivity {

    private TextView tvImportTableSummary;
    private LinearLayout containerTables;
    private TextView tvNoTableHint;

    private ActivityResultLauncher<String> exportFileLauncher;
    private ActivityResultLauncher<String[]> importFileLauncher;
    private ActivityResultLauncher<String> calendarPermissionLauncher;
    private String pendingExportJson;

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

        tvImportTableSummary = findViewById(R.id.tvImportTableSummary);
        containerTables = findViewById(R.id.containerTables);
        tvNoTableHint = findViewById(R.id.tvNoTableHint);

        exportFileLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/octet-stream"),
                uri -> {
                    if (uri != null && pendingExportJson != null) {
                        writeToFile(uri, pendingExportJson);
                        pendingExportJson = null;
                    }
                });

        importFileLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        processImportFile(uri);
                    }
                });

        findViewById(R.id.btnImportTable).setOnClickListener(v ->
                importFileLauncher.launch(new String[]{"application/octet-stream", "*/*"}));

        View btnImportCalendar = findViewById(R.id.btnImportCalendar);
        if (btnImportCalendar != null) {
            btnImportCalendar.setOnClickListener(v -> checkCalendarPermissionAndImport());
        }

        calendarPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        showCalendarImportDialog();
                    } else {
                        Toast.makeText(this, "需要日历权限才能导入", Toast.LENGTH_SHORT).show();
                    }
                });

        View ivAddTable = findViewById(R.id.ivAddTable);
        if (ivAddTable != null) {
            ivAddTable.setOnClickListener(v -> showAddTableDialog());
        }

        refreshAll();

        // Handle .hut file opened from external intent
        handleIncomingFileIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingFileIntent(intent);
    }

    private void handleIncomingFileIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_VIEW.equals(action)) return;
        Uri uri = intent.getData();
        if (uri == null) return;
        // Validate extension
        String fileName = null;
        try {
            android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                int nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (nameIdx >= 0 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIdx);
                }
                cursor.close();
            }
        } catch (Exception ignored) {}
        if (fileName != null && !fileName.toLowerCase(Locale.ROOT).endsWith(".hut")) {
            Toast.makeText(this, "仅支持 .hut 格式的课表文件", Toast.LENGTH_SHORT).show();
            return;
        }
        processImportFile(uri);
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyPageVisualStyle();
        refreshAll();
    }

    private void applyPageVisualStyle() {
        View root = findViewById(R.id.rootSettingsData);
        if (root != null) {
            UiStyleHelper.applySecondaryPageBackground(root, this);
        }
        UiStyleHelper.applyGlassCards(findViewById(android.R.id.content), this);
    }

    // ==================== Table List ====================

    private void refreshAll() {
        refreshTableCards();
        refreshStatusSummary();
    }

    /** Show ALL table cards. Single-click switches to that table. */
    private void refreshTableCards() {
        containerTables.removeAllViews();
        List<CourseTable> tables = CourseStorageManager.readAllCourseTables(this);
        long activeId = CourseStorageManager.getActiveTableId(this);

        if (tables.isEmpty()) {
            tvNoTableHint.setVisibility(View.VISIBLE);
            containerTables.setVisibility(View.GONE);
            return;
        }

        tvNoTableHint.setVisibility(View.GONE);
        containerTables.setVisibility(View.VISIBLE);

        for (CourseTable t : tables) {
            boolean isActive = (t.id == activeId);
            View card = buildTableCard(t, isActive);
            containerTables.addView(card);
        }
    }

    // ==================== Card View ====================

    private View buildTableCard(CourseTable table, boolean isActive) {
        int colorSurfaceContainer = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainer, 0xFFF0F4F8);
        int colorPrimary = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, 0xFF3366CC);
        int colorOnSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFF1A1C1E);
        int colorOnSurfaceVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF5F6368);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setClickable(true);
        card.setFocusable(true);
        int pad = dpToPx(16);
        card.setPadding(pad, dpToPx(12), pad, dpToPx(12));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dpToPx(24));
        bg.setColor(colorSurfaceContainer);
        card.setBackground(bg);

        card.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        ((LinearLayout.LayoutParams) card.getLayoutParams()).bottomMargin = dpToPx(8);

        // Indicator
        TextView indicator = new TextView(this);
        indicator.setText(isActive ? "● " : "○ ");
        indicator.setTextSize(16);
        indicator.setTextColor(isActive ? colorPrimary : colorOnSurfaceVariant);
        card.addView(indicator);

        // Info
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvName = new TextView(this);
        tvName.setText(safeTableName(table));
        tvName.setTextSize(16);
        tvName.setTextColor(colorOnSurface);
        info.addView(tvName);

        int courseCount = CourseStorageManager.countNonRemarkCoursesForTable(this, table.id);
        int agendaCount = AgendaStorageManager.loadAllAgendasForTable(this, table.id).size();
        TextView tvSum = new TextView(this);
        tvSum.setText(courseCount + " 门课程 · " + agendaCount + " 项日程");
        tvSum.setTextSize(12);
        tvSum.setTextColor(colorOnSurfaceVariant);
        info.addView(tvSum);

        card.addView(info);

        // Hint icon
        TextView hint = new TextView(this);
        hint.setText("⋮");
        hint.setTextSize(20);
        hint.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
        hint.setTextColor(colorOnSurfaceVariant);
        hint.setClickable(true);
        hint.setFocusable(true);
        android.util.TypedValue outValue = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true);
        hint.setBackgroundResource(outValue.resourceId);
        hint.setOnClickListener(v -> v.post(() -> showTableMgmtSheet(table)));
        card.addView(hint);

        // --- Click → switch to this table directly ---
        final long tableId = table.id;
        card.setOnClickListener(v -> v.post(() -> {
            long activeId = CourseStorageManager.getActiveTableId(SettingsDataActivity.this);
            if (tableId != activeId) {
                switchToTable(tableId);
            } else {
                Toast.makeText(SettingsDataActivity.this, "已是当前课表", Toast.LENGTH_SHORT).show();
            }
        }));

        // --- Long-press → management menu ---
        card.setOnLongClickListener(v -> {
            v.post(() -> showTableMgmtSheet(table));
            return true;
        });

        return card;
    }

    // ==================== BottomSheet: Table Switcher ====================

    private void showTableSwitcherSheet() {
        if (isFinishing() || isDestroyed()) return;
        int colorOnSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFF1A1C1E);

        List<CourseTable> tables = CourseStorageManager.readAllCourseTables(this);
        long activeId = CourseStorageManager.getActiveTableId(this);

        BottomSheetDialog sheet = new BottomSheetDialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dpToPx(8), 0, dpToPx(16));

        TextView title = new TextView(this);
        title.setText("切换课表");
        title.setTextSize(18);
        title.setPadding(dpToPx(20), dpToPx(12), dpToPx(20), dpToPx(8));
        title.setTextColor(colorOnSurface);
        root.addView(title);

        for (CourseTable t : tables) {
            View row = buildSheetTableRow(t, t.id == activeId,
                    () -> {
                        sheet.dismiss();
                        if (t.id != activeId) {
                            switchToTable(t.id);
                        }
                    },
                    () -> {
                        sheet.dismiss();
                        showTableMgmtSheet(t);
                    });
            root.addView(row);
        }

        sheet.setContentView(root);
        sheet.show();
    }

    private View buildSheetTableRow(CourseTable table, boolean isActive,
                                    Runnable onClick, Runnable onLongClick) {
        int colorPrimary = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, 0xFF3366CC);
        int colorOnSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFF1A1C1E);
        int colorOnSurfaceVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF5F6368);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dpToPx(20);
        row.setPadding(pad, dpToPx(10), dpToPx(12), dpToPx(10));
        row.setClickable(true);
        row.setFocusable(true);
        // set layout params to math parent
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView ind = new TextView(this);
        ind.setText(isActive ? "● " : "○ ");
        ind.setTextSize(14);
        ind.setTextColor(isActive ? colorPrimary : colorOnSurfaceVariant);
        row.addView(ind);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvName = new TextView(this);
        tvName.setText(safeTableName(table));
        tvName.setTextSize(15);
        tvName.setTextColor(colorOnSurface);
        info.addView(tvName);

        int cCount = CourseStorageManager.countNonRemarkCoursesForTable(this, table.id);
        int aCount = AgendaStorageManager.loadAllAgendasForTable(this, table.id).size();
        TextView tvSum = new TextView(this);
        tvSum.setText(cCount + " 门课程 · " + aCount + " 项日程");
        tvSum.setTextSize(12);
        tvSum.setTextColor(colorOnSurfaceVariant);
        info.addView(tvSum);

        row.addView(info);
        row.setOnClickListener(v -> onClick.run());
        row.setOnLongClickListener(v -> { onLongClick.run(); return true; });

        return row;
    }

    // ==================== BottomSheet: Management Menu ====================

    private void showTableMgmtSheet(CourseTable table) {
        if (isFinishing() || isDestroyed()) return;
        int colorOnSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFF1A1C1E);

        BottomSheetDialog sheet = new BottomSheetDialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dpToPx(8), 0, dpToPx(16));

        TextView title = new TextView(this);
        title.setText(safeTableName(table));
        title.setTextSize(18);
        title.setPadding(dpToPx(20), dpToPx(12), dpToPx(20), dpToPx(8));
        title.setTextColor(colorOnSurface);
        root.addView(title);

        root.addView(buildMgmtRow("分享", () -> {
            sheet.dismiss();
            showExportDialog(table);
        }));
        root.addView(buildMgmtRow("重命名", () -> {
            sheet.dismiss();
            showRenameDialog(table);
        }));
        root.addView(buildMgmtRow("删除", () -> {
            sheet.dismiss();
            showDeleteConfirmDialog(table);
        }));

        sheet.setContentView(root);
        sheet.show();
    }

    private View buildMgmtRow(String label, Runnable action) {
        int colorOnSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFF1A1C1E);

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(16);
        tv.setPadding(dpToPx(20), dpToPx(14), dpToPx(20), dpToPx(14));
        tv.setTextColor(colorOnSurface);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        tv.setClickable(true);
        tv.setFocusable(true);
        tv.setOnClickListener(v -> action.run());
        return tv;
    }

    // ==================== Switch Table ====================

    private void switchToTable(long tableId) {
        CourseStorageManager.setActiveTableId(this, tableId);
        refreshAll();
        Intent i = new Intent();
        i.putExtra("action", "reload_courses");
        setResult(RESULT_OK, i);
        Toast.makeText(this, "已切换课表", Toast.LENGTH_SHORT).show();
    }

    // ==================== Rename ====================

    private void showAddTableDialog() {
        if (isFinishing() || isDestroyed()) return;
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("输入新课表名称");
        int pad = dpToPx(20);
        input.setPadding(pad, dpToPx(16), pad, dpToPx(16));
        input.setBackground(null); // Simple look

        new MaterialAlertDialogBuilder(new androidx.appcompat.view.ContextThemeWrapper(this, com.google.android.material.R.style.Theme_Material3_DayNight))
                .setTitle("新增课表")
                .setView(input)
                .setPositiveButton("创建", (d, w) -> {
                    String newName = input.getText().toString().trim();
                    if (newName.isEmpty()) {
                        newName = "新课表 " + (CourseStorageManager.readAllCourseTables(this).size() + 1);
                    }
                    CourseTable newTable = new CourseTable();
                    newTable.name = newName;
                    long newId = CourseStorageManager.insertCourseTable(this, newTable);
                    if (newId != -1) {
                        CourseStorageManager.setActiveTableId(this, newId);
                        refreshAll();
                        Intent i = new Intent();
                        i.putExtra("action", "reload_courses");
                        setResult(RESULT_OK, i);
                        Toast.makeText(this, "建表成功并已切换: " + newName, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showRenameDialog(CourseTable table) {
        if (isFinishing() || isDestroyed()) return;
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(table.name);
        input.setSelection(input.getText().length());
        int pad = dpToPx(20);
        input.setPadding(pad, dpToPx(16), pad, dpToPx(16));
        input.setBackground(null);

        new MaterialAlertDialogBuilder(new androidx.appcompat.view.ContextThemeWrapper(this, com.google.android.material.R.style.Theme_Material3_DayNight))
                .setTitle("重命名课表")
                .setView(input)
                .setPositiveButton("确定", (d, w) -> {
                    String newName = input.getText().toString().trim();
                    if (newName.isEmpty()) {
                        Toast.makeText(this, "名称不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    table.name = newName;
                    CourseStorageManager.updateCourseTable(this, table);
                    refreshAll();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ==================== Delete ====================

    private void showDeleteConfirmDialog(CourseTable table) {
        if (isFinishing() || isDestroyed()) return;
        List<CourseTable> allTables = CourseStorageManager.readAllCourseTables(this);
        if (allTables.size() <= 1) {
            Toast.makeText(this, "至少保留一个课表", Toast.LENGTH_SHORT).show();
            return;
        }
        new MaterialAlertDialogBuilder(new androidx.appcompat.view.ContextThemeWrapper(this, com.google.android.material.R.style.Theme_Material3_DayNight))
                .setTitle("删除课表")
                .setMessage("确定要删除课表「" + safeTableName(table) + "」吗？\n该课表中的所有课程和日程数据将被永久删除。")
                .setPositiveButton("删除", (d, w) -> {
                    CourseStorageManager.deleteCourseTable(this, table.id);
                    refreshAll();
                    Intent i = new Intent();
                    i.putExtra("action", "reload_courses");
                    setResult(RESULT_OK, i);
                    Toast.makeText(this, "课表已删除", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ==================== Status Summary ====================

    private void refreshStatusSummary() {
        if (tvImportTableSummary != null) {
            tvImportTableSummary.setText("从文件导入课表数据");
        }
    }

    // ==================== Export (Share) ====================

    private void showExportDialog(CourseTable table) {
        if (isFinishing() || isDestroyed()) return;
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dpToPx(24), dpToPx(16), dpToPx(24), 0);

        int courseCount = CourseStorageManager.countNonRemarkCoursesForTable(this, table.id);
        CheckBox cbCourses = new CheckBox(this);
        cbCourses.setText("课表数据（" + courseCount + " 门课程）");
        cbCourses.setChecked(true);
        container.addView(cbCourses);

        int agendaCount = AgendaStorageManager.loadAllAgendasForTable(this, table.id).size();
        CheckBox cbAgendas = new CheckBox(this);
        cbAgendas.setText("日程数据（" + agendaCount + " 项日程）");
        cbAgendas.setChecked(true);
        container.addView(cbAgendas);

        boolean hasProfile = !table.profileName.isEmpty() || !table.profileStudentId.isEmpty()
                || !table.profileClassName.isEmpty() || !table.profileCollege.isEmpty();
        CheckBox cbProfile = new CheckBox(this);
        cbProfile.setText("个人信息" + (hasProfile ? "（" + (table.profileName.isEmpty() ? "-" : table.profileName) + "）" : "（无）"));
        cbProfile.setChecked(hasProfile);
        container.addView(cbProfile);

        new MaterialAlertDialogBuilder(new androidx.appcompat.view.ContextThemeWrapper(this, com.google.android.material.R.style.Theme_Material3_DayNight))
                .setTitle("分享课表：" + safeTableName(table))
                .setView(container)
                .setPositiveButton("导出到文件", (d, w) -> {
                    boolean exportCourses = cbCourses.isChecked();
                    boolean exportAgendas = cbAgendas.isChecked();
                    boolean exportProfile = cbProfile.isChecked();
                    if (!exportCourses && !exportAgendas) {
                        Toast.makeText(this, "请至少选择一项导出内容", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    exportToFile(table, exportCourses, exportAgendas, exportProfile);
                })
                .setNeutralButton("系统分享", (d, w) -> {
                    boolean exportCourses = cbCourses.isChecked();
                    boolean exportAgendas = cbAgendas.isChecked();
                    boolean exportProfile = cbProfile.isChecked();
                    if (!exportCourses && !exportAgendas) {
                        Toast.makeText(this, "请至少选择一项导出内容", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    shareViaSystem(table, exportCourses, exportAgendas, exportProfile);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void exportToFile(CourseTable table, boolean includeCourses, boolean includeAgendas, boolean includeProfile) {
        try {
            JSONObject root = new JSONObject();
            root.put("version", 1);
            root.put("type", "hut_course_export");
            root.put("exportTime", System.currentTimeMillis());
            root.put("tableName", safeTableName(table));

            if (includeCourses) {
                String json = CourseStorageManager.loadCoursesJsonForTable(this, table.id);
                root.put("courses", new JSONArray(json != null ? json : "[]"));
            } else {
                root.put("courses", new JSONArray());
            }

            if (includeAgendas) {
                List<Agenda> agendas = AgendaStorageManager.loadAllAgendasForTable(this, table.id);
                JSONArray arr = new JSONArray();
                for (Agenda a : agendas) {
                    JSONObject ao = new JSONObject();
                    ao.put("title", a.title != null ? a.title : "");
                    ao.put("description", a.description != null ? a.description : "");
                    ao.put("location", a.location != null ? a.location : "");
                    ao.put("date", a.date != null ? a.date : "");
                    ao.put("startMinute", a.startMinute);
                    ao.put("endMinute", a.endMinute);
                    ao.put("priority", a.priority);
                    ao.put("renderColor", a.renderColor);
                    ao.put("repeatRule", a.repeatRule != null ? a.repeatRule : "none");
                    ao.put("monthlyStrategy", a.monthlyStrategy != null ? a.monthlyStrategy : "skip");
                    ao.put("createdAt", a.createdAt);
                    ao.put("updatedAt", a.updatedAt);
                    arr.put(ao);
                }
                root.put("agendas", arr);
            } else {
                root.put("agendas", new JSONArray());
            }

            if (includeProfile) {
                JSONObject profileObj = new JSONObject();
                profileObj.put("name", table.profileName != null ? table.profileName : "");
                profileObj.put("studentId", table.profileStudentId != null ? table.profileStudentId : "");
                profileObj.put("className", table.profileClassName != null ? table.profileClassName : "");
                profileObj.put("college", table.profileCollege != null ? table.profileCollege : "");
                root.put("profile", profileObj);
            }

            pendingExportJson = root.toString(2);
            String safeName = safeTableName(table).replaceAll("[\\\\/:*?\"<>|]", "_");
            String filename = safeName + ".hut";
            exportFileLauncher.launch(filename);
        } catch (Exception e) {
            Toast.makeText(this, "导出失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void writeToFile(Uri uri, String content) {
        try {
            OutputStream os = getContentResolver().openOutputStream(uri);
            if (os != null) {
                os.write(content.getBytes("UTF-8"));
                os.close();
                Toast.makeText(this, "导出成功", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "写入文件失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void shareViaSystem(CourseTable table, boolean includeCourses, boolean includeAgendas, boolean includeProfile) {
        try {
            JSONObject root = new JSONObject();
            root.put("version", 1);
            root.put("type", "hut_course_export");
            root.put("exportTime", System.currentTimeMillis());
            root.put("tableName", safeTableName(table));

            if (includeCourses) {
                String json = CourseStorageManager.loadCoursesJsonForTable(this, table.id);
                root.put("courses", new JSONArray(json != null ? json : "[]"));
            } else {
                root.put("courses", new JSONArray());
            }

            if (includeAgendas) {
                List<Agenda> agendas = AgendaStorageManager.loadAllAgendasForTable(this, table.id);
                JSONArray arr = new JSONArray();
                for (Agenda a : agendas) {
                    JSONObject ao = new JSONObject();
                    ao.put("title", a.title != null ? a.title : "");
                    ao.put("description", a.description != null ? a.description : "");
                    ao.put("location", a.location != null ? a.location : "");
                    ao.put("date", a.date != null ? a.date : "");
                    ao.put("startMinute", a.startMinute);
                    ao.put("endMinute", a.endMinute);
                    ao.put("priority", a.priority);
                    ao.put("renderColor", a.renderColor);
                    ao.put("repeatRule", a.repeatRule != null ? a.repeatRule : "none");
                    ao.put("monthlyStrategy", a.monthlyStrategy != null ? a.monthlyStrategy : "skip");
                    ao.put("createdAt", a.createdAt);
                    ao.put("updatedAt", a.updatedAt);
                    arr.put(ao);
                }
                root.put("agendas", arr);
            } else {
                root.put("agendas", new JSONArray());
            }

            if (includeProfile) {
                JSONObject profileObj = new JSONObject();
                profileObj.put("name", table.profileName != null ? table.profileName : "");
                profileObj.put("studentId", table.profileStudentId != null ? table.profileStudentId : "");
                profileObj.put("className", table.profileClassName != null ? table.profileClassName : "");
                profileObj.put("college", table.profileCollege != null ? table.profileCollege : "");
                root.put("profile", profileObj);
            }

            // Write to temp file in cache directory
            String safeName = safeTableName(table).replaceAll("[\\\\/:*?\"<>|]", "_");
            java.io.File cacheDir = new java.io.File(getCacheDir(), "share");
            cacheDir.mkdirs();
            java.io.File shareFile = new java.io.File(cacheDir, safeName + ".hut");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(shareFile);
            fos.write(root.toString(2).getBytes("UTF-8"));
            fos.close();

            Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    shareFile);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/octet-stream");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "分享课表"));
        } catch (Exception e) {
            Toast.makeText(this, "分享失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== Import ====================

    private void processImportFile(Uri uri) {
        try {
            // Validate file extension
            String fileName = null;
            try {
                android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                if (cursor != null) {
                    int nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIdx >= 0 && cursor.moveToFirst()) {
                        fileName = cursor.getString(nameIdx);
                    }
                    cursor.close();
                }
            } catch (Exception ignored) {}
            if (fileName != null && !fileName.toLowerCase(Locale.ROOT).endsWith(".hut")) {
                Toast.makeText(this, "仅支持 .hut 格式的课表文件", Toast.LENGTH_SHORT).show();
                return;
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(getContentResolver().openInputStream(uri), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append("\n");
            reader.close();
            String content = sb.toString().trim();
            if (content.isEmpty()) {
                Toast.makeText(this, "文件为空", Toast.LENGTH_SHORT).show();
                return;
            }

            JSONObject root = new JSONObject(content);
            if (!"hut_course_export".equals(root.optString("type", ""))) {
                Toast.makeText(this, "文件格式不正确：不是有效的课表导出文件", Toast.LENGTH_SHORT).show();
                return;
            }
            if (root.optInt("version", 0) < 1) {
                Toast.makeText(this, "文件版本不支持", Toast.LENGTH_SHORT).show();
                return;
            }

            String tableName = root.optString("tableName", "");
            if (tableName.isEmpty()) tableName = "导入课表";
            final String finalTableName = tableName;

            final JSONArray coursesArr = root.optJSONArray("courses");
            final JSONArray agendasArr = root.optJSONArray("agendas");
            final JSONObject profileObj = root.optJSONObject("profile");
            final int fileCourses = (coursesArr != null) ? coursesArr.length() : 0;
            final int fileAgendas = (agendasArr != null) ? agendasArr.length() : 0;

            if (fileCourses == 0 && fileAgendas == 0) {
                Toast.makeText(this, "文件中没有课表或日程数据", Toast.LENGTH_SHORT).show();
                return;
            }

            // Show preview dialog with selective import
            showImportPreviewDialog(finalTableName, countCourses -> countCourses > 0, countAgendas -> countAgendas > 0,
                    fileCourses, fileAgendas, coursesArr, agendasArr, profileObj);

        } catch (Exception e) {
            Toast.makeText(this, "读取文件失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showImportPreviewDialog(String tableName, java.util.function.Predicate<Integer> checkCourses, java.util.function.Predicate<Integer> checkAgendas, int fileCourses, int fileAgendas, JSONArray coursesArr, JSONArray agendasArr, JSONObject profileObj) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dpToPx(24), dpToPx(16), dpToPx(24), 0);

        final CheckBox cbCourses = new CheckBox(this);
        cbCourses.setText("课表数据（" + fileCourses + " 门课程）");
        cbCourses.setChecked(checkCourses.test(fileCourses));
        cbCourses.setEnabled(fileCourses > 0);
        container.addView(cbCourses);

        final CheckBox cbAgendas = new CheckBox(this);
        cbAgendas.setText("日程数据（" + fileAgendas + " 项日程）");
        cbAgendas.setChecked(checkAgendas.test(fileAgendas));
        cbAgendas.setEnabled(fileAgendas > 0);
        container.addView(cbAgendas);

        final boolean hasProfile = profileObj != null;
        final CheckBox cbProfile = new CheckBox(this);
        String profileLabel = "个人信息";
        if (hasProfile) {
            String pn = profileObj.optString("name", "");
            profileLabel += "（" + (pn.isEmpty() ? "-" : pn) + "）";
        } else {
            profileLabel += "（无）";
        }
        cbProfile.setText(profileLabel);
        cbProfile.setChecked(hasProfile);
        container.addView(cbProfile);

        // If only one type has data, auto-select it and show a simpler message
        String title = "导入预览";
        if (fileCourses > 0 && fileAgendas == 0) {
            title = "将导入 " + fileCourses + " 门课程";
        } else if (fileAgendas > 0 && fileCourses == 0) {
            title = "将导入 " + fileAgendas + " 项日程";
        }

        new MaterialAlertDialogBuilder(new androidx.appcompat.view.ContextThemeWrapper(this, com.google.android.material.R.style.Theme_Material3_DayNight))
                .setTitle(title)
                .setView(container)
                .setPositiveButton("新增为新课表", (d, w) -> {
                    if (!cbCourses.isChecked() && !cbAgendas.isChecked()) {
                        Toast.makeText(this, "请至少选择一项导入内容", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    JSONObject profile = cbProfile.isChecked() ? profileObj : null;
                    doImport(tableName, cbCourses.isChecked() ? coursesArr : null, cbAgendas.isChecked() ? agendasArr : null, true, profile);
                })
                .setNeutralButton("覆盖当前展示的课表", (d, w) -> {
                    if (!cbCourses.isChecked() && !cbAgendas.isChecked()) {
                        Toast.makeText(this, "请至少选择一项导入内容", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    JSONObject profile = cbProfile.isChecked() ? profileObj : null;
                    doImport(tableName, cbCourses.isChecked() ? coursesArr : null, cbAgendas.isChecked() ? agendasArr : null, false, profile);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void doImport(String tableName, JSONArray coursesArr, JSONArray agendasArr, boolean isNewTable, JSONObject profileObj) {
        try {
            long targetTableId;
            if (isNewTable) {
                CourseTable newTable = new CourseTable();
                newTable.name = tableName;
                newTable.createTime = System.currentTimeMillis();
                targetTableId = CourseStorageManager.insertCourseTable(this, newTable);
            } else {
                targetTableId = CourseStorageManager.getActiveTableId(this);
                if (targetTableId == -1) {
                    Toast.makeText(this, "无活动课表，无法覆盖", Toast.LENGTH_SHORT).show();
                    return;
                }
                CourseTable activeTable = null;
                for (CourseTable t : CourseStorageManager.readAllCourseTables(this)) {
                    if (t.id == targetTableId) {
                        activeTable = t;
                        break;
                    }
                }
                if (activeTable != null && tableName != null && !tableName.isEmpty() && !tableName.equals("导入课表")) {
                    activeTable.name = tableName;
                    CourseStorageManager.updateCourseTable(this, activeTable);
                }
            }

            // Save profile if provided
            if (profileObj != null) {
                String pn = profileObj.optString("name", "");
                String pid = profileObj.optString("studentId", "");
                String pcn = profileObj.optString("className", "");
                String pc = profileObj.optString("college", "");
                CourseStorageManager.saveProfileForTable(this, targetTableId, pn, pid, pcn, pc);
            }

            int importedCourses = 0;
            int importedAgendas = 0;

            if (coursesArr != null && coursesArr.length() > 0) {
                List<Course> courses = CourseJsonCodec.fromJson(coursesArr.toString());
                CourseStorageManager.overwriteCoursesForTable(this, targetTableId, courses);
                importedCourses = courses.size();
            }

            if (agendasArr != null && agendasArr.length() > 0) {
                List<Agenda> agendas = new java.util.ArrayList<>();
                for (int i = 0; i < agendasArr.length(); i++) {
                    JSONObject ao = agendasArr.getJSONObject(i);
                    Agenda a = new Agenda();
                    a.title = ao.optString("title", "");
                    a.description = ao.optString("description", "");
                    a.location = ao.optString("location", "");
                    a.date = ao.optString("date", "");
                    a.startMinute = ao.optInt("startMinute", 480);
                    a.endMinute = ao.optInt("endMinute", 540);
                    a.priority = ao.optInt("priority", Agenda.PRIORITY_LOW);
                    a.renderColor = ao.optInt("renderColor", 0);
                    a.repeatRule = ao.optString("repeatRule", "none");
                    a.monthlyStrategy = ao.optString("monthlyStrategy", "skip");
                    a.createdAt = ao.optLong("createdAt", System.currentTimeMillis());
                    a.updatedAt = ao.optLong("updatedAt", System.currentTimeMillis());
                    agendas.add(a);
                }
                AgendaStorageManager.overwriteAgendasForTable(this, targetTableId, agendas);
                importedAgendas = agendas.size();
            }

            if (isNewTable) {
                CourseStorageManager.setActiveTableId(this, targetTableId);
            }
            refreshAll();

            Intent i = new Intent();
            i.putExtra("action", "reload_courses");
            setResult(RESULT_OK, i);

            Toast.makeText(this, "导入成功：" + importedCourses + "门课程，" + importedAgendas + "项日程", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this, "导入失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== Helpers ====================

    private String safeTableName(CourseTable table) {
        if (table == null) return "未命名课表";
        String name = table.name;
        if (name == null || name.trim().isEmpty()) return "未命名课表";
        return name.trim();
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    // ==================== Calendar Import ====================

    private void checkCalendarPermissionAndImport() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_CALENDAR)
                == PackageManager.PERMISSION_GRANTED) {
            showCalendarImportDialog();
        } else {
            calendarPermissionLauncher.launch(android.Manifest.permission.WRITE_CALENDAR);
        }
    }

    private void showCalendarImportDialog() {
        if (isFinishing() || isDestroyed()) return;
        long activeId = CourseStorageManager.getActiveTableId(this);
        CourseTable activeTable = null;
        for (CourseTable t : CourseStorageManager.readAllCourseTables(this)) {
            if (t.id == activeId) { activeTable = t; break; }
        }
        if (activeTable == null) {
            Toast.makeText(this, "没有活动课表", Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(8));

        int courseCount = CourseStorageManager.countNonRemarkCoursesForTable(this, activeId);
        final CheckBox cbCourses = new CheckBox(this);
        cbCourses.setText("课程信息（" + courseCount + " 门课程）");
        cbCourses.setChecked(true);
        container.addView(cbCourses);

        int agendaCount = AgendaStorageManager.loadAllAgendasForTable(this, activeId).size();
        final CheckBox cbAgendas = new CheckBox(this);
        cbAgendas.setText("日程信息（" + agendaCount + " 项日程）");
        cbAgendas.setChecked(true);
        container.addView(cbAgendas);

        // Date range label + picker button
        final TextView tvRangeLabel = new TextView(this);
        tvRangeLabel.setTextSize(13);
        tvRangeLabel.setPadding(0, dpToPx(12), 0, dpToPx(4));
        tvRangeLabel.setText("导出范围：全部时间");
        container.addView(tvRangeLabel);

        final long[] rangeStartMs = {System.currentTimeMillis()};
        final long[] rangeEndMs = {System.currentTimeMillis() + 180L * 24 * 3600 * 1000L}; // ~6 months default
        final boolean[] rangeSelected = {false};

        com.google.android.material.button.MaterialButton btnPickRange = new com.google.android.material.button.MaterialButton(this);
        btnPickRange.setText("选择时间范围");
        btnPickRange.setTextSize(14);
        btnPickRange.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        btnPickRange.setOnClickListener(v -> {
            com.google.android.material.datepicker.MaterialDatePicker<androidx.core.util.Pair<Long, Long>> picker =
                    com.google.android.material.datepicker.MaterialDatePicker.Builder.dateRangePicker()
                            .setTitleText("选择导出范围")
                            .setSelection(new androidx.core.util.Pair<>(rangeStartMs[0], rangeEndMs[0]))
                            .setTheme(R.style.ThemeOverlay_MyApplication_MaterialCalendar)
                            .setInputMode(com.google.android.material.datepicker.MaterialDatePicker.INPUT_MODE_CALENDAR)
                            .build();
            picker.addOnPositiveButtonClickListener(selection -> {
                if (selection != null) {
                    rangeStartMs[0] = selection.first;
                    rangeEndMs[0] = selection.second;
                    rangeSelected[0] = true;
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());
                    tvRangeLabel.setText("导出范围：" + sdf.format(new java.util.Date(rangeStartMs[0]))
                            + " — " + sdf.format(new java.util.Date(rangeEndMs[0])));
                }
            });
            picker.show(getSupportFragmentManager(), "calendar_range_picker");
        });
        container.addView(btnPickRange);

        final CourseTable finalTable = activeTable;
        new MaterialAlertDialogBuilder(new androidx.appcompat.view.ContextThemeWrapper(this, com.google.android.material.R.style.Theme_Material3_DayNight))
                .setTitle("导入到系统日历：" + safeTableName(finalTable))
                .setView(container)
                .setPositiveButton("开始导入", (d, w) -> {
                    if (!cbCourses.isChecked() && !cbAgendas.isChecked()) {
                        Toast.makeText(this, "请至少选择一项导入内容", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    performCalendarImport(finalTable, cbCourses.isChecked(), cbAgendas.isChecked(),
                            rangeStartMs[0], rangeEndMs[0], rangeSelected[0]);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void performCalendarImport(CourseTable table, boolean importCourses, boolean importAgendas,
                                        long rangeStartMs, long rangeEndMs, boolean rangeSelected) {
        new Thread(() -> {
            int totalEvents = 0;

            try {
                long calId = getOrCreateDefaultCalendarId();

                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());
                String rangeStr = rangeSelected
                        ? (sdf.format(new java.util.Date(rangeStartMs)) + " — " + sdf.format(new java.util.Date(rangeEndMs)))
                        : "全部时间";

                if (importCourses) {
                    List<Course> courses = CourseStorageManager.loadCoursesForTable(this, table.id);
                    for (Course c : courses) {
                        if (c == null || c.isRemark) continue;
                        if (c.weeks == null) continue;
                        for (int week : c.weeks) {
                            // Calculate date for this course occurrence
                            java.util.Calendar cal = getSemesterStartCalendar();
                            while (cal.get(java.util.Calendar.DAY_OF_WEEK) != java.util.Calendar.MONDAY) {
                                cal.add(java.util.Calendar.DAY_OF_MONTH, -1);
                            }
                            cal.add(java.util.Calendar.WEEK_OF_YEAR, week - 1);
                            cal.add(java.util.Calendar.DAY_OF_MONTH, c.dayOfWeek - 1);

                            int slotIndex = Math.max(0, Math.min(4, (c.startSection - 1) / 2));
                            int startMinute = new int[]{8 * 60, 10 * 60, 14 * 60, 16 * 60, 19 * 60}[slotIndex];
                            int endMinute = new int[]{9 * 60 + 40, 11 * 60 + 40, 15 * 60 + 40, 17 * 60 + 40, 20 * 60 + 40}[slotIndex];

                            long startMillis = cal.getTimeInMillis() + startMinute * 60000L;
                            long endMillis = cal.getTimeInMillis() + endMinute * 60000L;

                            // Filter by date range
                            if (rangeSelected && (startMillis < rangeStartMs || startMillis > rangeEndMs)) continue;

                            ContentValues values = new ContentValues();
                            values.put(CalendarContract.Events.CALENDAR_ID, calId);
                            values.put(CalendarContract.Events.TITLE, c.name + (c.isExperimental ? " [实验]" : ""));
                            values.put(CalendarContract.Events.DESCRIPTION,
                                    "教师：" + (c.teacher != null ? c.teacher : "") +
                                    "\n地点：" + (c.location != null ? c.location : ""));
                            values.put(CalendarContract.Events.EVENT_LOCATION, c.location != null ? c.location : "");
                            values.put(CalendarContract.Events.DTSTART, startMillis);
                            values.put(CalendarContract.Events.DTEND, endMillis);
                            values.put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().getID());
                            try {
                                getContentResolver().insert(CalendarContract.Events.CONTENT_URI, values);
                                totalEvents++;
                            } catch (Exception ignored) {}
                        }
                    }
                }

                if (importAgendas) {
                    List<Agenda> agendas = AgendaStorageManager.loadAllAgendasForTable(this, table.id);
                    for (Agenda a : agendas) {
                        if (a == null) continue;
                        // Use AgendaStorageManager to parse date properly
                        java.util.Calendar parsedDate = AgendaStorageManager.parseDateOrNull(a.date);
                        if (parsedDate == null) continue;
                        long startMillis = parsedDate.getTimeInMillis() + a.startMinute * 60000L;
                        long endMillis = parsedDate.getTimeInMillis() + Math.max(a.startMinute, a.endMinute) * 60000L;
                        if (endMillis <= startMillis) endMillis = startMillis + 3600000L;

                        // Filter by date range
                        if (rangeSelected && (startMillis < rangeStartMs || startMillis > rangeEndMs)) continue;

                        ContentValues values = new ContentValues();
                        values.put(CalendarContract.Events.CALENDAR_ID, calId);
                        values.put(CalendarContract.Events.TITLE, a.title != null ? a.title : "日程");
                        values.put(CalendarContract.Events.DESCRIPTION, a.description != null ? a.description : "");
                        values.put(CalendarContract.Events.EVENT_LOCATION, a.location != null ? a.location : "");
                        values.put(CalendarContract.Events.DTSTART, startMillis);
                        values.put(CalendarContract.Events.DTEND, endMillis);
                        values.put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().getID());
                        try {
                            getContentResolver().insert(CalendarContract.Events.CONTENT_URI, values);
                            totalEvents++;
                        } catch (Exception ignored) {}
                    }
                }

                String resultMsg = "已成功导出 " + totalEvents + " 个事件到系统日历\n范围：" + rangeStr;

                final int finalTotal = totalEvents;
                runOnUiThread(() -> {
                    new MaterialAlertDialogBuilder(new androidx.appcompat.view.ContextThemeWrapper(SettingsDataActivity.this, com.google.android.material.R.style.Theme_Material3_DayNight))
                            .setTitle("导出完成")
                            .setMessage(resultMsg)
                            .setPositiveButton("确定", null)
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    new MaterialAlertDialogBuilder(new androidx.appcompat.view.ContextThemeWrapper(SettingsDataActivity.this, com.google.android.material.R.style.Theme_Material3_DayNight))
                            .setTitle("导出失败")
                            .setMessage("发生错误：" + e.getMessage())
                            .setPositiveButton("确定", null)
                            .show();
                });
            }
        }).start();
    }

    private long getOrCreateDefaultCalendarId() {
        // Try to find an existing local calendar
        String[] projection = {CalendarContract.Calendars._ID, CalendarContract.Calendars.ACCOUNT_TYPE};
        android.database.Cursor cursor = null;
        try {
            cursor = getContentResolver().query(
                    CalendarContract.Calendars.CONTENT_URI,
                    projection,
                    CalendarContract.Calendars.ACCOUNT_TYPE + " = ?",
                    new String[]{"LOCAL"},
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }

        // Fallback: try any calendar
        try {
            cursor = getContentResolver().query(
                    CalendarContract.Calendars.CONTENT_URI,
                    projection,
                    null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }

        return 1L; // fallback
    }

    private long getSemesterStartMs() {
        return getSharedPreferences("course_storage", MODE_PRIVATE)
                .getLong("semester_start_date", System.currentTimeMillis());
    }

    private java.util.Calendar getSemesterStartCalendar() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(getSemesterStartMs());
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal;
    }

    private int getWeekForMillis(long millis) {
        java.util.Calendar start = getSemesterStartCalendar();
        while (start.get(java.util.Calendar.DAY_OF_WEEK) != java.util.Calendar.MONDAY) {
            start.add(java.util.Calendar.DAY_OF_MONTH, -1);
        }
        long diff = millis - start.getTimeInMillis();
        return (int) (diff / (7L * 24L * 60L * 60L * 1000L)) + 1;
    }
}


