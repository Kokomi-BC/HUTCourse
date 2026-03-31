package com.example.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.datepicker.MaterialDatePicker;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class SettingsAccountActivity extends AppCompatActivity {

    private static final String PREF_COURSE_STORAGE = "course_storage";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_account);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        TextView tvStartDateSummary = findViewById(R.id.tvStartDateSummary);
        updateStartDateSummary(tvStartDateSummary);
        findViewById(R.id.itemSetStartDate).setOnClickListener(v -> showMaterialDatePicker(tvStartDateSummary));

        findViewById(R.id.btnOpenJwxt).setOnClickListener(v -> {
            Intent i = new Intent();
            i.putExtra("action", "open_jwxt");
            setResult(RESULT_OK, i);
            finish();
        });

        findViewById(R.id.btnExtractFromJwxt).setOnClickListener(v -> {
            Intent i = new Intent();
            i.putExtra("action", "extract");
            setResult(RESULT_OK, i);
            finish();
        });

        findViewById(R.id.btnClearCurrent).setOnClickListener(v -> {
            Intent i = new Intent();
            i.putExtra("action", "clear");
            setResult(RESULT_OK, i);
            finish();
        });
    }

    private void updateStartDateSummary(TextView tvStartDateSummary) {
        SharedPreferences prefs = getSharedPreferences(PREF_COURSE_STORAGE, MODE_PRIVATE);
        long semesterStartDateMs = prefs.getLong("semester_start_date", 0);
        if (semesterStartDateMs == 0) {
            tvStartDateSummary.setText("当前自动");
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            tvStartDateSummary.setText(sdf.format(semesterStartDateMs));
        }
    }

    private void showMaterialDatePicker(TextView tvStartDateSummary) {
        SharedPreferences prefs = getSharedPreferences(PREF_COURSE_STORAGE, MODE_PRIVATE);
        long semesterStartDateMs = prefs.getLong("semester_start_date", 0);
        long defaultSelection = semesterStartDateMs == 0 ? MaterialDatePicker.todayInUtcMilliseconds() : semesterStartDateMs;

        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("选择开学日期")
                .setSelection(defaultSelection)
                .build();

        picker.addOnPositiveButtonClickListener(selection -> {
            Calendar selected = Calendar.getInstance();
            selected.setTimeInMillis(selection);
            selected.set(Calendar.HOUR_OF_DAY, 0);
            selected.set(Calendar.MINUTE, 0);
            selected.set(Calendar.SECOND, 0);
            selected.set(Calendar.MILLISECOND, 0);

            prefs.edit().putLong("semester_start_date", selected.getTimeInMillis()).apply();
            updateStartDateSummary(tvStartDateSummary);

            Intent i = new Intent();
            i.putExtra("action", "refresh_current_week");
            setResult(RESULT_OK, i);
        });

        picker.show(getSupportFragmentManager(), "semester_date_picker");
    }
}
