package cn.edu.hut.course;
import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.webkit.CookieManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;
import androidx.recyclerview.widget.RecyclerView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import cn.edu.hut.course.data.CampusBuildingStore;
import cn.edu.hut.course.data.AgendaStorageManager;
import cn.edu.hut.course.data.CourseJsonCodec;
import cn.edu.hut.course.data.CourseStorageManager;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
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
    private static final String PROFILE_URL = BASE_URL + "/jsxsd/framework/xsMainV_new.htmlx?t1=1";
    private static final String PREF_NAME = "course_storage";
    private static final String PREF_COURSE_COLORS = "course_colors";
    private static final String KEY_COURSES_JSON = "courses_json";
    private static final String KEY_SHOW_GRID_LINES = "show_grid_lines";
    private static final String KEY_TIMETABLE_THEME_COLOR = "timetable_theme_color";
    private static final String KEY_TIMETABLE_FONT_SCALE = "timetable_font_scale";
    private static final String KEY_PROFILE_NAME = "profile_name";
    private static final String KEY_PROFILE_STUDENT_ID = "profile_student_id";
    private static final String KEY_PROFILE_CLASS = "profile_class";
    private static final String KEY_PROFILE_COLLEGE = "profile_college";
    private static final String[] WEEK_DAY_LABELS = {"一", "二", "三", "四", "五", "六", "日"};
    private static final int[] SLOT_START_SECONDS = {8 * 3600, 10 * 3600, 14 * 3600, 16 * 3600, 19 * 3600};
    private static final int[] SLOT_END_SECONDS = {9 * 3600 + 40 * 60, 11 * 3600 + 40 * 60, 15 * 3600 + 40 * 60, 17 * 3600 + 40 * 60, 20 * 3600 + 40 * 60};
    private static final int NEXT_NOTICE_WINDOW_SECONDS = 40 * 60;
    private static final String[] SLOT_LABELS = {"第一大节", "第二大节", "第三大节", "第四大节", "第五大节"};
    private static final int MAX_IMPORTED_SECTION = 10;
    private static final int[] AGENDA_PRIORITY_VALUES = {Agenda.PRIORITY_LOW, Agenda.PRIORITY_MEDIUM, Agenda.PRIORITY_HIGH};
    private static final String[] AGENDA_PRIORITY_LABELS = {"低", "中", "高"};
    private static final String[] AGENDA_REPEAT_VALUES = {Agenda.REPEAT_NONE, Agenda.REPEAT_DAILY, Agenda.REPEAT_WEEKLY, Agenda.REPEAT_MONTHLY};
    private static final String[] AGENDA_REPEAT_LABELS = {"不重复", "每天", "每周", "每月固定日"};
    private static final String[] AGENDA_MONTHLY_VALUES = {Agenda.MONTHLY_SKIP, Agenda.MONTHLY_MONTH_END};
    private static final String[] AGENDA_MONTHLY_LABELS = {"短月跳过", "短月改月底"};
    private static final String KEY_SELECTED_NAV_ITEM_ID = "selected_nav_item_id";
    private static final int WEEK_OVERVIEW_BAR_COUNT_CAP = 10;
    private static final int WEEK_OVERVIEW_BAR_MIN_HEIGHT_DP = 42;
    private static final int WEEK_OVERVIEW_BAR_MAX_HEIGHT_DP = 84;
    private static final int WEEK_OVERVIEW_TODAY_DOT_SIZE_DP = 6;
    private static final int WEEK_OVERVIEW_TODAY_DOT_GAP_DP = 6;

    private com.google.android.material.card.MaterialCardView cardNextCourseNotice;
    private com.google.android.material.card.MaterialCardView cardTodayWeekOverview;
    private TextView tvMainTitle, tvEmptyHint, tvNextCourseNotice;
    private TextView tvTodayWeek, tvTodayDate, tvTodayWeekTotal, tvTodayWeekDone, tvNowTime;
    private TextView tvProfileName, tvProfileStudentId, tvProfileClass, tvProfileCollege;
    private ImageButton btnCloseNextCourseNotice;
    private FloatingActionButton fabAddAgenda;
    private BottomNavigationView bottomNav;
    private View bottomNavHost;
    // Grid for header rendering if needed, but we use VP2 now
    private ViewPager2 viewPager;
    private View pageSchedule, pageToday, pageAi, pageProfile, titleContainer, rootMain;
    private LinearLayout todayCoursesContainer, layoutTodayWeekStrip;

    private final List<Course> allCourses = new ArrayList<>();
    private int currentWeek = 1;
    private int totalWeeks = 20;
    private long semesterStartDateMs = 0;
    private boolean nextCourseNoticeDismissed = false;
    private String selectedCourseColorKey = null;
    private Calendar selectedTodayDate;
    private int weekOverviewPreviewWeek = -1;
    private int weekOverviewPreviewDay = -1;
    private boolean todayEndedTimelineCollapsed = true;
    private int selectedNavItemId = R.id.nav_schedule;
    private int lastRealtimeWeek = -1;
    private int lastRealtimeDay = -1;
    private int lastRealtimeSlot = -2;
    private final Handler noticeHandler = new Handler(Looper.getMainLooper());
    private final Runnable noticeTicker = new Runnable() {
        @Override
        public void run() {
            updateNextCourseNotice();
            refreshRealtimeCourseHighlight();
            updateTodayHeaderClock();
            noticeHandler.postDelayed(this, 1_000L);
        }
    };

    private ActivityResultLauncher<Intent> browserLauncher;
    private ActivityResultLauncher<Intent> settingsLauncher;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;

    private void updateTitle() {
        boolean scheduleVisible = pageSchedule != null && pageSchedule.getVisibility() == View.VISIBLE;
        if (tvMainTitle != null) {
            tvMainTitle.setText(scheduleVisible ? ("第" + currentWeek + "周课表") : "");
        }
        if (titleContainer != null) {
            titleContainer.setVisibility(scheduleVisible ? View.VISIBLE : View.GONE);
        }
    }

    private void applyMaterialScaffoldStyle() {
        int colorOnSurface = UiStyleHelper.resolveOnSurfaceColor(this);
        int colorOnSurfaceVariant = UiStyleHelper.resolveOnSurfaceVariantColor(this);
        int colorPrimary = getTimetableThemeColor();
        int navBackground = ColorUtils.blendARGB(UiStyleHelper.resolvePageBackgroundColor(this), UiStyleHelper.resolveGlassCardColor(this), 0.42f);

        if (tvMainTitle != null) {
            tvMainTitle.setTextColor(colorOnSurface);
        }
        if (tvNowTime != null) {
            tvNowTime.setTextColor(colorPrimary);
        }
        if (tvTodayWeek != null) {
            tvTodayWeek.setTextColor(colorOnSurfaceVariant);
        }
        if (tvTodayDate != null) {
            tvTodayDate.setTextColor(colorOnSurfaceVariant);
        }
        if (tvTodayWeekTotal != null) {
            tvTodayWeekTotal.setTextColor(colorOnSurfaceVariant);
        }
        if (tvTodayWeekDone != null) {
            tvTodayWeekDone.setTextColor(colorOnSurfaceVariant);
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
            bottomNav.setBackgroundTintList(ColorStateList.valueOf(navBackground));
            bottomNav.setItemActiveIndicatorEnabled(false);
            bottomNav.setItemBackground(buildBottomNavItemBackground(colorPrimary));
            final int navCorner = dp(30);
            bottomNav.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), navCorner);
                }
            });
            bottomNav.setClipToOutline(true);
            bottomNav.setClipChildren(false);
            bottomNav.setClipToPadding(false);
            View parent = (View) bottomNav.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).setClipChildren(false);
                ((ViewGroup) parent).setClipToPadding(false);
            }
            float navElevation = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, getResources().getDisplayMetrics());
            bottomNav.setElevation(navElevation);
            bottomNav.setTranslationZ(navElevation * 0.5f);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                bottomNav.setOutlineAmbientShadowColor(ColorUtils.setAlphaComponent(colorOnSurface, 88));
                bottomNav.setOutlineSpotShadowColor(ColorUtils.setAlphaComponent(colorOnSurface, 120));
            }
        }

        if (fabAddAgenda != null) {
            fabAddAgenda.setBackgroundTintList(ColorStateList.valueOf(colorPrimary));
            fabAddAgenda.setImageTintList(ColorStateList.valueOf(pickReadableTextColor(colorPrimary)));
            fabAddAgenda.setImageResource(R.drawable.ic_add_bold_28);
            fabAddAgenda.setCustomSize(dp(54));
            fabAddAgenda.setTranslationX(0f);
            fabAddAgenda.setTranslationY(0f);
            fabAddAgenda.setUseCompatPadding(false);
            fabAddAgenda.setCompatElevation(0f);
            fabAddAgenda.setElevation(0f);
            fabAddAgenda.setTranslationZ(0f);
            fabAddAgenda.setStateListAnimator(null);
        }
    }

    private void setupBottomNavInsetsHandling() {
        if (bottomNav == null) {
            return;
        }
        View host = bottomNavHost;
        if (host == null && bottomNav.getParent() instanceof View) {
            host = (View) bottomNav.getParent();
        }
        if (host == null) {
            return;
        }

        final View finalHost = host;
        int baseBottomMargin = 0;
        ViewGroup.LayoutParams hostParams = finalHost.getLayoutParams();
        if (hostParams instanceof ViewGroup.MarginLayoutParams) {
            baseBottomMargin = ((ViewGroup.MarginLayoutParams) hostParams).bottomMargin;
        }
        final int stableBottomMargin = baseBottomMargin;

        final int navBasePaddingLeft = bottomNav.getPaddingLeft();
        final int navBasePaddingTop = bottomNav.getPaddingTop();
        final int navBasePaddingRight = bottomNav.getPaddingRight();
        final int navBasePaddingBottom = bottomNav.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(finalHost, (v, insets) -> {
            Insets navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            Insets gestureInsets = insets.getInsets(WindowInsetsCompat.Type.systemGestures());
            int bottomInset = Math.max(0, Math.max(navInsets.bottom, gestureInsets.bottom));

            ViewGroup.LayoutParams params = v.getLayoutParams();
            if (params instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) params;
                int targetBottomMargin = stableBottomMargin + bottomInset;
                if (marginLayoutParams.bottomMargin != targetBottomMargin) {
                    marginLayoutParams.bottomMargin = targetBottomMargin;
                    v.setLayoutParams(marginLayoutParams);
                }
            }
            return insets;
        });

        ViewCompat.setOnApplyWindowInsetsListener(bottomNav, (v, insets) -> {
            if (v.getPaddingLeft() != navBasePaddingLeft
                    || v.getPaddingTop() != navBasePaddingTop
                    || v.getPaddingRight() != navBasePaddingRight
                    || v.getPaddingBottom() != navBasePaddingBottom) {
                v.setPadding(navBasePaddingLeft, navBasePaddingTop, navBasePaddingRight, navBasePaddingBottom);
            }
            return WindowInsetsCompat.CONSUMED;
        });

        ViewCompat.requestApplyInsets(finalHost);
    }

    private StateListDrawable buildBottomNavItemBackground(int accentColor) {
        float radius = dp(26);
        int checkedFill = ColorUtils.setAlphaComponent(accentColor, 70);

        GradientDrawable checked = new GradientDrawable();
        checked.setShape(GradientDrawable.RECTANGLE);
        checked.setCornerRadius(radius);
        checked.setColor(checkedFill);

        GradientDrawable normal = new GradientDrawable();
        normal.setShape(GradientDrawable.RECTANGLE);
        normal.setCornerRadius(radius);
        normal.setColor(Color.TRANSPARENT);

        InsetDrawable checkedInset = new InsetDrawable(checked, dp(8), dp(4), dp(8), dp(4));
        InsetDrawable normalInset = new InsetDrawable(normal, dp(8), dp(4), dp(8), dp(4));

        StateListDrawable stateList = new StateListDrawable();
        stateList.addState(new int[]{android.R.attr.state_checked}, checkedInset);
        stateList.addState(new int[]{}, normalInset);
        return stateList;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiStyleHelper.hideStatusBar(this);

        setContentView(R.layout.activity_main);

        tvMainTitle = findViewById(R.id.tvMainTitle);
        rootMain = findViewById(R.id.rootMain);
        titleContainer = findViewById(R.id.titleContainer);
        tvEmptyHint = findViewById(R.id.tvEmptyHint);
        pageSchedule = findViewById(R.id.pageSchedule);
        pageToday = findViewById(R.id.pageToday);
        pageAi = findViewById(R.id.pageAi);
        pageProfile = findViewById(R.id.pageProfile);
        tvTodayWeek = findViewById(R.id.tvTodayWeek);
        tvNowTime = findViewById(R.id.tvNowTime);
        tvTodayDate = findViewById(R.id.tvTodayDate);
        tvTodayWeekTotal = findViewById(R.id.tvTodayWeekTotal);
        tvTodayWeekDone = findViewById(R.id.tvTodayWeekDone);
        todayCoursesContainer = findViewById(R.id.todayCoursesContainer);
        layoutTodayWeekStrip = findViewById(R.id.layoutTodayWeekStrip);
        cardTodayWeekOverview = findViewById(R.id.cardTodayWeekOverview);
        bottomNavHost = findViewById(R.id.bottomNavHost);
        bottomNav = findViewById(R.id.bottomNav);
        viewPager = findViewById(R.id.viewPager);
        cardNextCourseNotice = findViewById(R.id.cardNextCourseNotice);
        tvNextCourseNotice = findViewById(R.id.tvNextCourseNotice);
        btnCloseNextCourseNotice = findViewById(R.id.btnCloseNextCourseNotice);
        fabAddAgenda = findViewById(R.id.fabAddAgenda);
        tvProfileName = findViewById(R.id.tvProfileName);
        tvProfileStudentId = findViewById(R.id.tvProfileStudentId);
        tvProfileClass = findViewById(R.id.tvProfileClass);
        tvProfileCollege = findViewById(R.id.tvProfileCollege);

        if (fabAddAgenda != null) {
            disableParentClipping(fabAddAgenda);
        }

        setupBottomNavInsetsHandling();

        if (fabAddAgenda != null) {
            fabAddAgenda.setOnClickListener(v -> showAgendaEditorSheet(null, resolveSelectedTodayDate(Calendar.getInstance(), getActualCurrentWeek())));
        }

        View itemSettingsEntry = findViewById(R.id.itemSettingsEntry);
        if (itemSettingsEntry != null) {
            itemSettingsEntry.setOnClickListener(v -> settingsLauncher.launch(new Intent(this, SettingsHomeActivity.class)));
        }
        browserLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                Intent data = result.getData();
                String cookie = data != null ? data.getStringExtra("cookie") : null;
                boolean loginSuccess = data != null && data.getBooleanExtra("login_success", false);
                if (loginSuccess) {
                    extractAllTables(cookie);
                } else {
                    loadCoursesFromLocal();
                }
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
                        case "extract_after_login":
                            extractAllTables(result.getData().getStringExtra("cookie"));
                            break;
                        case "extract":
                            Toast.makeText(this, "请在设置中登录教务系统后自动抓取", Toast.LENGTH_SHORT).show();
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

        locationPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            updateNextCourseNotice();
        });
        ensureLocationPermission();

        if (btnCloseNextCourseNotice != null) {
            btnCloseNextCourseNotice.setOnClickListener(v -> {
                nextCourseNoticeDismissed = true;
                updateNextCourseNotice();
            });
        }

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            selectedNavItemId = id;
            if (id == R.id.nav_today) {
                switchToTodayPage();
                updateNextCourseNotice();
                updateTitle();
                return true;
            }
            if (id == R.id.nav_ai) {
                switchToAiPage();
                updateNextCourseNotice();
                updateTitle();
                return true;
            }
            if (id == R.id.nav_schedule) {
                switchToSchedulePage();
                updateNextCourseNotice();
                updateTitle();
                return true;
            }
            if (id == R.id.nav_profile) {
                switchToProfilePage();
                updateNextCourseNotice();
                updateTitle();
                return true;
            }
            return false;
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (pageAi != null && pageAi.getVisibility() == View.VISIBLE) {
                    Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.pageAi);
                    if (fragment instanceof AiChatFragment && ((AiChatFragment) fragment).handleBackPressed()) {
                        return;
                    }
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });
        bottomNav.setOnItemReselectedListener(item -> {
            selectedNavItemId = item.getItemId();
            if (item.getItemId() == R.id.nav_schedule) {
                jumpToActualCurrentWeek(true);
                updateNextCourseNotice();
            } else if (item.getItemId() == R.id.nav_today) {
                refreshTodayPage();
            } else if (item.getItemId() == R.id.nav_ai) {
                ensureAiFragmentAttached();
            } else if (item.getItemId() == R.id.nav_profile) {
                renderProfileFromLocal();
            }
        });

        View.OnClickListener weekSelectorClick = v -> showWeekSelector();
        tvMainTitle.setOnClickListener(weekSelectorClick);
        if (titleContainer != null) {
            titleContainer.setOnClickListener(weekSelectorClick);
        }

        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        semesterStartDateMs = prefs.getLong("semester_start_date", 0);
        if (savedInstanceState != null) {
            selectedNavItemId = savedInstanceState.getInt(KEY_SELECTED_NAV_ITEM_ID, selectedNavItemId);
        }
        applyMaterialScaffoldStyle();
        setupViewPager();
        ensureAiFragmentAttached();
        loadCoursesFromLocal();
        renderProfileFromLocal();
        updateBackground();
        if (bottomNav != null) {
            showPageForNavItem(selectedNavItemId);
            if (bottomNav.getSelectedItemId() != selectedNavItemId) {
                bottomNav.setSelectedItemId(selectedNavItemId);
            }
        } else {
            showPageForNavItem(selectedNavItemId);
        }
        updateTodayHeaderClock();
        startNoticeTicker();
        openCourseEditorFromAction(getIntent());
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_SELECTED_NAV_ITEM_ID, selectedNavItemId);
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
        loadCoursesFromLocal();
        renderProfileFromLocal();
        updateBackground();
        refreshTodayPage();
        updateTodayHeaderClock();
        startNoticeTicker();
    }

    @Override
    protected void onPause() {
        noticeHandler.removeCallbacks(noticeTicker);
        CampusBuildingStore.setRealtimeDeviceLocationTracking(this, false);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        noticeHandler.removeCallbacks(noticeTicker);
        CampusBuildingStore.setRealtimeDeviceLocationTracking(this, false);
        super.onDestroy();
    }

    private void startNoticeTicker() {
        noticeHandler.removeCallbacks(noticeTicker);
        noticeHandler.post(noticeTicker);
    }

    private void updateTodayHeaderClock() {
        if (tvNowTime == null) return;
        Calendar now = Calendar.getInstance();
        tvNowTime.setText(String.format(Locale.getDefault(), "%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE)));
    }

    private void showWeekSelector() {
        if (pageSchedule == null || pageSchedule.getVisibility() != View.VISIBLE) {
            return;
        }
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_week_selector, null);
        dialog.setContentView(view);

        int accent = getTimetableThemeColor();
        int onSurface = UiStyleHelper.resolveOnSurfaceColor(this);
        int outline = ColorUtils.setAlphaComponent(onSurface, 56);
        int glass = UiStyleHelper.resolveGlassCardColor(this);
        
        GridLayout grid = view.findViewById(R.id.gridWeeks);
        for (int i = 0; i < totalWeeks; i++) {
            final int week = i + 1;
            MaterialButton btn = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btn.setText(String.valueOf(week));
            btn.setAllCaps(false);
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
            btn.setCornerRadius(dp(14));
            btn.setInsetTop(0);
            btn.setInsetBottom(0);
            btn.setStrokeWidth(dp(1));
            btn.setStrokeColor(ColorStateList.valueOf(outline));
            btn.setRippleColor(ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 80)));
            btn.setBackgroundTintList(ColorStateList.valueOf(glass));
            btn.setTextColor(onSurface);

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = GridLayout.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(8, 8, 8, 8);
            btn.setLayoutParams(params);

            if (week == currentWeek) {
                int selected = ColorUtils.setAlphaComponent(accent, 224);
                btn.setBackgroundTintList(ColorStateList.valueOf(selected));
                btn.setStrokeColor(ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 240)));
                btn.setTextColor(pickReadableTextColor(selected));
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

    private void switchToTodayPage() {
        if (pageToday != null) pageToday.setVisibility(View.VISIBLE);
        if (pageSchedule != null) pageSchedule.setVisibility(View.GONE);
        if (pageAi != null) pageAi.setVisibility(View.GONE);
        if (pageProfile != null) pageProfile.setVisibility(View.GONE);
        if (fabAddAgenda != null) fabAddAgenda.setVisibility(View.VISIBLE);
        updateTitle();
        refreshTodayPage();
    }

    private void switchToSchedulePage() {
        if (pageToday != null) pageToday.setVisibility(View.GONE);
        if (pageSchedule != null) pageSchedule.setVisibility(View.VISIBLE);
        if (pageAi != null) pageAi.setVisibility(View.GONE);
        if (pageProfile != null) pageProfile.setVisibility(View.GONE);
        if (fabAddAgenda != null) fabAddAgenda.setVisibility(View.GONE);
        updateTitle();
    }

    private void switchToAiPage() {
        if (pageToday != null) pageToday.setVisibility(View.GONE);
        if (pageSchedule != null) pageSchedule.setVisibility(View.GONE);
        if (pageAi != null) pageAi.setVisibility(View.VISIBLE);
        if (pageProfile != null) pageProfile.setVisibility(View.GONE);
        if (fabAddAgenda != null) fabAddAgenda.setVisibility(View.GONE);
        updateTitle();
    }

    private void switchToProfilePage() {
        if (pageToday != null) pageToday.setVisibility(View.GONE);
        if (pageSchedule != null) pageSchedule.setVisibility(View.GONE);
        if (pageAi != null) pageAi.setVisibility(View.GONE);
        if (pageProfile != null) pageProfile.setVisibility(View.VISIBLE);
        if (fabAddAgenda != null) fabAddAgenda.setVisibility(View.GONE);
        renderProfileFromLocal();
        updateTitle();
        styleProfileCards();
    }

    private void showPageForNavItem(int navItemId) {
        if (navItemId == R.id.nav_today) {
            switchToTodayPage();
        } else if (navItemId == R.id.nav_ai) {
            switchToAiPage();
        } else if (navItemId == R.id.nav_profile) {
            switchToProfilePage();
        } else {
            switchToSchedulePage();
        }
    }

    private void styleProfileCards() {
        if (pageProfile == null) return;
        int onSurface = UiStyleHelper.resolveOnSurfaceColor(this);
        int strokeColor = ColorUtils.setAlphaComponent(onSurface, 24);
        int glass = UiStyleHelper.resolveGlassCardColor(this);

        MaterialCardView cardProfileInfo = findViewById(R.id.cardProfileInfo);
        if (cardProfileInfo != null) {
            cardProfileInfo.setCardElevation(0f);
            cardProfileInfo.setRadius(dp(24));
            cardProfileInfo.setStrokeWidth(dp(1));
            cardProfileInfo.setStrokeColor(strokeColor);
            cardProfileInfo.setCardBackgroundColor(glass);
        }

        MaterialCardView itemSettingsEntry = findViewById(R.id.itemSettingsEntry);
        if (itemSettingsEntry != null) {
            itemSettingsEntry.setCardElevation(0f);
            itemSettingsEntry.setRadius(dp(24));
            itemSettingsEntry.setStrokeWidth(dp(1));
            itemSettingsEntry.setStrokeColor(strokeColor);
            itemSettingsEntry.setCardBackgroundColor(glass);
        }
    }

    private void ensureAiFragmentAttached() {
        if (pageAi == null) return;
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.pageAi);
        if (current == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.pageAi, new AiChatFragment())
                    .commitNowAllowingStateLoss();
        }
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

        int[] palette = ColorPaletteProvider.vibrantLightPalette();
        int hash = Math.abs(courseName.hashCode());
        int color = palette[hash % palette.length];
        if (isExperimental) {
            color = ColorUtils.blendARGB(color, Color.WHITE, 0.08f);
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
        return UiStyleHelper.resolveAccentColor(this);
    }

    private float getTimetableFontScale() {
        float value = getSharedPreferences(PREF_NAME, MODE_PRIVATE).getFloat(KEY_TIMETABLE_FONT_SCALE, 1.0f);
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 1.0f;
        }
        return Math.max(0.85f, Math.min(1.30f, value));
    }

    private int[] buildColorPalette() {
        return ColorPaletteProvider.vibrantLightPalette();
    }

    private int[] buildAgendaColorPalette() {
        return buildColorPalette();
    }

    private int getDefaultAgendaRenderColor() {
        boolean darkMode = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        return darkMode ? Color.BLACK : Color.WHITE;
    }

    private boolean isAgendaDefaultRenderColor(int color) {
        return color == 0 || color == Color.TRANSPARENT || color == Color.WHITE || color == Color.BLACK;
    }

    private int normalizeAgendaStoredRenderColor(int color) {
        if (isAgendaDefaultRenderColor(color)) {
            return 0;
        }
        int[] palette = buildAgendaColorPalette();
        for (int one : palette) {
            if (one == color) {
                return color;
            }
        }
        return 0;
    }

    private int resolveAgendaRenderColorValue(int storedColor) {
        int normalized = normalizeAgendaStoredRenderColor(storedColor);
        if (normalized == 0) {
            return getDefaultAgendaRenderColor();
        }
        return normalized;
    }

    private int resolveAgendaRenderColor(Agenda agenda) {
        return resolveAgendaRenderColorValue(agenda == null ? 0 : agenda.renderColor);
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
        int dp40 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 39, getResources().getDisplayMetrics());
        int dp120 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 120, getResources().getDisplayMetrics());
        float timetableFontScale = getTimetableFontScale();
        int colorOnSurface = UiStyleHelper.resolveOnSurfaceColor(this);
        int colorOnSurfaceVariant = UiStyleHelper.resolveOnSurfaceVariantColor(this);
        int colorOutline = UiStyleHelper.resolveOutlineColor(this);
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
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f * timetableFontScale);
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
            Calendar clickDate = cloneAsDay(dayCal);
            String dateStr = sdf.format(cal.getTime());
            t.setText("周" + days[i] + "\n" + dateStr);
            t.setTextColor(colorOnSurfaceVariant);
            t.setGravity(Gravity.CENTER);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f * timetableFontScale);
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
            int targetWeek = week;
            t.setOnClickListener(v -> showScheduleDayArrangementSheet(clickDate, targetWeek));
            grid.addView(t, p);
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        int todayOfWeek = today.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY ? 7 : today.get(Calendar.DAY_OF_WEEK) - 1;
        int dp2 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics());
        int glassBg = UiStyleHelper.resolveGlassCardColor(this);

        Map<Integer, List<ScheduleCellEntry>> cellEntries = new HashMap<>();

        for (Course c : allCourses) {
            if (c == null || c.isRemark || c.dayOfWeek < 1 || c.dayOfWeek > 7) {
                continue;
            }
            if (c.weeks == null || !c.weeks.contains(week)) {
                continue;
            }
            int slotIndex = Math.max(0, Math.min(SLOT_LABELS.length - 1, (c.startSection - 1) / 2));
            int slot = slotIndex + 1;
            String colorKey = buildCourseColorKey(c.name, c.isExperimental);
            boolean hasCustomColor = hasCustomCourseColor(c.name, c.isExperimental);
            int customColor = getCourseColor(c.name, c.isExperimental);
            boolean isCurrentCourse = week == getActualCurrentWeek()
                    && c.dayOfWeek == todayOfWeek
                    && slot == currentSlotIndex;
            boolean isSelected = colorKey.equals(selectedCourseColorKey);
            addScheduleCellEntry(cellEntries, slot, c.dayOfWeek,
                    ScheduleCellEntry.forCourse(c, slotIndex, colorKey, isCurrentCourse, isSelected, hasCustomColor, customColor));
        }

        for (int day = 1; day <= 7; day++) {
            Calendar targetDate = buildDateInAcademicWeek(week, day);
            List<Agenda> dayAgendas = AgendaStorageManager.queryAgendasByDate(this, targetDate);
            for (Agenda agenda : dayAgendas) {
                if (agenda == null) {
                    continue;
                }
                addAgendaEntriesByTimeRange(cellEntries, day, agenda);
            }
        }

        int cellBaseHeight = dp120 - 10;
        int cellGap = dp(8);
        for (int col = 1; col <= 7; col++) {
            int row = 1;
            while (row <= 5) {
                List<ScheduleCellEntry> entries = getSortedScheduleCellEntries(cellEntries, row, col);
                if (entries.isEmpty()) {
                    row++;
                    continue;
                }

                int rowSpan = 1;
                if (entries.size() == 1) {
                    ScheduleCellEntry onlyEntry = entries.get(0);
                    if (onlyEntry.type == ScheduleCellEntry.TYPE_COURSE) {
                        ScheduleVerticalMergeResult merged = tryMergeScheduleEntriesVertically(cellEntries, row, col, onlyEntry);
                        entries = new ArrayList<>();
                        entries.add(merged.entry);
                        rowSpan = merged.rowSpan;
                    } else {
                        rowSpan = computeAgendaRenderRowSpan(cellEntries, row, col, onlyEntry);
                    }
                }

                View cellView = createScheduleGridCellView(entries, week, col, themeColor, glassBg, showGridLines, dp2, darkMode, row, rowSpan, timetableFontScale);
                GridLayout.LayoutParams p = new GridLayout.LayoutParams(GridLayout.spec(row, rowSpan), GridLayout.spec(col, 1f));
                p.width = 0;
                p.height = cellBaseHeight * rowSpan + cellGap * Math.max(0, rowSpan - 1);
                p.setMargins(4, 4, 4, 4);
                grid.addView(cellView, p);

                row += rowSpan;
            }
        }
    }

    private int computeAgendaRenderRowSpan(Map<Integer, List<ScheduleCellEntry>> cellEntries,
                                           int startRow,
                                           int col,
                                           ScheduleCellEntry agendaEntry) {
        if (agendaEntry == null || agendaEntry.type != ScheduleCellEntry.TYPE_AGENDA) {
            return 1;
        }

        int desiredEndRow = startRow;
        for (int i = Math.max(0, startRow - 1); i < SLOT_START_SECONDS.length; i++) {
            int slotStartMinute = SLOT_START_SECONDS[i] / 60;
            int slotEndMinute = SLOT_END_SECONDS[i] / 60;
            if (agendaEntry.endMinute > slotStartMinute && agendaEntry.startMinute < slotEndMinute) {
                desiredEndRow = i + 1;
            }
        }
        int desiredRowSpan = Math.max(1, desiredEndRow - startRow + 1);
        int maxRowSpan = Math.max(1, 5 - startRow + 1);
        desiredRowSpan = Math.min(desiredRowSpan, maxRowSpan);

        for (int row = startRow + 1; row <= startRow + desiredRowSpan - 1; row++) {
            List<ScheduleCellEntry> occupied = getSortedScheduleCellEntries(cellEntries, row, col);
            if (!occupied.isEmpty()) {
                return Math.max(1, row - startRow);
            }
        }
        return desiredRowSpan;
    }

    private List<ScheduleCellEntry> getSortedScheduleCellEntries(Map<Integer, List<ScheduleCellEntry>> cellEntries,
                                                                 int row,
                                                                 int col) {
        int clampedRow = Math.max(1, Math.min(5, row));
        int clampedCol = Math.max(1, Math.min(7, col));
        int key = clampedRow * 16 + clampedCol;
        List<ScheduleCellEntry> source = cellEntries.get(key);
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        List<ScheduleCellEntry> sorted = new ArrayList<>(source);
        sorted.sort((a, b) -> {
            int byType = Integer.compare(a.type, b.type);
            if (byType != 0) {
                return byType;
            }
            return Integer.compare(a.startMinute, b.startMinute);
        });
        return sorted;
    }

    private ScheduleVerticalMergeResult tryMergeScheduleEntriesVertically(Map<Integer, List<ScheduleCellEntry>> cellEntries,
                                                                          int startRow,
                                                                          int col,
                                                                          ScheduleCellEntry firstEntry) {
        ScheduleCellEntry mergedEntry = firstEntry;
        int rowSpan = 1;
        int nextRow = startRow + 1;
        while (nextRow <= 5) {
            List<ScheduleCellEntry> nextEntries = getSortedScheduleCellEntries(cellEntries, nextRow, col);
            if (nextEntries.size() != 1) {
                break;
            }
            ScheduleCellEntry nextEntry = nextEntries.get(0);
            if (!canMergeScheduleEntries(mergedEntry, nextEntry)) {
                break;
            }
            mergedEntry = mergeScheduleEntries(mergedEntry, nextEntry);
            rowSpan++;
            nextRow++;
        }
        return new ScheduleVerticalMergeResult(mergedEntry, rowSpan);
    }

    private boolean canMergeScheduleEntries(ScheduleCellEntry current, ScheduleCellEntry next) {
        if (current == null || next == null || current.type != next.type) {
            return false;
        }
        if (current.type == ScheduleCellEntry.TYPE_COURSE) {
            return canMergeCourseEntries(current, next);
        }
        return canMergeAgendaEntries(current, next);
    }

    private boolean canMergeCourseEntries(ScheduleCellEntry current, ScheduleCellEntry next) {
        if (current.course == null || next.course == null) {
            return false;
        }
        if (!isSameCourseMergeTarget(current.course, next.course)) {
            return false;
        }
        int expectedNextSlot = current.slotIndex + getCourseEntrySpanCount(current);
        return next.slotIndex == expectedNextSlot;
    }

    private boolean isSameCourseMergeTarget(Course first, Course second) {
        if (first == null || second == null) {
            return false;
        }
        if (!safeText(first.name).equals(safeText(second.name))) {
            return false;
        }
        if (first.isExperimental != second.isExperimental) {
            return false;
        }
        if (!safeText(first.teacher).trim().equals(safeText(second.teacher).trim())) {
            return false;
        }
        if (!safeText(first.location).trim().equals(safeText(second.location).trim())) {
            return false;
        }
        return first.dayOfWeek == second.dayOfWeek;
    }

    private boolean canMergeAgendaEntries(ScheduleCellEntry current, ScheduleCellEntry next) {
        if (current.agenda == null || next.agenda == null) {
            return false;
        }
        if (current.agenda.id > 0 && next.agenda.id > 0 && current.agenda.id != next.agenda.id) {
            return false;
        }
        if (current.agenda.id <= 0 || next.agenda.id <= 0) {
            if (!safeText(current.agenda.title).equals(safeText(next.agenda.title))) {
                return false;
            }
            if (!safeText(current.agenda.date).equals(safeText(next.agenda.date))) {
                return false;
            }
        }
        return next.startMinute <= current.endMinute + 1;
    }

    private int getCourseEntrySpanCount(ScheduleCellEntry entry) {
        if (entry == null || entry.mergedCourses == null || entry.mergedCourses.isEmpty()) {
            return 1;
        }
        return Math.max(1, entry.mergedCourses.size());
    }

    private ScheduleCellEntry mergeScheduleEntries(ScheduleCellEntry current, ScheduleCellEntry next) {
        if (current.type == ScheduleCellEntry.TYPE_AGENDA) {
            int mergedStartMinute = Math.min(current.startMinute, next.startMinute);
            int mergedEndMinute = Math.max(current.endMinute, next.endMinute);
            return ScheduleCellEntry.forAgenda(current.agenda, Math.min(current.slotIndex, next.slotIndex), mergedStartMinute, mergedEndMinute);
        }

        List<Course> mergedCourses = new ArrayList<>();
        if (current.mergedCourses != null && !current.mergedCourses.isEmpty()) {
            for (Course one : current.mergedCourses) {
                if (one != null && !mergedCourses.contains(one)) {
                    mergedCourses.add(one);
                }
            }
        } else if (current.course != null) {
            mergedCourses.add(current.course);
        }
        if (next.mergedCourses != null && !next.mergedCourses.isEmpty()) {
            for (Course one : next.mergedCourses) {
                if (one != null && !mergedCourses.contains(one)) {
                    mergedCourses.add(one);
                }
            }
        } else if (next.course != null && !mergedCourses.contains(next.course)) {
            mergedCourses.add(next.course);
        }
        mergedCourses.sort((a, b) -> Integer.compare(a.startSection, b.startSection));

        Course primary = mergedCourses.isEmpty() ? current.course : mergedCourses.get(0);
        int mergedStartMinute = Math.min(current.startMinute, next.startMinute);
        int mergedEndMinute = Math.max(current.endMinute, next.endMinute);
        boolean hasCustomColor = current.hasCustomColor || next.hasCustomColor;
        int customColor = current.hasCustomColor ? current.customColor : next.customColor;
        return ScheduleCellEntry.forMergedCourse(
                primary,
                Math.min(current.slotIndex, next.slotIndex),
                mergedStartMinute,
                mergedEndMinute,
                current.courseColorKey,
                current.isCurrentCourse || next.isCurrentCourse,
                current.isSelectedCourse || next.isSelectedCourse,
            hasCustomColor,
            customColor,
                mergedCourses
        );
    }

    private void addScheduleCellEntry(Map<Integer, List<ScheduleCellEntry>> cellEntries,
                                      int row,
                                      int col,
                                      ScheduleCellEntry entry) {
        if (entry == null) {
            return;
        }
        int clampedRow = Math.max(1, Math.min(5, row));
        int clampedCol = Math.max(1, Math.min(7, col));
        int key = clampedRow * 16 + clampedCol;
        List<ScheduleCellEntry> list = cellEntries.get(key);
        if (list == null) {
            list = new ArrayList<>();
            cellEntries.put(key, list);
        }
        list.add(entry);
    }

    private void addAgendaEntriesByTimeRange(Map<Integer, List<ScheduleCellEntry>> cellEntries,
                                             int dayOfWeek,
                                             @NonNull Agenda agenda) {
        int timetableStartMinute = SLOT_START_SECONDS[0] / 60;
        int timetableEndMinute = SLOT_END_SECONDS[SLOT_END_SECONDS.length - 1] / 60;

        int agendaStartMinute = Math.max(0, Math.min(24 * 60, agenda.startMinute));
        int agendaEndMinute = Math.max(agendaStartMinute + 1, Math.min(24 * 60, agenda.endMinute));

        int visibleStartMinute = Math.max(agendaStartMinute, timetableStartMinute);
        int visibleEndMinute = Math.min(agendaEndMinute, timetableEndMinute);
        if (visibleEndMinute <= visibleStartMinute) {
            return;
        }

        int slotIndex = resolveAgendaAnchorSlotIndex(visibleStartMinute, visibleEndMinute);
        addScheduleCellEntry(cellEntries, slotIndex + 1, dayOfWeek,
                ScheduleCellEntry.forAgenda(agenda, slotIndex, visibleStartMinute, visibleEndMinute));
    }

    private int resolveAgendaAnchorSlotIndex(int visibleStartMinute, int visibleEndMinute) {
        int clampedStartMinute = Math.max(0, Math.min(24 * 60, visibleStartMinute));
        int clampedEndMinute = Math.max(clampedStartMinute + 1, Math.min(24 * 60, visibleEndMinute));

        for (int i = 0; i < SLOT_START_SECONDS.length; i++) {
            int slotStartMinute = SLOT_START_SECONDS[i] / 60;
            int slotEndMinute = SLOT_END_SECONDS[i] / 60;
            if (clampedStartMinute < slotEndMinute && clampedEndMinute > slotStartMinute) {
                return i;
            }
        }

        int nearestSlot = 0;
        int nearestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < SLOT_START_SECONDS.length; i++) {
            int slotStartMinute = SLOT_START_SECONDS[i] / 60;
            int distance = Math.abs(clampedStartMinute - slotStartMinute);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestSlot = i;
            }
        }
        return nearestSlot;
    }

    private View createScheduleGridCellView(List<ScheduleCellEntry> rawEntries,
                                            int week,
                                            int dayOfWeek,
                                            int themeColor,
                                            int glassBg,
                                            boolean showGridLines,
                                            int dp2,
                                            boolean darkMode,
                                            int cellStartRow,
                                            int cellRowSpan,
                                            float timetableFontScale) {
        if (rawEntries == null || rawEntries.isEmpty()) {
            return new View(this);
        }

        List<ScheduleCellEntry> entries = new ArrayList<>(rawEntries);
        entries.sort((a, b) -> {
            int byType = Integer.compare(a.type, b.type); // 课程优先
            if (byType != 0) {
                return byType;
            }
            int byStart = Integer.compare(a.startMinute, b.startMinute);
            if (byStart != 0) {
                return byStart;
            }
            return 0;
        });

        boolean hasCourseInCell = false;
        for (ScheduleCellEntry one : entries) {
            if (one.type == ScheduleCellEntry.TYPE_COURSE) {
                hasCourseInCell = true;
                break;
            }
        }

        if (entries.size() == 1) {
            return createScheduleEntryView(entries.get(0), week, dayOfWeek, themeColor, glassBg, showGridLines, dp2, hasCourseInCell, darkMode, cellStartRow, cellRowSpan, timetableFontScale);
        }

        FrameLayout wrapper = new FrameLayout(this);
        LinearLayout splitRow = new LinearLayout(this);
        splitRow.setOrientation(LinearLayout.HORIZONTAL);
        FrameLayout.LayoutParams splitLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        wrapper.addView(splitRow, splitLp);

        int visibleCount = Math.min(2, entries.size());
        for (int i = 0; i < visibleCount; i++) {
            View entryView = createScheduleEntryView(entries.get(i), week, dayOfWeek, themeColor, glassBg, showGridLines, dp2, hasCourseInCell, darkMode, cellStartRow, 1, timetableFontScale);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            if (i > 0) {
                lp.setMargins(dp(3), 0, 0, 0);
            }
            splitRow.addView(entryView, lp);
        }

        if (entries.size() > visibleCount) {
            TextView badge = new TextView(this);
            badge.setText("+" + (entries.size() - visibleCount));
            badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f * timetableFontScale);
            badge.setTypeface(null, Typeface.BOLD);
            badge.setTextColor(pickReadableTextColor(themeColor));
            badge.setBackground(makeRoundedSolid(themeColor, 10));
            badge.setPadding(dp(6), dp(2), dp(6), dp(2));
            FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.END
            );
            badgeLp.setMargins(0, dp(4), dp(4), 0);
            wrapper.addView(badge, badgeLp);
            Calendar targetDate = buildDateInAcademicWeek(week, dayOfWeek);
            wrapper.setOnClickListener(v -> showScheduleDayArrangementSheet(targetDate, week));
        }

        return wrapper;
    }

    private View createScheduleEntryView(ScheduleCellEntry entry,
                                         int week,
                                         int dayOfWeek,
                                         int themeColor,
                                         int glassBg,
                                         boolean showGridLines,
                                         int dp2,
                                         boolean hasCourseInCell,
                                         boolean darkMode,
                                         int cellStartRow,
                                         int cellRowSpan,
                                         float timetableFontScale) {
        MaterialCardView card = createScheduleEntryCard(entry, week, dayOfWeek, themeColor, glassBg, showGridLines, dp2, hasCourseInCell, darkMode, timetableFontScale);
        if (entry.type != ScheduleCellEntry.TYPE_AGENDA) {
            return card;
        }

        int rowStartIndex = Math.max(0, Math.min(SLOT_START_SECONDS.length - 1, cellStartRow - 1));
        int rowEndIndex = Math.max(rowStartIndex, Math.min(SLOT_END_SECONDS.length - 1, cellStartRow + Math.max(1, cellRowSpan) - 2));
        int spanStartMinute = SLOT_START_SECONDS[rowStartIndex] / 60;
        int spanEndMinute = SLOT_END_SECONDS[rowEndIndex] / 60;
        int spanDurationMinute = Math.max(1, spanEndMinute - spanStartMinute);

        int segmentStartMinute = Math.max(spanStartMinute, entry.startMinute);
        int segmentEndMinute = Math.min(spanEndMinute, Math.max(segmentStartMinute + 1, entry.endMinute));
        float topRatio = (segmentStartMinute - spanStartMinute) / (float) spanDurationMinute;
        float heightRatio = (segmentEndMinute - segmentStartMinute) / (float) spanDurationMinute;

        int baseHeight = dp(110);
        int gapHeight = dp(8);
        int cellHeight = baseHeight * Math.max(1, cellRowSpan) + gapHeight * Math.max(0, cellRowSpan - 1);
        int top = Math.max(0, Math.round(cellHeight * topRatio));
        int height = Math.max(dp(28), Math.round(cellHeight * heightRatio));
        if (top + height > cellHeight) {
            height = Math.max(dp(20), cellHeight - top);
        }

        FrameLayout wrapper = new FrameLayout(this);
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height,
                Gravity.TOP
        );
        cardLp.topMargin = top;
        wrapper.addView(card, cardLp);
        return wrapper;
    }

    private MaterialCardView createScheduleEntryCard(ScheduleCellEntry entry,
                                                     int week,
                                                     int dayOfWeek,
                                                     int themeColor,
                                                     int glassBg,
                                                     boolean showGridLines,
                                                     int dp2,
                                                     boolean hasCourseInCell,
                                                     boolean darkMode,
                                                     float timetableFontScale) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(16));
        card.setCardElevation(0f);
        card.setStrokeColor(Color.TRANSPARENT);
        if (showGridLines) {
            card.setPreventCornerOverlap(false);
        }

        if (entry.type == ScheduleCellEntry.TYPE_COURSE && entry.course != null) {
            int cardBg = entry.hasCustomColor ? ColorUtils.blendARGB(glassBg, entry.customColor, 0.30f) : glassBg;
            card.setCardBackgroundColor(cardBg);
            if (entry.isCurrentCourse || entry.isSelectedCourse) {
                card.setStrokeWidth(dp2);
                card.setStrokeColor(themeColor);
            } else {
                card.setStrokeWidth(0);
                card.setStrokeColor(Color.TRANSPARENT);
            }

            float screenWidthDp = getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density;
            boolean compactScreen = screenWidthDp <= 390f;
            int spanCount = Math.max(1, getCourseEntrySpanCount(entry));
            int textColor = pickReadableTextColor(cardBg);
            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            int contentPadding = compactScreen ? dp(4) : dp(6);
            content.setPadding(contentPadding, contentPadding, contentPadding, contentPadding);
            content.setGravity(Gravity.CENTER_VERTICAL);

            TextView titleView = new TextView(this);
            String titleText = safeText(entry.course.name).trim();
            if (titleText.isEmpty()) {
                titleText = "未命名课程";
            }
            if (entry.course.isExperimental) {
                titleText += " [实验]";
            }
            titleView.setText(titleText);
            titleView.setTextColor(textColor);
            titleView.setTypeface(null, Typeface.BOLD);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, (compactScreen ? 8.2f : 8.8f) * timetableFontScale);
            titleView.setMaxLines(spanCount > 1 ? 4 : 3);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            titleView.setIncludeFontPadding(false);
            titleView.setLineSpacing(0f, 0.92f);
            titleView.setLetterSpacing(-0.015f);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                titleView.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
                titleView.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);
            }
            if (titleText.matches(".*[A-Za-z].*")) {
                titleView.setTextScaleX(0.94f);
            }

            TextView teacherView = new TextView(this);
            String teacher = safeText(entry.course.teacher).trim();
            boolean hasTeacher = !teacher.isEmpty() && !"未定".equals(teacher);
            teacherView.setText(teacher);
            teacherView.setTextColor(ColorUtils.setAlphaComponent(textColor, 220));
            teacherView.setTextSize(TypedValue.COMPLEX_UNIT_SP, (compactScreen ? 7.2f : 8f) * timetableFontScale);
            teacherView.setSingleLine(true);
            teacherView.setEllipsize(TextUtils.TruncateAt.END);

            String location = formatScheduleCellLocation(entry.course.location);

            TextView locationView = new TextView(this);
            locationView.setText(location);
            locationView.setTextColor(textColor);
            locationView.setTypeface(null, Typeface.BOLD);
            locationView.setTextSize(TypedValue.COMPLEX_UNIT_SP, (compactScreen ? 7.6f : 8.4f) * timetableFontScale);
            locationView.setMaxLines(spanCount > 1 ? 3 : 2);
            locationView.setSingleLine(false);
            locationView.setEllipsize(null);
            locationView.setLineSpacing(0f, 0.95f);

            content.addView(titleView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (hasTeacher) {
                content.addView(teacherView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            content.addView(locationView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            card.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            card.setOnClickListener(v -> {
                selectedCourseColorKey = entry.courseColorKey;
                drawGrid();
                showCourseDetailSheet(entry.course, entry.courseColorKey, entry.mergedCourses);
            });
            return card;
        }

        Agenda agenda = entry.agenda;
        TextView tv = new TextView(this);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f * timetableFontScale);
        tv.setPadding(dp(6), dp(6), dp(6), dp(6));
        tv.setGravity(Gravity.CENTER);
        tv.setMaxLines(6);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(tv, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        int customAgendaColor = normalizeAgendaStoredRenderColor(agenda == null ? 0 : agenda.renderColor);
        boolean hasCustomAgendaColor = customAgendaColor != 0;
        int agendaPriority = agenda == null ? Agenda.PRIORITY_LOW : agenda.priority;
        boolean useThemeEmphasis = hasCustomAgendaColor || agendaPriority == Agenda.PRIORITY_HIGH;
        int agendaBaseColor = hasCustomAgendaColor
            ? resolveAgendaRenderColor(agenda)
            : (agendaPriority == Agenda.PRIORITY_HIGH ? themeColor : getDefaultAgendaRenderColor());
        int agendaBg = useThemeEmphasis
            ? ColorUtils.blendARGB(glassBg, agendaBaseColor, hasCourseInCell ? (darkMode ? 0.18f : 0.16f) : (darkMode ? 0.32f : 0.24f))
            : glassBg;
        card.setCardBackgroundColor(agendaBg);
        if (hasCourseInCell && useThemeEmphasis) {
            card.setStrokeWidth(dp(1));
            card.setStrokeColor(ColorUtils.setAlphaComponent(agendaBaseColor, darkMode ? 170 : 148));
        } else {
            card.setStrokeWidth(0);
            card.setStrokeColor(Color.TRANSPARENT);
        }

        String title = agenda == null ? "" : safeText(agenda.title).trim();
        if (title.isEmpty()) {
            title = "未命名日程";
        }
        int fullStartMinute = agenda == null ? 0 : Math.max(0, agenda.startMinute);
        int fullEndMinute = agenda == null ? 30 : Math.max(fullStartMinute + 1, Math.min(24 * 60, agenda.endMinute));
        int firstVisibleMinute = Math.max(fullStartMinute, SLOT_START_SECONDS[0] / 60);
        boolean isContinuationSegment = entry.startMinute > firstVisibleMinute;
        if (isContinuationSegment) {
            tv.setText(title);
        } else {
            tv.setText(title + "\n" + formatMinute(fullStartMinute) + "-" + formatMinute(fullEndMinute));
        }
        tv.setTextColor(pickReadableTextColor(agendaBg));

        if (agenda != null) {
            Calendar targetDate = buildDateInAcademicWeek(week, dayOfWeek);
            card.setOnClickListener(v -> showAgendaEditorSheet(agenda, targetDate));
        }
        return card;
    }

    private String formatScheduleCellLocation(String locationRaw) {
        String standard = CampusBuildingStore.toStandardLocation(this, locationRaw);
        if (TextUtils.isEmpty(standard) || "未定".equals(standard)) {
            return "地点未定";
        }

        int splitIndex = -1;
        for (int i = 1; i < standard.length(); i++) {
            if (Character.isDigit(standard.charAt(i))) {
                splitIndex = i;
                break;
            }
        }
        if (splitIndex > 1 && splitIndex < standard.length() - 1) {
            return standard.substring(0, splitIndex) + "\n" + standard.substring(splitIndex);
        }

        if (standard.length() >= 7) {
            int mid = standard.length() / 2;
            return standard.substring(0, mid) + "\n" + standard.substring(mid);
        }
        return standard;
    }

    private String formatCourseEntryTimeRange(ScheduleCellEntry entry) {
        if (entry == null) {
            return "";
        }
        int startSlotIndex = Math.max(0, Math.min(SLOT_START_SECONDS.length - 1, entry.slotIndex));
        int spanCount = Math.max(1, getCourseEntrySpanCount(entry));
        int endSlotIndex = Math.max(startSlotIndex, Math.min(SLOT_END_SECONDS.length - 1, startSlotIndex + spanCount - 1));

        int startMinute = SLOT_START_SECONDS[startSlotIndex] / 60;
        int endMinute = SLOT_END_SECONDS[endSlotIndex] / 60;
        return formatMinute(startMinute) + "-" + formatMinute(endMinute);
    }

    private void drawGrid() {
        moveToCurrentWeek(false, true);
        refreshTodayPage();
        updateTitle();
        updateNextCourseNotice();
    }

    private void refreshTodayPage() {
        Calendar now = Calendar.getInstance();
        int actualWeek = getActualCurrentWeek();
        int currentDay = toMondayFirstDay(now);
        int currentSeconds = now.get(Calendar.HOUR_OF_DAY) * 3600 + now.get(Calendar.MINUTE) * 60 + now.get(Calendar.SECOND);

        Calendar displayDate = resolveSelectedTodayDate(now, actualWeek);
        int selectedDay = toMondayFirstDay(displayDate);
        int previewDay = resolveWeekOverviewPreviewDay(actualWeek, selectedDay);
        Calendar contentDate = buildDateInAcademicWeek(actualWeek, previewDay);
        boolean viewingToday = isSameDay(contentDate, now);

        if (tvTodayWeek != null) {
            tvTodayWeek.setText("周" + WEEK_DAY_LABELS[selectedDay - 1]);
        }
        if (tvTodayDate != null) {
            tvTodayDate.setText(String.format(Locale.getDefault(), "%d月%d日", displayDate.get(Calendar.MONTH) + 1, displayDate.get(Calendar.DAY_OF_MONTH)));
        }
        updateTodayHeaderClock();

        List<TodayCourseItem> courses = buildDateCourseItems(actualWeek, previewDay);
        List<Agenda> agendas = AgendaStorageManager.queryAgendasByDate(this, contentDate);
        renderTodayTimelineCards(courses, agendas, viewingToday);
        renderTodayWeekOverview(actualWeek, previewDay, currentDay, currentSeconds);
        styleTodayOverviewCard();
    }

    private void disableParentClipping(@NonNull View target) {
        View current = target;
        while (current.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) current.getParent();
            parent.setClipChildren(false);
            parent.setClipToPadding(false);
            current = parent;
        }
    }

    private Calendar resolveSelectedTodayDate(Calendar now, int actualWeek) {
        Calendar today = cloneAsDay(now);
        if (selectedTodayDate == null) {
            setSelectedTodayDate(today);
        }
        Calendar selected = cloneAsDay(selectedTodayDate);
        if (getWeekForDate(selected) != actualWeek) {
            selected = today;
            setSelectedTodayDate(selected);
        }
        return selected;
    }

    private void setSelectedTodayDate(Calendar date) {
        if (date == null) {
            return;
        }
        selectedTodayDate = cloneAsDay(date);
        syncWeekOverviewPreview(getWeekForDate(selectedTodayDate), toMondayFirstDay(selectedTodayDate));
    }

    private void syncWeekOverviewPreview(int week, int dayOfWeek) {
        weekOverviewPreviewWeek = week;
        weekOverviewPreviewDay = Math.max(1, Math.min(7, dayOfWeek));
    }

    private int resolveWeekOverviewPreviewDay(int actualWeek, int fallbackDay) {
        int safeFallbackDay = Math.max(1, Math.min(7, fallbackDay));
        if (weekOverviewPreviewWeek != actualWeek || weekOverviewPreviewDay < 1 || weekOverviewPreviewDay > 7) {
            syncWeekOverviewPreview(actualWeek, safeFallbackDay);
        }
        return weekOverviewPreviewDay;
    }

    private Calendar cloneAsDay(Calendar source) {
        Calendar copy = (Calendar) source.clone();
        copy.set(Calendar.HOUR_OF_DAY, 0);
        copy.set(Calendar.MINUTE, 0);
        copy.set(Calendar.SECOND, 0);
        copy.set(Calendar.MILLISECOND, 0);
        return copy;
    }

    private int getWeekForDate(Calendar targetDate) {
        Calendar start = getSemesterStartCalendar();
        while (start.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            start.add(Calendar.DAY_OF_MONTH, -1);
        }
        start = cloneAsDay(start);

        Calendar target = cloneAsDay(targetDate);
        long diff = target.getTimeInMillis() - start.getTimeInMillis();
        return (int) (diff / (7L * 24L * 60L * 60L * 1000L)) + 1;
    }

    private Calendar buildDateInAcademicWeek(int week, int dayOfWeek) {
        Calendar start = getSemesterStartCalendar();
        while (start.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            start.add(Calendar.DAY_OF_MONTH, -1);
        }
        start = cloneAsDay(start);
        start.add(Calendar.DAY_OF_MONTH, Math.max(0, week - 1) * 7);
        start.add(Calendar.DAY_OF_MONTH, Math.max(0, Math.min(6, dayOfWeek - 1)));
        return start;
    }

    private int toMondayFirstDay(Calendar date) {
        int raw = date.get(Calendar.DAY_OF_WEEK);
        return raw == Calendar.SUNDAY ? 7 : raw - 1;
    }

    private boolean isSameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private List<TodayCourseItem> buildDateCourseItems(int targetWeek, int targetDay) {
        List<TodayCourseItem> raw = new ArrayList<>();
        for (Course c : allCourses) {
            if (c == null || c.isRemark || c.dayOfWeek != targetDay) continue;
            if (c.weeks == null || !c.weeks.contains(targetWeek)) continue;
            int slot = Math.max(0, Math.min(SLOT_LABELS.length - 1, (c.startSection - 1) / 2));
            raw.add(new TodayCourseItem(c, slot));
        }
        Collections.sort(raw, (a, b) -> Integer.compare(SLOT_START_SECONDS[a.slotIndex], SLOT_START_SECONDS[b.slotIndex]));

        List<TodayCourseItem> merged = new ArrayList<>();
        int index = 0;
        while (index < raw.size()) {
            TodayCourseItem seed = raw.get(index);
            List<Course> mergeCourses = new ArrayList<>();
            mergeCourses.add(seed.course);

            int startSlot = seed.slotIndex;
            int endSlot = seed.slotIndex;
            int next = index + 1;
            while (next < raw.size()) {
                TodayCourseItem candidate = raw.get(next);
                if (candidate.slotIndex != endSlot + 1) {
                    break;
                }
                if (!isSameCourseMergeTarget(seed.course, candidate.course)) {
                    break;
                }
                mergeCourses.add(candidate.course);
                endSlot = candidate.slotIndex;
                next++;
            }

            merged.add(new TodayCourseItem(seed.course, startSlot, endSlot, mergeCourses));
            index = next;
        }
        return merged;
    }

    private void styleTodayOverviewCard() {
        if (cardTodayWeekOverview == null) return;
        int onSurface = UiStyleHelper.resolveOnSurfaceColor(this);
        cardTodayWeekOverview.setCardElevation(0f);
        cardTodayWeekOverview.setRadius(dp(24));
        cardTodayWeekOverview.setStrokeWidth(dp(1));
        cardTodayWeekOverview.setStrokeColor(ColorUtils.setAlphaComponent(onSurface, 24));
        cardTodayWeekOverview.setCardBackgroundColor(UiStyleHelper.resolveGlassCardColor(this));
    }

    private void renderTodayTimelineCards(List<TodayCourseItem> courses, List<Agenda> agendas, boolean viewingToday) {
        if (todayCoursesContainer == null) return;
        todayCoursesContainer.removeAllViews();

        List<TodayTimelineItem> timelineItems = new ArrayList<>();
        for (TodayCourseItem item : courses) {
            timelineItems.add(TodayTimelineItem.forCourse(item));
        }
        for (Agenda agenda : agendas) {
            timelineItems.add(TodayTimelineItem.forAgenda(agenda));
        }
        timelineItems.sort((a, b) -> {
            int byStart = Integer.compare(a.startMinute, b.startMinute);
            if (byStart != 0) {
                return byStart;
            }
            return Integer.compare(a.type, b.type);
        });

        if (timelineItems.isEmpty()) {
            MaterialCardView emptyCard = new MaterialCardView(this);
            emptyCard.setRadius(dp(22));
            emptyCard.setStrokeWidth(1);
            emptyCard.setStrokeColor(ColorUtils.setAlphaComponent(Color.WHITE, 28));
            emptyCard.setCardElevation(0f);
            emptyCard.setCardBackgroundColor(UiStyleHelper.resolveGlassCardColor(this));

            TextView text = new TextView(this);
            text.setText(viewingToday ? "今天暂无课程与日程" : "该日暂无课程与日程");
            text.setPadding(dp(18), dp(18), dp(18), dp(18));
            text.setTextSize(16f);
            text.setTextColor(UiStyleHelper.resolveOnSurfaceColor(this));
            emptyCard.addView(text);
            todayCoursesContainer.addView(emptyCard);
            return;
        }

        if (!viewingToday) {
            for (TodayTimelineItem item : timelineItems) {
                todayCoursesContainer.addView(createTodayTimelineCard(item));
            }
            return;
        }

        Calendar now = Calendar.getInstance();
        int nowMinute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        List<TodayTimelineItem> ongoingItems = new ArrayList<>();
        List<TodayTimelineItem> upcomingItems = new ArrayList<>();
        List<TodayTimelineItem> endedItems = new ArrayList<>();

        for (TodayTimelineItem item : timelineItems) {
            if (item.endMinute <= nowMinute) {
                endedItems.add(item);
            } else if (item.startMinute <= nowMinute) {
                ongoingItems.add(item);
            } else {
                upcomingItems.add(item);
            }
        }

        addTodayTimelineSection("进行中", ongoingItems);
        addTodayTimelineSection("将要开始", upcomingItems);

        if (!endedItems.isEmpty()) {
            MaterialCardView endedHeader = new MaterialCardView(this);
            endedHeader.setCardBackgroundColor(Color.TRANSPARENT);
            endedHeader.setCardElevation(0f);
            endedHeader.setStrokeWidth(0);
            endedHeader.setUseCompatPadding(false);
            endedHeader.setRadius(dp(12));
            endedHeader.setClickable(true);
            endedHeader.setFocusable(true);
            endedHeader.setRippleColor(ColorStateList.valueOf(Color.TRANSPARENT));
            endedHeader.setForeground(null);

            LinearLayout endedHeaderRow = new LinearLayout(this);
            endedHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
            endedHeaderRow.setGravity(Gravity.CENTER_VERTICAL);
            endedHeaderRow.setPadding(dp(10), dp(6), dp(10), dp(6));
                endedHeaderRow.setClickable(false);
                endedHeaderRow.setFocusable(false);

            TextView endedHeaderText = new TextView(this);
            endedHeaderText.setTextSize(13f);
            endedHeaderText.setTypeface(null, Typeface.BOLD);
            endedHeaderText.setTextColor(UiStyleHelper.resolveOnSurfaceColor(this));
            endedHeaderText.setIncludeFontPadding(false);
            endedHeaderRow.addView(endedHeaderText);

            ImageView endedHeaderArrow = new ImageView(this);
            LinearLayout.LayoutParams arrowLp = new LinearLayout.LayoutParams(dp(18), dp(18));
            arrowLp.setMargins(dp(6), 0, 0, 0);
            endedHeaderArrow.setLayoutParams(arrowLp);
            endedHeaderArrow.setImageResource(R.drawable.ic_chevron_up_wide_24);
            endedHeaderArrow.setImageTintList(ColorStateList.valueOf(UiStyleHelper.resolveOnSurfaceColor(this)));
            endedHeaderRow.addView(endedHeaderArrow);

            endedHeader.addView(endedHeaderRow, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout endedContainer = new LinearLayout(this);
            endedContainer.setOrientation(LinearLayout.VERTICAL);
            endedContainer.setVisibility(todayEndedTimelineCollapsed ? View.GONE : View.VISIBLE);

            for (TodayTimelineItem item : endedItems) {
                endedContainer.addView(createTodayTimelineCard(item));
            }

            Runnable refreshEndedHeader = () -> {
                endedHeaderText.setText("已结束（" + endedItems.size() + "）");
                endedHeaderArrow.setImageResource(todayEndedTimelineCollapsed
                        ? R.drawable.ic_chevron_down_wide_24
                        : R.drawable.ic_chevron_up_wide_24);
            };
            refreshEndedHeader.run();

            endedHeader.setOnClickListener(v -> {
                todayEndedTimelineCollapsed = !todayEndedTimelineCollapsed;
                endedContainer.setVisibility(todayEndedTimelineCollapsed ? View.GONE : View.VISIBLE);
                refreshEndedHeader.run();
            });

            todayCoursesContainer.addView(endedHeader);
            todayCoursesContainer.addView(endedContainer);
        }
    }

    private void addTodayTimelineSection(String sectionName, List<TodayTimelineItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        todayCoursesContainer.addView(createTodayTimelineSectionLabel(sectionName + "（" + items.size() + "）"));
        for (TodayTimelineItem item : items) {
            todayCoursesContainer.addView(createTodayTimelineCard(item));
        }
    }

    private TextView createTodayTimelineSectionLabel(String text) {
        TextView label = new TextView(this);
        int onSurface = UiStyleHelper.resolveOnSurfaceColor(this);
        label.setText(text);
        label.setTextColor(onSurface);
        label.setTextSize(13f);
        label.setTypeface(null, Typeface.BOLD);
        label.setIncludeFontPadding(false);
        label.setPadding(dp(4), dp(2), dp(4), dp(4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(2), 0, dp(8));
        label.setLayoutParams(lp);
        return label;
    }

    private MaterialCardView createTodayTimelineCard(TodayTimelineItem item) {
        return item.type == TodayTimelineItem.TYPE_COURSE
                ? createCourseTimelineCard(item.courseItem)
                : createAgendaTimelineCard(item.agenda);
    }

    private MaterialCardView createCourseTimelineCard(TodayCourseItem item) {
        String colorKey = buildCourseColorKey(item.course.name, item.course.isExperimental);
        boolean hasCustomColor = hasCustomCourseColor(item.course.name, item.course.isExperimental);
        int accent = hasCustomColor ? getCourseColor(item.course.name, item.course.isExperimental) : getTimetableThemeColor();
        int onSurface = UiStyleHelper.resolveOnSurfaceColor(this);
        int onSurfaceVariant = UiStyleHelper.resolveOnSurfaceVariantColor(this);

        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(24));
        card.setCardElevation(0f);
        card.setStrokeWidth(1);
        card.setStrokeColor(ColorUtils.setAlphaComponent(onSurface, 24));
        card.setCardBackgroundColor(UiStyleHelper.resolveGlassCardColor(this));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(cardLp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout leftCol = new LinearLayout(this);
        leftCol.setOrientation(LinearLayout.VERTICAL);

        TextView slot = new TextView(this);
        if (item.startSlotIndex == item.endSlotIndex) {
            slot.setText(SLOT_LABELS[item.startSlotIndex]);
        } else {
            slot.setText("第" + (item.startSlotIndex + 1) + "-" + (item.endSlotIndex + 1) + "大节");
        }
        slot.setTextColor(onSurface);
        slot.setTextSize(13f);
        slot.setTypeface(null, Typeface.BOLD);
        leftCol.addView(slot);

        TextView slotTime = new TextView(this);
        String startText = String.format(Locale.getDefault(), "%02d:%02d", SLOT_START_SECONDS[item.startSlotIndex] / 3600, (SLOT_START_SECONDS[item.startSlotIndex] % 3600) / 60);
        String endText = String.format(Locale.getDefault(), "%02d:%02d", SLOT_END_SECONDS[item.endSlotIndex] / 3600, (SLOT_END_SECONDS[item.endSlotIndex] % 3600) / 60);
        slotTime.setText(startText + "-" + endText);
        slotTime.setTextColor(onSurfaceVariant);
        slotTime.setTextSize(11f);
        slotTime.setPadding(0, dp(3), 0, 0);
        leftCol.addView(slotTime);
        row.addView(leftCol);

        View divider = new View(this);
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(dp(2), dp(34));
        dividerLp.setMargins(dp(14), 0, dp(14), 0);
        divider.setLayoutParams(dividerLp);
        divider.setBackgroundColor(ColorUtils.setAlphaComponent(accent, 180));
        row.addView(divider);

        TextView name = new TextView(this);
        String title = item.course.name + (item.course.isExperimental ? " [实验]" : "");
        name.setText(formatCourseTitleForCard(title));
        name.setTextColor(onSurface);
        name.setTextSize(19f);
        name.setTypeface(null, Typeface.BOLD);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        name.setLayoutParams(nameLp);
        row.addView(name);

        String locationText = CampusBuildingStore.toStandardLocation(this, item.course.location);
        if (locationText == null || locationText.trim().isEmpty()) {
            locationText = "地点未定";
        }

        TextView location = new TextView(this);
        location.setText(locationText);
        location.setTextColor(ColorUtils.setAlphaComponent(accent, 220));
        location.setTextSize(12f);
        location.setTypeface(null, Typeface.BOLD);
        location.setSingleLine(true);
        location.setEllipsize(TextUtils.TruncateAt.END);
        location.setMaxWidth(dp(110));
        location.setPadding(dp(12), dp(8), dp(12), dp(8));
        location.setBackground(makeRoundedSolid(ColorUtils.setAlphaComponent(accent, 48), dp(14)));
        row.addView(location);

        card.addView(row);
        card.setOnClickListener(v -> {
            selectedCourseColorKey = colorKey;
            drawGrid();
            showCourseDetailSheet(item.course, colorKey, item.mergedCourses);
        });
        return card;
    }

    private MaterialCardView createAgendaTimelineCard(Agenda agenda) {
        boolean hasCustomColor = normalizeAgendaStoredRenderColor(agenda == null ? 0 : agenda.renderColor) != 0;
        int accent = hasCustomColor ? resolveAgendaRenderColor(agenda) : getTimetableThemeColor();
        int onSurface = UiStyleHelper.resolveOnSurfaceColor(this);
        int onSurfaceVariant = UiStyleHelper.resolveOnSurfaceVariantColor(this);

        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(24));
        card.setCardElevation(0f);
        card.setStrokeWidth(1);
        card.setStrokeColor(ColorUtils.setAlphaComponent(onSurface, 24));
        card.setCardBackgroundColor(UiStyleHelper.resolveGlassCardColor(this));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(cardLp);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout leftCol = new LinearLayout(this);
        leftCol.setOrientation(LinearLayout.VERTICAL);

        TextView startTime = new TextView(this);
        startTime.setText(formatMinute(agenda.startMinute));
        startTime.setTextColor(onSurfaceVariant);
        startTime.setTextSize(12f);
        startTime.setTypeface(null, Typeface.BOLD);
        leftCol.addView(startTime);

        TextView endTime = new TextView(this);
        endTime.setText(formatMinute(agenda.endMinute));
        endTime.setTextColor(onSurfaceVariant);
        endTime.setTextSize(12f);
        endTime.setTypeface(null, Typeface.BOLD);
        endTime.setPadding(0, dp(2), 0, 0);
        leftCol.addView(endTime);
        row.addView(leftCol);

        View divider = new View(this);
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(dp(2), dp(36));
        dividerLp.setMargins(dp(14), 0, dp(14), 0);
        divider.setLayoutParams(dividerLp);
        divider.setBackgroundColor(ColorUtils.setAlphaComponent(accent, 180));
        row.addView(divider);

        TextView title = new TextView(this);
        title.setText(safeText(agenda.title));
        title.setTextColor(onSurface);
        title.setTextSize(18f);
        title.setTypeface(null, Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(titleLp);
        row.addView(title);

        String locationText = CampusBuildingStore.toStandardLocation(this, safeText(agenda.location));
        boolean hasLocation = !TextUtils.isEmpty(locationText) && !"未定".equals(locationText);

        TextView badge = new TextView(this);
        badge.setTextSize(12f);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setPadding(dp(12), dp(7), dp(12), dp(7));
        badge.setSingleLine(true);
        badge.setEllipsize(TextUtils.TruncateAt.END);
        badge.setMaxWidth(dp(138));
        if (hasLocation) {
            badge.setText(locationText);
            int locationColor = ColorUtils.setAlphaComponent(accent, 228);
            badge.setTextColor(locationColor);
            badge.setBackground(makeRoundedSolid(ColorUtils.setAlphaComponent(accent, 50), dp(14)));
        } else {
            badge.setText(priorityText(agenda.priority));
            int priorityColor = priorityColor(agenda.priority, accent, onSurfaceVariant);
            badge.setTextColor(ColorUtils.setAlphaComponent(priorityColor, 230));
            badge.setBackground(makeRoundedSolid(ColorUtils.setAlphaComponent(priorityColor, 48), dp(14)));
        }
        row.addView(badge);

        content.addView(row);

        String desc = safeText(agenda.description).trim();
        if (!desc.isEmpty()) {
            TextView descTv = new TextView(this);
            descTv.setText(desc);
            descTv.setTextColor(onSurfaceVariant);
            descTv.setTextSize(13f);
            descTv.setMaxLines(3);
            descTv.setEllipsize(TextUtils.TruncateAt.END);
            descTv.setPadding(0, dp(8), 0, 0);
            content.addView(descTv);
        }

        card.addView(content);
        card.setOnClickListener(v -> {
            Calendar anchor = AgendaStorageManager.parseDateOrNull(agenda.date);
            if (anchor == null) {
                anchor = resolveSelectedTodayDate(Calendar.getInstance(), getActualCurrentWeek());
            }
            showAgendaEditorSheet(agenda, anchor);
        });
        return card;
    }

    private void renderTodayWeekOverview(int actualWeek, int activeDay, int currentDay, int currentSeconds) {
        if (layoutTodayWeekStrip == null) return;
        layoutTodayWeekStrip.removeAllViews();
        layoutTodayWeekStrip.setGravity(Gravity.CENTER_HORIZONTAL);
        layoutTodayWeekStrip.setBaselineAligned(false);
        int accent = getTimetableThemeColor();
        int onSurface = UiStyleHelper.resolveOnSurfaceColor(this);
        int onSurfaceVariant = UiStyleHelper.resolveOnSurfaceVariantColor(this);
        int dotAreaHeight = dp(WEEK_OVERVIEW_TODAY_DOT_SIZE_DP + WEEK_OVERVIEW_TODAY_DOT_GAP_DP);

        int weekTotal = 0;
        int weekDone = 0;
        int[] dayCombinedCounts = new int[7];
        int maxCombinedCount = 0;

        for (int day = 1; day <= 7; day++) {
            int dayCourseCount = 0;
            for (Course c : allCourses) {
                if (c == null || c.isRemark) continue;
                if (c.dayOfWeek != day) continue;
                if (c.weeks == null || !c.weeks.contains(actualWeek)) continue;
                weekTotal++;
                dayCourseCount++;
                int slot = Math.max(0, Math.min(SLOT_LABELS.length - 1, (c.startSection - 1) / 2));
                if (day < currentDay || (day == currentDay && SLOT_END_SECONDS[slot] < currentSeconds)) {
                    weekDone++;
                }
            }

            int dayAgendaCount = AgendaStorageManager.queryAgendasByDate(this, buildDateInAcademicWeek(actualWeek, day)).size();
            dayCombinedCounts[day - 1] = dayCourseCount + dayAgendaCount;
            if (dayCombinedCounts[day - 1] > maxCombinedCount) {
                maxCombinedCount = dayCombinedCounts[day - 1];
            }
        }

        int maxBarHeight = resolveWeekOverviewCapsuleHeight(maxCombinedCount);

        for (int day = 1; day <= 7; day++) {
            int combinedCount = dayCombinedCounts[day - 1];
            int chipHeight = resolveWeekOverviewCapsuleHeight(combinedCount);
            boolean isActive = day == activeDay;
            boolean isToday = day == currentDay;

            LinearLayout barColumn = new LinearLayout(this);
            barColumn.setOrientation(LinearLayout.VERTICAL);
            barColumn.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams columnLp = new LinearLayout.LayoutParams(0, maxBarHeight + dotAreaHeight, 1f);
            columnLp.setMargins(dp(3), dp(2), dp(3), 0);
            barColumn.setLayoutParams(columnLp);

            FrameLayout barAnchor = new FrameLayout(this);
            LinearLayout.LayoutParams barAnchorLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxBarHeight);
            barAnchor.setLayoutParams(barAnchorLp);

            TextView chip = new TextView(this);
            chip.setText(WEEK_DAY_LABELS[day - 1]);
            chip.setGravity(Gravity.CENTER);
            chip.setTextSize(13f);
            chip.setTypeface(null, Typeface.BOLD);
            chip.setTextColor(isActive ? pickReadableTextColor(accent) : onSurfaceVariant);
            chip.setBackground(makeRoundedSolid(isActive ? accent : ColorUtils.setAlphaComponent(onSurface, 28), dp(14)));
            chip.setPadding(0, 0, 0, 0);
            int targetDay = day;
            chip.setOnClickListener(v -> {
                syncWeekOverviewPreview(actualWeek, targetDay);
                refreshTodayPage();
            });

            FrameLayout.LayoutParams chipLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, chipHeight, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            chipLp.setMargins(dp(2), 0, dp(2), 0);
            chip.setLayoutParams(chipLp);
            barAnchor.addView(chip);
            barColumn.addView(barAnchor);

            View todayDot = new View(this);
            GradientDrawable dotDrawable = new GradientDrawable();
            dotDrawable.setShape(GradientDrawable.OVAL);
            dotDrawable.setColor(isToday ? accent : Color.TRANSPARENT);
            todayDot.setBackground(dotDrawable);
            todayDot.setVisibility(isToday ? View.VISIBLE : View.INVISIBLE);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(WEEK_OVERVIEW_TODAY_DOT_SIZE_DP), dp(WEEK_OVERVIEW_TODAY_DOT_SIZE_DP));
            dotLp.topMargin = dp(WEEK_OVERVIEW_TODAY_DOT_GAP_DP);
            dotLp.gravity = Gravity.CENTER_HORIZONTAL;
            todayDot.setLayoutParams(dotLp);
            barColumn.addView(todayDot);

            layoutTodayWeekStrip.addView(barColumn);
        }

        if (tvTodayWeekTotal != null) {
            tvTodayWeekTotal.setText("共" + weekTotal + "节");
        }
        if (tvTodayWeekDone != null) {
            tvTodayWeekDone.setText("已上" + weekDone + "节");
        }
    }

    private int resolveWeekOverviewCapsuleHeight(int combinedCount) {
        int minHeight = dp(WEEK_OVERVIEW_BAR_MIN_HEIGHT_DP);
        int maxHeight = dp(WEEK_OVERVIEW_BAR_MAX_HEIGHT_DP);
        if (maxHeight <= minHeight || WEEK_OVERVIEW_BAR_COUNT_CAP <= 0) {
            return minHeight;
        }
        int clampedCount = Math.max(0, Math.min(WEEK_OVERVIEW_BAR_COUNT_CAP, combinedCount));
        float ratio = clampedCount / (float) WEEK_OVERVIEW_BAR_COUNT_CAP;
        return minHeight + Math.round((maxHeight - minHeight) * ratio);
    }

    private int priorityColor(int priority, int accentColor, int fallback) {
        if (priority == Agenda.PRIORITY_HIGH) {
            return Color.parseColor("#D35400");
        }
        if (priority == Agenda.PRIORITY_LOW) {
            return fallback;
        }
        return accentColor;
    }

    private String priorityText(int priority) {
        if (priority == Agenda.PRIORITY_HIGH) {
            return "高";
        }
        if (priority == Agenda.PRIORITY_LOW) {
            return "低";
        }
        return "中";
    }

    private String repeatText(Agenda agenda) {
        String repeat = agenda == null ? "" : safeText(agenda.repeatRule).toLowerCase(Locale.ROOT);
        if (Agenda.REPEAT_DAILY.equals(repeat)) {
            return "重复: 每天";
        }
        if (Agenda.REPEAT_WEEKLY.equals(repeat)) {
            return "重复: 每周";
        }
        if (Agenda.REPEAT_MONTHLY.equals(repeat)) {
            boolean monthEnd = Agenda.MONTHLY_MONTH_END.equals(safeText(agenda.monthlyStrategy).toLowerCase(Locale.ROOT));
            return monthEnd ? "重复: 每月固定日(短月改月底)" : "重复: 每月固定日(短月跳过)";
        }
        return "重复: 不重复";
    }

    private String formatMinute(int minute) {
        int clamped = Math.max(0, Math.min(24 * 60, minute));
        return String.format(Locale.getDefault(), "%02d:%02d", clamped / 60, clamped % 60);
    }

    private String formatMergedCourseSectionLabel(ScheduleCellEntry entry) {
        int spanCount = Math.max(1, getCourseEntrySpanCount(entry));
        int startSlot = Math.max(1, Math.min(SLOT_LABELS.length, entry.slotIndex + 1));
        int endSlot = Math.max(startSlot, Math.min(SLOT_LABELS.length, startSlot + spanCount - 1));
        if (startSlot == endSlot) {
            return "第" + startSlot + "大节";
        }
        return "第" + startSlot + "-" + endSlot + "大节";
    }

    private String formatCourseSectionForDisplay(List<Course> courses) {
        if (courses == null || courses.isEmpty()) {
            return "未定";
        }
        int day = Math.max(1, Math.min(7, courses.get(0).dayOfWeek));
        List<Integer> slots = collectDistinctCourseSlots(courses);
        if (slots.isEmpty()) {
            return "未定";
        }
        return "周" + day + " 第" + formatCourseSlotRanges(slots) + "大节";
    }

    private List<Integer> collectDistinctCourseSlots(List<Course> courses) {
        List<Integer> slots = new ArrayList<>();
        if (courses == null) {
            return slots;
        }
        for (Course one : courses) {
            if (one == null) {
                continue;
            }
            int slot = Math.max(1, Math.min(SLOT_LABELS.length, (one.startSection - 1) / 2 + 1));
            if (!slots.contains(slot)) {
                slots.add(slot);
            }
        }
        Collections.sort(slots);
        return slots;
    }

    private String formatCourseSlotRanges(List<Integer> slots) {
        if (slots == null || slots.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int start = slots.get(0);
        int end = start;
        for (int i = 1; i < slots.size(); i++) {
            int current = slots.get(i);
            if (current == end + 1) {
                end = current;
                continue;
            }
            appendSlotRange(builder, start, end);
            builder.append("、");
            start = end = current;
        }
        appendSlotRange(builder, start, end);
        return builder.toString();
    }

    private List<Course> collectCourseSectionTargets(Course anchorCourse, List<Course> mergedCourses) {
        List<Course> targets = new ArrayList<>();
        if (anchorCourse == null) {
            return targets;
        }

        if (mergedCourses != null) {
            for (Course one : mergedCourses) {
                if (one == null || one.isRemark) {
                    continue;
                }
                if (!isSameCourseMergeTarget(anchorCourse, one) || !hasSameWeekPattern(anchorCourse, one)) {
                    continue;
                }
                if (!targets.contains(one)) {
                    targets.add(one);
                }
            }
        }

        if (!targets.contains(anchorCourse)) {
            targets.add(anchorCourse);
        }

        for (Course one : allCourses) {
            if (one == null || one.isRemark) {
                continue;
            }
            if (!isSameCourseMergeTarget(anchorCourse, one) || !hasSameWeekPattern(anchorCourse, one)) {
                continue;
            }
            if (!targets.contains(one)) {
                targets.add(one);
            }
        }

        targets.sort((a, b) -> Integer.compare(a.startSection, b.startSection));
        return targets;
    }

    private boolean hasSameWeekPattern(Course first, Course second) {
        if (first == null || second == null) {
            return false;
        }
        List<Integer> firstWeeks = first.weeks == null ? new ArrayList<>() : new ArrayList<>(first.weeks);
        List<Integer> secondWeeks = second.weeks == null ? new ArrayList<>() : new ArrayList<>(second.weeks);
        Collections.sort(firstWeeks);
        Collections.sort(secondWeeks);
        return firstWeeks.equals(secondWeeks);
    }

    private void appendSlotRange(StringBuilder builder, int start, int end) {
        if (builder == null) {
            return;
        }
        if (start == end) {
            builder.append(start);
            return;
        }
        builder.append(start).append("-").append(end);
    }

    private String safeText(String text) {
        return text == null ? "" : text;
    }

    private android.graphics.drawable.GradientDrawable makeRoundedSolid(int color, int radiusDp) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private CharSequence formatCourseTitleForCard(String title) {
        String raw = title == null ? "" : title.trim();
        if (raw.isEmpty()) {
            return "未命名课程";
        }
        if (raw.length() <= 8) {
            return raw;
        }

        int preferredBreak = Math.min(9, Math.max(6, raw.length() / 2));
        int breakAt = -1;
        for (int offset = 0; offset <= 3; offset++) {
            int right = preferredBreak + offset;
            if (right < raw.length() && isGoodBreakChar(raw.charAt(right))) {
                breakAt = right;
                break;
            }
            int left = preferredBreak - offset;
            if (left > 2 && left < raw.length() && isGoodBreakChar(raw.charAt(left))) {
                breakAt = left;
                break;
            }
        }
        if (breakAt < 0) {
            breakAt = preferredBreak;
        }

        String first = raw.substring(0, breakAt).trim();
        String second = raw.substring(breakAt).trim();
        if (first.isEmpty() || second.isEmpty()) {
            return raw;
        }
        return first + "\n" + second;
    }

    private boolean isGoodBreakChar(char ch) {
        return ch == ' ' || ch == '·' || ch == '•' || ch == ':' || ch == '：' || ch == '-' || ch == '/' || ch == '（' || ch == '(';
    }

    private static class TodayCourseItem {
        final Course course;
        final int slotIndex;
        final int startSlotIndex;
        final int endSlotIndex;
        final List<Course> mergedCourses;

        TodayCourseItem(Course course, int slotIndex) {
            this(course, slotIndex, slotIndex, null);
        }

        TodayCourseItem(Course course, int startSlotIndex, int endSlotIndex, List<Course> mergedCourses) {
            this.course = course;
            this.slotIndex = startSlotIndex;
            this.startSlotIndex = startSlotIndex;
            this.endSlotIndex = Math.max(startSlotIndex, endSlotIndex);
            this.mergedCourses = mergedCourses;
        }
    }

    private static final class TodayTimelineItem {
        static final int TYPE_COURSE = 0;
        static final int TYPE_AGENDA = 1;

        final int type;
        final int startMinute;
        final int endMinute;
        final TodayCourseItem courseItem;
        final Agenda agenda;

        private TodayTimelineItem(int type, int startMinute, int endMinute, TodayCourseItem courseItem, Agenda agenda) {
            this.type = type;
            this.startMinute = startMinute;
            this.endMinute = endMinute;
            this.courseItem = courseItem;
            this.agenda = agenda;
        }

        static TodayTimelineItem forCourse(TodayCourseItem item) {
            int startMinute = SLOT_START_SECONDS[item.startSlotIndex] / 60;
            int endMinute = SLOT_END_SECONDS[item.endSlotIndex] / 60;
            return new TodayTimelineItem(TYPE_COURSE, startMinute, endMinute, item, null);
        }

        static TodayTimelineItem forAgenda(Agenda agenda) {
            int startMinute = Math.max(0, agenda.startMinute);
            int endMinute = Math.max(startMinute + 1, agenda.endMinute);
            return new TodayTimelineItem(TYPE_AGENDA, startMinute, endMinute, null, agenda);
        }
    }

    private static final class ScheduleCellEntry {
        static final int TYPE_COURSE = 0;
        static final int TYPE_AGENDA = 1;

        final int type;
        final int slotIndex;
        final int startMinute;
        final int endMinute;
        final Course course;
        final Agenda agenda;
        final List<Course> mergedCourses;
        final String courseColorKey;
        final boolean isCurrentCourse;
        final boolean isSelectedCourse;
        final boolean hasCustomColor;
        final int customColor;

        private ScheduleCellEntry(int type,
                                  int slotIndex,
                                  int startMinute,
                                  int endMinute,
                                  Course course,
                                  Agenda agenda,
                                  List<Course> mergedCourses,
                                  String courseColorKey,
                                  boolean isCurrentCourse,
                                  boolean isSelectedCourse,
                                  boolean hasCustomColor,
                                  int customColor) {
            this.type = type;
            this.slotIndex = slotIndex;
            this.startMinute = startMinute;
            this.endMinute = endMinute;
            this.course = course;
            this.agenda = agenda;
            this.mergedCourses = mergedCourses;
            this.courseColorKey = courseColorKey;
            this.isCurrentCourse = isCurrentCourse;
            this.isSelectedCourse = isSelectedCourse;
            this.hasCustomColor = hasCustomColor;
            this.customColor = customColor;
        }

        static ScheduleCellEntry forCourse(Course course,
                                           int slotIndex,
                                           String colorKey,
                                           boolean isCurrentCourse,
                                           boolean isSelectedCourse,
                                           boolean hasCustomColor,
                                           int customColor) {
            int startMinute = SLOT_START_SECONDS[Math.max(0, Math.min(SLOT_START_SECONDS.length - 1, slotIndex))] / 60;
            int endMinute = SLOT_END_SECONDS[Math.max(0, Math.min(SLOT_END_SECONDS.length - 1, slotIndex))] / 60;
            return new ScheduleCellEntry(TYPE_COURSE, slotIndex, startMinute, endMinute, course, null, null,
                    colorKey, isCurrentCourse, isSelectedCourse, hasCustomColor, customColor);
        }

        static ScheduleCellEntry forMergedCourse(Course course,
                                                 int slotIndex,
                                                 int startMinute,
                                                 int endMinute,
                                                 String colorKey,
                                                 boolean isCurrentCourse,
                                                 boolean isSelectedCourse,
                                                 boolean hasCustomColor,
                                                 int customColor,
                                                 List<Course> mergedCourses) {
            return new ScheduleCellEntry(TYPE_COURSE, slotIndex, startMinute, endMinute, course, null, mergedCourses,
                    colorKey, isCurrentCourse, isSelectedCourse, hasCustomColor, customColor);
        }

        static ScheduleCellEntry forAgenda(Agenda agenda, int slotIndex, int segmentStartMinute, int segmentEndMinute) {
            int startMinute = Math.max(0, segmentStartMinute);
            int endMinute = Math.max(startMinute + 1, segmentEndMinute);
            return new ScheduleCellEntry(TYPE_AGENDA, slotIndex, startMinute, endMinute, null, agenda, null,
                    null, false, false, false, 0);
        }
    }

    private static final class ScheduleVerticalMergeResult {
        final ScheduleCellEntry entry;
        final int rowSpan;

        ScheduleVerticalMergeResult(ScheduleCellEntry entry, int rowSpan) {
            this.entry = entry;
            this.rowSpan = Math.max(1, rowSpan);
        }
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
        boolean inTodayPage = pageToday != null && pageToday.getVisibility() == View.VISIBLE;
        if (!inTodayPage || nextCourseNoticeDismissed) {
            CampusBuildingStore.setRealtimeDeviceLocationTracking(this, false);
            cardNextCourseNotice.setVisibility(View.GONE);
            return;
        }

        NextCourseNotice next = buildTodayNextCourseMessage();
        if (next == null) {
            CampusBuildingStore.setRealtimeDeviceLocationTracking(this, false);
            cardNextCourseNotice.setVisibility(View.GONE);
            return;
        }

        CampusBuildingStore.setRealtimeDeviceLocationTracking(this, true);

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

        UpcomingNoticeItem best = null;
        for (Course c : allCourses) {
            if (c == null || c.isRemark) continue;
            if (c.dayOfWeek != today) continue;
            if (c.weeks == null || !c.weeks.contains(actualWeek)) continue;

            int slot = (c.startSection - 1) / 2;
            if (slot < 0 || slot >= starts.length) continue;
            int start = starts[slot];
            if (start > currentSeconds && (best == null || start < best.startSeconds)) {
                String courseLabel = c.name + (c.isExperimental ? "[实验]" : "");
                best = UpcomingNoticeItem.forCourse(start, courseLabel, c.location);
            }
        }

        List<Agenda> todayAgendas = AgendaStorageManager.queryAgendasByDate(this, now);
        for (Agenda agenda : todayAgendas) {
            if (agenda == null) continue;
            int start = Math.max(0, agenda.startMinute) * 60;
            if (start <= currentSeconds) continue;
            if (best == null || start < best.startSeconds) {
                best = UpcomingNoticeItem.forAgenda(start, safeText(agenda.title), safeText(agenda.location));
            }
        }

        if (best == null) return null;

        int seconds = best.startSeconds - currentSeconds;
        if (seconds <= 0 || seconds > NEXT_NOTICE_WINDOW_SECONDS) {
            return null;
        }

        String startTime = String.format(Locale.getDefault(), "%02d:%02d", best.startSeconds / 3600, (best.startSeconds % 3600) / 60);
        String location = CampusBuildingStore.toStandardLocation(this, best.locationRaw);
        boolean hasLocation = !TextUtils.isEmpty(location) && !"未定".equals(location);

        StringBuilder plainBuilder = new StringBuilder();
        plainBuilder.append(best.isAgenda ? "即将开始 " : "下节课 ")
                .append(startTime)
                .append(" ")
                .append(TextUtils.isEmpty(best.title) ? (best.isAgenda ? "未命名日程" : "未命名课程") : best.title)
                .append("\n还有")
                .append(formatDuration(seconds));

        if (hasLocation) {
            plainBuilder.append("，地点").append(location);
        }

        if (hasLocation && CampusBuildingStore.hasLocationPermission(this)) {
            CampusBuildingStore.DistanceInfo distanceInfo = CampusBuildingStore.estimateDistanceFromDevice(this, best.locationRaw, false);
            if (distanceInfo.available) {
                if (distanceInfo.meters < 100f) {
                    plainBuilder.append("\n地点在附近");
                } else {
                    int walkSeconds = Math.max(1, Math.round(distanceInfo.meters / 1.35f));
                    plainBuilder.append("\n距离约")
                            .append(formatDistanceMeters(distanceInfo.meters))
                            .append("，步行约")
                            .append(formatDuration(walkSeconds));
                }
            }
        }

        String plain = plainBuilder.toString();
        SpannableString styled = new SpannableString(plain);
        int timeStart = plain.indexOf(startTime);
        if (timeStart >= 0) {
            styled.setSpan(new StyleSpan(Typeface.BOLD), timeStart, timeStart + startTime.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        int locStart = hasLocation ? plain.lastIndexOf(location) : -1;
        if (locStart >= 0 && hasLocation) {
            styled.setSpan(new StyleSpan(Typeface.BOLD), locStart, locStart + location.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return new NextCourseNotice(styled);
    }

    private static final class UpcomingNoticeItem {
        final boolean isAgenda;
        final int startSeconds;
        final String title;
        final String locationRaw;

        private UpcomingNoticeItem(boolean isAgenda, int startSeconds, String title, String locationRaw) {
            this.isAgenda = isAgenda;
            this.startSeconds = startSeconds;
            this.title = title;
            this.locationRaw = locationRaw;
        }

        static UpcomingNoticeItem forCourse(int startSeconds, String title, String locationRaw) {
            return new UpcomingNoticeItem(false, startSeconds, title, locationRaw);
        }

        static UpcomingNoticeItem forAgenda(int startSeconds, String title, String locationRaw) {
            return new UpcomingNoticeItem(true, startSeconds, title, locationRaw);
        }
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
                String activeCookie = finalCookie;
                ExtractOutcome outcome = scrapeAllTablesOnce(activeCookie);
                if (!outcome.hasAnySuccess || countNonRemarkCourses(outcome.courses) == 0) {
                    String refreshedCookie = trySilentLoginAndGetCookie(finalCookie);
                    if (refreshedCookie != null && !refreshedCookie.isEmpty()) {
                        ExtractOutcome retryOutcome = scrapeAllTablesOnce(refreshedCookie);
                        if (retryOutcome.hasAnySuccess && countNonRemarkCourses(retryOutcome.courses) > 0) {
                            outcome = retryOutcome;
                            activeCookie = refreshedCookie;
                        }
                    }
                }

                if (!outcome.hasAnySuccess || countNonRemarkCourses(outcome.courses) == 0) {
                    runOnUiThread(() -> Toast.makeText(this, "刷新失败：未获取到课表页面，请确认已在教务系统登录", Toast.LENGTH_LONG).show());
                    return;
                }
                
                List<Course> finalResult = deduplicate(outcome.courses);
                StudentProfile refreshedProfile = null;
                try {
                    refreshedProfile = fetchStudentProfile(activeCookie);
                    if (refreshedProfile != null) {
                        saveProfileToLocal(refreshedProfile);
                    }
                } catch (Exception profileEx) {
                    Log.w(TAG, "Profile refresh failed", profileEx);
                }

                final StudentProfile finalProfile = refreshedProfile;

                runOnUiThread(() -> {
                    allCourses.clear();
                    allCourses.addAll(finalResult);
                    calculateCurrentWeek();
                    saveCoursesToLocal();
                    updateScheduleViewState();
                    moveToCurrentWeek(true, true);
                    drawGrid();
                    if (finalProfile != null) {
                        renderProfile(finalProfile);
                    } else {
                        renderProfileFromLocal();
                    }
                    String toast = "刷新完成，共 " + allCourses.size() + " 门课程";
                    Toast.makeText(this, toast, Toast.LENGTH_SHORT).show();
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

    private static class StudentProfile {
        String name = "";
        String studentId = "";
        String className = "";
        String college = "";
    }

    private StudentProfile fetchStudentProfile(String cookie) throws Exception {
        if (cookie == null || cookie.trim().isEmpty()) {
            throw new IllegalStateException("缺少教务Cookie");
        }
        String html = fetch(PROFILE_URL, cookie);
        Document doc = Jsoup.parse(html);

        Element title = doc.selectFirst(".infoContentTitle");
        if (title == null || title.text().trim().isEmpty()) {
            throw new IllegalStateException("未找到个人信息区域");
        }

        StudentProfile profile = new StudentProfile();
        parseNameAndStudentId(title.text(), profile);

        Elements detailTexts = doc.select(".infoContentBody .qz-detailtext");
        for (Element detail : detailTexts) {
            if (detail == null) continue;
            String text = detail.text();
            if (text == null || text.trim().isEmpty()) continue;

            String normalized = text.replace('\u00A0', ' ');
            String[] parts = normalized.split("[：:]", 2);
            if (parts.length < 2) continue;

            String rawLabel = parts[0] == null ? "" : parts[0];
            String label = rawLabel.replace(" ", "").trim();
            String value = parts[1] == null ? "" : parts[1].trim();
            if (value.isEmpty()) continue;

            if (label.contains("学院")) {
                profile.college = value;
            } else if (label.contains("班级")) {
                profile.className = value;
            }
        }
        return profile;
    }

    private void parseNameAndStudentId(String titleText, StudentProfile profile) {
        String raw = titleText == null ? "" : titleText.trim();
        if (raw.isEmpty()) return;

        Matcher dashMatcher = Pattern.compile("^(.*?)[-－—]\\s*(\\d{6,})\\s*$").matcher(raw);
        if (dashMatcher.find()) {
            profile.name = dashMatcher.group(1).trim();
            profile.studentId = dashMatcher.group(2).trim();
            return;
        }

        Matcher idMatcher = Pattern.compile("(\\d{6,})$").matcher(raw);
        if (idMatcher.find()) {
            profile.studentId = idMatcher.group(1).trim();
            String before = raw.substring(0, idMatcher.start()).trim();
            before = before.replaceAll("[-－—]+$", "").trim();
            profile.name = before;
            return;
        }

        profile.name = raw;
    }

    private void saveProfileToLocal(StudentProfile profile) {
        if (profile == null) return;
        SharedPreferences.Editor editor = getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit();
        editor.putString(KEY_PROFILE_NAME, profile.name == null ? "" : profile.name);
        editor.putString(KEY_PROFILE_STUDENT_ID, profile.studentId == null ? "" : profile.studentId);
        editor.putString(KEY_PROFILE_CLASS, profile.className == null ? "" : profile.className);
        editor.putString(KEY_PROFILE_COLLEGE, profile.college == null ? "" : profile.college);
        editor.apply();
    }

    private StudentProfile readProfileFromLocal() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        StudentProfile profile = new StudentProfile();
        profile.name = prefs.getString(KEY_PROFILE_NAME, "");
        profile.studentId = prefs.getString(KEY_PROFILE_STUDENT_ID, "");
        profile.className = prefs.getString(KEY_PROFILE_CLASS, "");
        profile.college = prefs.getString(KEY_PROFILE_COLLEGE, "");
        return profile;
    }

    private void renderProfileFromLocal() {
        renderProfile(readProfileFromLocal());
    }

    private void renderProfile(StudentProfile profile) {
        if (profile == null) {
            profile = new StudentProfile();
        }

        if (tvProfileName != null) {
            tvProfileName.setText("姓名：" + (TextUtils.isEmpty(profile.name) ? "--" : profile.name));
        }
        if (tvProfileStudentId != null) {
            tvProfileStudentId.setText("学号：" + (TextUtils.isEmpty(profile.studentId) ? "--" : profile.studentId));
        }
        if (tvProfileClass != null) {
            tvProfileClass.setText("班级：" + (TextUtils.isEmpty(profile.className) ? "--" : profile.className));
        }
        if (tvProfileCollege != null) {
            tvProfileCollege.setText("学院：" + (TextUtils.isEmpty(profile.college) ? "--" : profile.college));
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
            if (startSec > getMaxSupportedSection()) {
                continue;
            }
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
        for (int r = 0; r < maxRows; r++) {
            int sectionStart = 0;
            for (int c = 0; c < maxCols; c++) {
                Element cell = grid[r][c];
                if (cell == null) {
                    continue;
                }
                sectionStart = extractSectionStartFromText(cell.text());
                if (sectionStart > 0) {
                    break;
                }
            }
            rowToSession[r] = sectionStart;
            if (r > 0 && rowToSession[r] == 0) {
                rowToSession[r] = rowToSession[r - 1];
            }
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
        if (!isSupportedStartSection(startSection)) {
            return;
        }
        String text = container.text().trim();
        if (text.isEmpty() || text.length() < 2) return;
        int declaredStartSection = extractSectionStartFromText(text);
        if (declaredStartSection > getMaxSupportedSection()) {
            return;
        }
        
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

    private void ensureLocationPermission() {
        if (CampusBuildingStore.hasLocationPermission(this)) {
            return;
        }
        if (locationPermissionLauncher != null) {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
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
            if (info.meters < 100f) {
                return base + "（在附近）";
            }
            return base + "（距离" + formatDistanceMeters(info.meters) + "）";
        }
        return base;
    }

    private String formatLocationBase(String locationRaw) {
        String base = CampusBuildingStore.toStandardLocation(this, locationRaw);
        if (TextUtils.isEmpty(base)) {
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

    private String formatDistanceMeters(float meters) {
        if (meters < 1000f) {
            return String.valueOf(Math.round(meters)) + "米";
        }
        return String.format(Locale.getDefault(), "%.1f公里", meters / 1000f);
    }

    private void showCustomTeacherInputDialog(Course c, Runnable afterPick) {
        EditText input = new EditText(this);
        input.setHint("输入教师名称");
        input.setText(c.teacher == null ? "" : c.teacher);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);

        newMaterialYouDialogBuilder()
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

        newMaterialYouDialogBuilder()
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
            if (!isSupportedStartSection(c.startSection)) continue;
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

    private int getMaxSupportedSection() {
        return Math.min(SLOT_LABELS.length * 2, MAX_IMPORTED_SECTION);
    }

    private boolean isSupportedStartSection(int startSection) {
        return startSection >= 1 && startSection <= getMaxSupportedSection();
    }

    private int extractSectionStartFromText(String rawText) {
        String normalized = safeText(rawText).replace("　", "").replace(" ", "");
        if (normalized.isEmpty()) {
            return 0;
        }

        Matcher bigMatcher = Pattern.compile("第([一二三四五六七八九十零〇两\\d]{1,3})大节").matcher(normalized);
        if (bigMatcher.find()) {
            int bigIndex = parseNumberToken(bigMatcher.group(1));
            return bigIndex > 0 ? bigIndex * 2 - 1 : 0;
        }

        Matcher sectionMatcher = Pattern.compile("第([一二三四五六七八九十零〇两\\d]{1,3})(?:[-~～到至][一二三四五六七八九十零〇两\\d]{1,3})?小?节").matcher(normalized);
        if (sectionMatcher.find()) {
            return parseNumberToken(sectionMatcher.group(1));
        }
        return 0;
    }

    private int parseNumberToken(String token) {
        String value = safeText(token).trim();
        if (value.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
        }

        String normalized = value.replace('两', '二').replace('〇', '零');
        if ("十".equals(normalized)) {
            return 10;
        }

        int tenIndex = normalized.indexOf('十');
        if (tenIndex >= 0) {
            int tens = 1;
            if (tenIndex > 0) {
                tens = parseChineseDigit(normalized.charAt(tenIndex - 1));
                if (tens < 0) {
                    return 0;
                }
            }
            int ones = 0;
            if (tenIndex < normalized.length() - 1) {
                ones = parseChineseDigit(normalized.charAt(tenIndex + 1));
                if (ones < 0) {
                    return 0;
                }
            }
            return tens * 10 + ones;
        }

        if (normalized.length() == 1) {
            int digit = parseChineseDigit(normalized.charAt(0));
            return digit < 0 ? 0 : digit;
        }
        return 0;
    }

    private int parseChineseDigit(char ch) {
        switch (ch) {
            case '零':
                return 0;
            case '一':
                return 1;
            case '二':
                return 2;
            case '三':
                return 3;
            case '四':
                return 4;
            case '五':
                return 5;
            case '六':
                return 6;
            case '七':
                return 7;
            case '八':
                return 8;
            case '九':
                return 9;
            default:
                return -1;
        }
    }

    private void sanitizeCourseSectionRange(List<Course> courses) {
        if (courses == null || courses.isEmpty()) {
            return;
        }
        for (int i = courses.size() - 1; i >= 0; i--) {
            Course c = courses.get(i);
            if (c == null) {
                courses.remove(i);
                continue;
            }
            if (!c.isRemark && !isSupportedStartSection(c.startSection)) {
                courses.remove(i);
            }
        }
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
        int bgColor = UiStyleHelper.resolvePageBackgroundColor(this);
        if (rootMain != null) {
            rootMain.setBackgroundColor(bgColor);
        }
        applyMaterialScaffoldStyle();
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
        CourseStorageManager.clearCourses(this);
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
        return CourseJsonCodec.toJson(allCourses);
    }

    private void parseJson(String json) throws Exception {
        List<Course> parsed = CourseJsonCodec.fromJson(json);
        sanitizeCourseSectionRange(parsed);
        allCourses.clear();
        allCourses.addAll(parsed);
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
        CourseStorageManager.saveCourses(this, allCourses);
    }

    private void loadCoursesFromLocal() {
        List<Course> localCourses = CourseStorageManager.loadCourses(this);
        sanitizeCourseSectionRange(localCourses);
        allCourses.clear();
        allCourses.addAll(localCourses);
        calculateCurrentWeek();
        updateScheduleViewState();
        drawGrid();
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
        showCourseDetailSheet(c, colorKey, null);
    }

    private void showCourseDetailSheet(Course c, String colorKey, List<Course> mergedCourses) {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        final int sheetSurfaceColor = UiStyleHelper.resolvePageBackgroundColor(this);
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics());
        layout.setPadding(pad, pad, pad, pad);
        scrollView.addView(layout, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText(c.name + (c.isExperimental ? " [实验]" : ""));
        title.setTextSize(20f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(UiStyleHelper.resolveOnSurfaceColor(this));
        layout.addView(title);

        int colorOnSurface = UiStyleHelper.resolveOnSurfaceColor(this);
        int colorOnSurfaceVariant = UiStyleHelper.resolveOnSurfaceVariantColor(this);

        MaterialCardView infoCard = createAgendaEditorSectionCard();
        LinearLayout rowsContainer = new LinearLayout(this);
        rowsContainer.setOrientation(LinearLayout.VERTICAL);
        infoCard.addView(rowsContainer);
        layout.addView(infoCard);

        final TextView[] teacherRef = new TextView[1];
        final TextView[] locationRef = new TextView[1];
        final TextView[] locationDistanceRef = new TextView[1];
        final TextView[] weeksRef = new TextView[1];
        final TextView[] sectionRef = new TextView[1];

        final List<Course> sectionTargets = collectCourseSectionTargets(c, mergedCourses);

        teacherRef[0] = addEditableInfoRow(rowsContainer, R.drawable.ic_profile, "教师", c.teacher == null || c.teacher.trim().isEmpty() ? "未定" : c.teacher.trim(),
            v -> showTeacherPicker(c, () -> onCourseInfoUpdated(c, teacherRef[0], locationRef[0], locationDistanceRef[0], weeksRef[0])), colorOnSurface, colorOnSurface);
        rowsContainer.addView(createAgendaEditorDivider());

        CourseLocationRowViews locationViews = addEditableLocationInfoRow(rowsContainer, R.drawable.ic_agenda_location_24, "地点", c.location,
            v -> showLocationPicker(c, () -> onCourseInfoUpdated(c, teacherRef[0], locationRef[0], locationDistanceRef[0], weeksRef[0])), colorOnSurface, colorOnSurface);
        locationRef[0] = locationViews.locationView;
        locationDistanceRef[0] = locationViews.distanceView;
        rowsContainer.addView(createAgendaEditorDivider());

        weeksRef[0] = addEditableInfoRow(rowsContainer, R.drawable.ic_today, "周次", formatWeeksForDisplay(c.weeks),
            v -> showWeeksPicker(c, () -> onCourseInfoUpdated(c, teacherRef[0], locationRef[0], locationDistanceRef[0], weeksRef[0])), colorOnSurface, colorOnSurface);
        rowsContainer.addView(createAgendaEditorDivider());

        sectionRef[0] = addEditableInfoRow(rowsContainer, R.drawable.ic_agenda_time_24, "节次", formatCourseSectionForDisplay(sectionTargets),
            v -> showCourseSectionPicker(sectionTargets, () -> {
                if (sectionRef[0] != null) {
                    sectionRef[0].setText(formatCourseSectionForDisplay(sectionTargets));
                }
                saveCoursesToLocal();
                updateNextCourseNotice();
                drawGrid();
            }), colorOnSurface, colorOnSurface);

        MaterialCardView colorCard = createAgendaEditorSectionCard();
        LinearLayout colorBody = new LinearLayout(this);
        colorBody.setOrientation(LinearLayout.VERTICAL);
        colorBody.setPadding(dp(14), dp(12), dp(14), dp(12));
        colorCard.addView(colorBody);

        TextView colorTitle = new TextView(this);
        colorTitle.setText("课程颜色");
        colorTitle.setTextSize(17f);
        colorTitle.setTypeface(null, Typeface.BOLD);
        colorTitle.setTextColor(colorOnSurface);
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
        applyAgendaEditorBottomSheetStyle(dialog, sheetSurfaceColor);
        dialog.setOnDismissListener(d -> {
            selectedCourseColorKey = null;
            drawGrid();
        });
        dialog.show();
    }

    private void showScheduleDayArrangementSheet(Calendar date, int week) {
        Calendar target = cloneAsDay(date);
        int day = toMondayFirstDay(target);
        List<TodayCourseItem> courses = buildDateCourseItems(week, day);
        List<Agenda> agendas = AgendaStorageManager.queryAgendasByDate(this, target);

        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(18), dp(18), dp(18));
        scrollView.addView(layout, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("当日安排 " + formatDateWithWeek(target));
        title.setTextSize(18f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(UiStyleHelper.resolveOnSurfaceColor(this));
        layout.addView(title);

        TextView courseSection = new TextView(this);
        courseSection.setText("课程");
        courseSection.setPadding(0, dp(14), 0, dp(8));
        courseSection.setTextSize(14f);
        courseSection.setTypeface(null, Typeface.BOLD);
        courseSection.setTextColor(UiStyleHelper.resolveOnSurfaceVariantColor(this));
        layout.addView(courseSection);

        if (courses.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("当日无课程");
            empty.setTextColor(UiStyleHelper.resolveOnSurfaceVariantColor(this));
            empty.setPadding(0, 0, 0, dp(8));
            layout.addView(empty);
        } else {
            for (TodayCourseItem item : courses) {
                MaterialCardView card = createCourseTimelineCard(item);
                card.setOnClickListener(v -> {
                    dialog.dismiss();
                    String colorKey = buildCourseColorKey(item.course.name, item.course.isExperimental);
                    selectedCourseColorKey = colorKey;
                    drawGrid();
                    showCourseDetailSheet(item.course, colorKey, item.mergedCourses);
                });
                layout.addView(card);
            }
        }

        TextView agendaSection = new TextView(this);
        agendaSection.setText("日程");
        agendaSection.setPadding(0, dp(10), 0, dp(8));
        agendaSection.setTextSize(14f);
        agendaSection.setTypeface(null, Typeface.BOLD);
        agendaSection.setTextColor(UiStyleHelper.resolveOnSurfaceVariantColor(this));
        layout.addView(agendaSection);

        if (agendas.isEmpty()) {
            TextView emptyAgenda = new TextView(this);
            emptyAgenda.setText("当日无日程");
            emptyAgenda.setTextColor(UiStyleHelper.resolveOnSurfaceVariantColor(this));
            emptyAgenda.setPadding(0, 0, 0, dp(8));
            layout.addView(emptyAgenda);
        } else {
            for (Agenda agenda : agendas) {
                MaterialCardView card = createAgendaTimelineCard(agenda);
                card.setOnClickListener(v -> {
                    dialog.dismiss();
                    showAgendaEditorSheet(agenda, target);
                });
                layout.addView(card);
            }
        }

        MaterialButton addButton = new MaterialButton(this);
        addButton.setText("新增日程");
        addButton.setAllCaps(false);
        addButton.setCornerRadius(dp(12));
        addButton.setBackgroundTintList(ColorStateList.valueOf(getTimetableThemeColor()));
        addButton.setTextColor(pickReadableTextColor(getTimetableThemeColor()));
        addButton.setOnClickListener(v -> {
            dialog.dismiss();
            showAgendaEditorSheet(null, target);
        });
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        addLp.setMargins(0, dp(8), 0, 0);
        addButton.setLayoutParams(addLp);
        layout.addView(addButton);

        dialog.setContentView(scrollView);
        dialog.show();
    }

    private void showAgendaEditorSheet(Agenda source, Calendar preferredDate) {
        Calendar initialDate;
        if (source != null) {
            initialDate = AgendaStorageManager.parseDateOrNull(source.date);
        } else {
            initialDate = null;
        }
        if (initialDate == null) {
            initialDate = preferredDate == null ? cloneAsDay(Calendar.getInstance()) : cloneAsDay(preferredDate);
        }

        final Calendar[] selectedDate = {cloneAsDay(initialDate)};
        final int[] startMinute = {source == null ? 8 * 60 : Math.max(0, source.startMinute)};
        final int[] endMinute = {source == null ? 9 * 60 : Math.min(24 * 60, source.endMinute)};
        if (endMinute[0] <= startMinute[0]) {
            endMinute[0] = Math.min(24 * 60, startMinute[0] + 30);
        }

        final int[] priority = {source == null ? Agenda.PRIORITY_LOW : normalizeAgendaPriority(source.priority)};
        final String[] repeatRule = {source == null ? Agenda.REPEAT_NONE : normalizeAgendaRepeat(source.repeatRule)};
        final String[] monthlyStrategy = {source == null ? Agenda.MONTHLY_SKIP : normalizeAgendaMonthlyStrategy(source.monthlyStrategy)};
        final String[] locationValue = {source == null ? "" : normalizeAgendaLocationInput(source.location)};
        final int[] agendaRenderColor = {normalizeAgendaStoredRenderColor(source == null ? 0 : source.renderColor)};
        final int sheetSurfaceColor = UiStyleHelper.resolvePageBackgroundColor(this);

        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(14), dp(16), dp(20));
        layout.setBackgroundColor(Color.TRANSPARENT);
        scrollView.addView(layout, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int onSurface = UiStyleHelper.resolveOnSurfaceColor(this);

        TextView sheetTitle = new TextView(this);
        sheetTitle.setText(source == null ? "新增日程" : "编辑日程");
        sheetTitle.setTextSize(20f);
        sheetTitle.setTypeface(null, Typeface.BOLD);
        sheetTitle.setTextColor(onSurface);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.setMargins(dp(2), 0, dp(2), 0);
        sheetTitle.setLayoutParams(titleLp);
        layout.addView(sheetTitle);

        MaterialCardView infoCard = createAgendaEditorSectionCard();
        LinearLayout infoBody = new LinearLayout(this);
        infoBody.setOrientation(LinearLayout.VERTICAL);
        infoCard.addView(infoBody);
        layout.addView(infoCard);

        EditText inputTitle = new EditText(this);
        inputTitle.setHint("待办标题");
        inputTitle.setText(source == null ? "" : safeText(source.title));
        styleAgendaEditorInlineInput(inputTitle, false);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setPadding(dp(14), dp(2), dp(12), dp(2));
        titleRow.addView(createAgendaRowIcon(R.drawable.ic_history_edit));
        LinearLayout.LayoutParams titleInputLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleInputLp.setMargins(dp(10), 0, 0, 0);
        inputTitle.setLayoutParams(titleInputLp);
        titleRow.addView(inputTitle);
        infoBody.addView(titleRow);
        infoBody.addView(createAgendaEditorDivider());

        EditText inputDesc = new EditText(this);
        inputDesc.setHint("详细描述（可选）");
        inputDesc.setText(source == null ? "" : safeText(source.description));
        styleAgendaEditorInlineInput(inputDesc, true);

        LinearLayout descRow = new LinearLayout(this);
        descRow.setOrientation(LinearLayout.HORIZONTAL);
        descRow.setGravity(Gravity.TOP);
        descRow.setPadding(dp(14), dp(2), dp(12), dp(2));
        ImageView descIcon = createAgendaRowIcon(R.drawable.ic_agenda_notes_24);
        LinearLayout.LayoutParams descIconLp = new LinearLayout.LayoutParams(dp(20), dp(20));
        descIconLp.setMargins(0, dp(10), 0, 0);
        descRow.addView(descIcon, descIconLp);
        LinearLayout.LayoutParams descInputLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        descInputLp.setMargins(dp(10), 0, 0, 0);
        inputDesc.setLayoutParams(descInputLp);
        descRow.addView(inputDesc);
        infoBody.addView(descRow);

        MaterialCardView settingsCard = createAgendaEditorSectionCard();
        LinearLayout settingsBody = new LinearLayout(this);
        settingsBody.setOrientation(LinearLayout.VERTICAL);
        settingsCard.addView(settingsBody);
        layout.addView(settingsCard);

        TextView locationValueView = createAgendaCapsuleSettingValueView();
        LinearLayout rowLocation = createAgendaEditorSettingRow(R.drawable.ic_agenda_location_24, "地点", locationValueView);
        settingsBody.addView(rowLocation);
        settingsBody.addView(createAgendaEditorDivider());

        TextView dateValueView = createAgendaCapsuleSettingValueView();
        LinearLayout rowDate = createAgendaEditorSettingRow(R.drawable.ic_today, "日期", dateValueView);
        settingsBody.addView(rowDate);
        settingsBody.addView(createAgendaEditorDivider());

        TextView startTimeValueView = createAgendaTimeValueView();
        TextView endTimeValueView = createAgendaTimeValueView();
        LinearLayout rowTimeRange = createAgendaEditorTimeRangeRow(R.drawable.ic_agenda_time_24, "时间段", startTimeValueView, endTimeValueView);
        settingsBody.addView(rowTimeRange);
        settingsBody.addView(createAgendaEditorDivider());

        MaterialAutoCompleteTextView repeatDropdownView = createAgendaDropdownView(AGENDA_REPEAT_LABELS);
        LinearLayout rowRepeat = createAgendaEditorDropdownRow(R.drawable.ic_agenda_repeat_24, "重复", repeatDropdownView);
        settingsBody.addView(rowRepeat);
        settingsBody.addView(createAgendaEditorDivider());

        MaterialAutoCompleteTextView priorityDropdownView = createAgendaDropdownView(AGENDA_PRIORITY_LABELS);
        LinearLayout rowPriority = createAgendaEditorDropdownRow(R.drawable.ic_agenda_priority_24, "优先级", priorityDropdownView);
        settingsBody.addView(rowPriority);
        settingsBody.addView(createAgendaEditorDivider());

        LinearLayout monthlyContainer = new LinearLayout(this);
        monthlyContainer.setOrientation(LinearLayout.VERTICAL);
        monthlyContainer.addView(createAgendaEditorDivider());
        TextView monthlyValueView = createAgendaSettingValueView();
        LinearLayout rowMonthlyStrategy = createAgendaEditorSettingRow(R.drawable.ic_schedule, "短月策略", monthlyValueView);
        monthlyContainer.addView(rowMonthlyStrategy);
        settingsBody.addView(monthlyContainer);

        refreshAgendaEditorButtons(dateValueView, startTimeValueView, endTimeValueView, priorityDropdownView, repeatDropdownView, monthlyValueView, locationValueView,
            monthlyContainer,
            selectedDate[0], startMinute[0], endMinute[0], priority[0], repeatRule[0], monthlyStrategy[0], locationValue[0]);

        MaterialCardView colorCard = createAgendaEditorSectionCard();
        LinearLayout colorBody = new LinearLayout(this);
        colorBody.setOrientation(LinearLayout.VERTICAL);
        colorBody.setPadding(dp(14), dp(12), dp(14), dp(12));
        colorCard.addView(colorBody);

        TextView colorTitle = new TextView(this);
        colorTitle.setText("日程颜色");
        colorTitle.setTextSize(17f);
        colorTitle.setTypeface(null, Typeface.BOLD);
        colorTitle.setTextColor(UiStyleHelper.resolveOnSurfaceColor(this));
        colorBody.addView(colorTitle);

        HorizontalScrollView colorScroll = new HorizontalScrollView(this);
        colorScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams colorScrollLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        colorScrollLp.setMargins(0, dp(10), 0, 0);
        colorScroll.setLayoutParams(colorScrollLp);

        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        colorScroll.addView(colorRow, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        colorBody.addView(colorScroll);
        layout.addView(colorCard);
        renderAgendaColorSlider(colorRow, agendaRenderColor);

        rowDate.setOnClickListener(v -> showAgendaDatePicker(selectedDate[0], pickedDate -> {
            selectedDate[0] = cloneAsDay(pickedDate);
            refreshAgendaEditorButtons(dateValueView, startTimeValueView, endTimeValueView, priorityDropdownView, repeatDropdownView, monthlyValueView, locationValueView,
                    monthlyContainer,
                    selectedDate[0], startMinute[0], endMinute[0], priority[0], repeatRule[0], monthlyStrategy[0], locationValue[0]);
        }));

        startTimeValueView.setOnClickListener(v -> showMinutePicker(startMinute[0], minute -> {
            startMinute[0] = minute;
            if (endMinute[0] <= startMinute[0]) {
                endMinute[0] = Math.min(24 * 60, startMinute[0] + 30);
            }
            refreshAgendaEditorButtons(dateValueView, startTimeValueView, endTimeValueView, priorityDropdownView, repeatDropdownView, monthlyValueView, locationValueView,
                monthlyContainer,
                selectedDate[0], startMinute[0], endMinute[0], priority[0], repeatRule[0], monthlyStrategy[0], locationValue[0]);
        }));

        endTimeValueView.setOnClickListener(v -> showMinutePicker(endMinute[0], minute -> {
            endMinute[0] = minute;
            if (endMinute[0] <= startMinute[0]) {
                endMinute[0] = Math.min(24 * 60, startMinute[0] + 30);
                Toast.makeText(this, "结束时间已自动调整为开始后30分钟", Toast.LENGTH_SHORT).show();
            }
            refreshAgendaEditorButtons(dateValueView, startTimeValueView, endTimeValueView, priorityDropdownView, repeatDropdownView, monthlyValueView, locationValueView,
                    monthlyContainer,
                    selectedDate[0], startMinute[0], endMinute[0], priority[0], repeatRule[0], monthlyStrategy[0], locationValue[0]);
        }));

        priorityDropdownView.setOnItemClickListener((parent, view, position, id) -> {
            int index = clampIndex(position, AGENDA_PRIORITY_VALUES.length);
            priority[0] = AGENDA_PRIORITY_VALUES[index];
                refreshAgendaEditorButtons(dateValueView, startTimeValueView, endTimeValueView, priorityDropdownView, repeatDropdownView, monthlyValueView, locationValueView,
                    monthlyContainer,
                    selectedDate[0], startMinute[0], endMinute[0], priority[0], repeatRule[0], monthlyStrategy[0], locationValue[0]);
        });

        repeatDropdownView.setOnItemClickListener((parent, view, position, id) -> {
            int index = clampIndex(position, AGENDA_REPEAT_VALUES.length);
            repeatRule[0] = AGENDA_REPEAT_VALUES[index];
                refreshAgendaEditorButtons(dateValueView, startTimeValueView, endTimeValueView, priorityDropdownView, repeatDropdownView, monthlyValueView, locationValueView,
                    monthlyContainer,
                    selectedDate[0], startMinute[0], endMinute[0], priority[0], repeatRule[0], monthlyStrategy[0], locationValue[0]);
        });

        rowMonthlyStrategy.setOnClickListener(v -> {
            try {
                final int[] picked = {clampIndex(indexOfString(AGENDA_MONTHLY_VALUES, monthlyStrategy[0]), AGENDA_MONTHLY_VALUES.length)};
                newMaterialYouDialogBuilder()
                        .setTitle("短月策略")
                        .setSingleChoiceItems(AGENDA_MONTHLY_LABELS, picked[0], (d, which) -> picked[0] = which)
                        .setNegativeButton("取消", null)
                        .setPositiveButton("确定", (d, which) -> {
                            int index = clampIndex(picked[0], AGENDA_MONTHLY_VALUES.length);
                            monthlyStrategy[0] = AGENDA_MONTHLY_VALUES[index];
                                refreshAgendaEditorButtons(dateValueView, startTimeValueView, endTimeValueView, priorityDropdownView, repeatDropdownView, monthlyValueView, locationValueView,
                                    monthlyContainer,
                                    selectedDate[0], startMinute[0], endMinute[0], priority[0], repeatRule[0], monthlyStrategy[0], locationValue[0]);
                        })
                        .show();
            } catch (Exception e) {
                Toast.makeText(this, "打开短月策略失败", Toast.LENGTH_SHORT).show();
            }
        });

        rowLocation.setOnClickListener(v -> showAgendaLocationPicker(locationValue[0], picked -> {
            locationValue[0] = normalizeAgendaLocationInput(picked);
                refreshAgendaEditorButtons(dateValueView, startTimeValueView, endTimeValueView, priorityDropdownView, repeatDropdownView, monthlyValueView, locationValueView,
                    monthlyContainer,
                    selectedDate[0], startMinute[0], endMinute[0], priority[0], repeatRule[0], monthlyStrategy[0], locationValue[0]);
        }));

        Runnable saveAction = () -> {
            String title = safeText(inputTitle.getText() == null ? "" : inputTitle.getText().toString()).trim();
            if (title.isEmpty()) {
                Toast.makeText(this, "请输入日程标题", Toast.LENGTH_SHORT).show();
                return;
            }
            if (endMinute[0] <= startMinute[0]) {
                Toast.makeText(this, "结束时间必须晚于开始时间", Toast.LENGTH_SHORT).show();
                return;
            }

            Agenda agenda = source == null ? new Agenda() : source.copy();
            agenda.title = title;
            agenda.description = safeText(inputDesc.getText() == null ? "" : inputDesc.getText().toString()).trim();
            agenda.date = AgendaStorageManager.formatDate(selectedDate[0]);
            agenda.startMinute = startMinute[0];
            agenda.endMinute = endMinute[0];
            agenda.location = normalizeAgendaLocationInput(locationValue[0]);
            agenda.priority = priority[0];
            agenda.renderColor = normalizeAgendaStoredRenderColor(agendaRenderColor[0]);
            agenda.repeatRule = repeatRule[0];
            agenda.monthlyStrategy = Agenda.REPEAT_MONTHLY.equals(repeatRule[0]) ? monthlyStrategy[0] : Agenda.MONTHLY_SKIP;

            boolean success;
            if (source == null) {
                long id = AgendaStorageManager.createAgenda(this, agenda);
                success = id > 0;
            } else {
                success = AgendaStorageManager.updateAgenda(this, agenda);
            }

            if (!success) {
                Toast.makeText(this, source == null ? "新增失败" : "保存失败", Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(this, source == null ? "已新增日程" : "已保存日程", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            setSelectedTodayDate(selectedDate[0]);
            refreshTodayPage();
            drawGrid();
        };

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionRowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionRowLp.setMargins(0, dp(12), 0, 0);
        actionRow.setLayoutParams(actionRowLp);

        if (source != null) {
            MaterialButton deleteButton = createAgendaActionButton(false);
            deleteButton.setText("删除日程");
            LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            deleteLp.setMargins(0, 0, dp(8), 0);
            deleteButton.setLayoutParams(deleteLp);
            deleteButton.setOnClickListener(v -> newMaterialYouDialogBuilder()
                    .setTitle("删除日程")
                    .setMessage("确定删除该日程吗？")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("删除", (d, which) -> {
                        if (AgendaStorageManager.deleteAgenda(this, source.id)) {
                            Toast.makeText(this, "已删除日程", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                            setSelectedTodayDate(selectedDate[0]);
                            refreshTodayPage();
                            drawGrid();
                        } else {
                            Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .show());
            actionRow.addView(deleteButton);
        }

        MaterialButton saveButton = createAgendaActionButton(true);
        saveButton.setText("保存");
        LinearLayout.LayoutParams saveLp = source == null
                ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        saveButton.setLayoutParams(saveLp);
        saveButton.setOnClickListener(v -> saveAction.run());
        actionRow.addView(saveButton);

        layout.addView(actionRow);

        dialog.setContentView(scrollView);
        applyAgendaEditorBottomSheetStyle(dialog, sheetSurfaceColor);
        dialog.show();
    }

    private void refreshAgendaEditorButtons(TextView dateValueView, TextView startTimeValueView, TextView endTimeValueView,
                                            MaterialAutoCompleteTextView priorityDropdownView, MaterialAutoCompleteTextView repeatDropdownView,
                                            TextView monthlyValueView, TextView locationValueView,
                                            View monthlyContainer,
                                            Calendar date, int startMinute, int endMinute,
                                            int priority, String repeatRule, String monthlyStrategy,
                                            String locationText) {
        dateValueView.setText(formatDateWithWeek(date));
        startTimeValueView.setText(formatMinute(startMinute));
        endTimeValueView.setText(formatMinute(endMinute));
        setAgendaDropdownText(priorityDropdownView, priorityText(priority));
        setAgendaDropdownText(repeatDropdownView, agendaRepeatLabel(repeatRule));
        monthlyValueView.setText(agendaMonthlyLabel(monthlyStrategy));
        locationValueView.setText(formatAgendaLocationButtonText(locationText));
        monthlyContainer.setVisibility(Agenda.REPEAT_MONTHLY.equals(repeatRule) ? View.VISIBLE : View.GONE);
    }

    private void setAgendaDropdownText(MaterialAutoCompleteTextView dropdown, String text) {
        if (dropdown == null) {
            return;
        }
        if (!TextUtils.equals(dropdown.getText(), text)) {
            dropdown.setText(text, false);
        }
        dropdown.post(() -> adjustAgendaDropdownCapsuleWidth(dropdown));
    }

    private void adjustAgendaDropdownCapsuleWidth(MaterialAutoCompleteTextView dropdown) {
        if (dropdown == null) {
            return;
        }
        CharSequence text = dropdown.getText();
        String displayText = safeText(text == null ? "" : text.toString()).trim();
        if (displayText.isEmpty()) {
            Object entriesTag = dropdown.getTag();
            if (entriesTag instanceof String[]) {
                String[] entries = (String[]) entriesTag;
                if (entries.length > 0) {
                    displayText = safeText(entries[0]);
                }
            }
        }
        if (displayText.isEmpty()) {
            return;
        }

        int horizontalPadding = dropdown.getPaddingLeft() + dropdown.getPaddingRight();
        int targetWidth = (int) Math.ceil(dropdown.getPaint().measureText(displayText)) + horizontalPadding + dp(8);
        targetWidth = Math.max(dp(86), Math.min(dp(220), targetWidth));

        ViewGroup.LayoutParams params = dropdown.getLayoutParams();
        if (params != null && params.width != targetWidth) {
            params.width = targetWidth;
            dropdown.setLayoutParams(params);
        }
    }

    private void applyAgendaEditorBottomSheetStyle(com.google.android.material.bottomsheet.BottomSheetDialog dialog, int surfaceColor) {
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

    private MaterialCardView createAgendaEditorSectionCard() {
        MaterialCardView card = new MaterialCardView(this);
        int onSurface = UiStyleHelper.resolveOnSurfaceColor(this);
        card.setRadius(dp(24));
        card.setCardElevation(0f);
        card.setStrokeWidth(1);
        card.setStrokeColor(ColorUtils.setAlphaComponent(onSurface, 24));
        card.setCardBackgroundColor(UiStyleHelper.resolveGlassCardColor(this));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, dp(12), 0, 0);
        card.setLayoutParams(cardLp);
        return card;
    }

    private ImageView createAgendaRowIcon(int iconRes) {
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setImageTintList(ColorStateList.valueOf(UiStyleHelper.resolveOnSurfaceVariantColor(this)));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(20), dp(20));
        icon.setLayoutParams(iconLp);
        return icon;
    }

    private void styleAgendaEditorInlineInput(EditText input, boolean multiLine) {
        if (input == null) {
            return;
        }
        int onSurface = UiStyleHelper.resolveOnSurfaceColor(this);
        int onSurfaceVariant = UiStyleHelper.resolveOnSurfaceVariantColor(this);
        input.setTextColor(onSurface);
        input.setHintTextColor(ColorUtils.setAlphaComponent(onSurfaceVariant, 180));
        input.setTextSize(17f);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setPadding(dp(4), dp(11), dp(4), dp(11));
        input.setMinHeight(dp(56));
        if (multiLine) {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            input.setSingleLine(false);
            input.setMinLines(6);
            input.setMaxLines(Integer.MAX_VALUE);
            input.setGravity(Gravity.TOP | Gravity.START);
            input.setHorizontallyScrolling(false);
            // 由外层 ScrollView 提供滚动条和滚动能力，规避部分 ROM 上 EditText 滚动条崩溃。
            input.setVerticalScrollBarEnabled(false);
            input.setOverScrollMode(View.OVER_SCROLL_NEVER);
        } else {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            input.setSingleLine(true);
            input.setMaxLines(1);
            input.setEllipsize(TextUtils.TruncateAt.END);
            input.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        }
    }

    private View createAgendaEditorDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        dividerLp.setMargins(0, 0, 0, 0);
        divider.setLayoutParams(dividerLp);
        return divider;
    }

    private TextView createAgendaSettingValueView() {
        TextView value = new TextView(this);
        value.setTextColor(UiStyleHelper.resolveOnSurfaceVariantColor(this));
        value.setTextSize(16f);
        value.setSingleLine(true);
        value.setEllipsize(TextUtils.TruncateAt.END);
        value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        value.setMaxWidth(dp(220));
        return value;
    }

    private TextView createAgendaCapsuleSettingValueView() {
        int onSurface = UiStyleHelper.resolveOnSurfaceColor(this);
        TextView value = new TextView(this);
        value.setTextColor(onSurface);
        value.setTextSize(15f);
        value.setTypeface(null, Typeface.BOLD);
        value.setSingleLine(true);
        value.setEllipsize(TextUtils.TruncateAt.END);
        value.setGravity(Gravity.CENTER);
        value.setMaxWidth(dp(220));
        value.setPadding(dp(14), dp(8), dp(14), dp(8));
        value.setBackground(makeRoundedSolid(ColorUtils.setAlphaComponent(onSurface, 28), dp(14)));
        return value;
    }

    private TextView createAgendaTimeValueView() {
        int onSurface = UiStyleHelper.resolveOnSurfaceColor(this);
        TextView value = new TextView(this);
        value.setTextColor(onSurface);
        value.setTextSize(15f);
        value.setTypeface(null, Typeface.BOLD);
        value.setGravity(Gravity.CENTER);
        value.setMinWidth(dp(84));
        value.setPadding(dp(12), dp(8), dp(12), dp(8));
        value.setBackground(makeRoundedSolid(ColorUtils.setAlphaComponent(onSurface, 28), dp(12)));
        value.setClickable(true);
        value.setFocusable(false);
        return value;
    }

    private LinearLayout createAgendaEditorTimeRangeRow(int iconRes, String label, TextView startValueView, TextView endValueView) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(56));
        row.setPadding(dp(14), dp(7), dp(12), dp(7));

        ImageView icon = createAgendaRowIcon(iconRes);
        row.addView(icon);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(17f);
        labelView.setTextColor(UiStyleHelper.resolveOnSurfaceColor(this));
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.setMargins(dp(10), 0, dp(10), 0);
        labelView.setLayoutParams(labelLp);
        row.addView(labelView);

        LinearLayout values = new LinearLayout(this);
        values.setOrientation(LinearLayout.HORIZONTAL);
        values.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams valuesLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        values.setLayoutParams(valuesLp);

        TextView middle = new TextView(this);
        middle.setText("至");
        middle.setTextSize(14f);
        middle.setTextColor(UiStyleHelper.resolveOnSurfaceVariantColor(this));
        LinearLayout.LayoutParams middleLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        middleLp.setMargins(dp(8), 0, dp(8), 0);

        values.addView(startValueView);
        values.addView(middle, middleLp);
        values.addView(endValueView);
        row.addView(values);
        return row;
    }

    private LinearLayout createAgendaEditorDropdownRow(int iconRes, String label, MaterialAutoCompleteTextView dropdownView) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(56));
        row.setPadding(dp(14), dp(8), dp(12), dp(8));

        ImageView icon = createAgendaRowIcon(iconRes);
        row.addView(icon);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(17f);
        labelView.setTextColor(UiStyleHelper.resolveOnSurfaceColor(this));
        labelView.setSingleLine(true);
        labelView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.setMargins(dp(10), 0, dp(10), 0);
        labelView.setLayoutParams(labelLp);
        row.addView(labelView);

        LinearLayout valueContainer = new LinearLayout(this);
        valueContainer.setOrientation(LinearLayout.HORIZONTAL);
        valueContainer.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams valueContainerLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        valueContainer.setLayoutParams(valueContainerLp);

        LinearLayout.LayoutParams dropdownLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valueContainer.addView(dropdownView, dropdownLp);
        row.addView(valueContainer);

        row.setOnClickListener(v -> dropdownView.post(dropdownView::showDropDown));
        return row;
    }

    private MaterialAutoCompleteTextView createAgendaDropdownView(String[] entries) {
        MaterialAutoCompleteTextView dropdown = new MaterialAutoCompleteTextView(this);
        dropdown.setTag(entries);
        int onSurface = UiStyleHelper.resolveOnSurfaceColor(this);
        int onSurfaceVariant = UiStyleHelper.resolveOnSurfaceVariantColor(this);
        final int popupColor = ColorUtils.setAlphaComponent(
                ColorUtils.blendARGB(UiStyleHelper.resolvePageBackgroundColor(this), UiStyleHelper.resolveGlassCardColor(this), 0.56f),
                255);
        int controlColor = ColorUtils.setAlphaComponent(onSurface, 28);

        GradientDrawable popupBg = new GradientDrawable();
        popupBg.setShape(GradientDrawable.RECTANGLE);
        popupBg.setCornerRadius(dp(14));
        popupBg.setColor(popupColor);
        popupBg.setStroke(dp(1), ColorUtils.setAlphaComponent(onSurfaceVariant, 56));

        dropdown.setTextColor(onSurface);
        dropdown.setTextSize(16f);
        dropdown.setTypeface(null, Typeface.BOLD);
        dropdown.setSingleLine(true);
        dropdown.setEllipsize(TextUtils.TruncateAt.END);
        dropdown.setInputType(InputType.TYPE_NULL);
        dropdown.setKeyListener(null);
        dropdown.setGravity(Gravity.CENTER);
        dropdown.setMinWidth(0);
        dropdown.setMaxWidth(dp(220));
        dropdown.setPadding(dp(16), dp(10), dp(16), dp(10));
        dropdown.setThreshold(0);
        dropdown.setBackground(makeRoundedSolid(controlColor, dp(16)));
        dropdown.setDropDownBackgroundDrawable(popupBg);
        dropdown.setDropDownVerticalOffset(dp(6));
        dropdown.setCursorVisible(false);
        dropdown.setFocusable(false);
        dropdown.setFocusableInTouchMode(false);
        dropdown.setSimpleItems(entries);
        dropdown.setOnClickListener(v -> dropdown.post(dropdown::showDropDown));
        return dropdown;
    }

    private LinearLayout createAgendaEditorSettingRow(int iconRes, String label, TextView valueView) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(56));
        row.setPadding(dp(14), dp(7), dp(12), dp(7));

        ImageView icon = createAgendaRowIcon(iconRes);
        row.addView(icon);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(17f);
        labelView.setTextColor(UiStyleHelper.resolveOnSurfaceColor(this));
        labelView.setSingleLine(true);
        labelView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.setMargins(dp(10), 0, dp(10), 0);
        labelView.setLayoutParams(labelLp);
        row.addView(labelView);

        LinearLayout valueContainer = new LinearLayout(this);
        valueContainer.setOrientation(LinearLayout.HORIZONTAL);
        valueContainer.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams valueContainerLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        valueContainer.setLayoutParams(valueContainerLp);
        valueContainer.addView(valueView);
        row.addView(valueContainer);

        return row;
    }

    private void styleAgendaEditorInput(EditText input) {
        if (input == null) {
            return;
        }
        int onSurface = UiStyleHelper.resolveOnSurfaceColor(this);
        int onSurfaceVariant = UiStyleHelper.resolveOnSurfaceVariantColor(this);
        input.setTextColor(onSurface);
        input.setHintTextColor(ColorUtils.setAlphaComponent(onSurfaceVariant, 180));
        input.setBackground(makeRoundedSolid(ColorUtils.setAlphaComponent(onSurface, 34), dp(14)));
        input.setPadding(dp(14), dp(10), dp(14), dp(10));
        input.setMinHeight(dp(46));
    }

    private MaterialButton createAgendaEditorButton() {
        MaterialButton button = new MaterialButton(this);
        button.setAllCaps(false);
        button.setCornerRadius(dp(14));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setMinHeight(dp(48));
        button.setStrokeWidth(0);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        int onSurface = UiStyleHelper.resolveOnSurfaceColor(this);
        button.setBackgroundTintList(ColorStateList.valueOf(ColorUtils.setAlphaComponent(onSurface, 34)));
        button.setTextColor(onSurface);
        return button;
    }

    private MaterialButton createAgendaActionButton(boolean primary) {
        MaterialButton button = createAgendaEditorButton();
        button.setCornerRadius(dp(24));
        button.setMinHeight(dp(52));
        button.setGravity(Gravity.CENTER);
        if (primary) {
            int accent = getTimetableThemeColor();
            button.setBackgroundTintList(ColorStateList.valueOf(accent));
            button.setTextColor(pickReadableTextColor(accent));
        }
        return button;
    }

    private LinearLayout buildAgendaEditorButtonRow(MaterialButton left, MaterialButton right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, dp(10), 0, 0);
        row.setLayoutParams(rowLp);

        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        leftLp.setMargins(0, 0, dp(6), 0);
        left.setLayoutParams(leftLp);

        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rightLp.setMargins(dp(6), 0, 0, 0);
        right.setLayoutParams(rightLp);

        row.addView(left);
        row.addView(right);
        return row;
    }

    private String formatAgendaLocationButtonText(String locationText) {
        String normalized = normalizeAgendaLocationInput(locationText);
        if (normalized.isEmpty()) {
            return "选择地点";
        }
        String standard = CampusBuildingStore.toStandardLocation(this, normalized);
        if (!TextUtils.isEmpty(standard) && !"未定".equals(standard)) {
            return standard;
        }
        return normalized;
    }

    private String normalizeAgendaLocationInput(String rawLocation) {
        String raw = safeText(rawLocation).trim();
        while (raw.startsWith("@") || raw.startsWith("＠")) {
            raw = raw.substring(1).trim();
        }
        if (raw.isEmpty()) {
            return "";
        }
        CampusBuildingStore.ResolvedLocation resolved = CampusBuildingStore.resolveLocation(this, raw);
        if (resolved == null) {
            return raw;
        }
        String merged = CampusBuildingStore.buildLocationText(resolved.buildingName, resolved.roomNumber);
        return merged.isEmpty() ? safeText(resolved.buildingName) : merged;
    }

    private com.google.android.material.dialog.MaterialAlertDialogBuilder newMaterialYouDialogBuilder() {
        // Use the activity's AppCompat/Material3 context directly to satisfy ThemeEnforcement.
        return new com.google.android.material.dialog.MaterialAlertDialogBuilder(new androidx.appcompat.view.ContextThemeWrapper(this, com.google.android.material.R.style.Theme_Material3_DayNight_Dialog_Alert));
    }


    private void showAgendaLocationPicker(String currentLocation, OnAgendaLocationPick callback) {
        List<String> options = new ArrayList<>();
        options.add("不设置");
        try {
            List<String> buildingNames = CampusBuildingStore.getBuildingNames(this);
            if (buildingNames != null && !buildingNames.isEmpty()) {
                options.addAll(buildingNames);
            }
        } catch (Exception ignored) {
        }

        CampusBuildingStore.ResolvedLocation resolved = null;
        try {
            resolved = CampusBuildingStore.resolveLocation(this, currentLocation);
        } catch (Exception ignored) {
        }

        try {
            String currentBuilding = resolved == null ? "" : safeText(resolved.buildingName);
            String currentRoom = resolved == null ? "" : safeText(resolved.roomNumber);
            int checked = currentBuilding.isEmpty() ? 0 : options.indexOf(currentBuilding);
            checked = clampIndex(checked, options.size());

            final int[] picked = {checked};
            newMaterialYouDialogBuilder()
                    .setTitle("设置地点")
                    .setSingleChoiceItems(options.toArray(new String[0]), checked, (dialog, which) -> picked[0] = which)
                    .setNeutralButton("自定义", (dialog, which) -> showAgendaCustomLocationInputDialog(currentLocation, callback))
                    .setNegativeButton("取消", null)
                    .setPositiveButton("确定", (dialog, which) -> {
                        int index = clampIndex(picked[0], options.size());
                        String selected = options.get(index);
                        if ("不设置".equals(selected)) {
                            if (callback != null) {
                                callback.onPick("");
                            }
                            return;
                        }
                        String roomSeed = selected.equals(currentBuilding) ? currentRoom : "";
                        showRoomNumberInputDialog(selected, roomSeed, room -> {
                            if (callback != null) {
                                callback.onPick(CampusBuildingStore.buildLocationText(selected, room));
                            }
                        });
                    })
                    .show();
        } catch (Exception e) {
            Toast.makeText(this, "地点选择器打开失败，已切换为手动输入", Toast.LENGTH_SHORT).show();
            showAgendaCustomLocationInputDialog(currentLocation, callback);
        }
    }

    private int clampIndex(int index, int size) {
        if (size <= 0) {
            return 0;
        }
        if (index < 0) {
            return 0;
        }
        if (index >= size) {
            return size - 1;
        }
        return index;
    }

    private void showAgendaCustomLocationInputDialog(String currentLocation, OnAgendaLocationPick callback) {
        EditText input = new EditText(this);
        input.setHint("输入自定义地点（可留空）");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setText(safeText(currentLocation));
        styleAgendaEditorInput(input);

        newMaterialYouDialogBuilder()
                .setTitle("自定义地点")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> {
                    String value = input.getText() == null ? "" : input.getText().toString().trim();
                    if (callback != null) {
                        callback.onPick(value);
                    }
                })
                .show();
    }

    private void showMinutePicker(int initialMinute, OnMinutePick callback) {
        int hour = Math.max(0, Math.min(23, initialMinute / 60));
        int minute = Math.max(0, Math.min(59, initialMinute % 60));
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTitleText("选择时间")
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(hour)
                .setMinute(minute)
                .build();
        picker.addOnPositiveButtonClickListener(v -> {
            if (callback != null) {
                callback.onPick(picker.getHour() * 60 + picker.getMinute());
            }
        });
        picker.show(getSupportFragmentManager(), "agenda_time_picker_" + System.currentTimeMillis());
    }

    private void showAgendaDatePicker(Calendar initialDate, OnDatePick callback) {
        Calendar seed = initialDate == null ? cloneAsDay(Calendar.getInstance()) : cloneAsDay(initialDate);
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("选择日期")
                .setSelection(seed.getTimeInMillis())
                .build();
        picker.addOnPositiveButtonClickListener(selection -> {
            if (selection == null) {
                return;
            }
            Calendar picked = Calendar.getInstance();
            picked.setTimeInMillis(selection);
            picked = cloneAsDay(picked);
            if (callback != null) {
                callback.onPick(picked);
            }
        });
        picker.show(getSupportFragmentManager(), "agenda_date_picker_" + System.currentTimeMillis());
    }

    private int normalizeAgendaPriority(int priority) {
        if (priority < Agenda.PRIORITY_LOW || priority > Agenda.PRIORITY_HIGH) {
            return Agenda.PRIORITY_LOW;
        }
        return priority;
    }

    private String normalizeAgendaRepeat(String repeatRule) {
        String rule = safeText(repeatRule).toLowerCase(Locale.ROOT);
        if (Agenda.REPEAT_DAILY.equals(rule) || Agenda.REPEAT_WEEKLY.equals(rule) || Agenda.REPEAT_MONTHLY.equals(rule)) {
            return rule;
        }
        return Agenda.REPEAT_NONE;
    }

    private String normalizeAgendaMonthlyStrategy(String strategy) {
        String value = safeText(strategy).toLowerCase(Locale.ROOT);
        if (Agenda.MONTHLY_MONTH_END.equals(value)) {
            return Agenda.MONTHLY_MONTH_END;
        }
        return Agenda.MONTHLY_SKIP;
    }

    private String formatDateWithWeek(Calendar date) {
        Calendar target = cloneAsDay(date);
        int day = toMondayFirstDay(target);
        return String.format(Locale.getDefault(), "%04d-%02d-%02d 周%s",
                target.get(Calendar.YEAR),
                target.get(Calendar.MONTH) + 1,
                target.get(Calendar.DAY_OF_MONTH),
                WEEK_DAY_LABELS[Math.max(0, Math.min(6, day - 1))]);
    }

    private String agendaRepeatLabel(String repeatRule) {
        int index = indexOfString(AGENDA_REPEAT_VALUES, normalizeAgendaRepeat(repeatRule));
        return AGENDA_REPEAT_LABELS[index];
    }

    private String agendaMonthlyLabel(String strategy) {
        int index = indexOfString(AGENDA_MONTHLY_VALUES, normalizeAgendaMonthlyStrategy(strategy));
        return AGENDA_MONTHLY_LABELS[index];
    }

    private int indexOfInt(int[] array, int value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == value) {
                return i;
            }
        }
        return 0;
    }

    private int indexOfString(String[] array, String value) {
        String target = safeText(value);
        for (int i = 0; i < array.length; i++) {
            if (array[i].equals(target)) {
                return i;
            }
        }
        return 0;
    }

    private static final class CourseLocationRowViews {
        final TextView locationView;
        final TextView distanceView;

        CourseLocationRowViews(TextView locationView, TextView distanceView) {
            this.locationView = locationView;
            this.distanceView = distanceView;
        }
    }

    private TextView addEditableInfoRow(LinearLayout parent, String label, String value, View.OnClickListener editAction,
                                        int labelColor, int valueColor) {
        return addEditableInfoRow(parent, 0, label, value, editAction, labelColor, valueColor);
    }

    private TextView addEditableInfoRow(LinearLayout parent, int iconRes, String label, String value, View.OnClickListener editAction,
                                        int labelColor, int valueColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(56));
        row.setPadding(dp(14), dp(7), dp(12), dp(7));

        if (iconRes != 0) {
            row.addView(createAgendaRowIcon(iconRes));
        }

        TextView labelTv = new TextView(this);
        labelTv.setText(label);
        labelTv.setTextSize(17f);
        labelTv.setTextColor(labelColor);
        labelTv.setSingleLine(true);
        labelTv.setEllipsize(TextUtils.TruncateAt.END);
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
        valueTv.setEllipsize(TextUtils.TruncateAt.END);
        valueTv.setGravity(Gravity.CENTER);
        valueTv.setMaxWidth(dp(220));
        valueTv.setPadding(dp(14), dp(8), dp(14), dp(8));
        valueTv.setBackground(makeRoundedSolid(ColorUtils.setAlphaComponent(valueColor, 28), dp(14)));
        valueContainer.addView(valueTv);
        row.addView(valueContainer);

        if (editAction != null) {
            row.setOnClickListener(editAction);
            valueTv.setOnClickListener(editAction);
        }

        parent.addView(row);
        return valueTv;
    }

    private CourseLocationRowViews addEditableLocationInfoRow(LinearLayout parent, int iconRes, String label, String locationRaw,
                                                              View.OnClickListener editAction, int labelColor, int valueColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(56));
        row.setPadding(dp(14), dp(7), dp(12), dp(7));

        if (iconRes != 0) {
            row.addView(createAgendaRowIcon(iconRes));
        }

        TextView labelTv = new TextView(this);
        labelTv.setText(label);
        labelTv.setTextSize(17f);
        labelTv.setTextColor(labelColor);
        labelTv.setSingleLine(true);
        labelTv.setEllipsize(TextUtils.TruncateAt.END);
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
        distanceTv.setEllipsize(TextUtils.TruncateAt.END);
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
        valueTv.setEllipsize(TextUtils.TruncateAt.END);
        valueTv.setGravity(Gravity.CENTER);
        valueTv.setMaxWidth(dp(220));
        valueTv.setPadding(dp(14), dp(8), dp(14), dp(8));
        valueTv.setBackground(makeRoundedSolid(ColorUtils.setAlphaComponent(valueColor, 28), dp(14)));
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
        newMaterialYouDialogBuilder()
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
        newMaterialYouDialogBuilder()
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

        newMaterialYouDialogBuilder()
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

    private void showCourseSectionPicker(List<Course> targets, Runnable afterPick) {
        if (targets == null || targets.isEmpty()) {
            return;
        }

        List<Course> sectionTargets = new ArrayList<>();
        for (Course one : targets) {
            if (one != null && !sectionTargets.contains(one)) {
                sectionTargets.add(one);
            }
        }
        if (sectionTargets.isEmpty()) {
            return;
        }
        sectionTargets.sort((a, b) -> Integer.compare(a.startSection, b.startSection));
        String[] labels = new String[SLOT_LABELS.length];
        boolean[] checked = new boolean[SLOT_LABELS.length];
        for (int i = 0; i < SLOT_LABELS.length; i++) {
            labels[i] = "第" + (i + 1) + "大节";
        }
        List<Integer> currentSlots = collectDistinctCourseSlots(sectionTargets);
        for (Integer slot : currentSlots) {
            if (slot != null && slot >= 1 && slot <= SLOT_LABELS.length) {
                checked[slot - 1] = true;
            }
        }

        newMaterialYouDialogBuilder()
                .setTitle("编辑节次")
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> {
                    List<Integer> selectedSlots = new ArrayList<>();
                    for (int i = 0; i < checked.length; i++) {
                        if (checked[i]) {
                            selectedSlots.add(i + 1);
                        }
                    }
                    if (selectedSlots.isEmpty()) {
                        Toast.makeText(this, "至少选择一个节次", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Collections.sort(selectedSlots);
                    applyCourseSectionSelection(sectionTargets, selectedSlots);
                    if (afterPick != null) {
                        afterPick.run();
                    }
                })
                .show();
    }

    private void applyCourseSectionSelection(List<Course> sectionTargets, List<Integer> selectedSlots) {
        if (sectionTargets == null || sectionTargets.isEmpty() || selectedSlots == null || selectedSlots.isEmpty()) {
            return;
        }

        List<Course> orderedTargets = new ArrayList<>();
        for (Course one : sectionTargets) {
            if (one != null && !orderedTargets.contains(one)) {
                orderedTargets.add(one);
            }
        }
        if (orderedTargets.isEmpty()) {
            return;
        }
        orderedTargets.sort((a, b) -> Integer.compare(a.startSection, b.startSection));

        Course template = orderedTargets.get(0);
        int dayOfWeek = template.dayOfWeek;
        List<Course> updated = new ArrayList<>();

        for (int i = 0; i < selectedSlots.size(); i++) {
            int slot = selectedSlots.get(i);
            Course target;
            if (i < orderedTargets.size()) {
                target = orderedTargets.get(i);
            } else {
                target = cloneCourseForSplit(template);
                allCourses.add(target);
            }
            target.dayOfWeek = dayOfWeek;
            target.startSection = slot * 2 - 1;
            target.sectionSpan = 2;
            updated.add(target);
        }

        for (int i = selectedSlots.size(); i < orderedTargets.size(); i++) {
            allCourses.remove(orderedTargets.get(i));
        }

        sectionTargets.clear();
        sectionTargets.addAll(updated);
        sectionTargets.sort((a, b) -> Integer.compare(a.startSection, b.startSection));
    }

    private Course cloneCourseForSplit(Course source) {
        Course copy = new Course();
        copy.name = safeText(source.name);
        copy.teacher = safeText(source.teacher);
        copy.location = safeText(source.location);
        copy.dayOfWeek = source.dayOfWeek;
        copy.startSection = source.startSection;
        copy.sectionSpan = source.sectionSpan;
        copy.timeStr = safeText(source.timeStr);
        copy.weeks = source.weeks == null ? new ArrayList<>() : new ArrayList<>(source.weeks);
        copy.typeClass = safeText(source.typeClass);
        copy.isExperimental = source.isExperimental;
        copy.isRemark = source.isRemark;
        return copy;
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

    private void renderAgendaColorSlider(LinearLayout container, int[] agendaColorHolder) {
        if (container == null || agendaColorHolder == null || agendaColorHolder.length == 0) {
            return;
        }
        container.removeAllViews();

        int currentStoredColor = normalizeAgendaStoredRenderColor(agendaColorHolder[0]);
        addColorDot(container, Color.TRANSPARENT, currentStoredColor == 0, true, v -> {
            agendaColorHolder[0] = 0;
            renderAgendaColorSlider(container, agendaColorHolder);
        });

        int[] palette = buildAgendaColorPalette();
        for (int color : palette) {
            boolean selected = currentStoredColor == color;
            addColorDot(container, color, selected, false, v -> {
                agendaColorHolder[0] = color;
                renderAgendaColorSlider(container, agendaColorHolder);
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
        int selectedColor = getTimetableThemeColor();
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

    private interface OnAgendaLocationPick {
        void onPick(String location);
    }

    private interface OnMinutePick {
        void onPick(int minute);
    }

    private interface OnDatePick {
        void onPick(Calendar date);
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
