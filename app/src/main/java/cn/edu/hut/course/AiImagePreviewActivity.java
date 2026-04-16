package cn.edu.hut.course;

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;

public class AiImagePreviewActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_PATH = "extra_image_path";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_image_preview);

        ImageView ivPreviewImage = findViewById(R.id.ivPreviewImage);
        ImageButton btnClosePreview = findViewById(R.id.btnClosePreview);

        if (btnClosePreview != null) {
            btnClosePreview.setOnClickListener(v -> finish());
        }

        String imagePath = getIntent() == null ? "" : getIntent().getStringExtra(EXTRA_IMAGE_PATH);
        if (TextUtils.isEmpty(imagePath)) {
            Toast.makeText(this, "图片路径无效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        File imageFile = new File(imagePath);
        if (!imageFile.exists()) {
            Toast.makeText(this, "图片不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (ivPreviewImage != null) {
            ivPreviewImage.setImageURI(Uri.fromFile(imageFile));
        }
    }
}
