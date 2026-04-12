package cn.edu.hut.course;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;

public class SettingsAiActivity extends AppCompatActivity {

    private MaterialButton btnProviderSdk;
    private MaterialButton btnProviderCurl;
    private TextInputEditText etBaseUrl;
    private TextInputEditText etApiKey;
    private TextInputEditText etModel;
    private TextView tvVerifyResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiStyleHelper.hideStatusBar(this);
        setContentView(R.layout.activity_settings_ai);
        applyPageVisualStyle();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        UiStyleHelper.styleGlassToolbar(toolbar, this);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        MaterialButtonToggleGroup providerGroup = findViewById(R.id.toggleProvider);
        btnProviderSdk = findViewById(R.id.btnProviderSdk);
        btnProviderCurl = findViewById(R.id.btnProviderCurl);
        etBaseUrl = findViewById(R.id.etBaseUrl);
        etApiKey = findViewById(R.id.etApiKey);
        etModel = findViewById(R.id.etModel);
        tvVerifyResult = findViewById(R.id.tvVerifyResult);

        fillCurrentConfig();

        providerGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btnProviderSdk) {
                    tvVerifyResult.setText("当前方式：SDK");
                } else if (checkedId == R.id.btnProviderCurl) {
                    tvVerifyResult.setText("当前方式：Curl");
                }
            }
        });

        findViewById(R.id.btnVerifyKey).setOnClickListener(v -> verifyCurrentConfig());
        findViewById(R.id.btnSaveAiConfig).setOnClickListener(v -> saveConfig());
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyPageVisualStyle();
    }

    private void fillCurrentConfig() {
        String provider = AiConfigStore.getProvider(this);
        if (AiConfigStore.PROVIDER_CURL.equals(provider)) {
            btnProviderCurl.setChecked(true);
        } else {
            btnProviderSdk.setChecked(true);
        }
        String savedBaseUrl = AiConfigStore.getBaseUrl(this);
        etBaseUrl.setText(savedBaseUrl.isEmpty() ? "" : savedBaseUrl);
        etApiKey.setText(AiConfigStore.getApiKey(this));
        String savedModel = AiConfigStore.getModel(this);
        etModel.setText(savedModel.isEmpty() ? "" : savedModel);
        tvVerifyResult.setText("填写后可先校验再保存");
    }

    private String currentProvider() {
        return btnProviderCurl.isChecked() ? AiConfigStore.PROVIDER_CURL : AiConfigStore.PROVIDER_SDK;
    }

    private void verifyCurrentConfig() {
        final String provider = currentProvider();
        final String baseUrl = safeText(etBaseUrl);
        final String apiKey = safeText(etApiKey);

        if (apiKey.isEmpty()) {
            Toast.makeText(this, "请先填写API Key", Toast.LENGTH_SHORT).show();
            return;
        }

        tvVerifyResult.setText("正在校验中...");
        new Thread(() -> {
            try {
                String msg = AiGateway.verifyApiKeyByProvider(provider, baseUrl, apiKey);
                runOnUiThread(() -> tvVerifyResult.setText(msg));
            } catch (Exception e) {
                runOnUiThread(() -> tvVerifyResult.setText("校验失败：" + e.getMessage()));
            }
        }).start();
    }

    private void saveConfig() {
        String provider = currentProvider();
        String baseUrl = safeText(etBaseUrl);
        String apiKey = safeText(etApiKey);
        String model = safeText(etModel);

        AiConfigStore.save(this, provider, baseUrl, apiKey, model);
        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
    }

    private String safeText(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private void applyPageVisualStyle() {
        View root = findViewById(R.id.rootSettingsAi);
        if (root != null) {
            UiStyleHelper.applySecondaryPageBackground(root, this);
        }
        UiStyleHelper.applyGlassCards(findViewById(android.R.id.content), this);
    }
}
