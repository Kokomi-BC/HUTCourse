package cn.edu.hut.course;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsAnimationCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.noties.markwon.Markwon;

import cn.edu.hut.course.ai.AiPromptCenter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AiChatFragment extends Fragment {

    private static final String PREF_AI_CHAT_HISTORY = "ai_chat_history";
    private static final String KEY_CHAT_HISTORY_JSON = "history_json";
    private static final int MAX_HISTORY_SESSIONS = 30;
    private static final Pattern TITLE_PATTERN = Pattern.compile("^(?:TITLE|标题)\\s*[:：]\\s*(.+)$", Pattern.CASE_INSENSITIVE);

    private DrawerLayout drawerAiChat;
    private LinearLayout chatContainer;
    private ScrollView chatScroll;
    private EditText etPrompt;
    private ImageButton btnSend;
    private ImageButton btnOpenHistory;
    private ImageButton btnNewSession;
    private ListView lvHistory;
    private LinearLayout historyDrawer;
    private TextView tvHistoryDrawerTitle;
    private TextView tvHistoryEmpty;
    private TextView tvHistoryMeta;
    private MaterialCardView composerCard;
    private LinearLayout composerInner;
    private TextView tvAiStatus;
    private Markwon markwon;
    private final Handler streamHandler = new Handler(Looper.getMainLooper());
    private final List<ChatSession> sessions = new ArrayList<>();
    private final List<HistoryRow> historyRows = new ArrayList<>();
    private HistorySessionAdapter historyAdapter;
    private ChatSession activeSession;
    private int highlightedHistoryPosition = -1;
    private TextView streamingBubble;
    private int baseComposerBottomMargin = 0;
    private int baseChatScrollBottomPadding = 0;
    private boolean keepChatAnchoredToBottomOnIme = false;
    private boolean pendingBottomScrollOnImeOpen = false;
    private boolean lastImeVisible = false;
    private boolean imeAnimationRunning = false;
    private int originalSoftInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED;
    private boolean hasSavedSoftInputMode = false;
    private View mainBottomNavHost;
    private int mainBottomNavHostHeight = 0;
    private View outsideTapCloseTarget;
    private View bottomNavTapCloseTarget;
    private final Object aiRequestLock = new Object();
    private long nextAiRequestToken = 1L;
    private long activeAiRequestToken = 0L;
    private boolean aiConversationRunning = false;
    private boolean aiStopRequested = false;
    @Nullable
    private Thread activeAiWorkerThread;
    @Nullable
    private Runnable activeStreamTicker;
    private View rootView;
    private PopupWindow sessionMenuPopup;
    private float lastTouchX = 0;
    private float lastTouchY = 0;

    public AiChatFragment() {
        super();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.activity_ai_chat, container, false);
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        applyPageVisualStyle();

        drawerAiChat = view.findViewById(R.id.drawerAiChat);
        chatContainer = view.findViewById(R.id.chatContainer);
        chatScroll = view.findViewById(R.id.chatScroll);
        etPrompt = view.findViewById(R.id.etPrompt);
        btnSend = view.findViewById(R.id.btnSend);
        btnOpenHistory = view.findViewById(R.id.btnOpenHistory);
        btnNewSession = view.findViewById(R.id.btnNewSession);
        lvHistory = view.findViewById(R.id.lvHistory);
        historyDrawer = view.findViewById(R.id.historyDrawer);
        tvHistoryDrawerTitle = view.findViewById(R.id.tvHistoryDrawerTitle);
        tvHistoryEmpty = view.findViewById(R.id.tvHistoryEmpty);
        tvHistoryMeta = view.findViewById(R.id.tvHistoryMeta);
        composerCard = view.findViewById(R.id.composerCard);
        composerInner = view.findViewById(R.id.composerInner);
        tvAiStatus = view.findViewById(R.id.tvAiStatus);
        markwon = Markwon.create(requireContext());

        if (composerCard != null) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) composerCard.getLayoutParams();
            baseComposerBottomMargin = lp.bottomMargin;
        }
        if (chatScroll != null) {
            baseChatScrollBottomPadding = chatScroll.getPaddingBottom();
        }

        captureMainBottomNavHost();
        configureSoftInputModeForChat();
        configurePromptInput();
        setupHistoryDrawer();
        loadHistory();
        ensureFreshSessionOnEnter();
        ensureGreetingForActiveSession();
        applyImeInsetBehavior();
        setupDismissHistoryDrawerOnOutsideTap();
        applyComposerStyle();
        refreshAiStatus();

        if (btnSend != null) {
            btnSend.setOnClickListener(v -> {
                if (isAiConversationRunning()) {
                    stopCurrentAiConversation(true);
                } else {
                    sendMessage();
                }
            });
            updateSendButtonMode(false);
        }
        if (btnOpenHistory != null) {
            btnOpenHistory.setOnClickListener(v -> {
                if (drawerAiChat != null) {
                    drawerAiChat.openDrawer(GravityCompat.START);
                }
            });
        }
        if (btnNewSession != null) {
            btnNewSession.setOnClickListener(v -> {
                startNewSession(true);
                ensureGreetingForActiveSession();
                if (drawerAiChat != null) {
                    drawerAiChat.closeDrawer(GravityCompat.START);
                }
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        captureMainBottomNavHost();
        setupDismissHistoryDrawerOnOutsideTap();
        configureSoftInputModeForChat();
        applyPageVisualStyle();
        applyComposerStyle();
        refreshAiStatus();
        View root = rootView == null ? null : rootView.findViewById(R.id.rootAiChat);
        if (root != null) {
            ViewCompat.requestApplyInsets(root);
        }
    }

    @Override
    public void onDestroyView() {
        if (sessionMenuPopup != null) {
            sessionMenuPopup.dismiss();
            sessionMenuPopup = null;
        }
        stopCurrentAiConversation(false);
        View root = rootView == null ? null : rootView.findViewById(R.id.rootAiChat);
        if (root != null) {
            ViewCompat.setOnApplyWindowInsetsListener(root, null);
            ViewCompat.setWindowInsetsAnimationCallback(root, null);
        }
        restoreSoftInputModeIfNeeded();
        ensureMainBottomNavVisible();
        mainBottomNavHost = null;
        clearDismissHistoryDrawerOnOutsideTap();
        if (composerCard != null) {
            composerCard.animate().cancel();
            composerCard.setTranslationY(0f);
        }
        super.onDestroyView();
    }

    private Context ctx() {
        return requireContext();
    }

    private void configurePromptInput() {
        if (etPrompt == null) return;
        etPrompt.setMinLines(1);
        etPrompt.setMaxLines(8);
        etPrompt.setHorizontallyScrolling(false);
        etPrompt.setOverScrollMode(View.OVER_SCROLL_NEVER);
        etPrompt.setOnTouchListener((v, event) -> {
            if (event != null && event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                prepareForImeTransition();
            }
            return false;
        });
        etPrompt.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                prepareForImeTransition();
            }
        });
    }

    private void prepareForImeTransition() {
        keepChatAnchoredToBottomOnIme = isChatNearBottom();
        pendingBottomScrollOnImeOpen = keepChatAnchoredToBottomOnIme;
    }

    private void configureSoftInputModeForChat() {
        if (getActivity() == null || getActivity().getWindow() == null) {
            return;
        }
        WindowManager.LayoutParams attrs = getActivity().getWindow().getAttributes();
        if (!hasSavedSoftInputMode) {
            originalSoftInputMode = attrs.softInputMode;
            hasSavedSoftInputMode = true;
        }
        int stateFlags = originalSoftInputMode & WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE;
        getActivity().getWindow().setSoftInputMode(stateFlags | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
    }

    private void restoreSoftInputModeIfNeeded() {
        if (!hasSavedSoftInputMode || getActivity() == null || getActivity().getWindow() == null) {
            return;
        }
        getActivity().getWindow().setSoftInputMode(originalSoftInputMode);
        hasSavedSoftInputMode = false;
    }

    private void setupDismissHistoryDrawerOnOutsideTap() {
        View root = rootView == null ? null : rootView.findViewById(R.id.rootAiChat);
        if (outsideTapCloseTarget != null && outsideTapCloseTarget != root) {
            outsideTapCloseTarget.setOnTouchListener(null);
        }
        outsideTapCloseTarget = root;
        if (outsideTapCloseTarget != null) {
            outsideTapCloseTarget.setOnTouchListener((v, event) -> {
                maybeCloseHistoryDrawerOnTap(event);
                return false;
            });
        }

        View bottomNav = mainBottomNavHost;
        if (bottomNav == null && getActivity() != null) {
            bottomNav = getActivity().findViewById(R.id.bottomNav);
        }
        if (bottomNavTapCloseTarget != null && bottomNavTapCloseTarget != bottomNav) {
            bottomNavTapCloseTarget.setOnTouchListener(null);
        }
        bottomNavTapCloseTarget = bottomNav;
        if (bottomNavTapCloseTarget != null) {
            bottomNavTapCloseTarget.setOnTouchListener((v, event) -> {
                maybeCloseHistoryDrawerOnTap(event);
                return false;
            });
        }
    }

    private void clearDismissHistoryDrawerOnOutsideTap() {
        if (outsideTapCloseTarget != null) {
            outsideTapCloseTarget.setOnTouchListener(null);
            outsideTapCloseTarget = null;
        }
        if (bottomNavTapCloseTarget != null) {
            bottomNavTapCloseTarget.setOnTouchListener(null);
            bottomNavTapCloseTarget = null;
        }
    }

    private void maybeCloseHistoryDrawerOnTap(@Nullable android.view.MotionEvent event) {
        if (event == null || event.getActionMasked() != android.view.MotionEvent.ACTION_DOWN) {
            return;
        }
        if (drawerAiChat != null && drawerAiChat.isDrawerOpen(GravityCompat.START)) {
            drawerAiChat.closeDrawer(GravityCompat.START);
        }
    }

    private boolean isAiConversationRunning() {
        synchronized (aiRequestLock) {
            return aiConversationRunning && activeAiRequestToken != 0L;
        }
    }

    private long beginAiConversationRequest() {
        synchronized (aiRequestLock) {
            long token = nextAiRequestToken++;
            activeAiRequestToken = token;
            aiConversationRunning = true;
            aiStopRequested = false;
            activeAiWorkerThread = null;
            return token;
        }
    }

    private void bindAiWorkerThread(long token, @NonNull Thread worker) {
        synchronized (aiRequestLock) {
            if (aiConversationRunning && activeAiRequestToken == token) {
                activeAiWorkerThread = worker;
            }
        }
    }

    private boolean isAiRequestActive(long token) {
        synchronized (aiRequestLock) {
            return aiConversationRunning && !aiStopRequested && activeAiRequestToken == token;
        }
    }

    private void finishAiConversationRequest(long token) {
        boolean shouldReset = false;
        synchronized (aiRequestLock) {
            if (activeAiRequestToken == token) {
                activeAiRequestToken = 0L;
                aiConversationRunning = false;
                aiStopRequested = false;
                activeAiWorkerThread = null;
                shouldReset = true;
            }
        }
        if (shouldReset) {
            updateSendButtonMode(false);
        }
    }

    private void stopCurrentAiConversation(boolean showNotice) {
        Thread workerToInterrupt;
        synchronized (aiRequestLock) {
            if (!aiConversationRunning || activeAiRequestToken == 0L) {
                return;
            }
            aiStopRequested = true;
            aiConversationRunning = false;
            activeAiRequestToken = 0L;
            workerToInterrupt = activeAiWorkerThread;
            activeAiWorkerThread = null;
        }

        if (workerToInterrupt != null) {
            workerToInterrupt.interrupt();
        }
        if (activeStreamTicker != null) {
            streamHandler.removeCallbacks(activeStreamTicker);
            activeStreamTicker = null;
        }
        removeTypingBubble();
        if (streamingBubble != null && chatContainer != null) {
            chatContainer.removeView(streamingBubble);
            streamingBubble = null;
        }
        if (showNotice) {
            addSystemMessage("已终止对话");
        }
        updateSendButtonMode(false);
    }

    private void updateSendButtonMode(boolean stopMode) {
        if (btnSend == null) {
            return;
        }
        btnSend.setEnabled(true);
        btnSend.setImageResource(stopMode ? R.drawable.ic_stop_solid : R.drawable.ic_send_up);
        btnSend.setContentDescription(stopMode ? "终止对话" : "发送");
    }

    private void captureMainBottomNavHost() {
        mainBottomNavHost = null;
        if (getActivity() == null) {
            return;
        }
        View bottomNav = getActivity().findViewById(R.id.bottomNav);
        if (bottomNav != null && bottomNav.getParent() instanceof View) {
            mainBottomNavHost = (View) bottomNav.getParent();
            if (mainBottomNavHost.getHeight() > 0) {
                mainBottomNavHostHeight = mainBottomNavHost.getHeight();
            } else if (mainBottomNavHost.getLayoutParams() != null && mainBottomNavHost.getLayoutParams().height > 0) {
                mainBottomNavHostHeight = mainBottomNavHost.getLayoutParams().height;
            }
        } else {
            mainBottomNavHostHeight = 0;
        }
        ensureMainBottomNavVisible();
    }

    private void ensureMainBottomNavVisible() {
        if (mainBottomNavHost == null) {
            return;
        }
        if (mainBottomNavHost.getVisibility() != View.VISIBLE) {
            mainBottomNavHost.setVisibility(View.VISIBLE);
        }
    }

    private int computeComposerLiftForIme(int imeBottom) {
        int keyboard = Math.max(0, imeBottom);
        if (keyboard == 0) {
            return 0;
        }
        int startThreshold = baseComposerBottomMargin + Math.max(0, mainBottomNavHostHeight);
        return Math.max(0, keyboard - startThreshold + dp(6));
    }

    private void applyImeInsetBehavior() {
        View root = rootView == null ? null : rootView.findViewById(R.id.rootAiChat);
        if (root == null) return;

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            boolean imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            Insets statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            Insets navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            int imeBottom = Math.max(0, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom);
            int targetBottomPadding = navBars.bottom;

            if (root.getPaddingTop() != statusBars.top || root.getPaddingBottom() != targetBottomPadding) {
                root.setPadding(
                        root.getPaddingLeft(),
                        statusBars.top,
                        root.getPaddingRight(),
                        targetBottomPadding
                );
            }

            if (!imeAnimationRunning) {
                int lift = imeVisible ? computeComposerLiftForIme(imeBottom) : 0;
                applyComposerImeOffset(lift);
                applyChatScrollBottomPadding(lift);
                anchorChatToBottomIfNeeded();
            }

            if (imeVisible && !lastImeVisible) {
                if (pendingBottomScrollOnImeOpen && chatScroll != null) {
                    chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
                }
                pendingBottomScrollOnImeOpen = false;
            } else if (!imeVisible && lastImeVisible) {
                keepChatAnchoredToBottomOnIme = false;
                pendingBottomScrollOnImeOpen = false;
            }
            lastImeVisible = imeVisible;

            return insets;
        });

        ViewCompat.setWindowInsetsAnimationCallback(root,
                new WindowInsetsAnimationCompat.Callback(WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                    @Override
                    public void onPrepare(@NonNull WindowInsetsAnimationCompat animation) {
                        if ((animation.getTypeMask() & WindowInsetsCompat.Type.ime()) != 0) {
                            imeAnimationRunning = true;
                        }
                    }

                    @NonNull
                    @Override
                    public WindowInsetsCompat onProgress(
                            @NonNull WindowInsetsCompat insets,
                            @NonNull List<WindowInsetsAnimationCompat> runningAnimations
                    ) {
                        boolean hasImeAnimationRunning = false;
                        for (WindowInsetsAnimationCompat animation : runningAnimations) {
                            if ((animation.getTypeMask() & WindowInsetsCompat.Type.ime()) != 0) {
                                hasImeAnimationRunning = true;
                                break;
                            }
                        }
                        if (hasImeAnimationRunning) {
                            int imeBottom = Math.max(0, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom);
                            int lift = computeComposerLiftForIme(imeBottom);
                            applyComposerImeOffset(lift);
                            applyChatScrollBottomPadding(lift);
                            anchorChatToBottomIfNeeded();
                        }
                        return insets;
                    }

                    @Override
                    public void onEnd(@NonNull WindowInsetsAnimationCompat animation) {
                        if ((animation.getTypeMask() & WindowInsetsCompat.Type.ime()) != 0) {
                            WindowInsetsCompat currentInsets = ViewCompat.getRootWindowInsets(root);
                            boolean imeVisibleNow = currentInsets != null
                                    && currentInsets.isVisible(WindowInsetsCompat.Type.ime());
                            int imeBottomNow = currentInsets == null
                                    ? 0
                                    : Math.max(0, currentInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom);
                            imeAnimationRunning = false;
                            int lift = imeVisibleNow ? computeComposerLiftForIme(imeBottomNow) : 0;
                            applyComposerImeOffset(lift);
                            applyChatScrollBottomPadding(lift);
                            anchorChatToBottomIfNeeded();
                            ViewCompat.requestApplyInsets(root);
                        }
                    }
                });

        ViewCompat.requestApplyInsets(root);
    }

    private void applyComposerImeOffset(int imeBottom) {
        if (composerCard == null) {
            return;
        }
        float targetTranslation = -Math.max(0, imeBottom);
        if (composerCard.getTranslationY() != targetTranslation) {
            composerCard.setTranslationY(targetTranslation);
        }
    }

    private void applyChatScrollBottomPadding(int extraBottom) {
        if (chatScroll == null) {
            return;
        }
        int target = baseChatScrollBottomPadding + Math.max(0, extraBottom);
        if (chatScroll.getPaddingBottom() == target) {
            return;
        }
        chatScroll.setPadding(
                chatScroll.getPaddingLeft(),
                chatScroll.getPaddingTop(),
                chatScroll.getPaddingRight(),
                target
        );
    }

    private void anchorChatToBottomIfNeeded() {
        if (!keepChatAnchoredToBottomOnIme || chatScroll == null) {
            return;
        }
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private boolean isChatNearBottom() {
        if (chatScroll == null || chatContainer == null || chatContainer.getChildCount() == 0) {
            return true;
        }
        int contentBottom = chatContainer.getBottom();
        int viewportBottom = chatScroll.getScrollY() + chatScroll.getHeight();
        return contentBottom - viewportBottom <= dp(24);
    }

    private void setupHistoryDrawer() {
        historyAdapter = new HistorySessionAdapter();
        if (lvHistory != null) {
            lvHistory.setAdapter(historyAdapter);
            lvHistory.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {
                ChatSession target = getSessionForRow(position);
                if (target == null) {
                    return;
                }
                clearHistoryHighlight();
                activeSession = target;
                renderActiveSession();
                historyAdapter.notifyDataSetChanged();
                if (drawerAiChat != null) {
                    drawerAiChat.closeDrawer(GravityCompat.START);
                }
            });
            lvHistory.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                }
                return false;
            });
            lvHistory.setOnItemLongClickListener((parent, view, position, id) -> {
                ChatSession target = getSessionForRow(position);
                if (target == null) {
                    return true;
                }
                int sessionIndex = sessions.indexOf(target);
                if (sessionIndex < 0) {
                    return true;
                }
                highlightedHistoryPosition = position;
                if (historyAdapter != null) {
                    historyAdapter.notifyDataSetChanged();
                }
                showSessionActionMenu(view, sessionIndex);
                return true;
            });
        }
    }

    private void startNewSession(boolean forceSave) {
        ChatSession session = new ChatSession();
        session.id = UUID.randomUUID().toString();
        session.title = "新对话";
        session.titleFromAi = false;
        session.updatedAt = System.currentTimeMillis();
        session.messages = new ArrayList<>();
        activeSession = session;
        chatContainer.removeAllViews();
        refreshHistoryRows();
        refreshAiStatus();
        if (forceSave) {
            saveHistory();
        }
    }

    private void ensureFreshSessionOnEnter() {
        if (sessions.isEmpty()) {
            startNewSession(false);
            return;
        }
        ChatSession latest = sessions.get(0);
        if (isUntouchedSession(latest)) {
            activeSession = latest;
            renderActiveSession();
            return;
        }
        startNewSession(true);
    }

    private void ensureGreetingForActiveSession() {
        if (activeSession == null) {
            return;
        }
        if (activeSession.messages == null) {
            activeSession.messages = new ArrayList<>();
        }
        if (activeSession.messages.isEmpty()) {
            addBubble(false, "你可以与我聊聊课程相关的问题", false, true);
        }
    }

    private void renderActiveSession() {
        chatContainer.removeAllViews();
        if (activeSession == null || activeSession.messages == null || activeSession.messages.isEmpty()) {
            refreshAiStatus();
            return;
        }
        for (ChatMessage one : activeSession.messages) {
            boolean isUser = "user".equalsIgnoreCase(one.role);
            if (!isUser && isToolFeedbackMessage(one.content)) {
                addSystemMessage(one.content);
                continue;
            }
            addBubble(isUser, one.content, false, false);
        }
        refreshAiStatus();
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void refreshHistoryRows() {
        rebuildHistoryRows();
        if (historyAdapter != null) {
            historyAdapter.notifyDataSetChanged();
        }
        if (tvHistoryMeta != null) {
            if (historyRows.isEmpty()) {
                tvHistoryMeta.setText("最近会话");
            } else {
                tvHistoryMeta.setText("最近会话 · " + sessions.size() + " 条");
            }
        }
        if (tvHistoryEmpty != null) {
            tvHistoryEmpty.setVisibility(historyRows.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void rebuildHistoryRows() {
        historyRows.clear();
        if (sessions.isEmpty()) {
            return;
        }

        List<ChatSession> sorted = new ArrayList<>(sessions);
        sorted.sort((a, b) -> Long.compare(b.updatedAt, a.updatedAt));

        LocalDate today = LocalDate.now();
        List<ChatSession> todayItems = new ArrayList<>();
        List<ChatSession> within7DaysItems = new ArrayList<>();
        List<ChatSession> within30DaysItems = new ArrayList<>();
        Map<YearMonth, List<ChatSession>> monthGroups = new LinkedHashMap<>();

        for (ChatSession session : sorted) {
            LocalDate sessionDate = Instant.ofEpochMilli(session.updatedAt)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
            long days = ChronoUnit.DAYS.between(sessionDate, today);
            if (days < 0) {
                days = 0;
            }

            if (days == 0) {
                todayItems.add(session);
            } else if (days <= 7) {
                within7DaysItems.add(session);
            } else if (days <= 30) {
                within30DaysItems.add(session);
            } else {
                YearMonth ym = YearMonth.from(sessionDate);
                monthGroups.computeIfAbsent(ym, k -> new ArrayList<>()).add(session);
            }
        }

        appendHistoryGroup("今日", todayItems);
        appendHistoryGroup("7天内", within7DaysItems);
        appendHistoryGroup("30天内", within30DaysItems);

        List<YearMonth> months = new ArrayList<>(monthGroups.keySet());
        months.sort(Comparator.reverseOrder());
        for (YearMonth ym : months) {
            String monthLabel = ym.getYear() + "年" + ym.getMonthValue() + "月";
            appendHistoryGroup(monthLabel, monthGroups.get(ym));
        }
    }

    private void appendHistoryGroup(String header, List<ChatSession> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        historyRows.add(HistoryRow.header(header));
        for (ChatSession one : items) {
            historyRows.add(HistoryRow.session(one));
        }
    }

    @Nullable
    private ChatSession getSessionForRow(int position) {
        if (position < 0 || position >= historyRows.size()) {
            return null;
        }
        HistoryRow row = historyRows.get(position);
        return row.isHeader ? null : row.session;
    }

    private void loadHistory() {
        sessions.clear();
        SharedPreferences prefs = requireContext().getSharedPreferences(PREF_AI_CHAT_HISTORY, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_CHAT_HISTORY_JSON, "");
        if (!TextUtils.isEmpty(raw)) {
            try {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.optJSONObject(i);
                    if (obj == null) continue;
                    ChatSession session = new ChatSession();
                    session.id = obj.optString("id", UUID.randomUUID().toString());
                    session.title = obj.optString("title", "新对话");
                    session.titleFromAi = obj.optBoolean("titleFromAi", !TextUtils.isEmpty(session.title) && !"新对话".equals(session.title));
                    session.updatedAt = obj.optLong("updatedAt", System.currentTimeMillis());
                    session.messages = new ArrayList<>();
                    JSONArray msgArr = obj.optJSONArray("messages");
                    if (msgArr != null) {
                        for (int j = 0; j < msgArr.length(); j++) {
                            JSONObject msgObj = msgArr.optJSONObject(j);
                            if (msgObj == null) continue;
                            ChatMessage msg = new ChatMessage();
                            msg.role = msgObj.optString("role", "assistant");
                            msg.content = msgObj.optString("content", "");
                            if (!TextUtils.isEmpty(msg.content)) {
                                session.messages.add(msg);
                            }
                        }
                    }
                    if (isSessionEligibleForHistory(session)) {
                        sessions.add(session);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (!sessions.isEmpty()) {
            activeSession = sessions.get(0);
            renderActiveSession();
        }
        refreshHistoryRows();
    }

    private void saveHistory() {
        trimHistoryIfNeeded();
        JSONArray arr = new JSONArray();
        for (ChatSession one : sessions) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("id", one.id);
                obj.put("title", safe(one.title));
                obj.put("titleFromAi", one.titleFromAi);
                obj.put("updatedAt", one.updatedAt);
                JSONArray msgArr = new JSONArray();
                if (one.messages != null) {
                    for (ChatMessage msg : one.messages) {
                        JSONObject msgObj = new JSONObject();
                        msgObj.put("role", safe(msg.role));
                        msgObj.put("content", safe(msg.content));
                        msgArr.put(msgObj);
                    }
                }
                obj.put("messages", msgArr);
                arr.put(obj);
            } catch (Exception ignored) {
            }
        }
        requireContext().getSharedPreferences(PREF_AI_CHAT_HISTORY, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CHAT_HISTORY_JSON, arr.toString())
                .apply();
    }

    private void trimHistoryIfNeeded() {
        while (sessions.size() > MAX_HISTORY_SESSIONS) {
            sessions.remove(sessions.size() - 1);
        }
    }

    private void touchActiveSession() {
        if (activeSession == null) {
            return;
        }
        if (!sessions.contains(activeSession)) {
            return;
        }
        activeSession.updatedAt = System.currentTimeMillis();
        sessions.remove(activeSession);
        sessions.add(0, activeSession);
    }

    private void promoteActiveSessionToHistoryIfEligible() {
        if (activeSession == null || !isSessionEligibleForHistory(activeSession)) {
            return;
        }
        sessions.remove(activeSession);
        activeSession.updatedAt = System.currentTimeMillis();
        sessions.add(0, activeSession);
        trimHistoryIfNeeded();
        refreshHistoryRows();
        saveHistory();
    }

    private boolean isSessionEligibleForHistory(ChatSession session) {
        if (session == null) {
            return false;
        }
        String title = safe(session.title).trim();
        if (title.isEmpty() || "新对话".equals(title) || !session.titleFromAi) {
            return false;
        }
        return countNonEmptyMessages(session) >= 2;
    }

    private int countNonEmptyMessages(ChatSession session) {
        if (session == null || session.messages == null) {
            return 0;
        }
        int count = 0;
        for (ChatMessage msg : session.messages) {
            if (msg != null && !TextUtils.isEmpty(msg.content)) {
                count++;
            }
        }
        return count;
    }

    private void appendMessageToHistory(boolean isUser, String text) {
        if (TextUtils.isEmpty(text)) {
            return;
        }
        if (activeSession == null) {
            startNewSession(false);
        }
        ChatMessage msg = new ChatMessage();
        msg.role = isUser ? "user" : "assistant";
        msg.content = text;
        if (activeSession.messages == null) {
            activeSession.messages = new ArrayList<>();
        }
        activeSession.messages.add(msg);
        if (sessions.contains(activeSession)) {
            touchActiveSession();
            refreshHistoryRows();
            saveHistory();
        } else {
            promoteActiveSessionToHistoryIfEligible();
        }
        refreshAiStatus();
    }

    private void updateSessionTitle(String title) {
        if (TextUtils.isEmpty(title)) {
            return;
        }
        if (activeSession == null) {
            startNewSession(false);
        }
        activeSession.title = title.trim();
        activeSession.titleFromAi = true;
        if (sessions.contains(activeSession)) {
            touchActiveSession();
            refreshHistoryRows();
            saveHistory();
        } else {
            promoteActiveSessionToHistoryIfEligible();
        }
        refreshAiStatus();
    }

    private void refreshAiStatus() {
        if (tvAiStatus == null) {
            return;
        }
        String title = "AI 对话";
        if (activeSession != null) {
            String sessionTitle = safe(activeSession.title).trim();
            if (!sessionTitle.isEmpty() && !"新对话".equals(sessionTitle)) {
                title = sessionTitle;
            }
        }
        tvAiStatus.setText(title);
        tvAiStatus.setTextColor(UiStyleHelper.resolveOnSurfaceColor(ctx()));
        tvAiStatus.setVisibility(View.VISIBLE);
    }

    private void applyComposerStyle() {
        int onSurface = UiStyleHelper.resolveOnSurfaceColor(ctx());
        int outline = UiStyleHelper.resolveOutlineColor(ctx());
        int accent = UiStyleHelper.resolveAccentColor(ctx());
        int accentFill = ColorUtils.blendARGB(accent, Color.WHITE, 0.08f);
        int glass = UiStyleHelper.resolveGlassCardColor(ctx());

        if (composerCard != null) {
            composerCard.setCardBackgroundColor(Color.TRANSPARENT);
            composerCard.setStrokeColor(Color.TRANSPARENT);
            composerCard.setStrokeWidth(0);
            composerCard.setClipToOutline(true);
            composerCard.setClipChildren(true);
        }
        if (composerInner != null) {
            GradientDrawable composerBg = new GradientDrawable();
            composerBg.setCornerRadius(dp(26));
            composerBg.setColor(glass);
            composerBg.setStroke(dp(1), outline);
            composerInner.setBackground(composerBg);
            composerInner.setClipToOutline(true);
        }
        if (etPrompt != null) {
            etPrompt.setTextColor(onSurface);
            etPrompt.setHintTextColor(ColorUtils.setAlphaComponent(onSurface, 136));
        }
        if (btnSend != null) {
            btnSend.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentFill));
            btnSend.setImageTintList(android.content.res.ColorStateList.valueOf(pickReadableTextColor(accentFill)));
            updateSendButtonMode(isAiConversationRunning());
        }
        if (btnOpenHistory != null) {
            btnOpenHistory.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentFill));
            btnOpenHistory.setImageTintList(android.content.res.ColorStateList.valueOf(pickReadableTextColor(accentFill)));
        }
        if (btnNewSession != null) {
            btnNewSession.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentFill));
            btnNewSession.setImageTintList(android.content.res.ColorStateList.valueOf(pickReadableTextColor(accentFill)));
        }
        if (tvHistoryMeta != null) {
            tvHistoryMeta.setTextColor(UiStyleHelper.resolveOnSurfaceVariantColor(ctx()));
        }
        applyHistoryDrawerStyle();
    }

    private void applyHistoryDrawerStyle() {
        if (historyDrawer == null) return;

        boolean dark = isDarkMode();
        int drawerBase = dark ? Color.BLACK : Color.WHITE;
        int textPrimary = dark ? Color.WHITE : Color.BLACK;
        int textSecondary = ColorUtils.setAlphaComponent(textPrimary, 168);
        int stroke = ColorUtils.setAlphaComponent(textPrimary, 44);

        GradientDrawable drawerBg = new GradientDrawable();
        drawerBg.setShape(GradientDrawable.RECTANGLE);
        drawerBg.setColor(drawerBase);
        drawerBg.setCornerRadii(new float[]{0, 0, dp(24), dp(24), dp(24), dp(24), 0, 0});
        drawerBg.setStroke(dp(1), stroke);
        historyDrawer.setBackground(drawerBg);

        if (drawerAiChat != null) {
            drawerAiChat.setScrimColor(ColorUtils.setAlphaComponent(Color.BLACK, dark ? 140 : 110));
        }
        if (tvHistoryDrawerTitle != null) {
            tvHistoryDrawerTitle.setTextColor(textPrimary);
        }
        if (tvHistoryMeta != null) {
            tvHistoryMeta.setTextColor(textSecondary);
        }
        if (tvHistoryEmpty != null) {
            tvHistoryEmpty.setTextColor(textSecondary);
        }
    }

    private boolean isDarkMode() {
        int mode = ctx().getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private void showSessionActionMenu(View anchor, int position) {
        if (anchor == null || position < 0 || position >= sessions.size()) return;

        dismissSessionMenu();

        Context context = ctx();
        boolean dark = isDarkMode();
        int bgColor = dark ? Color.parseColor("#2C2C2C") : Color.WHITE;
        int textPrimary = dark ? Color.WHITE : Color.BLACK;
        int textDanger = Color.parseColor("#E84B4B");

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        
        MaterialCardView card = new MaterialCardView(context);
        card.setCardElevation(dp(4));
        card.setRadius(dp(8));
        card.setCardBackgroundColor(bgColor);
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(ColorUtils.setAlphaComponent(textPrimary, 20));
        card.addView(container);

        int itemHeight = dp(44);
        int paddingHoriz = dp(16);
        int textSize = 14;

        // Rename
        TextView actionRename = new TextView(context);
        actionRename.setLayoutParams(new LinearLayout.LayoutParams(dp(120), itemHeight));
        actionRename.setGravity(Gravity.CENTER_VERTICAL);
        actionRename.setPadding(paddingHoriz, 0, paddingHoriz, 0);
        actionRename.setText("重命名");
        actionRename.setTextColor(textPrimary);
        actionRename.setTextSize(textSize);
        actionRename.setClickable(true);
        actionRename.setFocusable(true);
        TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        actionRename.setBackgroundResource(outValue.resourceId);
        actionRename.setOnClickListener(v -> {
            dismissSessionMenu();
            showRenameDialog(position);
        });
        container.addView(actionRename);

        // Divider
        View divider = new View(context);
        divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        divider.setBackgroundColor(ColorUtils.setAlphaComponent(textPrimary, 20));
        container.addView(divider);

        // Delete
        TextView actionDelete = new TextView(context);
        actionDelete.setLayoutParams(new LinearLayout.LayoutParams(dp(120), itemHeight));
        actionDelete.setGravity(Gravity.CENTER_VERTICAL);
        actionDelete.setPadding(paddingHoriz, 0, paddingHoriz, 0);
        actionDelete.setText("删除");
        actionDelete.setTextColor(textDanger);
        actionDelete.setTextSize(textSize);
        actionDelete.setClickable(true);
        actionDelete.setFocusable(true);
        actionDelete.setBackgroundResource(outValue.resourceId);
        actionDelete.setOnClickListener(v -> {
            dismissSessionMenu();
            confirmDeleteDialog(position);
        });
        container.addView(actionDelete);

        sessionMenuPopup = new PopupWindow(card,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        sessionMenuPopup.setOutsideTouchable(true);
        sessionMenuPopup.setElevation(dp(8));
        sessionMenuPopup.setOnDismissListener(() -> {
            sessionMenuPopup = null;
            clearHistoryHighlight();
        });

        int[] loc = new int[2];
        if (lvHistory != null) {
            lvHistory.getLocationInWindow(loc);
            int x = loc[0] + (int) lastTouchX;
            int y = loc[1] + (int) lastTouchY;
            sessionMenuPopup.showAtLocation(lvHistory, Gravity.NO_GRAVITY, x, y);
        } else {
            sessionMenuPopup.showAsDropDown(anchor, 0, 0);
        }
    }

    private void dismissSessionMenu() {
        if (sessionMenuPopup != null) {
            sessionMenuPopup.setOnDismissListener(null);
            sessionMenuPopup.dismiss();
            sessionMenuPopup = null;
        }
        clearHistoryHighlight();
    }

    private void clearHistoryHighlight() {
        if (highlightedHistoryPosition != -1) {
            highlightedHistoryPosition = -1;
            if (historyAdapter != null) {
                historyAdapter.notifyDataSetChanged();
            }
        }
    }

    private void showRenameDialog(int position) {
        if (position < 0 || position >= sessions.size()) return;
        ChatSession session = sessions.get(position);

        EditText input = new EditText(ctx());
        input.setText(safe(session.title));
        input.setHint("请输入对话名称");
        input.setSelection(input.getText() == null ? 0 : input.getText().length());

        new MaterialAlertDialogBuilder(ctx())
                .setTitle("重命名对话")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    String newTitle = input.getText() == null ? "" : input.getText().toString().trim();
                    if (newTitle.isEmpty()) {
                        return;
                    }
                    session.title = newTitle;
                    session.updatedAt = System.currentTimeMillis();
                    touchActiveSessionIfNeeded(session);
                    refreshHistoryRows();
                    saveHistory();
                })
                .show();
    }

    private void confirmDeleteDialog(int position) {
        if (position < 0 || position >= sessions.size()) return;
        new MaterialAlertDialogBuilder(ctx())
                .setTitle("删除对话")
                .setMessage("删除后不可恢复，确认删除？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> deleteSessionAt(position))
                .show();
    }

    private void pinSessionToTop(int position) {
        if (position < 0 || position >= sessions.size()) return;
        ChatSession session = sessions.remove(position);
        sessions.add(0, session);
        if (session == activeSession) {
            activeSession = sessions.get(0);
        }
        refreshHistoryRows();
        saveHistory();
    }

    private void deleteSessionAt(int position) {
        if (position < 0 || position >= sessions.size()) return;
        ChatSession removed = sessions.remove(position);

        if (sessions.isEmpty()) {
            activeSession = null;
            startNewSession(false);
            ensureGreetingForActiveSession();
        } else if (removed == activeSession) {
            activeSession = sessions.get(0);
            renderActiveSession();
        }

        refreshHistoryRows();
        saveHistory();
    }

    private void touchActiveSessionIfNeeded(ChatSession session) {
        if (session == null) return;
        if (session == activeSession) {
            touchActiveSession();
            return;
        }
        sessions.remove(session);
        sessions.add(0, session);
    }

    private void sendMessage() {
        if (isAiConversationRunning()) {
            return;
        }

        String userText = etPrompt.getText() == null ? "" : etPrompt.getText().toString().trim();
        if (userText.isEmpty()) {
            return;
        }

        final boolean requestTitleInFinalAnswer = shouldRequestModelTitleForCurrentTurn();

        addBubble(true, userText, false);
        etPrompt.setText("");

        if ("/notes".equalsIgnoreCase(userText)) {
            addBubble(false, NoteSkillManager.readNotes(ctx()), false);
            return;
        }
        if (userText.startsWith("/note ")) {
            String result = NoteSkillManager.appendNote(ctx(), userText.substring(6));
            addBubble(false, result, false);
            return;
        }
        if (userText.startsWith("/note-del ")) {
            String arg = userText.substring(10).trim();
            String result;
            if (arg.matches("\\d+")) {
                result = NoteSkillManager.deleteNoteByIndex(ctx(), Integer.parseInt(arg));
            } else {
                result = NoteSkillManager.deleteNoteByKeyword(ctx(), arg);
            }
            addBubble(false, result, false);
            return;
        }
        if (userText.startsWith("/note-edit ")) {
            String[] parts = userText.substring(11).trim().split("\\s+", 2);
            if (parts.length < 2 || !parts[0].matches("\\d+")) {
                addBubble(false, "修改失败：命令格式应为 /note-edit <序号> <内容>", false);
                return;
            }
            int index = Integer.parseInt(parts[0]);
            String result = NoteSkillManager.updateNoteByIndex(ctx(), index, parts[1]);
            addBubble(false, result, false);
            return;
        }
        if ("/note-clear".equalsIgnoreCase(userText)) {
            addBubble(false, NoteSkillManager.clearNotes(ctx()), false);
            return;
        }
        if (userText.startsWith("/cmd ")) {
            String cmd = userText.substring(5).trim();
            SkillCommandCenter.CommandBatchResult one = SkillCommandCenter.executeCommandsWithFeedback(ctx(), java.util.Collections.singletonList(cmd));
            addSystemMessage(one.userFeedback);
            return;
        }

        String apiKey = AiConfigStore.getApiKey(ctx());
        if (apiKey.isEmpty()) {
            addBubble(false, "请先到“设置 -> AI接入”配置API Key。", false);
            return;
        }

        String configuredModel = AiConfigStore.getModel(ctx());
        if (configuredModel.isEmpty()) {
            addBubble(false, "请先到“设置 -> AI接入”填写模型名或接入点 ID。", false);
            return;
        }

        final long requestToken = beginAiConversationRequest();
        updateSendButtonMode(true);
        addBubble(false, "思考中...", true);

        final String provider = AiConfigStore.getProvider(ctx());
        final String baseUrl = AiConfigStore.getBaseUrl(ctx());
        final String model = configuredModel;
        final String selectedText = getArguments() == null ? null : getArguments().getString("selected_text");

        Thread worker = new Thread(() -> {
            String reply = "";
            boolean cancelled = false;
            try {
                ensureAiRequestActiveOrThrow(requestToken);
                reply = runModelWithSkillCommands(
                        provider,
                        baseUrl,
                        apiKey,
                        model,
                        userText,
                        selectedText,
                        requestTitleInFinalAnswer,
                        requestToken
                );
                ensureAiRequestActiveOrThrow(requestToken);
            } catch (InterruptedException stopEx) {
                cancelled = true;
            } catch (Exception e) {
                if (isAiRequestActive(requestToken)) {
                    reply = "请求失败：" + e.getMessage();
                } else {
                    cancelled = true;
                }
            }

            if (!isAiRequestActive(requestToken)) {
                return;
            }

            final String finalReply = reply;
            final boolean finalCancelled = cancelled;
            if (!isAdded()) {
                if (!finalCancelled) {
                    finishAiConversationRequest(requestToken);
                }
                return;
            }
            requireActivity().runOnUiThread(() -> {
                if (finalCancelled || !isAiRequestActive(requestToken)) {
                    return;
                }
                removeTypingBubble();
                ReplyWithTitle parsed = extractTitle(finalReply);
                if (requestTitleInFinalAnswer && !TextUtils.isEmpty(parsed.title)) {
                    updateSessionTitle(parsed.title);
                }
                String visibleReplyRaw = TextUtils.isEmpty(parsed.title) ? finalReply : parsed.replyBody;
                String visibleReply = stripLeadingToolFeedbackLines(visibleReplyRaw);
                if (TextUtils.isEmpty(visibleReply.trim())) {
                    finishAiConversationRequest(requestToken);
                    return;
                }
                streamAssistantReply(visibleReply, requestToken, () -> {
                    if (!isAiRequestActive(requestToken)) {
                        return;
                    }
                    appendMessageToHistory(false, visibleReply);
                    finishAiConversationRequest(requestToken);
                });
            });
        }, "ai-chat-reply-" + requestToken);
        bindAiWorkerThread(requestToken, worker);
        worker.start();
    }

    private void ensureAiRequestActiveOrThrow(long requestToken) throws InterruptedException {
        if (!isAiRequestActive(requestToken) || Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("AI对话已终止");
        }
    }

    private String runModelWithSkillCommands(String provider, String baseUrl, String apiKey, String model,
                                             String userText, String selectedText,
                                             boolean requestTitleInFinalAnswer,
                                             long requestToken) throws Exception {
        String skillIndex = SkillCommandCenter.buildSkillIndexFromFrontmatter(ctx());
        String systemPrompt = AiPromptCenter.buildSystemPrompt();
        boolean includeCurrentTime = true;
        String firstTurnPrompt = AiPromptCenter.buildFirstTurnUserPrompt(
                skillIndex,
                selectedText,
                userText,
                includeCurrentTime,
                requestTitleInFinalAnswer
        );

        ensureAiRequestActiveOrThrow(requestToken);
        String assistantOutput = AiGateway.chat(provider, baseUrl, apiKey, model, systemPrompt, firstTurnPrompt);
        ensureAiRequestActiveOrThrow(requestToken);

        for (int round = 1; round <= 10; round++) {
            ensureAiRequestActiveOrThrow(requestToken);
            List<String> commands = SkillCommandCenter.extractCommands(assistantOutput);
            if (commands.isEmpty()) {
                return assistantOutput;
            }

            SkillCommandCenter.CommandBatchResult batch = SkillCommandCenter.executeCommandsWithFeedback(ctx(), commands);
            String commandResult = batch.modelFeedback;
            String roundMessage = batch.userFeedback;
            ensureAiRequestActiveOrThrow(requestToken);
            if (isAdded() && isAiRequestActive(requestToken)) {
                requireActivity().runOnUiThread(() -> {
                    if (isAiRequestActive(requestToken)) {
                        addSystemMessage(roundMessage);
                    }
                });
            }

                String nextPrompt = AiPromptCenter.buildToolFollowupPrompt(
                    userText,
                    assistantOutput,
                    commands,
                    commandResult,
                    requestTitleInFinalAnswer
                );
            ensureAiRequestActiveOrThrow(requestToken);
            assistantOutput = AiGateway.chat(provider, baseUrl, apiKey, model, systemPrompt, nextPrompt);
            ensureAiRequestActiveOrThrow(requestToken);
        }

        return assistantOutput + "\n\n(已达到最大命令轮次，若需继续请重试)";
    }

    private void addSystemMessage(String text) {
        if (TextUtils.isEmpty(text)) {
            return;
        }

        String normalized = safe(text)
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .trim();
        if (normalized.isEmpty()) {
            return;
        }
        String singleLine = normalized.replace("\n", "  ");
        String noticeText = singleLine.startsWith("工具") ? singleLine : "工具调用: " + singleLine;

        TextView tv = new TextView(ctx());
        tv.setText(noticeText);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        tv.setTextColor(ColorUtils.setAlphaComponent(UiStyleHelper.resolveOnSurfaceColor(ctx()), 196));
        tv.setAlpha(0.94f);
        tv.setGravity(Gravity.START);
        tv.setIncludeFontPadding(false);
        tv.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);

        int padH = dp(2);
        int padV = dp(1);
        tv.setPadding(padH, padV, padH, padV);
        tv.setBackground(null);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.START;
        lp.setMargins(dp(2), dp(2), dp(2), dp(3));
        tv.setLayoutParams(lp);

        chatContainer.addView(tv);
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private boolean isToolFeedbackMessage(String text) {
        String normalized = safe(text)
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .trim();
        if (normalized.isEmpty() || normalized.length() > 120) {
            return false;
        }

        String[] lines = normalized.split("\\n");
        for (String line : lines) {
            String one = safe(line).trim();
            if (one.isEmpty()) {
                continue;
            }
            boolean matched = one.startsWith("已读取技能")
                    || one.startsWith("读取了笔记")
                    || one.startsWith("已新增笔记")
                    || one.startsWith("已修改笔记")
                    || one.startsWith("已删除笔记")
                    || one.startsWith("已清空笔记")
                    || one.startsWith("已查询今日剩余课程")
                    || one.startsWith("已查询指定日期课程")
                    || one.startsWith("已按课程名查询课程")
                    || one.startsWith("已按关键词查询课程")
                    || one.startsWith("存在不支持的命令")
                    || one.startsWith("无可执行操作")
                    || one.startsWith("命令为空")
                    || one.startsWith("工具调用:");
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private String stripLeadingToolFeedbackLines(String rawReply) {
        String normalized = safe(rawReply).replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = normalized.split("\n");
        int start = 0;
        while (start < lines.length) {
            String line = safe(lines[start]).trim();
            if (line.isEmpty()) {
                start++;
                continue;
            }
            if (isToolFeedbackMessage(line)) {
                start++;
                continue;
            }
            break;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = start; i < lines.length; i++) {
            sb.append(lines[i]);
            if (i < lines.length - 1) {
                sb.append('\n');
            }
        }
        return sb.toString().trim();
    }

    private void addBubble(boolean isUser, String text, boolean typing) {
        addBubble(isUser, text, typing, true);
    }

    private void addBubble(boolean isUser, String text, boolean typing, boolean persistToHistory) {
        TextView tv = new TextView(ctx());
        tv.setTag(typing ? "typing" : null);
        if (typing) {
            tv.setText(text);
        } else {
            markwon.setMarkdown(tv, normalizeMarkdown(text));
        }
        tv.setTextSize(15f);
        tv.setLineSpacing(0f, 1.08f);
        tv.setTextColor(pickTextColor(isUser));
        int pad = dp(14);
        tv.setPadding(pad, pad, pad, pad);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(20));
        bg.setColor(pickBubbleColor(isUser));
        bg.setStroke(dp(1), ColorUtils.setAlphaComponent(UiStyleHelper.resolveOnSurfaceColor(ctx()), 36));
        tv.setBackground(bg);
        tv.setClipToOutline(true);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = isUser ? Gravity.END : Gravity.START;
        lp.setMargins(0, dp(8), 0, dp(8));
        tv.setLayoutParams(lp);

        int maxWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.84f);
        tv.setMaxWidth(maxWidth);

        chatContainer.addView(tv);
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
        if (!typing && persistToHistory) {
            appendMessageToHistory(isUser, text);
        }
    }

    private TextView addAssistantStreamingBubble() {
        TextView tv = new TextView(ctx());
        tv.setTextSize(15f);
        tv.setLineSpacing(0f, 1.08f);
        tv.setTextColor(pickTextColor(false));
        int pad = dp(14);
        tv.setPadding(pad, pad, pad, pad);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(20));
        bg.setColor(pickBubbleColor(false));
        bg.setStroke(dp(1), ColorUtils.setAlphaComponent(UiStyleHelper.resolveOnSurfaceColor(ctx()), 36));
        tv.setBackground(bg);
        tv.setClipToOutline(true);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.START;
        lp.setMargins(0, dp(8), 0, dp(8));
        tv.setLayoutParams(lp);
        tv.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.84f));

        chatContainer.addView(tv);
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
        return tv;
    }

    private void streamAssistantReply(String rawReply, long requestToken, Runnable onDone) {
        String normalized = normalizeMarkdown(rawReply);
        if (activeStreamTicker != null) {
            streamHandler.removeCallbacks(activeStreamTicker);
            activeStreamTicker = null;
        }
        if (streamingBubble != null) {
            chatContainer.removeView(streamingBubble);
            streamingBubble = null;
        }
        streamingBubble = addAssistantStreamingBubble();

        if (normalized.isEmpty()) {
            markwon.setMarkdown(streamingBubble, "");
            streamingBubble = null;
            if (onDone != null) onDone.run();
            return;
        }

        final int[] cursor = {0};
        final int total = normalized.length();
        final int step = 3;

        Runnable ticker = new Runnable() {
            @Override
            public void run() {
                if (!isAiRequestActive(requestToken)) {
                    activeStreamTicker = null;
                    if (streamingBubble != null) {
                        chatContainer.removeView(streamingBubble);
                        streamingBubble = null;
                    }
                    return;
                }
                cursor[0] = Math.min(total, cursor[0] + step);
                String partial = normalized.substring(0, cursor[0]);
                if (streamingBubble != null) {
                    markwon.setMarkdown(streamingBubble, partial);
                    chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
                }
                if (cursor[0] < total) {
                    activeStreamTicker = this;
                    streamHandler.postDelayed(this, 18L);
                } else {
                    activeStreamTicker = null;
                    streamingBubble = null;
                    if (onDone != null) onDone.run();
                }
            }
        };
        activeStreamTicker = ticker;
        streamHandler.post(ticker);
    }

    private void removeTypingBubble() {
        for (int i = chatContainer.getChildCount() - 1; i >= 0; i--) {
            View child = chatContainer.getChildAt(i);
            if ("typing".equals(child.getTag())) {
                chatContainer.removeViewAt(i);
                return;
            }
        }
    }

    private int pickBubbleColor(boolean isUser) {
        int primary = UiStyleHelper.resolveAccentColor(ctx());
        int surface = UiStyleHelper.resolveGlassCardColor(ctx());
        return isUser ? ColorUtils.blendARGB(primary, Color.WHITE, 0.10f) : surface;
    }

    private String normalizeMarkdown(String raw) {
        if (raw == null) return "";
        String normalized = raw.replace("\r\n", "\n");
        normalized = normalized.replace("\\r\\n", "\n");
        normalized = normalized.replace("\\n", "\n");
        normalized = normalized.replace("\r", "\n");
        normalized = normalized.replace("\\t", "    ");
        normalized = normalized.replace("\t", "    ");
        normalized = normalized.replace('\u00A0', ' ');
        normalized = normalized.replaceAll("(?m)[ \\t]+$", "");
        normalized = normalized.replaceAll("\\n{4,}", "\\n\\n\\n");
        return normalized.stripTrailing();
    }

    private ReplyWithTitle extractTitle(String rawReply) {
        String raw = safe(rawReply).replace("\r\n", "\n");
        int firstBreak = raw.indexOf('\n');
        String firstLine = firstBreak >= 0 ? raw.substring(0, firstBreak).trim() : raw.trim();
        Matcher matcher = TITLE_PATTERN.matcher(firstLine);
        if (!matcher.matches()) {
            return new ReplyWithTitle("", raw);
        }
        String title = matcher.group(1) == null ? "" : matcher.group(1).trim();
        String rest = firstBreak >= 0 ? raw.substring(firstBreak + 1) : "";
        if (rest.startsWith("\n")) {
            rest = rest.substring(1);
        }
        return new ReplyWithTitle(title, rest);
    }

    private int pickTextColor(boolean isUser) {
        if (isUser) {
            return pickReadableTextColor(pickBubbleColor(true));
        }
        return UiStyleHelper.resolveOnSurfaceColor(ctx());
    }

    private int pickReadableTextColor(int bgColor) {
        return ColorUtils.calculateLuminance(bgColor) < 0.5 ? Color.WHITE : Color.BLACK;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private boolean shouldRequestModelTitleForCurrentTurn() {
        return activeSession == null || isUntouchedSession(activeSession);
    }

    private boolean isUntouchedSession(ChatSession session) {
        if (session == null) {
            return true;
        }
        String title = safe(session.title).trim();
        return (title.isEmpty() || "新对话".equals(title)) && countUserMessages(session) == 0;
    }

    private int countUserMessages(ChatSession session) {
        if (session == null || session.messages == null) {
            return 0;
        }
        int count = 0;
        for (ChatMessage msg : session.messages) {
            if (msg != null && "user".equalsIgnoreCase(msg.role) && !TextUtils.isEmpty(msg.content)) {
                count++;
            }
        }
        return count;
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }

    private void applyPageVisualStyle() {
        View root = rootView == null ? null : rootView.findViewById(R.id.rootAiChat);
        if (root != null) {
            UiStyleHelper.applySecondaryPageBackground(root, ctx());
        }
        if (rootView != null) {
            rootView.setBackgroundColor(Color.TRANSPARENT);
            UiStyleHelper.applyGlassCards(rootView, ctx());
        }
    }

    public boolean handleBackPressed() {
        if (drawerAiChat != null && drawerAiChat.isDrawerOpen(GravityCompat.START)) {
            drawerAiChat.closeDrawer(GravityCompat.START);
            return true;
        }
        return false;
    }

    private static final class ReplyWithTitle {
        final String title;
        final String replyBody;

        ReplyWithTitle(String title, String replyBody) {
            this.title = title;
            this.replyBody = replyBody;
        }
    }

    private static final class ChatMessage {
        String role;
        String content;
    }

    private static final class ChatSession {
        String id;
        String title;
        boolean titleFromAi;
        long updatedAt;
        List<ChatMessage> messages;
    }

    private static final class HistoryRow {
        final boolean isHeader;
        final String headerText;
        final ChatSession session;

        private HistoryRow(boolean isHeader, String headerText, ChatSession session) {
            this.isHeader = isHeader;
            this.headerText = headerText;
            this.session = session;
        }

        static HistoryRow header(String text) {
            return new HistoryRow(true, text, null);
        }

        static HistoryRow session(ChatSession session) {
            return new HistoryRow(false, "", session);
        }
    }

    private static final class HistoryItemHolder {
        LinearLayout root;
        TextView title;
        TextView subtitle;
    }

    private final class HistorySessionAdapter extends BaseAdapter {

        private static final int TYPE_HEADER = 0;
        private static final int TYPE_ITEM = 1;

        private final LayoutInflater inflater = LayoutInflater.from(ctx());

        @Override
        public int getCount() {
            return historyRows.size();
        }

        @Override
        public Object getItem(int position) {
            return historyRows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            HistoryRow row = historyRows.get(position);
            return row.isHeader ? TYPE_HEADER : TYPE_ITEM;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            int viewType = getItemViewType(position);
            HistoryRow row = historyRows.get(position);

            if (viewType == TYPE_HEADER) {
                TextView headerView;
                if (!(convertView instanceof TextView)) {
                    headerView = new TextView(ctx());
                    headerView.setLayoutParams(new ListView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    ));
                    headerView.setPadding(dp(10), dp(10), dp(10), dp(4));
                    headerView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
                    headerView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                } else {
                    headerView = (TextView) convertView;
                }

                boolean dark = isDarkMode();
                int textColor = dark
                        ? ColorUtils.setAlphaComponent(Color.WHITE, 204)
                        : ColorUtils.setAlphaComponent(Color.BLACK, 184);
                headerView.setTextColor(textColor);
                headerView.setText(row.headerText);
                headerView.setBackgroundColor(Color.TRANSPARENT);
                return headerView;
            }

            HistoryItemHolder holder;
            if (convertView == null || convertView instanceof TextView) {
                convertView = inflater.inflate(R.layout.item_ai_history_session, parent, false);
                holder = new HistoryItemHolder();
                holder.root = convertView.findViewById(R.id.historyItemRoot);
                holder.title = convertView.findViewById(R.id.tvHistoryTitle);
                holder.subtitle = convertView.findViewById(R.id.tvHistorySubtitle);
                convertView.setTag(holder);
            } else {
                holder = (HistoryItemHolder) convertView.getTag();
            }

            ChatSession one = row.session;
            boolean selected = one == activeSession || position == highlightedHistoryPosition;

            holder.title.setText(safe(one.title));
            holder.subtitle.setVisibility(View.GONE);
            boolean dark = isDarkMode();
            int textPrimary = dark ? Color.WHITE : Color.BLACK;
            holder.title.setTextColor(textPrimary);

            if (selected) {
                int accent = UiStyleHelper.resolveAccentColor(ctx());
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(dp(14));
                bg.setColor(ColorUtils.setAlphaComponent(accent, dark ? 110 : 75));
                holder.root.setBackground(bg);
            } else {
                holder.root.setBackgroundColor(Color.TRANSPARENT);
            }

            return convertView;
        }
    }
}
