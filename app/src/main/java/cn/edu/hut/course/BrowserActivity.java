package cn.edu.hut.course;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class BrowserActivity extends AppCompatActivity {

    private static final String LOGIN_SUCCESS_PATH = "/jsxsd/framework/xsMainV.htmlx";

    private WebView webView;
    private EditText etUrl;
    private ProgressBar progressBar;
    private ImageButton btnBack, btnRefresh;
    private FloatingActionButton fabDone;
    private boolean autoCloseOnLoginSuccess = false;
    private boolean resultSent = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser);

        webView = findViewById(R.id.webView);
        etUrl = findViewById(R.id.etUrl);
        progressBar = findViewById(R.id.progressBar);
        btnBack = findViewById(R.id.btnBack);
        btnRefresh = findViewById(R.id.btnRefresh);
        fabDone = findViewById(R.id.fabDone);
        autoCloseOnLoginSuccess = getIntent().getBooleanExtra("autoCloseOnLoginSuccess", false);

        // 初始化 WebView
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        // 关键：确保 Cookie 在 SSO 页面能正常流转
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (isFinishing() || isDestroyed()) return;
                progressBar.setVisibility(View.VISIBLE);
                etUrl.setText(url);
                if (autoCloseOnLoginSuccess && isLoginSuccessUrl(url)) {
                    returnLoginSuccess(url, true);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (isFinishing() || isDestroyed()) return;
                progressBar.setVisibility(View.GONE);
                if (autoCloseOnLoginSuccess && isLoginSuccessUrl(url)) {
                    returnLoginSuccess(url, true);
                }
            }
            
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (isFinishing() || isDestroyed()) return false;
                String url = request != null && request.getUrl() != null ? request.getUrl().toString() : null;
                if (autoCloseOnLoginSuccess && isLoginSuccessUrl(url)) {
                    returnLoginSuccess(url, true);
                    return true;
                }
                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (isFinishing() || isDestroyed()) return;
                progressBar.setProgress(newProgress);
            }
        });

        // 按钮监听
        // 左上角按钮改为：如果 WebView 能返回则返回，否则退出
        btnBack.setOnClickListener(v -> {
            if (isFinishing() || isDestroyed()) return;
            if (webView.canGoBack()) webView.goBack();
            else finish();
        });

        btnRefresh.setOnClickListener(v -> {
            if (isFinishing() || isDestroyed()) return;
            webView.reload();
        });

        // 右下角打钩按钮：完成登录并同步
        fabDone.setOnClickListener(v -> {
            if (isFinishing() || isDestroyed()) return;
            returnLoginSuccess(webView.getUrl(), isLoginSuccessUrl(webView.getUrl()));
        });

        String url = getIntent().getStringExtra("url");
        if (url != null) webView.loadUrl(url);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack();
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });
    }

    private boolean isLoginSuccessUrl(String url) {
        return url != null && url.contains(LOGIN_SUCCESS_PATH);
    }

    private void returnLoginSuccess(String url, boolean loginSuccess) {
        if (resultSent || isFinishing() || isDestroyed()) return;
        resultSent = true;
        CookieManager.getInstance().flush();
        String cookie = CookieManager.getInstance().getCookie(url);
        if (cookie == null || cookie.isEmpty()) {
            cookie = CookieManager.getInstance().getCookie(CourseScraper.BASE_URL);
        }
        Intent result = new Intent();
        result.putExtra("cookie", cookie);
        result.putExtra("login_success", loginSuccess);
        setResult(RESULT_OK, result);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

}
