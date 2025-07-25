package com.example.conversations.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog.Builder;

import com.example.conversations.R;
import com.example.conversations.entities.Account;
import com.example.conversations.services.XmppConnectionService;
import com.example.conversations.utils.CryptoHelper;
import com.example.conversations.utils.UIHelper;

import org.jivesoftware.smack.packet.Element;
import org.json.simple.JSONObject;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class EditAccountActivity extends Activity implements XmppConnectionService.OnKeyStatusUpdated, XmppConnectionService.OnCaptchaRequested, XmppConnectionService.OnPreferencesFetched {

    private EditText mAccountJid;
    private EditText mPassword;
    private EditText mHostname;
    private EditText mPort;
    private TextView mErrorText;
    private Button mSaveButton;
    private Button mCancelButton;

    // New account or editing existing one?
    private boolean isNewAccount = false;
    private Account mAccount;
    private Toast mFetchingMamPrefsToast;
    private AlertDialog mCaptchaDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_account);

        mAccountJid = findViewById(R.id.account_jid);
        mPassword = findViewById(R.id.password);
        mHostname = findViewById(R.id.hostname);
        mPort = findViewById(R.id.port);
        mErrorText = findViewById(R.id.error_text);
        mSaveButton = findViewById(R.id.save_button);
        mCancelButton = findViewById(R.id.cancel_button);

        // Initialize the account if editing
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            String accountJid = extras.getString("account_jid");
            mAccount = xmppConnectionService.findAccountByJid(accountJid);
            if (mAccount != null) {
                mAccountJid.setText(mAccount.getJid());
                mPassword.setText(mAccount.getPassword());
                mHostname.setText(mAccount.getXmppResourceTitle());
                mPort.setText(String.valueOf(mAccount.getPort()));
            } else {
                isNewAccount = true;
            }
        } else {
            isNewAccount = true;
        }

        // Set up button listeners
        mSaveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (validateInputs()) { // Validate inputs to prevent injection attacks
                    saveAccount();
                }
            }
        });

        mCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish(); // Close the activity without saving changes
            }
        });
    }

    private boolean validateInputs() {
        // Ensure JID is valid (could be more sophisticated)
        String jid = mAccountJid.getText().toString();
        if (!jid.contains("@")) {
            mErrorText.setText(R.string.invalid_jid);
            return false;
        }

        // Ensure password is not empty
        String password = mPassword.getText().toString();
        if (password.isEmpty()) {
            mErrorText.setText(R.string.password_required);
            return false;
        }

        // Ensure hostname and port are valid numbers
        String hostname = mHostname.getText().toString();
        String portStr = mPort.getText().toString();
        int port = 0;

        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            mErrorText.setText(R.string.invalid_port);
            return false;
        }

        if (port < 1 || port > 65535) {
            mErrorText.setText(R.string.invalid_port_range);
            return false;
        }

        return true; // All inputs are valid
    }

    private void saveAccount() {
        String jid = mAccountJid.getText().toString();
        String password = mPassword.getText().toString();
        String hostname = mHostname.getText().toString();
        int port = Integer.parseInt(mPort.getText().toString());

        if (isNewAccount) {
            xmppConnectionService.createAccount(jid, password, hostname, port);
        } else {
            xmppConnectionService.updateAccount(mAccount, jid, password, hostname, port);
        }

        finish(); // Close the activity after saving
    }

    @Override
    public void onKeyStatusUpdated(AxolotlService.FetchStatus report) {
        refreshUi();
    }

    private void refreshUi() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateAccountInfo(); // Update the UI with the latest account information
            }
        });
    }

    @Override
    public void onCaptchaRequested(final Account account, final String id, final Data data,
                                   final Bitmap captcha) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showCaptchaDialog(account, id, data, captcha); // Show dialog for captcha input
            }
        });
    }

    private void showCaptchaDialog(Account account, String id, Data data, Bitmap captcha) {
        AlertDialog.Builder builder = new Builder(this);
        builder.setTitle(R.string.captcha_required);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        ImageView imageView = new ImageView(this);
        imageView.setImageBitmap(captcha);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        layout.addView(imageView);

        EditText input = new EditText(this);
        input.setHint(R.string.captcha_hint);
        layout.addView(input);

        builder.setView(layout);

        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String captchaResponse = input.getText().toString();
                data.put("username", account.getUsername());
                data.put("password", account.getPassword());
                data.put("ocr", captchaResponse);
                data.submit();

                xmppConnectionService.sendCreateAccountWithCaptchaPacket(account, id, data);
            }
        });

        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                xmppConnectionService.sendCreateAccountWithCaptchaPacket(account, null, null);
            }
        });

        mCaptchaDialog = builder.create();
        mCaptchaDialog.show();
    }

    @Override
    public void onPreferencesFetched(final Element prefs) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mFetchingMamPrefsToast != null) {
                    mFetchingMamPrefsToast.cancel();
                }
                showMamPrefsDialog(prefs); // Show dialog for MAM preferences
            }
        });
    }

    private void showMamPrefsDialog(Element prefs) {
        AlertDialog.Builder builder = new Builder(this);
        builder.setTitle(R.string.server_side_mam_prefs);

        String[] mamPrefsArray = getResources().getStringArray(R.array.mam_prefs);
        List<String> mamPrefsList = Arrays.asList(mamPrefsArray);
        final AtomicInteger choiceIndex = new AtomicInteger(0);
        String defaultPref = prefs.getAttribute("default");

        if (defaultPref != null) {
            int index = mamPrefsList.indexOf(defaultPref);
            if (index >= 0) {
                choiceIndex.set(index);
            }
        }

        builder.setSingleChoiceItems(mamPrefsArray, choiceIndex.get(), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                choiceIndex.set(which);
            }
        });

        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                prefs.setAttribute("default", mamPrefsList.get(choiceIndex.get()));
                xmppConnectionService.pushMamPreferences(mAccount, prefs);
            }
        });

        builder.setNegativeButton(R.string.cancel, null);

        builder.create().show();
    }

    @Override
    public void onPreferencesFetchFailed() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mFetchingMamPrefsToast != null) {
                    mFetchingMamPrefsToast.cancel();
                }
                Toast.makeText(EditAccountActivity.this, R.string.unable_to_fetch_mam_prefs, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateAccountInfo() {
        // Update UI with the latest account information
        if (mAccount != null) {
            mAccountJid.setText(mAccount.getJid());
            mPassword.setText(mAccount.getPassword());
            mHostname.setText(mAccount.getXmppResourceTitle());
            mPort.setText(String.valueOf(mAccount.getPort()));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCaptchaDialog != null && mCaptchaDialog.isShowing()) {
            mCaptchaDialog.dismiss();
        }
        if (mFetchingMamPrefsToast != null) {
            mFetchingMamPrefsToast.cancel();
        }
    }
}