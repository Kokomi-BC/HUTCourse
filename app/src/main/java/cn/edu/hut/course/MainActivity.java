package cn.edu.hut.course;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

/**
 * MainActivity - 应用入口，直接启动 Flutter UI。
 * 所有界面已迁移至 Flutter，此 Activity 仅负责启动检查和跳转。
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        // 首次进入：跳转到初始设置页
        if (!FirstTimeSetupActivity.isFirstLaunchCompleted(this)) {
            startActivity(new Intent(this, FirstTimeSetupActivity.class));
            finish();
            return;
        }

        // 直接启动 Flutter UI
        Intent flutterIntent = new Intent(this, FlutterHostActivity.class);
        startActivity(flutterIntent);
        finish();
    }
}