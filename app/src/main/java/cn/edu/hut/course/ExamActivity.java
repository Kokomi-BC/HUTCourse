package cn.edu.hut.course;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import cn.edu.hut.course.data.AgendaStorageManager;
import cn.edu.hut.course.data.ExamStorageManager;

public class ExamActivity extends AppCompatActivity {

    private RecyclerView rvExams;
    private TextView tvExamCount;
    private ExamAdapter adapter;
    private List<Exam> examList = new ArrayList<>();

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
        rvExams = findViewById(R.id.rvExams);
        rvExams.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ExamAdapter(this, examList);
        rvExams.setAdapter(adapter);

        // 进入页面时自动同步未导入的考试到日程（去重）
        autoSyncMissingToAgenda();

        loadExams();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyPageVisualStyle();
    }

    private void applyPageVisualStyle() {
        View root = findViewById(R.id.rootExam);
        if (root != null) {
            UiStyleHelper.applySecondaryPageBackground(root, this);
        }
        UiStyleHelper.applyGlassCards(findViewById(android.R.id.content), this);
    }

    private void autoSyncMissingToAgenda() {
        List<Exam> exams = ExamStorageManager.loadExams(this);
        if (exams.isEmpty()) return;

        List<Agenda> existingAgendas = AgendaStorageManager.loadAllAgendas(this);
        int added = 0;
        for (Exam exam : exams) {
            String title = "📝 考试：" + exam.courseName;
            boolean exists = false;
            for (Agenda a : existingAgendas) {
                if (title.equals(a.title) && exam.examDate != null && exam.examDate.equals(a.date)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                Agenda agenda = new Agenda();
                agenda.title = title;
                agenda.description = "考场：" + (exam.location != null ? exam.location : "") +
                        "  教师：" + (exam.teacher != null ? exam.teacher : "");
                agenda.location = exam.location != null ? exam.location : "";
                agenda.date = exam.examDate != null ? exam.examDate : "";
                agenda.startMinute = parseTimeToMinute(exam.startTime);
                agenda.endMinute = parseTimeToMinute(exam.endTime);
                agenda.priority = Agenda.PRIORITY_HIGH;
                agenda.renderColor = UiStyleHelper.resolveAccentColor(this);
                AgendaStorageManager.createAgenda(this, agenda);
                added++;
            }
        }
    }

    private void loadExams() {
        examList.clear();
        examList.addAll(ExamStorageManager.loadExams(this));
        Collections.sort(examList, (a, b) -> {
            int cmp = a.examDate.compareTo(b.examDate);
            if (cmp != 0) return cmp;
            return a.startTime.compareTo(b.startTime);
        });
        adapter.notifyDataSetChanged();
        tvExamCount.setText(String.valueOf(examList.size()));
    }

    private void addAllToAgenda() {
        List<Exam> exams = ExamStorageManager.loadExams(this);
        List<Agenda> existingAgendas = AgendaStorageManager.loadAllAgendas(this);
        int added = 0;
        int skipped = 0;

        for (Exam exam : exams) {
            String title = "📝 考试：" + exam.courseName;
            boolean exists = false;
            for (Agenda a : existingAgendas) {
                if (title.equals(a.title) && exam.examDate != null && exam.examDate.equals(a.date)) {
                    exists = true;
                    break;
                }
            }
            if (exists) {
                skipped++;
                continue;
            }

            Agenda agenda = new Agenda();
            agenda.title = title;
            agenda.description = "考场：" + (exam.location != null ? exam.location : "") +
                    "  教师：" + (exam.teacher != null ? exam.teacher : "");
            agenda.location = exam.location != null ? exam.location : "";
            agenda.date = exam.examDate != null ? exam.examDate : "";
                agenda.startMinute = parseTimeToMinute(exam.startTime);
            agenda.endMinute = parseTimeToMinute(exam.endTime);
            agenda.priority = Agenda.PRIORITY_HIGH;
            agenda.renderColor = UiStyleHelper.resolveAccentColor(this);
            AgendaStorageManager.createAgenda(this, agenda);
            added++;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("添加到日程")
                .setMessage(String.format(Locale.getDefault(),
                        "成功添加 %d 条\n已存在 %d 条（已跳过）", added, skipped))
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

    // ===== Adapter =====

    private static class ExamAdapter extends RecyclerView.Adapter<ExamAdapter.ViewHolder> {

        private final Context context;
        private final List<Exam> exams;
        private final int primaryColor;
        private final int onSurfaceColor;
        private final int onSurfaceVariantColor;

        ExamAdapter(Context context, List<Exam> exams) {
            this.context = context;
            this.exams = exams;
            this.primaryColor = UiStyleHelper.resolveAccentColor(context);
            this.onSurfaceColor = UiStyleHelper.resolveOnSurfaceColor(context);
            this.onSurfaceVariantColor = UiStyleHelper.resolveOnSurfaceVariantColor(context);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_exam_card, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Exam exam = exams.get(position);
            holder.tvCourseName.setText(exam.courseName);

            String dateLabel = exam.examDate != null ? exam.examDate : "--";
            String dateDisplay = dateLabel.length() >= 10 ? dateLabel.substring(5) : dateLabel;
            holder.tvExamDate.setText(dateDisplay);

            // 剩余天数
            String daysRemaining = calcDaysRemaining(exam.examDate);
            if (daysRemaining != null) {
                holder.tvDaysRemaining.setVisibility(View.VISIBLE);
                holder.tvDaysRemaining.setText(daysRemaining);
                if (daysRemaining.startsWith("已")) {
                    holder.tvDaysRemaining.setTextColor(0xFF999999);
                } else if (daysRemaining.startsWith("今天") || daysRemaining.startsWith("明天")) {
                    holder.tvDaysRemaining.setTextColor(0xFFE53935);
                } else {
                    holder.tvDaysRemaining.setTextColor(primaryColor);
                }
            } else {
                holder.tvDaysRemaining.setVisibility(View.GONE);
            }

            holder.tvExamTime.setText((exam.startTime != null ? exam.startTime : "--") +
                    " ~ " + (exam.endTime != null ? exam.endTime : "--"));
            holder.tvExamLocation.setText(exam.location != null ? exam.location : "--");

            // 日期标签：主题色弱化背景 + 主题色文字
            int accentBg = android.graphics.Color.argb(38, 
                    android.graphics.Color.red(primaryColor), 
                    android.graphics.Color.green(primaryColor), 
                    android.graphics.Color.blue(primaryColor));
            holder.tvExamDate.setBackgroundTintList(ColorStateList.valueOf(accentBg));
            holder.tvExamDate.setTextColor(primaryColor);

            // 卡片：玻璃风格（RecyclerView item 无法被 applyGlassCards 遍历到，需在此显式设置背景色）
            if (holder.itemView instanceof MaterialCardView) {
                MaterialCardView card = (MaterialCardView) holder.itemView;
                card.setCardElevation(0f);
                card.setCardBackgroundColor(UiStyleHelper.resolveGlassCardColor(context));
                card.setStrokeWidth(1);
                card.setStrokeColor(android.graphics.Color.argb(24,
                        android.graphics.Color.red(onSurfaceColor),
                        android.graphics.Color.green(onSurfaceColor),
                        android.graphics.Color.blue(onSurfaceColor)));
            }
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
                    return "已过期";
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

        @Override
        public int getItemCount() {
            return exams.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvCourseName, tvExamDate, tvDaysRemaining, tvExamTime, tvExamLocation;

            ViewHolder(View itemView) {
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
