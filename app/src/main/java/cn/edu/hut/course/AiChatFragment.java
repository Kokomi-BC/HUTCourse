package cn.edu.hut.course;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsAnimation;
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
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.noties.markwon.Markwon;

import cn.edu.hut.course.ai.AiPromptCenter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
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
    private TextView btnNewSession;
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
    private HistorySessionAdapter historyAdapter;
    private ChatSession activeSession;
    private TextView streamingBubble;
    private boolean hasSentCurrentTimeToModel = false;
    private int baseComposerBottomMargin = 0;
    private View rootView;
    private PopupWindow sessionMenuPopup;
    private int rootBottomGap = 0;
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

        configurePromptInput();
        setupHistoryDrawer();
        loadHistory();
        ensureFreshSessionOnEnter();
        ensureGreetingForActiveSession();
        applyImeInsetBehavior();
        applyComposerStyle();
        refreshAiStatus();

        btnSend.setOnClickListener(v -> sendMessage());
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
        if (getActivity() != null && getActivity().getWindow() != null) {
            // Let IME insets drive the composer movement.
            getActivity().getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                            | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
            );
        }
        applyPageVisualStyle();
        applyComposerStyle();
        refreshAiStatus();
    }

    @Override
    public void onDestroyView() {
        if (sessionMenuPopup != null) {
            sessionMenuPopup.dismiss();
            sessionMenuPopup = null;
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
    }

    private void applyImeInsetBehavior() {
        View root = rootView == null ? null : rootView.findViewById(R.id.rootAiChat);
        if (root == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            Insets navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            boolean imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            rootBottomGap = resolveRootBottomGap(v);
            int keyboardBottom = Math.max(0, ime.bottom - rootBottomGap);

            v.setPadding(v.getPaddingLeft(), statusBars.top, v.getPaddingRight(), 0);
            if (composerCard != null) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) composerCard.getLayoutParams();
                int targetBottomMargin = baseComposerBottomMargin + navBars.bottom + keyboardBottom;
                if (lp.bottomMargin != targetBottomMargin) {
                    lp.bottomMargin = targetBottomMargin;
                    composerCard.setLayoutParams(lp);
                }
            }
            if (chatScroll != null) {
                int targetPaddingBottom = imeVisible ? dp(8) : navBars.bottom + dp(8);
                if (chatScroll.getPaddingBottom() != targetPaddingBottom) {
                    chatScroll.setPadding(chatScroll.getPaddingLeft(), chatScroll.getPaddingTop(), chatScroll.getPaddingRight(), targetPaddingBottom);
                }
            }
            return insets;
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            root.setWindowInsetsAnimationCallback(new WindowInsetsAnimation.Callback(WindowInsetsAnimation.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                @Override
                public WindowInsets onProgress(WindowInsets insets, List<WindowInsetsAnimation> runningAnimations) {
                    WindowInsetsCompat compatInsets = WindowInsetsCompat.toWindowInsetsCompat(insets, root);
                    Insets navBars = compatInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
                    Insets ime = compatInsets.getInsets(WindowInsetsCompat.Type.ime());
                    boolean imeVisible = compatInsets.isVisible(WindowInsetsCompat.Type.ime());
                    int keyboardBottom = Math.max(0, ime.bottom - rootBottomGap);

                    if (composerCard != null) {
                        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) composerCard.getLayoutParams();
                        int targetBottomMargin = baseComposerBottomMargin + navBars.bottom + keyboardBottom;
                        if (lp.bottomMargin != targetBottomMargin) {
                            lp.bottomMargin = targetBottomMargin;
                            composerCard.setLayoutParams(lp);
                        }
                    }
                    if (chatScroll != null) {
                        int targetPaddingBottom = imeVisible ? dp(8) : navBars.bottom + dp(8);
                        if (chatScroll.getPaddingBottom() != targetPaddingBottom) {
                            chatScroll.setPadding(chatScroll.getPaddingLeft(), chatScroll.getPaddingTop(), chatScroll.getPaddingRight(), targetPaddingBottom);
                        }
                    }
                    return insets;
                }
            });
        }
        ViewCompat.requestApplyInsets(root);
    }

    private int resolveRootBottomGap(View root) {
        if (root == null || getActivity() == null || getActivity().getWindow() == null) {
            return 0;
        }
        int[] location = new int[2];
        root.getLocationInWindow(location);
        int rootBottomInWindow = location[1] + root.getHeight();
        int windowHeight = getActivity().getWindow().getDecorView().getHeight();
        if (windowHeight <= 0) {
            return 0;
        }
        return Math.max(0, windowHeight - rootBottomInWindow);
    }

    private void setupHistoryDrawer() {
        historyAdapter = new HistorySessionAdapter();
        if (lvHistory != null) {
            lvHistory.setAdapter(historyAdapter);
            lvHistory.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {
                if (position < 0 || position >= sessions.size()) {
                    return;
                }
                activeSession = sessions.get(position);
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
                if (position < 0 || position >= sessions.size()) {
                    return true;
                }
                showSessionActionMenu(view, position);
                return true;
            });
        }
    }

    private void startNewSession(boolean forceSave) {
        if (!sessions.isEmpty()) {
            ChatSession latest = sessions.get(0);
            if (isUntouchedSession(latest)) {
                activeSession = latest;
                renderActiveSession();
                refreshHistoryRows();
                return;
            }
        }

        ChatSession session = new ChatSession();
        session.id = UUID.randomUUID().toString();
        session.title = "新对话";
        session.updatedAt = System.currentTimeMillis();
        session.messages = new ArrayList<>();
        sessions.add(0, session);
        trimHistoryIfNeeded();
        activeSession = session;
        chatContainer.removeAllViews();
        refreshHistoryRows();
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
            return;
        }
        for (ChatMessage one : activeSession.messages) {
            boolean isUser = "user".equalsIgnoreCase(one.role);
            addBubble(isUser, one.content, false, false);
        }
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void refreshHistoryRows() {
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        if (historyAdapter != null) {
            historyAdapter.notifyDataSetChanged();
        }
        if (tvHistoryMeta != null) {
            if (sessions.isEmpty()) {
                tvHistoryMeta.setText("最近会话");
            } else {
                ChatSession latest = sessions.get(0);
                String latestTime = sdf.format(new Date(latest.updatedAt));
                tvHistoryMeta.setText("最近会话 · " + sessions.size() + " 条 · 最新 " + latestTime);
            }
        }
        if (tvHistoryEmpty != null) {
            tvHistoryEmpty.setVisibility(sessions.isEmpty() ? View.VISIBLE : View.GONE);
        }
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
                    sessions.add(session);
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
        activeSession.updatedAt = System.currentTimeMillis();
        sessions.remove(activeSession);
        sessions.add(0, activeSession);
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
        if (isUser && (TextUtils.isEmpty(activeSession.title) || "新对话".equals(activeSession.title))) {
            activeSession.title = makeFallbackTitle(text);
        }
        touchActiveSession();
        refreshHistoryRows();
        saveHistory();
    }

    private void updateSessionTitle(String title) {
        if (TextUtils.isEmpty(title)) {
            return;
        }
        if (activeSession == null) {
            startNewSession(false);
        }
        activeSession.title = title.trim();
        touchActiveSession();
        refreshHistoryRows();
        saveHistory();
    }

    private String makeFallbackTitle(String text) {
        String oneLine = safe(text).replace('\n', ' ').trim();
        if (oneLine.length() > 18) {
            return oneLine.substring(0, 18) + "...";
        }
        return oneLine.isEmpty() ? "新对话" : oneLine;
    }

    private void refreshAiStatus() {
        if (tvAiStatus != null) {
            tvAiStatus.setVisibility(View.GONE);
        }
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
        }
        if (btnOpenHistory != null) {
            btnOpenHistory.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentFill));
            btnOpenHistory.setImageTintList(android.content.res.ColorStateList.valueOf(pickReadableTextColor(accentFill)));
        }
        if (btnNewSession != null) {
            GradientDrawable newChatBg = new GradientDrawable();
            newChatBg.setCornerRadius(dp(999));
            newChatBg.setColor(ColorUtils.setAlphaComponent(accent, 72));
            newChatBg.setStroke(dp(1), ColorUtils.setAlphaComponent(accent, 176));
            btnNewSession.setBackground(newChatBg);
            btnNewSession.setTextColor(onSurface);
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
        if (btnNewSession != null) {
            int accent = UiStyleHelper.resolveAccentColor(ctx());
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(999));
            bg.setColor(ColorUtils.setAlphaComponent(accent, dark ? 128 : 64));
            bg.setStroke(dp(1), ColorUtils.setAlphaComponent(accent, dark ? 220 : 170));
            btnNewSession.setBackground(bg);
            btnNewSession.setTextColor(textPrimary);
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
            sessionMenuPopup.dismiss();
            sessionMenuPopup = null;
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
            addBubble(false, one.userFeedback, false);
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

        btnSend.setEnabled(false);
        addBubble(false, "思考中...", true);

        final String provider = AiConfigStore.getProvider(ctx());
        final String baseUrl = AiConfigStore.getBaseUrl(ctx());
        final String model = configuredModel;
        final String selectedText = getArguments() == null ? null : getArguments().getString("selected_text");

        new Thread(() -> {
            String reply;
            try {
                reply = runModelWithSkillCommands(provider, baseUrl, apiKey, model, userText, selectedText, requestTitleInFinalAnswer);
            } catch (Exception e) {
                reply = "请求失败：" + e.getMessage();
            }

            final String finalReply = reply;
            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> {
                removeTypingBubble();
                ReplyWithTitle parsed = extractTitle(finalReply);
                if (requestTitleInFinalAnswer && !TextUtils.isEmpty(parsed.title)) {
                    updateSessionTitle(parsed.title);
                }
                String visibleReply = TextUtils.isEmpty(parsed.title) ? finalReply : parsed.replyBody;
                if (TextUtils.isEmpty(visibleReply.trim())) {
                    btnSend.setEnabled(true);
                    return;
                }
                streamAssistantReply(visibleReply, () -> {
                    appendMessageToHistory(false, visibleReply);
                    btnSend.setEnabled(true);
                });
            });
        }).start();
    }

    private String runModelWithSkillCommands(String provider, String baseUrl, String apiKey, String model,
                                             String userText, String selectedText,
                                             boolean requestTitleInFinalAnswer) throws Exception {
        String skillIndex = SkillCommandCenter.buildSkillIndexFromFrontmatter(ctx());
        String systemPrompt = AiPromptCenter.buildSystemPrompt();
        boolean includeCurrentTime = !hasSentCurrentTimeToModel;
        String firstTurnPrompt = AiPromptCenter.buildFirstTurnUserPrompt(
                skillIndex,
                selectedText,
                userText,
                includeCurrentTime,
                requestTitleInFinalAnswer
        );

        String assistantOutput = AiGateway.chat(provider, baseUrl, apiKey, model, systemPrompt, firstTurnPrompt);
        if (includeCurrentTime) {
            hasSentCurrentTimeToModel = true;
        }

        for (int round = 1; round <= 10; round++) {
            List<String> commands = SkillCommandCenter.extractCommands(assistantOutput);
            if (commands.isEmpty()) {
                return assistantOutput;
            }

            SkillCommandCenter.CommandBatchResult batch = SkillCommandCenter.executeCommandsWithFeedback(ctx(), commands);
            String commandResult = batch.modelFeedback;
            String roundMessage = batch.userFeedback;
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> addBubble(false, roundMessage, false));
            }

                String nextPrompt = AiPromptCenter.buildToolFollowupPrompt(
                    userText,
                    assistantOutput,
                    commands,
                    commandResult,
                    requestTitleInFinalAnswer
                );
            assistantOutput = AiGateway.chat(provider, baseUrl, apiKey, model, systemPrompt, nextPrompt);
        }

        return assistantOutput + "\n\n(已达到最大命令轮次，若需继续请重试)";
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

    private void streamAssistantReply(String rawReply, Runnable onDone) {
        String normalized = normalizeMarkdown(rawReply);
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
                cursor[0] = Math.min(total, cursor[0] + step);
                String partial = normalized.substring(0, cursor[0]);
                if (streamingBubble != null) {
                    markwon.setMarkdown(streamingBubble, partial);
                    chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
                }
                if (cursor[0] < total) {
                    streamHandler.postDelayed(this, 18L);
                } else {
                    streamingBubble = null;
                    if (onDone != null) onDone.run();
                }
            }
        };
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
        long updatedAt;
        List<ChatMessage> messages;
    }

    private static final class HistoryItemHolder {
        LinearLayout root;
        TextView title;
        TextView subtitle;
    }

    private final class HistorySessionAdapter extends BaseAdapter {

        private final LayoutInflater inflater = LayoutInflater.from(ctx());
        private final SimpleDateFormat timeFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

        @Override
        public int getCount() {
            return sessions.size();
        }

        @Override
        public Object getItem(int position) {
            return sessions.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            HistoryItemHolder holder;
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.item_ai_history_session, parent, false);
                holder = new HistoryItemHolder();
                holder.root = convertView.findViewById(R.id.historyItemRoot);
                holder.title = convertView.findViewById(R.id.tvHistoryTitle);
                holder.subtitle = convertView.findViewById(R.id.tvHistorySubtitle);
                convertView.setTag(holder);
            } else {
                holder = (HistoryItemHolder) convertView.getTag();
            }

            ChatSession one = sessions.get(position);
            boolean selected = one == activeSession;
            int messageCount = one.messages == null ? 0 : one.messages.size();

            holder.title.setText(safe(one.title));
            holder.subtitle.setText(timeFormat.format(new Date(one.updatedAt)) + " · " + messageCount + "条消息");
                boolean dark = isDarkMode();
                int textPrimary = dark ? Color.WHITE : Color.BLACK;
                int textSecondary = ColorUtils.setAlphaComponent(textPrimary, 168);
                holder.title.setTextColor(textPrimary);
                holder.subtitle.setTextColor(textSecondary);

                int accent = UiStyleHelper.resolveAccentColor(ctx());
                int onSurface = textPrimary;
                int drawerBase = dark ? Color.BLACK : Color.WHITE;
            int fill = selected
                    ? ColorUtils.setAlphaComponent(accent, dark ? 110 : 75)
                    : ColorUtils.blendARGB(drawerBase, onSurface, dark ? 0.14f : 0.05f);
            int stroke = selected
                    ? ColorUtils.setAlphaComponent(accent, dark ? 230 : 180)
                    : ColorUtils.setAlphaComponent(onSurface, dark ? 68 : 42);

            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(14));
            bg.setColor(fill);
            bg.setStroke(dp(1), stroke);
            holder.root.setBackground(bg);

            return convertView;
        }
    }
}
