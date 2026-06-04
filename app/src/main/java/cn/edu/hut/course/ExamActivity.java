package cn.edu.hut.course;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import cn.edu.hut.course.data.AgendaStorageManager;
import cn.edu.hut.course.data.ExamStorageManager;

public class ExamActivity extends AppCompatActivity {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private RecyclerView rvExams;
    private TextView tvExamCount;
    private TextView tvExamSummaryLabel;
    private ExamAdapter adapter;
    private List<Exam> examList = new ArrayList<>();
    private List<Exam> upcomingExams = new ArrayList<>();
    private List<Exam> pastExams = new ArrayList<>();
    private final Map<String, Integer> agendaColorMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiStyleHelper.hideStatusBar(this);
        setContentView(R.layout.activity_exam);
        applyPageVisualStyle();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        UiStyleHelper.styleGlassToolbar(toolbar, this);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_rounded_24);
        toolbar.setNavigationOnClickListener(v -> finish());

        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_add_to_agenda) {
                addAllToAgenda();
                return true;
            }
            return false;
        });
        toolbar.inflateMenu(R.menu.menu_exam);

        tvExamCount = findViewById(R.id.tvExamCount);
        tvExamCount.setTextColor(UiStyleHelper.resolveAccentColor(this));
        tvExamSummaryLabel = findViewById(R.id.tvExamSummaryLabel);
        rvExams = findViewById(R.id.rvExams);
        rvExams.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ExamAdapter(this, agendaColorMap, this);
        rvExams.setAdapter(adapter);

        // 进入页面时自动同步考试到日程（全量删除+重建，确保变动同步）
        autoSyncToAgenda();

        loadExams();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyPageVisualStyle();
        // 从日程编辑返回后刷新考试列表和颜色
        loadExams();
    }

    private void applyPageVisualStyle() {
        View root = findViewById(R.id.rootExam);
        if (root != null) {
            UiStyleHelper.applySecondaryPageBackground(root, this);
        }
        UiStyleHelper.applyGlassCards(findViewById(android.R.id.content), this);
    }

    /** 全量同步：删除旧考试日程后重建，确保日期/时间/考场变动自动更新，同时保留用户设置的颜色 */
    private void autoSyncToAgenda() {
        List<Exam> exams = ExamStorageManager.loadExams(this);
        if (exams.isEmpty()) return;

        // 删除前保存旧日程的颜色映射
        Map<String, Integer> savedColors = new HashMap<>();
        List<Agenda> oldAgendas = AgendaStorageManager.loadAllAgendas(this);
        if (oldAgendas != null) {
            for (Agenda a : oldAgendas) {
                if (a != null && a.title != null && a.title.startsWith(AgendaStorageManager.EXAM_AGENDA_PREFIX)) {
                    String courseName = a.title.substring(AgendaStorageManager.EXAM_AGENDA_PREFIX.length());
                    if (a.renderColor != 0 && a.renderColor != Color.TRANSPARENT
                            && a.renderColor != Color.WHITE && a.renderColor != Color.BLACK) {
                        savedColors.put(courseName, a.renderColor);
                    }
                }
            }
        }

        int deleted = AgendaStorageManager.deleteExamAgendas(this);
        int added = 0;
        for (Exam exam : exams) {
            Agenda agenda = buildExamAgenda(exam);
            // 恢复用户之前设置的颜色
            Integer savedColor = savedColors.get(exam.courseName);
            if (savedColor != null) {
                agenda.renderColor = savedColor;
            }
            AgendaStorageManager.createAgenda(this, agenda);
            added++;
        }
        if (added > 0) {
            android.util.Log.d("ExamActivity", "Agenda sync: deleted " + deleted + ", added " + added);
        }
    }

    private Agenda buildExamAgenda(Exam exam) {
        Agenda agenda = new Agenda();
        agenda.title = AgendaStorageManager.EXAM_AGENDA_PREFIX + exam.courseName;
        agenda.description = "考场：" + (exam.location != null ? exam.location : "") +
                "  教师：" + (exam.teacher != null ? exam.teacher : "");
        agenda.location = exam.location != null ? exam.location : "";
        agenda.date = exam.examDate != null ? exam.examDate : "";
        agenda.startMinute = parseTimeToMinute(exam.startTime);
        agenda.endMinute = parseTimeToMinute(exam.endTime);
        agenda.priority = Agenda.PRIORITY_HIGH;
        agenda.renderColor = UiStyleHelper.resolveAccentColor(this);
        agenda.readOnly = true;  // 考试日程自动设为只读
        return agenda;
    }

    private void loadExams() {
        examList.clear();
        examList.addAll(ExamStorageManager.loadExams(this));
        Collections.sort(examList, (a, b) -> {
            int cmp = a.examDate.compareTo(b.examDate);
            if (cmp != 0) return cmp;
            return a.startTime.compareTo(b.startTime);
        });

        // 按今天为界分割：待开始 vs 已结束
        String today = AgendaStorageManager.formatDate(Calendar.getInstance());
        upcomingExams.clear();
        pastExams.clear();
        for (Exam e : examList) {
            if (e.examDate != null && e.examDate.compareTo(today) >= 0) {
                upcomingExams.add(e);
            } else {
                pastExams.add(e);
            }
        }
        // 已结束的倒序（最近结束的在前）
        Collections.reverse(pastExams);

        // 构建日程颜色映射
        agendaColorMap.clear();
        List<Agenda> allAgendas = AgendaStorageManager.loadAllAgendas(this);
        if (allAgendas != null) {
            for (Agenda a : allAgendas) {
                if (a != null && a.title != null && a.title.startsWith(AgendaStorageManager.EXAM_AGENDA_PREFIX)) {
                    String courseName = a.title.substring(AgendaStorageManager.EXAM_AGENDA_PREFIX.length());
                    int color = (a.renderColor != 0 && a.renderColor != Color.TRANSPARENT
                            && a.renderColor != Color.WHITE && a.renderColor != Color.BLACK)
                            ? a.renderColor : UiStyleHelper.resolveAccentColor(this);
                    agendaColorMap.put(courseName, color);
                }
            }
        }

        adapter.notifyDataSetChanged();

        // 更新顶部统计数字
        int remaining = upcomingExams.size();
        int total = examList.size();
        tvExamCount.setText(String.valueOf(remaining));
        tvExamSummaryLabel.setText(remaining > 0 ? "门考试待参加" : "所有考试已结束");
    }

    private void addAllToAgenda() {
        List<Exam> exams = ExamStorageManager.loadExams(this);

        int deleted = AgendaStorageManager.deleteExamAgendas(this);
        int added = 0;
        for (Exam exam : exams) {
            Agenda agenda = buildExamAgenda(exam);
            AgendaStorageManager.createAgenda(this, agenda);
            added++;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("添加到日程")
                .setMessage(String.format(Locale.getDefault(),
                        "已同步 %d 条考试到日程\n（旧日程已清理 %d 条）", added, deleted))
                .setPositiveButton("确定", null)
                .show();
    }

    private int parseTimeToMinute(String time) {
        if (time == null || time.isEmpty()) return 8 * 60;
        try {
            String[] parts = time.split(":");
            if (parts.length >= 2) {
                int h = Integer.parseInt(parts[0].trim());
                int m = Integer.parseInt(parts[1].trim());
                return h * 60 + m;
            }
        } catch (NumberFormatException ignored) {
        }
        return 8 * 60;
    }

    // ===== Adapter with collapsible sections =====

    private void showExamAgendaEditor(@NonNull Exam exam) {
        List<Agenda> allAgendas = AgendaStorageManager.loadAllAgendas(this);
        if (allAgendas == null) {
            Toast.makeText(this, "未找到对应日程", Toast.LENGTH_SHORT).show();
            return;
        }

        String titlePrefix = AgendaStorageManager.EXAM_AGENDA_PREFIX + exam.courseName;
        Agenda matched = null;
        for (Agenda a : allAgendas) {
            if (a != null && titlePrefix.equals(a.title)) {
                matched = a;
                break;
            }
        }
        if (matched == null) {
            Toast.makeText(this, "未找到对应日程，请先同步", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, AgendaOverviewActivity.class);
        intent.putExtra("edit_agenda_id", matched.id);
        startActivity(intent);
    }

    private void showExamAgendaColorEditor(@NonNull Exam exam) {
        List<Agenda> allAgendas = AgendaStorageManager.loadAllAgendas(this);
        if (allAgendas == null) {
            return;
        }

        String titlePrefix = AgendaStorageManager.EXAM_AGENDA_PREFIX + exam.courseName;
        List<Agenda> matched = new ArrayList<>();
        for (Agenda a : allAgendas) {
            if (a != null && titlePrefix.equals(a.title)) {
                matched.add(a);
            }
        }
        if (matched.isEmpty()) {
            Toast.makeText(this, "未找到对应日程", Toast.LENGTH_SHORT).show();
            return;
        }

        int currentColor = agendaColorMap.getOrDefault(exam.courseName,
                UiStyleHelper.resolveAccentColor(this));
        final int[] agendaRenderColor = {currentColor};
        final int sheetSurfaceColor = UiStyleHelper.resolvePageBackgroundColor(this);

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(14), dp(16), dp(20));
        layout.setBackgroundColor(Color.TRANSPARENT);
        scrollView.addView(layout, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int onSurface = UiStyleHelper.resolveOnSurfaceColor(this);

        TextView sheetTitle = new TextView(this);
        sheetTitle.setText("日程颜色 - " + exam.courseName);
        sheetTitle.setTextSize(18f);
        sheetTitle.setTypeface(null, Typeface.BOLD);
        sheetTitle.setTextColor(onSurface);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.setMargins(dp(2), 0, dp(2), dp(12));
        sheetTitle.setLayoutParams(titleLp);
        layout.addView(sheetTitle);

        MaterialCardView colorCard = new MaterialCardView(this);
        colorCard.setRadius(dp(24));
        colorCard.setCardElevation(0f);
        colorCard.setStrokeWidth(1);
        colorCard.setStrokeColor(ColorUtils.setAlphaComponent(onSurface, 24));
        colorCard.setCardBackgroundColor(UiStyleHelper.resolveGlassCardColor(this));
        LinearLayout.LayoutParams colorCardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        colorCard.setLayoutParams(colorCardLp);

        LinearLayout colorBody = new LinearLayout(this);
        colorBody.setOrientation(LinearLayout.VERTICAL);
        colorBody.setPadding(dp(14), dp(12), dp(14), dp(12));
        colorCard.addView(colorBody);

        HorizontalScrollView colorScroll = new HorizontalScrollView(this);
        colorScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams colorScrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        colorScrollLp.setMargins(0, dp(10), 0, 0);
        colorScroll.setLayoutParams(colorScrollLp);

        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        colorScroll.addView(colorRow, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        colorBody.addView(colorScroll);
        layout.addView(colorCard);

        final Runnable[] renderColorsHolder = {null};
        renderColorsHolder[0] = () -> {
            colorRow.removeAllViews();
            addExamColorDot(colorRow, 0, agendaRenderColor[0] == 0, true, v -> {
                agendaRenderColor[0] = 0;
                renderColorsHolder[0].run();
            });
            int[] palette = ColorPaletteProvider.vibrantLightPalette();
            for (int color : palette) {
                boolean selected = agendaRenderColor[0] == color;
                addExamColorDot(colorRow, color, selected, false, v -> {
                    agendaRenderColor[0] = color;
                    renderColorsHolder[0].run();
                });
            }
        };
        renderColorsHolder[0].run();

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionRowLp.setMargins(0, dp(12), 0, 0);
        actionRow.setLayoutParams(actionRowLp);

        MaterialButton cancelButton = new MaterialButton(this);
        cancelButton.setAllCaps(false);
        cancelButton.setCornerRadius(dp(24));
        cancelButton.setMinHeight(dp(52));
        cancelButton.setStrokeWidth(0);
        cancelButton.setBackgroundTintList(ColorStateList.valueOf(
                ColorUtils.setAlphaComponent(onSurface, 34)));
        cancelButton.setTextColor(onSurface);
        cancelButton.setText("取消");
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cancelLp.setMargins(0, 0, dp(8), 0);
        cancelButton.setLayoutParams(cancelLp);
        cancelButton.setOnClickListener(v -> dialog.dismiss());
        actionRow.addView(cancelButton);

        MaterialButton saveButton = new MaterialButton(this);
        saveButton.setAllCaps(false);
        saveButton.setCornerRadius(dp(24));
        saveButton.setMinHeight(dp(52));
        saveButton.setStrokeWidth(0);
        int accent = UiStyleHelper.resolveAccentColor(this);
        saveButton.setBackgroundTintList(ColorStateList.valueOf(accent));
        saveButton.setTextColor(ColorUtils.calculateLuminance(accent) < 0.5 ? Color.WHITE : Color.BLACK);
        saveButton.setText("保存");
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        saveButton.setLayoutParams(saveLp);
        saveButton.setOnClickListener(v -> {
            int newColor = agendaRenderColor[0];
            for (Agenda a : matched) {
                a.renderColor = newColor;
                AgendaStorageManager.updateAgenda(ExamActivity.this, a);
            }
            agendaColorMap.put(exam.courseName,
                    newColor != 0 ? newColor : UiStyleHelper.resolveAccentColor(ExamActivity.this));
            adapter.notifyDataSetChanged();
            dialog.dismiss();
        });
        actionRow.addView(saveButton);

        layout.addView(actionRow);
        dialog.setContentView(scrollView);

        dialog.setOnShowListener(d -> {
            FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet == null) return;
            BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(sheet);
            behavior.setDraggable(false);
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);

            GradientDrawable bg = new GradientDrawable();
            float radius = dp(28);
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setColor(sheetSurfaceColor);
            bg.setCornerRadii(new float[]{radius, radius, radius, radius, 0f, 0f, 0f, 0f});
            sheet.setBackground(bg);
            View parent = (View) sheet.getParent();
            if (parent != null) {
                parent.setBackgroundColor(Color.TRANSPARENT);
            }
        });
        dialog.show();
    }

    private void addExamColorDot(@NonNull LinearLayout container, int color,
                                  boolean selected, boolean isDefault,
                                  @NonNull View.OnClickListener click) {
        MaterialCardView dot = new MaterialCardView(this);
        int size = dp(38);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMargins(0, 0, dp(10), 0);
        dot.setLayoutParams(lp);
        dot.setRadius(size / 2f);
        dot.setCardElevation(0f);
        dot.setStrokeWidth(selected ? dp(2) : dp(1));
        int outline = UiStyleHelper.resolveOutlineColor(this);
        int selectedColor = UiStyleHelper.resolveAccentColor(this);
        dot.setStrokeColor(selected ? selectedColor : outline);

        if (isDefault) {
            dot.setCardBackgroundColor(UiStyleHelper.resolveGlassCardColor(this));
            TextView icon = new TextView(this);
            icon.setText("\u2298");
            icon.setGravity(Gravity.CENTER);
            icon.setTextSize(16f);
            icon.setTextColor(UiStyleHelper.resolveOnSurfaceVariantColor(this));
            dot.addView(icon, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            dot.setCardBackgroundColor(color);
        }
        dot.setOnClickListener(click);
        container.addView(dot);
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private class ExamAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final Context context;
        private final int primaryColor;
        private final int onSurfaceColor;
        private final int onSurfaceVariantColor;

        private final Map<String, Integer> agendaColorMap;
        private final ExamActivity parentActivity;

        private boolean upcomingCollapsed = false;
        private boolean pastCollapsed = true; // 已结束默认折叠

        ExamAdapter(Context context, Map<String, Integer> agendaColorMap, ExamActivity parentActivity) {
            this.context = context;
            this.agendaColorMap = agendaColorMap;
            this.parentActivity = parentActivity;
            this.primaryColor = UiStyleHelper.resolveAccentColor(context);
            this.onSurfaceColor = UiStyleHelper.resolveOnSurfaceColor(context);
            this.onSurfaceVariantColor = UiStyleHelper.resolveOnSurfaceVariantColor(context);
        }

        @Override
        public int getItemViewType(int position) {
            if (isHeaderPosition(position)) return TYPE_HEADER;
            return TYPE_ITEM;
        }

        private boolean isHeaderPosition(int position) {
            if (position == 0) return true;
            if (!upcomingCollapsed && position == 1 + upcomingExams.size()) return true;
            if (upcomingCollapsed && position == 1) return true;
            return false;
        }

        @Override
        public int getItemCount() {
            int count = 0;
            // 待开始 header (always visible)
            count++;
            if (!upcomingCollapsed) {
                count += upcomingExams.size();
            }
            // 已结束 header (only if there are past exams)
            if (!pastExams.isEmpty()) {
                count++;
                if (!pastCollapsed) {
                    count += pastExams.size();
                }
            }
            return count;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_HEADER) {
                View view = LayoutInflater.from(context).inflate(R.layout.item_exam_section_header, parent, false);
                return new HeaderViewHolder(view);
            } else {
                View view = LayoutInflater.from(context).inflate(R.layout.item_exam_card, parent, false);
                return new ExamViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof HeaderViewHolder) {
                bindHeader((HeaderViewHolder) holder, position);
            } else {
                bindExam((ExamViewHolder) holder, position);
            }
        }

        private void bindHeader(HeaderViewHolder holder, int position) {
            boolean isUpcoming = (position == 0);
            String title;
            int count;
            boolean collapsed;

            if (isUpcoming) {
                title = "将要开始";
                count = upcomingExams.size();
                collapsed = upcomingCollapsed;
            } else {
                title = "已结束";
                count = pastExams.size();
                collapsed = pastCollapsed;
            }

            holder.tvSectionTitle.setText(title);
            holder.tvSectionCount.setText(count + "门");
            holder.ivSectionArrow.setImageResource(collapsed
                    ? R.drawable.ic_chevron_down_wide_24
                    : R.drawable.ic_chevron_up_wide_24);

            holder.itemView.setOnClickListener(v -> {
                if (isUpcoming) {
                    upcomingCollapsed = !upcomingCollapsed;
                } else {
                    pastCollapsed = !pastCollapsed;
                }
                notifyDataSetChanged();
            });
        }

        private void bindExam(ExamViewHolder holder, int position) {
            Exam exam = getExamAtPosition(position);
            if (exam == null) return;

            holder.tvCourseName.setText(exam.courseName);

            // 应用日程颜色
            Integer agendaColor = agendaColorMap.get(exam.courseName);
            int displayColor = agendaColor != null ? agendaColor : primaryColor;
            holder.tvCourseName.setTextColor(displayColor);

            String dateLabel = exam.examDate != null ? exam.examDate : "--";
            String dateDisplay = dateLabel.length() >= 10 ? dateLabel.substring(5) : dateLabel;
            holder.tvExamDate.setText(dateDisplay);

            String daysRemaining = calcDaysRemaining(exam.examDate);
            if (daysRemaining != null) {
                holder.tvDaysRemaining.setVisibility(View.VISIBLE);
                holder.tvDaysRemaining.setText(daysRemaining);
                if (daysRemaining.startsWith("已")) {
                    holder.tvDaysRemaining.setTextColor(0xFF999999);
                } else if (daysRemaining.startsWith("今天") || daysRemaining.startsWith("明天")) {
                    holder.tvDaysRemaining.setTextColor(0xFFE53935);
                } else {
                    holder.tvDaysRemaining.setTextColor(displayColor);
                }
            } else {
                holder.tvDaysRemaining.setVisibility(View.GONE);
            }

            holder.tvExamTime.setText((exam.startTime != null ? exam.startTime : "--") +
                    " ~ " + (exam.endTime != null ? exam.endTime : "--"));
            holder.tvExamLocation.setText(exam.location != null ? exam.location : "--");

            int accentBg = android.graphics.Color.argb(38,
                    android.graphics.Color.red(displayColor),
                    android.graphics.Color.green(displayColor),
                    android.graphics.Color.blue(displayColor));
            holder.tvExamDate.setBackgroundTintList(ColorStateList.valueOf(accentBg));
            holder.tvExamDate.setTextColor(displayColor);

            if (holder.itemView instanceof MaterialCardView) {
                MaterialCardView card = (MaterialCardView) holder.itemView;
                card.setCardElevation(0f);
                card.setCardBackgroundColor(UiStyleHelper.resolveGlassCardColor(context));
                card.setStrokeWidth(1);
                card.setStrokeColor(android.graphics.Color.argb(24,
                        android.graphics.Color.red(onSurfaceColor),
                        android.graphics.Color.green(onSurfaceColor),
                        android.graphics.Color.blue(onSurfaceColor)));
                card.setOnClickListener(v -> parentActivity.showExamAgendaEditor(exam));
            }
        }

        private Exam getExamAtPosition(int position) {
            // Position 0: upcoming header
            // Then upcoming items (if not collapsed)
            // Then past header (if past not empty)
            // Then past items (if not collapsed)

            int upcomingHeaderPos = 0;
            int upcomingStart = 1;
            int upcomingEnd = upcomingStart + (upcomingCollapsed ? 0 : upcomingExams.size());

            if (position >= upcomingStart && position < upcomingEnd) {
                return upcomingExams.get(position - upcomingStart);
            }

            if (pastExams.isEmpty()) return null;

            int pastHeaderPos = upcomingEnd;
            int pastStart = pastHeaderPos + 1;

            if (position == pastHeaderPos) return null; // header, not an exam

            if (position >= pastStart && position < pastStart + (pastCollapsed ? 0 : pastExams.size())) {
                return pastExams.get(position - pastStart);
            }

            return null;
        }

        private String calcDaysRemaining(String examDate) {
            if (examDate == null || examDate.isEmpty()) return null;
            try {
                String[] parts = examDate.split("-");
                if (parts.length != 3) return null;
                Calendar examCal = Calendar.getInstance();
                examCal.set(Calendar.YEAR, Integer.parseInt(parts[0]));
                examCal.set(Calendar.MONTH, Integer.parseInt(parts[1]) - 1);
                examCal.set(Calendar.DAY_OF_MONTH, Integer.parseInt(parts[2]));
                examCal.set(Calendar.HOUR_OF_DAY, 0);
                examCal.set(Calendar.MINUTE, 0);
                examCal.set(Calendar.SECOND, 0);
                examCal.set(Calendar.MILLISECOND, 0);

                Calendar today = Calendar.getInstance();
                today.set(Calendar.HOUR_OF_DAY, 0);
                today.set(Calendar.MINUTE, 0);
                today.set(Calendar.SECOND, 0);
                today.set(Calendar.MILLISECOND, 0);

                long diffMs = examCal.getTimeInMillis() - today.getTimeInMillis();
                long diffDays = diffMs / (24 * 60 * 60 * 1000);

                if (diffDays < 0) {
                    return "已结束";
                } else if (diffDays == 0) {
                    return "今天考试";
                } else if (diffDays == 1) {
                    return "明天考试";
                } else {
                    return "剩余 " + diffDays + " 天";
                }
            } catch (Exception e) {
                return null;
            }
        }

        class HeaderViewHolder extends RecyclerView.ViewHolder {
            TextView tvSectionTitle, tvSectionCount;
            ImageView ivSectionArrow;

            HeaderViewHolder(View itemView) {
                super(itemView);
                tvSectionTitle = itemView.findViewById(R.id.tvSectionTitle);
                tvSectionCount = itemView.findViewById(R.id.tvSectionCount);
                ivSectionArrow = itemView.findViewById(R.id.ivSectionArrow);
            }
        }

        class ExamViewHolder extends RecyclerView.ViewHolder {
            TextView tvCourseName, tvExamDate, tvDaysRemaining, tvExamTime, tvExamLocation;

            ExamViewHolder(View itemView) {
                super(itemView);
                tvCourseName = itemView.findViewById(R.id.tvCourseName);
                tvExamDate = itemView.findViewById(R.id.tvExamDate);
                tvDaysRemaining = itemView.findViewById(R.id.tvDaysRemaining);
                tvExamTime = itemView.findViewById(R.id.tvExamTime);
                tvExamLocation = itemView.findViewById(R.id.tvExamLocation);
            }
        }
    }
}
