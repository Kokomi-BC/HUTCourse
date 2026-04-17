package cn.edu.hut.course;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import cn.edu.hut.course.data.AgendaStorageManager;
import cn.edu.hut.course.data.CampusBuildingStore;

public class AgendaOverviewActivity extends AppCompatActivity {

    private static final int STATUS_ONGOING = 1;
    private static final int STATUS_UPCOMING = 2;
    private static final int STATUS_ENDED = 3;
    private static final int OCCURRENCE_SCAN_DAYS = 540;
    private static final String[] WEEK_DAY_LABELS = {"一", "二", "三", "四", "五", "六", "日"};
    private static final int[] AGENDA_PRIORITY_VALUES = {Agenda.PRIORITY_LOW, Agenda.PRIORITY_MEDIUM, Agenda.PRIORITY_HIGH};
    private static final String[] AGENDA_PRIORITY_LABELS = {"低", "中", "高"};
    private static final String[] AGENDA_REPEAT_VALUES = {Agenda.REPEAT_NONE, Agenda.REPEAT_DAILY, Agenda.REPEAT_WEEKLY, Agenda.REPEAT_MONTHLY};
    private static final String[] AGENDA_REPEAT_LABELS = {"不重复", "每天", "每周", "每月固定日"};
    private static final String[] AGENDA_MONTHLY_VALUES = {Agenda.MONTHLY_SKIP, Agenda.MONTHLY_MONTH_END};
    private static final String[] AGENDA_MONTHLY_LABELS = {"短月跳过", "短月改月底"};

    private TextView tvAgendaOverviewTitle;
    private TextView tvAgendaOverviewSummary;
    private LinearLayout agendaOverviewContainer;

    private boolean agendaOngoingCollapsed = false;
    private boolean agendaUpcomingCollapsed = false;
    private boolean agendaEndedCollapsed = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiStyleHelper.hideStatusBar(this);
        setContentView(R.layout.activity_agenda_overview);

