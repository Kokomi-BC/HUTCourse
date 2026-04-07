package cn.edu.hut.course;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;

import cn.edu.hut.course.ai.AiPromptCenter;

import java.util.List;

public class AiChatActivity extends AppCompatActivity {

    private LinearLayout chatContainer;
    private ScrollView chatScroll;
    private EditText etPrompt;
    private MaterialButton btnSend;
    private TextView tvAiStatus;
    private boolean hasSentCurrentTimeToModel = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiStyleHelper.hideStatusBar(this);
        setContentView(R.layout.activity_ai_chat);
        applyPageVisualStyle();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        UiStyleHelper.styleGlassToolbar(toolbar, this);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        chatContainer = findViewById(R.id.chatContainer);
        chatScroll = findViewById(R.id.chatScroll);
        etPrompt = findViewById(R.id.etPrompt);
        btnSend = findViewById(R.id.btnSend);
        tvAiStatus = findViewById(R.id.tvAiStatus);

        refreshAiStatus();
        addBubble(false, "你可以直接聊天。\n命令支持：/notes、/note <内容>、/note-edit <序号> <内容>、/note-del <序号或关键词>、/note-clear。", false);

        btnSend.setOnClickListener(v -> sendMessage());
        findViewById(R.id.btnNoteQuick).setOnClickListener(v -> {
            String text = etPrompt.getText() == null ? "" : etPrompt.getText().toString().trim();
            if (text.isEmpty()) {
                Toast.makeText(this, "请输入要记录的内容", Toast.LENGTH_SHORT).show();
                return;
            }
            String result = NoteSkillManager.appendNote(this, text);
            addBubble(true, text, false);
            addBubble(false, result, false);
            etPrompt.setText("");
        });
        findViewById(R.id.btnReadNotes).setOnClickListener(v -> {
            String notes = NoteSkillManager.readNotes(this);
            addBubble(false, "当前注意事项：\n" + notes, false);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyPageVisualStyle();
        refreshAiStatus();
    }

    private void refreshAiStatus() {
        String provider = AiConfigStore.getProvider(this);
        String model = AiConfigStore.getModel(this);
        String key = AiConfigStore.getApiKey(this);
        String providerName = AiConfigStore.PROVIDER_SDK.equals(provider) ? "OpenAI SDK" : "Curl API";
        String keyState = key.isEmpty() ? "未配置Key" : "已配置Key";
        tvAiStatus.setText("当前模式：" + providerName + " | 模型：" + model + " | " + keyState);
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
                addBubble(false, finalReply, false);
                btnSend.setEnabled(true);
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
        tv.setText(text);
        tv.setTextSize(14f);
        tv.setTextColor(pickTextColor(isUser));
        int pad = dp(12);
        tv.setPadding(pad, pad, pad, pad);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(pickBubbleColor(isUser));
        tv.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = isUser ? Gravity.END : Gravity.START;
        lp.setMargins(0, dp(6), 0, dp(6));
        tv.setLayoutParams(lp);

        int maxWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.82f);
        tv.setMaxWidth(maxWidth);

        chatContainer.addView(tv);
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
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
        int primary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer, Color.parseColor("#DAE7F7"));
        int surface = UiStyleHelper.resolveGlassCardColor(this);
        return isUser ? ColorUtils.setAlphaComponent(primary, 240) : surface;
    }

    private int pickTextColor(boolean isUser) {
        if (isUser) {
            return MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimaryContainer, Color.BLACK);
        }
        return MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK);
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
