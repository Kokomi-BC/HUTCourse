package cn.edu.hut.course;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.viewpager2.widget.ViewPager2;
import androidx.recyclerview.widget.RecyclerView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "CourseScraper";
    private static final String BASE_URL = "http://jwxt.hut.edu.cn";
    private static final String LOGIN_URL = BASE_URL + "/jsxsd/sso.jsp";
    private static final String SUCCESS_URL = BASE_URL + "/jsxsd/framework/xsMainV.htmlx";
    private static final String LOGIN_SUCCESS_PATH = "/jsxsd/framework/xsMainV.htmlx";
    private static final String TARGET_URL = BASE_URL + "/jsxsd/xskb/xskb_list.do?viweType=0";
    private static final String EXPERIMENT_URL = BASE_URL + "/jsxsd/syjx/toXskb.do";
    private static final String PREF_NAME = "course_storage";
    private static final String PREF_COURSE_COLORS = "course_colors";
    private static final String KEY_COURSES_JSON = "courses_json";
    private static final String KEY_SHOW_GRID_LINES = "show_grid_lines";
    private static final String KEY_TIMETABLE_THEME_COLOR = "timetable_theme_color";

    private com.google.android.material.card.MaterialCardView cardNextCourseNotice;
    private TextView tvMainTitle, tvEmptyHint, tvRemarksSettings, tvNextCourseNotice;
    private ImageView ivBackground;
    private ImageButton btnCloseNextCourseNotice;
    private BottomNavigationView bottomNav;
    // Grid for header rendering if needed, but we use VP2 now
    private ViewPager2 viewPager;
    private View cardRemarks, pageSchedule, titleContainer, rootMain, vBackgroundScrim;
    private ScrollView scheduleScroll;
    private ScrollView pageSettings;
    private View settingsHome;
    private View itemAccountSync, itemDisplaySettings, itemDataManagement;

    private final List<Course> allCourses = new ArrayList<>();
    private int currentWeek = 1;
    private int totalWeeks = 20;
    private long semesterStartDateMs = 0;
    private boolean nextCourseNoticeDismissed = false;
    private String selectedCourseColorKey = null;
    private int lastRealtimeWeek = -1;
    private int lastRealtimeDay = -1;
    private int lastRealtimeSlot = -2;
    private final Handler noticeHandler = new Handler(Looper.getMainLooper());
    private final Runnable noticeTicker = new Runnable() {
        @Override
        public void run() {
            updateNextCourseNotice();
            refreshRealtimeCourseHighlight();
            noticeHandler.postDelayed(this, 1_000L);
        }
    };

    private ActivityResultLauncher<Intent> browserLauncher;
    private ActivityResultLauncher<Intent> settingsLauncher;

    private void updateTitle() {
        if (tvMainTitle != null) {
            if (pageSettings.getVisibility() == View.VISIBLE) {
                tvMainTitle.setText("设置");
            } else {
                tvMainTitle.setText("第" + currentWeek + "周课表");
            }
        }
        updateRemarksInSettings();
    }

    private void applyMaterialScaffoldStyle(int baseBackgroundColor, boolean imageMode) {
        int colorOnSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, ContextCompat.getColor(this, android.R.color.black));
        int colorOnSurfaceVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, colorOnSurface);
        int colorPrimary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, colorOnSurface);
        int colorSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, ContextCompat.getColor(this, android.R.color.white));
        int navBackground = ColorUtils.blendARGB(baseBackgroundColor, colorSurface, 0.30f);

        if (tvMainTitle != null) {
            tvMainTitle.setTextColor(colorOnSurface);
        }

        if (bottomNav != null) {
            int[][] states = new int[][]{
                    new int[]{android.R.attr.state_checked},
                    new int[]{}
            };
            int[] colors = new int[]{colorPrimary, colorOnSurfaceVariant};
            ColorStateList tintList = new ColorStateList(states, colors);
            bottomNav.setItemIconTintList(tintList);
            bottomNav.setItemTextColor(tintList);
            bottomNav.setBackgroundTintList(ColorStateList.valueOf(imageMode ? android.graphics.Color.TRANSPARENT : navBackground));
            bottomNav.setElevation(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6, getResources().getDisplayMetrics()));
        }
    }

    private void updateRemarksInSettings() {
        if (cardRemarks == null || tvRemarksSettings == null) return;
        StringBuilder sb = new StringBuilder();
        for (Course c : allCourses) {
            if (c.isRemark) {
                sb.append("• ").append(c.name).append("\n");
            }
        }
        if (sb.length() > 0) {
            cardRemarks.setVisibility(View.VISIBLE);
            tvRemarksSettings.setText(sb.toString().trim());
        } else {
            cardRemarks.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiStyleHelper.hideStatusBar(this);

        setContentView(R.layout.activity_main);

        tvMainTitle = findViewById(R.id.tvMainTitle);
        ivBackground = findViewById(R.id.ivBackground);
        vBackgroundScrim = findViewById(R.id.vBackgroundScrim);
        rootMain = findViewById(R.id.rootMain);
        titleContainer = findViewById(R.id.titleContainer);
        tvEmptyHint = findViewById(R.id.tvEmptyHint);
        pageSchedule = findViewById(R.id.pageSchedule);
        pageSettings = findViewById(R.id.pageSettings);
        bottomNav = findViewById(R.id.bottomNav);
        viewPager = findViewById(R.id.viewPager);
        cardRemarks = findViewById(R.id.cardRemarks);
        tvRemarksSettings = findViewById(R.id.tvRemarksSettings);
        cardNextCourseNotice = findViewById(R.id.cardNextCourseNotice);
        tvNextCourseNotice = findViewById(R.id.tvNextCourseNotice);
        btnCloseNextCourseNotice = findViewById(R.id.btnCloseNextCourseNotice);
        settingsHome = findViewById(R.id.settingsHome);
        itemAccountSync = findViewById(R.id.itemAccountSync);
        itemDisplaySettings = findViewById(R.id.itemDisplaySettings);
        itemDataManagement = findViewById(R.id.itemDataManagement);
        applyMainSettingsGlassStyle();

        browserLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                Intent data = result.getData();
                String cookie = data != null ? data.getStringExtra("cookie") : null;
                extractAllTables(cookie);
            } else {
                loadCoursesFromLocal();
            }
        });

        settingsLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                String action = result.getData().getStringExtra("action");
                if (action != null) {
                    switch (action) {
                        case "open_jwxt":
                            Intent intent = new Intent(this, BrowserActivity.class);
                            intent.putExtra("url", LOGIN_URL);
                            browserLauncher.launch(intent);
                            break;
                        case "extract":
                            extractAllTables(null);
                            break;
                        case "clear_schedule_only":
                            clearCurrentSchedule(false);
                            break;
                        case "logout":
                            clearLoginState();
                            break;
                        case "refresh_current_week":
                            semesterStartDateMs = getSharedPreferences(PREF_NAME, MODE_PRIVATE).getLong("semester_start_date", 0);
                            jumpToActualCurrentWeek(true);
                            break;
                        case "refresh_bg":
                            updateBackground();
                            break;
                        case "refresh_grid":
                            drawGrid();
                            break;
                        case "reload_courses":
                            loadCoursesFromLocal();
                            updateNextCourseNotice();
                            break;
                        case "open_course_editor":
                            openCourseEditorFromAction(result.getData());
                            break;
                        case "export_table":
                            exportTable();
                            break;
                        case "import_table":
                            importTable();
                            break;
                        case "export_cookie":
                            exportCookie();
                            break;
                        case "import_cookie":
                            importCookie();
                            break;
                    }
                }
            }
        });

        itemAccountSync.setOnClickListener(v -> settingsLauncher.launch(new Intent(this, SettingsAccountActivity.class)));
        itemDisplaySettings.setOnClickListener(v -> settingsLauncher.launch(new Intent(this, SettingsDisplayActivity.class)));
        itemDataManagement.setOnClickListener(v -> settingsLauncher.launch(new Intent(this, SettingsDataActivity.class)));

        if (btnCloseNextCourseNotice != null) {
            btnCloseNextCourseNotice.setOnClickListener(v -> {
                nextCourseNoticeDismissed = true;
                updateNextCourseNotice();
            });
        }

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            pageSchedule.setVisibility(id == R.id.nav_schedule ? View.VISIBLE : View.GONE);
            pageSettings.setVisibility(id == R.id.nav_settings ? View.VISIBLE : View.GONE);
            updateNextCourseNotice();
            updateTitle();
            return true;
        });
        bottomNav.setOnItemReselectedListener(item -> {
            if (item.getItemId() == R.id.nav_schedule) {
                jumpToActualCurrentWeek(true);
                updateNextCourseNotice();
            }
        });

        View.OnClickListener weekSelectorClick = v -> showWeekSelector();
        tvMainTitle.setOnClickListener(weekSelectorClick);
        if (titleContainer != null) {
            titleContainer.setOnClickListener(weekSelectorClick);
        }

        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        semesterStartDateMs = prefs.getLong("semester_start_date", 0);
        int defaultBg = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, ContextCompat.getColor(this, android.R.color.white));
        applyMaterialScaffoldStyle(defaultBg, false);
        setupViewPager();
        loadCoursesFromLocal();
        updateBackground();
        startNoticeTicker();
        openCourseEditorFromAction(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openCourseEditorFromAction(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        startNoticeTicker();
    }

    @Override
    protected void onPause() {
        noticeHandler.removeCallbacks(noticeTicker);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        noticeHandler.removeCallbacks(noticeTicker);
        super.onDestroy();
    }

    private void startNoticeTicker() {
        noticeHandler.removeCallbacks(noticeTicker);
        noticeHandler.post(noticeTicker);
    }

    private void showWeekSelector() {
        if (pageSettings != null && pageSettings.getVisibility() == View.VISIBLE) return;
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_week_selector, null);
        dialog.setContentView(view);
        
        GridLayout grid = view.findViewById(R.id.gridWeeks);
        for (int i = 0; i < totalWeeks; i++) {
            final int week = i + 1;
            MaterialButton btn = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btn.setText(String.valueOf(week));
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = GridLayout.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(8, 8, 8, 8);
            btn.setLayoutParams(params);
            if (week == currentWeek) {
                btn.setBackgroundTintList(ColorStateList.valueOf(
                    MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, android.graphics.Color.BLUE)
                ));
                btn.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimary, android.graphics.Color.WHITE));
            }
            btn.setOnClickListener(v -> {
                currentWeek = week;
                moveToCurrentWeek(false, true);
                updateTitle();
                dialog.dismiss();
            });
            grid.addView(btn);
        }
        dialog.show();
    }

    private void openCourseEditorFromAction(Intent data) {
        if (data == null) return;
        String action = data.getStringExtra("action");
        if (!"open_course_editor".equals(action)) return;
        String name = data.getStringExtra("course_name");
        int day = data.getIntExtra("course_day", -1);
        int start = data.getIntExtra("course_start", -1);
        boolean isExp = data.getBooleanExtra("course_is_experimental", false);
        if (name == null || name.isEmpty()) return;

        if (allCourses.isEmpty()) {
            loadCoursesFromLocal();
        }

        data.removeExtra("action");

        Course target = null;
        Course sameTypeTarget = null;
        for (Course c : allCourses) {
            if (c == null || c.isRemark) continue;
            if (!name.equals(c.name)) continue;
            if (day > 0 && start > 0 && c.dayOfWeek == day && c.startSection == start && c.isExperimental == isExp) {
                target = c;
                break;
            }
            if (c.isExperimental == isExp && sameTypeTarget == null) {
                sameTypeTarget = c;
            }
            if (target == null) {
                target = c;
            }
        }

        if (sameTypeTarget != null) {
            target = sameTypeTarget;
        }

        if (target == null) {
            Toast.makeText(this, "未找到对应课程", Toast.LENGTH_SHORT).show();
            return;
        }
        selectedCourseColorKey = buildCourseColorKey(target.name, target.isExperimental);
        drawGrid();
        showCourseDetailSheet(target, selectedCourseColorKey);
    }

    private void setupViewPager() {
        if (viewPager == null) return;
        viewPager.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                ScrollView sv = new ScrollView(parent.getContext());
                sv.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                GridLayout gl = new GridLayout(parent.getContext());
                gl.setColumnCount(8);
                gl.setPadding(10, 10, 10, 10);
                gl.setId(View.generateViewId());
                sv.addView(gl);
                return new RecyclerView.ViewHolder(sv){};
            }
            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                GridLayout gl = (GridLayout) ((ScrollView) holder.itemView).getChildAt(0);
                renderWeekGrid(gl, position + 1);
            }
            @Override public int getItemCount() { return totalWeeks; }
        });
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentWeek = position + 1;
                updateTitle();
            }
        });
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

        int[] palette = {
            MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer, 0),
            MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondaryContainer, 0),
            MaterialColors.getColor(this, com.google.android.material.R.attr.colorTertiaryContainer, 0),
            MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceVariant, 0),
            MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, 0),
            MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondary, 0),
            MaterialColors.getColor(this, com.google.android.material.R.attr.colorTertiary, 0),
            MaterialColors.getColor(this, com.google.android.material.R.attr.colorErrorContainer, 0)
        };
        int hash = Math.abs(courseName.hashCode());
        int color = palette[hash % palette.length];
        if (isExperimental) {
            color = MaterialColors.getColor(this, com.google.android.material.R.attr.colorTertiaryContainer, color);
        }
        return color;
    }

    private String buildCourseColorKey(String courseName, boolean isExperimental) {
        return courseName + (isExperimental ? "|EXP" : "|REG");
    }

    private boolean hasCustomCourseColor(String courseName, boolean isExperimental) {
        SharedPreferences mPrefs = getSharedPreferences(PREF_COURSE_COLORS, MODE_PRIVATE);
        String key = buildCourseColorKey(courseName, isExperimental);
        return mPrefs.contains(key) || mPrefs.contains(courseName);
    }

    private int getTimetableThemeColor() {
        int fallback = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, Color.parseColor("#4F7A5A"));
        return getSharedPreferences(PREF_NAME, MODE_PRIVATE).getInt(KEY_TIMETABLE_THEME_COLOR, fallback);
    }

    private int[] buildColorPalette() {
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

    private int pickReadableTextColor(int bgColor) {
        return ColorUtils.calculateLuminance(bgColor) < 0.5 ? Color.WHITE : Color.BLACK;
    }

    private void refreshRealtimeCourseHighlight() {
        if (viewPager == null || pageSchedule == null || pageSchedule.getVisibility() != View.VISIBLE) return;
        int week = getActualCurrentWeek();
        Calendar now = Calendar.getInstance();
        int day = now.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY ? 7 : now.get(Calendar.DAY_OF_WEEK) - 1;
        int slot = getCurrentTimeslotIndex();
        if (week != lastRealtimeWeek || day != lastRealtimeDay || slot != lastRealtimeSlot) {
            lastRealtimeWeek = week;
            lastRealtimeDay = day;
            lastRealtimeSlot = slot;
            drawGrid();
        }
    }

    private void renderWeekGrid(GridLayout grid, int week) {
        if (grid == null) return;
        grid.removeAllViews();
        int dp40 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 45, getResources().getDisplayMetrics());
        int dp120 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 120, getResources().getDisplayMetrics());
        int colorOnSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, ContextCompat.getColor(this, android.R.color.black));
        int colorOnSurfaceVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, colorOnSurface);
        int colorOutline = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline, colorOnSurfaceVariant);
        int themeColor = getTimetableThemeColor();
        boolean showGridLines = getSharedPreferences(PREF_NAME, MODE_PRIVATE).getBoolean(KEY_SHOW_GRID_LINES, true);
        boolean darkMode = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
            == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int highlightBg = ColorUtils.setAlphaComponent(themeColor, darkMode ? 160 : 130);

        String[] sectionTimes = {
            "8:00\n9:40",
            "10:00\n11:40",
            "14:00\n15:40",
            "16:00\n17:40",
            "19:00\n20:40"
        };

        int currentSlotIndex = -1;
        if (week == getActualCurrentWeek()) {
            currentSlotIndex = getCurrentTimeslotIndex();
        }

        for (int i = 0; i < 5; i++) {
            TextView t = new TextView(this);
            t.setText("第" + (i + 1) + "大节\n" + sectionTimes[i]);
            t.setTextColor(colorOnSurfaceVariant);
            t.setGravity(Gravity.CENTER);
            t.setLineSpacing(0f, 1f);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
            if (currentSlotIndex == i + 1) {
                applyActiveHeaderStyle(t, highlightBg, colorOutline, showGridLines);
            } else if (showGridLines) {
                android.graphics.drawable.GradientDrawable lineBg = new android.graphics.drawable.GradientDrawable();
                lineBg.setColor(android.graphics.Color.TRANSPARENT);
                lineBg.setStroke(1, colorOutline);
                t.setBackground(lineBg);
            }
            GridLayout.LayoutParams p = new GridLayout.LayoutParams(GridLayout.spec(i + 1), GridLayout.spec(0));
            p.width = dp40; p.height = dp120;
            grid.addView(t, p);
        }

        Calendar cal = getSemesterStartCalendar();
        cal.add(Calendar.WEEK_OF_YEAR, week - 1);
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            cal.add(Calendar.DAY_OF_MONTH, -1);
        }

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("M/dd", java.util.Locale.getDefault());
        String[] days = {"一", "二", "三", "四", "五", "六", "日"};
        Calendar today = Calendar.getInstance();
        for (int i = 0; i < 7; i++) {
            TextView t = new TextView(this);
            Calendar dayCal = (Calendar) cal.clone();
            String dateStr = sdf.format(cal.getTime());
            t.setText("周" + days[i] + "\n" + dateStr);
            t.setTextColor(colorOnSurfaceVariant);
            t.setGravity(Gravity.CENTER);
            boolean isToday = dayCal.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                    && dayCal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
            if (isToday) {
                applyActiveHeaderStyle(t, highlightBg, colorOutline, showGridLines);
            } else if (showGridLines) {
                android.graphics.drawable.GradientDrawable lineBg = new android.graphics.drawable.GradientDrawable();
                lineBg.setColor(android.graphics.Color.TRANSPARENT);
                lineBg.setStroke(1, colorOutline);
                t.setBackground(lineBg);
            }
            GridLayout.LayoutParams p = new GridLayout.LayoutParams(GridLayout.spec(0), GridLayout.spec(i + 1, 1f));
            p.width = 0; p.setGravity(Gravity.FILL_HORIZONTAL);
            grid.addView(t, p);
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        int todayOfWeek = today.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY ? 7 : today.get(Calendar.DAY_OF_WEEK) - 1;
        int dp2 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics());
        int glassBg = UiStyleHelper.resolveGlassCardColor(this);

        for (Course c : allCourses) {
            if (!c.isRemark && c.dayOfWeek >= 1 && c.dayOfWeek <= 7 && c.weeks != null && c.weeks.contains(week)) {
                MaterialCardView card = new MaterialCardView(this);
                card.setRadius(20);
                card.setCardElevation(0f);
                card.setStrokeColor(Color.TRANSPARENT);

                String colorKey = buildCourseColorKey(c.name, c.isExperimental);
                boolean hasCustomColor = hasCustomCourseColor(c.name, c.isExperimental);
                int customColor = getCourseColor(c.name, c.isExperimental);
                int cardBg = hasCustomColor ? ColorUtils.blendARGB(glassBg, customColor, 0.30f) : glassBg;
                card.setCardBackgroundColor(cardBg);

                int slot = (c.startSection - 1) / 2 + 1;
                boolean isCurrentCourse = week == getActualCurrentWeek()
                        && c.dayOfWeek == todayOfWeek
                        && slot == currentSlotIndex;
                boolean isSelected = colorKey.equals(selectedCourseColorKey);
                if (isCurrentCourse || isSelected) {
                    card.setStrokeWidth(dp2);
                    card.setStrokeColor(themeColor);
                } else {
                    card.setStrokeWidth(0);
                    card.setStrokeColor(Color.TRANSPARENT);
                }

                TextView tv = new TextView(this);
                String teacher = c.teacher == null ? "" : c.teacher.trim();
                String teacherLine = teacher.isEmpty() ? "" : "\n" + teacher;
                String location = (c.location == null || c.location.isEmpty()) ? "未定" : sanitizeLocation(c.location);
                tv.setText(c.name + (c.isExperimental ? "\n[实验]" : "") + teacherLine + "\n" + location);
                int textColor = pickReadableTextColor(cardBg);
                tv.setTextColor(textColor);
                tv.setTextSize(10); tv.setPadding(8, 8, 8, 8); tv.setGravity(Gravity.CENTER);
                card.addView(tv);
                card.setOnClickListener(v -> {
                    selectedCourseColorKey = colorKey;
                    drawGrid();
                    showCourseDetailSheet(c, colorKey);
                });
                int row = (c.startSection - 1) / 2 + 1;
                int col = c.dayOfWeek;
                if (row < 1) row = 1; if (row > 5) row = 5;
                if (col < 1) col = 1; if (col > 7) col = 7;
                GridLayout.LayoutParams p = new GridLayout.LayoutParams(GridLayout.spec(row), GridLayout.spec(col, 1f));
                p.width = 0; p.height = dp120 - 10; p.setMargins(4, 4, 4, 4);
                if (showGridLines) {
                    card.setPreventCornerOverlap(false);
                }
                grid.addView(card, p);
            }
        }
    }

    private void drawGrid() {
        moveToCurrentWeek(false, true);
        updateTitle();
        updateNextCourseNotice();
    }

    private void applyActiveHeaderStyle(TextView target, int backgroundColor, int outlineColor, boolean showGridLines) {
        if (target == null) return;
        target.setTextColor(pickReadableTextColor(backgroundColor));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg.setCornerRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics()));
        bg.setColor(backgroundColor);
        if (showGridLines) {
            bg.setStroke(1, outlineColor);
        }
        target.setBackground(bg);
        int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics());
        target.setPadding(pad, pad, pad, pad);
    }

    private int getCurrentTimeslotIndex() {
        Calendar now = Calendar.getInstance();
        int currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int[] starts = {8 * 60, 10 * 60, 14 * 60, 16 * 60, 19 * 60};
        int[] ends = {9 * 60 + 40, 11 * 60 + 40, 15 * 60 + 40, 17 * 60 + 40, 20 * 60 + 40};
        for (int i = 0; i < starts.length; i++) {
            if (currentMinutes >= starts[i] && currentMinutes <= ends[i]) {
                return i + 1;
            }
        }
        return -1;
    }

    private void updateNextCourseNotice() {
        if (cardNextCourseNotice == null || tvNextCourseNotice == null) return;
        boolean inSchedulePage = pageSchedule != null && pageSchedule.getVisibility() == View.VISIBLE;
        if (!inSchedulePage || nextCourseNoticeDismissed) {
            cardNextCourseNotice.setVisibility(View.GONE);
            return;
        }

        NextCourseNotice next = buildTodayNextCourseMessage();
        if (next == null) {
            cardNextCourseNotice.setVisibility(View.GONE);
            return;
        }

        applyNextCourseNoticeStyle();
        tvNextCourseNotice.setText(next.message);
        cardNextCourseNotice.setVisibility(View.VISIBLE);
    }

    private void applyNextCourseNoticeStyle() {
        if (cardNextCourseNotice == null || tvNextCourseNotice == null) return;
        int cardColor = UiStyleHelper.resolveGlassCardColor(this);
        int text = pickReadableTextColor(cardColor);

        cardNextCourseNotice.setCardBackgroundColor(cardColor);
        cardNextCourseNotice.setStrokeWidth(0);
        cardNextCourseNotice.setStrokeColor(Color.TRANSPARENT);
        tvNextCourseNotice.setTextColor(text);
        if (btnCloseNextCourseNotice != null) {
            btnCloseNextCourseNotice.setColorFilter(ColorUtils.setAlphaComponent(text, 170));
        }
    }

    private String formatDuration(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int mins = (totalSeconds % 3600) / 60;
        int secs = totalSeconds % 60;
        if (hours > 0) {
            return hours + "小时" + mins + "分钟" + secs + "秒";
        }
        if (mins > 0) {
            return mins + "分钟" + secs + "秒";
        }
        return secs + "秒";
    }

    private NextCourseNotice buildTodayNextCourseMessage() {
        int actualWeek = getActualCurrentWeek();
        Calendar now = Calendar.getInstance();
        int today = now.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY ? 7 : now.get(Calendar.DAY_OF_WEEK) - 1;
        int currentSeconds = now.get(Calendar.HOUR_OF_DAY) * 3600 + now.get(Calendar.MINUTE) * 60 + now.get(Calendar.SECOND);
        int[] starts = {8 * 3600, 10 * 3600, 14 * 3600, 16 * 3600, 19 * 3600};

        Course nextCourse = null;
        int nextStartSeconds = Integer.MAX_VALUE;
        for (Course c : allCourses) {
            if (c == null || c.isRemark) continue;
            if (c.dayOfWeek != today) continue;
            if (c.weeks == null || !c.weeks.contains(actualWeek)) continue;

            int slot = (c.startSection - 1) / 2;
            if (slot < 0 || slot >= starts.length) continue;
            int start = starts[slot];
            if (start > currentSeconds && start < nextStartSeconds) {
                nextStartSeconds = start;
                nextCourse = c;
            }
        }

        if (nextCourse == null) return null;
        int seconds = nextStartSeconds - currentSeconds;
        String startTime = String.format(Locale.getDefault(), "%02d:%02d", nextStartSeconds / 3600, (nextStartSeconds % 3600) / 60);
        String location = (nextCourse.location == null || nextCourse.location.isEmpty()) ? "未定" : sanitizeLocation(nextCourse.location);
        String courseLabel = nextCourse.name + (nextCourse.isExperimental ? "[实验]" : "");

        String plain = "下节 " + startTime + " " + courseLabel + "\n还有" + formatDuration(seconds) + "，地点:" + location;
        SpannableString styled = new SpannableString(plain);
        int timeStart = plain.indexOf(startTime);
        if (timeStart >= 0) {
            styled.setSpan(new StyleSpan(Typeface.BOLD), timeStart, timeStart + startTime.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        int locStart = plain.lastIndexOf(location);
        if (locStart >= 0) {
            styled.setSpan(new StyleSpan(Typeface.BOLD), locStart, locStart + location.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return new NextCourseNotice(styled);
    }

    private static class NextCourseNotice {
        final CharSequence message;

        NextCourseNotice(CharSequence message) {
            this.message = message;
        }
    }

    private int getActualCurrentWeek() {
        Calendar now = Calendar.getInstance();
        Calendar start = getSemesterStartCalendar();

        Calendar weekStart = (Calendar) start.clone();
        while (weekStart.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            weekStart.add(Calendar.DAY_OF_MONTH, -1);
        }
        weekStart.set(Calendar.HOUR_OF_DAY, 0);
        weekStart.set(Calendar.MINUTE, 0);
        weekStart.set(Calendar.SECOND, 0);
        weekStart.set(Calendar.MILLISECOND, 0);

        long diff = now.getTimeInMillis() - weekStart.getTimeInMillis();
        int calculatedWeek = (int) (diff / (7 * 24 * 60 * 60 * 1000.0)) + 1;
        if (calculatedWeek < 1) calculatedWeek = 1;
        if (calculatedWeek > totalWeeks) calculatedWeek = totalWeeks;
        return calculatedWeek;
    }

    private void jumpToActualCurrentWeek(boolean smoothScroll) {
        int actualWeek = getActualCurrentWeek();
        if (actualWeek != currentWeek) {
            currentWeek = actualWeek;
            moveToCurrentWeek(smoothScroll, true);
            updateTitle();
        }
    }

    private int getSafeWeekIndex() {
        int safeWeek = currentWeek;
        if (safeWeek < 1) safeWeek = 1;
        if (safeWeek > totalWeeks) safeWeek = totalWeeks;
        currentWeek = safeWeek;
        return safeWeek - 1;
    }

    private void moveToCurrentWeek(boolean smoothScroll, boolean notifyAdapter) {
        if (viewPager == null || viewPager.getAdapter() == null) return;
        if (notifyAdapter) {
            viewPager.getAdapter().notifyDataSetChanged();
        }
        int target = getSafeWeekIndex();
        int count = viewPager.getAdapter().getItemCount();
        if (count <= 0) return;
        if (target >= count) target = count - 1;
        viewPager.setCurrentItem(target, smoothScroll);
    }

private void extractAllTables(String passedCookie) {
        String cookie = passedCookie;
        if (cookie == null || cookie.isEmpty()) {
            cookie = CookieManager.getInstance().getCookie(TARGET_URL);
            if (cookie == null || cookie.isEmpty()) {
                cookie = CookieManager.getInstance().getCookie(BASE_URL);
            }
        }
        if (cookie == null || cookie.isEmpty()) {
            Toast.makeText(this, "请先进入系统登录", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "正在提取课表数据...", Toast.LENGTH_SHORT).show();

        final String finalCookie = cookie;
        new Thread(() -> {
            try {
                ExtractOutcome outcome = scrapeAllTablesOnce(finalCookie);
                if (!outcome.hasAnySuccess || countNonRemarkCourses(outcome.courses) == 0) {
                    String refreshedCookie = trySilentLoginAndGetCookie(finalCookie);
                    if (refreshedCookie != null && !refreshedCookie.isEmpty()) {
                        ExtractOutcome retryOutcome = scrapeAllTablesOnce(refreshedCookie);
                        if (retryOutcome.hasAnySuccess && countNonRemarkCourses(retryOutcome.courses) > 0) {
                            outcome = retryOutcome;
                        }
                    }
                }

                if (!outcome.hasAnySuccess || countNonRemarkCourses(outcome.courses) == 0) {
                    runOnUiThread(() -> Toast.makeText(this, "刷新失败：未获取到课表页面，请确认已在教务系统登录", Toast.LENGTH_LONG).show());
                    return;
                }
                
                List<Course> finalResult = deduplicate(outcome.courses);

                runOnUiThread(() -> {
                    allCourses.clear();
                    allCourses.addAll(finalResult);
                    calculateCurrentWeek();
                    saveCoursesToLocal();
                    updateScheduleViewState();
                    moveToCurrentWeek(true, true);
                    drawGrid();
                    Toast.makeText(this, "刷新完成，共 " + allCourses.size() + " 门课程", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Log.e(TAG, "Extraction error", e);
                runOnUiThread(() -> Toast.makeText(this, "刷新失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private static class ExtractOutcome {
        final List<Course> courses;
        final boolean hasAnySuccess;

        ExtractOutcome(List<Course> courses, boolean hasAnySuccess) {
            this.courses = courses;
            this.hasAnySuccess = hasAnySuccess;
        }
    }

    private ExtractOutcome scrapeAllTablesOnce(String cookie) {
        List<Course> combinedList = new ArrayList<>();
        boolean hasAnySuccess = false;
        try {
            String normalHtml = fetch(TARGET_URL, cookie);
            parseRegular(normalHtml, combinedList);
            hasAnySuccess = true;
        } catch (Exception e) {
            Log.e(TAG, "Regular parse error", e);
        }

        try {
            String expHtml = fetch(EXPERIMENT_URL, cookie);
            parseExperiment(expHtml, combinedList);
            hasAnySuccess = true;
        } catch (Exception e) {
            Log.e(TAG, "Experiment parse error", e);
        }
        return new ExtractOutcome(combinedList, hasAnySuccess);
    }

    private int countNonRemarkCourses(List<Course> courses) {
        int count = 0;
        for (Course c : courses) {
            if (c != null && !c.isRemark) {
                count++;
            }
        }
        return count;
    }

    
    private void parseRegular(String html, List<Course> out) {
        Document doc = Jsoup.parse(html);
        Elements trs = doc.select(".qz-weeklyTable-thbody .qz-weeklyTable-tr");
        for (int i = 0; i < trs.size(); i++) {
            int startSec = i * 2 + 1; // 0->1, 1->3, 2->5, 3->7, 4->9
            Elements tds = trs.get(i).select("td[name=kbDataTd]");
            for (int j = 0; j < tds.size(); j++) {
                int dayOfWeek = j + 1; // 0->1, 6->7
                Elements courseElements = tds.get(j).select("li.courselists-item");
                for (Element courseElem : courseElements) {
                    parseCourseElement(courseElem, dayOfWeek, startSec, 0, out, "Regular");
                }
            }
        }
        
        Elements tfootTexts = doc.select(".qz-weeklyTable-detailtext .td-cell");
        if (tfootTexts.size() > 0) {
            String bottomText = tfootTexts.get(0).text();
            String[] parts = bottomText.split(";");
            for (String part : parts) {
                if (part.trim().isEmpty()) continue;
                Course c = new Course();
                c.name = part.trim();
                c.location = ""; 
                c.weeks = parseWeeks(part);
                if (c.weeks.isEmpty()) {
                    for(int w=1; w<=20; w++) c.weeks.add(w);
                }
                c.dayOfWeek = 0;
                c.startSection = 0;
                c.isRemark = true;
                c.isExperimental = false;
                out.add(c);
            }
        }
    }

    private void parseExperiment(String html, List<Course> out) {
        String sourceLabel = "Experiment";
        Document doc = Jsoup.parse(html);
        Elements tables = doc.select("table");
        Element mainTable = null;
        for (Element t : tables) {
            if (t.text().contains("第一大节") && t.select("tr").size() >= 4) {
                mainTable = t;
                break;
            }
        }
        if (mainTable == null) {
            Log.w(TAG, "No suitable table found for " + sourceLabel);
            return;
        }

        Elements trs = mainTable.select("tr");
        int maxRows = trs.size();
        int maxCols = 20;
        Element[][] grid = new Element[maxRows][maxCols];

        for (int r = 0; r < maxRows; r++) {
            Element tr = trs.get(r);
            Elements tds = tr.select("> td, > th");
            int ptr = 0;
            for (Element td : tds) {
                while (ptr < maxCols && grid[r][ptr] != null) ptr++;
                if (ptr >= maxCols) break;

                int rowspan = 1, colspan = 1;
                try { if (td.hasAttr("rowspan")) rowspan = Integer.parseInt(td.attr("rowspan").trim()); } catch (Exception ignored) {}
                try { if (td.hasAttr("colspan")) colspan = Integer.parseInt(td.attr("colspan").trim()); } catch (Exception ignored) {}

                for (int i = 0; i < rowspan; i++) {
                    for (int j = 0; j < colspan; j++) {
                        if (r + i < maxRows && ptr + j < maxCols) {
                            grid[r + i][ptr + j] = td;
                        }
                    }
                }
                ptr += colspan;
            }
        }

        int actualCols = 0;
        for (int c = 0; c < maxCols; c++) {
            if (grid[0][c] != null || (maxRows > 1 && grid[1][c] != null)) {
                actualCols = c + 1;
            }
        }

        int[] rowToSession = new int[maxRows];
        for(int r = 0; r < maxRows; r++) {
            for(int c = 0; c < maxCols; c++) {
                if (grid[r][c] == null) continue;
                String txt = grid[r][c].text().trim();
                if (txt.contains("第一大节")) { rowToSession[r] = 1; break; }
                else if (txt.contains("第二大节")) { rowToSession[r] = 3; break; }
                else if (txt.contains("第三大节")) { rowToSession[r] = 5; break; }
                else if (txt.contains("第四大节")) { rowToSession[r] = 7; break; }
                else if (txt.contains("第五大节")) { rowToSession[r] = 9; break; }
                else if (txt.contains("第六大节")) { rowToSession[r] = 11; break; }
            }
            if (r > 0 && rowToSession[r] == 0) rowToSession[r] = rowToSession[r - 1];
        }

        int[] rowToWeek = new int[maxRows];
        for(int r = 0; r < maxRows; r++) {
            for (int c = 0; c < 2 && c < maxCols; c++) {
                if (grid[r][c] != null) {
                    String leftTxt = grid[r][c].text().trim();
                    Matcher m = Pattern.compile("^\\s*(\\d+)\\s*$").matcher(leftTxt);
                    if (m.find()) {
                        rowToWeek[r] = Integer.parseInt(m.group(1));
                        break;
                    }
                }
            }
            if (r > 0 && rowToWeek[r] == 0) rowToWeek[r] = rowToWeek[r - 1];
        }

        int[] colToDay = new int[maxCols];
        for(int r = 0; r < Math.min(3, maxRows); r++) {
            for(int c = 0; c < maxCols; c++) {
                if(grid[r][c] == null) continue;
                String txt = grid[r][c].text().trim();
                if(txt.contains("星期一") || txt.contains("周一")) colToDay[c] = 1;
                else if(txt.contains("星期二") || txt.contains("周二")) colToDay[c] = 2;
                else if(txt.contains("星期三") || txt.contains("周三")) colToDay[c] = 3;
                else if(txt.contains("星期四") || txt.contains("周四")) colToDay[c] = 4;
                else if(txt.contains("星期五") || txt.contains("周五")) colToDay[c] = 5;
                else if(txt.contains("星期六") || txt.contains("周六")) colToDay[c] = 6;
                else if(txt.contains("星期日") || txt.contains("周日")) colToDay[c] = 7;
            }
        }
        
        boolean hasDayHeaders = false;
        for(int d : colToDay) { if (d > 0) hasDayHeaders = true; }
        
        if (!hasDayHeaders) {
             int startCol = actualCols - 7;
             if (startCol < 0) startCol = 0;
             for (int c = startCol; c < actualCols; c++) {
                 colToDay[c] = c - startCol + 1;
             }
        }

        Set<Element> processed = new HashSet<>();
        for (int r = 0; r < maxRows; r++) {
            for (int c = 0; c < maxCols; c++) {
                Element td = grid[r][c];
                if (td == null || !processed.add(td)) continue;

                int dayOfWeek = colToDay[c];
                int startSec = rowToSession[r];
                if (dayOfWeek < 1 || dayOfWeek > 7 || startSec < 1) continue;

                Elements courseElements = td.select("div.kbcontent, div.mini_kb_content, li.courselists-item");
                if (courseElements.isEmpty() && (!td.text().isEmpty() && (td.text().contains("周") || td.text().contains("地点")))) {
                    courseElements.add(td);
                }

                for (Element courseElem : courseElements) {
                    if (courseElem.hasClass("kbcontent") && courseElem.html().contains("----------------------")) {
                        String[] parts = courseElem.html().split("----------------------|<hr[^>]*>");
                        for (String part : parts) {
                            if (part.trim().isEmpty()) continue;
                            Element wrapper = Jsoup.parseBodyFragment("<div>" + part + "</div>").selectFirst("div");
                            parseCourseElement(wrapper, dayOfWeek, startSec, rowToWeek[r], out, sourceLabel);
                        }
                    } else {
                        parseCourseElement(courseElem, dayOfWeek, startSec, rowToWeek[r], out, sourceLabel);
                    }
                }
            }
        }
    }

    private void parseCourseElement(Element container, int dayOfWeek, int startSection, int blockWeek, List<Course> out, String sourceLabel) {
        String text = container.text().trim();
        if (text.isEmpty() || text.length() < 2) return;
        
        Course c = new Course();
        
        Element nameElem = container.selectFirst("font[title=课程名称], .qz-hasCourse-title, strong, b");
        if (nameElem != null) {
            c.name = nameElem.text().trim();
        } else {
            String[] lines = text.split("\\s+|\\n");
            c.name = lines.length > 0 ? lines[0] : "未知课程";
        }
        
        Element roomElem = container.selectFirst("font[title=教室]");
        if (roomElem != null) {
            c.location = roomElem.text().trim();
        } else {
            Element detailItem = container.selectFirst(".qz-hasCourse-detailitem");
            if (detailItem != null && !detailItem.text().contains("老师") && !detailItem.text().contains("时间")) {
                c.location = detailItem.text().trim();
            } else {
                c.location = regex(text, "地点[：:]([^\\s；;]+)");
                if (c.location.isEmpty()) c.location = regex(text, "@([^\\s]+)");
            }
        }

        if (!"Experiment".equals(sourceLabel)) {
            c.teacher = extractTeacherForRegular(container, text);
            c.location = normalizeRegularLocation(c.location, text);
        }
        
        c.weeks = parseWeeks(text);
        if (c.weeks.isEmpty() && blockWeek > 0) {
            c.weeks.add(blockWeek);
        } else if (c.weeks.isEmpty()) {
            for(int i = 1; i <= 20; i++) c.weeks.add(i); 
        }
        
        c.isExperimental = sourceLabel.equals("Experiment") || text.contains("实验");
        c.dayOfWeek = dayOfWeek;
        c.startSection = startSection;

        Log.d(TAG, "Parsed >> " + c.name + " Wks: " + c.weeks + " (Exp: "+c.isExperimental+")");
        out.add(c);
    }

    private String extractLocationInParentheses(String location) {
        if (location == null || location.isEmpty()) return "";
        Matcher matcher = Pattern.compile("[（(]([^（）()]+)[）)]").matcher(location);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return location.trim();
    }

    private String extractTeacherForRegular(Element container, String fullText) {
        if (container != null) {
            Elements items = container.select(".qz-tooltipContent-detailitem");
            for (Element item : items) {
                String itemText = item.text();
                if (itemText.contains("老师")) {
                    String teacher = regex(itemText, "老师[：:]\\s*([^；;]+)");
                    if (!teacher.isEmpty()) {
                        return teacher;
                    }
                }
            }
        }
        return regex(fullText, "老师[：:]\\s*([^；;]+)");
    }

    private String normalizeRegularLocation(String parsedLocation, String fullText) {
        String fromFullText = regex(fullText, "地点[：:][^；;]*[（(]([^（）()]+)[）)]");
        if (!fromFullText.isEmpty()) {
            return sanitizeLocation(fromFullText);
        }
        return sanitizeLocation(extractLocationInParentheses(parsedLocation));
    }

    private String sanitizeLocation(String location) {
        if (location == null) return "";
        String normalized = location.trim();
        while (normalized.startsWith("@") || normalized.startsWith("＠")) {
            normalized = normalized.substring(1).trim();
        }
        return normalized;
    }

    private String trySilentLoginAndGetCookie(String fallbackCookie) {
        HttpURLConnection conn = null;
        try {
            String cookie = CookieManager.getInstance().getCookie(BASE_URL);
            if (cookie == null || cookie.isEmpty()) {
                cookie = fallbackCookie;
            }
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
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (entry.getKey() != null && "Set-Cookie".equalsIgnoreCase(entry.getKey()) && entry.getValue() != null) {
                    for (String one : entry.getValue()) {
                        CookieManager.getInstance().setCookie(BASE_URL, one);
                    }
                }
            }
            CookieManager.getInstance().flush();
        } catch (Exception e) {
            Log.w(TAG, "Silent login refresh failed", e);
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
        } catch (Exception e) {
            Log.w(TAG, "Silent login verify failed", e);
            return null;
        } finally {
            if (verifyConn != null) {
                verifyConn.disconnect();
            }
        }
    }

    private List<Course> deduplicate(List<Course> list) {
        Map<String, Course> map = new HashMap<>();
        for (Course c : list) {
            if (c.isRemark) {
                String key = "remark|" + c.name;
                if (map.containsKey(key)) {
                    Set<Integer> weeks = new HashSet<>(map.get(key).weeks);
                    weeks.addAll(c.weeks);
                    map.get(key).weeks = new ArrayList<>(weeks);
                    Collections.sort(map.get(key).weeks);
                } else {
                    map.put(key, c);
                }
                continue;
            }
            if (c.dayOfWeek < 1 || c.dayOfWeek > 7) continue;
            String key = c.name + "|" + c.dayOfWeek + "|" + c.startSection + "|" + (c.isExperimental ? "Exp" : "Reg");
            if (map.containsKey(key)) {
                Set<Integer> weeks = new HashSet<>(map.get(key).weeks);
                weeks.addAll(c.weeks);
                map.get(key).weeks = new ArrayList<>(weeks);
                Collections.sort(map.get(key).weeks); // 有序美观
            } else {
                map.put(key, c);
            }
        }
        return new ArrayList<>(map.values());
    }

    private String fetch(String url, String cookie) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestProperty("Cookie", cookie);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
            return sb.toString();
        }
    }

    private String regex(String s, String p) {
        Matcher m = Pattern.compile(p).matcher(s);
        return m.find() ? m.group(1).trim() : "";
    }

    private List<Integer> parseWeeks(String str) {
        Set<Integer> set = new HashSet<>();
        try {
            Matcher m = Pattern.compile("([\\d,，\\-]+)周").matcher(str);
            while (m.find()) {
                String clean = m.group(1).replace("　", "").replace(" ", "");
                for (String seg : clean.split("[,，]")) {
                    if (seg.contains("-")) {
                        String[] r = seg.split("-");
                        if (r.length >= 2) {
                            int start = Integer.parseInt(r[0].trim());
                            int end = Integer.parseInt(r[1].trim());
                            for (int i = start; i <= end; i++) set.add(i);
                        }
                    } else if (!seg.isEmpty()) {
                        set.add(Integer.parseInt(seg.trim()));
                    }
                }
            }
        } catch (Exception ignored) {}
        List<Integer> list = new ArrayList<>(set);
        Collections.sort(list);
        return list;
    }

    private void calculateCurrentWeek() {
        currentWeek = getActualCurrentWeek();
    }




    private void updateBackground() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String mode = prefs.getString("bg_mode", "color");
        int[] palette = buildBackgroundPalette();
        int bgIndex = prefs.getInt("bg_color_index", 0);
        if (bgIndex < 0 || bgIndex >= palette.length) bgIndex = 0;
        int bgColor = palette[bgIndex];
        boolean imageMode = "image".equals(mode);
        boolean darkMode = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;

        boolean imageShown = false;
        if (rootMain != null) {
            rootMain.setBackgroundColor(imageMode ? android.graphics.Color.TRANSPARENT : bgColor);
        }

        String uriStr = prefs.getString("bg_image_uri", "");
        if (imageMode && !uriStr.isEmpty() && ivBackground != null) {
            try {
                ivBackground.setVisibility(View.VISIBLE);
                ivBackground.setImageURI(android.net.Uri.parse(uriStr));
                imageShown = true;
            } catch (Exception e) {
                ivBackground.setVisibility(View.GONE);
            }
        } else if (ivBackground != null) {
            ivBackground.setVisibility(View.GONE);
        }

        if (rootMain != null && imageMode && !imageShown) {
            rootMain.setBackgroundColor(bgColor);
        }

        if (vBackgroundScrim != null) {
            if (imageMode && imageShown && darkMode) {
                vBackgroundScrim.setVisibility(View.VISIBLE);
                vBackgroundScrim.setAlpha(0.34f);
            } else {
                vBackgroundScrim.setVisibility(View.GONE);
            }
        }

        applyMaterialScaffoldStyle(bgColor, imageMode);
        applyMainSettingsGlassStyle();
    }

    private void applyMainSettingsGlassStyle() {
        if (cardRemarks instanceof MaterialCardView) {
            UiStyleHelper.styleGlassCard((MaterialCardView) cardRemarks, this);
        }
        if (itemAccountSync instanceof MaterialCardView) {
            UiStyleHelper.styleGlassCard((MaterialCardView) itemAccountSync, this);
        }
        if (itemDisplaySettings instanceof MaterialCardView) {
            UiStyleHelper.styleGlassCard((MaterialCardView) itemDisplaySettings, this);
        }
        if (itemDataManagement instanceof MaterialCardView) {
            UiStyleHelper.styleGlassCard((MaterialCardView) itemDataManagement, this);
        }
    }

    private int[] buildBackgroundPalette() {
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

    private void updateScheduleViewState() {
        boolean empty = allCourses.isEmpty();
        tvEmptyHint.setVisibility(empty ? View.VISIBLE : View.GONE);
        viewPager.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty && cardNextCourseNotice != null) {
            cardNextCourseNotice.setVisibility(View.GONE);
        }
    }


    private void clearCurrentSchedule(boolean includeCookie) {
        allCourses.clear();
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        prefs.edit().remove(KEY_COURSES_JSON).apply();
        getSharedPreferences("course_colors", MODE_PRIVATE).edit().clear().apply();
        clearAppCacheDirs();
        if (includeCookie) {
            CookieManager.getInstance().removeSessionCookies(null);
            CookieManager.getInstance().flush();
        }
        updateScheduleViewState();
        currentWeek = 1;
        drawGrid();
        Toast.makeText(this, includeCookie ? "已清空本地课表与登录状态" : "已清空本地课表（保留登录状态）", Toast.LENGTH_SHORT).show();
    }

    private void clearLoginState() {
        CookieManager.getInstance().removeSessionCookies(null);
        CookieManager.getInstance().flush();
        Toast.makeText(this, "已退出登录", Toast.LENGTH_SHORT).show();
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

    private String buildJson() throws Exception {
        JSONArray arr = new JSONArray();
        for (Course c : allCourses) {
            JSONObject o = new JSONObject();
            o.put("name", c.name); 
            o.put("teacher", c.teacher);
            o.put("dayOfWeek", c.dayOfWeek); 
            o.put("startSection", c.startSection);
            o.put("location", c.location); 
            o.put("isExperimental", c.isExperimental);
            o.put("isRemark", c.isRemark);
            JSONArray w = new JSONArray(); 
            if (c.weeks != null) for (int week : c.weeks) w.put(week); 
            o.put("weeks", w);
            arr.put(o);
        }
        return arr.toString();
    }

    private void parseJson(String json) throws Exception {
        JSONArray arr = new JSONArray(json); 
        allCourses.clear();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i); 
            Course c = new Course();
            c.name = o.getString("name"); 
            c.teacher = o.optString("teacher", "");
            c.dayOfWeek = o.getInt("dayOfWeek"); 
            c.startSection = o.getInt("startSection");
            c.location = o.optString("location", ""); 
            c.isExperimental = o.optBoolean("isExperimental", false);
            c.isRemark = o.optBoolean("isRemark", false);
            c.weeks = new ArrayList<>(); 
            JSONArray w = o.getJSONArray("weeks"); 
            for (int k = 0; k < w.length(); k++) c.weeks.add(w.getInt(k));
            allCourses.add(c);
        }
    }

    private Calendar getSemesterStartCalendar() {
        Calendar start = Calendar.getInstance();
        if (semesterStartDateMs != 0) {
            start.setTimeInMillis(semesterStartDateMs);
        } else {
            // Default logic: find the Monday of the current week, assuming current week is 1
            // Actually, without a saved date, we can assume the current week is 1 by default, or just use today's week as week 1.
            start.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
            start.set(Calendar.HOUR_OF_DAY, 0);
            start.set(Calendar.MINUTE, 0);
            start.set(Calendar.SECOND, 0);
            start.set(Calendar.MILLISECOND, 0);
        }
        return start;
    }

    private void saveCoursesToLocal() {
        try { getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit().putString(KEY_COURSES_JSON, buildJson()).apply(); } catch (Exception ignored) {}
    }

    private void loadCoursesFromLocal() {
        String json = getSharedPreferences(PREF_NAME, MODE_PRIVATE).getString(KEY_COURSES_JSON, "");
        if (!json.isEmpty()) { 
            try { 
                parseJson(json); 
                calculateCurrentWeek(); 
                updateScheduleViewState(); 
                drawGrid(); 
            } catch (Exception ignored) {} 
        }
    }

    private void exportTable() {
        try {
            String json = buildJson();
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("CourseTable", json));
            Toast.makeText(this, "课表已导出至剪贴板", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void importTable() {
        try {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
                String json = clipboard.getPrimaryClip().getItemAt(0).getText().toString();
                parseJson(json);
                getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                        .edit()
                        .putString("bg_mode", "color")
                        .putInt("bg_color_index", 0)
                        .apply();
                saveCoursesToLocal();
                calculateCurrentWeek();
                updateScheduleViewState();
                updateBackground();
                drawGrid();
                Toast.makeText(this, "导入成功", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "导入失败，数据格式可能不正确", Toast.LENGTH_SHORT).show();
        }
    }

    private void showCourseDetailSheet(Course c, String colorKey) {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics());
        layout.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText(c.name + (c.isExperimental ? " [实验]" : ""));
        title.setTextSize(18f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK));
        layout.addView(title);

        int colorOnSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK);
        int colorOnSurfaceVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.DKGRAY);

        LinearLayout rowsContainer = new LinearLayout(this);
        rowsContainer.setOrientation(LinearLayout.VERTICAL);
        rowsContainer.setPadding(0, pad / 2, 0, pad / 3);

        final TextView[] teacherRef = new TextView[1];
        final TextView[] locationRef = new TextView[1];
        final TextView[] weeksRef = new TextView[1];

        teacherRef[0] = addEditableInfoRow(rowsContainer, "教师", c.teacher == null || c.teacher.trim().isEmpty() ? "未定" : c.teacher.trim(),
            v -> showTeacherPicker(c, () -> onCourseInfoUpdated(c, teacherRef[0], locationRef[0], weeksRef[0])), colorOnSurface, colorOnSurfaceVariant);

        locationRef[0] = addEditableInfoRow(rowsContainer, "地点", c.location == null || c.location.trim().isEmpty() ? "未定" : sanitizeLocation(c.location),
            v -> showLocationPicker(c, () -> onCourseInfoUpdated(c, teacherRef[0], locationRef[0], weeksRef[0])), colorOnSurface, colorOnSurfaceVariant);

        weeksRef[0] = addEditableInfoRow(rowsContainer, "周次", formatWeeksForDisplay(c.weeks),
            v -> showWeeksPicker(c, () -> onCourseInfoUpdated(c, teacherRef[0], locationRef[0], weeksRef[0])), colorOnSurface, colorOnSurfaceVariant);

        addEditableInfoRow(rowsContainer, "节次", "周" + c.dayOfWeek + " 第" + ((c.startSection - 1) / 2 + 1) + "大节", null, colorOnSurface, colorOnSurfaceVariant);
        layout.addView(rowsContainer);

        TextView colorTitle = new TextView(this);
        colorTitle.setText("课程颜色");
        colorTitle.setTextSize(14f);
        colorTitle.setTypeface(null, Typeface.BOLD);
        colorTitle.setTextColor(colorOnSurfaceVariant);
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
        dialog.setOnDismissListener(d -> {
            selectedCourseColorKey = null;
            drawGrid();
        });
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
            locationValue.setText(c.location == null || c.location.trim().isEmpty() ? "未定" : sanitizeLocation(c.location));
        }
        if (weeksValue != null) {
            weeksValue.setText(formatWeeksForDisplay(c.weeks));
        }
        saveCoursesToLocal();
        updateNextCourseNotice();
        drawGrid();
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
        new MaterialAlertDialogBuilder(this)
                .setTitle("选择教师")
                .setSingleChoiceItems(options.toArray(new String[0]), checked, (dialog, which) -> picked[0] = which)
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
        for (Course item : allCourses) {
            if (item == null || item.isRemark) continue;
            String location = sanitizeLocation(item.location == null ? "" : item.location);
            if (!location.isEmpty() && !options.contains(location)) {
                options.add(location);
            }
        }
        String current = sanitizeLocation(c.location == null ? "" : c.location);
        int checked = current.isEmpty() ? 0 : options.indexOf(current);
        if (checked < 0) {
            options.add(current);
            checked = options.size() - 1;
        }
        final int[] picked = {checked};
        new MaterialAlertDialogBuilder(this)
                .setTitle("选择上课地点")
                .setSingleChoiceItems(options.toArray(new String[0]), checked, (dialog, which) -> picked[0] = which)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> {
                    String selected = options.get(picked[0]);
                    c.location = "未定".equals(selected) ? "" : selected;
                    if (afterPick != null) afterPick.run();
                })
                .show();
    }

    private void showWeeksPicker(Course c, Runnable afterPick) {
        int maxWeeks = Math.max(totalWeeks, 20);
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

        new MaterialAlertDialogBuilder(this)
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

    private void renderColorSlider(LinearLayout container, Course c, String colorKey) {
        container.removeAllViews();
        SharedPreferences prefs = getSharedPreferences(PREF_COURSE_COLORS, MODE_PRIVATE);
        boolean hasCustom = prefs.contains(colorKey) || prefs.contains(c.name);
        int current = getCourseColor(c.name, c.isExperimental);
        int[] palette = buildColorPalette();

        addColorDot(container, Color.TRANSPARENT, !hasCustom, true, v -> {
            prefs.edit().remove(colorKey).remove(c.name).apply();
            saveCoursesToLocal();
            drawGrid();
            renderColorSlider(container, c, colorKey);
        });

        for (int color : palette) {
            boolean selected = hasCustom && current == color;
            addColorDot(container, color, selected, false, v -> {
                prefs.edit().putInt(colorKey, color).apply();
                saveCoursesToLocal();
                drawGrid();
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
        int outline = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline, Color.GRAY);
        int selectedColor = getTimetableThemeColor();
        dot.setStrokeColor(selected ? selectedColor : outline);

        if (isDefault) {
            dot.setCardBackgroundColor(UiStyleHelper.resolveGlassCardColor(this));
            TextView icon = new TextView(this);
            icon.setText("⊘");
            icon.setGravity(Gravity.CENTER);
            icon.setTextSize(16f);
            icon.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.DKGRAY));
            dot.addView(icon, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            dot.setCardBackgroundColor(color);
        }
        dot.setOnClickListener(click);
        container.addView(dot);
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
        title.setTypeface(null, Typeface.BOLD);
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
        dialog.setContentView(layout);
        dialog.show();
    }

    private void exportCookie() {
        String cookie = CookieManager.getInstance().getCookie(TARGET_URL);
        if (cookie == null || cookie.isEmpty()) {
            cookie = CookieManager.getInstance().getCookie(BASE_URL);
        }
        if (cookie != null && !cookie.isEmpty()) {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Cookie", cookie));
            Toast.makeText(this, "Cookie已导出至剪贴板", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "暂无Cookie", Toast.LENGTH_SHORT).show();
        }
    }

    private void importCookie() {
        try {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
                String cookie = clipboard.getPrimaryClip().getItemAt(0).getText().toString();
                CookieManager.getInstance().setCookie(BASE_URL, cookie);
                Toast.makeText(this, "Cookie导入成功", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "导入失败", Toast.LENGTH_SHORT).show();
        }
    }
}
