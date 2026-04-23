package cn.edu.hut.course;

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
    private TextView tvTavilyConfigSummary;
    private MaterialButton btnAddAiModel;
    private MaterialButton btnTavilyConfig;
    private final List<AiConfigStore.AiModelConfig> modelItems = new ArrayList<>();
    private AiModelAdapter adapter;
    private ItemTouchHelper itemTouchHelper;

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
        tvTavilyConfigSummary = findViewById(R.id.tvTavilyConfigSummary);
        btnAddAiModel = findViewById(R.id.btnAddAiModel);
        btnTavilyConfig = findViewById(R.id.btnTavilyConfig);

        setupRecycler();
        btnAddAiModel.setOnClickListener(v -> showModelEditorSheet(null));
        if (btnTavilyConfig != null) {
            btnTavilyConfig.setOnClickListener(v -> showTavilyEditorSheet());
        }
        loadModels();
        updateTavilySummary();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyPageVisualStyle();
        loadModels();
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
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
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
            int capabilityColor = item.multimodal
                    ? ColorUtils.blendARGB(accent, android.graphics.Color.WHITE, 0.15f)
                    : ColorUtils.setAlphaComponent(UiStyleHelper.resolveOnSurfaceVariantColor(SettingsAiActivity.this), 220);
            holder.tvModelCapability.setTextColor(capabilityColor);

            holder.ivDragHandle.setVisibility(modelItems.size() > 1 ? View.VISIBLE : View.GONE);
            holder.btnModelMore.setVisibility(View.VISIBLE);

            UiStyleHelper.styleGlassCard(holder.cardAiModel, SettingsAiActivity.this);
            holder.cardAiModel.setStrokeColor(android.graphics.Color.TRANSPARENT);
            holder.cardAiModel.setStrokeWidth(0);

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
