package cn.edu.hut.course;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.card.MaterialCardView;

import cn.edu.hut.course.data.CampusBuildingStore;
import cn.edu.hut.course.data.CourseStorageManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SettingsDisplayActivity extends AppCompatActivity {

    private static final String PREF_COURSE_STORAGE = "course_storage";
    private static final String PREF_COURSE_COLORS = "course_colors";
    private static final String KEY_SHOW_GRID_LINES = "show_grid_lines";
    private static final String KEY_TIMETABLE_THEME_COLOR = "timetable_theme_color";

    private final List<Course> allCourses = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiStyleHelper.hideStatusBar(this);
        setContentView(R.layout.activity_settings_display);
        applyPageVisualStyle();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        UiStyleHelper.styleGlassToolbar(toolbar, this);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_rounded_24);
        toolbar.setNavigationOnClickListener(v -> finish());

        com.google.android.material.materialswitch.MaterialSwitch switchGridLines = findViewById(R.id.switchGridLines);

        SharedPreferences prefs = getSharedPreferences(PREF_COURSE_STORAGE, MODE_PRIVATE);
        switchGridLines.setChecked(prefs.getBoolean(KEY_SHOW_GRID_LINES, true));

        switchGridLines.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_SHOW_GRID_LINES, isChecked).apply();
            notifyMainToRefresh("refresh_grid");
        });

        findViewById(R.id.btnChooseThemeColor).setOnClickListener(v -> showTimetableThemeColorPicker());

        loadCoursesForEditor();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyPageVisualStyle();
        loadCoursesForEditor();
    }

    private void applyPageVisualStyle() {
        View root = findViewById(R.id.rootSettingsDisplay);
        if (root != null) {
            UiStyleHelper.applySecondaryPageBackground(root, this);
        }
        UiStyleHelper.applyGlassCards(findViewById(android.R.id.content), this);
    }

    private int[] buildVibrantPalette() {
        return ColorPaletteProvider.vibrantLightPalette();
    }

    private void showTimetableThemeColorPicker() {
        int[] palette = buildVibrantPalette();
        showColorPickerBottomSheet("课表主题色", palette, (index, color) -> {
            getSharedPreferences(PREF_COURSE_STORAGE, MODE_PRIVATE)
                    .edit()
                    .putInt(KEY_TIMETABLE_THEME_COLOR, color)
                    .apply();
            notifyMainToRefresh("refresh_grid");
        });
    }

    private static class DisplayCourseItem {
        final String name;
        final boolean isExperimental;
        final Course course;

        DisplayCourseItem(String name, boolean isExperimental, Course course) {
            this.name = name;
            this.isExperimental = isExperimental;
            this.course = course;
        }
    }

    private void loadCoursesForEditor() {
        LinearLayout container = findViewById(R.id.containerCourseColors);
        if (container == null) return;
        container.removeAllViews();
        loadCoursesFromLocal();

        Map<String, DisplayCourseItem> dedup = new LinkedHashMap<>();
        for (Course c : allCourses) {
            if (c == null || c.isRemark) continue;
            String key = buildCourseColorKey(c.name, c.isExperimental);
            if (!dedup.containsKey(key)) {
                dedup.put(key, new DisplayCourseItem(c.name, c.isExperimental, c));
            }
        }

        List<DisplayCourseItem> items = new ArrayList<>(dedup.values());

        if (items.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("暂无课程可编辑");
            tv.setPadding(0, 16, 0, 16);
            tv.setTextColor(UiStyleHelper.resolveOnSurfaceVariantColor(this));
            container.addView(tv);
            return;
        }

        Collections.sort(items, (a, b) -> {
            if (a.isExperimental != b.isExperimental) {
                return Boolean.compare(a.isExperimental, b.isExperimental);
            }
            return a.name.compareToIgnoreCase(b.name);
        });

        for (DisplayCourseItem item : items) {
            MaterialCardView card = new MaterialCardView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 10);
            card.setLayoutParams(lp);
            UiStyleHelper.styleGlassCard(card, this);
            card.setRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics()));

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(24, 18, 24, 18);

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            textCol.setLayoutParams(tlp);

            TextView title = new TextView(this);
            title.setText(item.name + (item.isExperimental ? " [实验]" : ""));
            title.setTextSize(15f);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setTextColor(UiStyleHelper.resolveOnSurfaceColor(this));

            TextView summary = new TextView(this);
            summary.setText(item.isExperimental ? "实验课" : "教学课");
            summary.setTextSize(12f);
            summary.setTextColor(UiStyleHelper.resolveOnSurfaceVariantColor(this));
            summary.setPadding(0, 4, 0, 0);

            textCol.addView(title);
            textCol.addView(summary);
            row.addView(textCol);

            card.addView(row);
            card.setOnClickListener(v -> showCourseDetailSheet(item.course));
            container.addView(card);
        }
    }

    private void loadCoursesFromLocal() {
        allCourses.clear();
        allCourses.addAll(CourseStorageManager.loadCourses(this));
    }

    private void saveCoursesToLocal() {
        CourseStorageManager.saveCourses(this, allCourses);
    }

    private interface OnPalettePickListener {
        void onPick(int index, int color);
    }

    private void showColorPickerBottomSheet(String titleText, int[] colors, OnPalettePickListener onColorSelect) {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, 24);
        layout.addView(title);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        grid.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (int i = 0; i < colors.length; i++) {
            int color = colors[i];
            int colorIndex = i;
            View colorDot = new View(this);
            int size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48, getResources().getDisplayMetrics());
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = size;
            params.height = size;
            params.setMargins(16, 16, 16, 16);
            colorDot.setLayoutParams(params);

            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            bg.setColor(color);
            bg.setStroke(2, UiStyleHelper.resolveOutlineColor(this));
            colorDot.setBackground(bg);
            colorDot.setOnClickListener(v -> {
                onColorSelect.onPick(colorIndex, color);
                dialog.dismiss();
            });
            grid.addView(colorDot);
        }

        layout.addView(grid);
        dialog.setContentView(layout);
        dialog.show();
    }

    private void showCourseDetailSheet(Course c) {
        if (c == null) return;
        String colorKey = buildCourseColorKey(c.name, c.isExperimental);
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        layout.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText(c.name + (c.isExperimental ? " [实验]" : ""));
        title.setTextSize(18f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(UiStyleHelper.resolveOnSurfaceColor(this));
        layout.addView(title);

        int labelColor = UiStyleHelper.resolveOnSurfaceVariantColor(this);
        int valueColor = UiStyleHelper.resolveOnSurfaceColor(this);

        LinearLayout rowsContainer = new LinearLayout(this);
        rowsContainer.setOrientation(LinearLayout.VERTICAL);
        rowsContainer.setPadding(0, pad / 2, 0, pad / 3);

        final TextView[] teacherRef = new TextView[1];
        final TextView[] locationRef = new TextView[1];
        final TextView[] weeksRef = new TextView[1];

        teacherRef[0] = addEditableInfoRow(rowsContainer, "教师",
                c.teacher == null || c.teacher.trim().isEmpty() ? "未定" : c.teacher.trim(),
                v -> showTeacherPicker(c, () -> onCourseInfoUpdated(c, teacherRef[0], locationRef[0], weeksRef[0])),
                labelColor, valueColor);

        locationRef[0] = addEditableInfoRow(rowsContainer, "地点",
            formatLocationWithDistance(c.location),
                v -> showLocationPicker(c, () -> onCourseInfoUpdated(c, teacherRef[0], locationRef[0], weeksRef[0])),
                labelColor, valueColor);

        weeksRef[0] = addEditableInfoRow(rowsContainer, "周次",
                formatWeeksForDisplay(c.weeks),
                v -> showWeeksPicker(c, () -> onCourseInfoUpdated(c, teacherRef[0], locationRef[0], weeksRef[0])),
                labelColor, valueColor);

        addEditableInfoRow(rowsContainer, "节次",
                "周" + c.dayOfWeek + " 第" + ((c.startSection - 1) / 2 + 1) + "大节",
                null, labelColor, valueColor);
        layout.addView(rowsContainer);

        TextView colorTitle = new TextView(this);
        colorTitle.setText("课程颜色");
        colorTitle.setTextSize(14f);
        colorTitle.setTypeface(null, Typeface.BOLD);
        colorTitle.setTextColor(labelColor);
        colorTitle.setPadding(0, pad / 4, 0, pad / 4);
        layout.addView(colorTitle);

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout paletteRow = new LinearLayout(this);
        paletteRow.setOrientation(LinearLayout.HORIZONTAL);
        hsv.addView(paletteRow, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        renderColorSlider(paletteRow, c, colorKey);
        layout.addView(hsv);

        dialog.setContentView(layout);
        dialog.setOnDismissListener(d -> loadCoursesForEditor());
        dialog.show();
    }

    private TextView addEditableInfoRow(LinearLayout parent, String label, String value, View.OnClickListener editAction,
                                        int labelColor, int valueColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textCol.setLayoutParams(textLp);

        TextView labelTv = new TextView(this);
        labelTv.setText(label);
        labelTv.setTextSize(12f);
        labelTv.setTextColor(labelColor);
        labelTv.setTypeface(null, Typeface.BOLD);

        TextView valueTv = new TextView(this);
        valueTv.setText(value);
        valueTv.setTextSize(16f);
        valueTv.setTextColor(valueColor);
        valueTv.setPadding(0, dp(2), 0, 0);

        textCol.addView(labelTv);
        textCol.addView(valueTv);
        row.addView(textCol);

        if (editAction != null) {
            ImageButton editBtn = new ImageButton(this);
            editBtn.setImageResource(android.R.drawable.ic_menu_edit);
            editBtn.setBackgroundColor(Color.TRANSPARENT);
            editBtn.setColorFilter(labelColor);
            editBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            editBtn.setPadding(dp(6), dp(6), dp(6), dp(6));
            editBtn.setContentDescription("编辑" + label);
            LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(dp(32), dp(32));
            editLp.setMargins(dp(10), 0, 0, 0);
            editBtn.setLayoutParams(editLp);
            editBtn.setOnClickListener(editAction);
            row.addView(editBtn);
        }

        parent.addView(row);
        return valueTv;
    }

    private void onCourseInfoUpdated(Course c, TextView teacherValue, TextView locationValue, TextView weeksValue) {
        if (teacherValue != null) {
            teacherValue.setText(c.teacher == null || c.teacher.trim().isEmpty() ? "未定" : c.teacher.trim());
        }
        if (locationValue != null) {
            locationValue.setText(formatLocationWithDistance(c.location));
        }
        if (weeksValue != null) {
            weeksValue.setText(formatWeeksForDisplay(c.weeks));
        }
        saveCoursesToLocal();
        notifyMainToRefresh("reload_courses");
    }

    private void showTeacherPicker(Course c, Runnable afterPick) {
        List<String> options = new ArrayList<>();
        options.add("未定");
        for (Course item : allCourses) {
            if (item == null || item.isRemark) continue;
            String teacher = item.teacher == null ? "" : item.teacher.trim();
            if (!teacher.isEmpty() && !options.contains(teacher)) {
                options.add(teacher);
            }
        }
        String current = c.teacher == null ? "" : c.teacher.trim();
        int checked = current.isEmpty() ? 0 : options.indexOf(current);
        if (checked < 0) {
            options.add(current);
            checked = options.size() - 1;
        }
        final int[] picked = {checked};
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(new androidx.appcompat.view.ContextThemeWrapper(this, com.google.android.material.R.style.Theme_Material3_DayNight_Dialog_Alert))
                .setTitle("选择教师")
                .setSingleChoiceItems(options.toArray(new String[0]), checked, (dialog, which) -> picked[0] = which)
                .setNeutralButton("自定义", (dialog, which) -> showCustomTeacherInputDialog(c, afterPick))
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> {
                    String selected = options.get(picked[0]);
                    c.teacher = "未定".equals(selected) ? "" : selected;
                    if (afterPick != null) afterPick.run();
                })
                .show();
    }

    private void showLocationPicker(Course c, Runnable afterPick) {
        List<String> options = new ArrayList<>();
        options.add("未定");
        options.addAll(CampusBuildingStore.getBuildingNames(this));

        CampusBuildingStore.ResolvedLocation resolved = CampusBuildingStore.resolveLocation(this, c.location);
        String currentBuilding = resolved == null ? "" : resolved.buildingName;
        String currentRoom = resolved == null ? "" : resolved.roomNumber;
        int checked = currentBuilding.isEmpty() ? 0 : options.indexOf(currentBuilding);
        if (checked < 0) checked = 0;

        final int[] picked = {checked};
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(new androidx.appcompat.view.ContextThemeWrapper(this, com.google.android.material.R.style.Theme_Material3_DayNight_Dialog_Alert))
                .setTitle("选择上课地点")
                .setSingleChoiceItems(options.toArray(new String[0]), checked, (dialog, which) -> picked[0] = which)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> {
                    String selected = options.get(picked[0]);
                    if ("未定".equals(selected)) {
                        c.location = "";
                        if (afterPick != null) afterPick.run();
                        return;
                    }
                    showRoomNumberInputDialog(selected, currentRoom, room -> {
                        c.location = CampusBuildingStore.buildLocationText(selected, room);
                        if (afterPick != null) afterPick.run();
                    });
                })
                .show();
    }

    private String formatLocationWithDistance(String locationRaw) {
        String base = CampusBuildingStore.toStandardLocation(this, locationRaw);
        if ("未定".equals(base)) {
            return base;
        }

        if (!CampusBuildingStore.hasLocationPermission(this)) {
            return base;
        }

        CampusBuildingStore.DistanceInfo info = CampusBuildingStore.estimateDistanceFromDevice(this, locationRaw);
        if (info.available) {
            return base + "（距离" + formatDistanceMeters(info.meters) + "）";
        }
        return base;
    }

    private String formatDistanceMeters(float meters) {
        if (meters < 1000f) {
            return String.valueOf(Math.round(meters)) + "米";
        }
        return String.format(java.util.Locale.getDefault(), "%.1f公里", meters / 1000f);
    }

    private void showCustomTeacherInputDialog(Course c, Runnable afterPick) {
        EditText input = new EditText(this);
        input.setHint("输入教师名称");
        input.setText(c.teacher == null ? "" : c.teacher);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(new androidx.appcompat.view.ContextThemeWrapper(this, com.google.android.material.R.style.Theme_Material3_DayNight_Dialog_Alert))
                .setTitle("自定义教师")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> {
                    c.teacher = input.getText() == null ? "" : input.getText().toString().trim();
                    if (afterPick != null) afterPick.run();
                })
                .show();
    }

    private void showRoomNumberInputDialog(String buildingName, String currentRoom, OnRoomConfirm callback) {
        EditText input = new EditText(this);
        input.setHint("教室位置（仅数字，可为空）");
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(currentRoom == null ? "" : currentRoom);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(new androidx.appcompat.view.ContextThemeWrapper(this, com.google.android.material.R.style.Theme_Material3_DayNight_Dialog_Alert))
                .setTitle("设置教室位置 - " + buildingName)
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> {
                    String raw = input.getText() == null ? "" : input.getText().toString();
                    String room = raw.replaceAll("[^0-9]", "").trim();
                    if (callback != null) {
                        callback.onConfirm(room);
                    }
                })
                .show();
    }

    private void showWeeksPicker(Course c, Runnable afterPick) {
        int maxWeeks = 20;
        String[] labels = new String[maxWeeks];
        boolean[] checked = new boolean[maxWeeks];
        Set<Integer> weekSet = new HashSet<>();
        if (c.weeks != null) {
            weekSet.addAll(c.weeks);
        }
        for (int i = 0; i < maxWeeks; i++) {
            int weekNo = i + 1;
            labels[i] = "第" + weekNo + "周";
            checked[i] = weekSet.contains(weekNo);
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(new androidx.appcompat.view.ContextThemeWrapper(this, com.google.android.material.R.style.Theme_Material3_DayNight_Dialog_Alert))
                .setTitle("选择上课周数")
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> {
                    List<Integer> selectedWeeks = new ArrayList<>();
                    for (int i = 0; i < checked.length; i++) {
                        if (checked[i]) selectedWeeks.add(i + 1);
                    }
                    if (selectedWeeks.isEmpty()) {
                        Toast.makeText(this, "至少选择一周", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Collections.sort(selectedWeeks);
                    c.weeks = selectedWeeks;
                    if (afterPick != null) afterPick.run();
                })
                .show();
    }

    private String buildCourseColorKey(String courseName, boolean isExperimental) {
        return courseName + (isExperimental ? "|EXP" : "|REG");
    }

    private int getCourseColor(String courseName, boolean isExperimental) {
        SharedPreferences mPrefs = getSharedPreferences(PREF_COURSE_COLORS, MODE_PRIVATE);
        String key = buildCourseColorKey(courseName, isExperimental);
        if (mPrefs.contains(key)) {
            return mPrefs.getInt(key, 0);
        }
        if (mPrefs.contains(courseName)) {
            return mPrefs.getInt(courseName, 0);
        }
        int[] palette = buildVibrantPalette();
        int hash = Math.abs(courseName.hashCode());
        int color = palette[hash % palette.length];
        if (isExperimental) {
            color = mixColor(color, Color.WHITE, 0.08f);
        }
        return color;
    }

    private void renderColorSlider(LinearLayout container, Course c, String colorKey) {
        container.removeAllViews();
        SharedPreferences prefs = getSharedPreferences(PREF_COURSE_COLORS, MODE_PRIVATE);
        boolean hasCustom = prefs.contains(colorKey) || prefs.contains(c.name);
        int current = getCourseColor(c.name, c.isExperimental);
        int[] palette = buildVibrantPalette();

        addColorDot(container, Color.TRANSPARENT, !hasCustom, true, v -> {
            prefs.edit().remove(colorKey).remove(c.name).apply();
            notifyMainToRefresh("reload_courses");
            renderColorSlider(container, c, colorKey);
        });

        for (int color : palette) {
            boolean selected = hasCustom && current == color;
            addColorDot(container, color, selected, false, v -> {
                prefs.edit().putInt(colorKey, color).apply();
                notifyMainToRefresh("reload_courses");
                renderColorSlider(container, c, colorKey);
            });
        }
    }

    private void addColorDot(LinearLayout container, int color, boolean selected, boolean isDefault, View.OnClickListener click) {
        MaterialCardView dot = new MaterialCardView(this);
        int size = dp(38);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMargins(0, 0, dp(10), 0);
        dot.setLayoutParams(lp);
        dot.setRadius(size / 2f);
        dot.setCardElevation(0f);
        dot.setStrokeWidth(selected ? dp(2) : dp(1));
        int outline = UiStyleHelper.resolveOutlineColor(this);
        int selectedColor = getSharedPreferences(PREF_COURSE_STORAGE, MODE_PRIVATE)
                .getInt(KEY_TIMETABLE_THEME_COLOR, ColorPaletteProvider.defaultThemeColor());
        dot.setStrokeColor(selected ? selectedColor : outline);

        if (isDefault) {
            dot.setCardBackgroundColor(UiStyleHelper.resolveGlassCardColor(this));
            TextView icon = new TextView(this);
            icon.setText("⊘");
            icon.setGravity(Gravity.CENTER);
            icon.setTextSize(16f);
            icon.setTextColor(UiStyleHelper.resolveOnSurfaceVariantColor(this));
            dot.addView(icon, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            dot.setCardBackgroundColor(color);
        }
        dot.setOnClickListener(click);
        container.addView(dot);
    }

    private int mixColor(int from, int to, float ratio) {
        float clamped = Math.max(0f, Math.min(1f, ratio));
        int r = Math.round(Color.red(from) * (1f - clamped) + Color.red(to) * clamped);
        int g = Math.round(Color.green(from) * (1f - clamped) + Color.green(to) * clamped);
        int b = Math.round(Color.blue(from) * (1f - clamped) + Color.blue(to) * clamped);
        return Color.rgb(r, g, b);
    }

    private String formatWeeksForDisplay(List<Integer> weeks) {
        if (weeks == null || weeks.isEmpty()) return "未定";
        List<Integer> sorted = new ArrayList<>(weeks);
        Collections.sort(sorted);
        StringBuilder sb = new StringBuilder();
        int start = sorted.get(0);
        int prev = start;
        for (int i = 1; i < sorted.size(); i++) {
            int cur = sorted.get(i);
            if (cur == prev + 1) {
                prev = cur;
                continue;
            }
            if (sb.length() > 0) sb.append(", ");
            if (start == prev) sb.append(start);
            else sb.append(start).append("-").append(prev);
            start = prev = cur;
        }
        if (sb.length() > 0) sb.append(", ");
        if (start == prev) sb.append(start);
        else sb.append(start).append("-").append(prev);
        return sb.toString();
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private interface OnRoomConfirm {
        void onConfirm(String roomNumber);
    }

    private void notifyMainToRefresh(String action) {
        Intent i = new Intent();
        i.putExtra("action", action);
        setResult(RESULT_OK, i);
    }
}
