package cn.edu.hut.course;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.drawable.GradientDrawable;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.appbar.MaterialToolbar;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
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
    private static final String KEY_TIMETABLE_FONT_SCALE = "timetable_font_scale";
    private static final int TIMETABLE_FONT_PERCENT_MIN = 85;
    private static final int TIMETABLE_FONT_PERCENT_MAX = 130;
    private static final int TIMETABLE_FONT_PERCENT_DEFAULT = 100;

    private final List<Course> allCourses = new ArrayList<>();
    private LinearLayout layoutThemePaletteRow;
    private TextView tvThemeColorValue;
    private TextView tvTimetableFontPercent;
    private SeekBar seekTimetableFontSize;

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
        layoutThemePaletteRow = findViewById(R.id.layoutThemePaletteRow);
        tvThemeColorValue = findViewById(R.id.tvThemeColorValue);
        tvTimetableFontPercent = findViewById(R.id.tvTimetableFontPercent);
        seekTimetableFontSize = findViewById(R.id.seekTimetableFontSize);

        SharedPreferences prefs = getSharedPreferences(PREF_COURSE_STORAGE, MODE_PRIVATE);
        switchGridLines.setChecked(prefs.getBoolean(KEY_SHOW_GRID_LINES, true));

        switchGridLines.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_SHOW_GRID_LINES, isChecked).apply();
            notifyMainToRefresh("refresh_grid");
        });

        View themeCard = findViewById(R.id.btnChooseThemeColor);
        if (themeCard != null) {
            themeCard.setOnClickListener(v -> showThemeHexColorPickerDialog());
        }

        renderThemePaletteRow();
        setupTimetableFontSizeControls();

        loadCoursesForEditor();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyPageVisualStyle();
        renderThemePaletteRow();
        refreshTimetableFontSizeControls();
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

    private int getCurrentThemeColor() {
        return getSharedPreferences(PREF_COURSE_STORAGE, MODE_PRIVATE)
                .getInt(KEY_TIMETABLE_THEME_COLOR, ColorPaletteProvider.defaultThemeColor());
    }

    private int getCurrentTimetableFontPercent() {
        float scale = getSharedPreferences(PREF_COURSE_STORAGE, MODE_PRIVATE)
                .getFloat(KEY_TIMETABLE_FONT_SCALE, TIMETABLE_FONT_PERCENT_DEFAULT / 100f);
        if (Float.isNaN(scale) || Float.isInfinite(scale)) {
            scale = TIMETABLE_FONT_PERCENT_DEFAULT / 100f;
        }
        int percent = Math.round(scale * 100f);
        return Math.max(TIMETABLE_FONT_PERCENT_MIN, Math.min(TIMETABLE_FONT_PERCENT_MAX, percent));
    }

    private void setCurrentThemeColor(int color) {
        getSharedPreferences(PREF_COURSE_STORAGE, MODE_PRIVATE)
                .edit()
                .putInt(KEY_TIMETABLE_THEME_COLOR, color)
                .apply();
        notifyMainToRefresh("refresh_grid");
        renderThemePaletteRow();
    }

    private void setCurrentTimetableFontPercent(int percent) {
        int clamped = Math.max(TIMETABLE_FONT_PERCENT_MIN, Math.min(TIMETABLE_FONT_PERCENT_MAX, percent));
        getSharedPreferences(PREF_COURSE_STORAGE, MODE_PRIVATE)
                .edit()
                .putFloat(KEY_TIMETABLE_FONT_SCALE, clamped / 100f)
                .apply();
        updateTimetableFontPercentLabel(clamped);
        notifyMainToRefresh("refresh_grid");
    }

    private void setupTimetableFontSizeControls() {
        if (seekTimetableFontSize == null) {
            return;
        }
        seekTimetableFontSize.setMax(TIMETABLE_FONT_PERCENT_MAX - TIMETABLE_FONT_PERCENT_MIN);
        refreshTimetableFontSizeControls();
        seekTimetableFontSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int percent = TIMETABLE_FONT_PERCENT_MIN + progress;
                updateTimetableFontPercentLabel(percent);
                if (fromUser) {
                    setCurrentTimetableFontPercent(percent);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void refreshTimetableFontSizeControls() {
        int current = getCurrentTimetableFontPercent();
        if (seekTimetableFontSize != null) {
            seekTimetableFontSize.setProgress(current - TIMETABLE_FONT_PERCENT_MIN);
        }
        updateTimetableFontPercentLabel(current);
    }

    private void updateTimetableFontPercentLabel(int percent) {
        if (tvTimetableFontPercent == null) {
            return;
        }
        int onSurface = UiStyleHelper.resolveOnSurfaceColor(this);
        tvTimetableFontPercent.setText(percent + "%");
        tvTimetableFontPercent.setTextColor(onSurface);
        tvTimetableFontPercent.setBackground(makeRoundedSolid(ColorUtils.setAlphaComponent(onSurface, 28), 10));
    }

    private void renderThemePaletteRow() {
        if (layoutThemePaletteRow == null) {
            return;
        }
        layoutThemePaletteRow.removeAllViews();
        int current = getCurrentThemeColor();
        int[] palette = buildVibrantPalette();
        int selectedStroke = UiStyleHelper.resolveOnSurfaceColor(this);

        for (int color : palette) {
            boolean selected = current == color;
            addPaletteDot(layoutThemePaletteRow, color, selected, null, selectedStroke, v -> setCurrentThemeColor(color));
        }

        addPaletteDot(layoutThemePaletteRow,
                UiStyleHelper.resolveGlassCardColor(this),
                false,
                "+",
                selectedStroke,
                v -> showThemeHexColorPickerDialog());

        if (tvThemeColorValue != null) {
            int onSurface = UiStyleHelper.resolveOnSurfaceColor(this);
            tvThemeColorValue.setText(formatColorHex(current));
            tvThemeColorValue.setTextColor(onSurface);
            tvThemeColorValue.setBackground(makeRoundedSolid(ColorUtils.setAlphaComponent(onSurface, 28), 10));
        }
    }

    private void showThemeHexColorPickerDialog() {
        int currentColor = getCurrentThemeColor();

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), dp(4));

        MaterialCardView preview = new MaterialCardView(this);
        preview.setRadius(dp(14));
        preview.setCardElevation(0f);
        preview.setStrokeWidth(dp(1));
        preview.setStrokeColor(ColorUtils.setAlphaComponent(UiStyleHelper.resolveOnSurfaceColor(this), 40));
        preview.setCardBackgroundColor(currentColor);
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        previewLp.setMargins(0, 0, 0, dp(12));
        preview.setLayoutParams(previewLp);
        content.addView(preview);

        TextView hint = new TextView(this);
        hint.setText("输入 #RRGGBB 颜色值");
        hint.setTextSize(12f);
        hint.setTextColor(UiStyleHelper.resolveOnSurfaceVariantColor(this));
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.setMargins(0, 0, 0, dp(8));
        hint.setLayoutParams(hintLp);
        content.addView(hint);

        EditText hexInput = new EditText(this);
        hexInput.setText(formatColorHex(currentColor));
        hexInput.setSingleLine(true);
        hexInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        hexInput.setTextColor(UiStyleHelper.resolveOnSurfaceColor(this));
        hexInput.setHintTextColor(UiStyleHelper.resolveOnSurfaceVariantColor(this));
        hexInput.setBackground(makeRoundedSolid(ColorUtils.setAlphaComponent(UiStyleHelper.resolveOnSurfaceColor(this), 20), 12));
        hexInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        content.addView(hexInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        hexInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                Integer parsed = parseHexColor(s == null ? "" : s.toString());
                if (parsed != null) {
                    preview.setCardBackgroundColor(parsed);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(
                new androidx.appcompat.view.ContextThemeWrapper(this, com.google.android.material.R.style.Theme_Material3_DayNight_Dialog_Alert))
                .setTitle("自定义主题色")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("应用", null)
                .show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            Integer parsed = parseHexColor(hexInput.getText() == null ? "" : hexInput.getText().toString());
            if (parsed == null) {
                hexInput.setError("请输入 #RRGGBB");
                return;
            }
            setCurrentThemeColor(parsed);
            dialog.dismiss();
        });
    }

    private Integer parseHexColor(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        if (normalized.length() != 6) {
            return null;
        }
        try {
            int rgb = Integer.parseInt(normalized, 16);
            return Color.rgb((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
        } catch (Exception e) {
            return null;
        }
    }

    private String formatColorHex(int color) {
        return String.format(java.util.Locale.getDefault(), "#%02X%02X%02X", Color.red(color), Color.green(color), Color.blue(color));
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

    private static final class CourseLocationRowViews {
        final TextView locationView;
        final TextView distanceView;

        CourseLocationRowViews(TextView locationView, TextView distanceView) {
            this.locationView = locationView;
            this.distanceView = distanceView;
        }
    }

    private void showCourseDetailSheet(Course c) {
        if (c == null) {
            return;
        }
        String colorKey = buildCourseColorKey(c.name, c.isExperimental);
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        int sheetSurfaceColor = UiStyleHelper.resolvePageBackgroundColor(this);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        layout.setPadding(pad, pad, pad, pad);
        scrollView.addView(layout, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText(c.name + (c.isExperimental ? " [实验]" : ""));
        title.setTextSize(20f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(UiStyleHelper.resolveOnSurfaceColor(this));
        layout.addView(title);

        int onSurface = UiStyleHelper.resolveOnSurfaceColor(this);

        MaterialCardView infoCard = createSectionCard();
        LinearLayout rowsContainer = new LinearLayout(this);
        rowsContainer.setOrientation(LinearLayout.VERTICAL);
        infoCard.addView(rowsContainer);
        layout.addView(infoCard);

        final TextView[] teacherRef = new TextView[1];
        final TextView[] locationRef = new TextView[1];
        final TextView[] locationDistanceRef = new TextView[1];
        final TextView[] weeksRef = new TextView[1];

        teacherRef[0] = addEditableInfoRow(rowsContainer,
                R.drawable.ic_profile,
                "教师",
                c.teacher == null || c.teacher.trim().isEmpty() ? "未定" : c.teacher.trim(),
                v -> showTeacherPicker(c, () -> onCourseInfoUpdated(c, teacherRef[0], locationRef[0], locationDistanceRef[0], weeksRef[0])),
                onSurface,
                onSurface);
        rowsContainer.addView(createSectionDivider());

        CourseLocationRowViews locationViews = addEditableLocationInfoRow(rowsContainer,
                R.drawable.ic_agenda_location_24,
                "地点",
                c.location,
                v -> showLocationPicker(c, () -> onCourseInfoUpdated(c, teacherRef[0], locationRef[0], locationDistanceRef[0], weeksRef[0])),
                onSurface,
                onSurface);
        locationRef[0] = locationViews.locationView;
        locationDistanceRef[0] = locationViews.distanceView;
        rowsContainer.addView(createSectionDivider());

        weeksRef[0] = addEditableInfoRow(rowsContainer,
                R.drawable.ic_today,
                "周次",
                formatWeeksForDisplay(c.weeks),
                v -> showWeeksPicker(c, () -> onCourseInfoUpdated(c, teacherRef[0], locationRef[0], locationDistanceRef[0], weeksRef[0])),
                onSurface,
                onSurface);
        rowsContainer.addView(createSectionDivider());

        addEditableInfoRow(rowsContainer,
                R.drawable.ic_agenda_time_24,
                "节次",
                formatCourseSectionForDisplay(c),
                null,
                onSurface,
                onSurface);

        MaterialCardView colorCard = createSectionCard();
        LinearLayout colorBody = new LinearLayout(this);
        colorBody.setOrientation(LinearLayout.VERTICAL);
        colorBody.setPadding(dp(14), dp(12), dp(14), dp(12));
        colorCard.addView(colorBody);

        TextView colorTitle = new TextView(this);
        colorTitle.setText("课程颜色");
        colorTitle.setTextSize(17f);
        colorTitle.setTypeface(null, Typeface.BOLD);
        colorTitle.setTextColor(onSurface);
        colorBody.addView(colorTitle);

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams hsvLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hsvLp.setMargins(0, dp(10), 0, 0);
        hsv.setLayoutParams(hsvLp);
        LinearLayout paletteRow = new LinearLayout(this);
        paletteRow.setOrientation(LinearLayout.HORIZONTAL);
        hsv.addView(paletteRow, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        renderColorSlider(paletteRow, c, colorKey);
        colorBody.addView(hsv);
        layout.addView(colorCard);

        dialog.setContentView(scrollView);
        applyBottomSheetSurfaceStyle(dialog, sheetSurfaceColor);
        dialog.setOnDismissListener(d -> loadCoursesForEditor());
        dialog.show();
    }

    private TextView addEditableInfoRow(LinearLayout parent,
                                        int iconRes,
                                        String label,
                                        String value,
                                        View.OnClickListener editAction,
                                        int labelColor,
                                        int valueColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(56));
        row.setPadding(dp(14), dp(7), dp(12), dp(7));

        if (iconRes != 0) {
            row.addView(createRowIcon(iconRes));
        }

        TextView labelTv = new TextView(this);
        labelTv.setText(label);
        labelTv.setTextSize(17f);
        labelTv.setTextColor(labelColor);
        labelTv.setSingleLine(true);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.setMargins(iconRes == 0 ? 0 : dp(10), 0, dp(10), 0);
        labelTv.setLayoutParams(labelLp);
        row.addView(labelTv);

        LinearLayout valueContainer = new LinearLayout(this);
        valueContainer.setOrientation(LinearLayout.HORIZONTAL);
        valueContainer.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams valueContainerLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        valueContainer.setLayoutParams(valueContainerLp);

        TextView valueTv = new TextView(this);
        valueTv.setText(value);
        valueTv.setTextSize(15f);
        valueTv.setTypeface(null, Typeface.BOLD);
        valueTv.setTextColor(valueColor);
        valueTv.setSingleLine(true);
        valueTv.setMaxWidth(dp(220));
        valueTv.setGravity(Gravity.CENTER);
        valueTv.setPadding(dp(14), dp(8), dp(14), dp(8));
        valueTv.setBackground(makeRoundedSolid(ColorUtils.setAlphaComponent(valueColor, 28), 14));
        valueContainer.addView(valueTv);
        row.addView(valueContainer);

        if (editAction != null) {
            row.setOnClickListener(editAction);
            valueTv.setOnClickListener(editAction);
        }

        parent.addView(row);
        return valueTv;
    }

    private CourseLocationRowViews addEditableLocationInfoRow(LinearLayout parent,
                                                              int iconRes,
                                                              String label,
                                                              String locationRaw,
                                                              View.OnClickListener editAction,
                                                              int labelColor,
                                                              int valueColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(56));
        row.setPadding(dp(14), dp(7), dp(12), dp(7));

        if (iconRes != 0) {
            row.addView(createRowIcon(iconRes));
        }

        TextView labelTv = new TextView(this);
        labelTv.setText(label);
        labelTv.setTextSize(17f);
        labelTv.setTextColor(labelColor);
        labelTv.setSingleLine(true);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.setMargins(iconRes == 0 ? 0 : dp(10), 0, dp(10), 0);
        labelTv.setLayoutParams(labelLp);
        row.addView(labelTv);

        LinearLayout valueContainer = new LinearLayout(this);
        valueContainer.setOrientation(LinearLayout.HORIZONTAL);
        valueContainer.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams valueContainerLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        valueContainer.setLayoutParams(valueContainerLp);

        TextView distanceTv = new TextView(this);
        distanceTv.setTextSize(12f);
        distanceTv.setTextColor(UiStyleHelper.resolveOnSurfaceVariantColor(this));
        distanceTv.setSingleLine(true);
        LinearLayout.LayoutParams distanceLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        distanceLp.setMargins(0, 0, dp(8), 0);
        distanceTv.setLayoutParams(distanceLp);
        updateLocationDistanceHintView(distanceTv, locationRaw);
        valueContainer.addView(distanceTv);

        TextView valueTv = new TextView(this);
        valueTv.setText(formatLocationBase(locationRaw));
        valueTv.setTextSize(15f);
        valueTv.setTypeface(null, Typeface.BOLD);
        valueTv.setTextColor(valueColor);
        valueTv.setSingleLine(true);
        valueTv.setMaxWidth(dp(220));
        valueTv.setGravity(Gravity.CENTER);
        valueTv.setPadding(dp(14), dp(8), dp(14), dp(8));
        valueTv.setBackground(makeRoundedSolid(ColorUtils.setAlphaComponent(valueColor, 28), 14));
        valueContainer.addView(valueTv);
        row.addView(valueContainer);

        if (editAction != null) {
            row.setOnClickListener(editAction);
            valueTv.setOnClickListener(editAction);
            distanceTv.setOnClickListener(editAction);
        }

        parent.addView(row);
        return new CourseLocationRowViews(valueTv, distanceTv);
    }

    private ImageView createRowIcon(int iconRes) {
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(UiStyleHelper.resolveOnSurfaceVariantColor(this)));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(20), dp(20));
        icon.setLayoutParams(iconLp);
        return icon;
    }

    private View createSectionDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(Color.TRANSPARENT);
        divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0));
        return divider;
    }

    private MaterialCardView createSectionCard() {
        MaterialCardView card = new MaterialCardView(this);
        int onSurface = UiStyleHelper.resolveOnSurfaceColor(this);
        card.setRadius(dp(24));
        card.setCardElevation(0f);
        card.setStrokeWidth(1);
        card.setStrokeColor(ColorUtils.setAlphaComponent(onSurface, 24));
        card.setCardBackgroundColor(UiStyleHelper.resolveGlassCardColor(this));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(12), 0, 0);
        card.setLayoutParams(lp);
        return card;
    }

    private void applyBottomSheetSurfaceStyle(com.google.android.material.bottomsheet.BottomSheetDialog dialog, int surfaceColor) {
        if (dialog == null) {
            return;
        }
        dialog.setOnShowListener(d -> {
            FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet == null) {
                return;
            }
            BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(sheet);
            behavior.setDraggable(false);
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);

            GradientDrawable background = new GradientDrawable();
            float radius = dp(28);
            background.setShape(GradientDrawable.RECTANGLE);
            background.setColor(surfaceColor);
            background.setCornerRadii(new float[]{radius, radius, radius, radius, 0f, 0f, 0f, 0f});
            sheet.setBackground(background);
            View parent = (View) sheet.getParent();
            if (parent != null) {
                parent.setBackgroundColor(Color.TRANSPARENT);
            }
        });
    }

    private String formatCourseSectionForDisplay(Course c) {
        if (c == null) {
            return "未定";
        }
        int day = Math.max(1, Math.min(7, c.dayOfWeek));
        int slot = Math.max(1, (c.startSection - 1) / 2 + 1);
        return "周" + day + " 第" + slot + "大节";
    }

    private void onCourseInfoUpdated(Course c, TextView teacherValue, TextView locationValue, TextView locationDistanceValue, TextView weeksValue) {
        if (teacherValue != null) {
            teacherValue.setText(c.teacher == null || c.teacher.trim().isEmpty() ? "未定" : c.teacher.trim());
        }
        if (locationValue != null) {
            locationValue.setText(formatLocationBase(c.location));
        }
        if (locationDistanceValue != null) {
            updateLocationDistanceHintView(locationDistanceValue, c.location);
        }
        if (weeksValue != null) {
            weeksValue.setText(formatWeeksForDisplay(c.weeks));
        }
        saveCoursesToLocal();
        notifyMainToRefresh("reload_courses");
    }

    private String formatLocationBase(String locationRaw) {
        String base = CampusBuildingStore.toStandardLocation(this, locationRaw);
        if (base == null || base.trim().isEmpty()) {
            return "未定";
        }
        return base;
    }

    private String formatLocationDistanceHint(String locationRaw) {
        String base = formatLocationBase(locationRaw);
        if ("未定".equals(base)) {
            return "";
        }
        if (!CampusBuildingStore.hasLocationPermission(this)) {
            return "";
        }
        CampusBuildingStore.DistanceInfo info = CampusBuildingStore.estimateDistanceFromDevice(this, locationRaw);
        if (!info.available) {
            return "";
        }
        if (info.meters < 100f) {
            return "在附近";
        }
        return "距离" + formatDistanceMeters(info.meters);
    }

    private void updateLocationDistanceHintView(TextView hintView, String locationRaw) {
        if (hintView == null) {
            return;
        }
        String hint = formatLocationDistanceHint(locationRaw);
        if (hint.isEmpty()) {
            hintView.setText("");
            hintView.setVisibility(View.GONE);
        } else {
            hintView.setText(hint);
            hintView.setVisibility(View.VISIBLE);
        }
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
        int selectedStroke = getCurrentThemeColor();

        addPaletteDot(container,
                UiStyleHelper.resolveGlassCardColor(this),
                !hasCustom,
                "⊘",
                selectedStroke,
                v -> {
            prefs.edit().remove(colorKey).remove(c.name).apply();
            notifyMainToRefresh("reload_courses");
            renderColorSlider(container, c, colorKey);
        });

        for (int color : palette) {
            boolean selected = hasCustom && current == color;
            addPaletteDot(container, color, selected, null, selectedStroke, v -> {
                prefs.edit().putInt(colorKey, color).apply();
                notifyMainToRefresh("reload_courses");
                renderColorSlider(container, c, colorKey);
            });
        }
    }

    private void addPaletteDot(LinearLayout container,
                               int color,
                               boolean selected,
                               String centerText,
                               int selectedStrokeColor,
                               View.OnClickListener click) {
        MaterialCardView dot = new MaterialCardView(this);
        int size = dp(38);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMargins(0, 0, dp(10), 0);
        dot.setLayoutParams(lp);
        dot.setRadius(size / 2f);
        dot.setCardElevation(0f);
        dot.setStrokeWidth(selected ? dp(2) : dp(1));
        dot.setStrokeColor(selected ? selectedStrokeColor : UiStyleHelper.resolveOutlineColor(this));

        if (centerText != null) {
            dot.setCardBackgroundColor(color);
            TextView icon = new TextView(this);
            icon.setText(centerText);
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

    private GradientDrawable makeRoundedSolid(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
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
