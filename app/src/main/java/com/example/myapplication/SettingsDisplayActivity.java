package com.example.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

public class SettingsDisplayActivity extends AppCompatActivity {

    private static final String PREF_COURSE_STORAGE = "course_storage";
    private static final String PREF_COURSE_COLORS = "course_colors";
    private static final String KEY_SHOW_GRID_LINES = "show_grid_lines";
    private ActivityResultLauncher<String[]> openDocumentLauncher;

    private LinearLayout groupColorOptions;
    private LinearLayout groupImageOptions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_display);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        groupColorOptions = findViewById(R.id.groupColorOptions);
        groupImageOptions = findViewById(R.id.groupImageOptions);
        MaterialButtonToggleGroup toggleBackgroundMode = findViewById(R.id.toggleBackgroundMode);
        MaterialButton btnModeColor = findViewById(R.id.btnModeColor);
        MaterialButton btnModeImage = findViewById(R.id.btnModeImage);
        com.google.android.material.materialswitch.MaterialSwitch switchGridLines = findViewById(R.id.switchGridLines);

        openDocumentLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) {
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    getSharedPreferences(PREF_COURSE_STORAGE, MODE_PRIVATE)
                            .edit()
                            .putString("bg_image_uri", uri.toString())
                            .putString("bg_mode", "image")
                            .apply();
                    notifyMainToRefresh("refresh_bg");
                    updateBackgroundModeUI("image");
                    Toast.makeText(this, "背景图片已更新", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "设置背景失败", Toast.LENGTH_SHORT).show();
                }
            }
        });

        SharedPreferences prefs = getSharedPreferences(PREF_COURSE_STORAGE, MODE_PRIVATE);
        String mode = prefs.getString("bg_mode", "color");
        switchGridLines.setChecked(prefs.getBoolean(KEY_SHOW_GRID_LINES, true));
        if ("image".equals(mode)) {
            btnModeImage.setChecked(true);
        } else {
            btnModeColor.setChecked(true);
        }
        updateBackgroundModeUI(mode);

        switchGridLines.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_SHOW_GRID_LINES, isChecked).apply();
            notifyMainToRefresh("refresh_grid");
        });

        toggleBackgroundMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            String selectedMode = checkedId == R.id.btnModeImage ? "image" : "color";
            getSharedPreferences(PREF_COURSE_STORAGE, MODE_PRIVATE)
                    .edit()
                    .putString("bg_mode", selectedMode)
                    .apply();
            updateBackgroundModeUI(selectedMode);
            notifyMainToRefresh("refresh_bg");
        });

        findViewById(R.id.btnChooseBgColor).setOnClickListener(v -> showBackgroundColorPicker());
        findViewById(R.id.btnSetBackground).setOnClickListener(v -> openDocumentLauncher.launch(new String[]{"image/*"}));
        findViewById(R.id.btnClearBackground).setOnClickListener(v -> {
            getSharedPreferences(PREF_COURSE_STORAGE, MODE_PRIVATE)
                    .edit()
                    .remove("bg_image_uri")
                    .putString("bg_mode", "color")
                    .apply();
            btnModeColor.setChecked(true);
            updateBackgroundModeUI("color");
            notifyMainToRefresh("refresh_bg");
            Toast.makeText(this, "已清除背景照片", Toast.LENGTH_SHORT).show();
        });

        loadCoursesAndRenderColors();
    }

    private void updateBackgroundModeUI(String mode) {
        boolean useImage = "image".equals(mode);
        groupImageOptions.setVisibility(useImage ? View.VISIBLE : View.GONE);
        groupColorOptions.setVisibility(useImage ? View.GONE : View.VISIBLE);
    }

    private int[] buildMaterialPalette() {
        int colorSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, ContextCompat.getColor(this, android.R.color.white));
        int p = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer, colorSurface);
        int s = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondaryContainer, colorSurface);
        int t = MaterialColors.getColor(this, com.google.android.material.R.attr.colorTertiaryContainer, colorSurface);
        int pv = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, colorSurface);
        int sv = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondary, colorSurface);
        int tv = MaterialColors.getColor(this, com.google.android.material.R.attr.colorTertiary, colorSurface);
        return new int[] {
            colorSurface,
            ColorUtils.blendARGB(colorSurface, p, 0.22f),
            ColorUtils.blendARGB(colorSurface, s, 0.22f),
            ColorUtils.blendARGB(colorSurface, t, 0.22f),
            ColorUtils.blendARGB(colorSurface, p, 0.35f),
            ColorUtils.blendARGB(colorSurface, s, 0.35f),
            ColorUtils.blendARGB(colorSurface, t, 0.35f),
            ColorUtils.blendARGB(colorSurface, pv, 0.16f),
            ColorUtils.blendARGB(colorSurface, sv, 0.16f),
            ColorUtils.blendARGB(colorSurface, tv, 0.16f),
            ColorUtils.blendARGB(colorSurface, pv, 0.28f),
            ColorUtils.blendARGB(colorSurface, sv, 0.28f)
        };
    }

    private void showBackgroundColorPicker() {
        int[] palette = buildMaterialPalette();
        showColorPickerBottomSheet("背景颜色", palette, (index, color) -> {
            getSharedPreferences(PREF_COURSE_STORAGE, MODE_PRIVATE)
                    .edit()
                    .putInt("bg_color_index", index)
                    .putString("bg_mode", "color")
                    .apply();
            notifyMainToRefresh("refresh_bg");
        });
    }

    private int getCourseColor(String courseName) {
        SharedPreferences mPrefs = getSharedPreferences(PREF_COURSE_COLORS, MODE_PRIVATE);
        if (mPrefs.contains(courseName)) {
            return mPrefs.getInt(courseName, 0);
        }
        int[] palette = buildMaterialPalette();
        int hash = Math.abs(courseName.hashCode());
        return palette[hash % palette.length];
    }

    private void loadCoursesAndRenderColors() {
        LinearLayout container = findViewById(R.id.containerCourseColors);
        container.removeAllViews();
        
        Set<String> uniqueCourses = new HashSet<>();
        try {
            String json = getSharedPreferences(PREF_COURSE_STORAGE, MODE_PRIVATE).getString("courses_json", "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (!o.optBoolean("isRemark", false)) {
                    uniqueCourses.add(o.getString("name"));
                }
            }
        } catch (Exception ignored) {}

        if (uniqueCourses.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("暂无课程");
            tv.setPadding(0, 16, 0, 16);
            container.addView(tv);
            return;
        }

        for (String course : uniqueCourses) {
            MaterialCardView card = new MaterialCardView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 16);
            card.setLayoutParams(lp);
            card.setCardElevation(0);
            card.setStrokeWidth(1);
            card.setStrokeColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline, 0));
            card.setRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics()));

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(32, 24, 32, 24);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView tv = new TextView(this);
            tv.setText(course);
            tv.setTextSize(16);
            tv.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, ContextCompat.getColor(this, android.R.color.black)));
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tv.setLayoutParams(tLp);

            View colorIndicator = new View(this);
            int size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics());
            LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(size, size);
            colorIndicator.setLayoutParams(cLp);
            
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            gd.setColor(getCourseColor(course));
            gd.setStroke(2, MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline, 0));
            colorIndicator.setBackground(gd);

            row.addView(tv);
            row.addView(colorIndicator);
            card.addView(row);

            card.setOnClickListener(v -> showColorPicker(course, gd));
            container.addView(card);
        }
    }

    private void showColorPicker(String courseName, android.graphics.drawable.GradientDrawable gd) {
        int[] palette = buildMaterialPalette();
        showColorPickerBottomSheet("选择颜色 - " + courseName, palette, (index, color) -> {
            getSharedPreferences(PREF_COURSE_COLORS, MODE_PRIVATE).edit().putInt(courseName, color).apply();
            gd.setColor(color);
            notifyMainToRefresh("refresh_grid");
        }, () -> {
            getSharedPreferences(PREF_COURSE_COLORS, MODE_PRIVATE).edit().remove(courseName).apply();
            gd.setColor(getCourseColor(courseName));
            notifyMainToRefresh("refresh_grid");
        });
    }

    private interface OnPalettePickListener {
        void onPick(int index, int color);
    }

    private void showColorPickerBottomSheet(String titleText, int[] colors, OnPalettePickListener onColorSelect) {
        showColorPickerBottomSheet(titleText, colors, onColorSelect, null);
    }

    private void showColorPickerBottomSheet(String titleText, int[] colors, OnPalettePickListener onColorSelect, Runnable onReset) {
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
            bg.setStroke(2, MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline, 0));
            colorDot.setBackground(bg);
            colorDot.setOnClickListener(v -> {
                onColorSelect.onPick(colorIndex, color);
                dialog.dismiss();
            });
            grid.addView(colorDot);
        }
        layout.addView(grid);

        if (onReset != null) {
            MaterialButton btnReset = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btnReset.setText("恢复默认颜色");
            btnReset.setTextColor(ColorStateList.valueOf(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, 0)));
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            btnLp.setMargins(0, 24, 0, 0);
            btnReset.setLayoutParams(btnLp);
            btnReset.setOnClickListener(v -> {
                onReset.run();
                dialog.dismiss();
            });
            layout.addView(btnReset);
        }

        dialog.setContentView(layout);
        dialog.show();
    }

    private void notifyMainToRefresh(String action) {
        Intent i = new Intent();
        i.putExtra("action", action);
        setResult(RESULT_OK, i);
    }
}
