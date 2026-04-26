package cn.edu.hut.course;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import cn.edu.hut.course.ai.AiPromptCenter;
import cn.edu.hut.course.data.AgendaStorageManager;

public class AgendaAiComposeBottomSheetFragment extends BottomSheetDialogFragment {

    public static final String TAG = "AgendaAiComposeBottomSheet";
    public static final String REQUEST_KEY = "agenda_ai_compose_result";
    public static final String RESULT_KEY_AGENDA_CHANGED = "result_agenda_changed";
    public static final String RESULT_KEY_PREFERRED_DATE_MILLIS = "result_preferred_date_millis";
    public static final String RESULT_KEY_SOURCE = "result_source";

    private static final String ARG_PREFERRED_DATE_MILLIS = "arg_preferred_date_millis";
    private static final String ARG_SOURCE = "arg_source";
    private static final String STATE_DRAFT = "state_draft";
    private static final String STATE_STATUS = "state_status";
    private static final String STATE_FEEDBACK = "state_feedback";
    private static final int MAX_TOOL_COMMAND_ROUNDS = 20;
    private static final int MAX_TOOL_MODEL_FEEDBACK_CHARS = 2200;
    private static final int FULLSCREEN_PROMPT_CHAR_THRESHOLD = 120;
    private static final int FULLSCREEN_PROMPT_LINE_THRESHOLD = 7;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicInteger activeToken = new AtomicInteger(0);

    private EditText etPrompt;
    private TextView tvStatus;
    private TextView tvFeedback;
    private MaterialButton btnClear;
    private MaterialButton btnSend;
    private View rootView;
    private ScrollView feedbackContainer;
    @Nullable
    private TextWatcher promptWatcher;
    @Nullable
    private FrameLayout sheetContainer;
    @Nullable
    private BottomSheetBehavior<FrameLayout> sheetBehavior;
    private boolean sheetFullscreen = false;

    private boolean requestRunning = false;
    private int basePaddingTop = 0;
    private int basePaddingBottom = 0;
    private String statusText = "";
    private String feedbackText = "";

    @NonNull
    public static AgendaAiComposeBottomSheetFragment newInstance(@Nullable Calendar preferredDate,
                                                                  @Nullable String sourceTag) {
        AgendaAiComposeBottomSheetFragment fragment = new AgendaAiComposeBottomSheetFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_PREFERRED_DATE_MILLIS, normalizeDateMillis(preferredDate));
        args.putString(ARG_SOURCE, safeText(sourceTag));
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_agenda_ai_compose, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rootView = view.findViewById(R.id.rootAgendaAiComposeSheet);
        etPrompt = view.findViewById(R.id.etAgendaAiPrompt);
        tvStatus = view.findViewById(R.id.tvAgendaAiStatus);
        tvFeedback = view.findViewById(R.id.tvAgendaAiFeedbackContent);
        btnClear = view.findViewById(R.id.btnAgendaAiClear);
        btnSend = view.findViewById(R.id.btnAgendaAiSend);
        feedbackContainer = view.findViewById(R.id.scrollAgendaAiFeedback);
        MaterialCardView composerCard = view.findViewById(R.id.cardAgendaAiComposer);

        applyAgendaComposerCardStyle(composerCard);
        styleAgendaPromptInput(etPrompt);

        if (rootView != null) {
            basePaddingTop = rootView.getPaddingTop();
            basePaddingBottom = rootView.getPaddingBottom();
        }

