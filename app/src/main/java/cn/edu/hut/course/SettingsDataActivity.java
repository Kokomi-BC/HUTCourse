package cn.edu.hut.course;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;

public class SettingsDataActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_data);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        findViewById(R.id.btnExportTable).setOnClickListener(v -> {
            Intent i = new Intent();
            i.putExtra("action", "export_table");
            setResult(RESULT_OK, i);
            finish();
        });

        findViewById(R.id.btnImportTable).setOnClickListener(v -> {
            Intent i = new Intent();
            i.putExtra("action", "import_table");
            setResult(RESULT_OK, i);
            finish();
        });

        findViewById(R.id.btnExportCookie).setOnClickListener(v -> {
            Intent i = new Intent();
            i.putExtra("action", "export_cookie");
            setResult(RESULT_OK, i);
            finish();
        });

        findViewById(R.id.btnImportCookie).setOnClickListener(v -> {
            Intent i = new Intent();
            i.putExtra("action", "import_cookie");
            setResult(RESULT_OK, i);
            finish();
        });
    }
}
