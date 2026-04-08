package cn.edu.hut.course;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.card.MaterialCardView;

import io.noties.markwon.Markwon;

import cn.edu.hut.course.ai.AiPromptCenter;

import java.util.List;

public class AiChatActivity extends AppCompatActivity {

    private static final String PREF_COURSE_STORAGE = "course_storage";
    private static final String KEY_TIMETABLE_THEME_COLOR = "timetable_theme_color";

    private LinearLayout chatContainer;
    private ScrollView chatScroll;
    private EditText etPrompt;
    private ImageButton btnSend;
    private MaterialCardView composerCard;
    private TextView tvAiStatus;
    private Markwon markwon;
    private final Handler streamHandler = new Handler(Looper.getMainLooper());
    private TextView streamingBubble;
    private boolean hasSentCurrentTimeToModel = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiStyleHelper.hideStatusBar(this);
        setContentView(R.layout.activity_ai_chat);
        applyPageVisualStyle();

        chatContainer = findViewById(R.id.chatContainer);
        chatScroll = findViewById(R.id.chatScroll);
        etPrompt = findViewById(R.id.etPrompt);
        btnSend = findViewById(R.id.btnSend);
        composerCard = findViewById(R.id.composerCard);
        tvAiStatus = findViewById(R.id.tvAiStatus);
        markwon = Markwon.create(this);

        configurePromptInput();
        applyImeInsetBehavior();
        applyComposerStyle();
        refreshAiStatus();
        addBubble(false, "你可以与我聊聊课程相关的问题", false);

