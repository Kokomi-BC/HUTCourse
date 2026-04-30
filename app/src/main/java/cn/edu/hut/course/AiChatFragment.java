package cn.edu.hut.course;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsAnimationCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import io.noties.markwon.Markwon;

import cn.edu.hut.course.ai.AiPromptCenter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AiChatFragment extends Fragment {

    private static final String TAG = "AiChatFragment";
    private static final long INSETS_PRIORITY_HOLD_MS = 160L;
    private static final long IME_PROBE_INTERVAL_MS = 32L;
    private static final long IME_PROBE_DURATION_MS = 1800L;
    private static final int PROMPT_EXPAND_THRESHOLD_LINES = 4;
    private static final int IME_DRIVER_NONE = 0;
    private static final int IME_DRIVER_INSETS = 1;
    private static final int IME_DRIVER_FALLBACK = 2;

    private static final String PREF_AI_CHAT_HISTORY = "ai_chat_history";
    private static final String KEY_CHAT_HISTORY_JSON = "history_json";
    private static final int MAX_HISTORY_SESSIONS = 30;
    private static final int MAX_TOOL_COMMAND_ROUNDS = 30;
    private static final int MAX_MODEL_CONTEXT_MESSAGES = 12;
    private static final int MAX_MODEL_CONTEXT_CHARS = 3200;
    private static final int MAX_MODEL_CONTEXT_ITEM_CHARS = 280;
    private static final int MAX_TOOL_RESULT_FOR_MODEL_CHARS = 2200;
    private static final int STREAM_RENDER_DELAY_MS = 14;
    private static final int STREAM_RENDER_TARGET_MS_SHORT = 320;
    private static final int STREAM_RENDER_TARGET_MS_MEDIUM = 680;
    private static final int STREAM_RENDER_TARGET_MS_LONG = 1100;
    private static final int STREAM_RENDER_FAST_PATH_THRESHOLD = 900;
    private static final Pattern TITLE_PATTERN = Pattern.compile("^(?:TITLE|标题)\\s*[:：]\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMAND_RESULT_LINE_PATTERN = Pattern.compile("^\\d+\\.\\s+.+?=>\\s*(.*)$");
    private static final String SYSTEM_CARD_PREFIX = "CARD_JSON:";
    private static final String SYSTEM_CARD_TYPE_JWXT_LOGIN = "jwxt_login";
    private static final String SYSTEM_CARD_TYPE_API_CONFIG = "api_config";
    private static final String SYSTEM_CARD_ACTION_OPEN_JWXT_LOGIN = "open_jwxt_login";
    private static final String SYSTEM_CARD_ACTION_OPEN_AI_SETTINGS = "open_ai_settings";
    private static final String SYSTEM_CARD_ACTION_OPEN_AMAP_NAVIGATION = "open_amap_navigation";
    public static final String REQUEST_KEY_AGENDA_CHANGED = "ai_chat_agenda_changed";
    public static final String RESULT_KEY_AGENDA_CHANGED = "result_ai_chat_agenda_changed";

    private DrawerLayout drawerAiChat;
    private LinearLayout chatContainer;
    private ScrollView chatScroll;
    private EditText etPrompt;
    private EditText etPromptFullscreen;
    private MaterialAutoCompleteTextView acModelPicker;
    private ImageButton btnAddImage;
    private ImageButton btnRemovePendingImage;
    private View pendingImageContainer;
    private ImageView ivPendingImage;
    private ImageButton btnSend;
    private ImageButton btnSendFullscreen;
    private ImageButton btnExpandComposer;
    private ImageButton btnCollapseComposer;
    private TextView tvComposerClear;
    private ImageButton btnOpenHistory;
    private ImageButton btnNewSession;
    private ListView lvHistory;
    private LinearLayout historyDrawer;
    private TextView tvHistoryDrawerTitle;
    private TextView tvHistoryEmpty;
    private TextView tvHistoryMeta;
    private MaterialCardView composerCard;
    private View composerInner;
    private View composerFullscreenOverlay;
    private View composerFullscreenPanel;
    private View titleModelSwitcherArea;
    private TextView tvAiStatus;
    private int baseHistoryDrawerPaddingTop = 0;
    private int baseHistoryDrawerPaddingBottom = 0;
    private Markwon markwon;
    private final Handler streamHandler = new Handler(Looper.getMainLooper());
    private final List<ChatSession> sessions = new ArrayList<>();
    private final List<HistoryRow> historyRows = new ArrayList<>();
    private final List<AiConfigStore.AiModelConfig> availableModels = new ArrayList<>();
    private final List<String> availableModelLabels = new ArrayList<>();
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
    @Nullable
    private DrawerLayout.SimpleDrawerListener historyDrawerOverlayListener;
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
    private float lastHistoryTouchRawX = -1f;
    private float lastHistoryTouchRawY = -1f;
    private long lastInsetsDispatchUptime = 0L;
    @Nullable
    private View imeFallbackRoot;
    @Nullable
    private ViewTreeObserver.OnGlobalLayoutListener imeGlobalLayoutListener;
    @Nullable
    private Runnable imeProbeTicker;
    private long imeProbeDeadlineUptime = 0L;
    private int lastGlobalKeyboardHeight = Integer.MIN_VALUE;
    private boolean lastGlobalImeVisible = false;
    private int imeDriver = IME_DRIVER_NONE;
    private boolean composerFullscreenMode = false;
    private boolean suppressPromptMirror = false;
    private boolean suppressModelPickerCallback = false;
    private final List<String> pendingImagePaths = new ArrayList<>();
    @Nullable
    private ActivityResultLauncher<String> imagePickerLauncher;
    @Nullable
    private ActivityResultLauncher<Intent> jwxtLoginLauncher;
    @Nullable
    private PendingLoginContinuation pendingLoginContinuation;

    public AiChatFragment() {
        super();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        imagePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), this::handleImagePicked);
        jwxtLoginLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this::handleJwxtLoginResult);
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
        etPromptFullscreen = view.findViewById(R.id.etPromptFullscreen);
        acModelPicker = view.findViewById(R.id.acModelPicker);
        btnAddImage = view.findViewById(R.id.btnAddImage);
        btnRemovePendingImage = view.findViewById(R.id.btnRemovePendingImage);
        pendingImageContainer = view.findViewById(R.id.pendingImageContainer);
        ivPendingImage = view.findViewById(R.id.ivPendingImage);
        btnSend = view.findViewById(R.id.btnSend);
        btnSendFullscreen = view.findViewById(R.id.btnSendFullscreen);
        btnExpandComposer = view.findViewById(R.id.btnExpandComposer);
        btnCollapseComposer = view.findViewById(R.id.btnCollapseComposer);
        tvComposerClear = view.findViewById(R.id.tvComposerClear);
        btnOpenHistory = view.findViewById(R.id.btnOpenHistory);
        btnNewSession = view.findViewById(R.id.btnNewSession);
        lvHistory = view.findViewById(R.id.lvHistory);
        historyDrawer = view.findViewById(R.id.historyDrawer);
        tvHistoryDrawerTitle = view.findViewById(R.id.tvHistoryDrawerTitle);
        tvHistoryEmpty = view.findViewById(R.id.tvHistoryEmpty);
        tvHistoryMeta = view.findViewById(R.id.tvHistoryMeta);
        composerCard = view.findViewById(R.id.composerCard);
        composerInner = view.findViewById(R.id.composerInner);
        composerFullscreenOverlay = view.findViewById(R.id.composerFullscreenOverlay);
        composerFullscreenPanel = view.findViewById(R.id.composerFullscreenPanel);
        titleModelSwitcherArea = view.findViewById(R.id.titleModelSwitcherArea);
        tvAiStatus = view.findViewById(R.id.tvAiStatus);
        markwon = Markwon.create(requireContext());

        if (composerCard != null) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) composerCard.getLayoutParams();
            baseComposerBottomMargin = lp.bottomMargin;
        }
        if (chatScroll != null) {
            baseChatScrollBottomPadding = chatScroll.getPaddingBottom();
        }
        if (historyDrawer != null) {
            baseHistoryDrawerPaddingTop = historyDrawer.getPaddingTop();
            baseHistoryDrawerPaddingBottom = historyDrawer.getPaddingBottom();
        }

        captureMainBottomNavHost();
        configureSoftInputModeForChat();
        configurePromptInput();
        setupModelPicker();
        setupImageAttachment();
        setupHistoryDrawer();
        loadHistory();
        ensureFreshSessionOnEnter();
        ensureGreetingForActiveSession();
        applyImeInsetBehavior();
        setupDismissHistoryDrawerOnOutsideTap();
        setupHistoryDrawerOverlayBehavior();
        applyComposerStyle();
        refreshAiStatus();
        setupComposerFullscreenControls();

        if (btnSend != null) {
            btnSend.setOnClickListener(v -> onSendButtonPressed());
            updateSendButtonMode(false);
        }
        if (btnSendFullscreen != null) {
            btnSendFullscreen.setOnClickListener(v -> onSendButtonPressed());
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
                if (activeSession != null && isUntouchedSession(activeSession)) {
                    Toast.makeText(requireContext(), "已处于新对话中", Toast.LENGTH_SHORT).show();
                } else {
                    startNewSession(true);
                    ensureGreetingForActiveSession();
                    if (drawerAiChat != null) {
                        drawerAiChat.closeDrawer(GravityCompat.START);
                    }
                }
            });
        }

        refreshModelPickerOptions();
        refreshComposerDraftUi();
    }

    @Override
    public void onResume() {
        super.onResume();
        captureMainBottomNavHost();
        setupDismissHistoryDrawerOnOutsideTap();
        setupHistoryDrawerOverlayBehavior();
        configureSoftInputModeForChat();
        applyPageVisualStyle();
        applyComposerStyle();
        refreshHistoryRows();
        refreshAiStatus();
        refreshModelPickerOptions();
        updateComposerExpandButtonVisibility();
        refreshComposerDraftUi();
        maybeResetComposerImeStateAfterInputBlur();
        View root = rootView == null ? null : rootView.findViewById(R.id.rootAiChat);
        if (root != null) {
            ViewCompat.requestApplyInsets(root);
        }
    }

    @Override
    public void onDestroyView() {
        stopImeProbe();
        clearImeGlobalLayoutFallback();
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
        if (drawerAiChat != null && historyDrawerOverlayListener != null) {
            drawerAiChat.removeDrawerListener(historyDrawerOverlayListener);
            historyDrawerOverlayListener = null;
        }
        restoreSoftInputModeIfNeeded();
        ensureMainBottomNavVisible();
        mainBottomNavHost = null;
        clearDismissHistoryDrawerOnOutsideTap();
        if (composerCard != null) {
            composerCard.animate().cancel();
            composerCard.setTranslationY(0f);
        }
        imeDriver = IME_DRIVER_NONE;
        composerFullscreenMode = false;
        pendingImagePaths.clear();
        availableModels.clear();
        availableModelLabels.clear();
        super.onDestroyView();
    }

    private Context ctx() {
        return requireContext();
    }

    private void onSendButtonPressed() {
        if (isAiConversationRunning()) {
            stopCurrentAiConversation(true);
        } else {
            sendMessage();
        }
    }

    private void setupModelPicker() {
        if (acModelPicker == null) {
            return;
        }
        View.OnClickListener showPickerListener = v -> {
            if (acModelPicker == null || !acModelPicker.isEnabled()) {
                return;
            }
            acModelPicker.post(() -> {
                acModelPicker.showDropDown();
            });
        };
        acModelPicker.setKeyListener(null);
        acModelPicker.setFocusable(false);
        acModelPicker.setFocusableInTouchMode(false);
        acModelPicker.setCursorVisible(false);
        acModelPicker.setLongClickable(false);
        acModelPicker.setOnClickListener(showPickerListener);
        acModelPicker.setThreshold(0);
        if (titleModelSwitcherArea != null) {
            titleModelSwitcherArea.setFocusable(false);
            titleModelSwitcherArea.setFocusableInTouchMode(false);
            titleModelSwitcherArea.setOnClickListener(showPickerListener);
        }
        if (tvAiStatus != null) {
            tvAiStatus.setFocusable(false);
            tvAiStatus.setFocusableInTouchMode(false);
            tvAiStatus.setOnClickListener(showPickerListener);
        }
        acModelPicker.setOnItemClickListener((parent, view, position, id) -> {
            if (suppressModelPickerCallback) {
                return;
            }
            if (position < 0 || position >= availableModels.size()) {
                return;
            }
            if (activeSession == null) {
                startNewSession(false);
            }
            if (activeSession != null) {
                activeSession.modelId = availableModels.get(position).id;
                if (sessions.contains(activeSession)) {
                    saveHistory();
                }
                syncModelPickerWithActiveSession();
            }
        });
    }

    private void refreshModelPickerOptions() {
        if (acModelPicker == null || !isAdded()) {
            return;
        }
        availableModels.clear();
        availableModelLabels.clear();
        List<AiConfigStore.AiModelConfig> models = AiConfigStore.getModels(ctx());
        availableModels.addAll(models);
        for (int i = 0; i < availableModels.size(); i++) {
            AiConfigStore.AiModelConfig one = availableModels.get(i);
            availableModelLabels.add(buildModelPickerLabel(one));
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            ctx(),
            R.layout.item_ai_model_dropdown,
            R.id.tvModelDropdownItem,
            availableModelLabels
        );
        acModelPicker.setAdapter(adapter);
        acModelPicker.setEnabled(!availableModels.isEmpty());
        syncModelPickerWithActiveSession();
    }

    private void syncModelPickerWithActiveSession() {
        if (acModelPicker == null) {
            return;
        }
        if (availableModels.isEmpty()) {
            suppressModelPickerCallback = true;
            acModelPicker.setText("未配置模型", false);
            suppressModelPickerCallback = false;
            return;
        }

        if (activeSession == null) {
            suppressModelPickerCallback = true;
            acModelPicker.setText(availableModelLabels.get(0), false);
            suppressModelPickerCallback = false;
            return;
        }

        int targetIndex = findModelIndexById(activeSession.modelId);
        if (targetIndex < 0) {
            activeSession.modelId = availableModels.get(0).id;
            targetIndex = 0;
        }

        suppressModelPickerCallback = true;
        acModelPicker.setText(availableModelLabels.get(targetIndex), false);
        suppressModelPickerCallback = false;
    }

    private String resolveModelDisplayName(@Nullable AiConfigStore.AiModelConfig model) {
        if (model == null) {
            return "";
        }
        String displayName = safe(model.displayName).trim();
        if (!displayName.isEmpty()) {
            return displayName;
        }
        String fallbackModel = safe(model.modelName).trim();
        if (!fallbackModel.isEmpty()) {
            return fallbackModel;
        }
        return "未命名模型";
    }

    private String buildModelPickerLabel(@Nullable AiConfigStore.AiModelConfig model) {
        if (model == null) {
            return "未配置模型";
        }
        return resolveModelDisplayName(model);
    }

    private int findModelIndexById(@Nullable String modelId) {
        if (TextUtils.isEmpty(modelId)) {
            return -1;
        }
        for (int i = 0; i < availableModels.size(); i++) {
            AiConfigStore.AiModelConfig one = availableModels.get(i);
            if (safe(one.id).equals(safe(modelId))) {
                return i;
            }
        }
        return -1;
    }

    @Nullable
    private AiConfigStore.AiModelConfig resolveModelForCurrentSession() {
        if (availableModels.isEmpty()) {
            refreshModelPickerOptions();
        }
        if (availableModels.isEmpty()) {
            return null;
        }
        if (activeSession == null) {
            startNewSession(false);
        }
        if (activeSession == null) {
            return availableModels.get(0);
        }
        int index = findModelIndexById(activeSession.modelId);
        if (index < 0) {
            activeSession.modelId = availableModels.get(0).id;
            index = 0;
        }
        syncModelPickerWithActiveSession();
        return availableModels.get(index);
    }

    private void setupImageAttachment() {
        if (btnAddImage != null) {
            btnAddImage.setOnClickListener(v -> {
                if (imagePickerLauncher != null) {
                    imagePickerLauncher.launch("image/*");
                }
            });
        }
        if (btnRemovePendingImage != null) {
            btnRemovePendingImage.setOnClickListener(v -> clearPendingImage());
        }
        if (ivPendingImage != null) {
            ivPendingImage.setOnClickListener(v -> openImagePreview(getLatestPendingImagePath()));
        }
        refreshPendingImagePreview();
    }

    private void handleImagePicked(@Nullable Uri uri) {
        if (uri == null || !isAdded()) {
            return;
        }
        if (pendingImagePaths.size() >= 4) {
            Toast.makeText(ctx(), "最多只能添加4张图片", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String localPath = copyPickedImageToLocal(uri);
            pendingImagePaths.add(localPath);
            refreshPendingImagePreview();
            refreshComposerDraftUi();
            Toast.makeText(ctx(), "已添加图片（" + pendingImagePaths.size() + "/4）", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(ctx(), "图片处理失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String copyPickedImageToLocal(@NonNull Uri uri) throws Exception {
        Bitmap bitmap;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.Source source = ImageDecoder.createSource(ctx().getContentResolver(), uri);
            bitmap = ImageDecoder.decodeBitmap(source);
        } else {
            try (InputStream in = ctx().getContentResolver().openInputStream(uri)) {
                bitmap = in == null ? null : BitmapFactory.decodeStream(in);
            }
        }
        if (bitmap == null) {
            throw new IllegalStateException("无法读取图片");
        }

        Bitmap toSave = scaleBitmapIfNeeded(bitmap, 960);
        File dir = new File(ctx().getFilesDir(), "ai_chat_images");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("无法创建图片目录");
        }
        File target = new File(dir, "img_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().replace("-", "") + ".jpg");
        try (FileOutputStream out = new FileOutputStream(target)) {
            if (!toSave.compress(Bitmap.CompressFormat.JPEG, 72, out)) {
                throw new IllegalStateException("图片写入失败");
            }
        }

        if (toSave != bitmap) {
            bitmap.recycle();
            toSave.recycle();
        } else {
            bitmap.recycle();
        }
        return target.getAbsolutePath();
    }

    private Bitmap scaleBitmapIfNeeded(@NonNull Bitmap source, int maxSide) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= maxSide && height <= maxSide) {
            return source;
        }
        float scale = Math.min((float) maxSide / (float) width, (float) maxSide / (float) height);
        int targetW = Math.max(1, Math.round(width * scale));
        int targetH = Math.max(1, Math.round(height * scale));
        return Bitmap.createScaledBitmap(source, targetW, targetH, true);
    }

    private void clearPendingImage() {
        pendingImagePaths.clear();
        refreshPendingImagePreview();
        refreshComposerDraftUi();
    }

    @Nullable
    private String getLatestPendingImagePath() {
        if (pendingImagePaths.isEmpty()) {
            return null;
        }
        return pendingImagePaths.get(pendingImagePaths.size() - 1);
    }

    private void refreshPendingImagePreview() {
        if (pendingImageContainer == null || ivPendingImage == null) {
            return;
        }
        if (pendingImagePaths.isEmpty()) {
            pendingImageContainer.setVisibility(View.GONE);
            ivPendingImage.setImageDrawable(null);
            return;
        }
        String latest = getLatestPendingImagePath();
        File file = new File(latest == null ? "" : latest);
        if (!file.exists()) {
            pendingImagePaths.remove(file.getAbsolutePath());
            pendingImageContainer.setVisibility(View.GONE);
            ivPendingImage.setImageDrawable(null);
            return;
        }
        pendingImageContainer.setVisibility(View.VISIBLE);
        ivPendingImage.setImageURI(Uri.fromFile(file));
        if (btnRemovePendingImage != null) {
            btnRemovePendingImage.setContentDescription("清空已选图片（" + pendingImagePaths.size() + "）");
        }
    }

    private void openImagePreview(@Nullable String imagePath) {
        if (!isAdded() || TextUtils.isEmpty(imagePath)) {
            return;
        }
        File imageFile = new File(imagePath);
        if (!imageFile.exists()) {
            Toast.makeText(ctx(), "图片不存在", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(ctx(), AiImagePreviewActivity.class);
        intent.putExtra(AiImagePreviewActivity.EXTRA_IMAGE_PATH, imageFile.getAbsolutePath());
        startActivity(intent);
    }

    private boolean hasPendingImage() {
        return !pendingImagePaths.isEmpty();
    }

    private boolean hasPromptDraft() {
        boolean hasMain = etPrompt != null && etPrompt.getText() != null && !TextUtils.isEmpty(etPrompt.getText().toString().trim());
        boolean hasFullscreen = etPromptFullscreen != null
                && etPromptFullscreen.getText() != null
                && !TextUtils.isEmpty(etPromptFullscreen.getText().toString().trim());
        return hasMain || hasFullscreen;
    }

    private void refreshComposerDraftUi() {
        boolean visible = true;
        if (btnSend != null) {
            btnSend.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        if (btnSendFullscreen != null) {
            btnSendFullscreen.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        updateAddImageButtonPlacement(visible);
    }

    private void updateAddImageButtonPlacement(boolean sendVisible) {
        if (btnAddImage == null || !(btnAddImage.getLayoutParams() instanceof FrameLayout.LayoutParams)) {
            return;
        }

        int targetGravity = Gravity.END | Gravity.CENTER_VERTICAL;
        int targetBottomMargin = 0;
        if (sendVisible && btnSend != null && btnSend.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams sendLp = (FrameLayout.LayoutParams) btnSend.getLayoutParams();
            int verticalMask = sendLp.gravity & Gravity.VERTICAL_GRAVITY_MASK;
            boolean alignBottom = verticalMask == Gravity.BOTTOM;
            targetGravity = alignBottom ? (Gravity.END | Gravity.BOTTOM) : (Gravity.END | Gravity.CENTER_VERTICAL);
            targetBottomMargin = alignBottom ? sendLp.bottomMargin : 0;
        }
        int targetEndMargin = sendVisible ? dp(56) : dp(8);

        FrameLayout.LayoutParams addLp = (FrameLayout.LayoutParams) btnAddImage.getLayoutParams();
        if (addLp.gravity != targetGravity
                || addLp.bottomMargin != targetBottomMargin
                || addLp.getMarginEnd() != targetEndMargin) {
            addLp.gravity = targetGravity;
            addLp.bottomMargin = targetBottomMargin;
            addLp.setMarginEnd(targetEndMargin);
            btnAddImage.setLayoutParams(addLp);
        }
    }

    private void configurePromptInput() {
        if (etPrompt == null) return;
        etPrompt.setMinLines(1);
        etPrompt.setMaxLines(6);
        etPrompt.setHorizontallyScrolling(false);
        etPrompt.setVerticalScrollBarEnabled(false);
        etPrompt.setOverScrollMode(View.OVER_SCROLL_NEVER);
        etPrompt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                syncPromptText(etPrompt, etPromptFullscreen);
                etPrompt.post(() -> updateComposerExpandButtonVisibility());
                refreshComposerDraftUi();
            }
        });
        etPrompt.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if ((right - left) != (oldRight - oldLeft)) {
                updateComposerExpandButtonVisibility();
            }
        });
        etPrompt.setLongClickable(true);
        etPrompt.setOnTouchListener((v, event) -> {
            ViewParent parent = v.getParent();
            if (parent != null && event != null) {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                    parent.requestDisallowInterceptTouchEvent(true);
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    parent.requestDisallowInterceptTouchEvent(false);
                }
            }
            return false;
        });
        etPrompt.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                prepareForImeTransition();
            } else {
                maybeResetComposerImeStateAfterInputBlur();
            }
        });

        if (etPromptFullscreen != null) {
            etPromptFullscreen.setMinLines(8);
            etPromptFullscreen.setMaxLines(120);
            etPromptFullscreen.setHorizontallyScrolling(false);
            etPromptFullscreen.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
            etPromptFullscreen.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    syncPromptText(etPromptFullscreen, etPrompt);
                    etPromptFullscreen.post(() -> updateComposerExpandButtonVisibility());
                    refreshComposerDraftUi();
                }
            });
            etPromptFullscreen.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    prepareForImeTransition();
                } else {
                    maybeResetComposerImeStateAfterInputBlur();
                }
            });
            etPromptFullscreen.setLongClickable(true);
            etPromptFullscreen.setOnTouchListener((v, event) -> {
                ViewParent parent = v.getParent();
                if (parent != null && event != null) {
                    int action = event.getActionMasked();
                    if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                        parent.requestDisallowInterceptTouchEvent(true);
                    } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                        parent.requestDisallowInterceptTouchEvent(false);
                    }
                }
                return false;
            });
        }
        updateComposerExpandButtonVisibility();
        refreshComposerDraftUi();
    }

    private void setupComposerFullscreenControls() {
        if (composerFullscreenOverlay != null) {
            composerFullscreenOverlay.setOnClickListener(v -> setComposerFullscreenMode(false));
        }
        if (composerFullscreenPanel != null) {
            composerFullscreenPanel.setOnClickListener(v -> {
            });
        }
        if (btnExpandComposer != null) {
            btnExpandComposer.setOnClickListener(v -> setComposerFullscreenMode(true));
        }
        if (btnCollapseComposer != null) {
            btnCollapseComposer.setOnClickListener(v -> setComposerFullscreenMode(false));
        }
        if (tvComposerClear != null) {
            tvComposerClear.setOnClickListener(v -> clearPromptInput());
        }
        updateComposerExpandButtonVisibility();
        refreshComposerDraftUi();
    }

    private void clearPromptInput() {
        suppressPromptMirror = true;
        if (etPrompt != null) {
            etPrompt.setText("");
        }
        if (etPromptFullscreen != null) {
            etPromptFullscreen.setText("");
        }
        suppressPromptMirror = false;
        updateComposerExpandButtonVisibility();
        refreshComposerDraftUi();
    }

    private void syncPromptText(@Nullable EditText source, @Nullable EditText target) {
        if (suppressPromptMirror || source == null || target == null) {
            return;
        }
        String sourceText = source.getText() == null ? "" : source.getText().toString();
        String targetText = target.getText() == null ? "" : target.getText().toString();
        if (TextUtils.equals(sourceText, targetText)) {
            return;
        }
        suppressPromptMirror = true;
        target.setText(sourceText);
        target.setSelection(sourceText.length());
        suppressPromptMirror = false;
    }

    private int resolvePromptLineCount(@Nullable EditText input) {
        if (input == null) {
            return 1;
        }
        int lineCount = input.getLineCount();
        if (lineCount > 0) {
            return lineCount;
        }
        String raw = input.getText() == null ? "" : input.getText().toString();
        if (raw.isEmpty()) {
            return 1;
        }
        return Math.max(1, raw.split("\\n", -1).length);
    }

    private void updateComposerExpandButtonVisibility() {
        if (btnExpandComposer == null && btnSend == null) {
            return;
        }
        boolean showExpand = false;
        if (composerFullscreenMode) {
            showExpand = false;
        } else {
            EditText source = etPrompt;
            if (source != null) {
                int lineCount = resolvePromptLineCount(source);
                showExpand = lineCount >= PROMPT_EXPAND_THRESHOLD_LINES;
            }
        }

        if (btnExpandComposer != null) {
            btnExpandComposer.setVisibility(showExpand ? View.VISIBLE : View.GONE);
        }

        if (btnSend != null && btnSend.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) btnSend.getLayoutParams();
            int targetGravity = showExpand ? (Gravity.END | Gravity.BOTTOM) : (Gravity.END | Gravity.CENTER_VERTICAL);
            int targetBottomMargin = showExpand ? dp(4) : 0;
            if (lp.gravity != targetGravity || lp.bottomMargin != targetBottomMargin) {
                lp.gravity = targetGravity;
                lp.bottomMargin = targetBottomMargin;
                btnSend.setLayoutParams(lp);
            }
        }

        updateAddImageButtonPlacement(btnSend != null && btnSend.getVisibility() == View.VISIBLE);
    }

    private void setComposerFullscreenMode(boolean enabled) {
        if (composerFullscreenOverlay == null) {
            return;
        }
        if (composerFullscreenMode == enabled) {
            return;
        }
        composerFullscreenMode = enabled;
        if (enabled) {
            composerFullscreenOverlay.setVisibility(View.VISIBLE);
            composerFullscreenOverlay.bringToFront();
            syncPromptText(etPrompt, etPromptFullscreen);
            if (etPromptFullscreen != null) {
                etPromptFullscreen.requestFocus();
                etPromptFullscreen.post(() -> {
                    if (etPromptFullscreen == null) {
                        return;
                    }
                    int length = etPromptFullscreen.getText() == null ? 0 : etPromptFullscreen.getText().length();
                    etPromptFullscreen.setSelection(length);
                });
            }
        } else {
            composerFullscreenOverlay.setVisibility(View.GONE);
            syncPromptText(etPromptFullscreen, etPrompt);
            if (etPrompt != null) {
                etPrompt.requestFocus();
                etPrompt.post(() -> {
                    if (etPrompt == null) {
                        return;
                    }
                    int length = etPrompt.getText() == null ? 0 : etPrompt.getText().length();
                    etPrompt.setSelection(length);
                });
            }
        }
        prepareForImeTransition();
        updateComposerExpandButtonVisibility();
        refreshComposerDraftUi();
    }

    @Nullable
    private EditText getActivePromptInput() {
        if (composerFullscreenMode && etPromptFullscreen != null) {
            return etPromptFullscreen;
        }
        return etPrompt;
    }

    private void prepareForImeTransition() {
        keepChatAnchoredToBottomOnIme = isChatNearBottom();
        pendingBottomScrollOnImeOpen = keepChatAnchoredToBottomOnIme;
        startImeProbe();
    }

    private void maybeResetComposerImeStateAfterInputBlur() {
        boolean mainFocused = etPrompt != null && etPrompt.hasFocus();
        boolean fullscreenFocused = etPromptFullscreen != null && etPromptFullscreen.hasFocus();
        if (mainFocused || fullscreenFocused) {
            return;
        }
        View root = rootView == null ? null : rootView.findViewById(R.id.rootAiChat);
        WindowInsetsCompat insets = root == null ? null : ViewCompat.getRootWindowInsets(root);
        boolean imeVisible = insets != null && insets.isVisible(WindowInsetsCompat.Type.ime());
        if (!imeVisible) {
            keepChatAnchoredToBottomOnIme = false;
            pendingBottomScrollOnImeOpen = false;
            stopImeProbe();
            applyImeLiftFromSource(0, IME_DRIVER_NONE);
        }
    }

    private void showKeyboard(@Nullable EditText input) {
        if (input == null || !isAdded()) {
            return;
        }
        InputMethodManager imm = (InputMethodManager) ctx().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }
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
        if (btnSend != null) {
            btnSend.setEnabled(true);
            btnSend.setImageResource(stopMode ? R.drawable.ic_stop_solid : R.drawable.ic_send_up);
            btnSend.setContentDescription(stopMode ? "终止对话" : "发送");
        }
        if (btnSendFullscreen != null) {
            btnSendFullscreen.setEnabled(true);
            btnSendFullscreen.setImageResource(stopMode ? R.drawable.ic_stop_solid : R.drawable.ic_send_up);
            btnSendFullscreen.setContentDescription(stopMode ? "终止对话" : "发送");
        }
        refreshComposerDraftUi();
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
        if (mainBottomNavHost != null) {
            mainBottomNavHost.post(() -> {
                if (mainBottomNavHost == null) {
                    return;
                }
                int measuredHeight = mainBottomNavHost.getHeight();
                if (measuredHeight > 0 && measuredHeight != mainBottomNavHostHeight) {
                    mainBottomNavHostHeight = measuredHeight;
                }
                updateHistoryDrawerBottomInset();
            });
        }
        updateHistoryDrawerBottomInset();
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

    private void setMainBottomNavVisible(boolean visible) {
        if (mainBottomNavHost == null) {
            return;
        }
        int targetVisibility = visible ? View.VISIBLE : View.INVISIBLE;
        if (mainBottomNavHost.getVisibility() != targetVisibility) {
            mainBottomNavHost.setVisibility(targetVisibility);
        }
    }

    private int computeHistoryDrawerBottomInset() {
        View root = rootView == null ? null : rootView.findViewById(R.id.rootAiChat);
        if (mainBottomNavHost == null || root == null || root.getHeight() <= 0 || mainBottomNavHost.getHeight() <= 0) {
            return 0;
        }

        int[] rootLoc = new int[2];
        int[] navLoc = new int[2];
        root.getLocationOnScreen(rootLoc);
        mainBottomNavHost.getLocationOnScreen(navLoc);
        int rootBottom = rootLoc[1] + root.getHeight();
        int navTop = navLoc[1];
        return Math.max(0, rootBottom - navTop);
    }

    private void updateHistoryDrawerBottomInset() {
        if (historyDrawer == null) {
            return;
        }
        ViewGroup.LayoutParams rawParams = historyDrawer.getLayoutParams();
        if (!(rawParams instanceof DrawerLayout.LayoutParams)) {
            return;
        }
        DrawerLayout.LayoutParams lp = (DrawerLayout.LayoutParams) rawParams;
        int desiredBottomInset = computeHistoryDrawerBottomInset();
        if (lp.bottomMargin == desiredBottomInset) {
            return;
        }
        lp.bottomMargin = desiredBottomInset;
        historyDrawer.setLayoutParams(lp);
    }

    private void setupHistoryDrawerOverlayBehavior() {
        if (drawerAiChat == null) {
            return;
        }
        if (historyDrawerOverlayListener != null) {
            drawerAiChat.removeDrawerListener(historyDrawerOverlayListener);
        }
        historyDrawerOverlayListener = new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {
                ensureMainBottomNavVisible();
                updateHistoryDrawerBottomInset();
            }

            @Override
            public void onDrawerOpened(@NonNull View drawerView) {
                ensureMainBottomNavVisible();
                updateHistoryDrawerBottomInset();
            }

            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
                ensureMainBottomNavVisible();
                updateHistoryDrawerBottomInset();
            }
        };
        drawerAiChat.addDrawerListener(historyDrawerOverlayListener);
        ensureMainBottomNavVisible();
    }

    private int getMainBottomNavEffectiveHeight() {
        int height = 0;
        if (mainBottomNavHost != null) {
            height = mainBottomNavHost.getHeight();
            if (height <= 0 && mainBottomNavHost.getLayoutParams() != null) {
                height = mainBottomNavHost.getLayoutParams().height;
            }
        }
        if (height <= 0) {
            height = mainBottomNavHostHeight;
        }
        return Math.max(0, height);
    }

    private int resolveSystemBottomInsetForIme(@NonNull View root) {
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(root);
        if (insets == null) {
            return 0;
        }
        Insets navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
        Insets gestures = insets.getInsets(WindowInsetsCompat.Type.systemGestures());
        return Math.max(0, Math.max(navBars.bottom, gestures.bottom));
    }

    private int computeComposerLiftForIme(@NonNull View root, int imeBottom) {
        int keyboard = Math.max(0, imeBottom);
        if (keyboard == 0) {
            return 0;
        }
        int reservedBottom = getMainBottomNavEffectiveHeight();
        if (reservedBottom <= 0) {
            reservedBottom = computeRootBottomInsetToWindow(root);
        }
        // Android 12 及以下常见 IME 高度包含系统底栏，需额外扣减避免输入框上方留白。
        if (shouldPreferFallbackImeDriver()) {
            reservedBottom += resolveSystemBottomInsetForIme(root);
        }
        return Math.max(0, keyboard - reservedBottom);
    }

    private int computeRootBottomInsetToWindow(@NonNull View root) {
        View windowRoot = null;
        if (getActivity() != null && getActivity().getWindow() != null) {
            windowRoot = getActivity().getWindow().getDecorView();
        }
        if (windowRoot == null || windowRoot.getHeight() <= 0) {
            windowRoot = root.getRootView();
        }
        if (windowRoot == null || windowRoot.getHeight() <= 0) {
            return 0;
        }
        int[] rootLocation = new int[2];
        int[] windowLocation = new int[2];
        root.getLocationOnScreen(rootLocation);
        windowRoot.getLocationOnScreen(windowLocation);
        int rootBottom = rootLocation[1] + root.getHeight();
        int windowBottom = windowLocation[1] + windowRoot.getHeight();
        return Math.max(0, windowBottom - rootBottom);
    }

    private int computeEffectiveImeBottom(@NonNull View root, @NonNull WindowInsetsCompat insets) {
        if (!insets.isVisible(WindowInsetsCompat.Type.ime())) {
            return 0;
        }
        return Math.max(0, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom);
    }

    private int estimateKeyboardHeightByVisibleFrame(@NonNull View root) {
        Rect visibleFrame = new Rect();
        root.getWindowVisibleDisplayFrame(visibleFrame);
        View rootView = root.getRootView();
        int rootHeight = rootView == null ? 0 : rootView.getHeight();
        if (rootHeight <= 0) {
            return 0;
        }
        return Math.max(0, rootHeight - visibleFrame.bottom);
    }

    private void markInsetsDispatched() {
        lastInsetsDispatchUptime = SystemClock.uptimeMillis();
    }

    private boolean shouldPreferFallbackImeDriver() {
        return android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.S;
    }

    private boolean isInsetsDispatchActive() {
        if (shouldPreferFallbackImeDriver()) {
            return false;
        }
        return SystemClock.uptimeMillis() - lastInsetsDispatchUptime <= INSETS_PRIORITY_HOLD_MS;
    }

    private void applyImeLiftFromSource(int lift, int source) {
        int effectiveSource = source;
        if (shouldPreferFallbackImeDriver() && source == IME_DRIVER_INSETS) {
            effectiveSource = IME_DRIVER_FALLBACK;
        }
        int normalizedLift = Math.max(0, lift);
        if (effectiveSource == IME_DRIVER_FALLBACK && (imeDriver == IME_DRIVER_INSETS || isInsetsDispatchActive())) {
            return;
        }
        if (effectiveSource == IME_DRIVER_INSETS) {
            imeDriver = IME_DRIVER_INSETS;
            stopImeProbe();
        } else if (effectiveSource == IME_DRIVER_FALLBACK) {
            if (imeDriver == IME_DRIVER_NONE) {
                imeDriver = IME_DRIVER_FALLBACK;
            }
        } else {
            imeDriver = IME_DRIVER_NONE;
        }
        applyComposerImeOffset(normalizedLift);
        applyChatScrollBottomPadding(normalizedLift);
        anchorChatToBottomIfNeeded();
    }

    private void applyImeInsetBehavior() {
        View root = rootView == null ? null : rootView.findViewById(R.id.rootAiChat);
        if (root == null) return;

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            boolean imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            Insets statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            Insets navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            Insets gestureInsets = insets.getInsets(WindowInsetsCompat.Type.systemGestures());
            int imeBottom = computeEffectiveImeBottom(root, insets);
            if (imeVisible && imeBottom > 0) {
                markInsetsDispatched();
            }
            int appBottomNavHeight = getMainBottomNavEffectiveHeight();

            // 在主界面 fragment 中，底部空间由应用底栏占用；仅在无应用底栏时回退系统底 inset。
            int targetBottomPadding;
            if (appBottomNavHeight > 0) {
                targetBottomPadding = 0;
            } else {
                targetBottomPadding = imeVisible ? 0 : Math.max(navBars.bottom, gestureInsets.bottom);
            }

            if (root.getPaddingTop() != statusBars.top || root.getPaddingBottom() != targetBottomPadding) {
                root.setPadding(
                    root.getPaddingLeft(),
                    statusBars.top,
                    root.getPaddingRight(),
                    targetBottomPadding
                );
            }
            applyHistoryDrawerInsets(statusBars.top);

            int lift = (imeVisible && imeBottom > 0) ? computeComposerLiftForIme(root, imeBottom) : 0;
            // 使用标准 WindowInsets + WindowInsetsAnimation 流程：
            // 动画中由 onProgress 驱动，稳态由 onApplyWindowInsets 收敛。
            if (!imeAnimationRunning) {
                if (imeVisible && imeBottom > 0) {
                    applyImeLiftFromSource(lift, IME_DRIVER_INSETS);
                } else if (!imeVisible) {
                    applyImeLiftFromSource(0, IME_DRIVER_NONE);
                }
            }

            if (imeVisible && !lastImeVisible) {
                if (pendingBottomScrollOnImeOpen && chatScroll != null) {
                    scrollChatToBottom();
                }
                pendingBottomScrollOnImeOpen = false;
            } else if (!imeVisible && lastImeVisible) {
                keepChatAnchoredToBottomOnIme = false;
                pendingBottomScrollOnImeOpen = false;
                stopImeProbe();
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
                            int imeBottom = computeEffectiveImeBottom(root, insets);
                            if (imeBottom > 0) {
                                markInsetsDispatched();
                                int lift = computeComposerLiftForIme(root, imeBottom);
                                applyImeLiftFromSource(lift, IME_DRIVER_INSETS);
                            }
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
                                    : computeEffectiveImeBottom(root, currentInsets);
                            imeAnimationRunning = false;
                            if (imeVisibleNow && imeBottomNow > 0) {
                                int lift = computeComposerLiftForIme(root, imeBottomNow);
                                applyImeLiftFromSource(lift, IME_DRIVER_INSETS);
                            } else {
                                applyImeLiftFromSource(0, IME_DRIVER_NONE);
                            }
                            ViewCompat.requestApplyInsets(root);
                        }
                    }
                });

        setupImeGlobalLayoutFallback(root);
        ViewCompat.requestApplyInsets(root);
    }

    private void applyHistoryDrawerInsets(int statusBarTopInset) {
        if (historyDrawer == null) {
            return;
        }
        int targetTop = baseHistoryDrawerPaddingTop + Math.max(0, statusBarTopInset);
        int targetBottom = baseHistoryDrawerPaddingBottom;
        if (historyDrawer.getPaddingTop() == targetTop && historyDrawer.getPaddingBottom() == targetBottom) {
            return;
        }
        historyDrawer.setPadding(
                historyDrawer.getPaddingLeft(),
                targetTop,
                historyDrawer.getPaddingRight(),
                targetBottom
        );
    }

    private void startImeProbe() {
        View root = imeFallbackRoot;
        if (root == null && rootView != null) {
            root = rootView.findViewById(R.id.rootAiChat);
        }
        if (root == null) {
            return;
        }
        imeProbeDeadlineUptime = SystemClock.uptimeMillis() + IME_PROBE_DURATION_MS;
        if (imeProbeTicker != null) {
            return;
        }
        imeProbeTicker = new Runnable() {
            @Override
            public void run() {
                View probeRoot = imeFallbackRoot;
                if (probeRoot == null && rootView != null) {
                    probeRoot = rootView.findViewById(R.id.rootAiChat);
                }
                if (probeRoot == null) {
                    stopImeProbe();
                    return;
                }

                // WindowInsets 正常分发时，优先由其驱动动画，probe 仅作兜底。
                boolean preferFallback = shouldPreferFallbackImeDriver();
                if (!preferFallback && (imeDriver == IME_DRIVER_INSETS || isInsetsDispatchActive())) {
                    if (SystemClock.uptimeMillis() < imeProbeDeadlineUptime) {
                        streamHandler.postDelayed(this, IME_PROBE_INTERVAL_MS);
                    } else {
                        stopImeProbe();
                    }
                    return;
                }

                WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(probeRoot);
                int fallbackVisibleHeight = estimateKeyboardHeightByVisibleFrame(probeRoot);
                int navBottom = insets == null
                        ? 0
                        : Math.max(0, insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom);
                int visibleThreshold = Math.max(dp(80), navBottom + dp(24));

                boolean imeVisibleByInsets = insets != null && insets.isVisible(WindowInsetsCompat.Type.ime());
                boolean imeLikelyVisible = imeVisibleByInsets || fallbackVisibleHeight > visibleThreshold;
                int imeBottom = 0;
                if (insets != null && imeVisibleByInsets) {
                    imeBottom = computeEffectiveImeBottom(probeRoot, insets);
                }
                if (imeBottom <= 0 && imeLikelyVisible) {
                    imeBottom = fallbackVisibleHeight;
                }

                if (imeVisibleByInsets && imeBottom > 0) {
                    int lift = computeComposerLiftForIme(probeRoot, imeBottom);
                    applyImeLiftFromSource(lift, IME_DRIVER_INSETS);
                    stopImeProbe();
                    return;
                }

                if (imeLikelyVisible) {
                    int lift = computeComposerLiftForIme(probeRoot, imeBottom);
                    applyImeLiftFromSource(lift, IME_DRIVER_FALLBACK);
                } else {
                    applyImeLiftFromSource(0, IME_DRIVER_NONE);
                }

                boolean keepRunning = SystemClock.uptimeMillis() < imeProbeDeadlineUptime;
                if (keepRunning) {
                    streamHandler.postDelayed(this, IME_PROBE_INTERVAL_MS);
                } else {
                    stopImeProbe();
                }
            }
        };
        streamHandler.post(imeProbeTicker);
    }

    private void stopImeProbe() {
        if (imeProbeTicker != null) {
            streamHandler.removeCallbacks(imeProbeTicker);
            imeProbeTicker = null;
            imeProbeDeadlineUptime = 0L;
        }
    }

    private void setupImeGlobalLayoutFallback(@NonNull View root) {
        clearImeGlobalLayoutFallback();
        imeFallbackRoot = root;
        lastGlobalKeyboardHeight = Integer.MIN_VALUE;
        lastGlobalImeVisible = false;

        imeGlobalLayoutListener = () -> {
            View fallbackRoot = imeFallbackRoot;
            if (fallbackRoot == null) {
                return;
            }

            boolean preferFallback = shouldPreferFallbackImeDriver();
            if (!preferFallback && (imeDriver == IME_DRIVER_INSETS || isInsetsDispatchActive())) {
                return;
            }

            int fallbackVisibleHeight = estimateKeyboardHeightByVisibleFrame(fallbackRoot);
            WindowInsetsCompat currentInsets = ViewCompat.getRootWindowInsets(fallbackRoot);
            int navBottom = currentInsets == null
                    ? 0
                    : Math.max(0, currentInsets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom);
            int visibleThreshold = Math.max(dp(80), navBottom + dp(24));
            boolean imeVisibleByInsets = currentInsets != null && currentInsets.isVisible(WindowInsetsCompat.Type.ime());
            int imeBottomByInsets = currentInsets == null ? 0 : computeEffectiveImeBottom(fallbackRoot, currentInsets);
            boolean imeLikelyVisible = imeVisibleByInsets || fallbackVisibleHeight > visibleThreshold;
            int imeBottomForLift;
            if (imeVisibleByInsets && imeBottomByInsets > 0) {
                imeBottomForLift = imeBottomByInsets;
            } else if (imeLikelyVisible) {
                imeBottomForLift = fallbackVisibleHeight;
            } else {
                imeBottomForLift = 0;
            }

            if (imeVisibleByInsets && imeBottomByInsets > 0) {
                int lift = computeComposerLiftForIme(fallbackRoot, imeBottomByInsets);
                applyImeLiftFromSource(lift, IME_DRIVER_INSETS);
                stopImeProbe();
            } else if (imeLikelyVisible) {
                int lift = computeComposerLiftForIme(fallbackRoot, imeBottomForLift);
                applyImeLiftFromSource(lift, IME_DRIVER_FALLBACK);
            } else {
                applyImeLiftFromSource(0, IME_DRIVER_NONE);
            }
            lastGlobalKeyboardHeight = fallbackVisibleHeight;
            lastGlobalImeVisible = imeLikelyVisible;
        };

        ViewTreeObserver observer = root.getViewTreeObserver();
        if (observer.isAlive()) {
            observer.addOnGlobalLayoutListener(imeGlobalLayoutListener);
        }
    }

    private void clearImeGlobalLayoutFallback() {
        if (imeFallbackRoot == null || imeGlobalLayoutListener == null) {
            imeFallbackRoot = null;
            imeGlobalLayoutListener = null;
            return;
        }
        ViewTreeObserver observer = imeFallbackRoot.getViewTreeObserver();
        if (observer.isAlive()) {
            observer.removeOnGlobalLayoutListener(imeGlobalLayoutListener);
        }
        imeFallbackRoot = null;
        imeGlobalLayoutListener = null;
        lastGlobalKeyboardHeight = Integer.MIN_VALUE;
        lastGlobalImeVisible = false;
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
        scrollChatToBottom();
    }

    private void scrollChatToBottom() {
        if (chatScroll == null || chatContainer == null) {
            return;
        }
        chatScroll.post(() -> {
            if (chatScroll == null || chatContainer == null) {
                return;
            }
            int contentBottom = chatContainer.getBottom() + chatContainer.getPaddingBottom();
            int targetY = Math.max(0, contentBottom - chatScroll.getHeight() + chatScroll.getPaddingBottom());
            chatScroll.scrollTo(0, targetY);
        });
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
            lvHistory.setSelector(new ColorDrawable(Color.TRANSPARENT));
            lvHistory.setDrawSelectorOnTop(false);
            lvHistory.setCacheColorHint(Color.TRANSPARENT);
            lvHistory.setDivider(null);
            lvHistory.setDividerHeight(0);
            lvHistory.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {
                ChatSession target = getSessionForRow(position);
                if (target == null) {
                    return;
                }
                clearHistoryHighlight();
                activeSession = target;
                clearPendingImage();
                syncModelPickerWithActiveSession();
                renderActiveSession();
                historyAdapter.notifyDataSetChanged();
                if (drawerAiChat != null) {
                    drawerAiChat.closeDrawer(GravityCompat.START);
                }
            });
            lvHistory.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    clearHistoryHighlight();
                }
            });
        }
        updateHistoryDrawerBottomInset();
    }

    private void startNewSession(boolean forceSave) {
        ChatSession session = new ChatSession();
        session.id = UUID.randomUUID().toString();
        session.title = "新对话";
        session.titleFromAi = false;
        session.updatedAt = System.currentTimeMillis();
        session.messages = new ArrayList<>();
        session.modelId = resolveDefaultModelId();
        activeSession = session;
        clearPendingImage();
        syncModelPickerWithActiveSession();
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
            if (TextUtils.isEmpty(activeSession.modelId)) {
                activeSession.modelId = resolveDefaultModelId();
            }
            syncModelPickerWithActiveSession();
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
            syncModelPickerWithActiveSession();
            refreshAiStatus();
            return;
        }
        for (int i = 0; i < activeSession.messages.size(); i++) {
            ChatMessage one = activeSession.messages.get(i);
            String role = safe(one.role).trim().toLowerCase(Locale.ROOT);
            boolean isUser = "user".equals(role);
            boolean isSystem = "system".equals(role);
            if (isSystem || (!isUser && isToolFeedbackMessage(one.content))) {
                addSystemMessage(one.content, false);
                continue;
            }
            if (!TextUtils.isEmpty(one.imagePath)) {
                addImageBubble(isUser, parseImagePaths(one.imagePath), one.content, false, i);
                continue;
            }
            addBubble(isUser, one.content, false, false, i);
        }
        syncModelPickerWithActiveSession();
        refreshAiStatus();
        scrollChatToBottom();
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
                    session.modelId = obj.optString("modelId", resolveDefaultModelId());
                    session.messages = new ArrayList<>();
                    JSONArray msgArr = obj.optJSONArray("messages");
                    if (msgArr != null) {
                        for (int j = 0; j < msgArr.length(); j++) {
                            JSONObject msgObj = msgArr.optJSONObject(j);
                            if (msgObj == null) continue;
                            ChatMessage msg = new ChatMessage();
                            msg.role = msgObj.optString("role", "assistant");
                            msg.content = msgObj.optString("content", "");
                            msg.imagePath = msgObj.optString("imagePath", "");
                            if (!TextUtils.isEmpty(msg.content) || !TextUtils.isEmpty(msg.imagePath)) {
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
                obj.put("modelId", safe(one.modelId));
                JSONArray msgArr = new JSONArray();
                if (one.messages != null) {
                    for (ChatMessage msg : one.messages) {
                        JSONObject msgObj = new JSONObject();
                        msgObj.put("role", safe(msg.role));
                        msgObj.put("content", safe(msg.content));
                        msgObj.put("imagePath", safe(msg.imagePath));
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
        if (countNonEmptyMessages(session) < 2) {
            return false;
        }
        String title = safe(session.title).trim();
        if (title.isEmpty() || "新对话".equals(title)) {
            String fallbackTitle = deriveFallbackSessionTitle(session);
            if (!TextUtils.isEmpty(fallbackTitle) && !"新对话".equals(fallbackTitle)) {
                session.title = fallbackTitle;
                session.titleFromAi = false;
                title = fallbackTitle;
            }
        }
        return !title.isEmpty() && !"新对话".equals(title);
    }

    private int countNonEmptyMessages(ChatSession session) {
        if (session == null || session.messages == null) {
            return 0;
        }
        int count = 0;
        for (ChatMessage msg : session.messages) {
            if (msg != null && (!TextUtils.isEmpty(msg.content) || !TextUtils.isEmpty(msg.imagePath))) {
                count++;
            }
        }
        return count;
    }

    @NonNull
    private String deriveFallbackSessionTitle(@Nullable ChatSession session) {
        if (session == null || session.messages == null || session.messages.isEmpty()) {
            return "新对话";
        }
        for (ChatMessage msg : session.messages) {
            if (msg == null) {
                continue;
            }
            String role = safe(msg.role).trim().toLowerCase(Locale.ROOT);
            if (!"user".equals(role)) {
                continue;
            }

            String normalizedText = normalizeTitleCandidate(msg.content);
            if (!normalizedText.isEmpty()) {
                return normalizedText;
            }

            if (!TextUtils.isEmpty(msg.imagePath)) {
                return "图片对话";
            }
        }
        return "新对话";
    }

    @NonNull
    private String normalizeTitleCandidate(@Nullable String raw) {
        String normalized = safe(raw)
                .replace("\r", " ")
                .replace("\n", " ")
                .trim()
                .replaceAll("\\s+", " ");
        if (normalized.isEmpty() || normalized.startsWith("/")) {
            return "";
        }
        final int maxLen = 18;
        if (normalized.length() > maxLen) {
            return normalized.substring(0, maxLen) + "...";
        }
        return normalized;
    }

    private void appendMessageToHistory(boolean isUser, String text) {
        appendMessageToHistoryByRole(isUser ? "user" : "assistant", text, null);
    }

    private void appendMessageToHistory(boolean isUser, String text, @Nullable List<String> imagePaths) {
        appendMessageToHistoryByRole(isUser ? "user" : "assistant", text, serializeImagePaths(imagePaths));
    }

    private void appendMessageToHistoryByRole(@NonNull String role, String text) {
        appendMessageToHistoryByRole(role, text, null);
    }

    private void appendMessageToHistoryByRole(@NonNull String role, String text, @Nullable String imagePath) {
        if (TextUtils.isEmpty(text) && TextUtils.isEmpty(imagePath)) {
            return;
        }
        if (activeSession == null) {
            startNewSession(false);
        }
        if (activeSession == null) {
            return;
        }
        if (TextUtils.isEmpty(activeSession.modelId)) {
            activeSession.modelId = resolveDefaultModelId();
        }
        ChatMessage msg = new ChatMessage();
        msg.role = role;
        msg.content = safe(text);
        msg.imagePath = safe(imagePath);
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
        int fieldBg = ColorUtils.blendARGB(UiStyleHelper.resolveGlassCardColor(ctx()), UiStyleHelper.resolvePageBackgroundColor(ctx()), 0.12f);
        int fullscreenBg = ColorUtils.blendARGB(UiStyleHelper.resolvePageBackgroundColor(ctx()), Color.WHITE, isDarkMode() ? 0.10f : 0.92f);

        if (composerCard != null) {
            composerCard.setCardBackgroundColor(Color.TRANSPARENT);
            composerCard.setStrokeColor(Color.TRANSPARENT);
            composerCard.setStrokeWidth(0);
            composerCard.setClipToOutline(true);
            composerCard.setClipChildren(true);
        }
        if (composerInner != null) {
            GradientDrawable composerBg = new GradientDrawable();
            composerBg.setCornerRadius(dp(28));
            composerBg.setColor(fieldBg);
            composerBg.setStroke(dp(1), outline);
            composerInner.setBackground(composerBg);
            composerInner.setClipToOutline(true);
        }
        if (etPrompt != null) {
            etPrompt.setTextColor(onSurface);
            etPrompt.setHintTextColor(ColorUtils.setAlphaComponent(onSurface, 146));
            etPrompt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
        }
        if (acModelPicker != null) {
            int pickerText = ColorUtils.setAlphaComponent(onSurface, 184);
            acModelPicker.setTextColor(pickerText);
            acModelPicker.setHintTextColor(ColorUtils.setAlphaComponent(pickerText, 138));
            acModelPicker.setBackground(null);
            GradientDrawable dropdownBg = new GradientDrawable();
            dropdownBg.setShape(GradientDrawable.RECTANGLE);
            dropdownBg.setCornerRadius(dp(14));
            int dropdownFill = ColorUtils.blendARGB(UiStyleHelper.resolvePageBackgroundColor(ctx()), Color.WHITE, isDarkMode() ? 0.08f : 0.92f);
            dropdownBg.setColor(dropdownFill);
            dropdownBg.setStroke(dp(1), ColorUtils.setAlphaComponent(onSurface, 34));
            acModelPicker.setDropDownBackgroundDrawable(dropdownBg);
        }
        if (btnAddImage != null) {
            btnAddImage.setImageTintList(android.content.res.ColorStateList.valueOf(ColorUtils.setAlphaComponent(onSurface, 196)));
            btnAddImage.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ColorUtils.setAlphaComponent(onSurface, 20)));
        }
        if (btnRemovePendingImage != null) {
            btnRemovePendingImage.setImageTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(ctx(), R.color.ai_close_button_icon)));
        }
        if (etPromptFullscreen != null) {
            etPromptFullscreen.setTextColor(onSurface);
            etPromptFullscreen.setHintTextColor(ColorUtils.setAlphaComponent(onSurface, 146));
            etPromptFullscreen.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        }
        if (composerFullscreenPanel != null) {
            GradientDrawable fullscreenPanelBg = new GradientDrawable();
            float corner = dp(22);
            fullscreenPanelBg.setCornerRadii(new float[]{corner, corner, corner, corner, 0f, 0f, 0f, 0f});
            fullscreenPanelBg.setColor(fullscreenBg);
            fullscreenPanelBg.setStroke(dp(1), ColorUtils.setAlphaComponent(onSurface, 26));
            composerFullscreenPanel.setBackground(fullscreenPanelBg);
        }
        if (btnExpandComposer != null) {
            btnExpandComposer.setImageTintList(android.content.res.ColorStateList.valueOf(ColorUtils.setAlphaComponent(onSurface, 172)));
        }
        if (btnCollapseComposer != null) {
            btnCollapseComposer.setImageTintList(android.content.res.ColorStateList.valueOf(ColorUtils.setAlphaComponent(onSurface, 172)));
        }
        if (tvComposerClear != null) {
            tvComposerClear.setTextColor(accent);
        }
        if (btnSend != null) {
            btnSend.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentFill));
            btnSend.setImageTintList(android.content.res.ColorStateList.valueOf(pickReadableTextColor(accentFill)));
            updateSendButtonMode(isAiConversationRunning());
        }
        if (btnSendFullscreen != null) {
            btnSendFullscreen.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentFill));
            btnSendFullscreen.setImageTintList(android.content.res.ColorStateList.valueOf(pickReadableTextColor(accentFill)));
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

        int drawerBase = UiStyleHelper.resolvePageBackgroundColor(ctx());
        int textPrimary = UiStyleHelper.resolveOnSurfaceColor(ctx());
        int textSecondary = UiStyleHelper.resolveOnSurfaceVariantColor(ctx());

        GradientDrawable drawerBg = new GradientDrawable();
        drawerBg.setShape(GradientDrawable.RECTANGLE);
        drawerBg.setColor(drawerBase);
        historyDrawer.setBackground(drawerBg);
        historyDrawer.setElevation(0f);
        historyDrawer.setTranslationZ(0f);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            historyDrawer.setOutlineAmbientShadowColor(ColorUtils.setAlphaComponent(Color.BLACK, 0));
            historyDrawer.setOutlineSpotShadowColor(ColorUtils.setAlphaComponent(Color.BLACK, 0));
        }
        updateHistoryDrawerBottomInset();

        if (drawerAiChat != null) {
            drawerAiChat.setDrawerElevation(0f);
            int scrimAlpha = isDarkMode() ? 142 : 112;
            drawerAiChat.setScrimColor(ColorUtils.setAlphaComponent(Color.BLACK, scrimAlpha));
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
        int bgColor = dark ? Color.parseColor("#2B2B2B") : Color.WHITE;
        int textPrimary = dark ? Color.WHITE : Color.BLACK;
        int textDanger = Color.parseColor("#E84B4B");

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        
        MaterialCardView card = new MaterialCardView(context);
        card.setCardElevation(dp(14));
        card.setRadius(dp(24));
        card.setCardBackgroundColor(bgColor);
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(ColorUtils.setAlphaComponent(textPrimary, 18));
        card.setUseCompatPadding(true);
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
        sessionMenuPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        sessionMenuPopup.setOutsideTouchable(true);
        sessionMenuPopup.setElevation(dp(8));
        sessionMenuPopup.setOnDismissListener(() -> {
            sessionMenuPopup = null;
            clearHistoryHighlight();
        });

        if (lastHistoryTouchRawX > 0f && lastHistoryTouchRawY > 0f) {
            card.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            int popupW = card.getMeasuredWidth();
            int popupH = card.getMeasuredHeight();
            int screenW = getResources().getDisplayMetrics().widthPixels;
            int screenH = getResources().getDisplayMetrics().heightPixels;
            int x = (int) lastHistoryTouchRawX - dp(8);
            int y = (int) lastHistoryTouchRawY - dp(8);
            x = Math.max(0, Math.min(Math.max(0, screenW - popupW), x));
            y = Math.max(0, Math.min(Math.max(0, screenH - popupH), y));
            sessionMenuPopup.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y);
        } else if (lvHistory != null) {
            sessionMenuPopup.showAsDropDown(anchor, dp(4), dp(4));
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

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(new androidx.appcompat.view.ContextThemeWrapper(ctx(), com.google.android.material.R.style.Theme_Material3_DayNight_Dialog_Alert))
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
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(new androidx.appcompat.view.ContextThemeWrapper(ctx(), com.google.android.material.R.style.Theme_Material3_DayNight_Dialog_Alert))
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
            syncModelPickerWithActiveSession();
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
            syncModelPickerWithActiveSession();
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

        EditText activeInput = getActivePromptInput();
        if (activeInput == null) {
            return;
        }
        String rawUserText = activeInput.getText() == null ? "" : activeInput.getText().toString().trim();
        final boolean hasImage = hasPendingImage();
        if (rawUserText.isEmpty() && !hasImage) {
            return;
        }
        if (rawUserText.isEmpty() && hasImage) {
            Toast.makeText(ctx(), "请先输入文字后再发送图片", Toast.LENGTH_SHORT).show();
            return;
        }

        AiConfigStore.AiModelConfig selectedModel = resolveModelForCurrentSession();
        if (selectedModel == null) {
            addSystemMessage(buildApiConfigCardPayload(
                    "尚未配置AI模型",
                    "请先前往 大模型设置 添加模型并填写 API Key。"
            ), true);
            return;
        }
        if (TextUtils.isEmpty(selectedModel.apiKey)) {
            addSystemMessage(buildApiConfigCardPayload(
                    "模型缺少 API Key",
                    "请先配置模型的 API Key，配置后即可继续提问。"
            ), true);
            return;
        }
        if (TextUtils.isEmpty(selectedModel.modelName)) {
            addSystemMessage(buildApiConfigCardPayload(
                    "模型缺少模型名",
                    "请先填写模型名/接入点 ID，配置完成后再试。"
            ), true);
            return;
        }
        if (hasImage && !selectedModel.multimodal) {
            Toast.makeText(ctx(), "模型未启用多模态，请到模型设置中开启后重试", Toast.LENGTH_SHORT).show();
            return;
        }

        final boolean requestTitleInFinalAnswer = shouldRequestModelTitleForCurrentTurn();
        final String modelPromptText = rawUserText;
        final String contextAwarePromptText = buildContextAwarePromptText(modelPromptText);
        final AiGateway.RequestCacheHint cacheHint = buildRequestCacheHint(selectedModel.modelName, contextAwarePromptText);
        final List<String> imagesForRequest = hasImage
                ? new ArrayList<>(pendingImagePaths.subList(0, Math.min(4, pendingImagePaths.size())))
                : java.util.Collections.emptyList();

        if (hasImage) {
            addImageBubble(true, imagesForRequest, rawUserText, true, -1);
        } else {
            addBubble(true, rawUserText, false);
        }
        clearPromptInput();
        clearPendingImage();
        if (composerFullscreenMode) {
            setComposerFullscreenMode(false);
        }

        if (!hasImage && "/memories".equalsIgnoreCase(rawUserText)) {
            addBubble(false, MemorySkillManager.readMemories(ctx()), false);
            return;
        }
        if (!hasImage && rawUserText.startsWith("/memory ")) {
            String result = MemorySkillManager.appendMemory(ctx(), rawUserText.substring(8));
            addBubble(false, result, false);
            return;
        }
        if (!hasImage && rawUserText.startsWith("/memory-del ")) {
            String arg = rawUserText.substring(12).trim();
            String result;
            if (arg.matches("\\d+")) {
                result = MemorySkillManager.deleteMemoryByIndex(ctx(), Integer.parseInt(arg));
            } else {
                result = MemorySkillManager.deleteMemoryByKeyword(ctx(), arg);
            }
            addBubble(false, result, false);
            return;
        }
        if (!hasImage && rawUserText.startsWith("/memory-edit ")) {
            String[] parts = rawUserText.substring(13).trim().split("\\s+", 2);
            if (parts.length < 2 || !parts[0].matches("\\d+")) {
                addBubble(false, "修改失败：命令格式应为 /memory-edit <序号> <内容>", false);
                return;
            }
            int index = Integer.parseInt(parts[0]);
            String result = MemorySkillManager.updateMemoryByIndex(ctx(), index, parts[1]);
            addBubble(false, result, false);
            return;
        }
        if (!hasImage && "/memory-clear".equalsIgnoreCase(rawUserText)) {
            addBubble(false, MemorySkillManager.clearMemories(ctx()), false);
            return;
        }
        if (!hasImage && rawUserText.startsWith("/cmd ")) {
            String cmd = rawUserText.substring(5).trim();
            List<String> manualCommands = SkillCommandCenter.extractCommands("CMD: " + cmd);
            if (manualCommands.isEmpty()) {
                manualCommands = java.util.Collections.singletonList(cmd);
            }
            SkillCommandCenter.CommandBatchResult one = SkillCommandCenter.executeCommandsWithFeedback(ctx(), manualCommands);
            addSystemMessage(one.userFeedback, true);
            return;
        }

        final long requestToken = beginAiConversationRequest();
        updateSendButtonMode(true);
        addBubble(false, "思考中...", true);

        final String provider = selectedModel.provider;
        final String baseUrl = selectedModel.baseUrl;
        final String model = selectedModel.modelName;
        final String apiKey = selectedModel.apiKey;
        final String modelId = safe(selectedModel.id);
        final boolean skillEnabled = AiConfigStore.isSkillEnabled(ctx());
        final boolean memorySkillEnabled = AiConfigStore.isMemorySkillEnabled(ctx());
        final boolean courseSkillEnabled = AiConfigStore.isCourseSkillEnabled(ctx());
        final boolean navigationSkillEnabled = AiConfigStore.isNavigationSkillEnabled(ctx());
        final boolean classroomSkillEnabled = AiConfigStore.isClassroomSkillEnabled(ctx());
        final boolean agendaSkillEnabled = AiConfigStore.isAgendaSkillEnabled(ctx());
        final boolean webSearchSkillEnabled = AiConfigStore.isWebSearchSkillEnabled(ctx());

        Thread worker = new Thread(() -> {
            String reply = "";
            boolean cancelled = false;
            PendingLoginContinuation loginContinuation = null;
            try {
                if (hasImage) {
                    Log.i(TAG, "multimodal request start token=" + requestToken
                            + ", model=" + safe(model)
                            + ", provider=" + safe(provider)
                            + ", imageCount=" + imagesForRequest.size()
                            + ", promptLen=" + modelPromptText.length());
                }
                ensureAiRequestActiveOrThrow(requestToken);
                reply = runModelWithSkillCommands(
                        provider,
                        baseUrl,
                        apiKey,
                        model,
                        modelId,
                        contextAwarePromptText,
                        modelPromptText,
                        requestTitleInFinalAnswer,
                        requestToken,
                        imagesForRequest,
                        cacheHint,
                        skillEnabled,
                        memorySkillEnabled,
                        courseSkillEnabled,
                        navigationSkillEnabled,
                        classroomSkillEnabled,
                        agendaSkillEnabled,
                        webSearchSkillEnabled
                );
                ensureAiRequestActiveOrThrow(requestToken);
                if (hasImage) {
                    Log.i(TAG, "multimodal request finish token=" + requestToken
                            + ", replyLen=" + safe(reply).length());
                }
            } catch (LoginRequiredException loginEx) {
                loginContinuation = loginEx.continuation;
            } catch (InterruptedException stopEx) {
                cancelled = true;
            } catch (Throwable e) {
                if (hasImage) {
                    Log.e(TAG, "multimodal request failed token=" + requestToken
                            + ", reason=" + safe(e.getMessage()), e);
                }
                if (isAiRequestActive(requestToken)) {
                    String reason = e.getMessage();
                    if (TextUtils.isEmpty(reason)) {
                        reason = hasImage ? "图文请求异常中断，请重试并尽量缩小图片" : "请求过程异常中断，请重试";
                    }
                    reply = "请求失败：" + reason;
                } else {
                    cancelled = true;
                }
            }

            if (!isAiRequestActive(requestToken)) {
                return;
            }

            if (loginContinuation != null) {
                final PendingLoginContinuation finalLoginContinuation = loginContinuation;
                if (!isAdded()) {
                    finishAiConversationRequest(requestToken);
                    return;
                }
                requireActivity().runOnUiThread(() -> {
                    if (!isAiRequestActive(requestToken)) {
                        return;
                    }
                    removeTypingBubble();
                    pendingLoginContinuation = finalLoginContinuation;
                    String cardPayload = buildJwxtLoginCardPayload(
                            finalLoginContinuation.userText,
                            finalLoginContinuation.modelId,
                            finalLoginContinuation.requestTitleInFinalAnswer
                    );
                    addSystemMessage(cardPayload, true);
                    finishAiConversationRequest(requestToken);
                });
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
                    if (hasImage) {
                        Log.w(TAG, "multimodal visible reply empty token=" + requestToken
                                + ", rawReply=" + clipForLog(finalReply));
                    }
                    String fallback = hasImage
                            ? "图文请求未返回可显示内容，请重试或缩小图片后再试。"
                            : "模型未返回可显示内容，请稍后重试。";
                    addBubble(false, fallback, false);
                    finishAiConversationRequest(requestToken);
                    return;
                }
                streamAssistantReply(visibleReply, requestToken, renderedView -> {
                    if (!isAiRequestActive(requestToken)) {
                        return;
                    }
                    appendMessageToHistory(false, visibleReply);
                    bindMessageMetadata(renderedView, false, visibleReply, getActiveSessionLastMessageIndex());
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

    private void handleJwxtLoginResult(@Nullable ActivityResult result) {
        if (!isAdded() || result == null) {
            return;
        }
        boolean loginSuccess = result.getResultCode() == Activity.RESULT_OK
                && result.getData() != null
                && result.getData().getBooleanExtra("login_success", false);
        if (!loginSuccess) {
            Toast.makeText(ctx(), "尚未完成教务系统登录，暂无法继续查询。", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(ctx(), "教务系统登录成功，正在继续处理请求。", Toast.LENGTH_SHORT).show();
        resumePendingAiAfterLogin();
    }

    private void launchJwxtLoginFromCard(@Nullable String resumePrompt,
                                          @Nullable String resumeModelId,
                                          boolean requestTitleInFinalAnswer) {
        if (!isAdded()) {
            return;
        }
        if (!TextUtils.isEmpty(resumePrompt)) {
            pendingLoginContinuation = new PendingLoginContinuation(
                    resumePrompt,
                    requestTitleInFinalAnswer,
                    resumeModelId
            );
        }
        Intent intent = new Intent(ctx(), BrowserActivity.class);
        intent.putExtra("url", CourseScraper.LOGIN_URL);
        intent.putExtra("autoCloseOnLoginSuccess", true);
        if (jwxtLoginLauncher != null) {
            jwxtLoginLauncher.launch(intent);
        } else {
            startActivity(intent);
        }
    }

    private void resumePendingAiAfterLogin() {
        PendingLoginContinuation continuation = pendingLoginContinuation;
        if (continuation == null || TextUtils.isEmpty(continuation.userText)) {
            return;
        }
        pendingLoginContinuation = null;
        continueAiConversationAfterLogin(continuation);
    }

    private void continueAiConversationAfterLogin(@NonNull PendingLoginContinuation continuation) {
        if (isAiConversationRunning()) {
            return;
        }
        if (activeSession == null) {
            startNewSession(false);
        }
        if (activeSession != null && !TextUtils.isEmpty(continuation.modelId)) {
            activeSession.modelId = continuation.modelId;
            syncModelPickerWithActiveSession();
        }

        AiConfigStore.AiModelConfig selectedModel = resolveModelForCurrentSession();
        if (selectedModel == null) {
            addSystemMessage(buildApiConfigCardPayload(
                    "尚未配置AI模型",
                    "请先前往 大模型设置 添加模型并填写 API Key。"
            ), true);
            return;
        }
        if (TextUtils.isEmpty(selectedModel.apiKey)) {
            addSystemMessage(buildApiConfigCardPayload(
                    "模型缺少 API Key",
                    "请先配置模型的 API Key，配置后即可继续提问。"
            ), true);
            return;
        }
        if (TextUtils.isEmpty(selectedModel.modelName)) {
            addSystemMessage(buildApiConfigCardPayload(
                    "模型缺少模型名",
                    "请先填写模型名/接入点 ID，配置完成后再试。"
            ), true);
            return;
        }

        final String modelPromptText = safe(continuation.userText);
        final boolean requestTitleInFinalAnswer = continuation.requestTitleInFinalAnswer;
        final String contextAwarePromptText = buildContextAwarePromptText(modelPromptText);
        final AiGateway.RequestCacheHint cacheHint = buildRequestCacheHint(selectedModel.modelName, contextAwarePromptText);

        final long requestToken = beginAiConversationRequest();
        updateSendButtonMode(true);
        addBubble(false, "思考中...", true);

        final String provider = selectedModel.provider;
        final String baseUrl = selectedModel.baseUrl;
        final String model = selectedModel.modelName;
        final String apiKey = selectedModel.apiKey;
        final String modelId = safe(selectedModel.id);
        final boolean skillEnabled = AiConfigStore.isSkillEnabled(ctx());
        final boolean memorySkillEnabled = AiConfigStore.isMemorySkillEnabled(ctx());
        final boolean courseSkillEnabled = AiConfigStore.isCourseSkillEnabled(ctx());
        final boolean navigationSkillEnabled = AiConfigStore.isNavigationSkillEnabled(ctx());
        final boolean classroomSkillEnabled = AiConfigStore.isClassroomSkillEnabled(ctx());
        final boolean agendaSkillEnabled = AiConfigStore.isAgendaSkillEnabled(ctx());
        final boolean webSearchSkillEnabled = AiConfigStore.isWebSearchSkillEnabled(ctx());

        Thread worker = new Thread(() -> {
            String reply = "";
            boolean cancelled = false;
            PendingLoginContinuation loginContinuation = null;
            try {
                ensureAiRequestActiveOrThrow(requestToken);
                reply = runModelWithSkillCommands(
                        provider,
                        baseUrl,
                        apiKey,
                        model,
                        modelId,
                        contextAwarePromptText,
                    modelPromptText,
                        requestTitleInFinalAnswer,
                        requestToken,
                        (List<String>) null,
                        cacheHint,
                        skillEnabled,
                        memorySkillEnabled,
                        courseSkillEnabled,
                        navigationSkillEnabled,
                        classroomSkillEnabled,
                        agendaSkillEnabled,
                        webSearchSkillEnabled
                );
                ensureAiRequestActiveOrThrow(requestToken);
            } catch (LoginRequiredException loginEx) {
                loginContinuation = loginEx.continuation;
            } catch (InterruptedException stopEx) {
                cancelled = true;
            } catch (Throwable e) {
                if (isAiRequestActive(requestToken)) {
                    String reason = e.getMessage();
                    if (TextUtils.isEmpty(reason)) {
                        reason = "请求过程异常中断，请重试";
                    }
                    reply = "请求失败：" + reason;
                } else {
                    cancelled = true;
                }
            }

            if (!isAiRequestActive(requestToken)) {
                return;
            }

            if (loginContinuation != null) {
                final PendingLoginContinuation finalLoginContinuation = loginContinuation;
                if (!isAdded()) {
                    finishAiConversationRequest(requestToken);
                    return;
                }
                requireActivity().runOnUiThread(() -> {
                    if (!isAiRequestActive(requestToken)) {
                        return;
                    }
                    removeTypingBubble();
                    pendingLoginContinuation = finalLoginContinuation;
                    String cardPayload = buildJwxtLoginCardPayload(
                            finalLoginContinuation.userText,
                            finalLoginContinuation.modelId,
                            finalLoginContinuation.requestTitleInFinalAnswer
                    );
                    addSystemMessage(cardPayload, true);
                    finishAiConversationRequest(requestToken);
                });
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
                    addBubble(false, "模型未返回可显示内容，请稍后重试。", false);
                    finishAiConversationRequest(requestToken);
                    return;
                }
                streamAssistantReply(visibleReply, requestToken, renderedView -> {
                    if (!isAiRequestActive(requestToken)) {
                        return;
                    }
                    appendMessageToHistory(false, visibleReply);
                    bindMessageMetadata(renderedView, false, visibleReply, getActiveSessionLastMessageIndex());
                    finishAiConversationRequest(requestToken);
                });
            });
        }, "ai-chat-resume-" + requestToken);
        bindAiWorkerThread(requestToken, worker);
        worker.start();
    }

    private String buildContextAwarePromptText(String userText) {
        String raw = safe(userText).trim();
        if (raw.isEmpty()) {
            return "";
        }
        String context = buildConversationContextSnippet(raw);
        if (TextUtils.isEmpty(context)) {
            return raw;
        }
        return "[历史对话上下文]\n"
                + context
                + "\n\n[用户问题]\n"
                + raw;
    }

    private String buildConversationContextSnippet(String currentUserText) {
        if (activeSession == null || activeSession.messages == null || activeSession.messages.isEmpty()) {
            return "";
        }

        List<String> lines = new ArrayList<>();
        int totalChars = 0;
        int omitted = 0;
        int skipTailIndex = -1;

        String currentTrimmed = safe(currentUserText).trim();
        int tailIndex = activeSession.messages.size() - 1;
        if (tailIndex >= 0) {
            ChatMessage tail = activeSession.messages.get(tailIndex);
            if (tail != null
                    && "user".equalsIgnoreCase(safe(tail.role))
                    && TextUtils.equals(safe(tail.content).trim(), currentTrimmed)) {
                skipTailIndex = tailIndex;
            }
        }

        for (int i = activeSession.messages.size() - 1; i >= 0; i--) {
            if (i == skipTailIndex) {
                continue;
            }
            ChatMessage one = activeSession.messages.get(i);
            if (one == null) {
                continue;
            }
            String role = safe(one.role).trim().toLowerCase(Locale.ROOT);
            boolean isUser = "user".equals(role);
            boolean isAssistant = "assistant".equals(role);
            if (!isUser && !isAssistant) {
                continue;
            }
            if (isAssistant && isToolFeedbackMessage(one.content)) {
                continue;
            }

            String body = compactForModelContext(one.content);
            if (!TextUtils.isEmpty(one.imagePath)) {
                body = body.isEmpty() ? "(发送了图片)" : "(发送了图片) " + body;
            }
            if (body.isEmpty()) {
                continue;
            }

            String line = (isUser ? "用户: " : "助手: ") + body;
            if (line.length() > MAX_MODEL_CONTEXT_ITEM_CHARS) {
                line = line.substring(0, MAX_MODEL_CONTEXT_ITEM_CHARS) + "...";
            }

            if (lines.size() >= MAX_MODEL_CONTEXT_MESSAGES || totalChars + line.length() > MAX_MODEL_CONTEXT_CHARS) {
                omitted++;
                continue;
            }

            lines.add(line);
            totalChars += line.length();
        }

        if (lines.isEmpty()) {
            return "";
        }

        Collections.reverse(lines);
        StringBuilder sb = new StringBuilder();
        if (omitted > 0) {
            sb.append("已省略更早 ").append(omitted).append(" 条历史消息。\n");
        }
        for (String line : lines) {
            sb.append("- ").append(line).append('\n');
        }
        return sb.toString().trim();
    }

    private String compactForModelContext(@Nullable String text) {
        String normalized = safe(text)
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\n', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.isEmpty()) {
            return "";
        }
        if (normalized.length() > MAX_MODEL_CONTEXT_ITEM_CHARS) {
            return normalized.substring(0, MAX_MODEL_CONTEXT_ITEM_CHARS) + "...";
        }
        return normalized;
    }

    @NonNull
    private AiGateway.RequestCacheHint buildRequestCacheHint(String model, String contextAwarePrompt) {
        String conversationId = activeSession == null ? "" : safe(activeSession.id).trim();
        if (conversationId.isEmpty()) {
            conversationId = "local-" + System.currentTimeMillis();
        }
        String cacheSeed = safe(model) + "|" + safe(contextAwarePrompt);
        String digest = sha256Hex(cacheSeed);
        if (digest.length() > 24) {
            digest = digest.substring(0, 24);
        }
        return new AiGateway.RequestCacheHint(conversationId, "chat-" + digest);
    }

    private String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(safe(raw).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(safe(raw).hashCode());
        }
    }

    private String runModelWithSkillCommands(String provider, String baseUrl, String apiKey, String model,
                                             String modelId,
                                             String modelUserText,
                                             String originalUserText,
                                             boolean requestTitleInFinalAnswer,
                                             long requestToken,
                                             @Nullable List<String> imagePaths,
                                             @Nullable AiGateway.RequestCacheHint cacheHint,
                                             boolean skillEnabled,
                             boolean memorySkillEnabled,
                             boolean courseSkillEnabled,
                             boolean navigationSkillEnabled,
                             boolean classroomSkillEnabled,
                             boolean agendaSkillEnabled,
                                             boolean webSearchSkillEnabled) throws Exception {
        String skillIndex = skillEnabled
                ? SkillCommandCenter.buildSkillIndexFromFrontmatter(ctx())
                : "技能功能已关闭";
        String systemPrompt = AiPromptCenter.buildSystemPrompt(
            skillEnabled,
            memorySkillEnabled,
            courseSkillEnabled,
            navigationSkillEnabled,
            classroomSkillEnabled,
            agendaSkillEnabled,
            webSearchSkillEnabled
        );
        boolean includeCurrentTime = true;
        String firstTurnPrompt = AiPromptCenter.buildFirstTurnUserPrompt(
                skillIndex,
            modelUserText,
                includeCurrentTime,
                requestTitleInFinalAnswer
        );

        ensureAiRequestActiveOrThrow(requestToken);
        String assistantOutput = requestModelReplyWithRetry(
            provider,
            baseUrl,
            apiKey,
            model,
            systemPrompt,
            firstTurnPrompt,
            imagePaths,
            cacheHint,
            0
        );
        ensureAiRequestActiveOrThrow(requestToken);
        if (imagePaths != null && !imagePaths.isEmpty() && ("模型返回为空".equals(assistantOutput) || assistantOutput.startsWith("模型返回为空（"))) {
            Log.w(TAG, "multimodal first round empty token=" + requestToken + ", output=" + clipForLog(assistantOutput));
        }

        if (!skillEnabled) {
            List<String> commands = SkillCommandCenter.extractCommands(assistantOutput);
            if (!commands.isEmpty()) {
                return "技能功能已关闭，请在大模型设置的模型设置中开启技能后再试。";
            }
            return assistantOutput;
        }

        boolean toolFeedbackShown = false;
        final Set<String> toolFeedbackItems = new LinkedHashSet<>();
        final int[] toolFeedbackHistoryIndex = new int[]{-1};
        boolean agendaMutationNotified = false;

        for (int round = 1; round <= MAX_TOOL_COMMAND_ROUNDS; round++) {
            ensureAiRequestActiveOrThrow(requestToken);
            List<String> commands = SkillCommandCenter.extractCommands(assistantOutput);
            if (commands.isEmpty()) {
                return assistantOutput;
            }

            SkillCommandCenter.CommandBatchResult batch = SkillCommandCenter.executeCommandsWithFeedback(ctx(), commands, originalUserText);
            String commandResult = batch.modelFeedback;
            String roundMessage = batch.userFeedback;
            if (!agendaMutationNotified && hasAgendaMutation(commands, commandResult)) {
                agendaMutationNotified = true;
                notifyAgendaMutationChanged();
            }
            String systemCardPayload = extractSystemCardPayloadFromCommandResult(commandResult);
            List<String> roundFeedbackItems = extractToolFeedbackItems(roundMessage);
            if (!roundFeedbackItems.isEmpty()) {
                toolFeedbackItems.addAll(roundFeedbackItems);
            }
            final String aggregatedToolFeedback = buildAggregatedToolFeedbackMessage(toolFeedbackItems);
            ensureAiRequestActiveOrThrow(requestToken);
            if (!toolFeedbackShown && isAdded() && isAiRequestActive(requestToken)) {
                requireActivity().runOnUiThread(() -> {
                    if (isAiRequestActive(requestToken)) {
                        toolFeedbackHistoryIndex[0] = addSystemMessage(aggregatedToolFeedback, true);
                    }
                });
                toolFeedbackShown = true;
            } else if (!roundFeedbackItems.isEmpty() && isAdded() && isAiRequestActive(requestToken)) {
                requireActivity().runOnUiThread(() -> {
                    if (!isAiRequestActive(requestToken)) {
                        return;
                    }
                    if (toolFeedbackHistoryIndex[0] >= 0) {
                        updateSystemTextMessageByHistoryIndex(toolFeedbackHistoryIndex[0], aggregatedToolFeedback);
                    }
                });
            }

            if (!TextUtils.isEmpty(systemCardPayload) && isAdded() && isAiRequestActive(requestToken)) {
                final String finalCardPayload = systemCardPayload;
                requireActivity().runOnUiThread(() -> {
                    if (isAiRequestActive(requestToken)) {
                        addSystemMessage(finalCardPayload, true);
                    }
                });
            }

            if (isJwxtLoginRequired(commandResult)) {
                throw new LoginRequiredException(
                        "教务登录状态失效，请先完成登录后继续",
                    new PendingLoginContinuation(originalUserText, requestTitleInFinalAnswer, modelId)
                );
            }

            String commandResultForModel = compactToolResultForModel(commandResult);
            String nextPrompt = AiPromptCenter.buildToolFollowupPrompt(
                    originalUserText,
                    assistantOutput,
                    commands,
                    commandResultForModel,
                    requestTitleInFinalAnswer
            );
            ensureAiRequestActiveOrThrow(requestToken);
            try {
                assistantOutput = requestModelReplyWithRetry(
                        provider,
                        baseUrl,
                        apiKey,
                        model,
                        systemPrompt,
                        nextPrompt,
                        null,
                        cacheHint,
                        round
                );
            } catch (Exception followupEx) {
                String fallbackReply = buildFallbackReplyFromCommandBatch(batch);
                if (!TextUtils.isEmpty(fallbackReply)) {
                    Log.w(TAG, "tool followup model call failed, fallback to command result, reason=" + clipForLog(followupEx.getMessage()), followupEx);
                    return fallbackReply;
                }
                throw followupEx;
            }
            ensureAiRequestActiveOrThrow(requestToken);
        }

        return assistantOutput + "\n\n(已达到最大命令轮次" + MAX_TOOL_COMMAND_ROUNDS + "次，若需继续请重试)";
    }

    private boolean hasAgendaMutation(@NonNull List<String> commands, @Nullable String modelFeedback) {
        boolean hasAgendaWriteCommand = false;
        for (String command : commands) {
            String lower = safe(command).toLowerCase(Locale.ROOT);
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

        String feedback = safe(modelFeedback).toLowerCase(Locale.ROOT);
        if (feedback.contains("创建成功") || feedback.contains("更新成功") || feedback.contains("删除成功")) {
            return true;
        }
        if (feedback.contains("已创建日程") || feedback.contains("已更新日程") || feedback.contains("已删除日程")) {
            return true;
        }
        return !feedback.contains("失败");
    }

    private void notifyAgendaMutationChanged() {
        if (!isAdded()) {
            return;
        }
        Activity host = getActivity();
        if (host == null) {
            return;
        }
        host.runOnUiThread(() -> {
            if (!isAdded()) {
                return;
            }
            Bundle result = new Bundle();
            result.putBoolean(RESULT_KEY_AGENDA_CHANGED, true);
            getParentFragmentManager().setFragmentResult(REQUEST_KEY_AGENDA_CHANGED, result);
        });
    }

    private String requestModelReplyWithRetry(String provider,
                                              String baseUrl,
                                              String apiKey,
                                              String model,
                                              String systemPrompt,
                                              String userPrompt,
                                              @Nullable List<String> imagePaths,
                                              @Nullable AiGateway.RequestCacheHint baseCacheHint,
                                              int roundTag) throws Exception {
        AiGateway.RequestCacheHint roundCacheHint = buildRoundCacheHint(baseCacheHint, model, userPrompt, roundTag);
        long firstStartMs = SystemClock.elapsedRealtime();
        try {
            String reply = AiGateway.chat(provider, baseUrl, apiKey, model, systemPrompt, userPrompt, imagePaths, roundCacheHint);
            long costMs = SystemClock.elapsedRealtime() - firstStartMs;
            Log.i(TAG, "model call done round=" + roundTag
                    + ", promptLen=" + safe(userPrompt).length()
                    + ", durationMs=" + costMs);
            return reply;
        } catch (Exception firstEx) {
            long firstCostMs = SystemClock.elapsedRealtime() - firstStartMs;
            if (!isLikelyTimeoutException(firstEx)) {
                Log.w(TAG, "model call failed round=" + roundTag
                        + ", promptLen=" + safe(userPrompt).length()
                        + ", durationMs=" + firstCostMs
                        + ", reason=" + clipForLog(firstEx.getMessage()));
                throw firstEx;
            }
            Log.w(TAG, "model call timeout, retry once without cache hint, round=" + roundTag
                    + ", firstDurationMs=" + firstCostMs
                    + ", reason=" + clipForLog(firstEx.getMessage()), firstEx);
            long retryStartMs = SystemClock.elapsedRealtime();
            String reply = AiGateway.chat(provider, baseUrl, apiKey, model, systemPrompt, userPrompt, imagePaths, null);
            long retryCostMs = SystemClock.elapsedRealtime() - retryStartMs;
            Log.i(TAG, "model retry done round=" + roundTag
                    + ", promptLen=" + safe(userPrompt).length()
                    + ", durationMs=" + retryCostMs);
            return reply;
        }
    }

    @NonNull
    private AiGateway.RequestCacheHint buildRoundCacheHint(@Nullable AiGateway.RequestCacheHint baseCacheHint,
                                                           @Nullable String model,
                                                           @Nullable String userPrompt,
                                                           int roundTag) {
        String conversationId = baseCacheHint == null ? "" : safe(baseCacheHint.conversationId).trim();
        if (conversationId.isEmpty() && activeSession != null) {
            conversationId = safe(activeSession.id).trim();
        }
        if (conversationId.isEmpty()) {
            conversationId = "local-" + System.currentTimeMillis();
        }
        String digest = sha256Hex(safe(model) + "|r=" + roundTag + "|" + safe(userPrompt));
        if (digest.length() > 24) {
            digest = digest.substring(0, 24);
        }
        return new AiGateway.RequestCacheHint(conversationId, "chat-r" + Math.max(0, roundTag) + "-" + digest);
    }

    private boolean isLikelyTimeoutException(@Nullable Throwable error) {
        Throwable cursor = error;
        int guard = 0;
        while (cursor != null && guard < 6) {
            String className = cursor.getClass().getName().toLowerCase(Locale.ROOT);
            if (className.contains("timeout") || cursor instanceof java.io.InterruptedIOException) {
                return true;
            }
            String msg = safe(cursor.getMessage()).toLowerCase(Locale.ROOT);
            if (msg.contains("timed out")
                    || msg.contains("timeout")
                    || msg.contains("调用模型超时")
                    || msg.contains("请求超时")) {
                return true;
            }
            cursor = cursor.getCause();
            guard++;
        }
        return false;
    }

    private String compactToolResultForModel(@Nullable String commandResult) {
        String normalized = safe(commandResult)
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
        normalized = sanitizeSystemCardPayloadForModel(normalized);
        if (normalized.length() <= MAX_TOOL_RESULT_FOR_MODEL_CHARS) {
            return normalized;
        }
        int keep = Math.max(800, MAX_TOOL_RESULT_FOR_MODEL_CHARS - 70);
        return normalized.substring(0, keep)
                + "\n...(工具返回较长，剩余内容已截断，共"
                + normalized.length()
                + "字符)";
    }

    @NonNull
    private String sanitizeSystemCardPayloadForModel(@Nullable String commandResult) {
        String normalized = safe(commandResult)
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
        if (normalized.isEmpty() || !normalized.contains(SYSTEM_CARD_PREFIX)) {
            return normalized;
        }

        String[] lines = normalized.split("\\n");
        StringBuilder sb = new StringBuilder();
        for (String rawLine : lines) {
            String line = safe(rawLine);
            int markerIndex = line.indexOf(SYSTEM_CARD_PREFIX);
            if (markerIndex >= 0) {
                String prefix = line.substring(0, markerIndex).trim();
                if (prefix.isEmpty()) {
                    line = "已生成系统卡片（参数已省略）";
                } else if (prefix.endsWith("=>")) {
                    line = prefix + " 已生成系统卡片（参数已省略）";
                } else {
                    line = prefix + " [已生成系统卡片，参数已省略]";
                }
            }
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(line);
        }
        return sb.toString().trim();
    }

    private String buildFallbackReplyFromCommandBatch(@NonNull SkillCommandCenter.CommandBatchResult batch) {
        String readable = extractReadableCommandResult(batch.modelFeedback);
        if (TextUtils.isEmpty(readable)) {
            readable = safe(batch.userFeedback)
                    .replaceFirst("^工具执行情况[：:]\\s*", "")
                    .trim();
        }
        if (TextUtils.isEmpty(readable)) {
            return "";
        }
        return "已完成相关操作，先为你返回可确认的结果：\n"
                + readable
                + "\n\n如需我继续补充说明或下一步建议，请直接告诉我。";
    }

    private String extractReadableCommandResult(@Nullable String modelFeedback) {
        String normalized = sanitizeSystemCardPayloadForModel(safe(modelFeedback))
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
        if (normalized.isEmpty()) {
            return "";
        }
        String[] lines = normalized.split("\\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String one = safe(line).trim();
            if (one.isEmpty()) {
                continue;
            }
            Matcher matcher = COMMAND_RESULT_LINE_PATTERN.matcher(one);
            if (matcher.matches()) {
                one = safe(matcher.group(1)).trim();
            }
            if (one.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(one);
        }
        return sb.toString().trim();
    }

    @NonNull
    private String extractSystemCardPayloadFromCommandResult(@Nullable String modelFeedback) {
        String normalized = safe(modelFeedback)
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
        if (normalized.isEmpty()) {
            return "";
        }
        if (normalized.startsWith(SYSTEM_CARD_PREFIX)) {
            return normalized;
        }
        String[] lines = normalized.split("\\n");
        for (String one : lines) {
            String line = safe(one).trim();
            if (line.isEmpty()) {
                continue;
            }
            int markerIndex = line.indexOf(SYSTEM_CARD_PREFIX);
            if (markerIndex < 0) {
                continue;
            }
            String payload = line.substring(markerIndex).trim();
            if (!payload.isEmpty()) {
                return payload;
            }
        }
        return "";
    }

    @NonNull
    private List<String> extractToolFeedbackItems(@Nullable String feedback) {
        List<String> items = new ArrayList<>();
        String normalized = safe(feedback)
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
        if (normalized.isEmpty()) {
            return items;
        }
        String[] lines = normalized.split("\\n");
        for (String rawLine : lines) {
            String line = safe(rawLine).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("工具执行情况")) {
                continue;
            }
            line = line.replaceFirst("^(?:[-*•]|\\d+[.)、：:]?)\\s*", "").trim();
            if (line.isEmpty()) {
                continue;
            }
            if (!line.endsWith("。") && !line.endsWith("！") && !line.endsWith("？")) {
                line = line + "。";
            }
            items.add(line);
        }
        if (items.isEmpty()) {
            items.add("已完成本轮工具调用。");
        }
        return items;
    }

    @NonNull
    private String buildAggregatedToolFeedbackMessage(@NonNull Set<String> items) {
        StringBuilder sb = new StringBuilder("工具执行情况：\n");
        if (items.isEmpty()) {
            sb.append("已完成本轮工具调用。");
            return sb.toString();
        }
        int index = 0;
        for (String one : items) {
            String line = safe(one).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (index > 0) {
                sb.append('\n');
            }
            sb.append("- ").append(line);
            index++;
        }
        if (index == 0) {
            sb.append("已完成本轮工具调用。");
        }
        return sb.toString();
    }

    private void updateSystemTextMessageByHistoryIndex(int historyIndex, @Nullable String newText) {
        if (historyIndex < 0) {
            return;
        }
        String normalized = safe(newText).trim();
        if (normalized.isEmpty()) {
            return;
        }

        boolean changed = false;
        if (activeSession != null && activeSession.messages != null
                && historyIndex >= 0 && historyIndex < activeSession.messages.size()) {
            ChatMessage message = activeSession.messages.get(historyIndex);
            if (message != null && "system".equalsIgnoreCase(safe(message.role))) {
                if (!TextUtils.equals(safe(message.content), normalized)) {
                    message.content = normalized;
                    changed = true;
                }
            }
        }

        TextView target = findSystemTextMessageViewByHistoryIndex(historyIndex);
        if (target != null) {
            target.setText(normalized);
            bindMessageMetadata(target, false, normalized, historyIndex);
        }

        if (changed) {
            if (sessions.contains(activeSession)) {
                touchActiveSession();
                refreshHistoryRows();
                saveHistory();
            } else {
                promoteActiveSessionToHistoryIfEligible();
            }
        }
    }

    @Nullable
    private TextView findSystemTextMessageViewByHistoryIndex(int historyIndex) {
        if (chatContainer == null || historyIndex < 0) {
            return null;
        }
        for (int i = chatContainer.getChildCount() - 1; i >= 0; i--) {
            View child = chatContainer.getChildAt(i);
            if (!(child instanceof TextView)) {
                continue;
            }
            Object tag = child.getTag();
            if (!(tag instanceof MessageViewMeta)) {
                continue;
            }
            MessageViewMeta meta = (MessageViewMeta) tag;
            if (!meta.isUser && !meta.typing && meta.historyIndex == historyIndex) {
                return (TextView) child;
            }
        }
        return null;
    }

    private int addSystemMessage(String text) {
        return addSystemMessage(text, false);
    }

    private int addSystemMessage(String text, boolean persistToHistory) {
        if (TextUtils.isEmpty(text)) {
            return -1;
        }

        String normalized = safe(text)
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .trim();
        if (normalized.isEmpty()) {
            return -1;
        }

        int historyIndex = -1;

        SystemCardPayload cardPayload = parseSystemCardPayload(normalized);
        if (cardPayload != null) {
            if (persistToHistory) {
                appendMessageToHistoryByRole("system", cardPayload.rawPayload);
                historyIndex = getActiveSessionLastMessageIndex();
            }
            renderSystemCard(cardPayload);
            return historyIndex;
        }

        String noticeText = normalized.startsWith("工具") ? normalized : "工具执行情况：\n" + normalized;

        if (persistToHistory) {
            appendMessageToHistoryByRole("system", noticeText);
            historyIndex = getActiveSessionLastMessageIndex();
        }

        TextView tv = new TextView(ctx());
        tv.setText(noticeText);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        tv.setTextColor(ColorUtils.setAlphaComponent(UiStyleHelper.resolveOnSurfaceColor(ctx()), 196));
        tv.setAlpha(0.94f);
        tv.setLineSpacing(0f, 1.18f);
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

        bindMessageMetadata(tv, false, noticeText, historyIndex);

        chatContainer.addView(tv);
        scrollChatToBottom();
        return historyIndex;
    }

    private boolean isJwxtLoginRequired(@Nullable String commandResult) {
        String lower = safe(commandResult).toLowerCase(Locale.ROOT);
        return lower.contains("未登录或登录已失效")
                || lower.contains("请先登录教务系统")
                || lower.contains("教务登录状态：未登录")
                || lower.contains("请先在“设置-账号与教务”完成登录");
    }

    private String buildJwxtLoginCardPayload(@Nullable String resumePrompt,
                                             @Nullable String resumeModelId,
                                             boolean requestTitleInFinalAnswer) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", SYSTEM_CARD_TYPE_JWXT_LOGIN);
            obj.put("title", "教务系统登录状态已失效");
            obj.put("description", "请先完成教务系统登录。登录成功后，系统将自动继续处理查询请求。");
            obj.put("action", SYSTEM_CARD_ACTION_OPEN_JWXT_LOGIN);
            obj.put("actionText", "前往教务系统登录");
            obj.put("resumePrompt", safe(resumePrompt));
            obj.put("resumeModelId", safe(resumeModelId));
            obj.put("requestTitle", requestTitleInFinalAnswer);
            return SYSTEM_CARD_PREFIX + obj;
        } catch (Exception ignored) {
            return "工具执行情况：\n教务登录状态已失效，请先登录教务系统。";
        }
    }

    private String buildApiConfigCardPayload(@NonNull String title, @NonNull String description) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", SYSTEM_CARD_TYPE_API_CONFIG);
            obj.put("title", title);
            obj.put("description", description);
            obj.put("action", SYSTEM_CARD_ACTION_OPEN_AI_SETTINGS);
            obj.put("actionText", "点击去配置API");
            return SYSTEM_CARD_PREFIX + obj;
        } catch (Exception ignored) {
            return "请先到设置中配置 AI 模型与 API Key。";
        }
    }

    @Nullable
    private SystemCardPayload parseSystemCardPayload(@Nullable String raw) {
        String text = safe(raw).trim();
        if (!text.startsWith(SYSTEM_CARD_PREFIX)) {
            return null;
        }
        String jsonText = text.substring(SYSTEM_CARD_PREFIX.length()).trim();
        if (jsonText.isEmpty()) {
            return null;
        }
        try {
            JSONObject obj = new JSONObject(jsonText);
            String type = safe(obj.optString("type", "")).trim();
            if (type.isEmpty()) {
                return null;
            }
            String title = safe(obj.optString("title", "")).trim();
            String description = safe(obj.optString("description", "")).trim();
            String action = safe(obj.optString("action", "")).trim();
            String actionText = safe(obj.optString("actionText", "")).trim();
            String resumePrompt = safe(obj.optString("resumePrompt", "")).trim();
            String resumeModelId = safe(obj.optString("resumeModelId", "")).trim();
            boolean requestTitle = obj.optBoolean("requestTitle", false);
            String targetName = safe(obj.optString("targetName", "")).trim();
            double targetLat = obj.has("targetLat") ? obj.optDouble("targetLat", Double.NaN) : Double.NaN;
            double targetLng = obj.has("targetLng") ? obj.optDouble("targetLng", Double.NaN) : Double.NaN;
            return new SystemCardPayload(
                    text,
                    type,
                    title,
                    description,
                    action,
                    actionText,
                    resumePrompt,
                    resumeModelId,
                    requestTitle,
                    targetName,
                    targetLat,
                    targetLng
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private void renderSystemCard(@NonNull SystemCardPayload payload) {
        if (chatContainer == null) {
            return;
        }

        maybeAddAssistantSeparator(false, false);

        int onSurface = UiStyleHelper.resolveOnSurfaceColor(ctx());
        int accent = UiStyleHelper.resolveAccentColor(ctx());

        MaterialCardView card = new MaterialCardView(ctx());
        card.setCardElevation(0f);
        card.setRadius(dp(24));
        card.setCardBackgroundColor(UiStyleHelper.resolveGlassCardColor(ctx()));
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(ColorUtils.setAlphaComponent(onSurface, 38));
        card.setTag(new MessageViewMeta(false, payload.rawPayload, false, -1));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardLp.setMargins(0, dp(8), 0, dp(8));
        card.setLayoutParams(cardLp);

        LinearLayout content = new LinearLayout(ctx());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(14), dp(16), dp(14));

        TextView titleView = new TextView(ctx());
        titleView.setText(TextUtils.isEmpty(payload.title) ? "工具卡片" : payload.title);
        titleView.setTextColor(onSurface);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        titleView.setIncludeFontPadding(false);
        content.addView(titleView);

        if (!TextUtils.isEmpty(payload.description)) {
            TextView descView = new TextView(ctx());
            descView.setText(payload.description);
            descView.setTextColor(ColorUtils.setAlphaComponent(onSurface, 190));
            descView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            descView.setLineSpacing(0f, 1.18f);
            descView.setIncludeFontPadding(false);
            LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            descLp.topMargin = dp(8);
            descView.setLayoutParams(descLp);
            content.addView(descView);
        }

        if (!TextUtils.isEmpty(payload.action)) {
            MaterialButton actionButton = new MaterialButton(ctx(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            actionButton.setText(TextUtils.isEmpty(payload.actionText) ? "立即前往" : payload.actionText);
            actionButton.setAllCaps(false);
            actionButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            actionButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            actionButton.setInsetTop(0);
            actionButton.setInsetBottom(0);
            actionButton.setCornerRadius(dp(14));
            actionButton.setStrokeWidth(dp(1));
            actionButton.setStrokeColor(ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 196)));
            actionButton.setBackgroundTintList(ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 26)));
            actionButton.setRippleColor(ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 82)));
            actionButton.setTextColor(accent);
            actionButton.setPadding(dp(12), dp(8), dp(12), dp(8));

            LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            actionLp.topMargin = dp(12);
            actionButton.setLayoutParams(actionLp);

            actionButton.setOnClickListener(v -> handleSystemCardAction(payload));
            content.addView(actionButton);
        }

        card.setOnClickListener(v -> {
            if (!TextUtils.isEmpty(payload.action)) {
                handleSystemCardAction(payload);
            }
        });

        card.addView(content);
        chatContainer.addView(card);
        scrollChatToBottom();
    }

    private void handleSystemCardAction(@NonNull SystemCardPayload payload) {
        if (SYSTEM_CARD_ACTION_OPEN_JWXT_LOGIN.equals(payload.action)) {
            launchJwxtLoginFromCard(payload.resumePrompt, payload.resumeModelId, payload.requestTitleInFinalAnswer);
            return;
        }
        if (SYSTEM_CARD_ACTION_OPEN_AI_SETTINGS.equals(payload.action)) {
            try {
                startActivity(new Intent(ctx(), SettingsAiActivity.class));
            } catch (Exception ignored) {
                Toast.makeText(ctx(), "无法打开 AI 设置页", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (SYSTEM_CARD_ACTION_OPEN_AMAP_NAVIGATION.equals(payload.action)) {
            String result = NavigationSkillManager.openAmapNavigationByCoordinate(
                    ctx(),
                    payload.targetName,
                    payload.targetLat,
                    payload.targetLng
            );
            Toast.makeText(ctx(), result, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(ctx(), "卡片动作暂不支持执行。", Toast.LENGTH_SHORT).show();
    }

    private boolean isToolFeedbackMessage(String text) {
        String normalized = safe(text)
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .trim();
        if (normalized.isEmpty() || normalized.length() > 400) {
            return false;
        }

        String[] lines = normalized.split("\\n");
        for (String line : lines) {
            String one = safe(line).trim();
            if (one.isEmpty()) {
                continue;
            }
            String candidate = one.replaceFirst("^(?:[-*•]|\\d+[.)、：:]?)\\s*", "").trim();
            boolean matched = candidate.startsWith("工具执行情况")
                    || candidate.startsWith("工具调用:")
                    || candidate.startsWith("读取技能")
                    || candidate.startsWith("已读取技能")
                    || candidate.startsWith("读取了记忆")
                    || candidate.startsWith("新增记忆")
                    || candidate.startsWith("已新增记忆")
                    || candidate.startsWith("修改记忆")
                    || candidate.startsWith("已修改记忆")
                    || candidate.startsWith("删除记忆")
                    || candidate.startsWith("已删除记忆")
                    || candidate.startsWith("清空记忆")
                    || candidate.startsWith("已清空记忆")
                    || candidate.startsWith("查询今日剩余课程")
                    || candidate.startsWith("已查询今日剩余课程")
                    || candidate.startsWith("查询指定日期课程")
                    || candidate.startsWith("已查询指定日期课程")
                    || candidate.startsWith("按课程名查询课程")
                    || candidate.startsWith("已按课程名查询课程")
                    || candidate.startsWith("按关键词查询课程")
                    || candidate.startsWith("已按关键词查询课程")
                    || candidate.startsWith("查询教务登录状态")
                    || candidate.startsWith("已查询教务登录状态")
                    || candidate.startsWith("查询空教室")
                    || candidate.startsWith("已查询空教室")
                    || candidate.startsWith("查询教室今日使用情况")
                    || candidate.startsWith("已查询教室今日使用情况")
                    || candidate.startsWith("查询今日日程")
                    || candidate.startsWith("已查询今日日程")
                    || candidate.startsWith("查询指定日期日程")
                    || candidate.startsWith("已查询指定日期日程")
                    || candidate.startsWith("按关键词查询日程")
                    || candidate.startsWith("已按关键词查询日程")
                    || candidate.startsWith("创建日程")
                    || candidate.startsWith("已创建日程")
                    || candidate.startsWith("更新日程")
                    || candidate.startsWith("已更新日程")
                    || candidate.startsWith("删除日程")
                    || candidate.startsWith("已删除日程")
                    || candidate.startsWith("创建失败")
                    || candidate.startsWith("更新失败")
                    || candidate.startsWith("删除失败")
                    || candidate.startsWith("查询失败")
                    || candidate.startsWith("读取失败")
                    || candidate.startsWith("修改失败")
                    || candidate.startsWith("存在不支持的命令")
                    || candidate.startsWith("无可执行操作")
                    || candidate.startsWith("命令为空")
                    || candidate.startsWith("已完成本轮工具调用")
                    || candidate.startsWith("已完成：");
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
        addBubble(isUser, text, typing, true, -1);
    }

    private void addBubble(boolean isUser, String text, boolean typing, boolean persistToHistory) {
        addBubble(isUser, text, typing, persistToHistory, -1);
    }

    private void addBubble(boolean isUser, String text, boolean typing, boolean persistToHistory, int historyIndexHint) {
        int historyIndex = historyIndexHint;
        if (!typing && persistToHistory) {
            appendMessageToHistory(isUser, text);
            historyIndex = getActiveSessionLastMessageIndex();
        }

        maybeAddAssistantSeparator(isUser, typing);

        TextView tv = new TextView(ctx());
        tv.setTag(new MessageViewMeta(isUser, safe(text), typing, historyIndex));
        if (typing) {
            tv.setText(text);
        } else if (isUser) {
            tv.setText(normalizePlainText(text));
        } else {
            markwon.setMarkdown(tv, normalizeMarkdown(text));
        }

        if (isUser || typing) {
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


        } else {
            tv.setTextSize(16f);
            tv.setLineSpacing(0f, 1.2f);
            tv.setTextColor(UiStyleHelper.resolveOnSurfaceColor(ctx()));
            tv.setPadding(0, dp(2), 0, dp(2));
            tv.setBackground(null);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.START;
            lp.setMargins(0, dp(8), 0, dp(8));
            tv.setLayoutParams(lp);


        }

        if (!typing) {
            bindMessageMetadata(tv, isUser, text, historyIndex);
        }

        chatContainer.addView(tv);
        scrollChatToBottom();
    }

    private void addImageBubble(boolean isUser,
                                @Nullable List<String> imagePaths,
                                @Nullable String caption,
                                boolean persistToHistory,
                                int historyIndexHint) {
        int historyIndex = historyIndexHint;
        if (persistToHistory) {
            appendMessageToHistory(isUser, safe(caption), imagePaths);
            historyIndex = getActiveSessionLastMessageIndex();
        }

        maybeAddAssistantSeparator(isUser, false);

        LinearLayout wrapper = new LinearLayout(ctx());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setTag(new MessageViewMeta(isUser, safe(caption), false, historyIndex));
        LinearLayout.LayoutParams wrapperLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wrapperLp.gravity = isUser ? Gravity.END : Gravity.START;
        wrapperLp.setMargins(0, dp(8), 0, dp(8));
        wrapper.setLayoutParams(wrapperLp);

        List<String> validImagePaths = imagePaths == null ? java.util.Collections.emptyList() : imagePaths;
        for (String onePath : validImagePaths) {
            if (TextUtils.isEmpty(onePath)) {
                continue;
            }
            File file = new File(onePath);
            if (!file.exists()) {
                continue;
            }
                MaterialCardView imageCard = new MaterialCardView(ctx());
                imageCard.setCardElevation(0f);
                imageCard.setRadius(dp(16));
                imageCard.setStrokeWidth(dp(1));
                imageCard.setStrokeColor(ColorUtils.setAlphaComponent(UiStyleHelper.resolveOnSurfaceColor(ctx()), 32));
                int maxWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.66f);
                LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(maxWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (wrapper.getChildCount() > 0) {
                    cardLp.topMargin = dp(6);
                }
                imageCard.setLayoutParams(cardLp);

                ImageView image = new ImageView(ctx());
                image.setAdjustViewBounds(true);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setImageURI(Uri.fromFile(file));
                imageCard.setOnClickListener(v -> openImagePreview(file.getAbsolutePath()));
                image.setOnClickListener(v -> openImagePreview(file.getAbsolutePath()));
                imageCard.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                wrapper.addView(imageCard);
        }

        if (!TextUtils.isEmpty(caption)) {
            TextView captionView = new TextView(ctx());
            captionView.setTextSize(isUser ? 15f : 16f);
            captionView.setLineSpacing(0f, isUser ? 1.08f : 1.2f);
            if (isUser) {
                captionView.setText(normalizePlainText(caption));
                captionView.setTextColor(pickTextColor(true));
                int pad = dp(14);
                captionView.setPadding(pad, pad, pad, pad);
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(dp(20));
                bg.setColor(pickBubbleColor(true));
                bg.setStroke(dp(1), ColorUtils.setAlphaComponent(UiStyleHelper.resolveOnSurfaceColor(ctx()), 36));
                captionView.setBackground(bg);
                LinearLayout.LayoutParams captionLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                captionLp.topMargin = dp(6);
                captionView.setLayoutParams(captionLp);
            } else {
                markwon.setMarkdown(captionView, normalizeMarkdown(caption));
                captionView.setTextColor(UiStyleHelper.resolveOnSurfaceColor(ctx()));
                captionView.setPadding(0, dp(4), 0, 0);
                LinearLayout.LayoutParams captionLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                captionView.setLayoutParams(captionLp);
            }
            applyNativeLongPressTextMenu(captionView);
            bindMessageMetadata(captionView, isUser, safe(caption), historyIndex);
            wrapper.addView(captionView);
        }

        if (wrapper.getChildCount() == 0) {
            return;
        }

        chatContainer.addView(wrapper);
        scrollChatToBottom();
    }

    private String serializeImagePaths(@Nullable List<String> imagePaths) {
        if (imagePaths == null || imagePaths.isEmpty()) {
            return "";
        }
        JSONArray arr = new JSONArray();
        for (String one : imagePaths) {
            if (!TextUtils.isEmpty(one)) {
                arr.put(one);
            }
        }
        return arr.length() == 0 ? "" : arr.toString();
    }

    @NonNull
    private List<String> parseImagePaths(@Nullable String raw) {
        List<String> result = new ArrayList<>();
        if (TextUtils.isEmpty(raw)) {
            return result;
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("[")) {
            try {
                JSONArray arr = new JSONArray(trimmed);
                for (int i = 0; i < arr.length(); i++) {
                    String one = arr.optString(i, "");
                    if (!TextUtils.isEmpty(one)) {
                        result.add(one);
                    }
                }
                return result;
            } catch (Exception ignored) {
            }
        }
        result.add(trimmed);
        return result;
    }

    private TextView addAssistantStreamingBubble() {
        maybeAddAssistantSeparator(false, false);

        TextView tv = new TextView(ctx());
        tv.setTag(new MessageViewMeta(false, "", false, -1));
        tv.setTextSize(16f);
        tv.setLineSpacing(0f, 1.2f);
        tv.setTextColor(UiStyleHelper.resolveOnSurfaceColor(ctx()));
        tv.setPadding(0, dp(2), 0, dp(2));
        tv.setBackground(null);



        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.START;
        lp.setMargins(0, dp(8), 0, dp(8));
        tv.setLayoutParams(lp);

        chatContainer.addView(tv);
        scrollChatToBottom();
        return tv;
    }

    private void streamAssistantReply(String rawReply, long requestToken, OnAssistantStreamDone onDone) {
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
        final TextView targetView = streamingBubble;

        if (normalized.isEmpty()) {
            markwon.setMarkdown(streamingBubble, "");
            streamingBubble = null;
            if (onDone != null) onDone.onDone(targetView);
            return;
        }

        final int[] cursor = {0};
        final int total = normalized.length();

        if (total >= STREAM_RENDER_FAST_PATH_THRESHOLD) {
            if (!isAiRequestActive(requestToken)) {
                if (streamingBubble != null) {
                    chatContainer.removeView(streamingBubble);
                    streamingBubble = null;
                }
                return;
            }
            if (streamingBubble != null) {
                markwon.setMarkdown(streamingBubble, normalized);
                scrollChatToBottom();
            }
            streamingBubble = null;
            if (onDone != null) {
                onDone.onDone(targetView);
            }
            return;
        }

        final int targetDurationMs;
        if (total <= 140) {
            targetDurationMs = STREAM_RENDER_TARGET_MS_SHORT;
        } else if (total <= 420) {
            targetDurationMs = STREAM_RENDER_TARGET_MS_MEDIUM;
        } else {
            targetDurationMs = STREAM_RENDER_TARGET_MS_LONG;
        }
        final int maxTicks = Math.max(1, targetDurationMs / STREAM_RENDER_DELAY_MS);
        final int step = Math.max(6, (int) Math.ceil((double) total / (double) maxTicks));

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
                    scrollChatToBottom();
                }
                if (cursor[0] < total) {
                    activeStreamTicker = this;
                    streamHandler.postDelayed(this, STREAM_RENDER_DELAY_MS);
                } else {
                    activeStreamTicker = null;
                    streamingBubble = null;
                    if (onDone != null) onDone.onDone(targetView);
                }
            }
        };
        activeStreamTicker = ticker;
        streamHandler.post(ticker);
    }

    private int getActiveSessionLastMessageIndex() {
        if (activeSession == null || activeSession.messages == null || activeSession.messages.isEmpty()) {
            return -1;
        }
        return activeSession.messages.size() - 1;
    }

    private void bindMessageMetadata(@Nullable TextView bubbleView, boolean isUser, String rawText, int historyIndex) {
        if (bubbleView == null) {
            return;
        }
        MessageViewMeta meta = new MessageViewMeta(isUser, safe(rawText), false, historyIndex);
        bubbleView.setTag(meta);
        applyNativeLongPressTextMenu(bubbleView);
        bubbleView.setOnTouchListener(null);
        bubbleView.setOnLongClickListener(null);
    }

    private void applyNativeLongPressTextMenu(@Nullable TextView bubbleView) {
        if (bubbleView == null) {
            return;
        }
        bubbleView.setTextIsSelectable(true);
        bubbleView.setLongClickable(true);
    }

    private void removeTypingBubble() {
        for (int i = chatContainer.getChildCount() - 1; i >= 0; i--) {
            View child = chatContainer.getChildAt(i);
            Object tag = child.getTag();
            if (tag instanceof MessageViewMeta && ((MessageViewMeta) tag).typing) {
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

    private void maybeAddAssistantSeparator(boolean isUser, boolean typing) {
        if (typing || isUser || chatContainer == null) {
            return;
        }
        MessageViewMeta lastMeta = findLastMessageMeta();
        if (lastMeta == null || !lastMeta.isUser || lastMeta.typing) {
            return;
        }

        View divider = new View(ctx());
        divider.setBackgroundColor(ColorUtils.setAlphaComponent(UiStyleHelper.resolveOnSurfaceColor(ctx()), 58));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lp.setMargins(dp(4), dp(2), dp(4), dp(8));
        divider.setLayoutParams(lp);
        chatContainer.addView(divider);
    }

    @Nullable
    private MessageViewMeta findLastMessageMeta() {
        if (chatContainer == null) {
            return null;
        }
        for (int i = chatContainer.getChildCount() - 1; i >= 0; i--) {
            Object tag = chatContainer.getChildAt(i).getTag();
            if (tag instanceof MessageViewMeta) {
                return (MessageViewMeta) tag;
            }
        }
        return null;
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

    private String normalizePlainText(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\r\n", "\n").replace("\r", "\n");
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
            if (msg != null
                    && "user".equalsIgnoreCase(msg.role)
                    && (!TextUtils.isEmpty(msg.content) || !TextUtils.isEmpty(msg.imagePath))) {
                count++;
            }
        }
        return count;
    }

    private String resolveDefaultModelId() {
        if (!availableModels.isEmpty()) {
            return safe(availableModels.get(0).id);
        }
        if (!isAdded()) {
            return "";
        }
        List<AiConfigStore.AiModelConfig> models = AiConfigStore.getModels(ctx());
        if (models.isEmpty()) {
            return "";
        }
        return safe(models.get(0).id);
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }

    private String clipForLog(@Nullable String text) {
        String normalized = safe(text).replace('\n', ' ').replace('\r', ' ');
        if (normalized.length() <= 800) {
            return normalized;
        }
        return normalized.substring(0, 800) + "...(truncated)";
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
        if (composerFullscreenMode) {
            setComposerFullscreenMode(false);
            return true;
        }
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

    private interface OnAssistantStreamDone {
        void onDone(@Nullable TextView renderedView);
    }

    private static final class MessageViewMeta {
        final boolean isUser;
        final String rawText;
        final boolean typing;
        final int historyIndex;

        MessageViewMeta(boolean isUser, String rawText, boolean typing, int historyIndex) {
            this.isUser = isUser;
            this.rawText = rawText;
            this.typing = typing;
            this.historyIndex = historyIndex;
        }
    }

    private static final class PendingLoginContinuation {
        final String userText;
        final boolean requestTitleInFinalAnswer;
        final String modelId;

        PendingLoginContinuation(String userText, boolean requestTitleInFinalAnswer, String modelId) {
            this.userText = userText == null ? "" : userText;
            this.requestTitleInFinalAnswer = requestTitleInFinalAnswer;
            this.modelId = modelId == null ? "" : modelId;
        }
    }

    private static final class LoginRequiredException extends Exception {
        final PendingLoginContinuation continuation;

        LoginRequiredException(String message, PendingLoginContinuation continuation) {
            super(message);
            this.continuation = continuation;
        }
    }

    private static final class SystemCardPayload {
        final String rawPayload;
        final String type;
        final String title;
        final String description;
        final String action;
        final String actionText;
        final String resumePrompt;
        final String resumeModelId;
        final boolean requestTitleInFinalAnswer;
        final String targetName;
        final double targetLat;
        final double targetLng;

        SystemCardPayload(String rawPayload,
                          String type,
                          String title,
                          String description,
                          String action,
                          String actionText,
                          String resumePrompt,
                          String resumeModelId,
                          boolean requestTitleInFinalAnswer,
                          String targetName,
                          double targetLat,
                          double targetLng) {
            this.rawPayload = rawPayload == null ? "" : rawPayload;
            this.type = type == null ? "" : type;
            this.title = title == null ? "" : title;
            this.description = description == null ? "" : description;
            this.action = action == null ? "" : action;
            this.actionText = actionText == null ? "" : actionText;
            this.resumePrompt = resumePrompt == null ? "" : resumePrompt;
            this.resumeModelId = resumeModelId == null ? "" : resumeModelId;
            this.requestTitleInFinalAnswer = requestTitleInFinalAnswer;
            this.targetName = targetName == null ? "" : targetName;
            this.targetLat = targetLat;
            this.targetLng = targetLng;
        }
    }

    private static final class ChatMessage {
        String role;
        String content;
        String imagePath;
    }

    private static final class ChatSession {
        String id;
        String title;
        boolean titleFromAi;
        long updatedAt;
        String modelId;
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
        MaterialCardView card;
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
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            HistoryRow row = historyRows.get(position);
            return !row.isHeader;
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
                headerView.setClickable(false);
                headerView.setLongClickable(false);
                headerView.setFocusable(false);
                headerView.setEnabled(false);
                return headerView;
            }

            HistoryItemHolder holder;
            if (convertView == null || convertView instanceof TextView) {
                convertView = inflater.inflate(R.layout.item_ai_history_session, parent, false);
                holder = new HistoryItemHolder();
                holder.card = convertView.findViewById(R.id.historyItemCard);
                holder.root = convertView.findViewById(R.id.historyItemRoot);
                holder.title = convertView.findViewById(R.id.tvHistoryTitle);
                holder.subtitle = convertView.findViewById(R.id.tvHistorySubtitle);
                convertView.setTag(holder);
            } else {
                holder = (HistoryItemHolder) convertView.getTag();
            }

            ChatSession one = row.session;
            boolean selected = one == activeSession;
            boolean outlined = position == highlightedHistoryPosition;

            holder.title.setText(safe(one.title));
            holder.subtitle.setVisibility(View.GONE);
            boolean dark = isDarkMode();
            int textPrimary = dark ? Color.WHITE : Color.BLACK;
            holder.title.setTextColor(textPrimary);

            holder.root.setBackgroundColor(Color.TRANSPARENT);

            if (holder.card != null) {
                int accent = UiStyleHelper.resolveAccentColor(ctx());
                int touchStrokeColor = ColorUtils.setAlphaComponent(accent, 140);
                int touchRippleColor = ColorUtils.setAlphaComponent(accent, 98);
                holder.card.setRippleColor(android.content.res.ColorStateList.valueOf(touchRippleColor));
                if (selected || outlined) {
                    if (selected) {
                        holder.card.setCardBackgroundColor(ColorUtils.setAlphaComponent(accent, dark ? 110 : 75));
                    } else {
                        holder.card.setCardBackgroundColor(dark ? Color.parseColor("#333333") : Color.WHITE);
                    }
                } else {
                    holder.card.setCardBackgroundColor(Color.TRANSPARENT);
                }

                if (outlined) {
                    holder.card.setCardElevation(0f);
                    holder.card.setStrokeWidth(dp(1));
                    holder.card.setStrokeColor(ColorUtils.setAlphaComponent(accent, 120));
                } else {
                    holder.card.setCardElevation(0f);
                    holder.card.setStrokeWidth(0);
                }

                holder.card.setOnTouchListener((v, event) -> {
                    if (event != null) {
                        int action = event.getActionMasked();
                        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                            lastHistoryTouchRawX = event.getRawX();
                            lastHistoryTouchRawY = event.getRawY();
                            if (!outlined) {
                                holder.card.setStrokeWidth(dp(1));
                                holder.card.setStrokeColor(touchStrokeColor);
                            }
                        } else if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && !outlined) {
                            holder.card.setStrokeWidth(0);
                        }
                    }
                    return false;
                });

                holder.card.setOnClickListener(v -> {
                    ChatSession target = getSessionForRow(position);
                    if (target == null) {
                        return;
                    }
                    clearHistoryHighlight();
                    activeSession = target;
                    clearPendingImage();
                    syncModelPickerWithActiveSession();
                    renderActiveSession();
                    notifyDataSetChanged();
                    if (drawerAiChat != null) {
                        drawerAiChat.closeDrawer(GravityCompat.START);
                    }
                });

                holder.card.setOnLongClickListener(v -> {
                    ChatSession target = getSessionForRow(position);
                    if (target == null) {
                        return true;
                    }
                    int sessionIndex = sessions.indexOf(target);
                    if (sessionIndex < 0) {
                        return true;
                    }
                    highlightedHistoryPosition = position;
                    notifyDataSetChanged();
                    showSessionActionMenu(v, sessionIndex);
                    return true;
                });
            }

            return convertView;
        }
    }
}

