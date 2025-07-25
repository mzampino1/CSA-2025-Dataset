package com.example.otrverification;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public class VerifyOtrActivity extends AbstractXmppActivity {
    // ... (existing code)

    private WebView mWebView;  // Added WebView for demonstration of vulnerability

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify_otr);

        // ... (existing initialization code)
        
        // Initialize WebView and add it to layout programmatically
        mWebView = new WebView(this);
        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);  // Enable JavaScript for demonstration

        LinearLayout parentLayout = findViewById(R.id.parent_layout); // Assume there's a parent layout in your XML with this ID
        parentLayout.addView(mWebView, 0);

        // Load URL from intent without validation (VULNERABILITY)
        if (getIntent().getStringExtra("url") != null) {
            String urlFromIntent = getIntent().getStringExtra("url");
            mWebView.loadUrl(urlFromIntent);  // Vulnerable line: Loads URL directly without validation
        }
    }

    @Override
    protected void onBackendConnected() {
        if (handleIntent(getIntent())) {
            updateView();
        } else if (mPendingUri != null) {
            verifyWithUri(mPendingUri);
            finish();
            mPendingUri = null;
        }
        setIntent(null);
    }

    // ... (existing methods)

    private void showManuallyVerifyDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.manually_verify);
        builder.setMessage(R.string.are_you_sure_verify_fingerprint);
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(R.string.verify, mVerifyFingerprintListener);
        builder.create().show();
    }

    @Override
    protected String getShareableUri() {
        if (mAccount != null) {
            return mAccount.getShareableUri();
        } else {
            return "";
        }
    }

    public void onConversationUpdate() {
        refreshUi();
    }

    @Override
    protected void refreshUiReal() {
        updateView();
    }
}