        btnSend.setOnClickListener(v -> sendMessage());
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyPageVisualStyle();
        applyComposerStyle();
        refreshAiStatus();
    }

    private void configurePromptInput() {
        if (etPrompt == null) return;
        etPrompt.setMinLines(1);
        etPrompt.setMaxLines(8);
        etPrompt.setHorizontallyScrolling(false);
    }

    private void applyImeInsetBehavior() {
        View root = findViewById(R.id.rootAiChat);
        if (root == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            Insets navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            boolean imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            int bottom = imeVisible ? Math.max(ime.bottom, navBars.bottom) : navBars.bottom;
            v.setPadding(v.getPaddingLeft(), statusBars.top, v.getPaddingRight(), bottom);
            return insets;
        });
    }

    private void refreshAiStatus() {
        String provider = AiConfigStore.getProvider(this);
        String model = AiConfigStore.getModel(this);
        String key = AiConfigStore.getApiKey(this);
        String providerName = AiConfigStore.PROVIDER_SDK.equals(provider) ? "OpenAI SDK" : "Curl API";
        String keyState = key.isEmpty() ? "未配置Key" : "已配置Key";
        tvAiStatus.setText("当前模式：" + providerName + " | 模型：" + model + " | " + keyState);
        tvAiStatus.setTextColor(UiStyleHelper.resolveOnSurfaceVariantColor(this));
    }

    private void applyComposerStyle() {
        int onSurface = UiStyleHelper.resolveOnSurfaceColor(this);
        int outline = UiStyleHelper.resolveOutlineColor(this);
        int accent = UiStyleHelper.resolveAccentColor(this);
        int accentFill = ColorUtils.blendARGB(accent, Color.WHITE, 0.08f);

        if (composerCard != null) {
            composerCard.setCardBackgroundColor(UiStyleHelper.resolveGlassCardColor(this));
            composerCard.setStrokeColor(outline);
            composerCard.setClipToOutline(true);
            composerCard.setClipChildren(true);
        }
        if (etPrompt != null) {
            etPrompt.setTextColor(onSurface);
            etPrompt.setHintTextColor(ColorUtils.setAlphaComponent(onSurface, 136));
        }
        if (btnSend != null) {
            btnSend.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentFill));
            btnSend.setImageTintList(android.content.res.ColorStateList.valueOf(pickReadableTextColor(accentFill)));
        }
    }

    private void sendMessage() {
        String userText = etPrompt.getText() == null ? "" : etPrompt.getText().toString().trim();
        if (userText.isEmpty()) {
            return;
        }

        addBubble(true, userText, false);
        etPrompt.setText("");

        if ("/notes".equalsIgnoreCase(userText)) {
            addBubble(false, NoteSkillManager.readNotes(this), false);
            return;
        }
        if (userText.startsWith("/note ")) {
            String result = NoteSkillManager.appendNote(this, userText.substring(6));
            addBubble(false, result, false);
            return;
        }
        if (userText.startsWith("/note-del ")) {
            String arg = userText.substring(10).trim();
            String result;
            if (arg.matches("\\d+")) {
                result = NoteSkillManager.deleteNoteByIndex(this, Integer.parseInt(arg));
            } else {
                result = NoteSkillManager.deleteNoteByKeyword(this, arg);
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
            String result = NoteSkillManager.updateNoteByIndex(this, index, parts[1]);
            addBubble(false, result, false);
            return;
        }
        if ("/note-clear".equalsIgnoreCase(userText)) {
            addBubble(false, NoteSkillManager.clearNotes(this), false);
            return;
        }
        if (userText.startsWith("/cmd ")) {
            String cmd = userText.substring(5).trim();
            SkillCommandCenter.CommandBatchResult one = SkillCommandCenter.executeCommandsWithFeedback(this, java.util.Collections.singletonList(cmd));
            addBubble(false, one.userFeedback, false);
            return;
        }

        String apiKey = AiConfigStore.getApiKey(this);
        if (apiKey.isEmpty()) {
            addBubble(false, "请先到“设置 -> AI接入”配置API Key。", false);
            return;
        }

        btnSend.setEnabled(false);
        addBubble(false, "思考中...", true);

        final String provider = AiConfigStore.getProvider(this);
        final String baseUrl = AiConfigStore.getBaseUrl(this);
        final String model = AiConfigStore.getModel(this);
        final String selectedText = getIntent().getStringExtra("selected_text");

        new Thread(() -> {
            String reply;
            try {
                reply = runModelWithSkillCommands(provider, baseUrl, apiKey, model, userText, selectedText);
            } catch (Exception e) {
                reply = "请求失败：" + e.getMessage();
            }

            final String finalReply = reply;
            runOnUiThread(() -> {
                removeTypingBubble();
                streamAssistantReply(finalReply, () -> btnSend.setEnabled(true));
            });
        }).start();
    }

    private String runModelWithSkillCommands(String provider, String baseUrl, String apiKey, String model,
                                             String userText, String selectedText) throws Exception {
        String skillIndex = SkillCommandCenter.buildSkillIndexFromFrontmatter(this);
        String systemPrompt = AiPromptCenter.buildSystemPrompt();
        boolean includeCurrentTime = !hasSentCurrentTimeToModel;
        String firstTurnPrompt = AiPromptCenter.buildFirstTurnUserPrompt(skillIndex, selectedText, userText, includeCurrentTime);

        String assistantOutput = AiGateway.chat(provider, baseUrl, apiKey, model, systemPrompt, firstTurnPrompt);
        if (includeCurrentTime) {
            hasSentCurrentTimeToModel = true;
        }

        for (int round = 1; round <= 10; round++) {
            List<String> commands = SkillCommandCenter.extractCommands(assistantOutput);
            if (commands.isEmpty()) {
                return assistantOutput;
            }

            SkillCommandCenter.CommandBatchResult batch = SkillCommandCenter.executeCommandsWithFeedback(this, commands);
            String commandResult = batch.modelFeedback;
            String roundMessage = batch.userFeedback;
            runOnUiThread(() -> addBubble(false, roundMessage, false));

            String nextPrompt = AiPromptCenter.buildToolFollowupPrompt(userText, assistantOutput, commands, commandResult);
            assistantOutput = AiGateway.chat(provider, baseUrl, apiKey, model, systemPrompt, nextPrompt);
        }

        return assistantOutput + "\n\n(已达到最大命令轮次，若需继续请重试)";
    }

    private void addBubble(boolean isUser, String text, boolean typing) {
        TextView tv = new TextView(this);
        tv.setTag(typing ? "typing" : null);
        if (typing) {
            tv.setText(text);
        } else {
            markwon.setMarkdown(tv, normalizeMarkdown(text));
        }
        tv.setTextSize(15f);
        tv.setLineSpacing(0f, 1.15f);
        tv.setTextColor(pickTextColor(isUser));
        int pad = dp(14);
        tv.setPadding(pad, pad, pad, pad);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(20));
        bg.setColor(pickBubbleColor(isUser));
        bg.setStroke(dp(1), ColorUtils.setAlphaComponent(UiStyleHelper.resolveOnSurfaceColor(this), 36));
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
    }

    private TextView addAssistantStreamingBubble() {
        TextView tv = new TextView(this);
        tv.setTextSize(15f);
        tv.setLineSpacing(0f, 1.15f);
        tv.setTextColor(pickTextColor(false));
        int pad = dp(14);
        tv.setPadding(pad, pad, pad, pad);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(20));
        bg.setColor(pickBubbleColor(false));
        bg.setStroke(dp(1), ColorUtils.setAlphaComponent(UiStyleHelper.resolveOnSurfaceColor(this), 36));
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
        int primary = UiStyleHelper.resolveAccentColor(this);
        int surface = UiStyleHelper.resolveGlassCardColor(this);
        return isUser ? ColorUtils.blendARGB(primary, Color.WHITE, 0.10f) : surface;
    }

    private String normalizeMarkdown(String raw) {
        if (raw == null) return "";
        String normalized = raw.replace("\r\n", "\n");
        normalized = normalized.replace("\\t", "    ");
        normalized = normalized.replace("\t", "    ");
        return normalized;
    }

    private int pickTextColor(boolean isUser) {
        if (isUser) {
            return pickReadableTextColor(pickBubbleColor(true));
        }
        return UiStyleHelper.resolveOnSurfaceColor(this);
    }

    private int pickReadableTextColor(int bgColor) {
        return ColorUtils.calculateLuminance(bgColor) < 0.5 ? Color.WHITE : Color.BLACK;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private void applyPageVisualStyle() {
        View root = findViewById(R.id.rootAiChat);
        if (root != null) {
            UiStyleHelper.applySecondaryPageBackground(root, this);
        }
        UiStyleHelper.applyGlassCards(findViewById(android.R.id.content), this);
    }
}
