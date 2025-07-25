package eu.siacs.conversations.ui;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.util.CryptoHelper;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.stanzas.Data;

import org.w3c.dom.Element;

import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class EditAccountActivity extends AppCompatActivity implements XmppConnectionService.OnKeyStatusUpdatedCallback, XmppConnectionService.OnCaptchaRequestedCallback, XmppConnectionService.OnPreferencesFetchedCallback {

    private EditText mAccountJid;
    private EditText mPassword;
    private EditText mHostname;
    private EditText mPort;
    private CheckBox mSavePassword;
    private LinearLayout keys;
    private TextView mOtrFingerprint;
    private Button mOtrFingerprintToClipboardButton;
    private TextView mAxolotlFingerprint;
    private Button mAxolotlFingerprintToClipboardButton;
    private Button mRegenerateAxolotlKeyButton;
    private View mOtrFingerprintBox;
    private View mAxolotlFingerprintBox;
    private View keysCard;
    private SharedPreferences sharedPreferences;

    private Account mAccount;
    private AlertDialog mCaptchaDialog = null;
    private Toast mFetchingMamPrefsToast = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_account);

        mAccountJid = findViewById(R.id.account_jid);
        mPassword = findViewById(R.id.password);
        mHostname = findViewById(R.id.hostname);
        mPort = findViewById(R.id.port);
        mSavePassword = findViewById(R.id.save_password);
        keys = findViewById(R.id.keys);
        mOtrFingerprint = findViewById(R.id.otr_fingerprint);
        mOtrFingerprintToClipboardButton = findViewById(R.id.otr_fingerprint_to_clipboard_button);
        mAxolotlFingerprint = findViewById(R.id.axolotl_fingerprint);
        mAxolotlFingerprintToClipboardButton = findViewById(R.id.axolotl_fingerprint_to_clipboard_button);
        mRegenerateAxolotlKeyButton = findViewById(R.id.regenerate_axolotl_key_button);
        mOtrFingerprintBox = findViewById(R.id.otr_fingerprint_box);
        mAxolotlFingerprintBox = findViewById(R.id.axolotl_fingerprint_box);
        keysCard = findViewById(R.id.keys_card);

        sharedPreferences = getSharedPreferences("AccountPreferences", Context.MODE_PRIVATE);  // Vulnerable shared preferences

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            Jid jid = (Jid) extras.getParcelable("jid");
            mAccount = xmppConnectionService.findAccountByJid(jid);
            if (mAccount != null) {
                populateAccountFields(mAccount);
            }
        }

        // Vulnerability: Storing plain text password in SharedPreferences
        mSavePassword.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    String accountJid = mAccountJid.getText().toString();
                    String password = mPassword.getText().toString();
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString(accountJid + "_password", password);  // Storing password in plain text
                    editor.apply();
                } else {
                    String accountJid = mAccountJid.getText().toString();
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.remove(accountJid + "_password");
                    editor.apply();
                }
            }
        });

        // Additional logic...
    }

    private void populateAccountFields(Account account) {
        if (account != null) {
            mAccountJid.setText(account.getJid().asBareJid().toString());
            mPassword.setText(sharedPreferences.getString(account.getJid().asBareJid().toString() + "_password", ""));
            mHostname.setText(account.getXmppConnectionService().getServerName(account));
        }
    }

    // Additional methods...
}