        View root = findViewById(R.id.rootAgendaOverview);
        tvAgendaOverviewTitle = findViewById(R.id.tvAgendaOverviewTitle);
        tvAgendaOverviewSummary = findViewById(R.id.tvAgendaOverviewSummary);
        agendaOverviewContainer = findViewById(R.id.agendaOverviewContainer);
        ImageButton btnBack = findViewById(R.id.btnAgendaOverviewBack);
        ImageButton btnAdd = findViewById(R.id.btnAgendaOverviewAdd);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }
        if (btnAdd != null) {
            btnAdd.setOnClickListener(v -> {
                showAgendaEditorDialog(null, Calendar.getInstance());
            });
        }

        applyPageStyle(root, btnBack, btnAdd);
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderAgendaOverviewPage();
    }

    private void applyPageStyle(@Nullable View root,
                                @Nullable ImageButton btnBack,
                                @Nullable ImageButton btnAdd) {
        if (root != null) {
            UiStyleHelper.applySecondaryPageBackground(root, this);
            UiStyleHelper.applyGlassCards(root, this);
        }

        int onSurface = UiStyleHelper.resolveOnSurfaceColor(this);

        if (tvAgendaOverviewTitle != null) {
            tvAgendaOverviewTitle.setTextColor(onSurface);
        }
        if (tvAgendaOverviewSummary != null) {
            tvAgendaOverviewSummary.setVisibility(View.GONE);
        }
        if (btnBack != null) {
            btnBack.setImageTintList(ColorStateList.valueOf(onSurface));
        }
        if (btnAdd != null) {
            btnAdd.setImageTintList(ColorStateList.valueOf(onSurface));
        }
    }

    private void renderAgendaOverviewPage() {
        if (agendaOverviewContainer == null) {
            return;
        }
        agendaOverviewContainer.removeAllViews();

        List<Agenda> allAgendas = AgendaStorageManager.loadAllAgendas(this);
        if (allAgendas == null) {
            allAgendas = new ArrayList<>();
        }

        List<AgendaOccurrenceItem> ongoing = new ArrayList<>();
        List<AgendaOccurrenceItem> upcoming = new ArrayList<>();
        List<AgendaOccurrenceItem> ended = new ArrayList<>();

        Calendar now = Calendar.getInstance();
        Calendar today = cloneAsDay(now);
        int nowMinute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        for (Agenda agenda : allAgendas) {
            if (agenda == null) {
                continue;
            }
            AgendaOccurrenceItem item = resolveAgendaOccurrenceItem(agenda, today, nowMinute);
            if (item.status == STATUS_ONGOING) {
                ongoing.add(item);
            } else if (item.status == STATUS_ENDED) {
                ended.add(item);
            } else {
                upcoming.add(item);
            }
        }

        sortAgendaOverviewItems(ongoing, false);
        sortAgendaOverviewItems(upcoming, false);
        sortAgendaOverviewItems(ended, true);

        if (!ongoing.isEmpty()) {
            addStatusSection("进行中", ongoing, STATUS_ONGOING);
        }
        addStatusSection("将要开始", upcoming, STATUS_UPCOMING);
        addStatusSection("已结束", ended, STATUS_ENDED);
    }

    @NonNull
    private AgendaOccurrenceItem resolveAgendaOccurrenceItem(@NonNull Agenda agenda,
                                                             @NonNull Calendar today,
                                                             int nowMinute) {
        AgendaOccurrenceItem item = new AgendaOccurrenceItem();
        item.agenda = agenda;

        Calendar todayDay = cloneAsDay(today);
        boolean occursToday = AgendaStorageManager.occursOnDate(agenda, todayDay);

        if (occursToday) {
            item.occurrenceDate = cloneAsDay(todayDay);
            if (agenda.startMinute <= nowMinute && nowMinute < agenda.endMinute) {
                item.status = STATUS_ONGOING;
                return item;
            }
            if (nowMinute < agenda.startMinute) {
                item.status = STATUS_UPCOMING;
                return item;
            }

            Calendar tomorrow = cloneAsDay(todayDay);
            tomorrow.add(Calendar.DAY_OF_YEAR, 1);
            Calendar nextDate = findNextOccurrenceDate(agenda, tomorrow, OCCURRENCE_SCAN_DAYS);
            if (nextDate != null) {
                item.status = STATUS_UPCOMING;
                item.occurrenceDate = nextDate;
            } else {
                item.status = STATUS_ENDED;
            }
            return item;
        }

        Calendar nextDate = findNextOccurrenceDate(agenda, todayDay, OCCURRENCE_SCAN_DAYS);
        if (nextDate != null) {
            item.status = STATUS_UPCOMING;
            item.occurrenceDate = nextDate;
            return item;
        }

        Calendar yesterday = cloneAsDay(todayDay);
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        Calendar lastDate = findLastOccurrenceDate(agenda, yesterday, OCCURRENCE_SCAN_DAYS);
        if (lastDate == null) {
            lastDate = AgendaStorageManager.parseDateOrNull(agenda.date);
        }
        item.status = STATUS_ENDED;
        item.occurrenceDate = lastDate;
        return item;
    }

    @Nullable
    private Calendar findNextOccurrenceDate(@Nullable Agenda agenda,
                                            @Nullable Calendar fromInclusive,
                                            int scanDays) {
        if (agenda == null || fromInclusive == null || scanDays < 0) {
            return null;
        }
        Calendar probe = cloneAsDay(fromInclusive);
        for (int i = 0; i <= scanDays; i++) {
            if (AgendaStorageManager.occursOnDate(agenda, probe)) {
                return cloneAsDay(probe);
            }
            probe.add(Calendar.DAY_OF_YEAR, 1);
        }
        return null;
    }

    @Nullable
    private Calendar findLastOccurrenceDate(@Nullable Agenda agenda,
                                            @Nullable Calendar fromInclusive,
                                            int scanDays) {
        if (agenda == null || fromInclusive == null || scanDays < 0) {
            return null;
        }
        Calendar probe = cloneAsDay(fromInclusive);
        for (int i = 0; i <= scanDays; i++) {
            if (AgendaStorageManager.occursOnDate(agenda, probe)) {
                return cloneAsDay(probe);
            }
            probe.add(Calendar.DAY_OF_YEAR, -1);
        }
        return null;
    }

    private void sortAgendaOverviewItems(@Nullable List<AgendaOccurrenceItem> items, boolean endedOrder) {
        if (items == null || items.size() <= 1) {
            return;
        }

        items.sort((left, right) -> {
            Calendar leftDate = left == null ? null : left.occurrenceDate;
            Calendar rightDate = right == null ? null : right.occurrenceDate;

            if (leftDate == null && rightDate != null) {
                return 1;
            }
            if (leftDate != null && rightDate == null) {
                return -1;
            }
            if (leftDate != null && rightDate != null) {
                int byDate = Long.compare(leftDate.getTimeInMillis(), rightDate.getTimeInMillis());
                if (byDate != 0) {
                    return endedOrder ? -byDate : byDate;
                }
            }

            Agenda leftAgenda = left == null ? null : left.agenda;
            Agenda rightAgenda = right == null ? null : right.agenda;
            int byMinute = endedOrder
                    ? Integer.compare(rightAgenda == null ? 0 : rightAgenda.endMinute, leftAgenda == null ? 0 : leftAgenda.endMinute)
                    : Integer.compare(leftAgenda == null ? 0 : leftAgenda.startMinute, rightAgenda == null ? 0 : rightAgenda.startMinute);
            if (byMinute != 0) {
                return byMinute;
            }
            return safeText(leftAgenda == null ? null : leftAgenda.title)
                    .compareToIgnoreCase(safeText(rightAgenda == null ? null : rightAgenda.title));
        });
    }

    private void addStatusSection(@NonNull String sectionName,
                                  @Nullable List<AgendaOccurrenceItem> items,
                                  int statusType) {
        if (agendaOverviewContainer == null) {
            return;
        }

        List<AgendaOccurrenceItem> safeItems = items == null ? new ArrayList<>() : items;

        MaterialCardView headerCard = new MaterialCardView(this);
        headerCard.setCardBackgroundColor(Color.TRANSPARENT);
        headerCard.setCardElevation(0f);
        headerCard.setStrokeWidth(0);
        headerCard.setUseCompatPadding(false);
        headerCard.setRadius(dp(12));
        headerCard.setClickable(true);
        headerCard.setFocusable(true);
        headerCard.setRippleColor(ColorStateList.valueOf(Color.TRANSPARENT));
        headerCard.setForeground(null);

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(dp(10), dp(6), dp(10), dp(6));
        headerRow.setClickable(false);
        headerRow.setFocusable(false);

        TextView headerText = new TextView(this);
        headerText.setTextSize(13f);
        headerText.setTypeface(null, Typeface.BOLD);
        headerText.setTextColor(UiStyleHelper.resolveOnSurfaceColor(this));
        headerText.setIncludeFontPadding(false);
        headerRow.addView(headerText);

        ImageView headerArrow = new ImageView(this);
        LinearLayout.LayoutParams arrowLp = new LinearLayout.LayoutParams(dp(18), dp(18));
        arrowLp.setMargins(dp(6), 0, 0, 0);
        headerArrow.setLayoutParams(arrowLp);
        headerArrow.setImageTintList(ColorStateList.valueOf(UiStyleHelper.resolveOnSurfaceColor(this)));
        headerRow.addView(headerArrow);

        headerCard.addView(headerRow, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout sectionContent = new LinearLayout(this);
        sectionContent.setOrientation(LinearLayout.VERTICAL);
        sectionContent.setVisibility(isSectionCollapsed(statusType) ? View.GONE : View.VISIBLE);

        if (safeItems.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无日程");
            empty.setTextColor(UiStyleHelper.resolveOnSurfaceVariantColor(this));
            empty.setTextSize(13f);
            empty.setPadding(dp(4), 0, dp(4), dp(8));
            sectionContent.addView(empty);
        } else {
            populateDateGroups(sectionContent, safeItems);
        }

        Runnable refreshHeader = () -> {
            boolean collapsed = isSectionCollapsed(statusType);
            headerText.setText(sectionName + "（" + safeItems.size() + "）");
            headerArrow.setImageResource(collapsed
                    ? R.drawable.ic_chevron_down_wide_24
                    : R.drawable.ic_chevron_up_wide_24);
            sectionContent.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        };
        refreshHeader.run();

        headerCard.setOnClickListener(v -> {
            setSectionCollapsed(statusType, !isSectionCollapsed(statusType));
            refreshHeader.run();
        });

        agendaOverviewContainer.addView(headerCard);
        agendaOverviewContainer.addView(sectionContent);
    }

    private void populateDateGroups(@NonNull LinearLayout container,
                                    @NonNull List<AgendaOccurrenceItem> items) {
        String currentKey = null;
        for (AgendaOccurrenceItem item : items) {
            if (item == null || item.agenda == null) {
                continue;
            }
            String dateKey = buildDateKey(item);
            if (!TextUtils.equals(currentKey, dateKey)) {
                currentKey = dateKey;
                container.addView(createDateLabel(formatDateLabel(item)));
            }
            container.addView(createAgendaTimelineCard(item));
        }
    }

    @NonNull
    private String buildDateKey(@NonNull AgendaOccurrenceItem item) {
        if (item.occurrenceDate != null) {
            return AgendaStorageManager.formatDate(item.occurrenceDate);
        }
        String raw = safeText(item.agenda == null ? null : item.agenda.date).trim();
        return raw.isEmpty() ? "未设置日期" : raw;
    }

    @NonNull
    private String formatDateLabel(@NonNull AgendaOccurrenceItem item) {
        Calendar date = item.occurrenceDate;
        if (date == null) {
            Calendar parsed = AgendaStorageManager.parseDateOrNull(item.agenda == null ? null : item.agenda.date);
            if (parsed != null) {
                date = parsed;
            }
        }
        if (date == null) {
            return "未设置日期";
        }

        Calendar normalized = cloneAsDay(date);
        int dayIndex = normalized.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
                ? 6
                : Math.max(0, normalized.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY);
        return String.format(Locale.getDefault(), "%04d-%02d-%02d 周%s",
                normalized.get(Calendar.YEAR),
                normalized.get(Calendar.MONTH) + 1,
                normalized.get(Calendar.DAY_OF_MONTH),
                WEEK_DAY_LABELS[dayIndex]);
    }

    @NonNull
    private TextView createDateLabel(@NonNull String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(UiStyleHelper.resolveOnSurfaceColor(this));
        label.setTextSize(13f);
        label.setTypeface(null, Typeface.BOLD);
        label.setIncludeFontPadding(false);
        label.setPadding(dp(4), dp(2), dp(4), dp(4));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, dp(2), 0, dp(8));
        label.setLayoutParams(lp);
        return label;
    }

    @NonNull
    private MaterialCardView createAgendaTimelineCard(@NonNull AgendaOccurrenceItem item) {
        Agenda agenda = item.agenda;
        int accent = resolveAgendaAccent(agenda);
        int onSurface = UiStyleHelper.resolveOnSurfaceColor(this);
        int onSurfaceVariant = UiStyleHelper.resolveOnSurfaceVariantColor(this);

        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(24));
        card.setCardElevation(0f);
        card.setStrokeWidth(1);
        card.setStrokeColor(ColorUtils.setAlphaComponent(onSurface, 24));
        card.setCardBackgroundColor(UiStyleHelper.resolveGlassCardColor(this));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
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

        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        );
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
        card.setOnClickListener(v -> showAgendaEditorDialog(agenda, item.occurrenceDate));
        return card;
    }

    private void showAgendaEditorDialog(@Nullable Agenda source, @Nullable Calendar preferredDate) {
        Calendar initialDate = source == null ? null : AgendaStorageManager.parseDateOrNull(source.date);
        if (initialDate == null) {
            initialDate = preferredDate == null ? cloneAsDay(Calendar.getInstance()) : cloneAsDay(preferredDate);
        }

        final Calendar[] selectedDate = {cloneAsDay(initialDate)};
        final int[] startMinute = {source == null ? 8 * 60 : Math.max(0, source.startMinute)};
        final int[] endMinute = {source == null ? 9 * 60 : Math.min(24 * 60, source.endMinute)};
        if (endMinute[0] <= startMinute[0]) {
            endMinute[0] = Math.min(24 * 60, startMinute[0] + 30);
        }
        final int[] priority = {normalizeAgendaPriorityValue(source == null ? Agenda.PRIORITY_LOW : source.priority)};
        final String[] repeatRule = {source == null ? Agenda.REPEAT_NONE : normalizeAgendaRepeat(source.repeatRule)};
        final String[] monthlyStrategy = {source == null ? Agenda.MONTHLY_SKIP : normalizeAgendaMonthlyStrategy(source.monthlyStrategy)};
        final String[] locationValue = {source == null ? "" : normalizeAgendaLocationInput(source.location)};
        final int[] agendaRenderColor = {normalizeAgendaStoredRenderColor(source == null ? 0 : source.renderColor)};
        final int sheetSurfaceColor = UiStyleHelper.resolvePageBackgroundColor(this);

        BottomSheetDialog dialog = new BottomSheetDialog(this);

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

        refreshAgendaEditorButtons(dateValueView, startTimeValueView, endTimeValueView, priorityDropdownView, repeatDropdownView,
            monthlyValueView, locationValueView,
            monthlyContainer,
            selectedDate[0], startMinute[0], endMinute[0], priority[0], repeatRule[0], monthlyStrategy[0], locationValue[0]);

        rowDate.setOnClickListener(v -> showAgendaDatePickerDialog(selectedDate[0], pickedDate -> {
            selectedDate[0] = cloneAsDay(pickedDate);
            refreshAgendaEditorButtons(dateValueView, startTimeValueView, endTimeValueView, priorityDropdownView, repeatDropdownView,
                monthlyValueView, locationValueView,
                monthlyContainer,
                selectedDate[0], startMinute[0], endMinute[0], priority[0], repeatRule[0], monthlyStrategy[0], locationValue[0]);
        }));

        startTimeValueView.setOnClickListener(v -> showAgendaTimePickerDialog(startMinute[0], pickedMinute -> {
            startMinute[0] = pickedMinute;
            if (endMinute[0] <= startMinute[0]) {
                endMinute[0] = Math.min(24 * 60, startMinute[0] + 30);
            }
            refreshAgendaEditorButtons(dateValueView, startTimeValueView, endTimeValueView, priorityDropdownView, repeatDropdownView,
                monthlyValueView, locationValueView,
                monthlyContainer,
                selectedDate[0], startMinute[0], endMinute[0], priority[0], repeatRule[0], monthlyStrategy[0], locationValue[0]);
        }));

        endTimeValueView.setOnClickListener(v -> showAgendaTimePickerDialog(endMinute[0], pickedMinute -> {
            endMinute[0] = pickedMinute;
            if (endMinute[0] <= startMinute[0]) {
                endMinute[0] = Math.min(24 * 60, startMinute[0] + 30);
                Toast.makeText(this, "结束时间已自动调整为开始后30分钟", Toast.LENGTH_SHORT).show();
            }
            refreshAgendaEditorButtons(dateValueView, startTimeValueView, endTimeValueView, priorityDropdownView, repeatDropdownView,
                monthlyValueView, locationValueView,
                monthlyContainer,
                selectedDate[0], startMinute[0], endMinute[0], priority[0], repeatRule[0], monthlyStrategy[0], locationValue[0]);
        }));

        priorityDropdownView.setOnItemClickListener((parent, view, position, id) -> {
            int index = clampIndex(position, AGENDA_PRIORITY_VALUES.length);
            priority[0] = AGENDA_PRIORITY_VALUES[index];
            refreshAgendaEditorButtons(dateValueView, startTimeValueView, endTimeValueView, priorityDropdownView, repeatDropdownView,
                monthlyValueView, locationValueView,
                monthlyContainer,
                selectedDate[0], startMinute[0], endMinute[0], priority[0], repeatRule[0], monthlyStrategy[0], locationValue[0]);
        });

        repeatDropdownView.setOnItemClickListener((parent, view, position, id) -> {
            int index = clampIndex(position, AGENDA_REPEAT_VALUES.length);
            repeatRule[0] = AGENDA_REPEAT_VALUES[index];
            refreshAgendaEditorButtons(dateValueView, startTimeValueView, endTimeValueView, priorityDropdownView, repeatDropdownView,
                monthlyValueView, locationValueView,
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
                    refreshAgendaEditorButtons(dateValueView, startTimeValueView, endTimeValueView, priorityDropdownView, repeatDropdownView,
                        monthlyValueView, locationValueView,
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
            refreshAgendaEditorButtons(dateValueView, startTimeValueView, endTimeValueView, priorityDropdownView, repeatDropdownView,
                monthlyValueView, locationValueView,
                monthlyContainer,
                selectedDate[0], startMinute[0], endMinute[0], priority[0], repeatRule[0], monthlyStrategy[0], locationValue[0]);
        }));

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
                agenda.location = normalizeAgendaLocationInput(locationValue[0]);
                agenda.date = AgendaStorageManager.formatDate(selectedDate[0]);
                agenda.startMinute = startMinute[0];
                agenda.endMinute = endMinute[0];
                agenda.priority = priority[0];
                agenda.renderColor = normalizeAgendaStoredRenderColor(agendaRenderColor[0]);
                agenda.repeatRule = repeatRule[0];
                agenda.monthlyStrategy = Agenda.REPEAT_MONTHLY.equals(repeatRule[0]) ? monthlyStrategy[0] : Agenda.MONTHLY_SKIP;

                boolean success;
                if (source == null) {
                    success = AgendaStorageManager.createAgenda(this, agenda) > 0L;
                } else {
                    success = AgendaStorageManager.updateAgenda(this, agenda);
                }

                if (!success) {
                    Toast.makeText(this, source == null ? "新增失败" : "保存失败", Toast.LENGTH_SHORT).show();
                    return;
                }

                Toast.makeText(this, source == null ? "已新增日程" : "已保存日程", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                renderAgendaOverviewPage();
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
                    .setPositiveButton("删除", (confirmDialog, which) -> {
                        if (AgendaStorageManager.deleteAgenda(this, source.id)) {
                            Toast.makeText(this, "已删除日程", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                            renderAgendaOverviewPage();
                        } else {
                            Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .show());
            actionRow.addView(deleteButton);
        }

        MaterialButton saveButton = createAgendaActionButton(true);
        saveButton.setText(source == null ? "新增" : "保存");
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

    private void refreshAgendaEditorButtons(@NonNull TextView dateValueView,
                                            @NonNull TextView startTimeValueView,
                                            @NonNull TextView endTimeValueView,
                                            @NonNull MaterialAutoCompleteTextView priorityDropdownView,
                                            @NonNull MaterialAutoCompleteTextView repeatDropdownView,
                                            @NonNull TextView monthlyValueView,
                                            @NonNull TextView locationValueView,
                                            @NonNull View monthlyContainer,
                                            @NonNull Calendar date,
                                            int startMinute,
                                            int endMinute,
                                            int priority,
                                            @NonNull String repeatRule,
                                            @NonNull String monthlyStrategy,
                                            @NonNull String locationText) {
        dateValueView.setText(formatEditableDateLabel(date));
        startTimeValueView.setText(formatMinute(startMinute));
        endTimeValueView.setText(formatMinute(endMinute));
        setAgendaDropdownText(priorityDropdownView, agendaPriorityLabel(priority));
        setAgendaDropdownText(repeatDropdownView, agendaRepeatLabel(repeatRule));
        monthlyValueView.setText(agendaMonthlyLabel(monthlyStrategy));
        locationValueView.setText(formatAgendaLocationButtonText(locationText));
        monthlyContainer.setVisibility(Agenda.REPEAT_MONTHLY.equals(repeatRule) ? View.VISIBLE : View.GONE);
    }

    private void setAgendaDropdownText(@Nullable MaterialAutoCompleteTextView dropdown, @NonNull String text) {
        if (dropdown == null) {
            return;
        }
        if (!TextUtils.equals(dropdown.getText(), text)) {
            dropdown.setText(text, false);
        }
        dropdown.post(() -> adjustAgendaDropdownCapsuleWidth(dropdown));
    }

    private void adjustAgendaDropdownCapsuleWidth(@Nullable MaterialAutoCompleteTextView dropdown) {
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

    private void showAgendaDatePickerDialog(@NonNull Calendar initialDate, @NonNull OnCalendarPicked callback) {
        Calendar seed = cloneAsDay(initialDate);
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
            callback.onPicked(cloneAsDay(picked));
        });
        picker.show(getSupportFragmentManager(), "agenda_date_picker_" + System.currentTimeMillis());
    }

    private void showAgendaTimePickerDialog(int currentMinute, @NonNull OnMinutePicked callback) {
        int hour = Math.max(0, Math.min(23, currentMinute / 60));
        int minute = Math.max(0, Math.min(59, currentMinute % 60));
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTitleText("选择时间")
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(hour)
                .setMinute(minute)
                .build();
        picker.addOnPositiveButtonClickListener(v -> callback.onPicked(picker.getHour() * 60 + picker.getMinute()));
        picker.show(getSupportFragmentManager(), "agenda_time_picker_" + System.currentTimeMillis());
    }

    private int normalizeAgendaPriorityValue(int priority) {
        if (priority < Agenda.PRIORITY_LOW || priority > Agenda.PRIORITY_HIGH) {
            return Agenda.PRIORITY_LOW;
        }
        return priority;
    }

    @NonNull
    private String normalizeAgendaRepeat(@Nullable String repeatRule) {
        String rule = safeText(repeatRule).toLowerCase(Locale.ROOT);
        if (Agenda.REPEAT_DAILY.equals(rule) || Agenda.REPEAT_WEEKLY.equals(rule) || Agenda.REPEAT_MONTHLY.equals(rule)) {
            return rule;
        }
        return Agenda.REPEAT_NONE;
    }

    @NonNull
    private String normalizeAgendaMonthlyStrategy(@Nullable String strategy) {
        String value = safeText(strategy).toLowerCase(Locale.ROOT);
        if (Agenda.MONTHLY_MONTH_END.equals(value)) {
            return Agenda.MONTHLY_MONTH_END;
        }
        return Agenda.MONTHLY_SKIP;
    }

    @NonNull
    private String agendaPriorityLabel(int priority) {
        if (priority == Agenda.PRIORITY_HIGH) {
            return "高";
        }
        if (priority == Agenda.PRIORITY_LOW) {
            return "低";
        }
        return "中";
    }

    @NonNull
    private String formatEditableDateLabel(@Nullable Calendar date) {
        if (date == null) {
            return "请选择日期";
        }
        Calendar normalized = cloneAsDay(date);
        int dayIndex = normalized.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
                ? 6
                : Math.max(0, normalized.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY);
        return String.format(Locale.getDefault(), "%04d-%02d-%02d 周%s",
                normalized.get(Calendar.YEAR),
                normalized.get(Calendar.MONTH) + 1,
                normalized.get(Calendar.DAY_OF_MONTH),
                WEEK_DAY_LABELS[dayIndex]);
    }

    @NonNull
    private String agendaRepeatLabel(@Nullable String repeatRule) {
        int index = indexOfString(AGENDA_REPEAT_VALUES, normalizeAgendaRepeat(repeatRule));
        return AGENDA_REPEAT_LABELS[index];
    }

    @NonNull
    private String agendaMonthlyLabel(@Nullable String strategy) {
        int index = indexOfString(AGENDA_MONTHLY_VALUES, normalizeAgendaMonthlyStrategy(strategy));
        return AGENDA_MONTHLY_LABELS[index];
    }

    private int indexOfString(@NonNull String[] array, @Nullable String value) {
        String target = safeText(value);
        for (int i = 0; i < array.length; i++) {
            if (array[i].equals(target)) {
                return i;
            }
        }
        return 0;
    }

    private void applyAgendaEditorBottomSheetStyle(@Nullable BottomSheetDialog dialog, int surfaceColor) {
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

    @NonNull
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

    @NonNull
    private ImageView createAgendaRowIcon(int iconRes) {
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setImageTintList(ColorStateList.valueOf(UiStyleHelper.resolveOnSurfaceVariantColor(this)));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(20), dp(20));
        icon.setLayoutParams(iconLp);
        return icon;
    }

    private void styleAgendaEditorInlineInput(@Nullable EditText input, boolean multiLine) {
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

    @NonNull
    private View createAgendaEditorDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        dividerLp.setMargins(0, 0, 0, 0);
        divider.setLayoutParams(dividerLp);
        return divider;
    }

    @NonNull
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

    @NonNull
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

    @NonNull
    private LinearLayout createAgendaEditorTimeRangeRow(int iconRes, @NonNull String label,
                                                         @NonNull TextView startValueView,
                                                         @NonNull TextView endValueView) {
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

    @NonNull
    private LinearLayout createAgendaEditorSettingRow(int iconRes, @NonNull String label, @NonNull TextView valueView) {
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

    @NonNull
    private LinearLayout createAgendaEditorDropdownRow(int iconRes,
                                                       @NonNull String label,
                                                       @NonNull MaterialAutoCompleteTextView dropdownView) {
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

    @NonNull
    private MaterialAutoCompleteTextView createAgendaDropdownView(@NonNull String[] entries) {
        MaterialAutoCompleteTextView dropdown = new MaterialAutoCompleteTextView(this);
        dropdown.setTag(entries);
        int onSurface = UiStyleHelper.resolveOnSurfaceColor(this);
        int onSurfaceVariant = UiStyleHelper.resolveOnSurfaceVariantColor(this);
        int popupColor = ColorUtils.setAlphaComponent(
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

    @NonNull
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

    private void styleAgendaEditorInput(@Nullable EditText input) {
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

    @NonNull
    private String formatAgendaLocationButtonText(@Nullable String locationText) {
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

    @NonNull
    private String normalizeAgendaLocationInput(@Nullable String rawLocation) {
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

    @NonNull
    private MaterialAlertDialogBuilder newMaterialYouDialogBuilder() {
        return new MaterialAlertDialogBuilder(new androidx.appcompat.view.ContextThemeWrapper(this, com.google.android.material.R.style.Theme_Material3_DayNight_Dialog_Alert));
    }

    private void showAgendaLocationPicker(@Nullable String currentLocation, @Nullable OnAgendaLocationPick callback) {
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

    private void showAgendaCustomLocationInputDialog(@Nullable String currentLocation, @Nullable OnAgendaLocationPick callback) {
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

    private void showRoomNumberInputDialog(@NonNull String buildingName,
                                           @Nullable String currentRoom,
                                           @Nullable OnRoomConfirm callback) {
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

    @NonNull
    private int[] buildColorPalette() {
        return ColorPaletteProvider.vibrantLightPalette();
    }

    @NonNull
    private int[] buildAgendaColorPalette() {
        return buildColorPalette();
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

    private void renderAgendaColorSlider(@Nullable LinearLayout container, @Nullable int[] agendaColorHolder) {
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

    private void addColorDot(@NonNull LinearLayout container,
                             int color,
                             boolean selected,
                             boolean isDefault,
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

    @NonNull
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

    @NonNull
    private MaterialButton createAgendaActionButton(boolean primary) {
        MaterialButton button = createAgendaEditorButton();
        button.setCornerRadius(dp(24));
        button.setMinHeight(dp(52));
        if (primary) {
            int accent = UiStyleHelper.resolveAccentColor(this);
            button.setBackgroundTintList(ColorStateList.valueOf(accent));
            button.setTextColor(pickReadableTextColor(accent));
        }
        return button;
    }

    private int pickReadableTextColor(int backgroundColor) {
        return ColorUtils.calculateLuminance(backgroundColor) < 0.5 ? Color.WHITE : Color.BLACK;
    }

    private interface OnCalendarPicked {
        void onPicked(@NonNull Calendar pickedDate);
    }

    private interface OnMinutePicked {
        void onPicked(int minute);
    }

    private interface OnRoomConfirm {
        void onConfirm(String roomNumber);
    }

    private interface OnAgendaLocationPick {
        void onPick(String location);
    }

    private boolean isSectionCollapsed(int statusType) {
        if (statusType == STATUS_ONGOING) {
            return agendaOngoingCollapsed;
        }
        if (statusType == STATUS_ENDED) {
            return agendaEndedCollapsed;
        }
        return agendaUpcomingCollapsed;
    }

    private void setSectionCollapsed(int statusType, boolean collapsed) {
        if (statusType == STATUS_ONGOING) {
            agendaOngoingCollapsed = collapsed;
            return;
        }
        if (statusType == STATUS_ENDED) {
            agendaEndedCollapsed = collapsed;
            return;
        }
        agendaUpcomingCollapsed = collapsed;
    }

    private int resolveAgendaAccent(@Nullable Agenda agenda) {
        int color = agenda == null ? 0 : agenda.renderColor;
        if (color == 0 || color == Color.TRANSPARENT || color == Color.WHITE || color == Color.BLACK) {
            return UiStyleHelper.resolveAccentColor(this);
        }
        return color;
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

    @NonNull
    private String priorityText(int priority) {
        if (priority == Agenda.PRIORITY_HIGH) {
            return "高";
        }
        if (priority == Agenda.PRIORITY_LOW) {
            return "低";
        }
        return "中";
    }

    @NonNull
    private String formatMinute(int minute) {
        int clamped = Math.max(0, Math.min(24 * 60, minute));
        return String.format(Locale.getDefault(), "%02d:%02d", clamped / 60, clamped % 60);
    }

    @NonNull
    private GradientDrawable makeRoundedSolid(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(radius);
        drawable.setColor(color);
        return drawable;
    }

    @NonNull
    private Calendar cloneAsDay(@NonNull Calendar source) {
        Calendar copy = (Calendar) source.clone();
        copy.set(Calendar.HOUR_OF_DAY, 0);
        copy.set(Calendar.MINUTE, 0);
        copy.set(Calendar.SECOND, 0);
        copy.set(Calendar.MILLISECOND, 0);
        return copy;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }

    @NonNull
    private String safeText(@Nullable String text) {
        return text == null ? "" : text;
    }

    private static final class AgendaOccurrenceItem {
        Agenda agenda;
        Calendar occurrenceDate;
        int status;
    }
}
