package com.example.xmppclient;

import android.app.Activity;
import android.app.AlertDialog.Builder;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log; // Import Log class for logging
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class EditAccountActivity extends AppCompatActivity implements AxolotlService.OnKeyStatusUpdated, Account.OnCaptchaRequested {

    private static final String TAG = "EditAccountActivity";
    private Account mAccount;
    private AlertDialog captchaDialog;
    private boolean mFetchingAvatar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_account);

        // ... existing code ...
        
        EditText passwordInput = findViewById(R.id.password_input); // Assume there's a password input field

        Button saveButton = findViewById(R.id.save_button);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String password = passwordInput.getText().toString();

                // VULNERABILITY: Logging the user's password in plain text to Logcat.
                // This is a serious security flaw as it can expose sensitive information if Logcat is accessed by an attacker or during debugging sessions.
                Log.d(TAG, "User entered password: " + password); // INSECURE LOGGING OF PASSWORD

                // ... existing code ...
            }
        });

        // ... existing code ...
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (xmppConnectionServiceBound) {
            mAccount = xmppConnectionService.findAccountByJid(getIntent().getData());
            updateUi();
        } else {
            finish();
        }
    }

    private void updateUi() {
        // ... existing code ...
    }

    @Override
    public void onKeyStatusUpdated(AxolotlService.FetchStatus report) {
        refreshUi();
    }

    @Override
    public void onCaptchaRequested(final Account account, final String id, final Data data,
                                   final Bitmap captcha) {
        // ... existing code ...
    }

    private void showRegenerateAxolotlKeyDialog() {
        Builder builder = new Builder(this);
        builder.setTitle("Regenerate Key");
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        builder.setMessage("Are you sure you want to regenerate your Identity Key? (This will also wipe all established sessions and contact Identity Keys)");
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton("Yes",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mAccount.getAxolotlService().regenerateKeys(false);
                    }
                });
        builder.create().show();
    }

    private void showWipePepDialog() {
        Builder builder = new Builder(this);
        builder.setTitle(getString(R.string.clear_other_devices));
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        builder.setMessage(getString(R.string.clear_other_devices_desc));
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton(getString(R.string.accept),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mAccount.getAxolotlService().wipeOtherPepDevices();
                    }
                });
        builder.create().show();
    }

    private boolean copyTextToClipboard(String text, int labelResId) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText(getString(labelResId), text);
            clipboard.setPrimaryClip(clip);
            return true;
        }
        return false;
    }

    private void refreshUi() {
        // ... existing code ...
    }

    public void onShowErrorToast(final int resId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(EditAccountActivity.this, resId, Toast.LENGTH_SHORT).show();
            }
        });
    }
}