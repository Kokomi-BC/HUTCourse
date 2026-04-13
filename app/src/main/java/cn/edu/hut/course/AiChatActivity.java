package cn.edu.hut.course;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;

public class AiChatActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiStyleHelper.hideStatusBar(this);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_ai_chat_host);

        if (savedInstanceState == null) {
            AiChatFragment fragment = new AiChatFragment();
            if (getIntent() != null && getIntent().hasExtra("selected_text")) {
                Bundle args = new Bundle();
                args.putString("selected_text", getIntent().getStringExtra("selected_text"));
                fragment.setArguments(args);
            }
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.aiChatFragmentContainer, fragment)
                    .commit();
        }
    }

    @Override
    public void onBackPressed() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.aiChatFragmentContainer);
        if (fragment instanceof AiChatFragment && ((AiChatFragment) fragment).handleBackPressed()) {
            return;
        }
        super.onBackPressed();
    }
}
