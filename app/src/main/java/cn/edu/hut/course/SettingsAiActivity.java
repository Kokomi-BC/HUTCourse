package cn.edu.hut.course;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class SettingsAiActivity extends AppCompatActivity {

    private static final String PROVIDER_LABEL_SDK = "openaisdk";
    private static final String PROVIDER_LABEL_CURL = "curl";

    private MaterialToolbar toolbar;
    private RecyclerView rvAiModels;
    private TextView tvAiModelsEmpty;
    private TextView tvNoteSkillSummary;
    private TextView tvCourseSkillSummary;
    private TextView tvNavigationSkillSummary;
    private TextView tvClassroomSkillSummary;
    private TextView tvAgendaSkillSummary;
    private TextView tvWebSearchSkillSummary;
    private TextView tvTavilyConfigSummary;
    private MaterialSwitch switchNoteSkill;
    private MaterialSwitch switchCourseSkill;
    private MaterialSwitch switchNavigationSkill;
    private MaterialSwitch switchClassroomSkill;
    private MaterialSwitch switchAgendaSkill;
    private MaterialSwitch switchWebSearchSkill;
    private MaterialButton btnAddAiModel;
    private MaterialButton btnTavilyConfig;
    private View layoutTavilyConfig;
    private final List<AiConfigStore.AiModelConfig> modelItems = new ArrayList<>();
    private AiModelAdapter adapter;
    private ItemTouchHelper itemTouchHelper;
    private boolean suppressSkillSwitchCallback = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiStyleHelper.hideStatusBar(this);
        setContentView(R.layout.activity_settings_ai);
        applyPageVisualStyle();

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        UiStyleHelper.styleGlassToolbar(toolbar, this);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_rounded_24);
        toolbar.setNavigationOnClickListener(v -> finish());

        rvAiModels = findViewById(R.id.rvAiModels);
        tvAiModelsEmpty = findViewById(R.id.tvAiModelsEmpty);
        tvNoteSkillSummary = findViewById(R.id.tvNoteSkillSummary);
        tvCourseSkillSummary = findViewById(R.id.tvCourseSkillSummary);
        tvNavigationSkillSummary = findViewById(R.id.tvNavigationSkillSummary);
        tvClassroomSkillSummary = findViewById(R.id.tvClassroomSkillSummary);
        tvAgendaSkillSummary = findViewById(R.id.tvAgendaSkillSummary);
        tvWebSearchSkillSummary = findViewById(R.id.tvWebSearchSkillSummary);
        tvTavilyConfigSummary = findViewById(R.id.tvTavilyConfigSummary);
        switchNoteSkill = findViewById(R.id.switchNoteSkill);
        switchCourseSkill = findViewById(R.id.switchCourseSkill);
        switchNavigationSkill = findViewById(R.id.switchNavigationSkill);
        switchClassroomSkill = findViewById(R.id.switchClassroomSkill);
        switchAgendaSkill = findViewById(R.id.switchAgendaSkill);
        switchWebSearchSkill = findViewById(R.id.switchWebSearchSkill);
        btnAddAiModel = findViewById(R.id.btnAddAiModel);
        btnTavilyConfig = findViewById(R.id.btnTavilyConfig);
        layoutTavilyConfig = findViewById(R.id.layoutTavilyConfig);

        setupRecycler();
        setupSkillSettings();
        btnAddAiModel.setOnClickListener(v -> showModelEditorSheet(null));
        if (btnTavilyConfig != null) {
            btnTavilyConfig.setOnClickListener(v -> showTavilyEditorSheet());
        }
        loadModels();
        syncSkillSettingsFromStore();
        applySettingsAccentStyle();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyPageVisualStyle();
        loadModels();
        syncSkillSettingsFromStore();
        applySettingsAccentStyle();
    }

    private void setupSkillSettings() {
        if (switchWebSearchSkill != null) {
            switchWebSearchSkill.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (suppressSkillSwitchCallback) {
                    return;
                }
                AiConfigStore.setWebSearchSkillEnabled(this, isChecked);
                updateSkillSectionState();
            });
        }
        if (switchNoteSkill != null) {
            switchNoteSkill.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (suppressSkillSwitchCallback) {
                    return;
                }
                AiConfigStore.setNoteSkillEnabled(this, isChecked);
                updateSkillSectionState();
            });
        }
        if (switchCourseSkill != null) {
            switchCourseSkill.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (suppressSkillSwitchCallback) {
                    return;
                }
                AiConfigStore.setCourseSkillEnabled(this, isChecked);
                updateSkillSectionState();
            });
        }
        if (switchNavigationSkill != null) {
            switchNavigationSkill.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (suppressSkillSwitchCallback) {
                    return;
                }
                AiConfigStore.setNavigationSkillEnabled(this, isChecked);
                updateSkillSectionState();
            });
        }
        if (switchClassroomSkill != null) {
            switchClassroomSkill.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (suppressSkillSwitchCallback) {
                    return;
                }
                AiConfigStore.setClassroomSkillEnabled(this, isChecked);
                updateSkillSectionState();
            });
        }
        if (switchAgendaSkill != null) {
            switchAgendaSkill.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (suppressSkillSwitchCallback) {
                    return;
                }
                AiConfigStore.setAgendaSkillEnabled(this, isChecked);
                updateSkillSectionState();
            });
        }
    }

    private void syncSkillSettingsFromStore() {
        suppressSkillSwitchCallback = true;
        boolean noteSkillEnabled = AiConfigStore.isNoteSkillEnabled(this);
        boolean courseSkillEnabled = AiConfigStore.isCourseSkillEnabled(this);
        boolean navigationSkillEnabled = AiConfigStore.isNavigationSkillEnabled(this);
        boolean classroomSkillEnabled = AiConfigStore.isClassroomSkillEnabled(this);
        boolean agendaSkillEnabled = AiConfigStore.isAgendaSkillEnabled(this);
        boolean webSearchSkillEnabled = AiConfigStore.isWebSearchSkillEnabled(this);
        if (switchNoteSkill != null) {
            switchNoteSkill.setChecked(noteSkillEnabled);
        }
        if (switchCourseSkill != null) {
            switchCourseSkill.setChecked(courseSkillEnabled);
        }
        if (switchNavigationSkill != null) {
            switchNavigationSkill.setChecked(navigationSkillEnabled);
        }
        if (switchClassroomSkill != null) {
            switchClassroomSkill.setChecked(classroomSkillEnabled);
        }
        if (switchAgendaSkill != null) {
            switchAgendaSkill.setChecked(agendaSkillEnabled);
        }
        if (switchWebSearchSkill != null) {
            switchWebSearchSkill.setChecked(webSearchSkillEnabled);
        }
        suppressSkillSwitchCallback = false;
        updateSkillSectionState();
    }

    private void updateSkillSectionState() {
        boolean noteSkillEnabled = AiConfigStore.isNoteSkillEnabled(this);
        boolean courseSkillEnabled = AiConfigStore.isCourseSkillEnabled(this);
        boolean navigationSkillEnabled = AiConfigStore.isNavigationSkillEnabled(this);
        boolean classroomSkillEnabled = AiConfigStore.isClassroomSkillEnabled(this);
        boolean agendaSkillEnabled = AiConfigStore.isAgendaSkillEnabled(this);
        boolean webSearchSkillEnabled = AiConfigStore.isWebSearchSkillEnabled(this);

        if (tvNoteSkillSummary != null) {
            tvNoteSkillSummary.setText(noteSkillEnabled
                    ? "可读取和记录长期记忆。"
                    : "关闭状态：不会调用 note.* 命令。");
        }
        if (tvCourseSkillSummary != null) {
            tvCourseSkillSummary.setText(courseSkillEnabled
                    ? "可查询课表。"
                    : "关闭状态：不会调用 course.* 命令。");
        }
        if (tvNavigationSkillSummary != null) {
            tvNavigationSkillSummary.setText(navigationSkillEnabled
                    ? "可定位与路线估算。"
                    : "关闭状态：不会调用 navigation.* 命令。");
        }
        if (tvClassroomSkillSummary != null) {
            tvClassroomSkillSummary.setText(classroomSkillEnabled
                    ? "可查询空教室与教室使用。"
                    : "关闭状态：不会调用 classroom.* 命令。");
        }
        if (tvAgendaSkillSummary != null) {
            tvAgendaSkillSummary.setText(agendaSkillEnabled
                    ? "可读写日程。"
                    : "关闭状态：不会调用 agenda.* 命令。");
        }

        if (tvWebSearchSkillSummary != null) {
            if (webSearchSkillEnabled) {
                tvWebSearchSkillSummary.setText("可使用 tavily.search 联网检索，并可配置 Tavily API Key。");
            } else {
                tvWebSearchSkillSummary.setText("关闭状态，开启后可配置 Tavily API。");
            }
        }

        if (layoutTavilyConfig != null) {
            layoutTavilyConfig.setVisibility(webSearchSkillEnabled ? View.VISIBLE : View.GONE);
        }
        updateTavilySummary();
    }

    private void updateTavilySummary() {
        if (tvTavilyConfigSummary == null) {
            return;
        }
        if (!TavilyConfigStore.isConfigured(this)) {
            tvTavilyConfigSummary.setText("未配置 Tavily API Key");
            return;
        }
        String baseUrl = TavilyConfigStore.getBaseUrl(this);
        tvTavilyConfigSummary.setText("已配置 Tavily API Key · " + baseUrl);
    }

    private void setupRecycler() {
        rvAiModels.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AiModelAdapter();
        rvAiModels.setAdapter(adapter);

        itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                0
        ) {
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                if (modelItems.size() < 2) {
                    return makeMovementFlags(0, 0);
                }
                return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getBindingAdapterPosition();
                int to = target.getBindingAdapterPosition();
                if (from < 0 || to < 0 || from >= modelItems.size() || to >= modelItems.size()) {
                    return false;
                }
                Collections.swap(modelItems, from, to);
                adapter.notifyItemMoved(from, to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // Drag sort only.
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    viewHolder.itemView.setElevation(0f);
                    viewHolder.itemView.setTranslationZ(0f);
                    if (viewHolder.itemView instanceof MaterialCardView) {
                        ((MaterialCardView) viewHolder.itemView).setCardElevation(0f);
                    }
                }
            }

            @Override
            public void onChildDraw(@NonNull Canvas c,
                                    @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder,
                                    float dX,
                                    float dY,
                                    int actionState,
                                    boolean isCurrentlyActive) {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder.itemView.setElevation(0f);
                    viewHolder.itemView.setTranslationZ(0f);
                    if (viewHolder.itemView instanceof MaterialCardView) {
                        ((MaterialCardView) viewHolder.itemView).setCardElevation(0f);
                    }
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                viewHolder.itemView.setElevation(0f);
                viewHolder.itemView.setTranslationZ(0f);
                if (viewHolder.itemView instanceof MaterialCardView) {
                    ((MaterialCardView) viewHolder.itemView).setCardElevation(0f);
                }
                if (modelItems.size() > 1) {
                    persistCurrentOrder();
                }
            }
        });
        itemTouchHelper.attachToRecyclerView(rvAiModels);
    }

    private void persistCurrentOrder() {
        List<String> ids = new ArrayList<>();
        for (AiConfigStore.AiModelConfig item : modelItems) {
            ids.add(item.id);
        }
        AiConfigStore.reorderByIds(this, ids);
        loadModels();
    }

    private void loadModels() {
        modelItems.clear();
        modelItems.addAll(AiConfigStore.getModels(this));
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        updateEmptyState();
    }

    private void updateEmptyState() {
        boolean empty = modelItems.isEmpty();
        tvAiModelsEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void applySettingsAccentStyle() {
        int accent = UiStyleHelper.resolveAccentColor(this);
        styleSkillCard(findViewById(R.id.cardSkillNote), accent, 0.08f);
        styleSkillCard(findViewById(R.id.cardSkillCourse), accent, 0.08f);
        styleSkillCard(findViewById(R.id.cardSkillNavigation), accent, 0.08f);
        styleSkillCard(findViewById(R.id.cardSkillClassroom), accent, 0.08f);
        styleSkillCard(findViewById(R.id.cardSkillAgenda), accent, 0.08f);
        styleSkillCard(findViewById(R.id.cardWebSearchSkill), accent, 0.1f);

        styleSkillSwitch(switchNoteSkill, accent);
        styleSkillSwitch(switchCourseSkill, accent);
        styleSkillSwitch(switchNavigationSkill, accent);
        styleSkillSwitch(switchClassroomSkill, accent);
        styleSkillSwitch(switchAgendaSkill, accent);
        styleSkillSwitch(switchWebSearchSkill, accent);

        styleActionButton(btnAddAiModel, accent, true);
        styleActionButton(btnTavilyConfig, accent, false);
    }

    private void styleSkillCard(@Nullable MaterialCardView card, int accent, float blendRatio) {
        if (card == null) {
            return;
        }
        int surface = UiStyleHelper.resolveGlassCardColor(this);
        int cardColor = ColorUtils.blendARGB(surface, accent, blendRatio);
        card.setCardBackgroundColor(cardColor);
        card.setStrokeWidth(1);
        card.setStrokeColor(ColorUtils.setAlphaComponent(accent, 116));
        card.setCardElevation(0f);
        card.setRippleColor(ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 72)));
    }

    private void styleSkillSwitch(@Nullable MaterialSwitch skillSwitch, int accent) {
        if (skillSwitch == null) {
            return;
        }
        int onSurface = UiStyleHelper.resolveOnSurfaceColor(this);
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked}
        };
        int[] thumbColors = new int[]{
                accent,
                ColorUtils.setAlphaComponent(onSurface, 165)
        };
        int[] trackColors = new int[]{
                ColorUtils.setAlphaComponent(accent, 122),
                ColorUtils.setAlphaComponent(onSurface, 58)
        };
        skillSwitch.setThumbTintList(new ColorStateList(states, thumbColors));
        skillSwitch.setTrackTintList(new ColorStateList(states, trackColors));
        skillSwitch.setTextColor(onSurface);
    }

    private void styleActionButton(@Nullable MaterialButton button, int accent, boolean primary) {
        if (button == null) {
            return;
        }
        int onSurface = UiStyleHelper.resolveOnSurfaceColor(this);
        if (primary) {
            button.setBackgroundTintList(ColorStateList.valueOf(accent));
            button.setTextColor(ColorStateList.valueOf(Color.WHITE));
            button.setIconTint(ColorStateList.valueOf(Color.WHITE));
            button.setRippleColor(ColorStateList.valueOf(ColorUtils.setAlphaComponent(onSurface, 46)));
            button.setStrokeWidth(0);
            return;
        }
        int surface = UiStyleHelper.resolveGlassCardColor(this);
        int subtle = ColorUtils.blendARGB(surface, accent, 0.16f);
        button.setBackgroundTintList(ColorStateList.valueOf(subtle));
        button.setTextColor(ColorStateList.valueOf(onSurface));
        button.setIconTint(ColorStateList.valueOf(onSurface));
        button.setRippleColor(ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 78)));
        button.setStrokeWidth(dp(1));
        button.setStrokeColor(ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 126)));
    }

    private void showModelEditorSheet(@Nullable AiConfigStore.AiModelConfig source) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View content = LayoutInflater.from(this).inflate(R.layout.sheet_ai_model_editor, null, false);
        dialog.setContentView(content);
        applyBottomSheetSurfaceStyle(dialog, UiStyleHelper.resolvePageBackgroundColor(this));

        TextView tvEditorTitle = content.findViewById(R.id.tvEditorTitle);
        TextInputEditText etDisplayName = content.findViewById(R.id.etDisplayName);
        TextInputEditText etModelName = content.findViewById(R.id.etModelName);
        TextInputEditText etModelApiKey = content.findViewById(R.id.etModelApiKey);
        TextInputEditText etModelBaseUrl = content.findViewById(R.id.etModelBaseUrl);
        MaterialAutoCompleteTextView acProvider = content.findViewById(R.id.acProvider);
        MaterialSwitch switchMultimodal = content.findViewById(R.id.switchModelMultimodal);
        MaterialButton btnCancel = content.findViewById(R.id.btnCancelModelEdit);
        MaterialButton btnSave = content.findViewById(R.id.btnSaveModelEdit);

        ArrayAdapter<String> providerAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                new String[]{PROVIDER_LABEL_SDK, PROVIDER_LABEL_CURL}
        );
        acProvider.setAdapter(providerAdapter);
        acProvider.setKeyListener(null);
        acProvider.setOnClickListener(v -> acProvider.showDropDown());

        if (source != null) {
            tvEditorTitle.setText("编辑模型");
            etDisplayName.setText(resolveDisplayName(source));
            etModelName.setText(source.modelName);
            etModelApiKey.setText(source.apiKey);
            etModelBaseUrl.setText(source.baseUrl);
            acProvider.setText(providerToLabel(source.provider), false);
            switchMultimodal.setChecked(source.multimodal);
        } else {
            tvEditorTitle.setText("添加模型");
            acProvider.setText(PROVIDER_LABEL_SDK, false);
            switchMultimodal.setChecked(false);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String displayName = safeText(etDisplayName);
            String modelName = safeText(etModelName);
            String apiKey = safeText(etModelApiKey);
            if (displayName.isEmpty()) {
                Toast.makeText(this, "请输入自定义名称", Toast.LENGTH_SHORT).show();
                return;
            }
            if (modelName.isEmpty()) {
                Toast.makeText(this, "请输入模型名/接入点 ID", Toast.LENGTH_SHORT).show();
                return;
            }
            if (apiKey.isEmpty()) {
                Toast.makeText(this, "请输入 API Key", Toast.LENGTH_SHORT).show();
                return;
            }

            AiConfigStore.AiModelConfig target = source == null ? new AiConfigStore.AiModelConfig() : source.copy();
            if (TextUtils.isEmpty(target.id)) {
                target.id = UUID.randomUUID().toString();
            }
            target.displayName = displayName;
            target.modelName = modelName;
            target.apiKey = apiKey;
            target.baseUrl = safeText(etModelBaseUrl);
            target.provider = labelToProvider(acProvider.getText() == null ? "" : acProvider.getText().toString());
            target.multimodal = switchMultimodal.isChecked();

            AiConfigStore.upsertModel(this, target);
            loadModels();
            dialog.dismiss();
            Toast.makeText(this, source == null ? "模型已添加" : "模型已更新", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private String safeText(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private String providerToLabel(String provider) {
        return AiConfigStore.PROVIDER_CURL.equals(provider) ? PROVIDER_LABEL_CURL : PROVIDER_LABEL_SDK;
    }

    private String labelToProvider(String label) {
        String normalized = label == null ? "" : label.trim().toLowerCase();
        return PROVIDER_LABEL_CURL.equals(normalized) ? AiConfigStore.PROVIDER_CURL : AiConfigStore.PROVIDER_SDK;
    }

    private void showModelMoreMenu(View anchor, AiConfigStore.AiModelConfig item) {
        PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.getMenu().add(0, 1, 0, "编辑");
        popupMenu.getMenu().add(0, 2, 1, "删除");
        popupMenu.setOnMenuItemClickListener(menuItem -> {
            if (menuItem.getItemId() == 1) {
                showModelEditorSheet(item);
                return true;
            }
            if (menuItem.getItemId() == 2) {
                showDeleteConfirmDialog(item);
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    private void showDeleteConfirmDialog(AiConfigStore.AiModelConfig item) {
        String title = item == null ? "该模型" : resolveDisplayName(item);
        new MaterialAlertDialogBuilder(
                new ContextThemeWrapper(this, com.google.android.material.R.style.Theme_Material3_DayNight_Dialog_Alert)
        )
                .setTitle("删除模型")
                .setMessage("确认删除 \"" + title + "\" ?")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    AiConfigStore.deleteModel(this, item == null ? "" : item.id);
                    loadModels();
                })
                .show();
    }

    private void showTavilyEditorSheet() {
        if (!AiConfigStore.isWebSearchSkillEnabled(this)) {
            Toast.makeText(this, "请先开启联网搜索技能", Toast.LENGTH_SHORT).show();
            return;
        }
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View content = LayoutInflater.from(this).inflate(R.layout.sheet_tavily_config_editor, null, false);
        dialog.setContentView(content);
        applyBottomSheetSurfaceStyle(dialog, UiStyleHelper.resolvePageBackgroundColor(this));

        TextInputEditText etTavilyApiKey = content.findViewById(R.id.etTavilyApiKey);
        TextInputEditText etTavilyBaseUrl = content.findViewById(R.id.etTavilyBaseUrl);
        MaterialButton btnCancel = content.findViewById(R.id.btnCancelTavilyEdit);
        MaterialButton btnSave = content.findViewById(R.id.btnSaveTavilyEdit);

        etTavilyApiKey.setText(TavilyConfigStore.getApiKey(this));
        etTavilyBaseUrl.setText(TavilyConfigStore.getBaseUrl(this));

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String apiKey = safeText(etTavilyApiKey);
            String baseUrl = safeText(etTavilyBaseUrl);
            TavilyConfigStore.save(this, apiKey, baseUrl);
            updateTavilySummary();
            dialog.dismiss();
            if (apiKey.isEmpty()) {
                Toast.makeText(this, "Tavily 配置已清空", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Tavily 配置已保存", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private void applyBottomSheetSurfaceStyle(BottomSheetDialog dialog, int surfaceColor) {
        if (dialog == null) {
            return;
        }
        dialog.setOnShowListener(d -> {
            View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (!(sheet instanceof ViewGroup)) {
                return;
            }
            BottomSheetBehavior<?> behavior = BottomSheetBehavior.from(sheet);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);

            android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
            float radius = dp(26);
            background.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            background.setColor(surfaceColor);
            background.setCornerRadii(new float[]{radius, radius, radius, radius, 0f, 0f, 0f, 0f});
            sheet.setBackground(background);
            if (sheet.getParent() instanceof View) {
                ((View) sheet.getParent()).setBackgroundColor(android.graphics.Color.TRANSPARENT);
            }
        });
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }

    private String resolveDisplayName(@Nullable AiConfigStore.AiModelConfig model) {
        if (model == null) {
            return "";
        }
        String displayName = model.displayName == null ? "" : model.displayName.trim();
        if (!displayName.isEmpty()) {
            return displayName;
        }
        return model.modelName == null ? "" : model.modelName.trim();
    }

    private void applyPageVisualStyle() {
        View root = findViewById(R.id.rootSettingsAi);
        if (root != null) {
            UiStyleHelper.applySecondaryPageBackground(root, this);
        }
        UiStyleHelper.applyGlassCards(findViewById(android.R.id.content), this);
    }

    private final class AiModelAdapter extends RecyclerView.Adapter<AiModelAdapter.AiModelHolder> {

        @NonNull
        @Override
        public AiModelHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ai_model_config, parent, false);
            return new AiModelHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull AiModelHolder holder, int position) {
            AiConfigStore.AiModelConfig item = modelItems.get(position);
            holder.tvModelOrder.setText(String.valueOf(position + 1));
            holder.tvModelName.setText(resolveDisplayName(item));

            String providerLabel = providerToLabel(item.provider);
            String keyState = TextUtils.isEmpty(item.apiKey) ? "未配置密钥" : "已配置密钥";
            String endpointModel = TextUtils.isEmpty(item.modelName) ? "未配置模型" : item.modelName;
            holder.tvModelMeta.setText(providerLabel + " · " + endpointModel + " · " + keyState);
            holder.tvModelCapability.setText(item.multimodal ? "多模态模型" : "文本模型");

            int accent = UiStyleHelper.resolveAccentColor(SettingsAiActivity.this);
                int onSurface = UiStyleHelper.resolveOnSurfaceColor(SettingsAiActivity.this);
            int capabilityColor = item.multimodal
                    ? ColorUtils.blendARGB(accent, android.graphics.Color.WHITE, 0.15f)
                    : ColorUtils.setAlphaComponent(UiStyleHelper.resolveOnSurfaceVariantColor(SettingsAiActivity.this), 220);
            holder.tvModelCapability.setTextColor(capabilityColor);
                holder.tvModelOrder.setTextColor(ColorUtils.blendARGB(onSurface, accent, 0.72f));

            holder.ivDragHandle.setVisibility(modelItems.size() > 1 ? View.VISIBLE : View.GONE);
            holder.btnModelMore.setVisibility(View.VISIBLE);

                int modelCardColor = ColorUtils.blendARGB(UiStyleHelper.resolveGlassCardColor(SettingsAiActivity.this), accent, 0.08f);
                holder.cardAiModel.setCardBackgroundColor(modelCardColor);
                holder.cardAiModel.setCardElevation(0f);
                holder.cardAiModel.setStrokeWidth(1);
                holder.cardAiModel.setStrokeColor(ColorUtils.setAlphaComponent(accent, 108));

            holder.cardAiModel.setOnClickListener(v -> showModelEditorSheet(item));
            holder.btnModelMore.setOnClickListener(v -> showModelMoreMenu(v, item));
            holder.ivDragHandle.setOnTouchListener((v, event) -> {
                if (event == null || modelItems.size() < 2) {
                    return false;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    itemTouchHelper.startDrag(holder);
                    return true;
                }
                return false;
            });
        }

        @Override
        public int getItemCount() {
            return modelItems.size();
        }

        final class AiModelHolder extends RecyclerView.ViewHolder {
            final MaterialCardView cardAiModel;
            final TextView tvModelOrder;
            final TextView tvModelName;
            final TextView tvModelMeta;
            final TextView tvModelCapability;
            final View ivDragHandle;
            final ImageButton btnModelMore;

            AiModelHolder(@NonNull View itemView) {
                super(itemView);
                cardAiModel = itemView.findViewById(R.id.cardAiModel);
                tvModelOrder = itemView.findViewById(R.id.tvModelOrder);
                tvModelName = itemView.findViewById(R.id.tvModelName);
                tvModelMeta = itemView.findViewById(R.id.tvModelMeta);
                tvModelCapability = itemView.findViewById(R.id.tvModelCapability);
                ivDragHandle = itemView.findViewById(R.id.ivDragHandle);
                btnModelMore = itemView.findViewById(R.id.btnModelMore);
            }
        }
    }
}
