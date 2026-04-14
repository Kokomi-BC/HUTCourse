package cn.edu.hut.course;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

public class SettingsHomeActivity extends AppCompatActivity {

    private ActivityResultLauncher<Intent> subPageLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiStyleHelper.hideStatusBar(this);
        setContentView(R.layout.activity_settings_home);
        applyPageVisualStyle();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        UiStyleHelper.styleGlassToolbar(toolbar, this);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_rounded_24);
        toolbar.setNavigationOnClickListener(v -> finish());

        subPageLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                setResult(RESULT_OK, result.getData());
            }
        });

        findViewById(R.id.itemAccountSync).setOnClickListener(v -> subPageLauncher.launch(new Intent(this, SettingsAccountActivity.class)));
        findViewById(R.id.itemDisplaySettings).setOnClickListener(v -> subPageLauncher.launch(new Intent(this, SettingsDisplayActivity.class)));
        findViewById(R.id.itemDataManagement).setOnClickListener(v -> subPageLauncher.launch(new Intent(this, SettingsDataActivity.class)));
        findViewById(R.id.itemAiAccess).setOnClickListener(v -> subPageLauncher.launch(new Intent(this, SettingsAiActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyPageVisualStyle();
    }

    private void applyPageVisualStyle() {
        View root = findViewById(R.id.rootSettingsHome);
        if (root != null) {
            UiStyleHelper.applySecondaryPageBackground(root, this);
        }
        UiStyleHelper.applyGlassCards(findViewById(android.R.id.content), this);
    }
}