        restoreState(savedInstanceState);
        applyInsetsHandling();
        bindActions();
        bindPromptWatcher();
    }

    @Override
    public void onDestroyView() {
        if (etPrompt != null && promptWatcher != null) {
            etPrompt.removeTextChangedListener(promptWatcher);
        }
        promptWatcher = null;
        sheetContainer = null;
        sheetBehavior = null;
        rootView = null;
        etPrompt = null;
        tvStatus = null;
        tvFeedback = null;
        btnClear = null;
        btnSend = null;
        feedbackContainer = null;
        super.onDestroyView();
    }

    @Override
    public void onStart() {
        super.onStart();
        configureSheetAppearance();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_DRAFT, safeText(etPrompt == null || etPrompt.getText() == null ? "" : etPrompt.getText().toString()));
        outState.putString(STATE_STATUS, statusText);
        outState.putString(STATE_FEEDBACK, feedbackText);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        worker.shutdownNow();
    }

    private void restoreState(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            statusText = safeText(savedInstanceState.getString(STATE_STATUS));
            feedbackText = safeText(savedInstanceState.getString(STATE_FEEDBACK));
            String draft = safeText(savedInstanceState.getString(STATE_DRAFT));
            if (etPrompt != null) {
                etPrompt.setText(draft);
                etPrompt.setSelection(draft.length());
            }
        }
        if (statusText.trim().isEmpty()) {
            statusText = "";
        }
        if (feedbackText.trim().isEmpty()) {
            feedbackText = "";
        }
        updatePanel(statusText, feedbackText);
        setRunning(false);
    }

    private void bindPromptWatcher() {
        if (etPrompt == null) {
            return;
        }
        if (promptWatcher != null) {
            etPrompt.removeTextChangedListener(promptWatcher);
        }
        promptWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (etPrompt == null) {
                    return;
                }
                etPrompt.post(AgendaAiComposeBottomSheetFragment.this::syncSheetModeByPrompt);
            }
        };
        etPrompt.addTextChangedListener(promptWatcher);
        etPrompt.post(this::syncSheetModeByPrompt);
    }

    private void bindActions() {
        if (btnClear != null) {
            btnClear.setOnClickListener(v -> {
                if (etPrompt != null) {
                    etPrompt.setText("");
                    etPrompt.requestFocus();
                }
            });
        }
        if (btnSend != null) {
            btnSend.setOnClickListener(v -> submitPrompt());
        }
    }

    private void submitPrompt() {
        if (requestRunning) {
            return;
        }
        Context context = getContext();
        if (context == null) {
            return;
        }

        String prompt = safeText(etPrompt == null || etPrompt.getText() == null ? "" : etPrompt.getText().toString()).trim();
        if (prompt.isEmpty()) {
            Toast.makeText(context, "请输入日程描述", Toast.LENGTH_SHORT).show();
            return;
        }

        Context appContext = context.getApplicationContext();
        AiConfigStore.AiModelConfig model = resolveModel(appContext);
        if (model == null) {
            updatePanel("发送失败", "尚未配置 AI 模型，请先在 AI 设置中完成配置。");
            return;
        }
        if (safeText(model.apiKey).trim().isEmpty()) {
            updatePanel("发送失败", "模型缺少 API Key，请先到 AI 设置中补充。");
            return;
        }
        if (safeText(model.modelName).trim().isEmpty()) {
            updatePanel("发送失败", "模型缺少模型名，请先到 AI 设置中补充。");
            return;
        }
        if (!AiConfigStore.isSkillEnabled(appContext)) {
            updatePanel("发送失败", "技能开关已关闭，无法自动创建日程。请在大模型设置的模型设置中开启技能后重试。");
            return;
        }
        if (!AiConfigStore.isAgendaSkillEnabled(appContext)) {
            updatePanel("发送失败", "日程技能已关闭，无法自动创建日程。请在大模型设置的技能设置中开启后重试。");
            return;
        }

        int token = activeToken.incrementAndGet();
        long preferredDateMillis = readPreferredDateMillis();
        String sourceTag = readSourceTag();

        setRunning(true);
        updatePanel("正在分析请求...", "正在调用 AI 与日程工具，请稍候。\n\n输入内容：\n" + prompt);

        worker.execute(() -> runAiFlow(token, appContext, model, prompt, preferredDateMillis, sourceTag));
    }

    private void runAiFlow(int token,
                           @NonNull Context context,
                           @NonNull AiConfigStore.AiModelConfig model,
                           @NonNull String rawPrompt,
                           long preferredDateMillis,
                           @NonNull String sourceTag) {
        try {
            boolean skillEnabled = AiConfigStore.isSkillEnabled(context);
            boolean noteSkillEnabled = AiConfigStore.isNoteSkillEnabled(context);
            boolean courseSkillEnabled = AiConfigStore.isCourseSkillEnabled(context);
            boolean navigationSkillEnabled = AiConfigStore.isNavigationSkillEnabled(context);
            boolean classroomSkillEnabled = AiConfigStore.isClassroomSkillEnabled(context);
            boolean agendaSkillEnabled = AiConfigStore.isAgendaSkillEnabled(context);
            boolean webSearchSkillEnabled = AiConfigStore.isWebSearchSkillEnabled(context);
            String promptWithDate = buildPromptWithPreferredDate(rawPrompt, preferredDateMillis);
            String systemPrompt = AiPromptCenter.buildSystemPrompt(
                    skillEnabled,
                    noteSkillEnabled,
                    courseSkillEnabled,
                    navigationSkillEnabled,
                    classroomSkillEnabled,
                    agendaSkillEnabled,
                    webSearchSkillEnabled
            );
            String skillIndex = SkillCommandCenter.buildSkillIndexFromFrontmatter(context);
            String firstPrompt = AiPromptCenter.buildFirstTurnUserPrompt(skillIndex, promptWithDate, true, false);

            postProgress(token, "正在请求模型...", "模型正在理解你的日程描述。");
            String assistantOutput = AiGateway.chat(
                    model.provider,
                    model.baseUrl,
                    model.apiKey,
                    model.modelName,
                    systemPrompt,
                    firstPrompt
            );

            StringBuilder toolFeedbackBuilder = new StringBuilder();
            boolean agendaChanged = false;

            for (int round = 1; round <= MAX_TOOL_COMMAND_ROUNDS; round++) {
                if (token != activeToken.get()) {
                    return;
                }
                List<String> commands = SkillCommandCenter.extractCommands(assistantOutput);
                if (commands.isEmpty()) {
                    String visibleReply = stripLeadingToolFeedbackLines(assistantOutput);
                    String merged = mergeFeedbackAndReply(toolFeedbackBuilder.toString(), visibleReply);
                    postCompleted(token, merged, agendaChanged, preferredDateMillis, sourceTag);
                    return;
                }

                SkillCommandCenter.CommandBatchResult batch = SkillCommandCenter.executeCommandsWithFeedback(context, commands, promptWithDate);
                if (!safeText(batch.userFeedback).trim().isEmpty()) {
                    if (toolFeedbackBuilder.length() > 0) {
                        toolFeedbackBuilder.append("\n\n");
                    }
                    toolFeedbackBuilder.append(batch.userFeedback.trim());
                }
                if (hasAgendaMutation(commands, batch.modelFeedback)) {
                    agendaChanged = true;
                }

                postProgress(token, "正在执行工具调用...", safeText(toolFeedbackBuilder.toString()));

                String nextPrompt = AiPromptCenter.buildToolFollowupPrompt(
                        promptWithDate,
                        assistantOutput,
                        commands,
                        compactToolFeedback(batch.modelFeedback),
                        false
                );
                assistantOutput = AiGateway.chat(
                        model.provider,
                        model.baseUrl,
                        model.apiKey,
                        model.modelName,
                        systemPrompt,
                        nextPrompt
                );
            }

            String visibleReply = stripLeadingToolFeedbackLines(assistantOutput);
            String merged = mergeFeedbackAndReply(
                    toolFeedbackBuilder.toString(),
                    visibleReply + "\n\n已达到工具调用轮次上限，请补充信息后重试。"
            );
            postCompleted(token, merged, agendaChanged, preferredDateMillis, sourceTag);
        } catch (Exception e) {
            String reason = safeText(e.getMessage()).trim();
            if (reason.isEmpty()) {
                reason = "网络或模型服务暂不可用，请稍后重试。";
            }
            postFailed(token, "执行失败", "执行失败：" + reason);
        }
    }

    private void postProgress(int token, @NonNull String status, @NonNull String feedback) {
        mainHandler.post(() -> {
            if (token != activeToken.get() || !isAdded()) {
                return;
            }
            updatePanel(status, feedback.trim().isEmpty() ? "工具调用中..." : feedback);
        });
    }

    private void postCompleted(int token,
                               @NonNull String feedback,
                               boolean agendaChanged,
                               long preferredDateMillis,
                               @NonNull String sourceTag) {
        mainHandler.post(() -> {
            if (token != activeToken.get() || !isAdded()) {
                return;
            }
            setRunning(false);
            updatePanel(agendaChanged ? "已完成并更新日程" : "已完成", feedback);
            if (agendaChanged) {
                Bundle result = new Bundle();
                result.putBoolean(RESULT_KEY_AGENDA_CHANGED, true);
                result.putLong(RESULT_KEY_PREFERRED_DATE_MILLIS, preferredDateMillis);
                result.putString(RESULT_KEY_SOURCE, sourceTag);
                getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
            }
        });
    }

    private void postFailed(int token, @NonNull String status, @NonNull String feedback) {
        mainHandler.post(() -> {
            if (token != activeToken.get() || !isAdded()) {
                return;
            }
            setRunning(false);
            updatePanel(status, feedback);
        });
    }

    private void updatePanel(@NonNull String status, @NonNull String feedback) {
        statusText = status;
        feedbackText = feedback;
        if (tvStatus != null) {
            String displayStatus = safeText(status).trim();
            tvStatus.setText(displayStatus);
            tvStatus.setVisibility(displayStatus.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (tvFeedback != null) {
            String displayFeedback = safeText(feedback).trim();
            tvFeedback.setText(displayFeedback);
            tvFeedback.setVisibility(displayFeedback.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (feedbackContainer != null) {
            String displayFeedback = safeText(feedback).trim();
            feedbackContainer.setVisibility(displayFeedback.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    private void setRunning(boolean running) {
        requestRunning = running;
        if (btnSend != null) {
            btnSend.setEnabled(!running);
            btnSend.setText(running ? "发送中..." : "发送");
        }
        if (btnClear != null) {
            btnClear.setEnabled(!running);
        }
    }

    private void applyAgendaComposerCardStyle(@Nullable MaterialCardView card) {
        if (card == null) {
            return;
        }
        int onSurface = UiStyleHelper.resolveOnSurfaceColor(requireContext());
        int surface = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorSurface, UiStyleHelper.resolveGlassCardColor(requireContext()));
        card.setCardBackgroundColor(surface);
        card.setRadius(dp(24));
        card.setCardElevation(0f);
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(ColorUtils.setAlphaComponent(onSurface, 24));
    }

    private void styleAgendaPromptInput(@Nullable EditText input) {
        if (input == null) {
            return;
        }
        int onSurface = UiStyleHelper.resolveOnSurfaceColor(requireContext());
        int onSurfaceVariant = UiStyleHelper.resolveOnSurfaceVariantColor(requireContext());
        input.setTextColor(onSurface);
        input.setHintTextColor(ColorUtils.setAlphaComponent(onSurfaceVariant, 180));
        input.setTextSize(17f);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setPadding(dp(4), dp(11), dp(4), dp(11));
        input.setMinHeight(dp(132));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setSingleLine(false);
        input.setMinLines(4);
        input.setMaxLines(Integer.MAX_VALUE);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setHorizontallyScrolling(false);
        // 由外层 BottomSheet 处理滚动，避免输入区域在键盘动画中被压缩成单行。
        input.setVerticalScrollBarEnabled(false);
        input.setOverScrollMode(View.OVER_SCROLL_NEVER);
    }

    private void syncSheetModeByPrompt() {
        applySheetMode(shouldUseFullscreenMode());
    }

    private boolean shouldUseFullscreenMode() {
        if (etPrompt == null) {
            return false;
        }
        CharSequence text = etPrompt.getText();
        int length = safeText(text == null ? "" : text.toString()).trim().length();
        int lines = Math.max(1, etPrompt.getLineCount());
        return length >= FULLSCREEN_PROMPT_CHAR_THRESHOLD || lines >= FULLSCREEN_PROMPT_LINE_THRESHOLD;
    }

    private void applySheetMode(boolean fullscreen) {
        if (sheetContainer == null || sheetBehavior == null) {
            return;
        }
        if (sheetFullscreen == fullscreen && sheetContainer.getLayoutParams() != null) {
            int currentHeight = sheetContainer.getLayoutParams().height;
            int targetHeight = fullscreen ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT;
            if (currentHeight == targetHeight) {
                return;
            }
        }

        ViewGroup.LayoutParams sheetLp = sheetContainer.getLayoutParams();
        if (sheetLp != null) {
            sheetLp.height = fullscreen ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT;
            sheetContainer.setLayoutParams(sheetLp);
        }

        sheetBehavior.setFitToContents(!fullscreen);
        sheetBehavior.setSkipCollapsed(true);
        if (sheetBehavior.getState() != BottomSheetBehavior.STATE_EXPANDED) {
            sheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
        sheetFullscreen = fullscreen;
    }

    private void configureSheetAppearance() {
        if (!(getDialog() instanceof BottomSheetDialog)) {
            return;
        }
        BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
        dialog.setDismissWithAnimation(true);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                            | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
            );
        }

        sheetContainer = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheetContainer == null) {
            return;
        }

        sheetBehavior = BottomSheetBehavior.from(sheetContainer);
        applySheetMode(shouldUseFullscreenMode());

        GradientDrawable background = new GradientDrawable();
        float radius = dp(28);
        background.setShape(GradientDrawable.RECTANGLE);
        int sheetSurface = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorSurface, UiStyleHelper.resolvePageBackgroundColor(requireContext()));
        background.setColor(sheetSurface);
        background.setCornerRadii(new float[]{radius, radius, radius, radius, 0f, 0f, 0f, 0f});
        sheetContainer.setBackground(background);

        View parent = (View) sheetContainer.getParent();
        if (parent != null) {
            parent.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    private void applyInsetsHandling() {
        if (rootView == null) {
            return;
        }
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            Insets nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            Insets gesture = insets.getInsets(WindowInsetsCompat.Type.systemGestures());

            int baseBottomInset = Math.max(nav.bottom, gesture.bottom);
            int targetBottom = basePaddingBottom + baseBottomInset;

            if (v.getPaddingBottom() != targetBottom || v.getPaddingTop() != basePaddingTop) {
                v.setPadding(v.getPaddingLeft(), basePaddingTop, v.getPaddingRight(), targetBottom);
            }
            syncSheetModeByPrompt();
            return insets;
        });
        ViewCompat.requestApplyInsets(rootView);
    }

    private void requestPromptFocus() {
        if (etPrompt == null) {
            return;
        }
        etPrompt.post(() -> {
            if (!isAdded() || etPrompt == null) {
                return;
            }
            etPrompt.requestFocus();
            Context context = getContext();
            if (context == null) {
                return;
            }
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(etPrompt, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    @Nullable
    private AiConfigStore.AiModelConfig resolveModel(@NonNull Context context) {
        List<AiConfigStore.AiModelConfig> models = AiConfigStore.getModels(context);
        if (models == null || models.isEmpty()) {
            return null;
        }
        for (AiConfigStore.AiModelConfig one : models) {
            if (one == null) {
                continue;
            }
            if (!safeText(one.modelName).trim().isEmpty() && !safeText(one.apiKey).trim().isEmpty()) {
                return one;
            }
        }
        return models.get(0);
    }

    @NonNull
    private String buildPromptWithPreferredDate(@NonNull String rawPrompt, long preferredDateMillis) {
        if (preferredDateMillis <= 0L) {
            return rawPrompt;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(preferredDateMillis);
        String date = AgendaStorageManager.formatDate(calendar);
        return "默认参考日期：" + date + "（当用户未提供日期时使用）。\n用户请求：" + rawPrompt;
    }

    private boolean hasAgendaMutation(@NonNull List<String> commands, @Nullable String modelFeedback) {
        boolean hasAgendaWriteCommand = false;
        for (String command : commands) {
            String lower = safeText(command).toLowerCase(Locale.ROOT);
            if (lower.startsWith("agenda.create")
                    || lower.startsWith("agenda.update")
                    || lower.startsWith("agenda.delete")) {
                hasAgendaWriteCommand = true;
                break;
            }
        }
        if (!hasAgendaWriteCommand) {
            return false;
        }

        String feedback = safeText(modelFeedback).toLowerCase(Locale.ROOT);
        if (feedback.contains("创建成功") || feedback.contains("更新成功") || feedback.contains("删除成功")) {
            return true;
        }
        if (feedback.contains("已创建日程") || feedback.contains("已更新日程") || feedback.contains("已删除日程")) {
            return true;
        }
        return !feedback.contains("失败");
    }

    @NonNull
    private String compactToolFeedback(@Nullable String modelFeedback) {
        String text = safeText(modelFeedback).trim();
        if (text.length() <= MAX_TOOL_MODEL_FEEDBACK_CHARS) {
            return text;
        }
        return text.substring(0, MAX_TOOL_MODEL_FEEDBACK_CHARS) + "...";
    }

    @NonNull
    private String mergeFeedbackAndReply(@NonNull String toolFeedback, @Nullable String reply) {
        String safeReply = safeText(reply).trim();
        String safeFeedback = safeText(toolFeedback).trim();
        if (safeFeedback.isEmpty()) {
            return safeReply.isEmpty() ? "未获取到可展示结果。" : safeReply;
        }
        if (safeReply.isEmpty()) {
            return safeFeedback;
        }
        return safeFeedback + "\n\nAI 结果：\n" + safeReply;
    }

    @NonNull
    private String stripLeadingToolFeedbackLines(@Nullable String rawReply) {
        String normalized = safeText(rawReply).replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n");

        int start = 0;
        while (start < lines.length) {
            String line = safeText(lines[start]).trim();
            if (line.isEmpty()) {
                start++;
                continue;
            }
            if (isToolFeedbackLine(line)) {
                start++;
                continue;
            }
            break;
        }

        StringBuilder builder = new StringBuilder();
        for (int i = start; i < lines.length; i++) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(lines[i]);
        }
        return builder.toString().trim();
    }

    private boolean isToolFeedbackLine(@NonNull String line) {
        String candidate = safeText(line)
                .replaceFirst("^(?:[-*•]|\\d+[.)、：:]?)\\s*", "")
                .trim();

        return candidate.startsWith("工具执行情况")
                || candidate.startsWith("已查询")
                || candidate.startsWith("已创建日程")
                || candidate.startsWith("已更新日程")
                || candidate.startsWith("已删除日程")
                || candidate.startsWith("创建日程")
                || candidate.startsWith("更新日程")
                || candidate.startsWith("删除日程")
                || candidate.startsWith("创建失败")
                || candidate.startsWith("更新失败")
                || candidate.startsWith("删除失败")
                || candidate.startsWith("查询失败")
                || candidate.startsWith("已完成：")
                || candidate.startsWith("已完成本轮工具调用");
    }

    private long readPreferredDateMillis() {
        Bundle args = getArguments();
        return args == null ? -1L : args.getLong(ARG_PREFERRED_DATE_MILLIS, -1L);
    }

    @NonNull
    private String readSourceTag() {
        Bundle args = getArguments();
        return args == null ? "" : safeText(args.getString(ARG_SOURCE));
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }

    private static long normalizeDateMillis(@Nullable Calendar date) {
        if (date == null) {
            return -1L;
        }
        Calendar clone = (Calendar) date.clone();
        clone.set(Calendar.HOUR_OF_DAY, 0);
        clone.set(Calendar.MINUTE, 0);
        clone.set(Calendar.SECOND, 0);
        clone.set(Calendar.MILLISECOND, 0);
        return clone.getTimeInMillis();
    }

    @NonNull
    private static String safeText(@Nullable String text) {
        return text == null ? "" : text;
    }
}
